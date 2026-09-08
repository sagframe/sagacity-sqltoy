package org.sagacity.sqltoy.config;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.HashMap;
import java.util.Map;

import org.junit.jupiter.api.Test;
import org.sagacity.sqltoy.config.model.SqlToyResult;
import org.sagacity.sqltoy.dialect.utils.DialectUtils;

/**
 * 字面量内含回车换行符的场景验证:
 * 所有字面量感知实现(escapeQuestionMarkInLiterals/maskLiterals/getSymMarkIndex相邻引号)
 * 均为字符级状态机无行语义,\r\n在字面量内视为普通内容字符,不应影响引号配对、参数识别与占位符恢复
 */
public class SqlNewlineLiteralEdgeTest {

	private SqlToyResult positional(String sql, Object[] values) {
		return SqlConfigParseUtils.processSql(sql, null, values, null);
	}

	private SqlToyResult named(String sql, Map<String, Object> values) {
		return SqlConfigParseUtils.processSql(sql, values);
	}

	@Test
	public void positionalLiteralWithCrLfQuestionMark() {
		// 字面量内含\r\n和?:?被占位符保护不占用参数位,恢复后字面量(含换行)保真
		SqlToyResult r = positional(
				"select * from t where remark='第一行\r\n?第二行' and id=? and name like ?",
				new Object[] { 2, "%王%" });
		assertArrayEquals(new Object[] { 2, "%王%" }, r.getParamsValue());
		assertTrue(r.getSql().contains("'第一行\r\n?第二行'"), "字面量含换行应保真,实际:" + r.getSql());
	}

	@Test
	public void namedLiteralWithNewlineAndColonParam() {
		// 字面量内换行+:xxx,掩码字符扫描不识别行边界,:xxx仍按字面量不绑定
		Map<String, Object> values = new HashMap<>();
		values.put("a", 1);
		SqlToyResult r = named("select '备注\n:tag\n结束' as x from t where a=:a", values);
		assertArrayEquals(new Object[] { 1 }, r.getParamsValue());
		assertTrue(r.getSql().contains("'备注\n:tag\n结束'"), "字面量应保真,实际:" + r.getSql());
	}

	@Test
	public void likeAcrossNewline() {
		// like与?之间跨行(\\s涵盖\\r\\n),%包裹正常
		SqlToyResult r = positional("select * from t where remark\nlike\n?", new Object[] { "张" });
		assertArrayEquals(new Object[] { "%张%" }, r.getParamsValue());
	}

	@Test
	public void doubledQuoteLiteralWithNewlineAndQuestionMark() {
		SqlToyResult r = positional(
				"select * from t where remark='it''s\r\n? ok' and id=?", new Object[] { 2 });
		assertArrayEquals(new Object[] { 2 }, r.getParamsValue());
		assertTrue(r.getSql().contains("'it''s\r\n? ok'"), "实际:" + r.getSql());
	}

	@Test
	public void getParamsCountWithMultilineLiteral() {
		// 字面量内含\r\n?不参与参数计数,仅where的?计数
		String sql = "select * from t\nwhere remark='a\r\n?b'\nand id=?";
		assertEquals(1, DialectUtils.getParamsCount(sql, false));
	}

	@Test
	public void escapedQuoteAcrossNewlineBoundary() {
		// 反斜杠转义引号与换行组合的字面量:闭合位置不受换行影响
		Map<String, Object> values = new HashMap<>();
		values.put("a", 1);
		SqlToyResult r = named("select * from t where remark='a\\\nb' and a=:a", values);
		assertArrayEquals(new Object[] { 1 }, r.getParamsValue());
	}
}
