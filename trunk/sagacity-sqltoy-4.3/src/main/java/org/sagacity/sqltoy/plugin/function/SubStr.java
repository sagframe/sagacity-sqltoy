/**
 * 
 */
package org.sagacity.sqltoy.plugin.function;

import org.sagacity.sqltoy.plugin.IFunction;
import org.sagacity.sqltoy.utils.DataSourceUtils.DBType;

/**
 * @project sqltoy-orm
 * @description 不同数据库substr函数的转化
 * @author renfei.chen <a href="mailto:zhongxuchen@gmail.com">联系作者</a>
 * @version id:Substring.java,Revision:v1.0,Date:2013-3-21
 */
public class SubStr extends IFunction {
	/**
	 * 本身就支持substr的数据库
	 */
	public String dialects() {
		return "mysql8,oracle12c,db2";
	}

	/**
	 * 匹配substr(xx，xx)函数的正则表达式
	 */
	public String regex() {
		return "(?i)\\W(substr|substring)\\(";
	}

	/**
	 * 针对不同数据库对如：substr(arg1,arg2,arg3)进行转换，框架自动将arg1和arg2等参数作为数组传进来
	 */
	public String wrap(int dialect, String functionName, boolean hasArgs, String... args) {
		if (dialect == DBType.SQLSERVER || dialect == DBType.SQLSERVER2017 || dialect == DBType.SQLSERVER2014
				|| dialect == DBType.SQLSERVER2016 || dialect == DBType.SYBASE_IQ) {
			return wrapArgs("substring", args);
		} else if (dialect == DBType.DB2 || dialect == DBType.DB2_11 || dialect == DBType.MYSQL
				|| dialect == DBType.MYSQL8 || dialect == DBType.ORACLE || dialect == DBType.ORACLE12) {
			return wrapArgs("substr", args);
		}
		// 表示不做修改
		return null;
	}

	/**
	 * 是否存在参数，如：oracle中的sysdate 就不是一个函数模式
	 */
	public boolean hasArgs() {
		return true;
	}

}
