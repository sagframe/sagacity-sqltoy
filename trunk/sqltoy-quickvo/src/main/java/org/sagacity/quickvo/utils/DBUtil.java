/**
 * @Copyright 2008 版权归陈仁飞，不要肆意侵权抄袭，如引用请注明出处保留作者信息。
 */
package org.sagacity.quickvo.utils;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.HashMap;
import java.util.logging.Logger;

import org.sagacity.quickvo.utils.callback.PreparedStatementResultHandler;

/**
 * @project sagacity-quickvo
 * @description 数据库连接工具类
 * @author chenrenfei $<a href="mailto:zhongxuchen@hotmail.com">联系作者</a>$
 * @version $id:DBUtil.java,Revision:v1.0,Date:Dec 24, 2008 3:52:05 PM $
 */
public class DBUtil {
	/**
	 * 定义日志
	 */
	private static Logger logger = LoggerUtil.getLogger();

	/**
	 * 数据库方言定义
	 */
	public static final class Dialect {
		// 基本不支持(用access为什么不用mysql呢?)
		public final static String ACCESS = "access";

		// oracle11g+
		public final static String ORACLE = "oracle";

		// 9.7为基础
		public final static String DB2 = "db2";

		// sqlserver建议采用2012或以上版本
		// 2005版本或以上版本,以2008为支持主体
		public final static String SQLSERVER = "sqlserver";

		// mysql的三个变种，5.6版本或以上
		public final static String MYSQL = "mysql";
		public final static String INNOSQL = "innosql";
		public final static String MARIADB = "mariadb";
		public final static String POSTGRESQL = "postgresql";

		public final static String CLICKHOUSE = "clickhouse";

		// 以15.4为基准起始版
		public final static String SYBASE_IQ = "sybase_iq";

		// 暂不支持
		public final static String SAP_HANA = "sap_hana";

		// 未充分验证
		public final static String SQLITE = "sqlite";
		public final static String UNDEFINE = "UNDEFINE";
	}

	/*
	 * 数据库类型数字标识
	 */
	public static final class DbType {
		// 通常的通用的
		public final static int UNDEFINE = 0;
		public final static int ACCESS = 1;
		public final static int ORACLE = 10;
		public final static int ORACLE12 = 12;
		public final static int DB2 = 20;
		// 不再支持sqlserver2000
		public final static int SQLSERVER = 30;
		public final static int MYSQL = 40;

		public final static int POSTGRESQL = 61;

		public final static int SYBASE_IQ = 80;
		public final static int SQLITE = 90;
		public final static int SAP_HANA = 100;
		public final static int CLICKHOUSE = 130;
	}

	public static HashMap<String, Integer> DBNameTypeMap = new HashMap<String, Integer>();
	static {
		DBNameTypeMap.put(Dialect.DB2, DbType.DB2);
		DBNameTypeMap.put(Dialect.ORACLE, DbType.ORACLE);
		DBNameTypeMap.put(Dialect.SQLSERVER, DbType.SQLSERVER);
		DBNameTypeMap.put(Dialect.MYSQL, DbType.MYSQL);
		// mariaDB的方言以mysql为基准
		DBNameTypeMap.put(Dialect.MARIADB, DbType.MYSQL);
		DBNameTypeMap.put(Dialect.INNOSQL, DbType.MYSQL);

		DBNameTypeMap.put(Dialect.POSTGRESQL, DbType.POSTGRESQL);
		DBNameTypeMap.put(Dialect.SYBASE_IQ, DbType.SYBASE_IQ);
		DBNameTypeMap.put(Dialect.SAP_HANA, DbType.SAP_HANA);
		DBNameTypeMap.put(Dialect.CLICKHOUSE, DbType.CLICKHOUSE);
		DBNameTypeMap.put(Dialect.UNDEFINE, DbType.UNDEFINE);
	}

	/**
	 * @todo 获取数据库类型
	 * @param conn
	 * @return
	 * @throws SQLException
	 */
	public static String getCurrentDBDialect(final Connection conn) throws SQLException {
		// 从hashMap中获取
		if (null != conn) {
			String dbDialect = conn.getMetaData().getDatabaseProductName();
			// oracle
			if (StringUtil.indexOfIgnoreCase(dbDialect, DBUtil.Dialect.ORACLE) != -1) {
				return DBUtil.Dialect.ORACLE;
			}
			// db2
			else if (StringUtil.indexOfIgnoreCase(dbDialect, DBUtil.Dialect.DB2) != -1) {
				return DBUtil.Dialect.DB2;
			}
			// sqlserver,只支持2000或以上版本
			else if (StringUtil.indexOfIgnoreCase(dbDialect, DBUtil.Dialect.SQLSERVER) != -1
					|| StringUtil.indexOfIgnoreCase(dbDialect, "Microsoft SQL Server") != -1) {
				return DBUtil.Dialect.SQLSERVER;
			}
			// mysql以及mysql的分支数据库
			else if (StringUtil.indexOfIgnoreCase(dbDialect, DBUtil.Dialect.MYSQL) != -1
					|| StringUtil.indexOfIgnoreCase(dbDialect, DBUtil.Dialect.MARIADB) != -1
					|| StringUtil.indexOfIgnoreCase(dbDialect, DBUtil.Dialect.INNOSQL) != -1) {
				return DBUtil.Dialect.MYSQL;
			}
			// sqlite
			else if (StringUtil.indexOfIgnoreCase(dbDialect, DBUtil.Dialect.SQLITE) != -1) {
				return DBUtil.Dialect.SQLITE;
			}
			// postgresql
			else if (StringUtil.indexOfIgnoreCase(dbDialect, DBUtil.Dialect.POSTGRESQL) != -1) {
				return DBUtil.Dialect.POSTGRESQL;
			} else if (StringUtil.indexOfIgnoreCase(dbDialect, DBUtil.Dialect.CLICKHOUSE) != -1) {
				return DBUtil.Dialect.CLICKHOUSE;
			}
			// sybase iq
			else if (StringUtil.indexOfIgnoreCase(dbDialect, DBUtil.Dialect.SYBASE_IQ) != -1
					|| StringUtil.indexOfIgnoreCase(dbDialect, "Sybase IQ") != -1
					|| (StringUtil.indexOfIgnoreCase(dbDialect, "sap") != -1
							&& StringUtil.indexOfIgnoreCase(dbDialect, "iq") != -1)) {
				return DBUtil.Dialect.SYBASE_IQ;
			}
		}
		return DBUtil.Dialect.UNDEFINE;
	}

	/**
	 * @todo 获取当前数据库的版本
	 * @return
	 */
	public static int getCurrentDBVersion(final Connection conn) {
		// -1表示版本不确定
		int result = -1;
		// 部分数据库驱动还不支持此方法
		try {
			result = conn.getMetaData().getDatabaseMajorVersion();
		} catch (Exception e) {
		}
		return result;
	}

	/**
	 * @todo <b>获取数据库类型</b>
	 * @author zhongxuchen
	 * @date 2011-8-3 下午06:25:41
	 * @param conn
	 * @return
	 * @throws SQLException
	 */
	public static int getDbType(final Connection conn) throws SQLException {
		// 从hashMap中获取
		String productName = conn.getMetaData().getDatabaseProductName();
		int majorVersion = getCurrentDBVersion(conn);
		String dbKey = productName + majorVersion;
		if (!DBNameTypeMap.containsKey(dbKey)) {
			String dbDialect = getCurrentDBDialect(conn);
			int dbType = DbType.UNDEFINE;
			// oracle
			if (StringUtil.indexOfIgnoreCase(dbDialect, Dialect.ORACLE) != -1) {
				dbType = DbType.ORACLE;
				if (majorVersion >= 12) {
					dbType = DbType.ORACLE12;
				}
			}
			// db2
			else if (StringUtil.indexOfIgnoreCase(dbDialect, Dialect.DB2) != -1) {
				dbType = DbType.DB2;
			}
			// sqlserver,只支持2008或以上版本
			else if (StringUtil.indexOfIgnoreCase(dbDialect, Dialect.SQLSERVER) != -1
					|| StringUtil.indexOfIgnoreCase(dbDialect, "Microsoft SQL Server") != -1) {
				dbType = DbType.SQLSERVER;
			}
			// mysql以及mysql的分支数据库
			else if (StringUtil.indexOfIgnoreCase(dbDialect, Dialect.MYSQL) != -1
					|| StringUtil.indexOfIgnoreCase(dbDialect, Dialect.MARIADB) != -1
					|| StringUtil.indexOfIgnoreCase(dbDialect, Dialect.INNOSQL) != -1) {
				dbType = DbType.MYSQL;
			}
			// postgresql
			else if (StringUtil.indexOfIgnoreCase(dbDialect, Dialect.POSTGRESQL) != -1) {
				dbType = DbType.POSTGRESQL;
			} // postgresql
			else if (StringUtil.indexOfIgnoreCase(dbDialect, Dialect.CLICKHOUSE) != -1) {
				dbType = DbType.CLICKHOUSE;
			}
			// sybase IQ
			else if (StringUtil.indexOfIgnoreCase(dbDialect, Dialect.SYBASE_IQ) != -1
					|| StringUtil.indexOfIgnoreCase(dbDialect, "Sybase IQ") != -1) {
				dbType = DbType.SYBASE_IQ;
			}
			// sqlite
			else if (StringUtil.indexOfIgnoreCase(dbDialect, Dialect.SQLITE) != -1) {
				dbType = DbType.SQLITE;
			}
			// access
			else if (StringUtil.indexOfIgnoreCase(dbDialect, Dialect.ACCESS) != -1) {
				dbType = DbType.ACCESS;
			}
			DBNameTypeMap.put(dbKey, dbType);
		}
		return DBNameTypeMap.get(dbKey);
	}

	/**
	 * @todo 提供统一的ResultSet,PreparedStatemenet 关闭功能
	 * @param userData
	 * @param pst
	 * @param rs
	 * @param preparedStatementResultHandler
	 * @return
	 */
	public static Object preparedStatementProcess(Object userData, PreparedStatement pst, ResultSet rs,
			PreparedStatementResultHandler preparedStatementResultHandler) throws Exception {
		try {
			preparedStatementResultHandler.execute(userData, pst, rs);
		} catch (Exception se) {
			se.printStackTrace();
			logger.info(se.getMessage());
			throw se;
		} finally {
			try {
				if (rs != null) {
					rs.close();
					rs = null;
				}
				if (pst != null) {
					pst.close();
					pst = null;
				}
			} catch (SQLException se) {
				se.printStackTrace();
			}
		}
		return preparedStatementResultHandler.getResult();
	}

}
