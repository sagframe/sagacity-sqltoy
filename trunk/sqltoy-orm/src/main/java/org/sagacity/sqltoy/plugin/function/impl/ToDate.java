/**
 * 
 */
package org.sagacity.sqltoy.plugin.function.impl;

import org.sagacity.sqltoy.plugin.function.IFunction;

/**
 * @project sqltoy-orm
 * @description 转换to_date函数
 * @author renfei.chen <a href="mailto:zhongxuchen@gmail.com">联系作者</a>
 * @version id:ToDate.java,Revision:v1.0,Date:2013-1-2
 */
public class ToDate extends IFunction {
	public String dialects() {
		return "oracle12c";
	}

	/*
	 * (non-Javadoc)
	 * 
	 * @see org.sagacity.sqltoy.config.function.IFunction#regex()
	 */
	public String name() {
		return "to_date";
	}

	/*
	 * (non-Javadoc)
	 * 
	 * @see org.sagacity.sqltoy.config.function.IFunction#regex()
	 */
	@Override
	public String regex() {
		return "(?i)\\Wto\\_date\\(";
	}

	/*
	 * (non-Javadoc)
	 * 
	 * @see org.sagacity.sqltoy.config.function.IFunction#wrap(java.lang.String [])
	 */
	@Override
	public String wrap(int dialect, String functionName, boolean hasArgs, String... args) {
		return null;
	}
}
