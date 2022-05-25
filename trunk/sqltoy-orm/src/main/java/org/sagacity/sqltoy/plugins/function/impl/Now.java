/**
 * 
 */
package org.sagacity.sqltoy.plugins.function.impl;

import java.util.regex.Pattern;

import org.sagacity.sqltoy.plugins.function.IFunction;
import org.sagacity.sqltoy.utils.DataSourceUtils.DBType;

/**
 * @project sqltoy-orm
 * @description 不同数据库当前系统时间获取方式
 * @author zhongxuchen
 * @version v1.0,Date:2013-3-25
 */
public class Now extends IFunction {
	private static Pattern regex = Pattern.compile("(?i)\\W(((now|getdate|sysdate)\\()|(sysdate\\W))");

	/*
	 * (non-Javadoc)
	 * 
	 * @see org.sagacity.sqltoy.config.function.IFunction#dialects()
	 */
	@Override
	public String dialects() {
		return ALL;
	}

	/*
	 * (non-Javadoc)
	 * 
	 * @see org.sagacity.sqltoy.config.function.IFunction#regex()
	 */
	@Override
	public Pattern regex() {
		return regex;
	}

	/*
	 * (non-Javadoc)
	 * 
	 * @see org.sagacity.sqltoy.config.function.IFunction#wrap(int,
	 * java.lang.String[])
	 */
	@Override
	public String wrap(int dialect, String functionName, boolean hasArgs, String... args) {
		if (dialect == DBType.MYSQL || dialect == DBType.POSTGRESQL || dialect == DBType.POSTGRESQL15
				|| dialect == DBType.TIDB || dialect == DBType.GAUSSDB || dialect == DBType.MYSQL57) {
			return wrapArgs("now", args);
		}
		if (dialect == DBType.ORACLE || dialect == DBType.OCEANBASE || dialect == DBType.DM
				|| dialect == DBType.ORACLE11) {
			return "sysdate";
		}
		if (dialect == DBType.SQLSERVER) {
			return wrapArgs("getdate", args);
		}
		return super.IGNORE;
	}
}
