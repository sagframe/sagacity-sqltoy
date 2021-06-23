/**
 * 
 */
package org.sagacity.sqltoy.config.model;

import java.io.Serializable;

/**
 * @project sagacity-sqltoy
 * @description sql执行日志模型
 * @author zhongxuchen
 * @version v1.0, Date:2020-9-25
 * @modify 2020-9-25,修改说明
 */
public class SqlExecuteLog implements Serializable {

	/**
	 * 
	 */
	private static final long serialVersionUID = 9086562500060758958L;

	/**
	 * 日志类型:0 表示sql日志，1:普通日志
	 */
	private int type = 0;

	/**
	 * 日志主题
	 */
	private String topic;

	/**
	 * 日志内容
	 */
	private String content;

	/**
	 * 日志参数
	 */
	private Object[] args;

	/**
	 * 
	 */
	public SqlExecuteLog(int type, String topic, String content, Object[] args) {
		this.type = type;
		this.topic = topic;
		this.content = content;
		this.args = args;
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
	 * @return the type
	 */
	public int getType() {
		return type;
	}

	/**
	 * @param type the type to set
	 */
	public void setType(int type) {
		this.type = type;
	}

	/**
	 * @return the content
	 */
	public String getContent() {
		return content;
	}

	/**
	 * @param content the content to set
	 */
	public void setContent(String content) {
		this.content = content;
	}

	/**
	 * @return the args
	 */
	public Object[] getArgs() {
		return args;
	}

	/**
	 * @param args the args to set
	 */
	public void setArgs(Object[] args) {
		this.args = args;
	}
}
