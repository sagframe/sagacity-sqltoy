package org.sagacity.sqltoy.utils;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.util.Collection;
import java.util.Date;
import java.util.Iterator;
import java.util.List;

import org.sagacity.sqltoy.config.SqlConfigParseUtils;

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
 * @modify {Date:2023-05-6 支持@if(:param==:param || 1==:flage)
 *         对比双方都是变量、变量可以在右边的场景}
 */
@SuppressWarnings("rawtypes")
public class MacroIfLogic {

	private MacroIfLogic() {
	}

	/**
	 * @todo 简单逻辑判断,只支持2个逻辑,update 2017-12-4 剔除freemarker复杂逻辑判断,减少框架依赖性
	 * @param evalExpression 表达式
	 * @param paramValues
	 * @param preCount
	 * @param logicParamCnt
	 * @return
	 */
	public static boolean evalLogic(String evalExpression, List paramValues, int preCount, int logicParamCnt) {
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
		String simpleResult = evalSimpleExpress(evalExpression, (logicParamCnt == 0) ? null : paramValues, preCount);
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
	 * @return
	 */
	private static String evalSimpleExpress(String evalExpression, List paramValues, int preCount) {
		// 不能超过两个运算符
		if (evalExpression.indexOf("||") != -1 && evalExpression.indexOf("&&") != -1) {
			return "undefine";
		}
		// 2020-08-25 增加include场景
		// 比较符号(等于用==,最后用=进行容错处理),<>符号前面已经统一规范成!=
		String[] compareStr = { "!=", "==", ">=", "<=", ">", "<", "=", " include ", " in ", " out ", " startswith ",
				" endswith " };
		// 增加对应compareStr的切割表达式(2020-10-21 修改为正则表达式，修复split错误)
		String[] splitReg = { "\\!\\=", "\\=\\=", "\\>\\=", "\\<\\=", "\\>", "\\<", "\\=", "\\s+include\\s+",
				"\\s+in\\s+", "\\s+out\\s+", "\\s+startswith\\s+", "\\s+endswith\\s+" };
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
			String compareType = "==";
			// 参数量计数器
			int meter = 0;
			// 表达式左边参数中是否包含？动态参数
			boolean hasArg = true;
			for (int i = 0; i < expressions.length; i++) {
				hasArg = false;
				express = expressions[i].trim();
				expressLow = express.toLowerCase();
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
					hasArg = StringUtil.matches(leftParamLow, SqlConfigParseUtils.ARG_NAME_PATTERN);
				}
				// 取出实际参数值
				if (hasArg) {
					leftValue = paramValues.get(preCount + meter);
					meter++;
				} else {
					leftValue = params[0].trim();
				}
				// update 2018-3-29,去除空格增强容错性
				rightValue = params[1].trim();
				// 对比值也是动态参数(update 2023-05-05)
				if (paramValues != null && "?".equals(rightValue)) {
					if (paramValues.get(preCount + meter) == null) {
						rightValue = "null";
					} else {
						rightValue = paramValues.get(preCount + meter).toString();
					}
					meter++;
				}
				// 计算单个比较的结果(update 2020-09-24 增加数组长度的提取)
				if (hasArg && (leftParamLow.startsWith("size(") || leftParamLow.startsWith("length("))) {
					expressResult[i] = compare((leftValue == null) ? 0 : CollectionUtil.convertArray(leftValue).length,
							compareType, rightValue);
				} else {
					expressResult[i] = compare(leftValue, compareType, rightValue);
				}
			}

			// 只支持&& 和||
			// 与运算
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
			e.printStackTrace();
		}
		return "undefine";
	}

	/**
	 * @todo 两个数据进行比较
	 * @param value
	 * @param compareType
	 * @param compareValue
	 * @return
	 */
	public static boolean compare(Object value, String compareType, String compareValue) {
		// 剔除首尾字符串标志符号
		compareValue = clearChar(compareValue);
		// 只支持加减运算
		String append = "0";
		String[] calculateStr = { "+", "-" };
		String[] tmpAry;
		// 判断是否有加减运算
		for (String calculate : calculateStr) {
			if (compareValue.trim().indexOf(calculate) > 0) {
				tmpAry = compareValue.split("+".equals(calculate) ? "\\+" : "\\-");
				// 正负数字
				append = calculate + tmpAry[1].trim();
				compareValue = tmpAry[0].trim();
				break;
			}
		}
		String type = "string";
		String dayTimeFmt = "yyyy-MM-dd HH:mm:ss";
		String dayFmt = "yyyy-MM-dd";
		String lowCompareValue = compareValue.toLowerCase();
		if ("now()".equals(lowCompareValue) || ".now".equals(lowCompareValue) || "${.now}".equals(lowCompareValue)
				|| "nowtime()".equals(lowCompareValue)) {
			compareValue = DateUtil.formatDate(DateUtil.addSecond(new Date(), Double.parseDouble(append)), dayTimeFmt);
			type = "time";
		} else if ("day()".equals(lowCompareValue) || "sysdate()".equals(lowCompareValue)
				|| ".day".equals(lowCompareValue) || ".day()".equals(lowCompareValue)
				|| "${.day}".equals(lowCompareValue)) {
			compareValue = DateUtil.formatDate(DateUtil.addSecond(new Date(), Double.parseDouble(append)), dayFmt);
			type = "date";
		}
		String valueStr = (value == null) ? "null" : clearChar(value.toString());
		if ("time".equals(type)) {
			valueStr = DateUtil.formatDate(value, dayTimeFmt);
		} else if ("date".equals(type)) {
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
		// 在数组范围内
		if ("in".equals(compareType)) {
			return in(value, valueStr, compareValue, type);
		}
		// 在数组范围外
		if ("out".equals(compareType)) {
			return out(value, valueStr, compareValue, type);
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
			return DateUtil.convertDateObject(valueStr).compareTo(DateUtil.convertDateObject(compare)) >= 0;
		}
		// 数字
		if (NumberUtil.isNumber(valueStr) && NumberUtil.isNumber(compare)) {
			return Double.parseDouble(valueStr) >= Double.parseDouble(compare);
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
			return DateUtil.convertDateObject(valueStr).compareTo(DateUtil.convertDateObject(compare)) <= 0;
		}
		// 数字
		if (NumberUtil.isNumber(valueStr) && NumberUtil.isNumber(compare)) {
			return Double.parseDouble(valueStr) <= Double.parseDouble(compare);
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
			return DateUtil.convertDateObject(valueStr).compareTo(DateUtil.convertDateObject(compare)) > 0;
		}
		// 数字
		if (NumberUtil.isNumber(valueStr) && NumberUtil.isNumber(compare)) {
			return Double.parseDouble(valueStr) > Double.parseDouble(compare);
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
			return DateUtil.convertDateObject(valueStr).compareTo(DateUtil.convertDateObject(compare)) < 0;
		}
		// 数字
		if (NumberUtil.isNumber(valueStr) && NumberUtil.isNumber(compare)) {
			return Double.parseDouble(valueStr) < Double.parseDouble(compare);
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
		if (value.getClass().isArray()) {
			Object[] values = CollectionUtil.convertArray(value);
			for (Object var : values) {
				if (compareLow.equals((var == null) ? null : var.toString().toLowerCase())) {
					return true;
				}
			}
		}

		// List集合包含
		if (value instanceof Collection) {
			Iterator iter = ((Collection) value).iterator();
			Object var;
			while (iter.hasNext()) {
				var = iter.next();
				if (compareLow.equals((var == null) ? null : var.toString().toLowerCase())) {
					return true;
				}
			}
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
	private static boolean in(Object value, String valueStr, String compare, String type) {
		if (value == null) {
			return false;
		}
		String[] compareAry = compare.toLowerCase().split("\\,");
		String compareLow = valueStr.toLowerCase();
		if (compareAry.length == 1) {
			return compareAry[0].contains(compareLow);
		}
		for (int i = 0; i < compareAry.length; i++) {
			if (compareLow.equals(compareAry[i].trim())) {
				return true;
			}
		}
		return false;
	}

	/**
	 * @TODO 在数组范围外
	 * @param value
	 * @param valueStr
	 * @param compare
	 * @param type
	 * @return
	 */
	private static boolean out(Object value, String valueStr, String compare, String type) {
		if (value == null) {
			return true;
		}
		String[] compareAry = compare.toLowerCase().split("\\,");
		String compareLow = valueStr.toLowerCase();
		if (compareAry.length == 1) {
			return !compareAry[0].contains(compareLow);
		}
		for (int i = 0; i < compareAry.length; i++) {
			if (compareLow.equals(compareAry[i].trim())) {
				return false;
			}
		}
		return true;
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
			Date var = DateUtil.convertDateObject(param);
			if (var.compareTo(DateUtil.convertDateObject(beginContrast)) >= 0
					&& var.compareTo(DateUtil.convertDateObject(endContrast)) <= 0) {
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

	private static String clearChar(String source) {
		if (source == null) {
			return source;
		}
		// 剔除首尾字符串标志符号
		if (source.startsWith("'") && source.endsWith("'")) {
			return source.substring(1, source.length() - 1);
		} else if (source.startsWith("\"") && source.endsWith("\"")) {
			return source.substring(1, source.length() - 1);
		}
		return source;
	}
}
