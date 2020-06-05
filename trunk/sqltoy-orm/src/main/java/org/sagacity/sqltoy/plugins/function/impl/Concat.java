/**
 * 
 */
package org.sagacity.sqltoy.plugins.function.impl;

import java.util.regex.Pattern;

import org.sagacity.sqltoy.plugins.function.IFunction;
import org.sagacity.sqltoy.utils.DataSourceUtils.DBType;

/**
 * @project sqltoy-orm
 * @description 针对mysql数据库字符连接函数concat在其它数据库中的函数转换
 * @author renfei.chen <a href="mailto:zhongxuchen@gmail.com">联系作者</a>
 * @version id:Concat.java,Revision:v1.0,Date:2013-3-21
 */
public class Concat extends IFunction {
	private static Pattern regex = Pattern.compile("(?i)\\Wconcat\\(");

	/*
	 * (non-Javadoc)
	 * 
	 * @see org.sagacity.sqltoy.config.function.IFunction#dialects()
	 */
	@Override
	public String dialects() {
		return "oracle";
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
		// 只针对oracle数据库,其他数据库原样返回
		if (dialect == DBType.ORACLE || dialect == DBType.OCEANBASE || dialect == DBType.DM
				|| dialect == DBType.ORACLE11) {
			// 超过2个参数
			if (args != null && args.length > 2) {
				StringBuilder result = new StringBuilder();
				for (int i = 0; i < args.length; i++) {
					if (i > 0) {
						result.append("||");
					}
					result.append(args[i].replace("\\'", "''"));
				}
				return result.toString();
			}
		}
		return super.IGNORE;
	}
}
