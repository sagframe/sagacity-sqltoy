/**
 * 
 */
package org.sagacity.sqltoy.utils;

import java.sql.Connection;
import java.sql.SQLException;
import java.util.HashMap;

import javax.sql.DataSource;

import org.sagacity.sqltoy.SqlToyConstants;
import org.sagacity.sqltoy.SqlToyContext;
import org.sagacity.sqltoy.callback.DataSourceCallbackHandler;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * @project sagacity-sqltoy
 * @description 提供统一的dataSource管理
 * @author chenrenfei <a href="mailto:zhongxuchen@gmail.com">联系作者</a>
 * @version id:DataSourceUtils.java,Revision:v1.0,Date:2015年3月3日
 * @modify data:2020-06-10
 *         剔除sqlserver2012,sap_hana,增加tidb、guassdb、oceanbase、dm数据库方言的支持
 */
public class DataSourceUtils {
	/**
	 * 定义日志
	 */
	protected final static Logger logger = LoggerFactory.getLogger(DataSourceUtils.class);

	/**
	 * 数据库方言定义
	 */
	public static final class Dialect {
		// oracle12c+
		public final static String ORACLE = "oracle";

		// oracle11g
		public final static String ORACLE11 = "oracle11";
		// 10.x
		public final static String DB2 = "db2";

		// sqlserver2012或以上版本
		public final static String SQLSERVER = "sqlserver";

		// mysql的三个变种，5.6版本或以上
		public final static String MYSQL = "mysql";
		public final static String MYSQL57 = "mysql57";
		public final static String INNOSQL = "innosql";
		public final static String MARIADB = "mariadb";

		// 9.5+ 开始
		public final static String POSTGRESQL = "postgresql";

		// 华为gaussdb(源于postgresql)未验证
		public final static String GAUSSDB = "gaussdb";

		// 3.0以上版本
		public final static String SQLITE = "sqlite";

		// mongodb
		public final static String MONGO = "mongo";

		// elasticsearch
		public final static String ES = "elastic";

		// 19.x版本
		public final static String CLICKHOUSE = "clickhouse";

		// 阿里 oceanbase(未验证)
		public final static String OCEANBASE = "oceanbase";

		// tidb(语法遵循mysql)未验证
		public final static String TIDB = "tidb";

		// 达梦数据库(dm8验证)
		public final static String DM = "dm";

		// 以15.4为基准起始版(基本目前没有用户)
		public final static String SYBASE_IQ = "sybase_iq";

		public final static String UNDEFINE = "UNDEFINE";
	}

	/*
	 * 数据库类型数字标识
	 */
	public static final class DBType {
		// 未定义未识别
		public final static int UNDEFINE = 0;
		// 12c+
		public final static int ORACLE = 10;
		// 11g
		public final static int ORACLE11 = 11;
		// 10.x版本
		public final static int DB2 = 20;
		// 2017及以上版本
		public final static int SQLSERVER = 30;
		public final static int MYSQL = 40;
		public final static int MYSQL57 = 42;

		// 默认9.5+版本
		public final static int POSTGRESQL = 50;

		// clickhouse
		public final static int CLICKHOUSE = 60;

		// gaussdb
		public final static int GAUSSDB = 70;
		// sqlite
		public final static int SQLITE = 80;
		// tidb
		public final static int TIDB = 90;
		// 阿里oceanbase
		public final static int OCEANBASE = 100;
		// 达梦
		public final static int DM = 110;
		public final static int MONGO = 120;
		public final static int ES = 130;

		// 下面将逐步淘汰
		public final static int SYBASE_IQ = 140;
	}

	public static HashMap<String, Integer> DBNameTypeMap = new HashMap<String, Integer>();
	static {
		DBNameTypeMap.put(Dialect.DB2, DBType.DB2);
		DBNameTypeMap.put(Dialect.ORACLE, DBType.ORACLE);
		DBNameTypeMap.put(Dialect.ORACLE11, DBType.ORACLE11);
		DBNameTypeMap.put(Dialect.SQLSERVER, DBType.SQLSERVER);
		DBNameTypeMap.put(Dialect.MYSQL, DBType.MYSQL);
		DBNameTypeMap.put(Dialect.MYSQL57, DBType.MYSQL57);
		// mariaDB的方言以mysql为基准
		DBNameTypeMap.put(Dialect.MARIADB, DBType.MYSQL);
		DBNameTypeMap.put(Dialect.INNOSQL, DBType.MYSQL);

		DBNameTypeMap.put(Dialect.POSTGRESQL, DBType.POSTGRESQL);
		DBNameTypeMap.put(Dialect.GAUSSDB, DBType.GAUSSDB);

		DBNameTypeMap.put(Dialect.MONGO, DBType.MONGO);
		DBNameTypeMap.put(Dialect.ES, DBType.ES);
		DBNameTypeMap.put(Dialect.SQLITE, DBType.SQLITE);
		DBNameTypeMap.put(Dialect.CLICKHOUSE, DBType.CLICKHOUSE);
		DBNameTypeMap.put(Dialect.OCEANBASE, DBType.OCEANBASE);
		// 2020-6-5 增加对达梦数据库的支持
		DBNameTypeMap.put(Dialect.DM, DBType.DM);
		// 2020-6-7 启动增加对tidb的支持
		DBNameTypeMap.put(Dialect.TIDB, DBType.TIDB);

		DBNameTypeMap.put(Dialect.UNDEFINE, DBType.UNDEFINE);
		// 纳入将不再支持范围
		DBNameTypeMap.put(Dialect.SYBASE_IQ, DBType.SYBASE_IQ);
	}

	/**
	 * @todo 获取数据库类型名称
	 * @param dbType
	 * @return
	 */
	public static String getDialect(Integer dbType) {
		switch (dbType) {
		case DBType.MYSQL:
			return Dialect.MYSQL;
		case DBType.MYSQL57:
			return Dialect.MYSQL57;
		case DBType.ORACLE:
			return Dialect.ORACLE;
		case DBType.POSTGRESQL:
			return Dialect.POSTGRESQL;
		case DBType.SQLSERVER:
			return Dialect.SQLSERVER;
		case DBType.DB2:
			return Dialect.DB2;
		case DBType.OCEANBASE:
			return Dialect.OCEANBASE;
		case DBType.GAUSSDB:
			return Dialect.GAUSSDB;
		case DBType.CLICKHOUSE:
			return Dialect.CLICKHOUSE;
		case DBType.SQLITE:
			return Dialect.SQLITE;
		case DBType.TIDB:
			return Dialect.TIDB;
		case DBType.DM:
			return Dialect.DM;
		case DBType.ORACLE11:
			return Dialect.ORACLE11;
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
			if (dbType == DBType.SQLSERVER || dbType == DBType.SYBASE_IQ) {
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
			// 剔除空白
			String dbDialect = conn.getMetaData().getDatabaseProductName().replaceAll("\\s*", "");
			// oracle
			if (StringUtil.indexOfIgnoreCase(dbDialect, Dialect.ORACLE) != -1) {
				return Dialect.ORACLE;
			}
			// mysql以及mysql的分支数据库
			if (StringUtil.indexOfIgnoreCase(dbDialect, Dialect.MYSQL) != -1
					|| StringUtil.indexOfIgnoreCase(dbDialect, Dialect.MARIADB) != -1
					|| StringUtil.indexOfIgnoreCase(dbDialect, Dialect.INNOSQL) != -1) {
				return Dialect.MYSQL;
			}
			// postgresql
			if (StringUtil.indexOfIgnoreCase(dbDialect, Dialect.POSTGRESQL) != -1) {
				return Dialect.POSTGRESQL;
			}
			// sqlserver,只支持2012或以上版本
			if (StringUtil.indexOfIgnoreCase(dbDialect, Dialect.SQLSERVER) != -1
					|| StringUtil.indexOfIgnoreCase(dbDialect, "mssql") != -1
					|| StringUtil.indexOfIgnoreCase(dbDialect, "microsoftsqlserver") != -1) {
				return Dialect.SQLSERVER;
			}
			// db2
			if (StringUtil.indexOfIgnoreCase(dbDialect, Dialect.DB2) != -1) {
				return Dialect.DB2;
			}
			// clickhouse
			if (StringUtil.indexOfIgnoreCase(dbDialect, Dialect.CLICKHOUSE) != -1) {
				return Dialect.CLICKHOUSE;
			}
			// OCEANBASE
			if (StringUtil.indexOfIgnoreCase(dbDialect, Dialect.OCEANBASE) != -1) {
				return Dialect.OCEANBASE;
			}
			// GAUSSDB
			if (StringUtil.indexOfIgnoreCase(dbDialect, Dialect.GAUSSDB) != -1) {
				return Dialect.GAUSSDB;
			}
			// sqlite
			if (StringUtil.indexOfIgnoreCase(dbDialect, Dialect.SQLITE) != -1) {
				return Dialect.SQLITE;
			} // dm
			if (StringUtil.indexOfIgnoreCase(dbDialect, Dialect.DM) != -1) {
				return Dialect.DM;
			} // TIDB
			if (StringUtil.indexOfIgnoreCase(dbDialect, Dialect.TIDB) != -1) {
				return Dialect.TIDB;
			}
			// sybase iq
			if (StringUtil.indexOfIgnoreCase(dbDialect, Dialect.SYBASE_IQ) != -1
					|| StringUtil.indexOfIgnoreCase(dbDialect, "sybaseiq") != -1
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
			// e.printStackTrace();
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
			if (dbDialect.equals(Dialect.ORACLE)) {
				dbType = DBType.ORACLE;
				if (majorVersion <= 11) {
					dbType = DBType.ORACLE11;
				}
			}
			// mysql以及mysql的分支数据库
			else if (dbDialect.equals(Dialect.MYSQL)) {
				dbType = DBType.MYSQL;
				if (majorVersion <= 5) {
					dbType = DBType.MYSQL57;
				}
			}
			// 9.5以上为标准支持模式
			else if (dbDialect.equals(Dialect.POSTGRESQL)) {
				dbType = DBType.POSTGRESQL;
			}
			// sqlserver,只支持2012或以上版本
			else if (dbDialect.equals(Dialect.SQLSERVER)) {
				// 2014+
				dbType = DBType.SQLSERVER;
			}
			// db2 10+版本
			else if (dbDialect.equals(Dialect.DB2)) {
				dbType = DBType.DB2;
			}
			// CLICKHOUSE
			else if (dbDialect.equals(Dialect.CLICKHOUSE)) {
				dbType = DBType.CLICKHOUSE;
			}
			// OCEANBASE
			else if (dbDialect.equals(Dialect.OCEANBASE)) {
				dbType = DBType.OCEANBASE;
			}
			// GAUSSDB
			else if (dbDialect.equals(Dialect.GAUSSDB)) {
				dbType = DBType.GAUSSDB;
			}
			// sqlite
			else if (dbDialect.equals(Dialect.SQLITE)) {
				dbType = DBType.SQLITE;
			} // dm
			else if (dbDialect.equals(Dialect.DM)) {
				dbType = DBType.DM;
			} // TIDB
			else if (dbDialect.equals(Dialect.TIDB)) {
				dbType = DBType.TIDB;
			} // sybase IQ
			else if (dbDialect.equals(Dialect.SYBASE_IQ)) {
				dbType = DBType.SYBASE_IQ;
			}
			DBNameTypeMap.put(dbKey, dbType);
		}
		return DBNameTypeMap.get(dbKey);
	}

	/**
	 * @TODO 这里的方言已经在SqlToyContext中已经做了规整(因此不会超出范围)
	 * @param dialect
	 * @return
	 */
	public static int getDBType(String dialect) {
		if (StringUtil.isBlank(dialect)) {
			return DBType.UNDEFINE;
		}
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
		case DBType.DB2: {
			return "select 1 from sysibm.sysdummy1";
		}
		case DBType.ORACLE:
		case DBType.OCEANBASE:
		case DBType.DM:
		case DBType.ORACLE11: {
			return "select 1 from dual";
		}
		case DBType.POSTGRESQL:
		case DBType.GAUSSDB: {
			return "select version()";
		}
		// mysql、tidb、sqlserver等
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
			if (SqlToyConstants.showDatasourceInfo()) {
				logger.debug("db.dialect={};conn.url={};schema={};catalog={}", dialect, conn.getMetaData().getURL(),
						conn.getSchema(), conn.getCatalog());
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
