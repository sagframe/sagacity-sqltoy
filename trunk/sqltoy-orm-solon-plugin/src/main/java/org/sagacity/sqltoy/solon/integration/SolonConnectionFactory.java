package org.sagacity.sqltoy.solon.integration;

import java.sql.Connection;
import java.sql.SQLException;

import javax.sql.DataSource;

import org.noear.solon.data.tran.TranUtils;
import org.sagacity.sqltoy.integration.ConnectionFactory;

public class SolonConnectionFactory implements ConnectionFactory {
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
            e.printStackTrace();
        }
    }
}
