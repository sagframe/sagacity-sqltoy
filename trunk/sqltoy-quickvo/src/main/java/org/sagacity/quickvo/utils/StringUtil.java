/**
 * @Copyright 2007 版权归陈仁飞，不要肆意侵权抄袭，如引用请注明出处保留作者信息。
 */
package org.sagacity.quickvo.utils;

import java.util.regex.Pattern;

/**
 * @project sagacity-quickvo
 * @description 字符串处理常用功能
 * @author zhongxuchen $<a href="mailto:zhongxuchen@gmail.com">联系作者</a>$
 * @version $id:StringUtil.java,Revision:v1.0,Date:Oct 19, 2007 10:09:42 AM $
 */
public class StringUtil {
	/**
	 * private constructor,cann't be instantiated by other class 私有构造函数方法防止被实例化
	 */
	private StringUtil() {
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
		if (null == str || str.toString().trim().equals("")) {
			return true;
		}
		return false;
	}

	public static String trim(String str) {
		if (str == null)
			return null;
		return str.trim();
	}

	/**
	 * @todo 替换换行、回车、tab符号;\r 换行、\t tab符合、\n 回车
	 * @param source
	 * @param target
	 * @return
	 */
	public static String clearMistyChars(String source, String target) {
		return source.replaceAll("\r", target).replaceAll("\t", target).replaceAll("\n", target);
	}

	/**
	 * @todo 返回第一个字符大写，其余保持不变的字符串
	 * @param sourceStr
	 * @return
	 */
	public static String firstToUpperCase(String sourceStr) {
		if (isBlank(sourceStr))
			return sourceStr;
		if (sourceStr.length() == 1)
			return sourceStr.toUpperCase();
		return sourceStr.substring(0, 1).toUpperCase().concat(sourceStr.substring(1));
	}

	/**
	 * @todo 返回第一个字符小写，其余保持不变的字符串
	 * @param sourceStr
	 * @return
	 */
	public static String firstToLowerCase(String sourceStr) {
		if (isBlank(sourceStr))
			return sourceStr;
		if (sourceStr.length() == 1)
			return sourceStr.toUpperCase();
		return sourceStr.substring(0, 1).toLowerCase().concat(sourceStr.substring(1));
	}

	/**
	 * @todo 返回第一个字符大写，其余保持不变的字符串
	 * @param sourceStr
	 * @return
	 */
	public static String firstToUpperOtherToLower(String sourceStr) {
		if (isBlank(sourceStr))
			return sourceStr;
		if (sourceStr.length() == 1)
			return sourceStr.toUpperCase();
		return sourceStr.substring(0, 1).toUpperCase().concat(sourceStr.substring(1).toLowerCase());
	}

	/**
	 * @todo 在不分大小写情况下字符所在位置
	 * @param source
	 * @param pattern
	 * @return
	 */
	public static int indexOfIgnoreCase(String source, String pattern) {
		if (source == null || pattern == null)
			return -1;
		return source.toLowerCase().indexOf(pattern.toLowerCase());
	}

	/**
	 * @todo 针对jdk1.4 replace(char,char)提供jdk1.5中replace(String,String)的功能
	 * @param source
	 * @param template
	 * @param target
	 * @return
	 */
	public static String replaceStr(String source, String template, String target) {
		return replaceStr(source, template, target, 0);
	}

	public static String replaceStr(String source, String template, String target, int fromIndex) {
		if (source == null)
			return null;
		if (template == null)
			return source;
		if (fromIndex >= source.length() - 1)
			return source;
		int index = source.indexOf(template, fromIndex);
		if (index != -1) {
			source = source.substring(0, index).concat(target).concat(source.substring(index + template.length()));
		}
		return source;
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
		if (source == null || template.equals(target))
			return source;
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
	 * @param p
	 * @return
	 */
	public static boolean matches(String source, Pattern p) {
		return p.matcher(source).find();
	}

	/**
	 * @todo 将字符串转换成驼峰形式
	 * @param source
	 * @param firstIsUpperCase
	 * @return
	 */
	public static String toHumpStr(String source, boolean firstIsUpperCase) {
		if (StringUtil.isBlank(source))
			return source;
		String[] humpAry = source.split("\\_");
		String cell;
		StringBuilder result = new StringBuilder();
		for (int i = 0; i < humpAry.length; i++) {
			cell = humpAry[i];
			// 全大写或全小写
			if (cell.toUpperCase().equals(cell)) {
				result.append(firstToUpperOtherToLower(cell));
			} else {
				result.append(firstToUpperCase(cell));
			}
		}
		// 首字母变大写
		if (firstIsUpperCase)
			return firstToUpperCase(result.toString());
		return firstToLowerCase(result.toString());
	}

	public static String[] trimArray(String[] source) {
		if (source == null)
			return source;
		for (int i = 0; i < source.length; i++) {
			source[i] = source[i].trim();
		}
		return source;
	}
}
