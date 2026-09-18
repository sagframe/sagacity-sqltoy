package org.sagacity.sqltoy.dialect;

import static org.junit.jupiter.api.Assertions.assertEquals;
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
 * SQLServer真实库冒烟:验证本轮sqlserver目标改动(Trim修饰符分级、两参datediff、
 * 三参datediff单位映射、字面量安全)在真实sqlserver上的最终效果。
 * 连接配置从target/sqlserver-probe.properties读取;探针表测完即删。
 */
public class SqlServerRealDbSmokeTest {

	private static Connection conn;

	private static boolean available = false;

	@BeforeAll
	public static void init() throws Exception {
		File cfg = new File("target/sqlserver-probe.properties");
		if (!cfg.exists()) {
			return;
		}
		Properties p = new Properties();
		try (InputStream in = new FileInputStream(cfg)) {
			p.load(in);
		}
		Class.forName("com.microsoft.sqlserver.jdbc.SQLServerDriver");
		conn = DriverManager.getConnection(p.getProperty("url"), p.getProperty("username"), p.getProperty("password"));
		try (Statement st = conn.createStatement()) {
			st.execute("if object_id('sqltoy_probe_t1') is not null drop table sqltoy_probe_t1");
			st.execute("if object_id('sqltoy_probe_t2') is not null drop table sqltoy_probe_t2");
			st.execute("create table sqltoy_probe_t1 (id int primary key, name varchar(100), "
					+ "score decimal(10,2), create_time datetime)");
			st.execute("insert into sqltoy_probe_t1 values (1,'admin',88.5,'2026-01-15 10:30:00')");
			st.execute("insert into sqltoy_probe_t1 values (2,'user',72.0,'2026-03-20 14:00:00')");
			st.execute("create table sqltoy_probe_t2 (id int primary key, b decimal(10,2))");
			st.execute("insert into sqltoy_probe_t2 values (11,110.0)");
			st.execute("insert into sqltoy_probe_t2 values (12,120.0)");
		}
		FunctionUtils.setFunctionConverts(java.util.Arrays.asList("default",
				"org.sagacity.sqltoy.plugins.function.impl.DateDiff"));
		available = true;
	}

	@AfterAll
	public static void destroy() throws Exception {
		if (conn != null) {
			try (Statement st = conn.createStatement()) {
				st.execute("if object_id('sqltoy_probe_t1') is not null drop table sqltoy_probe_t1");
				st.execute("if object_id('sqltoy_probe_t2') is not null drop table sqltoy_probe_t2");
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

	private int countByQuery(String sql) throws Exception {
		try (PreparedStatement pst = conn.prepareStatement(sql); ResultSet rs = pst.executeQuery()) {
			int n = 0;
			while (rs.next()) {
				n++;
			}
			return n;
		}
	}

	@Test
	public void sqlserverAvailable() {
		org.junit.jupiter.api.Assumptions.assumeTrue(available, "sqlserver探针环境不可用,跳过");
	}

	@Test
	public void trimMatrix() throws Exception {
		// 普通形态:rtrim(ltrim)
		assertEquals("admin", querySingle(convert(
				"select trim(' admin ') from sqltoy_probe_t1 where id=1", "sqlserver")).toString().trim());
		// both+空格:语义转rtrim(ltrim)(修复前原样保留导致BOTH关键字语法错误)
		assertEquals("admin", querySingle(convert(
				"select trim(both ' ' from name) from sqltoy_probe_t1 where id=1", "sqlserver")).toString().trim());
		// leading/trailing+空格:ltrim/rtrim
		assertEquals("admin", querySingle(convert(
				"select trim(leading from name) from sqltoy_probe_t1 where id=1", "sqlserver")).toString().trim());
		// 剔除字符非空格:原样保留(响亮报错,不静默改变语义)
		String nonSpace = convert("select trim(both 'x' from name) from sqltoy_probe_t1 where id=1", "sqlserver");
		assertEquals("select trim(both 'x' from name) from sqltoy_probe_t1 where id=1", nonSpace,
				"非空格剔除字符应原样保留");
	}

	@Test
	public void dateDiffMatrix() throws Exception {
		// 两参datediff契约d1-d2:转DATEDIFF(DAY,d2,d1)
		Object twoArg = querySingle(convert(
				"select datediff('2026-01-20','2026-01-15') from sqltoy_probe_t1 where id=1", "sqlserver"));
		assertEquals(5, ((Number) twoArg).intValue(), "两参datediff应转DATEDIFF(DAY,d2,d1)=5");
		// 三参契约d2-d1:参数顺序一致,单位映射
		Object threeArg = querySingle(convert(
				"select datediff(day,'2026-01-15','2026-01-20') from sqltoy_probe_t1 where id=1", "sqlserver"));
		assertEquals(5, ((Number) threeArg).intValue(), "三参datediff(day)=5");
		// 小时单位
		Object hourArg = querySingle(convert(
				"select datediff(hour,'2026-01-15 10:30:00','2026-01-15 15:30:00') from sqltoy_probe_t1 where id=1",
				"sqlserver"));
		assertEquals(5, ((Number) hourArg).intValue(), "三参datediff(hour)=5");
	}

	@Test
	public void miscFunctions() throws Exception {
		// nvl -> isnull
		assertEquals("admin", querySingle(convert(
				"select nvl(name,'none') from sqltoy_probe_t1 where id=1", "sqlserver")).toString().trim());
		// if -> case when
		assertEquals("high", querySingle(convert(
				"select if(score > 80, 'high', 'low') from sqltoy_probe_t1 where id=1", "sqlserver"))
				.toString().trim());
		// length -> len;char_length -> len
		assertEquals(5, ((Number) querySingle(convert("select length(name) from sqltoy_probe_t1 where id=1",
				"sqlserver"))).intValue());
		// substr两参 -> substring(s,n,len(s))
		assertEquals("dmin", querySingle(convert("select substr(name,2) from sqltoy_probe_t1 where id=1",
				"sqlserver")).toString().trim());
		// substr负起点 -> RIGHT
		assertEquals("in", querySingle(convert("select substr(name,-2) from sqltoy_probe_t1 where id=1",
				"sqlserver")).toString().trim());
		// to_date单参 -> convert
		assertEquals("2026-01-15", querySingle(convert(
				"select to_date('2026-01-15') from sqltoy_probe_t1 where id=1", "sqlserver")).toString().trim());
		// group_concat -> STRING_AGG
		assertEquals("admin-user", querySingle(convert(
				"select group_concat(name separator '-') from sqltoy_probe_t1", "sqlserver")).toString().trim());
		// concat原生多参
		assertEquals("admin-x", querySingle(convert(
				"select concat(name,'-','x') from sqltoy_probe_t1 where id=1", "sqlserver")).toString().trim());
		// instr两参转charindex(转换后的sql在sqlserver执行正确)
		assertEquals(2, ((Number) querySingle(convert(
				"select charindex('dmi',name) from sqltoy_probe_t1 where id=1", "sqlserver"))).intValue());
		// 字面量安全
		String literal = convert("select name from sqltoy_probe_t1 where name='nvl(a,b)'", "sqlserver");
		assertEquals("select name from sqltoy_probe_t1 where name='nvl(a,b)'", literal, "字面量不得被转换");
		assertEquals(0, countByQuery(literal), "字面量比较语句应正常执行且无匹配");
	}
}
