package org.sagacity.sqltoy.plugins.function.impl;

import java.util.regex.Pattern;

import org.sagacity.sqltoy.plugins.function.IFunction;
import org.sagacity.sqltoy.utils.DataSourceUtils.DBType;

/**
 * @project sagacity-sqltoy
 * @description 将在mysql中使用的if函数转换成case when 通用模式
 * @author zhongxuchen
 * @version v1.0,Date:2019-10-21
 */
public class If extends IFunction {
	// update 2026-10-11 补iif源拼写(sqlserver用户写法,2012+原生;sqlite 3.32+亦原生)
	private static Pattern regex = Pattern.compile("(?i)\\W(if|iif)\\(");

	@Override
	public String dialects() {
		return ALL;
	}

	@Override
	public Pattern regex() {
		return regex;
	}

	@Override
	public String wrap(int dbType, String functionName, boolean hasArgs, String... args) {
		String funLow = functionName.toLowerCase(java.util.Locale.ROOT);
		if (dbType == DBType.MYSQL || dbType == DBType.TIDB || dbType == DBType.MYSQL57 || dbType == DBType.DORIS
				|| dbType == DBType.STARROCKS || dbType == DBType.CLICKHOUSE) {
			// update 2026-9-5 clickhouse原生支持if(cond,a,b),原样保留
			// (参数形态不完整时走下方args判空的IGNORE响亮路径)
			return ("if".equals(funLow)) ? super.IGNORE
					: (("iif".equals(funLow) && args != null && args.length >= 3)
							? "if(" + args[0] + "," + args[1] + "," + args[2] + ")"
							: super.IGNORE);
		}
		// update 2026-10-11 iif源拼写在sqlserver(2012+)/sqlite(3.32+)为原生,透传
		if ("iif".equals(funLow) && (dbType == DBType.SQLSERVER || dbType == DBType.SQLITE)) {
			return super.IGNORE;
		}
		if (args == null || args.length < 3) {
			return super.IGNORE;
		}
		return " case when " + args[0] + " then " + args[1] + " else " + args[2] + " end ";
	}

}
