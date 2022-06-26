package org.sagacity.sqltoy;

import static java.lang.System.out;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.util.Collection;
import java.util.Date;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import org.sagacity.sqltoy.config.model.SqlExecuteLog;
import org.sagacity.sqltoy.config.model.SqlExecuteTrace;
import org.sagacity.sqltoy.model.OverTimeSql;
import org.sagacity.sqltoy.plugins.OverTimeSqlHandler;
import org.sagacity.sqltoy.utils.DateUtil;
import org.sagacity.sqltoy.utils.StringUtil;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * @project sagacity-sqltoy
 * @description 提供sql执行超时统计和基本的sql输出功能
 * @author zhongxuchen
 * @version v1.0,Date:2015年6月12日
 * @modify {Date:2020-06-15,改进sql日志输出,将条件参数带入到sql中输出，便于开发调试}
 * @modify {Date:2020-08-12,为日志输出增加统一uid,便于辨别同一组执行语句}
 */
public class SqlExecuteStat {
	/**
	 * 定义日志
	 */
	private final static Logger logger = LoggerFactory.getLogger(SqlExecuteStat.class);

	/**
	 * 是否调试阶段
	 */
	private static boolean debug = false;

	/**
	 * 打印慢sql(单位毫秒,默认超过18秒)
	 */
	private static int printSqlTimeoutMillis = 18000;

	// 用于拟合sql中的条件值表达式(前后都以非字符和数字为依据目的是最大幅度的避免参数值里面存在问号,实际执行过程中这个问题已经被规避,但调试打印参数带入无法规避)
	private final static Pattern ARG_PATTERN = Pattern.compile("\\W\\?\\W");

	// 通过ThreadLocal 来保存进程数据
	private static ThreadLocal<SqlExecuteTrace> threadLocal = new ThreadLocal<SqlExecuteTrace>();

	public static OverTimeSqlHandler overTimeSqlHandler;

	/**
	 * @todo 登记开始执行
	 * @param sqlId
	 * @param type
	 * @param debugPrint
	 */
	public static void start(String sqlId, String type, Boolean debugPrint) {
		threadLocal.set(new SqlExecuteTrace(sqlId, type, (debugPrint == null) ? debug : debugPrint.booleanValue()));
	}

	/**
	 * @todo 向线程中登记发生了异常,便于在finally里面明确是错误并打印相关sql
	 * @param exception
	 */
	public static void error(Exception exception) {
		if (threadLocal.get() != null) {
			threadLocal.get().setError(exception.getMessage());
		}
	}

	/**
	 * @todo 在debug模式下,在console端输出sql,便于开发人员查看
	 * @param topic
	 * @param sql
	 * @param paramValues
	 */
	public static void showSql(String topic, String sql, Object[] paramValues) {
		try {
			// debug模式直接输出
			if (threadLocal.get() != null) {
				threadLocal.get().addSqlLog(topic, sql, paramValues);
			}
		} catch (Exception e) {

		}
	}

	/**
	 * @TODO 提供中间日志输出
	 * @param topic
	 * @param message
	 * @param args
	 */
	public static void debug(String topic, String message, Object... args) {
		try {
			if (threadLocal.get() != null) {
				threadLocal.get().addLog(topic, message, args);
			}
		} catch (Exception e) {

		}
	}

	/**
	 * 在执行结尾时记录日志
	 */
	private static void destroyLog() {
		try {
			SqlExecuteTrace sqlTrace = threadLocal.get();
			if (sqlTrace == null) {
				return;
			}
			long runTime = sqlTrace.getExecuteTime();
			long overTime = runTime - printSqlTimeoutMillis;
			// sql执行超过阀值记录日志为软件优化提供依据
			if (overTime >= 0) {
				sqlTrace.setOverTime(true);
				sqlTrace.addLog("slowSql执行超时", "耗时(毫秒):{} >={} (阀值)!", runTime, printSqlTimeoutMillis);
			} else {
				sqlTrace.addLog("执行时长", "耗时:{} 毫秒 !", runTime);
			}
			// 日志输出
			printLogs(sqlTrace);
		} catch (Exception e) {

		}
	}

	/**
	 * @TODO 输出日志
	 * @param sqlTrace
	 */
	private static void printLogs(SqlExecuteTrace sqlTrace) {
		boolean errorLog = false;
		String reportStatus = "成功!";
		if (sqlTrace.isOverTime()) {
			errorLog = true;
			reportStatus = "执行耗时超阀值!";
		}
		if (sqlTrace.isError()) {
			errorLog = true;
			reportStatus = "发生异常错误!";
		}

		if (!errorLog) {
			// sql中已经标记了#not_debug# 表示无需输出日志(一般针对缓存检测、缓存加载等,避免这些影响业务日志)
			if (!sqlTrace.isPrint()) {
				return;
			}
		}

		String uid = sqlTrace.getUid();
		StringBuilder result = new StringBuilder();
		String optType = sqlTrace.getType();
		String codeTrace = getFirstTrace();
		result.append("\n/*|----------------------开始执行报告输出 --------------------------------------------------*/");
		result.append("\n/*|任 务 ID: " + uid);
		result.append("\n/*|执行结果: " + reportStatus);
		result.append("\n/*|执行类型: " + optType);
		result.append("\n/*|代码定位: " + codeTrace);
		if (sqlTrace.getId() != null) {
			result.append("\n/*|对应sqlId: " + sqlTrace.getId());
		}
		List<SqlExecuteLog> executeLogs = sqlTrace.getExecuteLogs();
		int step = 0;
		int logType;
		String topic;
		String content;
		Object[] args = null;
		String sql = null;
		for (SqlExecuteLog log : executeLogs) {
			step++;
			logType = log.getType();
			topic = log.getTopic();
			content = log.getContent();
			args = log.getArgs();
			if (logType == 0) {
				result.append("\n/*|---- 过程: " + step + "," + topic + "----------------");
				// 区别一些批量写和更新操作，参数较多不便于输出
				if (optType.startsWith("save") || optType.startsWith("deleteAll")
						|| optType.startsWith("batchUpdate")) {
					result.append("\n/*|     内部sql: ").append(fitSqlParams(content, args));
					result.append("\n/*|     save(All)|saveOrUpdate(All)|deleleAll|batchUpdate等不输出sql执行参数");
				} else {
					sql = fitSqlParams(content, args);
					result.append("\n/*|     模拟入参后sql: ").append(sql);
					result.append("\n/*|     sql参数: ");
					if (args != null && args.length > 0) {
						StringBuilder paramStr = new StringBuilder();
						for (int i = 0; i < args.length; i++) {
							if (i > 0) {
								paramStr.append(",");
							}
							paramStr.append("p[" + i + "]=" + args[i]);
						}
						result.append(paramStr);
					} else {
						result.append("无参数");
					}
				}
			} else {
				result.append("\n/*|---- 过程: " + step + "," + topic
						+ (content == null ? "" : ":" + StringUtil.fillArgs(content, args)));
			}
		}
		result.append("\n/*|----------------------完成执行报告输出 --------------------------------------------------*/");
		result.append("\n");
		if (sqlTrace.isError()) {
			logger.error(result.toString());
		} else if (sqlTrace.isOverTime()) {
			logger.warn(result.toString());
			if (overTimeSqlHandler != null) {
				overTimeSqlHandler
						.log(new OverTimeSql(sqlTrace.getId(), sql, args, sqlTrace.getExecuteTime(), codeTrace));
			}
		} else {
			if (logger.isDebugEnabled()) {
				logger.debug(result.toString());
			} else {
				out.println(result.toString());
			}
		}
	}

	/**
	 * 清理线程中的数据
	 */
	public static void destroy() {
		// 执行完成时打印日志
		destroyLog();
		threadLocal.remove();
		threadLocal.set(null);
	}

	public static void destroyNotLog() {
		threadLocal.remove();
		threadLocal.set(null);
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

	/**
	 * @TODO 将参数值拟合到sql中作为debug输出,便于开发进行调试(2020-06-15)
	 * @param sql
	 * @param params
	 * @return
	 */
	private static String fitSqlParams(String sql, Object[] params) {
		if (sql == null || params == null || params.length == 0) {
			return sql;
		}
		StringBuilder lastSql = new StringBuilder();
		// 补空的目的在于迎合匹配规则
		Matcher matcher = ARG_PATTERN.matcher(sql.concat(" "));
		int start = 0;
		int end;
		int index = 0;
		int paramSize = params.length;
		Object paramValue;
		// 逐个查找?用实际参数值进行替换
		while (matcher.find(start)) {
			end = matcher.start() + 1;
			lastSql.append(sql.substring(start, end));
			if (index < paramSize) {
				paramValue = params[index];
				// 字符
				if (paramValue instanceof CharSequence) {
					lastSql.append("'" + paramValue + "'");
				} else if (paramValue instanceof Date || paramValue instanceof LocalDateTime) {
					lastSql.append("'" + DateUtil.formatDate(paramValue, "yyyy-MM-dd HH:mm:ss") + "'");
				} else if (paramValue instanceof LocalDate) {
					lastSql.append("'" + DateUtil.formatDate(paramValue, "yyyy-MM-dd") + "'");
				} else if (paramValue instanceof LocalTime) {
					lastSql.append("'" + DateUtil.formatDate(paramValue, "HH:mm:ss") + "'");
				} else if (paramValue instanceof Object[]) {
					lastSql.append(combineArray((Object[]) paramValue));
				} else if (paramValue instanceof Collection) {
					lastSql.append(combineArray(((Collection) paramValue).toArray()));
				} else {
					lastSql.append("" + paramValue);
				}
			} else {
				// 问号数量大于参数值数量,说明sql中存在写死的条件值里面存在问号,因此不再进行条件值拟合
				return sql;
			}
			// 正则匹配最后是\\W,所以要-1
			start = matcher.end() - 1;
			index++;
		}
		lastSql.append(sql.substring(start));
		return lastSql.toString();
	}

	/**
	 * @TODO 组合in参数
	 * @param array
	 * @return
	 */
	private static String combineArray(Object[] array) {
		if (array == null || array.length == 0) {
			return " null ";
		}
		StringBuilder result = new StringBuilder();
		Object value;
		for (int i = 0; i < array.length; i++) {
			if (i > 0) {
				result.append(",");
			}
			value = array[i];
			if (value instanceof CharSequence) {
				result.append("'" + value + "'");
			} else if (value instanceof Date || value instanceof LocalDateTime) {
				result.append("'" + DateUtil.formatDate(value, "yyyy-MM-dd HH:mm:ss") + "'");
			} else if (value instanceof LocalDate) {
				result.append("'" + DateUtil.formatDate(value, "yyyy-MM-dd") + "'");
			} else if (value instanceof LocalTime) {
				result.append("'" + DateUtil.formatDate(value, "HH:mm:ss") + "'");
			} else {
				result.append("" + value);
			}
		}
		return result.toString();
	}

	/**
	 * @TODO 定位第一个调用sqltoy的代码位置
	 * @return
	 */
	public static String getFirstTrace() {
		StackTraceElement[] stackTraceElements = Thread.currentThread().getStackTrace();
		String className = null;
		int lineNumber = 0;
		String method = null;
		StackTraceElement traceElement;
		int length = stackTraceElements.length;
		// 逆序
		for (int i = length - 1; i > 0; i--) {
			traceElement = stackTraceElements[i];
			className = traceElement.getClassName();
			// 进入调用sqltoy的代码，此时取上一个
			if (className.startsWith("org.sagacity.sqltoy")) {
				method = traceElement.getMethodName();
				lineNumber = traceElement.getLineNumber();
				// 避免异常发生
				if (i + 1 < length) {
					traceElement = stackTraceElements[i + 1];
					className = traceElement.getClassName();
					method = traceElement.getMethodName();
					lineNumber = traceElement.getLineNumber();
				}
				break;
			}
		}
		return "" + className + "." + method + "[代码第:" + lineNumber + " 行]";
	}

	/**
	 * @TODO 获取执行总时长
	 * @return
	 */
	public static Long getExecuteTime() {
		SqlExecuteTrace sqlTrace = threadLocal.get();
		if (sqlTrace != null) {
			return sqlTrace.getExecuteTime();
		}
		return -1L;
	}

	public static SqlExecuteTrace get() {
		return threadLocal.get();
	}

	public static void set(SqlExecuteTrace sqlTrace) {
		threadLocal.set(sqlTrace);
	}

	public static void mergeTrace(SqlExecuteTrace sqlTrace) {
		if (sqlTrace != null) {
			threadLocal.set(new SqlExecuteTrace(sqlTrace.getId(), sqlTrace.getType(), sqlTrace.isPrint()));
		}
	}
}
