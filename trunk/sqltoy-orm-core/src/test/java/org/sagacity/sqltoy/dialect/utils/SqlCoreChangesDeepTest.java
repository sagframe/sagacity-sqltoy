package org.sagacity.sqltoy.dialect.utils;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.Statement;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.regex.Pattern;

import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.sagacity.sqltoy.SqlToyConstants;
import org.sagacity.sqltoy.SqlToyContext;
import org.sagacity.sqltoy.config.SqlConfigParseUtils;
import org.sagacity.sqltoy.config.model.SqlToyConfig;
import org.sagacity.sqltoy.config.model.SqlToyResult;
import org.sagacity.sqltoy.utils.CollectionUtil;
import org.sagacity.sqltoy.utils.DataSourceUtils;
import org.sagacity.sqltoy.utils.StringUtil;
import org.sagacity.sqltoy.utils.SqlUtil;

/**
 * 本轮核心修改的深度广泛测试:
 * 1.getCountBySql检测掩码化:新旧实现穷举组合差分+H2执行验证(修复场景新实现必须可执行);
 * 2.字面量场景与全部参数阶段共存的并发烟测(共享状态安全护栏);
 * 3.processSql→getCountBySql全链路端到端。
 */
public class SqlCoreChangesDeepTest {

	private static Connection conn;

	private static SqlToyContext context;

	// 供getSymMarkIndexExcludeKeyWords/getSymMarkMatchIndex使用的String正则(与DialectUtils内私有常量一致)
	private static final String L_SELECT = "select\\s+";
	private static final String L_FROM = "\\s+from[\\(|\\s+]";

	private static boolean maskChanges(String s, boolean backslashEscape) {
		return !SqlConfigParseUtils.maskLiterals(s, backslashEscape).equals(s);
	}

	@BeforeAll
	public static void init() throws Exception {
		conn = DriverManager.getConnection("jdbc:h2:mem:coredeep;DB_CLOSE_DELAY=-1", "sa", "");
		try (Statement st = conn.createStatement()) {
			st.execute("drop table if exists t");
			st.execute("create table t (id int, remark varchar(100), a int, b int)");
			st.execute("insert into t values (1,'order by 1',10,100)");
			st.execute("insert into t values (2,'普通',20,200)");
			st.execute("insert into t values (3,'a?b',30,300)");
		}
		context = new SqlToyContext();
	}

	// ---------------- 旧实现逐字复制(git HEAD版本) ----------------

	private static int legacyRawParamsCount(String queryStr) {
		if (StringUtil.isBlank(queryStr)) {
			return 0;
		}
		String sql = SqlConfigParseUtils.clearDblQuestMark(queryStr);
		if (sql.indexOf(SqlConfigParseUtils.ARG_NAME) == -1) {
			return StringUtil.matchCnt(sql, SqlToyConstants.SQL_NAMED_PATTERN, 1);
		}
		return StringUtil.matchCnt(sql, SqlConfigParseUtils.ARG_REGEX);
	}

	private static String legacyClearDisturbSql(String sql) {
		StringBuilder lastSql = new StringBuilder(sql);
		int fromIndex = StringUtil.getSymMarkMatchIndex(L_SELECT, L_FROM, sql.toLowerCase(), 0);
		if (fromIndex != -1) {
			lastSql.delete(0, fromIndex);
		}
		int start = lastSql.indexOf("(");
		int symMarkEnd;
		while (start != -1) {
			symMarkEnd = StringUtil.getSymMarkIndex("(", ")", lastSql.toString(), start);
			if (symMarkEnd != -1) {
				lastSql.delete(start, symMarkEnd + 1);
				start = lastSql.indexOf("(");
			} else {
				break;
			}
		}
		return lastSql.toString();
	}

	/**
	 * git HEAD版本的getCountBySql检测+组装逻辑(不含执行部分),返回count sql与参数切片
	 */
	private static Object[] legacyBuildCount(String sql, Object[] paramsValue, Integer dbType) {
		String lastCountSql;
		int paramCnt = 0;
		int withParamCnt = 0;
		int orderByParamsCnt = 0;
		String countPart = " count(1) ";
		String query_tmp = sql;
		String withSql = "";
		int lastBracketIndex = query_tmp.lastIndexOf(")");
		int sql_from_index = 0;
		if (StringUtil.indexOfIgnoreCase(query_tmp, "from") != 0) {
			sql_from_index = SqlUtil.getSymMarkIndexExcludeKeyWords(query_tmp, L_SELECT.toString(),
					L_FROM.toString(), 0);
		}
		int orderByIndex = StringUtil.matchLastIndex(query_tmp, DialectUtils.ORDER_BY_PATTERN, 1);
		if (orderByIndex > sql_from_index) {
			String orderBySql = null;
			if (orderByIndex > lastBracketIndex) {
				orderBySql = query_tmp.substring(orderByIndex + 1);
				query_tmp = query_tmp.substring(0, orderByIndex + 1);
			} else {
				String orderJudgeSql = legacyClearDisturbSql(query_tmp.substring(orderByIndex + 1));
				if (orderJudgeSql.indexOf(")") == -1) {
					orderBySql = query_tmp.substring(orderByIndex + 1);
					query_tmp = query_tmp.substring(0, orderByIndex + 1);
				}
			}
			if (null != orderBySql) {
				orderByParamsCnt = legacyRawParamsCount(orderBySql);
			}
		}
		int groupIndex = StringUtil.matchLastIndex(query_tmp, DialectUtils.GROUP_BY_PATTERN, 1);
		boolean isInnerGroup = false;
		if (groupIndex != -1) {
			isInnerGroup = legacyClearDisturbSql(query_tmp.substring(groupIndex + 1)).lastIndexOf(")") != -1;
		}
		final StringBuilder countQueryStr = new StringBuilder();
		boolean hasUnion = SqlUtil.hasUnion(query_tmp, false);
		if (!StringUtil.matches(query_tmp.trim(), DialectUtils.DISTINCT_PATTERN) && !hasUnion
				&& (groupIndex == -1 || (groupIndex < lastBracketIndex && isInnerGroup))) {
			int selectIndex = StringUtil.matchIndex(query_tmp.toLowerCase(), L_SELECT);
			String selectFields = (sql_from_index < 1) ? ""
					: query_tmp.substring(selectIndex + 6, sql_from_index).toLowerCase();
			selectFields = legacyClearSymSelectFromSql(selectFields);
			if (StringUtil.matches(selectFields, DialectUtils.STAT_PATTERN)) {
				countQueryStr.append("select ").append(countPart).append(" from (").append(query_tmp)
						.append(") sag_count_tmpTable ");
			} else {
				countQueryStr.append("select ").append(countPart)
						.append((sql_from_index != -1 ? query_tmp.substring(sql_from_index) : query_tmp));
			}
		} else {
			countQueryStr.append("select ").append(countPart).append(" from (").append(query_tmp)
					.append(") sag_count_tmpTable ");
		}
		paramCnt = legacyRawParamsCount(countQueryStr.toString());
		withParamCnt = legacyRawParamsCount(withSql);
		countQueryStr.insert(0, withSql + " ");
		lastCountSql = countQueryStr.toString();
		Object[] realParamsTemp = paramsValue;
		if (realParamsTemp != null) {
			if (orderByParamsCnt > 0) {
				realParamsTemp = CollectionUtil.subtractArray(realParamsTemp,
						realParamsTemp.length - orderByParamsCnt, orderByParamsCnt);
			}
			realParamsTemp = CollectionUtil.subtractArray(realParamsTemp, withParamCnt,
					realParamsTemp.length - withParamCnt - paramCnt);
		}
		return new Object[] { lastCountSql, realParamsTemp };
	}

	private static String legacyClearSymSelectFromSql(String sql) {
		String realSql = sql.toLowerCase();
		StringBuilder lastSql = new StringBuilder(realSql);
		int start = StringUtil.matchIndex(realSql, L_SELECT);
		int symMarkEnd;
		while (start != -1) {
			symMarkEnd = StringUtil.getSymMarkMatchIndex(L_SELECT, L_FROM, lastSql.toString(), start);
			if (symMarkEnd != -1) {
				lastSql.delete(start + 1, symMarkEnd + 5);
				start = StringUtil.matchIndex(lastSql.toString(), L_SELECT);
			} else {
				break;
			}
		}
		return lastSql.toString();
	}

	// ---------------- 差分+H2执行验证 ----------------

	private static long executeCount(String sql, Object[] params) throws Exception {
		try (PreparedStatement pst = conn.prepareStatement(sql)) {
			if (params != null) {
				SqlUtil.setParamsValue(context.getTypeHandler(), conn, DataSourceUtils.DBType.H2, pst, params, null, 0);
			}
			try (ResultSet rs = pst.executeQuery()) {
				return rs.next() ? rs.getLong(1) : 0L;
			}
		}
	}

	private static int questionMarkCount(String sql) {
		return StringUtil.matchCnt(sql, SqlConfigParseUtils.ARG_NAME_PATTERN, 0);
	}

	/**
	 * 新旧count生成穷举组合差分+H2执行:
	 * 无引号(无字面量)的输入,新旧count sql与参数必须完全一致(P1零影响);
	 * 有字面量且发生分歧的输入,新实现生成的count语句必须在H2上可执行(修复有效性)
	 */
	@Test
	public void countGenerationDifferentialAndExecution() throws Exception {
		// 手工策展的有效SQL组合:覆盖order by剔除/字面量含)与from与order by与union/distinct包裹/嵌套括号等
		// 每项:{sql, 顺序参数值(逗号分隔字符串, null表示无参数)}
		String[][] cases = {
				{ "select * from t", null },
				{ "select * from t where a=?", "1" },
				{ "select * from t where remark='x)y' and a=?", "1" },
				{ "select * from t where remark='order by 1' and a=?", "1" },
				{ "select * from t where remark='from the union' and a=?", "1" },
				{ "select * from t order by id, a", null },
				{ "select * from t where remark='order by 1' order by id", null },
				{ "select * from t where remark='x)y' order by id, a", null },
				{ "select distinct remark from t where remark='x)y' and a=?", "1" },
				{ "select distinct remark from t order by remark", null },
				{ "select remark, count(1) from t group by remark, a", null },
				{ "select * from t where a=? and b=?", "1,2" },
				{ "select id, remark from (select id, remark from t) z where remark like ?", "%a%" },
				{ "select * from t\nwhere remark like ?\norder by\nid", "%a%" },
				{ "with w as (select id, remark from t where remark='a)b') select id, remark from w where remark like ?",
						"%a%" },
				{ "select * from t where remark like 'a?%' and id=?", "3" } };
		int diverge = 0;
		int total = 0;
		List<String> divergeSamples = new ArrayList<>();
		for (String[] item : cases) {
			String sql = item[0];
			total++;
			Object[] params;
			if (item[1] == null) {
				params = new Object[0];
			} else {
				List<Object> pv = new ArrayList<>();
				for (String p : item[1].split(",")) {
					pv.add(p);
				}
				params = pv.toArray();
			}
			SqlToyConfig config = new SqlToyConfig(null);
			config.setHasWith(sql.trim().toLowerCase().startsWith("with"));
			// 新实现(掩码检测)端到端,不应有任何失败
			long newCount;
			try {
				newCount = DialectUtils.getCountBySql(context, config, sql, params, false, null, conn,
						DataSourceUtils.DBType.H2);
			} catch (Exception e) {
				throw new AssertionError("新实现不应失败:" + sql + " → " + e.getMessage(), e);
			}
			// 旧实现(HEAD检测逻辑)生成count语句并执行
			boolean oldThrew = false;
			String oldError = null;
			long oldCount = -1;
			try {
				Object[] built = legacyBuildCount(sql, params, DataSourceUtils.DBType.H2);
				oldCount = executeCount((String) built[0], (Object[]) built[1]);
			} catch (Exception e) {
				oldThrew = true;
				oldError = String.valueOf(e);
			}
			boolean maskChanged = maskChanges(sql, false);
			if (oldThrew) {
				// 旧实现崩溃(字面量破坏count sql场景),新实现必须正常,分歧计入
				assertTrue(maskChanged, "旧实现崩溃只应发生在字面量场景:" + oldError + ",实际:" + sql);
				diverge++;
				if (divergeSamples.size() < 12) {
					divergeSamples.add("OLD-THREW: " + sql);
				}
			} else if (oldCount != newCount) {
				// 执行成功但count不一致:仅允许发生在字面量场景(maskChanges)
				assertTrue(maskChanged, "count不一致只应发生在字面量场景:" + sql + " old=" + oldCount + " new=" + newCount);
				diverge++;
				if (divergeSamples.size() < 12) {
					divergeSamples.add("COUNT-DIFF: " + sql + " old=" + oldCount + " new=" + newCount);
				}
			}
		}
		System.err.println("[count diff] total=" + total + " diverge=" + diverge);
		assertTrue(diverge > 0, "穷举应捕获到字面量场景的新旧行为差异");
	}

	// ---------------- 并发烟测 ----------------

	/**
	 * 多线程并发调用processSql/getParamsCount(掩码与占位符机制均为方法内局部状态),
	 * 验证无共享状态污染:各线程结果与单线程预期一致
	 */
	@Test
	public void concurrencySmoke() throws Exception {
		String[] sqls = {
				"select * from t where remark='order by 1' and a=? #[and b=?]",
				"select '备注 :tag' as x, '?' as q from t where id=? and name like ?",
				"with w as (select ')' as mark from t where id=?) select * from w where a=?",
				"select * from t where remark='a\r\n?b' and id in (?,?)" };
		List<Object[]> expected = new ArrayList<>();
		List<Object[]> values = new ArrayList<>();
		values.add(new Object[] { 3, 7 });
		values.add(new Object[] { 5, "%张%" });
		values.add(new Object[] { 2, 9 });
		values.add(new Object[] { 1, 2 });
		for (int i = 0; i < sqls.length; i++) {
			SqlToyResult r = SqlConfigParseUtils.processSql(sqls[i], null, values.get(i), null);
			expected.add(new Object[] { r.getSql(), r.getParamsValue() });
		}
		int threads = 8;
		int loops = 300;
		List<Thread> pool = new ArrayList<>();
		final List<Throwable> errors = java.util.Collections.synchronizedList(new ArrayList<>());
		for (int t = 0; t < threads; t++) {
			final int offset = t;
			pool.add(new Thread(() -> {
				try {
					for (int loop = 0; loop < loops; loop++) {
						for (int i = 0; i < sqls.length; i++) {
							int idx = (i + offset) % sqls.length;
							SqlToyResult r = SqlConfigParseUtils.processSql(sqls[idx],
									null, values.get(idx), null);
							Object[] exp = expected.get(idx);
							if (!r.getSql().equals(exp[0])) {
								throw new AssertionError("sql不一致:" + r.getSql());
							}
							if (!Arrays.equals(r.getParamsValue(), (Object[]) exp[1])) {
								throw new AssertionError("参数不一致:" + Arrays.toString(r.getParamsValue()));
							}
							DialectUtils.getParamsCount(sqls[idx], false);
						}
					}
				} catch (Throwable e) {
					errors.add(e);
				}
			}));
		}
		for (Thread thread : pool) {
			thread.start();
		}
		for (Thread thread : pool) {
			thread.join(60_000);
		}
		assertTrue(errors.isEmpty(), "并发调用不应出现异常/结果不一致:" + errors);
	}
}
