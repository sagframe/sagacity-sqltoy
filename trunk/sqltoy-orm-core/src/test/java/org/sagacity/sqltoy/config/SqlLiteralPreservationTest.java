package org.sagacity.sqltoy.config;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.ResultSet;
import java.sql.Statement;

import org.junit.jupiter.api.Test;
import org.sagacity.sqltoy.config.model.SqlToyConfig;
import org.sagacity.sqltoy.config.model.SqlType;
import org.sagacity.sqltoy.utils.SqlUtil;

/**
 * @description 配置解析链路字面量保真回归:clearMark/clearMistyChars 及 parseSqlToyConfig
 *              不得把字符串字面量中的注释标记当注释删除,也不得压缩字面量内的换行/Tab。
 * @author zhongxuchen
 */
public class SqlLiteralPreservationTest {

	private static String parse(String sql, String dialect) {
		SqlToyConfig config = SqlConfigParseUtils.parseSqlToyConfig(sql, dialect, SqlType.search);
		return config.getSql(null);
	}

	@Test
	public void blockCommentMarkerInsideLiteralPreserved() {
		String sql = "select '/* not comment */' as x from t";
		assertEquals(sql, SqlUtil.clearMark(sql));
		assertTrue(parse(sql, "mysql").contains("'/* not comment */'"), "parseSqlToyConfig 不得删除字面量:");
		assertTrue(SqlUtil.clearMark("select 'a/*b*/c' as x from t").contains("'a/*b*/c'"));
	}

	@Test
	public void htmlCommentMarkerInsideLiteralPreserved() {
		String sql = "select '<!-- not comment -->' as x from t";
		assertEquals(sql, SqlUtil.clearMark(sql));
		assertTrue(parse(sql, "mysql").contains("'<!-- not comment -->'"));
	}

	@Test
	public void doubledQuoteAndCommentMarkerPreserved() {
		String sql = "select 'it''s /* ok */' as x from t";
		assertTrue(SqlUtil.clearMark(sql).contains("'it''s /* ok */'"));
		assertTrue(parse(sql, "mysql").contains("'it''s /* ok */'"));
	}

	@Test
	public void lineCommentMarkerInsideLiteralPreserved() {
		String sql = "select '-- not comment' as x from t";
		assertTrue(SqlUtil.clearMark(sql).contains("'-- not comment'"));
		assertTrue(parse(sql, "mysql").contains("'-- not comment'"));
	}

	@Test
	public void multilineLiteralPreserved() {
		String sql = "select 'line1\n  line2' as x from t";
		assertTrue(SqlUtil.clearMark(sql).contains("'line1\n  line2'"));
		assertTrue(parse(sql, "mysql").contains("'line1\n  line2'"), "parseSqlToyConfig 不得压缩字面量内空白");
	}

	@Test
	public void lineStartingWithDashDashInsideLiteralPreserved() {
		String sql = "select 'line1\n-- literal line\nline2' as x from t";
		assertTrue(SqlUtil.clearMark(sql).contains("'line1\n-- literal line\nline2'"));
		assertTrue(parse(sql, "mysql").contains("'line1\n-- literal line\nline2'"));
	}

	@Test
	public void tabInsideLiteralPreserved() {
		String sql = "select 'a\tb' as x from t";
		assertTrue(parse(sql, "mysql").contains("'a\tb'"));
	}

	@Test
	public void mysqlBackslashEscapedQuoteKeepsLiteral() {
		String sql = "select 'a\\'b /* c */' as x from t";
		String cleared = SqlUtil.clearMark(sql, true);
		assertTrue(cleared.contains("'a\\'b /* c */'"), "mysql反斜杠转义时字面量应完整保留: " + cleared);
		assertTrue(parse(sql, "mysql").contains("'a\\'b /* c */'"));
	}

	@Test
	public void postgresDollarQuotedStringPreserved() {
		String sql = "select $$/* not comment */$$ as x, $tag_1$line1\n  line2$tag_1$ as y from t";
		String cleared = SqlUtil.clearMark(sql);
		assertTrue(cleared.contains("$$/* not comment */$$"), "美元引用内容不应被当注释: " + cleared);
		assertTrue(cleared.contains("$tag_1$line1\n  line2$tag_1$"));
		assertTrue(parse(sql, "postgresql").contains("$$/* not comment */$$"));
		assertTrue(parse(sql, "postgresql").contains("$tag_1$line1\n  line2$tag_1$"));
		// 普通$1参数不能被误判为美元引用
		assertEquals("select $1", SqlUtil.clearMark("select $1"));
	}

	@Test
	public void postgresEscapeStringPreserved() {
		String sql = "select E'a\\'b /* not comment */' as x from t";
		assertTrue(SqlUtil.clearMark(sql).contains("E'a\\'b /* not comment */'"));
		assertTrue(parse(sql, "postgresql").contains("E'a\\'b /* not comment */'"));
	}

	@Test
	public void oracleQQuotePreserved() {
		String sql = "select q'[it's /* not comment */]' as x, q'{a--b}' as y, q'!a/*b*/!' as z from dual";
		String cleared = SqlUtil.clearMark(sql);
		assertTrue(cleared.contains("q'[it's /* not comment */]'"), "q-quote内嵌单引号不应提前闭合: " + cleared);
		assertTrue(cleared.contains("q'{a--b}'"));
		assertTrue(cleared.contains("q'!a/*b*/!'"));
		assertTrue(parse(sql, "oracle").contains("q'[it's /* not comment */]'"));
	}

	@Test
	public void mistyCharsPreservesSpecialLiteralForms() {
		assertTrue(SqlUtil.clearMistyChars("$tag$a\n  b$tag$", " ").contains("$tag$a\n  b$tag$"));
		assertTrue(SqlUtil.clearMistyChars("q'[a\n b]'", " ").contains("q'[a\n b]'"));
		assertTrue(SqlUtil.clearMistyChars("E'a\\'b\n c'", " ").contains("E'a\\'b\n c'"));
	}

	@Test
	public void multipleCommentsAndLiterals() {
		String sql = "select '/*a*/' as x /* real */, '--b' as y -- real\nfrom t";
		String cleared = SqlUtil.clearMark(sql);
		assertTrue(cleared.contains("'/*a*/'"));
		assertTrue(cleared.contains("'--b'"));
		assertFalse(cleared.contains("real"));
		String normalized = SqlUtil.clearMistyChars(cleared, " ");
		assertTrue(normalized.contains("'/*a*/' as x"));
		assertTrue(normalized.contains("'--b' as y"));
		assertTrue(normalized.endsWith("from t"));
	}

	@Test
	public void commentsAdjacentToTokens() {
		assertEquals("select a b from t", SqlUtil.clearMistyChars(SqlUtil.clearMark("select a/*c*/b from t"), " "));
		assertEquals("select a from t", SqlUtil.clearMistyChars(SqlUtil.clearMark("select a--c\nfrom t"), " "));
		assertEquals("select 1 +2", SqlUtil.clearMistyChars(SqlUtil.clearMark("select 1<!--c-->+2"), " "));
	}

	@Test
	public void crlfAndCrLineComments() {
		assertEquals("select a from t",
				SqlUtil.clearMistyChars(SqlUtil.clearMark("select a -- c\r\nfrom t"), " "));
		assertEquals("select a from t", SqlUtil.clearMistyChars(SqlUtil.clearMark("select a -- c\rfrom t"), " "));
	}

	@Test
	public void escapedIdentifierQuotes() {
		assertEquals("select \"a\"\"/*b*/\" from t", SqlUtil.clearMark("select \"a\"\"/*b*/\" from t"));
		assertEquals("select `a``/*b*/` from t", SqlUtil.clearMark("select `a``/*b*/` from t"));
		assertEquals("select [a]]/*b*/] from t", SqlUtil.clearMark("select [a]]/*b*/] from t"));
	}

	@Test
	public void qQuoteDelimiters() {
		String sql = "select q'<a/*b*/>' as a, q'(a--b)' as b, q'[a]b]' as c from dual";
		String cleared = SqlUtil.clearMark(sql);
		assertTrue(cleared.contains("q'<a/*b*/>'"));
		assertTrue(cleared.contains("q'(a--b)'"));
		assertTrue(cleared.contains("q'[a]b]'"));
	}

	@Test
	public void dollarQuoteEdges() {
		assertEquals("select $$$$", SqlUtil.clearMark("select $$$$"));
		String sql = "select $tag$con$tent$tag$, $$a/*b*/$$ from t";
		String cleared = SqlUtil.clearMark(sql);
		assertTrue(cleared.contains("$tag$con$tent$tag$"));
		assertTrue(cleared.contains("$$a/*b*/$$"));
		// 未闭合不是合法美元引用,原样保留
		assertEquals("select $tag$abc", SqlUtil.clearMark("select $tag$abc"));
	}

	@Test
	public void unclosedQuotesKept() {
		assertEquals("select 'abc", SqlUtil.clearMark("select 'abc"));
		assertEquals("select \"abc", SqlUtil.clearMark("select \"abc"));
		assertEquals("select [abc", SqlUtil.clearMark("select [abc"));
	}

	@Test
	public void trailingSeparatorsAfterComments() {
		assertEquals("select 1", SqlUtil.clearMark("select 1; -- c"));
		assertEquals("select 1", SqlUtil.clearMark("select 1, /* c */"));
	}

	@Test
	public void mistyCharsMatrix() {
		assertEquals("a,b", SqlUtil.clearMistyChars("a\r\nb", ","));
		assertEquals("a,b", SqlUtil.clearMistyChars("a\n\nb", ","));
		assertEquals("a,,b", SqlUtil.clearMistyChars("a\t\tb", ","));
		assertEquals("a b", SqlUtil.clearMistyChars("a\tb", " "));
		assertEquals("'a\r\nb'", SqlUtil.clearMistyChars("'a\r\nb'", " "));
		assertEquals("\"a\n b\"", SqlUtil.clearMistyChars("\"a\n b\"", " "));
	}

	@Test
	public void parseKeepsParamsAroundComments() {
		String parsed = parse("select * from t where a=:a /* c */ and b=:b", "mysql");
		assertTrue(parsed.contains(":a") && parsed.contains(":b"), "注释剔除不得影响参数: " + parsed);
		assertFalse(parsed.contains("/* c */"));
	}

	@Test
	public void executableCommentPreserved() {
		String sql = "select /*!40101 SET NAMES utf8 */ 1";
		assertTrue(SqlUtil.clearMark(sql).contains("/*!40101 SET NAMES utf8 */"));
	}

	@Test
	public void allLiteralFormsMatrix() {
		String[] literals = { "'/*c*/'", "'<!--c-->'", "'--c'", "'it''s /*c*/'", "\"/*c*/\"", "`/*c*/`",
				"[/*c*/]", "$$/*c*/$$", "$tag$/*c*/$tag$", "q'[/*c*/]'", "q'{--c}'", "q'</*c*/>'" };
		for (String literal : literals) {
			String sql = "select " + literal + " as x from t";
			assertEquals(sql, SqlUtil.clearMark(sql), "clearMark 破坏字面量: " + literal);
			assertTrue(SqlUtil.clearMistyChars(sql, " ").contains(literal), "clearMistyChars 破坏字面量: " + literal);
			assertTrue(parse(sql, "mysql").contains(literal), "parseSqlToyConfig 破坏字面量: " + literal);
			String cleared = SqlUtil.clearMark(sql);
			assertEquals(cleared, SqlUtil.clearMark(cleared), "clearMark 应幂等: " + literal);
			String misty = SqlUtil.clearMistyChars(sql, " ");
			assertEquals(misty, SqlUtil.clearMistyChars(misty, " "), "clearMistyChars 应幂等: " + literal);
		}
	}

	@Test
	public void realCommentsStillRemoved() {
		assertFalse(SqlUtil.clearMark("select 'a' /* real comment */ from t").contains("real comment"));
		assertFalse(SqlUtil.clearMark("select 'a' from t -- real comment").contains("real comment"));
		assertFalse(parse("select 'a' /* real comment */ from t", "mysql").contains("real comment"));
	}

	@Test
	public void apostropheInsideCommentDoesNotEatFollowingSql() {
		String sql = "select a -- don't touch\nfrom t";
		String normalized = SqlUtil.clearMistyChars(SqlUtil.clearMark(sql), " ");
		assertEquals("select a from t", normalized);
		assertFalse(normalized.contains("don't"));
	}

	@Test
	public void hintCommentStillPreserved() {
		String sql = "select /*+ index(t) */ '/* literal */' from t";
		String cleared = SqlUtil.clearMark(sql);
		assertTrue(cleared.contains("/*+ index(t) */"), "hint 应保留: " + cleared);
		assertTrue(cleared.contains("'/* literal */'"));
	}

	@Test
	public void quotedIdentifiersPreserveCommentMarkers() {
		assertEquals("select \"a/*b*/c\" from t", SqlUtil.clearMark("select \"a/*b*/c\" from t"));
		assertEquals("select `a/*b*/c` from t", SqlUtil.clearMark("select `a/*b*/c` from t"));
		assertEquals("select [a/*b*/c] from t", SqlUtil.clearMark("select [a/*b*/c] from t"));
	}

	@Test
	public void unclosedCommentsKeptForLoudFailure() {
		assertTrue(SqlUtil.clearMark("select 1 /* unclosed").contains("/* unclosed"));
		assertTrue(SqlUtil.clearMark("select 1 <!-- unclosed").contains("<!-- unclosed"));
	}

	@Test
	public void mistyCharsOutsideCollapsedInsidePreserved() {
		assertEquals("a b", SqlUtil.clearMistyChars("a \n b", " "));
		assertEquals("'a\n b'", SqlUtil.clearMistyChars("'a\n b'", " "));
		assertEquals("'a\tb'", SqlUtil.clearMistyChars("'a\tb'", " "));
	}

	@Test
	public void embeddedDatabasesReturnLiteralValues() throws Exception {
		String block = parse("select '/* not comment */' as x from (select 1) t", "h2");
		try (Connection conn = DriverManager.getConnection("jdbc:h2:mem:literal_preserve;DB_CLOSE_DELAY=-1", "sa", "");
				Statement st = conn.createStatement()) {
			assertEquals("/* not comment */", query(st, block));
			assertEquals("<!-- not comment -->",
					query(st, parse("select '<!-- not comment -->' as x from (select 1) t", "h2")));
			assertEquals("line1\n  line2",
					query(st, parse("select 'line1\n  line2' as x from (select 1) t", "h2")));
			assertEquals("-- not comment", query(st, parse("select '-- not comment' as x from (select 1) t", "h2")));
		}
		try (Connection conn = DriverManager.getConnection("jdbc:sqlite::memory:");
				Statement st = conn.createStatement()) {
			assertEquals("/* not comment */",
					query(st, parse("select '/* not comment */' as x from (select 1) t", "sqlite")));
			assertEquals("a\tb", query(st, parse("select 'a\tb' as x from (select 1) t", "sqlite")));
		}
	}

	private static String query(Statement st, String sql) throws Exception {
		try (ResultSet rs = st.executeQuery(sql)) {
			rs.next();
			return String.valueOf(rs.getObject(1));
		}
	}
}
