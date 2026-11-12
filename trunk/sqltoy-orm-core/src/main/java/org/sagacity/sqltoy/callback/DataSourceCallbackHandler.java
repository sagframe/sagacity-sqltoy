package org.sagacity.sqltoy.callback;

import java.sql.Connection;

import org.sagacity.sqltoy.model.DBProfile;

/**
 * @project sagacity-sqltoy
 * @description 数据库连接反调,通过反调传递connection,并通过Result进行数据交互
 * @author zhongxuchen
 * @version v1.0,Date:2012-06-10
 */
public abstract class DataSourceCallbackHandler {
	/**
	 * 结果集
	 */
	private Object result = null;

	/**
	 * 基于给定的连接需要实现的方法
	 * 
	 * @param conn    数据库连接
	 * @param profile 有效执行数据库档案(含dbType/dialect及真实连接事实)
	 * @throws Exception 异常
	 */
	public abstract void doConnection(Connection conn, DBProfile profile) throws Exception;

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
