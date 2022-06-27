package org.sagacity.sqltoy.model;

import java.io.Serializable;
import java.time.LocalDateTime;

/**
 * 提供超时执行的sql模型
 * 
 * @author zhongxuchen
 *
 */
public class OverTimeSql implements Serializable {

	/**
	 * 
	 */
	private static final long serialVersionUID = -3149678048950643843L;

	/**
	 * sqlId
	 */
	private String id;

	/**
	 * sql内容
	 */
	private String sql;

	/**
	 * 耗时(毫秒)
	 */
	private long takeTime;

	/**
	 * 执行时间
	 */
	private LocalDateTime logTime;

	/**
	 * 调用sql的代码位置
	 */
	private String codeTrace;

	/**
	 * 超时次数
	 */
	private int overTimeCount = 1;

	public OverTimeSql(String id, String sql,  long takeTime, String codeTrace) {
		this.id = id;
		this.sql = sql;
		this.takeTime = takeTime;
		this.codeTrace = codeTrace;
		this.logTime = LocalDateTime.now();

	}

	public String getId() {
		return id;
	}

	public void setId(String id) {
		this.id = id;
	}

	public String getSql() {
		return sql;
	}

	public void setSql(String sql) {
		this.sql = sql;
	}

	public long getTakeTime() {
		return takeTime;
	}

	public void setTakeTime(long takeTime) {
		this.takeTime = takeTime;
	}

	public String getCodeTrace() {
		return codeTrace;
	}

	public void setCodeTrace(String codeTrace) {
		this.codeTrace = codeTrace;
	}

	public LocalDateTime getLogTime() {
		return logTime;
	}

	public void setLogTime(LocalDateTime logTime) {
		this.logTime = logTime;
	}

	public int getOverTimeCount() {
		return overTimeCount;
	}

	public void setOverTimeCount(int overTimeCount) {
		this.overTimeCount = overTimeCount;
	}

}
