package org.sagacity.sqltoy.plugins.function.impl;

import java.util.Locale;
import java.util.regex.Pattern;

import org.sagacity.sqltoy.plugins.function.IFunction;
import org.sagacity.sqltoy.utils.DataSourceUtils.DBType;

/**
 * @project sagacity-sqltoy
 * @description 提供不同数据库length函数的转换(主要是length和len之间的互换)
 * @author zhongxuchen
 * @version v1.0,Date:2015-10-19
 */
public class Length extends IFunction {
	private static Pattern regex = Pattern.compile("(?i)\\W(length|lengthb|len|datalength|char_length)\\(");

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
		if (args == null || args.length == 0) {
			return super.IGNORE;
		}
		String funLow = functionName.toLowerCase(Locale.ROOT);
		if (dialect == DBType.SQLSERVER) {
			if ("datalength".equals(funLow)) {
				return wrapArgs("datalength", args);
			}
			return wrapArgs("len", args);
		}
		if (dialect == DBType.ORACLE || dialect == DBType.POSTGRESQL || dialect == DBType.POSTGRESQL14
				|| dialect == DBType.DB2 || dialect == DBType.GAUSSDB || dialect == DBType.MOGDB
				|| dialect == DBType.VASTBASE || dialect == DBType.OPENGAUSS || dialect == DBType.STARDB
				|| dialect == DBType.OSCAR || dialect == DBType.OCEANBASE || dialect == DBType.DM
				|| dialect == DBType.ORACLE11) {
			// update 2026-9-5 PG系与DB2无lengthb函数,转为octet_length(字节长度);
			// oracle/DM/OCEANBASE原生支持lengthb,原样保留
			if ((dialect == DBType.POSTGRESQL || dialect == DBType.POSTGRESQL14 || dialect == DBType.DB2
					|| dialect == DBType.GAUSSDB || dialect == DBType.MOGDB || dialect == DBType.VASTBASE
					|| dialect == DBType.OPENGAUSS || dialect == DBType.STARDB) && "lengthb".equals(funLow)) {
				return wrapArgs("octet_length", args);
			}
			if ("datalength".equals(funLow) || "char_length".equals(funLow) || "len".equals(funLow)) {
				return wrapArgs("length", args);
			}
			return wrapArgs(functionName, args);
		}
		if (dialect == DBType.MYSQL || dialect == DBType.TIDB || dialect == DBType.MYSQL57 || dialect == DBType.H2
				|| dialect == DBType.DORIS || dialect == DBType.STARROCKS) {
			if ("char_length".equals(funLow)) {
				return wrapArgs(functionName, args);
			}
			return wrapArgs("length", args);
		}
		return super.IGNORE;
	}

}
