package org.sagacity.sqltoy.plugins.function.impl;

import java.util.regex.Pattern;

import org.sagacity.sqltoy.plugins.function.IFunction;
import org.sagacity.sqltoy.utils.DataSourceUtils.DBType;

/**
 * 
 * @author zhongxuchen
 *
 */
public class GroupConcat extends IFunction {
	private static Pattern regex = Pattern.compile("(?i)\\Wgroup_concat\\(");

	@Override
	public String dialects() {
		return "";
	}

	@Override
	public Pattern regex() {
		return regex;
	}

	@Override
	public String wrap(int dialect, String functionName, boolean hasArgs, String... args) {
		if (dialect == DBType.POSTGRESQL || dialect == DBType.POSTGRESQL11 || dialect == DBType.POSTGRESQL10) {
			if (args.length > 1)
				return " array_to_string(ARRAY_AGG(" + args[0] + ")," + args[1] + ") ";
			else {
				return " array_to_string(ARRAY_AGG(" + args[0] + "),',') ";
			}
		} /*
			 * else if (dialect == DBType.MYSQL || dialect == DBType.MYSQL8) {
			 * 
			 * }
			 */
		return null;
	}

}
