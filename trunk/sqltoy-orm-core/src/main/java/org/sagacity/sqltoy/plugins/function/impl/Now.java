package org.sagacity.sqltoy.plugins.function.impl;

import java.util.regex.Pattern;

import org.sagacity.sqltoy.plugins.function.IFunction;
import org.sagacity.sqltoy.utils.DataSourceUtils.DBType;

/**
 * @project sagacity-sqltoy
 * @description 不同数据库当前系统时间获取方式
 * @author zhongxuchen
 * @version v1.0,Date:2013-03-25
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
		// mysql系now(fsp)支持小数秒精度参数,保留
		if (dialect == DBType.MYSQL || dialect == DBType.TIDB || dialect == DBType.MYSQL57 || dialect == DBType.DORIS
				|| dialect == DBType.STARROCKS) {
			return wrapArgs("now", args);
		}
		// update 2026-9-5 其余支持now()的库不带fsp参数
		// (mysql特有的now(6)转到这些库需丢弃参数,否则语法非法)
		if (dialect == DBType.POSTGRESQL || dialect == DBType.POSTGRESQL14 || dialect == DBType.GAUSSDB
				|| dialect == DBType.OPENGAUSS || dialect == DBType.MOGDB || dialect == DBType.STARDB
				|| dialect == DBType.OSCAR || dialect == DBType.VASTBASE || dialect == DBType.H2
				|| dialect == DBType.CLICKHOUSE) {
			return "now()";
		}
		if (dialect == DBType.ORACLE || dialect == DBType.OCEANBASE || dialect == DBType.DM
				|| dialect == DBType.ORACLE11) {
			// (H2在now()分支已处理,此处不再重复)
			return "sysdate";
		}
		if (dialect == DBType.SQLSERVER) {
			return wrapArgs("getdate", args);
		}
		// update 2026-9-5
		// 补充DB2/sqlite(此前sysdate/now/getdate在目标原样输出,DB2无这些函数,sqlite用CURRENT_TIMESTAMP)
		if (dialect == DBType.DB2) {
			return "CURRENT TIMESTAMP";
		}
		if (dialect == DBType.SQLITE) {
			return "CURRENT_TIMESTAMP";
		}
		return super.IGNORE;
	}
}
