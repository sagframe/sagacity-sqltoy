/**
 * 
 */
package org.sagacity.sqltoy.utils;

import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.SQLException;

import org.junit.jupiter.api.Test;
import org.junit.runner.RunWith;
import org.junit.runners.Parameterized;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * @author zhong
 *
 */
// @RunWith(Parameterized.class)
public class DataSourceUtilTest {
	/**
	 * 定义日志
	 */
	private final static Logger logger = LoggerFactory.getLogger(DataSourceUtilTest.class);

	/**
	 * 获取数据库连接
	 * 
	 * @param driver
	 * @param url
	 * @param username
	 * @param password
	 * @return
	 */
	public static Connection getConnection(String driver, String url, String username, String password) {
		logger.info("获取数据库连接!");
		Connection conn = null;
		try {
			Class.forName(driver);
			conn = DriverManager.getConnection(url, username, password);
		} catch (ClassNotFoundException connectionException) {
			System.out.println(connectionException.getMessage());
		} catch (SQLException connectionException) {
			System.out.println(connectionException.getMessage());
			logger.error("获取数据库失败!" + connectionException.getStackTrace());
		}
		return conn;
	}

	// @Test
	public static void getDBDialect() {
		String driver = "ru.yandex.clickhouse.ClickHouseDriver";
		String url = "jdbc:clickhouse://192.168.56.107:8123/sagframe";
		String user = "default";
		String password = "SagFrame@123";
		Connection conn = getConnection(driver, url, user, password);
		try {
			System.err.println(conn.getMetaData().getDatabaseProductName());

			System.err.println(conn.getMetaData().getDatabaseMajorVersion());
			System.err.println(conn.getMetaData().getDatabaseMinorVersion());
		} catch (SQLException e) {
			// TODO Auto-generated catch block
			e.printStackTrace();
		}
	}

	public static void main(String[] args) {
		DataSourceUtilTest.getDBDialect();
	}
}
