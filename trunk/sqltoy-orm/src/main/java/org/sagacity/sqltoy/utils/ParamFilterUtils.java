package org.sagacity.sqltoy.utils;

import java.math.BigDecimal;
import java.math.BigInteger;
import java.sql.Time;
import java.sql.Timestamp;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.util.ArrayList;
import java.util.Calendar;
import java.util.Collection;
import java.util.Date;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.regex.Matcher;

import org.sagacity.sqltoy.SqlToyContext;
import org.sagacity.sqltoy.config.model.CacheFilterModel;
import org.sagacity.sqltoy.config.model.ParamFilterModel;
import org.sagacity.sqltoy.model.DataAuthFilterConfig;
import org.sagacity.sqltoy.model.ParamsFilter;
import org.sagacity.sqltoy.plugins.IUnifyFieldsHandler;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * @project sagacity-sqltoy
 * @description sql查询参数过滤
 * @author zhongxuchen
 * @version v1.0,Date:2013-3-23
 * @modify Date:2020-7-15 {增加l-like,r-like为参数单边补充%从而不破坏索引,默认是两边}
 * @modify Date:2023-4-18 {增加to-string}
 * @modify Date:2023-05-01 {优化cache-arg,修复priorMatchEqual存在的bug}
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
			if (filterParams.length == 1 && "*".equals(filterParams[0])) {
				filterParams = paramsName;
			}
			// 排他性参数(当某些参数值都不为null,则设置其他参数值为null)
			if ("exclusive".equals(filterType) && paramFilterModel.getUpdateParams() != null) {
				filterExclusive(paramIndexMap, paramFilterModel, paramValues);
			} // 缓存中提取精准查询参数作为sql查询条件值
			else if ("cache-arg".equals(filterType)) {
				filterCache(sqlToyContext, paramIndexMap, paramFilterModel, paramValues);
			} else if ("clone".equals(filterType)) {
				filterClone(paramIndexMap, paramFilterModel, paramValues);
			}
			// 决定性参数不为null时即条件成立时，需要保留的参数(其他的参数全部设置为null)
			else if ("primary".equals(filterType)) {
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
							paramValues[index] = filterSingleParam(sqlToyContext, paramValues, paramValues[index],
									paramFilterModel, paramIndexMap);
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
			// 需要转化的值,将paramValue统一转化为数组
			List<String> paramValueAry = new ArrayList<String>();
			if (index >= 0 && paramValues[index] != null) {
				if (paramValues[index] instanceof Collection) {
					Object[] tmp = ((Collection) paramValues[index]).toArray();
					for (Object obj : tmp) {
						paramValueAry.add((obj == null) ? null : obj.toString().trim());
					}
				} else if (paramValues[index] instanceof Object[]) {
					Object[] tmp = (Object[]) paramValues[index];
					for (Object obj : tmp) {
						paramValueAry.add((obj == null) ? null : obj.toString().trim());
					}
				} else {
					paramValueAry.add(paramValues[index].toString().trim());
				}
			}
			if (paramValueAry.isEmpty()) {
				return;
			}
			// 将传递匹配条件转小写
			List<String> matchLowAry = new ArrayList<String>();
			for (String str : paramValueAry) {
				matchLowAry.add(str.toLowerCase());
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
			IUnifyFieldsHandler unifyHandler = sqlToyContext.getUnifyFieldsHandler();
			CacheFilterModel[] cacheFilters = paramFilterModel.getCacheFilters();
			CacheFilterModel cacheFilter;
			// 是否存在对缓存进行条件过滤，如过滤缓存中的状态，取状态为:1、2、3的缓存值
			boolean hasFilter = (cacheFilters == null) ? false : true;
			List<Map<String, String>> filterValues = new ArrayList<Map<String, String>>();
			if (hasFilter) {
				Integer cacheValueIndex;
				Object compareValue;
				for (int i = 0; i < cacheFilters.length; i++) {
					cacheFilter = cacheFilters[i];
					cacheValueIndex = paramIndexMap.get(cacheFilter.getCompareParam().toLowerCase());
					compareValue = cacheFilter.getCompareParam();
					// 是参数名称，提取对应值
					if (cacheValueIndex != null) {
						compareValue = paramValues[cacheValueIndex.intValue()];
					} else if (unifyHandler != null && unifyHandler.dataAuthFilters() != null) {
						// 通过统一传参，获取数据权限中的数据，如租户、授权机构等
						DataAuthFilterConfig dataAuthConfig = unifyHandler.dataAuthFilters()
								.get(cacheFilter.getCompareParam());
						if (dataAuthConfig != null && dataAuthConfig.getValues() != null) {
							compareValue = dataAuthConfig.getValues();
						}
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
			// 对比的缓存列(缓存数据以数组形式存储)
			int[] matchIndexes = paramFilterModel.getCacheMappingIndexes();
			// 最大允许匹配数量,缓存匹配一般用于in (?,?)形式的查询,in 参数有数量限制
			int maxLimit = paramFilterModel.getCacheMappingMax();
			// 匹配的缓存key结果集合
			Set<Object> matchedKeys = new HashSet<Object>();
			int cacheKeyIndex = paramFilterModel.getCacheKeyIndex();
			boolean include = true;
			// 是否优先判断相等
			boolean priorMatchEqual = paramFilterModel.isPriorMatchEqual();
			// 将条件参数值转小写进行统一比较
			String matchStr;
			String[] matchWords;
			// key 值
			Object keyCode;
			Object compareValue;
			// 优先匹配查询参数跟缓存名称等直接相等，精准匹配
			if (priorMatchEqual) {
				String keyLow;
				for (Object[] cacheRow : cacheDataMap.values()) {
					keyCode = cacheRow[cacheKeyIndex];
					include = true;
					// 对缓存进行过滤(比如过滤本人授权访问机构下面的员工或当期状态为生效的员工)
					if (hasFilter) {
						include = doCacheFilter(cacheFilters, cacheRow, filterValues);
					}
					// 过滤条件成立，且当前key没有被匹配过，开始匹配
					if (include) {
						keyLow = keyCode.toString().toLowerCase();
						skipLoop: for (int i = 0; i < paramValueAry.size(); i++) {
							// 从转小写集合中取值，避免每次toLowcase
							matchStr = matchLowAry.get(i);
							// 直接等于key
							if (matchStr.equals(keyLow)) {
								matchedKeys.add(keyCode);
								// 相等的剔除不再参与后续like匹配
								matchLowAry.remove(i);
								paramValueAry.remove(i);
								// 进入下一个key循环
								break;
							}
							// 名称跟比较列的值相同
							for (int matchIndex : matchIndexes) {
								compareValue = cacheRow[matchIndex];
								// 名称相同
								if (compareValue != null && matchStr.equals(compareValue.toString().toLowerCase())) {
									matchedKeys.add(keyCode);
									// 相等的剔除不再参与后续like匹配
									matchLowAry.remove(i);
									paramValueAry.remove(i);
									// 进入下一个key循环
									break skipLoop;
								}
							}
						}
						// 超出阈值、或者全部匹配到相等后跳出
						if (paramValueAry.isEmpty() || matchedKeys.size() >= maxLimit) {
							break;
						}
					}
				}
			}
			// 存在未完全相等的条件参数且匹配数量小于最大匹配量，继续进行like匹配
			if (!matchLowAry.isEmpty() && matchedKeys.size() < maxLimit) {
				// 将匹配参数切割成分词数组
				int likeArgSize = matchLowAry.size();
				List<String[]> paramsMatchWords = new ArrayList<String[]>();
				for (int i = 0; i < likeArgSize; i++) {
					paramsMatchWords.add(matchLowAry.get(i).split("\\s+"));
				}
				for (Object[] cacheRow : cacheDataMap.values()) {
					keyCode = cacheRow[cacheKeyIndex];
					include = true;
					// 已经存在无需再比较
					if (matchedKeys.contains(keyCode)) {
						include = false;
					}
					// 对缓存进行过滤(比如过滤本人授权访问机构下面的员工或当期状态为生效的员工)
					if (hasFilter && include) {
						include = doCacheFilter(cacheFilters, cacheRow, filterValues);
					}
					// 过滤条件成立，且当前key没有被匹配过，开始匹配
					if (include) {
						skipLoop: for (int i = 0; i < likeArgSize; i++) {
							matchWords = paramsMatchWords.get(i);
							for (int matchIndex : matchIndexes) {
								compareValue = cacheRow[matchIndex];
								// 匹配检索,全部转成小写比较
								if (compareValue != null
										&& StringUtil.like(compareValue.toString().toLowerCase(), matchWords)) {
									matchedKeys.add(keyCode);
									break skipLoop;
								}
							}
						}
						// 超出阈值跳出
						if (matchedKeys.size() >= maxLimit) {
							break;
						}
					}
				}
			}
			Object[] realMatched = null;
			// 没有通过缓存匹配到具体key，代入默认值
			if (matchedKeys.isEmpty()) {
				if (paramFilterModel.getCacheNotMatchedValue() != null) {
					realMatched = new Object[] { paramFilterModel.getCacheNotMatchedValue() };
				} else if (paramFilterModel.isCacheNotMatchedReturnSelf()) {
					realMatched = new String[paramValueAry.size()];
					paramValueAry.toArray(realMatched);
				} else {
					realMatched = new Object[0];
				}
			} else {
				realMatched = new Object[matchedKeys.size()];
				matchedKeys.toArray(realMatched);
			}
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
	 * @TODO 处理CacheArgs 的过滤逻辑,条件成立include=true
	 * @param cacheFilters
	 * @param cacheRow
	 * @param filterValues
	 * @return
	 */
	private static boolean doCacheFilter(CacheFilterModel[] cacheFilters, Object[] cacheRow,
			List<Map<String, String>> filterValues) {
		CacheFilterModel cacheFilter;
		boolean isEqual;
		for (int i = 0; i < cacheFilters.length; i++) {
			cacheFilter = cacheFilters[i];
			// 过滤条件是否相等
			if (cacheRow[cacheFilter.getCacheIndex()] == null) {
				isEqual = false;
			} else {
				isEqual = filterValues.get(i).containsKey(cacheRow[cacheFilter.getCacheIndex()].toString());
			}
			// 条件不成立则过滤掉
			if (("eq".equals(cacheFilter.getCompareType()) && !isEqual)
					|| ("neq".equals(cacheFilter.getCompareType()) && isEqual)) {
				return false;
			}
		}
		return true;
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
			if ("==".equals(compareType)) {
				if (null == paramValue && (null == compareValues || compareValues[0].equals("null"))) {
					isExclusive = true;
				} else if (null != paramValue && null != compareValues) {
					// 返回null表示条件成立
					if (null == filterEquals(paramValue, compareValues)) {
						isExclusive = true;
					}
				}
			} else if (null != paramValue && null != compareValues) {
				Object result = paramValue;
				if (">=".equals(compareType)) {
					result = filterMoreEquals(paramValue, compareValues[0]);
				} else if (">".equals(compareType)) {
					result = filterMore(paramValue, compareValues[0]);
				} else if ("<=".equals(compareType)) {
					result = filterLessEquals(paramValue, compareValues[0]);
				} else if ("<".equals(compareType)) {
					result = filterLess(paramValue, compareValues[0]);
				} else if ("<>".equals(compareType)) {
					result = filterNotEquals(paramValue, compareValues);
				} else if ("between".equals(compareType)) {
					result = filterBetween(paramValue, compareValues[0], compareValues[compareValues.length - 1]);
				} else if ("in".equals(compareType)) {
					result = filterEquals(paramValue, compareValues);
				}
				if (null == result) {
					isExclusive = true;
				}
			}
		}
		// 将排斥的参数设置为null
		if (isExclusive) {
			String updateParam;
			String updateValue = paramFilterModel.getUpdateValue();
			Object updateObj = null;
			boolean quotOtherParam = false;
			// update值引入其他参数的值
			if (paramIndexMap.containsKey(updateValue.toLowerCase())) {
				quotOtherParam = true;
				updateObj = paramValues[paramIndexMap.get(updateValue.toLowerCase())];
			}
			for (int i = 0, n = paramFilterModel.getUpdateParams().length; i < n; i++) {
				updateParam = paramFilterModel.getUpdateParams()[i].toLowerCase();
				index = (paramIndexMap.get(updateParam) == null) ? -1 : paramIndexMap.get(updateParam);
				// 排他性参数中有值为null则排他条件不成立
				if (index != -1) {
					if (quotOtherParam) {
						paramValues[index] = updateObj;
					} else {
						if (null == updateValue) {
							paramValues[index] = null;
						} else if (null == paramValues[index]) {
							paramValues[index] = updateValue;
						} else if (paramValues[index] instanceof LocalDate) {
							paramValues[index] = DateUtil.asLocalDate(parseDateStr(updateValue));
						} else if (paramValues[index] instanceof LocalDateTime) {
							paramValues[index] = DateUtil.asLocalDateTime(parseDateStr(updateValue));
						} else if (paramValues[index] instanceof LocalTime) {
							paramValues[index] = DateUtil.asLocalTime(parseDateStr(updateValue));
						} else if (paramValues[index] instanceof Timestamp) {
							paramValues[index] = DateUtil.getTimestamp(parseDateStr(updateValue));
						} else if (paramValues[index] instanceof Time) {
							paramValues[index] = new Time(parseDateStr(updateValue).getTime());
						} else if (paramValues[index] instanceof Date) {
							paramValues[index] = parseDateStr(updateValue);
						} else if (paramValues[index] instanceof BigDecimal) {
							paramValues[index] = new BigDecimal(updateValue);
						} else if (paramValues[index] instanceof Long) {
							paramValues[index] = new Long(updateValue);
						} else if (paramValues[index] instanceof Integer) {
							paramValues[index] = new Integer(updateValue);
						} else if (paramValues[index] instanceof Double) {
							paramValues[index] = new Double(updateValue);
						} else if (paramValues[index] instanceof Float) {
							paramValues[index] = new Float(updateValue);
						} else if (paramValues[index] instanceof BigInteger) {
							paramValues[index] = new BigInteger(updateValue);
						} else {
							paramValues[index] = updateValue;
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
			} else if (paramValue instanceof Timestamp) {
				cloneValue = ((Timestamp) paramValue).clone();
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
			} else if (paramValue instanceof ArrayList) {
				cloneValue = ((ArrayList) paramValue).clone();
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
	 * @param paramValues
	 * @param paramValue
	 * @param paramFilterModel
	 * @param paramIndexMap
	 * @return
	 */
	private static Object filterSingleParam(SqlToyContext sqlToyContext, Object[] paramValues, Object paramValue,
			ParamFilterModel paramFilterModel, HashMap<String, Integer> paramIndexMap) {
		String filterType = paramFilterModel.getFilterType();
		// null或者非设置default默认值
		if (null == paramValue && !"default".equals(filterType)) {
			return null;
		}
		Object result = paramValue;
		if ("blank".equals(filterType)) {
			result = paramValue;
			if (paramValue instanceof CharSequence) {
				if ("".equals(paramValue.toString().trim())) {
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
		} else if ("default".equals(filterType)) {
			result = filterDefault(paramValues, paramValue, paramFilterModel, paramIndexMap);
		} else if ("eq".equals(filterType)) {
			result = filterEquals(paramValue, paramFilterModel.getValues());
		} else if ("gt".equals(filterType)) {
			result = filterMore(paramValue, paramFilterModel.getValues()[0]);
		} else if ("gte".equals(filterType)) {
			result = filterMoreEquals(paramValue, paramFilterModel.getValues()[0]);
		} else if ("lt".equals(filterType)) {
			result = filterLess(paramValue, paramFilterModel.getValues()[0]);
		} else if ("lte".equals(filterType)) {
			result = filterLessEquals(paramValue, paramFilterModel.getValues()[0]);
		} else if ("between".equals(filterType)) {
			result = filterBetween(paramValue, paramFilterModel.getValues()[0],
					paramFilterModel.getValues()[paramFilterModel.getValues().length - 1]);
		} else if ("replace".equals(filterType)) {
			result = replace(paramValue, paramFilterModel.getRegex(), paramFilterModel.getValues()[0],
					paramFilterModel.isFirst());
		} else if ("split".equals(filterType)) {
			result = splitToArray(paramValue, paramFilterModel.getSplit(), paramFilterModel.getDataType());
		} else if ("date-format".equals(filterType)) {
			result = dateFormat(paramValue, paramFilterModel.getFormat());
		} else if ("neq".equals(filterType)) {
			result = filterNotEquals(paramValue, paramFilterModel.getValues());
		} else if ("to-date".equals(filterType)) {
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
		} else if ("to-number".equals(filterType)) {
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
		} else if ("to-string".equals(filterType)) {
			result = toString(paramValue, paramFilterModel.getAddQuote());
		} else if ("to-array".equals(filterType)) {
			result = toArray(paramValue, paramFilterModel.getDataType());
		} else if ("l-like".equals(filterType)) {
			result = like(paramValue, true);
		} else if ("r-like".equals(filterType)) {
			result = like(paramValue, false);
		} // 增加将数组条件组合成in () 查询条件参数'x1','x2'的形式 ，add 2019-1-4
		else if ("to-in-arg".equals(filterType)) {
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
						if (!"".equals(tmp)) {
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
		} else if ("custom-handler".equals(filterType)) {
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
	 * @TODO 处理默认值
	 * @param paramValues
	 * @param paramValue
	 * @param paramFilterModel
	 * @param paramIndexMap
	 * @return
	 */
	private static Object filterDefault(Object[] paramValues, Object paramValue, ParamFilterModel paramFilterModel,
			HashMap<String, Integer> paramIndexMap) {
		Object[] values = paramFilterModel.getValues();
		// 当前值为null，默认值不为null
		if (null == paramValue && (values != null && values.length > 0 && null != values[0])) {
			String valueString = values[0].toString();
			// 默认值直接指定另外一个参数的值
			if (paramIndexMap.containsKey(valueString.toLowerCase())) {
				return paramValues[paramIndexMap.get(valueString.toLowerCase())];
			}
			if (paramFilterModel.getIsArray()) {
				return splitToArray(valueString, paramFilterModel.getSplit(), paramFilterModel.getDataType());
			}
			return convertType(valueString, paramFilterModel.getDataType());
		} else {
			return paramValue;
		}
	}

	/**
	 * @todo 进行字符串替换
	 * @param paramValue
	 * @param regex
	 * @param valueVar
	 * @param isFirst
	 * @return
	 */
	private static Object replace(Object paramValue, String regex, Object valueVar, boolean isFirst) {
		if (paramValue == null || regex == null || valueVar == null) {
			return null;
		}
		String value = valueVar.toString();
		if (paramValue instanceof String) {
			value = Matcher.quoteReplacement(value);
			if (isFirst) {
				return paramValue.toString().replaceFirst(regex, value);
			}
			return paramValue.toString().replaceAll(regex, value);
		} else if (paramValue instanceof String[]) {
			String[] result = (String[]) paramValue;
			value = Matcher.quoteReplacement(value);
			for (int i = 0; i < result.length; i++) {
				if (isFirst) {
					result[i] = result[i].replaceFirst(regex, value);
				} else {
					result[i] = result[i].replaceAll(regex, value);
				}
			}
			return result;
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
			if (",".equals(split)) {
				arrays = paramValue.toString().split("\\,");
			} else if (";".equals(split)) {
				arrays = paramValue.toString().split("\\;");
			} else if (":".equals(split)) {
				arrays = paramValue.toString().split("\\:");
			} else if ("".equals(split)) {
				arrays = paramValue.toString().split("\\s+");
			} else {
				arrays = paramValue.toString().split(splitSign);
			}
			if (dataType == null || "string".equals(dataType)) {
				return arrays;
			}
			result = new Object[arrays.length];
			System.arraycopy(arrays, 0, result, 0, arrays.length);
		}
		for (int i = 0, n = result.length; i < n; i++) {
			if (null != result[i]) {
				result[i] = convertType(result[i].toString(), dataType);
			}
		}
		return result;
	}

	private static Object convertType(String value, String dataType) {
		if (value == null) {
			return value;
		}
		if ("integer".equals(dataType) || "int".equals(dataType)) {
			return Integer.valueOf(value);
		} else if ("long".equals(dataType)) {
			return Long.valueOf(value);
		} else if ("float".equals(dataType)) {
			return Float.valueOf(value);
		} else if ("double".equals(dataType)) {
			return Double.valueOf(value);
		} else if ("decimal".equals(dataType) || "number".equals(dataType)) {
			return new BigDecimal(value);
		} else if ("localdate".equals(dataType)) {
			return DateUtil.asLocalDate(parseDateStr(value));
		} else if ("localdatetime".equals(dataType)) {
			return DateUtil.asLocalDateTime(parseDateStr(value));
		} else if ("localtime".equals(dataType)) {
			return DateUtil.asLocalTime(parseDateStr(value));
		} else if ("time".equals(dataType)) {
			return new Time(parseDateStr(value).getTime());
		} else if ("timestamp".equals(dataType)) {
			return DateUtil.getTimestamp(parseDateStr(value));
		} else if ("date".equals(dataType)) {
			return parseDateStr(value);
		} else if ("biginteger".equals(dataType)) {
			return new BigInteger(value);
		}
		return value;
	}

	private static Date parseDateStr(String dateStr) {
		if (dateStr.equals("sysdate()") || dateStr.equals("now()")) {
			return DateUtil.getNowTime();
		}
		String[] tmpAry = null;
		boolean isAdd = false;
		// 1:hour;2:day;3:week;4:month;5:year
		int addType = 2;
		if (dateStr.contains("+")) {
			tmpAry = dateStr.split("\\+");
			isAdd = true;
		} // sysdate()-2d形式，排除2023-05-20 纯以数字开头的纯日期
		else if (!StringUtil.matches(dateStr, "^\\d{2,4}") && dateStr.contains("-")) {
			tmpAry = dateStr.split("\\-");
			isAdd = false;
		}
		if (tmpAry != null && tmpAry.length == 2) {
			String addStr = tmpAry[1].trim();
			int addValue = 0;
			// sysdate()-2d 字母结尾
			if (StringUtil.matches(addStr, "[a-z|A-Z]$")) {
				// 最后一位字母
				String addTypeStr = addStr.substring(addStr.length() - 1).toLowerCase();
				if (addTypeStr.equals("h")) {
					addType = 1;
				} else if (addTypeStr.equals("w")) {
					addType = 3;
				} else if (addTypeStr.equals("m")) {
					addType = 4;
				} else if (addTypeStr.equals("y")) {
					addType = 5;
				}
				addValue = Integer.parseInt(addStr.substring(0, addStr.length() - 1));
			} else {
				addValue = Integer.parseInt(addStr);
			}
			if (!isAdd) {
				addValue = 0 - addValue;
			}
			Date starDate;
			String firstString = tmpAry[0].trim().toLowerCase();
			// '2019-12-13' 形式
			if (firstString.startsWith("'") && firstString.endsWith("'")) {
				firstString = firstString.substring(1, firstString.length() - 1);
			}
			if (firstString.equals("sysdate()") || firstString.equals("now()")) {
				starDate = DateUtil.getNowTime();
			} else if (firstString.equals("first_of_month")) {
				starDate = DateUtil.firstDayOfMonth(DateUtil.getNowTime());
			} else if (firstString.equals("first_of_year")) {
				starDate = DateUtil.parse((DateUtil.getYear(DateUtil.getNowTime()) + "-01-01"), "yyyy-MM-dd");
			} else if (firstString.equals("last_of_month")) {
				starDate = DateUtil.lastDayOfMonth(DateUtil.getNowTime());
			} else if (firstString.equals("last_of_year")) {
				starDate = DateUtil.parse((DateUtil.getYear(DateUtil.getNowTime()) + "-12-31"), "yyyy-MM-dd");
			} else if (firstString.equals("first_of_month")) {
				starDate = DateUtil.firstDayOfMonth(DateUtil.getNowTime());
			} else if (firstString.equals("first_of_week")) {
				Calendar ca = Calendar.getInstance();
				ca.setTime(DateUtil.parse(DateUtil.getNowTime(), DAY_FORMAT));
				ca.add(Calendar.DAY_OF_WEEK, -ca.get(Calendar.DAY_OF_WEEK) + 2);
				starDate = ca.getTime();
			} else if (firstString.equals("last_of_week")) {
				Calendar ca = Calendar.getInstance();
				ca.setTime(DateUtil.parse(DateUtil.getNowTime(), DAY_FORMAT));
				ca.add(Calendar.DAY_OF_WEEK, -ca.get(Calendar.DAY_OF_WEEK) + 8);
				starDate = ca.getTime();
			} else {
				starDate = DateUtil.parseString(firstString);
			}
			// 小时
			if (addType == 1) {
				return DateUtil.addSecond(starDate, addValue * 3600);
			}
			// 天
			if (addType == 2) {
				return DateUtil.addDay(starDate, addValue);
			}
			// 周
			if (addType == 3) {
				return DateUtil.addDay(starDate, addValue * 7);
			}
			// 月
			if (addType == 4) {
				return DateUtil.addMonth(starDate, addValue);
			}
			// 年
			if (addType == 5) {
				return DateUtil.addYear(starDate, addValue);
			}
		}
		return DateUtil.parseString(dateStr);
	}

	/**
	 * @todo 转换数据为字符串
	 * @param paramValue
	 * @param addQuote   增加引号的类型:none(不增加)、single(单引号)、double(双引号)
	 * @return
	 */
	private static Object toString(Object paramValue, String addQuote) {
		if (paramValue == null) {
			return null;
		}
		// 数组
		if (paramValue.getClass().isArray()) {
			List<String> result = new ArrayList<String>();
			Object[] arrays = CollectionUtil.convertArray(paramValue);
			for (int i = 0, n = arrays.length; i < n; i++) {
				result.add(dataToString(arrays[i], addQuote));
			}
			String[] resultAry = new String[result.size()];
			result.toArray(resultAry);
			return resultAry;
		} // 集合
		else if (paramValue instanceof Collection) {
			List<String> result = new ArrayList<String>();
			Iterator iter = ((Collection) paramValue).iterator();
			while (iter.hasNext()) {
				result.add(dataToString(iter.next(), addQuote));
			}
			return result;
		} else {
			return dataToString(paramValue, addQuote);
		}
	}

	// for toString方法
	private static String dataToString(Object paramValue, String addQuote) {
		if (paramValue == null) {
			return null;
		}
		String result;
		if (paramValue instanceof BigDecimal) {
			result = ((BigDecimal) paramValue).toPlainString();
		} else if (paramValue instanceof LocalTime) {
			result = DateUtil.formatDate(paramValue, "HH:mm:ss");
		} else if (paramValue instanceof LocalDate) {
			result = DateUtil.formatDate(paramValue, "yyyy-MM-dd");
		} else if ((paramValue instanceof LocalDateTime) || (paramValue instanceof Date)) {
			result = DateUtil.formatDate(paramValue, "yyyy-MM-dd HH:mm:ss");
		} else {
			result = paramValue.toString();
		}
		if (addQuote == null) {
			return result;
		}
		if ("single".equals(addQuote)) {
			// 已经加了单引号不再重复增加
			if (result.startsWith("'") && result.endsWith("'")) {
				return result;
			}
			return "'".concat(result).concat("'");
		}
		if ("double".equals(addQuote)) {
			// 已经加了双引号不再重复增加
			if (result.startsWith("\"") && result.endsWith("\"")) {
				return result;
			}
			return "\"".concat(result).concat("\"");
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
				if ("integer".equals(dataType) || "int".equals(dataType)) {
					result[i] = Integer.valueOf(value);
				} else if ("long".equals(dataType)) {
					result[i] = Long.valueOf(value);
				} else if ("float".equals(dataType)) {
					result[i] = Float.valueOf(value);
				} else if ("double".equals(dataType)) {
					result[i] = Double.valueOf(value);
				} else if ("decimal".equals(dataType) || "number".equals(dataType)) {
					result[i] = new BigDecimal(value);
				} else if ("string".equals(dataType)) {
					result[i] = value;
				} else if ("biginteger".equals(dataType)) {
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
		BigDecimal value = new BigDecimal(paramValue.toString().replace(",", ""));
		if (dataType == null) {
			result = value;
		} else if ("integer".equals(dataType) || "int".equals(dataType)) {
			result = Integer.valueOf(value.intValue());
		} else if ("long".equals(dataType)) {
			result = Long.valueOf(value.longValue());
		} else if ("float".equals(dataType)) {
			result = Float.valueOf(value.floatValue());
		} else if ("double".equals(dataType)) {
			result = Double.valueOf(value.doubleValue());
		} else if ("biginteger".equals(dataType)) {
			result = value.toBigInteger();
		} else {
			result = value;
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
				if ("hhmmss".equals(fmtStyle) || "hh:mm:ss".equals(fmtStyle)) {
					type = "localtime";
				} else {
					type = "localdatetime";
				}
			}
		}
		// 取当前月份的第一天
		if ("first_of_month".equals(fmtStyle)) {
			result = DateUtil.firstDayOfMonth(paramValue);
		} // 年的第一天
		else if ("first_of_year".equals(fmtStyle)) {
			result = DateUtil.getYear(paramValue) + "-01-01";
		} // 取当前月份的最后一天
		else if ("last_of_month".equals(fmtStyle)) {
			result = DateUtil.lastDayOfMonth(paramValue);
		} // 年的最后一天
		else if ("last_of_year".equals(fmtStyle)) {
			result = DateUtil.getYear(paramValue) + "-12-31";
		} // 取指定日期的星期一的日期
		else if ("first_of_week".equals(fmtStyle)) {
			Calendar ca = Calendar.getInstance();
			ca.setTime(DateUtil.parse(paramValue, DAY_FORMAT));
			ca.add(Calendar.DAY_OF_WEEK, -ca.get(Calendar.DAY_OF_WEEK) + 2);
			result = ca.getTime();
		} // 取指定日期的星期天的日期
		else if ("last_of_week".equals(fmtStyle)) {
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
		if ("localdate".equals(type)) {
			return DateUtil.asLocalDate((Date) result);
		}
		if ("localdatetime".equals(type)) {
			return DateUtil.asLocalDateTime((Date) result);
		}
		if ("timestamp".equals(type)) {
			return java.sql.Timestamp.valueOf(DateUtil.asLocalDateTime((Date) result));
		}
		if ("localtime".equals(type)) {
			return DateUtil.asLocalTime((Date) result);
		}
		if ("time".equals(type)) {
			return java.sql.Time.valueOf(DateUtil.asLocalTime((Date) result));
		}
		return result;
	}

	/**
	 * @todo 转换sql参数,将对象数组中的值与给定的参照数值比较， 如果相等则置数组中的值为null
	 * @param param
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
			// 日期
			if (type == 1) {
				// 长度小于6不够成日期、时间类型格式
				if (contrast.length() >= 6) {
					if (param instanceof LocalTime) {
						if (((LocalTime) param).compareTo(LocalTime.parse(contrast)) == 0) {
							return null;
						}
					} else {
						Date compareDate = "sysdate".equals(contrast.toLowerCase())
								? DateUtil.parse(DateUtil.getNowTime(), DAY_FORMAT)
								: DateUtil.convertDateObject(contrast);
						if (compareDate != null && DateUtil.convertDateObject(param).compareTo(compareDate) == 0) {
							return null;
						}
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
				// 长度小于6不够成日期、时间类型格式
				if (contrast.length() >= 6) {
					if (param instanceof LocalTime) {
						if (((LocalTime) param).compareTo(LocalTime.parse(contrast)) == 0) {
							return param;
						}
					} else {
						Date compareDate = "sysdate".equalsIgnoreCase(contrast)
								? DateUtil.parse(DateUtil.getNowTime(), DAY_FORMAT)
								: DateUtil.convertDateObject(contrast);
						if (DateUtil.convertDateObject(param).compareTo(compareDate) == 0) {
							return param;
						}
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
			if ("sysdate".equals(contrast.toLowerCase())) {
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
			if ("sysdate".equals(contrast.toLowerCase())) {
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
			if ("sysdate".equals(contrast.toLowerCase())) {
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
			if ("sysdate".equals(contrast.toLowerCase())) {
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
