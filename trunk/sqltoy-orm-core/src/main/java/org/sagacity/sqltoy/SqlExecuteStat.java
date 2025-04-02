package org.sagacity.sqltoy;

import static java.lang.System.out;

import java.lang.reflect.Proxy;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import org.sagacity.sqltoy.config.model.SqlExecuteLog;
import org.sagacity.sqltoy.config.model.SqlExecuteTrace;
import org.sagacity.sqltoy.model.OperateDetailType;
import org.sagacity.sqltoy.model.OverTimeSql;
import org.sagacity.sqltoy.plugins.FirstBizCodeTrace;
import org.sagacity.sqltoy.plugins.OverTimeSqlHandler;
import org.sagacity.sqltoy.plugins.formater.SqlFormater;
import org.sagacity.sqltoy.utils.DataSourceUtils;
import org.sagacity.sqltoy.utils.SqlUtil;
import org.sagacity.sqltoy.utils.StringUtil;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.alibaba.ttl.TransmittableThreadLocal;

/**
 * @project sagacity-sqltoy
 * @description 提供sql执行超时统计和基本的sql输出功能
 * @author zhongxuchen
 * @version v1.0,Date:2015年6月12日
 * @modify {Date:2020-06-15,改进sql日志输出,将条件参数带入到sql中输出，便于开发调试}
 * @modify {Date:2020-08-12,为日志输出增加统一uid,便于辨别同一组执行语句}
 * @modify {Date:2024-07-18,强化对首个业务代码位置的定位,支持aop场景}
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
	 * 打印慢sql(单位毫秒,默认超过8秒)
	 */
	private static int printSqlTimeoutMillis = 8000;

	// 用于拟合sql中的条件值表达式(前后都以非字符和数字为依据目的是最大幅度的避免参数值里面存在问号,实际执行过程中这个问题已经被规避,但调试打印参数带入无法规避)
	private final static Pattern ARG_PATTERN = Pattern.compile("\\W\\?\\W");

	// 通过ThreadLocal 来保存线程数据
	private static ThreadLocal<SqlExecuteTrace> threadLocal = new TransmittableThreadLocal<SqlExecuteTrace>();

	// sql执行超时处理器
	public static OverTimeSqlHandler overTimeSqlHandler;

	/**
	 * 获取业务代码调用位置的实现类
	 */
	public static FirstBizCodeTrace firstBizCodeTrace;

	/**
	 * sql格式化输出器(用于debug sql输出)
	 */
	private static SqlFormater sqlFormater;

	/**
	 * @todo 登记开始执行
	 * @param sqlId
	 * @param type
	 * @param debugPrint
	 */
	public static void start(String sqlId, OperateDetailType type, Boolean debugPrint) {
		threadLocal.set(new SqlExecuteTrace(sqlId, type, (debugPrint == null) ? debug : debugPrint.booleanValue()));
	}

	public static void start(String sqlId, OperateDetailType type, Long batchSize, Boolean debugPrint) {
		SqlExecuteTrace sqlExecuteTrace = new SqlExecuteTrace(sqlId, type,
				(debugPrint == null) ? debug : debugPrint.booleanValue());
		sqlExecuteTrace.setBatchSize(batchSize);
		threadLocal.set(sqlExecuteTrace);
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

	public static void setDialect(String dialect) {
		if (threadLocal.get() != null) {
			threadLocal.get().setDialect(dialect);
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
				sqlTrace.addLog("执行总时长", "耗时:{} 毫秒 !", runTime);
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
		int dbType = DataSourceUtils.getDBType(sqlTrace.getDialect());
		String optType = sqlTrace.getType()
				+ (sqlTrace.getBatchSize() != null ? "[" + sqlTrace.getBatchSize() + "条记录]" : "");
		String codeTrace = getFirstTrace();
		result.append("\n/*|----------------------开始执行报告输出 --------------------------------------------------*/");
		result.append("\n/*|任 务 ID: " + uid);
		result.append("\n/*|执行结果: " + reportStatus);
		result.append("\n/*|数据方言: " + sqlTrace.getDialect());
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
					result.append("\n/*|     内部sql: ").append(fitSqlParams(content, args, dbType));
					result.append("\n/*|     save(All)|saveOrUpdate(All)|deleleAll|batchUpdate等不输出sql执行参数");
				} else {
					sql = fitSqlParams(content, args, dbType);
					// 对sql格式化输出
					if (sqlFormater != null) {
						sql = sqlFormater.format(sql, sqlTrace.getDialect());
						result.append("\n/*|     模拟入参后sql:\n").append(sql);
					} else {
						result.append("\n/*|     模拟入参后sql:").append(sql);
					}
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
				result.append("\n/*|---- 过程: " + step + "," + (null == topic ? "" : topic)
						+ (null == content ? "" : ":" + StringUtil.fillArgs(content, args)));
			}
		}
		result.append("\n/*|----------------------完成执行报告输出 --------------------------------------------------*/");
		result.append("\n");
		if (sqlTrace.isError()) {
			logger.error(result.toString());
		} else if (sqlTrace.isOverTime()) {
			logger.warn(result.toString());
			if (overTimeSqlHandler != null) {
				overTimeSqlHandler.log(new OverTimeSql(sqlTrace.getId(), sql, sqlTrace.getExecuteTime(), codeTrace));
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

	/**
	 * @param debug the debug to set
	 */
	public static void setDebug(boolean debug) {
		SqlExecuteStat.debug = debug;
	}

	public static void setOverTimeSqlHandler(OverTimeSqlHandler overTimeSqlHandler) {
		SqlExecuteStat.overTimeSqlHandler = overTimeSqlHandler;
	}

	/**
	 * @param printSqlTimeoutMillis the printSqlTimeoutMillis to set
	 */
	public static void setPrintSqlTimeoutMillis(int printSqlTimeoutMillis) {
		SqlExecuteStat.printSqlTimeoutMillis = printSqlTimeoutMillis;
	}

	public static void setSqlFormater(SqlFormater sqlFormater) {
		SqlExecuteStat.sqlFormater = sqlFormater;
	}

	/**
	 * @TODO 将参数值拟合到sql中作为debug输出,便于开发进行调试(2020-06-15)
	 * @param sql
	 * @param params
	 * @param dbType
	 * @return
	 */
	public static String fitSqlParams(String sql, Object[] params, int dbType) {
		if (sql == null || params == null || params.length == 0) {
			return sql;
		}
		StringBuilder lastSql = new StringBuilder();
		// 补空的目的在于迎合匹配规则
		Matcher matcher = ARG_PATTERN.matcher(sql.concat(" "));
		int start = 0;
		int index = 0;
		int paramSize = params.length;
		String preSql;
		// 逐个查找?用实际参数值进行替换
		while (matcher.find(start)) {
			preSql = sql.substring(start, matcher.start() + 1);
			lastSql.append(preSql);
			if (index < paramSize) {
				lastSql.append(SqlUtil.toSqlLogStr(params[index], preSql, true, dbType));
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
	 * @TODO 定位第一个调用sqltoy的代码位置
	 * @return
	 */
	public static String getFirstTrace() {
		StackTraceElement[] stackTraceElements = Thread.currentThread().getStackTrace();
		if (stackTraceElements == null || stackTraceElements.length == 0) {
			return "未知类.未知方法[代码第:null行]";
		}
		StackTraceElement traceElement = null;
		String className = null;
		if (firstBizCodeTrace != null) {
			traceElement = firstBizCodeTrace.getFirstTrace(stackTraceElements);
		} else {
			int length = stackTraceElements.length;
			// 逆序
			for (int i = length - 1; i > 0; i--) {
				traceElement = stackTraceElements[i];
				className = traceElement.getClassName();
				// 进入调用sqltoy的代码，此时取上一个
				if (className.startsWith(SqlToyConstants.SQLTOY_PACKAGE)) {
					// 避免异常发生
					if (i + 1 < length) {
						traceElement = stackTraceElements[i + 1];
						className = traceElement.getClassName();
						// 判断是否代理类，找到非代理类、非主流框架类
						int nextIndex = 2;
						while ((i + nextIndex) < length && (Proxy.isProxyClass(traceElement.getClass())
								|| className.startsWith("java.") || className.startsWith("sun.")
								|| className.startsWith("com.sun.") || className.startsWith("org.springframework.")
								|| className.startsWith("org.noear.solon."))) {
							traceElement = stackTraceElements[i + nextIndex];
							className = traceElement.getClassName();
							nextIndex++;
						}
					}
					break;
				}
			}
		}
		return traceElement.getClassName() + "." + traceElement.getMethodName() + "[代码第:" + traceElement.getLineNumber()
				+ " 行]";
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

}
