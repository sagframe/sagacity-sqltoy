package org.sagacity.sqltoy.config;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;

import org.junit.jupiter.api.Test;
import org.sagacity.sqltoy.config.model.SqlToyConfig;
import org.sagacity.sqltoy.config.model.SqlType;

/**
 * 回归测试：sqlId方言变体查找对版本化方言的兼容回退
 * oracle11/postgresql14/mysql57 精确变体未命中时回退到基础方言命名的sql,
 * 精确版本变体存在时优先于回退变体
 */
public class SqlScriptLoaderDialectFallbackTest {

	private SqlScriptLoader loader = new SqlScriptLoader();

	private void put(String id, String sql) throws Exception {
		loader.putSqlToyConfig(new SqlToyConfig(id, sql));
	}

	/**
	 * oracle11 回退到 _oracle 命名(既有行为回归)
	 */
	@Test
	public void oracle11FallbackToOracle() throws Exception {
		put("q1_oracle", "select 1 as oracle_sql");
		SqlToyConfig result = loader.getSqlConfig("q1", SqlType.search, "oracle11", null, true);
		assertNotNull(result, "oracle11 should fallback to q1_oracle");
		assertEquals("select 1 as oracle_sql", result.getSql());
	}

	/**
	 * postgresql14 回退到 _postgresql 命名(修复前回退缺失导致静默使用通用sql)
	 */
	@Test
	public void postgresql14FallbackToPostgresql() throws Exception {
		put("q2_postgresql", "select 2 as pg_sql");
		SqlToyConfig result = loader.getSqlConfig("q2", SqlType.search, "postgresql14", null, true);
		assertNotNull(result, "postgresql14 should fallback to q2_postgresql");
		assertEquals("select 2 as pg_sql", result.getSql());
	}

	/**
	 * mysql57 回退到 _mysql 命名
	 */
	@Test
	public void mysql57FallbackToMysql() throws Exception {
		put("q3_mysql", "select 3 as mysql_sql");
		SqlToyConfig result = loader.getSqlConfig("q3", SqlType.search, "mysql57", null, true);
		assertNotNull(result, "mysql57 should fallback to q3_mysql");
		assertEquals("select 3 as mysql_sql", result.getSql());
	}

	/**
	 * dialect_sqlId 前缀形式的变体同样支持回退
	 */
	@Test
	public void prefixVariantFallback() throws Exception {
		put("postgresql_q4", "select 4 as prefix_pg");
		SqlToyConfig result = loader.getSqlConfig("q4", SqlType.search, "postgresql14", null, true);
		assertNotNull(result, "postgresql14 should fallback to postgresql_q4");
		assertEquals("select 4 as prefix_pg", result.getSql());
	}

	/**
	 * 精确版本变体优先于回退变体
	 */
	@Test
	public void exactVersionVariantTakesPrecedence() throws Exception {
		put("q5_postgresql14", "select 5 as pg14_exact");
		put("q5_postgresql", "select 5 as pg_fallback");
		SqlToyConfig result = loader.getSqlConfig("q5", SqlType.search, "postgresql14", null, true);
		assertEquals("select 5 as pg14_exact", result.getSql());
	}

	/**
	 * UNDEFINE表示未识别方言,等同于未指定:不应命中sqlId_undefine变体,直接使用通用sql
	 */
	@Test
	public void undefineDialectEqualsToBlank() throws Exception {
		put("q6", "select 6 as common_sql");
		put("q6_undefine", "select 6 as undefine_sql");
		// 常量为大小写混合的UNDEFINE,大小写两种形式都应被排除
		assertEquals("select 6 as common_sql", loader.getSqlConfig("q6", SqlType.search, "UNDEFINE", null, true).getSql());
		assertEquals("select 6 as common_sql", loader.getSqlConfig("q6", SqlType.search, "undefine", null, true).getSql());
	}
}
