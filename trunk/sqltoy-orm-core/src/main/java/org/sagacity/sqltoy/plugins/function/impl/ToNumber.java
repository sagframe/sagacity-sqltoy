package org.sagacity.sqltoy.plugins.function.impl;

import java.util.regex.Pattern;

import org.sagacity.sqltoy.plugins.function.IFunction;
import org.sagacity.sqltoy.utils.DataSourceUtils.DBType;

/**
 * @project sagacity-sqltoy
 * @description 数字转换
 * @author zhongxuchen
 * @version v1.0,Date:2013-01-02
 * @modify Date:2026-9-5 实现常用形态转换:单参to_number(expr)按目标库转数值CAST;
 *         oracle系原生to_number原样保留;带格式模型的2参/3参形态(如'9999.99')各库格式模型差异大,
 *         不转换原样保留(pg系2参to_number(text,text)原生支持,保留恰好正确);
 *         精度契约:DECIMAL(20,6),超出20位整数或6位小数的值会被舍入,更高精度要求请直接使用原生数值列
 */
public class ToNumber extends IFunction {
	private static Pattern regex = Pattern.compile("(?i)\\Wto_number\\(");

	/*
	 * (non-Javadoc)
	 * 
	 * @see org.sagacity.sqltoy.config.function.IFunction#regex()
	 */
	@Override
	public Pattern regex() {
		return regex;
	}

	@Override
	public String dialects() {
		return ALL;
	}

	/*
	 * (non-Javadoc)
	 * 
	 * @see org.sagacity.sqltoy.config.function.IFunction#wrap(java.lang.String [])
	 */
	@Override
	public String wrap(int dialect, String functionName, boolean hasArgs, String... args) {
		if (args == null || args.length == 0) {
			return super.IGNORE;
		}
		// oracle系原生to_number原样保留
		if (dialect == DBType.ORACLE || dialect == DBType.DM || dialect == DBType.OCEANBASE
				|| dialect == DBType.ORACLE11) {
			return super.IGNORE;
		}
		// 带格式模型或nls参数的形态,各库格式模型差异大,不转换原样保留(pg系2参to_number原生支持)
		if (args.length > 1) {
			return super.IGNORE;
		}
		// PG系numeric为任意精度,无损
		if (dialect == DBType.POSTGRESQL || dialect == DBType.POSTGRESQL14 || dialect == DBType.GAUSSDB
				|| dialect == DBType.MOGDB || dialect == DBType.OPENGAUSS || dialect == DBType.VASTBASE
				|| dialect == DBType.STARDB || dialect == DBType.OSCAR) {
			return "CAST(" + args[0] + " AS numeric)";
		}
		// mysql系/sqlserver/H2/DB2:DECIMAL(20,6)精度契约见类注释
		if (dialect == DBType.MYSQL || dialect == DBType.TIDB || dialect == DBType.MYSQL57 || dialect == DBType.DORIS
				|| dialect == DBType.STARROCKS || dialect == DBType.H2 || dialect == DBType.SQLSERVER
				|| dialect == DBType.DB2) {
			return "CAST(" + args[0] + " AS DECIMAL(20,6))";
		}
		return super.IGNORE;
	}
}
