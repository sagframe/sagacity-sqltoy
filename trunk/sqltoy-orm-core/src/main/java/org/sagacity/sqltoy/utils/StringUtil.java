package org.sagacity.sqltoy.utils;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collection;
import java.util.Collections;
import java.util.Iterator;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * @project sagacity-sqltoy
 * @description 字符串处理常用功能
 * @author zhongxuchen
 * @version v1.0,Date:Oct 19, 2007 10:09:42 AM
 * @modify Date:2020-01-14 优化splitExcludeSymMark 方法,增加对\' 和 \" 符号的排除
 * @modify Date:2020-05-18 完整修复splitExcludeSymMark bug
 * @modify Date:2023-09-12 修复splitExcludeSymMark，以多字符切割的bug
 */
@SuppressWarnings({ "rawtypes" })
public class StringUtil {
	/**
	 * 字符串中包含中文的表达式
	 */
	private static final Pattern chinaPattern = Pattern.compile("[\u4e00-\u9fa5]");

	/**
	 * 单引号匹配正则表达式
	 */
	private static final Pattern quotaPattern = Pattern.compile("(^')|([^\\\\]')");

	private static final Pattern quotaChkPattern = Pattern.compile("[^\\\\]'");

	/**
	 * 双引号匹配正则表达式
	 */
	private static final Pattern twoQuotaPattern = Pattern.compile("(^\")|([^\\\\]\")");

	private static final Pattern twoQuotaChkPattern = Pattern.compile("[^\\\\]\"");

	// 字符串regex重载的编译缓存:调用方(DateUtil日期解析等热路径)传入的均为常量正则,
	// 避免每次Pattern.compile;超出上限时直接编译,防御极端场景下动态正则撑爆缓存
	private static final ConcurrentHashMap<String, Pattern> PATTERN_CACHE = new ConcurrentHashMap<String, Pattern>();

	private static Pattern patternOf(String regex) {
		if (PATTERN_CACHE.size() > 1000) {
			return Pattern.compile(regex);
		}
		return PATTERN_CACHE.computeIfAbsent(regex, Pattern::compile);
	}

	/**
	 * private constructor,cann't be instantiated by other class 私有构造函数方法防止被实例化
	 */
	private StringUtil() {
	}

	/**
	 * 转义注释中的特殊字符（修复:逐字符检测，避免重复转义+部分转义遗漏）
	 *
	 * @param commentStr 原始注释字符串（可为null/空）
	 * @return 转义后的字符串，null/空输入返回原值
	 */
	public static String escapeComment(String commentStr) {
		// 1. 空值安全处理：null/空字符串直接返回
		if (commentStr == null || commentStr.isEmpty()) {
			return commentStr;
		}
		StringBuilder sb = new StringBuilder(commentStr.length() + 20);
		char[] chars = commentStr.toCharArray();
		for (int i = 0; i < chars.length; i++) {
			char c = chars[i];
			// 核心：如果是 \，并且下一个也是 \ → 已经转义，直接跳过
			if (c == '\\' && i < chars.length - 1 && chars[i + 1] == '\\') {
				sb.append("\\\\"); // 保留原样 \\
				i++; // 跳过下一个字符（因为已经一起处理）
			}
			// 普通 \ 未转义，需要转义
			else if (c == '\\') {
				sb.append("\\\\");
			}
			// 双引号必须转义
			else if (c == '"') {
				sb.append("\\\"");
			}
			// 其他字符原样保留
			else {
				sb.append(c);
			}
		}
		return sb.toString();
	}

	public static String trim(String str) {
		if (str == null) {
			return null;
		}
		return str.trim();
	}

	/**
	 * 字符串trim后比较是否相等
	 * 
	 * @param source 源字符串，可为null
	 * @param target 目标字符串，可为null
	 * @return trim后相等返回true；任一为null时仅当两者同为null返回true
	 */
	public static boolean trimedEquals(String source, String target) {
		if (source == null || target == null) {
			return source == target;
		}
		return source.trim().equals(target.trim());
	}

	public static boolean trimedIgnoreCaseEquals(String source, String target) {
		if (source == null || target == null) {
			return source == target;
		}
		return source.trim().equalsIgnoreCase(target.trim());
	}

	/**
	 * 将对象转为字符串排除null
	 * 
	 * @param obj 任意对象，可为null
	 * @return 对象的字符串形式，obj为null返回空字符串
	 */
	public static String toString(Object obj) {
		if (null == obj) {
			return "";
		}
		return obj.toString();
	}

	/**
	 * 判断字符串是空或者空白
	 * 
	 * @param str 待判断的对象，支持字符串、集合、Map和数组类型
	 * @return true表示不为空且不为空白；null、空白字符串、空集合、空Map、空数组均返回false
	 */
	public static boolean isNotBlank(Object str) {
		return !isBlank(str);
	}

	public static boolean isBlank(Object str) {
		if (null == str) {
			return true;
		}
		if (str instanceof CharSequence) {
			return str.toString().trim().isEmpty();
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
	 * 替换换行、回车、tab符号;\r回车 、\t tab符合、\n 换行
	 * 
	 * @param source 原始字符串，可为null
	 * @param target 用于替换\t、\r、\n的字符或字符串
	 * @return 替换后的字符串，source为null返回null
	 */
	public static String clearMistyChars(String source, String target) {
		if (source == null) {
			return null;
		}
		return source.replaceAll("\t|\r|\n", target);
	}

	/**
	 * 返回第一个字符大写，其余保持不变的字符串
	 * 
	 * @param sourceStr 原始字符串，空白时原样返回
	 * @return 首字符大写后的字符串
	 */
	public static String firstToUpperCase(String sourceStr) {
		if (isBlank(sourceStr)) {
			return sourceStr;
		}
		if (sourceStr.length() == 1) {
			return sourceStr.toUpperCase(Locale.ROOT);
		}
		return sourceStr.substring(0, 1).toUpperCase(Locale.ROOT).concat(sourceStr.substring(1));
	}

	/**
	 * 返回第一个字符小写，其余保持不变的字符串
	 * 
	 * @param sourceStr 原始字符串，空白时原样返回
	 * @return 首字符小写后的字符串
	 */
	public static String firstToLowerCase(String sourceStr) {
		if (isBlank(sourceStr)) {
			return sourceStr;
		}
		if (sourceStr.length() == 1) {
			return sourceStr.toLowerCase(Locale.ROOT);
		}
		return sourceStr.substring(0, 1).toLowerCase(Locale.ROOT).concat(sourceStr.substring(1));
	}

	/**
	 * 返回第一个字符大写，其余保持不变的字符串
	 * 
	 * @param sourceStr 原始字符串，空白时原样返回
	 * @return 首字符大写、其余字符全部小写后的字符串
	 */
	public static String firstToUpperOtherToLower(String sourceStr) {
		if (isBlank(sourceStr)) {
			return sourceStr;
		}
		if (sourceStr.length() == 1) {
			return sourceStr.toUpperCase(Locale.ROOT);
		}
		return sourceStr.substring(0, 1).toUpperCase(Locale.ROOT)
				.concat(sourceStr.substring(1).toLowerCase(Locale.ROOT));
	}

	/**
	 * 在不分大小写情况下字符所在位置
	 * 
	 * @param source  原始字符串，可为null
	 * @param pattern 待查找的字符串
	 * @return 不区分大小写首次出现的位置，source或pattern为null返回-1
	 */
	public static int indexOfIgnoreCase(String source, String pattern) {
		if (source == null || pattern == null) {
			return -1;
		}
		return source.toLowerCase(Locale.ROOT).indexOf(pattern.toLowerCase(Locale.ROOT));
	}

	public static int indexOfIgnoreCase(String source, String pattern, int start) {
		if (source == null || pattern == null) {
			return -1;
		}
		return source.toLowerCase(Locale.ROOT).indexOf(pattern.toLowerCase(Locale.ROOT), start);
	}

	/**
	 * 左补零
	 * 
	 * @param source 原始字符串，null时原样返回
	 * @param length 目标长度
	 * @return 左侧补零至指定长度的字符串，长度已不小于目标长度时原样返回
	 */
	public static String addLeftZero2Len(String source, int length) {
		return addSign2Len(source, length, 0, 0);
	}

	public static String addRightZero2Len(String source, int length) {
		return addSign2Len(source, length, 0, 1);
	}

	/**
	 * 用空字符给字符串补足不足指定长度部分
	 * 
	 * @param source 原始字符串，null时原样返回
	 * @param length 目标长度
	 * @return 右侧补空格至指定长度的字符串，长度已不小于目标长度时原样返回
	 */
	public static String addRightBlank2Len(String source, int length) {
		return addSign2Len(source, length, 1, 1);
	}

	/**
	 * @param source      原始字符串，null或长度已达标时原样返回
	 * @param length      目标长度
	 * @param flag        补充字符类型：0补零，1补空格
	 * @param leftOrRight 补充方向：0左侧补，1右侧补
	 * @return 补足指定长度后的字符串
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
	 * 用特定符号循环拼接指定的字符串
	 * 
	 * @date 2012-7-12 下午10:17:30
	 * @param source   待重复拼接的字符串，null按空字符串处理
	 * @param sign     各段之间的连接符号
	 * @param loopSize 重复次数，小于等于0返回空字符串
	 * @return 如source="a"、sign=","、loopSize=3时返回"a,a,a"
	 */
	public static String loopAppendWithSign(String source, String sign, int loopSize) {
		if (loopSize <= 0) {
			return "";
		}
		String item = (source == null) ? "" : source;
		return String.join(sign, Collections.nCopies(loopSize, item));
	}

	/**
	 * 补字符(限单字符)
	 * 
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
	 * 查询对称标记符号的位置，startIndex必须是<source.indexOf(beginMarkSign)
	 * 
	 * @param beginMarkSign 开始标记符号，单双引号时自动排除\'和\"转义形式
	 * @param endMarkSign   结束标记符号
	 * @param source        原始字符串
	 * @param startIndex    起始查找位置
	 * @return 结束标记符号的位置，未找到返回-1
	 */
	public static int getSymMarkIndex(String beginMarkSign, String endMarkSign, String source, int startIndex) {
		if (source == null) {
			return -1;
		}
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
					// begin后紧跟同类引号(''或"")为空字面量的成对终结,终结引号取当前相邻位,
					// 规避终结点后移导致字面量后的分隔符被误判为字面量内部;
					// begin后是\转义引号时终结引号后移一位(维持原有\'转义语义)
					if (beginMarkSign.charAt(0) != source.charAt(beginSignIndex + 1)) {
						endIndex = endIndex + 1;
					}
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
						// 同上:begin后紧跟同类引号为空字面量成对终结,取当前相邻位
						if (beginMarkSign.charAt(0) != source.charAt(beginSignIndex + 1)) {
							endIndex = endIndex + 1;
						}
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
	 * 查找对称标记符号的位置，扫描过程跳过'...'字符串字面量(''成对转义): 规避字面量内的括号等符号被误当语法符号参与配对
	 * 
	 * @param beginMark 开始标记(单字符，如"(")，需与endMark不同
	 * @param endMark   结束标记(单字符，如")")
	 * @param source    原始字符串，null返回-1
	 * @param fromIndex 从该位置开始查找开始标记
	 * @return 对称结束标记的位置，未找到开始标记或未配对成功返回-1
	 */
	public static int getSymMarkIndexSkipQuoted(String beginMark, String endMark, String source, int fromIndex) {
		if (source == null) {
			return -1;
		}
		char beginChar = beginMark.charAt(0);
		char endChar = endMark.charAt(0);
		char[] chars = source.toCharArray();
		int n = chars.length;
		boolean inString = false;
		// -1表示尚未进入开始标记;进入后为当前嵌套深度
		int depth = -1;
		int i = Math.max(fromIndex, 0);
		while (i < n) {
			char c = chars[i];
			if (inString) {
				if (c == '\'') {
					// ''成对转义为字面量内容
					if (i + 1 < n && chars[i + 1] == '\'') {
						i += 2;
						continue;
					}
					inString = false;
				}
				i++;
				continue;
			}
			if (c == '\'') {
				inString = true;
				i++;
				continue;
			}
			if (c == beginChar) {
				depth = (depth == -1) ? 1 : depth + 1;
			} else if (c == endChar) {
				if (depth > 0) {
					depth--;
					if (depth == 0) {
						return i;
					}
				}
			}
			i++;
		}
		return -1;
	}

	/**
	 * 查询对称标记符号的位置
	 * 
	 * @param beginMarkSign 开始标记符号
	 * @param endMarkSign   结束标记符号
	 * @param source        原始字符串
	 * @param startIndex    起始查找位置
	 * @return 不区分大小写下结束标记符号的位置，未找到返回-1
	 */
	public static int getSymMarkIndexIgnoreCase(String beginMarkSign, String endMarkSign, String source,
			int startIndex) {
		return getSymMarkIndex(beginMarkSign.toLowerCase(Locale.ROOT), endMarkSign.toLowerCase(Locale.ROOT),
				source.toLowerCase(Locale.ROOT), startIndex);
	}

	/**
	 * 查询对称标记符号的位置
	 * 
	 * @param beginMarkSign 开始标记符号的正则表达式
	 * @param endMarkSign   结束标记符号的正则表达式
	 * @param source        原始字符串
	 * @param startIndex    起始查找位置
	 * @return 结束标记正则匹配的起始位置，未找到返回-1
	 */
	public static int getSymMarkMatchIndex(String beginMarkSign, String endMarkSign, String source, int startIndex) {
		if (source == null) {
			return -1;
		}
		// 判断对称符号是否相等
		boolean symMarkIsEqual = beginMarkSign.equals(endMarkSign) ? true : false;
		// update 2026-9-8 复用PATTERN_CACHE(原每次调用现场编译,调用方为SQL处理热路径)
		Pattern startP = patternOf(beginMarkSign);
		Pattern endP = patternOf(endMarkSign);
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
	 * 逆向查询对称标记符号的位置
	 * 
	 * @param beginMarkSign 开始标记符号
	 * @param endMarkSign   结束标记符号
	 * @param source        原始字符串
	 * @param endIndex      主要endMarkSign的length,一般lastIndex(sign)+sign.length()
	 * @return 从后往前最近的开始标记符号位置，未找到返回-1
	 */
	public static int getSymMarkReverseIndex(String beginMarkSign, String endMarkSign, String source, int endIndex) {
		if (source == null) {
			return -1;
		}
		int beginIndex = source.length() - endIndex;
		String realSource = new StringBuilder(source).reverse().toString();
		String realStartMark = beginMarkSign;
		String realEndMark = endMarkSign;
		if (realStartMark.length() > 1) {
			realStartMark = new StringBuilder(realStartMark).reverse().toString();
		}
		if (realEndMark.length() > 1) {
			realEndMark = new StringBuilder(realEndMark).reverse().toString();
		}
		int index = getSymMarkIndex(realEndMark, realStartMark, realSource, beginIndex < 0 ? 0 : beginIndex);
		// 未找到时index=-1参与运算会返回错误下标而非-1,调用方依赖-1做终止判断
		if (index == -1) {
			return -1;
		}
		return source.length() - index - realStartMark.length();
	}

	/**
	 * 剔除字符串中对称符号和中间的内容,便于判断剩余部分内容是否有动态参数,减少干扰
	 * 
	 * @param sql       原始字符串(通常是sql语句)
	 * @param startMark 开始标记符号，如括号
	 * @param endMark   结束标记符号
	 * @return 剔除对称符号及其内部内容后的字符串，sql为null返回null
	 */
	public static String clearSymMarkContent(String sql, String startMark, String endMark) {
		if (sql == null) {
			return null;
		}
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
	 * 通过正则表达式判断是否匹配
	 * 
	 * @param source 待匹配的字符串，空白返回false
	 * @param regex  正则表达式
	 * @return 存在匹配片段返回true，否则返回false
	 */
	public static boolean matches(String source, String regex) {
		if (regex == null) {
			return false;
		}
		return matches(source, patternOf(regex));
	}

	/**
	 * 通过正则表达式判断是否匹配
	 * 
	 * @param source  待匹配的字符串，空白返回false
	 * @param pattern 编译后的正则表达式对象
	 * @return 存在匹配片段返回true，否则返回false
	 */
	public static boolean matches(String source, Pattern pattern) {
		if (isBlank(source)) {
			return false;
		}
		return pattern.matcher(source).find();
	}

	/**
	 * 找到匹配的位置
	 * 
	 * @param source 原始字符串，null返回-1
	 * @param regex  正则表达式
	 * @return 首次匹配的起始位置，未匹配返回-1
	 */
	public static int matchIndex(String source, String regex) {
		return matchIndex(source, patternOf(regex));
	}

	public static int[] matchIndex(String source, String regex, int start) {
		return matchIndex(source, patternOf(regex), start);
	}

	public static int matchIndex(String source, Pattern pattern) {
		if (source == null) {
			return -1;
		}
		Matcher m = pattern.matcher(source);
		if (m.find()) {
			return m.start();
		}
		return -1;
	}

	public static int[] matchIndex(String source, Pattern pattern, int start) {
		if (source == null || source.length() <= start) {
			return new int[] { -1, -1 };
		}
		Matcher m = pattern.matcher(source.substring(start));
		if (m.find()) {
			return new int[] { m.start() + start, m.end() + start };
		}
		return new int[] { -1, -1 };
	}

	public static int matchLastIndex(String source, String regex) {
		return matchLastIndex(source, patternOf(regex), 0);
	}

	public static int matchLastIndex(String source, Pattern pattern) {
		return matchLastIndex(source, pattern, 0);
	}

	public static int matchLastIndex(String source, Pattern pattern, int offset) {
		if (source == null) {
			return -1;
		}
		Matcher m = pattern.matcher(source);
		// offset不能为负数，做保护
		offset = Math.max(offset, 0);
		int matchIndex = -1;
		int start = 0;
		while (m.find(start)) {
			matchIndex = m.start();
			start = Math.max(m.end() - offset, m.start() + 1);
		}
		return matchIndex;
	}

	/**
	 * 获取匹配成功的个数
	 * 
	 * @param source 原始字符串，null返回0
	 * @param regex  正则表达式
	 * @return 匹配成功的次数
	 */
	public static int matchCnt(String source, String regex) {
		return matchCnt(source, patternOf(regex), 0);
	}

	/**
	 * 获取匹配成功的个数
	 * 
	 * @param source  原始字符串，null返回0
	 * @param pattern 编译后的正则表达式对象
	 * @return 匹配成功的次数
	 */
	public static int matchCnt(String source, Pattern pattern) {
		return matchCnt(source, pattern, 0);
	}

	/**
	 * 获取匹配成功的个数
	 * 
	 * @param source  原始字符串，null返回0
	 * @param pattern 编译后的正则表达式对象
	 * @param offset  相邻匹配可重叠的字符数量，负数按0处理
	 * @return 匹配成功的次数
	 */
	public static int matchCnt(String source, Pattern pattern, int offset) {
		if (source == null) {
			return 0;
		}
		Matcher matcher = pattern.matcher(source);
		offset = Math.max(offset, 0);
		int count = 0;
		int start = 0;
		while (matcher.find(start)) {
			count++;
			start = Math.max(matcher.end() - offset, matcher.start() + 1);
		}
		return count;
	}

	/**
	 * 获取匹配成功的个数
	 * 
	 * @param source     原始字符串，null返回0
	 * @param regex      正则表达式
	 * @param beginIndex 匹配范围的起始位置(含)
	 * @param endIndex   匹配范围的结束位置(不含)
	 * @return 指定范围内匹配成功的次数
	 */
	public static int matchCnt(String source, String regex, int beginIndex, int endIndex) {
		if (source == null) {
			return 0;
		}
		return matchCnt(source.substring(beginIndex, endIndex), patternOf(regex), 0);
	}

	public static int matchCnt(String source, String regex, int beginIndex, int endIndex, int offset) {
		if (source == null) {
			return 0;
		}
		return matchCnt(source.substring(beginIndex, endIndex), patternOf(regex), offset);
	}

	/**
	 * 获取字符指定次数的位置
	 * 
	 * @param source 原始字符串，null返回-1
	 * @param regex  待查找的字符串(字面匹配，非正则)
	 * @param order  出现的次序，从0开始(0表示第一次出现)
	 * @return 指定次序出现的位置，不存在返回-1
	 */
	public static int indexOrder(String source, String regex, int order) {
		if (source == null) {
			return -1;
		}
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
	 * 字符串转ASCII
	 * 
	 * @param str 原始字符串，null返回空数组
	 * @return 每个字符对应ASCII码值的int数组
	 */
	public static int[] str2ASCII(String str) {
		if (str == null) {
			return new int[0];
		}
		char[] chars = str.toCharArray(); // 把字符中转换为字符数组
		int[] result = new int[chars.length];
		for (int i = 0; i < chars.length; i++) {// 输出结果
			result[i] = (int) chars[i];
		}
		return result;
	}

	/**
	 * 切割字符串，排除特殊字符对，如a,b,c,dd(a,c),dd(a,c)不能切割
	 * 
	 * @param source    原始字符串，null返回null
	 * @param splitSign 如逗号、分号、冒号或具体字符串,非正则表达式
	 * @param filterMap 对称符号对(如单引号对、括号对)，位于符号对内部的分隔符不参与切割；null或空时不做排除
	 * @return 切割后的字符串数组
	 */
	public static String[] splitExcludeSymMark(String source, String splitSign, Map<String, String> filterMap) {
		if (source == null) {
			return null;
		}
		int splitIndex = source.indexOf(splitSign);
		if (splitIndex == -1) {
			return new String[] { source };
		}
		if (filterMap == null || filterMap.isEmpty()) {
			return splitRegex(source, splitSign, false);
		}
		List<String[]> filters = matchFilters(source, filterMap);
		if (filters.isEmpty()) {
			return splitRegex(source, splitSign, false);
		}
		int splitSignLen = splitSign.length();
		int start = 0;
		int skipIndex = 0;
		int preSplitIndex = splitIndex;
		ArrayList<String> splitResults = new ArrayList<String>();
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
		return splitResults.toArray(new String[0]);
	}

	/**
	 * 获取对称符号的开始和结束位置
	 * 
	 * @param source     原始字符串
	 * @param filter     对称符号对数组，filter[0]为开始符号、filter[1]为结束符号
	 * @param skipIndex  起始查找位置
	 * @param splitIndex 分隔符号位置，用于判断对称符号是否覆盖分隔符
	 * @return 长度为2的数组，[0]为开始位置、[1]为结束位置，未找到时对应元素为-1
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
	 * 匹配有效的过滤器
	 * 
	 * @param source    原始字符串
	 * @param filterMap 候选对称符号对，key为开始符号、value为结束符号
	 * @return 在字符串中开始和结束符号均存在的符号对列表
	 */
	public static List<String[]> matchFilters(String source, Map<String, String> filterMap) {
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
	 * 将字符串转换成驼峰形式
	 * 
	 * @param source           原始字符串，以下划线或横杠分词，如ORGAN_INFO
	 * @param firstIsUpperCase 首字母是否大写
	 * @param removeDealine    是否移除下划线
	 * @return 驼峰形式字符串，如ORGAN_INFO在firstIsUpperCase=false时返回organInfo；空白时原样返回
	 */
	public static String toHumpStr(String source, boolean firstIsUpperCase, boolean removeDealine) {
		if (isBlank(source)) {
			return source;
		}
		// update 2018-3-22 将-符号统一成_
		String[] humpAry = source.trim().replace("-", "_").split("\\_");
		String cell;
		StringBuilder result = new StringBuilder();
		for (int i = 0, n = humpAry.length; i < n; i++) {
			cell = humpAry[i];
			if (i > 0 && !removeDealine) {
				result.append("_");
			}
			// 全大写或全小写
			if (cell.toUpperCase(Locale.ROOT).equals(cell)) {
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
	 * 通过特殊符号对字符进行安全模糊化处理
	 * 
	 * @param value      原始对象，null返回null
	 * @param preLength  保留明文的头部字符数量
	 * @param tailLength 保留明文的尾部字符数量
	 * @param maskStr    掩盖符号，null或空默认为***
	 * @return 头尾保留、中间以掩盖符号填充的字符串；长度不超过头尾保留之和时原样返回
	 */
	public static String secureMask(Object value, int preLength, int tailLength, String maskStr) {
		if (value == null) {
			return null;
		}
		preLength = Math.max(0, preLength);
		tailLength = Math.max(0, tailLength);
		String tmp = value.toString();
		if (tmp.length() <= preLength + tailLength) {
			return tmp;
		}
		return tmp.substring(0, preLength).concat((maskStr == null || "".equals(maskStr)) ? "***" : maskStr)
				.concat(tmp.substring(tmp.length() - tailLength));
	}

	/**
	 * 判断字符串中是否包含中文
	 * 
	 * @param str 待判断的字符串，null返回false
	 * @return true表示包含中文字符
	 */
	public static boolean hasChinese(String str) {
		if (str == null) {
			return false;
		}
		return chinaPattern.matcher(str).find();
	}

	/**
	 * 驼峰形式字符用分割符号链接,example:humpToSplitStr("organInfo","_") result:organ_Info
	 * 
	 * @param source 驼峰形式字符串，null返回null
	 * @param split  分割符号
	 * @return 在大写字母前插入分割符号后的字符串
	 */
	public static String humpToSplitStr(String source, String split) {
		if (source == null) {
			return null;
		}
		char[] chars = source.trim().toCharArray();
		StringBuilder result = new StringBuilder();
		int charInt;
		int uperCaseCnt = 0;
		for (int i = 0, n = chars.length; i < n; i++) {
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
	 * 加工字段名称，将数据库sql查询的columnName转成对应对象的属性名称(去除下划线)
	 * 
	 * @param labelNames 数据库查询结果的列名数组(可带"name:alias"别名形式)，null返回null
	 * @return 首字母小写的驼峰属性名数组，与输入数组等长且位置对应
	 */
	public static String[] humpFieldNames(String[] labelNames) {
		if (labelNames == null) {
			return null;
		}
		String[] result = new String[labelNames.length];
		int aliasIndex = 0;
		for (int i = 0, n = labelNames.length; i < n; i++) {
			if (labelNames[i] == null) {
				result[i] = null;
				continue;
			}
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
	 * 填充args参数,将字符串中的${}按位置顺序填入具体参数值
	 * 
	 * @param template 含${}占位符的模板字符串，为null或无参数时原样返回
	 * @param args     按顺序填充的参数值，null值以"null"填充
	 * @return 填充后的字符串
	 */
	public static String fillArgs(String template, Object... args) {
		if (template == null || args == null || args.length == 0) {
			return template;
		}
		for (Object arg : args) {
			template = template.replaceFirst("\\$?\\{\\s*\\}",
					(arg == null) ? "null" : Matcher.quoteReplacement(arg.toString()));
		}
		return template;
	}

	public static String replaceFirstStr(String source, String target, String replacement, int fromIndex) {
		if (source == null || target == null || target.isEmpty()) {
			return source;
		}
		// 从fromIndex位置开始查找
		int idx = source.indexOf(target, fromIndex);
		if (idx == -1) {
			return source;
		}
		String rep = (replacement == null) ? "" : replacement;
		return source.substring(0, idx) + rep + source.substring(idx + target.length());
	}

	public static String replaceFirstStr(String source, String target, String replacement) {
		return replaceFirstStr(source, target, replacement, 0);
	}

	/**
	 * 提供偏移替换后字符长度的全量替换
	 * 
	 * @param source   原始字符串，null原样返回
	 * @param template 待替换的字符串
	 * @param target   替换后的字符串
	 * @return 全量替换后的字符串
	 */
	public static String replaceAllStr(String source, String template, String target) {
		return replaceAllStr(source, template, target, 0);
	}

	public static String replaceAllStr(String source, String template, String target, int fromIndex) {
		if (source == null) {
			return source;
		}
		return replaceAllStr(source, template, target, fromIndex, source.length() - 1);
	}

	public static String replaceAllStr(String source, String template, String target, int fromIndex, int endIndex) {
		if (source == null || template == null || target == null || template.isEmpty() || template.equals(target)) {
			return source;
		}
		int srcLen = source.length();
		// 边界矫正：统一收敛到合法下标
		int realFrom = Math.max(0, fromIndex);
		int realEnd = Math.min(srcLen - 1, endIndex);
		// 区间无效，直接返回
		if (realFrom > realEnd) {
			return source;
		}
		// 拆分：前缀 + 待替换区间 + 后缀（基于原字符串下标，绝对安全）
		String prefix = source.substring(0, realFrom);
		String mid = source.substring(realFrom, realEnd + 1);
		String suffix = source.substring(realEnd + 1);
		// 在子串内做替换 + 控制偏移，防死循环/重复匹配
		StringBuilder midSb = new StringBuilder(mid);
		int tplLen = template.length();
		int targetLen = target.length();
		int pos = 0;
		while ((pos = midSb.indexOf(template, pos)) != -1) {
			midSb.replace(pos, pos + tplLen, target);
			pos += targetLen;
		}
		return prefix + midSb + suffix;
	}

	/**
	 * 替换部分全角字符为半角
	 * 
	 * @param SBCStr 含全角字符的原始字符串，空白时原样返回
	 * @return 全角符号(;?.:'"，【】（）＝等)替换为对应半角后的字符串
	 */
	public static String toDBC(String SBCStr) {
		if (isBlank(SBCStr)) {
			return SBCStr;
		}
		char[] chars = SBCStr.toCharArray();
		StringBuilder sb = new StringBuilder(chars.length);
		for (char c : chars) {
			switch (c) {
			case '；':
				sb.append(';');
				break;
			case '？':
				sb.append('?');
				break;
			case '．':
				sb.append('.');
				break;
			case '：':
				sb.append(':');
				break;
			case '＇':
				sb.append('\'');
				break;
			case '＂':
				sb.append('"');
				break;
			case '，':
				sb.append(',');
				break;
			case '【':
				sb.append('[');
				break;
			case '】':
				sb.append(']');
				break;
			case '）':
				sb.append(')');
				break;
			case '（':
				sb.append('(');
				break;
			case '＝':
				sb.append('=');
				break;
			default:
				sb.append(c);
				break;
			}
		}
		return sb.toString();
	}

	/**
	 * 字符连接
	 * 
	 * @param sign     各元素间的连接符号，null默认为逗号
	 * @param skipNull true跳过null元素，false将null以"null"字符串参与连接
	 * @param arys     待连接的元素，null或空返回空字符串
	 * @return 连接后的字符串
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
	 * 提供类似于sql中的like功能
	 * 
	 * @param source   原始字符串，null返回false
	 * @param keywords 将匹配的字符用空格或者%进行切割并trim变成字符数组进行匹配
	 * @return 关键字按顺序在字符串中依次出现(位置递增)返回true，否则返回false
	 */
	public static boolean like(String source, String[] keywords) {
		if (source == null || keywords == null || keywords.length == 0) {
			return false;
		}
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

	public static String[] trimArray(String[] paramNames) {
		if (paramNames == null) {
			return null;
		}
		return Arrays.stream(paramNames).map(s -> s == null ? null : s.trim()).toArray(String[]::new);
	}

	/**
	 * 将字符串进行正则表达式切割
	 * 
	 * @param source 原始字符串，null返回null
	 * @param regex  正则表达式，常用符号(?,;:.|等)已做转义保护，空白串按连续空白切割
	 * @param doTrim true对切割后的每段做trim
	 * @return 切割后的字符串数组
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
		} else if (".".equals(regex)) {
			result = source.split("\\.");
		} else if ("|".equals(regex)) {
			result = source.split("\\|");
		} else if (regex.length() > 0 && "".equals(regex.trim())) {
			result = source.split("\\s+");
		} else if ("||".equals(regex)) {
			result = source.split("\\|{2}");
		} else if ("&&".equals(regex)) {
			result = source.split("\\&{2}");
		} else {
			result = source.split(regex);
		}
		if (doTrim) {
			for (int i = 0, n = result.length; i < n; i++) {
				result[i] = result[i].trim();
			}
		}
		return result;
	}

	/**
	 * 用字符串index分割字符串，主要用于动态缓存翻译key,key2,key3形式经过翻译后用特定字符拼接:name1->key2->name3,再用link符号切割开对key2进行翻译形成name1->name2->name3形式
	 * 
	 * @param str       原始字符串，null或空返回空数组
	 * @param delimiter 分割符号，null或空返回仅含原字符串的数组
	 * @return 按字面分割符号(非正则)切割后的字符串数组
	 */
	public static String[] splitByIndex(String str, String delimiter) {
		return splitByIndex(str, delimiter, false);
	}

	public static String[] splitByIndex(String str, String delimiter, boolean doTrim) {
		if (str == null || str.isEmpty()) {
			return new String[] {};
		}
		if (delimiter == null || delimiter.isEmpty()) {
			return new String[] { str };
		}
		List<String> parts = new ArrayList<>();
		int delimiterLength = delimiter.length();
		int startIndex = 0;
		int foundIndex;
		// 循环查找分隔符位置并截取
		while ((foundIndex = str.indexOf(delimiter, startIndex)) != -1) {
			// 截取从startIndex到分隔符起始位置的子串
			if (doTrim) {
				parts.add(str.substring(startIndex, foundIndex).trim());
			} else {
				parts.add(str.substring(startIndex, foundIndex));
			}
			// 更新起始位置为分隔符结束位置
			startIndex = foundIndex + delimiterLength;
		}
		// 截取最后一段（分隔符之后的剩余部分）
		if (doTrim) {
			parts.add(str.substring(startIndex).trim());
		} else {
			parts.add(str.substring(startIndex));
		}
		return parts.toArray(new String[0]);
	}

	/**
	 * 处理空白和null，给与默认值
	 * 
	 * @param value        待判断的字符串
	 * @param defaultValue value为空白或null时返回的默认值
	 * @return value非空白时返回原值，否则返回defaultValue
	 */
	public static String ifBlank(String value, String defaultValue) {
		if (isBlank(value)) {
			return defaultValue;
		}
		return value;
	}

	/**
	 * 替换正则表达式指定匹配次序的字符
	 * 
	 * @param source     原始字符串，null原样返回
	 * @param pattern    编译后的正则表达式对象
	 * @param replaceStr 替换后的字符串
	 * @param matchCnt   目标匹配的次序，从1开始
	 * @param offset     偏移字符数量
	 * @return 第matchCnt次匹配被替换后的字符串，匹配次数不足时原样返回
	 */
	public static String replaceRegex(String source, Pattern pattern, String replaceStr, int matchCnt, int offset) {
		if (source == null) {
			return source;
		}
		offset = Math.max(offset, 0);
		Matcher matcher = pattern.matcher(source);
		int count = 0;
		int start = 0;
		int end = -1;
		while (matcher.find(start)) {
			count++;
			end = matcher.end();
			if (count == matchCnt) {
				return source.substring(0, matcher.start()) + replaceStr + source.substring(end);
			}
			start = Math.max(end - offset, matcher.start() + 1);
		}
		return source;
	}

	public static String toLowerOrUpper(String source, String upperOrLower) {
		if (upperOrLower == null || source == null) {
			return source;
		}
		if (upperOrLower.equals("upper")) {
			return source.toUpperCase(Locale.ROOT);
		} else if (upperOrLower.equals("lower")) {
			return source.toLowerCase(Locale.ROOT);
		}
		return source;
	}

	/**
	 * 剔除首位逗号和双引号
	 * 
	 * @param str 原始字符串，null或长度小于2原样返回
	 * @return 去除首尾成对单引号或双引号后的字符串，不成对时原样返回
	 */
	public static String removeStartEndQuote(String str) {
		if (str == null || str.length() < 2) {
			return str;
		}
		int endIndex = str.length() - 1;
		char first = str.charAt(0);
		char last = str.charAt(endIndex);
		if ((first == '"' && last == '"') || (first == '\'' && last == '\'')) {
			return str.substring(1, endIndex);
		}
		return str;
	}

	/**
	 * 完全离散脱敏：将脱敏字符均匀散布在整个字符串中，而非连续块，增加逆向还原难度。 算法：根据脱敏比例计算步长 stride = ceil(length /
	 * maskLength)，从 stride/2 开始每隔 stride 个字符脱敏一位， 末尾剩余不足部分从尾部补齐，确保脱敏字符总数准确且分布离散。
	 *
	 * @param str      原始字符串
	 * @param maskCode 脱敏替换字符（取首字符）
	 * @param maskRate 脱敏比例(1~100)，表示脱敏字符占字符串总长度的百分比
	 * @return 脱敏后的字符串
	 */
	public static String maskByRate(String str, String maskCode, int maskRate) {
		if (str == null || str.isEmpty() || maskCode == null || maskCode.isEmpty() || maskRate <= 0) {
			return str;
		}
		int length = str.length();
		int maskLength = (int) Math.ceil(length * maskRate / 100.0);
		if (maskLength >= length) {
			return maskCode.repeat(length);
		}
		int stride = (int) Math.ceil((double) length / maskLength);
		char maskChar = maskCode.charAt(0);
		StringBuilder maskedStr = new StringBuilder(str);
		int count = 0;
		for (int i = stride / 2; i < length && count < maskLength; i += stride) {
			maskedStr.setCharAt(i, maskChar);
			count++;
		}
		for (int i = length - 1; i >= 0 && count < maskLength; i--) {
			if (maskedStr.charAt(i) != maskChar) {
				maskedStr.setCharAt(i, maskChar);
				count++;
			}
		}
		return maskedStr.toString();
	}
}
