package org.sagacity.sqltoy.plugins.function.impl;

import java.util.regex.Pattern;

import org.sagacity.sqltoy.plugins.function.IFunction;
import org.sagacity.sqltoy.utils.DataSourceUtils.DBType;
import org.sagacity.sqltoy.utils.StringUtil;

/**
 * 
 * @author zhongxuchen
 *
 */
public class GroupConcat extends IFunction {
	private static Pattern regex = Pattern.compile("(?i)\\W(group_concat|string_agg)\\(");
	private static Pattern separtorPattern = Pattern.compile("\\Wseparator\\W");

	@Override
	public String dialects() {
		return ALL;
	}

	@Override
	public Pattern regex() {
		return regex;
	}

	@Override
	public String wrap(int dialect, String functionName, boolean hasArgs, String... args) {
		if (args == null || args.length == 0)
			return super.IGNORE;
		String tmp = args[args.length - 1];
		String sign = "','";
		int matchIndex = StringUtil.matchIndex(tmp.toLowerCase(), separtorPattern);
		if (matchIndex > 0)
			sign = tmp.substring(matchIndex + 11).trim();
		if (dialect == DBType.POSTGRESQL || dialect == DBType.POSTGRESQL11 || dialect == DBType.POSTGRESQL12) {
			// 原则上可以通过string_agg 但如果类型不是字符串就会报错
			if (args.length > 1) {
				return " array_to_string(ARRAY_AGG(" + args[0] + ")," + args[1] + ") ";
			} else {
				if (matchIndex > 0) {
					return " array_to_string(ARRAY_AGG(" + args[0].substring(0, matchIndex) + ")," + sign + ") ";
				} else {
					return " array_to_string(ARRAY_AGG(" + args[0] + ")," + sign + ") ";
				}
			}
		} else if (dialect == DBType.MYSQL || dialect == DBType.MYSQL8) {
			if (functionName.equalsIgnoreCase("group_concat"))
				return super.IGNORE;
			return " group_concat(" + args[0] + " separator " + args[1] + ") ";
		}

		return super.IGNORE;
	}

}
