package org.sagacity.sqltoy.utils;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.sagacity.sqltoy.utils.DataSourceUtils.DBType;

/**
 * 保留字转义与SqlUtil纯函数(validateInArg/hasLock)矩阵:
 * 1.convertWord按库族选择转义符(sqlserver/sqlite方括号,mysql反引号,pg/oracle族双引号);
 * 2.validateInArg的合法/非法形态;
 * 3.hasLock对已含for update的sql短路。
 */
public class ReservedWordsAndSqlUtilTest {

	@BeforeEach
	public void setUp() {
		// 注入跨库通用关键字(name/flag多数库非保留字,order/status在部分库有歧义,用典型保留字验证)
		ReservedWordsUtil.put("order");
		ReservedWordsUtil.put("desc");
	}

	@AfterEach
	public void tearDown() {
		ReservedWordsUtil.clear();
	}

	@Test
	public void convertWordByFamily() {
		assertEquals("[order]", ReservedWordsUtil.convertWord("order", DBType.SQLSERVER), "sqlserver方括号");
		assertEquals("[order]", ReservedWordsUtil.convertWord("order", DBType.SQLITE), "sqlite方括号");
		assertEquals("`order`", ReservedWordsUtil.convertWord("order", DBType.MYSQL), "mysql反引号");
		assertEquals("`order`", ReservedWordsUtil.convertWord("order", DBType.TIDB), "tidb反引号");
		assertEquals("\"order\"", ReservedWordsUtil.convertWord("order", DBType.ORACLE), "oracle双引号");
		assertEquals("\"order\"", ReservedWordsUtil.convertWord("order", DBType.POSTGRESQL), "pg双引号");
		assertEquals("\"order\"", ReservedWordsUtil.convertWord("order", DBType.KINGBASE), "kingbase双引号");
	}

	@Test
	public void nonKeywordPassesThrough() {
		assertEquals("staff_name", ReservedWordsUtil.convertWord("staff_name", DBType.MYSQL), "非保留字原样");
	}

	@Test
	public void validateInArgForms() {
		// 合法:引号包裹逗号分隔
		assertTrue(SqlUtil.validateInArg("'a','b'"), "引号逗号分隔合法");
		assertTrue(SqlUtil.validateInArg("'a'"), "单引号项合法");
		// 数字逗号分隔合法
		assertTrue(SqlUtil.validateInArg("1,2,3"), "数字逗号分隔合法");
		// 单个数字不合法(回退参数化)
		assertFalse(SqlUtil.validateInArg("123"), "单个数字应回退参数化");
		// 逗号开头/结尾不合法
		assertFalse(SqlUtil.validateInArg(",1,2"), "逗号开头不合法");
		assertFalse(SqlUtil.validateInArg("1,2,"), "逗号结尾不合法");
		// 引号包裹与裸数字混用不合法
		assertFalse(SqlUtil.validateInArg("'a',1"), "引号与数字混用不合法");
	}

	@Test
	public void hasLockShortCircuits() {
		assertTrue(SqlUtil.hasLock("select * from t for update", DBType.MYSQL), "已含for update应识别");
		assertTrue(SqlUtil.hasLock("select * from t for update nowait", DBType.ORACLE), "已含nowait应识别");
		assertFalse(SqlUtil.hasLock("select * from t where id=1", DBType.MYSQL), "无锁语句应返回false");
	}

	@Test
	public void getLockSqlForms() {
		// UPGRADE_NOWAIT
		assertEquals(" for update nowait ",
				DefaultDialectUtils_getLockSql(DBType.ORACLE, org.sagacity.sqltoy.model.LockMode.UPGRADE_NOWAIT, 0, false, null, false));
		// 默认UPGRADE
		assertEquals(" for update ",
				DefaultDialectUtils_getLockSql(DBType.MYSQL, org.sagacity.sqltoy.model.LockMode.UPGRADE, 0, false, null, false));
		// UPGRADE带等待秒(mysql系由setSessionLockWait承担,appendWaitSeconds=false)
		assertEquals(" for update ",
				DefaultDialectUtils_getLockSql(DBType.MYSQL, org.sagacity.sqltoy.model.LockMode.UPGRADE, 5, false, null, false));
		// appendWaitSeconds=true输出wait子句
		assertEquals(" for update  wait 5",
				DefaultDialectUtils_getLockSql(DBType.ORACLE, org.sagacity.sqltoy.model.LockMode.UPGRADE, 5, false, null, true));
		// SKIPLOCKED由supportSkipLocked门控
		assertEquals(" for update skip locked ",
				DefaultDialectUtils_getLockSql(DBType.MYSQL, org.sagacity.sqltoy.model.LockMode.UPGRADE_SKIPLOCK, 0, true, " for update skip locked ", false));
	}

	/** getLockSql在DefaultDialectUtils为public static,直接引用 */
	private static String DefaultDialectUtils_getLockSql(Integer dbType,
			org.sagacity.sqltoy.model.LockMode lockMode, int lockWaitTimeout, boolean supportSkipLocked,
			String skipLockedSuffix, boolean appendWaitSeconds) {
		return org.sagacity.sqltoy.dialect.utils.DefaultDialectUtils.getLockSql(null, dbType, lockMode,
				lockWaitTimeout, supportSkipLocked, skipLockedSuffix, appendWaitSeconds);
	}
}
