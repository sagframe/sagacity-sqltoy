package org.sagacity.sqltoy.utils;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.lang.reflect.Field;
import java.lang.reflect.Proxy;
import java.nio.file.Files;
import java.nio.file.Path;
import java.sql.Connection;
import java.sql.DatabaseMetaData;
import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.regex.Pattern;

import javax.sql.DataSource;

import org.junit.jupiter.api.Test;
import org.sagacity.sqltoy.SqlToyContext;
import org.sagacity.sqltoy.config.model.EntityMeta;
import org.sagacity.sqltoy.config.model.FieldMeta;

/**
 * 性能修复批次回归：正则编译缓存(行为等价+缓存命中)、ExpressionUtil常量正则、
 * DataSourceUtils数据源weak缓存、convertSqlMap容量上限、FileUtil摘要StringBuilder
 */
public class PerfRegressionBatchTest {

	// ============ 1. StringUtil 正则编译缓存 ============

	@Test
	public void stringRegexOverloadsBehaviorUnchanged() {
		assertTrue(StringUtil.matches("hello123", "\\d+"));
		assertFalse(StringUtil.matches("hello", "\\d+"));
		assertEquals(5, StringUtil.matchIndex("hello123", "\\d+"));
		assertArrayEquals(new int[] { 5, 8 }, StringUtil.matchIndex("hello123", "\\d+", 0));
		// "1a22b333"中"22"唯一出现在下标2
		assertEquals(2, StringUtil.matchLastIndex("1a22b333", "22"));
		assertEquals(3, StringUtil.matchCnt("a1b2c3", "\\d"));
		assertEquals(2, StringUtil.matchCnt("a1b2c3", "\\d", 0, 4));
		assertEquals(2, StringUtil.matchCnt("a1b2c3", "\\d", 0, 4, 0));
	}

	@SuppressWarnings("unchecked")
	private static ConcurrentHashMap<String, Pattern> patternCache() throws Exception {
		Field field = StringUtil.class.getDeclaredField("PATTERN_CACHE");
		field.setAccessible(true);
		return (ConcurrentHashMap<String, Pattern>) field.get(null);
	}

	@Test
	public void regexCompiledPatternIsCached() throws Exception {
		// 同一regex重复调用必须复用同一个Pattern实例(证明缓存生效,而非每次compile)
		String regex = "perf-cache-probe-\\d+";
		StringUtil.matches("abc123", regex);
		Pattern first = patternCache().get(regex);
		assertTrue(first != null, "正则应进入编译缓存");
		StringUtil.matches("abc456", regex);
		assertSame(first, patternCache().get(regex));
	}

	// ============ 2. ExpressionUtil 常量正则 ============

	@Test
	public void expressionFunctionPatternUnchanged() throws Exception {
		assertTrue(ExpressionUtil.isFunctionCal("sqrt(10)"));
		assertFalse(ExpressionUtil.isFunctionCal("plainValue"));
		assertEquals(String.valueOf(Math.sqrt(4)), ExpressionUtil.getValue("sqrt(4)"));
		assertEquals("plainValue", ExpressionUtil.getValue("plainValue"));
	}

	// ============ 3. DataSourceUtils 数据源weak缓存 ============

	@Test
	public void dataSourceDbTypeCachedPerDataSourceInstance() {
		SqlToyContext context = new SqlToyContext() {
			@Override
			public Connection getConnection(DataSource dataSource) {
				return (Connection) Proxy.newProxyInstance(getClass().getClassLoader(),
						new Class[] { Connection.class }, (proxy, method, args) -> {
							if ("getMetaData".equals(method.getName())) {
								return Proxy.newProxyInstance(getClass().getClassLoader(),
										new Class[] { DatabaseMetaData.class }, (metaProxy, metaMethod, metaArgs) -> {
											if ("getDatabaseProductName".equals(metaMethod.getName())) {
												return "MySQL";
											}
											if ("getDatabaseMajorVersion".equals(metaMethod.getName())) {
												return 8;
											}
											return null;
										});
							}
							if (method.getReturnType() == boolean.class) {
								return false;
							}
							if (method.getReturnType() == int.class) {
								return 0;
							}
							return null;
						});
			}

			@Override
			public void releaseConnection(Connection conn, DataSource dataSource) {
			}
		};
		DataSource dataSource = (DataSource) Proxy.newProxyInstance(getClass().getClassLoader(),
				new Class[] { DataSource.class }, (proxy, method, args) -> {
					if ("hashCode".equals(method.getName())) {
						return System.identityHashCode(proxy);
					}
					if ("equals".equals(method.getName())) {
						return proxy == args[0];
					}
					if ("toString".equals(method.getName())) {
						return "stubDataSource";
					}
					if (method.getReturnType() == boolean.class) {
						return false;
					}
					if (method.getReturnType() == int.class) {
						return 0;
					}
					return null;
				});
		int dbType = DataSourceUtils.getDBType(context, dataSource);
		assertEquals(DataSourceUtils.DBType.MYSQL, dbType);
		// 第二次调用命中缓存,结果一致
		assertEquals(dbType, DataSourceUtils.getDBType(context, dataSource));
		assertEquals("mysql", DataSourceUtils.getDialect(context, dataSource));
	}

	// ============ 4. convertSqlMap 容量上限 ============

	@SuppressWarnings("unchecked")
	@Test
	public void convertSqlCacheStopsGrowingBeyondLimit() throws Exception {
		Field field = SqlUtil.class.getDeclaredField("convertSqlMap");
		field.setAccessible(true);
		ConcurrentHashMap<String, String> cache = (ConcurrentHashMap<String, String>) field.get(null);
		cache.clear();
		EntityMeta entityMeta = new EntityMeta();
		entityMeta.setTableName("t_user_info");
		entityMeta.setFieldsArray(new String[] { "userName" });
		FieldMeta fieldMeta = new FieldMeta("userName", "USER_NAME", null, null, java.sql.Types.VARCHAR, true, false,
				50, 0, 0);
		entityMeta.addFieldMeta(fieldMeta);
		String converted = SqlUtil.convertFieldsToColumns(entityMeta, "userName like :userName");
		assertTrue(converted.contains("USER_NAME"), "属性名应转换为列名,实际:" + converted);
		assertTrue(cache.size() >= 1, "未超限时应当缓存");
		assertEquals(converted, SqlUtil.convertFieldsToColumns(entityMeta, "userName like :userName"));
		// 填满至上限后再转换新sql:结果仍正确,但缓存不再增长
		for (int i = cache.size(); i < 2000; i++) {
			cache.put("perf-dummy-" + i, "x");
		}
		assertEquals(2000, cache.size());
		String convertedAgain = SqlUtil.convertFieldsToColumns(entityMeta, "userName like :userName and status=1");
		assertTrue(convertedAgain.contains("USER_NAME"));
		assertEquals(2000, cache.size(), "超限后不应再写入缓存");
	}

	// ============ 5. FileUtil 摘要 StringBuilder ============

	@Test
	public void fileDigestValueUnchanged() throws Exception {
		Path file = Files.createTempFile("sqltoy-digest", ".txt");
		try {
			Files.write(file, "hello".getBytes("UTF-8"));
			// "hello"的MD5固定值,验证StringBuilder改写不改变摘要输出
			assertEquals("5d41402abc4b2a76b9719d911017c592", FileUtil.getFileMessageDigest(file.toString(), "MD5"));
		} finally {
			Files.deleteIfExists(file);
		}
	}

	private static void assertArrayEquals(int[] expected, int[] actual) {
		org.junit.jupiter.api.Assertions.assertArrayEquals(expected, actual);
	}
}
