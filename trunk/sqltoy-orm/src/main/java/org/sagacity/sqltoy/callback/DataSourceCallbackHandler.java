/**
 * 
 */
package org.sagacity.sqltoy.callback;

import java.sql.Connection;

/**
 * @project sagacity-sqltoy
 * @description 数据库连接反调,通过反调传递Connection,并通过Result进行数据交互
 * @author renfei.chen <a href="mailto:zhongxuchen@hotmail.com">联系作者</a>
 * @version id:DataSourceCallbackHandler.java,Revision:v1.0,Date:2012-6-10
 */
public abstract class DataSourceCallbackHandler {
	/**
	 * 结果集
	 */
	private Object result = null;

	/**
	 * @todo 基于给定的连接需要实现的方法
	 * @param conn    数据库连接
	 * @param dbType  数据库类型
	 * @param dialect 数据方言
	 * @throws Exception 异常
	 */
	public abstract void doConnection(Connection conn, Integer dbType, String dialect) throws Exception;

	/**
	 * @return the result
	 */
	public Object getResult() {
		return result;
	}

	/**
	 * @param result the result to set
	 */
	public void setResult(Object result) {
		this.result = result;
	}

}
