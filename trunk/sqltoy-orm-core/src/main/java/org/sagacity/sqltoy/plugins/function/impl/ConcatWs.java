package org.sagacity.sqltoy.plugins.function.impl;

import java.util.regex.Pattern;

import org.sagacity.sqltoy.plugins.function.IFunction;
import org.sagacity.sqltoy.utils.DataSourceUtils.DBType;

/**
 * @project sagacity-sqltoy
 * @description 针对mysql数据库字符连接函数concat_ws在其它数据库中的函数转换
 * @author zhongxuchen
 * @version v1.0,Date:2013-03-21
 */
public class ConcatWs extends IFunction {
	private static Pattern regex = Pattern.compile("(?i)\\Wconcat_ws\\(");

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
		// update 2026-9-5 补充OCEANBASE(oracle模式无concat_ws)/DB2/sqlite的||拼接转换
		// (此前仅oracle系处理,这些库原样输出concat_ws非法);
		// 已知边界:mysql concat_ws跳过null参数,||拼接在oracle系将null视为空串、db2/sqlite传播null,
		// 参数可能为null时结果存在差异
		if (dialect == DBType.ORACLE || dialect == DBType.ORACLE11 || dialect == DBType.OCEANBASE
				|| dialect == DBType.DB2 || dialect == DBType.SQLITE) {
			StringBuilder result = new StringBuilder();
			String split = args[0].replace("\\'", "''");
			for (int i = 1; i < args.length; i++) {
				if (i > 1) {
					result.append("||").append(split).append("||");
				}
				result.append(args[i].replace("\\'", "''"));
			}
			return result.toString();
		} else if (dialect == DBType.DM) {
			String splitStr = args[0].trim();
			// dm concat_ws不支持双引号包装分割符号
			if (splitStr.startsWith("\"") && splitStr.endsWith("\"")) {
				args[0] = "'" + splitStr.substring(1, splitStr.length() - 1) + "'";
				return wrapArgs("concat_ws", args);
			}
		}
		return super.IGNORE;
	}

}
