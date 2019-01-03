/**
 * 
 */
package org.sagacity.sqltoy.utils;

import java.math.BigDecimal;
import java.util.Collection;
import java.util.Date;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.sagacity.sqltoy.SqlToyContext;
import org.sagacity.sqltoy.config.model.ParamFilterModel;

/**
 * @project sqltoy-orm
 * @description sql查询参数过滤
 * @author zhongxuchen <a href="mailto:zhongxuchen@hotmail.com">联系作者</a>
 * @version id:ParamFilterUtils.java,Revision:v1.0,Date:2013-3-23
 */
@SuppressWarnings({ "rawtypes", "unchecked" })
public class ParamFilterUtils {
	/**
	 * 定义日志
	 */
	protected final static Logger logger = LogManager.getLogger(ParamFilterUtils.class);

	// 默认日期格式
	private static final String DAY_FORMAT = "yyyy-MM-dd";

	/**
	 * @todo 对查询条件参数进行filter过滤加工处理(如:判断是否为null、日期格式转换等等)
	 * @param sqlToyContext
	 * @param paramsName
	 * @param values
	 * @param filters
	 * @return
	 */
	public static Object[] filterValue(SqlToyContext sqlToyContext, String[] paramsName, Object[] values,
			ParamFilterModel[] filters) {
		if (paramsName == null || paramsName.length == 0 || filters == null || filters.length == 0)
			return values;
		HashMap<String, Integer> paramIndexMap = new HashMap<String, Integer>();
		int paramSize = paramsName.length;
		Object[] paramValues = new Object[paramSize];
		for (int i = 0; i < paramSize; i++) {
			paramIndexMap.put(paramsName[i].toLowerCase(), i);
			paramValues[i] = values[i];
		}
		String[] filterParams;
		int index;
		HashMap<String, String> retainMap;
		String filterParam;
		boolean hasPrimary = false;
		for (ParamFilterModel paramFilterModel : filters) {
			filterParams = paramFilterModel.getParams();
			// 通配符表示针对所有参数
			if (filterParams.length == 1 && filterParams[0].equals("*"))
				filterParams = paramsName;
			// 排他性参数(当某些参数值都不为null,则设置其他参数值为null)
			if (paramFilterModel.getFilterType().equals("exclusive") && paramFilterModel.getUpdateParams() != null) {
				filterExclusive(paramIndexMap, paramFilterModel, paramValues);
			} // 缓存中提取精准查询参数作为sql查询条件值
			else if (paramFilterModel.getFilterType().equals("cache-arg")) {
				filterCache(sqlToyContext, paramIndexMap, paramFilterModel, paramValues);
			}
			// 决定性参数不为null时即条件成立时，需要保留的参数(其他的参数全部设置为null)
			else if (paramFilterModel.getFilterType().equals("primary") && !hasPrimary) {
				retainMap = paramFilterModel.getExcludesMap();
				index = (paramIndexMap.get(paramFilterModel.getParam()) == null) ? -1
						: paramIndexMap.get(paramFilterModel.getParam());
				// 决定性参数值不为null
				if (index != -1 && paramValues[index] != null) {
					for (int j = 0; j < paramSize; j++) {
						// 排除自身
						if (j != index && (retainMap == null || !retainMap.containsKey(paramsName[j].toLowerCase()))) {
							paramValues[j] = null;
						}
					}
					hasPrimary = true;
				}
			} else {
				for (int i = 0, n = filterParams.length; i < n; i++) {
					filterParam = filterParams[i].toLowerCase();
					index = (paramIndexMap.get(filterParam) == null) ? -1 : paramIndexMap.get(filterParam);
					if (index != -1 && paramValues[index] != null) {
						if (paramFilterModel.getExcludesMap() == null
								|| !paramFilterModel.getExcludesMap().containsKey(filterParam)) {
							paramValues[index] = filterSingleParam(paramValues[index], paramFilterModel);
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
			int index = (paramIndexMap.get(paramFilterModel.getParam()) == null) ? -1
					: paramIndexMap.get(paramFilterModel.getParam());
			if (index == -1)
				return;
			String aliasName = paramFilterModel.getAliasName();
			if (StringUtil.isBlank(aliasName))
				aliasName = paramFilterModel.getParam();
			if (!paramIndexMap.containsKey(aliasName)) {
				logger.warn("cache-arg 从缓存:{}取实际条件值别名:{}配置错误,其不在于实际sql语句中!", paramFilterModel.getCacheName(),
						aliasName);
				return;
			}
			HashMap<String, Object[]> cacheDataMap = sqlToyContext.getTranslateManager().getCacheData(sqlToyContext,
					paramFilterModel.getCacheName(), paramFilterModel.getCacheType());
			if (cacheDataMap == null || cacheDataMap.isEmpty()) {
				logger.warn("缓存:{} 可能不存在,在通过缓存获取查询条件key值时异常,请检查!", paramFilterModel.getCacheName());
				return;
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
		String filterParam = paramFilterModel.getParam();
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
					if (null == filterEquals(paramValue, compareValues))
						isExclusive = true;
				}
			} else {
				if (null != paramValue && null != compareValues) {
					Object result = paramValue;
					if (compareType.equals(">="))
						result = filterMoreEquals(paramValue, compareValues[0]);
					else if (compareType.equals(">"))
						result = filterMore(paramValue, compareValues[0]);
					else if (compareType.equals("<="))
						result = filterLessEquals(paramValue, compareValues[0]);
					else if (compareType.equals("<"))
						result = filterLess(paramValue, compareValues[0]);
					else if (compareType.equals("<>"))
						result = filterNotEquals(paramValue, compareValues);
					else if (compareType.equals("between")) {
						result = filterBetween(paramValue, compareValues[0], compareValues[compareValues.length - 1]);
					} else if (compareType.equals("in"))
						result = filterEquals(paramValue, compareValues);
					if (null == result)
						isExclusive = true;
				}
			}
		}
		// 将排斥的参数设置为null
		if (isExclusive) {
			String updateParam;
			String updateValue = paramFilterModel.getUpdateValue();
			for (int i = 0, n = paramFilterModel.getUpdateParams().length; i < n; i++) {
				updateParam = paramFilterModel.getUpdateParams()[i];
				index = (paramIndexMap.get(updateParam) == null) ? -1 : paramIndexMap.get(updateParam);
				// 排他性参数中有值为null则排他条件不成立
				if (index != -1) {
					if (null == updateValue)
						paramValues[index] = null;
					else {
						if (null == paramValues[index])
							paramValues[index] = updateValue;
						else {
							if (paramValues[index] instanceof Date)
								paramValues[index] = DateUtil.convertDateObject(updateValue);
							else if (paramValues[index] instanceof Number)
								paramValues[index] = new BigDecimal(updateValue);
							else
								paramValues[index] = updateValue;
						}
					}
				}
			}
		}
	}

	/**
	 * @todo 过滤加工单个参数的值
	 * @param paramValue
	 * @param paramFilterModel
	 * @return
	 */
	private static Object filterSingleParam(Object paramValue, ParamFilterModel paramFilterModel) {
		if (null == paramValue)
			return null;
		Object result = paramValue;
		String filterType = paramFilterModel.getFilterType();
		if (filterType.equals("blank")) {
			result = paramValue;
			if (paramValue instanceof String) {
				if (paramValue.toString().trim().equals(""))
					result = null;
			} else if (paramValue instanceof Collection) {
				if (((Collection) paramValue).isEmpty())
					result = null;
			} else if (paramValue instanceof Map) {
				if (((Map) paramValue).isEmpty())
					result = null;
			} else if (paramValue.getClass().isArray()) {
				if (CollectionUtil.convertArray(paramValue).length == 0)
					result = null;
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
		} // 增加将数组条件组合成in () 查询条件参数'x1','x2'的形式 ，add 2019-1-4
		else if (filterType.equals("to-in-arg")) {
			if (paramValue instanceof CharSequence) {
				result = paramValue.toString();
			} else {
				try {
					result = SqlUtil.combineQueryInStr(paramValue, null, null, true);
				} catch (Exception e) {
					logger.error("sql 参数过滤转换过程:将数组转成in (:params) 形式的条件值过程错误:{}", e.getMessage());
				}
			}
		}
		return result;
	}

	/**
	 * @todo 进行字符串替换
	 * @param paramValue
	 * @param regex
	 * @param value
	 * @param isFirst
	 * @return
	 */
	private static Object replace(Object paramValue, String regex, String value, boolean isFirst) {
		if (paramValue == null || regex == null || value == null)
			return null;
		if (paramValue instanceof String) {
			if (isFirst)
				return paramValue.toString().replaceFirst(regex, value);
			else
				return paramValue.toString().replaceAll(regex, value);
		} else
			return paramValue;
	}

	/**
	 * @todo 日期格式化
	 * @param paramValue
	 * @param format
	 * @return
	 */
	private static String dateFormat(Object paramValue, String format) {
		if (paramValue == null)
			return null;
		if (format == null)
			return paramValue.toString();
		return DateUtil.formatDate(paramValue, format);
	}

	/**
	 * @todo 切割字符串变成数组
	 * @param paramValue
	 * @param splitSign
	 * @param dataType
	 * @return
	 */
	private static Object[] splitToArray(Object paramValue, String splitSign, String dataType) {
		if (paramValue == null)
			return null;
		Object[] result = null;
		// 原本就是数组
		if (paramValue.getClass().isArray() || paramValue instanceof Collection) {
			result = CollectionUtil.convertArray(paramValue);
			if (dataType == null)
				return result;
		} else {
			String[] arrays = paramValue.toString().split(splitSign);
			if (dataType == null || dataType.equals("string"))
				return arrays;
			result = new Object[arrays.length];
			System.arraycopy(arrays, 0, result, 0, arrays.length);
		}
		String value;
		for (int i = 0, n = result.length; i < n; i++) {
			if (null != result[i]) {
				value = result[i].toString();
				if (dataType.equals("integer") || dataType.equals("int"))
					result[i] = new Integer(value);
				else if (dataType.equals("long"))
					result[i] = new Long(value);
				else if (dataType.equals("float"))
					result[i] = new Float(value);
				else if (dataType.equals("double"))
					result[i] = new Double(value);
				else if (dataType.equals("decimal") || dataType.equals("number"))
					result[i] = new BigDecimal(value);
				else if (dataType.equals("date"))
					result[i] = DateUtil.parseString(value);
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
		if (paramValue == null)
			return null;
		Object[] result = CollectionUtil.convertArray(paramValue);
		if (dataType == null)
			return result;
		String value;
		for (int i = 0, n = result.length; i < n; i++) {
			if (result[i] != null) {
				value = result[i].toString();
				if (dataType.equals("integer") || dataType.equals("int"))
					result[i] = new Integer(value);
				else if (dataType.equals("long"))
					result[i] = new Long(value);
				else if (dataType.equals("float"))
					result[i] = new Float(value);
				else if (dataType.equals("double"))
					result[i] = new Double(value);
				else if (dataType.equals("decimal") || dataType.equals("number"))
					result[i] = new BigDecimal(value);
				else if (dataType.equals("string"))
					result[i] = value;
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
		if (dataType == null)
			result = value;
		else {
			if (dataType.equals("integer") || dataType.equals("int"))
				result = new Integer(value.intValue());
			else if (dataType.equals("long"))
				result = new Long(value.longValue());
			else if (dataType.equals("float"))
				result = new Float(value.floatValue());
			else if (dataType.equals("double"))
				result = new Double(value.doubleValue());
			else
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
		// 代码有冗余,暂不需优化
		// 取当前月份的第一天
		if (format.equalsIgnoreCase("first_day")) {
			result = DateUtil.firstDayOfMonth(paramValue);
			if (paramFilterModel.getIncrementDays() != 0)
				result = DateUtil.addDay(result, paramFilterModel.getIncrementDays());
			result = DateUtil.parse(result, DAY_FORMAT);
		} // 年的第一天
		else if (format.equalsIgnoreCase("first_year_day")) {
			result = DateUtil.getYear(paramValue) + "-01-01";
			if (paramFilterModel.getIncrementDays() != 0)
				result = DateUtil.addDay(result, paramFilterModel.getIncrementDays());
			result = DateUtil.parse(result, DAY_FORMAT);
		} // 取当前月份的最后一天
		else if (format.equalsIgnoreCase("last_day")) {
			result = DateUtil.lastDayOfMonth(paramValue);
			if (paramFilterModel.getIncrementDays() != 0)
				result = DateUtil.addDay(result, paramFilterModel.getIncrementDays());
			result = DateUtil.parse(result, DAY_FORMAT);
		} // 年的最后一天
		else if (format.equalsIgnoreCase("last_year_day")) {
			result = DateUtil.getYear(paramValue) + "-12-31";
			if (paramFilterModel.getIncrementDays() != 0)
				result = DateUtil.addDay(result, paramFilterModel.getIncrementDays());
			result = DateUtil.parse(result, DAY_FORMAT);
		} else {
			result = DateUtil.addDay(paramValue, paramFilterModel.getIncrementDays());
			if (StringUtil.isNotBlank(format))
				result = DateUtil.parse(DateUtil.formatDate(result, format), format);
		}
		return result;
	}

	/**
	 * @todo 转换sql参数,将对象数组中的值与给定的参照数值比较， 如果相等则置数组中的值为null
	 * @param params
	 * @param contrasts
	 * @return
	 */
	private static Object filterEquals(Object param, String[] contrasts) {
		if (null == param || contrasts == null || contrasts.length == 0)
			return null;
		int type = 0;
		if (param instanceof Date)
			type = 1;
		else if (param instanceof Number)
			type = 2;

		// 只要有一个对比值相等表示成立，返回null
		for (String contrast : contrasts) {
			if (type == 1) {
				Date compareDate = contrast.equalsIgnoreCase("sysdate")
						? DateUtil.parse(DateUtil.getNowTime(), DAY_FORMAT)
						: DateUtil.convertDateObject(contrast);
				if (compareDate != null && DateUtil.convertDateObject(param).compareTo(compareDate) == 0)
					return null;
			} else if (type == 2) {
				if (NumberUtil.isNumber(contrast)
						&& Double.parseDouble(param.toString()) == Double.parseDouble(contrast))
					return null;
			} else if (param.toString().compareTo(contrast) == 0)
				return null;
		}
		return param;
	}

	/**
	 * @todo 转换sql参数,将对象数组中的值与给定的参照数值比较， 如果不相等则置数组中的值为null
	 * @param param
	 * @param contrasts
	 * @return
	 */
	private static Object filterNotEquals(Object param, String[] contrasts) {
		if (null == param)
			return null;
		if (contrasts == null || contrasts.length == 0)
			return param;
		int type = 0;
		if (param instanceof Date)
			type = 1;
		else if (param instanceof Number)
			type = 2;
		// 只要有一个对比值相等表示不成立，返回参数本身的值
		for (String contrast : contrasts) {
			if (type == 1) {
				Date compareDate = contrast.equalsIgnoreCase("sysdate")
						? DateUtil.parse(DateUtil.getNowTime(), DAY_FORMAT)
						: DateUtil.convertDateObject(contrast);
				if (compareDate == null || DateUtil.convertDateObject(param).compareTo(compareDate) == 0)
					return param;
			} else if (type == 2) {
				// 非数字或相等
				if (!NumberUtil.isNumber(contrast)
						|| Double.parseDouble(param.toString()) == Double.parseDouble(contrast))
					return param;
			} else if (param.toString().compareTo(contrast) == 0)
				return param;
		}
		return null;
	}

	/**
	 * @todo 过滤参数值小于指定值，并返回null
	 * @param param
	 * @param contrast
	 * @return
	 */
	private static Object filterLess(Object param, String contrast) {
		if (null == param)
			return null;
		if (param instanceof Date) {
			Date compareDate;
			if (contrast.equalsIgnoreCase("sysdate"))
				compareDate = DateUtil.parse(DateUtil.getNowTime(), DAY_FORMAT);
			else
				compareDate = DateUtil.convertDateObject(contrast);
			if (DateUtil.convertDateObject(param).before(compareDate))
				return null;
		} else if (param instanceof Number) {
			if (Double.parseDouble(param.toString()) < Double.parseDouble(contrast))
				return null;
		} else if (param.toString().compareTo(contrast) < 0)
			return null;
		return param;
	}

	/**
	 * @todo 过滤参数值小于等于指定值，并返回null
	 * @param param
	 * @param contrast
	 * @return
	 */
	private static Object filterLessEquals(Object param, String contrast) {
		if (null == param)
			return null;
		if (param instanceof Date) {
			Date compareDate;
			if (contrast.equalsIgnoreCase("sysdate"))
				compareDate = DateUtil.parse(DateUtil.getNowTime(), DAY_FORMAT);
			else
				compareDate = DateUtil.convertDateObject(contrast);
			if (DateUtil.convertDateObject(param).compareTo(compareDate) <= 0)
				return null;
		} else if (param instanceof Number) {
			if (Double.parseDouble(param.toString()) <= Double.parseDouble(contrast))
				return null;
		} else if (param.toString().compareTo(contrast) <= 0)
			return null;
		return param;
	}

	/**
	 * @todo 过滤大于指定参照数据值,否则查询条件为null
	 * @param param
	 * @param contrast
	 * @return
	 */
	private static Object filterMore(Object param, String contrast) {
		if (null == param)
			return null;
		if (param instanceof Date) {
			Date compareDate;
			if (contrast.equalsIgnoreCase("sysdate"))
				compareDate = DateUtil.parse(DateUtil.getNowTime(), DAY_FORMAT);
			else
				compareDate = DateUtil.convertDateObject(contrast);
			if (DateUtil.convertDateObject(param).compareTo(compareDate) > 0)
				return null;
		} else if (param instanceof Number) {
			if (Double.parseDouble(param.toString()) > Double.parseDouble(contrast))
				return null;
		} else if (param.toString().compareTo(contrast) > 0)
			return null;
		return param;
	}

	/**
	 * @todo 过滤大于等于指定参照数据值,否则查询条件为null
	 * @param param
	 * @param contrast
	 * @return
	 */
	private static Object filterMoreEquals(Object param, String contrast) {
		if (null == param)
			return null;
		if (param instanceof Date) {
			Date compareDate;
			if (contrast.equalsIgnoreCase("sysdate"))
				compareDate = DateUtil.parse(DateUtil.getNowTime(), DAY_FORMAT);
			else
				compareDate = DateUtil.convertDateObject(contrast);
			if (DateUtil.convertDateObject(param).compareTo(compareDate) >= 0)
				return null;
		} else if (param instanceof Number) {
			if (Double.parseDouble(param.toString()) >= Double.parseDouble(contrast))
				return null;
		} else if (param.toString().compareTo(contrast) >= 0)
			return null;
		return param;
	}

	/**
	 * @todo 参数大于等于并小于等于给定的数据范围时表示条件无效，自动置参数值为null
	 * @param param
	 * @param beginContrast
	 * @param endContrast
	 * @return
	 */
	private static Object filterBetween(Object param, String beginContrast, String endContrast) {
		if (null == param)
			return null;
		if (param instanceof Date) {
			if (DateUtil.convertDateObject(param).compareTo(DateUtil.convertDateObject(beginContrast)) >= 0
					&& DateUtil.convertDateObject(param).compareTo(DateUtil.convertDateObject(endContrast)) <= 0)
				return null;
		} else if (param instanceof Number) {
			if (Double.parseDouble(param.toString()) >= Double.parseDouble(beginContrast)
					&& Double.parseDouble(param.toString()) <= Double.parseDouble(endContrast))
				return null;
		} else if (param.toString().compareTo(beginContrast) >= 0 && param.toString().compareTo(endContrast) <= 0)
			return null;
		return param;
	}

}
