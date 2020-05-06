package org.sagacity.sqltoy.utils;

import java.util.HashSet;

/**
 * @project sagacity-sqltoy
 * @description 用来处理sql中的数据库保留字
 * @author zhongxuchen <a href="mailto:zhongxuchen@hotmail.com">联系作者</a>
 * @version id:ReservedWordsUtil.java,Revision:v1.0,Date:2020-05-06
 */
public class ReservedWordsUtil {
	private static HashSet<String> reservedWords = new HashSet<String>();

	/**
	 * 加载保留字
	 * 
	 * @param words
	 */
	public static void put(String words) {
		if (StringUtil.isBlank(words))
			return;
		String[] strs = words.split("\\,");
		for (String str : strs) {
			if (!"".equals(str.trim())) {
				reservedWords.add(str.trim().toLowerCase());
			}
		}
	}
}
