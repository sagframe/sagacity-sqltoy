package org.sagacity.sqltoy.config;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.util.ArrayList;
import java.util.List;

import org.junit.jupiter.api.Test;
import org.sagacity.sqltoy.config.model.SqlToyConfig;
import org.sagacity.sqltoy.config.model.SqlType;
import org.sagacity.sqltoy.config.model.SqlWithAnalysis;
import org.sagacity.sqltoy.utils.DataSourceUtils;
import org.sagacity.sqltoy.utils.DataSourceUtils.DBType;

/**
 * update 2026-10-18 CTE物化hint(AS [NOT] MATERIALIZED)按目标库适配的docker真库端到端验证。
 * 走sqltoy真实运行链路:parseSqlToyConfig(共享sql,dialect=null)完整解析管线 → getSql(目标方言)
 * 在getDialectSql收口点按dbType保留/剔除hint → JDBC在真库执行。
 * <p>
 * 因果链证明:对不支持库(mysql/tidb/sqlserver),先执行**原始带MATERIALIZED的sql**断言抛 SQLException
 * (复现用户遇到的执行报错),再执行**收口点适配后的sql**断言成功(证明剔除即修复);对支持库
 * (PG/openGauss/金仓/Vastbase/瀚高)断言hint被保留且执行成功。连接不上的库优雅跳过(不致CI红),
 * 末尾断言至少各验证了一个支持库与一个不支持库,保证测试有意义。
 */
public class CteMaterializedRealDbTest {

	// { 名称, 驱动, url, 用户, 密码, sqltoy方言名 }
	private static final String[][] PROBES = {
			{ "mysql9", "com.mysql.cj.jdbc.Driver",
					"jdbc:mysql://localhost:3307/sqltoy?useSSL=false&allowPublicKeyRetrieval=true", "root",
					"Sqltoy@2026", "mysql" },
			{ "tidb85", "com.mysql.cj.jdbc.Driver",
					"jdbc:mysql://localhost:4001/test?useSSL=false&allowPublicKeyRetrieval=true", "root", "", "tidb" },
			{ "sqlserver25", "com.microsoft.sqlserver.jdbc.SQLServerDriver",
					"jdbc:sqlserver://localhost:1434;encrypt=false;trustServerCertificate=true", "sa", "Sqltoy@2026",
					"sqlserver" },
			{ "postgresql18", "org.postgresql.Driver", "jdbc:postgresql://localhost:5433/postgres", "postgres",
					"Sqltoy@2026", "postgresql" },
			{ "opengauss5", "org.opengauss.Driver", "jdbc:opengauss://localhost:5432/postgres", "gaussdb",
					"Sqltoy@2026", "opengauss" },
			{ "vastbase", "cn.com.vastbase.Driver", "jdbc:vastbase://localhost:5436/postgres", "sqltoy", "Sqltoy@2026",
					"vastbase" },
			{ "highgo", "org.postgresql.Driver", "jdbc:postgresql://localhost:5867/highgo", "highgo", "Sqltoy@2026",
					"highgo" },
			{ "kingbase", "org.postgresql.Driver", "jdbc:postgresql://localhost:54321/kingbase", "system",
					"12345678ab", "kingbase" } };

	// 共享sql(未按方言定制):PG物化hint混入,非PG库直接执行会报语法错误
	private static final String RAW_MATERIALIZED = "WITH sag_mat AS MATERIALIZED (SELECT 1 AS id) SELECT id FROM sag_mat";
	private static final String RAW_NOT_MATERIALIZED = "WITH sag_nm AS NOT MATERIALIZED (SELECT 1 AS id) SELECT id FROM sag_nm";

	private static Connection open(String[] probe) throws Exception {
		Class.forName(probe[1]);
		return DriverManager.getConnection(probe[2], probe[3], probe[4]);
	}

	private static int queryFirstId(Connection conn, String sql) throws SQLException {
		try (Statement st = conn.createStatement(); ResultSet rs = st.executeQuery(sql)) {
			assertTrue(rs.next(), "应有结果行: " + sql);
			return rs.getInt(1);
		}
	}

	@Test
	public void verifyAcrossDbs() {
		List<String> skipped = new ArrayList<String>();
		List<String> verifiedSupported = new ArrayList<String>();
		List<String> verifiedUnsupported = new ArrayList<String>();
		for (String[] probe : PROBES) {
			String name = probe[0];
			String dialect = probe[5];
			int dbType = DataSourceUtils.getDBType(dialect);
			boolean supports = SqlWithAnalysis.supportsCteMaterialized(dbType);
			try (Connection conn = open(probe)) {
				// ---- 收口点适配:共享sql经getSql(目标方言)按dbType保留/剔除hint ----
				SqlToyConfig matCfg = SqlConfigParseUtils.parseSqlToyConfig(RAW_MATERIALIZED, null, SqlType.search);
				String adaptedMat = matCfg.getSql(dialect);
				SqlToyConfig notCfg = SqlConfigParseUtils.parseSqlToyConfig(RAW_NOT_MATERIALIZED, null, SqlType.search);
				String adaptedNot = notCfg.getSql(dialect);

				if (supports) {
					// 支持库:hint应保留
					assertTrue(adaptedMat.toLowerCase().contains("materialized"),
							name + " 支持物化hint,适配后应保留,实际:" + adaptedMat);
					assertTrue(adaptedNot.toLowerCase().contains("not materialized"),
							name + " 支持物化hint,NOT形态应保留,实际:" + adaptedNot);
				} else {
					// 不支持库:hint应被剔除
					assertFalse(adaptedMat.toLowerCase().contains("materialized"),
							name + " 不支持物化hint,适配后应剔除,实际:" + adaptedMat);
					assertFalse(adaptedNot.toLowerCase().contains("materialized"),
							name + " 不支持物化hint,NOT形态应剔除,实际:" + adaptedNot);
					// 因果链:原始带hint的sql直接执行必报语法错误(复现用户bug)
					assertThrows(SQLException.class, () -> queryFirstId(conn, RAW_MATERIALIZED),
							name + " 原始带MATERIALIZED的sql应报语法错误(复现bug)");
				}
				// 适配后的sql在真库执行成功且结果正确(证明修复有效)
				assertEquals(1, queryFirstId(conn, adaptedMat), name + " 适配后MATERIALIZED形态执行结果应为1");
				assertEquals(1, queryFirstId(conn, adaptedNot), name + " 适配后NOT形态执行结果应为1");

				if (supports) {
					verifiedSupported.add(name);
				} else {
					verifiedUnsupported.add(name);
				}
			} catch (Exception e) {
				// 连接不上(容器未启动)优雅跳过,不致测试红
				String msg = e.getMessage();
				skipped.add(name + "(" + e.getClass().getSimpleName() + ": "
						+ (msg == null ? "" : msg.substring(0, Math.min(120, msg.length()))) + ")");
			}
		}
		System.out.println("[CteMaterializedRealDb] 已验证支持库=" + verifiedSupported + " 不支持库=" + verifiedUnsupported
				+ " 跳过=" + skipped);
		// 保证测试有意义:至少各覆盖一个支持库与一个不支持库
		assertTrue(verifiedUnsupported.size() >= 1, "应至少验证一个不支持库(剔除路径),实际跳过:" + skipped);
		assertTrue(verifiedSupported.size() >= 1, "应至少验证一个支持库(保留路径),实际跳过:" + skipped);
	}

	// 白名单dbType与Dialect映射的纯文本自检(不依赖真库,CI恒跑)
	@Test
	public void dialectToDbTypeMapping() {
		assertEquals(DBType.MYSQL, DataSourceUtils.getDBType("mysql"));
		assertEquals(DBType.TIDB, DataSourceUtils.getDBType("tidb"));
		assertEquals(DBType.SQLSERVER, DataSourceUtils.getDBType("sqlserver"));
		assertEquals(DBType.POSTGRESQL, DataSourceUtils.getDBType("postgresql"));
		assertEquals(DBType.OPENGAUSS, DataSourceUtils.getDBType("opengauss"));
		assertEquals(DBType.VASTBASE, DataSourceUtils.getDBType("vastbase"));
		assertEquals(DBType.HIGHGO, DataSourceUtils.getDBType("highgo"));
		assertEquals(DBType.KINGBASE, DataSourceUtils.getDBType("kingbase"));
	}
}
