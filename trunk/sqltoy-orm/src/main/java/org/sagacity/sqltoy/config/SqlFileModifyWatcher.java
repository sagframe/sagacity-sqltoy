package org.sagacity.sqltoy.config;

import java.util.List;
import java.util.concurrent.ConcurrentHashMap;

import org.sagacity.sqltoy.config.model.SqlToyConfig;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * @project sqltoy-orm
 * @description 用于检测sql文件内容发生变化,如果发生变化则重新加载文件
 * @author zhongxuchen
 * @version v1.0, Date:2012年8月26日
 * @modify 2019年8月26日,将原本调用sql时检测sql文件更新改为一个独立的后台线程
 */
public class SqlFileModifyWatcher extends Thread {
	/**
	 * 定义全局日志
	 */
	private final static Logger logger = LoggerFactory.getLogger(SqlFileModifyWatcher.class);

	private ConcurrentHashMap<String, SqlToyConfig> sqlCache;
	// 存放文件最后修改时间,用于比较是否发生变更
	private ConcurrentHashMap<String, Long> filesLastModifyMap;
	// 存放sql文件
	private List realSqlList;

	/**
	 * 数据库类型
	 */
	private String dialect;

	/**
	 * xml解析格式
	 */
	private String encoding;

	/**
	 * 检测频率
	 */
	private int sleepSeconds = 1;

	/**
	 * 延迟检测时长(秒)
	 */
	private int delayCheckSeconds;

	public SqlFileModifyWatcher(ConcurrentHashMap<String, SqlToyConfig> sqlCache,
			ConcurrentHashMap<String, Long> filesLastModifyMap, List realSqlList, String dialect, String encoding,
			int delayCheckSeconds, int sleepSeconds) {
		this.sqlCache = sqlCache;
		this.realSqlList = realSqlList;
		this.dialect = dialect;
		this.encoding = encoding;
		this.filesLastModifyMap = filesLastModifyMap;
		this.delayCheckSeconds = delayCheckSeconds;
		this.sleepSeconds = (sleepSeconds >= 1) ? sleepSeconds : 1;
	}

	@Override
	public void run() {
		// 延时
		try {
			if (delayCheckSeconds >= 1) {
				Thread.sleep(1000 * delayCheckSeconds);
			}
		} catch (InterruptedException e) {
		}
		boolean isRun = true;
		while (isRun) {
			try {
				SqlXMLConfigParse.parseXML(realSqlList, filesLastModifyMap, sqlCache, encoding, dialect);
			} catch (Exception e) {
				e.printStackTrace();
				logger.error("debug 模式下重新解析SQL对应的xml文件错误!{}", e.getMessage(), e);
			}
			try {
				// 隔几秒进行一次检测,默认开发模式为1秒钟
				Thread.sleep(1000 * sleepSeconds);
			} catch (InterruptedException e) {
				logger.warn("sql文件变更监测程序进程异常,监测将终止!{}", e.getMessage(), e);
				isRun = false;
			}
		}

	}

}
