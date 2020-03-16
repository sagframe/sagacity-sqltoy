/**
 * 
 */
package org.sagacity.sqltoy.utils;

import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.ResultSet;
import java.sql.SQLException;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * @author zhong
 *
 */
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
		String[] types = new String[] { "TABLE" };
		String tableName;
		try {
			ResultSet rs = conn.getMetaData().getPrimaryKeys("default", null, "TRADE_ORDER_INFO_3");
			while (rs.next()) {
				System.err.println("pk=" + rs.getString("COLUMN_NAME"));
			}
			rs = conn.getMetaData().getColumns("default", null, "TRADE_ORDER_INFO_3", null);
			while (rs.next()) {
				System.err.println("columnName=" + rs.getString("COLUMN_NAME"));
				System.err.println("默认值" + rs.getString("COLUMN_DEF"));
				System.err.println("备注:=" + rs.getString("REMARKS"));
				System.err.println(rs.getInt("DATA_TYPE"));
				System.err.println(rs.getString("TYPE_NAME"));
				System.err.println(rs.getInt("COLUMN_SIZE"));
				System.err.println(rs.getInt("DECIMAL_DIGITS"));
				System.err.println(rs.getInt("NUM_PREC_RADIX"));
				System.err.println(rs.getInt("NULLABLE"));
			}
		} catch (SQLException e) {
			// TODO Auto-generated catch block
			e.printStackTrace();
		}
	}

	public static void main(String[] args) {
		DataSourceUtilTest.getDBDialect();
	}
}
