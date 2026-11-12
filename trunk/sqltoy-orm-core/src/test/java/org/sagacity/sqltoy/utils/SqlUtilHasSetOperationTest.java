package org.sagacity.sqltoy.utils;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.Test;

/**
 * hasSetOperation(2026-11-10 WIP)语义锁定:union/intersect/except/minus 统一判定,
 * 口径与 hasUnion 一致(剔除 with/子查询括号/掩码字面量后只判最外层);
 * hasUnion 经 hasSetKeyword 重构后行为不变一并回归。
 */
public class SqlUtilHasSetOperationTest {

	@Test
	public void bareSetOperationsDetected() {
		assertTrue(SqlUtil.hasSetOperation("select id from t1 union select id from t2", true), "union");
		assertTrue(SqlUtil.hasSetOperation("select id from t1 except select id from t2", true), "except");
		assertTrue(SqlUtil.hasSetOperation("select id from t1 intersect select id from t2", true), "intersect");
		assertTrue(SqlUtil.hasSetOperation("select id from t1 minus select id from t2", true), "minus");
		assertTrue(SqlUtil.hasSetOperation("select id from t1 UNION ALL select id from t2", true), "UNION ALL大小写");
		assertTrue(SqlUtil.hasSetOperation("select id from t1 EXCEPT select id from t2", true), "EXCEPT大小写");
	}

	@Test
	public void keywordAdjacentToParenDetected() {
		// update 2026-11-10 关键字紧贴括号(无空格)写法:括号内容剔除后关键字落到串尾,
		// 尾部\W边界只有补过空白才满足;漏判会让sqlserver分页把order by直接追加在except后(真库Msg 104)
		assertTrue(SqlUtil.hasSetOperation("select id from t1 except(select id from t2)", true), "except(");
		assertTrue(SqlUtil.hasSetOperation("select id from t1 intersect(select id from t2)", true), "intersect(");
		assertTrue(SqlUtil.hasSetOperation("select id from t1 union(select id from t2)", true), "union(");
		// hasUnion 同口径回归(原有假阴性一并修正)
		assertTrue(SqlUtil.hasUnion("select id from t1 union(select id from t2)", true));
		assertFalse(SqlUtil.hasUnion("select id from t1 except(select id from t2)", true), "except不触发union判定");
	}

	@Test
	public void noSetOperationRejected() {
		assertFalse(SqlUtil.hasSetOperation("select id from t1 where name = 'x'", true));
		assertFalse(SqlUtil.hasSetOperation("select id from t1 order by id", true));
	}

	@Test
	public void setKeywordFunctionFormNotDetected() {
		// update 2026-11-12 minus(a,b)是ClickHouse算术函数,不是集合运算:
		// 误命中会让CH分页误包派生表,内层重复列名(t1.id,t2.id)报Duplicate column name
		assertFalse(SqlUtil.hasSetOperation("select t1.id,t2.id from t where minus(a,b)>0", true),
				"minus()函数不命中");
		assertFalse(SqlUtil.hasSetOperation("select minus(a,b) diff from t", true), "select列中的minus()");
		assertFalse(SqlUtil.hasSetOperation("select id from t where t.minus(a,b)>0", true), "限定名minus()");
		assertFalse(SqlUtil.hasSetOperation("select id from t where minus (a,b)>0", true), "minus与括号间有空格");
		assertFalse(SqlUtil.hasSetOperation("select id from t where name = 'a minus b'", true),
				"字面量内minus不命中");
		// 关键字紧贴括号但内容是select的集合运算写法仍命中
		assertTrue(SqlUtil.hasSetOperation("select id from t1 minus(select id from t2)", true), "minus(select");
		assertTrue(SqlUtil.hasSetOperation("select id from t1 minus (select id from t2)", true), "minus (select");
		// 函数与真实集合运算并存时仍命中
		assertTrue(SqlUtil.hasSetOperation("select minus(a,b) d from t union select id from t2", true),
				"函数+真实union");
	}

	@Test
	public void literalSetKeywordNotDetected() {
		// 字面量内的except不触发(掩码口径,与hasUnion一致)
		assertFalse(SqlUtil.hasSetOperation("select id from t1 where name = 'a except b'", true));
		assertFalse(SqlUtil.hasSetOperation("select id from t1 where remark like '%union%'", true));
	}

	@Test
	public void innerSubQuerySetOperationExcludedWhenClearMisty() {
		// clearMistyChar=true只判最外层:子查询括号内的集合运算被剔除
		assertFalse(SqlUtil.hasSetOperation(
				"select id from t1 where id in (select id from a except select id from b)", true));
		// 最外层存在则命中
		assertTrue(SqlUtil.hasSetOperation(
				"select id from t1 where id in (select id from a) except select id from t2", true));
	}

	@Test
	public void withCteSetOperationExcluded() {
		// with内的集合运算被剔除后最外层无集合运算(与hasUnion同口径)
		assertFalse(SqlUtil.hasSetOperation(
				"with x as (select id from a union select id from b) select id from x", true));
		// with剔除后最外层仍有except则命中
		assertTrue(SqlUtil.hasSetOperation(
				"with x as (select id from a) select id from x except select id from t2", true));
	}

	@Test
	public void hasUnionRegressionUnchanged() {
		// hasUnion重构(共用hasSetKeyword)后行为锁定:minus/except/intersect不属于union判定
		assertTrue(SqlUtil.hasUnion("select id from t1 union select id from t2", true));
		assertFalse(SqlUtil.hasUnion("select id from t1 except select id from t2", true), "except不触发union判定");
		assertFalse(SqlUtil.hasUnion("select id from t1 where name='a union b'", true), "字面量union不触发");
		assertFalse(SqlUtil.hasUnion("with x as (select 1 a union select 2 a) select a from x", true),
				"with内union不触发");
		assertTrue(SqlUtil.hasUnion("select id from t1 where id in (select 1) union select id from t2", true),
				"最外层union触发");
	}
}
