package org.sagacity.sqltoy.config;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.lang.reflect.Method;
import java.util.Random;

import org.junit.jupiter.api.Test;
import org.sagacity.sqltoy.config.model.SqlToyResult;
import org.sagacity.sqltoy.utils.StringUtil;

/**
 * SqlConfigParseUtils 性能改动的等价性验证:
 * 用“旧算法重实现”做差分,再用随机串模糊测试覆盖边界,确保加速没有改变语义。
 * <li>countArgMarks 逐字符计数 vs 正则计数</li>
 * <li>hasDanglingConnector 逐字符探测 vs 原正则探测(必须双向一致)</li>
 * <li>cleanDanglingConnectorInParens 输出 vs 原replaceAll循环输出(必须逐字符一致)</li>
 * <li>matchIndexAtEnd 窗口匹配 vs 原全串find(必须无假阳性,真实窗口内不得漏匹配)</li>
 * <li>matchesAtStart lookingAt vs 原find(^锚定模式下等价)</li>
 * </ul>
 */
public class SqlConfigParseUtilsEquivalenceTest {

	private static final java.util.regex.Pattern DANGLING = java.util.regex.Pattern
			.compile("(?i)(\\(\\s*(and|or)\\b|\\b(and|or)\\s*\\))");
	private static final java.util.regex.Pattern WHERE_END = SqlConfigParseUtils.WHERE_END_PATTERN;
	private static final java.util.regex.Pattern WHERE_ONE = SqlConfigParseUtils.WHERE_ONE_EQUAL_PATTERN;
	private static final java.util.regex.Pattern UPDATE_SET = SqlConfigParseUtils.UPDATE_SET_PATTERN;
	private static final java.util.regex.Pattern WHERE_CLOSE = SqlConfigParseUtils.WHERE_CLOSE_PATTERN;
	private static final java.util.regex.Pattern AND_START = SqlConfigParseUtils.AND_START_PATTERN;

	private static final String[] TOKENS = { "and", "or", "AND", "Or", "(", ")", " ", "\n", "\t", "\r", "where",
			"WHERE", "1", "=", "set", "SET", "x", "order by", "group by", "having", "limit", "union", "?", ":", "'",
			"[", "]", "#", "@if(", "and(", "or)" };

	private static Method countArgMarks;
	private static Method matchIndexAtEnd;
	private static Method matchesAtStart;
	private static Method hasDanglingConnector;
	private static Method cleanDanglingConnectorInParens;

	static {
		try {
			countArgMarks = declared("countArgMarks", String.class);
			matchIndexAtEnd = declared("matchIndexAtEnd", String.class, java.util.regex.Pattern.class, char.class);
			matchesAtStart = declared("matchesAtStart", String.class, java.util.regex.Pattern.class);
			hasDanglingConnector = declared("hasDanglingConnector", String.class);
			cleanDanglingConnectorInParens = declared("cleanDanglingConnectorInParens", String.class);
		} catch (Exception e) {
			throw new IllegalStateException(e);
		}
	}

	private static Method declared(String name, Class<?>... types) throws Exception {
		Method method = SqlConfigParseUtils.class.getDeclaredMethod(name, types);
		method.setAccessible(true);
		return method;
	}

	// ============ 1. "?"计数 ============

	@Test
	public void argMarkCountEqualsRegexCount() throws Exception {
		Random random = new Random(20261111L);
		for (int i = 0; i < 3000; i++) {
			String sql = randomText(random);
			int expected = StringUtil.matchCnt(sql, SqlConfigParseUtils.ARG_NAME_PATTERN, 0);
			assertEquals(expected, ((Integer) countArgMarks.invoke(null, sql)).intValue(), "输入:" + sql);
		}
	}

	// ============ 2. 悬挂连接符探测(双向一致) ============

	@Test
	public void danglingDetectorEqualsRegexDetector() throws Exception {
		Random random = new Random(20261112L);
		int hit = 0;
		for (int i = 0; i < 5000; i++) {
			String sql = randomText(random);
			boolean expected = DANGLING.matcher(sql).find();
			boolean actual = (Boolean) hasDanglingConnector.invoke(null, sql);
			assertEquals(expected, actual, "探测不一致,输入:" + sql);
			if (expected) {
				hit++;
			}
		}
		assertTrue(hit > 100, "随机样本应覆盖到悬挂连接符场景,实际:" + hit);
	}

	@Test
	public void danglingDetectorBoundaryCases() throws Exception {
		String[] positives = { "( and x=1)", "(or x=1)", "(   AND  x=1)", "(x=1 or )", "(x=1 and   )", "(or)",
				"( AND )" };
		String[] negatives = { "(order by x)", "( x=1 )", "and(x)", "(x and y)", "(orange x=1)", "(x=1 orx)" };
		for (String sql : positives) {
			assertTrue((Boolean) hasDanglingConnector.invoke(null, sql), "应识别:" + sql);
		}
		for (String sql : negatives) {
			assertTrue(!((Boolean) hasDanglingConnector.invoke(null, sql)), "不应识别:" + sql);
		}
	}

	// ============ 3. 清理结果与旧实现逐字符一致 ============

	@Test
	public void cleanResultEqualsLegacyImplementation() throws Exception {
		Random random = new Random(20261113L);
		int changed = 0;
		for (int i = 0; i < 4000; i++) {
			String sql = randomText(random);
			String expected = legacyClean(sql);
			String actual = (String) cleanDanglingConnectorInParens.invoke(null, sql);
			assertEquals(expected, actual, "清理结果不一致,输入:" + sql);
			if (!expected.equals(sql)) {
				changed++;
			}
		}
		assertTrue(changed > 50, "随机样本应覆盖到实际清理场景,实际:" + changed);
	}

	private static String legacyClean(String sql) {
		String result = sql;
		while (true) {
			String cleaned = result.replaceAll("(?i)\\(\\s*(and|or)\\b", "(")
					.replaceAll("(?i)\\b(and|or)\\s*\\)", ")");
			if (cleaned.equals(result)) {
				return result;
			}
			result = cleaned;
		}
	}

	// ============ 4. 末尾锚定窗口匹配 ============

	@Test
	public void endAnchoredMatchHasNoFalsePositive() throws Exception {
		Random random = new Random(20261114L);
		for (int i = 0; i < 3000; i++) {
			String sql = randomText(random);
			assertNoFalsePositive(sql, WHERE_END, 'e');
			assertNoFalsePositive(sql, WHERE_ONE, '1');
			assertNoFalsePositive(sql, UPDATE_SET, 't');
		}
	}

	private void assertNoFalsePositive(String sql, java.util.regex.Pattern pattern, char lastChar) throws Exception {
		int expected = StringUtil.matchIndex(sql, pattern);
		int actual = (Integer) matchIndexAtEnd.invoke(null, sql, pattern, lastChar);
		if (actual >= 0) {
			assertEquals(expected, actual, "不应出现假阳性,输入:" + sql);
		}
	}

	@Test
	public void endAnchoredMatchFindsRealisticForms() throws Exception {
		assertEndMatch("select * from t where", WHERE_END, 'e', true);
		assertEndMatch("select * from t WHERE   ", WHERE_END, 'e', true);
		assertEndMatch("select * from t where 1=1", WHERE_ONE, '1', true);
		assertEndMatch("select * from t where 1 = 1   ", WHERE_ONE, '1', true);
		assertEndMatch("update t set", UPDATE_SET, 't', true);
		// 换行/缩进(格式化sql)
		assertEndMatch("update t\n\t set  ", UPDATE_SET, 't', true);
		assertEndMatch("select * from t where\n        1\n        =\n        1", WHERE_ONE, '1', true);
		assertEndMatch("select * from t where x=1", WHERE_END, 'e', false);
		assertEndMatch("select * from t where 1=2", WHERE_ONE, '1', false);
		assertEndMatch("select * from t offset 1", UPDATE_SET, 't', false);
	}

	private void assertEndMatch(String sql, java.util.regex.Pattern pattern, char lastChar, boolean expectedFound)
			throws Exception {
		int expected = StringUtil.matchIndex(sql, pattern);
		int actual = (Integer) matchIndexAtEnd.invoke(null, sql, pattern, lastChar);
		assertEquals(expected >= 0, expectedFound, "参考实现判定异常:" + sql);
		assertEquals(expected, actual, "窗口匹配与全串匹配不一致:" + sql);
	}

	/**
	 * 已知边界:窗口只在“匹配起点距离末尾超过END_WINDOW且中间是超长空白”时才可能漏,属可接受的有界近似;
	 * 此处锁定该行为,避免以后窗口被误调小而悄悄影响SQL拼接
	 */
	@Test
	public void endAnchoredWindowBoundaryIsDocumented() throws Exception {
		StringBuilder sql = new StringBuilder("select * from t where");
		for (int i = 0; i < 300; i++) {
			sql.append(' ');
		}
		sql.append("1=1");
		// 匹配仍从头覆盖到末尾,窗口按最后非空白字符回退,应当正常命中
		assertEquals(StringUtil.matchIndex(sql.toString(), WHERE_ONE),
				((Integer) matchIndexAtEnd.invoke(null, sql.toString(), WHERE_ONE, '1')).intValue());
	}

	/**
	 * 窗口边界附近的精确验证:构造长度使匹配起点分别落在窗口内、窗口外,二者都必须与全串匹配一致
	 * (窗口外依赖“未命中回退全串”的兜底)
	 */
	@Test
	public void endAnchoredWindowBoundaryExact() throws Exception {
		for (int spaces : new int[] { 0, 100, 250, 254, 255, 256, 257, 300, 1000 }) {
			StringBuilder sql = new StringBuilder("select * from t where");
			for (int i = 0; i < spaces; i++) {
				sql.append(' ');
			}
			sql.append("1=1");
			String text = sql.toString();
			int expected = StringUtil.matchIndex(text, WHERE_ONE);
			int actual = (Integer) matchIndexAtEnd.invoke(null, text, WHERE_ONE, '1');
			assertEquals(expected, actual, "空白数:" + spaces + " 时窗口匹配与全串不一致");
		}
		// 末尾空白与换行混合
		String mixed = "select * from t where\n    1\n    =\n    1\n\t  ";
		assertEquals(StringUtil.matchIndex(mixed, WHERE_ONE),
				((Integer) matchIndexAtEnd.invoke(null, mixed, WHERE_ONE, '1')).intValue());
	}

	/**
	 * 词边界与控制字符的定向用例:Java正则的\b把下划线、CJK、重音字母都当单词字符
	 */
	@Test
	public void danglingDetectorUnicodeAndUnderscoreBoundaries() throws Exception {
		String[] cases = { "(and_x)", "(and1x)", "(and中)", "(and-x)", "(or_1)", "x and_)", "(AND_x)",
				"(and\u00e9x)", "(\tand\n)", "(  or  )", "(or)", "x(and)y" };
		for (String sql : cases) {
			boolean expected = DANGLING.matcher(sql).find();
			boolean actual = (Boolean) hasDanglingConnector.invoke(null, sql);
			assertEquals(expected, actual, "边界探测不一致:" + sql);
		}
	}

	/**
	 * 清理必须具备幂等性与收敛性(多层括号嵌套时不漏、不重复改)
	 */
	@Test
	public void cleanIsIdempotentAndConverges() throws Exception {
		String[] cases = { "((((and x))))", "( and ( or y ) )", "((( or )))", "(and)", "((and (or)))",
				"( x or ) and ( y and )" };
		for (String sql : cases) {
			String once = (String) cleanDanglingConnectorInParens.invoke(null, sql);
			String twice = (String) cleanDanglingConnectorInParens.invoke(null, once);
			assertEquals(once, twice, "清理不幂等:" + sql);
			assertEquals(legacyClean(sql), once, "与旧实现不一致:" + sql);
			assertTrue(!DANGLING.matcher(once).find(), "清理后仍存在悬挂连接符:" + sql + " -> " + once);
		}
	}

	// ============ 5. 起始锚定匹配 ============

	@Test
	public void startAnchoredMatchEqualsFind() throws Exception {
		Random random = new Random(20261115L);
		int hit = 0;
		for (int i = 0; i < 3000; i++) {
			String sql = randomText(random);
			hit += checkStart(sql, AND_START) ? 1 : 0;
			hit += checkStart(sql, WHERE_CLOSE) ? 1 : 0;
		}
		assertTrue(hit > 50, "随机样本应覆盖起始匹配场景,实际:" + hit);
	}

	private boolean checkStart(String sql, java.util.regex.Pattern pattern) throws Exception {
		boolean expected = StringUtil.matches(sql, pattern);
		boolean actual = (Boolean) matchesAtStart.invoke(null, sql, pattern);
		assertEquals(expected, actual, "起始匹配不一致,输入:" + sql);
		return expected;
	}

	// ============ 6. processSql 端到端不变量 ============

	/**
	 * 随机多条件场景下验证processSql的后置不变量:不残留#[、占位符与参数数量一致、无悬挂连接符
	 */
	@Test
	public void processSqlKeepsInvariants() throws Exception {
		Random random = new Random(20261117L);
		for (int i = 0; i < 200; i++) {
			int fragmentCount = 1 + random.nextInt(30);
			StringBuilder sql = new StringBuilder("select * from t_order t where 1=1 ");
			String[] names = new String[fragmentCount];
			Object[] values = new Object[fragmentCount];
			for (int f = 0; f < fragmentCount; f++) {
				names[f] = "p" + f;
				values[f] = random.nextBoolean() ? null : ("v" + f);
				sql.append("#[and t.c").append(f).append("=:").append(names[f]).append("] ");
			}
			SqlToyResult result = SqlConfigParseUtils.processSql(sql.toString(), names, values, "mysql");
			assertTrue(!result.getSql().contains("#["), "残留动态标记:" + result.getSql());
			int argCount = StringUtil.matchCnt(result.getSql(), SqlConfigParseUtils.ARG_NAME_PATTERN, 0);
			int paramCount = result.getParamsValue() == null ? 0 : result.getParamsValue().length;
			assertEquals(argCount, paramCount, "占位符与参数数量不一致:" + result.getSql());
			assertTrue(!DANGLING.matcher(result.getSql()).find(), "清理后仍有悬挂连接符:" + result.getSql());
		}
	}

	// ============ 随机文本 ============

	private static String randomText(Random random) {
		int size = random.nextInt(30);
		StringBuilder text = new StringBuilder();
		for (int i = 0; i < size; i++) {
			if (random.nextInt(4) == 0) {
				text.append((char) (32 + random.nextInt(95)));
			} else {
				text.append(TOKENS[random.nextInt(TOKENS.length)]);
			}
		}
		return text.toString();
	}
}
