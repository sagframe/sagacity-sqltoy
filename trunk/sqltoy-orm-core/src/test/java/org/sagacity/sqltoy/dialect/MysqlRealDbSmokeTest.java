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
 * MySQL真实库冒烟:验证本轮函数转换/字面量安全/union-all-count改动在真实mysql上的最终效果。
 * 连接配置从target/mysql-probe.properties读取(该目录不入库,避免凭据进入仓库),
 * 文件不存在或连接失败时整组用例自动跳过。
 * 探针表sqltoy_probe_t1/t2由用例创建并在结束时清理。
 */
public class MysqlRealDbSmokeTest {

	private static Connection conn;

	private static SqlToyContext context;

	private static DataSource dataSource;

	private static boolean available = false;

	@BeforeAll
	public static void init() throws Exception {
		File cfg = new File("target/mysql-probe.properties");
		if (!cfg.exists()) {
			return;
		}
		Properties p = new Properties();
		try (InputStream in = new FileInputStream(cfg)) {
			p.load(in);
		}
		Class.forName("com.mysql.cj.jdbc.Driver");
		conn = DriverManager.getConnection(p.getProperty("url"), p.getProperty("username"), p.getProperty("password"));
		try (Statement st = conn.createStatement()) {
			st.execute("drop table if exists sqltoy_probe_t1");
			st.execute("drop table if exists sqltoy_probe_t2");
			st.execute("create table sqltoy_probe_t1 (id int primary key, name varchar(100), "
					+ "score decimal(10,2), create_time datetime)");
			st.execute("insert into sqltoy_probe_t1 values (1,'admin',88.5,'2026-01-15 10:30:00')");
			st.execute("insert into sqltoy_probe_t1 values (2,'user',72.0,'2026-03-20 14:00:00')");
			st.execute("create table sqltoy_probe_t2 (id int primary key, b decimal(10,2))");
			st.execute("insert into sqltoy_probe_t2 values (11,110.0)");
			st.execute("insert into sqltoy_probe_t2 values (12,120.0)");
		}
		dataSource = wrapMysql(p);
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

	private static DataSource wrapMysql(Properties p) {
		com.mysql.cj.jdbc.MysqlDataSource ds = new com.mysql.cj.jdbc.MysqlDataSource();
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

	/** mysql连接可用性(不可达时整组跳过而非报错) */
	@Test
	public void mysqlAvailable() {
		org.junit.jupiter.api.Assumptions.assumeTrue(available, "mysql探针环境不可用,跳过");
	}

	@Test
	public void functionExecutionMatrix() throws Exception {
		org.junit.jupiter.api.Assumptions.assumeTrue(available, "mysql探针环境不可用,跳过");
		// nvl -> ifnull
		assertEquals("admin",
				querySingle(convert("select nvl(name,'none') from sqltoy_probe_t1 where id=1", "mysql")).toString());
		// to_number单参 -> CAST AS DECIMAL(20,6)
		assertEquals(88.5, ((Number) querySingle(convert("select to_number('88.5') from dual", "mysql")))
				.doubleValue(), 0.000001, "单参to_number执行错误");
		assertEquals(88.5, ((Number) querySingle(
				convert("select to_number(score) from sqltoy_probe_t1 where id=1", "mysql"))).doubleValue(),
				0.000001, "数值列to_number执行错误");
		// to_date单参 -> DATE();2参 -> STR_TO_DATE+格式token互换
		assertEquals("2026-01-15", querySingle(
				convert("select to_date(create_time) from sqltoy_probe_t1 where id=1", "mysql")).toString());
		assertEquals("2024-01-01", querySingle(
				convert("select to_date('2024-01-01','yyyy-MM-dd') from dual", "mysql")).toString());
		// date_format原生;to_char转date_format
		assertEquals("2026-01-15", querySingle(
				convert("select date_format(create_time,'%Y-%m-%d') from sqltoy_probe_t1 where id=1", "mysql"))
				.toString());
		assertEquals("2026-01-15", querySingle(
				convert("select to_char(create_time,'yyyy-MM-dd') from sqltoy_probe_t1 where id=1", "mysql"))
				.toString());
		// to_char 24小时制
		assertEquals("2026-01-15 10:30:00", querySingle(convert(
				"select to_char(create_time,'yyyy-MM-dd HH:mm:ss') from sqltoy_probe_t1 where id=1", "mysql"))
				.toString());
		// datediff两参契约d1-d2原生保留;三参DAY口径统一转自然天差DATEDIFF(timestampdiff的DAY按完整24小时锚定)
		assertEquals(5L, ((Number) querySingle(convert("select datediff('2026-01-20','2026-01-15') from dual",
				"mysql"))).longValue(), "两参datediff=d1-d2");
		assertEquals(5L, ((Number) querySingle(convert("select datediff(day,'2026-01-15','2026-01-20') from dual",
				"mysql"))).longValue(), "三参datediff=d2-d1转DATEDIFF");
		assertEquals(5L, ((Number) querySingle(convert(
				"select timestampdiff(DAY,'2026-01-15','2026-01-20') from dual", "mysql"))).longValue(),
				"timestampdiff(DAY)口径统一转DATEDIFF");
		// 年/月口径统一为年月分量差('2026-01-31'→'2026-03-01'跨月边界=2,与他库同值;timestampdiff原生给1)
		assertEquals(2L, ((Number) querySingle(convert(
				"select datediff(month,'2026-01-31','2026-03-01') from dual", "mysql"))).longValue(),
				"月差应为年月分量差=2");
		// decode转case when(需显式启用)
		FunctionUtils.setFunctionConverts(java.util.Arrays
				.asList("org.sagacity.sqltoy.plugins.function.impl.Decode"));
		try {
			assertEquals("high", querySingle(convert(
					"select decode(score,88.5,'high','low') from sqltoy_probe_t1 where id=1", "mysql")).toString());
			assertEquals("low", querySingle(convert(
					"select decode(score,88.5,'high','low') from sqltoy_probe_t1 where id=2", "mysql")).toString());
		} finally {
			FunctionUtils.setFunctionConverts(java.util.Arrays.asList("default",
				"org.sagacity.sqltoy.plugins.function.impl.DateDiff"));
		}
		// group_concat原生;string_agg转group_concat
		assertEquals("admin-user", querySingle(
				convert("select group_concat(name separator '-') from sqltoy_probe_t1", "mysql")).toString());
		assertEquals("admin-user", querySingle(
				convert("select string_agg(name,'-') from sqltoy_probe_t1", "mysql")).toString());
		// concat三参原生;concat_ws原生
		assertEquals("admin-x", querySingle(
				convert("select concat(name,'-','x') from sqltoy_probe_t1 where id=1", "mysql")).toString());
		// length/char_length(字符长度一致)
		assertEquals(5, ((Number) querySingle(
				convert("select length(name) from sqltoy_probe_t1 where id=1", "mysql"))).intValue());
		assertEquals(5, ((Number) querySingle(
				convert("select char_length(name) from sqltoy_probe_t1 where id=1", "mysql"))).intValue());
		// substr负起点(mysql原生形态)
		assertEquals("in", querySingle(
				convert("select substr(name,-2) from sqltoy_probe_t1 where id=1", "mysql")).toString());
		// trim三形态(mysql原生FROM形态)
		assertEquals("admin", querySingle(
				convert("select trim(both ' ' from ' admin ') from dual", "mysql")).toString());
		// if原生
		assertEquals("high", querySingle(
				convert("select if(score > 80, 'high', 'low') from sqltoy_probe_t1 where id=1", "mysql")).toString());
		// instr三参转locate
		assertEquals(2, ((Number) querySingle(convert("select instr(name,'dmi',1) from sqltoy_probe_t1 where id=1",
				"mysql"))).intValue());
		// sysdate/now(fsp)转now()并正确执行
		assertNotNullNow(convert("select sysdate from dual", "mysql"));
		assertNotNullNow(convert("select now(6) from dual", "mysql"));
	}

	private void assertNotNullNow(String sql) throws Exception {
		Object v = querySingle(sql);
		assertNotNull(v, "时间函数执行结果不应为null: " + sql);
	}

	/** 字面量安全:字面量内的函数文本不得破坏语句,且语句在真实mysql上正确执行 */
	@Test
	public void literalSafetyOnRealMysql() throws Exception {
		org.junit.jupiter.api.Assumptions.assumeTrue(available, "mysql探针环境不可用,跳过");
		String literalNvl = convert("select name from sqltoy_probe_t1 where name='nvl(a,b)'", "mysql");
		assertEquals("select name from sqltoy_probe_t1 where name='nvl(a,b)'", literalNvl, "字面量nvl(a,b)不得被转换");
		assertEquals(0, countByQuery(literalNvl), "字面量比较语句应正常执行且无匹配");
		String literalSysdate = convert("select name from sqltoy_probe_t1 where remark='sysdate'", "mysql");
		assertEquals("select name from sqltoy_probe_t1 where remark='sysdate'", literalSysdate,
				"sysdate字面量不得转now()");
		String literalIf = convert("select name from sqltoy_probe_t1 where remark='if(a,b)'", "mysql");
		assertEquals("select name from sqltoy_probe_t1 where remark='if(a,b)'", literalIf, "if字面量不得转case when");
	}

	/** union-all-count真实工厂链路:DialectFactory.getCountBySql在真实mysql上的计数 */
	@Test
	public void unionAllCountRealFactory() throws Exception {
		org.junit.jupiter.api.Assumptions.assumeTrue(available, "mysql探针环境不可用,跳过");
		// 双分支:t1两行+t2两行=4
		assertEquals(4L, unionAllCount(
				"select a from (select id a from sqltoy_probe_t1) x union all select b from sqltoy_probe_t2"),
				"双分支union-all-count真实执行错误");
		// 大写UNION ALL(REGEX修复激活)
		assertEquals(4L, unionAllCount(
				"select a from (select id a from sqltoy_probe_t1) x UNION ALL select b from sqltoy_probe_t2"),
				"大写UNION ALL应正确计数");
		// 字面量含' union all '文本:不得被拆分
		assertEquals(2L, unionAllCount("select remark from (select 'x' remark from sqltoy_probe_t1 where id=1) x "
				+ "where remark=' union all ' union all select b from sqltoy_probe_t2"),
				"字面量union all不得参与拆分");
	}

	private Long unionAllCount(String sql) throws Exception {
		SqlToyConfig config = new SqlToyConfig("mysql");
		config.setSql(sql);
		config.setUnionAllCount(true);
		QueryExecutor queryExecutor = new QueryExecutor(sql);
		return DialectFactory.getInstance().getCountBySql(context, queryExecutor, config, dataSource);
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
}
