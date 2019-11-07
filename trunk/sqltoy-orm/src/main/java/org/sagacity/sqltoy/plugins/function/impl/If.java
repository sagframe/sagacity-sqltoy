package org.sagacity.sqltoy.plugins.function.impl;

import java.util.regex.Pattern;

import org.sagacity.sqltoy.plugins.function.IFunction;
import org.sagacity.sqltoy.utils.DataSourceUtils.DBType;

/**
 * 
 * @author zhongxuchen
 *
 */
public class If extends IFunction {
	private static Pattern regex = Pattern.compile("(?i)\\Wif\\(");

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
		if (dialect == DBType.MYSQL || dialect == DBType.MYSQL8) {
			return super.IGNORE;
		}
		if (args.length < 3)
			return super.IGNORE;
		return " case when " + args[0] + " then " + args[1] + " else " + args[2] + " end ";
	}

}
