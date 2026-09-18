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

import javax.sql.DataSource;

import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.sagacity.sqltoy.SqlToyContext;
import org.sagacity.sqltoy.config.model.SqlToyConfig;
import org.sagacity.sqltoy.dialect.DialectFactory;
import org.sagacity.sqltoy.model.QueryExecutor;
import org.sagacity.sqltoy.plugins.function.FunctionUtils;
import org.sagacity.sqltoy.utils.DataSourceUtils;

/**
 * PostgreSQL真实库冒烟:验证本轮PG系改动在真实pg上的最终效果。
 * 覆盖:lengthb→octet_length、group_concat→array_to_string(多列||重组)、
 * datediff(::date自然天差/epoch换算)、to_date双形态、nvl→coalesce、concat_ws原生、
 * 字面量安全、union-all-count真实工厂链路。
 * 连接配置从target/pg-probe.properties读取(不入库);探针表sqltoy_probe_*测完即删。
 */
public class PgRealDbSmokeTest {

	private static Connection conn;

	private static SqlToyContext context;

	private static DataSource dataSource;

	private static boolean available = false;

	@BeforeAll
	public static void init() throws Exception {
		File cfg = new File("target/pg-probe.properties");
		if (!cfg.exists()) {
			return;
		}
		Properties p = new Properties();
		try (InputStream in = new FileInputStream(cfg)) {
			p.load(in);
		}
		Class.forName("org.postgresql.Driver");
		conn = DriverManager.getConnection(p.getProperty("url"), p.getProperty("username"), p.getProperty("password"));
		try (Statement st = conn.createStatement()) {
			st.execute("drop table if exists sqltoy_probe_t1");
			st.execute("drop table if exists sqltoy_probe_t2");
			st.execute("create table sqltoy_probe_t1 (id int primary key, name varchar(100), "
					+ "score numeric(10,2), create_time timestamp)");
			st.execute("insert into sqltoy_probe_t1 values (1,'admin',88.5,'2026-01-15 10:30:00')");
			st.execute("insert into sqltoy_probe_t1 values (2,'user',72.0,'2026-03-20 14:00:00')");
			st.execute("create table sqltoy_probe_t2 (id int primary key, b numeric(10,2))");
			st.execute("insert into sqltoy_probe_t2 values (11,110.0)");
			st.execute("insert into sqltoy_probe_t2 values (12,120.0)");
		}
		dataSource = wrapPg(p);
		context = new SqlToyContext();
		context.setConnectionFactory(new org.sagacity.sqltoy.integration.ConnectionFactory() {
			@Override
			public Connection getConnection(DataSource ds) {
				try {
					return ds.getConnection();
				} catch (Exception e) {
					throw new RuntimeException(e);
				}
			}

			@Override
			public void releaseConnection(Connection c, DataSource ds) {
				try {
					if (c != null) {
						c.close();
					}
				} catch (Exception e) {
					// ignore
				}
			}
		});
		FunctionUtils.setFunctionConverts(java.util.Arrays.asList("default",
				"org.sagacity.sqltoy.plugins.function.impl.DateDiff"));
		available = true;
	}

	private static DataSource wrapPg(Properties p) {
		org.postgresql.ds.PGSimpleDataSource ds = new org.postgresql.ds.PGSimpleDataSource();
		ds.setURL(p.getProperty("url"));
		ds.setUser(p.getProperty("username"));
		ds.setPassword(p.getProperty("password"));
		return ds;
	}

	@AfterAll
	public static void destroy() throws Exception {
		if (conn != null) {
			try (Statement st = conn.createStatement()) {
				st.execute("drop table if exists sqltoy_probe_t1");
				st.execute("drop table if exists sqltoy_probe_t2");
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
	public void pgAvailable() {
		org.junit.jupiter.api.Assumptions.assumeTrue(available, "pg探针环境不可用,跳过");
	}

	@Test
	public void functionExecutionMatrix() throws Exception {
		org.junit.jupiter.api.Assumptions.assumeTrue(available, "pg探针环境不可用,跳过");
		// nvl -> coalesce
		assertEquals("admin",
				querySingle(convert("select nvl(name,'none') from sqltoy_probe_t1 where id=1", "postgresql")).toString());
		// to_number单参 -> CAST AS numeric
		assertEquals(88.5, ((Number) querySingle(convert("select to_number('88.5') from sqltoy_probe_t1 where id=1",
				"postgresql"))).doubleValue(), 0.000001, "单参to_number执行错误");
		// to_date单参 -> CAST AS date
		assertEquals("2026-01-15", querySingle(
				convert("select to_date(create_time) from sqltoy_probe_t1 where id=1", "postgresql")).toString()
				.substring(0, 10));
		// to_date两参原生保留并正确执行
		assertEquals("2024-01-01", querySingle(
				convert("select to_date('2024-01-01','yyyy-MM-dd') from sqltoy_probe_t1 where id=1", "postgresql"))
				.toString().substring(0, 10));
		// lengthb -> octet_length(字节长度;utf8中文3字节)
		assertEquals(5, ((Number) querySingle(convert("select lengthb(name) from sqltoy_probe_t1 where id=1",
				"postgresql"))).intValue());
		assertEquals(3, ((Number) querySingle(convert("select lengthb('中') from sqltoy_probe_t1 where id=1",
				"postgresql"))).intValue(), "lengthb应为字节语义(utf8中文3字节)");
		// length原生
		assertEquals(5, ((Number) querySingle(convert("select length(name) from sqltoy_probe_t1 where id=1",
				"postgresql"))).intValue());
		// substr/substring
		assertEquals("adm", querySingle(convert("select substr(name,1,3) from sqltoy_probe_t1 where id=1",
				"postgresql")).toString());
		// instr两参 -> position
		assertEquals(2, ((Number) querySingle(convert(
				"select instr(name,'dmi') from sqltoy_probe_t1 where id=1", "postgresql"))).intValue());
		// trim普通与修饰符形态(pg原生标准FROM形态,原样保留)
		assertEquals("admin", querySingle(
				convert("select trim(' admin ') from sqltoy_probe_t1 where id=1", "postgresql")).toString());
		assertEquals("admin", querySingle(convert(
				"select trim(both ' ' from ' admin ') from sqltoy_probe_t1 where id=1", "postgresql")).toString());
		// concat多参原生;concat_ws原生
		assertEquals("admin-x", querySingle(convert(
				"select concat(name,'-','x') from sqltoy_probe_t1 where id=1", "postgresql")).toString());
		assertEquals("admin-admin", querySingle(convert(
				"select concat_ws('-',name,name) from sqltoy_probe_t1 where id=1", "postgresql")).toString());
		// if转case when(pg无if)
		assertEquals("high", querySingle(convert(
				"select if(score > 80, 'high', 'low') from sqltoy_probe_t1 where id=1", "postgresql")).toString());
		// sysdate转now()
		assertNotNullNow(convert("select sysdate from sqltoy_probe_t1 where id=1", "postgresql"));
		// datediff两参契约d1-d2(::date自然天差)
		assertEquals(5L, ((Number) querySingle(convert(
				"select datediff('2026-01-20','2026-01-15') from sqltoy_probe_t1 where id=1", "postgresql")))
				.longValue(), "两参datediff应转::date自然天差");
		// datediff三参契约d2-d1转date_part日分量
		assertEquals(5L, ((Number) querySingle(convert(
				"select datediff(day,'2026-01-15','2026-01-20') from sqltoy_probe_t1 where id=1", "postgresql")))
				.longValue(), "三参datediff(day)执行错误");
		// 字面量安全
		String literal = convert("select name from sqltoy_probe_t1 where name='nvl(a,b)'", "postgresql");
		assertEquals("select name from sqltoy_probe_t1 where name='nvl(a,b)'", literal, "字面量nvl不得被转换");
	}

	private void assertNotNullNow(String sql) throws Exception {
		Object v = querySingle(sql);
		assertNotNull(v, "时间函数执行结果不应为null: " + sql);
	}

	/** group_concat多列拼接与string_agg原生保留 */
	@Test
	public void groupConcatOnRealPg() throws Exception {
		// 单列
		assertEquals("admin-user", querySingle(convert(
				"select group_concat(name separator '-') from sqltoy_probe_t1", "postgresql")).toString());
		// 多列拼接(修复前丢失列)
		String multi = convert("select group_concat(a, b separator '-') from sqltoy_probe_t1", "postgresql");
		assertTrue(multi.replaceAll("\\s+", "").contains("ARRAY_AGG(a||b)"), "PG多列应以||重组: " + multi);
		// string_agg原生保留
		assertEquals("admin-user", querySingle(
				convert("select string_agg(name,'-') from sqltoy_probe_t1", "postgresql")).toString());
	}

	/** union-all-count真实工厂链路(DialectFactory.getCountBySql,含派生表别名修复) */
	@Test
	public void unionAllCountRealFactory() throws Exception {
		org.junit.jupiter.api.Assumptions.assumeTrue(available, "pg探针环境不可用,跳过");
		// 双分支:t1两行+t2两行=4
		assertEquals(4L, unionAllCount(
				"select a from (select id a from sqltoy_probe_t1) x union all select b from sqltoy_probe_t2"),
				"双分支union-all-count真实执行错误");
		// 大写UNION ALL
		assertEquals(4L, unionAllCount(
				"select a from (select id a from sqltoy_probe_t1) x UNION ALL select b from sqltoy_probe_t2"),
				"大写UNION ALL应正确计数");
		// 三分支:t1两行+t2两行+t1一行=5
		assertEquals(5L, unionAllCount(
				"select a from sqltoy_probe_t1 union all select b from sqltoy_probe_t2 union all select a from sqltoy_probe_t1 where id=1"),
				"三分支union-all-count执行错误");
	}

	private Long unionAllCount(String sql) throws Exception {
		SqlToyConfig config = new SqlToyConfig("postgresql");
		config.setSql(sql);
		config.setUnionAllCount(true);
		QueryExecutor queryExecutor = new QueryExecutor(sql);
		return DialectFactory.getInstance().getCountBySql(context, queryExecutor, config, dataSource);
	}
}
