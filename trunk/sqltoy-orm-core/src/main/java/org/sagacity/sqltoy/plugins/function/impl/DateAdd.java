package org.sagacity.sqltoy.plugins.function.impl;

import java.util.Locale;
import java.util.regex.Pattern;

import org.sagacity.sqltoy.plugins.function.FunctionUtils;
import org.sagacity.sqltoy.plugins.function.IFunction;
import org.sagacity.sqltoy.utils.DataSourceUtils.DBType;

/**
 * @project sagacity-sqltoy
 * @description 日期区间加减函数族的跨库适配(2026-10-11补齐AddMonths审计时的边界项):
 *              源拼写覆盖mysql的date_add(x, INTERVAL n unit)/date_sub/adddate(x,n天)/
 *              subdate与sqlserver的dateadd(unit,n,x)——参数内嵌单位关键字,按尾token解析。
 *              单位支持DAY/MONTH/YEAR/HOUR/MINUTE/SECOND/WEEK(含复数/大小写归一),
 *              QUARTER/MICROSECOND等罕见单位响亮保留。 边界:pg/oracle的表达式形态 x + INTERVAL 'n
 *              month' 非函数调用,正则框架 无法捕获(不改写,目标库原生可用);sqlite的月份修饰符不钳制月末
 *              (date('2026-01-31','+1 months')=2026-03-03,与oracle系02-28偏离),
 *              全单位响亮放弃;月末钳制语义:mysql系/oracle系/pg系/db2/hana/ch均钳制。
 * @author zhongxuchen
 * @version v1.0,Date:2026-10-11
 */
public class DateAdd extends IFunction {
	private static Pattern regex = Pattern.compile("(?i)\\W(date_add|date_sub|dateadd|adddate|subdate)\\(");

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
		String funLow = functionName.toLowerCase(Locale.ROOT);
		boolean sub = "date_sub".equals(funLow) || "subdate".equals(funLow);
		String base;
		String n;
		String unit;
		if ("dateadd".equals(funLow)) {
			// sqlserver形态:dateadd(unit, n, x);单位词可带引号
			if (args.length < 3) {
				return super.IGNORE;
			}
			// 引号单位形态('MONTH')为本框架各适配器的渲染产物(如AddMonths的h2形态),
			// 二次加工会破坏既有输出(链式改写干扰),视为已归一形态透传
			if (args[0].trim().startsWith("'")) {
				return super.IGNORE;
			}
			unit = args[0].trim().replace("'", "").toUpperCase(Locale.ROOT);
			n = args[1].trim();
			base = args[2].trim();
		} else if ("adddate".equals(funLow) || "subdate".equals(funLow)) {
			// mysql简写:adddate(x, n)/subdate(x, n),单位为天
			base = args[0].trim();
			n = args[1].trim();
			unit = "DAY";
		} else {
			// mysql形态:date_add(x, INTERVAL n unit);自尾部取单位词(容忍n为含空格表达式)
			base = args[0].trim();
			String iv = args[1].trim();
			if (!iv.matches("(?i)^interval\\s+.+")) {
				return super.IGNORE;
			}
			iv = iv.substring(9).trim();
			int sp = iv.lastIndexOf(' ');
			if (sp < 0) {
				return super.IGNORE;
			}
			n = iv.substring(0, sp).trim();
			unit = iv.substring(sp + 1).trim().toUpperCase(Locale.ROOT);
		}
		// 单位归一(复数→单数)
		if (unit.endsWith("S")) {
			unit = unit.substring(0, unit.length() - 1);
		}
		if (!isSupportedUnit(unit)) {
			// QUARTER/MICROSECOND等罕见单位,响亮保留交由目标库报错
			return super.IGNORE;
		}
		// n统一加括号防表达式优先级问题;sub以负号前缀承担减法
		String nn = "(" + n + ")";
		if (sub) {
			nn = "-" + nn;
		}
		// mysql系(含OB/tidb/doris/starrocks):date_add原生;本库原生拼写date_add/adddate/
		// subdate透传,外来拼写(dateadd)与date_sub统一归一为DATE_ADD形态
		if (dialect == DBType.MYSQL || dialect == DBType.MYSQL57 || dialect == DBType.TIDB || dialect == DBType.DORIS
				|| dialect == DBType.STARROCKS || dialect == DBType.OCEANBASE) {
			if (!sub && ("date_add".equals(funLow) || "adddate".equals(funLow))) {
				return super.IGNORE;
			}
			return "DATE_ADD(" + base + ", INTERVAL " + nn + " " + unit + ")";
		}
		// sqlserver:dateadd原生;外来拼写归一为DATEADD(unit,n,x)
		if (dialect == DBType.SQLSERVER) {
			if ("dateadd".equals(funLow)) {
				return super.IGNORE;
			}
			return "DATEADD(" + unit.toLowerCase(Locale.ROOT) + ", " + nn + ", " + base + ")";
		}
		// h2:DATEADD('UNIT',n,x)
		if (dialect == DBType.H2) {
			return "DATEADD('" + unit + "', " + nn + ", " + base + ")";
		}
		// clickhouse:addDays/addMonths/addYears/addHours/addMinutes/addSeconds
		if (dialect == DBType.CLICKHOUSE) {
			return chForm(unit, base, nn);
		}
		// db2:x +/- n UNITs(复数单位)
		if (dialect == DBType.DB2) {
			if ("WEEK".equals(unit)) {
				// update 2026-10-12 DB2的+n DAYS要求n为整数字面量,不接受算术表达式
				// (1*7报SQLCODE=-182)也不接受带括号的(1)(报-206),原始n预乘7;
				// 非纯数字(表达式/参数)响亮保留
				try {
					int n7 = Integer.parseInt(n.trim()) * 7;
					return "(" + base + (sub ? " - " : " + ") + n7 + " DAYS)";
				} catch (NumberFormatException e) {
					return super.IGNORE;
				}
			}
			return "(" + base + (sub ? " - " : " + ") + stripNeg(nn) + " " + unit + "S)";
		}
		// hana:ADD_DAYS/ADD_MONTHS/ADD_YEARS/ADD_SECONDS族(时分化秒)
		if (dialect == DBType.HANA) {
			if ("DAY".equals(unit)) {
				return "ADD_DAYS(" + base + "," + nn + ")";
			}
			// update 2026-10-12 hana无ADD_WEEKS,WEEK按7天转ADD_DAYS
			if ("WEEK".equals(unit)) {
				return "ADD_DAYS(" + base + "," + nn + "*7)";
			}
			if ("MONTH".equals(unit)) {
				return "ADD_MONTHS(" + base + "," + nn + ")";
			}
			if ("YEAR".equals(unit)) {
				return "ADD_YEARS(" + base + "," + nn + ")";
			}
			if ("HOUR".equals(unit)) {
				return "ADD_SECONDS(" + base + "," + nn + "*3600)";
			}
			if ("MINUTE".equals(unit)) {
				return "ADD_SECONDS(" + base + "," + nn + "*60)";
			}
			return "ADD_SECONDS(" + base + "," + nn + ")";
		}
		// oracle系(oracle/oracle11/dm)与kingbase:月走add_months,其余算术;
		// 裸字符串字面量按NLS解析报错(真库实测),base经oracleDateLiteralExpr包裹
		if (dialect == DBType.ORACLE || dialect == DBType.ORACLE11 || dialect == DBType.DM
				|| dialect == DBType.KINGBASE) {
			String ob = FunctionUtils.oracleDateLiteralExpr(base);
			if ("MONTH".equals(unit)) {
				return "add_months(" + ob + "," + nn + ")";
			}
			if ("YEAR".equals(unit)) {
				return "add_months(" + ob + "," + nn + "*12)";
			}
			String factor = dayFactor(unit);
			String sign = sub ? " - " : " + ";
			if (factor == null) {
				// 时分秒:天数折算(x +/- n/24|1440|86400,oracle的/为小数除)
				String div = "HOUR".equals(unit) ? "24" : ("MINUTE".equals(unit) ? "1440" : "86400");
				return "(" + ob + sign + stripNeg(nn) + "/" + div + ")";
			}
			return "(" + ob + sign + (factor.equals("1") ? stripNeg(nn) : stripNeg(nn) + "*" + factor) + ")";
		}
		// openGauss系(og/gaussdb/mogdb/vastbase/oscar/stardb):月/年走原生add_months
		// (og真库实测),其余单位走pg系区间乘法(月末钳制语义一致);字面量包::timestamp
		// (og7/vastbase真库实测:unknown字面量+interval按interval解析报错)
		if (dialect == DBType.OPENGAUSS || dialect == DBType.GAUSSDB || dialect == DBType.MOGDB
				|| dialect == DBType.VASTBASE || dialect == DBType.OSCAR || dialect == DBType.STARDB) {
			if ("MONTH".equals(unit)) {
				return "add_months(" + base + "," + nn + ")";
			}
			if ("YEAR".equals(unit)) {
				return "add_months(" + base + "," + nn + "*12)";
			}
			return "(" + FunctionUtils.castLiteralTimestamp(base) + (sub ? " - " : " + ") + "(" + n + ") * interval '1 "
					+ unit.toLowerCase(Locale.ROOT) + "')";
		}
		// vanilla pg:区间乘法(n * interval '1 unit',全单位钳制语义一致);字面量包::timestamp
		if (dialect == DBType.POSTGRESQL || dialect == DBType.POSTGRESQL14 || dialect == DBType.HIGHGO) {
			return "(" + FunctionUtils.castLiteralTimestamp(base) + (sub ? " - " : " + ") + "(" + n + ") * interval '1 "
					+ unit.toLowerCase(Locale.ROOT) + "')";
		}
		// sqlite:月份修饰符不钳制月末(实测),全单位响亮保留
		return super.IGNORE;
	}

	private static boolean isSupportedUnit(String unit) {
		return "DAY".equals(unit) || "MONTH".equals(unit) || "YEAR".equals(unit) || "HOUR".equals(unit)
				|| "MINUTE".equals(unit) || "SECOND".equals(unit) || "WEEK".equals(unit);
	}

	// 天类单位的倍数因子(非天类返回null)
	private static String dayFactor(String unit) {
		switch (unit) {
		case "DAY":
			return "1";
		case "WEEK":
			return "7";
		default:
			return null;
		}
	}

	private static String chForm(String unit, String base, String nn) {
		switch (unit) {
		case "DAY":
			return "addDays(" + base + "," + nn + ")";
		case "MONTH":
			return "addMonths(" + base + "," + nn + ")";
		case "YEAR":
			return "addYears(" + base + "," + nn + ")";
		case "HOUR":
			return "addHours(" + base + "," + nn + ")";
		case "MINUTE":
			return "addMinutes(" + base + "," + nn + ")";
		case "WEEK":
			return "addDays(" + base + "," + nn + "*7)";
		default:
			return "addSeconds(" + base + "," + nn + ")";
		}
	}

	// db2形态无括号负数前缀的整洁化:-(n)在"+ -(n) DAYS"不可用,改为" - (n) UNITs"由调用方拼
	private static String stripNeg(String nn) {
		return nn.startsWith("-") ? nn.substring(1) : nn;
	}
}
