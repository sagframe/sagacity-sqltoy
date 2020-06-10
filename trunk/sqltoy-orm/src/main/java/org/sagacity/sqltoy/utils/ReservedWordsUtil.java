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
		singlePattern = Pattern.compile("(?i)(\\W||\\s)(`|\"|\\[)(" + fullRegex + ")(`|\"|\\])(\\s||\\W)");
	}

	/**
	 * @TODO 处理框架基于对象操作生成的简单sql,对默认[]符号进行数据库转换
	 * @param sql
	 * @param dbType
	 * @return
	 */
	public static String convertSimpleSql(String sql, Integer dbType) {
		if (reservedWords.isEmpty())
			return sql;
		if (dbType == DBType.MYSQL || dbType == DBType.TIDB || dbType == DBType.MYSQL57) {
			return sql.replaceAll("\\[", "`").replaceAll("\\]", "`");
		}
		if (dbType == DBType.ORACLE || dbType == DBType.POSTGRESQL || dbType == DBType.DB2 || dbType == DBType.DM
				|| dbType == DBType.GAUSSDB || dbType == DBType.OCEANBASE || dbType == DBType.ORACLE11) {
			return sql.replaceAll("\\[", "\"").replaceAll("\\]", "\"");
		}
		if (dbType == null || dbType == DBType.SQLSERVER || dbType == DBType.SQLITE) {
			return sql;
		}
		// 剔除保留字符号
		return sql.replaceAll("\\[", "").replaceAll("\\]", "");
	}

	/**
	 * 转换单词
	 * 
	 * @param column
	 * @param dbType
	 * @return
	 */
	public static String convertWord(String column, Integer dbType) {
		// 非保留字
		if (reservedWords.isEmpty())
			return column;
		if (!reservedWords.contains(column.toLowerCase()))
			return column;
		// 默认加上[]符合便于后面根据不同数据库类型进行替换,而其他符号则难以替换
		if (dbType == null || dbType == DBType.SQLSERVER || dbType == DBType.SQLITE) {
			return "[".concat(column).concat("]");
		}
		if (dbType == DBType.MYSQL || dbType == DBType.TIDB || dbType == DBType.MYSQL57) {
			return "`".concat(column).concat("`");
		}
		if (dbType == DBType.ORACLE || dbType == DBType.POSTGRESQL || dbType == DBType.DB2 || dbType == DBType.GAUSSDB
				|| dbType == DBType.DM || dbType == DBType.OCEANBASE || dbType == DBType.ORACLE11) {
			return "\"".concat(column).concat("\"");
		}
		return column;
	}

	/**
	 * @TODO 对整个sql进行保留字处理
	 * @param sql
	 * @param dbType
	 * @return
	 */
	public static String convertSql(String sql, Integer dbType) {
		if (reservedWords.isEmpty())
			return sql;

		if (dbType == null || dbType == DBType.ES || dbType == DBType.MONGO || dbType == DBType.UNDEFINE)
			return sql;
		StringBuilder sqlBuff = new StringBuilder();
		Matcher matcher;
		int start = 0;
		int end = 0;
		String keyWord;
		matcher = singlePattern.matcher(sql);
		int subSize = 0;
		while (matcher.find()) {
			subSize = 0;
			end = matcher.start() + 1;
			keyWord = matcher.group().substring(1);
			if (keyWord.startsWith("`") || keyWord.startsWith("\"") || keyWord.startsWith("[")) {
				keyWord = keyWord.substring(1);
			}
			sqlBuff.append(sql.substring(start, end));
			keyWord = keyWord.substring(0, keyWord.length() - 1);
			if (keyWord.endsWith("`") || keyWord.endsWith("\"") || keyWord.endsWith("]")) {
				keyWord = keyWord.substring(0, keyWord.length() - 1);
				subSize = 1;
			}
			if (dbType == DBType.POSTGRESQL || dbType == DBType.ORACLE || dbType == DBType.DB2
					|| dbType == DBType.GAUSSDB || dbType == DBType.DM || dbType == DBType.OCEANBASE
					|| dbType == DBType.ORACLE11) {
				sqlBuff.append("\"").append(keyWord).append("\"");
			} else if (dbType == DBType.SQLSERVER || dbType == DBType.SQLITE) {
				sqlBuff.append("[").append(keyWord).append("]");
			} else if (dbType == DBType.MYSQL || dbType == DBType.TIDB || dbType == DBType.MYSQL57) {
				sqlBuff.append("`").append(keyWord).append("`");
			} else {
				sqlBuff.append(keyWord);
			}
			start = matcher.end() - subSize;
		}

		if (start > 0) {
			sqlBuff.append(sql.substring(start));
			return sqlBuff.toString();
		}
		return sql;

	}
}
