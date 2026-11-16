package org.sagacity.sqltoy.plugins.function;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.Arrays;
import java.util.Random;

import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.sagacity.sqltoy.SqlToyConstants;
import org.sagacity.sqltoy.utils.StringUtil;

/**
 * D3a回归:FunctionUtils函数参数切分按方言处理反斜杠,避免'C:\'这类标准SQL字面量
 * 被当成"转义引号"后把后续逗号错切;mysql系的\'仍按转义处理。仅验证FunctionUtils专用切分,
 * 不改变StringUtil.splitExcludeSymMark全局语义。
 */
public class FunctionUtilsSplitArgsTest {

	@BeforeAll
	public static void init() {
		FunctionUtils.setFunctionConverts(Arrays.asList("default"));
	}

	@Test
	public void standardSqlBackslashBeforeClosingQuote() {
		// SQL Server/PostgreSQL: 'C:\'合法,反斜杠不转义引号
		String[] args = FunctionUtils.splitFunctionArgs("'C:\\', 'a,b'", false);
		assertEquals(2, args.length);
		assertEquals("'C:\\'", args[0]);
		assertEquals(" 'a,b'", args[1]);
		// 两个反斜杠同样只能切出两段
		args = FunctionUtils.splitFunctionArgs("'C:\\\\', 'a,b'", false);
		assertEquals(2, args.length);
	}

	@Test
	public void standardSqlQuotesAndNesting() {
		assertEquals(2, FunctionUtils.splitFunctionArgs("'', 'a,b'", false).length);
		assertEquals(2, FunctionUtils.splitFunctionArgs("'a''b', 'c,d'", false).length);
		assertEquals(2, FunctionUtils.splitFunctionArgs("\"a,b\", 'c,d'", false).length);
		assertEquals(2, FunctionUtils.splitFunctionArgs("nvl(a,b), 'c,d'", false).length);
		assertEquals(2, FunctionUtils.splitFunctionArgs("[a,b], 'c,d'", false).length);
		assertEquals(2, FunctionUtils.splitFunctionArgs("{a,b}, 'c,d'", false).length);
		assertEquals(3, FunctionUtils.splitFunctionArgs("a, b, c", false).length);
	}

	@Test
	public void mysqlBackslashEscapesQuote() {
		// MySQL: 'it\'s' 内部的\'是转义,逗号在字面量外才切
		String[] args = FunctionUtils.splitFunctionArgs("'it\\'s, ok', 'a,b'", true);
		assertEquals(2, args.length);
		assertEquals("'it\\'s, ok'", args[0]);
		assertEquals(" 'a,b'", args[1]);
		// 偶数个反斜杠后的引号是真实收尾
		args = FunctionUtils.splitFunctionArgs("'C:\\\\', 'a,b'", true);
		assertEquals(2, args.length);
		// ''成对转义仍生效
		assertEquals(2, FunctionUtils.splitFunctionArgs("'a''b', 'c,d'", true).length);
	}

	@Test
	public void nullAndEmpty() {
		assertNull(FunctionUtils.splitFunctionArgs(null, false));
		assertArrayEquals(new String[] { "" }, FunctionUtils.splitFunctionArgs("", false));
	}

	@Test
	public void dialectSqlDoesNotMisSplitStandardBackslash() {
		String out = FunctionUtils.getDialectSql("select concat('C:\\', 'a,b') from t", "sqlserver");
		assertTrue(out.contains("'a,b'"), "argument containing comma should stay intact, got: " + out);
		String postgres = FunctionUtils.getDialectSql("select concat('C:\\', 'a,b') from t", "postgresql");
		assertTrue(postgres.contains("'a,b'"), "argument containing comma should stay intact, got: " + postgres);
		String mysql = FunctionUtils.getDialectSql("select concat('it\\'s', 'a,b') from t", "mysql");
		assertTrue(mysql.contains("it\\'s"), "mysql escaped quote should stay intact, got: " + mysql);
		assertTrue(mysql.contains("'a,b'"), "argument containing comma should stay intact, got: " + mysql);
		// 嵌套函数:外层nvl的参数切分不能被内层concat的字面量逗号干扰
		String nested = FunctionUtils.getDialectSql("select nvl(concat('C:\\', 'a,b'), 'x,y') from t", "sqlserver");
		assertTrue(nested.contains("'a,b'"), "nested argument containing comma should stay intact, got: " + nested);
		assertTrue(nested.contains("'x,y'"), "outer argument containing comma should stay intact, got: " + nested);
	}

	private static final String[] STANDARD_TOKENS = { "a", "b", "'x'", "'a,b'", "'C:\\'", "'a''b'", "''",
			"nvl(a,b)", "[a,b]", "{a,b}", "\"a,b\"", " 'x' " };

	private static final String[] MYSQL_TOKENS = { "a", "b", "'it\\'s'", "'a,b'", "'C:\\\\'", "'a''b'", "''",
			"nvl(a,b)", "[a,b]", "{a,b}", "\"a,b\"", " 'x' " };

	@Test
	public void tokensMatrix() {
		for (String first : STANDARD_TOKENS) {
			for (String second : STANDARD_TOKENS) {
				String[] actual = FunctionUtils.splitFunctionArgs(first + "," + second, false);
				assertArrayEquals(new String[] { first, second }, actual, "standard: " + first + "," + second);
			}
		}
		for (String first : MYSQL_TOKENS) {
			for (String second : MYSQL_TOKENS) {
				String[] actual = FunctionUtils.splitFunctionArgs(first + "," + second, true);
				assertArrayEquals(new String[] { first, second }, actual, "mysql: " + first + "," + second);
			}
		}
	}

	@Test
	public void randomizedTokens() {
		Random random = new Random(20261116L);
		for (int round = 0; round < 5000; round++) {
			boolean mysql = (round & 1) == 0;
			String[] tokens = mysql ? MYSQL_TOKENS : STANDARD_TOKENS;
			int count = 1 + random.nextInt(8);
			String[] expected = new String[count];
			StringBuilder source = new StringBuilder();
			for (int i = 0; i < count; i++) {
				expected[i] = tokens[random.nextInt(tokens.length)];
				if (i > 0) {
					source.append(',');
				}
				source.append(expected[i]);
			}
			String[] actual = FunctionUtils.splitFunctionArgs(source.toString(), mysql);
			assertArrayEquals(expected, actual, "dialect=" + (mysql ? "mysql" : "standard") + " sql=" + source);
		}
	}

	@Test
	public void largeArgumentList() {
		StringBuilder source = new StringBuilder();
		int count = 2000;
		for (int i = 0; i < count; i++) {
			if (i > 0) {
				source.append(',');
			}
			source.append('a').append(i);
		}
		String[] actual = FunctionUtils.splitFunctionArgs(source.toString(), false);
		assertEquals(count, actual.length);
		assertEquals("a0", actual[0]);
		assertEquals("a1999", actual[count - 1]);
	}

	@Test
	public void oldSplitBehaviorUnchangedForNormalSql() {
		String[] corpus = { "a,b", "a,b,c", "nvl(a,b),c", "'x,y',z", "\"x,y\",z", "[a,b],c", "{a,b},c",
				"'a''b',c", "'' ,c", "a,,b", ",a" };
		for (String args : corpus) {
			String[] oldResult = StringUtil.splitExcludeSymMark(args, ",", SqlToyConstants.filters);
			String[] newResult = FunctionUtils.splitFunctionArgs(args, false);
			assertArrayEquals(oldResult, newResult, "old split behavior changed for: " + args);
		}
	}

	@Test
	public void dialectAwareSkipQuoted() {
		String standard = "f('C:\\', x)";
		assertEquals(standard.indexOf(')'),
				StringUtil.getSymMarkIndexSkipQuoted("(", ")", standard, 1, false));
		// 4参保持标准语义
		assertEquals(standard.indexOf(')'), StringUtil.getSymMarkIndexSkipQuoted("(", ")", standard, 1));
		String mysql = "f('it\\'s', x)";
		assertEquals(mysql.indexOf(')'), StringUtil.getSymMarkIndexSkipQuoted("(", ")", mysql, 1, true));
	}
}
