package org.sagacity.sqltoy.plugins.function.impl;

import java.util.Locale;
import java.util.regex.Pattern;

import org.sagacity.sqltoy.plugins.function.FunctionUtils;
import org.sagacity.sqltoy.plugins.function.IFunction;
import org.sagacity.sqltoy.utils.DataSourceUtils.DBType;

/**
 * @project sagacity-sqltoy
 * @description 周族函数的跨库适配(2026-11-10 补齐):week(d)/weekday(d)/dayofweek(d)三源。
 *              week按MySQL默认mode=0精确语义(周日始,0-53,week1=含本年首个周日的周)以锚点法组合
 *              (各库jan1的周内位置+首周日锚);带mode参数的week(d,m)仅MySQL原生,其余方言响亮
 *              (8种mode语义各异,跨库组合不成比例)。weekday=周一0基;dayofweek=周日1基(ODBC)。
 *              取模基准:2000-01-02为周日/2000-01-03为周一,规避sqlserver的@@datefirst与
 *              oracle的to_char('D')NLS_TERRITORY依赖。CH 已适配(weekday/dayofweek 以
 *              toDayOfWeek 改写,week 原生); HANA 第一版不适配(响亮)。
 * @author zhongxuchen
 * @version v1.0,Date:2026-11-10
 */
public class Week extends IFunction {

	// week( 不会误匹配weekofyear(/yearweek((week后须紧跟括号);交替顺序保证weekday优先于week试探
	private static Pattern regex = Pattern.compile("(?i)\\W(week|weekday|dayofweek)\\(");

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
		String d = args[0].trim();
		// update 2026-11-11 PG系字面量入参显式定形:week('2026-11-11') 会生成 extract(doy from
		// ('...')),
		// PG对unknown类型歧义报 "function pg_catalog.extract(unknown, unknown) is not
		// unique"(真库实测);
		// 列/参数形态不受影响(castLiteralTimestamp仅对单引号字面量生效)
		if (isPgFamily(dialect)) {
			d = FunctionUtils.castLiteralTimestamp(d);
		}
		// update 2026-11-10 ClickHouse 从 mysqlFamily 移出:CH 无 weekday 函数(真库
		// UNKNOWN_FUNCTION),
		// 且 dayOfWeek 为 ISO 周一=1..周日=7,与 MySQL 的 ODBC 周日=1..周六=7 语义相反,不能透传。
		// 以 toDayOfWeek 组合改写;week(d) 单参形态 CH 原生 week 与 MySQL mode=0 一致,保持透传。
		if (dialect == DBType.CLICKHOUSE) {
			if ("weekday".equals(funLow)) {
				// MySQL weekday 周一=0..周日=6 = toDayOfWeek(周一=1..周日=7)-1
				return "toDayOfWeek(" + d + ")-1";
			}
			if ("dayofweek".equals(funLow)) {
				// MySQL dayofweek 周日=1..周六=7 = (toDayOfWeek % 7)+1
				return "(toDayOfWeek(" + d + ") % 7) + 1";
			}
			// week(d) 单参原生正确;week(d,mode) 两参模式语义不同,均透传
			return super.IGNORE;
		}
		boolean mysqlFamily = dialect == DBType.MYSQL || dialect == DBType.MYSQL57 || dialect == DBType.TIDB
				|| dialect == DBType.DORIS || dialect == DBType.STARROCKS || dialect == DBType.OCEANBASE;
		if (mysqlFamily) {
			return super.IGNORE;
		}
		if ("weekday".equals(funLow)) {
			return wrapWeekday(dialect, d);
		}
		if ("dayofweek".equals(funLow)) {
			return wrapDayofweek(dialect, d);
		}
		// week:带mode参数(8种语义各异)仅MySQL原生,其余响亮
		if (args.length >= 2) {
			return super.IGNORE;
		}
		return wrapWeek(dialect, d);
	}

	/** PG内核系(含openGauss/gaussdb/kingbase/highgo/vastbase等派生库) */
	private static boolean isPgFamily(int dialect) {
		return dialect == DBType.POSTGRESQL || dialect == DBType.POSTGRESQL14 || dialect == DBType.OPENGAUSS
				|| dialect == DBType.GAUSSDB || dialect == DBType.MOGDB || dialect == DBType.VASTBASE
				|| dialect == DBType.STARDB || dialect == DBType.OSCAR || dialect == DBType.KINGBASE
				|| dialect == DBType.HIGHGO;
	}

	/** weekday(d):周一=0..周日=6 */
	private String wrapWeekday(int dialect, String d) {
		// 2000-01-03为周一:日期差取模
		// update 2026-11-10 oracle/dm/db2/sqlserver的取模对负被除数返回负数(2000-01-03之前的日期
		// 差值为负,weekday会得到负值),统一补mod(mod(x,7)+7,7)/((x%7+7)%7)归一到0-6
		if (dialect == DBType.ORACLE || dialect == DBType.ORACLE11 || dialect == DBType.DM) {
			return "mod(mod(trunc(" + d + ") - date '2000-01-03', 7) + 7, 7)";
		}
		if (dialect == DBType.DB2) {
			return "mod(mod(days(" + d + ") - days('2000-01-03'), 7) + 7, 7)";
		}
		if (dialect == DBType.POSTGRESQL || dialect == DBType.POSTGRESQL14 || dialect == DBType.OPENGAUSS
				|| dialect == DBType.GAUSSDB || dialect == DBType.MOGDB || dialect == DBType.VASTBASE
				|| dialect == DBType.STARDB || dialect == DBType.OSCAR || dialect == DBType.KINGBASE
				|| dialect == DBType.HIGHGO) {
			// pg的dow周日=0:周日→6/周一→0;extract返回numeric(double),::int强转规避%对double不存在
			// (kingbase/highgo真库报operator does not exist: double precision % integer)
			return "(extract(dow from (" + d + "))::int + 6) % 7";
		}
		if (dialect == DBType.SQLSERVER) {
			// update 2026-11-10 负被除数的%返回负数(2000-01-03之前),同oracle/dm/db2归一到0-6
			return "((datediff(day, '2000-01-03', " + d + ") % 7 + 7) % 7)";
		}
		if (dialect == DBType.SQLITE) {
			// update 2026-11-10 sqlite列须先归一:ORM的setTimestamp存毫秒INTEGER,直接strftime(ts)
			// 把整数当儒略日返回NULL(真库实证typeof=integer 1794285296000→'%w'=NULL);沿用
			// DateFormat/DateDiff/Extract同一口径(sqliteDateTextExpr:裸列/占位符→本地墙钟文本,
			// 字面量/表达式透传),周内位置按会话时区(Java)解释,与MySQL weekday(墙钟)语义一致
			String datetime = FunctionUtils.sqliteDateTextExpr(d);
			return "(cast(strftime('%w', " + datetime + ") as integer) + 6) % 7";
		}
		if (dialect == DBType.H2) {
			// h2的dayofweek周日=1(ODBC):周日→6/周一→0
			return "(dayofweek(" + d + ") + 5) % 7";
		}
		return super.IGNORE;
	}

	/** dayofweek(d):周日=1..周六=7(ODBC) */
	private String wrapDayofweek(int dialect, String d) {
		// db2/h2原生DAYOFWEEK即ODBC语义
		if (dialect == DBType.DB2 || dialect == DBType.H2) {
			return super.IGNORE;
		}
		if (dialect == DBType.ORACLE || dialect == DBType.ORACLE11 || dialect == DBType.DM) {
			// update 2026-11-10 负被除数归一(同weekday):2000-01-02之前日期差为负
			return "mod(mod(trunc(" + d + ") - date '2000-01-02', 7) + 7, 7) + 1";
		}
		if (dialect == DBType.POSTGRESQL || dialect == DBType.POSTGRESQL14 || dialect == DBType.OPENGAUSS
				|| dialect == DBType.GAUSSDB || dialect == DBType.MOGDB || dialect == DBType.VASTBASE
				|| dialect == DBType.STARDB || dialect == DBType.OSCAR || dialect == DBType.KINGBASE
				|| dialect == DBType.HIGHGO) {
			return "(extract(dow from (" + d + "))::int + 1)";
		}
		if (dialect == DBType.SQLSERVER) {
			// update 2026-11-10 负被除数归一(同weekday),再+1成ODBC 1-7
			return "(((datediff(day, '2000-01-02', " + d + ") % 7) + 7) % 7 + 1)";
		}
		if (dialect == DBType.SQLITE) {
			// 同weekday:裸列/占位符先归一,避免毫秒整数被当儒略日
			String datetime = FunctionUtils.sqliteDateTextExpr(d);
			return "(cast(strftime('%w', " + datetime + ") as integer) + 1)";
		}
		return super.IGNORE;
	}

	/**
	 * week(d)按MySQL mode=0:周日始,0-53,week1=含本年首个周日的周(锚点法)。
	 * first_sunday=jan1+((7-dow0(jan1))%7),d<first_sunday→0,否则floor((d-first_sunday)/7)+1
	 */
	private String wrapWeek(int dialect, String d) {
		// Oracle系:jan1=trunc(d,'yyyy');dow0(x)=mod(x-date'2000-01-02',7)(0=周日)
		if (dialect == DBType.ORACLE || dialect == DBType.ORACLE11 || dialect == DBType.DM) {
			String jan1 = "trunc(" + d + ",'yyyy')";
			String firstSunday = jan1 + " + mod(7 - mod(" + jan1 + " - date '2000-01-02', 7), 7)";
			return "(case when trunc(" + d + ") < " + firstSunday + " then 0 else floor((trunc(" + d + ") - ("
					+ firstSunday + "))/7) + 1 end)";
		}
		// PG系:doy=extract(doy);jan1_dow0=extract(dow from date_trunc('year',d))(0=周日);
		// fs_doy=(7-jan1_dow0)%7+1;doy<fs_doy→0,否则(doy-fs_doy)/7+1(整数除法截断)
		if (dialect == DBType.POSTGRESQL || dialect == DBType.POSTGRESQL14 || dialect == DBType.OPENGAUSS
				|| dialect == DBType.GAUSSDB || dialect == DBType.MOGDB || dialect == DBType.VASTBASE
				|| dialect == DBType.STARDB || dialect == DBType.OSCAR || dialect == DBType.KINGBASE
				|| dialect == DBType.HIGHGO) {
			String doy = "(extract(doy from (" + d + "))::int)";
			String fsDoy = "((7 - extract(dow from date_trunc('year', (" + d + ")))::int) % 7 + 1)";
			// openGauss内核系(kingbase/og/vastbase)的int/int返回浮点(与主库PG整数除法不同),
			// floor取整+case外层::int归一(真库实测45.2857→45.0,::int后呈现45)
			return "(case when " + doy + " < " + fsDoy + " then 0 else floor((" + doy + " - " + fsDoy
					+ ")/7) + 1 end)::int";
		}
		if (dialect == DBType.SQLSERVER) {
			// jan1_dow0=datediff(day,'2000-01-02',jan1)%7;jan1=datefromparts(year,1,1);
			// sqlserver的int/int为整数除法(截断),无需floor
			String jan1 = "datefromparts(year(" + d + "), 1, 1)";
			String fsDoy = "((7 - datediff(day, '2000-01-02', " + jan1 + ") % 7) % 7 + 1)";
			String doy = "datepart(dayofyear, " + d + ")";
			return "(case when " + doy + " < " + fsDoy + " then 0 else (" + doy + " - " + fsDoy + ")/7 + 1 end)";
		}
		if (dialect == DBType.DB2) {
			// jan1=d-(dayofyear-1) days;jan1_dow0=dayofweek(jan1)-1(ODBC 1=周日);
			// fs=jan1+((7-jan1_dow0)%7) days;d<fs→0,否则(days(d)-days(fs))/7+1
			String jan1 = "((" + d + ") - (dayofyear(" + d + ") - 1) days)";
			String firstSunday = "(" + jan1 + " + ((7 - (dayofweek(" + jan1 + ") - 1)) % 7) days)";
			return "(case when (" + d + ") < " + firstSunday + " then 0 else (days(" + d + ") - days(" + firstSunday
					+ "))/7 + 1 end)";
		}
		if (dialect == DBType.SQLITE) {
			// jan1=date(d,'start of year');jan1_dow0=strftime('%w')(0=周日);
			// fs_doy=(7-jan1_dow0)%7+1;doy=strftime('%j');sqlite的int/int为整数除法
			// update 2026-11-10 裸列/占位符先归一(毫秒整数→本地日期文本),字面量/表达式透传
			String datetime = FunctionUtils.sqliteDateTextExpr(d);
			String doy = "cast(strftime('%j', " + datetime + ") as integer)";
			String fsDoy = "((7 - cast(strftime('%w', date(" + datetime + ", 'start of year')) as integer)) % 7 + 1)";
			return "(case when " + doy + " < " + fsDoy + " then 0 else (" + doy + " - " + fsDoy + ")/7 + 1 end)";
		}
		if (dialect == DBType.H2) {
			// jan1=dateadd(day,1-dayofyear(d),d);jan1_dow0=dayofweek(jan1)-1;
			// fs_doy=(7-jan1_dow0)%7+1;h2的int/int为整数除法
			String jan1 = "dateadd(day, 1 - dayofyear(" + d + "), " + d + ")";
			String fsDoy = "((7 - (dayofweek(" + jan1 + ") - 1)) % 7 + 1)";
			String doy = "dayofyear(" + d + ")";
			return "(case when " + doy + " < " + fsDoy + " then 0 else (" + doy + " - " + fsDoy + ")/7 + 1 end)";
		}
		return super.IGNORE;
	}
}
