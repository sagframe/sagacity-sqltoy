package org.sagacity.sqltoy.integration.impl;

import java.sql.Connection;
import java.sql.SQLException;

import javax.sql.DataSource;

import org.sagacity.sqltoy.exception.DataAccessException;
import org.sagacity.sqltoy.integration.ConnectionFactory;
import org.sagacity.sqltoy.utils.DBTransUtils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * @project sagacity-sqltoy
 * @description 提供一个简单的不是spring、solon等框架场景下的连接获取处理
 * @author zhongxuchen
 * @version v1.0, Date:2024年4月20日
 * @modify 2024年4月20日,修改说明
 */
public class SimpleConnectionFactory implements ConnectionFactory {
	private final static Logger logger = LoggerFactory.getLogger(SimpleConnectionFactory.class);

	@Override
	public Connection getConnection(DataSource dataSource) {
		// 优先获取当前线程绑定的连接
		Connection conn = DBTransUtils.getCurrentConnection();
		if (conn == null && dataSource != null) {
			try {
				conn = dataSource.getConnection();
			} catch (SQLException e) {
				// 返回null会让后续所有jdbc操作以远离根因的NPE暴露,必须抛出保留根因
				throw new DataAccessException("获取数据库连接失败:" + e.getMessage(), e);
			}
		}
		return conn;
	}

	@Override
	public void releaseConnection(Connection conn, DataSource datasource) {
		// 当前线程的连接不为null,则不做处理，由事务统一最终关闭
		if (DBTransUtils.getCurrentConnection() != null) {
			return;
		}
		try {
			if (conn != null) {
				conn.close();
			}
		} catch (SQLException e) {
			logger.error("关闭数据库连接失败:{}", e.getMessage(), e);
		}
	}

}
