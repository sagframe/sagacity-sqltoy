package org.sagacity.sqltoy.plugins.function.impl;

import java.util.regex.Pattern;

import org.sagacity.sqltoy.plugins.function.FunctionUtils;
import org.sagacity.sqltoy.plugins.function.IFunction;
import org.sagacity.sqltoy.utils.DataSourceUtils.DBType;

/**
 * @project sagacity-sqltoy
 * @description 日期格式化
 * @author zhongxuchen
 * @version v1.0,Date:2019-09-09
 * @modify Date:2019-09-09, 修改说明
 */
public class DateFormat extends IFunction {
	private static Pattern regex = Pattern.compile("(?i)\\Wdate_format\\(");

	/*
	 * (non-Javadoc)
	 *
	 * @see org.sagacity.sqltoy.plugin.IFunction#dialects()
	 */
	@Override
	public String dialects() {
		return super.ALL;
	}

	/*
	 * (non-Javadoc)
	 *
	 * @see org.sagacity.sqltoy.plugin.IFunction#regex()
	 */
	@Override
	public Pattern regex() {
		return regex;
	}

	/*
	 * (non-Javadoc)
	 *
	 * @see org.sagacity.sqltoy.plugin.IFunction#wrap(int, java.lang.String,
	 * boolean, java.lang.String[])
	 */
	@Override
	public String wrap(int dialect, String functionName, boolean hasArgs, String... args) {
		if (args == null || args.length < 2) {
			return super.IGNORE;
		}
		String format;
		switch (dialect) {
		case DBType.POSTGRESQL:
		case DBType.POSTGRESQL14:
		case DBType.ORACLE:
		case DBType.GAUSSDB:
		case DBType.MOGDB:
		case DBType.STARDB:
		case DBType.OSCAR:
		case DBType.OPENGAUSS:
		case DBType.VASTBASE:
		case DBType.OCEANBASE:
		case DBType.DM:
		case DBType.ORACLE11: {
			// 日期
			format = args[1].replace("%Y", "yyyy").replace("%y", "yy").replace("%m", "MM").replace("%d", "dd");
			// 时间处理
			format = format.replace("%T", "hh24:mi:ss");
			format = format.replace("%H", "hh24").replace("%h", "hh").replace("%i", "mi").replace("%s", "ss");
			// update 2026-9-10 PG语法系裸?首参补::timestamp(date_format恒为日期语义,无数值模型
			// 场景,numericModelPossible=false):vanilla PG对to_char(unknown,unknown)报重载歧义
			return "to_char(" + FunctionUtils.pgToCharParamCast(dialect, args[0], format, false) + "," + format + ")";
		}
		case DBType.MYSQL:
		case DBType.DORIS:
		case DBType.STARROCKS:
		case DBType.TIDB:
		case DBType.MYSQL57: {
			// 日期
			format = args[1].replace("yyyy", "%Y").replace("yy", "%y").replace("MM", "%m").replace("dd", "%d");
			// 时间处理(update 2026-9-5 补java 24小时制HH→%H;需置于hh24/hh映射之后)
			format = format.replace("hh24", "%H").replace("hh", "%h").replace("HH", "%H").replace("mm", "%i")
					.replace("mi", "%i").replace("ss", "%s");
			return "date_format(" + args[0] + "," + format + ")";
		}
		case DBType.H2: {
			// update 2026-9-5 date_format语义为日期格式化(date转字符串),
			// 原parsedatetime是解析(string转date)方向相反,修正为formatdatetime
			// 日期(update 2026-9-9 修正%y两位年误映射为yyyy四位年)
			format = args[1].replace("%Y", "yyyy").replace("%y", "yy").replace("%m", "MM").replace("%d", "dd");
			// 时间处理
			format = format.replace("%T", "HH:mm:ss");
			format = format.replace("%H", "HH").replace("%h", "hh").replace("%i", "mm").replace("%s", "ss");
			return "formatdatetime(" + args[0] + "," + format + ")";
		}
		case DBType.SQLSERVER: {
			// update 2026-9-6 补sqlserver分支(此前落default原样输出date_format报"不是可识别的内置函数名"):
			// format串映射到CONVERT的style精度有限,以FORMAT函数(sqlserver 2012+)实现,支持java样式
			format = args[1].replace("%Y", "yyyy").replace("%y", "yy").replace("%m", "MM").replace("%d", "dd");
			format = format.replace("%T", "HH:mm:ss");
			format = format.replace("%H", "HH").replace("%h", "hh").replace("%i", "mm").replace("%s", "ss");
			return "FORMAT(" + args[0] + ",'" + format.replace("'", "") + "')";
		}
		case DBType.DB2: {
			// db2以VARCHAR_FORMAT(等价TO_CHAR)实现
			format = args[1].replace("%Y", "yyyy").replace("%y", "yy").replace("%m", "MM").replace("%d", "dd");
			format = format.replace("%T", "HH24:MI:SS");
			format = format.replace("%H", "HH24").replace("%h", "HH").replace("%i", "MI").replace("%s", "SS");
			return "VARCHAR_FORMAT(" + args[0] + "," + format + ")";
		}
		case DBType.CLICKHOUSE: {
			// update 2026-9-9 clickhouse原生支持date_format(formatDateTime的mysql兼容别名,%token同构),
			// 仅需将java样式token转%形态(原default原样输出,java样式格式串在目标库静默失效)
			format = args[1].replace("yyyy", "%Y").replace("yy", "%y").replace("MM", "%m").replace("dd", "%d");
			format = format.replace("hh24", "%H").replace("hh", "%h").replace("HH", "%H").replace("mm", "%i")
					.replace("mi", "%i").replace("ss", "%s");
			// update 2026-9-11 首参包toDateTime:clickhouse-jdbc将日期参数以String发送,
			// formatDateTime(String)报Illegal type(26.8实测);toDateTime对Date/DateTime列与
			// ISO文本参数均兼容(幂等转换)
			return "date_format(toDateTime(" + args[0] + ")," + format + ")";
		}
		case DBType.SQLITE: {
			// update 2026-9-6 sqlite以strftime实现(格式标识差异:Y m d H M S);
			// JDBC setTimestamp在sqlite存为毫秒Long,strftime直接作用于毫秒返回null,
			// 须先datetime(ts/1000,'unixepoch')转为日期文本再格式化
			format = args[1].replace("%Y", "%Y").replace("%y", "%y").replace("%m", "%m").replace("%d", "%d");
			format = format.replace("%T", "%H:%M:%S");
			format = format.replace("%H", "%H").replace("%h", "%H").replace("%i", "%M").replace("%s", "%S");
			// update 2026-9-10 参数归一收敛至sqliteDateTextExpr:字面量/文本函数表达式透传
			// (原一律/1000包裹对date('..')等文本形态算出1970垃圾值),列/占位符补'localtime'
			// (原'unixepoch'UTC墙钟使本地午夜毫秒值偏1天,date_format(:dt)实测'2026-01-14')
			return "strftime(" + format + "," + FunctionUtils.sqliteDateTextExpr(args[0]) + ")";
		}
		default:
			return super.IGNORE;
		}
	}

}
