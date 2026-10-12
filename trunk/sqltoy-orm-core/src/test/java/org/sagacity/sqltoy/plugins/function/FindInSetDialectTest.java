package org.sagacity.sqltoy.plugins.function;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.Arrays;

import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

/**
 * 回归锁定:find_in_set(str,逗号分隔串)的跨库改写——
 * MySQL语法系原生透传;其余库以"首尾补逗号+精确子串定位"的boolean形态表达完整元素相等。
 * 语义护栏:①产物必须boolean(pg系where不接受"非零整数即真",裸真值惯用法须直接可用);
 * ②禁用LIKE形态(元素含_/%%通配符会误命中,如NODE_11误命中NODE_1,节点清单恰以下划线
 * +数字命名);③函数后的">0/>=1/!=0/<>0"与"=0"比较符由suffix钩子连带消费,
 * 显式比较惯用法与裸真值惯用法双兼容;"=1"(首个元素)无法等价还原,不消费交目标库响亮报错。
 */
public class FindInSetDialectTest {

	/** rainbow消费节点查询:find_in_set最典型的真实形态(裸真值+动态块拼接产物) */
	private final static String RAINBOW_SQL = "(mq_consumer_nodes is null or find_in_set(?,mq_consumer_nodes)) and status=1";

	@BeforeAll
	public static void init() {
		FunctionUtils.setFunctionConverts(Arrays.asList("default"));
	}

	@Test
	public void pgFamilyBareFormIsBoolean() {
		for (String dialect : new String[] { "postgresql", "postgresql14", "kingbase", "gaussdb", "opengauss",
				"oscar", "stardb", "mogdb", "vastbase" }) {
			String sql = FunctionUtils.getDialectSql(RAINBOW_SQL, dialect);
			assertEquals(
					"(mq_consumer_nodes is null or (strpos(','||mq_consumer_nodes||',', ','||?||',')>0)) and status=1",
					sql, dialect + "应改写为补逗号+strpos的boolean形态");
		}
	}

	@Test
	public void mysqlFamilyNativePassthrough() {
		for (String dialect : new String[] { "mysql", "mysql57", "tidb", "oceanbase" }) {
			assertEquals(RAINBOW_SQL, FunctionUtils.getDialectSql(RAINBOW_SQL, dialect),
					dialect + "原生find_in_set应透传不改写");
		}
	}

	@Test
	public void mysqlSyntaxNoNativeUsesConcatInstr() {
		for (String dialect : new String[] { "h2", "doris", "starrocks" }) {
			String sql = FunctionUtils.getDialectSql(RAINBOW_SQL, dialect);
			assertEquals(
					"(mq_consumer_nodes is null or (instr(concat(',',mq_consumer_nodes,','), concat(',',?,','))>0)) and status=1",
					sql, dialect + "应改写为concat+instr形态(||在MySQL语法系是OR,不可用)");
		}
	}

	@Test
	public void oracleFamilyUsesInstrConcatenation() {
		for (String dialect : new String[] { "oracle", "oracle11", "dm" }) {
			assertEquals(
					"(mq_consumer_nodes is null or (instr(','||mq_consumer_nodes||',', ','||?||',')>0)) and status=1",
					FunctionUtils.getDialectSql(RAINBOW_SQL, dialect), dialect + "应改写为||+instr形态");
		}
	}

	@Test
	public void sqlserverUsesCharindexPlus() {
		assertEquals(
				"(mq_consumer_nodes is null or (charindex(','+?+',', ','+mq_consumer_nodes+',')>0)) and status=1",
				FunctionUtils.getDialectSql(RAINBOW_SQL, "sqlserver"), "sqlserver无instr/||,须charindex+'+'");
	}

	@Test
	public void db2AndHanaLocateArgumentOrder() {
		// db2的locate(sub,str)针在前;hana的locate(str,sub)草垛在前(与db2反序)
		assertEquals(
				"(mq_consumer_nodes is null or (locate(','||?||',', ','||mq_consumer_nodes||',')>0)) and status=1",
				FunctionUtils.getDialectSql(RAINBOW_SQL, "db2"), "db2的locate参数序须针在前");
		assertEquals(
				"(mq_consumer_nodes is null or (locate(','||mq_consumer_nodes||',', ','||?||',')>0)) and status=1",
				FunctionUtils.getDialectSql(RAINBOW_SQL, "hana"), "hana的locate参数序须草垛在前");
	}

	@Test
	public void sqliteAndClickHouseForms() {
		assertEquals(
				"(mq_consumer_nodes is null or (instr(','||mq_consumer_nodes||',', ','||?||',')>0)) and status=1",
				FunctionUtils.getDialectSql(RAINBOW_SQL, "sqlite"), "sqlite应改写为||+instr形态");
		assertEquals(
				"(mq_consumer_nodes is null or (position(concat(',',mq_consumer_nodes,','), concat(',',?,','))>0)) and status=1",
				FunctionUtils.getDialectSql(RAINBOW_SQL, "clickhouse"), "clickhouse应改写为concat+position逗号形态");
	}

	@Test
	public void comparisonSuffixConsumedAndRewritten() {
		// 命中口径族:>0/>=1/!=0/<>0 连带消费并改写为命中boolean(空格变体亦然)
		assertEquals("select * from t where (strpos(','||nodes||',', ','||'a'||',')>0)",
				FunctionUtils.getDialectSql("select * from t where find_in_set('a',nodes)>0", "postgresql"),
				"'>0'应消费并改写为命中口径");
		assertEquals("select * from t where (strpos(','||nodes||',', ','||'a'||',')>0)",
				FunctionUtils.getDialectSql("select * from t where find_in_set('a',nodes) >= 1", "postgresql"),
				"'>=1'与'>0'同为命中口径");
		assertEquals("select * from t where (strpos(','||nodes||',', ','||'a'||',')>0)",
				FunctionUtils.getDialectSql("select * from t where find_in_set('a',nodes)!=0", "postgresql"),
				"'!=0'与'>0'同为命中口径");
		// 未命中口径:=0 反转为 strpos…=0
		assertEquals("select * from t where (strpos(','||nodes||',', ','||'a'||',')=0)",
				FunctionUtils.getDialectSql("select * from t where find_in_set('a',nodes) = 0", "postgresql"),
				"'=0'应反转改写为未命中口径");
		// mysql系原生含比较符整体透传
		assertEquals("select * from t where find_in_set('a',nodes)>0",
				FunctionUtils.getDialectSql("select * from t where find_in_set('a',nodes)>0", "mysql"),
				"mysql原生含比较符透传");
	}

	@Test
	public void equalsOneSuffixNotConsumedForLoudFailure() {
		// "=1"(首个元素)无等价还原:比较符不消费,boolean改写+未消费的"=1"交目标库响亮报错
		String sql = FunctionUtils.getDialectSql("select * from t where find_in_set('a',nodes)=1", "postgresql");
		assertTrue(sql.contains("strpos(") && sql.endsWith("=1"), "'=1'不做口径反转,原样保留响亮报错:" + sql);
	}

	@Test
	public void uppercaseAndNestedInsideOtherFunction() {
		// 大写形态同样命中(正则不区分大小写)
		String upper = FunctionUtils.getDialectSql("select * from t where FIND_IN_SET('a',tags)", "postgresql");
		assertTrue(upper.contains("strpos("), "大写FIND_IN_SET应被转换");
		assertFalse(upper.toLowerCase().contains("find_in_set"), "转换后不应残留find_in_set");
		// 嵌套在其他函数参数内(case when的boolean上下文)逐层展开
		String nested = FunctionUtils.getDialectSql(
				"select case when find_in_set('a',tags)>0 then 1 else 0 end from t", "postgresql");
		assertEquals("select case when (strpos(','||tags||',', ','||'a'||',')>0) then 1 else 0 end from t", nested,
				"case when内嵌find_in_set>0应两层各自完成转换");
	}

	@Test
	public void malformedArgsKeptForLoudFailure() {
		// 单参形态语义不明,原样保留交由目标库响亮报错
		String malformed = "select find_in_set(only_one_arg) from t";
		assertEquals(malformed, FunctionUtils.getDialectSql(malformed, "postgresql"), "参数不完整应原样保留");
	}

	@Test
	public void literalContentNotConverted() {
		// 字面量内的函数文本不受转换破坏
		String sql = "select 'find_in_set(a,b)' from t";
		assertEquals(sql, FunctionUtils.getDialectSql(sql, "postgresql"), "字面量内的find_in_set文本不应被改写");
	}
}
