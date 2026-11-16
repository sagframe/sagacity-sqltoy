package org.sagacity.sqltoy.utils;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.junit.jupiter.api.Assertions.fail;

import java.util.ArrayList;
import java.util.List;
import java.util.Random;

import org.junit.jupiter.api.Test;
import org.sagacity.sqltoy.config.SqlConfigParseUtils;

/**
 * hasOrderBy深度回归:覆盖最外层/子查询/窗口函数/字面量/整串括号包裹/ORder标记/大小写与空白,
 * 并用旧实现做差分、再用独立逐字符参考实现交叉验证,确保本次括号深度修复只把该判true的场景
 * 从false纠正为true,不把任何旧true改成false。
 */
public class SqlUtilHasOrderByDeepTest {

	private static final String[] TOP_LEVEL_EXPRESSIONS = { "a", "a, b", "a desc nulls last", "nvl(a,0)",
			"coalesce(a, 0)", "upper(a)", "lower(a)", "substr(a, 1, 2)", "decode(a, 1, 'x', 0)",
			"to_char(a, 'YYYY')", "cast(a as number)", "round(a, 2)", "(a)", "((a))", "(a, b)", "\"a\"", "t.a",
			"schema.t.a", "1", ":sortCol", "?", "a collate binary",
			"(case when a>0 then 1 else 0 end)", "case when a>0 then nvl(b,0) else 0 end",
			"(select max(b) from t2)", "nvl((select max(b) from t2), 0)",
			"case when a>0 then (select max(b) from t2) else 0 end", "a + b", "a || b",
			// 最后一个order by候选位于窗口函数/标量子查询内部,更靠前的候选才是真正的最外层排序
			"a, row_number() over (order by b)", "a, (select max(b) from t2 order by 1)",
			"(select max(b) from t2), rank() over (order by a)", "row_number() over (order by b)",
			"row_number() over (order by b), a", "(row_number() over (order by b))" };

	private static final String[] FIXED_FALSE_TO_TRUE = { "select * from t order by nvl(a,0)",
			"select * from t order by (case when a>0 then 1 else 0 end)",
			"select * from t order by coalesce(a, 0), b", "select * from t order by upper(a)",
			"select * from t order by substr(a, 1, 2)", "select * from t order by decode(a, 1, 'x', 0)",
			"select * from t order by (a)", "select * from t order by (select max(b) from t2)",
			"select * from t order by nvl((select max(b) from t2), 0)",
			"select x.a from (select a from t) x order by nvl(x.a,0)",
			"with x as (select a from t) select * from x order by nvl(a,0)",
			"select a from t union all select a from t2 order by nvl(a,0)",
			// 最后一个候选在窗口函数/标量子查询内,但前面存在最外层order by
			"select * from t order by a, row_number() over (order by b)",
			"select * from t order by a, (select max(b) from t2 order by 1)",
			"select * from t order by (select max(b) from t2), rank() over (order by a)",
			"select * from t order by row_number() over (order by b)",
			"select * from t order by row_number() over (order by b), a",
			"select * from t order by (row_number() over (order by b))" };

	private static final String[] NESTED = { "select * from (select a from t order by a) x",
			"select * from (select a from t order by nvl(a,0)) x",
			"select * from ((select a from t order by nvl(a,0))) x",
			"select * from (select a from t order by nvl(a,0)) x where b=1",
			"with x as (select a from t order by a) select * from x",
			"with x as (select a from t order by nvl(a,0)) select * from x",
			"with x as (select a from t order by a), y as (select a from t2) select * from y",
			"select * from t where exists (select 1 from t2 order by a)",
			"select * from t where exists (select 1 from t2 order by nvl(a,0))",
			"select * from t where a in (select a from t2 order by a)",
			"select * from t where a in (select a from t2 order by nvl(a,0))",
			"select (select a from t2 order by a) from t",
			"select * from t where a=(select max(a) from t2 order by a)",
			"select row_number() over (order by a) rn from t",
			"select rank() over (partition by b order by a) rn from t",
			"select sum(a) over (order by b rows between unbounded preceding and current row) from t",
			"select row_number() over (order by nvl(a,0)) rn from t",
			"select * from (select a from t order by a, row_number() over (order by b)) x" };

	private static final String[] LITERAL_ONLY = { "select * from t where remark=' order by a'",
			"select * from t where remark=')'", "select * from t where remark='('",
			"select * from t where remark='it''s order by a'",
			"select * from t where remark='order by nvl(a,0)'",
			"select * from t where remark='(' and b=')'" };

	private static final String[] WRAPPED = { "(select a from t order by a)",
			"(select a from t order by nvl(a,0))", "((select a from t order by nvl(a,0)))",
			" ( select a from t order by nvl(a,0) ) " };

	private static final String[] MATRIX_EXPRESSIONS = { "a", "nvl(a,0)", "coalesce(a, 0)", "upper(a)", "(a)",
			"(case when a>0 then 1 else 0 end)", "(select max(b) from t2)", "nvl((select max(b) from t2), 0)",
			"substr(a, 1, 2)" };

	private static final String[] ORDER_KEYWORDS = { "order by", "ORDER BY", "Order By", "order\nby" };

	@Test
	public void topLevelOrderBy() {
		for (String expression : TOP_LEVEL_EXPRESSIONS) {
			assertTrue(SqlUtil.hasOrderBy("select a, b from t order by ".concat(expression), true),
					"top-level order by should be true: " + expression);
		}
		// 大小写/换行/制表/多空格/尾分号
		assertTrue(SqlUtil.hasOrderBy("select a from t ORDER BY nvl(a,0)", true));
		assertTrue(SqlUtil.hasOrderBy("select a from t Order\nBy nvl(a,0)", true));
		assertTrue(SqlUtil.hasOrderBy("select a from t order\tby nvl(a,0)", true));
		assertTrue(SqlUtil.hasOrderBy("select a from t order    by nvl(a,0)", true));
		assertTrue(SqlUtil.hasOrderBy("select a from t order by nvl(a,0);", true));
		// 真实排序前存在字面量括号,不应干扰
		assertTrue(SqlUtil.hasOrderBy("select a from t where remark=')' order by nvl(a,0)", true));
	}

	@Test
	public void orderByExpressionParenthesisIsFixed() {
		// 这些是本次修复的关键:旧实现因"最后一个)"在排序表达式内而误判false
		for (String sql : FIXED_FALSE_TO_TRUE) {
			assertFalse(oldHasOrderBy(sql, true), "old implementation should be false: " + sql);
			assertTrue(SqlUtil.hasOrderBy(sql, true), "fixed implementation should be true: " + sql);
		}
	}

	@Test
	public void nestedOrderByStaysFalse() {
		for (String sql : NESTED) {
			assertFalse(SqlUtil.hasOrderBy(sql, true), "nested order by should be false: " + sql);
		}
	}

	@Test
	public void literalOrderByStaysFalse() {
		for (String sql : LITERAL_ONLY) {
			assertFalse(SqlUtil.hasOrderBy(sql, true), "literal order by should be false: " + sql);
		}
	}

	@Test
	public void wrappedSqlStaysFalse() {
		for (String sql : WRAPPED) {
			assertFalse(SqlUtil.hasOrderBy(sql, true), "wrapped sql should keep historical false: " + sql);
		}
	}

	@Test
	public void judgeUpcaseMarkerStillForcesFalse() {
		assertFalse(SqlUtil.hasOrderBy("select a from t ORder by a", true));
		assertFalse(SqlUtil.hasOrderBy("select a from t ORder by nvl(a,0)", true));
		assertFalse(SqlUtil.hasOrderBy("select a from t ORder by (case when a>0 then 1 else 0 end)", true));
		// judgeUpcase=false时该特殊标记不参与
		assertTrue(SqlUtil.hasOrderBy("select a from t ORder by nvl(a,0)", false));
		// 字面量内的ORder不参与,真实order by照常判定
		assertTrue(SqlUtil.hasOrderBy("select a from t where remark='ORder by nvl(a,0)' order by a", true));
	}

	@Test
	public void oldTrueNeverBecomesFalse() {
		List<String> all = new ArrayList<String>();
		for (String expression : TOP_LEVEL_EXPRESSIONS) {
			all.add("select a, b from t order by ".concat(expression));
		}
		for (String sql : FIXED_FALSE_TO_TRUE) {
			all.add(sql);
		}
		for (String sql : NESTED) {
			all.add(sql);
		}
		for (String sql : LITERAL_ONLY) {
			all.add(sql);
		}
		for (String sql : WRAPPED) {
			all.add(sql);
		}
		all.add("select a from t");
		all.add("select a from t where remark='order by a'");
		all.add("select a from t where remark='x)' order by a");
		for (String sql : all) {
			boolean oldResult = oldHasOrderBy(sql, true);
			boolean newResult = SqlUtil.hasOrderBy(sql, true);
			assertFalse(oldResult && !newResult, "old true must not become false: " + sql);
		}
	}

	@Test
	public void randomizedShapes() {
		Random random = new Random(20261113L);
		for (int i = 0; i < 50000; i++) {
			String expression = MATRIX_EXPRESSIONS[random.nextInt(MATRIX_EXPRESSIONS.length)];
			String orderKeyword = ORDER_KEYWORDS[random.nextInt(ORDER_KEYWORDS.length)];
			int shape = random.nextInt(7);
			String sql = buildShape(shape, orderKeyword, expression);
			boolean expected = expectedShape(shape);
			boolean actual = SqlUtil.hasOrderBy(sql, true);
			assertTrue(expected == actual, "case " + i + " expected " + expected + " but got " + actual + ": " + sql);
			// 同时验证旧true不会被改false
			assertFalse(oldHasOrderBy(sql, true) && !actual, "old true must not become false: " + sql);
		}
	}

	@Test
	public void exhaustiveContextMatrix() {
		for (String expression : MATRIX_EXPRESSIONS) {
			for (String orderKeyword : ORDER_KEYWORDS) {
				for (int shape = 0; shape <= 6; shape++) {
					String sql = buildShape(shape, orderKeyword, expression);
					boolean expected = expectedShape(shape);
					boolean actual = SqlUtil.hasOrderBy(sql, true);
					assertTrue(expected == actual,
							"shape=" + shape + " expected " + expected + " but got " + actual + ": " + sql);
					assertFalse(oldHasOrderBy(sql, true) && !actual, "old true must not become false: " + sql);
				}
			}
		}
	}

	@Test
	public void parenthesisDepthMatrix() {
		for (int depth = 0; depth <= 25; depth++) {
			// 排序表达式自身嵌套depth层函数括号:仍是最外层
			StringBuilder expression = new StringBuilder();
			for (int i = 0; i < depth; i++) {
				expression.append("upper(");
			}
			expression.append("a");
			for (int i = 0; i < depth; i++) {
				expression.append(")");
			}
			String topLevel = "select a from t order by " + expression;
			assertTrue(SqlUtil.hasOrderBy(topLevel, true), "depth=" + depth + " top-level: " + topLevel);

			// 整串再套depth+1层括号:不是最外层
			StringBuilder wrapped = new StringBuilder();
			for (int i = 0; i <= depth; i++) {
				wrapped.append('(');
			}
			wrapped.append(topLevel);
			for (int i = 0; i <= depth; i++) {
				wrapped.append(')');
			}
			assertFalse(SqlUtil.hasOrderBy(wrapped.toString(), true), "depth=" + depth + " wrapped: " + wrapped);

			// 再套depth层派生表:order by全部在子查询内
			String nested = topLevel;
			for (int i = 0; i < depth; i++) {
				nested = "select * from (" + nested + ") x";
			}
			boolean expected = depth == 0;
			assertTrue(expected == SqlUtil.hasOrderBy(nested, true),
					"depth=" + depth + " nested expected " + expected + ": " + nested);
		}
	}

	/**
	 * 构造随机/矩阵场景:shape 0/1/6为最外层,2/3/4/5为子查询/窗口/字面量/整串包裹。
	 */
	private static String buildShape(int shape, String orderKeyword, String expression) {
		switch (shape) {
		case 0:
			return "select a, b from t where b=1 " + orderKeyword + " " + expression;
		case 1:
			return "select x.a from (select a from t) x " + orderKeyword + " " + expression;
		case 2:
			return "select * from (select a, b from t " + orderKeyword + " " + expression + ") x";
		case 3:
			return "select row_number() over (" + orderKeyword + " " + expression + ") rn from t";
		case 4:
			return "select a from t where remark=' " + orderKeyword + " " + expression.replace("'", "''") + "'";
		case 5:
			return "(" + "select a from t " + orderKeyword + " " + expression + ")";
		default:
			// 最外层order by后紧跟窗口函数order by:最后一个候选是内层,但外层排序真实存在
			return "select a from t " + orderKeyword + " a, row_number() over (" + orderKeyword + " " + expression
					+ ")";
		}
	}

	private static boolean expectedShape(int shape) {
		return shape == 0 || shape == 1 || shape == 6;
	}

	@Test
	public void independentTokenizerAgrees() {
		Random random = new Random(20261115L);
		for (int i = 0; i < 10000; i++) {
			String expression = MATRIX_EXPRESSIONS[random.nextInt(MATRIX_EXPRESSIONS.length)];
			String orderKeyword = ORDER_KEYWORDS[random.nextInt(ORDER_KEYWORDS.length)];
			int shape = random.nextInt(7);
			String sql = buildShape(shape, orderKeyword, expression);
			boolean expected = expectedShape(shape);
			boolean reference = referenceHasOrderBy(sql);
			boolean actual = SqlUtil.hasOrderBy(sql, true);
			assertTrue(expected == reference, "reference mismatch, expected " + expected + ": " + sql);
			assertTrue(reference == actual, "tokenizer and implementation disagree: " + sql);
		}
	}

	/**
	 * 独立参考实现:逐字符识别最外层order by(不依赖ORDER_BY_PATTERN与实现中的深度扫描),
	 * 仅用于交叉验证生成场景。语义:存在深度0的order by即为最外层;整串括号包裹/子查询内为false。
	 */
	private static boolean referenceHasOrderBy(String sql) {
		String maskedSql = SqlConfigParseUtils.maskLiterals(sql, false);
		int depth = 0;
		int minDepth = Integer.MAX_VALUE;
		for (int i = 0, n = maskedSql.length(); i < n; i++) {
			char c = maskedSql.charAt(i);
			if (c == '(') {
				depth++;
				continue;
			}
			if (c == ')') {
				depth--;
				continue;
			}
			if ((c == 'o' || c == 'O') && i + 5 <= n && maskedSql.regionMatches(true, i, "order", 0, 5)) {
				boolean leftOk = i == 0 || !isWordChar(maskedSql.charAt(i - 1));
				int j = i + 5;
				while (j < n && Character.isWhitespace(maskedSql.charAt(j))) {
					j++;
				}
				if (!leftOk || j == i + 5 || j + 1 >= n) {
					continue;
				}
				if ((maskedSql.charAt(j) == 'b' || maskedSql.charAt(j) == 'B')
						&& (maskedSql.charAt(j + 1) == 'y' || maskedSql.charAt(j + 1) == 'Y')) {
					int after = j + 2;
					if (after == n || !isWordChar(maskedSql.charAt(after))) {
						if (depth < minDepth) {
							minDepth = depth;
						}
						i = j + 1;
					}
				}
			}
		}
		return minDepth == 0;
	}

	private static boolean isWordChar(char c) {
		return Character.isLetterOrDigit(c) || c == '_';
	}

	@Test
	public void randomizedGarbageNeverThrows() {
		Random random = new Random(20261114L);
		String alphabet = "()' abcdefghijklmnopqrstuvwxyzORDERBY,.\n\t+-";
		for (int i = 0; i < 50000; i++) {
			int length = random.nextInt(200);
			StringBuilder sql = new StringBuilder(length + 16);
			for (int j = 0; j < length; j++) {
				sql.append(alphabet.charAt(random.nextInt(alphabet.length())));
			}
			if (i % 3 == 0) {
				sql.insert(random.nextInt(sql.length() + 1), "order by ");
			}
			if (i % 7 == 0) {
				sql.insert(random.nextInt(sql.length() + 1), "over(");
			}
			if (i % 11 == 0) {
				sql.insert(random.nextInt(sql.length() + 1), "(");
			}
			String input = sql.toString();
			try {
				boolean newResult = SqlUtil.hasOrderBy(input, true);
				boolean oldResult = oldHasOrderBy(input, true);
				assertFalse(oldResult && !newResult, "old true must not become false: " + input);
			} catch (Throwable e) {
				fail("hasOrderBy threw on input [" + input + "]: " + e);
			}
		}
	}

	@Test
	public void manyNestedOrderByCandidates() {
		StringBuilder sql = new StringBuilder("select ");
		for (int i = 0; i < 2000; i++) {
			sql.append("row_number() over (order by a) rn").append(i).append(", ");
		}
		sql.append("b from t");
		assertFalse(SqlUtil.hasOrderBy(sql.toString(), true), "only nested window order by should be false");
		assertTrue(SqlUtil.hasOrderBy(sql.append(" order by b").toString(), true),
				"top-level order by after many nested candidates should be true");
	}

	@Test
	public void largeInputsDoNotBreakDepthScan() {
		// 2000层函数括号:排序表达式自身开闭,应判最外层
		StringBuilder deep = new StringBuilder("select a from t order by ");
		for (int i = 0; i < 2000; i++) {
			deep.append("nvl(");
		}
		deep.append("a,0");
		for (int i = 0; i < 2000; i++) {
			deep.append(",0)");
		}
		assertTrue(SqlUtil.hasOrderBy(deep.toString(), true));

		// 2000层包裹:整体在括号内,应维持false
		StringBuilder wrapped = new StringBuilder();
		for (int i = 0; i < 2000; i++) {
			wrapped.append('(');
		}
		wrapped.append("select a from t order by nvl(a,0)");
		for (int i = 0; i < 2000; i++) {
			wrapped.append(')');
		}
		assertFalse(SqlUtil.hasOrderBy(wrapped.toString(), true));

		// 大段字面量内的order by/)不参与判定
		StringBuilder literal = new StringBuilder("select a from t where remark='");
		for (int i = 0; i < 10000; i++) {
			literal.append("order by ) ( ");
		}
		literal.append("'");
		assertFalse(SqlUtil.hasOrderBy(literal.toString(), true));
	}

	/**
	 * 修复前实现,仅用于差分:确认本次改动不会把旧true改成false。
	 */
	private static boolean oldHasOrderBy(String sql, boolean judgeUpcase) {
		String maskedSql = SqlConfigParseUtils.maskLiterals(sql, false);
		int lastBracketIndex = maskedSql.lastIndexOf(")");
		boolean result = false;
		int orderByIndex = StringUtil.matchLastIndex(maskedSql, SqlUtil.ORDER_BY_PATTERN, 1);
		if (orderByIndex > lastBracketIndex) {
			result = true;
		}
		if (judgeUpcase) {
			int upcaseOrderBy = StringUtil.matchLastIndex(maskedSql, SqlUtil.UPCASE_ORDER_PATTERN, 1);
			if (upcaseOrderBy > lastBracketIndex) {
				result = false;
			}
		}
		return result;
	}
}
