package org.sagacity.sqltoy.plugins.function.impl;

import java.util.regex.Pattern;

import org.sagacity.sqltoy.plugins.function.IFunction;
import org.sagacity.sqltoy.utils.DataSourceUtils.DBType;

/**
 * @project sagacity-sqltoy
 * @description 不同数据库substr函数的转化
 * @author zhongxuchen
 * @version v1.0,Date:2013-03-21
 */
public class SubStr extends IFunction {
	private static Pattern regex = Pattern.compile("(?i)\\W(substr|substring)\\(");

	/**
	 * 本身就支持substr的数据库
	 */
	@Override
	public String dialects() {
		return ALL;
	}

	/**
	 * 匹配substr(xx，xx)函数的正则表达式
	 */
	@Override
	public Pattern regex() {
		return regex;
	}

	/**
	 * 针对不同数据库对如：substr(arg1,arg2,arg3)进行转换，框架自动将arg1和arg2等参数作为数组传进来
	 */
	@Override
	public String wrap(int dialect, String functionName, boolean hasArgs, String... args) {
		if (args == null || args.length == 0) {
			return super.IGNORE;
		}
		if (dialect == DBType.POSTGRESQL || dialect == DBType.POSTGRESQL14 || dialect == DBType.GAUSSDB
				|| dialect == DBType.OPENGAUSS || dialect == DBType.MOGDB || dialect == DBType.VASTBASE
				|| dialect == DBType.SQLSERVER || dialect == DBType.H2 || dialect == DBType.STARDB
				|| dialect == DBType.OSCAR) {
			if (dialect == DBType.SQLSERVER && args.length == 2) {
				// update 2026-9-5 mysql惯用负数起点(substr(s,-2)=末2位):
				// sqlserver substring负起点语义不同,字面量负数转RIGHT(表达式无法判断,原样处理)
				String start = args[1].trim();
				if (start.startsWith("-") && start.substring(1).matches("\\d+")) {
					return "RIGHT(" + args[0] + "," + start.substring(1) + ")";
				}
				return "substring(" + args[0] + "," + args[1] + ",len(" + args[0] + "))";
			}
			return wrapArgs("substring", args);
		}
		if (dialect == DBType.MYSQL || dialect == DBType.ORACLE || dialect == DBType.TIDB || dialect == DBType.MYSQL57
				|| dialect == DBType.DB2 || dialect == DBType.DM || dialect == DBType.OCEANBASE
				|| dialect == DBType.ORACLE11 || dialect == DBType.DORIS || dialect == DBType.STARROCKS) {
			return wrapArgs("substr", args);
		}
		// 表示不做修改
		return super.IGNORE;
	}

	/**
	 * 是否存在参数，如：oracle中的sysdate 就不是一个函数模式
	 */
	public boolean hasArgs() {
		return true;
	}

}
