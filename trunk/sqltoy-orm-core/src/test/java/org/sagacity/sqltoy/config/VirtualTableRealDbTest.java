package org.sagacity.sqltoy.config;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.Statement;
import java.util.ArrayList;
import java.util.List;

import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.sagacity.sqltoy.config.model.SqlType;

/**
 * @virtualTable()虚表宏的docker真库执行验证:经parseSqlToyConfig完整解析管线(宏展开→
 * 函数/保留字转换)后JDBC执行,覆盖两类方言(oracle/db2/hana/dm展开伪表族与
 * pg/mysql/sqlserver等剔除宏族)×三种形态(字面量行/union常量兜底行/别名@dual与@dummyTable)。
 * 沿用AllFunctionsAllDbsRealDbSmokeTest的PROBES连接矩阵(去掉gbase)
 */
public class VirtualTableRealDbTest {

	private static final String[][] PROBES = {
			{ "oracle", "oracle.jdbc.OracleDriver", "jdbc:oracle:thin:@//localhost:1521/FREEPDB1", "system",
					"Sqltoy@2026", "oracle" },
			{ "db2", "com.ibm.db2.jcc.DB2Driver", "jdbc:db2://localhost:50000/SQLTOYDB", "db2inst1", "Sqltoy@2026",
					"db2" },
			{ "hana", "com.sap.db.jdbc.Driver", "jdbc:sap://localhost:39041/?databaseName=HXE", "SYSTEM",
					"Sqltoy2026", "hana" },
			{ "dm", "dm.jdbc.driver.DmDriver", "jdbc:dm://localhost:5236?schema=SYSDBA", "SYSDBA", "Sqltoy@2026",
					"dm" },
			{ "mysql", "com.mysql.cj.jdbc.Driver",
					"jdbc:mysql://localhost:3307/sqltoy?useSSL=false&allowPublicKeyRetrieval=true", "root",
					"Sqltoy@2026", "mysql" },
			{ "postgresql", "org.postgresql.Driver", "jdbc:postgresql://localhost:5433/postgres", "postgres",
					"Sqltoy@2026", "postgresql" },
			{ "highgo", "org.postgresql.Driver", "jdbc:postgresql://localhost:5867/highgo", "highgo", "Sqltoy@2026",
					"highgo" },
			{ "vastbase", "cn.com.vastbase.Driver", "jdbc:vastbase://localhost:5436/postgres", "sqltoy",
					"Sqltoy@2026", "vastbase" },
			{ "opengauss", "org.opengauss.Driver", "jdbc:opengauss://localhost:5432/postgres", "gaussdb",
					"Sqltoy@2026", "opengauss" },
			{ "sqlserver", "com.microsoft.sqlserver.jdbc.SQLServerDriver",
					"jdbc:sqlserver://localhost:1434;encrypt=false;trustServerCertificate=true", "sa", "Sqltoy@2026",
					"sqlserver" } };

	@BeforeAll
	public static void init() throws Exception {
		List<String> failures = new ArrayList<String>();
		for (String[] probe : PROBES) {
			try (Connection conn = open(probe)) {
				try (Statement st = conn.createStatement()) {
					execQuiet(st, probe, "drop table vt_probe_t");
					String nameType = nameType(probe);
					st.execute("create table vt_probe_t (id " + nameType + ", name " + nameType + ")");
					st.execute("insert into vt_probe_t values ('1','研发')");
					st.execute("insert into vt_probe_t values ('2','测试')");
				}
			} catch (Exception e) {
				failures.add(probe[0] + " init失败: " + e.getMessage());
			}
		}
		assertTrue(failures.isEmpty(), "init失败: " + failures);
	}

	@AfterAll
	public static void destroy() throws Exception {
		for (String[] probe : PROBES) {
			try (Connection conn = open(probe); Statement st = conn.createStatement()) {
				execQuiet(st, probe, "drop table vt_probe_t");
			} catch (Exception e) {
				// ignore
			}
		}
	}

	private static Connection open(String[] probe) throws Exception {
		Class.forName(probe[1]);
		return DriverManager.getConnection(probe[2], probe[3], probe[4]);
	}

	private static String nameType(String[] probe) {
		// hana建议nvarchar;db2的varchar默认字节语义对中文需放大;统一用足长度的字符类型
		if ("hana".equals(probe[0])) {
			return "nvarchar(30)";
		}
		if ("db2".equals(probe[0])) {
			return "varchar(60)";
		}
		return "varchar(30)";
	}

	private static void execQuiet(Statement st, String[] probe, String sql) {
		try {
			st.execute(sql);
		} catch (Exception e) {
			// ignore
		}
	}

	private List<String> query(String[] probe, String sql) throws Exception {
		return queryRaw(probe[5], sql);
	}

	private List<String> queryRaw(String dialect, String sql) throws Exception {
		String converted = SqlConfigParseUtils.parseSqlToyConfig(sql, dialect, SqlType.search).getSql(dialect);
		try (Connection conn = openByDialect(dialect);
				PreparedStatement pst = conn.prepareStatement(converted);
				ResultSet rs = pst.executeQuery()) {
			List<String> rows = new ArrayList<String>();
			while (rs.next()) {
				rows.add(rs.getString(1) + "|" + rs.getString(2));
			}
			return rows;
		}
	}

	private static Connection openByDialect(String dialect) throws Exception {
		for (String[] probe : PROBES) {
			if (probe[5].equals(dialect)) {
				return open(probe);
			}
		}
		throw new IllegalStateException("no probe for dialect " + dialect);
	}

	/** sqlserver默认排序规则仅ASCII(中文字面量变??),该库常量用ASCII */
	private static String literal(String probeName, String cn, String ascii) {
		return "sqlserver".equals(probeName) ? ascii : cn;
	}

	/** 形态1:字面量单行select,伪表族展开from dual系、无from族剔除宏,均应返回1行常量 */
	@Test
	public void literalRowSelect() throws Exception {
		List<String> failures = new ArrayList<String>();
		for (String[] probe : PROBES) {
			String word = literal(probe[0], "全局", "global");
			String sql = "select '0' as vid,'" + word + "' as vname @virtualTable()";
			List<String> rows = query(probe, sql);
			if (rows.size() != 1 || !("0|" + word).equals(rows.get(0))) {
				failures.add(probe[0] + " => " + rows);
			}
		}
		assertTrue(failures.isEmpty(), "字面量行: " + failures);
	}

	/** 形态2:常量兜底行union真实表(宏的文档主场景),应3行且含'0|全局'兜底行 */
	@Test
	public void unionConstantFallbackRow() throws Exception {
		List<String> failures = new ArrayList<String>();
		for (String[] probe : PROBES) {
			boolean ss = "sqlserver".equals(probe[0]);
			String word = literal(probe[0], "全局", "global");
			String n1 = ss ? "dev" : "研发";
			String n2 = ss ? "test" : "测试";
			String sql = "select '0' vid,'" + word + "' vname @virtualTable() "
					+ "union all select id,name from vt_probe_t order by vid";
			try (Connection conn = open(probe); Statement st = conn.createStatement()) {
				execQuiet(st, probe, "delete from vt_probe_t");
				st.execute("insert into vt_probe_t values ('1','" + n1 + "')");
				st.execute("insert into vt_probe_t values ('2','" + n2 + "')");
			}
			try {
				List<String> rows = query(probe, sql);
				if (rows.size() != 3 || !rows.contains("0|" + word) || !rows.contains("1|" + n1)
						|| !rows.contains("2|" + n2)) {
					failures.add(probe[0] + " => " + rows);
				}
			} catch (Exception e) {
				failures.add(probe[0] + " 异常: " + e.getMessage());
			}
		}
		assertTrue(failures.isEmpty(), "union兜底行: " + failures);
	}

	/** 形态3:别名@dual()/@dummyTable()与@virtualTable()三拼写等价(oracle/db2/pg三代表库) */
	@Test
	public void aliasesEquivalent() throws Exception {
		String[] aliasProbe = null, vtProbe = null;
		for (String[] p : PROBES) {
			if ("oracle".equals(p[0])) {
				aliasProbe = p;
			}
			if ("postgresql".equals(p[0])) {
				vtProbe = p;
			}
		}
		assertEquals("0|全局",
				query(aliasProbe, "select '0' as vid,'全局' as vname @dual()").get(0), "oracle的@dual()别名");
		assertEquals("0|全局",
				query(aliasProbe, "select '0' as vid,'全局' as vname @dummyTable()").get(0), "oracle的@dummyTable()别名");
		assertEquals("0|全局",
				query(vtProbe, "select '0' as vid,'全局' as vname @dual()").get(0), "pg的@dual()别名剔除宏");
	}

	/** 形态4:select 1 @virtualTable() where/exists——剔除族无from带条件、伪表族from dual系带条件,11库语义等价 */
	@Test
	public void whereAndExistsWithoutFrom() throws Exception {
		List<String> failures = new ArrayList<String>();
		for (String[] probe : PROBES) {
			try (Connection conn = open(probe); Statement st = conn.createStatement()) {
				execQuiet(st, probe, "drop table vt_where_t");
				st.execute("create table vt_where_t (id int)");
				st.execute("insert into vt_where_t values (1)");
			}
			// A: where 1=1 应1行;B: exists假应0行;C: exists真应1行
			String[][] cases = { { "A", "select 1 v @virtualTable() where 1=1", "1", "1" },
					{ "B", "select 1 v @virtualTable() where exists (select 1 from vt_where_t where 1=0)", "0", null },
					{ "C", "select 1 v @virtualTable() where exists (select 1 from vt_where_t where id=1)", "1", "1" } };
			for (String[] c : cases) {
				String dialect = probe[5];
				String converted = SqlConfigParseUtils.parseSqlToyConfig(c[1], dialect, SqlType.search).getSql(dialect);
				try (Connection conn = open(probe);
						PreparedStatement pst = conn.prepareStatement(converted);
						ResultSet rs = pst.executeQuery()) {
					int got = 0;
					String val = null;
					while (rs.next()) {
						got++;
						val = rs.getString(1);
					}
					if (got != Integer.parseInt(c[2]) || (val != null && !val.equals(c[3]))) {
						failures.add(probe[0] + " " + c[0] + " rows=" + got + " v=" + val + " sql=" + converted);
					}
				} catch (Exception e) {
					failures.add(probe[0] + " " + c[0] + " 异常: " + e.getMessage() + " sql=" + converted);
				}
			}
			try (Connection conn = open(probe); Statement st = conn.createStatement()) {
				execQuiet(st, probe, "drop table vt_where_t");
			}
		}
		assertTrue(failures.isEmpty(), "where/exists形态: " + failures);
	}

	/** 宏与函数转换共存:select uuid()族同语句,展开顺序不冲突(oracle为代表) */
	@Test
	public void macroWithFunctionConvert() throws Exception {
		String[] oracle = PROBES[0];
		String sql = "select '0' as vid, to_char(sysdate,'yyyy-MM-dd') as vname @virtualTable()";
		List<String> rows = query(oracle, sql);
		assertEquals(1, rows.size(), "宏与函数共存单行");
		assertTrue(rows.get(0).startsWith("0|"), "首列常量保留: " + rows.get(0));
		assertTrue(rows.get(0).length() > 11, "次列应为日期形态: " + rows.get(0));
	}
}
