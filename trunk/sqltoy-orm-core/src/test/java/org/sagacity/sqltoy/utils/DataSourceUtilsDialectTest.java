package org.sagacity.sqltoy.utils;

import static org.junit.jupiter.api.Assertions.assertEquals;

import java.lang.reflect.Proxy;
import java.sql.Connection;
import java.sql.DatabaseMetaData;
import java.util.HashMap;
import java.util.Map;

import org.junit.jupiter.api.Test;
import org.sagacity.sqltoy.model.IgnoreKeyCaseMap;

/**
 * 回归测试:getCurrentDBDialect数据库类型识别
 * (a)主流数据库驱动getDatabaseProductName实际返回值逐一识别准确;
 * (b)产品名为null时返回UNDEFINE不再NPE;
 * (c)dialectMap兜底匹配按key长度降序(最长优先),产品名同时命中多个key时结果稳定;
 * (d)dialectMap方言名映射(如oscar->oracle)保持生效
 */
public class DataSourceUtilsDialectTest {

	/**
	 * 通过JDK动态代理构造仅支持元数据方法的Connection mock
	 */
	private static Connection mockConnection(final String productName) {
		DatabaseMetaData metaData = (DatabaseMetaData) Proxy.newProxyInstance(
				DataSourceUtilsDialectTest.class.getClassLoader(), new Class<?>[] { DatabaseMetaData.class },
				(proxy, method, args) -> {
					if ("getDatabaseProductName".equals(method.getName())) {
						return productName;
					}
					if ("getDatabaseMajorVersion".equals(method.getName())) {
						return 0;
					}
					return defaultAnswer(method.getName(), proxy, args);
				});
		return (Connection) Proxy.newProxyInstance(DataSourceUtilsDialectTest.class.getClassLoader(),
				new Class<?>[] { Connection.class }, (proxy, method, args) -> {
					if ("getMetaData".equals(method.getName())) {
						return metaData;
					}
					return defaultAnswer(method.getName(), proxy, args);
				});
	}

	private static Object defaultAnswer(String methodName, Object proxy, Object[] args) {
		if ("toString".equals(methodName)) {
			return "mock";
		}
		if ("hashCode".equals(methodName)) {
			return System.identityHashCode(proxy);
		}
		if ("equals".equals(methodName)) {
			return proxy == args[0];
		}
		return null;
	}

	@Test
	public void dialectDetectionForEachProduct() throws Exception {
		Map<String, String> cases = new HashMap<String, String>();
		cases.put("Oracle", "oracle");
		cases.put("MySQL", "mysql");
		cases.put("MariaDB", "mysql");
		cases.put("PostgreSQL", "postgresql");
		// mssql-jdbc实际返回值
		cases.put("Microsoft SQL Server", "sqlserver");
		cases.put("DB2/LINUXX8664", "db2");
		cases.put("ClickHouse", "clickhouse");
		// 达梦驱动返回"DM DBMS"
		cases.put("DM DBMS", "dm");
		// TDengine官方驱动(2.0.39+/3.x)返回"TDengine"
		cases.put("TDengine", "tdengine");
		// 金仓kingbase8驱动返回"KingbaseES",不应被postgresql截胡
		cases.put("KingbaseES", "kingbase");
		cases.put("openGauss", "opengauss");
		cases.put("GaussDB", "gaussdb");
		cases.put("MogDB", "mogdb");
		cases.put("SQLite", "sqlite");
		cases.put("H2", "h2");
		cases.put("OSCAR", "oscar");
		// greenplum专用驱动,内部映射为postgresql方言
		cases.put("Greenplum", "postgresql");
		cases.put("Apache Doris", "doris");
		cases.put("StarRocks", "starrocks");
		cases.put("Impala", "impala");
		cases.put("OceanBase", "oceanbase");
		cases.put("Elasticsearch", "elastic");
		cases.put("Vastbase", "vastbase");
		for (Map.Entry<String, String> entry : cases.entrySet()) {
			assertEquals(entry.getValue(), DataSourceUtils.getCurrentDBDialect(mockConnection(entry.getKey())),
					"产品名:" + entry.getKey() + " 识别错误!");
		}
	}

	@Test
	public void nullProductNameReturnsUndefine() throws Exception {
		// 修复前:getDatabaseProductName返回null时replaceAll直接NPE
		// 常量已小写化为undefine,断言引用常量避免大小写硬编码
		assertEquals(org.sagacity.sqltoy.utils.DataSourceUtils.Dialect.UNDEFINE,
				DataSourceUtils.getCurrentDBDialect(mockConnection(null)));
	}

	@Test
	public void dialectMapLongestKeyMatchWins() throws Exception {
		IgnoreKeyCaseMap<String, String> backup = new IgnoreKeyCaseMap<String, String>(DataSourceUtils.dialectMap);
		try {
			DataSourceUtils.dialectMap.clear();
			// 产品名"GBase 8s"同时含两个key,最长key优先且结果稳定(不依赖ConcurrentHashMap迭代顺序)
			DataSourceUtils.dialectMap.put("gbase", "mysql");
			DataSourceUtils.dialectMap.put("gbase8s", "sqlserver");
			assertEquals("sqlserver", DataSourceUtils.getCurrentDBDialect(mockConnection("GBase 8s")));
		} finally {
			DataSourceUtils.dialectMap.clear();
			DataSourceUtils.dialectMap.putAll(backup);
		}
	}

	@Test
	public void dialectMapNameMappingStillApplied() throws Exception {
		IgnoreKeyCaseMap<String, String> backup = new IgnoreKeyCaseMap<String, String>(DataSourceUtils.dialectMap);
		try {
			DataSourceUtils.dialectMap.clear();
			// 识别出的方言名再次映射:oscar->oracle
			DataSourceUtils.dialectMap.put("oscar", "oracle");
			assertEquals("oracle", DataSourceUtils.getCurrentDBDialect(mockConnection("OSCAR")));
		} finally {
			DataSourceUtils.dialectMap.clear();
			DataSourceUtils.dialectMap.putAll(backup);
		}
	}
}
