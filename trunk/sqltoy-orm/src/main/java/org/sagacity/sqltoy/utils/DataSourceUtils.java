/**
 * 
 */
package org.sagacity.sqltoy.utils;

import java.sql.Connection;
import java.sql.SQLException;
import java.util.HashMap;

import javax.sql.DataSource;

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.sagacity.sqltoy.SqlToyContext;
import org.sagacity.sqltoy.callback.DataSourceCallbackHandler;

/**
 * @project sagacity-sqltoy
 * @description 提供统一的dataSource管理
 * @author chenrenfei <a href="mailto:zhongxuchen@gmail.com">联系作者</a>
 * @version id:DataSourceUtils.java,Revision:v1.0,Date:2015年3月3日
 */
public class DataSourceUtils {
	/**
	 * 定义日志
	 */
	protected final static Logger logger = LogManager.getLogger(DataSourceUtils.class);

	/**
	 * 数据库方言定义
	 */
	public static final class Dialect {
		// oracle10、11g(11g为主体)
		public final static String ORACLE = "oracle";

		// oracle12c或以上版本
		public final static String ORACLE12 = "oracle12";
		// 10.x
		public final static String DB2 = "db2";
		// 11+ 版本
		public final static String DB2_11 = "db2_11";

		// sqlserver建议采用2012或以上版本
		// 2005版本或以上版本,以2008为支持主体
		public final static String SQLSERVER = "sqlserver";
		public final static String SQLSERVER2014 = "sqlserver2014";
		public final static String SQLSERVER2016 = "sqlserver2016";
		public final static String SQLSERVER2017 = "sqlserver2017";
		public final static String SQLSERVER2019 = "sqlserver2019";

		// mysql的三个变种，5.6版本或以上
		public final static String MYSQL = "mysql";
		public final static String MYSQL8 = "mysql8";
		public final static String INNOSQL = "innosql";
		public final static String MARIADB = "mariadb";

		public final static String POSTGRESQL = "postgresql";
		public final static String POSTGRESQL10 = "postgresql10";
		public final static String POSTGRESQL11 = "postgresql11";

		// 华为gaussdb(源于postgresql)
		public final static String GAUSSDB = "gaussdb";

		// 以15.4为基准起始版
		public final static String SYBASE_IQ = "sybase_iq";

		// 暂不支持
		public final static String SAP_HANA = "hana";

		// 未充分验证
		public final static String SQLITE = "sqlite";

		public final static String MONGO = "mongo";
		public final static String ES = "elastic";
		public final static String UNDEFINE = "UNDEFINE";
	}

	/*
	 * 数据库类型数字标识
	 */
	public static final class DBType {
		// 通常的通用的
		public final static int UNDEFINE = 0;
		// 10g,11g
		public final static int ORACLE = 10;
		// 支持12c
		public final static int ORACLE12 = 12;
		// 10.x版本
		public final static int DB2 = 20;
		public final static int DB2_11 = 21;
		// 2012及以上版本
		public final static int SQLSERVER = 30;
		public final static int SQLSERVER2014 = 32;
		public final static int SQLSERVER2016 = 33;
		public final static int SQLSERVER2017 = 34;
		public final static int SQLSERVER2019 = 35;
		public final static int MYSQL = 40;
		public final static int MYSQL8 = 42;

		public final static int SAP_HANA = 50;
		// 默认9.5+版本
		public final static int POSTGRESQL = 60;
		public final static int POSTGRESQL10 = 61;
		public final static int POSTGRESQL11 = 62;

		// gaussdb
		public final static int GAUSSDB = 70;

		public final static int SYBASE_IQ = 80;
		public final static int SQLITE = 90;

		public final static int MONGO = 110;
		public final static int ES = 120;
	}

	public static HashMap<String, Integer> DBNameTypeMap = new HashMap<String, Integer>();
	static {
		DBNameTypeMap.put(Dialect.DB2, DBType.DB2);
		DBNameTypeMap.put(Dialect.DB2_11, DBType.DB2_11);
		DBNameTypeMap.put(Dialect.ORACLE, DBType.ORACLE);
		DBNameTypeMap.put(Dialect.ORACLE12, DBType.ORACLE12);
		DBNameTypeMap.put(Dialect.SQLSERVER, DBType.SQLSERVER);
		DBNameTypeMap.put(Dialect.SQLSERVER2014, DBType.SQLSERVER2014);
		DBNameTypeMap.put(Dialect.SQLSERVER2016, DBType.SQLSERVER2016);
		DBNameTypeMap.put(Dialect.SQLSERVER2017, DBType.SQLSERVER2017);
		DBNameTypeMap.put(Dialect.SQLSERVER2019, DBType.SQLSERVER2019);
		DBNameTypeMap.put(Dialect.MYSQL, DBType.MYSQL);
		DBNameTypeMap.put(Dialect.MYSQL8, DBType.MYSQL8);
		// mariaDB的方言以mysql为基准
		DBNameTypeMap.put(Dialect.MARIADB, DBType.MYSQL);
		DBNameTypeMap.put(Dialect.INNOSQL, DBType.MYSQL);

		DBNameTypeMap.put(Dialect.POSTGRESQL, DBType.POSTGRESQL);
		DBNameTypeMap.put(Dialect.POSTGRESQL10, DBType.POSTGRESQL10);
		DBNameTypeMap.put(Dialect.POSTGRESQL11, DBType.POSTGRESQL11);

		DBNameTypeMap.put(Dialect.GAUSSDB, DBType.GAUSSDB);
		DBNameTypeMap.put(Dialect.SYBASE_IQ, DBType.SYBASE_IQ);
		DBNameTypeMap.put(Dialect.SAP_HANA, DBType.SAP_HANA);
		DBNameTypeMap.put(Dialect.UNDEFINE, DBType.UNDEFINE);
		DBNameTypeMap.put(Dialect.MONGO, DBType.MONGO);
		DBNameTypeMap.put(Dialect.ES, DBType.ES);
	}

	public static String getDialect(Integer dbType) {
		switch (dbType) {
		case DBType.DB2:
			return Dialect.DB2;
		case DBType.MYSQL:
			return Dialect.MYSQL;
		case DBType.MYSQL8:
			return Dialect.MYSQL8;
		case DBType.ORACLE:
			return Dialect.ORACLE;
		case DBType.ORACLE12:
			return Dialect.ORACLE12;
		case DBType.POSTGRESQL:
			return Dialect.POSTGRESQL;
		case DBType.POSTGRESQL10:
			return Dialect.POSTGRESQL10;
		case DBType.POSTGRESQL11:
			return Dialect.POSTGRESQL11;
		case DBType.SQLITE:
			return Dialect.SQLITE;
		case DBType.SQLSERVER:
			return Dialect.SQLSERVER;
		case DBType.SQLSERVER2014:
			return Dialect.SQLSERVER2014;
		case DBType.SQLSERVER2016:
			return Dialect.SQLSERVER2016;
		case DBType.SQLSERVER2017:
			return Dialect.SQLSERVER2017;
		case DBType.SQLSERVER2019:
			return Dialect.SQLSERVER2019;
		case DBType.GAUSSDB:
			return Dialect.GAUSSDB;
		case DBType.SAP_HANA:
			return Dialect.SAP_HANA;
		case DBType.ES:
			return Dialect.ES;
		case DBType.MONGO:
			return Dialect.MONGO;
		case DBType.SYBASE_IQ:
			return Dialect.SYBASE_IQ;
		default:
			return Dialect.UNDEFINE;
		}
	}

	/**
	 * @todo <b>获取数据库批量sql语句的分割符号</b>
	 * @param conn
	 * @return
	 */
	public static String getDatabaseSqlSplitSign(Connection conn) {
		try {
			int dbType = getDbType(conn);
			// sybase or sqlserver
			if (dbType == DBType.SYBASE_IQ || dbType == DBType.SQLSERVER || dbType == DBType.SQLSERVER2017
					|| dbType == DBType.SQLSERVER2014 || dbType == DBType.SQLSERVER2016
					|| dbType == DBType.SQLSERVER2019) {
				return " go ";
			}
		} catch (Exception e) {
			e.printStackTrace();
		}
		return ";";
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
			if (StringUtil.indexOfIgnoreCase(dbDialect, Dialect.ORACLE) != -1) {
				return Dialect.ORACLE;
			}
			// db2
			else if (StringUtil.indexOfIgnoreCase(dbDialect, Dialect.DB2) != -1) {
				return Dialect.DB2;
			}
			// sqlserver,只支持2000或以上版本
			else if (StringUtil.indexOfIgnoreCase(dbDialect, Dialect.SQLSERVER) != -1
					|| StringUtil.indexOfIgnoreCase(dbDialect, "Microsoft SQL Server") != -1) {
				return Dialect.SQLSERVER;
			}
			// mysql以及mysql的分支数据库
			else if (StringUtil.indexOfIgnoreCase(dbDialect, Dialect.MYSQL) != -1
					|| StringUtil.indexOfIgnoreCase(dbDialect, Dialect.MARIADB) != -1
					|| StringUtil.indexOfIgnoreCase(dbDialect, Dialect.INNOSQL) != -1) {
				return Dialect.MYSQL;
			}
			// sqlite
			else if (StringUtil.indexOfIgnoreCase(dbDialect, Dialect.SQLITE) != -1) {
				return Dialect.SQLITE;
			}
			// postgresql
			else if (StringUtil.indexOfIgnoreCase(dbDialect, Dialect.POSTGRESQL) != -1) {
				return Dialect.POSTGRESQL;
			} // GAUSSDB
			else if (StringUtil.indexOfIgnoreCase(dbDialect, Dialect.GAUSSDB) != -1) {
				return Dialect.GAUSSDB;
			}
			// hana
			else if (StringUtil.indexOfIgnoreCase(dbDialect, Dialect.SAP_HANA) != -1) {
				return Dialect.SAP_HANA;
			}
			// sybase iq
			else if (StringUtil.indexOfIgnoreCase(dbDialect, Dialect.SYBASE_IQ) != -1
					|| StringUtil.indexOfIgnoreCase(dbDialect, "Sybase IQ") != -1
					|| (StringUtil.indexOfIgnoreCase(dbDialect, "sap") != -1
							&& StringUtil.indexOfIgnoreCase(dbDialect, "iq") != -1)) {
				return Dialect.SYBASE_IQ;
			}
		}
		return Dialect.UNDEFINE;
	}

	/**
	 * @todo 获取当前数据库的版本
	 * @return
	 * @throws SQLException
	 */
	private static int getDBVersion(final Connection conn) throws SQLException {
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
	 * @param conn
	 * @return
	 * @throws SQLException
	 */
	public static int getDbType(final Connection conn) throws SQLException {
		// 从hashMap中获取
		String productName = conn.getMetaData().getDatabaseProductName();
		int majorVersion = getDBVersion(conn);
		String dbKey = productName + majorVersion;
		if (!DBNameTypeMap.containsKey(dbKey)) {
			String dbDialect = getCurrentDBDialect(conn);
			int dbType = DBType.UNDEFINE;
			// oracle
			if (StringUtil.indexOfIgnoreCase(dbDialect, Dialect.ORACLE) != -1) {
				dbType = DBType.ORACLE;
				if (majorVersion >= 12) {
					dbType = DBType.ORACLE12;
				}
			}
			// db2 10+版本
			else if (StringUtil.indexOfIgnoreCase(dbDialect, Dialect.DB2) != -1) {
				dbType = DBType.DB2;
				// 11+版本
				if (majorVersion >= 11) {
					dbType = DBType.DB2_11;
				}
			}
			// sqlserver,只支持2012或以上版本
			else if (StringUtil.indexOfIgnoreCase(dbDialect, Dialect.SQLSERVER) != -1
					|| StringUtil.indexOfIgnoreCase(dbDialect, "Microsoft SQL Server") != -1) {
				dbType = DBType.SQLSERVER;
				// 2012版本,2014版本也归为2012
				if (majorVersion >= 2019)
					dbType = DBType.SQLSERVER2019;
				else if (majorVersion >= 2017)
					dbType = DBType.SQLSERVER2017;
				else if (majorVersion >= 2016)
					dbType = DBType.SQLSERVER2016;
				else if (majorVersion >= 2014)
					dbType = DBType.SQLSERVER2014;
			}
			// mysql以及mysql的分支数据库
			else if (StringUtil.indexOfIgnoreCase(dbDialect, Dialect.MYSQL) != -1
					|| StringUtil.indexOfIgnoreCase(dbDialect, Dialect.MARIADB) != -1
					|| StringUtil.indexOfIgnoreCase(dbDialect, Dialect.INNOSQL) != -1) {
				dbType = DBType.MYSQL;
				if (majorVersion > 5)
					dbType = DBType.MYSQL8;
			}
			// 9.5以上为标准支持模式
			else if (StringUtil.indexOfIgnoreCase(dbDialect, Dialect.POSTGRESQL) != -1) {
				dbType = DBType.POSTGRESQL;
				// 10+版本
				if (majorVersion >= 11)
					dbType = DBType.POSTGRESQL11;
				else if (majorVersion >= 10)
					dbType = DBType.POSTGRESQL10;
			}
			// sybase IQ
			else if (StringUtil.indexOfIgnoreCase(dbDialect, Dialect.SYBASE_IQ) != -1
					|| StringUtil.indexOfIgnoreCase(dbDialect, "Sybase IQ") != -1) {
				dbType = DBType.SYBASE_IQ;
			} // GAUSSDB
			else if (StringUtil.indexOfIgnoreCase(dbDialect, Dialect.GAUSSDB) != -1) {
				dbType = DBType.GAUSSDB;
			}
			// sqlite
			else if (StringUtil.indexOfIgnoreCase(dbDialect, Dialect.SQLITE) != -1) {
				dbType = DBType.SQLITE;
			}
			DBNameTypeMap.put(dbKey, dbType);
		}
		return DBNameTypeMap.get(dbKey);
	}

	public static int getDBType(String dialect) {
		return DBNameTypeMap.get(dialect.toLowerCase());
	}

	/**
	 * @todo 获取不同数据库validator语句
	 * @param conn
	 * @return
	 * @throws Exception
	 */
	public static String getValidateQuery(final Connection conn) throws Exception {
		int dbType = getDbType(conn);
		switch (dbType) {
		case DBType.DB2:
			return "select 1 from sysibm.sysdummy1";
		case DBType.ORACLE:
		case DBType.ORACLE12:
			return "select 1 from dual";
		case DBType.POSTGRESQL:
		case DBType.POSTGRESQL10:
		case DBType.POSTGRESQL11:
		case DBType.GAUSSDB:
			return "select version()";
		default:
			return "select 1";
		}
	}

	/**
	 * @todo <b>统一处理DataSource以及对应的Connection，便于跟spring事务集成</b>
	 * @param sqltoyContext
	 * @param datasource
	 * @param handler
	 * @return
	 */
	public static Object processDataSource(SqlToyContext sqltoyContext, DataSource datasource,
			DataSourceCallbackHandler handler) {
		Connection conn = org.springframework.jdbc.datasource.DataSourceUtils.getConnection(datasource);
		Integer dbType;
		String dialect;
		try {
			// 统一提取数据库方言类型
			if (null != sqltoyContext && StringUtil.isNotBlank(sqltoyContext.getDialect())) {
				dialect = sqltoyContext.getDialect();
				dbType = getDBType(dialect);
			} else {
				dbType = getDbType(conn);
				dialect = getDialect(dbType);
			}
			// 调试显示数据库信息,便于在多数据库场景下辨别查询对应的数据库
			if (sqltoyContext.isDebug()) {
				try {
					logger.debug("db.dialect={};conn.url={};schema={};catalog={}", dialect, conn.getMetaData().getURL(),
							conn.getSchema(), conn.getCatalog());
				} catch (Exception e) {

				}
			}
			// 调用反调，传入conn和数据库类型进行实际业务处理(数据库类型主要便于DialectFactory获取对应方言处理类)
			handler.doConnection(conn, dbType, dialect);
		} catch (Exception e) {
			e.printStackTrace();
			org.springframework.jdbc.datasource.DataSourceUtils.releaseConnection(conn, datasource);
			conn = null;
			throw new RuntimeException(e);
		} finally {
			// 释放连接,连接池实际是归还连接，未必一定关闭
			org.springframework.jdbc.datasource.DataSourceUtils.releaseConnection(conn, datasource);
		}
		// 返回反调的结果
		return handler.getResult();
	}
}
