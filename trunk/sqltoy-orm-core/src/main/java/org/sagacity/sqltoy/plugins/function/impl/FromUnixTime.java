package org.sagacity.sqltoy.plugins.function.impl;

import java.util.regex.Pattern;

import org.sagacity.sqltoy.plugins.function.IFunction;
import org.sagacity.sqltoy.utils.DataSourceUtils.DBType;

/**
 * @project sagacity-sqltoy
 * @description from_unixtime(n)的跨库适配(2026-11-10 补齐,unix_timestamp的逆,update
 *              2026-11-11 时区一致性):
 *              MySQL系/DM原生(dm8自带且会话时区感知正确,含fmt形态);PG系to_timestamp(n); sqlserver
 *              dateadd(second,n+会话偏移,'1970-01-01');DB2 基准+(n+CURRENT
 *              TIMEZONE偏移)秒; H2在UTC基准加时后按会话时区转本地墙钟(目标日期DST感知);sqlite
 *              datetime(n,'unixepoch','localtime');Oracle先标UTC再 at time zone
 *              sessiontimezone按目标日期应用DST呈现;HANA add_seconds+当前偏移(固定偏移口径,真库实测);
 *              OB/CH(MySQL兼容)透传。
 *              带fmt格式参数的形态仅MySQL系/DM原生(跨库需格式串方言转换,组合复杂,其余方言响亮)。
 * @author zhongxuchen
 * @version v1.0,Date:2026-11-10
 */
public class FromUnixTime extends IFunction {

	private static Pattern regex = Pattern.compile("(?i)\\W(from_unixtime|to_timestamp)\\(");

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
		String funLow = functionName.toLowerCase(java.util.Locale.ROOT);
		// update 2026-11-10 补to_timestamp单参源(PG的epoch转时刻习惯):仅单参为epoch语义,
		// 双参to_timestamp(str,fmt)是解析语义归ToDate承接(其对本形态IGNORE让位);注册序ToDate在前,
		// 其对单参IGNORE后此处接力转换
		boolean pgEpochSrc = "to_timestamp".equals(funLow);
		if (pgEpochSrc && args.length >= 2) {
			return super.IGNORE;
		}
		String n = args[0].trim();
		// update 2026-11-10 to_timestamp源的单参若是字符串字面量,语义是"文本解析"而非epoch:
		// 直转from_unixtime在mysql/sqlite对非数字字符串静默返回NULL(真库行为),db2本可原生解析的
		// 时刻会被算成epoch数值——两类都是静默错值。非数值字面量一律透传,由目标库对to_timestamp
		// 的有无/解析能力响亮裁决(pg系原生解析、其他库报函数不存在)
		if (pgEpochSrc && isQuotedNonNumeric(n)) {
			return super.IGNORE;
		}
		// 带fmt格式参数:仅MySQL系/DM原生(其余方言需格式串转换,响亮)
		// update 2026-11-11 DM归原生:dm8的from_unixtime原生支持MySQL风格fmt(真库实测
		// from_unixtime(1794362400,'%Y-%m-%d %H:%i')='2026-11-11 10:00')
		if (args.length >= 2) {
			if (dialect == DBType.MYSQL || dialect == DBType.MYSQL57 || dialect == DBType.TIDB
					|| dialect == DBType.DORIS || dialect == DBType.STARROCKS || dialect == DBType.DM) {
				return super.IGNORE;
			}
			return super.IGNORE;
		}
		// MySQL系/OB/DM原生(from_unixtime源);PG的to_timestamp源在此须转为from_unixtime
		// update 2026-11-11 DM归原生:dm8自带from_unixtime且会话时区感知正确(真库实测
		// from_unixtime(1794362400)='2026-11-11 10:00:00',即+08墙钟呈现)
		if (dialect == DBType.MYSQL || dialect == DBType.MYSQL57 || dialect == DBType.TIDB || dialect == DBType.DORIS
				|| dialect == DBType.STARROCKS || dialect == DBType.OCEANBASE || dialect == DBType.DM) {
			return pgEpochSrc ? "from_unixtime(" + n + ")" : super.IGNORE;
		}
		// update 2026-11-10 CH修正:无from_unixtime(真库同族问题),转CH原生fromUnixTimestamp(仅无fmt形态)
		if (dialect == DBType.CLICKHOUSE) {
			return "fromUnixTimestamp(" + n + ")";
		}
		// PG系:to_timestamp(n)返回timestamptz(绝对时刻,按会话时区呈现,与MySQL行为一致)
		if (dialect == DBType.POSTGRESQL || dialect == DBType.POSTGRESQL14 || dialect == DBType.OPENGAUSS
				|| dialect == DBType.GAUSSDB || dialect == DBType.MOGDB || dialect == DBType.VASTBASE
				|| dialect == DBType.STARDB || dialect == DBType.OSCAR || dialect == DBType.KINGBASE
				|| dialect == DBType.HIGHGO) {
			return "to_timestamp(" + n + ")";
		}
		if (dialect == DBType.SQLSERVER) {
			// 固定偏移时区下加当前偏移对齐MySQL墙钟呈现;SQLServer无会话时区名,
			// 命名时区的历史DST需调用方显式处理,不能由当前偏移推导
			String tzOffset = "(datepart(tzoffset, sysdatetimeoffset()) * 60)";
			return "dateadd(second, (" + n + " + " + tzOffset + "), '1970-01-01')";
		}
		if (dialect == DBType.SQLITE) {
			// update 2026-11-11 与MySQL会话时区语义对齐(本地墙钟呈现),原缺少localtime恒为UTC文本
			return "datetime(" + n + ", 'unixepoch', 'localtime')";
		}
		if (dialect == DBType.H2) {
			// update 2026-11-11 先在UTC基准上得到绝对时刻,再按会话命名时区转为本地墙钟;
			// CAST按目标日期应用DST,不能用current_timestamp的当前偏移补偿历史时间
			return "cast(dateadd('second', " + n + ", timestamp with time zone '1970-01-01 00:00:00+00') as timestamp)";
		}
		// DB2:秒数加到epoch基准
		if (dialect == DBType.DB2) {
			// CURRENT TIMEZONE为DECIMAL(6,0)的hhmmss持续时间,按符号统一换算为秒;
			// DB2无会话时区名,该补偿适用于固定偏移时区,历史DST需调用方显式处理
			String tzOffset = ("(sign(current timezone) * (integer(abs(current timezone)/10000) * 3600"
					+ " + mod(integer(abs(current timezone)/100),100) * 60"
					+ " + mod(integer(abs(current timezone)),100)))");
			return "timestamp(timestamp('1970-01-01 00:00:00') + (" + n + " + " + tzOffset + ") seconds)";
		}
		// Oracle先将epoch基准标注为UTC,再按会话命名时区呈现;AT TIME ZONE按目标时刻计算DST偏移
		// (update 2026-11-11 真库Oracle23实测放开;DM归原生透传,见上方原生分支注释)
		if (dialect == DBType.ORACLE || dialect == DBType.ORACLE11) {
			return "cast(from_tz(timestamp '1970-01-01 00:00:00' + numtodsinterval(" + n
					+ ", 'SECOND'), 'UTC') at time zone sessiontimezone as timestamp)";
		}
		// update 2026-11-11 HANA补映射(hana2真库实测):无from_unixtime;add_seconds为纯墙钟加法
		// (实测add_seconds('1970-01-01 00:00:00',1794362400)='2026-11-11 02:00:00'),加当前偏移
		// seconds_between(current_utctimestamp,current_timestamp)(=本地-UTC秒,含符号;UTC容器为0)。
		// 该口径适用于固定偏移时区;当前偏移不能推导目标日期的历史DST,命名时区场景需调用方显式处理
		if (dialect == DBType.HANA) {
			String tzOffset = "seconds_between(current_utctimestamp, current_timestamp)";
			return "add_seconds('1970-01-01 00:00:00', (" + n + " + " + tzOffset + "))";
		}
		return super.IGNORE;
	}

	/**
	 * @description 判断入参是否为"字符串字面量且非数字":此类入参在to_timestamp源下是文本解析语义, 不能按epoch数值转发
	 * @param arg 已trim的入参
	 * @return true表示引号包裹且内容非数值
	 */
	private static boolean isQuotedNonNumeric(String arg) {
		if (!Truncate.isQuotedLiteral(arg)) {
			return false;
		}
		String inner = arg.substring(1, arg.length() - 1).trim();
		return !inner.matches("[-+]?[0-9]+(\\.[0-9]+)?");
	}
}
