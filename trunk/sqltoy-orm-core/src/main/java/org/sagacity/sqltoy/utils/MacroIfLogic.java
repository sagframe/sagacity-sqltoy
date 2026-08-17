package org.sagacity.sqltoy.utils;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.util.Collection;
import java.util.Date;
import java.util.List;
import java.util.Map;
import java.util.regex.Pattern;

import org.sagacity.sqltoy.SqlToyConstants;
import org.sagacity.sqltoy.config.SqlConfigParseUtils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * @project sagacity-sqltoy
 * @description 提供针对sql中 @if(:paramName1>=value1 && :paramName2!=value2)
 *              性质的逻辑判断,返回true或false,适用于sql和mongo等所有查询语句中使用
 * @author zhongxuchen
 * @version v1.0,Date:2017年12月9日
 * @modify {Date:2017-12-4 剔除freemarker复杂逻辑判断,减少框架依赖性}
 * @modify {Date:2020-08-25 增加include场景,数组类型或字符串类型包含某个特定值 }
 * @modify {Date:2020-09-24 增加数组长度的提取 length(:paramName)>10 模式}
 * @modify {Date:2022-05-10 支持@if(1==1)无参数模式}
 * @modify {Date:2023-05-6 支持@if(:param1==:param2 || 1==:param3) 左右参数可都是变量的场景}
 * @modify {Date:2024-10-5 增加sqlParamType参数,支持?、:name、@(:name) 三种场景 }
 */
@SuppressWarnings("rawtypes")
public class MacroIfLogic {
	private final static Logger logger = LoggerFactory.getLogger(MacroIfLogic.class);

	private final static String BLANK = " ";

	// 必须是数字开头+1位特定字符(可选)
	private final static Pattern timePattern = Pattern.compile("(?i)^\\d+[SHDWMY]?$");

	private final static Pattern timeTypePattern = Pattern.compile("(?i)^\\d+[SHDWMY]$");

	// 日期形式的字面量(如2024-01-01、2024/01/01 10:20:30),不能参与加减运算切割
	private final static Pattern DATE_VALUE_PATTERN = Pattern
			.compile("^\\d{2,4}([-/]\\d{1,2}){1,2}(\\s+\\d{1,2}:\\d{1,2}(:\\d{1,2})?(\\.\\d+)?)?$");

	private MacroIfLogic() {
	}

	/**
	 * @todo 只支持||和&&简单逻辑判断
	 * @param evalExpression 表达式
	 * @param paramValues
	 * @param preCount
	 * @param logicParamCnt
	 * @param sqlParamType   0:?常规sql;1:elastich sql;2:mongodb
	 * @return
	 */
	public static boolean evalLogic(String evalExpression, List paramValues, int preCount, int logicParamCnt,
			int sqlParamType) {
		Object value;
		for (int i = 0; i < logicParamCnt; i++) {
			value = paramValues.get(preCount + i);
			// 空数组、空集合不参与判断
			if (value != null) {
				if ((value.getClass().isArray() && CollectionUtil.convertArray(value).length == 0)
						|| ((value instanceof Collection) && ((Collection) value).isEmpty())) {
					return false;
				}
			}
		}
		// 规范判断符号标准(<>转为!=)
		evalExpression = evalExpression.replaceAll("\\<\\>", "!=").replaceAll("\r|\t|\n", " ").trim();
		// 先通过简单表达式进行计算,格式如:@if(:name>=xxx || :name<=xxx)
		String simpleResult = evalSimpleExpress(evalExpression, (logicParamCnt == 0) ? null : paramValues, preCount,
				sqlParamType);
		if (!"undefine".equals(simpleResult)) {
			return Boolean.parseBoolean(simpleResult);
		}
		// 默认返回true，表示@if()模式不起作用
		return true;
	}

	/**
	 * @todo 简单表达式(单独列出来便于做容错性处理)
	 * @param evalExpression
	 * @param paramValues
	 * @param preCount
	 * @param sqlParamType
	 * @return
	 */
	private static String evalSimpleExpress(String evalExpression, List paramValues, int preCount, int sqlParamType) {
		// 目前只支持单一运算符,不支持同时有与和或(待强化:在其他领域已经解决此问题,将算法融入进来，供借鉴学习)
		if (evalExpression.indexOf("||") != -1 && evalExpression.indexOf("&&") != -1) {
			return "undefine";
		}
		// 2020-08-25 增加include场景
		// 比较符号(等于用==,最后用=进行容错处理),<>符号前面已经统一规范成!=
		String[] compareStr = { "!=", "==", ">=", "<=", ">", "<", "=", " include ", " exclude ", " in ", " out ",
				" startswith ", " endswith " };
		// 增加对应compareStr的切割表达式(2020-10-21 修改为正则表达式，修复split错误)
		String[] splitReg = { "\\!\\=", "\\=\\=", "\\>\\=", "\\<\\=", "\\>", "\\<", "\\=", "\\s+include\\s+",
				"\\s+exclude\\s+", "\\s+in\\s+", "\\s+out\\s+", "\\s+startswith\\s+", "\\s+endswith\\s+" };
		String splitStr = "==";
		String logicStr = "\\&\\&";
		String[] expressions;
		try {
			if (evalExpression.indexOf("||") != -1) {
				logicStr = "\\|\\|";
			}
			expressions = evalExpression.split(logicStr);
			boolean[] expressResult = new boolean[expressions.length];
			String express;
			String expressLow;
			String[] params;
			Object leftValue;
			String leftParamLow;
			String rightValue;
			Object rightObj;
			String compareType = "==";
			// 参数量计数器
			int meter = 0;
			// 表达式左边参数中是否包含？动态参数
			boolean hasArg = true;
			boolean isNegate = false;
			// 结果取反
			boolean negateResult = false;
			for (int i = 0; i < expressions.length; i++) {
				hasArg = false;
				rightObj = null;
				negateResult = false;
				express = expressions[i].trim();
				expressLow = express.toLowerCase();
				// 默认为等于判断(update 2025-4-9 @if(:paramType!=1 && :booleanParam))
				compareType = "==";
				// 匹配对应的判断逻辑符号
				for (int j = 0; j < compareStr.length; j++) {
					if (expressLow.indexOf(compareStr[j]) != -1) {
						compareType = compareStr[j].trim();
						splitStr = splitReg[j];
						break;
					}
				}
				params = express.split(splitStr);
				// 对比的参照参数名称
				leftParamLow = params[0].trim().toLowerCase();
				// 判断左边是否有?参数
				if (paramValues != null) {
					hasArg = hasArg(leftParamLow, sqlParamType);
				}
				// 取出实际参数值
				isNegate = false;
				if (hasArg) {
					leftValue = paramValues.get(preCount + meter);
					// 支持枚举类型
					if (leftValue != null && leftValue instanceof Enum) {
						leftValue = BeanUtil.getEnumValue(leftValue);
					}
					// !:paramName 取反
					if (leftParamLow.startsWith("!")) {
						isNegate = true;
					}
					meter++;
				} else {
					leftValue = params[0].trim();
				}
				// update 2018-3-29,去除空格增强容错性
				if (params.length > 1) {
					rightValue = params[1].trim();
					if (isNegate) {
						negateResult = true;
					}
				} else {
					if (isNegate) {
						rightValue = "false";
					} else {
						rightValue = "true";
					}
				}
				// 对比值也是动态参数(update 2023-05-05)
				if (paramValues != null && hasArg(rightValue, sqlParamType)) {
					rightObj = paramValues.get(preCount + meter);
					if (rightObj == null) {
						rightValue = "null";
					} else if (rightObj instanceof Enum) {
						rightObj = BeanUtil.getEnumValue(rightObj).toString();
					}
					// 没有计算符号，将rightValue置为对象字符
					if (!rightValue.contains("+") && !rightValue.contains("-")) {
						if (rightObj != null) {
							rightValue = rightObj.toString();
						}
					} else {
						// 剔除可能存在的左右括号
						if (rightValue.startsWith("(") && rightValue.endsWith(")")) {
							rightValue = rightValue.substring(1, rightValue.length() - 1).trim();
						}
					}
					meter++;
				}
				// 计算单个比较的结果(update 2020-09-24 增加数组长度的提取)
				// !size()/!length()取反前缀也要走长度提取,否则数组被按toString字符串比较
				if (hasArg && (leftParamLow.startsWith("size(") || leftParamLow.startsWith("length(")
						|| leftParamLow.startsWith("!size(") || leftParamLow.startsWith("!length("))) {
					expressResult[i] = compare((leftValue == null) ? 0 : getSize(leftValue), compareType, rightValue,
							rightObj);
				} else {
					expressResult[i] = compare(leftValue, compareType, rightValue, rightObj);
				}
				// 存在取反场景
				if (negateResult) {
					expressResult[i] = !expressResult[i];
				}
			}

			// 只支持&& 和||与运算
			if ("\\&\\&".equals(logicStr) || "&&".equals(logicStr)) {
				for (int i = 0; i < expressions.length; i++) {
					if (!expressResult[i]) {
						return "false";
					}
				}
				return "true";
			}
			// 或运算
			for (int i = 0; i < expressions.length; i++) {
				if (expressResult[i]) {
					return "true";
				}
			}
			return "false";
		} catch (Exception e) {
			logger.error("evalSimpleExpress 方法执行异常", e);
		}
		return "undefine";
	}

	/**
	 * 获取对比数据的长度，字符串返回字符串长度、数组和集合返回size
	 * 
	 * @param compareValue
	 * @return
	 */
	private static int getSize(Object compareValue) {
		if (compareValue == null) {
			return 0;
		}
		if (compareValue instanceof Object[]) {
			return ((Object[]) compareValue).length;
		}
		if (compareValue instanceof Collection) {
			return ((Collection) compareValue).size();
		}
		if (compareValue instanceof CharSequence) {
			return ((CharSequence) compareValue).length();
		}
		if (compareValue instanceof Map) {
			return ((Map) compareValue).size();
		}
		if (compareValue instanceof int[]) {
			return ((int[]) compareValue).length;
		}
		if (compareValue instanceof long[]) {
			return ((long[]) compareValue).length;
		}
		if (compareValue instanceof double[]) {
			return ((double[]) compareValue).length;
		}
		if (compareValue instanceof boolean[]) {
			return ((boolean[]) compareValue).length;
		}
		if (compareValue instanceof short[]) {
			return ((short[]) compareValue).length;
		}
		if (compareValue instanceof char[]) {
			return ((char[]) compareValue).length;
		}
		if (compareValue instanceof byte[]) {
			return ((byte[]) compareValue).length;
		}
		return 0;
	}

	/**
	 * @todo 两个数据进行比较
	 * @param value
	 * @param compareType
	 * @param originalCompareValue 原始
	 * @param compareValueObj
	 * @return
	 */
	public static boolean compare(Object value, String compareType, String originalCompareValue,
			Object compareValueObj) {
		// 剔除首尾字符串标志符号
		originalCompareValue = StringUtil.removeStartEndQuote(originalCompareValue);
		String compareValue = originalCompareValue;
		// 日期形式的字面量(如2024-01-01)不参与加减运算判断,避免被切割成算术表达式
		boolean dateLikeValue = StringUtil.matches(compareValue.trim(), DATE_VALUE_PATTERN);
		// 只支持加减运算
		String append = "0";
		String[] calculateStr = { "+", "-" };
		String[] tmpAry;
		// 0:second;1:hour;2:day;3:week;4:month;5:year
		int addType = -1;
		boolean hasCalculate = false;
		// 判断是否有加减运算
		for (String calculate : calculateStr) {
			if (!dateLikeValue && compareValue.trim().indexOf(calculate) > 0) {
				tmpAry = compareValue.split("+".equals(calculate) ? "\\+" : "\\-");
				hasCalculate = false;
				String firstStr = tmpAry[0].trim();
				String secondStr = tmpAry[1].trim();
				// 只支持:sysdate()+2d 一种模式
				if (StringUtil.matches(secondStr, timePattern)) {
					hasCalculate = true;
					compareValue = firstStr;
					// 正负数字
					append = calculate + secondStr;
					// 时间单位
					if (StringUtil.matches(secondStr, timeTypePattern)) {
						// 取最后一位
						String timeType = append.substring(append.length() - 1).toUpperCase();
						append = append.substring(0, append.length() - 1);
						if (timeType.equals("S")) {
							addType = 0;
						} else if (timeType.equals("H")) {
							addType = 1;
						} else if (timeType.equals("D")) {
							addType = 2;
						} else if (timeType.equals("W")) {
							addType = 3;
						} else if (timeType.equals("M")) {
							addType = 4;
						} else if (timeType.equals("Y")) {
							addType = 5;
						}
					}
					break;
				} // 5-:paramValue
				else if (StringUtil.matches(firstStr, timePattern)) {
					if (compareValueObj != null) {
						originalCompareValue = ExpressionUtil
								.calculate(firstStr + calculate + compareValueObj.toString()).toString();
						compareValueObj = null;
					} else {
						originalCompareValue = ExpressionUtil.calculate(compareValue).toString();
					}
					hasCalculate = false;
					break;
				}
			}
		}
		String type = "string";
		String dayTimeFmt = "yyyy-MM-dd HH:mm:ss";
		String dayFmt = "yyyy-MM-dd";
		// 存在计算符号
		if (hasCalculate) {
			String lowCompareValue = compareValue.toLowerCase();
			// 默认秒
			if ("now()".equals(lowCompareValue) || ".now".equals(lowCompareValue) || "${.now}".equals(lowCompareValue)
					|| "nowtime()".equals(lowCompareValue) || "systime()".equals(lowCompareValue)
					|| "curtime()".equals(lowCompareValue) || "curtime".equals(lowCompareValue)
					|| "sysdate()".equals(lowCompareValue) || "sysdate".equals(lowCompareValue)
					|| "sysdatetime".equals(lowCompareValue) || "sysdatetime()".equals(lowCompareValue)) {
				compareValue = DateUtil.formatDate(addTimeByType(new Date(), append, addType == -1 ? 0 : addType),
						dayTimeFmt);
				type = "time";
			} // 默认天
			else if ("day()".equals(lowCompareValue) || ".day".equals(lowCompareValue)
					|| ".day()".equals(lowCompareValue) || "${.day}".equals(lowCompareValue)) {
				compareValue = DateUtil.formatDate(addTimeByType(new Date(), append, addType == -1 ? 2 : addType),
						dayFmt);
				type = "date";
			} else if (compareValueObj != null) {
				// 数字加减
				if (NumberUtil.isNumber(compareValueObj.toString())) {
					compareValue = ExpressionUtil.calculate(compareValueObj.toString() + append).toString();
				} else {
					if (compareValueObj instanceof Date || compareValueObj instanceof LocalDateTime) {
						type = "time";
						if (addType == -1) {
							addType = 0;
						}
					} else if (compareValueObj instanceof LocalDate) {
						type = "date";
						if (addType == -1) {
							addType = 2;
						}
					}
					Date resultDate = addTimeByType(DateUtil.convertDateObject(compareValueObj), append,
							addType == -1 ? 0 : addType);
					if (type.equals("date")) {
						compareValue = DateUtil.formatDate(resultDate, dayFmt);
					} else {
						compareValue = DateUtil.formatDate(resultDate, dayTimeFmt);
					}
				}
			} else {
				compareValue = ExpressionUtil.calculate(originalCompareValue).toString();
			}
		} else {
			if (compareValueObj != null) {
				if (compareValueObj instanceof Date || compareValueObj instanceof LocalDateTime) {
					type = "time";
					compareValue = DateUtil.formatDate(compareValueObj, dayTimeFmt);
				} else if (compareValueObj instanceof LocalDate) {
					type = "date";
					compareValue = DateUtil.formatDate(compareValueObj, dayFmt);
				} else {
					compareValue = compareValueObj.toString();
				}
			} else {
				compareValue = originalCompareValue;
			}
		}
		// 右侧是日期形式的字符串而左侧是日期类型参数时,统一转成日期格式比较,避免按Date.toString()进行字符串比较
		if (dateLikeValue && (value instanceof Date || value instanceof LocalDateTime)) {
			type = (compareValue.indexOf(":") == -1) ? "date" : "time";
		} else if (dateLikeValue && value instanceof LocalDate && compareValue.indexOf(":") == -1) {
			type = "date";
		}
		String valueStr = (value == null) ? "null" : StringUtil.removeStartEndQuote(value.toString());
		if (value != null && "time".equals(type)) {
			valueStr = DateUtil.formatDate(value, dayTimeFmt);
		} else if (value != null && "date".equals(type)) {
			valueStr = DateUtil.formatDate(value, dayFmt);
		}
		// 等于(兼容等于号非法)
		if ("==".equals(compareType) || "=".equals(compareType)) {
			return valueStr.equalsIgnoreCase(compareValue);
		}
		// 不等于
		if ("!=".equals(compareType)) {
			return !valueStr.equalsIgnoreCase(compareValue);
		}
		// 为null时只参与等于或不等于逻辑判断
		if (value == null) {
			return false;
		}
		// 大于等于
		if (">=".equals(compareType)) {
			return moreEqual(value, valueStr, compareValue, type);
		}
		// 小于等于
		if ("<=".equals(compareType)) {
			return lessEqual(value, valueStr, compareValue, type);
		}
		// 大于
		if (">".equals(compareType)) {
			return more(value, valueStr, compareValue, type);
		}
		// 小于
		if ("<".equals(compareType)) {
			return less(value, valueStr, compareValue, type);
		}
		// 包含
		if ("include".equals(compareType)) {
			return include(value, valueStr, compareValue, type);
		}
		// 不包含
		if ("exclude".equals(compareType)) {
			return !include(value, valueStr, compareValue, type);
		}
		// 在数组范围内
		if ("in".equals(compareType)) {
			return in(value, valueStr, compareValue, compareValueObj, type);
		}
		// 在数组范围外
		if ("out".equals(compareType)) {
			return !in(value, valueStr, compareValue, compareValueObj, type);
		}
		// 以xxx字符开始
		if ("startswith".equals(compareType)) {
			return valueStr.startsWith(compareValue);
		}
		// 以xxx字符结束
		if ("endswith".equals(compareType)) {
			return valueStr.endsWith(compareValue);
		}
		// between
		if ("between".equals(compareType)) {
			String[] compareValues = compareValue.split("\\,");
			if (compareValues.length == 2) {
				return between(value, valueStr, compareValues[0], compareValues[1]);
			}
		}
		return true;
	}

	private static Date addTimeByType(Date date, String append, int addType) {
		// 秒
		if (addType == 0) {
			return DateUtil.addSecond(date, Double.parseDouble(append));
		} // 小时
		else if (addType == 1) {
			return DateUtil.addHour(date, Double.parseDouble(append));
		} // 天
		else if (addType == 2) {
			return DateUtil.addDay(date, Double.parseDouble(append));
		} // 周
		else if (addType == 3) {
			return DateUtil.addDay(date, Double.parseDouble(append) * 7);
		} // 月
		else if (addType == 4) {
			return DateUtil.addMonth(date, Double.valueOf(append).intValue());
		} // 年
		else if (addType == 5) {
			return DateUtil.addYear(date, Double.valueOf(append).intValue());
		}
		return date;
	}

	/**
	 * @TODO 日期比较辅助:任一侧无法解析为日期时返回null(调用方按false处理),
	 *       避免convertDateObject返回null直接compareTo抛NPE中断整个@if解析
	 * @param valueStr
	 * @param compare
	 * @return null表示无法比较
	 */
	private static Integer compareDate(String valueStr, String compare) {
		Date date1 = DateUtil.convertDateObject(valueStr);
		Date date2 = DateUtil.convertDateObject(compare);
		if (date1 == null || date2 == null) {
			return null;
		}
		return Integer.valueOf(date1.compareTo(date2));
	}

	/**
	 * @todo 大于等于
	 * @param value
	 * @param valueStr
	 * @param compare
	 * @param type
	 * @return
	 */
	private static boolean moreEqual(Object value, String valueStr, String compare, String type) {
		if ("time".equals(type) || "date".equals(type)) {
			Integer cmpResult = compareDate(valueStr, compare);
			return (cmpResult != null) && cmpResult.intValue() >= 0;
		}
		// 数字
		if (NumberUtil.isNumber(valueStr) && NumberUtil.isNumber(compare)) {
			return new BigDecimal(valueStr).compareTo(new BigDecimal(compare)) >= 0;
		}
		return valueStr.compareTo(compare) >= 0;
	}

	/**
	 * @todo 小于等于
	 * @param value
	 * @param valueStr
	 * @param compare
	 * @param type
	 * @return
	 */
	private static boolean lessEqual(Object value, String valueStr, String compare, String type) {
		if ("time".equals(type) || "date".equals(type)) {
			Integer cmpResult = compareDate(valueStr, compare);
			return (cmpResult != null) && cmpResult.intValue() <= 0;
		}
		// 数字
		if (NumberUtil.isNumber(valueStr) && NumberUtil.isNumber(compare)) {
			return new BigDecimal(valueStr).compareTo(new BigDecimal(compare)) <= 0;
		}
		return valueStr.compareTo(compare) <= 0;
	}

	/**
	 * @todo 大于
	 * @param value
	 * @param valueStr
	 * @param compare
	 * @param type
	 * @return
	 */
	private static boolean more(Object value, String valueStr, String compare, String type) {
		if ("time".equals(type) || "date".equals(type)) {
			Integer cmpResult = compareDate(valueStr, compare);
			return (cmpResult != null) && cmpResult.intValue() > 0;
		}
		// 数字
		if (NumberUtil.isNumber(valueStr) && NumberUtil.isNumber(compare)) {
			return new BigDecimal(valueStr).compareTo(new BigDecimal(compare)) > 0;
		}
		return valueStr.compareTo(compare) > 0;
	}

	/**
	 * @todo 小于
	 * @param value
	 * @param valueStr
	 * @param compare
	 * @param type
	 * @return
	 */
	private static boolean less(Object value, String valueStr, String compare, String type) {
		if ("time".equals(type) || "date".equals(type)) {
			Integer cmpResult = compareDate(valueStr, compare);
			return (cmpResult != null) && cmpResult.intValue() < 0;
		}
		// 数字
		if (NumberUtil.isNumber(valueStr) && NumberUtil.isNumber(compare)) {
			return new BigDecimal(valueStr).compareTo(new BigDecimal(compare)) < 0;
		}
		return valueStr.compareTo(compare) < 0;
	}

	/**
	 * @todo include包含(忽视大小写)
	 * @param value
	 * @param valueStr
	 * @param compare
	 * @param type
	 * @return
	 */
	private static boolean include(Object value, String valueStr, String compare, String type) {
		if (value == null) {
			return false;
		}
		String compareLow = compare.toLowerCase();
		// 字符串包含
		if (value instanceof String) {
			return valueStr.toLowerCase().contains(compareLow);
		}
		// 数组集合包含
		if (value.getClass().isArray() || value instanceof Collection) {
			// convertArray 覆盖了Collection和数组两种场景
			Object[] values = CollectionUtil.convertArray(value);
			for (Object item : values) {
				if (compareLow.equals((item == null) ? null : item.toString().toLowerCase())) {
					return true;
				}
			}
		}
		// map
		if (value instanceof Map) {
			return ((Map) value).containsKey(compare);
		}
		return false;
	}

	/**
	 * @TODO 在数组范围内
	 * @param value
	 * @param valueStr
	 * @param compare
	 * @param type
	 * @return
	 */
	private static boolean in(Object value, String valueStr, String compare, Object compareObj, String type) {
		if (value == null) {
			return false;
		}
		String valueLow = valueStr.toLowerCase();
		if (compareObj != null
				&& (compareObj.getClass().isArray() || compareObj instanceof Collection || compareObj instanceof Map)) {
			// 数组集合包含
			if (compareObj.getClass().isArray() || compareObj instanceof Collection) {
				Object[] values = CollectionUtil.convertArray(compareObj);
				for (Object item : values) {
					if (valueLow.equals((item == null) ? null : item.toString().toLowerCase())) {
						return true;
					}
				}
			}
			// map
			if (compareObj instanceof Map) {
				return ((Map) compareObj).containsKey(valueStr);
			}
			return false;
		}
		// 去除括号，兼容in(xxx,xxx)模式
		if (compare.startsWith("(") && compare.endsWith(")")) {
			compare = compare.substring(1, compare.length() - 1).trim();
		}
		// 统一转小写进行比较
		String[] compareAry = compare.toLowerCase().split("\\,");
		if (compareAry.length == 1) {
			return compareAry[0].contains(valueLow);
		}
		String tmpStr;
		for (String itemStr : compareAry) {
			tmpStr = itemStr.trim();
			if (valueLow.equals(tmpStr) || valueLow.equals(StringUtil.removeStartEndQuote(tmpStr))) {
				return true;
			}
		}
		return false;
	}

	/**
	 * @todo 参数大于等于并小于等于给定的数据范围时表示条件无效，自动置参数值为null
	 * @param param
	 * @param valueStr
	 * @param beginContrast
	 * @param endContrast
	 * @return
	 */
	private static boolean between(Object param, String valueStr, String beginContrast, String endContrast) {
		if (null == param) {
			return false;
		}
		if (param instanceof Date || param instanceof LocalDate || param instanceof LocalDateTime) {
			Date dateVar = DateUtil.convertDateObject(param);
			if (dateVar.compareTo(DateUtil.convertDateObject(beginContrast)) >= 0
					&& dateVar.compareTo(DateUtil.convertDateObject(endContrast)) <= 0) {
				return true;
			}
		} else if (param instanceof LocalTime) {
			if (((LocalTime) param).compareTo(LocalTime.parse(beginContrast)) >= 0
					&& ((LocalTime) param).compareTo(LocalTime.parse(endContrast)) <= 0) {
				return true;
			}
		} else if (param instanceof Number) {
			if ((new BigDecimal(param.toString()).compareTo(new BigDecimal(beginContrast)) >= 0)
					&& (new BigDecimal(param.toString()).compareTo(new BigDecimal(endContrast)) <= 0)) {
				return true;
			}
		} else if (valueStr.compareTo(beginContrast) >= 0 && valueStr.compareTo(endContrast) <= 0) {
			return true;
		}
		return false;
	}

	/**
	 * @todo 判断包含动态参数
	 * @param value
	 * @param sqlParamType
	 * @return
	 */
	private static boolean hasArg(String value, int sqlParamType) {
		// 常规sql ?
		if (sqlParamType == 0 && StringUtil.matches(value, SqlConfigParseUtils.ARG_NAME_PATTERN)) {
			return true;
		}
		// elasticsearch sql :name
		if (sqlParamType == 1
				&& StringUtil.matches(BLANK.concat(value).concat(BLANK), SqlToyConstants.SQL_NAMED_PATTERN)) {
			return true;
		}
		// mongo nosql @(:name)
		if (sqlParamType == 2 && StringUtil.matches(value, SqlToyConstants.NOSQL_NAMED_PATTERN)) {
			return true;
		}
		return false;
	}
}