package org.sagacity.sqltoy.plugins.function.impl;

import java.util.Locale;
import java.util.regex.Pattern;

import org.sagacity.sqltoy.plugins.function.IFunction;
import org.sagacity.sqltoy.utils.DataSourceUtils.DBType;

/**
 * @project sagacity-sqltoy
 * @description 字符串去除两边的空白
 * @author zhongxuchen
 * @version v1.0,Date:2013-03-21
 */
public class Trim extends IFunction {
	private static Pattern regex = Pattern.compile("(?i)\\Wtrim\\(");

	/*
	 * (non-Javadoc)
	 * 
	 * @see org.sagacity.sqltoy.config.function.IFunction#dialects()
	 */
	@Override
	public String dialects() {
		return ALL;
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
		if (args == null || args.length == 0) {
			return super.IGNORE;
		}
		// update 2026-9-5 修饰符形态(trim(both 'x' from col)等)按目标库分级处理:
		// H2/mysql/oracle/pg/db2原生支持标准修饰符形态原样保留;
		// sqlserver的TRIM不支持both/leading/trailing关键字,剔除字符为空格(或未写)时
		// 按语义转ltrim/rtrim/rtrim(ltrim),非空格字符无法表达则原样保留响亮报错;
		// sqlite无FROM形态,但trim/ltrim/rtrim的二参剔除字符集语义与标准一致,可无损映射
		String arg = args[0].trim();
		String argLow = arg.toLowerCase(Locale.ROOT);
		boolean modifierForm = argLow.contains(" from ");
		// update 2026-9-9 rest按实际前导关键字长度截取:原按keyword.length()定长截取,
		// 对省略BOTH/LEADING/TRAILING关键字的trim('x' from col)形态会把remstr首部误截掉
		String keyword = "both";
		String rest = "";
		if (modifierForm) {
			if (argLow.startsWith("leading ")) {
				keyword = "leading";
				rest = arg.substring(8).trim();
			} else if (argLow.startsWith("trailing ")) {
				keyword = "trailing";
				rest = arg.substring(9).trim();
			} else if (argLow.startsWith("both ")) {
				rest = arg.substring(5).trim();
			} else {
				// trim('x' from col)/trim(from col)形态,无关键字前缀,rest保持整体
				rest = arg;
			}
		}
		int fromIdx = rest.toLowerCase(Locale.ROOT).startsWith("from ") ? 0
				: rest.toLowerCase(Locale.ROOT).indexOf(" from ");
		if (dialect == DBType.H2) {
			// H2原生支持标准修饰符形态
			if (modifierForm) {
				return super.IGNORE;
			}
			return "trim(both ' ' from " + arg + ")";
		}
		if (dialect == DBType.SQLITE) {
			// sqlite无FROM形态:转trim/ltrim/rtrim,剔除字符集语义与标准一致(无剔除字符为空格)
			if (!modifierForm) {
				return super.IGNORE;
			}
			if (fromIdx == -1) {
				return super.IGNORE;
			}
			String chars = (fromIdx > 0) ? rest.substring(0, fromIdx).trim() : "";
			String col = (fromIdx == 0) ? rest.substring(5).trim() : rest.substring(fromIdx + 6).trim();
			if ("leading".equals(keyword)) {
				return "ltrim(" + col + (chars.isEmpty() ? "" : "," + chars) + ")";
			}
			if ("trailing".equals(keyword)) {
				return "rtrim(" + col + (chars.isEmpty() ? "" : "," + chars) + ")";
			}
			return "trim(" + col + (chars.isEmpty() ? "" : "," + chars) + ")";
		}
		if (dialect == DBType.SQLSERVER) {
			if (!modifierForm) {
				return "rtrim(ltrim(" + arg + "))";
			}
			if (fromIdx == -1) {
				return super.IGNORE;
			}
			// from之前的剔除字符(去引号),未写视为空格
			String chars = (fromIdx > 0) ? rest.substring(0, fromIdx).replace("'", "").trim() : "";
			String col = (fromIdx == 0) ? rest.substring(5).trim() : rest.substring(fromIdx + 6).trim();
			// 剔除字符非空格:sqlserver无法用ltrim/rtrim表达,原样保留交由目标库报错
			if (!chars.isEmpty()) {
				return super.IGNORE;
			}
			if ("leading".equals(keyword)) {
				return "ltrim(" + col + ")";
			}
			if ("trailing".equals(keyword)) {
				return "rtrim(" + col + ")";
			}
			return "rtrim(ltrim(" + col + "))";
		}
		return super.IGNORE;
	}
}