package org.sagacity.sqltoy.plugins.function.impl;

import java.util.regex.Pattern;

import org.sagacity.sqltoy.plugins.function.IFunction;
import org.sagacity.sqltoy.utils.DataSourceUtils.DBType;

/**
 * @author zhongxuchen
 * @version v1.0,Date:2013-04-12
 * @project sagacity-sqltoy
 * @description 数据库判断空的处理逻辑函数转换
 * @modify Date:2013-04-12 填写修改说明
 */
public class Nvl extends IFunction {

	private static Pattern regex = Pattern.compile("(?i)\\W(nvl|isnull|ifnull)\\(");

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
		// String funLow = functionName.toLowerCase(Locale.ROOT);
		if (dialect == DBType.SQLSERVER) {
			return wrapArgs("isnull", args);
		}
		// update 2026-9-9 补CLICKHOUSE(原生coalesce,此前落IGNORE原样输出nvl报函数不存在)
		if (dialect == DBType.POSTGRESQL || dialect == DBType.POSTGRESQL14 || dialect == DBType.DB2
				|| dialect == DBType.OPENGAUSS || dialect == DBType.STARDB || dialect == DBType.OSCAR
				|| dialect == DBType.GAUSSDB || dialect == DBType.MOGDB || dialect == DBType.VASTBASE
				|| dialect == DBType.H2 || dialect == DBType.CLICKHOUSE) {
			return wrapArgs("coalesce", args);
		}
		if (dialect == DBType.MYSQL || dialect == DBType.TIDB || dialect == DBType.MYSQL57 || dialect == DBType.DORIS
				|| dialect == DBType.STARROCKS) {
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
		// (H2已在coalesce分支处理,此处原重复分支已移除)
		return super.IGNORE;
	}

}
