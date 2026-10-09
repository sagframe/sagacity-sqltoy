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
 * KingbaseES真实库冒烟(2026-10-9,连接信息对齐sqltoy-verify:54321,system):
 * 验证近期函数改造在金仓V9(默认oracle兼容模式)上的执行语义。重点:聚合排序子句
 * (suffix钩子,ARRAY_AGG内嵌ORDER BY)、substr负起点RIGHT、strpos/nvl/lengthb的pg系
 * 映射、datediff的kingbase专属trunc口径、concat三参转||(sys.concat两参遮蔽)。
 * 连接配置从target/kingbase-probe.properties读取(不入库);探针表测完即删。
 */
public class KingbaseRealDbSmokeTest {

	private static Connection conn;

	private static boolean available = false;

	@BeforeAll
	public static void init() throws Exception {
		File cfg = new File("target/kingbase-probe.properties");
		if (!cfg.exists()) {
			return;
		}
		Properties p = new Properties();
		try (InputStream in = new FileInputStream(cfg)) {
			p.load(in);
		}
		Class.forName("com.kingbase8.Driver");
		conn = DriverManager.getConnection(p.getProperty("url"), p.getProperty("username"), p.getProperty("password"));
		try (Statement st = conn.createStatement()) {
			st.execute("drop table if exists sqltoy_probe_t1");
			st.execute("create table sqltoy_probe_t1 (id int primary key, name varchar(100), "
					+ "score decimal(10,2), create_time timestamp, remark varchar(100))");
			st.execute("insert into sqltoy_probe_t1 values (1,'admin',88.5,'2026-01-15 10:30:00',null)");
			st.execute("insert into sqltoy_probe_t1 values (2,'user',72.0,'2026-03-20 14:00:00','r2')");
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

	private Object querySingle(String sql) throws Exception {
		try (PreparedStatement pst = conn.prepareStatement(sql); ResultSet rs = pst.executeQuery()) {
			rs.next();
			return rs.getObject(1);
		}
	}

	private String convert(String sql) {
		return FunctionUtils.getDialectSql(sql, "kingbase");
	}

	/** pg系字符串/判空族映射执行:strpos透传、nvl→coalesce、isnull单参/nvl2判空、lengthb→octet_length */
	@Test
	public void stringAndNullConversions() throws Exception {
		assertEquals(2, ((Number) querySingle(convert("select strpos(name,'dmi') from sqltoy_probe_t1 where id=1")))
				.intValue(), "strpos为pg系原生应透传执行");
		assertEquals(2, ((Number) querySingle(convert("select instr(name,'dmi') from sqltoy_probe_t1 where id=1")))
				.intValue(), "instr两参应转position执行");
		assertEquals("none", querySingle(convert("select nvl(remark,'none') from sqltoy_probe_t1 where id=1"))
				.toString(), "nvl应转coalesce执行");
		assertEquals(0, ((Number) querySingle(convert("select isnull(name) from sqltoy_probe_t1 where id=1")))
				.intValue(), "单参isnull应转判空表达式");
		assertEquals("n", querySingle(convert("select nvl2(remark,'y','n') from sqltoy_probe_t1 where id=1"))
				.toString(), "nvl2应转case when判非空");
		assertEquals(5, ((Number) querySingle(convert("select lengthb(name) from sqltoy_probe_t1 where id=1")))
				.intValue(), "lengthb应转octet_length执行");
		// substr负起点(pg系分支转RIGHT):金仓原生substr负起点为pg语义,不转会返回整串
		assertEquals("in", querySingle(convert("select substr(name,-2) from sqltoy_probe_t1 where id=1")).toString(),
				"负起点两参应转RIGHT取末n位");
	}

	/** 聚合排序子句(2026-10-4 suffix钩子)在金仓的执行:ARRAY_AGG内嵌ORDER BY */
	@Test
	public void aggregateWithOrderBy() throws Exception {
		// listagg源→array_to_string(ARRAY_AGG(name ORDER BY id)) 按id序拼接
		assertEquals("admin-user", querySingle(convert(
				"select listagg(name,'-') within group (order by id) from sqltoy_probe_t1")).toString(),
				"listagg排序子句应转ARRAY_AGG内嵌ORDER BY并按id序拼接");
		assertEquals("user-admin", querySingle(convert(
				"select listagg(name,'-') within group (order by id desc) from sqltoy_probe_t1")).toString(),
				"降序排序子句应按id降序拼接");
		// 残段内嵌order by(pg源string_agg形态同理)
		assertEquals("admin-user", querySingle(convert(
				"select group_concat(name order by id separator '-') from sqltoy_probe_t1")).toString(),
				"残段order by应转ARRAY_AGG内嵌ORDER BY");
		// string_agg为pg系原生连同order by透传
		assertEquals("admin-user", querySingle(convert(
				"select string_agg(name,'-' order by id) from sqltoy_probe_t1")).toString(),
				"string_agg原生含order by应透传执行");
	}

	/** datediff金仓专属口径(V9 oracle兼容:date含时间,统一trunc向零截断)与日期函数族 */
	@Test
	public void dateConversions() throws Exception {
		assertEquals(5, ((Number) querySingle(convert(
				"select datediff(day, to_date('2026-01-15','yyyy-MM-dd'), to_date('2026-01-20','yyyy-MM-dd')) from dual")))
				.intValue(), "三参datediff应按kingbase口径转换执行=5");
		// 同日不同时刻:trunc后天差为0
		assertEquals(0, ((Number) querySingle(convert(
				"select datediff(create_time, create_time) from sqltoy_probe_t1 where id=1"))).intValue(),
				"两参datediff自然天差=0");
		assertEquals("2026-01-15", querySingle(convert(
				"select to_char(create_time,'yyyy-MM-dd') from sqltoy_probe_t1 where id=1")).toString(),
				"to_char应转pg系格式执行");
		assertEquals("2026-01-15", querySingle(convert(
				"select date_format(create_time,'%Y-%m-%d') from sqltoy_probe_t1 where id=1")).toString(),
				"date_format应转to_char执行");
		assertNotNull(querySingle(convert("select now() from sqltoy_probe_t1 where id=1")), "now应执行");
		// current_date金仓原生透传执行(零点日期,驱动以Timestamp返回,toString带00:00:00.0尾)
		String kbCurdate = querySingle(convert("select current_date from sqltoy_probe_t1 where id=1")).toString();
		assertTrue(kbCurdate.startsWith(java.time.LocalDate.now().toString()),
				"current_date应透传返回今天零点: " + kbCurdate);
	}

	/** concat三参转||(金仓sys.concat两参遮蔽pg_catalog.concat,三参原生报函数不存在) */
	@Test
	public void concatThreeArgsToPipe() throws Exception {
		assertEquals("admin-x", querySingle(convert("select concat(name,'-','x') from sqltoy_probe_t1 where id=1"))
				.toString(), "concat三参应转||拼接执行");
		// concat_ws跳null(pg原生)
		assertEquals("admin", querySingle(convert("select concat_ws('-',name,remark) from sqltoy_probe_t1 where id=1"))
				.toString(), "concat_ws原生跳null执行");
	}
}
