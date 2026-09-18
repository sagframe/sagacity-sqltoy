package org.sagacity.sqltoy.dialect.utils;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.Statement;
import java.util.ArrayList;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.sagacity.sqltoy.SqlToyConstants;
import org.sagacity.sqltoy.SqlToyContext;
import org.sagacity.sqltoy.config.SqlConfigParseUtils;
import org.sagacity.sqltoy.config.model.SqlToyConfig;
import org.sagacity.sqltoy.config.model.SqlType;
import org.sagacity.sqltoy.config.model.SqlWithAnalysis;
import org.sagacity.sqltoy.dialect.DialectFactory;
import org.sagacity.sqltoy.model.LockMode;
import org.sagacity.sqltoy.utils.DataSourceUtils;
import org.sagacity.sqltoy.utils.StringUtil;
import org.sagacity.sqltoy.utils.SqlUtil;

/**
 * 字面量掩码化改动的全影响面测试:
 * 1.差分护栏:无字面量sql上新旧实现(git HEAD版本)结果必须完全一致(证明常规路径零影响);
 * 2.参考实现交叉:含字面量sql上新实现必须与独立编写的引号感知扫描器一致(证明修复正确);
 * 3.调用点专项:lockSql/hasOrderByOrUnion/isComplexPageQuery/parseSqlToyConfig配置标记;
 * 4.H2端到端:getCountBySql与union-all-count装配(复刻DialectFactory逻辑)执行结果验证;
 * 5.maskLiterals幂等性:已掩码入参二次掩码必须原样(双路径叠加调用的安全前提)
 */
public class SqlAnalyzeImpactBroadTest {

	private static Connection conn;

	private static SqlToyContext context;

	// 与DialectUtils内私有常量一致
	private static final String SELECT_REGEX = "select\\s+";
	private static final String FROM_REGEX = "\\s+from[\\(\\s+]";
	private static final String WHERE_REGEX = "\\s+where[\\(\\s+]";

	@BeforeAll
	public static void init() throws Exception {
		conn = DriverManager.getConnection("jdbc:h2:mem:impactbroad;DB_CLOSE_DELAY=-1", "sa", "");
		try (Statement st = conn.createStatement()) {
			st.execute("drop table if exists t1");
			st.execute("drop table if exists t2");
			st.execute("create table t1 (id int, remark varchar(100), a int)");
			st.execute("insert into t1 values (1,'x',10)");
			st.execute("insert into t1 values (2,'y',20)");
			st.execute("insert into t1 values (3,' union all ',30)");
			st.execute("create table t2 (id int, a int)");
			st.execute("insert into t2 values (11,110)");
			st.execute("insert into t2 values (12,120)");
		}
		context = new SqlToyContext();
	}

	// ---------------- git HEAD版本旧实现(差分基线,逐字复制) ----------------

	private static int legacyGetSymMarkIndex(String sql, String startRegex, String endRegex, int startIndex) {
		Pattern endPattern = Pattern.compile(endRegex);
		String sqlLow = sql.toLowerCase();
		int startRegexIndex = StringUtil.matchIndex(sqlLow, startRegex, startIndex)[0];
		int endRegIndex = StringUtil.getSymMarkMatchIndex(startRegex, endRegex, sqlLow, startIndex);
		if (endRegIndex > 0
				&& StringUtil.matchCnt(startIndex == 0 ? sqlLow : sqlLow.substring(startIndex), endPattern) == 1) {
			return endRegIndex;
		}
		String startMark = "(", endMark = ")";
		int startBreaket = sqlLow.indexOf(startMark, startRegexIndex);
		if (startBreaket < endRegIndex && startBreaket > 0) {
			int start = startBreaket;
			int symMarkEnd;
			String tail;
			while (start != -1) {
				symMarkEnd = StringUtil.getSymMarkIndex(startMark, endMark, sqlLow, start);
				if (symMarkEnd != -1) {
					tail = sqlLow.substring(symMarkEnd);
					sqlLow = sqlLow.substring(0, start) + sqlLow.substring(start, symMarkEnd).replace("from", "AAAA")
							.replace("select", "AAAAAA").replace("where", "AAAAA") + tail;
					if (!StringUtil.matches(tail, endPattern)) {
						break;
					}
					start = sqlLow.indexOf(startMark, symMarkEnd);
				} else {
					break;
				}
			}
			int lastEndRegIndex = StringUtil.getSymMarkMatchIndex(startRegex, endRegex, sqlLow, startIndex);
			if (lastEndRegIndex == -1) {
				return endRegIndex;
			}
			return lastEndRegIndex;
		}
		return endRegIndex;
	}

	private static boolean legacyHasUnion(String sql, boolean clearMistyChar) {
		if (!StringUtil.matches(sql, SqlUtil.UNION_PATTERN)) {
			return false;
		}
		if (StringUtil.matches(SqlUtil.BLANK + sql, SqlToyConstants.withPattern)) {
			SqlWithAnalysis sqlWith = new SqlWithAnalysis(sql);
			sql = sqlWith.getRejectWithSql();
		}
		String tmpSql = SqlUtil.BLANK + (clearMistyChar ? SqlUtil.clearMistyChars(sql, SqlUtil.BLANK) : sql);
		StringBuilder lastSql = new StringBuilder(tmpSql);
		int fromIndex = StringUtil.getSymMarkMatchIndex(SqlUtil.SELECT_REGEX, SqlUtil.FROM_REGEX,
				tmpSql.toLowerCase(), 0);
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
		return StringUtil.matches(lastSql.toString(), SqlUtil.UNION_PATTERN);
	}

	private static String legacyClearDisturbSql(String sql) {
		StringBuilder lastSql = new StringBuilder(sql);
		int fromIndex = StringUtil.getSymMarkMatchIndex(SELECT_REGEX, FROM_REGEX, sql.toLowerCase(), 0);
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
	 * git HEAD版本union-all-count装配(原始串match+split),仅用于无字面量场景差分
	 */
	private static String legacyUnionAllCountSql(String rejectWithSql, String withSql, String countPart) {
		if (!StringUtil.matches(rejectWithSql, SqlToyConstants.UNION_ALL_REGEX)) {
			return null;
		}
		String[] unionSqls = rejectWithSql.split(SqlToyConstants.UNION_ALL_REGEX);
		StringBuilder countSql = new StringBuilder();
		countSql.append(withSql);
		countSql.append(" select sum(row_count) from (");
		int sql_from_index;
		for (int i = 0; i < unionSqls.length; i++) {
			sql_from_index = SqlUtil.getSymMarkIndexExcludeKeyWords(unionSqls[i], SELECT_REGEX, FROM_REGEX, 0);
			countSql.append(" select ").append(countPart).append(" row_count ")
					.append((sql_from_index != -1 ? unionSqls[i].substring(sql_from_index) : unionSqls[i]));
			if (i < unionSqls.length - 1) {
				countSql.append(" union all ");
			}
		}
		countSql.append(" ) ");
		return countSql.toString();
	}

	/**
	 * 新版union-all-count装配(复刻DialectFactory修改后逻辑):掩码串定位拆分点,按位置切原串;
	 * 分支边界以union/all关键词为基准向两侧收缩到非空白为止
	 */
	private static String newUnionAllCountSql(String rejectWithSql, String withSql, String countPart,
			boolean backslashEscape) {
		String maskedRejectSql = SqlConfigParseUtils.maskLiterals(rejectWithSql, backslashEscape);
		if (!StringUtil.matches(maskedRejectSql, SqlToyConstants.UNION_ALL_REGEX)) {
			return null;
		}
		Matcher unionAllMatcher = Pattern.compile(SqlToyConstants.UNION_ALL_REGEX).matcher(maskedRejectSql);
		List<String> unionSqls = new ArrayList<>();
		int splitStart = 0;
		while (unionAllMatcher.find()) {
			// 关键词定位基于匹配组内的小写偏移,兼容UNION ALL大写形态
			String matchGroup = unionAllMatcher.group().toLowerCase();
			int unionPos = unionAllMatcher.start() + matchGroup.indexOf("union");
			int allEnd = unionAllMatcher.start() + matchGroup.indexOf("all", matchGroup.indexOf("union")) + 3;
			int begin = unionPos;
			while (begin > splitStart && Character.isWhitespace(maskedRejectSql.charAt(begin - 1))) {
				begin--;
			}
			int end = allEnd;
			while (end < maskedRejectSql.length() && Character.isWhitespace(maskedRejectSql.charAt(end))) {
				end++;
			}
			unionSqls.add(rejectWithSql.substring(splitStart, begin));
			splitStart = end;
		}
		unionSqls.add(rejectWithSql.substring(splitStart));
		// 剔除最后一个分支携带的外层order by(与DialectFactory保持一致)
		String lastBranch = unionSqls.get(unionSqls.size() - 1);
		String maskedLastBranch = SqlConfigParseUtils.maskLiterals(lastBranch, backslashEscape);
		int lastOrderByIndex = StringUtil.matchLastIndex(maskedLastBranch, DialectUtils.ORDER_BY_PATTERN, 1);
		if (lastOrderByIndex > -1 && maskedLastBranch.lastIndexOf(")") < lastOrderByIndex) {
			unionSqls.set(unionSqls.size() - 1, lastBranch.substring(0, lastOrderByIndex + 1));
		}
		StringBuilder countSql = new StringBuilder();
		countSql.append(withSql);
		countSql.append(" select sum(row_count) from (");
		int sql_from_index;
		for (int i = 0; i < unionSqls.size(); i++) {
			String branch = unionSqls.get(i);
			// distinct分支回退为整体包裹计数(与DialectFactory保持一致)
			if (StringUtil.matches(SqlConfigParseUtils.maskLiterals(branch, backslashEscape).trim(),
					DialectUtils.DISTINCT_PATTERN)) {
				countSql.append(" select ").append(countPart).append(" row_count from (").append(branch)
						.append(") sag_union_count_").append(i).append(" ");
			} else {
				sql_from_index = SqlUtil.getSymMarkIndexExcludeKeyWords(branch, SELECT_REGEX, FROM_REGEX, 0);
				countSql.append(" select ").append(countPart).append(" row_count ")
						.append((sql_from_index != -1 ? branch.substring(sql_from_index) : branch));
			}
			if (i < unionSqls.size() - 1) {
				countSql.append(" union all ");
			}
		}
		countSql.append(" ) ");
		return countSql.toString();
	}

	/**
	 * 独立参考实现:逐字符扫描,跳过'...'字面量(''成对转义),返回第一个真实顶层关键词前空白段起点
	 * (与\\s+keyword[\\(\\s+]匹配语义对齐)
	 */
	private static int refTopLevelKeywordIndex(String sql, String keyword) {
		String low = sql.toLowerCase();
		int n = low.length();
		boolean inString = false;
		int depth = 0;
		// 跳过开头的select词
		int i = low.indexOf("select") + 6;
		for (; i < n; i++) {
			char c = low.charAt(i);
			if (inString) {
				if (c == '\'') {
					if (i + 1 < n && low.charAt(i + 1) == '\'') {
						i++;
					} else {
						inString = false;
					}
				}
				continue;
			}
			if (c == '\'') {
				inString = true;
				continue;
			}
			if (c == '(') {
				depth++;
				continue;
			}
			if (c == ')') {
				depth--;
				continue;
			}
			if (depth == 0 && low.startsWith(keyword, i)) {
				boolean wordBefore = (i == 0) || Character.isWhitespace(low.charAt(i - 1));
				char after = (i + keyword.length() < n) ? low.charAt(i + keyword.length()) : ' ';
				if (wordBefore && (Character.isWhitespace(after) || after == '(')) {
					int s = i;
					while (s > 0 && Character.isWhitespace(low.charAt(s - 1))) {
						s--;
					}
					return s;
				}
			}
		}
		return -1;
	}

	private static long countByJdbc(String sql) throws Exception {
		try (PreparedStatement pst = conn.prepareStatement(sql); ResultSet rs = pst.executeQuery()) {
			rs.next();
			return rs.getLong(1);
		}
	}

	// ---------------- 1.差分护栏:无字面量场景新旧必须一致 ----------------

	/** 无字面量sql全形态语料:select/from/where定位、hasUnion、clearDisturbSql新旧必须逐一相等 */
	@Test
	public void differentialNoLiteral() {
		String[] corpus = {
				// 简单查询
				"select a, b from t1 where a=1",
				// 子查询嵌套各形态
				"select a from (select b from t2) x",
				"select (select a from t2) b from t1",
				"select (select a from t2) , (select b from t3) y from t1",
				"select a from (select b from (select c from t3) y) x",
				// 既有测试场景
				"select sum(a),(day from(ab)) as b from (select * from tableb) as b where (a+b)>5 and c.a>(1+5)",
				"select sum(a),(day xxx(ab)) as b from (select * from tableb) as b where (a+b)>5 and c.a>(1+5)",
				"select a, b from (select * from tableb where 1=1 and t.m>100) as b where (a+b)>5 and c.a>(1+5)",
				// union形态
				"select a from t1 union select b from t2",
				"select a from t1 union all select b from t2",
				"select * from (select a from t1 union all select b from t2) x",
				// join与多表
				"select a from t1 left join t2 on t1.id=t2.id where a>1",
				"select a from t1 , t2 where a>1",
				// 聚合分组排序
				"select a , count(1) from t1 group by a order by a desc",
				"select distinct a from t1 order by a",
				// where括号与函数括号
				"select a from t1 where (a=1 or b=2) and c=3",
				"select concat(a,b) , trim(c) from t1",
				"select a from t1 where exists (select 1 from t2 where t2.id=t1.id)",
				// case when
				"select case when a=1 then b else c end from t1",
				// 大小写与换行
				"SELECT A FROM T1 WHERE A=1",
				"select a\n  from t1\n  where a=1",
				// 无where结尾
				"select a from t1 order by id" };
		for (String sql : corpus) {
			assertEquals(legacyGetSymMarkIndex(sql, SELECT_REGEX, FROM_REGEX, 0),
					SqlUtil.getSymMarkIndexExcludeKeyWords(sql, SELECT_REGEX, FROM_REGEX, 0),
					"select...from定位差分不一致: " + sql);
			assertEquals(legacyGetSymMarkIndex(sql, FROM_REGEX, WHERE_REGEX, 0),
					SqlUtil.getSymMarkIndexExcludeKeyWords(sql, FROM_REGEX, WHERE_REGEX, 0),
					"from...where定位差分不一致: " + sql);
			assertEquals(legacyHasUnion(sql, false), SqlUtil.hasUnion(sql, false), "hasUnion差分不一致: " + sql);
			assertEquals(legacyHasUnion(sql, true), SqlUtil.hasUnion(sql, true), "hasUnion(clear)差分不一致: " + sql);
			assertEquals(legacyClearDisturbSql(sql), DialectUtils.clearDisturbSql(sql),
					"clearDisturbSql差分不一致: " + sql);
			// 无字面量场景新实现同时必须与参考实现一致(三层交叉)
			assertEquals(refTopLevelKeywordIndex(sql, "from"),
					SqlUtil.getSymMarkIndexExcludeKeyWords(sql, SELECT_REGEX, FROM_REGEX, 0),
					"select...from定位与参考实现不一致: " + sql);
			// 新旧union-all-count装配必须一致(小写场景新旧正则等价)
			assertEquals(legacyUnionAllCountSql(sql, "", " count(1) "),
					newUnionAllCountSql(sql, "", " count(1) ", false), "union-all-count装配差分不一致: " + sql);
		}
		// from...where起始位置变体(复刻SqlUtilTest.testGetFromIndex1调用形态)
		String sql = "select a, b from (select * from tableb where 1=1 and t.m>100) as b where (a+b)>5 and c.a>(1+5)";
		int fromIndex = SqlUtil.getSymMarkIndexExcludeKeyWords(sql, SELECT_REGEX, FROM_REGEX, 0);
		assertEquals(legacyGetSymMarkIndex(sql, FROM_REGEX, WHERE_REGEX, fromIndex - 1),
				SqlUtil.getSymMarkIndexExcludeKeyWords(sql, FROM_REGEX, WHERE_REGEX, fromIndex - 1),
				"startIndex变体差分不一致");
	}

	// ---------------- 2.字面量场景:新实现必须与参考实现一致 ----------------

	@Test
	public void literalSqlMatchesReference() {
		String[] corpus = {
				"select a , ' from (' from t1",
				"select concat(a,'(') , (select b from t2) y from t1",
				"select a , (select b from t2) , (select c from t3) y , '(' from t1 where z=')'",
				"select a , 'it''s from (' from t1",
				"Select A , ' From (' From T1",
				"select (select a from t2 where b=' from ') c from t1",
				"select a from t1 where x='(' and y in (select c from t2)",
				"select 'select from where' as c , a from (select b from t2) x",
				"select a ,\n ' from (' from\n t1",
				// 字面量内含where:from定位不受影响,where定位须跳过字面量
				"select a from t1 where b=' where 1=1 ' and c=2" };
		for (String sql : corpus) {
			assertEquals(refTopLevelKeywordIndex(sql, "from"),
					SqlUtil.getSymMarkIndexExcludeKeyWords(sql, SELECT_REGEX, FROM_REGEX, 0),
					"字面量场景from定位错误: " + sql);
		}
		// where定位:字面量内的where不得参与
		String sql = "select a from t1 where b=' where 1=1 ' and c=2";
		int fromIndex = SqlUtil.getSymMarkIndexExcludeKeyWords(sql, SELECT_REGEX, FROM_REGEX, 0);
		assertEquals(refTopLevelKeywordIndex(sql, "where"),
				SqlUtil.getSymMarkIndexExcludeKeyWords(sql, FROM_REGEX, WHERE_REGEX, fromIndex - 1),
				"字面量内where被误当真实where");
	}

	// ---------------- 3.调用点专项 ----------------

	/** lockSql(sqlserver锁查询):字面量'('与' from ('不得使from定位错位导致锁提示注入点错乱 */
	@Test
	public void lockSqlWithLiteral() {
		// 别名形态
		String sql = "select a , ' from (' as c from orders o where o.id=1";
		String locked = SqlServerDialectUtils.lockSql(sql, null, LockMode.UPGRADE);
		assertTrue(locked.contains("orders o with (rowlock xlock)"), "锁提示应注入别名之后: " + locked);
		assertTrue(locked.contains("' from ('"), "字面量必须原样保留: " + locked);
		assertTrue(locked.startsWith("select a , ' from (' as c from"), "select段不得被切割错位: " + locked);
		// 无别名形态
		locked = SqlServerDialectUtils.lockSql("select a from orders where id=1", null, LockMode.UPGRADE);
		assertTrue(locked.contains("orders with (rowlock xlock)"), "锁提示应注入表名之后: " + locked);
		// as别名形态
		locked = SqlServerDialectUtils.lockSql("select a from orders as o where id=1", null, LockMode.UPGRADE_NOWAIT);
		assertTrue(locked.contains("orders as o with (rowlock xlock nowait)"), "as别名nowait形态: " + locked);
	}

	/** hasOrderByOrUnion(5个方言util的top包裹判定):字面量不参与,真实关键词照常判定 */
	@Test
	public void hasOrderByOrUnionScenes() {
		assertFalse(DialectUtils.hasOrderByOrUnion("select a from t where remark=' order by id'"),
				"字面量order by误报");
		assertFalse(DialectUtils.hasOrderByOrUnion("select a from t where remark=' union all select '"),
				"字面量union误报");
		assertFalse(DialectUtils.hasOrderByOrUnion("select a from t where remark=' ( '"), "字面量括号误报");
		assertTrue(DialectUtils.hasOrderByOrUnion("select a from t order by id"), "真实order by漏判");
		assertTrue(DialectUtils.hasOrderByOrUnion("select a from t union all select b from t2"), "真实union漏判");
		// 子查询内容被剔除,内层order by不影响外层判定(既有语义)
		assertEquals(legacyClearDisturbSql("select a from t where x=(select b from t2 order by id)"),
				DialectUtils.clearDisturbSql("select a from t where x=(select b from t2 order by id)"),
				"clearDisturbSql差分不一致");
	}

	/** isComplexPageQuery:字面量内的union/join/括号不得误判复杂查询 */
	@Test
	public void isComplexPageQueryScenes() {
		assertFalse(DialectUtils.isComplexPageQuery("select a from t where remark=' union all '"),
				"字面量union误判复杂");
		assertFalse(DialectUtils.isComplexPageQuery("select a from t where remark=' inner join '"),
				"字面量join误判复杂");
		assertFalse(DialectUtils.isComplexPageQuery("select a from t where remark=' ( '"), "字面量括号误判复杂");
		// from~where段的子查询/多表判定(既有语义:复杂度只看from到where之间)
		assertTrue(DialectUtils.isComplexPageQuery("select a from (select b from t2) x where c=1"),
				"from段子查询应判复杂");
		assertFalse(DialectUtils.isComplexPageQuery("select a from t where x in (select b from t2)"),
				"where段子查询按既有语义不判复杂");
		assertTrue(DialectUtils.isComplexPageQuery("select a from t1 , t2 where a>1"), "多表应判复杂");
		assertTrue(DialectUtils.isComplexPageQuery("select a from t union all select b from t2"), "union应判复杂");
	}

	/** parseSqlToyConfig的hasUnion标记:字面量union不再误标,真实union(含字面量括号干扰)正确标记 */
	@Test
	public void parseSqlToyConfigHasUnionFlag() throws Exception {
		SqlToyConfig config = SqlConfigParseUtils.parseSqlToyConfig("select a from t1 where remark=' union all '",
				"mysql", SqlType.search);
		assertNotNull(config, "配置解析失败");
		assertFalse(config.isHasUnion(), "字面量union不应标记hasUnion");
		config = SqlConfigParseUtils.parseSqlToyConfig(
				"select a from t1 union all select b from t2", "mysql", SqlType.search);
		assertTrue(config.isHasUnion(), "真实union应标记hasUnion");
		config = SqlConfigParseUtils.parseSqlToyConfig("select concat(a,'(') from t1 union all select b from t2",
				"mysql", SqlType.search);
		assertTrue(config.isHasUnion(), "字面量括号不得干扰union标记");
		// 括号内的union不参与(既有契约)
		config = SqlConfigParseUtils.parseSqlToyConfig("select * from (select a from t1 union all select b from t2) x",
				"mysql", SqlType.search);
		assertFalse(config.isHasUnion(), "括号内局部union按契约不标记");
	}

	// ---------------- 4.H2端到端 ----------------

	/** getCountBySql端到端:字面量干扰sql的count改写执行结果必须正确 */
	@Test
	public void getCountBySqlEndToEnd() throws Exception {
		SqlToyConfig config = new SqlToyConfig(null);
		// 字面量' from (':count定位到真实from
		assertEquals(3L, DialectUtils.getCountBySql(context, config,
				"select a , ' from (' from t1", new Object[] {}, false, null, conn, DataSourceUtils.DBType.H2)
				.longValue(), "字面量' from ('场景count错误");
		// 字面量'('+子查询:count正确
		assertEquals(3L, DialectUtils.getCountBySql(context, config,
				"select concat(a,'(') , (select max(a) from t1) y from t1", new Object[] {}, false, null, conn,
				DataSourceUtils.DBType.H2).longValue(), "字面量'('+子查询场景count错误");
		// 字面量union all:不判union,走简单count,字面量作为过滤条件生效(仅1行remark等于该字面量)
		assertEquals(1L, DialectUtils.getCountBySql(context, config,
				"select a from t1 where remark=' union all '", new Object[] {}, false, null, conn,
				DataSourceUtils.DBType.H2).longValue(), "字面量union all场景count错误");
		// 真实union+字面量'(':包裹count,branch1命中3行+branch2两行=5
		assertEquals(5L, DialectUtils.getCountBySql(context, config,
				"select a from t1 where a>0 or remark='(' union all select a from t2", new Object[] {}, false, null,
				conn, DataSourceUtils.DBType.H2).longValue(), "真实union+字面量括号场景count错误");
	}

	/** union-all-count装配端到端:字面量场景分支拆分与count执行必须正确 */
	@Test
	public void unionAllCountAssembly() throws Exception {
		String countPart = " count(1) ";
		// 基础双分支:3+2=5
		String countSql = newUnionAllCountSql("select a from t1 union all select a from t2", "", countPart, false);
		assertNotNull(countSql, "union all未触发装配");
		assertEquals(5L, countByJdbc(countSql), "双分支union-all-count错误: " + countSql);
		// 分支含字面量'('与' from (':分支from定位正确,字面量原样保留
		countSql = newUnionAllCountSql("select concat(a,'(') c from t1 union all select ' from (' c , a from t2",
				"", countPart, false);
		assertEquals(5L, countByJdbc(countSql), "含字面量分支union-all-count错误: " + countSql);
		// 字面量内的union all不得参与拆分(真实拆分点只有1处;branch1按字面量过滤命中1行)
		countSql = newUnionAllCountSql("select remark from t1 where remark=' union all ' union all select a from t2",
				"", countPart, false);
		assertEquals(3L, countByJdbc(countSql), "字面量union all参与拆分导致错误: " + countSql);
		// 仅字面量union all:不进入装配(返回null走常规包裹)
		assertNull(newUnionAllCountSql("select a from t1 where remark=' union all '", "", countPart, false),
				"字面量union all不应触发装配");
		// with + union all:with片段原样保留在count sql前部
		countSql = newUnionAllCountSql("select a from w union all select a from t2",
				"with w as (select a from t1) ", countPart, false);
		assertEquals(5L, countByJdbc(countSql), "with+union-all-count错误: " + countSql);
		// distinct分支回退整体包裹计数:与直接包裹结果一致
		countSql = newUnionAllCountSql("select distinct a from t1 union all select b from t2", "", countPart, false);
		assertEquals(5L, countByJdbc(countSql), "distinct分支回退包裹后计数错误: " + countSql);
		// mysql系反斜杠转义形态
		countSql = newUnionAllCountSql("select concat(a,'\\\\') c from t1 union all select a from t2", "", countPart,
				true);
		assertEquals(5L, countByJdbc(countSql), "反斜杠转义形态union-all-count错误: " + countSql);
		// 无字面量场景新旧装配差分
		String[] noLiteralUnions = { "select a from t1 union all select a from t2",
				"select a from t1 union all select b from t2 union all select c from t1",
				"select a from (select b from t2) x union all select c from t1 where a>1" };
		for (String unionSql : noLiteralUnions) {
			assertEquals(legacyUnionAllCountSql(unionSql, "", countPart),
					newUnionAllCountSql(unionSql, "", countPart, false), "union-all-count装配差分不一致: " + unionSql);
		}
	}

	// ---------------- 5.maskLiterals幂等性(叠加掩码安全前提) ----------------

	@Test
	public void maskLiteralsIdempotent() {
		String[] corpus = {
				"select a , ' from (' from t1",
				"select concat(a,'(') , (select b from t2) y from t1",
				"select a , 'it''s from (' from t1",
				"select a from t where remark=' order by (1' order by id",
				"select * from t where remark like ? escape '\\' and remark='it''s ok'",
				"select a from t where x='\\'' union all select b from t2",
				"select 'a' , 'b' , (select c from t where d='e(f') from t2" };
		for (String sql : corpus) {
			for (boolean backslashEscape : new boolean[] { false, true }) {
				String once = SqlConfigParseUtils.maskLiterals(sql, backslashEscape);
				String twice = SqlConfigParseUtils.maskLiterals(once, backslashEscape);
				assertEquals(once, twice, "掩码必须幂等: " + sql + " backslashEscape=" + backslashEscape);
				assertEquals(sql.length(), once.length(), "掩码必须等长: " + sql);
			}
		}
	}
}
