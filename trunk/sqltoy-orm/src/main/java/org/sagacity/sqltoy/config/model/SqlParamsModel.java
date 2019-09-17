/**
 * 
 */
package org.sagacity.sqltoy.config.model;

import java.io.Serializable;

/**
 * @project sqltoy-orm
 * @description sql和解析参数后的模型,用于存放sql和sql中的参数名称数值
 * @author chenrenfei <a href="mailto:zhongxuchen@gmail.com">联系作者</a>
 * @version id:SqlParamsModel.java,Revision:v1.0,Date:2015年1月9日
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

}
