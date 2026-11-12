package org.sagacity.sqltoy.config.model;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.lang.reflect.Method;

import org.junit.jupiter.api.Test;
import org.sagacity.sqltoy.config.SqlConfigParseUtils;
import org.sagacity.sqltoy.utils.StringUtil;

/**
 * findBodyEnd替代StringUtil.getSymMarkIndex的差分验证(2026-9-3替换的发版核实):
 *
 * 1.穷举差分:字符集{(')'}与{(')','a,'}'}的短串全组合,findBodyEnd必须与
 *   独立oracle路径(maskLiterals掩码+纯深度计数,语义:''成对转义)完全一致;
 *   平衡括号场景新旧实现结果一致(替换不改变正确场景的行为);
 *   不平衡括号场景旧实现返回静默截断位,新实现统一返回-1(行为差异,固化)。
 * 2.专项语义case:字面量内括号/''转义/嵌套/注释/\'等,人工预期断言。
 * 3.端到端:SqlWithAnalysis解析正确性与未闭合体的异常行为。
 */
public class SqlWithFindBodyEndDiffTest {

	private static int findBodyEnd(String sql, int fromIndex) throws Exception {
		Method m = SqlWithAnalysis.class.getDeclaredMethod("findBodyEnd", String.class, int.class);
		m.setAccessible(true);
		return (Integer) m.invoke(null, sql, fromIndex);
	}

	/**
	 * 独立oracle:对fromIndex起点的子串做maskLiterals(仅''成对转义,与
	 * findBodyEnd语义一致)把字面量内的括号置空后,在掩码串上做纯深度计数;
	 * 与被测实现无代码共享
	 */
	private static int oracleBodyEnd(String sql, int fromIndex) {
		String masked = SqlConfigParseUtils.maskLiterals(sql.substring(fromIndex), false);
		int depth = -1;
		for (int i = 0; i < masked.length(); i++) {
			char c = masked.charAt(i);
			if (c == '(') {
				depth = (depth == -1) ? 1 : depth + 1;
			} else if (c == ')') {
				if (depth > 0) {
					depth--;
					if (depth == 0) {
						return fromIndex + i;
					}
				}
			}
		}
		return -1;
	}

	/**
	 * 穷举差分1:无引号括号串(长度1..12全组合)。
	 * 断言:findBodyEnd与oracle一致;配对成功的(平衡)串上新旧实现一致;
	 * 不平衡串上新实现必须-1(旧实现返回静默截断位,属预期行为差异)
	 */
	@Test
	public void exhaustiveBracketsDiff() throws Exception {
		int maxLen = 12;
		int agree = 0;
		int divergeOnUnbalanced = 0;
		for (int len = 1; len <= maxLen; len++) {
			int total = 1 << len;
			for (int mask = 0; mask < total; mask++) {
				StringBuilder sb = new StringBuilder();
				for (int i = 0; i < len; i++) {
					sb.append((mask >> i & 1) == 1 ? ')' : '(');
				}
				String sql = sb.toString();
				int newIdx = findBodyEnd(sql, 0);
				int oracleIdx = oracleBodyEnd(sql, 0);
				assertEquals(oracleIdx, newIdx, "findBodyEnd与oracle不一致: " + sql);
				int oldIdx = StringUtil.getSymMarkIndex("(", ")", sql, 0);
				if (oracleIdx != -1) {
					assertEquals(oracleIdx, oldIdx, "平衡括号场景旧实现应与新实现一致: " + sql);
					agree++;
				} else if (oldIdx != -1) {
					divergeOnUnbalanced++;
				}
			}
		}
		assertTrue(agree > 1000, "平衡场景样本应充分,实际:" + agree);
		assertTrue(divergeOnUnbalanced > 0, "应存在不平衡场景的新旧行为差异样本");
	}

	/**
	 * 穷举差分2:含字母与引号的混合串(长度1..6全组合)。
	 * 断言:findBodyEnd与oracle(掩码+深度计数)完全一致,覆盖引号不平衡、
	 * ''成对转义、字面量内括号等所有组合;并统计与旧实现的行为分歧样本>0
	 * (证明替换确实改变了字面量场景的行为)
	 */
	@Test
	public void exhaustiveLiteralMixedDiff() throws Exception {
		int maxLen = 6;
		char[] chars = { '(', ')', 'a', '\'' };
		int diffFromOld = 0;
		for (int len = 1; len <= maxLen; len++) {
			int total = 1 << (len * 2);
			for (int mask = 0; mask < total; mask++) {
				StringBuilder sb = new StringBuilder();
				for (int i = 0; i < len; i++) {
					sb.append(chars[(mask >> (i * 2)) & 3]);
				}
				String sql = sb.toString();
				int newIdx = findBodyEnd(sql, 0);
				int oracleIdx = oracleBodyEnd(sql, 0);
				assertEquals(oracleIdx, newIdx, "findBodyEnd与oracle不一致: [" + sql + "]");
				int oldIdx = StringUtil.getSymMarkIndex("(", ")", sql, 0);
				if (oldIdx != newIdx) {
					diffFromOld++;
				}
			}
		}
		assertTrue(diffFromOld > 0, "字面量混合场景应存在新旧行为分歧样本:" + diffFromOld);
	}

	// ---------------- 专项语义case ----------------

	/**
	 * 替换目标的修复场景:体含收尾括号字符的字面量。旧实现把字面量内的括号
	 * 当终结导致截断,新实现返回真实配对位置
	 */
	@Test
	public void literalCloseParenFixed() throws Exception {
		String body = "(select ')' as mark from t)";
		int newIdx = findBodyEnd(body, 0);
		int oldIdx = StringUtil.getSymMarkIndex("(", ")", body, 0);
		assertEquals(body.length() - 1, newIdx, "应配对到真实收尾括号");
		assertTrue(oldIdx != newIdx, "旧实现应被字面量干扰,实际:" + oldIdx);
	}

	/**
	 * ''成对转义字面量:字面量内的括号(含转义引号间的)不得参与配对
	 */
	@Test
	public void doubleQuoteEscapeLiteral() throws Exception {
		String body = "(select 'it''s ) ( ok' as v, (select 1) as c from t)";
		assertEquals(body.length() - 1, findBodyEnd(body, 0), "''转义字面量内括号应被跳过");
		String plain = "(select 'it''s ) ok' as v from t)";
		assertEquals(plain.length() - 1, findBodyEnd(plain, 0), "含''转义的完整配对");
	}

	/**
	 * 深层嵌套与字面量交错:深度计数必须正确归零
	 */
	@Test
	public void deepNestedWithLiterals() throws Exception {
		String body = "((select concat('(', '(') from (select ')' as x) z) union (select 1))";
		assertEquals(body.length() - 1, findBodyEnd(body, 0), "嵌套与字面量交错应配对到末尾括号");
		// 去掉末尾一个收尾括号则不闭合,应返回-1
		assertEquals(-1, findBodyEnd(body.substring(0, body.length() - 1), 0), "未闭合应返回-1");
	}

	/**
	 * fromIndex语义:从as位置起找第一个开括号,与调用点传参方式一致
	 */
	@Test
	public void fromIndexSemantics() throws Exception {
		String sql = " with cte as (select ')' as m from t) select * from cte";
		int asIdx = sql.toLowerCase().indexOf("as") + 1;
		// 体收尾括号是串中最后一个右括号字符(字面量内的右括号必须被跳过)
		assertEquals(sql.lastIndexOf(')'), findBodyEnd(sql, asIdx), "从as位置起配对到真实收尾括号");
		// fromIndex越过体开括号后,体内无其他开括号,深度无法归零,应返回-1
		int afterOpen = sql.indexOf('(') + 1;
		assertEquals(-1, findBodyEnd(sql, afterOpen), "越过体开括号后无法配对");
	}

	/**
	 * 已知局限(与maskLiterals语义对齐):反斜杠转义引号按标准SQL语义处理,
	 * 即'a加反斜杠加引号的字面量在第二个引号处终结;mysql系方言中该引号属
	 * 转义引号。此局限相比旧实现(完全无字面量感知)仍是改进
	 */
	@Test
	public void backslashQuoteKnownLimitation() throws Exception {
		// 'a\后跟引号:标准SQL下字面量为a\,其后的)参与配对
		String sql = "(select 'a\\' as v) tail";
		assertEquals(sql.indexOf(')'), findBodyEnd(sql, 0), "标准SQL语义:字面量在第二引号终结");
	}

	/**
	 * 已知局限(新旧一致):注释内的收尾括号不被跳过,会提前终结配对。
	 * 旧实现同样把注释内括号当终结,属维持现状而非回归
	 */
	@Test
	public void commentParenSameAsLegacy() throws Exception {
		// 注释体: (select 1 注释含) from t) — 注释内的)提前终结
		String sql = "(select 1 /* ) */ from t)";
		int newIdx = findBodyEnd(sql, 0);
		int oldIdx = StringUtil.getSymMarkIndex("(", ")", sql, 0);
		assertEquals(oldIdx, newIdx, "注释场景新旧行为应一致(均被注释内括号干扰)");
		assertTrue(newIdx < sql.length() - 1, "注释内括号会提前终结(已知限制)");
	}

	// ---------------- SqlWithAnalysis端到端 ----------------

	/**
	 * 修复场景端到端:体含收尾括号字符的字面量时withSql/withSqlSet/rejectWithSql必须完整
	 */
	@Test
	public void parseWithLiteralParenBody() {
		SqlWithAnalysis w = new SqlWithAnalysis(
				"with w as (select ')' as mark from t where id=1) select * from w");
		assertTrue(w.isHasWith());
		assertEquals("with w as (select ')' as mark from t where id=1) ", w.getWithSql());
		String[] cte = w.getWithSqlSet().get(0);
		assertEquals("w", cte[0]);
		// 体内容=开括号之后至收尾括号之前(不含两端括号)
		assertEquals("select ')' as mark from t where id=1", cte[2], "体应完整提取至真实收尾括号");
		assertEquals("select * from w", w.getRejectWithSql().trim(), "主查询应完整剥离");
	}

	/**
	 * 多CTE且每个体都含字面量括号
	 */
	@Test
	public void parseMultipleCteWithLiteralParens() {
		SqlWithAnalysis w = new SqlWithAnalysis(
				"with a as (select '(' as l from t1), b as (select ')' as r from t2) select * from a, b");
		assertTrue(w.isHasWith());
		assertEquals(2, w.getWithSqlSet().size());
		assertEquals("a", w.getWithSqlSet().get(0)[0]);
		assertEquals("b", w.getWithSqlSet().get(1)[0]);
		assertEquals("select '(' as l from t1", w.getWithSqlSet().get(0)[2]);
		assertEquals("select ')' as r from t2", w.getWithSqlSet().get(1)[2]);
		assertEquals("with a as (select '(' as l from t1), b as (select ')' as r from t2) ", w.getWithSql());
		assertEquals("select * from a, b", w.getRejectWithSql().trim());
		assertTrue(w.getFootSql().startsWith("select"));
	}

	/**
	 * 未闭合体:findBodyEnd返回-1,parse下游substring抛越界异常(病态输入的
	 * 行为固化);旧实现对该形态(无任何收尾括号)同样返回-1并抛异常
	 */
	@Test
	public void unclosedBodyThrows() throws Exception {
		String sql = "with a as (select id from t1 select * from t";
		assertEquals(-1, oracleBodyEnd(sql, 0));
		assertThrows(Exception.class, () -> new SqlWithAnalysis(sql), "未闭合体应显式抛异常");
		// 旧实现同样返回-1
		assertEquals(-1, StringUtil.getSymMarkIndex("(", ")", sql, 0));
	}

	/**
	 * 首括号后紧跟成对括号的不平衡形态:旧实现静默返回内层收尾括号导致
	 * 错误切分(不抛异常);新实现返回-1(行为差异点:病态输入下由静默
	 * 错误改为显式异常)
	 */
	@Test
	public void unbalancedSilentTruncationDiff() throws Exception {
		String sql = "with a as (() select id from t";
		int from = sql.toLowerCase().indexOf("as") + 1;
		int newIdx = findBodyEnd(sql, from);
		int oldIdx = StringUtil.getSymMarkIndex("(", ")", sql, from);
		assertEquals(-1, newIdx, "新实现:不闭合返回-1");
		assertTrue(oldIdx != -1, "旧实现:静默返回内层收尾括号位置(错误切分)");
	}

	/**
	 * 性能冒烟:大sql上findBodyEnd完成全串扫描的耗时应在毫秒级(线性扫描,
	 * 不与旧实现对比绝对值——旧实现遇字面量错切提前返回,耗时无可比性)
	 */
	@Test
	public void performanceSmoke() throws Exception {
		StringBuilder sb = new StringBuilder("(select 'text ) ( literal' as v");
		for (int i = 0; i < 8000; i++) {
			sb.append(", (select 'x)y(' from t");
		}
		for (int i = 0; i < 8000; i++) {
			sb.append(')');
		}
		sb.append(")");
		String big = sb.toString();
		long t1 = System.nanoTime();
		for (int i = 0; i < 50; i++) {
			int idx = findBodyEnd(big, 0);
			assertEquals(big.length() - 1, idx, "大串配对必须正确(第" + i + "轮)");
		}
		long newCost = System.nanoTime() - t1;
		System.err.println("[findBodyEnd perf] 50x~250KB=" + newCost / 1000000 + "ms");
		assertTrue(newCost < 2_000_000_000L, "50轮大串扫描应在2秒内,实际:" + newCost / 1000000 + "ms");
	}

	// ---------------- count链路端到端 ----------------

	/**
	 * count端到端:with体含收尾括号字符的字面量时,getCountBySql的with剥离与
	 * count改写必须正确(H2实际执行);旧实现在此场景会把字面量内的)当with体
	 * 终结,导致with剥离破碎、count语句语法错误
	 */
	@Test
	public void countEndToEndWithLiteralParenBody() throws Exception {
		java.sql.Connection conn = java.sql.DriverManager.getConnection("jdbc:h2:mem:withbodyend;DB_CLOSE_DELAY=-1",
				"sa", "");
		try (java.sql.Statement st = conn.createStatement()) {
			st.execute("drop table if exists t");
			st.execute("create table t (id int, remark varchar(100))");
			st.execute("insert into t values (1,')marker(')");
			st.execute("insert into t values (2,'normal')");
		}
		org.sagacity.sqltoy.SqlToyContext context = new org.sagacity.sqltoy.SqlToyContext();
		org.sagacity.sqltoy.config.model.SqlToyConfig config = new org.sagacity.sqltoy.config.model.SqlToyConfig(null);
		config.setHasWith(true);
		String sql = "with w as (select ')' as mark from t where id=1) select * from w";
		Long count = org.sagacity.sqltoy.dialect.utils.DialectUtils.getCountBySql(context, config, sql, new Object[0],
				false, null, conn, org.sagacity.sqltoy.utils.DataSourceUtils.DBType.H2);
		assertEquals(1L, count.longValue(), "with体含括号字面量的count应正确执行并返回1");
		// 多CTE+字面量括号形态
		String sql2 = "with a as (select '(' as l from t where id=1), b as (select ')' as r from t where id=2) "
				+ "select a.l from a join b on a.l=b.r";
		config = new org.sagacity.sqltoy.config.model.SqlToyConfig(null);
		config.setHasWith(true);
		Long count2 = org.sagacity.sqltoy.dialect.utils.DialectUtils.getCountBySql(context, config, sql2,
				new Object[0], false, null, conn, org.sagacity.sqltoy.utils.DataSourceUtils.DBType.H2);
		assertEquals(0L, count2.longValue(), "多CTE字面量括号的count应正确执行(无交集返回0)");
		conn.close();
	}
}
