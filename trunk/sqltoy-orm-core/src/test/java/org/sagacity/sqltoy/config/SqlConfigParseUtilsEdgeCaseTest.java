package org.sagacity.sqltoy.config;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.lang.reflect.Method;
import java.util.Arrays;
import java.util.List;

import org.junit.jupiter.api.Test;
import org.sagacity.sqltoy.config.model.SqlToyResult;
import org.sagacity.sqltoy.utils.StringUtil;

/**
 * SqlConfigParseUtils 的边缘场景验证:空sql/空片段/无分隔相邻片段/字面量问号/注释/CRLF/大小写/
 * union多where/多占位符片段/in单占位符/JSON双问号/超长与Unicode参数/非法片段/方言为null,
 * 以及优化后几个private辅助函数的边界输入。
 */
public class SqlConfigParseUtilsEdgeCaseTest {

	private static final Method COUNT_ARG_MARKS;
	private static final Method MATCH_INDEX_AT_END;
	private static final Method HAS_DANGLING;
	private static final Method MATCHES_AT_START;

	static {
		try {
			COUNT_ARG_MARKS = declared("countArgMarks", String.class);
			MATCH_INDEX_AT_END = declared("matchIndexAtEnd", String.class, java.util.regex.Pattern.class, char.class);
			HAS_DANGLING = declared("hasDanglingConnector", String.class);
			MATCHES_AT_START = declared("matchesAtStart", String.class, java.util.regex.Pattern.class);
		} catch (Exception e) {
			throw new IllegalStateException(e);
		}
	}

	private static Method declared(String name, Class<?>... types) throws Exception {
		Method method = SqlConfigParseUtils.class.getDeclaredMethod(name, types);
		method.setAccessible(true);
		return method;
	}

	// ============ 空输入与短路 ============

	@Test
	public void emptyAndBlankSqlAreReturnedAsIs() throws Exception {
		assertEquals("", SqlConfigParseUtils.processSql("", null, new Object[0], "mysql").getSql());
		assertEquals("   ", SqlConfigParseUtils.processSql("   ", null, new Object[0], "mysql").getSql());
	}

	@Test
	public void zeroParamsShortCircuitsWithoutProcessingMarkers() throws Exception {
		// 既有设计:无参数时直接返回原sql,不解析#[]
		String sql = "select * from t where 1=1 #[and a=?]";
		SqlToyResult result = SqlConfigParseUtils.processSql(sql, null, new Object[0], "mysql");
		assertEquals(sql, result.getSql());
	}

	// ============ 片段形态 ============

	@Test
	public void emptyFragmentIsRemovedWhenOtherParamsExist() throws Exception {
		SqlToyResult result = SqlConfigParseUtils.processSql("select * from t where 1=1 #[] #[and a=?]", null,
				new Object[] { "A" }, "mysql");
		assertInvariants(result);
		assertFalse(result.getSql().contains("#["), result.getSql());
		assertTrue(result.getSql().contains("a=?"), result.getSql());
	}

	@Test
	public void adjacentFragmentsWithoutSeparatorStayAligned() throws Exception {
		SqlToyResult result = SqlConfigParseUtils.processSql("select * from t where 1=1#[and a=?]#[and b=?]", null,
				new Object[] { "A", null }, "mysql");
		assertInvariants(result);
		assertTrue(result.getSql().contains("a=?"), result.getSql());
		assertFalse(result.getSql().contains("b=?"), result.getSql());
		assertEquals(1, result.getParamsValue().length);
		assertEquals("A", result.getParamsValue()[0]);
	}

	@Test
	public void fragmentWithTwoPlaceholdersDroppedWhenAnyValueNull() throws Exception {
		SqlToyResult result = SqlConfigParseUtils.processSql("select * from t where 1=1 #[and a=? and b=?]", null,
				new Object[] { "A", null }, "mysql");
		assertInvariants(result);
		assertFalse(result.getSql().contains("a=?"), result.getSql());
		assertEquals(0, result.getParamsValue() == null ? 0 : result.getParamsValue().length);

		SqlToyResult kept = SqlConfigParseUtils.processSql("select * from t where 1=1 #[and a=? and b=?]", null,
				new Object[] { "A", "B" }, "mysql");
		assertInvariants(kept);
		assertTrue(kept.getSql().contains("a=? and b=?"), kept.getSql());
		assertEquals(2, kept.getParamsValue().length);
	}

	// ============ 字面量 / 注释 / 换行 / 大小写 ============

	@Test
	public void questionMarkInsideLiteralIsNotTreatedAsParam() throws Exception {
		SqlToyResult result = SqlConfigParseUtils.processSql("select * from t where c='?' and a=? #[and b=?]", null,
				new Object[] { "A", "B" }, "mysql");
		assertTrue(result.getSql().contains("c='?'"), result.getSql());
		assertTrue(result.getSql().contains("a=?"), result.getSql());
		assertTrue(result.getSql().contains("b=?"), result.getSql());
		assertEquals(2, result.getParamsValue().length);
		assertEquals("A", result.getParamsValue()[0]);
		assertEquals("B", result.getParamsValue()[1]);
	}

	@Test
	public void sqlCommentsArePreservedAndDoNotBreakAlignment() throws Exception {
		SqlToyResult result = SqlConfigParseUtils.processSql(
				"select * from t where 1=1 -- where 1=1\n and a=? #[and b=?]", null, new Object[] { "A", null },
				"mysql");
		assertInvariants(result);
		assertTrue(result.getSql().contains("-- where 1=1"), result.getSql());
		assertEquals(1, result.getParamsValue().length);
	}

	@Test
	public void crlfLineEndingsAreHandled() throws Exception {
		SqlToyResult result = SqlConfigParseUtils.processSql("select * from t where\r\n1\r\n=\r\n1\r\n#[and a=?]",
				null, new Object[] { "A" }, "mysql");
		assertInvariants(result);
		assertFalse(result.getSql().contains("1\r\n=\r\n1"), result.getSql());
		assertTrue(result.getSql().contains("a=?"), result.getSql());
	}

	@Test
	public void mixedCaseKeywordsAreHandled() throws Exception {
		SqlToyResult result = SqlConfigParseUtils.processSql("select * from t WHERE 1=1 #[AND a=?]", null,
				new Object[] { "A" }, "mysql");
		assertInvariants(result);
		assertFalse(result.getSql().contains("1=1"), result.getSql());
		assertTrue(result.getSql().contains("a=?"), result.getSql());
	}

	// ============ 多where / in / 双问号 ============

	@Test
	public void unionWithMultipleWhereKeepsOnlyRelevantOne() throws Exception {
		SqlToyResult result = SqlConfigParseUtils.processSql(
				"select * from t where 1=1 union all select * from b where 1=1 #[and a=?]", null, new Object[] { "A" },
				"mysql");
		assertInvariants(result);
		assertEquals(1, countOccurrences(result.getSql(), "1=1"), "只应剔除第二个where 1=1:" + result.getSql());
		assertTrue(result.getSql().contains("a=?"), result.getSql());
	}

	@Test
	public void singleInPlaceholderExpandsArrayParam() throws Exception {
		SqlToyResult result = SqlConfigParseUtils.processSql("select * from t where a in (?)", null,
				new Object[] { Arrays.asList("1", "2") }, "mysql");
		assertEquals("select * from t where a in (?,?) ", result.getSql().replace("\n", " ").trim() + " ");
		assertEquals(2, result.getParamsValue().length);
		assertEquals("1", result.getParamsValue()[0]);
		assertEquals("2", result.getParamsValue()[1]);
	}

	@Test
	public void jsonDoubleQuestionMarkOperatorIsPreserved() throws Exception {
		SqlToyResult result = SqlConfigParseUtils.processSql(
				"select * from t where x??'$.a' and a=? #[and b=?]", null, new Object[] { "A", "B" }, "mysql");
		assertTrue(result.getSql().contains("x??'$.a'"), result.getSql());
		assertEquals(2, result.getParamsValue().length);
	}

	// ============ 参数值极端形态 ============

	@Test
	public void longAndUnicodeParamValuesArePreserved() throws Exception {
		String longValue = repeat('X', 5000);
		String unicode = "中文😀值";
		SqlToyResult result = SqlConfigParseUtils.processSql("select * from t where a=? #[and b=?]", null,
				new Object[] { longValue, unicode }, "mysql");
		assertInvariants(result);
		assertEquals(2, result.getParamsValue().length);
		assertEquals(longValue, result.getParamsValue()[0]);
		assertEquals(unicode, result.getParamsValue()[1]);
	}

	// ============ 非法输入 / 方言 ============

	@Test
	public void unbalancedFragmentThrowsIllegalFormat() {
		assertThrows(java.util.IllegalFormatFlagsException.class, () -> SqlConfigParseUtils
				.processSql("#[and a=?", null, new Object[] { "A" }, "mysql"));
	}

	@Test
	public void nullDialectParsesSameAsMysql() throws Exception {
		String sql = "select * from t where 1=1 #[and a=?]";
		SqlToyResult withNull = SqlConfigParseUtils.processSql(sql, null, new Object[] { "A" }, null);
		SqlToyResult withMysql = SqlConfigParseUtils.processSql(sql, null, new Object[] { "A" }, "mysql");
		assertEquals(withMysql.getSql(), withNull.getSql());
		assertEquals(withMysql.getParamsValue().length, withNull.getParamsValue().length);
	}

	// ============ private 辅助函数边界 ============

	@Test
	public void countArgMarksBoundaries() throws Exception {
		assertEquals(0, ((Integer) COUNT_ARG_MARKS.invoke(null, (Object) null)).intValue());
		assertEquals(0, ((Integer) COUNT_ARG_MARKS.invoke(null, "")).intValue());
		assertEquals(1, ((Integer) COUNT_ARG_MARKS.invoke(null, "?")).intValue());
		assertEquals(2, ((Integer) COUNT_ARG_MARKS.invoke(null, "??")).intValue());
		assertEquals(1, ((Integer) COUNT_ARG_MARKS.invoke(null, "中文?😀")).intValue());
	}

	@Test
	public void endAnchoredMatchBoundaries() throws Exception {
		// 缺少\W前导(行首)时不应命中
		assertEquals(-1, invokeMatchEnd("where", SqlConfigParseUtils.WHERE_END_PATTERN, 'e'));
		assertTrue(invokeMatchEnd(" where", SqlConfigParseUtils.WHERE_END_PATTERN, 'e') >= 0);
		assertEquals(-1, invokeMatchEnd("xwhere 1=1", SqlConfigParseUtils.WHERE_ONE_EQUAL_PATTERN, '1'));
		assertEquals(-1, invokeMatchEnd("where 1=1x", SqlConfigParseUtils.WHERE_ONE_EQUAL_PATTERN, '1'));
		// 行首缺少\W前导,不命中;有前导空白则命中
		assertEquals(-1, invokeMatchEnd("WHERE 1 = 1   ", SqlConfigParseUtils.WHERE_ONE_EQUAL_PATTERN, '1'));
		assertTrue(invokeMatchEnd(" WHERE 1 = 1   ", SqlConfigParseUtils.WHERE_ONE_EQUAL_PATTERN, '1') >= 0);
		assertTrue(invokeMatchEnd("update t SET  ", SqlConfigParseUtils.UPDATE_SET_PATTERN, 't') >= 0);
		assertEquals(-1, invokeMatchEnd("update t set x=1", SqlConfigParseUtils.UPDATE_SET_PATTERN, 't'));
	}

	private static int invokeMatchEnd(String sql, java.util.regex.Pattern pattern, char lastChar) throws Exception {
		return (Integer) MATCH_INDEX_AT_END.invoke(null, sql, pattern, lastChar);
	}

	@Test
	public void danglingDetectorBoundaries() throws Exception {
		assertTrue((Boolean) HAS_DANGLING.invoke(null, "(and)"));
		assertTrue((Boolean) HAS_DANGLING.invoke(null, "( and )"));
		assertTrue((Boolean) HAS_DANGLING.invoke(null, "(OR)"));
		assertFalse((Boolean) HAS_DANGLING.invoke(null, ")and("));
		assertFalse((Boolean) HAS_DANGLING.invoke(null, "(order by)"));
		assertFalse((Boolean) HAS_DANGLING.invoke(null, ""));
	}

	@Test
	public void startAnchoredMatchBoundaries() throws Exception {
		assertTrue((Boolean) MATCHES_AT_START.invoke(null, "and x=1", SqlConfigParseUtils.AND_START_PATTERN));
		assertFalse((Boolean) MATCHES_AT_START.invoke(null, "\nand x=1", SqlConfigParseUtils.AND_START_PATTERN),
				"未开启MULTILINE,^不应匹配第二行");
		assertFalse((Boolean) MATCHES_AT_START.invoke(null, "xand", SqlConfigParseUtils.AND_START_PATTERN));
		// 调用方均先trim再匹配,故带前导空白的文本不在起始匹配范围内
		assertFalse((Boolean) MATCHES_AT_START.invoke(null, " ORDER BY id",
				java.util.regex.Pattern.compile("(?i)^((order|group)\\s+by|(inner|left|right|full)\\s+join|having|union|limit)\\W")));
		assertTrue((Boolean) MATCHES_AT_START.invoke(null, "ORDER BY id",
				java.util.regex.Pattern.compile("(?i)^((order|group)\\s+by|(inner|left|right|full)\\s+join|having|union|limit)\\W")));
	}

	// ============ 通用后置条件 ============

	private static void assertInvariants(SqlToyResult result) {
		String sql = result.getSql();
		assertFalse(sql.contains("#["), "残留动态标记:" + sql);
		int argCount = StringUtil.matchCnt(sql, SqlConfigParseUtils.ARG_NAME_PATTERN, 0);
		int paramCount = result.getParamsValue() == null ? 0 : result.getParamsValue().length;
		assertEquals(paramCount, argCount, "占位符与参数数量不一致:" + sql);
	}

	private static int countOccurrences(String text, String token) {
		int count = 0;
		int index = 0;
		while ((index = text.indexOf(token, index)) >= 0) {
			count++;
			index += token.length();
		}
		return count;
	}

	private static String repeat(char c, int count) {
		StringBuilder builder = new StringBuilder(count);
		for (int i = 0; i < count; i++) {
			builder.append(c);
		}
		return builder.toString();
	}
}
