/**
 * 
 */
package org.sagacity.sqltoy.dialect;

import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.SQLException;

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

/**
 * @project sagacity-sqltoy4.0
 * @description
 *              <p>
 * 				请在此说明类的功能
 *              </p>
 * @author chenrenfei <a href="mailto:zhongxuchen@gmail.com">联系作者</a>
 * @version id:DBUtils.java,Revision:v1.0,Date:2017年12月9日
 */
public class DBUtils {
	/**
	 * 定义日志
	 */
	private final static Logger logger = LogManager.getLogger(DBUtils.class);
	
	/**
	 * url like:jdbc:sqlserver://localhost:1433;databasename=PMCenter_CCB
	 */
	public final static String DRIVER_SQLSERVER = "com.microsoft.sqlserver.jdbc.SQLServerDriver";

	/**
	 * url like:jdbc:mysql://localhost:3306/sagacity?useUnicode=true&
	 * characterEncoding=utf-8
	 */
	public final static String DRIVER_MYSQL = "com.mysql.jdbc.Driver";

	/**
	 * url like:jdbc:oracle:thin:@localhost:1521:sagacity
	 */
	public final static String DRIVER_ORACLE = "oracle.jdbc.driver.OracleDriver";

	/**
	 * url like:jdbc:db2://localhost:60004/apms
	 */
	public final static String DRIVER_DB2 = "com.ibm.db2.jcc.DB2Driver";

	// url like: jdbc:sybase:Tds:localhost:2638?ServiceName=iqdemo
	public final static String DRIVER_SYBASE_IQ = "com.sybase.jdbc4.jdbc.SybDriver";

	/**
	 * url like:jdbc:postgresql://host:port/database
	 */
	public final static String DRIVER_POSTGRESQL = "org.postgresql.Driver";

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
}
