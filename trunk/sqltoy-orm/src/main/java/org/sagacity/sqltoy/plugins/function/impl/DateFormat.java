/**
 *
 */
package org.sagacity.sqltoy.plugins.function.impl;

import java.util.regex.Pattern;

import org.sagacity.sqltoy.plugins.function.IFunction;
import org.sagacity.sqltoy.utils.DataSourceUtils.DBType;

/**
 * @project sagacity-sqltoy
 * @description 日期格式化
 * @author zhongxuchen
 * @version v1.0, Date:2019年9月9日
 * @modify 2019年9月9日, 修改说明
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
		case DBType.POSTGRESQL15:
		case DBType.ORACLE:
		case DBType.GAUSSDB:
		case DBType.MOGDB:
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
		case DBType.MYSQL:
		case DBType.TIDB:
		case DBType.MYSQL57: {
			// 日期
			format = args[1].replace("yyyy", "%Y").replace("yy", "%y").replace("MM", "%m").replace("dd", "%d");
			// 时间处理
			format = format.replace("hh24", "%H").replace("hh", "%h").replace("mi", "%i").replace("ss", "%s");
			return "date_format(" + args[0] + "," + format + ")";
		}
		case DBType.H2: {
			// 日期
			format = args[1].replace("%Y", "yyyy").replace("%y", "yyyy").replace("%m", "MM").replace("%d", "dd");
			// 时间处理
			format = format.replace("%T", "hh:mm:ss");
			format = format.replace("%H", "hh").replace("%h", "hh").replace("%i", "mm").replace("%s", "ss");
			return "parsedatetime(" + args[0] + "," + format + ")";
		}
		default:
			return super.IGNORE;
		}
	}

}
