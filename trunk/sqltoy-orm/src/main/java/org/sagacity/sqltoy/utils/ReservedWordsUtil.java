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

	/**
	 * @TODO 处理框架生成的简单sql,默认以[]符号作为转义符号
	 * @param sql
	 * @param dbType
	 * @return
	 */
	public static String convertSimpleSql(String sql, Integer dbType) {
		if (reservedWords.isEmpty())
			return sql;
		if (dbType == DBType.MYSQL || dbType == DBType.MYSQL57) {
			return sql.replaceAll("\\[", "`").replaceAll("\\]", "`");
		}
		if (dbType == DBType.ORACLE || dbType == DBType.POSTGRESQL || dbType == DBType.DB2
				|| dbType == DBType.ORACLE11) {
			return sql.replaceAll("\\[", "\"").replaceAll("\\]", "\"");
		}
		if (dbType == null || dbType == DBType.SQLSERVER || dbType == DBType.SQLITE || dbType == DBType.SQLSERVER2012) {
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
		if (dbType == null || dbType == DBType.SQLSERVER || dbType == DBType.SQLITE || dbType == DBType.SQLSERVER2012) {
			return "[".concat(column).concat("]");
		}
		if (dbType == DBType.MYSQL || dbType == DBType.MYSQL57) {
			return "`".concat(column).concat("`");
		}
		if (dbType == DBType.ORACLE || dbType == DBType.POSTGRESQL || dbType == DBType.ORACLE11) {
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
		while (matcher.find()) {
			end = matcher.start() + 1;
			sqlBuff.append(sql.substring(start, end));
			keyWord = matcher.group().trim();
			keyWord = keyWord.substring(2, keyWord.length() - 2);
			if (dbType == DBType.POSTGRESQL || dbType == DBType.ORACLE || dbType == DBType.DB2
					|| dbType == DBType.GAUSSDB || dbType == DBType.ORACLE11) {
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
