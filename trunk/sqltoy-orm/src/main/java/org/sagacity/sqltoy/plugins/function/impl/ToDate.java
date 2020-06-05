/**
 * 
 */
package org.sagacity.sqltoy.plugins.function.impl;

import java.util.regex.Pattern;

import org.sagacity.sqltoy.plugins.function.IFunction;

/**
 * @project sqltoy-orm
 * @description 转换to_date函数
 * @author renfei.chen <a href="mailto:zhongxuchen@gmail.com">联系作者</a>
 * @version id:ToDate.java,Revision:v1.0,Date:2013-1-2
 */
public class ToDate extends IFunction {
	private static Pattern regex = Pattern.compile("(?i)\\Wto\\_date\\(");

	public String dialects() {
		return "oracle,dm";
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
	 * @see org.sagacity.sqltoy.config.function.IFunction#wrap(java.lang.String [])
	 */
	@Override
	public String wrap(int dialect, String functionName, boolean hasArgs, String... args) {
		return super.IGNORE;
	}
}
