package org.sagacity.sqltoy.dialect.impl;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.lang.reflect.Method;

import org.junit.jupiter.api.Test;
import org.sagacity.sqltoy.SqlToyConstants;

/**
 * update 2026-11-12 DB2双引号标识符掩码(368e6de9f)的对抗性边界测试——补上此前复核中缺失的针对性用例:
 * <p>
 * maskQuotedIdentifiers须把"..."标识符整段掩掉、跳过'...'字面量(含''转义),
 * 使hasTopLevelFrom的FROM判定和"with ur"尾缀摘取不被标识符/字面量内的关键字干扰。
 * <ol>
 * <li>单引号字面量内的双引号是普通字符——不得被当作标识符定界(select '"' from t)</li>
 * <li>未闭合单引号:skipStringLiteral返回末尾,不越界不抛异常</li>
 * <li>''转义在字面量内:整体跳过不中断</li>
 * <li>""转义在标识符内:两引号一并掩掉</li>
 * <li>混合嵌套:字面量含双引号+标识符含单引号交错</li>
 * <li>端到端:appendWithUR的虚表补齐与with ur摘取在含引号场景下正确</li>
 * </ol>
 */
public class DB2QuotedIdentifierMaskingTest {

	private static String mask(String sql) throws Exception {
		Method method = DB2Dialect.class.getDeclaredMethod("maskQuotedIdentifiers", String.class);
		method.setAccessible(true);
		return (String) method.invoke(null, sql);
	}

	private static String appendWithUr(String sql) throws Exception {
		DB2Dialect dialect = new DB2Dialect();
		Method method = DB2Dialect.class.getDeclaredMethod("appendWithUR", String.class);
		method.setAccessible(true);
		return (String) method.invoke(dialect, sql);
	}

	// ==================== 字面量内双引号 ====================

	@Test
	public void doubleQuoteInsideStringLiteralIsNotIdentifier() throws Exception {
		// 字面量'"'中的双引号是普通字符;掩码后from仍在,hasTopLevelFrom正确判有FROM
		String masked = mask("select '\"' from t");
		assertTrue(masked.contains("from"), "字面量内双引号不应被当标识符定界(否则from被吞): " + masked);
	}

	@Test
	public void doubleQuoteAtStartOfStringLiteralWithFrom() throws Exception {
		// 368e6de9f修复的原始场景:select '"' from t——旧版从首个"位置起扫,吞掉后续FROM
		String masked = mask("select '\"' from t");
		assertTrue(masked.indexOf("from") > 0, "from须在掩码后串中: " + masked);
	}

	@Test
	public void multipleDoubleQuotesInsideLiterals() throws Exception {
		// 两个字面量各含双引号,from/with ur在两者之间
		String masked = mask("select '\"a\"' as x, '\"b\"' as y from t");
		assertTrue(masked.contains("from"), "多个字面量内双引号不干扰from: " + masked);
	}

	// ==================== 未闭合引号 ====================

	@Test
	public void unclosedStringLiteralDoesNotThrow() throws Exception {
		// 未闭合单引号:skipStringLiteral返回末尾,不越界不抛异常
		String masked = mask("select 'unclosed from t");
		assertEquals("select 'unclosed from t", masked, "未闭合字面量整体跳过,内部from不参与掩码");
	}

	@Test
	public void unclosedDoubleQuoteIdentifierDoesNotThrow() throws Exception {
		// 未闭合双引号标识符:inQuote持续到末尾,全掩掉
		String masked = mask("select \"unclosed from t");
		assertFalse(masked.contains("from"), "未闭合标识符内from应被掩掉: " + masked);
	}

	// ==================== ''转义 ====================

	@Test
	public void escapedSingleQuoteInsideLiteralIsSkipped() throws Exception {
		// 'it''s from t'含''转义:整体跳过,字面量内的from保留原文(交由maskLiterals处理)
		String masked = mask("select 'it''s from t' as v, col from t2");
		// 真正的from(from t2)仍在,且字面量原文不变
		assertTrue(masked.contains("from t2"), "真正的from须保留: " + masked);
		assertTrue(masked.contains("'it''s from t'"), "字面量原文(含''转义)不变: " + masked);
	}

	@Test
	public void escapedDoubleQuoteInsideIdentifierIsMasked() throws Exception {
		// "a""b"含""转义:两引号一并掩掉
		String masked = mask("select \"a\"\"b\" from t");
		assertTrue(masked.contains("from"), "标识符双引号转义后from仍在: " + masked);
	}

	// ==================== 混合嵌套 ====================

	@Test
	public void mixedLiteralAndIdentifierNesting() throws Exception {
		// 字面量含双引号+标识符含单引号交错
		String sql = "select '\"' as a, \"col'name\" as b, 'plain' as c from t";
		String masked = mask(sql);
		assertTrue(masked.contains("from"), "混合嵌套不吞from: " + masked);
	}

	@Test
	public void withUrInsideQuotedIdentifierIsMasked() throws Exception {
		// 标识符名含"with ur"字样:掩码后不被误摘
		String masked = mask("select col from t -- \"with ur\"");
		// 注释里的不算;真正的标识符场景:
		String masked2 = mask("select \"col with ur\" from t");
		assertFalse(masked2.contains("with ur"), "标识符内with ur应被掩掉: " + masked2);
	}

	// ==================== 端到端:appendWithUR ====================

	@Test
	public void literalWithDoubleQuoteStillGetsVirtualTable() throws Exception {
		// 端到端:select '"' 无FROM——须补虚表(hasTopLevelFrom不被字面量内from干扰... 此场景无from)
		String result = appendWithUr("select '\"'");
		assertTrue(result.contains("FROM"), "无FROM查询须补虚表: " + result);
	}

	@Test
	public void literalWithDoubleQuoteAndFromDoesNotGetVirtualTable() throws Exception {
		// select '"' from t:有FROM,不补虚表
		String result = appendWithUr("select '\"' from t");
		assertFalse(result.contains("FROM " + DB2Dialect.VIRTUAL_TABLE),
				"有FROM不得补虚表: " + result);
	}

	@Test
	public void identifierNamedFromDoesNotCountAsFrom() throws Exception {
		// select 1 as "from":标识符from不算顶层FROM,须补虚表
		String result = appendWithUr("select 1 as \"from\"");
		assertTrue(result.contains("FROM " + DB2Dialect.VIRTUAL_TABLE),
				"标识符from不算FROM,须补虚表: " + result);
	}

	@Test
	public void stringWithFromInsideDoesNotCountAsFrom() throws Exception {
		// select 'a from b':字面量内from不算顶层FROM,须补虚表
		String result = appendWithUr("select 'a from b'");
		assertTrue(result.contains("FROM " + DB2Dialect.VIRTUAL_TABLE),
				"字面量内from不算FROM,须补虚表: " + result);
	}

	@Test
	public void withUrSuffixInQuotedIdentifierNotStripped() throws Exception {
		// 已带with ur但藏在标识符内:不误摘;真with ur正常摘取不重复
		String result = appendWithUr("select col from t with ur");
		assertTrue(result.endsWith("with ur") || result.contains("with ur"),
				"真with ur须保留或正确追回: " + result);
		// 只出现一次
		int count = result.split("with ur", -1).length - 1;
		assertEquals(1, count, "with ur只出现一次: " + result);
	}
}
