package org.sagacity.sqltoy.integration.impl;

import java.sql.Connection;

import javax.sql.DataSource;

import org.sagacity.sqltoy.integration.ConnectionFactory;
import org.springframework.jdbc.datasource.DataSourceUtils;

/**
 * 
 * @project sagacity-sqltoy
 * @description 提供基于spring的connection获取和释放实现
 * @author zhongxuchen
 * @version v1.0, Date:2022年6月14日
 * @modify 2022年6月14日,修改说明
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
	 * sqltoy只是获得connection进行sql处理，conn的关闭和commit都交spring事务处理
	 */
	@Override
	public void releaseConnection(Connection conn, DataSource dataSource) {
		DataSourceUtils.releaseConnection(conn, dataSource);
	}

}
