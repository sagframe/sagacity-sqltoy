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
	private static Pattern singlePattern = null;

	/**
	 * 加载保留字
	 * 
	 * @param words
	 */
	public static void put(String words) {
		if (StringUtil.isBlank(words))
			return;
		String[] strs = words.split("\\,");
		String regex;
		String fullRegex = "";
		int index = 0;
		for (String str : strs) {
			regex = str.trim().toLowerCase();
			if (!"".equals(regex)) {
				reservedWords.add(regex);
				if (index > 0) {
					fullRegex = fullRegex.concat("|");
				}
				fullRegex = fullRegex.concat(regex);
				index++;
			}
		}
		singlePattern = Pattern.compile("(?i)(\\W|\\s)(`|\"|\\[)(" + fullRegex + ")(`|\"|\\])(\\s|\\W)");
	}

//	public static String convertSql(String sql, Integer dbType) {
//		if (reservedWords.isEmpty())
//			return sql;
//
//		StringBuilder sqlBuff = new StringBuilder();
//		Matcher matcher;
//		String lastSql = sql;
//		for (Pattern pattern : reservedWordPattern) {
//			int start = 0;
//			int end = 0;
//			String keyWord;
//			matcher = pattern.matcher(lastSql);
//			while (matcher.find()) {
//				end = matcher.start() + 1;
//				sqlBuff.append(sql.substring(start, end));
//				keyWord = matcher.group().trim();
//				keyWord = keyWord.substring(2, keyWord.length() - 2);
//				if (dbType == DBType.DB2 || dbType == DBType.ORACLE || dbType == DBType.POSTGRESQL
//						|| dbType == DBType.ORACLE11) {
//					sqlBuff.append("\"").append(keyWord).append("\"");
//				} else if (dbType == DBType.SQLSERVER || dbType == DBType.SQLITE || dbType == DBType.SQLSERVER2012) {
//					sqlBuff.append("[").append(keyWord).append("]");
//				} else if (dbType == DBType.MYSQL || dbType == DBType.MYSQL57) {
//					sqlBuff.append("`").append(keyWord).append("`");
//				} else {
//					sqlBuff.append(keyWord);
//				}
//				start = matcher.end() - 1;
//			}
//			if (start > 0) {
//				sqlBuff.append(sql.substring(start));
//				lastSql = sqlBuff.toString();
//			}
//		}
//		return lastSql;
//	}

	public static String convertSql(String sql, Integer dbType) {
		if (reservedWords.isEmpty())
			return sql;

		StringBuilder sqlBuff = new StringBuilder();
		Matcher matcher;
		int start = 0;
		int end = 0;
		String keyWord;
		matcher = singlePattern.matcher(sql);
		while (matcher.find()) {
			end = matcher.start() + 1;
			sqlBuff.append(sql.substring(start, end));
			keyWord = matcher.group().trim();
			keyWord = keyWord.substring(2, keyWord.length() - 2);
			if (dbType == DBType.DB2 || dbType == DBType.ORACLE || dbType == DBType.POSTGRESQL
					|| dbType == DBType.ORACLE11) {
				sqlBuff.append("\"").append(keyWord).append("\"");
			} else if (dbType == DBType.SQLSERVER || dbType == DBType.SQLITE || dbType == DBType.SQLSERVER2012) {
				sqlBuff.append("[").append(keyWord).append("]");
			} else if (dbType == DBType.MYSQL || dbType == DBType.MYSQL57) {
				sqlBuff.append("`").append(keyWord).append("`");
			} else {
				sqlBuff.append(keyWord);
			}
			start = matcher.end() - 1;
		}

		if (start > 0) {
			sqlBuff.append(sql.substring(start));
			return sqlBuff.toString();
		}
		return sql;

	}

	public static void main(String[] args) {
		String sql = CommonUtils.readFileAsString("classpath:scripts/reservedWords.txt", "UTF-8");

		ReservedWordsUtil.put("maxvalue,minvalue");
		String lastSql = ReservedWordsUtil.convertSql(sql, DBType.POSTGRESQL);
		System.err.println(lastSql);
	}
}
