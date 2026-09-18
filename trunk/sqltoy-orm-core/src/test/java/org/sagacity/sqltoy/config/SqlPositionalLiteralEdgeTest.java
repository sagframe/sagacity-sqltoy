package org.sagacity.sqltoy.config;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.Arrays;

import org.junit.jupiter.api.Test;
import org.sagacity.sqltoy.config.model.SqlToyResult;

/**
 * 位置参数模式字面量?占位符化(修复)的等价性测试电池。
 * 覆盖真实项目常规写法(位置参数+字面量,字面量内不含?),
 * 修复前后输出必须完全一致——修复仅允许影响"字面量内含?被误当参数占位符"的场景。
 */
public class SqlPositionalLiteralEdgeTest {

	private SqlToyResult positional(String sql, Object[] values) {
		return SqlConfigParseUtils.processSql(sql, null, values, null);
	}

	@Test
	public void fragmentKeptWhenParamsPresent() {
		SqlToyResult r = positional("select * from t where a=? #[and b=?]", new Object[] { 1, 2 });
		assertArrayEquals(new Object[] { 1, 2 }, r.getParamsValue());
		assertTrue(r.getSql().contains("b=?"), "参数齐全时片段应保留,实际:" + r.getSql());
	}

	@Test
	public void fragmentDroppedWhenParamNull() {
		SqlToyResult r = positional("select * from t where a=? #[and b=?]", new Object[] { 1, null });
		assertArrayEquals(new Object[] { 1 }, r.getParamsValue());
		assertFalse(r.getSql().contains("b=?"), "参数为null时片段应剔除,实际:" + r.getSql());
	}

	@Test
	public void literalWithoutQuestionMarkUntouched() {
		SqlToyResult r = positional("select * from t where remark='备注' and a=? #[and b=?]",
				new Object[] { 1, 2 });
		assertArrayEquals(new Object[] { 1, 2 }, r.getParamsValue());
		assertTrue(r.getSql().contains("'备注'"), "字面量应保持原样,实际:" + r.getSql());
	}

	@Test
	public void likeWithPlainLiteral() {
		SqlToyResult r = positional("select * from t where remark='备注' and name like ?",
				new Object[] { "张" });
		assertArrayEquals(new Object[] { "%张%" }, r.getParamsValue());
		assertTrue(r.getSql().contains("'备注'"), "实际:" + r.getSql());
	}

	@Test
	public void blankPositionalWithPlainLiteral() {
		// @blank(?)语义:参数仅用于空判断后被剥离(ProcessBlankTest同款语义,非空也不绑定)
		SqlToyResult r = positional("select * from t where remark='备注' and @blank(?) and status=?",
				new Object[] { "x", 1 });
		assertArrayEquals(new Object[] { 1 }, r.getParamsValue());
		assertTrue(r.getSql().contains("'备注'"), "实际:" + r.getSql());
	}

	@Test
	public void inPositionalWithPlainLiteral() {
		SqlToyResult r = positional("select '标签' from t where id in (?)", new Object[] { new Integer[] { 1, 2 } });
		assertArrayEquals(new Object[] { 1, 2 }, r.getParamsValue());
		assertTrue(r.getSql().contains("'标签'"), "实际:" + r.getSql());
	}

	@Test
	public void replaceNullWithPlainLiteral() {
		// a的值为null:a=?转换为a is null,参数剔除后仅剩b的值
		SqlToyResult r = positional("select * from t where remark='备注' and a=? and b=?",
				new Object[] { null, "x" });
		assertArrayEquals(new Object[] { "x" }, r.getParamsValue());
		assertTrue(r.getSql().contains("a is null"), "实际:" + r.getSql());
		assertTrue(r.getSql().contains("'备注'"), "实际:" + r.getSql());
	}

	@Test
	public void multipleFragmentsWithPlainLiteral() {
		SqlToyResult r = positional("select * from t where remark='备注' and a=? #[and b=?] #[and c=?]",
				new Object[] { 1, null, 3 });
		// c有值片段保留,b为null片段剔除
		assertEquals(2, r.getParamsValue().length);
		assertEquals(1, r.getParamsValue()[0]);
		assertEquals(3, r.getParamsValue()[1]);
		assertTrue(r.getSql().contains("'备注'"), "实际:" + r.getSql());
	}

	// ---------------- 修复后的字面量含?场景 ----------------

	@Test
	public void literalQuestionMarkFragmentNoCrash() {
		// 修复前:字面量'a?'中的?被计入preParamCnt,片段读取越界参数崩溃
		SqlToyResult r = positional("select * from t where remark='a?' and b=? #[and c=?]",
				new Object[] { "v1", "v2" });
		assertArrayEquals(new Object[] { "v1", "v2" }, r.getParamsValue());
		assertTrue(r.getSql().contains("'a?'"), "字面量应保真,实际:" + r.getSql());
		assertTrue(r.getSql().contains("c=?"), "实际:" + r.getSql());
	}

	@Test
	public void literalQuestionMarkWithNullParam() {
		// 修复前:null参数的null文本替换命中字面量内的?(字面量被破坏),且后续参数错位绑定
		SqlToyResult r = positional("select * from t where remark='OK?' and a=? and b=?",
				new Object[] { null, "x" });
		assertArrayEquals(new Object[] { "x" }, r.getParamsValue());
		assertTrue(r.getSql().contains("'OK?'"), "字面量应保真,实际:" + r.getSql());
		assertTrue(r.getSql().contains("a is null"), "实际:" + r.getSql());
	}

	@Test
	public void literalQuestionMarkInInClause() {
		SqlToyResult r = positional("select 'id?' from t where id in (?)",
				new Object[] { new Integer[] { 1, 2 } });
		assertArrayEquals(new Object[] { 1, 2 }, r.getParamsValue());
		assertTrue(r.getSql().contains("'id?'"), "字面量应保真,实际:" + r.getSql());
	}

	@Test
	public void literalQuestionMarkWithBlank() {
		// 字面量内的?不应占用@blank(?)的参数位
		SqlToyResult r = positional("select * from t where remark='确定?' and @blank(?) and status=?",
				new Object[] { "x", 1 });
		assertArrayEquals(new Object[] { 1 }, r.getParamsValue());
		assertTrue(r.getSql().contains("'确定?'"), "字面量应保真,实际:" + r.getSql());
	}

	// ---------------- 占位符机制共存影响点 ----------------

	@Test
	public void literalQuestionMarkInsideKeptFragment() {
		// 保留片段内的字面量?:占位符随片段保留,末尾恢复后字面量保真
		SqlToyResult r = positional("select * from t where a=? #[and remark='确定?' and c=?]",
				new Object[] { 1, "x" });
		assertArrayEquals(new Object[] { 1, "x" }, r.getParamsValue());
		assertTrue(r.getSql().contains("'确定?'"), "字面量应保真,实际:" + r.getSql());
		assertFalse(r.getSql().contains("#sqltoy"), "占位符不应残留,实际:" + r.getSql());
	}

	@Test
	public void literalQuestionMarkInsideDroppedFragment() {
		// 剔除片段内的字面量?:随片段一起消失(片段内容本就应整体剔除)
		SqlToyResult r = positional("select * from t where a=? #[and remark='确定?' and c=?]",
				new Object[] { 1, null });
		assertArrayEquals(new Object[] { 1 }, r.getParamsValue());
		assertFalse(r.getSql().contains("确定?"), "实际:" + r.getSql());
		assertFalse(r.getSql().contains("#sqltoy"), "实际:" + r.getSql());
	}

	@Test
	public void literalDoubleQuestionMarkPreserved() {
		// 字面量内的??经DBL_QUESTMARK占位符后恢复保真
		SqlToyResult r = positional("select 'a??b' from t where id in (?)",
				new Object[] { new Integer[] { 1, 2 } });
		assertArrayEquals(new Object[] { 1, 2 }, r.getParamsValue());
		assertTrue(r.getSql().contains("'a??b'"), "实际:" + r.getSql());
	}

	@Test
	public void literalQuestionMarkWithLikeEscapeClause() {
		// 字面量含?不影响like参数的转义与ESCAPE子句追加
		SqlToyResult r = positional("select * from t where remark='a?' and name like ?",
				new Object[] { "a_b" });
		String v = (String) r.getParamsValue()[0];
		assertTrue(v.contains("\\_"), "实际:" + v);
		assertTrue(r.getSql().toLowerCase().contains("escape"), "实际:" + r.getSql());
		assertTrue(r.getSql().contains("'a?'"), "字面量应保真,实际:" + r.getSql());
	}

	@Test
	public void multipleLiteralsWithQuestionMarks() {
		SqlToyResult r = positional("select 'a?' as c1, 'b?' as c2 from t where d=? and e=?",
				new Object[] { 1, 2 });
		assertArrayEquals(new Object[] { 1, 2 }, r.getParamsValue());
		assertTrue(r.getSql().contains("'a?'") && r.getSql().contains("'b?'"), "实际:" + r.getSql());
	}
}
