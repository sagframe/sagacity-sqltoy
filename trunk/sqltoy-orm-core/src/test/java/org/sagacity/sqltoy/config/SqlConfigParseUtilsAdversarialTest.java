package org.sagacity.sqltoy.config;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.lang.reflect.Method;
import java.util.regex.Pattern;

import org.junit.jupiter.api.Test;
import org.sagacity.sqltoy.config.model.SqlToyResult;
import org.sagacity.sqltoy.utils.StringUtil;

/**
 * update 2026-11-11 SqlConfigParseUtils热路径优化(字符计数/尾部窗口/悬挂探测)的对抗性深测:
 * 项目必经路径(每条sql都经过),在既有差分等价测试之上补三类边界与性能门禁:
 * <ol>
 * <li>窗口回退等价:超长混合空白(空格/制表/换行>256字符)跨出END_WINDOW时的全串回退,
 * 与整串find严格一致(dcdfce703补丁的边界)</li>
 * <li>countArgMarks与hasDanglingConnector的对抗形态(字面量?、边界词、大小写、超长串)</li>
 * <li>性能门禁:片段数1→40的耗时比须远低于平方级(平方=1600倍),且绝对耗时有上限——
 * 防止优化被无声回退成每片段全串正则扫描</li>
 */
public class SqlConfigParseUtilsAdversarialTest {

	private static final Pattern WHERE_END = SqlConfigParseUtils.WHERE_END_PATTERN;
	private static final Pattern WHERE_ONE = SqlConfigParseUtils.WHERE_ONE_EQUAL_PATTERN;
	private static final Pattern UPDATE_SET = SqlConfigParseUtils.UPDATE_SET_PATTERN;
	private static final Pattern LEGACY_DANGLING_LEFT = Pattern.compile("(?i)\\(\\s*(and|or)\\b");
	private static final Pattern LEGACY_DANGLING_RIGHT = Pattern.compile("(?i)\\b(and|or)\\s*\\)");
	private static final Pattern ARG_MARK = Pattern.compile("\\?");

	// ==================== 窗口回退等价 ====================

	@Test
	public void oversizedMixedWhitespaceFallsBackToFullScan() throws Exception {
		Method matchIndexAtEnd = method("matchIndexAtEnd", String.class, Pattern.class, char.class);
		// 300个混合空白字符(空格/制表/换行/CR)跨出256窗口,回退全串后必须仍命中
		// (jre8同步适配:repeat改nCopies)
		String blanks = String.join("", java.util.Collections.nCopies(80, " \t\r\n"));
		for (Object[] caseSpec : new Object[][] { { "select * from t where" + blanks, WHERE_END, 'e' },
				{ "select * from t where" + blanks + "1=1", WHERE_ONE, '1' },
				{ "update t set a=1" + blanks + "where id=1" + blanks, WHERE_END, 'e' },
				{ "update t set a=1 where id=1 set", UPDATE_SET, 't' } }) {
			String sql = (String) caseSpec[0];
			Pattern pattern = (Pattern) caseSpec[1];
			char lastChar = (Character) caseSpec[2];
			int expected = StringUtil.matchIndex(sql, pattern);
			int actual = (Integer) matchIndexAtEnd.invoke(null, sql, pattern, lastChar);
			assertEquals(expected, actual, "窗口回退须与整串find一致: [" + sql.length() + "字符]");
		}
	}

	@Test
	public void endToEndWhereOneEqualStripWithOversizedTrailingBlank() {
		// where 1=1剔除链路在超长空白形态下端到端等价
		StringBuilder sql = new StringBuilder("select * from t where 1=1");
		sql.append(String.join("", java.util.Collections.nCopies(3, " and name=:name")));
		sql.append(String.join("", java.util.Collections.nCopies(300, " ")));
		SqlToyResult result = SqlConfigParseUtils.processSql(sql.toString(), new String[] { "name" },
				new Object[] { "v" }, "mysql");
		assertTrue(result.getSql().contains("name=?") || result.getSql().contains("name = ?"),
				"参数应完成命名转位置: " + result.getSql());
	}

	// ==================== countArgMarks 对抗 ====================

	@Test
	public void countArgMarksMatchesRegexOnAdversarialInputs() throws Exception {
		Method countArgMarks = method("countArgMarks", String.class);
		String[] adversarial = { "", "?", "??", "a?b?c?", "'?literal?'", "a''b?'?''?", "中文?数据?",
				"select 'a?b' from t where x=? and y=?", String.join("", java.util.Collections.nCopies(100000, "?")),
				"select\n?\tfrom\r\nt" };
		for (String sql : adversarial) {
			int expected = StringUtil.matchCnt(sql, ARG_MARK, 0);
			int actual = (Integer) countArgMarks.invoke(null, sql);
			assertEquals(expected, actual, "countArgMarks须与正则计数一致: [" + sql.length() + "字符]");
		}
	}

	// ==================== 悬挂连接符探测对抗 ====================

	@Test
	public void danglingDetectorMatchesLegacyRegexOnAdversarialInputs() throws Exception {
		Method detector = method("hasDanglingConnector", String.class);
		String[] adversarial = { "( and x=1", "(AND x=1", "(  or  x=1", "( and_x=1", "( android=1", "(a and b)",
				"x=1 or )", "x=1 OR\t)", "x=1 or)", "x=1 orr)", "'( and' literal", "cast(a as int) and (b)",
				"( and ( or ) )", "(((and x=1)))", "", "(", ")", "and", "or" };
		for (String sql : adversarial) {
			boolean legacy = LEGACY_DANGLING_LEFT.matcher(sql).find() || LEGACY_DANGLING_RIGHT.matcher(sql).find();
			boolean actual = (Boolean) detector.invoke(null, sql);
			assertEquals(legacy, actual, "悬挂探测须与旧正则判定一致: " + sql);
		}
	}

	@Test
	public void danglingCleanResultMatchesLegacyOnAdversarialInputs() throws Exception {
		// 清理结果须与旧replaceAll链完全一致(含字面量内形态——两者同等处理,保持等价而非"修正")
		String[] inputs = { "select * from t where ( and x=1", "select * from t where x=1 or )",
				"select * from t where ( ( and or ) ) and y=2", "select '( and' from t where ( and x=1",
				"select * from t where ( AND x=1 and ( OR y=2 ) )" };
		for (String sql : inputs) {
			String legacy = sql;
			while (true) {
				String cleaned = LEGACY_DANGLING_RIGHT.matcher(
						LEGACY_DANGLING_LEFT.matcher(legacy).replaceAll("(")).replaceAll(")");
				if (cleaned.equals(legacy)) {
					break;
				}
				legacy = cleaned;
			}
			Method cleaner = method("cleanDanglingConnectorInParens", String.class);
			String actual = (String) cleaner.invoke(null, sql);
			assertEquals(legacy, actual, "清理结果须与旧实现一致: " + sql);
		}
	}

	// ==================== 性能门禁 ====================

	/**
	 * 片段数1→40的耗时比门禁:旧实现每片段对整串做多次全串正则扫描(近似平方),优化后应为近线性。
	 * 阈值取100倍(线性40倍+富余),平方形态(1600倍)必然击穿;配合绝对上限双保险,防优化无声回退。
	 */
	@Test
	public void parseScalingStaysNearLinear() throws Exception {
		int iterations = 2000;
		long oneFragment = timeProcessSql(1, iterations);
		long fortyFragments = timeProcessSql(40, iterations);
		double ratio = (double) fortyFragments / Math.max(1, oneFragment);
		assertTrue(ratio < 100, "1→40片段耗时比应近线性(实测:" + String.format("%.1f", ratio)
				+ "倍,平方形态约1600倍),优化可能被回退");
		// 绝对上限:40片段动态sql单次解析应远低于5ms(现状微秒级,防数量级回退)
		double perCallMs = (double) fortyFragments / iterations / 1_000_000;
		assertTrue(perCallMs < 5, "40片段单次解析耗时:" + String.format("%.3f", perCallMs) + "ms,超绝对上限");
	}

	private static long timeProcessSql(int fragmentCount, int iterations) {
		StringBuilder sql = new StringBuilder("select * from t_order t where 1=1 ");
		int paramCount = fragmentCount + 2;
		String[] names = new String[paramCount];
		Object[] values = new Object[paramCount];
		for (int i = 0; i < fragmentCount; i++) {
			names[i] = "p" + i;
			values[i] = (i % 2 == 0) ? "v" + i : null;
			sql.append("#[and t.c").append(i).append("=:").append(names[i]).append("] ");
		}
		names[fragmentCount] = "g0";
		values[fragmentCount] = "v0";
		names[fragmentCount + 1] = "h1";
		values[fragmentCount + 1] = "v1";
		sql.append("#[and (t.g0=:").append(names[fragmentCount]).append(" or t.h1=:").append(names[fragmentCount + 1])
				.append(")] ");
		String sqlStr = sql.toString();
		// 预热(JIT)+结果正确性自证
		SqlToyResult check = SqlConfigParseUtils.processSql(sqlStr, names, values, "mysql");
		assertTrue(!check.getSql().contains("#["), "动态sql应完成处理");
		for (int i = 0; i < 500; i++) {
			SqlConfigParseUtils.processSql(sqlStr, names, values, "mysql");
		}
		long start = System.nanoTime();
		for (int i = 0; i < iterations; i++) {
			SqlConfigParseUtils.processSql(sqlStr, names, values, "mysql");
		}
		return System.nanoTime() - start;
	}

	private static Method method(String name, Class<?>... parameterTypes) throws Exception {
		Method declared = SqlConfigParseUtils.class.getDeclaredMethod(name, parameterTypes);
		declared.setAccessible(true);
		return declared;
	}
}
