package org.sagacity.sqltoy.utils;

import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;

import java.lang.reflect.Proxy;
import java.sql.Connection;
import java.sql.DatabaseMetaData;

import org.junit.jupiter.api.Test;
import org.sagacity.sqltoy.model.DBProfile;

/**
 * 回归测试:resolvePGobjectHolder按URL schema收窄PGobject探测范围(update 2026-9-17)
 * (a)PG系schema(postgresql/stardb)仍解析出同源驱动PGobject句柄,create可构造实例;
 * (b)非PG系schema(mysql/oracle)不再兜底探测org.postgresql.util.PGobject,直接占位句柄
 * (create返回null,由调用方回退setString/setObject(str,OTHER),json/vector绑定行为不变)
 */
public class DataSourceUtilsPgObjectHolderTest {

	/**
	 * 通过JDK动态代理构造支持getURL/getDatabaseProductName的Connection mock
	 */
	private static Connection mockConnection(final String url, final String productName) {
		DatabaseMetaData metaData = (DatabaseMetaData) Proxy.newProxyInstance(
				DataSourceUtilsPgObjectHolderTest.class.getClassLoader(), new Class<?>[] { DatabaseMetaData.class },
				(proxy, method, args) -> {
					if ("getURL".equals(method.getName())) {
						return url;
					}
					if ("getDatabaseProductName".equals(method.getName())) {
						return productName;
					}
					if ("getDatabaseMajorVersion".equals(method.getName())) {
						return 15;
					}
					return defaultAnswer(method.getName(), proxy, args);
				});
		return (Connection) Proxy.newProxyInstance(DataSourceUtilsPgObjectHolderTest.class.getClassLoader(),
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
	public void pgSchemaResolvesHolder() throws Exception {
		DBProfile profile = DataSourceUtils.getDBProfile(mockConnection("jdbc:postgresql://127.0.0.1:5432/test_pg",
				"PostgreSQL"));
		assertNotNull(profile.getPgObjectHolder().create("jsonb", "{}"), "postgresql schema应解析出PGobject句柄!");
	}

	@Test
	public void stardbSchemaStillResolvesHolder() throws Exception {
		DBProfile profile = DataSourceUtils.getDBProfile(
				mockConnection("jdbc:stardb://127.0.0.1:5432/test_stardb", "StarDB"));
		assertNotNull(profile.getPgObjectHolder().create("json", "{}"), "stardb schema应保留org.postgresql探测!");
	}

	@Test
	public void nonPGSchemaSkipsProbe() throws Exception {
		DBProfile mysqlProfile = DataSourceUtils
				.getDBProfile(mockConnection("jdbc:mysql://127.0.0.1:3306/test_mysql", "MySQL"));
		assertNull(mysqlProfile.getPgObjectHolder().create("json", "{}"), "mysql schema不应再兜底探测PGobject!");
		DBProfile oracleProfile = DataSourceUtils
				.getDBProfile(mockConnection("jdbc:oracle:thin:@127.0.0.1:1521:test_oracle", "Oracle"));
		assertNull(oracleProfile.getPgObjectHolder().create("json", "{}"), "oracle schema不应再兜底探测PGobject!");
	}
}
