/**
 * 
 */
package org.sagacity.sqltoy;

import static java.lang.System.out;

import java.util.List;

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.sagacity.sqltoy.config.model.SqlToyResult;
import org.sagacity.sqltoy.model.SqlExecuteTrace;

/**
 * @project sagacity-sqltoy4.0
 * @description 提供sql执行统计,先提供基本的sql输出功能
 * @author zhongxuchen <a href="mailto:zhongxuchen@gmail.com">联系作者</a>
 * @version id:SqlExecuteStat.java,Revision:v1.0,Date:2015年6月12日
 */
public class SqlExecuteStat {
	/**
	 * 定义日志
	 */
	private final static Logger logger = LogManager.getLogger(SqlExecuteStat.class);

	/**
	 * 输出sql的策略
	 */
	private static String printSqlStrategy = "error";

	/**
	 * 是否调试阶段
	 */
	private static boolean debug = false;

	/**
	 * 超时打印sql(毫秒,默认30秒)
	 */
	private static int printSqlTimeoutMillis = 30000;

	private static ThreadLocal<SqlExecuteTrace> threadLocal = new ThreadLocal<SqlExecuteTrace>();

	/**
	 * 登记开始执行
	 * 
	 * @param sqlId
	 * @param type
	 */
	public static void start(String sqlId, String type) {
		threadLocal.set(new SqlExecuteTrace(sqlId, type));
	}

	/**
	 * 登记发生了异常
	 */
	public static void error(Exception e) {
		if (threadLocal.get() != null)
			threadLocal.get().setError(true);
	}

	public static void showSql(String sql, Object[] paramValues) {
		try {
			// debug模式直接输出
			if (debug && printSqlStrategy.equals("debug")) {
				printSql(sql, paramValues, false);
			} else if (threadLocal.get() != null)
				threadLocal.get().addSqlToyResult(sql, paramValues);
		} catch (Exception e) {

		}
	}

	/**
	 * @todo 实际执行打印sql和参数
	 * @param sql
	 * @param paramValues
	 * @param isLogger
	 */
	private static void printSql(String sql, Object[] paramValues, boolean isLogger) {
		SqlExecuteTrace sqlTrace = threadLocal.get();
		StringBuilder paramStr = new StringBuilder();
		if (paramValues != null) {
			for (int i = 0; i < paramValues.length; i++) {
				if (i > 0)
					paramStr.append(",");
				paramStr.append("p[" + i + "]=" + paramValues[i]);
			}
		}
		if (sqlTrace != null) {
			if (isLogger) {
				logger.error("执行:{} 类型的sql,sqlId={}", sqlTrace.getType(), sqlTrace.getId() + " 发生异常!");
			} else
				out.println("执行:{" + sqlTrace.getType() + "} 类型sql,sqlId=" + sqlTrace.getId());
		}
		if (isLogger) {
			logger.error("sqlScript:{}", sql);
			logger.error("sqlParams:{}", paramStr);
		} else {
			out.println("sqlScript:" + sql);
			out.println("sqlParams:" + paramStr);
		}
	}

	private static void loggerSql() {
		try {
			SqlExecuteTrace sqlTrace = threadLocal.get();
			if (sqlTrace == null)
				return;
			long overTime = sqlTrace.getExecuteTime() - printSqlTimeoutMillis;
			if (overTime >= 0 && sqlTrace.getStart() != null)
				logger.warn("类型:{}的sql执行超出阀值:{} 共:{} 毫秒,请优化!", printSqlTimeoutMillis, sqlTrace.getType(), overTime);
			else if (!sqlTrace.isError())
				return;
			List<SqlToyResult> sqlToyResults = sqlTrace.getSqlToyResults();
			for (SqlToyResult sqlResult : sqlToyResults)
				printSql(sqlResult.getSql(), sqlResult.getParamsValue(), true);
		} catch (Exception e) {

		}
	}

	public static void destroy() {
		loggerSql();
		threadLocal.remove();
		threadLocal.set(null);
	}

	/**
	 * @param printSqlStrategy
	 *            the printSqlStrategy to set
	 */
	public static void setPrintSqlStrategy(String printSqlStrategy) {
		SqlExecuteStat.printSqlStrategy = printSqlStrategy.toLowerCase();
	}

	/**
	 * @param debug
	 *            the debug to set
	 */
	public static void setDebug(boolean debug) {
		SqlExecuteStat.debug = debug;
	}

	/**
	 * @param printSqlTimeoutMillis
	 *            the printSqlTimeoutMillis to set
	 */
	public static void setPrintSqlTimeoutMillis(int printSqlTimeoutMillis) {
		SqlExecuteStat.printSqlTimeoutMillis = printSqlTimeoutMillis;
	}

}
