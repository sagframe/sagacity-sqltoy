package org.sagacity.sqltoy.dialect;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.regex.Pattern;

import org.junit.jupiter.api.Test;
import org.sagacity.sqltoy.dialect.utils.DialectUtils;
import org.sagacity.sqltoy.dialect.utils.SqlServerDialectUtils;
import org.sagacity.sqltoy.model.LockMode;
import org.sagacity.sqltoy.utils.StringUtil;

public class SqlServerDialectTest {
	private static final Pattern ORDER_BY = Pattern.compile("(?i)\\Worder\\s*by\\W");

	@Test
	public void testPageSql() {
		String realSql = "select top partation( order by) from (select from table ) t1 where t1.name=?";
		StringBuilder sql = new StringBuilder(realSql);
		int orderByIndex = StringUtil.matchIndex(realSql, ORDER_BY);
		if (orderByIndex > 0) {
			String clearSql = DialectUtils.clearDisturbSql(realSql);
			orderByIndex = StringUtil.matchIndex(clearSql, ORDER_BY);
		}
		if (orderByIndex < 0) {
			sql.append(" order by NEWID() ");
		}
		System.err.println(sql.toString());
	}

	// ======================== lockSql tests ========================

	/**
	 * 无别名表名 -- 修复前会抛 ArrayIndexOutOfBoundsException
	 */
	@Test
	public void testLockSql_tableWithoutAlias() {
		String sql = "select * from my_table";
		String result = SqlServerDialectUtils.lockSql(sql, null, LockMode.UPGRADE);
		assertNotNull(result);
		assertTrue(result.toLowerCase().contains("with (rowlock xlock)"),
				"should contain rowlock xlock hint, got: " + result);
		System.err.println("testLockSql_tableWithoutAlias => " + result);
	}

	/**
	 * 无别名表名 + UPGRADE_NOWAIT
	 */
	@Test
	public void testLockSql_tableWithoutAlias_nowait() {
		String sql = "select * from my_table";
		String result = SqlServerDialectUtils.lockSql(sql, null, LockMode.UPGRADE_NOWAIT);
		assertNotNull(result);
		assertTrue(result.toLowerCase().contains("with (rowlock readpast)"),
				"should contain rowlock readpast hint, got: " + result);
	}

	/**
	 * 无别名表名 + UPGRADE_SKIPLOCK
	 */
	@Test
	public void testLockSql_tableWithoutAlias_skiplock() {
		String sql = "select * from my_table";
		String result = SqlServerDialectUtils.lockSql(sql, null, LockMode.UPGRADE_SKIPLOCK);
		assertNotNull(result);
		assertTrue(result.toLowerCase().contains("with (rowlock readpast)"),
				"should contain rowlock readpast hint, got: " + result);
	}

	/**
	 * 表别名 + where -- 走 where 分支
	 */
	@Test
	public void testLockSql_aliasWithWhere() {
		String sql = "select * from my_table t where t.id=?";
		String result = SqlServerDialectUtils.lockSql(sql, null, LockMode.UPGRADE);
		assertNotNull(result);
		assertTrue(result.toLowerCase().contains("t with (rowlock xlock)"),
				"lock hint should be after alias 't', got: " + result);
		System.err.println("testLockSql_aliasWithWhere => " + result);
	}

	/**
	 * 表别名使用 AS 关键字 -- 走 as 分支
	 */
	@Test
	public void testLockSql_aliasWithAs() {
		String sql = "select * from my_table as t where t.id=?";
		String result = SqlServerDialectUtils.lockSql(sql, null, LockMode.UPGRADE);
		assertNotNull(result);
		assertTrue(result.toLowerCase().contains("t with (rowlock xlock)"),
				"lock hint should be after 'as t', got: " + result);
		System.err.println("testLockSql_aliasWithAs => " + result);
	}

	/**
	 * 多表逗号分隔 -- 走 , 分支
	 */
	@Test
	public void testLockSql_aliasWithComma() {
		String sql = "select * from my_table t, other_table o where t.id=o.id";
		String result = SqlServerDialectUtils.lockSql(sql, null, LockMode.UPGRADE);
		assertNotNull(result);
		assertTrue(result.toLowerCase().contains("with (rowlock xlock)"), "should contain lock hint, got: " + result);
		System.err.println("testLockSql_aliasWithComma => " + result);
	}

	/**
	 * inner join -- 走 join 分支
	 */
	@Test
	public void testLockSql_aliasWithJoin() {
		String sql = "select * from my_table t inner join other_table o on t.id=o.id";
		String result = SqlServerDialectUtils.lockSql(sql, null, LockMode.UPGRADE);
		assertNotNull(result);
		assertTrue(result.toLowerCase().contains("with (rowlock xlock)"), "should contain lock hint, got: " + result);
		System.err.println("testLockSql_aliasWithJoin => " + result);
	}

	/**
	 * null lockMode 应直接返回原始SQL
	 */
	@Test
	public void testLockSql_nullLockMode() {
		String sql = "select * from my_table where id=?";
		String result = SqlServerDialectUtils.lockSql(sql, null, null);
		assertEquals(sql, result, "null lockMode should return original sql");
	}

	/**
	 * SQL 已包含 lock hint 应直接返回原始SQL
	 */
	@Test
	public void testLockSql_alreadyHasLock() {
		String sql = "select * from my_table with (rowlock xlock) where id=?";
		String result = SqlServerDialectUtils.lockSql(sql, null, LockMode.UPGRADE);
		assertEquals(sql, result, "already-locked sql should return as-is");
	}

	/**
	 * 指定 tableName 参数
	 */
	@Test
	public void testLockSql_explicitTableName() {
		String sql = "select * from my_table where id=?";
		String result = SqlServerDialectUtils.lockSql(sql, "my_table", LockMode.UPGRADE);
		assertNotNull(result);
		assertTrue(result.toLowerCase().contains("with (rowlock xlock)"), "should contain lock hint, got: " + result);
		System.err.println("testLockSql_explicitTableName => " + result);
	}

	/**
	 * 带列名的复杂SQL
	 */
	@Test
	public void testLockSql_complexSelect() {
		String sql = "select t.id, t.name, t.create_time from my_table t where t.status=? and t.type=?";
		String result = SqlServerDialectUtils.lockSql(sql, null, LockMode.UPGRADE);
		assertNotNull(result);
		assertTrue(result.toLowerCase().contains("with (rowlock xlock)"),
				"complex select should contain lock hint, got: " + result);
		System.err.println("testLockSql_complexSelect => " + result);
	}
}
