package org.sagacity.sqltoy.plugins.function.impl;

import java.util.Locale;
import java.util.regex.Pattern;

import org.sagacity.sqltoy.plugins.function.IFunction;
import org.sagacity.sqltoy.utils.DataSourceUtils.DBType;

/**
 * @project sagacity-sqltoy
 * @description left(str,n)/right(str,n)前缀后缀取串的跨库适配(A方案2026-10-11)。
 *              绝大多数库原生(mysql系含OB/tidb/doris/starrocks/pg系含og族/kingbase/
 *              vastbase/sqlserver/db2/dm/h2/ch/hana均真库实测原生);仅oracle与sqlite
 *              无此二函数,转substr形态(两库substr负起点均自尾部取,probed)。
 * @author zhongxuchen
 * @version v1.0,Date:2026-10-11
 */
public class LeftRight extends IFunction {
	private static Pattern regex = Pattern.compile("(?i)\\W(left|right)\\(");

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
		boolean isLeft = "left".equals(functionName.toLowerCase(Locale.ROOT));
		// oracle/sqlite无left()/right():substr等效(oracle系字面量日期不涉及,此处为字符串参)
		if (dialect == DBType.ORACLE || dialect == DBType.ORACLE11 || dialect == DBType.SQLITE) {
			return isLeft ? "substr(" + args[0] + ",1," + args[1] + ")" : "substr(" + args[0] + ",-" + args[1] + ")";
		}
		return super.IGNORE;
	}
}
