package org.sagacity.sqltoy.plugins.connection.impl;

import java.sql.Connection;

import javax.sql.DataSource;

import org.sagacity.sqltoy.plugins.connection.ConnectionFactory;
import org.springframework.jdbc.datasource.DataSourceUtils;

/**
 * @project sagacity-sqltoy
 * @description 提供Connection获取和释放的扩展接口默认实现
 * @author zhongxuchen
 */
public class DefaultConnectionFactory implements ConnectionFactory {

	@Override
	public Connection getConnection(DataSource dataSource) {
		return DataSourceUtils.getConnection(dataSource);
	}

	@Override
	public void releaseConnection(Connection conn, DataSource dataSource) {
		DataSourceUtils.releaseConnection(conn, dataSource);
	}

}
