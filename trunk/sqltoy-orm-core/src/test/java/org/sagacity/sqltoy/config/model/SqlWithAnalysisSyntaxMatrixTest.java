package org.sagacity.sqltoy.config.model;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.Test;

/**
 * CTE语法差异矩阵测试:验证SqlWithAnalysis对各类数据库CTE写法的识别覆盖。
 * 覆盖:基础/多CTE/RECURSIVE/列名列表/大小写/换行/PG物化提示/嵌套括号/字面量含括号等
 */
public class SqlWithAnalysisSyntaxMatrixTest {

	private String normalize(String s) {
		// 空白归一化:换行/多空白与单空格视为等价,断言聚焦结构而非排版
		return s.toLowerCase().replaceAll("\\s+", " ").trim();
	}

	private void assertRecognized(String sql, String expectWithFragment, String expectTailFragment) {
		SqlWithAnalysis w = new SqlWithAnalysis(sql);
		assertTrue(w.isHasWith(), "应识别为with查询:" + sql);
		String withSql = normalize(w.getWithSql());
		String tail = normalize(w.getRejectWithSql());
		assertTrue(withSql.contains(expectWithFragment.toLowerCase()),
				"with体应完整提取,实际:[" + w.getWithSql() + "] sql=" + sql);
		assertTrue(tail.contains(expectTailFragment.toLowerCase()),
				"主查询应保留,实际:[" + w.getRejectWithSql() + "] sql=" + sql);
		assertFalse(tail.startsWith("with"), "with部分应被剥离,实际:" + w.getRejectWithSql());
	}

	// T1 基础形态
	@Test
	public void basicCte() {
		assertRecognized("WITH t AS (SELECT id FROM t1) SELECT * FROM t",
				"with t as (select id from t1)", "select * from t");
	}

	// T2 多CTE
	@Test
	public void multipleCte() {
		assertRecognized(
				"with a as (select id from t1), b as (select dept from t2) select * from a join b on a.id=b.dept",
				"with a as (select id from t1)", "select * from a join b");
	}

	// T3 WITH RECURSIVE(PG/MySQL8/H2)
	@Test
	public void recursiveCte() {
		assertRecognized(
				"WITH RECURSIVE t(n) AS (VALUES (1) UNION ALL SELECT n+1 FROM t WHERE n < 5) SELECT sum(n) FROM t",
				"with recursive t(n) as", "select sum(n) from t");
	}

	// T4 列名列表(ANSI)
	@Test
	public void columnListCte() {
		assertRecognized("WITH t(a, b) AS (SELECT id, name FROM t1) SELECT * FROM t",
				"with t(a, b) as (select id, name from t1)", "select * from t");
	}

	// T5 PG12+ 物化提示
	@Test
	public void materializedCte() {
		assertRecognized("WITH t AS MATERIALIZED (SELECT id FROM t1) SELECT * FROM t",
				"with t as materialized (select id from t1)", "select * from t");
	}

	// T6 PG12+ 否定物化提示(两个修饰词)
	@Test
	public void notMaterializedCte() {
		assertRecognized("WITH t AS NOT MATERIALIZED (SELECT id FROM t1) SELECT * FROM t",
				"with t as not materialized (select id from t1)", "select * from t");
	}

	// T7 混合大小写
	@Test
	public void mixedCaseCte() {
		assertRecognized("with t As (Select id From t1) select * from t",
				"with t as (select id from t1)", "select * from t");
	}

	// T8 头部与体跨行
	@Test
	public void multilineHeaderCte() {
		assertRecognized(
				"WITH\nt AS\n(SELECT id FROM t1)\nSELECT * FROM t",
				"with t as (select id from t1)", "select * from t");
	}

	// T9 下划线数字别名
	@Test
	public void underscoreDigitAlias() {
		assertRecognized("WITH t_1 AS (SELECT id FROM t1) SELECT * FROM t_1",
				"with t_1 as (select id from t1)", "select * from t_1");
	}

	// T10 体内字面量含右括号(字面量感知配对)
	@Test
	public void literalParenInBody() {
		assertRecognized("WITH t AS (SELECT ')' AS mark FROM t1) SELECT * FROM t",
				"with t as (select ')' as mark from t1)", "select * from t");
	}

	// T11 体内嵌套括号
	@Test
	public void nestedParenInBody() {
		assertRecognized("WITH t AS (SELECT concat('(', id) AS v FROM t1) SELECT * FROM t",
				"with t as (select concat('(', id) as v from t1)", "select * from t");
	}

	// T12 体内字面量含", xx as ("干扰第二CTE识别(字面量感知修复后应正确)
	@Test
	public void secondCteAfterLiteralWithFakeMarker() {
		assertRecognized(
				"with a as (select '备注, b as (测试' as memo from t1), b as (select id from t2) select * from b",
				"with a as (select '备注, b as (测试' as memo from t1)",
				"select * from b");
	}

	// T13 RECURSIVE与列名列表组合
	@Test
	public void recursiveWithColumnList() {
		assertRecognized(
				"WITH RECURSIVE t(n) AS (VALUES (1) UNION ALL SELECT n+1 FROM t WHERE n < 5) SELECT sum(n) FROM t",
				"recursive t(n) as", "select sum(n) from t");
	}

	// T14 小写with关键字
	@Test
	public void lowercaseWith() {
		assertRecognized("with t as (select id from t1) select * from t",
				"with t as (select id from t1)", "select * from t");
	}

	// ---------------- 已知覆盖缺口(维持现状,识别不到则按无with处理) ----------------

	/**
	 * 覆盖缺口:withPattern字符类[a-z|0-9|\_]不含中文字符,中文CTE别名识别不到,
	 * hasWith=false时count会按无with优化导致with子句丢失。如需支持需扩展正则字符类。
	 */
	@Test
	public void chineseAliasNotRecognized() {
		SqlWithAnalysis w = new SqlWithAnalysis("with 中文表 as (select id from t1) select * from 中文表");
		assertFalse(w.isHasWith(), "当前版本不支持中文CTE别名(已知覆盖缺口)");
	}
}
