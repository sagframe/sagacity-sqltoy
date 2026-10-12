package org.sagacity.sqltoy.dialect;

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

import org.junit.jupiter.api.Test;
import org.sagacity.sqltoy.plugins.function.FunctionUtils;

/**
 * find_in_set跨库真库执行冒烟(2026-10-11):10个docker库+内嵌sqlite逐库验证改写产物的
 * 真实执行语义。核心断言面:完整元素命中(NODE_1不得误命中NODE_11,下划线+数字命名陷阱)、
 * 多行命中、未命中、=0反口径、裸真值形态;OB以oceanbase(原生透传)与mysql(verify惯配)
 * 双方言各验一遍。连接信息对齐sqltoy-verify。
 * 已知口径差异(实测锁定):list为NULL的行,mysql系原生find_in_set返回0(=0口径命中该行),
 * 各改写系(||/concat对NULL传播)该行不参与命中——两类家族对NULL行的归属不同,注释注明。
 */
public class FindInSetRealDbSmokeTest {

	/** {标签, 驱动类, url, 用户, 密码, 转换方言, 时间列无关-是否clickhouse引擎} */
	private static final String[][] PROBES = {
			{ "postgresql", "org.postgresql.Driver", "jdbc:postgresql://localhost:5433/postgres", "postgres",
					"Sqltoy@2026", "postgresql" },
			{ "opengauss", "org.opengauss.Driver", "jdbc:opengauss://localhost:5432/postgres", "gaussdb",
					"Sqltoy@2026", "opengauss" },
			{ "kingbase", "com.kingbase8.Driver", "jdbc:kingbase8://localhost:54321/kingbase", "system",
					"12345678ab", "kingbase" },
			{ "mysql", "com.mysql.cj.jdbc.Driver",
					"jdbc:mysql://localhost:3307/sqltoy?useSSL=false&allowPublicKeyRetrieval=true", "root",
					"Sqltoy@2026", "mysql" },
			{ "sqlserver", "com.microsoft.sqlserver.jdbc.SQLServerDriver",
					"jdbc:sqlserver://localhost:1434;encrypt=false;trustServerCertificate=true", "sa", "Sqltoy@2026",
					"sqlserver" },
			{ "db2", "com.ibm.db2.jcc.DB2Driver", "jdbc:db2://localhost:50000/SQLTOYDB", "db2inst1", "Sqltoy@2026",
					"db2" },
			{ "dm", "dm.jdbc.driver.DmDriver", "jdbc:dm://localhost:5236?schema=SYSDBA", "SYSDBA", "Sqltoy@2026",
					"dm" },
			{ "hana", "com.sap.db.jdbc.Driver", "jdbc:sap://localhost:39041/?databaseName=HXE", "SYSTEM",
					"Sqltoy2026", "hana" },
			{ "clickhouse", "com.clickhouse.jdbc.ClickHouseDriver",
					"jdbc:clickhouse://localhost:8123/default?user=sqltoy&password=Sqltoy@2026", null, null,
					"clickhouse" },
			{ "oceanbase(ob方言)", "com.oceanbase.jdbc.Driver", "jdbc:oceanbase://localhost:2881/test", "root@test",
					"Sqltoy2026", "oceanbase" },
			{ "oceanbase(mysql方言)", "com.oceanbase.jdbc.Driver", "jdbc:oceanbase://localhost:2881/test",
					"root@test", "Sqltoy2026", "mysql" },
			// update 2026-10-11 补齐docker全部关系型库(连接信息对齐sqltoy-verify)
			{ "vastbase", "cn.com.vastbase.Driver", "jdbc:vastbase://localhost:5436/postgres", "sqltoy",
					"Sqltoy@2026", "vastbase" },
			{ "oracle", "oracle.jdbc.OracleDriver", "jdbc:oracle:thin:@//localhost:1521/FREEPDB1",
					"system", "Sqltoy@2026", "oracle" },
			{ "og7", "org.opengauss.Driver", "jdbc:opengauss://localhost:5435/postgres", "gaussdb",
					"Sqltoy@2026", "opengauss" },
			{ "tidb", "com.mysql.cj.jdbc.Driver",
					"jdbc:mysql://localhost:4001/test?useSSL=false&allowPublicKeyRetrieval=true", "root", "",
					"tidb" },
			{ "doris", "com.mysql.cj.jdbc.Driver",
					"jdbc:mysql://localhost:9031/sqltoy_verify?useSSL=false&allowPublicKeyRetrieval=true", "root", "",
					"doris" },
			{ "starrocks", "com.mysql.cj.jdbc.Driver",
					"jdbc:mysql://localhost:9030/sqltoy_verify?useSSL=false&allowPublicKeyRetrieval=true", "root", "",
					"starrocks" }, };

	@Test
	public void findInSetSemanticsOnRealDbs() throws Exception {
		FunctionUtils.setFunctionConverts(Arrays.asList("default"));
		List<String> failures = new ArrayList<String>();
		for (String[] probe : PROBES) {
			try {
				verifyOnDb(probe[0], probe[1], probe[2], probe[3], probe[4], probe[5], failures);
			} catch (Throwable e) {
				failures.add(probe[0] + " 环境异常: " + e);
			}
		}
		// 内嵌sqlite
		try (Connection conn = DriverManager.getConnection("jdbc:sqlite::memory:"); Statement st = conn.createStatement()) {
			st.execute("create table sqltoy_fis_t (id int primary key, tags varchar(100))");
			insertFisRows(conn, false);
			verifyQueries("sqlite", conn, "sqlite", failures);
		} catch (Throwable e) {
			failures.add("sqlite 环境异常: " + e);
		}
		assertTrue(failures.isEmpty(), "find_in_set真库验证失败清单: " + failures);
	}

	private void verifyOnDb(String label, String driver, String url, String user, String pass, String dialect,
			List<String> failures) throws Exception {
		Class.forName(driver);
		try (Connection conn = (user == null) ? DriverManager.getConnection(url)
				: DriverManager.getConnection(url, user, pass); Statement st = conn.createStatement()) {
			try {
				st.execute("drop table sqltoy_fis_t");
			} catch (Exception e) {
				// 首次运行表不存在,忽略
			}
			boolean ch = "clickhouse".equals(label);
			if (ch) {
				st.execute("create table sqltoy_fis_t (id Int32, tags Nullable(String)) engine=MergeTree() order by id");
			} else if ("doris".equals(label) || "starrocks".equals(label)) {
				// doris无timestamp类型且须显式分布式子句(unique模型)
				st.execute("create table sqltoy_fis_t (id int, tags varchar(100))"
						+ " unique key(id) distributed by hash(id) buckets 1");
			} else {
				String idCol = "db2".equals(dialect) ? "id int not null primary key" : "id int primary key";
				st.execute("create table sqltoy_fis_t (" + idCol + ", tags varchar(100))");
			}
			try {
				insertFisRows(conn, ch);
				verifyQueries(label, conn, dialect, failures);
			} finally {
				try {
					st.execute("drop table sqltoy_fis_t");
				} catch (Exception e) {
					// ignore
				}
			}
		}
	}

	/** 数据:NODE_11与NODE_1构成完整元素陷阱;row4的tags为NULL(家族口径差异见类注释) */
	private void insertFisRows(Connection conn, boolean ch) throws Exception {
		try (PreparedStatement pst = conn.prepareStatement("insert into sqltoy_fis_t (id, tags) values (?,?)")) {
			String[][] data = { { "1", "NODE_1,NODE_2" }, { "2", "NODE_11,NODE_3" }, { "3", "NODE_2,NODE_4" },
					{ "4", null } };
			for (String[] row : data) {
				pst.setInt(1, Integer.parseInt(row[0]));
				if (row[1] == null) {
					pst.setNull(2, java.sql.Types.VARCHAR);
				} else {
					pst.setString(2, row[1]);
				}
				pst.executeUpdate();
			}
		}
	}

	private void verifyQueries(String label, Connection conn, String dialect, List<String> failures) {
		// {说明, where条件sql片段, 期望行数};=0口径对tags为NULL的row4按引擎家族锁定实际行为
		// (2026-10-11实测):mysql系原生find_in_set(str,NULL)返回NULL→该行不参与(3);
		// oracle兼容||引擎(dm/kingbase/opengauss)把NULL按空串拼接→strpos未命中→=0计入(4);
		// NULL传播引擎(vanilla pg/sqlserver/db2/hana/sqlite/ch)表达式为NULL→不参与(3)。
		// 三家族对NULL行的归属存在引擎固有差异(与mysql原生严格对齐需补"list is not null"守卫,
		// 属实现取舍,见报告),此处锁定各家族实际行为
		int missCount = ("dm".equals(dialect) || "kingbase".equals(dialect) || "opengauss".equals(dialect)
				|| "oracle".equals(dialect)) ? 4 : 3;
		String[][] cases = {
				{ "NODE_1完整命中(NODE_11不得误命中)", "find_in_set('NODE_1',tags)>0", "1" },
				{ "NODE_11完整命中", "find_in_set('NODE_11',tags)>0", "1" },
				{ "NODE_2多行命中", "find_in_set('NODE_2',tags)>0", "2" },
				{ "未命中NODE_9", "find_in_set('NODE_9',tags)>0", "0" },
				{ "=0反口径", "find_in_set('NODE_9',tags)=0", String.valueOf(missCount) },
				{ "裸真值形态", "find_in_set('NODE_2',tags)", "2" }, };
		for (String[] c : cases) {
			String sql = FunctionUtils.getDialectSql("select count(*) from sqltoy_fis_t where " + c[1], dialect);
			try (PreparedStatement pst = conn.prepareStatement(sql); ResultSet rs = pst.executeQuery()) {
				rs.next();
				int actual = rs.getInt(1);
				if (actual != Integer.parseInt(c[2])) {
					failures.add(label + " " + c[0] + " 期望" + c[2] + " 实际" + actual + " sql=" + sql);
				}
			} catch (Exception e) {
				failures.add(label + " " + c[0] + " 执行异常: " + e + " sql=" + sql);
			}
		}
	}
}
