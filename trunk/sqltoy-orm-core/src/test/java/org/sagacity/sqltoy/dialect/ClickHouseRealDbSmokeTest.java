package org.sagacity.sqltoy.dialect;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.junit.jupiter.api.Assertions.assertNotNull;

import java.io.File;
import java.io.FileInputStream;
import java.io.InputStream;
import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.Statement;
import java.util.Properties;

import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.sagacity.sqltoy.plugins.function.FunctionUtils;

/**
 * ClickHouse真实库冒烟:验证本轮Now补的clickhouse→now()映射,以及CH目标的
 * IGNORE边界(未做CH专属映射的函数原样保留,执行报错属已知文档化缺口)。
 * 连接配置从target/clickhouse-probe.properties读取。
 */
public class ClickHouseRealDbSmokeTest {

	private static Connection conn;

	private static boolean available = false;

	@BeforeAll
	public static void init() throws Exception {
		File cfg = new File("target/clickhouse-probe.properties");
		if (!cfg.exists()) {
			return;
		}
		Properties p = new Properties();
		try (InputStream in = new FileInputStream(cfg)) {
			p.load(in);
		}
		Class.forName("com.clickhouse.jdbc.ClickHouseDriver");
		conn = DriverManager.getConnection(p.getProperty("url"));
		try (Statement st = conn.createStatement()) {
			st.execute("drop table if exists sqltoy_probe_t1");
			st.execute("create table sqltoy_probe_t1 (id UInt8, name String, score Decimal(10,2), "
					+ "create_time DateTime) engine = MergeTree() order by id");
			st.execute("insert into sqltoy_probe_t1 values (1,'admin',88.5,'2026-01-15 10:30:00')");
			st.execute("insert into sqltoy_probe_t1 values (2,'user',72.0,'2026-03-20 14:00:00')");
		}
		FunctionUtils.setFunctionConverts(java.util.Arrays.asList("default"));
		available = true;
	}

	@AfterAll
	public static void destroy() throws Exception {
		if (conn != null) {
			try (Statement st = conn.createStatement()) {
				st.execute("drop table if exists sqltoy_probe_t1");
			} catch (Exception e) {
				// ignore
			}
			conn.close();
		}
	}

	private String convert(String sql, String dialect) {
		return FunctionUtils.getDialectSql(sql, dialect);
	}

	private Object querySingle(String sql) throws Exception {
		try (Statement st = conn.createStatement(); ResultSet rs = st.executeQuery(sql)) {
			rs.next();
			return rs.getObject(1);
		} catch (Exception e) {
			Throwable root = e;
			while (root.getCause() != null) {
				root = root.getCause();
			}
			System.out.println("[CH_ERR] " + root.getMessage());
			throw e;
		}
	}

	@Test
	public void chAvailable() {
		org.junit.jupiter.api.Assumptions.assumeTrue(available, "clickhouse探针环境不可用,跳过");
	}

	/** Now的clickhouse映射:sysdate/now/getdate均转now()并正确执行 */
	@Test
	public void nowConversions() throws Exception {
		org.junit.jupiter.api.Assumptions.assumeTrue(available, "clickhouse探针环境不可用,跳过");
		assertEquals("select now() from sqltoy_probe_t1 where id=1".replaceAll("\\s+", " "),
				convert("select sysdate from sqltoy_probe_t1 where id=1", "clickhouse").replaceAll("\\s+", " ")
						.trim().isEmpty() ? "" : convert("select sysdate from sqltoy_probe_t1 where id=1", "clickhouse")
								.replaceAll("\\s+", " ").trim(),
				"sysdate应转now()");
		assertEquals("admin", querySingle(convert(
				"select name from sqltoy_probe_t1 where id=1 and create_time > now() - INTERVAL 1 YEAR", "clickhouse"))
				.toString());
	}

	/** CH原生函数在IGNORE下原样执行:concat/length/substr/if */
	@Test
	public void chNativeFunctions() throws Exception {
		org.junit.jupiter.api.Assumptions.assumeTrue(available, "clickhouse探针环境不可用,跳过");
		assertEquals("admin-x", querySingle(convert(
				"select concat(name,'-','x') from sqltoy_probe_t1 where id=1", "clickhouse")).toString());
		assertEquals(5, ((Number) querySingle(convert("select length(name) from sqltoy_probe_t1 where id=1",
				"clickhouse"))).intValue());
		assertEquals("adm", querySingle(convert("select substr(name,1,3) from sqltoy_probe_t1 where id=1",
				"clickhouse")).toString());
		assertEquals("high", querySingle(convert(
				"select if(score > 80, 'high', 'low') from sqltoy_probe_t1 where id=1", "clickhouse")).toString());
	}

	/** 已知文档化缺口:nvl/date_format/group_concat在CH目标为IGNORE原样(非法),仅断言不破坏原语句 */
	@Test
	public void chDocumentedGaps() throws Exception {
		org.junit.jupiter.api.Assumptions.assumeTrue(available, "clickhouse探针环境不可用,跳过");
		String nvl = convert("select nvl(name,'none') from sqltoy_probe_t1 where id=1", "clickhouse");
		assertTrue(nvl.contains("nvl("), "nvl在CH目标应为IGNORE原样(文档化缺口)");
		String gf = convert("select group_concat(name) from sqltoy_probe_t1", "clickhouse");
		assertTrue(gf.contains("group_concat("), "group_concat在CH目标应为IGNORE原样(文档化缺口)");
	}
}
