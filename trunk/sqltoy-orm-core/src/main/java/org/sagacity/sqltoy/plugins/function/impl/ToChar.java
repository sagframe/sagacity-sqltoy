package org.sagacity.sqltoy.plugins.function.impl;

import java.util.regex.Pattern;

import org.sagacity.sqltoy.plugins.function.IFunction;
import org.sagacity.sqltoy.utils.DataSourceUtils.DBType;

/**
 * @project sagacity-sqltoy
 * @description 将其它类型数据转换成字符串
 * @author zhongxuchen
 * @version v1.0,Date:2013-01-02
 */
public class ToChar extends IFunction {
	// update 2026-9-9 正则收窄为仅匹配to_char:原先同时匹配date_format/FORMATDATETIME与DateFormat
	// 职责重叠,date_format在H2目标下先被DateFormat正确转为formatdatetime,再被本函数二次转成
	// to_char(H2 2.x无此函数),最终产物取决于注册顺序,存在链式改写风险
	private static Pattern regex = Pattern.compile("(?i)\\Wto_char\\(");

	@Override
	public String dialects() {
		return ALL;
	}

	/*
	 * (non-Javadoc)
	 *
	 * @see org.sagacity.sqltoy.config.function.IFunction#wrap(java.lang.String [])
	 */
	@Override
	public String wrap(int dialect, String functionName, boolean hasArgs, String... args) {
		if (args == null || args.length < 2) {
			return super.IGNORE;
		}
		String format;
		switch (dialect) {
		case DBType.MYSQL:
		case DBType.TIDB:
		case DBType.DORIS:
		case DBType.STARROCKS:
		case DBType.MYSQL57: {
			// 日期
			format = args[1].replace("yyyy", "%Y").replace("yy", "%y").replace("MM", "%m").replace("dd", "%d");
			// 时间处理(update 2026-9-5 补java 24小时制HH→%H与分钟mm→%i;需置于hh24/hh映射之后)
			format = format.replace("hh24", "%H").replace("hh", "%h").replace("HH", "%H").replace("mm", "%i")
					.replace("mi", "%i").replace("ss", "%s");
			return "date_format(" + args[0] + "," + format + ")";
		}
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
			return "to_char(" + args[0] + "," + format + ")";
		}
		case DBType.H2: {
			// H2原生支持to_char(oracle兼容格式模型,端到端实测验证);
			// update 2026-9-9 修正%y(两位年)误映射为yyyy(四位年)
			format = args[1].replace("%Y", "yyyy").replace("%y", "yy").replace("%m", "MM").replace("%d", "dd");
			// 时间处理(update 2026-9-5 H2 to_char为oracle兼容格式模型,%H应为HH24 24小时制,原hh为12小时制)
			format = format.replace("%T", "hh24:mi:ss");
			format = format.replace("%H", "hh24").replace("%h", "hh").replace("%i", "mi").replace("%s", "ss");
			return "to_char(" + args[0] + "," + format + ")";
		}
		case DBType.CLICKHOUSE: {
			// update 2026-9-9 clickhouse无to_char,以date_format(formatDateTime的mysql兼容别名)承担,
			// token与mysql分支同构(%i分钟/%s秒)
			format = args[1].replace("yyyy", "%Y").replace("yy", "%y").replace("MM", "%m").replace("dd", "%d");
			format = format.replace("hh24", "%H").replace("hh", "%h").replace("HH", "%H").replace("mm", "%i")
					.replace("mi", "%i").replace("ss", "%s");
			return "date_format(" + args[0] + "," + format + ")";
		}
		default:
			return super.IGNORE;
		}
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
}
