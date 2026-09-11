package org.sagacity.sqltoy.config;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.Arrays;
import java.util.HashMap;
import java.util.Map;

import org.junit.jupiter.api.Test;
import org.sagacity.sqltoy.config.model.SqlToyConfig;
import org.sagacity.sqltoy.config.model.SqlToyResult;
import org.sagacity.sqltoy.config.model.SqlType;
import org.sagacity.sqltoy.utils.SqlUtil;

/**
 * 普通SQL行为等价性测试电池:字面量感知(掩码)修复的前后对照基线。
 * 这些场景覆盖真实项目的常规写法(SQL含字面量但字面量内不含?/:参数),
 * 修复前后输出必须完全一致——修复只允许影响"字面量内容被误当参数"的异常路径。
 */
public class SqlLiteralAwareEdgeTest {

	private SqlToyResult named(String sql, Map<String, Object> values) {
		return SqlConfigParseUtils.processSql(sql, values);
	}

	private SqlToyResult named(String sql, Map<String, Object> values, String dialect) {
		return SqlConfigParseUtils.processSql(sql, values, dialect);
	}

	private SqlToyResult positional(String sql, Object[] values) {
		return SqlConfigParseUtils.processSql(sql, null, values);
	}

	@Test
	public void namedQueryWithPlainLiteral() {
		Map<String, Object> values = new HashMap<>();
		values.put("sexType", "M");
		values.put("name", "张");
		SqlToyResult r = named(
				"select '有效' as statusName, name from staff_info where 1=1 #[and sexType=:sexType] #[and name like :name]",
				values);
		assertArrayEquals(new Object[] { "M", "%张%" }, r.getParamsValue());
		assertTrue(r.getSql().contains("'有效' as statusName"), "字面量应保持原样,实际:" + r.getSql());
	}

	@Test
	public void doubledQuoteLiteralThenParam() {
		Map<String, Object> values = new HashMap<>();
		values.put("status", "1");
		SqlToyResult r = named("select * from t where remark='it''s ok' and status=:status", values);
		assertArrayEquals(new Object[] { "1" }, r.getParamsValue());
		assertTrue(r.getSql().contains("'it''s ok'"), "''转义字面量应保持原样,实际:" + r.getSql());
		assertTrue(r.getSql().contains("status=?"), "实际:" + r.getSql());
	}

	/**
	 * MySQL反斜杠转义字面量('a\'b')后接命名参数:掩码必须正确识别\'不终结字面量,
	 * 否则后续真参数会被掩掉(此用例是掩码方案最关键的回归防线)
	 */
	@Test
	public void mysqlBackslashLiteralThenParam() {
		Map<String, Object> values = new HashMap<>();
		values.put("name", "ab");
		SqlToyResult r = named("select * from t where remark='a\\'b' and name like :name", values, "mysql");
		assertArrayEquals(new Object[] { "%ab%" }, r.getParamsValue());
		assertTrue(r.getSql().contains("'a\\'b'"), "MySQL转义字面量应保持原样,实际:" + r.getSql());
	}

	@Test
	public void likeWithLiteralElsewhere() {
		Map<String, Object> values = new HashMap<>();
		values.put("name", "ab");
		SqlToyResult r = named("select '备注' from t where name like :name", values);
		assertArrayEquals(new Object[] { "%ab%" }, r.getParamsValue());
		assertTrue(r.getSql().contains("'备注'"), "实际:" + r.getSql());
	}

	@Test
	public void inListWithLiteralInSelect() {
		Map<String, Object> values = new HashMap<>();
		values.put("ids", Arrays.asList(1, 2));
		SqlToyResult r = named("select 'A,B,C' as tags from t where id in (:ids)", values);
		assertArrayEquals(new Object[] { 1, 2 }, r.getParamsValue());
		assertTrue(r.getSql().contains("'A,B,C'"), "实际:" + r.getSql());
	}

	@Test
	public void positionalWithPlainLiteral() {
		SqlToyResult r = positional("select '固定值' from t where a=? and b=?", new Object[] { 1, 2 });
		assertArrayEquals(new Object[] { 1, 2 }, r.getParamsValue());
		assertTrue(r.getSql().contains("'固定值'"), "实际:" + r.getSql());
	}

	@Test
	public void noLiteralNamed() {
		Map<String, Object> values = new HashMap<>();
		values.put("a", 1);
		values.put("b", "x");
		SqlToyResult r = named("select * from t where a=:a and b=:b", values);
		assertArrayEquals(new Object[] { 1, "x" }, r.getParamsValue());
	}

	@Test
	public void likeLiteralPatternUntouched() {
		// like '常%':字面量形式的like模式无参数绑定,不应被like加工触碰
		Map<String, Object> values = new HashMap<>();
		values.put("status", "1");
		SqlToyResult r = named("select * from t where name like '常%' and status=:status", values);
		assertArrayEquals(new Object[] { "1" }, r.getParamsValue());
		assertTrue(r.getSql().contains("like '常%'"), "字面量like模式应保持原样,实际:" + r.getSql());
	}

	@Test
	public void pseudoConditionWithLiteralInside() {
		// #[]片段内含字面量(字面量内无参数),片段按参数c的有无正常保留/剔除
		Map<String, Object> values = new HashMap<>();
		values.put("a", 1);
		values.put("c", 2);
		SqlToyResult r = named("select * from t where a=:a #[and remark='备注' and c=:c]", values);
		assertEquals(2, r.getParamsValue().length);
		assertTrue(r.getSql().contains("'备注'"), "片段保留时字面量应保持原样,实际:" + r.getSql());
	}

	// ---------------- 命名模式字面量?保真(问号占位符机制回归) ----------------

	@Test
	public void namedLiteralQuestionMarkPreserved() {
		Map<String, Object> values = new HashMap<>();
		values.put("a", 1);
		SqlToyResult r = named("select 'a?b' as x from t where a=:a", values);
		assertArrayEquals(new Object[] { 1 }, r.getParamsValue());
		assertTrue(r.getSql().contains("'a?b'"), "字面量应保真,实际:" + r.getSql());
	}

	@Test
	public void namedLiteralQuestionMarkInsideFragment() {
		Map<String, Object> values = new HashMap<>();
		values.put("status", 1);
		values.put("name", "张");
		SqlToyResult r = named(
				"select * from t where status=:status #[and remark='确定?' and name like :name]", values);
		assertArrayEquals(new Object[] { 1, "%张%" }, r.getParamsValue());
		assertTrue(r.getSql().contains("'确定?'"), "片段保留时字面量应保真,实际:" + r.getSql());
	}

	@Test
	public void namedDoubledQuoteLiteralWithQuestionMark() {
		Map<String, Object> values = new HashMap<>();
		values.put("a", 1);
		SqlToyResult r = named("select * from t where remark='it''s ? ok' and a=:a", values);
		assertArrayEquals(new Object[] { 1 }, r.getParamsValue());
		assertTrue(r.getSql().contains("'it''s ? ok'"), "''转义字面量应保真,实际:" + r.getSql());
	}

	@Test
	public void twoLoopsWithLiteralsStillWork() {
		// @loop与字面量共存:循环内容本身就是带引号字符串,展开不受掩码影响(processLoop先于命名转换)
		Map<String, Object> values = new HashMap<>();
		values.put("a", Arrays.asList(1, 2));
		SqlToyResult r = named("select * from t where #[@loop(:a,'x=:a[i]','or')]", values);
		assertTrue(r.getSql().contains("x=1") && r.getSql().contains("x=2"), "实际:" + r.getSql());
		assertFalse(r.getSql().contains("@loop"), "实际:" + r.getSql());
	}

	// ---------------- 修复后的字面量保真场景 ----------------

	@Test
	public void literalColonInPositionalMode() {
		// 位置参数模式下字面量内的:xxx同样不应被转换为?占位符
		SqlToyResult r = positional("select 'a:b' as x from t where a=?", new Object[] { 1 });
		assertArrayEquals(new Object[] { 1 }, r.getParamsValue());
		assertTrue(r.getSql().contains("'a:b'"), "字面量应保持原样,实际:" + r.getSql());
	}

	@Test
	public void likeTextInsideLiteralNotProcessed() {
		// 字面量内的"like ?"文本不是真正的like条件,不应触发%包裹
		SqlToyResult r = positional("select 'x like ?' as x from t where a=?", new Object[] { 1 });
		assertArrayEquals(new Object[] { 1 }, r.getParamsValue(), "字面量内的like ?不应导致参数被%包裹");
	}

	@Test
	public void literalQuestionMarkWithDoubledQuote() {
		// ''转义字面量内含?,占位符计数应跳过整个字面量
		Map<String, Object> values = new HashMap<>();
		values.put("name", "ab");
		SqlToyResult r = named("select * from t where remark='it''s ok?' and name like :name", values);
		assertArrayEquals(new Object[] { "%ab%" }, r.getParamsValue());
		assertTrue(r.getSql().contains("'it''s ok?'"), "实际:" + r.getSql());
	}

	@Test
	public void mysqlEscapedQuoteLiteralWithQuestionMark() {
		// mysql系:\'不终结字面量,字面量内的?不参与计数,后续真参数正常对位
		Map<String, Object> values = new HashMap<>();
		values.put("name", "ab");
		SqlToyResult r = named("select * from t where remark='a\\'b?' and name like :name", values, "mysql");
		assertArrayEquals(new Object[] { "%ab%" }, r.getParamsValue());
		assertTrue(r.getSql().contains("'a\\'b?'"), "实际:" + r.getSql());
	}

	// ---------------- hasWith/hasUnion掩码的方言一致性(2026-9-10 转义约定统一经DBProfile提供) ----------------

	@Test
	public void hasWithMysqlEscapedQuoteNoFalsePositive() {
		// mysql约定\'不终结字面量:字面量整体被掩,其中的with xx as (文本不得泄漏误报
		String sql = "select * from t where remark = 'don\\'t write with t1 as (select 1)' and a=1";
		assertFalse(SqlConfigParseUtils.hasWith(sql, true), "mysql约定下字面量内with文本不应触发");
		// 对照基线:标准约定按\'终结字面量,掩码错位导致泄漏文本被误判(此sql本就是mysql写法)
		assertTrue(SqlConfigParseUtils.hasWith(sql, false), "标准约定下掩码错位应误判(对照基线)");
	}

	@Test
	public void hasWithMysqlEscapedQuoteNoFalseNegative() {
		// \'字面量之后的真实CTE:mysql约定掩码不错位,真实with被正确识别
		String sql = "select * from t where remark='a\\'b' and id in (with cte as (select 1) select * from cte)";
		assertTrue(SqlConfigParseUtils.hasWith(sql, true), "mysql约定下真实with应被识别");
		// 对照基线:标准约定下\'提前终结字面量,后续真实with被幻影字面量吞没漏判
		assertFalse(SqlConfigParseUtils.hasWith(sql, false), "标准约定下真实with被吞没(对照基线)");
	}

	@Test
	public void hasUnionMysqlEscapedQuoteLiteral() {
		// mysql \'字面量内的union文本:mysql约定下字面量整体被掩不误报
		String sql = "select a from t1 where remark='it\\'s union all mine'";
		assertFalse(SqlUtil.hasUnion(sql, false, true), "mysql约定下字面量内union文本不应触发");
	}

	@Test
	public void parseConfigMysqlDialectEscapeAwareMask() {
		// parseSqlToyConfig按mysql方言解析掩码:\'字面量不错位,其中with文本不会误置hasWith
		String sql = "select * from t where remark = 'don\\'t write with t1 as (select 1)'";
		SqlToyConfig config = SqlConfigParseUtils.parseSqlToyConfig(sql, "mysql", SqlType.search);
		assertFalse(config.isHasWith(), "mysql方言下hasWith不应被字面量内with文本误置");
	}
}
