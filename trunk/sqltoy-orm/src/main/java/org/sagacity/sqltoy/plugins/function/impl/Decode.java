/**
 * 
 */
package org.sagacity.sqltoy.plugins.function.impl;

import java.util.regex.Pattern;

import org.sagacity.sqltoy.plugins.function.IFunction;
import org.sagacity.sqltoy.utils.DataSourceUtils.DBType;

/**
 * @project sqltoy-orm
 * @description oracle decode函数
 * @author renfei.chen <a href="mailto:zhongxuchen@gmail.com">联系作者</a>
 * @version id:Decode.java,Revision:v1.0,Date:2013-1-2
 */
public class Decode extends IFunction {
	private static Pattern regex = Pattern.compile("(?i)\\Wdecode\\(");

	/*
	 * (non-Javadoc)
	 * 
	 * @see org.sagacity.sqltoy.config.function.IFunction#regex()
	 */
	@Override
	public Pattern regex() {
		return regex;
	}

	public String dialects() {
		return super.ALL;
	}

	/*
	 * (non-Javadoc)
	 * 
	 * @see org.sagacity.sqltoy.config.function.IFunction#wrap(java.lang.String [])
	 */
	@Override
	public String wrap(int dialect, String functionName, boolean hasArgs, String... args) {
		/*
		 * if (dialect == DBType.MYSQL || dialect == DBType.MYSQL8) { return
		 * wrapArgs("ELT", args); } else
		 */
		// oracle支持decode
		if (dialect == DBType.ORACLE || dialect == DBType.DM || dialect == DBType.OCEANBASE
				|| dialect == DBType.ORACLE11) {
			return super.IGNORE;
		}
		// decode(param,a1,a11,a2,a21,other)
		String param = args[0];
		StringBuilder sql = new StringBuilder(" case ");
		int loopSize = (args.length - 1) / 2;
		for (int i = 0; i < loopSize; i++) {
			sql.append(" when ").append(param).append("=").append(args[1 + i * 2]).append(" then ")
					.append(args[1 + i * 2 + 1]);
		}
		sql.append(" else ").append(args[args.length - 1]);
		sql.append(" end ");
		return sql.toString();
	}
}
