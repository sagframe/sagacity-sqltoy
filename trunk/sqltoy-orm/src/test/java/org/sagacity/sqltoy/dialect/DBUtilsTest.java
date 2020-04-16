/**
 * 
 */
package org.sagacity.sqltoy.dialect;

import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.SQLException;
import java.util.regex.Pattern;

import org.sagacity.sqltoy.utils.StringUtil;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * @project sagacity-sqltoy4.0
 * @description 请在此说明类的功能
 * @author chenrenfei <a href="mailto:zhongxuchen@gmail.com">联系作者</a>
 * @version id:DBUtils.java,Revision:v1.0,Date:2017年12月9日
 */
public class DBUtilsTest {
	/**
	 * 定义日志
	 */
	private final static Logger logger = LoggerFactory.getLogger(DBUtilsTest.class);

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

	/**
	 * @todo 去除掉sql中的所有对称的select 和 from 中的内容，排除干扰
	 * @param sql
	 * @return
	 */
	public static String clearSymSelectFromSql(String sql) {
		StringBuilder lastSql = new StringBuilder(sql);
		String SELECT_REGEX = "(?i)\\Wselect\\s+";
		String FROM_REGEX = "(?i)\\sfrom[\\(|\\s+]";
		// 删除所有对称的括号中的内容
		int start = StringUtil.matchIndex(sql, SELECT_REGEX);
		int symMarkEnd;
		while (start != -1) {
			symMarkEnd = StringUtil.getSymMarkMatchIndex(SELECT_REGEX, FROM_REGEX, lastSql.toString(), start);
			if (symMarkEnd != -1) {
				lastSql.delete(start + 1, symMarkEnd + 5);
				start = StringUtil.matchIndex(lastSql.toString(), SELECT_REGEX);
			} else
				break;
		}
		return lastSql.toString();
	}

	public static void main(String[] args) {
		Pattern PARAM_NAME_PATTERN = Pattern.compile("\\W\\:\\s*\\d*[a-z|A-Z]+\\w+(\\.\\w+)*\\s*");
		// Pattern NAME_PATTERN =
		// Pattern.compile("\\W\\:\\s*((\\d*[a-z|A-Z]+\\d*)+[\\.|\\_]?(\\d*[a-z|A-Z]+\\d*)?)\\s*");
		Boolean result = StringUtil.matches("1990-10-05 23:10:45", PARAM_NAME_PATTERN);
		System.err.println(result);
		Boolean result1 = StringUtil.matches(" :1begin", PARAM_NAME_PATTERN);
		System.err.println(result1);
		Boolean result2 = StringUtil.matches(" :be23gin", PARAM_NAME_PATTERN);
		System.err.println(result2);
		String SELECT_REGEX = "select\\s+";
		String FROM_REGEX = "\\s+from[\\(|\\s+]";
		String sql = "select (select a    from table) as col,col2,(select b from table1) as col3 from tableA";
		int sql_from_index = StringUtil.getSymMarkMatchIndex(SELECT_REGEX, FROM_REGEX, sql.toLowerCase(), 0);
		int selectIndex = StringUtil.matchIndex(sql, SELECT_REGEX);
		String selectFields = (sql_from_index < 1) ? "" : sql.substring(selectIndex + 6, sql_from_index).toLowerCase();
		System.err.println("1=" + clearSymSelectFromSql(selectFields));
	}
}
