package org.sagacity.sqltoy.plugins.function.impl;

import java.util.Locale;
import java.util.regex.Pattern;

import org.sagacity.sqltoy.plugins.function.FunctionUtils;
import org.sagacity.sqltoy.plugins.function.IFunction;
import org.sagacity.sqltoy.utils.DataSourceUtils.DBType;

/**
 * @project sagacity-sqltoy
 * @description 日期时间分量提取函数的跨库适配(2026-10-11扩展:year/month/day外补
 *              hour/minute/second与dayofmonth,完整覆盖mysql惯用的分量提取族)。
 *              原生透传:mysql系(含OB)/db2/h2/ch全部分量;sqlserver的year/month/day;
 *              hana的year/month;dm的year/month/day。sqlserver无hour/minute/second
 *              函数转DATEPART;oracle与pg系(含og系)/kingbase转EXTRACT;sqlite转
 *              cast+strftime(%m/%d/%H零填充文本,cast对齐mysql数值语义)。
 *              正则安全边界:dateadd(month,…)、datediff(day,…)、make_interval(months=>…)
 *              等关键字形态的分量词后无"(",不会误命中;add_months(的"months("亦不命中。
 * @author zhongxuchen
 * @version v1.0,Date:2026-10-11
 */
public class YearMonthDay extends IFunction {
	private static Pattern regex = Pattern.compile("(?i)\\W(year|month|day|dayofmonth|quarter|hour|minute|second)\\(");

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
		String source = functionName.toLowerCase(Locale.ROOT);
		// dayofmonth(mysql/db2/h2原生)语义与day完全一致,归一为day处理
		String part = "dayofmonth".equals(source) ? "day" : source;
		boolean hms = "hour".equals(part) || "minute".equals(part) || "second".equals(part);
		// update 2026-10-12
		// mysql系(含OB)全部分量原生;db2/h2/ch全部分量原生(YEAR/MONTH/DAY/DAYOFMONTH/HOUR/MINUTE/SECOND均原生);dm的year/month/day原生(10-11真库实测dm.year)
		if (dialect == DBType.MYSQL || dialect == DBType.MYSQL57 || dialect == DBType.TIDB || dialect == DBType.DORIS
				|| dialect == DBType.STARROCKS || dialect == DBType.OCEANBASE || dialect == DBType.DB2
				|| dialect == DBType.H2 || dialect == DBType.CLICKHOUSE) {
			return super.IGNORE;
		}
		// dayofmonth在mysql/db2/h2原生,其余库换名为该库的day形态
		if ("day".equals(part) && "dayofmonth".equals(source)) {
			if (dialect == DBType.SQLSERVER || dialect == DBType.DM) {
				return wrapArgs("day", args);
			}
		}
		// sqlserver:year/month/day原生;hour/minute/second无函数形态转DATEPART
		boolean needPart = hms || "quarter".equals(part) || "dayofmonth".equals(source);
		if (dialect == DBType.SQLSERVER) {
			if (!needPart) {
				return super.IGNORE;
			}
			return "DATEPART(" + part + "," + args[0] + ")";
		}
		// dm:year/month/day/quarter原生(真库实测);hour/minute/second未实测走EXTRACT
		if (dialect == DBType.DM && !hms) {
			return super.IGNORE;
		}
		// hana:quarter实测返回2026-Q1串非数值,以月份算术承担(真库探针)
		if (dialect == DBType.HANA && "quarter".equals(part)) {
			return "(floor((month(" + args[0] + ")-1)/3)+1)";
		}
		// hana:year/month原生;day(10-11真库实测无DAY函数)与其余分量走EXTRACT
		if (dialect == DBType.HANA && ("year".equals(part) || "month".equals(part))) {
			return super.IGNORE;
		}
		if (dialect == DBType.SQLITE && "quarter".equals(part)) {
			String m1 = FunctionUtils.sqliteDateTextExpr(args[0]);
			return "(cast((cast(strftime('%m'," + m1 + ") as integer)-1)/3 as integer)+1)";
		}
		// sqlite:strftime(列/占位符经sqliteDateTextExpr归一,字面量与函数表达式透传);
		// %m/%d/%H为零填充文本,cast对齐mysql数值语义
		if (dialect == DBType.SQLITE) {
			String fmt = fmtOf(part);
			return "cast(strftime('" + fmt + "'," + FunctionUtils.sqliteDateTextExpr(args[0]) + ") as integer)";
		}
		// oracle系(oracle/oracle11/dm/kingbase)与hana:EXTRACT(分量 FROM x);裸字符串字面量
		// 包to_date(oracle真库实测ORA-30076:NLS/类型不认ISO裸串)
		// oracle/oracle11/dm/hana:字面量包to_date(hana不认::timestamp语法,真库实测);
		// kingbase为pg内核归入下方pg系渲染(EXTRACT(QUARTER)原生可用)
		// oracle/oracle11/dm/hana:字面量包to_date(hana不认::timestamp语法,真库实测);
		// kingbase为pg内核归入下方pg系渲染(EXTRACT(QUARTER)原生可用)
		if (dialect == DBType.ORACLE || dialect == DBType.ORACLE11 || dialect == DBType.DM || dialect == DBType.HANA) {
			// oracle系EXTRACT不支持QUARTER(真库实测ORA-00907),转to_char('Q');
			// hana的QUARTER已在上方算术分支处理,不会进入此处
			if ("quarter".equals(part)) {
				return "to_number(to_char(" + FunctionUtils.oracleDateLiteralExpr(args[0]) + ",'Q'))";
			}
			return "EXTRACT(" + part.toUpperCase(Locale.ROOT) + " FROM " + FunctionUtils.oracleDateLiteralExpr(args[0])
					+ ")";
		}
		// pg系(含og系/kingbase):EXTRACT;裸字符串字面量包::timestamp(og7/vastbase真库实测:
		// unknown字面量与EXTRACT组合被按interval解析而报错);vanilla pg的extract
		// 返回int,og系返回numeric,数值语义一致
		return "EXTRACT(" + part.toUpperCase(Locale.ROOT) + " FROM " + FunctionUtils.castLiteralTimestamp(args[0])
				+ ")";
	}

	private static String fmtOf(String part) {
		switch (part) {
		case "year":
			return "%Y";
		case "month":
			return "%m";
		case "hour":
			return "%H";
		case "minute":
			return "%M";
		case "second":
			return "%S";
		default:
			return "%d";
		}
	}
}