package org.sagacity.sqltoy.config.model;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.Test;
import org.sagacity.sqltoy.utils.DataSourceUtils.DBType;

/**
 * D2回归:MySQL的\'在CTE字面量中不再按标准SQL提前闭合;
 * 标准SQL的反斜杠语义保持不变;无法配对时不再抛StringIndexOutOfBoundsException。
 */
public class SqlWithAnalysisBackslashTest {

	@Test
	public void mysqlBackslashQuoteInCteBody() {
		String sql = "with t as (select 'it\\'s (x)' c from dual) select * from t";
		SqlWithAnalysis mysql = new SqlWithAnalysis(sql, DBType.MYSQL);
		assertTrue(mysql.isHasWith());
		assertEquals(1, mysql.getWithSqlSet().size());
		assertEquals("t", mysql.getWithSqlSet().get(0)[0]);
		assertEquals("select * from t", mysql.getRejectWithSql().trim());
		assertTrue(mysql.getWithSql().contains("from dual"), "CTE体应完整保留: " + mysql.getWithSql());
	}

	@Test
	public void mysqlBackslashQuoteWithBracketInsideLiteral() {
		// 标准语义会在b)处提前结束CTE;MySQL语义必须把)当作字面量内容
		String sql = "with t as (select 'a\\'b)' c from dual) select * from t";
		SqlWithAnalysis mysql = new SqlWithAnalysis(sql, DBType.MYSQL);
		assertTrue(mysql.isHasWith());
		assertEquals("select * from t", mysql.getRejectWithSql().trim());
		assertTrue(mysql.getWithSql().contains("from dual"), "CTE体应完整保留: " + mysql.getWithSql());
	}

	@Test
	public void standardBackslashBeforeQuoteStillWorks() {
		// 标准SQL中反斜杠不是转义符,'C:\'是合法的已闭合字面量
		String sql = "with t as (select 'C:\\' c from dual) select * from t";
		SqlWithAnalysis standard = new SqlWithAnalysis(sql);
		assertTrue(standard.isHasWith());
		assertEquals("select * from t", standard.getRejectWithSql().trim());
		SqlWithAnalysis postgresql = new SqlWithAnalysis(sql, DBType.POSTGRESQL);
		assertTrue(postgresql.isHasWith());
		assertEquals("select * from t", postgresql.getRejectWithSql().trim());
	}

	@Test
	public void standardDoubledQuoteStillWorks() {
		String sql = "with t as (select 'a''b)' c from dual) select * from t";
		SqlWithAnalysis standard = new SqlWithAnalysis(sql);
		assertTrue(standard.isHasWith());
		assertEquals("select * from t", standard.getRejectWithSql().trim());
	}

	@Test
	public void mysqlMultipleCtesWithBackslashQuotes() {
		String sql = "with a as (select 'x\\'y)' c from dual), "
				+ "b as (select 'z\\'w)' d from dual) select * from a,b";
		SqlWithAnalysis mysql = new SqlWithAnalysis(sql, DBType.MYSQL);
		assertTrue(mysql.isHasWith());
		assertEquals(2, mysql.getWithSqlSet().size());
		assertEquals("a", mysql.getWithSqlSet().get(0)[0]);
		assertEquals("b", mysql.getWithSqlSet().get(1)[0]);
		assertEquals("select * from a,b", mysql.getRejectWithSql().trim());
		assertTrue(mysql.getWithSql().contains("x\\'y"), "第一个CTE体: " + mysql.getWithSql());
		assertTrue(mysql.getWithSql().contains("z\\'w"), "第二个CTE体: " + mysql.getWithSql());
	}

	@Test
	public void mysqlRecursiveCteWithBackslashQuote() {
		String sql = "with recursive t(n) as ("
				+ "select 'it\\'s (x)' from dual union all select n+1 from t where n<3"
				+ ") select * from t";
		SqlWithAnalysis mysql = new SqlWithAnalysis(sql, DBType.MYSQL);
		assertTrue(mysql.isHasWith());
		assertEquals(1, mysql.getWithSqlSet().size());
		assertEquals("t", mysql.getWithSqlSet().get(0)[0]);
		assertEquals("select * from t", mysql.getRejectWithSql().trim());
		assertTrue(mysql.getWithSql().contains("union all"), "递归CTE体: " + mysql.getWithSql());
	}

	@Test
	public void mysqlMaterializedHintStrippedWithBackslashQuote() {
		String sql = "with t as materialized (select 'it\\'s (x)' c from dual) select * from t";
		SqlWithAnalysis mysql = new SqlWithAnalysis(sql, DBType.MYSQL);
		assertTrue(mysql.isHasWith());
		assertEquals("select * from t", mysql.getRejectWithSql().trim());
		assertTrue(mysql.getWithSql().contains("from dual"), "CTE体: " + mysql.getWithSql());
		assertTrue(mysql.getWithSql().toLowerCase().indexOf("materialized") == -1,
				"mysql不支持materialized,应剔除: " + mysql.getWithSql());
		assertTrue(mysql.getAdaptedSql().toLowerCase().indexOf("materialized") == -1,
				"adaptedSql应剔除materialized: " + mysql.getAdaptedSql());
	}

	@Test
	public void mysqlEvenBackslashesBeforeQuote() {
		// 两个反斜杠为偶数,MySQL下引号正常闭合,(不能把后面的)吞进字面量
		String sql = "with t as (select 'a\\\\' c from dual) select * from t";
		SqlWithAnalysis mysql = new SqlWithAnalysis(sql, DBType.MYSQL);
		assertTrue(mysql.isHasWith());
		assertEquals("select * from t", mysql.getRejectWithSql().trim());
		assertTrue(mysql.getWithSql().contains("from dual"), "CTE体: " + mysql.getWithSql());
	}

	@Test
	public void mysqlAliasColumnsWithBackslashQuote() {
		String sql = "with t(a,b) as (select 'x\\'y)', 1 from dual) select * from t";
		SqlWithAnalysis mysql = new SqlWithAnalysis(sql, DBType.MYSQL);
		assertTrue(mysql.isHasWith());
		assertEquals("t", mysql.getWithSqlSet().get(0)[0]);
		assertEquals("(a,b)", mysql.getWithSqlSet().get(0)[4]);
		assertEquals("select * from t", mysql.getRejectWithSql().trim());
	}

	@Test
	public void mysqlNestedParensOutsideLiteral() {
		String sql = "with t as (select (1+2) x, 'it\\'s (x)' y from dual) select * from t";
		SqlWithAnalysis mysql = new SqlWithAnalysis(sql, DBType.MYSQL);
		assertTrue(mysql.isHasWith());
		assertEquals("select * from t", mysql.getRejectWithSql().trim());
		assertTrue(mysql.getWithSql().contains("(1+2)"), mysql.getWithSql());
	}

	@Test
	public void unmatchedBodyThrowsReadableException() {
		String sql = "with t as (select 1";
		IllegalArgumentException standard = assertThrows(IllegalArgumentException.class,
				() -> new SqlWithAnalysis(sql));
		assertTrue(standard.getMessage().contains("CTE body bracket"), standard.getMessage());
		IllegalArgumentException mysql = assertThrows(IllegalArgumentException.class,
				() -> new SqlWithAnalysis(sql, DBType.MYSQL));
		assertTrue(mysql.getMessage().contains("CTE body bracket"), mysql.getMessage());
	}

	@Test
	public void hasUnionUsesMysqlBackslashSemantics() {
		String sql = "with t as (select 'it\\'s (x)' c from dual) select * from t union select * from t";
		assertTrue(org.sagacity.sqltoy.utils.SqlUtil.hasUnion(sql, true, true));
	}
}
