package org.sagacity.sqltoy.utils;

import java.util.ArrayList;
import java.util.Collection;
import java.util.HashMap;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * @project sagacity-sqltoy
 * @description 字符串处理常用功能
 * @author zhongxuchen
 * @version v1.0,Date:Oct 19, 2007 10:09:42 AM
 * @modify {Date:2020-01-14,优化splitExcludeSymMark 方法,增加对\' 和 \" 符号的排除}
 * @modify {Date:2020-05-18,完整修复splitExcludeSymMark bug}
 * @modify {Date:2023-09-12,修复splitExcludeSymMark，以多字符切割的bug}
 */
@SuppressWarnings({ "rawtypes", "unchecked" })
public class StringUtil {
	/**
	 * 字符串中包含中文的表达式
	 */
	private static Pattern chinaPattern = Pattern.compile("[\u4e00-\u9fa5]");

	/**
	 * 单引号匹配正则表达式
	 */
	private static Pattern quotaPattern = Pattern.compile("(^')|([^\\\\]')");

	private static Pattern quotaChkPattern = Pattern.compile("[^\\\\]'");

	/**
	 * 双引号匹配正则表达式
	 */
	private static Pattern twoQuotaPattern = Pattern.compile("(^\")|([^\\\\]\")");

	private static Pattern twoQuotaChkPattern = Pattern.compile("[^\\\\]\"");

	/**
	 * private constructor,cann't be instantiated by other class 私有构造函数方法防止被实例化
	 */
	private StringUtil() {
	}

	public static String trim(String str) {
		if (str == null) {
			return null;
		}
		return str.trim();
	}

	/**
	 * @todo 将对象转为字符串排除null
	 * @param obj
	 * @return
	 */
	public static String toString(Object obj) {
		if (null == obj) {
			return "";
		}
		return obj.toString();
	}

	/**
	 * @todo 判断字符串是空或者空白
	 * @param str
	 * @return
	 */
	public static boolean isNotBlank(Object str) {
		return !isBlank(str);
	}

	public static boolean isBlank(Object str) {
		if (null == str) {
			return true;
		}
		if ((str instanceof CharSequence) && "".equals(str.toString().trim())) {
			return true;
		}
		// 下面做了一些冗余性校验
		if ((str instanceof Collection) && ((Collection) str).isEmpty()) {
			return true;
		}
		if ((str instanceof Map) && ((Map) str).isEmpty()) {
			return true;
		}
		if ((str instanceof Object[]) && ((Object[]) str).length == 0) {
			return true;
		}
		return false;
	}

	/**
	 * @todo 替换换行、回车、tab符号;\r回车 、\t tab符合、\n 换行
	 * @param source
	 * @param target
	 * @return
	 */
	public static String clearMistyChars(String source, String target) {
		if (source == null) {
			return null;
		}
		return source.replaceAll("\t|\r|\n", target);
	}

	/**
	 * @todo 返回第一个字符大写，其余保持不变的字符串
	 * @param sourceStr
	 * @return
	 */
	public static String firstToUpperCase(String sourceStr) {
		if (isBlank(sourceStr)) {
			return sourceStr;
		}
		if (sourceStr.length() == 1) {
			return sourceStr.toUpperCase();
		}
		return sourceStr.substring(0, 1).toUpperCase().concat(sourceStr.substring(1));
	}

	/**
	 * @todo 返回第一个字符小写，其余保持不变的字符串
	 * @param sourceStr
	 * @return
	 */
	public static String firstToLowerCase(String sourceStr) {
		if (isBlank(sourceStr)) {
			return sourceStr;
		}
		if (sourceStr.length() == 1) {
			return sourceStr.toLowerCase();
		}
		return sourceStr.substring(0, 1).toLowerCase().concat(sourceStr.substring(1));
	}

	/**
	 * @todo 返回第一个字符大写，其余保持不变的字符串
	 * @param sourceStr
	 * @return
	 */
	public static String firstToUpperOtherToLower(String sourceStr) {
		if (isBlank(sourceStr)) {
			return sourceStr;
		}
		if (sourceStr.length() == 1) {
			return sourceStr.toUpperCase();
		}
		return sourceStr.substring(0, 1).toUpperCase().concat(sourceStr.substring(1).toLowerCase());
	}

	/**
	 * @todo 在不分大小写情况下字符所在位置
	 * @param source
	 * @param pattern
	 * @return
	 */
	public static int indexOfIgnoreCase(String source, String pattern) {
		if (source == null || pattern == null) {
			return -1;
		}
		return source.toLowerCase().indexOf(pattern.toLowerCase());
	}

	public static int indexOfIgnoreCase(String source, String pattern, int start) {
		if (source == null || pattern == null) {
			return -1;
		}
		return source.toLowerCase().indexOf(pattern.toLowerCase(), start);
	}

	/**
	 * @todo 左补零
	 * @param source
	 * @param length
	 * @return
	 */
	public static String addLeftZero2Len(String source, int length) {
		return addSign2Len(source, length, 0, 0);
	}

	public static String addRightZero2Len(String source, int length) {
		return addSign2Len(source, length, 0, 1);
	}

	/**
	 * @todo 用空字符给字符串补足不足指定长度部分
	 * @param source
	 * @param length
	 * @return
	 */
	public static String addRightBlank2Len(String source, int length) {
		return addSign2Len(source, length, 1, 1);
	}

	/**
	 * @param source
	 * @param length
	 * @param flag
	 * @param leftOrRight
	 * @return
	 */
	private static String addSign2Len(String source, int length, int flag, int leftOrRight) {
		if (source == null || source.length() >= length) {
			return source;
		}
		int addSize = length - source.length();
		StringBuilder addStr = new StringBuilder();
		// 右边
		if (leftOrRight == 1) {
			addStr.append(source);
		}
		String sign = (flag == 1) ? " " : "0";
		for (int i = 0; i < addSize; i++) {
			addStr.append(sign);
		}
		// 左边
		if (leftOrRight == 0) {
			addStr.append(source);
		}
		return addStr.toString();
	}

	/**
	 * @todo <b>用特定符号循环拼接指定的字符串</b>
	 * @date 2012-7-12 下午10:17:30
	 * @param source
	 * @param sign
	 * @param loopSize
	 * @return
	 */
	public static String loopAppendWithSign(String source, String sign, int loopSize) {
		if (loopSize == 0) {
			return "";
		}
		if (loopSize == 1) {
			return source;
		}
		StringBuilder result = new StringBuilder(source);
		for (int i = 1; i < loopSize; i++) {
			result.append(sign).append(source);
		}
		return result.toString();
	}

	/**
	 * @todo 补字符(限单字符)
	 * @param source
	 * @param sign
	 * @param size
	 * @param isLeft
	 */
	public static String appendStr(String source, String sign, int size, boolean isLeft) {
		int length = 0;
		StringBuilder addStr = new StringBuilder("");
		String tmpStr = "";
		if (source != null) {
			length = source.length();
			tmpStr = source;
		}
		if (!isLeft) {
			addStr.append(tmpStr);
		}
		for (int i = 0; i < size - length; i++) {
			addStr.append(sign);
		}
		if (isLeft) {
			addStr.append(tmpStr);
		}
		return addStr.toString();
	}

	/**
	 * @todo 查询对称标记符号的位置，startIndex必须是<source.indexOf(beginMarkSign)
	 * @param beginMarkSign
	 * @param endMarkSign
	 * @param source
	 * @param startIndex
	 * @return
	 */
	public static int getSymMarkIndex(String beginMarkSign, String endMarkSign, String source, int startIndex) {
		Pattern pattern = null;
		Pattern chkPattern = null;
		// 单引号和双引号，排除\' 和 \"
		if ("'".equals(beginMarkSign)) {
			pattern = quotaPattern;
			chkPattern = quotaChkPattern;
		} else if ("\"".equals(beginMarkSign)) {
			pattern = twoQuotaPattern;
			chkPattern = twoQuotaChkPattern;
		}
		// 判断对称符号是否相等
		boolean symMarkIsEqual = beginMarkSign.equals(endMarkSign) ? true : false;
		int beginSignIndex = -1;
		if (pattern == null) {
			beginSignIndex = source.indexOf(beginMarkSign, startIndex);
		} else {
			beginSignIndex = matchIndex(source, pattern, startIndex)[0];
			// 转义符号占一位,开始位后移一位
			if (beginSignIndex > startIndex) {
				beginSignIndex = beginSignIndex + 1;
			}
		}
		if (beginSignIndex == -1) {
			return source.indexOf(endMarkSign, startIndex);
		}
		int endIndex = -1;
		if (pattern == null) {
			endIndex = source.indexOf(endMarkSign, beginSignIndex + 1);
		} else {
			endIndex = matchIndex(source, pattern, beginSignIndex + 1)[0];
			// 转义符号占一位,开始位后移一位
			if (endIndex > beginSignIndex + 1) {
				endIndex = endIndex + 1;
			} else if (endIndex == beginSignIndex + 1) {
				if (matchIndex(source, chkPattern, beginSignIndex + 1)[0] == endIndex) {
					endIndex = endIndex + 1;
				}
			}
		}
		int preEndIndex = 0;
		while (endIndex > beginSignIndex) {
			// 寻找下一个开始符号
			if (pattern == null) {
				beginSignIndex = source.indexOf(beginMarkSign, (symMarkIsEqual ? endIndex : beginSignIndex) + 1);
			} else {
				beginSignIndex = matchIndex(source, pattern, endIndex + 1)[0];
				// 转义符号占一位,开始位后移一位
				if (beginSignIndex > endIndex + 1) {
					beginSignIndex = beginSignIndex + 1;
				} else if (beginSignIndex == endIndex + 1) {
					if (matchIndex(source, chkPattern, endIndex + 1)[0] == beginSignIndex) {
						beginSignIndex = beginSignIndex + 1;
					}
				}
			}

			// 找不到或则下一个开始符号位置大于截止符号则返回
			if (beginSignIndex == -1 || beginSignIndex > endIndex) {
				return endIndex;
			}
			// 记录上一个截止位置
			preEndIndex = endIndex;
			// 开始符号在截止符号前则寻找下一个截止符号
			if (pattern == null) {
				endIndex = source.indexOf(endMarkSign, (symMarkIsEqual ? beginSignIndex : endIndex) + 1);
			} else {
				endIndex = matchIndex(source, pattern, beginSignIndex + 1)[0];
				// 转义符号占一位,开始位后移一位
				if (endIndex > beginSignIndex + 1) {
					endIndex = endIndex + 1;
				} else if (endIndex == beginSignIndex + 1) {
					if (matchIndex(source, chkPattern, beginSignIndex + 1)[0] == endIndex) {
						endIndex = endIndex + 1;
					}
				}
			}
			// 找不到则返回上一个截止位置
			if (endIndex == -1) {
				return preEndIndex;
			}
		}
		return endIndex;
	}

	/**
	 * @todo 查询对称标记符号的位置
	 * @param beginMarkSign
	 * @param endMarkSign
	 * @param source
	 * @param startIndex
	 * @return
	 */
	public static int getSymMarkIndexIgnoreCase(String beginMarkSign, String endMarkSign, String source,
			int startIndex) {
		return getSymMarkIndex(beginMarkSign.toLowerCase(), endMarkSign.toLowerCase(), source.toLowerCase(),
				startIndex);
	}

	/**
	 * @todo 查询对称标记符号的位置
	 * @param beginMarkSign
	 * @param endMarkSign
	 * @param source
	 * @param startIndex
	 * @return
	 */
	public static int getSymMarkMatchIndex(String beginMarkSign, String endMarkSign, String source, int startIndex) {
		// 判断对称符号是否相等
		boolean symMarkIsEqual = beginMarkSign.equals(endMarkSign) ? true : false;
		Pattern startP = Pattern.compile(beginMarkSign);
		Pattern endP = Pattern.compile(endMarkSign);
		int[] beginSignIndex = matchIndex(source, startP, startIndex);
		if (beginSignIndex[0] == -1) {
			return matchIndex(source, endP, startIndex)[0];
		}
		int[] endIndex = matchIndex(source, endP, beginSignIndex[1]);
		int[] tmpIndex = { 0, 0 };
		while (endIndex[0] > beginSignIndex[0]) {
			// 寻找下一个开始符号
			beginSignIndex = matchIndex(source, startP, (symMarkIsEqual ? endIndex[1] : beginSignIndex[1]));
			// 找不到或则下一个开始符号位置大于截止符号则返回
			if (beginSignIndex[0] == -1 || beginSignIndex[0] > endIndex[0]) {
				return endIndex[0];
			}
			tmpIndex = endIndex;
			// 开始符号在截止符号前则寻找下一个截止符号
			endIndex = matchIndex(source, endP, (symMarkIsEqual ? beginSignIndex[1] : endIndex[1]));
			// 找不到则返回
			if (endIndex[0] == -1) {
				return tmpIndex[0];
			}
		}
		return endIndex[0];
	}

	/**
	 * @todo 逆向查询对称标记符号的位置
	 * @param beginMarkSign
	 * @param endMarkSign
	 * @param source
	 * @param endIndex      主要endMarkSign的length,一般lastIndex(sign)+sign.length()
	 * @return
	 */
	public static int getSymMarkReverseIndex(String beginMarkSign, String endMarkSign, String source, int endIndex) {
		int beginIndex = source.length() - endIndex;
		String realSource = new StringBuffer(source).reverse().toString();
		String realStartMark = beginMarkSign;
		String realEndMark = endMarkSign;
		if (realStartMark.length() > 1) {
			realStartMark = new StringBuffer(realStartMark).reverse().toString();
		}
		if (realEndMark.length() > 1) {
			realEndMark = new StringBuffer(realEndMark).reverse().toString();
		}
		int index = getSymMarkIndex(realEndMark, realStartMark, realSource, beginIndex < 0 ? 0 : beginIndex);
		return source.length() - index - realStartMark.length();
	}

	/**
	 * @todo 剔除字符串中对称符号和中间的内容
	 * @param sql
	 * @param startMark
	 * @param endMark
	 * @return
	 */
	public static String clearSymMarkContent(String sql, String startMark, String endMark) {
		StringBuilder lastSql = new StringBuilder(sql);
		int endMarkLength = endMark.length();
		// 删除所有对称的括号中的内容
		int start = lastSql.indexOf(startMark);
		int symMarkEnd;
		while (start != -1) {
			symMarkEnd = StringUtil.getSymMarkIndex(startMark, endMark, lastSql.toString(), start);
			if (symMarkEnd != -1) {
				lastSql.delete(start, symMarkEnd + endMarkLength);
				start = lastSql.indexOf(startMark);
			} else {
				break;
			}
		}
		return lastSql.toString();
	}

	/**
	 * @todo 通过正则表达式判断是否匹配
	 * @param source
	 * @param regex
	 * @return
	 */
	public static boolean matches(String source, String regex) {
		return matches(source, Pattern.compile(regex));
	}

	/**
	 * @todo 通过正则表达式判断是否匹配
	 * @param source
	 * @param pattern
	 * @return
	 */
	public static boolean matches(String source, Pattern pattern) {
		if (isBlank(source)) {
			return false;
		}
		return pattern.matcher(source).find();
	}

	/**
	 * @todo 找到匹配的位置
	 * @param source
	 * @param regex
	 * @return
	 */
	public static int matchIndex(String source, String regex) {
		return matchIndex(source, Pattern.compile(regex));
	}

	public static int[] matchIndex(String source, String regex, int start) {
		return matchIndex(source, Pattern.compile(regex), start);
	}

	public static int matchIndex(String source, Pattern pattern) {
		Matcher m = pattern.matcher(source);
		if (m.find()) {
			return m.start();
		}
		return -1;
	}

	public static int[] matchIndex(String source, Pattern pattern, int start) {
		if (source.length() <= start) {
			return new int[] { -1, -1 };
		}
		Matcher m = pattern.matcher(source.substring(start));
		if (m.find()) {
			return new int[] { m.start() + start, m.end() + start };
		}
		return new int[] { -1, -1 };
	}

	public static int matchLastIndex(String source, String regex) {
		return matchLastIndex(source, Pattern.compile(regex), 0);
	}

	public static int matchLastIndex(String source, Pattern pattern) {
		return matchLastIndex(source, pattern, 0);
	}

	public static int matchLastIndex(String source, Pattern pattern, int offset) {
		if (source == null) {
			return -1;
		}
		Matcher m = pattern.matcher(source);
		int matchIndex = -1;
		int start = 0;
		while (m.find(start)) {
			matchIndex = m.start();
			start = m.end() - offset;
		}
		return matchIndex;
	}

	/**
	 * @todo 获取匹配成功的个数
	 * @param source
	 * @param regex
	 * @return
	 */
	public static int matchCnt(String source, String regex) {
		return matchCnt(source, Pattern.compile(regex), 0);
	}

	/**
	 * @todo 获取匹配成功的个数
	 * @param source
	 * @param pattern
	 * @return
	 */
	public static int matchCnt(String source, Pattern pattern) {
		return matchCnt(source, pattern, 0);
	}

	/**
	 * @todo 获取匹配成功的个数
	 * @param source
	 * @param pattern
	 * @param offset
	 * @return
	 */
	public static int matchCnt(String source, Pattern pattern, int offset) {
		if (source == null) {
			return 0;
		}
		Matcher matcher = pattern.matcher(source);
		int count = 0;
		int start = 0;
		while (matcher.find(start)) {
			count++;
			start = matcher.end() - offset;
		}
		return count;
	}

	/**
	 * @todo 获取匹配成功的个数
	 * @param source
	 * @param regex
	 * @param beginIndex
	 * @param endIndex
	 * @return
	 */
	public static int matchCnt(String source, String regex, int beginIndex, int endIndex) {
		return matchCnt(source.substring(beginIndex, endIndex), Pattern.compile(regex), 0);
	}

	public static int matchCnt(String source, String regex, int beginIndex, int endIndex, int offset) {
		return matchCnt(source.substring(beginIndex, endIndex), Pattern.compile(regex), offset);
	}

	/**
	 * @todo 获取字符指定次数的位置
	 * @param source
	 * @param regex
	 * @param order
	 * @return
	 */
	public static int indexOrder(String source, String regex, int order) {
		int begin = 0;
		int count = 0;
		int index = source.indexOf(regex, begin);
		while (index != -1) {
			if (count == order) {
				return index;
			}
			begin = index + 1;
			index = source.indexOf(regex, begin);
			count++;
		}
		return -1;
	}

	/**
	 * @todo 字符串转ASCII
	 * @param str
	 * @return
	 */
	public static int[] str2ASCII(String str) {
		char[] chars = str.toCharArray(); // 把字符中转换为字符数组
		int[] result = new int[chars.length];
		for (int i = 0; i < chars.length; i++) {// 输出结果
			result[i] = (int) chars[i];
		}
		return result;
	}

	/**
	 * @todo 切割字符串，排除特殊字符对，如a,b,c,dd(a,c),dd(a,c)不能切割
	 * @param source
	 * @param splitSign 如逗号、分号、冒号或具体字符串,非正则表达式
	 * @param filterMap
	 * @return
	 */
	public static String[] splitExcludeSymMark(String source, String splitSign, HashMap filterMap) {
		if (source == null) {
			return null;
		}
		int splitIndex = source.indexOf(splitSign);
		if (splitIndex == -1) {
			return new String[] { source };
		}
		if (filterMap == null || filterMap.isEmpty()) {
			if ("?".equals(splitSign)) {
				return source.split("\\?");
			} else if (",".equals(splitSign)) {
				return source.split("\\,");
			} else if (";".equals(splitSign)) {
				return source.split("\\;");
			} else if (":".equals(splitSign)) {
				return source.split("\\:");
			} else if ("".equals(splitSign.trim())) {
				return source.split("\\s+");
			} else if ("||".equals(splitSign.trim())) {
				return source.split("\\|{2}");
			} else if ("&&".equals(splitSign.trim())) {
				return source.split("\\&{2}");
			} else {
				return source.split(splitSign);
			}
		}
		List<String[]> filters = matchFilters(source, filterMap);
		if (filters.isEmpty()) {
			if ("?".equals(splitSign)) {
				return source.split("\\?");
			} else if (",".equals(splitSign)) {
				return source.split("\\,");
			} else if (";".equals(splitSign)) {
				return source.split("\\;");
			} else if (":".equals(splitSign)) {
				return source.split("\\:");
			} else if ("".equals(splitSign.trim())) {
				return source.split("\\s+");
			} else if ("||".equals(splitSign.trim())) {
				return source.split("\\|{2}");
			} else if ("&&".equals(splitSign.trim())) {
				return source.split("\\&{2}");
			} else {
				return source.split(splitSign);
			}
		}
		int splitSignLen = splitSign.length();
		int start = 0;
		int skipIndex = 0;
		int preSplitIndex = splitIndex;
		ArrayList splitResults = new ArrayList();
		int max = -1;
		int[] startEnd;
		while (splitIndex != -1) {
			max = -1;
			for (String[] filter : filters) {
				startEnd = getStartEndIndex(source, filter, skipIndex, splitIndex);
				// 分隔符号在对称符号的首尾中间,表示分隔符号属于内部字符串,在对称符号的终止位置后面重新获取分隔符号的位置
				if (startEnd[0] >= 0 && startEnd[0] <= splitIndex && startEnd[1] >= splitIndex && startEnd[1] > max) {
					max = startEnd[1];
				}
			}
			if (max > -1) {
				// 对称符号后移动1位
				skipIndex = max + 1;
				splitIndex = source.indexOf(splitSign, skipIndex);
			}
			// 分隔符号位置没有变化，表示其不在对称符号中间
			if (preSplitIndex == splitIndex) {
				// 切割分隔符前部分
				splitResults.add(source.substring(start, preSplitIndex));
				// 重新记录下一次开始切割位置
				start = preSplitIndex + splitSignLen;
				skipIndex = start;
				splitIndex = source.indexOf(splitSign, skipIndex);
				preSplitIndex = splitIndex;
			} else {
				preSplitIndex = splitIndex;
			}
		}
		splitResults.add(source.substring(start));
		String[] resultStr = new String[splitResults.size()];
		for (int j = 0; j < splitResults.size(); j++) {
			resultStr[j] = (String) splitResults.get(j);
		}
		return resultStr;
	}

	/**
	 * @TODO 获取对称符号的开始和结束位置
	 * @param source
	 * @param filter
	 * @param skipIndex
	 * @param splitIndex
	 * @return
	 */
	private static int[] getStartEndIndex(String source, String[] filter, int skipIndex, int splitIndex) {
		int[] result = { -1, -1 };
		Pattern pattern = null;
		if ("'".equals(filter[0])) {
			pattern = quotaPattern;
		} else if ("\"".equals(filter[0])) {
			pattern = twoQuotaPattern;
		}
		String tmp;
		if (pattern == null) {
			result[0] = source.indexOf(filter[0], skipIndex);
			if (result[0] >= 0) {
				result[1] = getSymMarkIndex(filter[0], filter[1], source, skipIndex);
			}
		} else {
			result[0] = matchIndex(source, pattern, skipIndex)[0];
			if (result[0] >= 0) {
				tmp = source.substring(result[0], result[0] + 1);
				if (!"'".equals(tmp) && !"\"".equals(tmp)) {
					result[0] = result[0] + 1;
				}
				result[1] = getSymMarkIndex(filter[0], filter[1], source, result[0]);
			}
		}
		while (result[1] > 0 && result[1] < splitIndex) {
			if (pattern == null) {
				// 非正则表达式,往后移动一位
				result[0] = source.indexOf(filter[0], result[1] + 1);
				if (result[0] > 0) {
					result[1] = getSymMarkIndex(filter[0], filter[1], source, result[0]);
				} else {
					result[1] = -1;
				}
			} else {
				tmp = source.substring(result[1], result[1] + 1);
				if (!"'".equals(tmp) && !"\"".equals(tmp)) {
					result[0] = matchIndex(source, pattern, result[1] + 2)[0];
				} else {
					result[0] = matchIndex(source, pattern, result[1] + 1)[0];
				}
				// 正则表达式有一个转义符号占一位
				if (result[0] > 0) {
					tmp = source.substring(result[0], result[0] + 1);
					if (!"'".equals(tmp) && !"\"".equals(tmp)) {
						result[0] = result[0] + 1;
					}
					result[1] = getSymMarkIndex(filter[0], filter[1], source, result[0]);
				} else {
					result[1] = -1;
				}
			}
		}
		return result;
	}

	/**
	 * @TODO 匹配有效的过滤器
	 * @param source
	 * @param filterMap
	 * @return
	 */
	public static List<String[]> matchFilters(String source, HashMap filterMap) {
		List<String[]> result = new ArrayList<String[]>();
		Iterator iter = filterMap.entrySet().iterator();
		String beginSign;
		String endSign;
		int beginSignIndex;
		int endSignIndex;
		Map.Entry entry;
		Pattern pattern;
		Pattern chkPattern;
		// 排除不存在的过滤对称符号
		while (iter.hasNext()) {
			entry = (Map.Entry) iter.next();
			beginSign = (String) entry.getKey();
			endSign = (String) entry.getValue();
			pattern = null;
			chkPattern = null;
			if ("'".equals(beginSign)) {
				pattern = quotaPattern;
				chkPattern = quotaChkPattern;
			} else if ("\"".equals(beginSign)) {
				pattern = twoQuotaPattern;
				chkPattern = twoQuotaChkPattern;
			}
			endSignIndex = -1;
			if (pattern == null) {
				beginSignIndex = source.indexOf(beginSign);
				if (beginSignIndex > -1) {
					endSignIndex = source.indexOf(endSign, beginSignIndex + 1);
				}
			} else {
				beginSignIndex = matchIndex(source, pattern);
				// 转义符号占一位,开始位后移一位
				if (beginSignIndex > -1) {
					beginSignIndex = beginSignIndex + 1;
					endSignIndex = matchIndex(source, pattern, beginSignIndex + 1)[0];
					// 转义符号占一位,开始位后移一位
					if (endSignIndex > beginSignIndex + 1) {
						endSignIndex = endSignIndex + 1;
					} else if (endSignIndex == beginSignIndex + 1) {
						if (matchIndex(source, chkPattern, beginSignIndex + 1)[0] == endSignIndex) {
							endSignIndex = endSignIndex + 1;
						}
					}
				}
			}
			if (beginSignIndex != -1 && endSignIndex != -1) {
				result.add(new String[] { beginSign, endSign });
			}
		}
		return result;
	}

	public static String toHumpStr(String source, boolean firstIsUpperCase) {
		return toHumpStr(source, firstIsUpperCase, true);
	}

	/**
	 * @todo 将字符串转换成驼峰形式
	 * @param source
	 * @param firstIsUpperCase
	 * @param removeDealine
	 * @return
	 */
	public static String toHumpStr(String source, boolean firstIsUpperCase, boolean removeDealine) {
		if (isBlank(source)) {
			return source;
		}
		// update 2018-3-22 将-符号统一成_
		String[] humpAry = source.trim().replace("-", "_").split("\\_");
		String cell;
		StringBuilder result = new StringBuilder();
		for (int i = 0; i < humpAry.length; i++) {
			cell = humpAry[i];
			if (i > 0 && !removeDealine) {
				result.append("_");
			}
			// 全大写或全小写
			if (cell.toUpperCase().equals(cell)) {
				result.append(firstToUpperOtherToLower(cell));
			} else {
				result.append(firstToUpperCase(cell));
			}
		}
		// 首字母变大写
		if (firstIsUpperCase) {
			return firstToUpperCase(result.toString());
		}
		return firstToLowerCase(result.toString());
	}

	/**
	 * @todo 通过特殊符号对字符进行安全模糊化处理
	 * @param value
	 * @param preLength
	 * @param tailLength
	 * @param maskStr
	 * @return
	 */
	public static String secureMask(Object value, int preLength, int tailLength, String maskStr) {
		if (value == null) {
			return null;
		}
		String tmp = value.toString();
		if (tmp.length() <= preLength + tailLength) {
			return tmp;
		}
		return tmp.substring(0, preLength).concat((maskStr == null || "".equals(maskStr)) ? "***" : maskStr)
				.concat(tmp.substring(tmp.length() - tailLength));
	}

	/**
	 * @todo 判断字符串中是否包含中文
	 * @param str
	 * @return
	 */
	public static boolean hasChinese(String str) {
		if (chinaPattern.matcher(str).find()) {
			return true;
		}
		return false;
	}

	/**
	 * @todo 驼峰形式字符用分割符号链接,example:humpToSplitStr("organInfo","_") result:organ_Info
	 * @param source
	 * @param split
	 * @return
	 */
	public static String humpToSplitStr(String source, String split) {
		if (source == null) {
			return null;
		}
		char[] chars = source.trim().toCharArray();
		StringBuilder result = new StringBuilder();
		int charInt;
		int uperCaseCnt = 0;
		for (int i = 0; i < chars.length; i++) {
			charInt = chars[i];
			if (charInt >= 65 && charInt <= 90) {
				uperCaseCnt++;
			} else {
				uperCaseCnt = 0;
			}
			// 连续大写
			if (uperCaseCnt == 1 && i != 0) {
				result.append(split);
			}
			result.append(Character.toString(chars[i]));
		}
		return result.toString();
	}

	/**
	 * @todo 加工字段名称，将数据库sql查询的columnName转成对应对象的属性名称(去除下划线)
	 * @param labelNames
	 * @return
	 */
	public static String[] humpFieldNames(String[] labelNames) {
		if (labelNames == null) {
			return null;
		}
		String[] result = new String[labelNames.length];
		int aliasIndex = 0;
		for (int i = 0, n = labelNames.length; i < n; i++) {
			aliasIndex = labelNames[i].indexOf(":");
			if (aliasIndex != -1) {
				result[i] = toHumpStr(labelNames[i].substring(aliasIndex + 1), false);
			} else {
				result[i] = toHumpStr(labelNames[i], false);
			}
		}
		return result;
	}

	/**
	 * @todo 填充args参数
	 * @param template
	 * @param args
	 * @return
	 */
	public static String fillArgs(String template, Object... args) {
		if (template == null || (args == null || args.length == 0)) {
			return template;
		}
		for (Object arg : args) {
			template = template.replaceFirst("\\$?\\{\\s*\\}",
					(arg == null) ? "null" : Matcher.quoteReplacement(arg.toString()));
		}
		return template;
	}

	/**
	 * @todo 针对jdk1.4 replace(char,char)提供jdk1.5中replace(String,String)的功能
	 * @param source
	 * @param template
	 * @param target
	 * @return
	 */
	public static String replaceAllStr(String source, String template, String target) {
		return replaceAllStr(source, template, target, 0);
	}

	public static String replaceAllStr(String source, String template, String target, int fromIndex) {
		if (source == null || template.equals(target)) {
			return source;
		}
		int index = source.indexOf(template, fromIndex);
		int subLength = target.length() - template.length();
		int begin = index - 1;
		while (index != -1 && index >= begin) {
			source = source.substring(0, index).concat(target).concat(source.substring(index + template.length()));
			begin = index + subLength + 1;
			index = source.indexOf(template, begin);
		}
		return source;
	}

	public static String replaceAllStr(String source, String template, String target, int fromIndex, int endIndex) {
		if (source == null || template.equals(target) || endIndex <= fromIndex) {
			return source;
		}
		if (endIndex >= source.length() - 1) {
			return replaceAllStr(source, template, target, fromIndex);
		}
		String beforeStr = (fromIndex == 0) ? "" : source.substring(0, fromIndex);
		String replaceBody = source.substring(fromIndex, endIndex + 1);
		String endStr = source.substring(endIndex + 1);
		int index = replaceBody.indexOf(template);
		int begin = index - 1;
		// 替换后的偏移量，避免在替换内容中再次替换形成死循环
		int subLength = target.length() - template.length();
		while (index != -1 && index >= begin) {
			replaceBody = replaceBody.substring(0, index).concat(target)
					.concat(replaceBody.substring(index + template.length()));
			begin = index + subLength + 1;
			index = replaceBody.indexOf(template, begin);
		}
		return beforeStr.concat(replaceBody).concat(endStr);
	}

	/**
	 * @TODO 替换部分全角字符为半角
	 * @param SBCStr
	 * @return
	 */
	public static String toDBC(String SBCStr) {
		if (isBlank(SBCStr)) {
			return SBCStr;
		}
		// 常用符号进行全角转半角
		return SBCStr.replaceAll("\\；", ";").replaceAll("\\？", "?").replaceAll("\\．", ".").replaceAll("\\：", ":")
				.replaceAll("\\＇", "'").replaceAll("\\＂", "\"").replaceAll("\\，", ",").replaceAll("\\【", "[")
				.replaceAll("\\】", "]").replaceAll("\\）", ")").replaceAll("\\（", "(").replaceAll("\\＝", "=");
	}

	/**
	 * @TODO 字符连接
	 * @param sign
	 * @param skipNull
	 * @param arys
	 * @return
	 */
	public static String linkAry(String sign, boolean skipNull, Object... arys) {
		if (arys == null || arys.length == 0) {
			return "";
		}
		String linkSign = (sign == null) ? "," : sign;
		int index = 0;
		StringBuilder result = new StringBuilder();
		for (Object str : arys) {
			if (str != null || !skipNull) {
				if (index > 0) {
					result.append(linkSign);
				}
				result.append((str == null) ? "null" : str.toString());
				index++;
			}
		}
		return result.toString();
	}

	/**
	 * @TODO 提供类似于sql中的like功能
	 * @param source
	 * @param keywords 将匹配的字符用空格或者%进行切割并trim变成字符数组进行匹配
	 * @return
	 */
	public static boolean like(String source, String[] keywords) {
		int index = 0;
		for (String keyword : keywords) {
			index = source.indexOf(keyword, index);
			if (index == -1) {
				return false;
			}
			// 位置从前一个匹配字符的尾部开始
			index = index + keyword.length();
		}
		return true;
	}

	public static void arrayTrim(String[] params) {
		if (params == null || params.length == 0) {
			return;
		}
		for (int i = 0; i < params.length; i++) {
			params[i] = params[i].trim();
		}
	}

	/**
	 * @TODO 将字符串进行正则表达式切割
	 * @param source
	 * @param regex
	 * @param doTrim
	 * @return
	 */
	public static String[] splitRegex(String source, String regex, boolean doTrim) {
		if (source == null) {
			return null;
		}
		String[] result;
		if ("?".equals(regex)) {
			result = source.split("\\?");
		} else if (",".equals(regex)) {
			result = source.split("\\,");
		} else if (";".equals(regex)) {
			result = source.split("\\;");
		} else if (":".equals(regex)) {
			result = source.split("\\:");
		} else if ("".equals(regex.trim())) {
			result = source.split("\\s+");
		} else {
			result = source.split(regex);
		}
		if (doTrim) {
			for (int i = 0; i < result.length; i++) {
				result[i] = result[i].trim();
			}
		}
		return result;
	}

	/**
	 * @TODO 处理空白和null，给与默认值
	 * @param value
	 * @param defaultValue
	 * @return
	 */
	public static String ifBlank(String value, String defaultValue) {
		if (isBlank(value)) {
			return defaultValue;
		}
		return value;
	}
}
