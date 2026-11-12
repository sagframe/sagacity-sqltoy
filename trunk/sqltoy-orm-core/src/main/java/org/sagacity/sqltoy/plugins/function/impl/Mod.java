package org.sagacity.sqltoy.plugins.function.impl;

import java.util.regex.Pattern;

import org.sagacity.sqltoy.plugins.function.IFunction;
import org.sagacity.sqltoy.utils.DataSourceUtils.DBType;

/**
 * @project sagacity-sqltoy
 * @description 取模函数mod(a,b)的跨库适配(2026-10-11真库探针:18环境仅sqlserver 无mod函数——'mod' is
 *              not a recognized built-in function name,其取模
 *              只有%运算符;其余mysql系/pg系/oracle/db2/dm/hana/ch/sqlite/h2/
 *              kingbase/starrocks等均原生)。sqlserver转(a % b)。
 *              边界:%运算符写法(oracle/hana不支持该运算符)非函数形态,超出函数名
 *              正则框架捕获范围,不适配——跨库SQL请统一写mod()函数(可移植性最好)。
 * @author zhongxuchen
 * @version v1.0,Date:2026-10-11
 */
public class Mod extends IFunction {
	private static Pattern regex = Pattern.compile("(?i)\\Wmod\\(");

	@Override
	public String dialects() {
		return ALL;
	}

	@Override
	public Pattern regex() {
		return regex;
	}

	@Override
	public String wrap(int dialect, String functionName, boolean hasArgs, String... args) {
		if (args == null || args.length < 2) {
			return super.IGNORE;
		}
		// sqlserver无mod函数(真库实测),%运算符承担;其余全库原生透传
		if (dialect == DBType.SQLSERVER) {
			return "(" + args[0].trim() + " % " + args[1].trim() + ")";
		}
		return super.IGNORE;
	}
}
