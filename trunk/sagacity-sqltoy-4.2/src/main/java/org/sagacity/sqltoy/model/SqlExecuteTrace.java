/**
 * 
 */
package org.sagacity.sqltoy.model;

import java.io.Serializable;

/**
 * @project sagacity-sqltoy4.2
 * @description sql执行日志
 * @author chenrenfei <a href="mailto:zhongxuchen@gmail.com">联系作者</a>
 * @version id:ExecuteLog.java,Revision:v1.0,Date:2018年3月24日
 */
public class SqlExecuteTrace implements Serializable {
	/**
	 * 
	 */
	private static final long serialVersionUID = 6050450953137017285L;

	public SqlExecuteTrace(String id) {
		this.id = id;
		this.start = System.currentTimeMillis();
	}

	/**
	 * 开始执行
	 */
	private Long start;

	/**
	 * sqlid
	 */
	private String id;

	/**
	 * 执行的sql
	 */
	private String sqlScript;

	/**
	 * 执行参数
	 */
	private Object[] params;

	/**
	 * @return the start
	 */
	public Long getStart() {
		return start;
	}

	public long getExecuteTime() {
		if (start == null)
			return -1;
		return System.currentTimeMillis() - start;
	}

	/**
	 * @return the id
	 */
	public String getId() {
		return id;
	}

	/**
	 * @param id
	 *            the id to set
	 */
	public void setId(String id) {
		this.id = id;
	}

	/**
	 * @return the sqlScript
	 */
	public String getSqlScript() {
		return sqlScript;
	}

	/**
	 * @param sqlScript
	 *            the sqlScript to set
	 */
	public void setSqlScript(String sqlScript) {
		this.sqlScript = sqlScript;
	}

	/**
	 * @return the params
	 */
	public Object[] getParams() {
		return params;
	}

	/**
	 * @param params
	 *            the params to set
	 */
	public void setParams(Object[] params) {
		this.params = params;
	}

}
