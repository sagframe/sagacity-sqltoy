/**
 * 
 */
package org.sagacity.sqltoy.config.model;

/**
 * 
 * @project sqltoy-orm
 * @description 提供一个统一的sql表达形式，即都以:named形式作为参数，便于统一处理
 * @author chenrenfei <a href="mailto:zhongxuchen@gmail.com">联系作者</a>
 * @version id:UnifySqlParams.java,Revision:v1.0,Date:2015年3月3日
 */
public class UnifySqlParams implements java.io.Serializable {
	/**
	 * 
	 */
	private static final long serialVersionUID = 1898722865439598731L;
	
	/**
	 * 以:named形式存在的sql,如果是?则统一构造成:paramNamed1,2等
	 */
	private String sql;
	
	/**
	 * sql中的参数别名
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
	 * @param sql
	 *            the sql to set
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
	 * @param paramsName
	 *            the paramsName to set
	 */
	public void setParamsName(String[] paramsName) {
		this.paramsName = paramsName;
	}

	/**
	 * @return the paramCnt
	 */
	public int getParamCnt() {
		return paramCnt;
	}

	/**
	 * @param paramCnt the paramCnt to set
	 */
	public void setParamCnt(int paramCnt) {
		this.paramCnt = paramCnt;
	}

}