package org.sagacity.sqltoy.plugins.function.impl;

import java.util.regex.Pattern;

import org.sagacity.sqltoy.plugins.function.IFunction;
import org.sagacity.sqltoy.utils.DataSourceUtils.DBType;

/**
 * 增加date_diff/datediff函数不同数据库适配(暂时不启用)
 */
public class DateDiff extends IFunction {
	private static Pattern regex = Pattern.compile("(?i)\\W(datediff|timestampdiff)\\(");

	public String dialects() {
		return ALL;
	}

	@Override
	public Pattern regex() {
		return regex;
	}

	@Override
	public String wrap(int dbType, String functionName, boolean hasArgs, String... args) {
		if (args == null || args.length < 2) {
			return super.IGNORE;
		}
		// 默认为天
		String[] realArgs;
		if (args.length == 2) {
			realArgs = new String[] { "DAY", args[0], args[1] };
		} else {
			realArgs = args;
		}
		// 去除掉单引号、双引号
		String unitType = realArgs[0].toUpperCase().replace("'", "").replace("\"", "");
		String realFunctionName = "datediff";
		String[][] unitConstracts = null;
		if (dbType == DBType.MYSQL || dbType == DBType.MYSQL57 || dbType == DBType.DORIS
				|| dbType == DBType.STARROCKS) {
			unitConstracts = new String[][] { { "DD", "DAY" }, { "MM", "MONTH" }, { "YY", "YEAR" },
					{ "YYYY", "YEAR" } };
			realFunctionName = "timestampdiff";
		} else if (dbType == DBType.ORACLE || dbType == DBType.ORACLE11) {
			if (unitType.equals("YEAR")) {
				return "TRUNC(MONTHS_BETWEEN(" + realArgs[1] + "," + realArgs[2] + ")/12,1)";
			} else if (unitType.equals("MONTH")) {
				return "TRUNC(MONTHS_BETWEEN(" + realArgs[1] + "," + realArgs[2] + "),1)";
			} else if (unitType.equals("DAY")) {
				return "EXTRACT(DAY FROM (" + realArgs[2] + "-" + realArgs[1] + "))";
			}
		} else if (dbType == DBType.MOGDB || dbType == DBType.POSTGRESQL || dbType == DBType.POSTGRESQL15
				|| dbType == DBType.OPENGAUSS || dbType == DBType.VASTBASE || dbType == DBType.STARDB) {
			// round(extract(epoch from(tni.update_time-tni.utime))/3600,2)
			if (unitType.equals("YEAR")) {
				return "(date_part('year'," + realArgs[2] + ")-date_part('year'," + realArgs[1] + "))";
			} else if (unitType.equals("MONTH")) {
				return "((date_part('year'," + realArgs[2] + ")-date_part('year'," + realArgs[1]
						+ "))*12+date_part('month'," + realArgs[2] + ")-date_part('month'," + realArgs[1] + "))";
			} else if (unitType.equals("WEEK")) {
				return "round(date_part('day'," + realArgs[2] + "-" + realArgs[1] + ")/7,1)";
			} else if (unitType.equals("DAY")) {
				return "date_part('day'," + realArgs[2] + "-" + realArgs[1] + ")";
			} else if (unitType.equals("HOUR")) {
				return "round(extract(epoch from(" + realArgs[2] + "-" + realArgs[1] + "))/3600,1)";
			} else if (unitType.equals("MINUTE")) {
				return "round(extract(epoch from(" + realArgs[2] + "-" + realArgs[1] + "))/60,1)";
			} else if (unitType.equals("SECOND")) {
				return "round(extract(epoch from(" + realArgs[2] + "-" + realArgs[1] + ")),0)";
			}
		} else if (dbType == DBType.SQLSERVER) {

		}
		if (unitConstracts != null) {
			realArgs[0] = getMatchedType(unitType, unitConstracts);
			return wrapArgs(realFunctionName, realArgs);
		}
		return super.IGNORE;
	}

	private String getMatchedType(String unitType, String[][] matchConstract) {
		for (String[] constract : matchConstract) {
			if (unitType.equals(constract[0])) {
				return constract[1];
			}
		}
		return unitType;
	}
}
