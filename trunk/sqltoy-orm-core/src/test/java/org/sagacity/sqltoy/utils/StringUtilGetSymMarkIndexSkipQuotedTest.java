package org.sagacity.sqltoy.utils;

import static org.junit.jupiter.api.Assertions.assertEquals;

import org.junit.jupiter.api.Test;

/**
 * StringUtil.getSymMarkIndexSkipQuoted 深度回归:
 * 1.标准SQL与MySQL(\'转义)两种引号语义;
 * 2.括号嵌套、字面量内括号、''成对转义、奇数/偶数反斜杠;
 * 3.短串全组合与独立oracle差分(含fromIndex);
 * 4.null、无开始标记、无结束标记、未闭合字面量等边界。
 */
public class StringUtilGetSymMarkIndexSkipQuotedTest {

	private static final char[] ALPHABET = { '(', ')', '\'', '\\', 'a' };

	/**
	 * 独立oracle:不复用被测方法的任何逻辑,按"是否在字面量内"的显式状态机实现。
	 */
	private static int oracle(String source, int fromIndex, boolean backslashEscape) {
		if (source == null) {
			return -1;
		}
		char[] chars = source.toCharArray();
		boolean inString = false;
		int depth = -1;
		int i = Math.max(fromIndex, 0);
		while (i < chars.length) {
			char c = chars[i];
			if (inString) {
				if (c == '\'') {
					if (backslashEscape && escapedByOddBackslash(chars, i)) {
						i++;
						continue;
					}
					if (i + 1 < chars.length && chars[i + 1] == '\'') {
						i += 2;
						continue;
					}
					inString = false;
				}
				i++;
				continue;
			}
			if (c == '\'') {
				inString = true;
				i++;
				continue;
			}
			if (c == '(') {
				depth = (depth == -1) ? 1 : depth + 1;
			} else if (c == ')') {
				if (depth > 0) {
					depth--;
					if (depth == 0) {
						return i;
					}
				}
			}
			i++;
		}
		return -1;
	}

	private static boolean escapedByOddBackslash(char[] chars, int index) {
		int count = 0;
		for (int i = index - 1; i >= 0 && chars[i] == '\\'; i--) {
			count++;
		}
		return (count & 1) == 1;
	}

	@Test
	public void exhaustiveShortStringsMatchOracle() {
		int maxLen = 8;
		for (int len = 1; len <= maxLen; len++) {
			int total = pow(ALPHABET.length, len);
			for (int code = 0; code < total; code++) {
				String source = buildString(code, len);
				for (boolean backslashEscape : new boolean[] { false, true }) {
					int expected = oracle(source, 0, backslashEscape);
					int actual = StringUtil.getSymMarkIndexSkipQuoted("(", ")", source, 0, backslashEscape);
					assertEquals(expected, actual,
							"source=[" + source + "], backslashEscape=" + backslashEscape);
				}
			}
		}
	}

	@Test
	public void exhaustiveFromIndexMatchOracle() {
		int maxLen = 5;
		for (int len = 1; len <= maxLen; len++) {
			int total = pow(ALPHABET.length, len);
			for (int code = 0; code < total; code++) {
				String source = buildString(code, len);
				for (int fromIndex = 0; fromIndex <= len; fromIndex++) {
					for (boolean backslashEscape : new boolean[] { false, true }) {
						int expected = oracle(source, fromIndex, backslashEscape);
						int actual = StringUtil.getSymMarkIndexSkipQuoted("(", ")", source, fromIndex,
								backslashEscape);
						assertEquals(expected, actual,
								"source=[" + source + "], fromIndex=" + fromIndex + ", backslashEscape="
										+ backslashEscape);
					}
				}
			}
		}
	}

	@Test
	public void randomLongStringsMatchOracle() {
		java.util.Random random = new java.util.Random(20261114L);
		for (int round = 0; round < 10000; round++) {
			int len = 1 + random.nextInt(120);
			StringBuilder sb = new StringBuilder(len);
			for (int i = 0; i < len; i++) {
				sb.append(ALPHABET[random.nextInt(ALPHABET.length)]);
			}
			String source = sb.toString();
			for (boolean backslashEscape : new boolean[] { false, true }) {
				int expected = oracle(source, 0, backslashEscape);
				int actual = StringUtil.getSymMarkIndexSkipQuoted("(", ")", source, 0, backslashEscape);
				assertEquals(expected, actual,
						"round=" + round + ", source=[" + source + "], backslashEscape=" + backslashEscape);
			}
		}
	}

	@Test
	public void deepNestingDoesNotOverflow() {
		int depth = 20000;
		StringBuilder sb = new StringBuilder(depth * 2);
		for (int i = 0; i < depth; i++) {
			sb.append('(');
		}
		for (int i = 0; i < depth; i++) {
			sb.append(')');
		}
		assertEquals(depth * 2 - 1, StringUtil.getSymMarkIndexSkipQuoted("(", ")", sb.toString(), 0, false));
		assertEquals(depth * 2 - 1, StringUtil.getSymMarkIndexSkipQuoted("(", ")", sb.toString(), 0, true));
	}

	@Test
	public void fourParamOverloadKeepsStandardSemantics() {
		String sql = "('a\\'b)'";
		assertEquals(6, StringUtil.getSymMarkIndexSkipQuoted("(", ")", sql, 0));
		assertEquals(6, StringUtil.getSymMarkIndexSkipQuoted("(", ")", sql, 0, false));
	}

	@Test
	public void standardAndMysqlQuoteSemanticsDifferOnBackslashQuote() {
		// 标准SQL: 反斜杠不转义,第一个'就闭合字面量,末尾)是外层结束
		String sql = "('a\\'b)'";
		assertEquals(6, StringUtil.getSymMarkIndexSkipQuoted("(", ")", sql, 0, false));
		// MySQL: \'是字面量内的转义引号,末尾)仍在字面量内,没有外层结束
		assertEquals(-1, StringUtil.getSymMarkIndexSkipQuoted("(", ")", sql, 0, true));
	}

	@Test
	public void evenBackslashesDoNotEscapeQuote() {
		// 两个反斜杠为偶数:MySQL下引号正常闭合
		String sql = "('a\\\\'b)'";
		assertEquals(7, StringUtil.getSymMarkIndexSkipQuoted("(", ")", sql, 0, true));
		assertEquals(7, StringUtil.getSymMarkIndexSkipQuoted("(", ")", sql, 0, false));
	}

	@Test
	public void doubledQuoteIsLiteralInBothModes() {
		String sql = "('a''b)')";
		assertEquals(8, StringUtil.getSymMarkIndexSkipQuoted("(", ")", sql, 0, false));
		assertEquals(8, StringUtil.getSymMarkIndexSkipQuoted("(", ")", sql, 0, true));
	}

	@Test
	public void nestedBracketsAndBracketLiterals() {
		assertEquals(6, StringUtil.getSymMarkIndexSkipQuoted("(", ")", "(a(b)c)", 0, false));
		assertEquals(4, StringUtil.getSymMarkIndexSkipQuoted("(", ")", "(')')", 0, true));
		assertEquals(6, StringUtil.getSymMarkIndexSkipQuoted("[", "]", "[a[b]c]", 0, false));
		assertEquals(6, StringUtil.getSymMarkIndexSkipQuoted("{", "}", "{a{b}c}", 0, true));
	}

	@Test
	public void fromIndexStartsScanAtGivenPosition() {
		assertEquals(3, StringUtil.getSymMarkIndexSkipQuoted("(", ")", "x(a)y", 1, false));
		assertEquals(3, StringUtil.getSymMarkIndexSkipQuoted("(", ")", "x(a)y", 0, false));
		assertEquals(-1, StringUtil.getSymMarkIndexSkipQuoted("(", ")", "x(a)y", 2, false));
		assertEquals(-1, StringUtil.getSymMarkIndexSkipQuoted("(", ")", "x(a)y", 5, false));
		assertEquals(3, StringUtil.getSymMarkIndexSkipQuoted("(", ")", "x(a)y", -10, false));
	}

	@Test
	public void unmatchedOrNullReturnsMinusOne() {
		assertEquals(-1, StringUtil.getSymMarkIndexSkipQuoted("(", ")", null, 0, false));
		assertEquals(-1, StringUtil.getSymMarkIndexSkipQuoted("(", ")", "", 0, false));
		assertEquals(-1, StringUtil.getSymMarkIndexSkipQuoted("(", ")", "abc", 0, false));
		assertEquals(-1, StringUtil.getSymMarkIndexSkipQuoted("(", ")", "(abc", 0, false));
		assertEquals(-1, StringUtil.getSymMarkIndexSkipQuoted("(", ")", "abc)", 0, false));
		// 字面量未闭合:字面量内的)全部跳过
		assertEquals(-1, StringUtil.getSymMarkIndexSkipQuoted("(", ")", "(a'b)c)", 0, false));
		assertEquals(-1, StringUtil.getSymMarkIndexSkipQuoted("(", ")", "(a'b)c)", 0, true));
	}

	private static int pow(int base, int exp) {
		int result = 1;
		for (int i = 0; i < exp; i++) {
			result *= base;
		}
		return result;
	}

	private static String buildString(int code, int len) {
		char[] chars = new char[len];
		int value = code;
		for (int i = 0; i < len; i++) {
			chars[i] = ALPHABET[value % ALPHABET.length];
			value /= ALPHABET.length;
		}
		return new String(chars);
	}
}
