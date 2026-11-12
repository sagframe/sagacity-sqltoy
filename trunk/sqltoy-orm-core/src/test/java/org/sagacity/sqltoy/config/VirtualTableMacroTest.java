package org.sagacity.sqltoy.config;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.lang.reflect.Field;
import java.util.HashMap;
import java.util.Map;

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
