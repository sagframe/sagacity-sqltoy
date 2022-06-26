package org.sagacity.sqltoy.model;

import java.io.Serializable;

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
	 * 参数
	 */
	private Object[] paramValues;

	/**
	 * 耗时(毫秒)
	 */
	private long takeTime;

	/**
	 * 调用sql的代码位置
	 */
	private String codeTrace;

	public OverTimeSql(String id, String sql, Object[] paramValues, long takeTime, String codeTrace) {
		this.id = id;
		this.sql = sql;
		this.paramValues = paramValues;
		this.takeTime = takeTime;
		this.codeTrace = codeTrace;
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

	public Object[] getParamValues() {
		return paramValues;
	}

	public void setParamValues(Object[] paramValues) {
		this.paramValues = paramValues;
	}
}
