package org.sagacity.sqltoy.utils;

import java.math.BigDecimal;
import java.math.BigInteger;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.util.ArrayList;
import java.util.Calendar;
import java.util.Collection;
import java.util.Date;
import java.util.HashMap;
import java.util.Iterator;
import java.util.List;
import java.util.Map;

import org.sagacity.sqltoy.SqlToyContext;
import org.sagacity.sqltoy.config.model.CacheFilterModel;
import org.sagacity.sqltoy.config.model.ParamFilterModel;
import org.sagacity.sqltoy.model.ParamsFilter;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * @project sagacity-sqltoy
 * @description sql查询参数过滤
 * @author zhongxuchen
 * @version v1.0,Date:2013-3-23
 * @modify Date:2020-7-15 {增加l-like,r-like为参数单边补充%从而不破坏索引,默认是两边}
 */
@SuppressWarnings({ "rawtypes", "unchecked" })
public class ParamFilterUtils {
	/**
	 * 定义日志
	 */
	protected final static Logger logger = LoggerFactory.getLogger(ParamFilterUtils.class);

	// 默认日期格式
	private static final String DAY_FORMAT = "yyyy-MM-dd";

	private ParamFilterUtils() {
	}

	/**
	 * @todo 对查询条件参数进行filter过滤加工处理(如:判断是否为null、日期格式转换等等)
	 * @param sqlToyContext
	 * @param paramArgs
	 * @param values
	 * @param filters
	 * @return
	 */
	public static Object[] filterValue(SqlToyContext sqlToyContext, String[] paramArgs, Object[] values,
			List<ParamFilterModel> filters) {
		if ((filters == null || filters.size() == 0) || values == null || values.length == 0) {
			return values;
		}
		// update 2020-09-08 当全是?模式传参时，在非分页等场景下paramNames会为null导致正常params="*" 的blank过滤无效
		// 构造出参数便于统一处理
		String[] paramsName;
		if ((paramArgs == null || paramArgs.length == 0)) {
			paramsName = new String[values.length];
			for (int i = 0; i < values.length; i++) {
				paramsName[i] = "param_" + i;
			}
		} else {
			paramsName = paramArgs;
		}
		HashMap<String, Integer> paramIndexMap = new HashMap<String, Integer>();
		int paramSize = paramsName.length;
		Object[] paramValues = new Object[paramSize];
		for (int i = 0; i < paramSize; i++) {
			paramIndexMap.put(paramsName[i].toLowerCase(), i);
			paramValues[i] = values[i];
		}
		String[] filterParams;
		int index;
		String filterParam;
		String filterType;
		for (ParamFilterModel paramFilterModel : filters) {
			filterParams = paramFilterModel.getParams();
			filterType = paramFilterModel.getFilterType();
			// 通配符表示针对所有参数
			if (filterParams.length == 1 && filterParams[0].equals("*")) {
				filterParams = paramsName;
			}
			// 排他性参数(当某些参数值都不为null,则设置其他参数值为null)
			if (filterType.equals("exclusive") && paramFilterModel.getUpdateParams() != null) {
				filterExclusive(paramIndexMap, paramFilterModel, paramValues);
			} // 缓存中提取精准查询参数作为sql查询条件值
			else if (filterType.equals("cache-arg")) {
				filterCache(sqlToyContext, paramIndexMap, paramFilterModel, paramValues);
			} else if (filterType.equals("clone")) {
				filterClone(paramIndexMap, paramFilterModel, paramValues);
			}
			// 决定性参数不为null时即条件成立时，需要保留的参数(其他的参数全部设置为null)
			else if (filterType.equals("primary")) {
				filterParam = paramFilterModel.getParam().toLowerCase();
				index = (paramIndexMap.get(filterParam) == null) ? -1 : paramIndexMap.get(filterParam);
				// 决定性参数值不为null
				if (index != -1 && paramValues[index] != null) {
					for (int j = 0; j < paramSize; j++) {
						// 排除自身
						if (j != index && !paramFilterModel.getExcludes().contains(paramsName[j].toLowerCase())) {
							paramValues[j] = null;
						}
					}
				}
			} else {
				for (int i = 0, n = filterParams.length; i < n; i++) {
					filterParam = filterParams[i].toLowerCase();
					index = (paramIndexMap.get(filterParam) == null) ? -1 : paramIndexMap.get(filterParam);
					if (index != -1 && paramValues[index] != null) {
						if (!paramFilterModel.getExcludes().contains(filterParam)) {
							paramValues[index] = filterSingleParam(sqlToyContext, paramValues[index], paramFilterModel);
						}
					}
				}
			}
		}
		return paramValues;
	}

	/**
	 * @todo 从缓存中过滤提取值作为实际查询语句的条件
	 * @param sqlToyContext
	 * @param paramIndexMap
	 * @param paramFilterModel
	 * @param paramValues
	 */
	private static void filterCache(SqlToyContext sqlToyContext, HashMap<String, Integer> paramIndexMap,
			ParamFilterModel paramFilterModel, Object[] paramValues) {
		try {
			String paramName = paramFilterModel.getParam().toLowerCase();
			int index = (paramIndexMap.get(paramName) == null) ? -1 : paramIndexMap.get(paramName);
			// 需要转化的值
			String paramValue = null;
			if (index >= 0) {
				paramValue = (paramValues[index] == null) ? null : paramValues[index].toString();
			}
			if (paramValue == null) {
				return;
			}
			// 是否将转化的值按新的条件参数存储
			String aliasName = paramFilterModel.getAliasName();
			if (StringUtil.isBlank(aliasName)) {
				aliasName = paramFilterModel.getParam();
			}
			if (!paramIndexMap.containsKey(aliasName.toLowerCase())) {
				logger.warn("cache-arg 从缓存:{}取实际条件值别名:{}配置错误,其不在于实际sql语句中!", paramFilterModel.getCacheName(),
						aliasName);
				return;
			}
			// 获取缓存数据
			HashMap<String, Object[]> cacheDataMap = sqlToyContext.getTranslateManager()
					.getCacheData(paramFilterModel.getCacheName(), paramFilterModel.getCacheType());
			if (cacheDataMap == null || cacheDataMap.isEmpty()) {
				logger.warn("缓存:{} 可能不存在,在通过缓存获取查询条件key值时异常,请检查!", paramFilterModel.getCacheName());
				return;
			}

			// 本身就是一个key 代码值,不做处理
			if (cacheDataMap.containsKey(paramValue)) {
				// 存在别名,设置别名对应的值
				if (StringUtil.isNotBlank(paramFilterModel.getAliasName())) {
					int aliasIndex = paramIndexMap.get(paramFilterModel.getAliasName().toLowerCase());
					paramValues[aliasIndex] = paramValue;
				}
				return;
			}
			CacheFilterModel[] cacheFilters = paramFilterModel.getCacheFilters();
			CacheFilterModel cacheFilter;
			// 是否存在对缓存进行条件过滤
			boolean hasFilter = (cacheFilters == null) ? false : true;
			List<Map<String, String>> filterValues = new ArrayList<Map<String, String>>();
			if (hasFilter) {
				Integer cacheValueIndex;
				Object compareValue;
				for (int i = 0; i < cacheFilters.length; i++) {
					cacheFilter = cacheFilters[i];
					cacheValueIndex = paramIndexMap.get(cacheFilter.getCompareParam().toLowerCase());
					compareValue = cacheFilter.getCompareParam();
					if (cacheValueIndex != null) {
						compareValue = paramValues[cacheValueIndex.intValue()];
					}
					Map<String, String> tmp = new HashMap<String, String>();
					if (compareValue.getClass().isArray()) {
						Object[] ary = (Object[]) compareValue;
						for (Object obj : ary) {
							tmp.put(obj.toString(), "1");
						}
					} else if (compareValue instanceof Collection) {
						for (Iterator iter = ((Collection) compareValue).iterator(); iter.hasNext();) {
							tmp.put(iter.next().toString(), "1");
						}
					} else {
						tmp.put(compareValue.toString(), "1");
					}
					filterValues.add(tmp);
				}
			}
			// 匹配数量最大为1000
			int matchCnt = 0;
			// 对比的缓存列(缓存数据以数组形式存储)
			int[] matchIndexes = paramFilterModel.getCacheMappingIndexes();
			// 最大允许匹配数量,缓存匹配一般用于in (?,?)形式的查询,in 参数有数量限制
			int maxLimit = paramFilterModel.getCacheMappingMax();
			// 匹配的缓存key结果集合
			List<Object> matchKeys = new ArrayList<Object>();

			// 循环缓存进行匹配,匹配上将key值放入数组
			Iterator<Object[]> iter = cacheDataMap.values().iterator();
			Object[] cacheRow;
			int cacheKeyIndex = paramFilterModel.getCacheKeyIndex();
			boolean skip = false;
			// 将条件参数值转小写进行统一比较
			String[] lowMatchStr = paramValue.trim().toLowerCase().split("\\s+");
			boolean hasEqual = false;
			while (iter.hasNext()) {
				cacheRow = iter.next();
				skip = false;
				// 对缓存进行过滤(比如过滤本人授权访问机构下面的员工或当期状态为生效的员工)
				if (hasFilter) {
					for (int i = 0; i < cacheFilters.length; i++) {
						cacheFilter = cacheFilters[i];
						// 过滤条件是否相等
						if (cacheRow[cacheFilter.getCacheIndex()] == null) {
							hasEqual = false;
						} else {
							hasEqual = filterValues.get(i)
									.containsKey(cacheRow[cacheFilter.getCacheIndex()].toString());
						}
						// 条件成立则过滤掉
						if ((cacheFilter.getCompareType().equals("eq") && hasEqual)
								|| (cacheFilter.getCompareType().equals("neq") && !hasEqual)) {
							skip = true;
							break;
						}
					}
				}
				if (!skip) {
					for (int matchIndex : matchIndexes) {
						// 匹配检索,全部转成小写比较
						if (cacheRow[matchIndex] != null
								&& StringUtil.like(cacheRow[matchIndex].toString().toLowerCase(), lowMatchStr)) {
							// 第0列为key
							matchKeys.add(cacheRow[cacheKeyIndex]);
							matchCnt++;
							break;
						}
					}
				}
				// 超出阈值跳出
				if (matchCnt == maxLimit) {
					break;
				}
			}

			// 没有匹配到具体key,将笔名对应的列值设置为当前条件值
			if (matchKeys.isEmpty()) {
				if (StringUtil.isNotBlank(paramFilterModel.getAliasName())) {
					int aliasIndex = paramIndexMap.get(paramFilterModel.getAliasName());
					// 没有设置未匹配的默认值,直接将当前参数值作为别名值
					if (StringUtil.isBlank(paramFilterModel.getCacheNotMatchedValue())) {
						paramValues[aliasIndex] = new String[] { paramValue };
					} else {
						paramValues[aliasIndex] = new String[] { paramFilterModel.getCacheNotMatchedValue() };
					}
				} else if (StringUtil.isNotBlank(paramFilterModel.getCacheNotMatchedValue())) {
					paramValues[index] = paramFilterModel.getCacheNotMatchedValue();
				}
				return;
			}
			Object[] realMatched = new Object[matchKeys.size()];
			matchKeys.toArray(realMatched);
			// 存在别名,设置别名对应的值
			if (StringUtil.isNotBlank(paramFilterModel.getAliasName())) {
				int aliasIndex = paramIndexMap.get(paramFilterModel.getAliasName().toLowerCase());
				paramValues[aliasIndex] = realMatched;
			} else {
				paramValues[index] = realMatched;
			}
		} catch (Exception e) {
			e.printStackTrace();
			logger.error("通过缓存匹配查询条件key失败:{}", e.getMessage());
		}
	}

	/**
	 * @todo 互斥性参数filter
	 * @param paramIndexMap
	 * @param paramFilterModel
	 * @param paramValues
	 */
	private static void filterExclusive(HashMap<String, Integer> paramIndexMap, ParamFilterModel paramFilterModel,
			Object[] paramValues) {
		boolean isExclusive = false;
		String filterParam = paramFilterModel.getParam().toLowerCase();
		int index = (paramIndexMap.get(filterParam) == null) ? -1 : paramIndexMap.get(filterParam);
		// 排他性参数中有值为null则排他条件不成立
		if (index != -1) {
			Object paramValue = paramValues[index];
			String compareType = paramFilterModel.getCompareType();
			String[] compareValues = paramFilterModel.getCompareValues();
			if (compareType.equals("==")) {
				if (null == paramValue && null == compareValues) {
					isExclusive = true;
				} else if (null != paramValue && null != compareValues) {
					// 返回null表示条件成立
					if (null == filterEquals(paramValue, compareValues)) {
						isExclusive = true;
					}
				}
			} else {
				if (null != paramValue && null != compareValues) {
					Object result = paramValue;
					if (compareType.equals(">=")) {
						result = filterMoreEquals(paramValue, compareValues[0]);
					} else if (compareType.equals(">")) {
						result = filterMore(paramValue, compareValues[0]);
					} else if (compareType.equals("<=")) {
						result = filterLessEquals(paramValue, compareValues[0]);
					} else if (compareType.equals("<")) {
						result = filterLess(paramValue, compareValues[0]);
					} else if (compareType.equals("<>")) {
						result = filterNotEquals(paramValue, compareValues);
					} else if (compareType.equals("between")) {
						result = filterBetween(paramValue, compareValues[0], compareValues[compareValues.length - 1]);
					} else if (compareType.equals("in")) {
						result = filterEquals(paramValue, compareValues);
					}
					if (null == result) {
						isExclusive = true;
					}
				}
			}
		}
		// 将排斥的参数设置为null
		if (isExclusive) {
			String updateParam;
			String updateValue = paramFilterModel.getUpdateValue();
			for (int i = 0, n = paramFilterModel.getUpdateParams().length; i < n; i++) {
				updateParam = paramFilterModel.getUpdateParams()[i].toLowerCase();
				index = (paramIndexMap.get(updateParam) == null) ? -1 : paramIndexMap.get(updateParam);
				// 排他性参数中有值为null则排他条件不成立
				if (index != -1) {
					if (null == updateValue) {
						paramValues[index] = null;
					} else {
						if (null == paramValues[index]) {
							paramValues[index] = updateValue;
						} else {
							if (paramValues[index] instanceof Date) {
								paramValues[index] = DateUtil.convertDateObject(updateValue);
							} else if (paramValues[index] instanceof Number) {
								paramValues[index] = new BigDecimal(updateValue);
							} else {
								paramValues[index] = updateValue;
							}
						}
					}
				}
			}
		}
	}

	/**
	 * @TODO 将某个参数的值赋给另外一个参数,场景:前端传单日期条件参数，实际查询要组成beginDate,endDate场景
	 * @param paramIndexMap
	 * @param paramFilterModel
	 * @param paramValues
	 */
	private static void filterClone(HashMap<String, Integer> paramIndexMap, ParamFilterModel paramFilterModel,
			Object[] paramValues) {
		if (paramFilterModel.getParam() == null || paramFilterModel.getUpdateParams() == null
				|| paramFilterModel.getUpdateParams().length != 1) {
			return;
		}
		String filterParam = paramFilterModel.getParam().toLowerCase();
		String updateParam = paramFilterModel.getUpdateParams()[0].toLowerCase();
		int paramIndex = (paramIndexMap.get(filterParam) == null) ? -1 : paramIndexMap.get(filterParam);
		int updateIndex = (paramIndexMap.get(updateParam) == null) ? -1 : paramIndexMap.get(updateParam);
		// 存在clone的参数属性
		if (paramIndex != -1 && updateIndex != -1) {
			Object paramValue = paramValues[paramIndex];
			if (paramValue == null) {
				return;
			}
			Object cloneValue = null;
			if (paramValue instanceof String) {
				cloneValue = paramValue.toString();
			} else if (paramValue instanceof Date) {
				cloneValue = ((Date) paramValue).clone();
			} else if (paramValue instanceof LocalDate) {
				LocalDate date = (LocalDate) paramValue;
				cloneValue = LocalDate.of(date.getYear(), date.getMonthValue(), date.getDayOfMonth());
			} else if (paramValue instanceof LocalDateTime) {
				LocalDateTime date = (LocalDateTime) paramValue;
				cloneValue = LocalDateTime.of(date.getYear(), date.getMonthValue(), date.getDayOfMonth(),
						date.getHour(), date.getMinute(), date.getSecond());
			} else if (paramValue instanceof LocalTime) {
				LocalTime date = (LocalTime) paramValue;
				cloneValue = LocalTime.of(date.getHour(), date.getMinute(), date.getSecond());
			} else if (paramValue.getClass().isArray()) {
				cloneValue = ((Object[]) paramValue).clone();
			} else {
				cloneValue = paramValue;
			}
			if (cloneValue != null) {
				paramValues[updateIndex] = cloneValue;
			}
		}
	}

	/**
	 * @todo 过滤加工单个参数的值
	 * @param sqlToyContext
	 * @param paramValue
	 * @param paramFilterModel
	 * @return
	 */
	private static Object filterSingleParam(SqlToyContext sqlToyContext, Object paramValue,
			ParamFilterModel paramFilterModel) {
		if (null == paramValue) {
			return null;
		}
		Object result = paramValue;
		String filterType = paramFilterModel.getFilterType();
		if (filterType.equals("blank")) {
			result = paramValue;
			if (paramValue instanceof CharSequence) {
				if (paramValue.toString().trim().equals("")) {
					result = null;
				}
			} else if (paramValue instanceof Collection) {
				if (((Collection) paramValue).isEmpty()) {
					result = null;
				}
			} else if (paramValue instanceof Map) {
				if (((Map) paramValue).isEmpty()) {
					result = null;
				}
			} else if (paramValue.getClass().isArray()) {
				if (CollectionUtil.convertArray(paramValue).length == 0) {
					result = null;
				}
			}
		} else if (filterType.equals("eq")) {
			result = filterEquals(paramValue, paramFilterModel.getValues());
		} else if (filterType.equals("gt")) {
			result = filterMore(paramValue, paramFilterModel.getValues()[0]);
		} else if (filterType.equals("gte")) {
			result = filterMoreEquals(paramValue, paramFilterModel.getValues()[0]);
		} else if (filterType.equals("lt")) {
			result = filterLess(paramValue, paramFilterModel.getValues()[0]);
		} else if (filterType.equals("lte")) {
			result = filterLessEquals(paramValue, paramFilterModel.getValues()[0]);
		} else if (filterType.equals("between")) {
			result = filterBetween(paramValue, paramFilterModel.getValues()[0],
					paramFilterModel.getValues()[paramFilterModel.getValues().length - 1]);
		} else if (filterType.equals("replace")) {
			result = replace(paramValue, paramFilterModel.getRegex(), paramFilterModel.getValues()[0],
					paramFilterModel.isFirst());
		} else if (filterType.equals("split")) {
			result = splitToArray(paramValue, paramFilterModel.getSplit(), paramFilterModel.getDataType());
		} else if (filterType.equals("date-format")) {
			result = dateFormat(paramValue, paramFilterModel.getFormat());
		} else if (filterType.equals("neq")) {
			result = filterNotEquals(paramValue, paramFilterModel.getValues());
		} else if (filterType.equals("to-date")) {
			if (paramValue.getClass().isArray()) {
				Object[] arrays = CollectionUtil.convertArray(paramValue);
				for (int i = 0, n = arrays.length; i < n; i++) {
					arrays[i] = toDate(arrays[i], paramFilterModel);
				}
				result = arrays;
			} else if (paramValue instanceof List) {
				List valueList = (List) paramValue;
				for (int i = 0, n = valueList.size(); i < n; i++) {
					valueList.set(i, toDate(valueList.get(i), paramFilterModel));
				}
				result = valueList;
			} else {
				result = toDate(paramValue, paramFilterModel);
			}
		} else if (filterType.equals("to-number")) {
			if (paramValue.getClass().isArray()) {
				Object[] arrays = CollectionUtil.convertArray(paramValue);
				for (int i = 0, n = arrays.length; i < n; i++) {
					arrays[i] = toNumber(arrays[i], paramFilterModel.getDataType());
				}
				result = arrays;
			} else if (paramValue instanceof List) {
				List valueList = (List) paramValue;
				for (int i = 0, n = valueList.size(); i < n; i++) {
					valueList.set(i, toNumber(valueList.get(i), paramFilterModel.getDataType()));
				}
				result = valueList;
			} else {
				result = toNumber(paramValue, paramFilterModel.getDataType());
			}
		} else if (filterType.equals("to-array")) {
			result = toArray(paramValue, paramFilterModel.getDataType());
		} else if (filterType.equals("l-like")) {
			result = like(paramValue, true);
		} else if (filterType.equals("r-like")) {
			result = like(paramValue, false);
		} // 增加将数组条件组合成in () 查询条件参数'x1','x2'的形式 ，add 2019-1-4
		else if (filterType.equals("to-in-arg")) {
			if (paramValue instanceof CharSequence) {
				String inArg = paramValue.toString();
				if (!paramFilterModel.isSingleQuote()) {
					result = inArg;
				} else if (inArg.startsWith("'") && inArg.endsWith("'")) {
					result = inArg;
				} else {
					String[] args = inArg.split("\\,");
					StringBuilder inStr = new StringBuilder();
					String tmp;
					int cnt = 0;
					for (String arg : args) {
						tmp = arg.trim();
						if (!tmp.equals("")) {
							if (cnt > 0) {
								inStr.append(",");
							}
							if (!tmp.startsWith("'")) {
								inStr.append("'");
							}
							inStr.append(tmp);
							if (!tmp.endsWith("'")) {
								inStr.append("'");
							}
							cnt++;
						}
					}
					result = inStr.toString();
				}
			} else {
				try {
					result = SqlUtil.combineQueryInStr(paramValue, null, null, paramFilterModel.isSingleQuote());
				} catch (Exception e) {
					logger.error("sql 参数过滤转换过程:将数组转成in (:params) 形式的条件值过程错误:{}", e.getMessage());
				}
			}
		} else if (filterType.equals("custom-handler")) {
			if (sqlToyContext.getCustomFilterHandler() == null) {
				throw new RuntimeException("sql中filter使用了custom-handler类型,但spring.sqltoy.customFilterHandler未定义具体实现类!");
			}
			result = sqlToyContext.getCustomFilterHandler().process(paramValue, paramFilterModel.getType());
		} else {
			logger.warn("sql中filters定义的filterType={} 目前没有对应的实现!", filterType);
		}
		return result;
	}

	/**
	 * @todo 对参数进行左边或右补%符号,便于like处理,sqltoy在不做处理情况下会默认左右都补%符合,单独一边补%则可以保留索引
	 * @param paramValue
	 * @param isLeft
	 * @return
	 */
	private static String like(Object paramValue, boolean isLeft) {
		if (StringUtil.isBlank(paramValue)) {
			return null;
		}
		if (isLeft) {
			return "%".concat(paramValue.toString());
		}
		return paramValue.toString().concat("%");
	}

	/**
	 * @todo 进行字符串替换
	 * @param paramValue
	 * @param regex
	 * @param value
	 * @param isFirst
	 * @return
	 */
	private static Object replace(Object paramValue, String regex, Object valueVar, boolean isFirst) {
		if (paramValue == null || regex == null || valueVar == null) {
			return null;
		}
		String value = valueVar.toString();
		if (paramValue instanceof String) {
			if (isFirst) {
				return paramValue.toString().replaceFirst(regex, value);
			}
			return paramValue.toString().replaceAll(regex, value);
		}
		return paramValue;
	}

	/**
	 * @todo 日期格式化
	 * @param paramValue
	 * @param format
	 * @return
	 */
	private static Object dateFormat(Object paramValue, String format) {
		if (paramValue == null) {
			return null;
		}
		if (format == null) {
			return paramValue;
		}
		Object result;
		if (paramValue.getClass().isArray()) {
			Object[] arrays = CollectionUtil.convertArray(paramValue);
			for (int i = 0, n = arrays.length; i < n; i++) {
				arrays[i] = DateUtil.formatDate(arrays[i], format);
			}
			result = arrays;
		} else if (paramValue instanceof List) {
			List valueList = (List) paramValue;
			for (int i = 0, n = valueList.size(); i < n; i++) {
				valueList.set(i, DateUtil.formatDate(valueList.get(i), format));
			}
			result = valueList;
		} else {
			result = DateUtil.formatDate(paramValue, format);
		}
		return result;
	}

	/**
	 * @todo 切割字符串变成数组
	 * @param paramValue
	 * @param splitSign
	 * @param dataType
	 * @return
	 */
	private static Object[] splitToArray(Object paramValue, String splitSign, String dataType) {
		if (paramValue == null) {
			return null;
		}
		Object[] result = null;
		// 原本就是数组
		if (paramValue.getClass().isArray() || paramValue instanceof Collection) {
			result = CollectionUtil.convertArray(paramValue);
			if (dataType == null) {
				return result;
			}
		} else {
			String[] arrays = null;
			String split = splitSign.trim();
			if (split.equals(",")) {
				arrays = paramValue.toString().split("\\,");
			} else if (split.equals(";")) {
				arrays = paramValue.toString().split("\\;");
			} else if (split.equals(":")) {
				arrays = paramValue.toString().split("\\:");
			} else if (split.equals("")) {
				arrays = paramValue.toString().split("\\s+");
			} else {
				arrays = paramValue.toString().split(splitSign);
			}
			if (dataType == null || dataType.equals("string")) {
				return arrays;
			}
			result = new Object[arrays.length];
			System.arraycopy(arrays, 0, result, 0, arrays.length);
		}
		String value;
		for (int i = 0, n = result.length; i < n; i++) {
			if (null != result[i]) {
				value = result[i].toString();
				if (dataType.equals("integer") || dataType.equals("int")) {
					result[i] = Integer.valueOf(value);
				} else if (dataType.equals("long")) {
					result[i] = Long.valueOf(value);
				} else if (dataType.equals("float")) {
					result[i] = Float.valueOf(value);
				} else if (dataType.equals("double")) {
					result[i] = Double.valueOf(value);
				} else if (dataType.equals("decimal") || dataType.equals("number")) {
					result[i] = new BigDecimal(value);
				} else if (dataType.equals("date")) {
					result[i] = DateUtil.parseString(value);
				} else if (dataType.equals("biginteger")) {
					result[i] = new BigInteger(value);
				}
			}
		}
		return result;
	}

	/**
	 * @todo 转换数据为数组类型
	 * @param paramValue
	 * @param dataType
	 * @return
	 */
	private static Object[] toArray(Object paramValue, String dataType) {
		if (paramValue == null) {
			return null;
		}
		Object[] result = CollectionUtil.convertArray(paramValue);
		if (dataType == null) {
			return result;
		}
		String value;
		for (int i = 0, n = result.length; i < n; i++) {
			if (result[i] != null) {
				value = result[i].toString();
				if (dataType.equals("integer") || dataType.equals("int")) {
					result[i] = Integer.valueOf(value);
				} else if (dataType.equals("long")) {
					result[i] = Long.valueOf(value);
				} else if (dataType.equals("float")) {
					result[i] = Float.valueOf(value);
				} else if (dataType.equals("double")) {
					result[i] = Double.valueOf(value);
				} else if (dataType.equals("decimal") || dataType.equals("number")) {
					result[i] = new BigDecimal(value);
				} else if (dataType.equals("string")) {
					result[i] = value;
				} else if (dataType.equals("biginteger")) {
					result[i] = new BigInteger(value);
				}
			}
		}
		return result;
	}

	/**
	 * @todo 将sql的参数值类型转换为number(页面有时会以字符串进行传输)
	 * @param paramValue
	 * @param dataType
	 * @return
	 */
	private static Object toNumber(Object paramValue, String dataType) {
		Object result;
		BigDecimal value = new BigDecimal(paramValue.toString().replaceAll(",", ""));
		if (dataType == null) {
			result = value;
		} else {
			if (dataType.equals("integer") || dataType.equals("int")) {
				result = Integer.valueOf(value.intValue());
			} else if (dataType.equals("long")) {
				result = Long.valueOf(value.longValue());
			} else if (dataType.equals("float")) {
				result = Float.valueOf(value.floatValue());
			} else if (dataType.equals("double")) {
				result = Double.valueOf(value.doubleValue());
			} else if (dataType.equals("biginteger")) {
				result = value.toBigInteger();
			} else {
				result = value;
			}
		}
		return result;
	}

	/**
	 * @todo 将sql的参数值类型转换为日期类型(页面有时会以字符串进行传输)
	 * @param paramValue
	 * @param paramFilterModel
	 * @return
	 */
	private static Object toDate(Object paramValue, ParamFilterModel paramFilterModel) {
		Object result;
		String format = (paramFilterModel.getFormat() == null) ? "" : paramFilterModel.getFormat();
		String fmtStyle = format.toLowerCase();
		String realFmt = DAY_FORMAT;
		// 解析时已经转小写
		String type = paramFilterModel.getType();
		if (StringUtil.isBlank(type)) {
			// 默认为日期格式
			type = "localdate";
			if (fmtStyle.contains("hhmmss") || fmtStyle.contains("hh:mm:ss")) {
				if (fmtStyle.equals("hhmmss") || fmtStyle.equals("hh:mm:ss")) {
					type = "localtime";
				} else {
					type = "localdatetime";
				}
			}
		}
		// 取当前月份的第一天
		if (fmtStyle.equals("first_of_month")) {
			result = DateUtil.firstDayOfMonth(paramValue);
		} // 年的第一天
		else if (fmtStyle.equals("first_of_year")) {
			result = DateUtil.getYear(paramValue) + "-01-01";
		} // 取当前月份的最后一天
		else if (fmtStyle.equals("last_of_month")) {
			result = DateUtil.lastDayOfMonth(paramValue);
		} // 年的最后一天
		else if (fmtStyle.equals("last_of_year")) {
			result = DateUtil.getYear(paramValue) + "-12-31";
		} // 取指定日期的星期一的日期
		else if (fmtStyle.equals("first_of_week")) {
			Calendar ca = Calendar.getInstance();
			ca.setTime(DateUtil.parse(paramValue, DAY_FORMAT));
			ca.add(Calendar.DAY_OF_WEEK, -ca.get(Calendar.DAY_OF_WEEK) + 2);
			result = ca.getTime();
		} // 取指定日期的星期天的日期
		else if (fmtStyle.equals("last_of_week")) {
			Calendar ca = Calendar.getInstance();
			ca.setTime(DateUtil.parse(paramValue, DAY_FORMAT));
			ca.add(Calendar.DAY_OF_WEEK, -ca.get(Calendar.DAY_OF_WEEK) + 8);
			result = ca.getTime();
		} else {
			result = DateUtil.convertDateObject(paramValue);
			if (StringUtil.isNotBlank(format)) {
				realFmt = format;
			} else {
				realFmt = null;
			}
		}
		// 存在日期加减
		if (paramFilterModel.getIncrementTime() != 0) {
			switch (paramFilterModel.getTimeUnit()) {
			// 天优先
			case DAYS: {
				result = DateUtil.addDay(result, paramFilterModel.getIncrementTime());
				break;
			}
			case SECONDS: {
				result = DateUtil.addSecond(result, paramFilterModel.getIncrementTime());
				break;
			}
			case MILLISECONDS: {
				result = DateUtil.addMilliSecond(result, paramFilterModel.getIncrementTime().longValue());
				break;
			}
			case MINUTES: {
				result = DateUtil.addSecond(result, 60 * paramFilterModel.getIncrementTime());
				break;
			}
			case HOURS: {
				result = DateUtil.addSecond(result, 3600 * paramFilterModel.getIncrementTime());
				break;
			}
			case MONTHS: {
				result = DateUtil.addMonth(result, paramFilterModel.getIncrementTime().intValue());
				break;
			}
			case YEARS: {
				result = DateUtil.addYear(result, paramFilterModel.getIncrementTime().intValue());
				break;
			}
			default: {
				result = DateUtil.addDay(result, paramFilterModel.getIncrementTime());
				break;
			}
			}
		}
		if (realFmt != null) {
			result = DateUtil.parse(result, realFmt);
		}
		if (type.equals("localdate")) {
			return DateUtil.asLocalDate((Date) result);
		}
		if (type.equals("localdatetime")) {
			return DateUtil.asLocalDateTime((Date) result);
		}
		if (type.equals("timestamp")) {
			return java.sql.Timestamp.valueOf(DateUtil.asLocalDateTime((Date) result));
		}
		if (type.equals("localtime")) {
			return DateUtil.asLocalTime((Date) result);
		}
		if (type.equals("time")) {
			return java.sql.Time.valueOf(DateUtil.asLocalTime((Date) result));
		}
		return result;
	}

	/**
	 * @todo 转换sql参数,将对象数组中的值与给定的参照数值比较， 如果相等则置数组中的值为null
	 * @param params
	 * @param contrasts
	 * @return
	 */
	private static Object filterEquals(Object param, Object[] contrasts) {
		if (null == param || contrasts == null || contrasts.length == 0) {
			return null;
		}
		// 条件参数是数组，则等价于in 处理,即对比值在条件值数组中，就表示成立，将条件值转为null
		// 这个属于极端少量的场景
		if (param.getClass().isArray() && contrasts.length == 1) {
			Object[] ary = CollectionUtil.convertArray(param);
			String contrast = contrasts[0].toString();
			for (Object var : ary) {
				if (var != null && var.toString().equals(contrast)) {
					return null;
				}
			}
			return param;
		}
		int type = 0;
		if (param instanceof Date || param instanceof LocalDate || param instanceof LocalTime
				|| param instanceof LocalDateTime) {
			type = 1;
		} else if (param instanceof Number) {
			type = 2;
		}

		// 只要有一个对比值相等表示成立，返回null
		String contrast;
		for (Object tmp : contrasts) {
			contrast = tmp.toString();
			if (type == 1) {
				if (param instanceof LocalTime) {
					if (((LocalTime) param).compareTo(LocalTime.parse(contrast)) == 0) {
						return null;
					}
				} else {
					Date compareDate = contrast.toLowerCase().equals("sysdate")
							? DateUtil.parse(DateUtil.getNowTime(), DAY_FORMAT)
							: DateUtil.convertDateObject(contrast);
					if (compareDate != null && DateUtil.convertDateObject(param).compareTo(compareDate) == 0) {
						return null;
					}
				}
			} else if (type == 2) {
				if (NumberUtil.isNumber(contrast)
						&& (new BigDecimal(param.toString()).compareTo(new BigDecimal(contrast)) == 0)) {
					return null;
				}
			} else if (param.toString().compareTo(contrast) == 0) {
				return null;
			}
		}
		return param;
	}

	/**
	 * @todo 转换sql参数,将对象数组中的值与给定的参照数值比较， 如果不相等则置数组中的值为null
	 * @param param
	 * @param contrasts
	 * @return
	 */
	private static Object filterNotEquals(Object param, Object[] contrasts) {
		if (null == param) {
			return null;
		}
		if (contrasts == null || contrasts.length == 0) {
			return param;
		}
		// 条件参数是数组，则等价于做not in 处理,即对比值不在条件值数组中，就表示成立，将条件值转为null
		// 这个属于极端少量的场景
		if (param.getClass().isArray() && contrasts.length == 1) {
			Object[] ary = CollectionUtil.convertArray(param);
			String contrast = (contrasts[0] == null) ? null : contrasts[0].toString();
			for (Object var : ary) {
				// 相等则表示存在，not equals则不成立
				if (var != null && var.toString().equals(contrast)) {
					return param;
				}
			}
			return null;
		}
		int type = 0;
		if (param instanceof Date || param instanceof LocalDate || param instanceof LocalTime
				|| param instanceof LocalDateTime) {
			type = 1;
		} else if (param instanceof Number) {
			type = 2;
		}
		// 只要有一个对比值相等表示不成立，返回参数本身的值
		String contrast;
		for (Object tmp : contrasts) {
			contrast = (tmp == null) ? null : tmp.toString();
			if (StringUtil.isBlank(contrast)) {
				return param;
			}
			if (type == 1) {
				if (param instanceof LocalTime) {
					if (((LocalTime) param).compareTo(LocalTime.parse(contrast)) == 0) {
						return param;
					}
				} else {
					Date compareDate = contrast.equalsIgnoreCase("sysdate")
							? DateUtil.parse(DateUtil.getNowTime(), DAY_FORMAT)
							: DateUtil.convertDateObject(contrast);
					if (DateUtil.convertDateObject(param).compareTo(compareDate) == 0) {
						return param;
					}
				}
			} else if (type == 2) {
				// 非数字或相等
				if (!NumberUtil.isNumber(contrast)
						|| (new BigDecimal(param.toString()).compareTo(new BigDecimal(contrast)) == 0)) {
					return param;
				}
			} else if (param.toString().compareTo(contrast) == 0) {
				return param;
			}
		}
		return null;
	}

	/**
	 * @todo 过滤参数值小于指定值，并返回null
	 * @param param
	 * @param contrastParam
	 * @return
	 */
	private static Object filterLess(Object param, Object contrastParam) {
		if (null == param) {
			return null;
		}
		String contrast = contrastParam.toString();
		if (param instanceof Date || param instanceof LocalDate || param instanceof LocalDateTime) {
			Date compareDate;
			if (contrast.toLowerCase().equals("sysdate")) {
				compareDate = DateUtil.parse(DateUtil.getNowTime(), DAY_FORMAT);
			} else {
				compareDate = DateUtil.convertDateObject(contrast);
			}
			if (DateUtil.convertDateObject(param).before(compareDate)) {
				return null;
			}
		} else if (param instanceof LocalTime) {
			if (((LocalTime) param).isBefore(LocalTime.parse(contrast))) {
				return null;
			}
		} else if (param instanceof Number) {
			if (new BigDecimal(param.toString()).compareTo(new BigDecimal(contrast)) < 0) {
				return null;
			}
		} else if (param.toString().compareTo(contrast) < 0) {
			return null;
		}
		return param;
	}

	/**
	 * @todo 过滤参数值小于等于指定值，并返回null
	 * @param param
	 * @param contrastParam
	 * @return
	 */
	private static Object filterLessEquals(Object param, Object contrastParam) {
		if (null == param) {
			return null;
		}
		String contrast = contrastParam.toString();
		if (param instanceof Date || param instanceof LocalDate || param instanceof LocalDateTime) {
			Date compareDate;
			if (contrast.toLowerCase().equals("sysdate")) {
				compareDate = DateUtil.parse(DateUtil.getNowTime(), DAY_FORMAT);
			} else {
				compareDate = DateUtil.convertDateObject(contrast);
			}
			if (DateUtil.convertDateObject(param).compareTo(compareDate) <= 0) {
				return null;
			}
		} else if (param instanceof LocalTime) {
			if (((LocalTime) param).compareTo(LocalTime.parse(contrast)) <= 0) {
				return null;
			}
		} else if (param instanceof Number) {
			if (new BigDecimal(param.toString()).compareTo(new BigDecimal(contrast)) <= 0) {
				return null;
			}
		} else if (param.toString().compareTo(contrast) <= 0) {
			return null;
		}
		return param;
	}

	/**
	 * @todo 过滤大于指定参照数据值,否则查询条件为null
	 * @param param
	 * @param contrastParam
	 * @return
	 */
	private static Object filterMore(Object param, Object contrastParam) {
		if (null == param) {
			return null;
		}
		String contrast = contrastParam.toString();
		if (param instanceof Date || param instanceof LocalDate || param instanceof LocalDateTime) {
			Date compareDate;
			if (contrast.toLowerCase().equals("sysdate")) {
				compareDate = DateUtil.parse(DateUtil.getNowTime(), DAY_FORMAT);
			} else {
				compareDate = DateUtil.convertDateObject(contrast);
			}
			if (DateUtil.convertDateObject(param).compareTo(compareDate) > 0) {
				return null;
			}
		} else if (param instanceof LocalTime) {
			if (((LocalTime) param).compareTo(LocalTime.parse(contrast)) > 0) {
				return null;
			}
		} else if (param instanceof Number) {
			if (new BigDecimal(param.toString()).compareTo(new BigDecimal(contrast)) > 0) {
				return null;
			}
		} else if (param.toString().compareTo(contrast) > 0) {
			return null;
		}
		return param;
	}

	/**
	 * @todo 过滤大于等于指定参照数据值,否则查询条件为null
	 * @param param
	 * @param contrastParam
	 * @return
	 */
	private static Object filterMoreEquals(Object param, Object contrastParam) {
		if (null == param) {
			return null;
		}
		String contrast = contrastParam.toString();
		if (param instanceof Date || param instanceof LocalDate || param instanceof LocalDateTime) {
			Date compareDate;
			if (contrast.toLowerCase().equals("sysdate")) {
				compareDate = DateUtil.parse(DateUtil.getNowTime(), DAY_FORMAT);
			} else {
				compareDate = DateUtil.convertDateObject(contrast);
			}
			if (DateUtil.convertDateObject(param).compareTo(compareDate) >= 0) {
				return null;
			}
		} else if (param instanceof LocalTime) {
			if (((LocalTime) param).compareTo(LocalTime.parse(contrast)) >= 0) {
				return null;
			}
		} else if (param instanceof Number) {
			if (new BigDecimal(param.toString()).compareTo(new BigDecimal(contrast)) >= 0) {
				return null;
			}
		} else if (param.toString().compareTo(contrast) >= 0) {
			return null;
		}
		return param;
	}

	/**
	 * @todo 参数大于等于并小于等于给定的数据范围时表示条件无效，自动置参数值为null
	 * @param param
	 * @param beginValue
	 * @param endValue
	 * @return
	 */
	private static Object filterBetween(Object param, Object beginValue, Object endValue) {
		if (null == param) {
			return null;
		}
		String beginContrast = beginValue.toString();
		String endContrast = endValue.toString();
		if (param instanceof Date || param instanceof LocalDate || param instanceof LocalDateTime) {
			Date var = DateUtil.convertDateObject(param);
			if (var.compareTo(DateUtil.convertDateObject(beginContrast)) >= 0
					&& var.compareTo(DateUtil.convertDateObject(endContrast)) <= 0) {
				return null;
			}
		} else if (param instanceof LocalTime) {
			if (((LocalTime) param).compareTo(LocalTime.parse(beginContrast)) >= 0
					&& ((LocalTime) param).compareTo(LocalTime.parse(endContrast)) <= 0) {
				return null;
			}
		} else if (param instanceof Number) {
			if ((new BigDecimal(param.toString()).compareTo(new BigDecimal(beginContrast)) >= 0)
					&& (new BigDecimal(param.toString()).compareTo(new BigDecimal(endContrast)) <= 0)) {
				return null;
			}
		} else if (param.toString().compareTo(beginContrast) >= 0 && param.toString().compareTo(endContrast) <= 0) {
			return null;
		}
		return param;
	}

	/**
	 * @TODO 整合sql中定义的filter和代码中自定义的filters
	 * @param filters
	 * @param extFilters
	 * @return
	 */
	public static List<ParamFilterModel> combineFilters(List<ParamFilterModel> filters, List<ParamsFilter> extFilters) {
		if (extFilters == null || extFilters.isEmpty()) {
			return filters;
		}
		List<ParamFilterModel> result = new ArrayList<ParamFilterModel>();
		if (filters != null && !filters.isEmpty()) {
			result.addAll(filters);
		}
		for (ParamsFilter filter : extFilters) {
			ParamFilterModel paramFilter = new ParamFilterModel();
			paramFilter.setFilterType(filter.getType());
			paramFilter.setParams(filter.getParams());
			if (filter.getParams().length == 1) {
				paramFilter.setParam(filter.getParams()[0]);
			}
			if (filter.getExcludes() != null) {
				for (String s : filter.getExcludes()) {
					paramFilter.addExclude(s);
				}
			}
			paramFilter.setFormat(filter.getDateType());
			paramFilter.setValues(filter.getValue());
			// 加减天数
			paramFilter.setIncrementTime(Double.valueOf(filter.getIncrease()));
			paramFilter.setTimeUnit(filter.getTimeUnit());
			result.add(paramFilter);
		}
		return result;
	}
}
