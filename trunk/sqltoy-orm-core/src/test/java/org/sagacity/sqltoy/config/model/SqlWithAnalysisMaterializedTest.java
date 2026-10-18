package org.sagacity.sqltoy.config.model;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.Test;
import org.sagacity.sqltoy.utils.DataSourceUtils.DBType;

/**
 * update 2026-10-18 CTE物化hint(AS [NOT] MATERIALIZED)按目标库类型保留/剔除的单元测试。
 * 白名单经docker真库实测:PG18/openGauss5.0/金仓V9/瀚高(PG12.3)/Vastbase均支持;
 * mysql9.3/tidb8.5/mssql2025混入即报语法错误。本测试锁定SQL文本层的剔除/保留/字面量安全行为
 */
public class SqlWithAnalysisMaterializedTest {

	private String norm(String s) {
		return s.toLowerCase().replaceAll("\\s+", " ").trim();
	}

	// ---------- supportsCteMaterialized 白名单 ----------

	@Test
	public void supportedDbTypes() {
		assertTrue(SqlWithAnalysis.supportsCteMaterialized(DBType.POSTGRESQL));
		assertTrue(SqlWithAnalysis.supportsCteMaterialized(DBType.POSTGRESQL14));
		assertTrue(SqlWithAnalysis.supportsCteMaterialized(DBType.OPENGAUSS));
		assertTrue(SqlWithAnalysis.supportsCteMaterialized(DBType.KINGBASE));
		assertTrue(SqlWithAnalysis.supportsCteMaterialized(DBType.VASTBASE));
		assertTrue(SqlWithAnalysis.supportsCteMaterialized(DBType.HIGHGO));
	}

	@Test
	public void unsupportedDbTypes() {
		assertFalse(SqlWithAnalysis.supportsCteMaterialized(DBType.MYSQL));
		assertFalse(SqlWithAnalysis.supportsCteMaterialized(DBType.MYSQL57));
		assertFalse(SqlWithAnalysis.supportsCteMaterialized(DBType.SQLSERVER));
		assertFalse(SqlWithAnalysis.supportsCteMaterialized(DBType.ORACLE));
		assertFalse(SqlWithAnalysis.supportsCteMaterialized(DBType.ORACLE11));
		assertFalse(SqlWithAnalysis.supportsCteMaterialized(DBType.TIDB));
		assertFalse(SqlWithAnalysis.supportsCteMaterialized(DBType.H2));
		assertFalse(SqlWithAnalysis.supportsCteMaterialized(DBType.CLICKHOUSE));
		assertFalse(SqlWithAnalysis.supportsCteMaterialized(DBType.DB2));
		assertFalse(SqlWithAnalysis.supportsCteMaterialized(DBType.DM));
		assertFalse(SqlWithAnalysis.supportsCteMaterialized(DBType.HANA));
		assertFalse(SqlWithAnalysis.supportsCteMaterialized(DBType.SQLITE));
		assertFalse(SqlWithAnalysis.supportsCteMaterialized(DBType.UNDEFINE));
	}

	// ---------- 支持库:原样保留(同一引用) ----------

	@Test
	public void keepOnPostgresql() {
		String sql = "WITH t AS MATERIALIZED (SELECT id FROM t1) SELECT * FROM t";
		assertSame(sql, SqlWithAnalysis.adaptCteMaterialized(sql, DBType.POSTGRESQL), "PG支持物化hint,应原样返回");
		String sql2 = "with t as not materialized (select 1) select * from t";
		assertSame(sql2, SqlWithAnalysis.adaptCteMaterialized(sql2, DBType.KINGBASE), "金仓支持,应原样返回");
	}

	// ---------- 不支持库:剔除 ----------

	@Test
	public void stripMaterializedOnMysql() {
		assertEquals("WITH t AS (SELECT id FROM t1) SELECT * FROM t",
				SqlWithAnalysis.adaptCteMaterialized("WITH t AS MATERIALIZED (SELECT id FROM t1) SELECT * FROM t",
						DBType.MYSQL));
	}

	@Test
	public void stripNotMaterializedOnSqlServer() {
		assertEquals("WITH t AS (SELECT id FROM t1) SELECT * FROM t",
				SqlWithAnalysis.adaptCteMaterialized("WITH t AS NOT MATERIALIZED (SELECT id FROM t1) SELECT * FROM t",
						DBType.SQLSERVER));
	}

	@Test
	public void stripMultipleCte() {
		String sql = "with a as materialized (select 1), b as not materialized (select 2) select * from a join b on a.id=b.id";
		assertEquals("with a as (select 1), b as (select 2) select * from a join b on a.id=b.id",
				SqlWithAnalysis.adaptCteMaterialized(sql, DBType.TIDB));
	}

	@Test
	public void stripCaseAndSpacingVariants() {
		// 大小写混写 + 多空白
		assertEquals("with t as (select 1) select * from t",
				norm(SqlWithAnalysis.adaptCteMaterialized("WITH T AS   NOT   MATERIALIZED   (select 1) select * from t",
						DBType.MYSQL)));
		// materialized与'('之间无空白
		assertEquals("with t as (select 1) select * from t",
				norm(SqlWithAnalysis.adaptCteMaterialized("with t as materialized(select 1) select * from t",
						DBType.MYSQL)));
	}

	@Test
	public void stripWithColumnListAndRecursive() {
		// 带列名清单 + RECURSIVE
		assertEquals("with recursive t (id, pid) as (select 1, 2) select * from t",
				norm(SqlWithAnalysis.adaptCteMaterialized(
						"with recursive t (id, pid) as materialized (select 1, 2) select * from t", DBType.MYSQL)));
	}

	// ---------- 快速通道 / 无误剔 ----------

	@Test
	public void noMaterializedKeywordReturnsSame() {
		String sql = "with t as (select id from t1) select * from t";
		assertSame(sql, SqlWithAnalysis.adaptCteMaterialized(sql, DBType.MYSQL), "不含materialized应走快速通道原样返回");
	}

	@Test
	public void columnAliasNamedMaterializedNotStripped() {
		// 列别名恰为materialized_xxx,后无'(',不应误剔
		String sql = "select x as materialized_col from t";
		assertEquals(sql, SqlWithAnalysis.adaptCteMaterialized(sql, DBType.MYSQL));
	}

	@Test
	public void literalContentNotStripped() {
		// 字面量内的"as materialized ("文本不应被剔(掩码定位)
		String sql = "with t as (select 'as materialized (' as mark) select * from t";
		assertEquals(sql, SqlWithAnalysis.adaptCteMaterialized(sql, DBType.MYSQL), "字面量内文本应受掩码保护");
	}

	@Test
	public void blankAndNullSafe() {
		assertSame(null, SqlWithAnalysis.adaptCteMaterialized(null, DBType.MYSQL));
		assertEquals("", SqlWithAnalysis.adaptCteMaterialized("", DBType.MYSQL));
	}

	// ---------- 剔除后仍是合法可解析的with ----------

	@Test
	public void strippedSqlStillParsesAsWith() {
		String stripped = SqlWithAnalysis.adaptCteMaterialized(
				"with t as materialized (select id from t1) select * from t", DBType.MYSQL);
		SqlWithAnalysis w = new SqlWithAnalysis(stripped);
		assertTrue(w.isHasWith(), "剔除后仍应被识别为with查询");
		assertTrue(norm(w.getWithSql()).contains("with t as (select id from t1)"), "with体应完整:" + w.getWithSql());
		assertFalse(norm(stripped).contains("materialized"), "剔除后不应残留materialized关键字");
	}
}
