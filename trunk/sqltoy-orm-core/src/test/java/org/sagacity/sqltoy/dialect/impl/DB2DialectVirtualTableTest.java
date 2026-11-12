package org.sagacity.sqltoy.dialect.impl;

import static org.junit.jupiter.api.Assertions.assertEquals;

import java.lang.reflect.Method;

import org.junit.jupiter.api.Test;
import org.sagacity.sqltoy.SqlToyConstants;

/**
 * DB2 无FROM查询补虚表 + 隔离后缀的回归测试(反射调用私有方法,无需真库)
 *
 * <p>
 * 历史问题:(1)FROM判定用裸正则,{@code select 'a from b'} 的字面量from被误判为已有FROM而漏补虚表;
 * (2)未区分顶层FROM,{@code select (select max(id) from t)} 因子查询内FROM而漏补虚表(顶层仍无FROM);
 * (3)已带"with ur"的语句再补虚表会拼成 {@code select 99 with ur FROM SYSIBM.SYSDUMMY1 with ur}。
 * 真库语义(DB2强制FROM,SQL0104)由容器用例覆盖,本类只锁SQL文本形态。
 */
public class DB2DialectVirtualTableTest {

	private static String appendWithUr(String sql) throws Exception {
		DB2Dialect dialect = new DB2Dialect();
		Method method = DB2Dialect.class.getDeclaredMethod("appendWithUR", String.class);
		method.setAccessible(true);
		return (String) method.invoke(dialect, sql);
	}

	/** 隔离后缀按全局配置拼装,断言不受配置影响 */
	private static String withUr(String expect) {
		return SqlToyConstants.db2WithUR() ? expect + " with ur" : expect;
	}

	@Test
	public void constantQueryGetsVirtualTable() throws Exception {
		assertEquals(withUr("select 99 FROM " + DB2Dialect.VIRTUAL_TABLE), appendWithUr("select 99"));
	}

	@Test
	public void literalContainingFromDoesNotCount() throws Exception {
		// 字面量中的from不得被当成已有FROM
		assertEquals(withUr("select 'a from b' FROM " + DB2Dialect.VIRTUAL_TABLE), appendWithUr("select 'a from b'"));
	}

	@Test
	public void subQueryOnlyFromIsNotTopLevel() throws Exception {
		// FROM只在子查询内:顶层仍无FROM,必须补虚表
		assertEquals(withUr("select (select max(id) from t) FROM " + DB2Dialect.VIRTUAL_TABLE),
				appendWithUr("select (select max(id) from t)"));
	}

	@Test
	public void existingFromIsKept() throws Exception {
		assertEquals(withUr("select * from t"), appendWithUr("select * from t"));
		// 括号内的from与本层无关,本层有FROM则不补
		assertEquals(withUr("select count(1) from (select id from t) x"),
				appendWithUr("select count(1) from (select id from t) x"));
	}

	@Test
	public void existingWithUrIsNotDuplicated() throws Exception {
		// 摘出已有UR再补虚表,只保留一个UR且位于末尾
		assertEquals("select 99 FROM " + DB2Dialect.VIRTUAL_TABLE + " with ur", appendWithUr("select 99 with ur"));
		assertEquals("select * from t with ur", appendWithUr("select * from t with ur"));
	}

	@Test
	public void fromAsIdentifierPrefixIsNotAFromClause() throws Exception {
		// fromx/afrom 不是FROM子句;字面量掩码后仍应补虚表
		assertEquals(withUr("select fromx FROM " + DB2Dialect.VIRTUAL_TABLE), appendWithUr("select fromx"));
	}

	@Test
	public void valuesQueryIsNotAppendedVirtualTable() throws Exception {
		// VALUES形式查询本身合法且无需FROM,补虚表反而SQL0104(真库实测回归)
		assertEquals(withUr("values (1)"), appendWithUr("values (1)"));
		assertEquals(withUr("values (1, 'a')"), appendWithUr("values (1, 'a')"));
		assertEquals(withUr("VALUES (99)"), appendWithUr("VALUES (99)"));
	}

	@Test
	public void doubleQuoteInsideStringLiteralIsNotIdentifier() throws Exception {
		// update 2026-11-12 单引号字面量内的"是普通字符,不能当作标识符定界:否则字面量后的FROM被吞,
		// 补虚表后拼出重复FROM(真库SQL0104)
		assertEquals(withUr("select '\"' from t"), appendWithUr("select '\"' from t"));
		assertEquals(withUr("select case when c='\"' then 1 else 0 end from t"),
				appendWithUr("select case when c='\"' then 1 else 0 end from t"));
		assertEquals(withUr("select replace(c, '\"', '') from t"), appendWithUr("select replace(c, '\"', '') from t"));
	}

	@Test
	public void quotedIdentifierIsMaskedButKeepsRealFrom() throws Exception {
		// 双引号标识符内的from/with ur不参与判定,标识符后的真实FROM照常识别
		assertEquals(withUr("select \"from\" from t"), appendWithUr("select \"from\" from t"));
		assertEquals(withUr("select x as \"with ur\" from t"), appendWithUr("select x as \"with ur\" from t"));
		// 无真实FROM时仍补虚表
		assertEquals(withUr("select 1 as \"from\" FROM " + DB2Dialect.VIRTUAL_TABLE),
				appendWithUr("select 1 as \"from\""));
	}
}
