/**
 * 
 */
package org.sagacity.sqltoy.plugins.function.impl;

import java.util.regex.Pattern;

import org.sagacity.sqltoy.plugins.function.IFunction;
import org.sagacity.sqltoy.utils.DataSourceUtils.DBType;

/**
 * @project sqltoy-orm
 * @description 不同数据库substr函数的转化
 * @author renfei.chen <a href="mailto:zhongxuchen@gmail.com">联系作者</a>
 * @version id:Substring.java,Revision:v1.0,Date:2013-3-21
 */
public class SubStr extends IFunction {
	private static Pattern regex = Pattern.compile("(?i)\\W(substr|substring)\\(");

	/**
	 * 本身就支持substr的数据库
	 */
	public String dialects() {
		return ALL;
	}

	/**
	 * 匹配substr(xx，xx)函数的正则表达式
	 */
	public Pattern regex() {
		return regex;
	}

	/**
	 * 针对不同数据库对如：substr(arg1,arg2,arg3)进行转换，框架自动将arg1和arg2等参数作为数组传进来
	 */
	public String wrap(int dialect, String functionName, boolean hasArgs, String... args) {
		if (dialect == DBType.POSTGRESQL || dialect == DBType.GAUSSDB || dialect == DBType.SQLSERVER
				|| dialect == DBType.SYBASE_IQ) {
			return wrapArgs("substring", args);
		}
		if (dialect == DBType.MYSQL || dialect == DBType.ORACLE || dialect == DBType.TIDB || dialect == DBType.MYSQL57
				|| dialect == DBType.DB2 || dialect == DBType.DM || dialect == DBType.OCEANBASE
				|| dialect == DBType.ORACLE11) {
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
