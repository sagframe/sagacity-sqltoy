package org.sagacity.sqltoy.config;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

import org.junit.jupiter.api.Disabled;
import org.junit.jupiter.api.Test;
import org.sagacity.sqltoy.SqlToyConstants;
import org.sagacity.sqltoy.config.model.SqlParamsModel;
import org.sagacity.sqltoy.config.model.SqlToyResult;
import org.sagacity.sqltoy.dialect.utils.DialectUtils;
import org.sagacity.sqltoy.utils.DataSourceUtils;
import org.sagacity.sqltoy.utils.SqlUtil;
import org.sagacity.sqltoy.utils.StringUtil;

/**
 * 字面量感知(掩码)修复的穷举差分验证:
 * 对本轮修改的processNamedParamsQuery、processLike(经processSql端到端)、
 * DialectUtils.getParamsCount,将git HEAD旧实现逐字复制为legacy方法,
 * 在含引号/冒号/问号/反斜杠的穷举语料上对照,验证:
 * P1(零影响):掩码与原串一致的输入(无字面量内容),新旧结果完全一致;
 * P2(变更面):所有分歧输入必然存在字面量内容(maskLiterals结果 != 原串)。
 */
public class SqlConfigParseMaskDifferentialTest {

	private static final char[] ALPHABET = { 'a', ':', '\'', '?', '\\', ' ' };

	private static final org.slf4j.Logger logger = org.slf4j.LoggerFactory
			.getLogger(SqlConfigParseMaskDifferentialTest.class);

	// ---------------- 旧实现逐字复制(git HEAD版本) ----------------

	private static SqlParamsModel legacyProcessNamedParamsQuery(String queryStr) {
		SqlParamsModel sqlParam = new SqlParamsModel();
		sqlParam.setSql(queryStr);
		java.util.regex.Matcher m = SqlToyConstants.SQL_NAMED_PATTERN.matcher(queryStr);
		List<String> paramsName = new ArrayList<String>();
		StringBuilder lastSql = new StringBuilder();
		int start = 0;
		String group;
		while (m.find(start)) {
			group = m.group();
			paramsName.add(group.substring(2).trim());
			lastSql.append(queryStr.substring(start, m.start() + 1)).append(SqlConfigParseUtils.ARG_NAME);
			if (StringUtil.matches(group, SqlToyConstants.BLANK_END)) {
				start = m.end() - 1;
			} else {
				start = m.end();
			}
		}
		if (start == 0) {
			return sqlParam;
		}
		lastSql.append(queryStr.substring(start));
		sqlParam.setSql(lastSql.toString());
		sqlParam.setParamsName(paramsName.toArray(new String[0]));
		return sqlParam;
	}

	private static SqlToyResult legacyProcessLike(SqlToyResult sqlToyResult, String dialect) {
		if (null == sqlToyResult.getParamsValue() || sqlToyResult.getParamsValue().length == 0) {
			return sqlToyResult;
		}
		int dbType = DataSourceUtilsHolder.getDBType(dialect);
		String queryStr = sqlToyResult.getSql();
		java.util.regex.Matcher m = SqlConfigParseUtils.LIKE_PATTERN.matcher(queryStr);
		int paramCnt = 0;
		String likeValStr;
		StringBuilder sqlBuilder = null;
		int lastEnd = 0;
		boolean isBackslashEscape;
		if (SqlToyConstants.backslashEscaping != null) {
			isBackslashEscape = SqlToyConstants.backslashEscaping;
		} else {
			isBackslashEscape = SqlConfigParseUtils.isBackslashEscapeDialect(dbType);
		}
		String escapeClause = isBackslashEscape ? " ESCAPE '\\\\'" : " ESCAPE '\\'";
		boolean supportEscapeClause = dbType != DBTypeHolder.CLICKHOUSE && dbType != DBTypeHolder.IMPALA
				&& dbType != DBTypeHolder.TDENGINE;
		while (m.find()) {
			paramCnt = StringUtil.matchCnt(queryStr.substring(0, m.start()), SqlConfigParseUtils.ARG_NAME_PATTERN, 0);
			likeValStr = (sqlToyResult.getParamsValue()[paramCnt] == null) ? null
					: sqlToyResult.getParamsValue()[paramCnt].toString();
			if (null == likeValStr) {
				continue;
			}
			String stripped = likeValStr.replace("\\%", "");
			boolean needEscape = false;
			if (stripped.indexOf("%") != -1) {
				String escaped = SqlUtil.escapeLikeValue(likeValStr, dbType, false);
				sqlToyResult.getParamsValue()[paramCnt] = escaped;
				needEscape = escaped.contains("\\");
			} else {
				String escaped = SqlUtil.escapeLikeValue(likeValStr, dbType, true);
				sqlToyResult.getParamsValue()[paramCnt] = "%".concat(escaped).concat("%");
				needEscape = escaped.contains("\\");
			}
			String tailAfterMatch = queryStr.substring(m.end()).trim().toLowerCase();
			if (needEscape && supportEscapeClause && !tailAfterMatch.startsWith("escape")) {
				if (sqlBuilder == null) {
					sqlBuilder = new StringBuilder();
				}
				sqlBuilder.append(queryStr.substring(lastEnd, m.end()));
				sqlBuilder.append(escapeClause);
				lastEnd = m.end();
			}
		}
		if (sqlBuilder != null) {
			sqlBuilder.append(queryStr.substring(lastEnd));
			sqlToyResult.setSql(sqlBuilder.toString());
		}
		return sqlToyResult;
	}

	private static int legacyGetParamsCount(String queryStr) {
		if (StringUtil.isBlank(queryStr)) {
			return 0;
		}
		String sql = SqlConfigParseUtils.clearDblQuestMark(queryStr);
		if (sql.indexOf(SqlConfigParseUtils.ARG_NAME) == -1) {
			return StringUtil.matchCnt(sql, SqlToyConstants.SQL_NAMED_PATTERN, 1);
		}
		return StringUtil.matchCnt(sql, SqlConfigParseUtils.ARG_REGEX);
	}

	/** DataSourceUtils.getDBType的静态内联(避免测试类持有方言工具的额外耦合) */
	private static class DataSourceUtilsHolder {
		static int getDBType(String dialect) {
			return DataSourceUtils.getDBType(dialect);
		}
	}

	private static class DBTypeHolder {
		static final int CLICKHOUSE = DataSourceUtils.DBType.CLICKHOUSE;
		static final int IMPALA = DataSourceUtils.DBType.IMPALA;
		static final int TDENGINE = DataSourceUtils.DBType.TDENGINE;
	}

	// ---------------- 语料生成 ----------------

	private static List<String> corpus(char[] alphabet, int maxLen) {
		List<String> result = new ArrayList<>();
		for (int len = 0; len <= maxLen; len++) {
			int combos = (int) Math.pow(alphabet.length, len);
			for (int code = 0; code < combos; code++) {
				char[] chars = new char[len];
				int v = code;
				for (int i = 0; i < len; i++) {
					chars[i] = alphabet[v % alphabet.length];
					v /= alphabet.length;
				}
				result.add(new String(chars));
			}
		}
		return result;
	}

	private static boolean maskChanges(String s, boolean backslashEscape) {
		return !SqlConfigParseUtils.maskLiterals(s, backslashEscape).equals(s);
	}

	// ---------------- 差分性质 ----------------

	/**
	 * P1+P2:processNamedParamsQuery新旧对照——无字面量内容的输入零变化,
	 * 分歧仅出现在掩码改变原串的输入(字面量内含:xxx等参数形态)
	 */
	@Test
	public void namedParamsQueryDifferential() {
		int divergeFalse = 0;
		int divergeTrue = 0;
		for (String s : corpus(ALPHABET, 5)) {
			SqlParamsModel oldR = legacyProcessNamedParamsQuery(s);
			SqlParamsModel newFalse = SqlConfigParseUtils.processNamedParamsQuery(s, false);
			SqlParamsModel newTrue = SqlConfigParseUtils.processNamedParamsQuery(s, true);
			boolean maskFalse = maskChanges(s, false);
			boolean maskTrue = maskChanges(s, true);
			if (!oldR.getSql().equals(newFalse.getSql())
					|| !Arrays.equals(oldR.getParamsName(), newFalse.getParamsName())) {
				divergeFalse++;
				assertTrue(maskFalse, "分歧输入必须存在字面量内容,实际:" + s);
			}
			if (!oldR.getSql().equals(newTrue.getSql())
					|| !Arrays.equals(oldR.getParamsName(), newTrue.getParamsName())) {
				divergeTrue++;
				assertTrue(maskTrue, "分歧输入必须存在字面量内容(\\'规则),实际:" + s);
			}
			// 掩码不改变原串时,新实现必须与旧实现完全一致(P1)
			if (!maskFalse) {
				assertEquals(oldR.getSql(), newFalse.getSql(), "无字面量内容输入必须零变化:" + s);
				assertArrayEquals(oldR.getParamsName(), newFalse.getParamsName(), "无字面量内容输入必须零变化:" + s);
			}
		}
		logger.info("namedParamsQuery differential divergeFalse={} divergeTrue={}", divergeFalse, divergeTrue);
		System.err.println("[named diff] divergeFalse=" + divergeFalse + " divergeTrue=" + divergeTrue);
	}

	/**
	 * P1+P2:getParamsCount新旧对照
	 */
	@Test
	public void getParamsCountDifferential() {
		int diverge = 0;
		for (String s : corpus(ALPHABET, 5)) {
			int oldR = legacyGetParamsCount(s);
			int newR = DialectUtils.getParamsCount(s, false);
			if (oldR != newR) {
				diverge++;
				assertTrue(maskChanges(s, false), "分歧输入必须存在字面量内容,实际:" + s);
			}
			if (!maskChanges(s, false)) {
				assertEquals(oldR, newR, "无字面量内容输入必须零变化:" + s);
			}
		}
		logger.info("getParamsCount differential diverge={}", diverge);
		System.err.println("[count diff] diverge=" + diverge);
	}

	/**
	 * P1+P2:processLike(经processSql端到端驱动)新旧对照——
	 * 语料为仅含like条件的sql(此时processSql其余阶段均为空操作,端到端输出即processLike输出)
	 */
	@Test
	public void processLikeDifferential() {
		char[] sub = { 'a', '\'', '?', ' ' };
		List<String> sides = corpus(sub, 3);
		int diverge = 0;
		int total = 0;
		for (String x : sides) {
			for (String y : sides) {
				for (String tpl : new String[] { "{X} like ?{Y}", "select * from t where {X} like ? and b like ?{Y}" }) {
					String sql = tpl.replace("{X}", x).replace("{Y}", y);
					// ??是sqltoy双占位符特殊语法,走独立预处理(DBL_QUESTMARK),不在本差分范围
					if (sql.contains("??")) {
						continue;
					}
					total++;
					Object[] params = { "50", "ab" };
					// 旧实现遇字面量?会数组越界(正是修复的缺陷);参数数量不匹配的垃圾输入新旧均可能异常
					SqlToyResult oldR = null;
					boolean oldThrew = false;
					try {
						oldR = legacyProcessLike(new SqlToyResult(sql, params), null);
					} catch (Exception e) {
						oldThrew = true;
					}
					SqlToyResult newR = null;
					boolean newThrew = false;
					try {
						newR = SqlConfigParseUtils.processSql(sql, null, new Object[] { "50", "ab" }, null);
					} catch (Exception e) {
						newThrew = true;
					}
					// 任一方异常:双方一致异常(如参数数量不匹配的垃圾输入)属等价行为;
					// 单侧异常只允许发生在存在字面量内容的输入上
					if (oldThrew || newThrew) {
						if (oldThrew != newThrew) {
							assertTrue(maskChanges(sql, false),
									"单侧异常只允许发生在字面量场景,实际:" + sql);
						}
						if (maskChanges(sql, false)) {
							diverge++;
						}
						continue;
					}
					// 端到端processSql相对纯processLike存在空白级无害差异,sql按trim对比
					boolean same = oldR.getSql().trim().equals(newR.getSql().trim())
							&& Arrays.equals(oldR.getParamsValue(), newR.getParamsValue());
					if (!same) {
						diverge++;
						assertTrue(maskChanges(sql, false),
								"分歧输入必须存在字面量内容,实际:" + sql + " old=" + oldR.getSql() + "/"
										+ Arrays.toString(oldR.getParamsValue()) + " new=" + newR.getSql() + "/"
										+ Arrays.toString(newR.getParamsValue()));
					}
					// 无字面量内容时,参数值必须与旧实现完全一致
					if (!maskChanges(sql, false)) {
						assertArrayEquals(oldR.getParamsValue(), newR.getParamsValue(), "无字面量输入参数必须零变化:" + sql);
					}
				}
			}
		}
		logger.info("processLike differential total={} diverge={}", total, diverge);
		System.err.println("[like diff] total=" + total + " diverge=" + diverge);
	}

	/**
	 * 回归测试:位置参数模式下#[]片段的参数计数具备字面量感知。
	 * 修复前字面量'a?'中的?被计入preParamCnt,片段按错误切片读取参数导致
	 * IndexOutOfBoundsException(processNullConditions)。
	 */
	@Test
	public void positionalPseudoWithLiteralQuestionMark() {
		SqlToyResult r = SqlConfigParseUtils.processSql(
				"select * from t where remark='a?' and b=? #[and c=?]", null,
				new Object[] { "v1", "v2" }, null);
		assertArrayEquals(new Object[] { "v1", "v2" }, r.getParamsValue());
		assertTrue(r.getSql().contains("'a?'"), "字面量应保真,实际:" + r.getSql());
		assertTrue(r.getSql().contains("c=?"), "片段应保留,实际:" + r.getSql());
	}
}
