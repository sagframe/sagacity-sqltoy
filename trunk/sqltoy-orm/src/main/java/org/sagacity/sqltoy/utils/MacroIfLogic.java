package org.sagacity.sqltoy.utils;

import java.util.Collection;
import java.util.Date;
import java.util.Iterator;
import java.util.List;

/**
 * @project sagacity-sqltoy
 * @description 提供针对sql中 @if(:paramName1>=value1 && :paramName2!=value2)
 *              性质的逻辑判断,返回true或false,适用于sql和mongo等所有查询语句中使用
 * @author zhongxuchen
 * @version v1.0,Date:2017年12月9日
 * @modify {Date:2017-12-4 剔除freemarker复杂逻辑判断,减少框架依赖性}
 * @modify {Date:2020-08-25 增加include场景,数组类型或字符串类型包含某个特定值 }
 * @modify {Date:2020-09-24 增加数组长度的提取 length(:paramName)>10 模式}
 */
public class MacroIfLogic {

	private MacroIfLogic() {
	}

	/**
	 * @todo 简单逻辑判断,只支持2个逻辑,update 2017-12-4 剔除freemarker复杂逻辑判断,减少框架依赖性
	 * @param sql
	 * @param paramValues
	 * @param preCount
	 * @param logicParamCnt
	 * @return
	 */
	public static boolean evalLogic(String sql, List paramValues, int preCount, int logicParamCnt) {
		if (logicParamCnt == 0) {
			return true;
		}
		Object value;
		for (int i = 0; i < logicParamCnt; i++) {
			value = paramValues.get(preCount + i);
			// 参数为null会参与后面等于和不等于逻辑判断,数组不参与判断
			if (value != null) {
				if ((value.getClass().isArray() && CollectionUtil.convertArray(value).length == 0)
						|| ((value instanceof Collection) && ((Collection) value).isEmpty())) {
					return false;
				}
			}
		}
		// 规范判断符号标准(<>转为!=)
		sql = sql.replaceAll("\\<\\>", "!=").replaceAll("\r|\t|\n", " ").trim();
		// 先通过简单表达式进行计算,格式如:@if(:name>=xxx || :name<=xxx)
		String simpleResult = evalSimpleExpress(sql, paramValues, preCount);
		if (!simpleResult.equals("undefine")) {
			return Boolean.parseBoolean(simpleResult);
		}
		// 默认返回true，表示@if()模式不起作用
		return true;
	}

	/**
	 * @todo 简单表达式(单独列出来便于做容错性处理)
	 * @param sql
	 * @param paramValues
	 * @param preCount
	 * @return
	 */
	private static String evalSimpleExpress(String sql, List paramValues, int preCount) {
		// 不能超过两个运算符
		if (sql.indexOf("||") != -1 && sql.indexOf("&&") != -1) {
			return "undefine";
		}
		// 2020-08-25 增加include场景
		// 比较符号(等于用==,最后用=进行容错处理),<>符号前面已经统一规范成!=
		String[] compareStr = { "!=", "==", ">=", "<=", ">", "<", "=", " include ", " in ", " out " };
		// 增加对应compareStr的切割表达式(2020-10-21 修改为正则表达式，修复split错误)
		String[] splitReg = { "\\!\\=", "\\=\\=", "\\>\\=", "\\<\\=", "\\>", "\\<", "\\=", "\\s+include\\s+",
				"\\s+in\\s+", "\\s+out\\s+" };
		String splitStr = "==";
		String logicStr = "\\&\\&";
		String[] expressions;
		try {
			if (sql.indexOf("||") != -1) {
				logicStr = "\\|\\|";
			}
			expressions = sql.split(logicStr);
			boolean[] expressResult = new boolean[expressions.length];
			String express;
			Object value;
			String compareValue;
			String expressLow;
			String[] params;
			String compareParam;
			String compareType = "==";
			for (int i = 0; i < expressions.length; i++) {
				value = paramValues.get(preCount + i);
				express = expressions[i].trim();
				expressLow = express.toLowerCase();
				for (int j = 0; j < compareStr.length; j++) {
					if (expressLow.indexOf(compareStr[j]) != -1) {
						compareType = compareStr[j].trim();
						splitStr = splitReg[j];
						break;
					}
				}
				params = express.split(splitStr);
				// 对比的参照参数名称
				compareParam = params[0].trim().toLowerCase();
				// update 2018-3-29,去除空格增强容错性
				compareValue = params[1].trim();
				// 计算单个比较的结果(update 2020-09-24 增加数组长度的提取)
				if (compareParam.startsWith("size(") || compareParam.startsWith("length(")) {
					expressResult[i] = compare(value == null ? 0 : CollectionUtil.convertArray(value).length,
							compareType, compareValue);
				} else {
					expressResult[i] = compare(value, compareType, compareValue);
				}
			}

			// 只支持&& 和||
			// 与运算
			if (logicStr.equals("\\&\\&") || logicStr.equals("&&")) {
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
	private static boolean compare(Object value, String compareType, String compareValue) {
		// 剔除首尾字符串标志符号
		if (compareValue.startsWith("'") && compareValue.endsWith("'")) {
			compareValue = compareValue.substring(1, compareValue.length() - 1);
		} else if (compareValue.startsWith("\"") && compareValue.endsWith("\"")) {
			compareValue = compareValue.substring(1, compareValue.length() - 1);
		}
		// 只支持加减运算
		String append = "0";
		String[] calculateStr = { "+", "-" };
		String[] tmpAry;
		// 判断是否有加减运算
		for (String calculate : calculateStr) {
			if (compareValue.trim().indexOf(calculate) > 0) {
				tmpAry = compareValue.split(calculate.equals("+") ? "\\+" : "\\-");
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
		if (lowCompareValue.equals("now()") || lowCompareValue.equals(".now") || lowCompareValue.equals("${.now}")
				|| lowCompareValue.equals("nowtime()")) {
			compareValue = DateUtil.formatDate(DateUtil.addSecond(new Date(), Double.parseDouble(append)), dayTimeFmt);
			type = "time";
		} else if (lowCompareValue.equals("day()") || lowCompareValue.equals("sysdate()")
				|| lowCompareValue.equals(".day") || lowCompareValue.equals(".day()")
				|| lowCompareValue.equals("${.day}")) {
			compareValue = DateUtil.formatDate(DateUtil.addSecond(new Date(), Double.parseDouble(append)), dayFmt);
			type = "date";
		}
		compareValue = compareValue.replaceAll("\'", "").replaceAll("\"", "");
		String realValue = (value == null) ? "null" : value.toString();
		if (type.equals("time")) {
			realValue = DateUtil.formatDate(value, dayTimeFmt);
		} else if (type.equals("date")) {
			realValue = DateUtil.formatDate(value, dayFmt);
		}
		// 等于(兼容等于号非法)
		if (compareType.equals("==") || compareType.equals("=")) {
			return realValue.equalsIgnoreCase(compareValue);
		}
		// 不等于
		if (compareType.equals("!=")) {
			return !realValue.equalsIgnoreCase(compareValue);
		}
		// 为null时只参与等于或不等于逻辑判断
		if (value == null) {
			return false;
		}
		// 大于等于
		if (compareType.equals(">=")) {
			return moreEqual(value, realValue, compareValue, type);
		}
		// 小于等于
		if (compareType.equals("<=")) {
			return lessEqual(value, realValue, compareValue, type);
		}
		// 大于
		if (compareType.equals(">")) {
			return more(value, realValue, compareValue, type);
		}
		// 小于
		if (compareType.equals("<")) {
			return less(value, realValue, compareValue, type);
		}
		// 包含
		if (compareType.equals("include")) {
			return include(value, realValue, compareValue, type);
		}
		// 在数组范围内
		if (compareType.equals("in")) {
			return in(value, realValue, compareValue, type);
		}
		// 在数组范围外
		if (compareType.equals("out")) {
			return out(value, realValue, compareValue, type);
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
		if (type.equals("time") || type.equals("date")) {
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
		if (type.equals("time") || type.equals("date")) {
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
		if (type.equals("time") || type.equals("date")) {
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
		if (type.equals("time") || type.equals("date")) {
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
}
