package org.sagacity.sqltoy.dialect;

import static org.junit.jupiter.api.Assertions.assertEquals;
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
import org.sagacity.sqltoy.utils.DataSourceUtils;

/**
 * DB2真实库冒烟:验证本轮db2目标改动(now/sysdate/getdate→CURRENT TIMESTAMP、
 * group_concat→listagg within group、lengthb→octet_length)在真实db2上的最终效果。
 * 连接配置从target/db2-probe.properties读取;探针表测完即删。
 */
public class Db2RealDbSmokeTest {

	private static Connection conn;

	private static boolean available = false;

	@BeforeAll
	public static void init() throws Exception {
		File cfg = new File("target/db2-probe.properties");
		if (!cfg.exists()) {
			return;
		}
		Properties p = new Properties();
		try (InputStream in = new FileInputStream(cfg)) {
			p.load(in);
		}
		Class.forName("com.ibm.db2.jcc.DB2Driver");
		conn = DriverManager.getConnection(p.getProperty("url"), p.getProperty("username"), p.getProperty("password"));
		try (Statement st = conn.createStatement()) {
			// db2的DROP TABLE不支持IF EXISTS,首次运行表不存在时忽略-204
			try {
				st.execute("drop table sqltoy_probe_t1");
			} catch (Exception e) {
				// ignore
			}
			st.execute("create table sqltoy_probe_t1 (id integer primary key not null, name varchar(100), "
					+ "score decimal(10,2), create_time timestamp)");
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
				st.execute("drop table sqltoy_probe_t1");
			} catch (Exception e) {
				// ignore
			}
			conn.close();
		}
	}

	private Object querySingle(String sql) throws Exception {
		try (PreparedStatement pst = conn.prepareStatement(sql); ResultSet rs = pst.executeQuery()) {
			rs.next();
			return rs.getObject(1);
		}
	}

	@Test
	public void db2Available() {
		org.junit.jupiter.api.Assumptions.assumeTrue(available, "db2探针环境不可用,跳过");
	}

	/** now/sysdate/getdate转CURRENT TIMESTAMP(本轮Now.java补的db2映射) */
	@Test
	public void nowConversion() throws Exception {
		org.junit.jupiter.api.Assumptions.assumeTrue(available, "db2探针环境不可用,跳过");
		assertNotNull(querySingle(convert("select now() from sqltoy_probe_t1 where id=1", "db2")), "now应转CURRENT TIMESTAMP");
		assertNotNull(querySingle(convert("select sysdate from sqltoy_probe_t1 where id=1", "db2")), "sysdate应转CURRENT TIMESTAMP");
		assertNotNull(querySingle(convert("select getdate() from sqltoy_probe_t1 where id=1", "db2")), "getdate应转CURRENT TIMESTAMP");
	}

	/** group_concat转listagg within group(本轮GroupConcat补的db2映射) */
	@Test
	public void groupConcatConversion() throws Exception {
		org.junit.jupiter.api.Assumptions.assumeTrue(available, "db2探针环境不可用,跳过");
		Object v = querySingle(convert("select group_concat(name separator '-') from sqltoy_probe_t1", "db2"));
		assertEquals("admin-user", v.toString(), "group_concat应转listagg within group");
	}

	/** lengthb转octet_length(本轮Length.java补的db2映射;utf8中文3字节) */
	@Test
	public void lengthbConversion() throws Exception {
		org.junit.jupiter.api.Assumptions.assumeTrue(available, "db2探针环境不可用,跳过");
		assertEquals(5, ((Number) querySingle(convert("select lengthb(name) from sqltoy_probe_t1 where id=1",
				"db2"))).intValue(), "lengthb应转octet_length");
	}

	private String convert(String sql, String dialect) {
		return FunctionUtils.getDialectSql(sql, dialect);
	}
}
