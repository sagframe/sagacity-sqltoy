package org.sagacity.sqltoy.dialect.utils;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.Test;
import org.sagacity.sqltoy.model.LockMode;

/**
 * @description SQL Server lockSql 边缘矩阵:无from、方括号/schema、别名、派生表、CTE、
 *              函数from、注释、join、锁模式、已存在锁等场景。
 * @author zhongxuchen
 */
public class SqlServerLockSqlEdgeTest {

	private static String lock(String sql, String tableName) {
		return SqlServerDialectUtils.lockSql(sql, tableName, LockMode.UPGRADE);
	}

	@Test
	public void plainSelectWithoutFromThrowsClearException() {
		IllegalArgumentException ex = assertThrows(IllegalArgumentException.class,
				() -> SqlServerDialectUtils.lockSql("select 1", null, LockMode.UPGRADE));
		assertTrue(ex.getMessage().contains("from clause"), ex.getMessage());
	}

	@Test
	public void functionOnlyFromThrowsClearException() {
		// trim(... from name)中的from不是from子句,不能把name当表加锁
		IllegalArgumentException ex = assertThrows(IllegalArgumentException.class,
				() -> SqlServerDialectUtils.lockSql("select trim(' ' from name)", null, LockMode.UPGRADE));
		assertTrue(ex.getMessage().contains("from clause"), ex.getMessage());
	}

	@Test
	public void fromInsideLiteralOrCommentDoesNotCount() {
		assertThrows(IllegalArgumentException.class,
				() -> SqlServerDialectUtils.lockSql("select 'from t' as x", null, LockMode.UPGRADE));
		assertThrows(IllegalArgumentException.class,
				() -> SqlServerDialectUtils.lockSql("select 1 /* from t */", null, LockMode.UPGRADE));
	}

	@Test
	public void bracketTableAndAlias() {
		assertEquals("select [user_id] from [user] with (rowlock xlock) where [user_id]=?",
				lock("select [user_id] from [user] where [user_id]=?", null));
		assertEquals("select * from [user] u with (rowlock xlock) where u.id=?",
				lock("select * from [user] u where u.id=?", null));
	}

	@Test
	public void schemaBracketVariants() {
		assertEquals("select * from [dbo].[sys_user] u with (rowlock xlock) where u.id=?",
				lock("select * from [dbo].[sys_user] u where u.id=?", null));
		assertEquals("select * from [dbo].sys_user u with (rowlock xlock) where u.id=?",
				lock("select * from [dbo].sys_user u where u.id=?", null));
		assertEquals("select * from dbo.[sys_user] u with (rowlock xlock) where u.id=?",
				lock("select * from dbo.[sys_user] u where u.id=?", null));
		assertEquals("select * from sys_user u with (rowlock xlock) where u.id=?",
				lock("select * from sys_user u where u.id=?", "dbo.sys_user"));
	}

	@Test
	public void aliasForms() {
		assertEquals("select * from t as u with (rowlock xlock) where u.id=?",
				lock("select * from t as u where u.id=?", null));
		assertEquals("select * from t [u] with (rowlock xlock) where [u].id=?",
				lock("select * from t [u] where [u].id=?", null));
		assertEquals("select * from t \"u\" with (rowlock xlock) where \"u\".id=?",
				lock("select * from t \"u\" where \"u\".id=?", null));
	}

	@Test
	public void lockModes() {
		assertEquals("select * from t with (rowlock xlock nowait)",
				SqlServerDialectUtils.lockSql("select * from t", null, LockMode.UPGRADE_NOWAIT));
		assertEquals("select * from t with (rowlock readpast)",
				SqlServerDialectUtils.lockSql("select * from t", null, LockMode.UPGRADE_SKIPLOCK));
	}

	@Test
	public void existingLockIsNotDuplicated() {
		assertEquals("select * from t with (nolock)",
				lock("select * from t with (nolock)", null));
		assertEquals("select * from t with (rowlock xlock)",
				lock("select * from t with (rowlock xlock)", null));
		assertEquals("select * from t with (updlock, rowlock)",
				lock("select * from t with (updlock, rowlock)", null));
		assertEquals("select * from t u with (nolock)",
				lock("select * from t u with (nolock)", null));
		// 旧式表提示
		assertEquals("select * from t (nolock)",
				lock("select * from t (nolock)", null));
	}

	@Test
	public void nonLockTableHintIsMerged() {
		assertEquals("select * from t with (index(idx), rowlock, xlock)",
				lock("select * from t with (index(idx))", null));
		assertEquals("select * from t u with (index(idx), rowlock, xlock)",
				lock("select * from t u with (index(idx))", null));
	}

	@Test
	public void lockHintOnSubqueryDoesNotSuppressTargetLock() {
		String sql = "select * from t where id in (select id from u with (nolock))";
		String result = lock(sql, null);
		assertTrue(result.contains("from t with (rowlock xlock)"), result);
		assertTrue(result.contains("u with (nolock)"), result);
	}

	@Test
	public void lockHintOnJoinedTableDoesNotSuppressTargetLock() {
		String sql = "select * from t join u with (nolock) on t.id=u.id";
		String result = lock(sql, null);
		assertTrue(result.contains("from t with (rowlock xlock)"), result);
		assertTrue(result.contains("u with (nolock)"), result);
	}

	@Test
	public void commentAndLiteralLockTextDoesNotSuppressLock() {
		String sql = "select * from t -- with (nolock)\n where note='with (nolock)'";
		String result = lock(sql, null);
		assertTrue(result.contains("from t with (rowlock xlock)"), result);
		assertTrue(result.contains("'with (nolock)'"), result);
	}

	@Test
	public void joinLocksFirstTableOnly() {
		String sql = "select * from t1 a join t2 b on a.id=b.id";
		assertEquals("select * from t1 a with (rowlock xlock) join t2 b on a.id=b.id", lock(sql, null));
		// lock只认第一个from后的表,tableName不覆盖实际SQL
		assertEquals("select * from t1 a with (rowlock xlock) join t2 b on a.id=b.id", lock(sql, "t2"));
	}

	@Test
	public void commaSeparatedLocksFirstTable() {
		String sql = "select * from t1 a, t2 b where a.id=b.id";
		assertEquals("select * from t1 a with (rowlock xlock), t2 b where a.id=b.id", lock(sql, null));
	}

	@Test
	public void derivedTableRejectedClearly() {
		IllegalArgumentException ex = assertThrows(IllegalArgumentException.class,
				() -> lock("select * from (select * from t) x", null));
		assertTrue(ex.getMessage().contains("reference a table directly"), ex.getMessage());
	}

	@Test
	public void nestedDerivedTableRejectedClearly() {
		IllegalArgumentException ex = assertThrows(IllegalArgumentException.class,
				() -> lock("select * from (select * from (select * from t) a) b", null));
		assertTrue(ex.getMessage().contains("reference a table directly"), ex.getMessage());
	}

	@Test
	public void cteUsesFirstTopLevelFromTable() {
		// lock不推演CTE,只按第一个顶层from后的表引用处理
		String sql = "with cte as (select * from t) select * from cte";
		assertTrue(lock(sql, null).contains("from cte with (rowlock xlock)"));
	}

	@Test
	public void commentsAroundTableReference() {
		// lockSql复用clearMark,注释在生成的可执行sql中被清理
		String block = lock("select * from /* c */ [user] u where u.id=?", null);
		assertTrue(block.contains("[user] u with (rowlock xlock)"), block);
		String line = lock("select * from -- c\n t u where u.id=?", null);
		assertTrue(line.contains("t u with (rowlock xlock)"), line);
	}

	@Test
	public void caseInsensitiveKeywordsAndIdentifiers() {
		assertEquals("SELECT * FROM [User] AS U with (rowlock xlock) WHERE U.id=?",
				lock("SELECT * FROM [User] AS U WHERE U.id=?", "user"));
	}

	@Test
	public void trailingSemicolon() {
		// clearMark会清理末尾分号,JDBC执行不需要保留
		assertEquals("select * from t with (rowlock xlock)", lock("select * from t;", null));
	}

	@Test
	public void tableValuedFunctionThrowsClearException() {
		IllegalArgumentException ex = assertThrows(IllegalArgumentException.class,
				() -> lock("select * from openjson(@j)", null));
		assertTrue(ex.getMessage().contains("cannot determine"), ex.getMessage());
	}

	@Test
	public void tableNameDoesNotOverrideFirstFromTable() {
		assertEquals("select * from t with (rowlock xlock)", lock("select * from t", "other"));
	}
}
