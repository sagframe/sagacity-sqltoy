/**
 * 
 */
package org.sagacity.sqltoy.plugins.function.impl;

import java.util.regex.Pattern;

import org.sagacity.sqltoy.plugins.function.IFunction;
import org.sagacity.sqltoy.utils.DataSourceUtils.DBType;

/**
 * @project sqltoy-orm
 * @description 数据库判断空的处理逻辑函数转换
 * @author renfei.chen <a href="mailto:zhongxuchen@hotmail.com">联系作者</a>
 * @version Revision:v1.0,Date:2013-4-12
 * @Modification Date:2013-4-12 {填写修改说明}
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
		return "";
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
				|| dialect == DBType.SQLSERVER2016 || dialect == DBType.SQLSERVER2019)
			return wrapArgs("isnull", args);
		else if (dialect == DBType.DB2 || dialect == DBType.POSTGRESQL)
			return wrapArgs("coalesce", args);
		else if (dialect == DBType.MYSQL || dialect == DBType.MYSQL8 || dialect == DBType.SYBASE_IQ) {
			if (args.length == 1)
				return wrapArgs("isnull", args);
			else
				return wrapArgs("ifnull", args);
		} else if (dialect == DBType.SQLITE)
			return wrapArgs("ifnull", args);
		else if (dialect == DBType.ORACLE || dialect == DBType.ORACLE12)
			return wrapArgs("nvl", args);
		return null;
	}

}
