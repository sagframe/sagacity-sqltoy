package org.sagacity.sqltoy.config;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.lang.reflect.Field;
import java.util.HashMap;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import org.junit.jupiter.api.Test;
import org.sagacity.sqltoy.config.model.SqlType;
import org.sagacity.sqltoy.utils.DataSourceUtils;
import org.sagacity.sqltoy.utils.DataSourceUtils.DBType;

/**
 * update 2026-10-13 虚表宏@virtualTable()回归(兼容别名@dual()/@dummyTable(),忽略大小写):
 * select常量行伪表按方言展开——oracle系(oracle/oracle11/dm/oceanbase)→from dual、
 * db2→from sysibm.sysdummy1、hana→from dummy(映射对齐各Dialect的VIRTUAL_TABLE常量),
 * 其余方言原生支持无from select剔除宏;无宏sql零变化;经parseSqlToyConfig完整管线后
 * 宏已被展开。allDbTypesCovered反射穷举DBType全部常量,新增数据库类型未登记映射时显式失败
 * <p>
 * update 2026-11-16 增补前置from写法回归:select ... from @virtualTable() 在伪表族复用
 * 前置from只保留一个from、在无from族连from一并剔除;重点锁定from判定准确性(词边界避开
 * address_from/from_x等列名、仅消费紧邻from、)from嵌套、单引号字面量掩码),并覆盖别名/
 * 大小写/换行/零空白/null与未知方言/多宏/子查询/CTE/注释/幂等
 */
public class VirtualTableMacroTest {

	private static final String SQL = "select '0' tenant_id,'全局公共' tenant_name @virtualTable() "
			+ "union select s.tenant_id,s.tenant_name from sys_tenant_info s where s.status=1";

	private String parse(String dialect) {
		return SqlConfigParseUtils.parseSqlToyConfig(SQL, dialect, SqlType.search).getSql(dialect);
	}

	private String expand(String dialect) {
		return SqlConfigParseUtils.expandVirtualTable(SQL, dialect);
	}

	private String expand(String sql, String dialect) {
		return SqlConfigParseUtils.expandVirtualTable(sql, dialect);
	}

	/** 统计from关键字出现次数,用于断言前置from被复用/剔除后不重复、不悬空 */
	private int countFrom(String sql) {
		Matcher matcher = Pattern.compile("(?i)\\bfrom\\b").matcher(sql);
		int count = 0;
		while (matcher.find()) {
			count++;
		}
		return count;
	}

	/** oracle系展开为from dual,常量行union结构保持 */
	@Test
	public void oracleFamilyExpandDual() {
		for (String dialect : new String[] { "oracle", "oracle11", "dm", "oceanbase" }) {
			String sql = parse(dialect);
			assertTrue(sql.contains(" from dual "), dialect + " should expand @virtualTable() to from dual:" + sql);
			assertFalse(sql.contains("@virtualTable"), dialect + " macro should be consumed:" + sql);
			assertTrue(sql.contains("union"), dialect + " union structure should remain:" + sql);
		}
	}

	/** db2展开为from sysibm.sysdummy1,hana展开为from dummy(对齐各自Dialect的VIRTUAL_TABLE) */
	@Test
	public void db2AndHanaExpandOwnDummy() {
		assertTrue(expand("db2").contains(" from sysibm.sysdummy1 "), "db2 should expand to sysibm.sysdummy1");
		assertTrue(expand("hana").contains(" from dummy "), "hana should expand to dummy");
		assertFalse(expand("hana").contains("@virtualTable"), "hana macro should be consumed");
	}

	/** 支持无from select的库剔除宏,select常量行直接接union */
	@Test
	public void fromlessDialectsStripMacro() {
		for (String dialect : new String[] { "mysql", "postgresql", "kingbase", "opengauss", "sqlserver", "h2",
				"clickhouse", "impala", "sqlite", "tidb", "gaussdb", "mogdb", "oscar", "vastbase", "stardb",
				"highgo" }) {
			String sql = parse(dialect);
			assertFalse(sql.contains("@virtualTable"), dialect + " macro should be stripped:" + sql);
			assertFalse(sql.contains(" dual "), dialect + " no dual expected:" + sql);
			assertFalse(sql.contains(" sysdummy1 "), dialect + " no sysdummy1 expected:" + sql);
			assertFalse(sql.contains(" from dummy "), dialect + " no dummy expected:" + sql);
			assertTrue(sql.contains("union"), dialect + " union structure should remain:" + sql);
		}
	}

	/**
	 * update 2026-11-16 前置from写法:伪表族复用该from,select 1 from @virtualTable() 只展开出
	 * 一个from(from dual),不产生from from dual;db2/hana同样保持单from
	 */
	@Test
	public void fromPrefixedMacroExpandsSingleFrom() {
		for (String dialect : new String[] { "oracle", "oracle11", "dm", "oceanbase" }) {
			String sql = expand("select 1 v from @virtualTable() where 1=1", dialect);
			assertTrue(sql.contains(" from dual "), dialect + " should expand to from dual:" + sql);
			assertEquals(1, countFrom(sql), dialect + " should keep exactly one from:" + sql);
			assertFalse(sql.toLowerCase().contains("from from"), dialect + " should not duplicate from:" + sql);
		}
		String db2Sql = expand("select 1 v from @virtualTable()", "db2");
		assertTrue(db2Sql.contains(" from sysibm.sysdummy1 "), "db2 should reuse preceding from:" + db2Sql);
		assertEquals(1, countFrom(db2Sql), "db2 should keep exactly one from:" + db2Sql);
		assertEquals(1, countFrom(expand("select 1 v from @virtualTable()", "hana")),
				"hana should keep exactly one from");
	}

	/**
	 * update 2026-11-16 无from族:前置from随宏一并剔除,后续where/exists等子句保持合法,
	 * 不残留悬空from
	 */
	@Test
	public void fromPrefixedMacroStrippedWithFrom() {
		for (String dialect : new String[] { "mysql", "mysql57", "tidb", "doris", "starrocks", "postgresql",
				"postgresql14", "gaussdb", "mogdb", "opengauss", "stardb", "vastbase", "oscar", "kingbase",
				"highgo", "sqlserver", "sqlite", "h2", "clickhouse", "impala", "tdengine", "mongo", "es",
				"undefine" }) {
			String sql = expand("select 1 v from @virtualTable() where 1=1", dialect);
			assertFalse(sql.contains("@"), dialect + " macro should be consumed:" + sql);
			assertEquals(0, countFrom(sql), dialect + " dangling from should be removed:" + sql);
			assertEquals("select 1 v where 1=1", sql.replaceAll("\\s+", " ").trim(),
					dialect + " should become a from-less select:" + sql);
		}
	}

	/**
	 * update 2026-11-16 宏前不写from(select 1 v @virtualTable()):伪表族自动补一个from
	 * (dual/sysibm.sysdummy1/dummy),无from族只剔除宏、不补from;与前置from写法互不影响
	 */
	@Test
	public void macroWithoutFromSupplementsFrom() {
		for (String dialect : new String[] { "oracle", "oracle11", "dm", "oceanbase" }) {
			String sql = expand("select 1 v @virtualTable() where 1=1", dialect);
			assertTrue(sql.contains(" from dual "), dialect + " should supplement from dual:" + sql);
			assertEquals(1, countFrom(sql), dialect + " should add exactly one from:" + sql);
		}
		String db2Sql = expand("select 1 v @virtualTable()", "db2");
		assertTrue(db2Sql.contains(" from sysibm.sysdummy1 "), "db2 should supplement sysdummy1:" + db2Sql);
		assertEquals(1, countFrom(db2Sql), "db2 should add exactly one from:" + db2Sql);
		assertTrue(expand("select 1 v @virtualTable()", "hana").contains(" from dummy "),
				"hana should supplement dummy");
		for (String dialect : new String[] { "mysql", "postgresql", "sqlserver", "h2" }) {
			String sql = expand("select 1 v @virtualTable() where 1=1", dialect);
			assertFalse(sql.contains("@"), dialect + " macro should be consumed:" + sql);
			assertEquals(0, countFrom(sql), dialect + " should not supplement from:" + sql);
			assertEquals("select 1 v where 1=1", sql.replaceAll("\\s+", " ").trim(), dialect + ": " + sql);
		}
	}

	/** 前置from与union组合:只消费宏前的from,真实查询的from保留 */
	@Test
	public void fromPrefixedMacroInUnion() {
		String sql = "select '0' id from @virtualTable() union select id from t";
		String onOracle = expand(sql, "oracle");
		assertTrue(onOracle.contains(" from dual "), "oracle union should expand macro to dual:" + onOracle);
		assertEquals(2, countFrom(onOracle), "oracle union should keep dual from and real from:" + onOracle);
		assertTrue(onOracle.contains(" from t"), "oracle union real from should remain:" + onOracle);
		String onPg = expand(sql, "postgresql");
		assertEquals(1, countFrom(onPg), "pg union should only keep the real from:" + onPg);
		assertEquals("select '0' id union select id from t", onPg.replaceAll("\\s+", " ").trim(),
				"pg union should remove only the macro's from:" + onPg);
	}

	/** 完整解析管线:前置from展开结果随SqlToyConfig固化,伪表族单from、剔除族零from */
	@Test
	public void fromPrefixedMacroThroughParsePipeline() {
		String[][] pseudo = { { "oracle", " from dual " }, { "oracle11", " from dual " }, { "dm", " from dual " },
				{ "oceanbase", " from dual " }, { "db2", " from sysibm.sysdummy1 " },
				{ "hana", " from dummy " } };
		for (String[] item : pseudo) {
			String sql = SqlConfigParseUtils
					.parseSqlToyConfig("select 1 v from @virtualTable() where 1=1", item[0], SqlType.search)
					.getSql(item[0]);
			assertTrue(sql.contains(item[1]), item[0] + " pipeline should expand to" + item[1] + ":" + sql);
			assertEquals(1, countFrom(sql), item[0] + " pipeline should keep exactly one from:" + sql);
		}
		for (String dialect : new String[] { "mysql", "postgresql", "sqlserver", "h2", "clickhouse" }) {
			String sql = SqlConfigParseUtils
					.parseSqlToyConfig("select 1 v from @virtualTable() where 1=1", dialect, SqlType.search)
					.getSql(dialect);
			assertEquals(0, countFrom(sql), dialect + " pipeline should remove dangling from:" + sql);
			assertEquals("select 1 v where 1=1", sql.replaceAll("\\s+", " ").trim(),
					dialect + " pipeline should become a from-less select:" + sql);
		}
	}

	/** 两种写法的规范化结果锁定:伪表族单from、无from族零from,避免回归到from from/悬空from */
	@Test
	public void bothFormsNormalizedExpansion() {
		String[][] cases = { { "oracle", "select 1 v from @virtualTable()", "select 1 v from dual" },
				{ "oracle", "select 1 v @virtualTable()", "select 1 v from dual" },
				{ "postgresql", "select 1 v from @virtualTable()", "select 1 v" },
				{ "postgresql", "select 1 v @virtualTable()", "select 1 v" },
				{ "db2", "select 1 v from @virtualTable()", "select 1 v from sysibm.sysdummy1" },
				{ "hana", "select 1 v @virtualTable()", "select 1 v from dummy" } };
		for (String[] item : cases) {
			assertEquals(item[2], expand(item[1], item[0]).replaceAll("\\s+", " ").trim(),
					item[0] + " normalized expansion mismatch for " + item[1]);
		}
	}

	/**
	 * 边界输入:null/空白sql、null/未知方言、相似宏名、零空白贴合、制表/多空格、
	 * 方言名大小写、重复展开幂等均不得抛异常或误改sql
	 */
	@Test
	public void boundaryInputsHandled() {
		// null/空/纯空白sql原样返回
		assertNull(SqlConfigParseUtils.expandVirtualTable(null, "oracle"));
		assertEquals("", SqlConfigParseUtils.expandVirtualTable("", "oracle"));
		assertEquals("   ", SqlConfigParseUtils.expandVirtualTable("   ", "oracle"));
		// null/空/未知方言:按无虚表处理,前置from与宏一并剔除,不抛异常
		for (String dialect : new String[] { null, "", "not-a-dialect" }) {
			String sql = SqlConfigParseUtils.expandVirtualTable("select 1 v from @virtualTable() where 1=1", dialect);
			assertFalse(sql.contains("@"), "dialect[" + dialect + "] macro should be consumed:" + sql);
			assertEquals(0, countFrom(sql), "dialect[" + dialect + "] dangling from should be removed:" + sql);
			assertEquals("select 1 v where 1=1", sql.replaceAll("\\s+", " ").trim(),
					"dialect[" + dialect + "] should become a from-less select:" + sql);
		}
		// 相似但非法的宏名/缺括号/括号内有内容:不做任何替换
		String[] unchanged = { "select 1 from @virtualTable", "select 1 from @virtualTableX()",
				"select 1 from @virtualTables()", "select 1 from @virtual_table()",
				"select 1 from @virtualTable(1)", "select 1 from @dualX()", "select 1 from @dummyTables()" };
		for (String sql : unchanged) {
			assertEquals(sql, SqlConfigParseUtils.expandVirtualTable(sql, "oracle"), "should be unchanged:" + sql);
		}
		// from与宏零空白/制表符/多空格均可识别
		for (String sql : new String[] { "select 1 v from@virtualTable()", "select 1 v from\t@virtualTable()",
				"select 1 v from     @virtualTable()" }) {
			String expanded = expand(sql, "oracle");
			assertTrue(expanded.contains(" from dual "), "should expand:" + expanded);
			assertEquals(1, countFrom(expanded), "should keep one from:" + expanded);
		}
		// 方言名大小写不敏感(与DataSourceUtils.getDBType口径一致)
		assertTrue(expand("select 1 v from @virtualTable()", "ORACLE").contains(" from dual "),
				"uppercase dialect should expand");
		// 重复展开幂等
		for (String dialect : new String[] { "oracle", "postgresql" }) {
			String once = expand("select 1 v from @virtualTable()", dialect);
			assertEquals(once, expand(once, dialect), dialect + " expansion should be idempotent");
		}
	}

	/**
	 * 多宏/嵌套组合:多个宏(含混合from前缀)全部展开;子查询/CTE/exists中只消费紧邻各宏的
	 * from,外层真实查询的from保留
	 */
	@Test
	public void multiMacroAndNestedComposition() {
		String multi = "select 1 from @virtualTable() union select 2 @dual() union select 3 from @dummyTable()";
		String onOracle = expand(multi, "oracle");
		assertFalse(onOracle.contains("@"), "all macros should be consumed:" + onOracle);
		assertEquals(3, countFrom(onOracle), "each macro should keep one from:" + onOracle);
		String onPg = expand(multi, "postgresql");
		assertFalse(onPg.contains("@"), "all macros should be consumed on pg:" + onPg);
		assertEquals(0, countFrom(onPg), "no from should remain on pg:" + onPg);

		String subQuery = "select * from (select 1 v from @virtualTable()) x";
		assertEquals(2, countFrom(expand(subQuery, "oracle")), "inner dual and outer real from:" + subQuery);
		String pgSub = expand(subQuery, "postgresql");
		assertEquals(1, countFrom(pgSub), "only outer real from should remain on pg:" + pgSub);

		String cte = "with c as (select 1 v from @virtualTable()) select * from c";
		assertEquals(2, countFrom(expand(cte, "oracle")), "cte dual and outer from c:" + cte);
		assertEquals(1, countFrom(expand(cte, "postgresql")), "cte from removed, outer from c remains:" + cte);

		String exists = "select 1 v from @virtualTable() where exists(select 1 from t)";
		assertEquals(2, countFrom(expand(exists, "oracle")), "dual plus exists from:" + exists);
		assertEquals(1, countFrom(expand(exists, "postgresql")), "only exists from on pg:" + exists);
	}

	/**
	 * update 2026-11-16 嵌套子查询与)from紧邻:内层真实from table保留,外层紧邻宏的
	 * ")from @virtualTable()"(from前为右括号)仍能正确识别,不得误消费内层from
	 */
	@Test
	public void nestedSubqueryWithAdjacentFrom() {
		String[] sqls = { "select (select 1 from table)from @virtualTable()",
				"select (select 1 from table) from @virtualTable()",
				"select (select 1 from table)from@virtualTable()",
				"select (select 1 from table)\nFROM @virtualTable()" };
		for (String sql : sqls) {
			String onOracle = expand(sql, "oracle");
			assertTrue(onOracle.contains(" from dual "), "oracle should expand macro:" + onOracle);
			assertEquals(2, countFrom(onOracle), "inner real from and outer dual from:" + onOracle);
			String onPg = expand(sql, "postgresql");
			assertEquals(1, countFrom(onPg), "only inner real from should remain on pg:" + onPg);
			assertEquals("select (select 1 from table)", onPg.replaceAll("\\s+", " ").trim(),
					"pg should remove only the outer from:" + onPg);
		}
	}

	/** 非from关键字不被误消费:transform/from_x等标识符内的from不触发前缀 */
	@Test
	public void fromKeywordNotMisconsumed() {
		String transform = expand("select transform v @virtualTable()", "oracle");
		assertTrue(transform.contains(" from dual "), "transform should not consume macro's from:" + transform);
		assertEquals(1, countFrom(transform), "transform should leave one from:" + transform);
		String fromX = expand("select from_x v @virtualTable()", "postgresql");
		assertEquals(0, countFrom(fromX), "from_x is not the from keyword:" + fromX);
		String nested = "select 1 from t where exists(select 1 v from @virtualTable())";
		assertEquals(2, countFrom(expand(nested, "oracle")), "outer from t and inner dual:" + nested);
		assertEquals(1, countFrom(expand(nested, "postgresql")), "outer from t should remain:" + nested);
	}

	/**
	 * update 2026-11-16 列名/标识符内含from(如address_from、from_address、addressfrom):
	 * 词边界保证其中的from不被当作前置from消费,列名原样保留,宏仍按无前置from语义展开
	 */
	@Test
	public void columnNameContainingFromNotMisconsumed() {
		String[] columns = { "address_from", "from_address", "addressfrom", "my_from_col", "form_from" };
		for (String column : columns) {
			String sql = "select " + column + " @virtualTable()";
			String onOracle = expand(sql, "oracle");
			assertEquals(1, countFrom(onOracle), column + " oracle should keep only the supplemented from:"
					+ onOracle);
			assertTrue(onOracle.contains(" from dual "), column + " should supplement from dual:" + onOracle);
			assertTrue(onOracle.contains(column), column + " column name must remain intact:" + onOracle);
			String onPg = expand(sql, "postgresql");
			assertEquals(0, countFrom(onPg), column + " pg should have no from:" + onPg);
			assertEquals("select " + column, onPg.replaceAll("\\s+", " ").trim(),
					column + " pg should leave the column untouched:" + onPg);
		}
		// 列名与宏之间零空白同样不能误判(宏自身替换会补出分隔空白)
		String attached = expand("select address_from@virtualTable()", "oracle");
		assertTrue(attached.contains("address_from"), "attached column name must remain intact:" + attached);
		assertEquals(1, countFrom(attached), "attached column should keep only the supplemented from:" + attached);
	}

	/**
	 * update 2026-11-16 from/宏判定不受字符串字面量干扰:字面量内容先掩码再匹配,
	 * 'from @virtualTable()' 不展开、内容原样保留;字面量外的真宏正常展开
	 */
	@Test
	public void literalContentNotTreatedAsFromOrMacro() {
		String literalOnly = "select 'from @virtualTable()' as tag, id from t";
		assertEquals(literalOnly, expand(literalOnly, "oracle"), "literal macro must stay intact on oracle");
		assertEquals(literalOnly, expand(literalOnly, "postgresql"), "literal macro must stay intact on pg");
		// 字面量内的宏标记保留,字面量外的真宏正常展开
		String mixed = "select '@virtualTable()' as tag, 1 v @virtualTable()";
		String onOracle = expand(mixed, "oracle");
		assertTrue(onOracle.contains("'@virtualTable()'"), "literal marker should remain:" + onOracle);
		assertTrue(onOracle.contains(" from dual "), "real macro should expand:" + onOracle);
		String onPg = expand(mixed, "postgresql");
		assertTrue(onPg.contains("'@virtualTable()'"), "literal marker should remain on pg:" + onPg);
		assertFalse(onPg.contains(" from "), "no real from should remain on pg:" + onPg);
		// 字面量内的from不被当作前置from,字面量外的宏仍按无from语义展开
		String literalFrom = "select 'from' as tag @virtualTable()";
		String oracleFrom = expand(literalFrom, "oracle");
		assertTrue(oracleFrom.contains("'from'"), "literal from should remain:" + oracleFrom);
		assertTrue(oracleFrom.contains(" from dual "), "macro should still expand:" + oracleFrom);
		// ''转义与mysql系\'转义下的字面量同样整体掩码
		String escaped = "select 'it''s from @virtualTable()' as tag from t";
		assertEquals(escaped, expand(escaped, "oracle"), "'' escaped literal should stay intact");
		String mysqlEscaped = "select 'it\\'s from @virtualTable()' as tag from t";
		assertEquals(mysqlEscaped, expand(mysqlEscaped, "mysql"),
				"mysql backslash escaped literal should stay intact");
	}

	/** 注释穿插:parseSqlToyConfig先clearMark,块注释/行注释位于from与宏之间仍能识别前置from */
	@Test
	public void commentBetweenFromAndMacro() {
		String block = SqlConfigParseUtils
				.parseSqlToyConfig("select 1 v from /* vt */ @virtualTable() where 1=1", "oracle", SqlType.search)
				.getSql("oracle");
		assertTrue(block.contains(" from dual "), "block comment should be cleared:" + block);
		assertEquals(1, countFrom(block), "block comment should keep one from:" + block);
		String line = SqlConfigParseUtils
				.parseSqlToyConfig("select 1 v from -- vt\n@virtualTable() where 1=1", "postgresql", SqlType.search)
				.getSql("postgresql");
		assertEquals(0, countFrom(line), "line comment should be cleared and from removed:" + line);
	}

	/** 前置from与别名/大小写/换行组合:三种宏拼写展开等价,且都只消费紧邻的from */
	@Test
	public void fromPrefixedMacroAliasAndCaseTolerant() {
		for (String macro : new String[] { "@virtualTable()", "@VirtualTable()", "@dual()", "@DUMMYTABLE()" }) {
			String sql = "select 1 v\nFROM\n " + macro + " where 1=1";
			String onOracle = expand(sql, "oracle");
			assertTrue(onOracle.contains(" from dual "), macro + " should expand to dual:" + onOracle);
			assertEquals(1, countFrom(onOracle), macro + " should keep exactly one from:" + onOracle);
			String onPg = expand(sql, "postgresql");
			assertFalse(onPg.contains("@"), macro + " macro should be consumed on pg:" + onPg);
			assertEquals(0, countFrom(onPg), macro + " should remove preceding from on pg:" + onPg);
		}
	}

	/** 非直接相邻的from不被误消费:外层from t保留,内层宏仍按原生语义补from/剔除 */
	@Test
	public void nonAdjacentFromNotConsumed() {
		String sql = "select 1 from t where exists(select 1 @virtualTable())";
		String onOracle = expand(sql, "oracle");
		assertEquals(2, countFrom(onOracle), "outer from and inner from dual should both remain:" + onOracle);
		String onPg = expand(sql, "postgresql");
		assertEquals(1, countFrom(onPg), "only outer from should remain on pg:" + onPg);
	}

	/**
	 * 覆盖完整性:反射穷举DBType全部int常量,经getDialect(dbType)反查方言名走真实展开路径;
	 * 剔除型方言(支持无from select/非sql方言/未识别)显式登记为空串——新增DBType未纳入
	 * 虚表决策时映射缺失,断言失败并点名常量
	 */
	@Test
	public void allDbTypesCovered() throws Exception {
		Map<Integer, String> expected = new HashMap<Integer, String>();
		// 需伪表方言(映射对齐各Dialect的VIRTUAL_TABLE常量)
		expected.put(DBType.ORACLE, " from dual ");
		expected.put(DBType.ORACLE11, " from dual ");
		expected.put(DBType.DM, " from dual ");
		expected.put(DBType.OCEANBASE, " from dual ");
		expected.put(DBType.DB2, " from sysibm.sysdummy1 ");
		expected.put(DBType.HANA, " from dummy ");
		// 剔除型:原生支持无from select,或非sql方言/未识别
		int[] stripTypes = { DBType.SQLSERVER, DBType.MYSQL, DBType.MYSQL57, DBType.DORIS, DBType.STARROCKS,
				DBType.POSTGRESQL, DBType.POSTGRESQL14, DBType.CLICKHOUSE, DBType.GAUSSDB, DBType.SQLITE,
				DBType.TIDB, DBType.KINGBASE, DBType.MONGO, DBType.ES, DBType.TDENGINE, DBType.IMPALA, DBType.H2,
				DBType.OSCAR, DBType.MOGDB, DBType.VASTBASE, DBType.OPENGAUSS, DBType.STARDB, DBType.HIGHGO,
				DBType.UNDEFINE };
		for (int dbType : stripTypes) {
			expected.put(dbType, "");
		}
		for (Field field : DBType.class.getFields()) {
			if (field.getType() != int.class) {
				continue;
			}
			int dbType = (Integer) field.get(null);
			String dialect = DataSourceUtils.getDialect(dbType);
			assertNotNull(dialect, field.getName() + " has no dialect name");
			String expectVirtual = expected.get(dbType);
			assertNotNull(expectVirtual,
					field.getName() + "(" + dbType + ") 未在虚表映射登记,请为新增数据库类型明确伪表或剔除策略");
			String sql = expand(dialect);
			if (expectVirtual.isEmpty()) {
				assertFalse(sql.contains(" dual ") || sql.contains(" sysdummy1 ") || sql.contains(" from dummy "),
						field.getName() + "(" + dialect + ") should strip macro:" + sql);
			} else {
				assertTrue(sql.contains(expectVirtual),
						field.getName() + "(" + dialect + ") should expand to" + expectVirtual + ":" + sql);
			}
			assertFalse(sql.contains("@virtualTable"), field.getName() + " macro should be consumed:" + sql);
			// update 2026-11-16 前置from写法同样按DBType穷举:伪表族单from、剔除族零from
			String fromPrefixedSql = expand("select 1 v from @virtualTable()", dialect);
			assertFalse(fromPrefixedSql.contains("@virtualTable"),
					field.getName() + " from-prefixed macro should be consumed:" + fromPrefixedSql);
			if (expectVirtual.isEmpty()) {
				assertEquals(0, countFrom(fromPrefixedSql), field.getName() + "(" + dialect
						+ ") from-prefixed should leave no from:" + fromPrefixedSql);
			} else {
				assertTrue(fromPrefixedSql.contains(expectVirtual), field.getName() + "(" + dialect
						+ ") from-prefixed should expand to" + expectVirtual + ":" + fromPrefixedSql);
				assertEquals(1, countFrom(fromPrefixedSql), field.getName() + "(" + dialect
						+ ") from-prefixed should keep exactly one from:" + fromPrefixedSql);
			}
		}
	}

	/** 无宏sql原样返回(含@字符的普通sql不受影响) */
	@Test
	public void sqlWithoutMacroUnchanged() {
		String sql = "select t.staff_name@email from sys_staff_info t where t.email like 'a@%'";
		assertEquals(sql, SqlConfigParseUtils.expandVirtualTable(sql, "oracle"),
				"sql without @virtualTable() should be unchanged");
	}

	/** 大小写与空白容错:@VirtualTable( ) 与 @virtualtable() 均识别 */
	@Test
	public void caseAndSpaceTolerant() {
		String sql = SqlConfigParseUtils
				.expandVirtualTable("select 1 cnt @VirtualTable( ) union select 2 from t", "oracle");
		assertTrue(sql.contains(" from dual "), "case-insensitive macro should expand:" + sql);
	}

	/**
	 * 别名兼容:@virtualTable(正式名)/@dual/@dummyTable共用同一展开逻辑,各种大小写变体
	 * 在oracle(展开伪表)与postgresql(剔除)两种方言下行为一致
	 */
	@Test
	public void aliasAndCaseVariantsEquivalent() {
		String[] macros = { "@virtualTable()", "@VirtualTable()", "@VIRTUALTABLE()", "@virtualtable()",
				"@dual()", "@Dual()", "@DUAL()", "@dual( )", "@dummyTable()", "@DummyTable()", "@DUMMYTABLE()",
				"@dummytable()" };
		for (String macro : macros) {
			String sql = "select '0' id,'全局' name " + macro + " union select id,name from t";
			String onOracle = SqlConfigParseUtils.expandVirtualTable(sql, "oracle");
			assertTrue(onOracle.contains(" from dual "), macro + " should expand to dual on oracle:" + onOracle);
			assertFalse(onOracle.contains("@"), macro + " macro should be consumed:" + onOracle);
			String onPg = SqlConfigParseUtils.expandVirtualTable(sql, "postgresql");
			assertFalse(onPg.contains("@"), macro + " macro should be stripped on pg:" + onPg);
			assertFalse(onPg.contains(" dual "), macro + " no dual expected on pg:" + onPg);
		}
	}
}
