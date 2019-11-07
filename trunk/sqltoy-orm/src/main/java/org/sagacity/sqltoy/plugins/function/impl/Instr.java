/**
 * 
 */
package org.sagacity.sqltoy.plugins.function.impl;

import java.util.regex.Pattern;

import org.sagacity.sqltoy.plugins.function.IFunction;
import org.sagacity.sqltoy.utils.DataSourceUtils.DBType;

/**
 * @project sqltoy-orm
 * @description 针对不同数据库字符串indexOf 函数的不同用法转换
 * @author renfei.chen <a href="mailto:zhongxuchen@gmail.com">联系作者</a>
 * @version id:Instr.java,Revision:v1.0,Date:2013-3-21
 */
public class Instr extends IFunction {
	private static Pattern regex = Pattern.compile("(?i)\\W(instr|charindex)\\(");

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
		if (dialect == DBType.SQLSERVER || dialect == DBType.SQLSERVER2017 || dialect == DBType.SQLSERVER2014
				|| dialect == DBType.SQLSERVER2016 || dialect == DBType.SQLSERVER2019 || dialect == DBType.SYBASE_IQ) {
			if (functionName.equalsIgnoreCase("instr")) {
				StringBuilder result = new StringBuilder();
				result.append("charindex(").append(args[1]).append(",").append(args[0]);
				if (args.length == 3)
					result.append(",").append(args[2]);
				return result.append(")").toString();
			}
		} else if (dialect == DBType.DB2 || dialect == DBType.DB2_11 || dialect == DBType.ORACLE
				|| dialect == DBType.ORACLE12 || dialect == DBType.MYSQL || dialect == DBType.MYSQL8
				|| dialect == DBType.POSTGRESQL || dialect == DBType.POSTGRESQL11 || dialect == DBType.POSTGRESQL10) {
			if (functionName.equalsIgnoreCase("charindex")) {
				StringBuilder result = new StringBuilder();
				result.append("instr(").append(args[1]).append(",").append(args[0]);
				if (args.length == 3)
					result.append(",").append(args[2]);
				return result.append(")").toString();
			}
		}
		return super.IGNORE;
	}
}
