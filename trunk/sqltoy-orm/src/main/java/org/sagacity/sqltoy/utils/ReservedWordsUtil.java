package org.sagacity.sqltoy.utils;

import java.util.HashSet;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import org.sagacity.sqltoy.utils.DataSourceUtils.DBType;

/**
 * @project sagacity-sqltoy
 * @description 用来处理sql中的数据库保留字
 * @author zhongxuchen <a href="mailto:zhongxuchen@hotmail.com">联系作者</a>
 * @version id:ReservedWordsUtil.java,Revision:v1.0,Date:2020-05-06
 */
public class ReservedWordsUtil {
	private static HashSet<String> reservedWords = new HashSet<String>();

	private static Pattern reservedWordPattern = null;

	/**
	 * 加载保留字
	 * 
	 * @param words
	 */
	public static void put(String words) {
		if (StringUtil.isBlank(words))
			return;
		String[] strs = words.split("\\,");
		String regex = "(?i)\\W\\s*(`|\"|\\[)(";
		int index = 0;
		for (String str : strs) {
			if (!"".equals(str.trim())) {
				reservedWords.add(str.trim().toLowerCase());
				if (index > 0) {
					regex = regex + "|";
				}
				regex = regex + str.trim();
				index++;
			}
		}
		regex = regex + ")(`|\"|\\])\\s*\\W";
		reservedWordPattern = Pattern.compile(regex);
	}

	public static String convertSql(String sql, Integer dbType) {
		if (reservedWords.isEmpty())
			return sql;
		Matcher matcher = reservedWordPattern.matcher(sql);
		StringBuilder sqlBuff = new StringBuilder();
		int start = 0;
		int end = 0;
		String keyWord;
		while (matcher.find()) {
			end = matcher.start() + 1;
			sqlBuff.append(sql.substring(start, end));
			keyWord = matcher.group().trim();
			keyWord = keyWord.substring(1, keyWord.length() - 1);
			if (dbType == DBType.DB2 || dbType == DBType.ORACLE || dbType == DBType.POSTGRESQL
					|| dbType == DBType.ORACLE11) {
				sqlBuff.append("\"").append(keyWord).append("\"");
			} else if (dbType == DBType.SQLSERVER || dbType == DBType.SQLSERVER2012) {
				sqlBuff.append("[").append(keyWord).append("]");
			} else if (dbType == DBType.MYSQL || dbType == DBType.MYSQL57) {
				sqlBuff.append("`").append(keyWord).append("`");
			}
			start = matcher.end() - 1;
		}
		return sql;
	}
}
