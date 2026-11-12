package org.sagacity.sqltoy.utils;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.sagacity.sqltoy.utils.DataSourceUtils.DBType;

/**
 * 保留字转换的边界回归:convertSql原实现用matcher.start()+1、match.substring(1)、subSize做
 * "定长边界"算术,隐含首尾边界各消费1个字符的假定;当边界为空匹配(方括号保留字位于sql首尾、
 * 或紧邻单词字符)时定位错位,会输出被改坏的sql。改按关键字组位置定位后,这些形态都必须正确。
 */
public class ReservedWordsUtilBoundaryTest {

	@BeforeEach
	public void setUp() {
		// put为累加合并语义且保留字集合是全局静态,显式清空建立前置状态
		ReservedWordsUtil.clear();
		ReservedWordsUtil.put("desc,order");
	}

	@AfterEach
	public void tearDown() {
		// 避免全局静态状态影响其他测试类
		ReservedWordsUtil.clear();
	}

	@Test
	public void reservedWordInMiddleIsConverted() {
		assertEquals("select a from t where \"desc\"=1",
				ReservedWordsUtil.convertSql("select a from t where [desc]=1", DBType.POSTGRESQL));
		// 逗号分隔的多个保留字
		assertEquals("select \"desc\",\"order\" from t",
				ReservedWordsUtil.convertSql("select [desc],[order] from t", DBType.POSTGRESQL));
	}

	/**
	 * 尾组空匹配:sql以[col]收尾时模式尾边界无字符可消费
	 */
	@Test
	public void reservedWordAtEndOfSql() {
		assertEquals("select a from t order by \"desc\"",
				ReservedWordsUtil.convertSql("select a from t order by [desc]", DBType.POSTGRESQL));
	}

	/**
	 * 首组空匹配:sql以[col]开头时模式首边界无字符可消费
	 */
	@Test
	public void reservedWordAtStartOfSql() {
		assertEquals("\"desc\"=1", ReservedWordsUtil.convertSql("[desc]=1", DBType.POSTGRESQL));
	}

	/**
	 * 首组空匹配:方括号保留字紧邻单词字符(如b[desc])
	 */
	@Test
	public void reservedWordAdjacentToWordChar() {
		assertEquals("select a from t where x=b\"desc\" and y=1",
				ReservedWordsUtil.convertSql("select a from t where x=b[desc] and y=1", DBType.POSTGRESQL));
	}

	@Test
	public void otherDialectQuotes() {
		assertEquals("select `desc` from t", ReservedWordsUtil.convertSql("select [desc] from t", DBType.MYSQL));
		assertEquals("select [desc] from t", ReservedWordsUtil.convertSql("select [desc] from t", DBType.SQLSERVER));
		assertEquals("select \"desc\" from t", ReservedWordsUtil.convertSql("select [desc] from t", DBType.H2));
		// update 2026-9-15 覆盖补齐:clickhouse/impala纳入反引号组(ch标识符可用反引号或双引号,
		// impala沿hive习惯用反引号),hana纳入双引号组(原convertSql漏配落else被剥引号成裸标识符)
		assertEquals("select `desc` from t", ReservedWordsUtil.convertSql("select [desc] from t", DBType.CLICKHOUSE));
		assertEquals("select `desc` from t", ReservedWordsUtil.convertSql("select [desc] from t", DBType.IMPALA));
		assertEquals("select \"desc\" from t", ReservedWordsUtil.convertSql("select [desc] from t", DBType.HANA));
		// convertSql对UNDEFINE原样返回,不做转换
		assertEquals("select [desc] from t", ReservedWordsUtil.convertSql("select [desc] from t", DBType.UNDEFINE));
	}

	@Test
	public void noMatchReturnsSameInstance() {
		String sql = "select a from t where b=1";
		assertEquals(sql, ReservedWordsUtil.convertSql(sql, DBType.POSTGRESQL));
	}

	@Test
	public void convertSimpleSqlBracketReplacement() {
		assertEquals("select \"desc\" from t where \"order\"=1",
				ReservedWordsUtil.convertSimpleSql("select [desc] from t where [order]=1", DBType.POSTGRESQL));
		assertEquals("select `desc` from t", ReservedWordsUtil.convertSimpleSql("select [desc] from t", DBType.MYSQL));
		// update 2026-9-15 覆盖补齐:clickhouse/impala反引号、hana双引号
		assertEquals("select `desc` from t",
				ReservedWordsUtil.convertSimpleSql("select [desc] from t", DBType.CLICKHOUSE));
		assertEquals("select `desc` from t", ReservedWordsUtil.convertSimpleSql("select [desc] from t", DBType.IMPALA));
		assertEquals("select \"desc\" from t", ReservedWordsUtil.convertSimpleSql("select [desc] from t", DBType.HANA));
		// null防护前置后不再NPE,与sqlserver/sqlite同款原样返回
		assertEquals("select [desc] from t", ReservedWordsUtil.convertSimpleSql("select [desc] from t", null));
		// 未识别方言剔除方括号
		assertEquals("select desc from t", ReservedWordsUtil.convertSimpleSql("select [desc] from t", DBType.UNDEFINE));
	}

	@Test
	public void convertWordAndIsKeyWord() {
		assertEquals("\"desc\"", ReservedWordsUtil.convertWord("desc", DBType.KINGBASE));
		assertEquals("[desc]", ReservedWordsUtil.convertWord("desc", DBType.SQLSERVER));
		assertEquals("`desc`", ReservedWordsUtil.convertWord("desc", DBType.MYSQL));
		// update 2026-9-15 覆盖补齐:hana双引号、clickhouse/impala反引号
		assertEquals("\"desc\"", ReservedWordsUtil.convertWord("desc", DBType.HANA));
		assertEquals("`desc`", ReservedWordsUtil.convertWord("desc", DBType.CLICKHOUSE));
		assertEquals("`desc`", ReservedWordsUtil.convertWord("desc", DBType.IMPALA));
		assertTrue(ReservedWordsUtil.isKeyWord("DESC"));
		assertFalse(ReservedWordsUtil.isKeyWord("name"));
	}
}
