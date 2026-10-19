package org.sagacity.sqltoy.plugins.function;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.File;
import java.io.FileInputStream;
import java.io.InputStream;
import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.ResultSet;
import java.sql.Statement;
import java.util.Arrays;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Properties;

import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.Assumptions;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/**
 * @description 2026-10-19 function 缺陷修复的真库验收(docs/function-fix-verification.md 第三、五节)。
 *              覆盖 Oracle mm/HH 格式模型、Java HH:mm、DB2 posstr、position 字面量、
 *              trim(from)、string_agg 排序、SQLServer 周截断、current_timestamp、
 *              末尾裸关键字、DM concat 多参、OceanBase datediff 等须真库确认的项。
 *              连接配置读取 target/&lt;db&gt;-probe.properties,文件缺失或连接失败时整库跳过。
 * @author zhongxuchen
 */
@DisplayName("function修复真库验收")
public class FunctionFixRealDbVerificationTest {

	private static final Map<String, Db> DBS = new LinkedHashMap<String, Db>();

	@BeforeAll
	public static void init() {
		FunctionUtils.setFunctionConverts(Arrays.asList("default"));
		add("mysql", "mysql", "com.mysql.cj.jdbc.Driver");
		add("sqlserver", "sqlserver", "com.microsoft.sqlserver.jdbc.SQLServerDriver");
		add("oracle", "oracle", "oracle.jdbc.OracleDriver");
		add("postgresql", "pg", "org.postgresql.Driver");
		add("db2", "db2", "com.ibm.db2.jcc.DB2Driver");
		add("dm", "dm", "dm.jdbc.driver.DmDriver");
		add("oceanbase", "ob", "com.oceanbase.jdbc.Driver");
		add("hana", "hana", "com.sap.db.jdbc.Driver");
		add("clickhouse", "clickhouse", "com.clickhouse.jdbc.ClickHouseDriver");
		add("opengauss", "opengauss", "org.opengauss.Driver");
	}

	private static void add(String key, String filePrefix, String driver) {
		File cfg = new File("target/" + filePrefix + "-probe.properties");
		if (!cfg.exists()) {
			DBS.put(key, new Db(key, null, null));
			return;
		}
		Properties p = new Properties();
		Connection conn = null;
		try (InputStream in = new FileInputStream(cfg)) {
			p.load(in);
			Class.forName(driver);
			conn = DriverManager.getConnection(p.getProperty("url"), p.getProperty("username"),
					p.getProperty("password"));
		} catch (Throwable e) {
			System.out.println("[probe-skip] " + key + " -> " + e.getMessage());
			conn = null;
		}
		DBS.put(key, new Db(key, conn, p.getProperty("url")));
	}

	@AfterAll
	public static void destroy() {
		for (Db db : DBS.values()) {
			if (db.conn != null) {
				try {
					db.conn.close();
				} catch (Exception e) {
					// ignore
				}
			}
		}
	}

	private static void ready(String key) {
		Db db = DBS.get(key);
		Assumptions.assumeTrue(db != null && db.conn != null, key + " 真库不可用,跳过");
	}

	private static String c(String sql, String dialect) {
		return FunctionUtils.getDialectSql(sql, dialect);
	}

	private static Object one(String key, String sql) throws Exception {
		Db db = DBS.get(key);
		System.out.println("[" + key + "] " + sql);
		try (Statement st = db.conn.createStatement(); ResultSet rs = st.executeQuery(sql)) {
			assertTrue(rs.next(), "查询无结果: " + sql);
			return rs.getObject(1);
		}
	}

	private static String str(Object value) {
		return value == null ? null : value.toString().trim();
	}

	/** 日期/时间结果统一为 yyyy-MM-dd 或 yyyy-MM-dd HH:mm:ss 文本 */
	private static String dateText(Object value) {
		if (value == null) {
			return null;
		}
		if (value instanceof java.sql.Timestamp) {
			return ((java.sql.Timestamp) value).toLocalDateTime().toLocalDate().toString();
		}
		if (value instanceof java.sql.Date) {
			return ((java.sql.Date) value).toLocalDate().toString();
		}
		if (value instanceof java.util.Date) {
			return new java.sql.Date(((java.util.Date) value).getTime()).toString();
		}
		String text = value.toString().trim();
		if (text.length() > 10 && text.charAt(10) == ' ') {
			return text.substring(0, 10);
		}
		return text;
	}

	private static int intOf(Object value) {
		return ((Number) value).intValue();
	}

	@Test
	@DisplayName("MySQL:format/trim/position 真库矩阵")
	public void mysqlMatrix() throws Exception {
		ready("mysql");
		assertEquals("2026-03-20", str(one("mysql", c("select to_char('2026-03-20','yyyy-mm-dd')", "mysql"))));
		assertEquals("2026-03-20 14:30:45",
				str(one("mysql", c("select to_char('2026-03-20 14:30:45','yyyy-mm-dd hh24:mi:ss')", "mysql"))));
		// HH按框架约定为24小时制(hh才是12小时)
		assertEquals("14:30",
				str(one("mysql", c("select to_char('2026-03-20 14:30:45','HH:MI')", "mysql"))));
		assertEquals("02:30",
				str(one("mysql", c("select to_char('2026-03-20 14:30:45','hh:MI')", "mysql"))));
		assertEquals("2026-03-20", dateText(one("mysql", c("select to_date('2026-03-20','yyyy-mm-dd')", "mysql"))));
		assertEquals("2026-03-20 14:30:45", str(one("mysql",
				c("select date_format('2026-03-20 14:30:45','yyyy-MM-dd HH:mm:ss')", "mysql"))));
		assertEquals("x", str(one("mysql", c("select trim(from '  x  ')", "mysql"))));
		assertEquals(0, intOf(one("mysql", c("select position('a' in 'x in y')", "mysql"))));
	}

	@Test
	@DisplayName("SQLServer:FORMAT/trim/position/周截断/string_agg/current_timestamp")
	public void sqlServerMatrix() throws Exception {
		ready("sqlserver");
		assertEquals("2026-03-20 14:30:45", str(one("sqlserver",
				c("select date_format('2026-03-20 14:30:45','yyyy-MM-dd HH:mm:ss')", "sqlserver"))));
		assertEquals("2026-03-20 14:30:45", str(one("sqlserver",
				c("select to_char('2026-03-20 14:30:45','yyyy-MM-dd HH:mm:ss')", "sqlserver"))));
		// HH=24小时、hh=12小时的真库执行
		assertEquals("14:30", str(one("sqlserver",
				c("select to_char('2026-03-20 14:30:45','HH:MI')", "sqlserver"))));
		assertEquals("02:30", str(one("sqlserver",
				c("select to_char('2026-03-20 14:30:45','hh:MI')", "sqlserver"))));
		assertEquals("x", str(one("sqlserver", c("select trim(from '  x  ')", "sqlserver"))));
		assertEquals(0, intOf(one("sqlserver", c("select position('a' in 'x in y')", "sqlserver"))));
		assertEquals("2026-03-16", dateText(
				one("sqlserver", c("select trunc(cast('2026-03-20 14:30:45' as datetime),'IW')", "sqlserver"))));
		assertEquals("a-b", str(one("sqlserver",
				c("select string_agg(name,'-' order by id) from (values ('a',1),('b',2)) t(name,id)", "sqlserver"))));
		assertNotNull(one("sqlserver", c("select current_timestamp", "sqlserver")));
	}

	@Test
	@DisplayName("SQLServer:多列group_concat真实执行(数值列/字符串列/null)")
	public void sqlServerMultiColumnGroupConcat() throws Exception {
		ready("sqlserver");
		// 数值列+字符串列:CONCAT转字符串,不得做数值加法
		assertEquals("123x", str(one("sqlserver",
				c("select group_concat(a,b) from (values (123,'x')) t(a,b)", "sqlserver"))));
		// 任一参数为null:该行不参与拼接(MySQL concat + group_concat 语义)
		assertEquals("1y", str(one("sqlserver",
				c("select group_concat(a,b) from (values (1,'y'),(2,null)) t(a,b)", "sqlserver"))));
		// 全部为null:结果null/空
		Object allNull = one("sqlserver", c("select group_concat(a,b) from (values (1,null)) t(a,b)", "sqlserver"));
		assertTrue(allNull == null || "".equals(str(allNull)), "全null行应不产出文本: " + allNull);
	}

	@Test
	@DisplayName("Oracle:current_timestamp保留TIMESTAMP、trim(from)、trunc(IW)")
	public void oracleMatrix() throws Exception {
		ready("oracle");
		Object ts = one("oracle", c("select current_timestamp from dual", "oracle"));
		assertNotNull(ts);
		String typeName = ts.getClass().getName().toUpperCase(java.util.Locale.ROOT);
		assertTrue(typeName.contains("TIMESTAMP") && !typeName.contains("DATE"),
				"current_timestamp应返回TIMESTAMP[WITH TIME ZONE],实际=" + typeName);
		assertEquals("2026-03-20", str(one("oracle", c("select to_char(date '2026-03-20','yyyy-mm-dd') from dual", "oracle"))));
		assertEquals("x", str(one("oracle", c("select trim(from '  x  ') from dual", "oracle"))));
		assertEquals("2026-03-16",
				dateText(one("oracle", c("select trunc(date '2026-03-20','IW') from dual", "oracle"))));
	}

	@Test
	@DisplayName("PostgreSQL:string_agg排序/posstr/position字面量")
	public void postgresMatrix() throws Exception {
		ready("postgresql");
		assertEquals("a-b", str(one("postgresql", c(
				"select string_agg(name,'-') within group (order by id) from (values ('a',1),('b',2)) t(name,id)",
				"postgresql"))));
		assertEquals(2, intOf(one("postgresql", c("select posstr('admin','dmi')", "postgresql"))));
		assertEquals(0, intOf(one("postgresql", c("select position('a' in 'x in y')", "postgresql"))));
	}

	@Test
	@DisplayName("DB2:posstr透传/小写Oracle格式模型待确认项")
	public void db2Matrix() throws Exception {
		ready("db2");
		assertEquals(2, intOf(
				one("db2", c("select posstr('admin','dmi') from sysibm.sysdummy1", "db2"))));
		String lower = c("select to_date('2026-03-20','yyyy-mm-dd') from sysibm.sysdummy1", "db2");
		System.out.println("[db2] 小写模型转换后: " + lower);
		assertEquals("2026-03-20", dateText(one("db2", lower)));
		// 对照:大写模型
		assertEquals("2026-03-20", dateText(one("db2",
				c("select to_date('2026-03-20','YYYY-MM-DD') from sysibm.sysdummy1", "db2"))));
		// HH=24小时、hh=12小时的真库执行(DB2原生HH为12小时)
		assertEquals("14:30", str(one("db2",
				c("select to_char(timestamp('2026-03-20 14:30:45'),'HH:MI') from sysibm.sysdummy1", "db2"))));
		assertEquals("02:30", str(one("db2",
				c("select to_char(timestamp('2026-03-20 14:30:45'),'hh:MI') from sysibm.sysdummy1", "db2"))));
	}

	@Test
	@DisplayName("ClickHouse:to_char/date_format 真执行")
	public void clickHouseMatrix() throws Exception {
		ready("clickhouse");
		assertEquals("2026-03-20", str(one("clickhouse",
				c("select to_char(toDate('2026-03-20'),'yyyy-mm-dd')", "clickhouse"))));
		assertEquals("2026-03-20 14:30:45", str(one("clickhouse",
				c("select date_format(toDateTime('2026-03-20 14:30:45'),'yyyy-MM-dd HH:mm:ss')", "clickhouse"))));
		assertEquals("14:30", str(one("clickhouse",
				c("select to_char(toDateTime('2026-03-20 14:30:45'),'HH:MI')", "clickhouse"))));
		assertEquals("02:30", str(one("clickhouse",
				c("select to_char(toDateTime('2026-03-20 14:30:45'),'hh:MI')", "clickhouse"))));
	}

	@Test
	@DisplayName("HANA:to_char/to_date 真执行")
	public void hanaMatrix() throws Exception {
		ready("hana");
		assertEquals("2026-03-20", str(one("hana",
				c("select to_char(to_date('2026-03-20'),'yyyy-mm-dd') from dummy", "hana"))));
		assertEquals("2026-03-20", dateText(one("hana",
				c("select to_date('2026-03-20','yyyy-mm-dd') from dummy", "hana"))));
	}

	@Test
	@DisplayName("DM:concat多参待确认项 + current_timestamp")
	public void dmMatrix() throws Exception {
		ready("dm");
		assertNotNull(one("dm", c("select current_timestamp from dual", "dm")));
		String sql = c("select concat('a','-','b') from dual", "dm");
		System.out.println("[dm] concat三参转换后: " + sql);
		assertEquals("a-b", str(one("dm", sql)));
	}

	@Test
	@DisplayName("OceanBase:datediff(unit,...)待确认项(mysql/oracle两种方言视角)")
	public void oceanbaseMatrix() throws Exception {
		ready("oceanbase");
		assertNotNull(one("oceanbase", c("select current_timestamp from dual", "oceanbase")));
		// 1) 框架默认识别为 oceanbase 方言(按 oracle 系产物生成):本机 OB 为 CE mysql 模式,
		// 无法执行 oracle 系表达式,仅锁定产物形态;oracle 模式租户可直接执行该形态
		String obSql = c("select datediff(day, '2026-03-01','2026-03-20') from dual", "oceanbase");
		System.out.println("[oceanbase] 方言=oceanbase 转换后: " + obSql);
		assertTrue(obSql.contains("TRUNC(to_date('2026-03-20','yyyy-MM-dd'))")
				&& obSql.contains("TRUNC(to_date('2026-03-01','yyyy-MM-dd'))"),
				"oceanbase方言应生成oracle系日期相减产物: " + obSql);
		// 2) verify 工程显式配 dialect=mysql:mysql 模式租户真执行应得 19
		String mysqlView = c("select datediff(day,'2026-03-01','2026-03-20') from dual", "mysql");
		System.out.println("[oceanbase] 方言=mysql 转换后: " + mysqlView);
		assertEquals(19, intOf(one("oceanbase", mysqlView)));
	}

	@Test
	@DisplayName("PostgreSQL系:串尾裸关键字转换 + openGauss执行")
	public void trailingKeywordPostgres() throws Exception {
		ready("postgresql");
		String sql = c("select sysdate", "postgresql");
		assertEquals("select now()", sql.trim());
		assertNotNull(one("postgresql", sql));
	}

	@Test
	@DisplayName("openGauss:串尾裸关键字真执行")
	public void trailingKeywordOpenGauss() throws Exception {
		ready("opengauss");
		assertNotNull(one("opengauss", c("select sysdate", "opengauss")));
	}

	static class Db {
		final String key;
		final Connection conn;
		final String url;

		Db(String key, Connection conn, String url) {
			this.key = key;
			this.conn = conn;
			this.url = url;
		}
	}
}
