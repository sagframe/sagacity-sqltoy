package org.sagacity.sqltoy.utils;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.sagacity.sqltoy.utils.DataSourceUtils.DBType;

/**
 * convertSql 的对比回归(该方法的引用符替换曾被重写为按关键字组定位,替换逻辑风险高):
 * 1) oracle差分:用例由"前缀×token×后缀"构成且前缀/后缀不含任何引用符号,正确输出可由独立的
 * 字符串替换oracle精确算出,并校验"除引用符号外字符原样保留"的不变量;
 * 2) 方言映射、多token/相邻token、put/clear的状态语义;
 * 3) 已知局限(字面量内的方括号同样会被转换)以断言固话,避免将来无意中改变。
 */
public class ReservedWordsUtilConvertSqlTest {

	private static final String[][] ORACLE_PAIRS = { { "[desc]", "\"desc\"" }, { "[order]", "\"order\"" },
			{ "[DESC]", "\"DESC\"" }, { "`desc`", "\"desc\"" }, { "`order`", "\"order\"" }, { "'desc'", "\"desc\"" },
			{ "'order'", "\"order\"" }, { "\"desc\"", "\"desc\"" }, { "\"order\"", "\"order\"" } };

	/** 独立oracle:只做引用符号替换,其余字符原样保留 */
	private static String oracle(String sql) {
		String result = sql;
		for (String[] pair : ORACLE_PAIRS) {
			result = result.replace(pair[0], pair[1]);
		}
		return result;
	}

	/** 去掉引用符号后的文本:转换只允许改变引用符号,其它字符必须原样保留 */
	private static String stripDelimiters(String value) {
		return value.replace("\"", "").replace("`", "").replace("[", "").replace("]", "").replace("'", "");
	}

	private static final String[] PREFIXES = { "", " ", ",", "(", ".", "=", "b", "1", "\n", "\t", "x=",
			"select a from t where ", "select x=y(", "a", "  ", "where 1=1 and " };

	private static final String[] TOKENS = { "[desc]", "\"desc\"", "`desc`", "'desc'", "[order]", "[DESC]",
			"[desc],[order]", "\"desc\", `order`", "[desc] , [order]" };

	private static final String[] SUFFIXES = { "", " ", ",", ")", ";", "and y=1", "b", "1", "\n", "from t", "=1",
			" desc" };

	@BeforeEach
	public void setUp() {
		// put为累加合并语义且保留字集合为全局静态,显式清空建立前置状态
		ReservedWordsUtil.clear();
		ReservedWordsUtil.put("desc,order");
	}

	@AfterEach
	public void tearDown() {
		ReservedWordsUtil.clear();
	}

	@Test
	public void oracleDifferentialForAllPositions() {
		int cases = 0;
		int converted = 0;
		String sql;
		String expected;
		String actual;
		for (String prefix : PREFIXES) {
			for (String token : TOKENS) {
				for (String suffix : SUFFIXES) {
					sql = prefix + token + suffix;
					expected = oracle(sql);
					actual = ReservedWordsUtil.convertSql(sql, DBType.POSTGRESQL);
					assertEquals(expected, actual, "sql=[" + escape(sql) + "]");
					// 不变量1:除引用符号外字符原样保留(不得出现旧实现那种多一个"["的错位)
					assertEquals(stripDelimiters(sql), stripDelimiters(actual), "字符被改动,sql=[" + escape(sql) + "]");
					// 不变量2:方括号作为引用符必须全部被转换掉
					assertFalse(actual.indexOf('[') != -1 || actual.indexOf(']') != -1,
							"方括号未转换,sql=[" + escape(sql) + "] actual=[" + escape(actual) + "]");
					cases++;
					if (!expected.equals(sql)) {
						converted++;
					}
				}
			}
		}
		// 用例规模与"确有大量用例真的发生了转换"双重保障,避免空转
		assertEquals(PREFIXES.length * TOKENS.length * SUFFIXES.length, cases);
		assertTrue(converted > 300, "发生转换的用例数异常少:" + converted);
	}

	@Test
	public void dialectDelimiterMatrix() {
		String sql = "select a from t where [desc]=1";
		assertEquals("select a from t where \"desc\"=1", ReservedWordsUtil.convertSql(sql, DBType.POSTGRESQL));
		assertEquals("select a from t where \"desc\"=1", ReservedWordsUtil.convertSql(sql, DBType.ORACLE));
		assertEquals("select a from t where \"desc\"=1", ReservedWordsUtil.convertSql(sql, DBType.KINGBASE));
		assertEquals("select a from t where `desc`=1", ReservedWordsUtil.convertSql(sql, DBType.MYSQL));
		assertEquals("select a from t where `desc`=1", ReservedWordsUtil.convertSql(sql, DBType.DORIS));
		// SQLSERVER/SQLITE保持方括号
		assertEquals(sql, ReservedWordsUtil.convertSql(sql, DBType.SQLSERVER));
		assertEquals(sql, ReservedWordsUtil.convertSql(sql, DBType.SQLITE));
		assertEquals("select a from t where \"desc\"=1", ReservedWordsUtil.convertSql(sql, DBType.H2));
		// update 2026-9-15 CLICKHOUSE已加入反引号族(远端改动),不再裸输出
		assertEquals("select a from t where `desc`=1", ReservedWordsUtil.convertSql(sql, DBType.CLICKHOUSE));
		// UNDEFINE/ES/MONGO:原样返回
		assertEquals(sql, ReservedWordsUtil.convertSql(sql, DBType.UNDEFINE));
		assertEquals(sql, ReservedWordsUtil.convertSql(sql, DBType.ES));
	}

	@Test
	public void multipleTokensInOneSql() {
		String sql = "insert into t ([desc],[order],name) values (:desc,:order,:name)";
		assertEquals("insert into t (\"desc\",\"order\",name) values (:desc,:order,:name)",
				ReservedWordsUtil.convertSql(sql, DBType.POSTGRESQL));
	}

	@Test
	public void mixedDelimiterFormsInOneSql() {
		// 四种引用符形态混合出现,均按保留字处理
		String sql = "select [desc] as a,\"order\" as b,`desc` as c,'order' as d from t";
		assertEquals("select \"desc\" as a,\"order\" as b,\"desc\" as c,\"order\" as d from t",
				ReservedWordsUtil.convertSql(sql, DBType.POSTGRESQL));
	}

	@Test
	public void wordCaseIsPreserved() {
		assertEquals("select \"DESC\" from t", ReservedWordsUtil.convertSql("select [DESC] from t", DBType.POSTGRESQL));
		assertEquals("select \"Desc\" from t", ReservedWordsUtil.convertSql("select [Desc] from t", DBType.POSTGRESQL));
	}

	@Test
	public void adjacentTokensAreBothConverted() {
		// 无分隔相邻:模式尾组(\s||\W)的空交替排在\W之前,非空白字符不会被消费,故后一个引用同样被识别
		assertEquals("select \"desc\"\"order\" from t",
				ReservedWordsUtil.convertSql("select [desc][order] from t", DBType.POSTGRESQL));
		// 空白分隔
		assertEquals("select \"desc\" \"order\" from t",
				ReservedWordsUtil.convertSql("select [desc] [order] from t", DBType.POSTGRESQL));
	}

	/* ==================== 已知局限(与重写前一致,固化为断言) ==================== */

	@Test
	public void knownLimitationBracketInsideLiteralIsConverted() {
		// 转换不做字面量识别:字符串字面量内的方括号同样被转换(与重写前一致)
		String actual = ReservedWordsUtil.convertSql("select 'a[desc]b' from t", DBType.POSTGRESQL);
		assertEquals("select 'a\"desc\"b' from t", actual);
	}

	/* ==================== 状态语义 ==================== */

	@Test
	public void clearResetsToIdentity() {
		ReservedWordsUtil.clear();
		String sql = "select [desc] from t";
		assertEquals(sql, ReservedWordsUtil.convertSql(sql, DBType.POSTGRESQL));
	}

	@Test
	public void cumulativePutKeepsPreviousWords() {
		// put为累加合并语义:第二次put后两批保留字都要生效
		ReservedWordsUtil.put("group");
		assertEquals("select \"desc\",\"order\",\"group\" from t",
				ReservedWordsUtil.convertSql("select [desc],[order],[group] from t", DBType.POSTGRESQL));
	}

	@Test
	public void blankPutIgnored() {
		ReservedWordsUtil.clear();
		ReservedWordsUtil.put("  ");
		ReservedWordsUtil.put(null);
		assertEquals("select [desc] from t", ReservedWordsUtil.convertSql("select [desc] from t", DBType.POSTGRESQL));
	}

	@Test
	public void emptySqlIdentity() {
		assertEquals("", ReservedWordsUtil.convertSql("", DBType.POSTGRESQL));
	}

	@Test
	public void nonReservedWordIsUntouched() {
		String sql = "select [name] from t";
		assertEquals(sql, ReservedWordsUtil.convertSql(sql, DBType.POSTGRESQL));
	}

	private static String escape(String value) {
		return value.replace("\\", "\\\\").replace("\r", "\\r").replace("\n", "\\n").replace("\t", "\\t");
	}
}
