/**
 * 
 */
package org.sagacity.sqltoy;

import org.sagacity.sqltoy.model.SqlExecuteTrace;

/**
 * @project sagacity-sqltoy4.0
 * @description 提供sql执行统计,先提供基本的sql输出功能
 * @author zhongxuchen <a href="mailto:zhongxuchen@gmail.com">联系作者</a>
 * @version id:SqlExecuteStat.java,Revision:v1.0,Date:2015年6月12日
 */
public class SqlExecuteStat {
	/**
	 * 输出sql的策略
	 */
	private static String printSqlStrategy = "error";
	
	/**
	 * 是否调试阶段
	 */
	private static boolean debug=false;
	
	/**
	 * 超时打印sql(毫秒,默认30秒)
	 */
	private static int printSqlTimeoutMillis = 30000;
	
	private static ThreadLocal<SqlExecuteTrace> threadLocal = new ThreadLocal<SqlExecuteTrace>();

	public static void start(String sqlId) {
		threadLocal.set(new SqlExecuteTrace(sqlId));
	}

	public static void showSql(String sql, Object[] params) {

	}

	public static void errorShowSql() {

	}

	public static void destroy() {
		threadLocal.remove();
	}

	/**
	 * @param printSqlStrategy the printSqlStrategy to set
	 */
	public static void setPrintSqlStrategy(String printSqlStrategy) {
		SqlExecuteStat.printSqlStrategy = printSqlStrategy;
	}

	/**
	 * @param debug the debug to set
	 */
	public static void setDebug(boolean debug) {
		SqlExecuteStat.debug = debug;
	}

	/**
	 * @param printSqlTimeoutMillis the printSqlTimeoutMillis to set
	 */
	public static void setPrintSqlTimeoutMillis(int printSqlTimeoutMillis) {
		SqlExecuteStat.printSqlTimeoutMillis = printSqlTimeoutMillis;
	}
	
	
}
