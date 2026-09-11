package org.sagacity.sqltoy.utils;

import java.sql.Connection;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.WeakHashMap;
import java.util.concurrent.ConcurrentHashMap;

import javax.sql.DataSource;

import org.sagacity.sqltoy.SqlToyConstants;
import org.sagacity.sqltoy.SqlToyContext;
import org.sagacity.sqltoy.SqlToyThreadDataHolder;
import org.sagacity.sqltoy.callback.DataSourceCallbackHandler;
import org.sagacity.sqltoy.config.model.CaseType;
import org.sagacity.sqltoy.model.DBProfile;
import org.sagacity.sqltoy.model.IgnoreKeyCaseMap;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * @author zhongxuchen
 * @version v1.0,Date:2015-03-03
 * @project sagacity-sqltoy
 * @description 提供统一的dataSource管理
 * @modify Date:2020-06-10 剔除mssql2008,hana,增加tidb、guassdb、oceanbase、dm数据库方言的支持
 * @modify Date:2022-08-29 增加h2数据库的支持
 * @modify Date:2022-09-29 getDialect(DataSource)和getDBType(DataSource)
 *         增加缓存机制，避免获取connection来判断
 */
public class DataSourceUtils {
	/**
	 * 定义日志
	 */
	protected final static Logger logger = LoggerFactory.getLogger(DataSourceUtils.class);

	private DataSourceUtils() {

	}

	// 存放数据库方言(dataSource.toString(),dialect)
	public static ConcurrentHashMap<String, String> DBDialectMap = new ConcurrentHashMap<String, String>();
	// 存放数据库方言类型(dataSource.toString(),dbType)
	public static ConcurrentHashMap<String, Integer> DBTypeMap = new ConcurrentHashMap<String, Integer>();
	public static ConcurrentHashMap<String, Integer> DBNameTypeMap = new ConcurrentHashMap<String, Integer>();
	public static IgnoreKeyCaseMap<String, String> dialectMap = new IgnoreKeyCaseMap<String, String>();

	// 以DataSource对象为key的dbType/方言缓存:身份语义避免hashCode碰撞互串,
	// weak引用使动态数据源被替换后旧条目可随GC回收,不再永久持有失效数据源
	private static final Map<DataSource, Integer> dataSourceDbTypeCache = Collections
			.synchronizedMap(new WeakHashMap<DataSource, Integer>());
	private static final Map<DataSource, String> dataSourceDialectCache = Collections
			.synchronizedMap(new WeakHashMap<DataSource, String>());

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
		public final static String MSSQL = "mssql";

		// mysql的三个变种，5.6版本或以上
		public final static String MYSQL = "mysql";
		public final static String MYSQL57 = "mysql57";
		public final static String INNOSQL = "innosql";
		public final static String MARIADB = "mariadb";
		public final static String DORIS = "doris";
		public final static String STARROCKS = "starrocks";

		// 9.5+ 开始
		public final static String POSTGRESQL = "postgresql";
		// public final static String POSTGRESQL15 = "postgresql15";
		public final static String POSTGRESQL14 = "postgresql14";
		public final static String GREENPLUM = "greenplum";
		// 神通数据库
		public final static String OSCAR = "oscar";

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

		// tidb(语法遵循mysql)
		public final static String TIDB = "tidb";

		// 达梦数据库(dm8验证)
		public final static String DM = "dm";

		// 人大金仓数据库
		public final static String KINGBASE = "kingbase";
		public final static String IMPALA = "impala";
		public final static String TDENGINE = "tdengine";

		// h2
		public final static String H2 = "h2";

		// mogdb
		public final static String MOGDB = "mogdb";
		// 海量数据库(opengauss)
		public final static String VASTBASE = "vastbase";
		public final static String OPENGAUSS = "opengauss";
		public final static String STARDB = "stardb";
		public final static String UNDEFINE = "undefine";
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
		public final static int DORIS = 46;
		public final static int STARROCKS = 47;

		// 默认15+版本(update 2026-7-30 将postgresql默认提升到15+版本，增加postgresql14 代替<=14的老版本)
		public final static int POSTGRESQL = 50;
		// public final static int POSTGRESQL15 = 51;
		public final static int POSTGRESQL14 = 52;

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

		// 人大金仓数据库
		public final static int KINGBASE = 120;
		public final static int MONGO = 130;
		public final static int ES = 140;
		public final static int TDENGINE = 150;
		public final static int IMPALA = 160;
		// h2
		public final static int H2 = 170;
		public final static int OSCAR = 180;

		// MOGDB 基于openGauss开发。
		public final static int MOGDB = 190;
		public final static int VASTBASE = 200;
		public final static int OPENGAUSS = 210;
		public final static int STARDB = 220;
	}

	static {
		initialize();
	}

	public static void initialize() {
		DBNameTypeMap.put(Dialect.DB2, DBType.DB2);
		DBNameTypeMap.put(Dialect.ORACLE, DBType.ORACLE);
		DBNameTypeMap.put(Dialect.ORACLE11, DBType.ORACLE11);
		DBNameTypeMap.put(Dialect.SQLSERVER, DBType.SQLSERVER);
		DBNameTypeMap.put(Dialect.MSSQL, DBType.SQLSERVER);

		DBNameTypeMap.put(Dialect.MYSQL, DBType.MYSQL);
		DBNameTypeMap.put(Dialect.MYSQL57, DBType.MYSQL57);
		// mariaDB的方言以mysql为基准
		DBNameTypeMap.put(Dialect.MARIADB, DBType.MYSQL);
		DBNameTypeMap.put(Dialect.INNOSQL, DBType.MYSQL);
		DBNameTypeMap.put(Dialect.POSTGRESQL, DBType.POSTGRESQL);
		// DBNameTypeMap.put(Dialect.POSTGRESQL15, DBType.POSTGRESQL15);
		DBNameTypeMap.put(Dialect.POSTGRESQL14, DBType.POSTGRESQL14);
		DBNameTypeMap.put(Dialect.GREENPLUM, DBType.POSTGRESQL);
		DBNameTypeMap.put(Dialect.GAUSSDB, DBType.GAUSSDB);
		// 20240702 增加对mogdb的支持
		DBNameTypeMap.put(Dialect.MOGDB, DBType.MOGDB);
		DBNameTypeMap.put(Dialect.OPENGAUSS, DBType.OPENGAUSS);
		DBNameTypeMap.put(Dialect.STARDB, DBType.STARDB);
		DBNameTypeMap.put(Dialect.MONGO, DBType.MONGO);
		DBNameTypeMap.put(Dialect.ES, DBType.ES);
		DBNameTypeMap.put(Dialect.SQLITE, DBType.SQLITE);
		DBNameTypeMap.put(Dialect.CLICKHOUSE, DBType.CLICKHOUSE);
		DBNameTypeMap.put(Dialect.OCEANBASE, DBType.OCEANBASE);
		// 2020-6-5 增加对达梦数据库的支持
		DBNameTypeMap.put(Dialect.DM, DBType.DM);
		// 2020-8-14 增加对人大金仓数据库支持
		DBNameTypeMap.put(Dialect.KINGBASE, DBType.KINGBASE);
		// 2020-6-7 启动增加对tidb的支持
		DBNameTypeMap.put(Dialect.TIDB, DBType.TIDB);
		DBNameTypeMap.put(Dialect.TDENGINE, DBType.TDENGINE);
		DBNameTypeMap.put(Dialect.IMPALA, DBType.IMPALA);

		// 20220829 增加对h2的支持
		DBNameTypeMap.put(Dialect.H2, DBType.H2);
		DBNameTypeMap.put(Dialect.OSCAR, DBType.OSCAR);
		DBNameTypeMap.put(Dialect.VASTBASE, DBType.VASTBASE);
		DBNameTypeMap.put(Dialect.DORIS, DBType.DORIS);
		DBNameTypeMap.put(Dialect.STARROCKS, DBType.STARROCKS);

		// 默认设置oscar、vastbase数据库用gaussdb方言来实现
		// dialectMap.put(Dialect.OSCAR, Dialect.OPENGAUSS);
		DBNameTypeMap.put(Dialect.UNDEFINE, DBType.UNDEFINE);
	}

	/**
	 * @param dbType
	 * @return 获取数据库类型名称
	 */
	public static String getDialect(Integer dbType) {
		switch (dbType) {
		case DBType.MYSQL: {
			return Dialect.MYSQL;
		}
		case DBType.MYSQL57: {
			return Dialect.MYSQL57;
		}
		case DBType.ORACLE: {
			return Dialect.ORACLE;
		}
		case DBType.POSTGRESQL: {
			return Dialect.POSTGRESQL;
		}
		case DBType.POSTGRESQL14: {
			return Dialect.POSTGRESQL14;
		}
		case DBType.SQLSERVER: {
			return Dialect.SQLSERVER;
		}
		case DBType.DB2: {
			return Dialect.DB2;
		}
		case DBType.OCEANBASE: {
			return Dialect.OCEANBASE;
		}
		case DBType.GAUSSDB: {
			return Dialect.GAUSSDB;
		}
		case DBType.MOGDB: {
			return Dialect.MOGDB;
		}
		case DBType.STARDB: {
			return Dialect.STARDB;
		}
		case DBType.OPENGAUSS: {
			return Dialect.OPENGAUSS;
		}
		case DBType.CLICKHOUSE: {
			return Dialect.CLICKHOUSE;
		}
		case DBType.SQLITE: {
			return Dialect.SQLITE;
		}
		case DBType.TIDB: {
			return Dialect.TIDB;
		}
		case DBType.DM: {
			return Dialect.DM;
		}
		// 修复遗漏:kingbase的dbType映射成方言常量,避免退化为undefine导致方言变体sql失效
		case DBType.KINGBASE: {
			return Dialect.KINGBASE;
		}
		case DBType.ORACLE11: {
			return Dialect.ORACLE11;
		}
		case DBType.ES: {
			return Dialect.ES;
		}
		case DBType.MONGO: {
			return Dialect.MONGO;
		}
		case DBType.IMPALA: {
			return Dialect.IMPALA;
		}
		case DBType.TDENGINE: {
			return Dialect.TDENGINE;
		}
		case DBType.H2: {
			return Dialect.H2;
		}
		case DBType.OSCAR: {
			return Dialect.OSCAR;
		}
		case DBType.VASTBASE: {
			return Dialect.VASTBASE;
		}
		case DBType.DORIS: {
			return Dialect.DORIS;
		}
		case DBType.STARROCKS: {
			return Dialect.STARROCKS;
		}
		default:
			return Dialect.UNDEFINE;
		}
	}

	/**
	 * @param conn
	 * @return 获取数据库批量sql语句的分割符号
	 */
	public static String getDatabaseSqlSplitSign(Connection conn) {
		try {
			int dbType = getDBType(conn);
			return getDatabaseSqlSplitSign(dbType);
		} catch (Exception e) {
			logger.error("getDatabaseSqlSplitSign method execution failed", e);
		}
		return ";";
	}

	/**
	 * sqlserver批量脚本通过go进行分割(前后带空格)
	 */
	public static final String SQLSERVER_SPLIT_SIGN = " go ";

	public static String getDatabaseSqlSplitSign(int dbType) {
		// sqlserver
		if (dbType == DBType.SQLSERVER) {
			return SQLSERVER_SPLIT_SIGN;
		}
		return ";";
	}

	/**
	 * @param conn
	 * @return
	 * @throws SQLException 获取数据库类型
	 */
	public static String getCurrentDBDialect(final Connection conn) throws SQLException {
		// update 2026-9-6 委托DBProfile统筹缓存(以URL为key,含dialect/dbType等一次解析),
		// 原始匹配逻辑(产品名+URL特征+dialectMap映射)提炼为resolveDialectByMeta供
		// getDBProfile未命中解析时调用,避免委托形成循环;dialectMap运行期修改自此为
		// 首次解析冻结语义(与getDBType的既有缓存行为一致),需热生效可显式配置dialect
		return getDBProfile(conn).getDialect();
	}

	/**
	 * update 2026-9-6 方言原始匹配(自原getCurrentDBDialect提炼,仅getDBProfile解析链内部使用):
	 * 产品名匹配+URL特征优先+dialectMap自定义映射,无缓存实时
	 */
	private static String resolveDialectByMeta(final Connection conn) throws SQLException {
		String dilectName = Dialect.UNDEFINE;
		// 从hashMap中获取
		if (null != conn) {
			// update 2026-9-5 URL schema特征优先:openGauss系驱动上报ProductName=PostgreSQL,
			// 特征命中以识别名替代产品名走后续匹配(识别名恰含方言关键字,自然命中对应分支;
			// 尾部dialectMap自定义映射对URL识别结果同样生效,尊重用户显式配置)
			String urlDialect = getDBDialectByUrl(conn);
			String dbDialect;
			if (urlDialect != null) {
				dbDialect = urlDialect;
			} else {
				String productName = conn.getMetaData().getDatabaseProductName();
				// 个别第三方驱动getDatabaseProductName可能返回null,按未识别处理,避免replaceAll抛NPE
				if (productName == null) {
					return dilectName;
				}
				// 剔除空白
				dbDialect = productName.replaceAll("\\s+", "");
			}
			// oracle
			if (StringUtil.indexOfIgnoreCase(dbDialect, Dialect.ORACLE) != -1) {
				dilectName = Dialect.ORACLE;
			} // mysql以及mysql的分支数据库
			else if (StringUtil.indexOfIgnoreCase(dbDialect, Dialect.MYSQL) != -1
					|| StringUtil.indexOfIgnoreCase(dbDialect, Dialect.MARIADB) != -1
					|| StringUtil.indexOfIgnoreCase(dbDialect, Dialect.INNOSQL) != -1) {
				dilectName = Dialect.MYSQL;
				// update 2026-9-6 StarRocks/Doris的current_version()引擎探测已上移至getDBProfile
				// 统一处理(探测属档案属性解析,随URL_PROFILE_CACHE每URL缓存一次),
				// 本方法恢复纯产品名+URL特征的实时语义
			} // doris
			else if (StringUtil.indexOfIgnoreCase(dbDialect, Dialect.DORIS) != -1) {
				dilectName = Dialect.DORIS;
			} else if (StringUtil.indexOfIgnoreCase(dbDialect, Dialect.STARROCKS) != -1) {
				dilectName = Dialect.STARROCKS;
			}
			// postgresql
			else if (StringUtil.indexOfIgnoreCase(dbDialect, Dialect.POSTGRESQL) != -1) {
				dilectName = Dialect.POSTGRESQL;
			} // sqlserver,只支持2012或以上版本
			else if (StringUtil.indexOfIgnoreCase(dbDialect, Dialect.SQLSERVER) != -1
					|| StringUtil.indexOfIgnoreCase(dbDialect, "mssql") != -1
					|| StringUtil.indexOfIgnoreCase(dbDialect, "microsoftsqlserver") != -1) {
				dilectName = Dialect.SQLSERVER;
			} // db2
			else if (StringUtil.indexOfIgnoreCase(dbDialect, Dialect.DB2) != -1) {
				dilectName = Dialect.DB2;
			} // clickhouse
			else if (StringUtil.indexOfIgnoreCase(dbDialect, Dialect.CLICKHOUSE) != -1) {
				dilectName = Dialect.CLICKHOUSE;
			} // OCEANBASE
			else if (StringUtil.indexOfIgnoreCase(dbDialect, Dialect.OCEANBASE) != -1) {
				dilectName = Dialect.OCEANBASE;
			} // opengauss
			else if (StringUtil.indexOfIgnoreCase(dbDialect, Dialect.OPENGAUSS) != -1) {
				dilectName = Dialect.OPENGAUSS;
			}
			// GAUSSDB
			else if (StringUtil.indexOfIgnoreCase(dbDialect, Dialect.GAUSSDB) != -1
					|| "zenith".equalsIgnoreCase(dbDialect)) {
				dilectName = Dialect.GAUSSDB;
			} // MOGDB
			else if (StringUtil.indexOfIgnoreCase(dbDialect, Dialect.MOGDB) != -1) {
				dilectName = Dialect.MOGDB;
			} else if (StringUtil.indexOfIgnoreCase(dbDialect, Dialect.STARDB) != -1) {
				dilectName = Dialect.STARDB;
			} else if (StringUtil.indexOfIgnoreCase(dbDialect, Dialect.SQLITE) != -1) {
				dilectName = Dialect.SQLITE;
			} // dm
			else if (StringUtil.indexOfIgnoreCase(dbDialect, Dialect.DM) != -1) {
				dilectName = Dialect.DM;
			} // TIDB
			else if (StringUtil.indexOfIgnoreCase(dbDialect, Dialect.TIDB) != -1) {
				dilectName = Dialect.TIDB;
			} // 2022-12-14 验证
			else if (StringUtil.indexOfIgnoreCase(dbDialect, Dialect.TDENGINE) != -1) {
				dilectName = Dialect.TDENGINE;
			} else if (StringUtil.indexOfIgnoreCase(dbDialect, Dialect.KINGBASE) != -1) {
				dilectName = Dialect.KINGBASE;
			} else if (StringUtil.indexOfIgnoreCase(dbDialect, Dialect.GREENPLUM) != -1) {
				dilectName = Dialect.POSTGRESQL;
			} else if (StringUtil.indexOfIgnoreCase(dbDialect, Dialect.IMPALA) != -1) {
				dilectName = Dialect.IMPALA;
			} // elasticsearch
			else if (StringUtil.indexOfIgnoreCase(dbDialect, Dialect.ES) != -1) {
				dilectName = Dialect.ES;
			} // 20220829 h2
			else if (StringUtil.indexOfIgnoreCase(dbDialect, Dialect.H2) != -1) {
				dilectName = Dialect.H2;
			} else if (StringUtil.indexOfIgnoreCase(dbDialect, Dialect.OSCAR) != -1) {
				dilectName = Dialect.OSCAR;
			} else if (StringUtil.indexOfIgnoreCase(dbDialect, Dialect.VASTBASE) != -1) {
				dilectName = Dialect.VASTBASE;
			} else if (!dialectMap.isEmpty()) {
				// 针对框架未支持的数据库，通过dialectMap的key进行匹配;
				// IgnoreKeyCaseMap基于ConcurrentHashMap,entrySet迭代顺序不确定,
				// 按key长度降序(最长优先)遍历,保证产品名同时命中多个key时结果稳定且取最具体匹配
				List<Map.Entry<String, String>> dialectEntries = new ArrayList<Map.Entry<String, String>>(
						dialectMap.entrySet());
				dialectEntries.sort((one, two) -> two.getKey().length() - one.getKey().length());
				for (Map.Entry<String, String> entry : dialectEntries) {
					if (StringUtil.indexOfIgnoreCase(dbDialect, entry.getKey()) != -1) {
						dilectName = entry.getValue().toLowerCase(Locale.ROOT);
						break;
					}
				}
			}
		}
		// 存在数据库方言映射，将类似oscar数据库映射成oracle执行
		if (dialectMap.containsKey(dilectName)) {
			dilectName = dialectMap.get(dilectName).toLowerCase(Locale.ROOT);
		}
		return dilectName;
	}

	/**
	 * @param conn
	 * @return
	 * @throws SQLException 获取当前数据库的版本
	 */
	private static int getDBVersion(final Connection conn) throws SQLException {
		// -1表示版本不确定
		int result = -1;
		// 部分数据库驱动还不支持此方法
		try {
			result = conn.getMetaData().getDatabaseMajorVersion();
		} catch (Exception e) {
			// ignore,部分数据库驱动还不支持此方法
		}
		return result;
	}

	/**
	 * update 2026-9-10 db2的GSE空间扩展schema探测(随URL档案每URL一次):12.1起内置空间引擎与 db2gse
	 * GSE扩展可并存(实测12.1.5容器GSE仍启用、12.1.2容器仅内置),且内置ST_GEOMETRY与
	 * db2gse.ST_GEOMETRY为不同UDT(函数产物与列类型错配报-408),geometry参数化包装须按GSE
	 * 存在性分派db2gse前缀或内置非限定形态;非db2为null,探测异常为TRUE(保持既有db2gse形态)。
	 * (原probeSqlServerJsonType服务器级json类型探测已于2026-9-10移除:merge/insert-ignore统一
	 * 裸?+coalesce形态对原生json列与nvarchar承载列双兼容,不再依赖服务器级探测)
	 */
	private static Boolean probeDB2GseSchema(final Connection conn, int dbType) {
		if (dbType != DBType.DB2) {
			return null;
		}
		try (Statement st = conn.createStatement();
				ResultSet rs = st.executeQuery("select 1 from syscat.schemata where schemaname='DB2GSE'")) {
			return rs.next() ? Boolean.TRUE : Boolean.FALSE;
		} catch (Exception e) {
			return Boolean.TRUE;
		}
	}

	/**
	 * update 2026-9-5 JDBC URL schema特征与方言识别名的映射:openGauss/MOGDB/VASTBASE/STARDB等
	 * 国产PG系内核的getDatabaseProductName()误报为PostgreSQL(openGauss 5.0.0实测上报
	 * PostgreSQL/9.2.4),仅靠产品名会被误判为postgresql方言,而其saveOrUpdate生成的 ON
	 * CONFLICT语法在这些内核默认不解析;JDBC URL的schema段(jdbc:opengauss:等)
	 * 由驱动忠实保留,特征命中以识别名参与后续方言匹配(含dialectMap自定义映射)
	 */
	private static final Map<String, String> URL_SCHEMA_DIALECT = new HashMap<>();
	static {
		URL_SCHEMA_DIALECT.put("opengauss", Dialect.OPENGAUSS);
		URL_SCHEMA_DIALECT.put("mogdb", Dialect.MOGDB);
		URL_SCHEMA_DIALECT.put("vastbase", Dialect.VASTBASE);
		URL_SCHEMA_DIALECT.put("stardb", Dialect.STARDB);
		URL_SCHEMA_DIALECT.put("gaussdb", Dialect.GAUSSDB);
		// update 2026-9-6 DM驱动compatibleMode=oracle时ProductName伪装成Oracle(实测被误判
		// 为oracle方言走Oracle11gDialect,identity列生成nvl(?,null)赋值形态报-2723),
		// URL特征优先纠正(与openGauss伪装PostgreSQL同构场景)
		URL_SCHEMA_DIALECT.put("dm", Dialect.DM);
		// update 2026-9-6 oceanbase的ProductName为"MySQL 5.7.25-OceanBase_CE..."(mysql分支
		// 在产品名判定链中先命中被误判mysql方言,ob4.3无string_to_vector等mysql9函数),
		// URL特征jdbc:oceanbase://优先纠正
		URL_SCHEMA_DIALECT.put("oceanbase", Dialect.OCEANBASE);
	}

	/**
	 * @param conn
	 * @return URL schema特征命中的方言识别名,未命中或获取URL失败返回null
	 */
	private static String getDBDialectByUrl(final Connection conn) {
		try {
			String jdbcUrl = conn.getMetaData().getURL();
			if (jdbcUrl != null && jdbcUrl.startsWith("jdbc:")) {
				int schemaEnd = jdbcUrl.indexOf(':', 5);
				if (schemaEnd > 5) {
					return URL_SCHEMA_DIALECT.get(jdbcUrl.substring(5, schemaEnd).toLowerCase(Locale.ROOT));
				}
			}
		} catch (Exception e) {
			// 部分驱动或连接代理场景获取url失败,回退产品名探测
		}
		return null;
	}

	/**
	 * @param conn
	 * @return
	 * @throws SQLException 获取数据库类型
	 */
	public static int getDBType(final Connection conn) throws SQLException {
		// update 2026-9-6
		// 委托DBProfile统筹缓存(以URL为key,dialect/dbType/productName/majorVersion
		// 及PG系扩展类型句柄一次解析),取代原DBNameTypeMap键式缓存,外部行为不变
		return getDBProfile(conn).getDbType();
	}

	// update 2026-9-6 连接维度特征档案缓存:以JDBC URL为key(同一URL必然指向同一数据库实例),
	// getDBType/getCurrentDBDialect消费方/SqlUtil的PGobject绑定统一走此档案
	private static final ConcurrentHashMap<String, DBProfile> URL_PROFILE_CACHE = new ConcurrentHashMap<String, DBProfile>(
			16);

	/**
	 * update 2026-9-6 获取连接的数据库特征档案(进程级缓存,以JDBC URL为key):
	 * 一次解析dialect/productName/majorVersion/dbType及PG系扩展类型PGobject绑定句柄,
	 * 统筹此前DataSourceUtils与SqlUtil各自独立的解析与缓存。
	 * 热路径仅一次getMetaData().getURL()(实测均摊0.04us)+缓存查找,productName/majorVersion
	 * 等重解析仅在缓存未命中时执行(含StarRocks的current_version探测),每URL至多一次。
	 * 不缓存运行期可变属性(dialectMap自定义映射、backslashEscaping全局开关),保持实时语义。
	 * 
	 * @param conn 数据库连接
	 * @return 特征档案(url获取失败的连接返回实时解析的临时档案,不入缓存)
	 */
	public static DBProfile getDBProfile(final Connection conn) throws SQLException {
		// 仅取URL用于缓存查找(轻量),缓存命中直接返回
		String connUrl = null;
		try {
			connUrl = conn.getMetaData().getURL();
		} catch (Exception e) {
			// 部分代理连接获取url失败,走实时解析
		}
		// URL可否作为缓存key:非null即可(getURL契约返回合法jdbc URL或null,ConcurrentHashMap
		// 不允许null key会抛NPE);获取不到url的连接只实时解析不入缓存
		boolean urlAsCacheKey = (connUrl != null);
		if (urlAsCacheKey) {
			DBProfile profile = URL_PROFILE_CACHE.get(connUrl);
			if (profile != null) {
				return profile;
			}
		}
		// 缓存未命中(或URL不可得):完整解析(产品名/版本/方言映射/引擎探测/PGobject句柄)
		String productName = conn.getMetaData().getDatabaseProductName();
		int majorVersion = getDBVersion(conn);
		// 解析链:resolveDialectByMeta(产品名+URL特征+dialectMap映射,实时) + StarRocks引擎探测
		String dialect = resolveDialect(conn, resolveDialectByMeta(conn));
		int dbType = dialectToDbType(dialect, majorVersion);
		DBProfile profile = new DBProfile(connUrl, dialect, dbType, productName, majorVersion,
				resolvePGobjectHolder(connUrl), probeDB2GseSchema(conn, dbType), isBackslashEscapeDbType(dbType));
		// URL可标识的连接入缓存(并发竞争时保留先入条目)
		if (urlAsCacheKey) {
			DBProfile exist = URL_PROFILE_CACHE.putIfAbsent(connUrl, profile);
			if (exist != null) {
				return exist;
			}
		}
		return profile;
	}

	/**
	 * update 2026-9-10 获取当前执行上下文的数据库特征档案:连接获取阶段绑定至线程上下文
	 * (setDBProfile),数据库运行期特征(反斜杠转义约定等)统一经此档案提供,消费方无需直接
	 * 访问ThreadDataHolder;配置解析期等无连接上下文场景返回null,由调用方回退配置方言
	 * 
	 * @return 当前线程上下文的特征档案,无连接上下文时为null
	 */
	public static DBProfile getCurrentDBProfile() {
		return SqlToyThreadDataHolder.getDBProfile();
	}

	/**
	 * update 2026-9-10 判断dbType是否属反斜杠转义族(字面量内反斜杠为转义字符,\'不终结字面量):
	 * mysql/mysql57/tidb/doris/starrocks/oceanbase;PostgreSQL/Oracle/SQLServer/DB2/h2/kingbase等
	 * 按标准SQL单反斜杠语义(kingbase即使mysql兼容模式亦单斜杠)。
	 * 构建DBProfile解析出dbType时即完成判定并随档案缓存,运行期经profile.isBackslashEscape()直取
	 * 
	 * @param dbType 数据库类型,参见DBType
	 * @return true表示字面量内反斜杠为转义字符
	 */
	public static boolean isBackslashEscapeDbType(int dbType) {
		return dbType == DBType.MYSQL || dbType == DBType.MYSQL57 || dbType == DBType.TIDB || dbType == DBType.DORIS
				|| dbType == DBType.STARROCKS || dbType == DBType.OCEANBASE;
	}

	/**
	 * update 2026-9-6 方言的引擎探测统一处理:mysql协议方言(mysql系元数据同形)时校正
	 * StarRocks/Doris——实测其经mysql驱动连接时ProductName/Version/URL三信号全部伪装成MySQL
	 * (8.0.33/jdbc:mysql)无法靠元数据区分。 update 2026-9-10
	 * 探测顺序对调(实测修正):一级@@version_comment(所有mysql协议引擎均支持,
	 * 零报错风险)命中doris字样归DORIS——原current_version()先行的探测在Doris(无此函数,实测
	 * 2.1.0/4.1.3均无)报HY000/ErrorCode 1105,命中Hikari对mysql驱动的fatal vendor错误码清单,
	 * 池连接被标记broken逐出引发后续用例"Connection is closed"雪崩(真mysql报42000非fatal故
	 * 此前未暴露);二级current_version()兜底StarRocks(SR有该函数探测不报错,返回值校验版本串
	 * 特征如4.1.4-4a9848e含git哈希后缀,而其@@version_comment无产品名仅"4.1.4-4a9848e"无法
	 * 一级识别),特征不符保持mysql方言并warn,可显式配置dialect覆盖。
	 * DialectFactory中DORIS与STARROCKS共用DorisDialect,两家均正确落方言
	 * (本方法仅在getDBProfile解析时调用,随URL缓存每URL探测一次)
	 */
	private static String resolveDialect(Connection conn, String dialect) {
		if (!Dialect.MYSQL.equals(dialect)) {
			return dialect;
		}
		// 一级:@@version_comment(零报错风险)——Doris返回"Doris version
		// doris-x.y.z-..."(2.1/4.1实测),
		// 真mysql返回"MySQL Community Server...",TiDB返回"TiDB Server..."(update 2026-9-10
		// 实测
		// TiDB 8.5.1的元数据ProductName已由"TiDB"变为"MySQL"(7.5.1为TiDB),产品名识别链失效致
		// 误归MYSQL方言生成string_to_vector等mysql9专属语法,此处按version_comment纠正归TIDB),
		// SR返回纯版本串无产品名(落二级current_version兜底)
		try (java.sql.Statement probe = conn.createStatement();
				java.sql.ResultSet prs = probe.executeQuery("select @@version_comment")) {
			if (prs.next()) {
				String versionComment = prs.getString(1);
				if (versionComment != null) {
					String commentLow = versionComment.toLowerCase(Locale.ROOT);
					if (commentLow.contains("doris")) {
						logger.info("detected doris engine by version_comment={}", versionComment);
						return Dialect.DORIS;
					}
					if (commentLow.contains("starrocks")) {
						logger.info("detected starrocks engine by version_comment={}", versionComment);
						return Dialect.STARROCKS;
					}
					if (commentLow.contains("tidb")) {
						logger.info("detected tidb engine by version_comment={}", versionComment);
						return Dialect.TIDB;
					}
				}
			}
		} catch (Exception ignore) {
			// 个别引擎不支持@@version_comment查询,落二级探测
		}
		// 二级:current_version()为StarRocks特有函数(mysql报Unknown function 42000非fatal),
		// 返回值须校验版本串特征,防御其他数据库未来实现同名函数导致误判
		try (java.sql.Statement probe = conn.createStatement();
				java.sql.ResultSet prs = probe.executeQuery("select current_version()")) {
			if (prs.next()) {
				String engineVersion = prs.getString(1);
				if (engineVersion != null && (engineVersion.matches("(?i).*(starrocks|doris).*")
						|| engineVersion.matches("\\d+\\.\\d+\\.\\d+-[0-9a-zA-Z]{6,}.*"))) {
					logger.info("detected starrocks/doris compatible engine by current_version()={}", engineVersion);
					return engineVersion.toLowerCase(Locale.ROOT).contains("doris") ? Dialect.DORIS : Dialect.STARROCKS;
				}
				logger.warn(
						"current_version()={} does not match starrocks/doris version pattern, keep mysql dialect! "
								+ "if this engine is actually starrocks/doris, please config dialect explicitly!",
						engineVersion);
			}
		} catch (Exception ignore) {
			// mysql无此函数,保持mysql方言
		}
		return Dialect.MYSQL;
	}

	/**
	 * 方言名+主版本→dbType(自原getDBType(conn)的判定链提炼,含版本修正)
	 */
	private static int dialectToDbType(String dbDialect, int majorVersion) {
		if (dbDialect.equals(Dialect.ORACLE)) {
			if (majorVersion <= 11) {
				return DBType.ORACLE11;
			}
			return DBType.ORACLE;
		} else if (dbDialect.equals(Dialect.ORACLE11)) {
			return DBType.ORACLE11;
		} else if (dbDialect.equals(Dialect.MYSQL)) {
			if (majorVersion <= 5) {
				return DBType.MYSQL57;
			}
			return DBType.MYSQL;
		} else if (dbDialect.equals(Dialect.MYSQL57)) {
			return DBType.MYSQL57;
		} else if (dbDialect.equals(Dialect.POSTGRESQL)) {
			if (majorVersion < 15) {
				return DBType.POSTGRESQL14;
			}
			return DBType.POSTGRESQL;
		} else if (dbDialect.equals(Dialect.GREENPLUM)) {
			return DBType.POSTGRESQL;
		} else if (dbDialect.equals(Dialect.SQLSERVER)) {
			return DBType.SQLSERVER;
		} else if (dbDialect.equals(Dialect.DB2)) {
			return DBType.DB2;
		} else if (dbDialect.equals(Dialect.CLICKHOUSE)) {
			return DBType.CLICKHOUSE;
		} else if (dbDialect.equals(Dialect.OCEANBASE)) {
			return DBType.OCEANBASE;
		} else if (dbDialect.equals(Dialect.OPENGAUSS)) {
			return DBType.OPENGAUSS;
		} else if (dbDialect.equals(Dialect.GAUSSDB)) {
			return DBType.GAUSSDB;
		} else if (dbDialect.equals(Dialect.MOGDB)) {
			return DBType.MOGDB;
		} else if (dbDialect.equals(Dialect.STARDB)) {
			return DBType.STARDB;
		} else if (dbDialect.equals(Dialect.SQLITE)) {
			return DBType.SQLITE;
		} else if (dbDialect.equals(Dialect.DM)) {
			return DBType.DM;
		} else if (dbDialect.equals(Dialect.TIDB)) {
			return DBType.TIDB;
		} else if (dbDialect.equals(Dialect.IMPALA)) {
			return DBType.IMPALA;
		} else if (dbDialect.equals(Dialect.TDENGINE)) {
			return DBType.TDENGINE;
		} else if (dbDialect.equals(Dialect.KINGBASE)) {
			return DBType.KINGBASE;
		} else if (dbDialect.equals(Dialect.ES)) {
			return DBType.ES;
		} else if (dbDialect.equals(Dialect.H2)) {
			return DBType.H2;
		} else if (dbDialect.equals(Dialect.OSCAR)) {
			return DBType.OSCAR;
		} else if (dbDialect.equals(Dialect.VASTBASE)) {
			return DBType.VASTBASE;
		} else if (dbDialect.equals(Dialect.DORIS)) {
			return DBType.DORIS;
		} else if (dbDialect.equals(Dialect.STARROCKS)) {
			return DBType.STARROCKS;
		}
		return DBType.UNDEFINE;
	}

	/**
	 * update 2026-9-6 URL scheme→同源驱动PGobject反射句柄(自SqlUtil迁入统筹):
	 * 实测PGobject不能跨驱动setObject(pg驱动收到org.opengauss的PGobject报Can't infer the SQL
	 * type,反向同理);按URL的schema段选择同源驱动的PGobject类,对应不上
	 * classpath中的驱动类时返回constructor为null的哨兵,由调用方回退setObject(str,OTHER)。
	 * 注意本匹配的对象是"驱动"而非"数据库":用postgresql官方驱动连openGauss/vastbase等
	 * PG系库时(pom注释中明示的兼容用法),URL必为jdbc:postgresql:从而解析出org.postgresql的
	 * PGobject,与实际驱动同源,天然正确;实测openGauss默认SCRAM(sha256)认证下PG官方驱动 连接即被拒(Invalid SCRAM
	 * client initialization),须openGauss侧开启兼容认证方可使用此形态
	 */
	private static DBProfile.PGobjectHolder resolvePGobjectHolder(String url) {
		try {
			if (url == null || !url.startsWith("jdbc:")) {
				return NULL_PG_HOLDER;
			}
			int schemaEnd = url.indexOf(':', 5);
			if (schemaEnd <= 5) {
				return NULL_PG_HOLDER;
			}
			String schema = url.substring(5, schemaEnd).toLowerCase(Locale.ROOT);
			String pgObjectClass;
			if ("opengauss".equals(schema) || "mogdb".equals(schema)) {
				pgObjectClass = "org.opengauss.util.PGobject";
			} else if ("vastbase".equals(schema)) {
				pgObjectClass = "cn.com.vastbase.util.PGobject";
			} else if ("gaussdb".equals(schema)) {
				// update 2026-9-6 GaussDB Kernel JDBC(com.huaweicloud.gaussdb:gaussdbjdbc,
				// Driver类com.huawei.gaussdb.jdbc.Driver)的PGobject在华为自有包路径
				pgObjectClass = "com.huawei.gaussdb.jdbc.util.PGobject";
			} else if ("kingbase8".equals(schema) || "kingbase".equals(schema)) {
				// update 2026-9-10 金仓驱动(jdbc:kingbase8:)整包重定位com.kingbase8,PGobject同构类为
				// com.kingbase8.util.KBobject(小写o);若走else兜底误构org.postgresql.util.PGobject,
				// 跨驱动setObject必败(KES V9容器实测KSQLException:Can't infer the SQL type)
				pgObjectClass = "com.kingbase8.util.KBobject";
			} else if ("postgresql".equals(schema)) {
				pgObjectClass = "org.postgresql.util.PGobject";
			} else {
				// stardb等其他PG系驱动:多数兼容postgresql驱动包路径,尝试后失败由调用方回退
				// update 2026-9-10 注意:oscar(神通)驱动jar解包实证无org.postgresql包路径亦无
				// PGobject同构类(老pgjdbc深度魔改的自有实现),OSCAR不得加入isPGFamily:否则
				// classpath有pg驱动时holder构造成功但跨驱动setObject必败且无回退
				// (回退仅在holder为null时触发),其json/vector以setString绑定为正确形态
				pgObjectClass = "org.postgresql.util.PGobject";
			}
			Class<?> clazz = Class.forName(pgObjectClass);
			return new DBProfile.PGobjectHolder(clazz.getDeclaredConstructor(),
					clazz.getMethod("setType", String.class), clazz.getMethod("setValue", String.class));
		} catch (Throwable e) {
			return NULL_PG_HOLDER;
		}
	}

	// 无法解析URL/驱动的占位句柄(constructor为null)
	private static final DBProfile.PGobjectHolder NULL_PG_HOLDER = new DBProfile.PGobjectHolder(null, null, null);

	/**
	 * @param dialect
	 * @return 这里的方言已经在SqlToyContext中已经做了规整(因此不会超出范围)
	 */
	public static int getDBType(String dialect) {
		if (StringUtil.isBlank(dialect)) {
			return DBType.UNDEFINE;
		}
		String dialectLow = dialect.toLowerCase(Locale.ROOT);
		// 方言映射
		if (dialectMap.containsKey(dialectLow)) {
			dialectLow = dialectMap.get(dialectLow).toLowerCase(Locale.ROOT);
		}
		if (!DBNameTypeMap.containsKey(dialectLow)) {
			logger.warn("the database dialect:[{}] is not included in the dialect map initialized by sqltoy",
					dialectLow);
			return DBType.UNDEFINE;
		}
		return DBNameTypeMap.get(dialectLow);
	}

	/**
	 * 获取不同数据库validator语句
	 *
	 * @param dbType
	 * @return
	 * @throws Exception
	 */
	public static String getValidateQuery(final int dbType) throws Exception {
		switch (dbType) {
		case DBType.DB2: {
			return "select 1 from sysibm.sysdummy1";
		}
		case DBType.ORACLE:
		case DBType.OCEANBASE:
		case DBType.DM:
		case DBType.H2:
		case DBType.ORACLE11: {
			return "select 1 from dual";
		}
		case DBType.POSTGRESQL:
		case DBType.POSTGRESQL14:
		case DBType.OSCAR:
		case DBType.VASTBASE:
		case DBType.MOGDB:
		case DBType.STARDB:
		case DBType.OPENGAUSS:
		case DBType.GAUSSDB: {
			return "select version()";
		}
		// mysql、tidb、sqlserver、sqlite、doris等
		default:
			return "select 1";
		}
	}

	/**
	 * @param conn
	 * @return
	 * @throws Exception 获取不同数据库validator语句
	 */
	public static String getValidateQuery(final Connection conn) throws Exception {
		int dbType = getDBType(conn);
		return getValidateQuery(dbType);
	}

	/**
	 * @param sqltoyContext
	 * @param datasource
	 * @param handler
	 * @return 统一处理DataSource以及对应的Connection，便于跟spring事务集成
	 */
	public static Object processDataSource(SqlToyContext sqltoyContext, DataSource datasource,
			DataSourceCallbackHandler handler) {
		if (datasource == null) {
			throw new IllegalArgumentException(
					"dataSource is null, possible causes:\n 1. the connection pool is misconfigured and no DataSource was created;\n 2. in multi-datasource scenario spring.sqltoy.defaultDataSource=xxx is not configured;\n 3. the dataSource name specified in the dao does not exist, please check!");
		}
		Connection conn = sqltoyContext.getConnection(datasource);
		Integer dbType;
		String dialect;
		try {
			// 统一提取数据库方言类型
			if (null != sqltoyContext && StringUtil.isNotBlank(sqltoyContext.getDialect())) {
				dialect = sqltoyContext.getDialect();
				dbType = getDBType(dialect);
				// 显式dialect时仍采集连接真实档案(getDBProfile含dbType/主版本等,随URL缓存)
				SqlToyThreadDataHolder.setDBProfile(getDBProfile(conn));
			} else {
				DBProfile connProfile = getDBProfile(conn);
				dbType = connProfile.getDbType();
				dialect = getDialect(dbType);
				SqlToyThreadDataHolder.setDBProfile(connProfile);
			}
			// 调试显示数据库信息,便于在多数据库场景下辨别查询对应的数据库
			if (SqlToyConstants.showDatasourceInfo()) {
				logger.debug("db.dialect={};conn.url={};schema={};catalog={};username={}", dialect,
						conn.getMetaData().getURL(), conn.getSchema(), conn.getCatalog(),
						conn.getMetaData().getUserName());
			}
			// 调用反调，传入conn和数据库类型进行实际业务处理(数据库类型主要便于DialectFactory获取对应方言处理类)
			handler.doConnection(conn, dbType, dialect);
		} catch (Exception e) {
			logger.error("processDataSource method execution failed", e);
			sqltoyContext.releaseConnection(conn, datasource);
			conn = null;
			throw new RuntimeException(e);
		} finally {
			SqlToyThreadDataHolder.clearDBProfile();
			// 释放连接,连接池实际是归还连接，未必一定关闭
			sqltoyContext.releaseConnection(conn, datasource);
		}
		// 返回反调的结果
		return handler.getResult();
	}

	/**
	 * @param sqltoyContext
	 * @param datasource
	 * @return 获取数据库的类型
	 */
	public static int getDBType(SqlToyContext sqltoyContext, DataSource datasource) {
		if (datasource == null) {
			return DBType.UNDEFINE;
		}
		Integer dbType = dataSourceDbTypeCache.get(datasource);
		if (dbType != null) {
			return dbType;
		}
		Connection conn = sqltoyContext.getConnection(datasource);
		dbType = DBType.UNDEFINE;
		try {
			dbType = getDBType(conn);
			dataSourceDbTypeCache.put(datasource, dbType);
		} catch (Exception e) {
			logger.error("getDBType method execution failed", e);
			sqltoyContext.releaseConnection(conn, datasource);
			conn = null;
			throw new RuntimeException(e);
		} finally {
			// 释放连接,连接池实际是归还连接，未必一定关闭
			sqltoyContext.releaseConnection(conn, datasource);
		}
		return dbType;
	}

	/**
	 * @param sqltoyContext
	 * @param datasource
	 * @return
	 * @TDDO 获取数据库类型的名称
	 */
	public static String getDialect(SqlToyContext sqltoyContext, DataSource datasource) {
		if (datasource == null) {
			return Dialect.UNDEFINE;
		}
		// update 2022-9-30 增加缓存避免通过connection获取数据库方言
		String dialect = dataSourceDialectCache.get(datasource);
		if (dialect != null) {
			return dialect;
		}
		Connection conn = sqltoyContext.getConnection(datasource);
		try {
			dialect = getDialect(conn);
			dataSourceDialectCache.put(datasource, dialect);
		} catch (Exception e) {
			logger.error("getDialect method execution failed", e);
			sqltoyContext.releaseConnection(conn, datasource);
			conn = null;
			throw new RuntimeException(e);
		} finally {
			// 释放连接,连接池实际是归还连接，未必一定关闭
			sqltoyContext.releaseConnection(conn, datasource);
		}
		return dialect;
	}

	/**
	 * @param conn
	 * @return
	 * @throws Exception 根据连接获取数据库方言
	 */
	private static String getDialect(Connection conn) throws Exception {
		if (conn == null) {
			return Dialect.UNDEFINE;
		}
		int dbType = getDBType(conn);
		switch (dbType) {
		case DBType.DB2:
			return Dialect.DB2;
		case DBType.ORACLE:
			return Dialect.ORACLE;
		case DBType.ORACLE11:
			return Dialect.ORACLE11;
		case DBType.POSTGRESQL:
			return Dialect.POSTGRESQL;
		case DBType.POSTGRESQL14:
			return Dialect.POSTGRESQL14;
		case DBType.MYSQL:
			return Dialect.MYSQL;
		case DBType.MYSQL57:
			return Dialect.MYSQL57;
		case DBType.SQLSERVER:
			return Dialect.SQLSERVER;
		case DBType.SQLITE:
			return Dialect.SQLITE;
		case DBType.CLICKHOUSE:
			return Dialect.CLICKHOUSE;
		case DBType.TIDB:
			return Dialect.TIDB;
		case DBType.OCEANBASE:
			return Dialect.OCEANBASE;
		case DBType.DM:
			return Dialect.DM;
		case DBType.KINGBASE:
			return Dialect.KINGBASE;
		case DBType.TDENGINE:
			return Dialect.TDENGINE;
		case DBType.OPENGAUSS:
			return Dialect.OPENGAUSS;
		case DBType.GAUSSDB:
			return Dialect.GAUSSDB;
		case DBType.MOGDB:
			return Dialect.MOGDB;
		case DBType.STARDB:
			return Dialect.STARDB;
		case DBType.IMPALA:
			return Dialect.IMPALA;
		case DBType.H2:
			return Dialect.H2;
		case DBType.OSCAR:
			return Dialect.OSCAR;
		case DBType.VASTBASE:
			return Dialect.VASTBASE;
		case DBType.DORIS:
			return Dialect.DORIS;
		case DBType.STARROCKS:
			return Dialect.STARROCKS;
		default:
			return Dialect.UNDEFINE;
		}
	}

	/**
	 * @param dbType
	 * @return 获取数据库对应的nvl函数
	 */
	public static String getNvlFunction(Integer dbType) {
		switch (dbType) {
		case DBType.DB2:
			return "nvl";
		case DBType.ORACLE:
		case DBType.ORACLE11:
			return "nvl";
		case DBType.POSTGRESQL:
		case DBType.POSTGRESQL14:
			return "COALESCE";
		case DBType.MYSQL:
		case DBType.MYSQL57:
		case DBType.DORIS:
		case DBType.STARROCKS:
			return "ifnull";
		case DBType.SQLSERVER:
			return "isnull";
		case DBType.SQLITE:
			return "ifnull";
		case DBType.CLICKHOUSE:
			return "ifnull";
		case DBType.TIDB:
			return "ifnull";
		case DBType.OCEANBASE:
			return "nvl";
		case DBType.DM:
			return "nvl";
		case DBType.GAUSSDB:
		case DBType.OPENGAUSS:
		case DBType.MOGDB:
		case DBType.STARDB:
		case DBType.OSCAR:
		case DBType.VASTBASE:
			return "nvl";
		case DBType.KINGBASE:
			return "nvl";
		case DBType.IMPALA:
			return "ifnull";
		case DBType.H2:
			return "COALESCE";
		default:
			return "nvl";
		}
	}

	/**
	 * 默认postgresql、gaussdb、mogdb、vastbase要转小写
	 * 
	 * @param dbType
	 * @return
	 */
	public static CaseType getReturnPrimaryKeyColumnCase(Integer dbType) {
		String dialect = getDialect(dbType);
		if (SqlToyConstants.dialectReturnPrimaryColumnCase != null) {
			String caseType = SqlToyConstants.dialectReturnPrimaryColumnCase.get(dialect);
			if (caseType != null) {
				return CaseType.getCaseType(caseType);
			}
		}
		// postgresql系列数据库默认转小写
		if (dbType == DBType.POSTGRESQL || dbType == DBType.POSTGRESQL14) {
			return CaseType.LOWER;
		}
		return CaseType.DEFAULT;
	}

	/**
	 * 单行记录插入需要返回主键值时,主键字段名称是否需要大小写转换，postgresql要转小写
	 * 
	 * @param columnName
	 * @param dbType
	 * @return
	 */
	public static String getReturnPrimaryKeyColumn(String columnName, Integer dbType) {
		CaseType caseType = getReturnPrimaryKeyColumnCase(dbType);
		if (caseType == CaseType.UPPER) {
			return columnName.toUpperCase(Locale.ROOT);
		} else if (caseType == CaseType.LOWER) {
			return columnName.toLowerCase(Locale.ROOT);
		}
		return columnName;
	}

	/**
	 * 数据库是否支持where (code,type) in ((:codeList,:typeList)) 多字段in场景
	 * 
	 * @param dbType
	 * @return
	 */
	public static boolean isSupportMultiFieldIn(Integer dbType) {
		// 通过sqltoy.close.multiFieldIn 参数关闭多字段in,避免当前数据库不支持多字段in
		if (SqlToyConstants.closeMultiFieldIn()) {
			return false;
		}
		if (dbType == DBType.MYSQL || dbType == DBType.POSTGRESQL || dbType == DBType.GAUSSDB
				|| dbType == DBType.SQLSERVER || dbType == DBType.ORACLE || dbType == DBType.DM || dbType == DBType.TIDB
				|| dbType == DBType.KINGBASE || dbType == DBType.MOGDB || dbType == DBType.STARDB
				|| dbType == DBType.OSCAR || dbType == DBType.OPENGAUSS || dbType == DBType.VASTBASE
				|| dbType == DBType.POSTGRESQL14 || dbType == DBType.CLICKHOUSE || dbType == DBType.H2
				|| dbType == DBType.SQLITE || dbType == DBType.ORACLE11) {
			return true;
		}
		// update 2025-10-13 starrocks不支持
		return false;
	}
}
