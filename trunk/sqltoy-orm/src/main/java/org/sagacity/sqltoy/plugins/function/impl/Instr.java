/**
 * 
 */
package org.sagacity.sqltoy.plugins.function.impl;

import java.util.regex.Pattern;

import org.sagacity.sqltoy.plugins.function.IFunction;
import org.sagacity.sqltoy.utils.DataSourceUtils.DBType;

/**
 * @project sqltoy-orm
 * @description 针对不同数据库字符串indexOf 函数的不同用法转换
 * @author renfei.chen <a href="mailto:zhongxuchen@gmail.com">联系作者</a>
 * @version id:Instr.java,Revision:v1.0,Date:2013-3-21
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
		String[] realArgs;
		if (functionName.equalsIgnoreCase("position")) {
			realArgs = args[0].split("(?i)\\sin\\s");
		} else {
			realArgs = args;
		}
		StringBuilder result = new StringBuilder();
		if (dialect == DBType.SQLSERVER || dialect == DBType.SYBASE_IQ) {
			if (functionName.equalsIgnoreCase("charindex"))
				return super.IGNORE;
			result.append("charindex(");
			if (functionName.equalsIgnoreCase("position")) {
				result.append(realArgs[0]).append(",").append(realArgs[1]);
			} else {
				result.append(realArgs[1]).append(",").append(realArgs[0]);
				if (realArgs.length > 2) {
					result.append(",").append(realArgs[2]);
				}
				if (realArgs.length > 3) {
					result.append(",").append(realArgs[3]);
				}
			}
			return result.append(")").toString();
		}
		if (dialect == DBType.MYSQL || dialect == DBType.ORACLE || dialect == DBType.DB2 || dialect == DBType.OCEANBASE
				|| dialect == DBType.DM || dialect == DBType.TIDB || dialect == DBType.ORACLE11
				|| dialect == DBType.MYSQL57) {
			if (functionName.equalsIgnoreCase("instr"))
				return super.IGNORE;
			result.append("instr(").append(realArgs[1]).append(",").append(realArgs[0]);
			if (realArgs.length > 2) {
				result.append(",").append(realArgs[2]);
			}
			if (realArgs.length > 3) {
				result.append(",").append(realArgs[3]);
			}
			return result.append(")").toString();
		}
		if (dialect == DBType.POSTGRESQL || dialect == DBType.GAUSSDB) {
			if (functionName.equalsIgnoreCase("position"))
				return super.IGNORE;
			if (realArgs.length == 2) {
				result.append("position(");
				if (functionName.equalsIgnoreCase("charindex")) {
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
