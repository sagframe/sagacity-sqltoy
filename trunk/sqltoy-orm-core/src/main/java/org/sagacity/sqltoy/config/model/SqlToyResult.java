/**
 * 
 */
package org.sagacity.sqltoy.config.model;

import java.io.Serializable;

/**
 * @project sqltoy-orm
 * @description 通过sqlToy工具处理后查询条件以及sql语句的最终结果
 * @author zhongxuchen
 * @version v1.0,Date:2009-12-13
 */
public class SqlToyResult implements Serializable {
	/**
	 * 
	 */
	private static final long serialVersionUID = 1290910325447165025L;

	/**
	 * 处理后的sql语句
	 */
	private String sql;

	/**
	 * 对应sql条件参数位置的值
	 */
	private Object[] paramsValue;

	public SqlToyResult() {

	}

	public SqlToyResult(String sql, Object[] paramsValue) {
		this.sql = sql;
		this.paramsValue = paramsValue;
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
	 * @return the paramsValue
	 */
	public Object[] getParamsValue() {
		return paramsValue;
	}

	/**
	 * @param paramsValue the paramsValue to set
	 */
	public void setParamsValue(Object[] paramsValue) {
		this.paramsValue = paramsValue;
	}
}
