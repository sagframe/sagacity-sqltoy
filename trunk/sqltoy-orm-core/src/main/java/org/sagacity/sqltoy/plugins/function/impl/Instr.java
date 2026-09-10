package org.sagacity.sqltoy.plugins.function.impl;

import java.util.Locale;
import java.util.regex.Pattern;

import org.sagacity.sqltoy.plugins.function.IFunction;
import org.sagacity.sqltoy.utils.DataSourceUtils.DBType;

/**
 * @project sagacity-sqltoy
 * @description 针对不同数据库字符串indexOf 函数的不同用法转换
 * @author zhongxuchen
 * @version v1.0,Date:2013-03-21
 */
public class Instr extends IFunction {
	private static Pattern regex = Pattern.compile("(?i)\\W(instr|charindex|position)\\(");

	/*
	 * (non-Javadoc)
	 * 
	 * @see org.sagacity.sqltoy.config.function.IFunction#dialects()
	 */
	@Override
	public String dialects() {
		return ALL;
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

	/*
	 * (non-Javadoc)
	 * 
	 * @see org.sagacity.sqltoy.config.function.IFunction#wrap(int,
	 * java.lang.String[])
	 */
	@Override
	public String wrap(int dialect, String functionName, boolean hasArgs, String... args) {
		if (args == null || args.length == 0) {
			return super.IGNORE;
		}
		String[] realArgs;
		String funLow = functionName.toLowerCase(Locale.ROOT);
		if ("position".equals(funLow)) {
			realArgs = args[0].split("(?i)\\sin\\s");
		} else {
			realArgs = args;
		}
		StringBuilder result = new StringBuilder();
		if (dialect == DBType.SQLSERVER) {
			if ("charindex".equals(funLow)) {
				return super.IGNORE;
			}
			// update 2026-9-5 charindex不支持occurrence参数(instr第4参),
			// 静默丢弃会改变语义,保持原样交由目标库报错(响亮失败优于静默错误结果)
			if (realArgs.length > 3) {
				return super.IGNORE;
			}
			result.append("charindex(");
			if ("position".equals(funLow)) {
				result.append(realArgs[0]).append(",").append(realArgs[1]);
			} else {
				result.append(realArgs[1]).append(",").append(realArgs[0]);
				if (realArgs.length > 2) {
					result.append(",").append(realArgs[2]);
				}
			}
			return result.append(")").toString();
		}
		// update 2026-9-9 Db2 LUW无INSTR函数(LOCATE/POSSTR承担),原分支对instr原样透传会报
		// 函数不存在;LOCATE(search,source[,start])参数语义与mysql的locate一致,统一转locate
		if (dialect == DBType.DB2) {
			if ("position".equals(funLow)) {
				// position(sub in str):realArgs=[sub,str]
				return "locate(" + realArgs[0] + "," + realArgs[1] + ")";
			}
			if ("charindex".equals(funLow)) {
				// charindex(sub,str[,start]):参数序与locate一致
				return wrapArgs("locate", realArgs);
			}
			// instr(str,sub[,pos[,occurrence]]):第4参occurrence无locate对应形态,原样保留交由目标库报错
			if (realArgs.length > 3) {
				return super.IGNORE;
			}
			result.append("locate(").append(realArgs[1]).append(",").append(realArgs[0]);
			if (realArgs.length > 2) {
				result.append(",").append(realArgs[2]);
			}
			return result.append(")").toString();
		}
		if (dialect == DBType.MYSQL || dialect == DBType.ORACLE || dialect == DBType.OCEANBASE || dialect == DBType.DM
				|| dialect == DBType.TIDB || dialect == DBType.ORACLE11 || dialect == DBType.MYSQL57
				|| dialect == DBType.H2 || dialect == DBType.DORIS || dialect == DBType.STARROCKS) {
			if ("instr".equals(funLow)) {
				// update 2026-9-5 三参instr(str,sub,pos)在mysql无此语法,转locate(sub,str,pos);
				// 四参(occurrence)mysql的locate同样不支持,原样保留交由目标库报错
				if (dialect == DBType.MYSQL || dialect == DBType.TIDB || dialect == DBType.MYSQL57
						|| dialect == DBType.DORIS || dialect == DBType.STARROCKS) {
					if (realArgs.length == 3) {
						return "locate(" + realArgs[1] + "," + realArgs[0] + "," + realArgs[2] + ")";
					}
				}
				return super.IGNORE;
			}
			// position mysql、h2也支持 update 2021-11-11
			if (dialect == DBType.MYSQL || dialect == DBType.MYSQL57 || dialect == DBType.H2 || dialect == DBType.DORIS
					|| dialect == DBType.STARROCKS) {
				if ("position".equals(funLow)) {
					return super.IGNORE;
				}
			}
			result.append("instr(").append(realArgs[1]).append(",").append(realArgs[0]);
			if (realArgs.length > 2) {
				result.append(",").append(realArgs[2]);
			}
			if (realArgs.length > 3) {
				result.append(",").append(realArgs[3]);
			}
			return result.append(")").toString();
		}
		if (dialect == DBType.POSTGRESQL || dialect == DBType.POSTGRESQL14 || dialect == DBType.GAUSSDB
				|| dialect == DBType.OPENGAUSS || dialect == DBType.OSCAR || dialect == DBType.STARDB
				|| dialect == DBType.MOGDB || dialect == DBType.VASTBASE) {
			if ("position".equals(funLow)) {
				return super.IGNORE;
			}
			if (realArgs.length == 2) {
				result.append("position(");
				if ("charindex".equals(funLow)) {
					result.append(realArgs[0]).append(" in ").append(realArgs[1]);
				} else {
					result.append(realArgs[1]).append(" in ").append(realArgs[0]);
				}
				return result.append(")").toString();
			}
		}
		return super.IGNORE;
	}
}
