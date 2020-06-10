/**
 * 
 */
package org.sagacity.sqltoy.plugins.function.impl;

import java.util.regex.Pattern;

import org.sagacity.sqltoy.plugins.function.IFunction;
import org.sagacity.sqltoy.utils.DataSourceUtils.DBType;

/**
 * @project sqltoy-orm
 * @description 提供不同数据库length函数的转换(主要是length和len之间的互换)
 * @author chenrenfei <a href="mailto:zhongxuchen@gmail.com">联系作者</a>
 * @version id:Length.java,Revision:v1.0,Date:2015年10月19日
 */
public class Length extends IFunction {
	private static Pattern regex = Pattern.compile("(?i)\\W(length|lengthb|len|datalength|char\\_length)\\(");

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
	 * java.lang.String, boolean, java.lang.String[])
	 */
	@Override
	public String wrap(int dialect, String functionName, boolean hasArgs, String... args) {
		if (dialect == DBType.SQLSERVER) {
			if (functionName.equalsIgnoreCase("datalength")) {
				return wrapArgs("datalength", args);
			}
			return wrapArgs("len", args);
		}
		if (dialect == DBType.ORACLE || dialect == DBType.POSTGRESQL || dialect == DBType.DB2
				|| dialect == DBType.GAUSSDB || dialect == DBType.OCEANBASE || dialect == DBType.DM
				|| dialect == DBType.ORACLE11) {
			if (functionName.equalsIgnoreCase("datalength") || functionName.equalsIgnoreCase("char_length")
					|| functionName.equalsIgnoreCase("len")) {
				return wrapArgs("length", args);
			}
			return wrapArgs(functionName, args);
		}
		if (dialect == DBType.MYSQL || dialect == DBType.TIDB || dialect == DBType.MYSQL57) {
			if (functionName.equalsIgnoreCase("char_length")) {
				return wrapArgs(functionName, args);
			}
			return wrapArgs("length", args);
		}
		if (dialect == DBType.SYBASE_IQ) {
			if (functionName.equalsIgnoreCase("char_length")) {
				return wrapArgs(functionName, args);
			}
			return wrapArgs("datalength", args);
		}
		return super.IGNORE;
	}

}
