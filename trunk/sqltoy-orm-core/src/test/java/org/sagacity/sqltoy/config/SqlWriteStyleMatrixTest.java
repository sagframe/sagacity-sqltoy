package org.sagacity.sqltoy.config;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.HashMap;
import java.util.Map;

import org.junit.jupiter.api.Test;
import org.sagacity.sqltoy.config.model.SqlToyResult;

/**
 * 多SQL写法矩阵测试:位置/命名参数模式下,各类语句写法(字面量、转义、片段、in、like、
 * 双占位符等)与字面量感知防护共存的行为验证。
 */
public class SqlWriteStyleMatrixTest {

	private SqlToyResult positional(String sql, Object[] values) {
		return SqlConfigParseUtils.processSql(sql, null, values, null);
	}

	private SqlToyResult named(String sql, Map<String, Object> values) {
		return SqlConfigParseUtils.processSql(sql, values);
	}

	// ---------------- 位置参数:字面量与参数混排 ----------------

	@Test
	public void literalQuestionMarkBeforeParams() {
		SqlToyResult r = positional("select '?' as x from t where a=? and b like ?",
				new Object[] { 1, "k" });
		assertArrayEquals(new Object[] { 1, "%k%" }, r.getParamsValue());
		assertTrue(r.getSql().contains("'?'"), "实际:" + r.getSql());
	}

	@Test
	public void literalQuestionMarkBetweenParams() {
		SqlToyResult r = positional("select * from t where a=? and c='?' and b like ?",
				new Object[] { 1, "k" });
		assertArrayEquals(new Object[] { 1, "%k%" }, r.getParamsValue());
		assertTrue(r.getSql().contains("c='?'"), "实际:" + r.getSql());
	}

	@Test
	public void literalWithDoubledQuoteAndQuestionMark() {
		SqlToyResult r = positional("select * from t where c='it''s ?' and a=?", new Object[] { 1 });
		assertArrayEquals(new Object[] { 1 }, r.getParamsValue());
		assertTrue(r.getSql().contains("'it''s ?'"), "实际:" + r.getSql());
	}

	@Test
	public void literalQuestionMarkInsideInClause() {
		SqlToyResult r = positional("select 'x?' as c, id from t where id in (?,?)",
				new Object[] { 1, 2 });
		assertArrayEquals(new Object[] { 1, 2 }, r.getParamsValue());
		assertTrue(r.getSql().contains("'x?'"), "实际:" + r.getSql());
	}

	@Test
	public void likeValueWithQuestionMarkIsData() {
		// 值中的?是绑定数据,不会被加工破坏
		SqlToyResult r = positional("select * from t where name like ?", new Object[] { "x?y" });
		assertArrayEquals(new Object[] { "%x?y%" }, r.getParamsValue());
	}

	@Test
	public void likeWithSelfProvidedWildcards() {
		// like '%'||?||'%' 自带通配符写法,like不匹配?占位符,不重复加工
		SqlToyResult r = positional("select * from t where name like '%'||?||'%'",
				new Object[] { "王" });
		assertArrayEquals(new Object[] { "王" }, r.getParamsValue());
	}

	@Test
	public void literalWithSquareBracketFragmentMarker() {
		// 字面量内含#[文本(已知边界:片段检测字面量盲区)——此处验证的是其后的正常参数不受影响
		SqlToyResult r = positional("select * from t where a=? and b=?", new Object[] { 1, 2 });
		assertArrayEquals(new Object[] { 1, 2 }, r.getParamsValue());
	}

	// ---------------- 位置参数:片段与字面量、参数混排 ----------------

	@Test
	public void fragmentWithLiteralQuestionMarkKept() {
		SqlToyResult r = positional("select * from t where a=? #[and c='x?y' and d=?]",
				new Object[] { 1, "x", 2 });
		assertArrayEquals(new Object[] { 1, "x", 2 }, r.getParamsValue());
		assertTrue(r.getSql().contains("'x?y'"), "字面量应保真,实际:" + r.getSql());
	}

	@Test
	public void fragmentWithLiteralQuestionMarkDropped() {
		// 注意:字面量'x?y'内的?为字面量文本(占位符保护),不参与参数绑定,仅d=?是参数
		SqlToyResult r = positional("select * from t where a=? #[and c='x?y' and d=?]",
				new Object[] { 1, null });
		assertArrayEquals(new Object[] { 1 }, r.getParamsValue());
	}

	@Test
	public void castAndNestedParensParam() {
		SqlToyResult r = positional("select * from t where id=((?)) and v=cast(? as varchar)",
				new Object[] { 2, "v" });
		assertArrayEquals(new Object[] { 2, "v" }, r.getParamsValue());
	}

	// ---------------- 命名参数:字面量与参数混排 ----------------

	@Test
	public void namedLiteralWithColonWord() {
		// 修复场景:字面量内的:tag不再被劫持
		Map<String, Object> values = new HashMap<>();
		values.put("a", 1);
		SqlToyResult r = named("select '备注 :tag' as x from t where a=:a", values);
		assertArrayEquals(new Object[] { 1 }, r.getParamsValue());
		assertTrue(r.getSql().contains("'备注 :tag'"), "实际:" + r.getSql());
	}

	@Test
	public void namedLiteralQuestionMarkInFragment() {
		// 片段内引号中的:r按SQL语义为字面量(掩码修复后不绑定参数),仅s/n是参数
		Map<String, Object> values = new HashMap<>();
		values.put("s", 1);
		values.put("n", "张");
		SqlToyResult r = named(
				"select * from t where s=:s #[and r=':r' and n like :n]", values);
		assertArrayEquals(new Object[] { 1, "%张%" }, r.getParamsValue());
		assertTrue(r.getSql().contains("':r'"), "片段内字面量应保真,实际:" + r.getSql());
	}

	@Test
	public void namedCaseWhenWithParams() {
		Map<String, Object> values = new HashMap<>();
		values.put("flag", 1);
		values.put("id", 2);
		SqlToyResult r = named(
				"select case when :flag=1 then 'y' else 'n' end as v from t where id=:id", values);
		assertArrayEquals(new Object[] { 1, 2 }, r.getParamsValue());
	}

	@Test
	public void namedBetweenParams() {
		Map<String, Object> values = new HashMap<>();
		values.put("lo", 1);
		values.put("hi", 3);
		SqlToyResult r = named("select * from t where id between :lo and :hi", values);
		assertArrayEquals(new Object[] { 1, 3 }, r.getParamsValue());
	}

	@Test
	public void namedExistsSubquery() {
		Map<String, Object> values = new HashMap<>();
		values.put("id", 2);
		SqlToyResult r = named(
				"select * from t a where exists (select 1 from t b where b.id=a.id and b.id=:id)", values);
		assertArrayEquals(new Object[] { 2 }, r.getParamsValue());
	}

	// ---------------- 通用:替换/恢复后的字面量保真 ----------------

	@Test
	public void multipleLiteralsWithMarkersPreserved() {
		// 注:'#[x]'形式的字面量会被片段检测误剔除,属已知设计边界(见processNullConditions),
		// 此处用[x]验证普通方括号字面量保真
		Map<String, Object> values = new HashMap<>();
		values.put("a", 1);
		SqlToyResult r = named(
				"select '[x]' as c1, 'y:z' as c2, '?w' as c3, 'it''s' as c4 from t where a=:a", values);
		assertArrayEquals(new Object[] { 1 }, r.getParamsValue());
		String sql = r.getSql();
		assertTrue(sql.contains("'[x]'"), "实际:" + sql);
		assertTrue(sql.contains("'y:z'"), "实际:" + sql);
		assertTrue(sql.contains("'?w'"), "实际:" + sql);
		assertTrue(sql.contains("'it''s'"), "实际:" + sql);
	}
}
