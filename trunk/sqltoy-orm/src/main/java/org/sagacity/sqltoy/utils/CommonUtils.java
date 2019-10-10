/**
 * 
 */
package org.sagacity.sqltoy.utils;

import java.io.File;
import java.io.FileInputStream;
import java.io.FileNotFoundException;
import java.io.InputStream;
import java.net.URL;
import java.util.Collection;
import java.util.Date;
import java.util.Enumeration;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

/**
 * @project sagacity-sqltoy4.0
 * @description 提供一些常用的函數處理
 * @author chenrenfei <a href="mailto:zhongxuchen@gmail.com">联系作者</a>
 * @version id:CommonUtils.java,Revision:v1.0,Date:2017年12月9日
 */
public class CommonUtils {
	/**
	 * 整数数字正则表达式
	 */
	private final static String INTEGER_REGEX = "^[+-]?[\\d]+$";

	/**
	 * 数字格式正则表达式(整数浮点数)
	 */
	private final static String NUMBER_REGEX = "^[+-]?[\\d]+(\\.\\d+)?$";

	/**
	 * @todo 判断字符串是整数
	 * @param obj
	 * @return
	 */
	public static boolean isInteger(String obj) {
		return StringUtil.matches(obj, INTEGER_REGEX);
	}

	/**
	 * @todo 判断字符串是否为数字
	 * @param numberStr
	 * @return
	 */
	public static boolean isNumber(String numberStr) {
		return StringUtil.matches(numberStr, NUMBER_REGEX);
	}

	/**
	 * @todo 产生随机数数组
	 * @param maxValue 随机数的最大值
	 * @param size     随机数的个数
	 * @return
	 */
	public static Object[] randomArray(int maxValue, int size) {
		int realSize = size;
		if (realSize > maxValue)
			realSize = maxValue;
		// 长度等于最大值
		if (realSize == maxValue) {
			Object[] result = new Object[maxValue];
			for (int i = 0; i < maxValue; i++) {
				result[i] = i;
			}
			return result;
		}
		Set<Integer> resultSet = new HashSet<Integer>(realSize);
		int randomNum;
		while (resultSet.size() < realSize) {
			randomNum = (int) (Math.random() * maxValue);
			resultSet.add(randomNum);
		}
		return resultSet.toArray();
	}

	/**
	 * @todo 按照概率获取对应概率的数据索引，如：A：概率80%，B：10%，C：6%，D：4%，将出现概率放入数组， 按随机规则返回对应概率的索引
	 * @param probabilities
	 * @return
	 */
	public static int getProbabilityIndex(int[] probabilities) {
		int total = 0;
		for (int probabilitiy : probabilities)
			total = total + probabilitiy;
		int randomData = (int) (Math.random() * total) + 1;
		int base = 0;
		for (int i = 0; i < probabilities.length; i++) {
			if (randomData > base && randomData <= base + probabilities[i])
				return i;
			base = base + probabilities[i];
		}
		return 0;
	}

	/**
	 * @todo 获得指定路径的文件
	 * @param file 文件路径like:classpath:xxx.xml或xxx.xml
	 * @return
	 */
	public static InputStream getFileInputStream(Object file) {
		if (file == null)
			return null;
		try {
			if (file instanceof InputStream)
				return (InputStream) file;
			if (file instanceof File)
				return new FileInputStream((File) file);

			String realFile = (String) file;
			if (StringUtil.isBlank(realFile))
				return null;
			// 文件路径
			if (new File(realFile).exists()) {
				return new FileInputStream(realFile);
			}
			if (StringUtil.indexOfIgnoreCase(realFile.trim(), "classpath:") == 0)
				realFile = realFile.trim().substring(10).trim();
			if (realFile.charAt(0) == '/')
				realFile = realFile.substring(1);
			InputStream result = Thread.currentThread().getContextClassLoader().getResourceAsStream(realFile);
			if (result == null) {
				try {
					Enumeration<URL> urls = Thread.currentThread().getContextClassLoader().getResources(realFile);
					URL url;
					while (urls.hasMoreElements()) {
						url = urls.nextElement();
						result = new FileInputStream(url.getFile());
						if (result != null)
							break;
					}
				} catch (Exception e) {
					e.printStackTrace();
				}
			}
			return result;
		} catch (FileNotFoundException fn) {
			fn.printStackTrace();
		}
		return null;
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
		if (logicParamCnt == 0)
			return true;
		Object value;
		for (int i = 0; i < logicParamCnt; i++) {
			value = paramValues.get(preCount + i);
			// 参数为null等价于#[]判断正确
			if ((value == null) || (value.getClass().isArray() && CollectionUtil.convertArray(value).length == 0)
					|| ((value instanceof Collection) && ((Collection) value).isEmpty())) {
				return false;
			}
		}
		// 规范判断符号标准
		sql = sql.replaceAll("\\<\\>", "!=").trim();
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
		if (sql.indexOf("||") != -1 && sql.indexOf("&&") != -1)
			return "undefine";
		// 比较符号(等于用==,最后用=进行容错处理)
		String[] compareStr = { "!=", "==", ">=", "<=", ">", "<", "=" };
		String splitStr = "==";
		String logicStr = "&&";
		String[] expressions;
		try {
			if (sql.indexOf("||") != -1)
				logicStr = "||";
			expressions = sql.split(logicStr);
			// 超过2个运算,交freemarker
			if (expressions.length > 2)
				return "undefine";
			boolean[] expressResult = new boolean[expressions.length];
			String express;
			Object value;
			String compareValue;
			for (int i = 0; i < expressions.length; i++) {
				value = paramValues.get(preCount + i);
				express = expressions[i].trim();
				for (int j = 0; j < compareStr.length; j++) {
					if (express.indexOf(compareStr[j]) != -1) {
						splitStr = compareStr[j];
						break;
					}
				}
				// update 2018-3-29,去除空格增强容错性
				compareValue = express.split(splitStr)[1].trim();
				// 计算单个比较的结果
				expressResult[i] = compare(value, splitStr, compareValue);
			}
			if (expressions.length == 1) {
				return (expressResult[0] ? "true" : "false");
			}
			// 只支持&& 和||
			if (logicStr.equals("&&")) {
				return ((expressResult[0] && expressResult[1]) ? "true" : "false");
			}
			return ((expressResult[0] || expressResult[1]) ? "true" : "false");
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
		// 判断是否有加减运算
		for (String calculate : calculateStr) {
			if (compareValue.trim().indexOf(calculate) > 0) {
				String[] tmpAry = compareValue.split(calculate.equals("+") ? "\\+" : "\\-");
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
		if (CommonUtils.isNumber(valueStr) && CommonUtils.isNumber(compare)) {
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
		if (CommonUtils.isNumber(valueStr) && CommonUtils.isNumber(compare)) {
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
		if (CommonUtils.isNumber(valueStr) && CommonUtils.isNumber(compare)) {
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
		if (CommonUtils.isNumber(valueStr) && CommonUtils.isNumber(compare)) {
			return Double.parseDouble(valueStr) < Double.parseDouble(compare);
		}
		return valueStr.compareTo(compare) < 0;
	}

	/**
	 * @todo 加工字段名称，将数据库sql查询的columnName转成对应对象的属性名称(去除下划线)
	 * @param labelNames
	 * @return
	 */
	public static String[] humpFieldNames(String[] labelNames) {
		if (labelNames == null)
			return null;
		String[] result = new String[labelNames.length];
		for (int i = 0, n = labelNames.length; i < n; i++) {
			result[i] = StringUtil.toHumpStr(labelNames[i], false);
		}
		return result;
	}
}
