package org.sagacity.sqltoy.config.model;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.List;

import org.junit.jupiter.api.Test;
import org.sagacity.sqltoy.utils.DataSourceUtils.DBType;

/**
 * update 2026-10-19 SqlWithAnalysis parse()重构(提取parenIdx/cteDef局部变量+新增dbType感知
 * adaptedBuffer分支)的多场景回归。三个目标:
 * ①向后兼容——单参构造(所有既有消费点:processFastWith/getCountBySql/SqlServerDialect/
 *   DefaultDialectUtils/hasUnion均用单参)的withSql/rejectWithSql/preSql/footSql/withSqlSet/hasWith
 *   逐字段锁定,证明重构未改变原解析行为;
 * ②一致性——单参与双参(支持库)对全部getter产出完全相同(支持库stripMaterialized=false走原路径);
 * ③剔除正确性——双参(不支持库)对ext位置级剔除,withSql/withSqlSet/adaptedSql同步干净,且
 *   aliasTable/body/withAfter/aliasParams等其余字段不受影响(保障processFastWith重组正确)。
 */
public class SqlWithAnalysisScenarioTest {

	/**
	 * 覆盖各类CTE形态的sql电池:无with/单CTE/物化/NOT物化/多CTE混合/递归+列清单/列清单/字面量含
	 * materialized文本/派生表别名materialized/CTE名恰为materialized/前缀注释/hint与内层派生别名共存/
	 * 大小写多空白/materialized与'('无空格
	 */
	private static final String[] SQL_BATTERY = { "select * from t", "with t as (select 1) select * from t",
			"with t as materialized (select 1) select * from t",
			"with t as not materialized (select 1) select * from t",
			"with a as materialized (select 1), b as (select 2) select * from a,b",
			"with a as (select 1), b as not materialized (select 2) select * from a,b",
			"with recursive t (id, pid) as materialized (select 1, 2) select * from t",
			"with t (a, b) as (select 1, 2) select * from t",
			"with t as (select 'as materialized (' as m) select * from t",
			"select * from (select 1) as materialized (x)", "with materialized as (select 1) select * from materialized",
			"/* c */ with t as materialized (select 1) select * from t",
			"with t as materialized (select * from (select 1 as a) as materialized (x)) select * from t",
			"WITH T AS   NOT   MATERIALIZED   (select 1) select * from t",
			"with t as materialized(select 1) select * from t" };

	private void assertWithSqlSetEquals(List<String[]> expected, List<String[]> actual, String sql) {
		if (expected == null) {
			assertNull(actual, "withSqlSet应同为null: " + sql);
			return;
		}
		assertEquals(expected.size(), actual.size(), "withSqlSet大小不一致: " + sql);
		for (int i = 0; i < expected.size(); i++) {
			assertArrayEquals(expected.get(i), actual.get(i), "withSqlSet[" + i + "]不一致: " + sql);
		}
	}

	// ========== ② 一致性:单参 == 双参(支持库),全部getter ==========

	@Test
	public void oneArgEqualsTwoArgSupported() {
		for (String sql : SQL_BATTERY) {
			SqlWithAnalysis one = new SqlWithAnalysis(sql);
			// 支持库(POSTGRESQL)→stripMaterialized=false→adaptedBuffer=null→与单参同一路径
			SqlWithAnalysis two = new SqlWithAnalysis(sql, DBType.POSTGRESQL);
			assertEquals(one.isHasWith(), two.isHasWith(), "hasWith不一致: " + sql);
			assertEquals(one.getSql(), two.getSql(), "sql不一致: " + sql);
			assertEquals(one.getWithSql(), two.getWithSql(), "withSql不一致: " + sql);
			assertEquals(one.getRejectWithSql(), two.getRejectWithSql(), "rejectWithSql不一致: " + sql);
			assertEquals(one.getPreSql(), two.getPreSql(), "preSql不一致: " + sql);
			assertEquals(one.getFootSql(), two.getFootSql(), "footSql不一致: " + sql);
			assertEquals(one.getAdaptedSql(), two.getAdaptedSql(), "adaptedSql不一致: " + sql);
			assertWithSqlSetEquals(one.getWithSqlSet(), two.getWithSqlSet(), sql);
		}
	}

	// ========== ① 向后兼容:单参构造withSqlSet各字段锁定(既有消费点依赖) ==========

	@Test
	public void oneArgWithSqlSetSingleCte() {
		// 单CTE带物化:单参构造保留materialized于ext(不剔除)
		SqlWithAnalysis w = new SqlWithAnalysis("with t as materialized (select 1) select * from t");
		assertTrue(w.isHasWith());
		assertEquals("with t as materialized (select 1) ", w.getWithSql());
		assertArrayEquals(new String[] { "t", " materialized ", "select 1", "", "" }, w.getWithSqlSet().get(0));
	}

	@Test
	public void oneArgWithSqlSetMultipleCte() {
		SqlWithAnalysis w = new SqlWithAnalysis("with a as materialized (select 1), b as (select 2) select * from a,b");
		List<String[]> set = w.getWithSqlSet();
		assertEquals(2, set.size());
		assertArrayEquals(new String[] { "a", " materialized ", "select 1", "", "" }, set.get(0));
		assertArrayEquals(new String[] { "b", " ", "select 2", "", "" }, set.get(1));
	}

	@Test
	public void oneArgWithSqlSetRecursiveColumnList() {
		// 递归+列清单:withAfter=recursive,aliasParams=" (id, pid)"(前导空格为原有行为,保留名字与
		// 列清单间隔供processFastWith重组),ext保留not materialized
		SqlWithAnalysis w = new SqlWithAnalysis(
				"with recursive t (id, pid) as not materialized (select 1, 2) select * from t");
		assertArrayEquals(new String[] { "t", " not materialized ", "select 1, 2", "recursive", " (id, pid)" },
				w.getWithSqlSet().get(0));
	}

	@Test
	public void oneArgNoWith() {
		SqlWithAnalysis w = new SqlWithAnalysis("select * from t where id=1");
		assertFalse(w.isHasWith());
		assertNull(w.getWithSqlSet());
		assertEquals("select * from t where id=1", w.getRejectWithSql());
		// 无with时adaptedSql为null,getAdaptedSql回退原sql
		assertSame("select * from t where id=1", w.getAdaptedSql());
	}

	// ========== ③ 剔除正确性:双参(不支持库)ext/withSql/adaptedSql同步干净 ==========

	@Test
	public void twoArgUnsupportedStripsExtAndWithSql() {
		SqlWithAnalysis w = new SqlWithAnalysis("with t as materialized (select 1) select * from t", DBType.MYSQL);
		// ext剔除为单空格,withSql同步干净,其余字段(aliasTable/body/withAfter/aliasParams)不受影响
		assertArrayEquals(new String[] { "t", " ", "select 1", "", "" }, w.getWithSqlSet().get(0));
		assertEquals("with t as (select 1) ", w.getWithSql());
		assertEquals("with t as (select 1) select * from t", w.getAdaptedSql());
	}

	@Test
	public void twoArgUnsupportedStripsOnlyMaterializedCte() {
		// 多CTE:仅含materialized的ext被剔,本就无hint的CTE ext保持" "
		SqlWithAnalysis w = new SqlWithAnalysis("with a as materialized (select 1), b as (select 2) select * from a,b",
				DBType.MYSQL);
		List<String[]> set = w.getWithSqlSet();
		assertArrayEquals(new String[] { "a", " ", "select 1", "", "" }, set.get(0));
		assertArrayEquals(new String[] { "b", " ", "select 2", "", "" }, set.get(1));
		assertEquals("with a as (select 1), b as (select 2) select * from a,b", w.getAdaptedSql());
	}

	@Test
	public void twoArgUnsupportedRecursiveColumnListStripped() {
		SqlWithAnalysis w = new SqlWithAnalysis(
				"with recursive t (id, pid) as not materialized (select 1, 2) select * from t", DBType.TIDB);
		// ext剔除,withAfter/aliasParams保留(aliasParams前导空格为原有行为)
		assertArrayEquals(new String[] { "t", " ", "select 1, 2", "recursive", " (id, pid)" }, w.getWithSqlSet().get(0));
		assertEquals("with recursive t (id, pid) as (select 1, 2) select * from t", w.getAdaptedSql());
	}

	@Test
	public void twoArgSupportedKeepsMaterialized() {
		// 支持库:ext/withSql/adaptedSql均保留materialized
		SqlWithAnalysis w = new SqlWithAnalysis("with t as materialized (select 1) select * from t",
				DBType.POSTGRESQL);
		assertArrayEquals(new String[] { "t", " materialized ", "select 1", "", "" }, w.getWithSqlSet().get(0));
		assertEquals("with t as materialized (select 1) ", w.getWithSql());
		// 支持库stripMaterialized=false→adaptedSql为null→getAdaptedSql回退原sql(同一引用)
		assertSame("with t as materialized (select 1) select * from t", w.getAdaptedSql());
	}

	// ========== 边缘形态:绝不误伤 ==========

	@Test
	public void twoArgUnsupportedCteNamedMaterializedUntouched() {
		// CTE名恰为materialized:名字在aliasTable不在ext,ext=" "无materialized→不剔
		String sql = "with materialized as (select 1) select * from materialized";
		SqlWithAnalysis w = new SqlWithAnalysis(sql, DBType.MYSQL);
		assertEquals("materialized", w.getWithSqlSet().get(0)[0]);
		assertEquals(" ", w.getWithSqlSet().get(0)[1]);
		assertEquals(sql, w.getAdaptedSql());
	}

	@Test
	public void twoArgUnsupportedLiteralMaterializedUntouched() {
		// 字面量内"as materialized (":findBodyEnd括号配对跳过字面量,ext=" "→不剔,字面量逐字保留
		String sql = "with t as (select 'as materialized (' as m) select * from t";
		SqlWithAnalysis w = new SqlWithAnalysis(sql, DBType.MYSQL);
		assertEquals(sql, w.getAdaptedSql());
	}

	@Test
	public void twoArgUnsupportedDerivedAliasUntouched() {
		// 派生表别名materialized(无with):parse早退,adaptedSql为null→getAdaptedSql回退原sql
		String sql = "select * from (select 1) as materialized (x)";
		SqlWithAnalysis w = new SqlWithAnalysis(sql, DBType.MYSQL);
		assertFalse(w.isHasWith());
		assertSame(sql, w.getAdaptedSql());
	}

	@Test
	public void twoArgUnsupportedInnerDerivedAliasPreservedWhenHintStripped() {
		// 真hint(外层CTE ext)剔除,内层派生表别名(在CTE body内,非ext)保留
		SqlWithAnalysis w = new SqlWithAnalysis(
				"with t as materialized (select * from (select 1 as a) as materialized (x)) select * from t",
				DBType.MYSQL);
		assertEquals("with t as (select * from (select 1 as a) as materialized (x)) select * from t",
				w.getAdaptedSql());
	}

	@Test
	public void twoArgUnsupportedPrefixCommentPreserved() {
		// 前缀注释:headSql原样保留,仅CTE ext被剔
		SqlWithAnalysis w = new SqlWithAnalysis("/* c */ with t as materialized (select 1) select * from t",
				DBType.MYSQL);
		assertEquals("/* c */ with t as (select 1) select * from t", w.getAdaptedSql());
	}

	@Test
	public void twoArgUnsupportedNoMaterializedReconstructIdentical() {
		// 有with但无materialized:ext=" "不触发剔除,adaptedSql重组后与原sql逐字节相同
		String sql = "with t as (select id from t1) select * from t";
		SqlWithAnalysis w = new SqlWithAnalysis(sql, DBType.MYSQL);
		assertEquals(sql, w.getAdaptedSql());
	}

	@Test
	public void twoArgUnsupportedNoWithReturnsSame() {
		// 无with:parse早退,adaptedSql为null→getAdaptedSql回退原sql(同一引用)
		String sql = "select * from t";
		assertSame(sql, new SqlWithAnalysis(sql, DBType.MYSQL).getAdaptedSql());
	}

	// ========== adaptCteMaterialized静态入口与双参构造等价 ==========

	@Test
	public void adaptCteMaterializedDelegatesToTwoArgParse() {
		for (String sql : SQL_BATTERY) {
			// 不支持库:静态入口与双参构造getAdaptedSql一致(快速通道命中时静态入口直接返回原sql,
			// 双参构造无with时getAdaptedSql也回退原sql,二者内容恒等)
			assertEquals(new SqlWithAnalysis(sql, DBType.MYSQL).getAdaptedSql(),
					SqlWithAnalysis.adaptCteMaterialized(sql, DBType.MYSQL), "adaptCteMaterialized应与双参解析等价: " + sql);
			// 支持库:静态入口原样返回
			assertSame(sql, SqlWithAnalysis.adaptCteMaterialized(sql, DBType.POSTGRESQL), "支持库应原样返回: " + sql);
		}
	}
}
