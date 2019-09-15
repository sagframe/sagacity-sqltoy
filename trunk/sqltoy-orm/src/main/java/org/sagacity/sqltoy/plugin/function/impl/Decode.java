/**
 * 
 */
package org.sagacity.sqltoy.plugin.function.impl;

import org.sagacity.sqltoy.plugin.function.IFunction;
import org.sagacity.sqltoy.utils.DataSourceUtils.DBType;

/**
 * @project sqltoy-orm
 * @description oracle decode函数
 * @author renfei.chen <a href="mailto:zhongxuchen@gmail.com">联系作者</a>
 * @version id:Decode.java,Revision:v1.0,Date:2013-1-2
 */
public class Decode extends IFunction {
	/*
	 * (non-Javadoc)
	 * 
	 * @see org.sagacity.sqltoy.config.function.IFunction#regex()
	 */
	@Override
	public String regex() {
		return "(?i)\\Wdecode\\(";
	}

	public String dialects() {
		return "mysql8";
	}

	/*
	 * (non-Javadoc)
	 * 
	 * @see org.sagacity.sqltoy.config.function.IFunction#wrap(java.lang.String [])
	 */
	@Override
	public String wrap(int dialect, String functionName, boolean hasArgs, String... args) {
		if (dialect == DBType.MYSQL || dialect == DBType.MYSQL8) {
			return wrapArgs("ELT", args);
		}
		return null;
	}
}
