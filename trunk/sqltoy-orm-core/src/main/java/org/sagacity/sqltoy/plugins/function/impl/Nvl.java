/**
 *
 */
package org.sagacity.sqltoy.plugins.function.impl;

import java.util.regex.Pattern;

import org.sagacity.sqltoy.plugins.function.IFunction;
import org.sagacity.sqltoy.utils.DataSourceUtils.DBType;

/**
 * @author zhongxuchen
 * @version v1.0, Date:2013-4-12
 * @project sqltoy-orm
 * @description 数据库判断空的处理逻辑函数转换
 * @modify Date:2013-4-12 {填写修改说明}
 */
public class Nvl extends IFunction {

	private static Pattern regex = Pattern.compile("(?i)\\W(nvl|isnull|ifnull|coalesce)\\(");

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
		if (args == null || args.length == 0) {
			return super.IGNORE;
		}
		String funLow = functionName.toLowerCase();
		if (dialect == DBType.SQLSERVER) {
			return wrapArgs("isnull", args);
		}
		if (dialect == DBType.POSTGRESQL || dialect == DBType.POSTGRESQL15 || dialect == DBType.DB2
				|| dialect == DBType.GAUSSDB || dialect == DBType.MOGDB || dialect == DBType.H2) {
			return wrapArgs("coalesce", args);
		}
		if (dialect == DBType.MYSQL || dialect == DBType.TIDB || dialect == DBType.MYSQL57) {
			if (args.length == 1) {
				return wrapArgs("isnull", args);
			}
			return wrapArgs("ifnull", args);
		}
		if (dialect == DBType.SQLITE) {
			return wrapArgs("ifnull", args);
		}
		if (dialect == DBType.ORACLE || dialect == DBType.DM || dialect == DBType.OCEANBASE
				|| dialect == DBType.ORACLE11) {
			return wrapArgs("nvl", args);
		}
		if (dialect == DBType.H2) {
			if ("coalesce".equals(funLow)) {
				return wrapArgs("coalesce", args);
			}
			return wrapArgs("ifnull", args);
		}
		return super.IGNORE;
	}

}
