/**
 * 
 */
package org.sagacity.sqltoy.model;

import java.io.Serializable;

/**
 * @project sagacity-sqltoy
 * @description 请在此说明类的功能
 * @author zhong
 * @version v1.0, Date:2020-9-25
 * @modify 2020-9-25,修改说明
 */
public class SqlExecuteLog implements Serializable {

	/**
	 * 
	 */
	private static final long serialVersionUID = 9086562500060758958L;

	/**
	 * 日志主题
	 */
	private String topic;

	private String sql;

	private Object[] paramValues;

	/**
	 * 
	 */
	public SqlExecuteLog(String topic, String sql, Object[] paramValues) {
		this.topic = topic;
		this.sql = sql;
		this.paramValues = paramValues;
	}

	/**
	 * @return the topic
	 */
	public String getTopic() {
		return topic;
	}

	/**
	 * @param topic the topic to set
	 */
	public void setTopic(String topic) {
		this.topic = topic;
	}

	/**
	 * @return the sql
	 */
	public String getSql() {
		return sql;
	}

	/**
	 * @param sql the sql to set
	 */
	public void setSql(String sql) {
		this.sql = sql;
	}

	/**
	 * @return the paramValues
	 */
	public Object[] getParamValues() {
		return paramValues;
	}

	/**
	 * @param paramValues the paramValues to set
	 */
	public void setParamValues(Object[] paramValues) {
		this.paramValues = paramValues;
	}

}
