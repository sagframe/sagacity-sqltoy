package org.sagacity.sqltoy.integration;

import java.sql.Connection;

import javax.sql.DataSource;

/**
 * @project sagacity-sqltoy
 * @description 提供Connection获取和释放的扩展接口定义
 * @author zhongxuchen
 * @version v1.0, Date:2022年6月14日
 * @modify 2022年6月14日,修改说明
 */
public interface ConnectionFactory {
	/**
	 * @TODO 获得连接
	 * @param dataSource
	 * @return
	 */
	public Connection getConnection(DataSource dataSource);

	/**
	 * @TODO 释放连接
	 * @param conn
	 * @param datasource
	 */
	public void releaseConnection(Connection conn, DataSource datasource);
}
