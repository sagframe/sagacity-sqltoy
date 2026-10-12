package org.sagacity.sqltoy.plugins.function.impl;

import java.util.Locale;
import java.util.regex.Pattern;

import org.sagacity.sqltoy.plugins.function.IFunction;
import org.sagacity.sqltoy.utils.DataSourceUtils.DBType;

/**
 * @project sagacity-sqltoy
 * @description lpad/rpad定长填充的跨库适配(A方案2026-10-11)。除sqlserver与sqlite外
 *              全部原生(mysql系/pg系含og族/kingbase/vastbase/oracle/db2/dm/h2/ch/
 *              hana真库实测);sqlserver以REPLICATE组合(注意mysql超长截断取前段的语义
 *              须先LEFT(x,n)再拼填充);sqlite无REPLICATE等价物,响亮放弃。
 * @author zhongxuchen
 * @version v1.0,Date:2026-10-11
 */
public class Pad extends IFunction {
	private static Pattern regex = Pattern.compile("(?i)\\W(lpad|rpad)\\(");

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
		boolean isLpad = "lpad".equals(functionName.toLowerCase(Locale.ROOT));
		// 填充字符默认空格(mysql契约);sqlite无REPLICATE等价,响亮保留
		String pad = (args.length > 2) ? args[2] : "' '";
		if (dialect == DBType.SQLSERVER) {
			// mysql超长截断保留前n段:先LEFT(x,n)再拼填充后取定长
			if (isLpad) {
				return "RIGHT(REPLICATE(" + pad + "," + args[1] + ")+LEFT(" + args[0] + "," + args[1] + ")," + args[1]
						+ ")";
			}
			return "LEFT(LEFT(" + args[0] + "," + args[1] + ")+REPLICATE(" + pad + "," + args[1] + ")," + args[1] + ")";
		}
		return super.IGNORE;
	}
}
