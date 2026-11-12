package org.sagacity.sqltoy.config;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.lang.reflect.Method;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Random;

import org.junit.jupiter.api.Test;
import org.sagacity.sqltoy.SqlToyConstants;
import org.sagacity.sqltoy.config.model.SqlToyResult;
import org.sagacity.sqltoy.utils.CollectionUtil;
import org.sagacity.sqltoy.utils.StringUtil;

/**
 * processNullConditions 整方法级差分测试(该项目最核心的sql解析逻辑):
 * 测试内保留一份“优化前算法”的逐行拷贝(正则计数/整串find/整串toLowerCase/replaceAll清理),
 * 与当前实现跑同一批输入,要求输出的SQL与参数数组完全一致。
 * <p>
 * 覆盖:where/where 1=1、and/or片段、括号组、order by结尾、update set、随机null组合。
 * </p>
 */
public class SqlConfigParseUtilsLegacyDiffTest {

	private static final Method PROCESS_NULL_CONDITIONS;

	static {
		try {
			PROCESS_NULL_CONDITIONS = SqlConfigParseUtils.class.getDeclaredMethod("processNullConditions",
					SqlToyResult.class);
			PROCESS_NULL_CONDITIONS.setAccessible(true);
		} catch (Exception e) {
			throw new IllegalStateException(e);
		}
	}

	// ============ 固定场景 ============

	@Test
	public void fixedCasesMatchLegacyAlgorithm() throws Exception {
		List<String[]> cases = new ArrayList<String[]>();
		cases.add(new String[] { "select * from t where 1=1 #[and a=?] #[and b=?]", "A|B" });
		cases.add(new String[] { "select * from t where 1=1 #[and a=?] #[and b=?]", "A|<null>" });
		cases.add(new String[] { "select * from t where 1=1 #[and a=?] #[and b=?]", "<null>|B" });
		cases.add(new String[] { "select * from t where 1=1 #[and a=?] #[and b=?]", "<null>|<null>" });
		cases.add(new String[] { "select * from t where #[and a=?]", "<null>" });
		cases.add(new String[] { "select * from t where #[and a=?]", "A" });
		cases.add(new String[] { "select * from t where 1=1 #[or a=?]", "A" });
		cases.add(new String[] { "select * from t where 1=1 and ( #[a=?] or 1=1 )", "<null>" });
		cases.add(new String[] { "select * from t where 1=1 and ( #[a=?] or #[b=?] )", "<null>|<null>" });
		cases.add(new String[] { "select * from t where 1=1 and ( #[a=?] or #[b=?] )", "A|<null>" });
		cases.add(new String[] { "select * from t where 1=1 #[and ( a=? or b=? )]", "A|B" });
		cases.add(new String[] { "select * from t where 1=1 #[and ( a=? or b=? )]", "A|<null>" });
		cases.add(new String[] { "select * from t where 1=1 #[and a=?] order by id", "A" });
		cases.add(new String[] { "select * from t where 1=1 #[and a=?] order by id", "<null>" });
		cases.add(new String[] { "update t set c1=? #[ , c2=?] where id=?", "V1|V2|9" });
		cases.add(new String[] { "select * from t where 1=1 #[and a is null] #[and b=?]", "B" });
		cases.add(new String[] { "select * from t where 1=1 #[and a=?] #[and b is not ?]", "A|<null>" });
		// 边缘形态:CRLF、注释、相邻片段、大小写、union多where、字面量?、空片段、同片段多占位符、空串参数
		cases.add(new String[] { "select * from t where\r\n1\r\n=\r\n1\r\n#[and a=?]", "A" });
		cases.add(new String[] { "select * from t where 1=1 -- where 1=1\n and a=? #[and b=?]", "A|<null>" });
		cases.add(new String[] { "select * from t where 1=1#[and a=?]#[and b=?]", "A|<null>" });
		cases.add(new String[] { "select * from t WHERE 1=1 #[AND a=?]", "A" });
		cases.add(new String[] { "select * from t where 1=1 union all select * from b where 1=1 #[and a=?]", "A" });
		cases.add(new String[] { "select * from t where 1=1 #[] #[and a=?]", "A" });
		cases.add(new String[] { "select * from t where 1=1 #[and a=? and b=?]", "A|<null>" });
		cases.add(new String[] { "select * from t where 1=1 #[and a=? and b=?]", "A|B" });
		cases.add(new String[] { "select * from t where a=? #[and b=?]", "|<null>" });
		for (String[] testCase : cases) {
			assertSameAsLegacy(testCase[0], paramValues(testCase[1]), testCase[0] + " / " + testCase[1]);
		}
	}

	// ============ 随机场景 ============

	@Test
	public void randomCasesMatchLegacyAlgorithm() throws Exception {
		Random random = new Random(20261119L);
		String[] prefixes = { "select * from t where 1=1 ", "select * from t where ", "select * from t where 1=1 and ( " };
		String[] suffixes = { "", " order by id", " group by id", " )", " ) order by id" };
		int compared = 0;
		for (int i = 0; i < 400; i++) {
			int fragmentCount = 1 + random.nextInt(10);
			StringBuilder sql = new StringBuilder(prefixes[random.nextInt(prefixes.length)]);
			Object[] values = new Object[fragmentCount];
			StringBuilder valuesText = new StringBuilder();
			for (int f = 0; f < fragmentCount; f++) {
				boolean isNull = random.nextBoolean();
				String value = isNull ? null : ("v" + f);
				values[f] = value;
				valuesText.append(f == 0 ? "" : "|").append(isNull ? "" : value);
				String connector = random.nextInt(5) == 0 ? "or" : "and";
				sql.append("#[").append(connector).append(" c").append(f).append("=?] ");
			}
			sql.append(suffixes[random.nextInt(suffixes.length)]);
			assertSameAsLegacy(sql.toString(), values, "随机用例" + i + ":" + sql);
			compared++;
		}
		assertEquals(400, compared);
	}

	// ============ 公开入口(NoSQL路径也在用) ============

	/**
	 * processWhereLinkAnd 是public方法,MongoElasticOperations等NoSQL路径也直接调用,
	 * 这里单独做随机差分,保证公开入口的输出与优化前完全一致
	 */
	@Test
	public void processWhereLinkAndMatchesLegacy() {
		String[] preSqls = { "select * from t where", "select * from t where 1=1", "update t set",
				"select * from t", "select * from t where 1=1 and (", "select * from t where 1=1 AND " };
		String[] contents = { " and a=?", " or a=?", " ", "", " , c2=?", " )", " and (a=? or b=?)", "A=?" };
		String[] tails = { "", " order by id", ")", " and b=?", " where x=?", " ) order by 1", " group by id" };
		Random random = new Random(20261120L);
		for (int i = 0; i < 2000; i++) {
			String preSql = preSqls[random.nextInt(preSqls.length)];
			String content = contents[random.nextInt(contents.length)];
			boolean isEndWithAndOr = random.nextBoolean();
			String tail = tails[random.nextInt(tails.length)];
			String expected = legacyProcessWhereLinkAnd(preSql, content, isEndWithAndOr, tail);
			String actual = SqlConfigParseUtils.processWhereLinkAnd(preSql, content, isEndWithAndOr, tail);
			assertEquals(expected, actual,
					"preSql=[" + preSql + "] content=[" + content + "] tail=[" + tail + "] flag=" + isEndWithAndOr);
		}
	}

	// ============ 差分执行 ============

	private void assertSameAsLegacy(String sql, Object[] params, String label) throws Exception {
		SqlToyResult legacy = new SqlToyResult(sql, params == null ? null : params.clone());
		SqlToyResult actual = new SqlToyResult(sql, params == null ? null : params.clone());
		legacyProcessNullConditions(legacy);
		PROCESS_NULL_CONDITIONS.invoke(null, actual);
		assertEquals(legacy.getSql(), actual.getSql(), "[" + label + "] SQL不一致");
		assertParamsEquals(legacy.getParamsValue(), actual.getParamsValue(), label);
	}

	private static void assertParamsEquals(Object[] expected, Object[] actual, String label) {
		int expectedSize = expected == null ? 0 : expected.length;
		int actualSize = actual == null ? 0 : actual.length;
		assertEquals(expectedSize, actualSize, "[" + label + "] 参数数量不一致");
		for (int i = 0; i < expectedSize; i++) {
			assertEquals(expected[i], actual[i], "[" + label + "] 第" + i + "个参数不一致");
		}
	}

	private static Object[] paramValues(String joined) {
		if (joined == null || joined.isEmpty()) {
			return new Object[0];
		}
		String[] parts = joined.split("\\|", -1);
		Object[] values = new Object[parts.length];
		for (int i = 0; i < parts.length; i++) {
			values[i] = "<null>".equals(parts[i]) ? null : parts[i];
		}
		return values;
	}

	// ============ 优化前算法(逐行保留) ============

	private static void legacyProcessNullConditions(SqlToyResult sqlToyResult) {
		String queryStr = sqlToyResult.getSql();
		int pseudoMarkStart = queryStr.indexOf(SqlConfigParseUtils.SQL_PSEUDO_START_MARK);
		if (pseudoMarkStart == -1) {
			return;
		}
		int paramCnt, preParamCnt, beginMarkIndex, endMarkIndex;
		String preSql, markContentSql, tailSql;
		List paramValuesList = CollectionUtil.arrayToList(sqlToyResult.getParamsValue());
		int ifStart;
		int ifLogicSignStart;
		boolean isEndWithAndOr = false;
		int ifLogicCnt = 0;
		boolean isDynamicSql;
		while (pseudoMarkStart != -1) {
			ifLogicCnt = 0;
			isEndWithAndOr = false;
			beginMarkIndex = queryStr.lastIndexOf(SqlConfigParseUtils.SQL_PSEUDO_START_MARK);
			endMarkIndex = StringUtil.getSymMarkIndex(SqlConfigParseUtils.SQL_PSEUDO_SYM_START_MARK,
					SqlConfigParseUtils.SQL_PSEUDO_END_MARK, queryStr, beginMarkIndex);
			if (endMarkIndex == -1) {
				throw new java.util.IllegalFormatFlagsException("missing ]");
			}
			preSql = queryStr.substring(0, beginMarkIndex).concat(SqlConfigParseUtils.BLANK);
			markContentSql = SqlConfigParseUtils.BLANK
					.concat(queryStr.substring(beginMarkIndex + SqlConfigParseUtils.SQL_PSEUDO_START_MARK_LENGTH,
							endMarkIndex))
					.concat(SqlConfigParseUtils.BLANK);
			ifLogicSignStart = StringUtil.matchIndex(markContentSql, SqlConfigParseUtils.IF_ALL_PATTERN);
			ifStart = StringUtil.matchIndex(markContentSql, SqlConfigParseUtils.IF_PATTERN);
			if (ifStart == ifLogicSignStart && ifStart > 0) {
				ifLogicCnt = 1;
			} else if (ifStart == -1 && ifLogicSignStart > 0) {
				int symIfIndex = SqlConfigParseUtils.getStartIfIndex(preSql,
						SqlConfigParseUtils.SQL_PSEUDO_SYM_START_MARK, SqlConfigParseUtils.SQL_PSEUDO_END_MARK);
				beginMarkIndex = queryStr.substring(0, symIfIndex)
						.lastIndexOf(SqlConfigParseUtils.SQL_PSEUDO_START_MARK);
				preSql = queryStr.substring(0, beginMarkIndex).concat(SqlConfigParseUtils.BLANK);
				markContentSql = SqlConfigParseUtils.BLANK
						.concat(queryStr.substring(beginMarkIndex + SqlConfigParseUtils.SQL_PSEUDO_START_MARK_LENGTH,
								endMarkIndex))
						.concat(SqlConfigParseUtils.BLANK);
				ifLogicCnt = StringUtil.matchCnt(markContentSql, SqlConfigParseUtils.IF_ALL_PATTERN);
			}
			tailSql = queryStr.substring(endMarkIndex + SqlConfigParseUtils.SQL_PSEUDO_END_MARK_LENGTH);
			preParamCnt = StringUtil.matchCnt(preSql, SqlConfigParseUtils.ARG_NAME_PATTERN, 0);
			markContentSql = SqlConfigParseUtils.processIfLogic(markContentSql,
					SqlConfigParseUtils.SQL_PSEUDO_START_MARK, SqlConfigParseUtils.SQL_PSEUDO_END_MARK,
					SqlConfigParseUtils.ARG_NAME_PATTERN, paramValuesList, preSql, preParamCnt, ifLogicCnt, 0, 0);
			if (ifLogicCnt == 0) {
				isEndWithAndOr = StringUtil.matches(markContentSql, SqlToyConstants.AND_OR_END);
				paramCnt = StringUtil.matchCnt(markContentSql, SqlConfigParseUtils.ARG_NAME_PATTERN, 0);
				markContentSql = (paramCnt == 0) ? SqlConfigParseUtils.BLANK
						: SqlConfigParseUtils.processMarkContent(markContentSql,
								SqlConfigParseUtils.ARG_NAME_PATTERN, paramValuesList, preParamCnt, paramCnt, true);
			} else {
				isDynamicSql = SqlConfigParseUtils.isDynamicSql(markContentSql,
						SqlConfigParseUtils.SQL_PSEUDO_START_MARK, SqlConfigParseUtils.SQL_PSEUDO_END_MARK);
				if (!isDynamicSql) {
					isEndWithAndOr = StringUtil.matches(markContentSql, SqlToyConstants.AND_OR_END);
					paramCnt = StringUtil.matchCnt(markContentSql, SqlConfigParseUtils.ARG_NAME_PATTERN, 0);
					markContentSql = SqlConfigParseUtils.processMarkContent(markContentSql,
							SqlConfigParseUtils.ARG_NAME_PATTERN, paramValuesList, preParamCnt, paramCnt, true);
				} else {
					String clearSymMarkStr = StringUtil.clearSymMarkContent(markContentSql,
							SqlConfigParseUtils.SQL_PSEUDO_START_MARK, SqlConfigParseUtils.SQL_PSEUDO_END_MARK);
					int clearAfterArgCnt = StringUtil.matchCnt(clearSymMarkStr,
							SqlConfigParseUtils.ARG_NAME_PATTERN, 0);
					if (clearAfterArgCnt > 0) {
						markContentSql = SqlConfigParseUtils.SQL_PSEUDO_START_MARK.concat(markContentSql)
								.concat(SqlConfigParseUtils.SQL_PSEUDO_END_MARK);
					} else {
						isEndWithAndOr = StringUtil.matches(markContentSql, SqlToyConstants.AND_OR_END);
					}
				}
			}
			queryStr = legacyProcessWhereLinkAnd(preSql, markContentSql, isEndWithAndOr, tailSql);
			pseudoMarkStart = queryStr.indexOf(SqlConfigParseUtils.SQL_PSEUDO_START_MARK);
		}
		sqlToyResult.setSql(queryStr);
		sqlToyResult.setParamsValue(paramValuesList.toArray());
	}

	private static String legacyProcessWhereLinkAnd(String preSql, String markContentSql, boolean isEndWithAndOr,
			String tailSql) {
		return legacyCleanDangling(
				legacyProcessWhereLinkAndInternal(preSql, markContentSql, isEndWithAndOr, tailSql));
	}

	private static String legacyProcessWhereLinkAndInternal(String preSql, String markContentSql,
			boolean isEndWithAndOr, String tailSql) {
		String subStr = markContentSql.concat(tailSql);
		String tmp = subStr.trim();
		int index = StringUtil.matchIndex(preSql, SqlConfigParseUtils.WHERE_END_PATTERN);
		if (index >= 0) {
			if ("".equals(tmp)) {
				return preSql.substring(0, index + 1).concat(" ");
			}
			if (StringUtil.matches(tmp, SqlConfigParseUtils.AND_START_PATTERN)) {
				return preSql.concat(" ").concat(subStr.trim().substring(3)).concat(" ");
			} else if (StringUtil.matches(tmp, SqlConfigParseUtils.OR_START_PATTERN)) {
				return preSql.concat(" ").concat(subStr.trim().substring(2)).concat(" ");
			} else if ("".equals(markContentSql.trim())) {
				String tailTrim = tailSql.trim();
				if (tailTrim.startsWith(")")) {
					return preSql.substring(0, index + 1).concat(" ").concat(tailSql).concat(" ");
				} else if (StringUtil.matches(tailTrim.toLowerCase(Locale.ROOT),
						SqlConfigParseUtils.WHERE_CLOSE_PATTERN)) {
					return preSql.substring(0, index + 1).concat(" ").concat(tailSql).concat(" ");
				} else {
					if (isEndWithAndOr) {
						return preSql.concat(" ").concat(tailSql).concat(" ");
					} else {
						return preSql.concat(" 1=1 ").concat(tailSql).concat(" ");
					}
				}
			}
		}
		index = StringUtil.matchIndex(preSql, SqlConfigParseUtils.WHERE_ONE_EQUAL_PATTERN);
		if (index >= 0) {
			if (StringUtil.matches(tmp, SqlConfigParseUtils.AND_START_PATTERN)) {
				return preSql.substring(0, index + 1).concat(" where ").concat(subStr.trim().substring(3))
						.concat(" ");
			} else if (StringUtil.matches(tmp, SqlConfigParseUtils.OR_START_PATTERN)) {
				return preSql.substring(0, index + 1).concat(" where ").concat(subStr.trim().substring(2))
						.concat(" ");
			} else if (tmp.startsWith(")")) {
				return preSql.substring(0, index + 1).concat(subStr).concat(" ");
			} else if (StringUtil.matches(tmp.toLowerCase(Locale.ROOT), SqlConfigParseUtils.WHERE_CLOSE_PATTERN)) {
				return preSql.substring(0, index + 1).concat(subStr).concat(" ");
			} else if (!"".equals(markContentSql.trim())) {
				if (StringUtil.matches(tmp, SqlConfigParseUtils.BLANK_START_PATTERN)) {
					return preSql.concat(" ").concat(subStr).concat(" ");
				} else {
					return preSql.substring(0, index + 1).concat(" where ").concat(subStr).concat(" ");
				}
			}
		}
		if (StringUtil.matches(preSql, SqlConfigParseUtils.UPDATE_SET_PATTERN) && tmp.startsWith(",")) {
			return preSql.concat(" ").concat(subStr.trim().substring(1)).concat(" ");
		}
		return preSql.concat(" ").concat(subStr);
	}

	private static String legacyCleanDangling(String sql) {
		if (StringUtil.isBlank(sql)) {
			return sql;
		}
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
}
