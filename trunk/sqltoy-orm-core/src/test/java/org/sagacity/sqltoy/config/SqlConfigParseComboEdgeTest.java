package org.sagacity.sqltoy.config;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.Arrays;
import java.util.HashMap;
import java.util.Map;

import org.junit.jupiter.api.Disabled;
import org.junit.jupiter.api.Test;
import org.sagacity.sqltoy.config.model.SqlToyResult;

/**
 * SqlConfigParseUtils 组合边缘场景测试:
 * like参数对位、字面量/注释干扰、#[...]嵌套、多@loop并存、in列表展开
 */
public class SqlConfigParseComboEdgeTest {

	private SqlToyResult positional(String sql, Object[] values) {
		return SqlConfigParseUtils.processSql(sql, null, values);
	}

	private SqlToyResult named(String sql, Map<String, Object> values) {
		return SqlConfigParseUtils.processSql(sql, values);
	}

	// ---------------- like参数对位 ----------------

	@Test
	public void likeAlignsToCorrectParam() {
		SqlToyResult r = positional("select * from t where a like ? and b like ?", new Object[] { "50", "ab" });
		assertEquals(2, r.getParamsValue().length);
		assertEquals("%50%", r.getParamsValue()[0]);
		assertEquals("%ab%", r.getParamsValue()[1]);
	}

	@Test
	public void likeValueWithUnderscoreGetsEscapedAndEscapeClause() {
		SqlToyResult r = positional("select * from t where name like ?", new Object[] { "a_b" });
		String v = (String) r.getParamsValue()[0];
		assertTrue(v.contains("\\_"), "下划线应被转义,实际:" + v);
		assertTrue(r.getSql().toLowerCase().contains("escape"), "含转义字符时sql应追加ESCAPE子句,实际:" + r.getSql());
	}

	@Test
	public void likeValueAlreadyHasPercentNotDoubleWrapped() {
		// 值自带%时视为用户显式通配,不再前后补%,%作为通配符保留
		SqlToyResult r = positional("select * from t where name like ?", new Object[] { "5%0" });
		assertEquals("5%0", r.getParamsValue()[0]);
	}

	/**
	 * 回归测试:字符串字面量中的?不参与like的参数计数(修复后与JDBC驱动语义对齐)。
	 * '50?'中的?曾被ARG_NAME_PATTERN计数,like ?对位到不存在的第2个参数导致数组越界。
	 */
	@Test
	public void likeParamCountIgnoresLiteralQuestionMark() {
		SqlToyResult r = positional("select '50?' as x from t where name like ?", new Object[] { "ab" });
		assertEquals("%ab%", r.getParamsValue()[0], "字面量中的?不应参与like参数计数");
	}

	/**
	 * 回归测试:字符串字面量中的:xxx不被命名参数替换逻辑劫持(修复后字面量保真)。
	 * 修复前'备注 :tag'中的:tag因无值被内联为null文本,字面量被静默破坏为'备注 null'。
	 */
	@Test
	public void namedParamInsideLiteralNotHijacked() {
		Map<String, Object> values = new HashMap<>();
		values.put("name", "ab");
		SqlToyResult r = named("select '备注 :tag' as x from t where name like :name", values);
		assertTrue(r.getSql().contains("备注 :tag"), "字面量应保持原样不被参数化,实际:" + r.getSql());
		assertEquals(1, r.getParamsValue().length, "仅:name是有效参数");
	}

	// ---------------- #[...]嵌套与静态片段 ----------------

	@Test
	public void nestedPseudoConditionsInnerAndOuter() {
		Map<String, Object> values = new HashMap<>();
		values.put("a", 1);
		values.put("c", 2);
		// 外层直系参数c有值→外层保留;内层参数b未传→内层#[or b=:b]剔除
		SqlToyResult r = named("select * from t where a=:a #[and (c=:c #[or b=:b])]", values);
		assertFalse(r.getSql().contains("#["), "处理完成后不应残留#[标记,实际:" + r.getSql());
		assertFalse(r.getSql().contains("b="), "b未传值时内层条件应被剔除,实际:" + r.getSql());
		assertEquals(2, r.getParamsValue().length);
		assertEquals(1, r.getParamsValue()[0]);
		assertEquals(2, r.getParamsValue()[1]);
	}

	/**
	 * 行为记录:#[]内无动态参数的静态片段原样保留(含#[标记),
	 * #[]语义即动态条件片段,包裹纯静态内容属使用不当,不做剔除也不做标记清理
	 */
	@Test
	public void pseudoMarkWithoutDynamicParamsIsKeptVerbatim() {
		SqlToyResult r = positional("select * from t where 1=1 #[and status='A']", new Object[] {});
		assertTrue(r.getSql().contains("#[and status='A']"), "无动态参数的#[]片段原样保留,实际:" + r.getSql());
		assertEquals(0, r.getParamsValue().length);
	}

	// ---------------- @loop组合 ----------------

	/**
	 * 同一条sql中两个@loop并存(2025-5-17的threadLocal计数器服务的场景),各自独立展开互不干扰
	 */
	@Test
	public void twoLoopsExpandIndependently() {
		Map<String, Object> values = new HashMap<>();
		values.put("a", Arrays.asList(1, 2));
		values.put("b", Arrays.asList("x", "y"));
		SqlToyResult r = named(
				"select * from t where #[@loop(:a,'x=:a[i]','or')] and #[@loop(:b,'y=:b[i]','and')]", values);
		String sql = r.getSql();
		assertFalse(sql.contains("@loop"), "@loop应被完全展开,实际:" + sql);
		assertTrue(sql.contains("x=1") && sql.contains("x=2"), "第一个loop应展开,实际:" + sql);
		assertTrue(sql.contains("y='x'") && sql.contains("y='y'"), "第二个loop应展开且字符串带引号,实际:" + sql);
	}

	// ---------------- in列表展开 ----------------

	@Test
	public void inListPositionalExpansion() {
		SqlToyResult r = positional("select * from t where id in (?)", new Object[] { new Integer[] { 1, 2, 3 } });
		assertArrayEquals(new Object[] { 1, 2, 3 }, r.getParamsValue());
		assertFalse(r.getSql().contains("in (?)"), "in (?)应展开为等数量的?,实际:" + r.getSql());
	}

	@Test
	public void inListNamedExpansion() {
		Map<String, Object> values = new HashMap<>();
		values.put("ids", Arrays.asList(7, 8, 9));
		SqlToyResult r = named("select * from t where id in (:ids)", values);
		assertArrayEquals(new Object[] { 7, 8, 9 }, r.getParamsValue());
	}

	/**
	 * 行为记录:in列表混含null/数字/字符串的展开结果(null的处理方式以实际为准,先探明再定规则)
	 */
	@Test
	public void inListWithMixedNullNumberString() {
		Map<String, Object> values = new HashMap<>();
		values.put("ids", Arrays.asList(1, null, "a"));
		SqlToyResult r = named("select * from t where id in (:ids)", values);
		System.out.println("[mixed in-list] sql=" + r.getSql() + " params=" + Arrays.toString(r.getParamsValue()));
		// 展开不应抛异常,且不应残留:ids占位
		assertFalse(r.getSql().contains(":ids"));
	}
}
