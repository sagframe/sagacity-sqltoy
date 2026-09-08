package org.sagacity.sqltoy.plugins.function.impl;

import java.util.regex.Pattern;

import org.sagacity.sqltoy.plugins.function.IFunction;
import org.sagacity.sqltoy.utils.DataSourceUtils.DBType;

/**
 * @project sagacity-sqltoy
 * @description 针对mysql数据库字符连接函数concat在其它数据库中的函数转换
 * @author zhongxuchen
 * @version v1.0,Date:2013-03-21
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
		return super.ALL;
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
		if (args == null || args.length < 2) {
			return super.IGNORE;
		}
		// update 2026-9-5 sqlite无concat函数、DB2/oracle系(含OCEANBASE,与Nvl/Now等函数的oracle系
		// 归属保持一致)的concat仅支持两参:超出支持的参数个数统一转||拼接
		// (sqlite从两参起转,oracle/db2/oceanbase三参起转)
		if (dialect == DBType.ORACLE || dialect == DBType.ORACLE11 || dialect == DBType.DB2 || dialect == DBType.SQLITE
				|| dialect == DBType.OCEANBASE) {
			if (dialect != DBType.SQLITE && args.length < 3) {
				return super.IGNORE;
			}
			StringBuilder result = new StringBuilder();
			for (int i = 0; i < args.length; i++) {
				if (i > 0) {
					result.append("||");
				}
				result.append(args[i].replace("\\'", "''"));
			}
			return result.toString();
		}
		return super.IGNORE;
	}
}
