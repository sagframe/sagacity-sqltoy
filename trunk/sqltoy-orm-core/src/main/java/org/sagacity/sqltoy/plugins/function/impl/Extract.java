package org.sagacity.sqltoy.plugins.function.impl;

import java.util.Locale;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import org.sagacity.sqltoy.plugins.function.FunctionUtils;
import org.sagacity.sqltoy.plugins.function.IFunction;
import org.sagacity.sqltoy.utils.DataSourceUtils.DBType;

/**
 * @project sagacity-sqltoy
 * @description 标准EXTRACT(分量 FROM x)与pg的date_part('分量',x)作为源形态的跨库适配
 *              (A方案2026-10-11,遵循"不能只认mysql写法"——pg/oracle用户的原生写法)。
 *              支持分量:YEAR/MONTH/DAY/QUARTER/HOUR/MINUTE/SECOND。
 *              绝大多数库EXTRACT原生(mysql系/pg系/og族/kingbase/vastbase/ch/hana/h2/
 *              db2/oracle[年月日]);例外:sqlserver转DATEPART;sqlite转cast+strftime;
 *              oracle的QUARTER不被EXTRACT支持(ORA-00907,真库实测)转to_number(to_char);
 *              db2的QUARTER走原生QUARTER(x);hana的QUARTER实测返回'2026-Q1'非数值,
 *              以月份算术承担;oracle的时分秒须timestamp定型,包CAST。
 * @author zhongxuchen
 * @version v1.0,Date:2026-10-11
 */
public class Extract extends IFunction {
	private static Pattern regex = Pattern.compile("(?i)\\W(extract|date_part)\\(");

	// extract(x from y)参数内的分量 FROM 分隔
	private static Pattern FROM_PATTERN = Pattern.compile("(?i)^(\\w+)\\s+from\\s+(.+)$");

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
		if (args == null || args.length == 0) {
			return super.IGNORE;
		}
		String funLow = functionName.toLowerCase(Locale.ROOT);
		String part;
		String base;
		if ("date_part".equals(funLow)) {
			if (args.length < 2) {
				return super.IGNORE;
			}
			part = args[0].trim().replace("'", "").toUpperCase(Locale.ROOT);
			base = args[1].trim();
		} else {
			Matcher m = FROM_PATTERN.matcher(args[0].trim());
			if (!m.matches()) {
				// 非分量FROM形态(异常写法),原样保留响亮
				return super.IGNORE;
			}
			part = m.group(1).toUpperCase(Locale.ROOT);
			base = m.group(2).trim();
		}
		if (!isSupported(part)) {
			return super.IGNORE;
		}
		// 原生EXTRACT族(mysql系含OB/tidb/doris/starrocks/pg系含og族/kingbase/vastbase/
		// ch/hana/h2;oracle年月日原生):归一EXTRACT形态渲染(幂等)
		boolean extractNative = dialect == DBType.MYSQL || dialect == DBType.MYSQL57 || dialect == DBType.TIDB
				|| dialect == DBType.DORIS || dialect == DBType.STARROCKS || dialect == DBType.OCEANBASE
				|| dialect == DBType.POSTGRESQL || dialect == DBType.POSTGRESQL14 || dialect == DBType.GAUSSDB
				|| dialect == DBType.OPENGAUSS || dialect == DBType.MOGDB || dialect == DBType.VASTBASE
				|| dialect == DBType.OSCAR || dialect == DBType.STARDB || dialect == DBType.KINGBASE
				|| dialect == DBType.HIGHGO || dialect == DBType.CLICKHOUSE || dialect == DBType.HANA
				|| dialect == DBType.H2 || dialect == DBType.GBASE8S;
		if (extractNative) {
			// 基参定型按家族:pg系::timestamp;hana包to_date;mysql系/ch/h2裸参原生可解析
			String typed = typedBase(dialect, base);
			if ("QUARTER".equals(part)) {
				if (dialect == DBType.HANA) {
					return "(floor((month(" + typed + ")-1)/3)+1)";
				}
				return "EXTRACT(QUARTER FROM " + typed + ")";
			}
			return "EXTRACT(" + part + " FROM " + typed + ")";
		}
		if (dialect == DBType.SQLSERVER) {
			return "DATEPART(" + part.toLowerCase(Locale.ROOT) + "," + base + ")";
		}
		if (dialect == DBType.SQLITE) {
			if ("QUARTER".equals(part)) {
				String m1 = FunctionUtils.sqliteDateTextExpr(base);
				return "(cast((cast(strftime('%m'," + m1 + ") as integer)-1)/3 as integer)+1)";
			}
			String fmt = fmtOf(part);
			return "cast(strftime('" + fmt + "'," + FunctionUtils.sqliteDateTextExpr(base) + ") as integer)";
		}
		// oracle/oracle11:年月日原生;QUARTER不被EXTRACT支持(实测)转to_char('Q');
		// 时分秒须timestamp定型包CAST(DATE列直接EXTRACT报错)
		if (dialect == DBType.ORACLE || dialect == DBType.ORACLE11) {
			String ob = FunctionUtils.oracleDateLiteralExpr(base);
			if ("QUARTER".equals(part)) {
				return "to_number(to_char(" + ob + ",'Q'))";
			}
			if ("HOUR".equals(part) || "MINUTE".equals(part) || "SECOND".equals(part)) {
				return "EXTRACT(" + part + " FROM CAST(" + ob + " AS TIMESTAMP))";
			}
			return "EXTRACT(" + part + " FROM " + ob + ")";
		}
		// db2:EXTRACT年月日时分秒原生;QUARTER走原生QUARTER(x)
		if (dialect == DBType.DB2) {
			if ("QUARTER".equals(part)) {
				return "QUARTER(" + base + ")";
			}
			return "EXTRACT(" + part + " FROM " + base + ")";
		}
		return super.IGNORE;
	}

	private static boolean isSupported(String part) {
		return "YEAR".equals(part) || "MONTH".equals(part) || "DAY".equals(part) || "QUARTER".equals(part)
				|| "HOUR".equals(part) || "MINUTE".equals(part) || "SECOND".equals(part);
	}

	// 原生EXTRACT族的基参定型:pg系字面量包::timestamp(og7等严格类型);hana包to_date;
	// mysql系/ch/h2对裸字面量原生可解析,原样返回
	private static String typedBase(int dialect, String base) {
		if (dialect == DBType.POSTGRESQL || dialect == DBType.POSTGRESQL14 || dialect == DBType.GAUSSDB
				|| dialect == DBType.OPENGAUSS || dialect == DBType.MOGDB || dialect == DBType.VASTBASE
				|| dialect == DBType.OSCAR || dialect == DBType.STARDB || dialect == DBType.KINGBASE
				|| dialect == DBType.HIGHGO) {
			return FunctionUtils.castLiteralTimestamp(base);
		}
		if (dialect == DBType.HANA) {
			return FunctionUtils.oracleDateLiteralExpr(base);
		}
		return base;
	}

	private static String fmtOf(String part) {
		switch (part) {
		case "YEAR":
			return "%Y";
		case "MONTH":
			return "%m";
		case "HOUR":
			return "%H";
		case "MINUTE":
			return "%M";
		case "SECOND":
			return "%S";
		default:
			return "%d";
		}
	}
}
