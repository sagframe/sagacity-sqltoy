package org.sagacity.sqltoy.plugins.function.impl;

import java.util.regex.Pattern;

import org.sagacity.sqltoy.plugins.function.FunctionUtils;
import org.sagacity.sqltoy.plugins.function.IFunction;
import org.sagacity.sqltoy.utils.DataSourceUtils.DBType;

/**
 * @project sagacity-sqltoy
 * @description unix_timestamp([date])的跨库适配(2026-11-10 补齐,update 2026-11-11
 *              时区一致性):MySQL系/DM原生
 *              (dm8自带且会话时区感知正确);PG系extract(epoch)(按会话时区);sqlserver
 *              datediff(second)-会话时区偏移; DB2 days+midnight_seconds-CURRENT
 *              TIMEZONE偏移(hhmmss换算秒);H2显式转timestamptz
 *              (按会话时区及入参日期应用DST);Oracle用sys_extract_utc(
 *              from_tz(ts,sessiontimezone))按入参日期应用DST规则;sqlite按类型归一(INTEGER毫秒绝对时刻/TEXT本地墙钟);
 *              HANA seconds_between-当前偏移(固定偏移口径,真库实测)。无参形态取各库当前时间。
 *              时区语义:按各库会话时区(MySQL unix_timestamp同为会话时区,口径一致)。
 * @author zhongxuchen
 * @version v1.0,Date:2026-11-10
 */
public class UnixTimestamp extends IFunction {

	private static Pattern regex = Pattern.compile("(?i)\\Wunix_timestamp\\(");

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
		// 无参:当前时间戳;有参:取首参(ts表达式)
		boolean hasTs = args != null && args.length > 0 && !args[0].trim().isEmpty();
		// MySQL系/OB(MySQL模式)原生
		// update 2026-11-11 DM归原生透传:dm8自带unix_timestamp且语义与MySQL一致(真库实测
		// unix_timestamp('2026-11-11 10:00:00')=1794362400,即+08墙钟的绝对epoch;无参取当前时刻),
		// 此前"dm墙钟差偏8小时"的实测是组合公式的问题,原生函数本身时区感知正确
		if (dialect == DBType.MYSQL || dialect == DBType.MYSQL57 || dialect == DBType.TIDB || dialect == DBType.DORIS
				|| dialect == DBType.STARROCKS || dialect == DBType.OCEANBASE || dialect == DBType.DM) {
			return super.IGNORE;
		}
		// update 2026-11-10
		// CH修正:无unix_timestamp函数(真库报UNKNOWN_FUNCTION,提示toUnixTimestamp),
		// 原误归mysqlFamily透传;转CH原生toUnixTimestamp
		if (dialect == DBType.CLICKHOUSE) {
			return hasTs ? "toUnixTimestamp(" + args[0].trim() + ")" : "toUnixTimestamp(now())";
		}
		// extract(epoch from timestamp)按无时区墙钟差计算,并不随会话时区变化;
		// 显式::timestamptz按会话时区解释无时区入参,字面量先::timestamp定形以消除unknown重载歧义
		if (dialect == DBType.POSTGRESQL || dialect == DBType.POSTGRESQL14 || dialect == DBType.OPENGAUSS
				|| dialect == DBType.GAUSSDB || dialect == DBType.MOGDB || dialect == DBType.VASTBASE
				|| dialect == DBType.STARDB || dialect == DBType.OSCAR || dialect == DBType.KINGBASE
				|| dialect == DBType.HIGHGO) {
			return hasTs
					? "(extract(epoch from (" + FunctionUtils.castLiteralTimestamp(args[0].trim()) + ")::timestamptz))"
					: "(extract(epoch from now()))";
		}
		// update 2026-11-11 Oracle补映射(FROM_TZ+会话命名时区,DST感知);DM归原生透传(见上方原生分支注释)
		if (dialect == DBType.SQLSERVER) {
			// 固定偏移时区下减当前偏移对齐MySQL会话墙钟语义;SQLServer无会话时区名,
			// 命名时区的历史DST需调用方显式处理,不能由当前偏移推导。
			// datediff(second)返回int:1970基准下2038年内不溢出
			String tzOffset = "(datepart(tzoffset, sysdatetimeoffset()) * 60)";
			return hasTs ? "(datediff(second, '1970-01-01', " + args[0].trim() + ") - " + tzOffset + ")"
					: "(datediff(second, '1970-01-01', getdate()) - " + tzOffset + ")";
		}
		if (dialect == DBType.DB2) {
			// days+midnight_seconds为墙钟差;CURRENT TIMEZONE为DECIMAL(6,0)的hhmmss持续时间,
			// 按符号统一换算为秒后减偏移;DB2无会话时区名,历史DST需调用方显式处理
			String tzOffset = ("(sign(current timezone) * (integer(abs(current timezone)/10000) * 3600"
					+ " + mod(integer(abs(current timezone)/100),100) * 60"
					+ " + mod(integer(abs(current timezone)),100)))");
			// update 2026-11-11 cast bigint:偏移表达式为DECIMAL,不收敛会把返回值类型从INTEGER变成DOUBLE
			return hasTs
					? "cast(((days(cast(" + args[0].trim()
							+ " as timestamp)) - days('1970-01-01')) * 86400 + midnight_seconds(cast(" + args[0].trim()
							+ " as timestamp)) - " + tzOffset + ") as bigint)"
					: "cast(((days(current timestamp) - days('1970-01-01')) * 86400 + midnight_seconds(current timestamp)"
							+ " - " + tzOffset + ") as bigint)";
		}
		if (dialect == DBType.H2) {
			// update 2026-11-11 无时区时间转为timestamptz时按会话命名时区及入参日期应用偏移;
			// 带时区入参保持原绝对时刻,不能再减current_timestamp的当前偏移(历史DST会错一小时)
			return hasTs ? "(extract(epoch from cast((" + args[0].trim() + ") as timestamp with time zone)))"
					: "(extract(epoch from current_timestamp))";
		}
		if (dialect == DBType.SQLITE) {
			// ORM的setTimestamp存毫秒INTEGER,直接strftime会把整数当儒略日返回NULL;
			// 数值按绝对时刻归一,文本统一按本地墙钟转UTC后再取绝对epoch秒
			// strftime('%s')恒返回TEXT,SQLite类型序INTEGER/REAL<TEXT会令 unix_timestamp(x)>N 恒真、
			// <N 恒假(ORDER BY亦按文本排序),必须cast为integer,与Extract/Week/YearMonthDay等sqlite适配一致
			if (!hasTs) {
				return "cast(strftime('%s', 'now') as integer)";
			}
			return "cast(strftime('%s', " + sqliteEpochArg(args[0]) + ") as integer)";
		}
		// Oracle通过会话命名时区完成epoch映射;字面量入参仍IGNORE(受NLS_DATE_FORMAT影响),
		// 不做格式猜测,响亮交由用户改为列/参数或to_date
		if (dialect == DBType.ORACLE || dialect == DBType.ORACLE11) {
			if (hasTs && Truncate.isQuotedLiteral(args[0].trim())) {
				return super.IGNORE;
			}
			if (!hasTs) {
				return "cast(round((cast(sys_extract_utc(current_timestamp) as date) - date '1970-01-01')"
						+ " * 86400) as number(38))";
			}
			// FROM_TZ按入参日期应用会话命名时区及DST规则,SYS_EXTRACT_UTC再取绝对时刻;
			// 不能用current_timestamp的当前偏移,否则历史夏令时日期会偏一小时
			String utcTs = "sys_extract_utc(from_tz(cast(" + args[0].trim() + " as timestamp), sessiontimezone))";
			return "cast(round((cast(" + utcTs + " as date) - date '1970-01-01') * 86400) as number(38))";
		}
		// update 2026-11-11 HANA补映射(hana2真库实测):无unix_timestamp(328 invalid function);
		// seconds_between('epoch',ts)为墙钟差(实测seconds_between(d1,d2)=d2-d1),减去当前偏移
		// seconds_between(current_utctimestamp,current_timestamp)(=本地-UTC秒,含符号;UTC容器为0)。
		// 该口径适用于固定偏移时区;当前偏移不能推导入参日期的历史DST,命名时区场景需调用方显式处理
		if (dialect == DBType.HANA) {
			String tzOffset = "seconds_between(current_utctimestamp, current_timestamp)";
			return hasTs ? "(seconds_between('1970-01-01 00:00:00', " + args[0].trim() + ") - " + tzOffset + ")"
					: "(seconds_between('1970-01-01 00:00:00', current_timestamp) - " + tzOffset + ")";
		}
		return super.IGNORE;
	}

	/**
	 * @description sqlite epoch入参归一:INTEGER毫秒按绝对时刻转UTC文本;字面量、TEXT列及日期函数表达式
	 *              均按本地墙钟经'utc'修饰转UTC文本,避免同一文本因入参形态不同产生时区偏差
	 * @param arg 已trim的入参
	 * @return 可直接交给strftime('%s', ...)的表达式
	 */
	private static String sqliteEpochArg(String arg) {
		String tmp = (arg == null) ? null : arg.trim();
		if (tmp == null || tmp.isEmpty()) {
			return tmp;
		}
		if ((tmp.length() > 2 && tmp.startsWith("'") && tmp.endsWith("'")) || tmp.indexOf('(') >= 0) {
			return "datetime(" + tmp + ",'utc')";
		}
		return "case when typeof(" + tmp + ") in ('integer','real') then datetime(" + tmp
				+ "/1000,'unixepoch') else datetime(" + tmp + ",'utc') end";
	}
}
