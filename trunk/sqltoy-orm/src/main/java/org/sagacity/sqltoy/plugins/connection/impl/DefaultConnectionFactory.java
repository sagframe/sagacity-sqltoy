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
	/**
	 * 此处是sqltoy跟数据源唯一有关联的地方，其他地方只负责将dataSource传递过来
	 */
	@Override
	public Connection getConnection(DataSource dataSource) {
		return DataSourceUtils.getConnection(dataSource);
	}

	/**
	 * sqltoy只是获得connection进行sql处理，conn的关闭和commit都
	 */
	@Override
	public void releaseConnection(Connection conn, DataSource dataSource) {
		DataSourceUtils.releaseConnection(conn, dataSource);
	}

}
