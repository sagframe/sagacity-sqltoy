package org.sagacity.sqltoy.dialect;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.Test;
import org.sagacity.sqltoy.dialect.utils.SqlServerDialectUtils;
import org.sagacity.sqltoy.model.LockMode;
import org.sagacity.sqltoy.plugins.function.FunctionUtils;

/**
 * 6.0全面排查:高严重度可疑点的最小复现锚点(2026-9-18审计)。
 * 每个用例锁定一个已确认缺陷的当前行为,修复后应更新断言为正确行为。
 */
public class DefectReproTest {

	// ============ A1: SqlServerDialectUtils.lockSql 表名未转义+别名分支不全 ============

	/** A1-a 方括号表名必须按标识符处理,不能被当正则字符类撕碎SQL */
	@Test
	public void a1a_bracketTableKeepsSql() {
		String sql = "select [user_id],[user_name] from [user] where [user_id]=?";
		String result = SqlServerDialectUtils.lockSql(sql, null, LockMode.UPGRADE);
		System.out.println("[A1a] " + result);
		assertEquals("select [user_id],[user_name] from [user] with (rowlock xlock) where [user_id]=?", result);
	}

	/** A1-b schema限定方括号名必须能定位并加锁,不能静默无锁 */
	@Test
	public void a1b_schemaBracketGetsLock() {
		String sql = "select * from [dbo].[sys_user] t where t.id=?";
		String result = SqlServerDialectUtils.lockSql(sql, null, LockMode.UPGRADE);
		System.out.println("[A1b] " + result);
		assertEquals("select * from [dbo].[sys_user] t with (rowlock xlock) where t.id=?", result);
	}

	/** A1-c 别名+order by:表提示必须位于别名之后 */
	@Test
	public void a1c_aliasOrderByHintAfterAlias() {
		String sql = "select * from my_table t order by t.create_date";
		String result = SqlServerDialectUtils.lockSql(sql, null, LockMode.UPGRADE);
		System.out.println("[A1c] " + result);
		assertEquals("select * from my_table t with (rowlock xlock) order by t.create_date", result);
	}

	/** A1-d 无from属于非法加锁诉求:抛明确异常,不能裸StringIndexOutOfBoundsException */
	@Test
	public void a1d_noFromThrowsClearException() {
		IllegalArgumentException ex = assertThrows(IllegalArgumentException.class,
				() -> SqlServerDialectUtils.lockSql("select 1", null, LockMode.UPGRADE));
		System.out.println("[A1d] " + ex.getMessage());
		assertTrue(ex.getMessage().contains("from clause"), "异常信息应说明缺少from: " + ex.getMessage());
	}

	/** A1-e 派生表不属于lock简单语境:明确报不支持,不能PatternSyntaxException */
	@Test
	public void a1e_derivedTableRejectedClearly() {
		IllegalArgumentException ex = assertThrows(IllegalArgumentException.class,
				() -> SqlServerDialectUtils.lockSql("select * from (select * from t) x", null, LockMode.UPGRADE));
		System.out.println("[A1e] " + ex.getMessage());
		assertTrue(ex.getMessage().contains("reference a table directly"), ex.getMessage());
	}

	/** A1-f 显式tableName:支持schema方括号并加在别名后 */
	@Test
	public void a1f_explicitSchemaTableName() {
		String sql = "select * from [dbo].[sys_user] u where u.id=?";
		String result = SqlServerDialectUtils.lockSql(sql, "dbo.sys_user", LockMode.UPGRADE);
		System.out.println("[A1f] " + result);
		assertEquals("select * from [dbo].[sys_user] u with (rowlock xlock) where u.id=?", result);
	}

	/** A1-g lock只认第一个from后的表,tableName不覆盖SQL实际表 */
	@Test
	public void a1g_firstFromTableWins() {
		String result = SqlServerDialectUtils.lockSql("select * from t", "other", LockMode.UPGRADE);
		System.out.println("[A1g] " + result);
		assertEquals("select * from t with (rowlock xlock)", result);
	}

	// ============ B4: FunctionUtils.replaceFunction 无参函数IGNORE分支off-by-one ============

	/** B4 裸sysdate在Now.wrap返回IGNORE的方言(impala)下,分隔符被输出两次
	 * (update 2026-10-4 缺陷已修:IGNORE分支无参形态续接改从endMarkIndex+1起,精确锁定原样) */
	@Test
	public void b4_bareSysdateDuplicateSeparator() {
		FunctionUtils.setFunctionConverts(java.util.Arrays.asList("default"));
		String sql = "select sysdate,name from t";
		String result = FunctionUtils.getDialectSql(sql, "impala");
		System.out.println("[B4] " + result);
		assertEquals(sql, result, "裸形态IGNORE应原样保留(分隔符不得重复)");
	}

	// ============ B1: 字面量内完整 #[] 按成对片段处理(暂不做字面量感知) ============

	/** 字面量里的完整 #[] 不能破坏后续正常 #[] 的配对与参数解析 */
	@Test
	public void b1_completePseudoMarkInLiteralKeepsFollowingCondition() throws Exception {
		String sql = "select * from sys_user where remark='#[literal]' and user_id=:userId and #[name=:name]";
		org.sagacity.sqltoy.config.model.SqlToyResult result = org.sagacity.sqltoy.config.SqlConfigParseUtils
				.processSql(sql, new String[] { "userId", "name" }, new Object[] { 100, "n" }, "mysql");
		System.out.println("[B1] " + result.getSql());
		assertTrue(result.getSql().contains("user_id"), "正常条件不应受影响: " + result.getSql());
		assertTrue(result.getSql().contains("name=?"), "后续#[]条件应正常处理: " + result.getSql());
	}
}
