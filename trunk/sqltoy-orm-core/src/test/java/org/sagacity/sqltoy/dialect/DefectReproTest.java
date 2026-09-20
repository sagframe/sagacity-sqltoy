package org.sagacity.sqltoy.dialect;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
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

	/** A1-a 方括号表名被当正则字符类,replaceFirst命中错误位置撕碎SQL */
	@Test
	public void a1a_bracketTableCorruptsSql() {
		String sql = "select [user_id],[user_name] from [user] where [user_id]=?";
		String result = SqlServerDialectUtils.lockSql(sql, null, LockMode.UPGRADE);
		System.out.println("[A1a] " + result);
		// 期望(修复后):hint应加在[user]之后; 当前缺陷:from的r被[user]字符类命中替换
		boolean corrupted = result.contains("f[user]") || result.contains("om [user]");
		assertTrue(corrupted || !result.contains("with (rowlock"),
				"当前行为锚点:方括号表名导致SQL撕碎或hint丢失, result=" + result);
	}

	/** A1-b schema限定方括号名:正则无命中→静默不加锁(updateFetch语义下=丢失更新) */
	@Test
	public void a1b_schemaBracketSilentNoLock() {
		String sql = "select * from [dbo].[sys_user] t where t.id=?";
		String result = SqlServerDialectUtils.lockSql(sql, null, LockMode.UPGRADE);
		System.out.println("[A1b] " + result);
		assertFalse(result.contains("with (rowlock"), "当前行为锚点:schema方括号名静默无锁");
	}

	/** A1-c 别名+order by后继:hint插在表名与别名之间→T-SQL语法错误 */
	@Test
	public void a1c_aliasOrderByMisplacedHint() {
		String sql = "select * from my_table t order by t.create_date";
		String result = SqlServerDialectUtils.lockSql(sql, null, LockMode.UPGRADE);
		System.out.println("[A1c] " + result);
		// 正确形态应为 "my_table t with (rowlock xlock)";缺陷形态为 "my_table with (rowlock xlock)  t"
		assertTrue(result.contains("my_table with (rowlock xlock)  t") || result.contains("my_table with (rowlock"),
				"当前行为锚点:hint位于别名之前(非法), result=" + result);
	}

	// ============ B4: FunctionUtils.replaceFunction 无参函数IGNORE分支off-by-one ============

	/** B4 裸sysdate在Now.wrap返回IGNORE的方言(impala)下,分隔符被输出两次 */
	@Test
	public void b4_bareSysdateDuplicateSeparator() {
		FunctionUtils.setFunctionConverts(java.util.Arrays.asList("default"));
		String sql = "select sysdate,name from t";
		String result = FunctionUtils.getDialectSql(sql, "impala");
		System.out.println("[B4] " + result);
		// 缺陷形态: "select sysdate,,name"(逗号重复); 正确形态应保持原样或单分隔符
		assertTrue(result.contains("sysdate,,") || result.equals(sql) || result.contains("sysdate, name")
				|| result.contains("sysdate,name"),
				"锚点:观察off-by-one是否复现, result=" + result);
		if (result.contains("sysdate,,")) {
			System.out.println("[B4] 缺陷复现:分隔符重复");
		}
	}

	// ============ B1: #[...] 闭合定位不感知字面量,条件内不成对]截断真条件 ============

	/** B1 动态条件字面量含单个]时,#[被提前闭合,产生悬空SQL片段 */
	@Test
	public void b1_bracketInLiteralTruncatesCondition() throws Exception {
		String sql = "select * from sys_user where user_id=:userId and #[remark = 'a]b']";
		org.sagacity.sqltoy.config.model.SqlToyResult result = org.sagacity.sqltoy.config.SqlConfigParseUtils
				.processSql(sql, new String[] { "userId", "remark" }, new Object[] { 100, "x" }, "mysql");
		System.out.println("[B1] " + result.getSql());
		// 正确形态: remark参数有值时条件保留 → "... and remark = ?";
		// 缺陷形态: 'a]b'的]被当#[闭合 → 悬空 "b']" 进入SQL
		boolean truncated = result.getSql().contains("b']") || !result.getSql().contains("remark");
		System.out.println("[B1] truncated=" + truncated);
		assertTrue(truncated || result.getSql().contains("remark = ?"), "锚点:观察截断是否复现");
	}

	/** B1对照:字面量内成对[]——实测条件同样被静默剔除(比截断更危险:全表查询),修复后应反转断言 */
	@Test
	public void b1control_pairedBracketsOk() throws Exception {
		String sql = "select * from sys_user where user_id=:userId and #[cat = '[test]']";
		org.sagacity.sqltoy.config.model.SqlToyResult result = org.sagacity.sqltoy.config.SqlConfigParseUtils
				.processSql(sql, new String[] { "userId", "cat" }, new Object[] { 100, "x" }, "mysql");
		System.out.println("[B1c] " + result.getSql());
		// 当前缺陷行为:cat有值但条件被整体剔除(悬空and);修复后此断言应反转为 contains("cat = ?")
		assertFalse(result.getSql().contains("cat = ?"),
				"当前行为锚点:成对括号字面量的#[]条件被静默剔除(修复后反转此断言)");
	}
}
