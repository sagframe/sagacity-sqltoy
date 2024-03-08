/**
 * 
 */
package org.sagacity.sqltoy.config.model;

import java.io.Serializable;

/**
 * @project sqltoy-orm
 * @description sql和解析参数后的模型,用于存放sql和sql中的参数名称数值
 * @author zhongxuchen
 * @version v1.0,Date:2015年1月9日
 */
public class SqlParamsModel implements Serializable {

	/**
	 * 
	 */
	private static final long serialVersionUID = -3519750508404237911L;

	/**
	 * 替换:name为?的sql
	 */
	private String sql;

	/**
	 * sql中的参数名称数组
	 */
	private String[] paramsName;

	/**
	 * 参数数量
	 */
	private int paramCnt = 0;

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
	 * @return the paramsName
	 */
	public String[] getParamsName() {
		return paramsName;
	}

	/**
	 * @param paramsName the paramsName to set
	 */
	public void setParamsName(String[] paramsName) {
		this.paramsName = paramsName;
	}

	public int getParamCnt() {
		return paramCnt;
	}

	public void setParamCnt(int paramCnt) {
		this.paramCnt = paramCnt;
	}

}
