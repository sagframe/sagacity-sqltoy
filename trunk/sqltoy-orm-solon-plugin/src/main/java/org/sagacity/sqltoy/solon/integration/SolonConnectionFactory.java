package org.sagacity.sqltoy.solon.integration;

import java.sql.Connection;
import java.sql.SQLException;

import javax.sql.DataSource;

import org.noear.solon.data.tran.TranUtils;
import org.sagacity.sqltoy.integration.ConnectionFactory;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * 基于 Solon 事务工具(TranUtils)实现的数据库连接工厂，保证连接参与 Solon 事务
 *
 * @author noear
 * @since 5.6
 */
public class SolonConnectionFactory implements ConnectionFactory {
    private static final Logger logger = LoggerFactory.getLogger(SolonConnectionFactory.class);

    @Override
    public Connection getConnection(DataSource dataSource) {
        try {
            return TranUtils.getConnectionProxy(dataSource);
        } catch (SQLException e) {
            throw new RuntimeException(e);
        }
    }

    @Override
    public void releaseConnection(Connection connection, DataSource dataSource) {
        try {
            if (connection != null) {
                connection.close();
            }
        } catch (SQLException e) {
            logger.error("release connection failed!", e);
        }
    }
}
