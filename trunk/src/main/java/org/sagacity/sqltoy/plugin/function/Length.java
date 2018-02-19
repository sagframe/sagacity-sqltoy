/**
 * 
 */
package org.sagacity.sqltoy.plugin.function;

import org.sagacity.sqltoy.plugin.IFunction;
import org.sagacity.sqltoy.utils.DataSourceUtils.DBType;

/**
 * @project sqltoy-orm
 * @description 提供不同数据库length函数的转换(主要是length和len之间的互换)
 * @author chenrenfei <a href="mailto:zhongxuchen@gmail.com">联系作者</a>
 * @version id:Length.java,Revision:v1.0,Date:2015年10月19日
 */
public class Length extends IFunction {

	/*
	 * (non-Javadoc)
	 * 
	 * @see org.sagacity.sqltoy.config.function.IFunction#dialects()
	 */
	@Override
	public String dialects() {
		return "";
	}

	/*
	 * (non-Javadoc)
	 * 
	 * @see org.sagacity.sqltoy.config.function.IFunction#regex()
	 */
	@Override
	public String regex() {
		return "(?i)\\W(length|lengthb|len|datalength|char\\_length)\\(";
	}

	/*
	 * (non-Javadoc)
	 * 
	 * @see org.sagacity.sqltoy.config.function.IFunction#wrap(int,
	 * java.lang.String, boolean, java.lang.String[])
	 */
	@Override
	public String wrap(int dialect, String functionName, boolean hasArgs, String... args) {
		if (dialect == DBType.SQLSERVER || dialect == DBType.SQLSERVER2017 || dialect == DBType.SQLSERVER2014
				|| dialect == DBType.SQLSERVER2016) {
			if (functionName.equalsIgnoreCase("datalength"))
				return wrapArgs("datalength", args);
			else
				return wrapArgs("len", args);
		} else if (dialect == DBType.SYBASE_IQ) {
			if (functionName.equalsIgnoreCase("char_length"))
				return wrapArgs(functionName, args);
			else
				return wrapArgs("datalength", args);
		} else if (dialect == DBType.DB2 || dialect == DBType.DB2_11 || dialect == DBType.ORACLE
				|| dialect == DBType.ORACLE12 || dialect == DBType.POSTGRESQL || dialect == DBType.POSTGRESQL10) {
			if (functionName.equalsIgnoreCase("datalength") || functionName.equalsIgnoreCase("char_length")
					|| functionName.equalsIgnoreCase("len")) {
				return wrapArgs("length", args);
			} else
				return wrapArgs(functionName, args);
		} else if (dialect == DBType.MYSQL || dialect == DBType.MYSQL8) {
			if (functionName.equalsIgnoreCase("char_length"))
				return wrapArgs(functionName, args);
			else
				return wrapArgs("length", args);
		}
		return null;
	}

}
