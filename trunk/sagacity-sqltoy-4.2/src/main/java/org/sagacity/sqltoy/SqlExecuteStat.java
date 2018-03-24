/**
 * 
 */
package org.sagacity.sqltoy;

import org.sagacity.sqltoy.model.SqlExecuteTrace;

/**
 * @project sagacity-sqltoy4.0
 * @description 提供sql执行统计
 * @author zhongxuchen <a href="mailto:zhongxuchen@gmail.com">联系作者</a>
 * @version id:SqlExecuteStat.java,Revision:v1.0,Date:2015年6月12日
 */
public class SqlExecuteStat {
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
}
