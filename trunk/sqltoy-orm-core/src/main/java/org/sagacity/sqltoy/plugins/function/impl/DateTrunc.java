package org.sagacity.sqltoy.plugins.function.impl;

import java.util.Locale;
import java.util.regex.Pattern;

import org.sagacity.sqltoy.plugins.function.FunctionUtils;
import org.sagacity.sqltoy.plugins.function.IFunction;
import org.sagacity.sqltoy.utils.DataSourceUtils.DBType;

/**
 * @project sagacity-sqltoy
 * @description 日期截断族的跨库适配(A方案2026-10-11):源形态覆盖oracle的trunc(x[,unit])
 *              (单位码DD/MM/Q/YYYY/IW)、pg的date_trunc('unit',x)与db2的trunc_timestamp,
 *              三种惯用写法统一进入转换。单位支持DAY/WEEK/MONTH/QUARTER/YEAR
 *              (周统一ISO周一锚定,注释声明);时分秒截断不支持响亮保留。
 *              安全边界:1参trunc(x)全库透传——oracle的数值截断trunc(123.45,2)单位词
 *              映射不上日期单位自然响亮,防误伤;数值2参同理。
 *              原生透传(真库探针2026-10-11):trunc系=oracle/oracle11/dm/db2/og系/
 *              kingbase/vastbase;date_trunc=pg/pg14/ch(单位前置)/h2/doris(双参数序实测
 *              均可)/db2;trunc_timestamp=db2。starrocks/tidb/mysql系无原生,mysql组合形态;
 *              sqlserver用DATEFROMPARTS族;hana用TO_DATE+TO_CHAR组合;sqlite用修饰符链
 *              (季截断的月份回退须printf形态,||拼接实测得null)。
 * @author zhongxuchen
 * @version v1.0,Date:2026-10-11
 */
public class DateTrunc extends IFunction {
	private static Pattern regex = Pattern.compile("(?i)\\W(trunc|date_trunc|trunc_timestamp)\\(");

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
		String funLow = functionName.toLowerCase(Locale.ROOT);
		// 1参trunc(x):全库透传(oracle的日截断原生;亦保护数值trunc用法)
		if (args == null || args.length < 2) {
			return super.IGNORE;
		}
		String base;
		String unitWord;
		if ("date_trunc".equals(funLow)) {
			// pg序:('unit', x)
			base = args[1].trim();
			unitWord = args[0].trim().replace("'", "");
		} else {
			// oracle/db2序:(x, unit)
			base = args[0].trim();
			unitWord = args[1].trim().replace("'", "");
		}
		String unit = normalizeUnit(unitWord);
		if (unit == null) {
			// 数值截断trunc(123.45,2)或罕见单位:响亮保留
			return super.IGNORE;
		}
		// ---- 原生透传族 ----
		// oracle兼容格式模型;无date_trunc函数由下方转换族承担)
		boolean truncNative = dialect == DBType.ORACLE || dialect == DBType.ORACLE11 || dialect == DBType.DM
				|| dialect == DBType.DB2 || dialect == DBType.OPENGAUSS || dialect == DBType.GAUSSDB
				|| dialect == DBType.MOGDB || dialect == DBType.OSCAR || dialect == DBType.STARDB
				|| dialect == DBType.VASTBASE || dialect == DBType.KINGBASE;
		// trunc(x,'code')原生:oracle/oracle11/dm/db2/og系/kingbase/vastbase(真库探针);
		// 例外:WEEK单位在og内核族(og5实测不认IW码,与og7同方言名无法按方言区分)统一
		// 走下方date_trunc('week')转换,该形态og5/og7/kb/vb真库均验证
		boolean ogCore = dialect == DBType.OPENGAUSS || dialect == DBType.GAUSSDB || dialect == DBType.MOGDB
				|| dialect == DBType.OSCAR || dialect == DBType.STARDB || dialect == DBType.VASTBASE
				|| dialect == DBType.KINGBASE;
		if ("trunc".equals(funLow) && truncNative && !("WEEK".equals(unit) && ogCore)) {
			return super.IGNORE;
		}
		if ("trunc_timestamp".equals(funLow) && dialect == DBType.DB2) {
			return super.IGNORE;
		}
		// h2的date_trunc('week')为周日锚定(真库实测2026-03-15),与ISO周一口径不符,
		// WEEK单位不走透传,转组合形态;其余单位h2原生透传
		if ("date_trunc".equals(funLow) && !"WEEK".equals(unit)
				&& (dialect == DBType.POSTGRESQL || dialect == DBType.POSTGRESQL14 || dialect == DBType.CLICKHOUSE
						|| dialect == DBType.HIGHGO || dialect == DBType.H2 || dialect == DBType.DORIS
						|| dialect == DBType.DB2)) {
			return super.IGNORE;
		}
		if (dialect == DBType.H2 && "WEEK".equals(unit)) {
			// h2周一起点:DAYOFWEEK(1=周日..7=周六)偏移,日截断后回退
			return "DATEADD('DAY',-((DAYOFWEEK(" + base + ")+5)%7), date_trunc('day'," + base + "))";
		}
		// ---- 转换族 ----
		if (truncNative) {
			// og内核族的WEEK:og5不认IW码,统一pg内核形态date_trunc('week')(og5/og7/kb/vb验证)
			if ("WEEK".equals(unit) && ogCore) {
				return "date_trunc('week'," + FunctionUtils.castLiteralTimestamp(base) + ")";
			}
			// date_trunc/trunc_timestamp源转oracle码形态TRUNC(x,'code')
			String baseExpr = FunctionUtils.oracleDateLiteralExpr(base);
			return "TRUNC(" + baseExpr + ",'" + oracleCode(unit) + "')";
		}
		if (dialect == DBType.POSTGRESQL || dialect == DBType.POSTGRESQL14 || dialect == DBType.CLICKHOUSE
				|| dialect == DBType.HIGHGO || dialect == DBType.H2 || dialect == DBType.DORIS) {
			// trunc源转date_trunc('unit',x)(字面量经::timestamp定型)
			return "date_trunc('" + unit.toLowerCase(Locale.ROOT) + "'," + FunctionUtils.castLiteralTimestamp(base)
					+ ")";
		}
		// mysql语法族(含OB/tidb/starrocks):组合形态
		if (dialect == DBType.MYSQL || dialect == DBType.MYSQL57 || dialect == DBType.OCEANBASE
				|| dialect == DBType.TIDB || dialect == DBType.STARROCKS) {
			switch (unit) {
			case "DAY":
				return "DATE(" + base + ")";
			case "MONTH":
				return "DATE_FORMAT(" + base + ",'%Y-%m-01')";
			case "QUARTER":
				return "DATE_FORMAT(DATE_SUB(" + base + ", INTERVAL (MONTH(" + base + ")-1)%3 MONTH),'%Y-%m-01')";
			case "YEAR":
				return "DATE_FORMAT(" + base + ",'%Y-01-01')";
			default:
				return "DATE_SUB(DATE(" + base + "), INTERVAL WEEKDAY(" + base + ") DAY)";
			}
		}
		if (dialect == DBType.SQLSERVER) {
			switch (unit) {
			case "DAY":
				return "CONVERT(date, " + base + ")";
			case "MONTH":
				return "DATEFROMPARTS(YEAR(" + base + "),MONTH(" + base + "),1)";
			case "QUARTER":
				return "DATEFROMPARTS(YEAR(" + base + "),(MONTH(" + base + ")-1)/3*3+1,1)";
			case "YEAR":
				return "DATEFROMPARTS(YEAR(" + base + "),1,1)";
			default:
				// ISO周一锚定:1900-01-01为周一,按天数差%7回退;不要用DATEDIFF(week),
				// 它受SET DATEFIRST影响(默认周日),会静默错一天
				return "DATEADD(day,-((DATEDIFF(day,0," + base + "))%7),CONVERT(date," + base + "))";
			}
		}
		if (dialect == DBType.HANA) {
			switch (unit) {
			case "DAY":
				return "TO_DATE(TO_CHAR(" + base + ",'YYYY-MM-DD'))";
			case "MONTH":
				return "TO_DATE(TO_CHAR(" + base + ",'YYYY-MM'),'YYYY-MM')";
			case "QUARTER":
				// hana无%取模运算符(真库实测语法错),用MOD函数
				return "TO_DATE(TO_CHAR(ADD_MONTHS(" + base + ",-MOD(MONTH(" + base + ")-1,3)),'YYYY-MM'),'YYYY-MM')";
			case "YEAR":
				return "TO_DATE(TO_CHAR(" + base + ",'YYYY'),'YYYY')";
			default:
				return "ADD_DAYS(TO_DATE(TO_CHAR(" + base + ",'YYYY-MM-DD')),-WEEKDAY(" + base + "))";
			}
		}
		if (dialect == DBType.SQLITE) {
			String m = FunctionUtils.sqliteDateTextExpr(base);
			switch (unit) {
			case "DAY":
				return "date(" + m + ")";
			case "MONTH":
				return "date(" + m + ",'start of month')";
			case "QUARTER":
				// 月份回退须printf形态(||拼接实测null)
				return "date(" + m + ",'start of month',printf('-%d months'," + "(cast(strftime('%m'," + m
						+ ") as integer)-1)%3))";
			case "YEAR":
				return "date(" + m + ",'start of year')";
			default:
				return "date(" + m + ",'-'||((cast(strftime('%w'," + m + ") as integer)+6)%7)||' days')";
			}
		}
		return super.IGNORE;
	}

	// 单位归一:oracle码与pg名双形态
	private static String normalizeUnit(String w) {
		String u = w.trim().toUpperCase(Locale.ROOT);
		switch (u) {
		case "DD":
		case "DAY":
			return "DAY";
		case "MM":
		case "MONTH":
			return "MONTH";
		case "Q":
		case "QUARTER":
			return "QUARTER";
		case "YY":
		case "YYYY":
		case "Y":
		case "YEAR":
		case "SYYYY":
		case "IYYY":
			return "YEAR";
		case "IW":
		case "WEEK":
			return "WEEK";
		default:
			return null;
		}
	}

	private static String oracleCode(String unit) {
		switch (unit) {
		case "MONTH":
			return "MM";
		case "QUARTER":
			return "Q";
		case "YEAR":
			return "YYYY";
		case "WEEK":
			return "IW";
		default:
			return "DD";
		}
	}
}
