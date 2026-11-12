package org.sagacity.sqltoy.dialect;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

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
 * openGauss真实库冒烟:验证PG系国产库(openGauss 5.0.0)的函数转换与字面量安全。
 * 覆盖:nvl→coalesce、to_date原生、lengthb(字节语义)、datediff转换形态(date_part/epoch,
 * openGauss的date-date返回interval与vanilla PG不同且无原生datediff)、字面量安全、
 * vector能力边界(5.0.0无vector类型)。
 * 连接配置从target/opengauss-probe.properties读取(不入库);探针表测完即删。
 */
public class OpenGaussRealDbSmokeTest {

	private static Connection conn;

	private static boolean available = false;

	@BeforeAll
	public static void init() throws Exception {
		File cfg = new File("target/opengauss-probe.properties");
		if (!cfg.exists()) {
			return;
		}
		Properties p = new Properties();
		try (InputStream in = new FileInputStream(cfg)) {
			p.load(in);
		}
		Class.forName("org.opengauss.Driver");
		conn = DriverManager.getConnection(p.getProperty("url"), p.getProperty("username"), p.getProperty("password"));
		try (Statement st = conn.createStatement()) {
			st.execute("drop table if exists sqltoy_probe_t1");
			st.execute("create table sqltoy_probe_t1 (id int primary key, name varchar(100), score numeric(10,2))");
			st.execute("insert into sqltoy_probe_t1 values (1,'admin',88.5)");
			st.execute("insert into sqltoy_probe_t1 values (2,'user',72.0)");
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
		try (PreparedStatement pst = conn.prepareStatement(sql); ResultSet rs = pst.executeQuery()) {
			rs.next();
			return rs.getObject(1);
		}
	}

	@Test
	public void ogAvailable() {
		org.junit.jupiter.api.Assumptions.assumeTrue(available, "opengauss探针环境不可用,跳过");
	}

	/** 函数矩阵:nvl/to_date/lengthb/concat/substr等在真实openGauss上执行(走opengauss首选方言) */
	@Test
	public void functionExecutionMatrix() throws Exception {
		org.junit.jupiter.api.Assumptions.assumeTrue(available, "opengauss探针环境不可用,跳过");
		// nvl→coalesce
		assertEquals("admin", querySingle(convert("select nvl(name,'none') from sqltoy_probe_t1 where id=1",
				"opengauss")).toString());
		// to_date两参原生
		Object td = querySingle(convert("select to_date('2024-01-01','yyyy-MM-dd') from sqltoy_probe_t1 where id=1",
				"opengauss"));
		assertNotNull(td, "to_date原生执行");
		// lengthb字节语义(utf8中文3字节)
		assertEquals(3, ((Number) querySingle(convert("select lengthb('中') from sqltoy_probe_t1 where id=1",
				"opengauss"))).intValue(), "lengthb应为字节语义");
		assertEquals(3, ((Number) querySingle(convert("select octet_length('中') from sqltoy_probe_t1 where id=1",
				"opengauss"))).intValue(), "octet_length应为字节语义");
		// concat/substr
		assertEquals("ab", querySingle(convert("select concat('a','b') from sqltoy_probe_t1 where id=1",
				"opengauss")).toString());
		assertEquals("adm", querySingle(convert("select substr(name,1,3) from sqltoy_probe_t1 where id=1",
				"opengauss")).toString());
	}

	/**
	 * datediff转换形态在真实openGauss上执行:openGauss的date-date返回interval(vanilla PG为整数天)
	 * 且无原生datediff,转换走date_part('day',interval)取整天天差与epoch换算形态(实测5.0.0)。
	 * DateDiff未默认注册,用例内显式启用
	 */
	@Test
	public void dateDiffConvertedForm() throws Exception {
		org.junit.jupiter.api.Assumptions.assumeTrue(available, "opengauss探针环境不可用,跳过");
		FunctionUtils.setFunctionConverts(java.util.Arrays
				.asList("org.sagacity.sqltoy.plugins.function.impl.DateDiff"));
		try {
			// 两参:d1-d2整数天
			String two = convert("select datediff('2026-01-20','2026-01-15') from sqltoy_probe_t1 where id=1",
					"opengauss");
			assertTrue(two.contains("date_part('day'"), "openGauss两参应转date_part天差: " + two);
			assertEquals(5, ((Number) querySingle(two)).intValue(), "两参自然天差应为5");
			// 三参day:d2-d1
			String threeDay = convert("select datediff(day,'2026-01-15','2026-01-20') from sqltoy_probe_t1 where id=1",
					"opengauss");
			assertEquals(5, ((Number) querySingle(threeDay)).intValue(), "三参day差应为5");
			// 三参hour:完整小时截断(5天2.5小时=122,口径统一后不再保留小数)
			String threeHour = convert(
					"select datediff(hour,'2026-01-15 08:00:00','2026-01-20 10:30:00') from sqltoy_probe_t1 where id=1",
					"opengauss");
			assertEquals(122, ((Number) querySingle(threeHour)).intValue(), "三参hour差应为122");
			// 三参month:date_part年月重组(2025-01→2026-03=14,年月分量差跨库同值)
			String threeMonth = convert(
					"select datediff(month,'2025-01-15','2026-03-20') from sqltoy_probe_t1 where id=1",
					"opengauss");
			assertEquals(14, ((Number) querySingle(threeMonth)).intValue(), "三参month差应为14");
			// 字面量安全在openGauss上
			assertEquals("select name from sqltoy_probe_t1 where name='nvl(a,b)'",
					convert("select name from sqltoy_probe_t1 where name='nvl(a,b)'", "opengauss"), "字面量安全");
		} finally {
			FunctionUtils.setFunctionConverts(java.util.Arrays.asList("default"));
		}
	}

	/** vector能力边界:openGauss 5.0.0无vector类型(文档化) */
	@Test
	public void vectorNotSupportedIn50() throws Exception {
		org.junit.jupiter.api.Assumptions.assumeTrue(available, "opengauss探针环境不可用,跳过");
		boolean created = false;
		try (Statement st = conn.createStatement()) {
			st.execute("create table sqltoy_probe_vec (id int, embedding vector(3))");
			created = true;
		} catch (Exception e) {
			// openGauss 5.0.0无vector类型
		}
		assertTrue(!created, "openGauss 5.0.0不应支持vector类型(文档化边界)");
		if (created) {
			try (Statement st = conn.createStatement()) {
				st.execute("drop table sqltoy_probe_vec");
			} catch (Exception e) {
				// ignore
			}
		}
	}
}
