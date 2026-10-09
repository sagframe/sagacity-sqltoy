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

/**
 * OceanBase真实库冒烟(2026-10-9,连接信息对齐sqltoy-verify:CE容器mysql模式租户2881)。
 * OB CE(社区版)只有mysql模式租户(oracle模式为企业版特性),sqltoy的"oceanbase"方言产物
 * 按oracle系生成(nvl透传/listagg/||拼接)仅适用于oracle模式租户;mysql模式的正确姿势是
 * 配置dialect=mysql(verify工程同款)——本类即以mysql方言验证整条转换链在OB真库的执行,
 * 并锁定oceanbase方言在mysql模式的支持面边界(2026-10-9探针实测:nvl/instr/substr负起点
 * 原生可用;||拼接被mysql模式当逻辑或静默返回0;listagg/to_date/to_char/sysdate不存在)。
 * 连接配置从target/ob-probe.properties读取(不入库);探针表测完即删。
 */
public class OceanbaseRealDbSmokeTest {

	private static Connection conn;

	private static boolean available = false;

	@BeforeAll
	public static void init() throws Exception {
		File cfg = new File("target/ob-probe.properties");
		if (!cfg.exists()) {
			return;
		}
		Properties p = new Properties();
		try (InputStream in = new FileInputStream(cfg)) {
			p.load(in);
		}
		Class.forName("com.oceanbase.jdbc.Driver");
		conn = DriverManager.getConnection(p.getProperty("url"), p.getProperty("username"), p.getProperty("password"));
		try (Statement st = conn.createStatement()) {
			st.execute("drop table if exists sqltoy_probe_t1");
			st.execute("create table sqltoy_probe_t1 (id int primary key, name varchar(100), "
					+ "score decimal(10,2), create_time datetime, remark varchar(100))");
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

	private String convertMy(String sql) {
		// mysql模式租户的正确方言(verify同款),验证mysql转换链在OB真库执行
		return FunctionUtils.getDialectSql(sql, "mysql");
	}

	private String convertOb(String sql) {
		return FunctionUtils.getDialectSql(sql, "oceanbase");
	}

	/** mysql方言转换链在OB真库执行:判空族/聚合(含排序子句)/日期族/字符串族 */
	@Test
	public void mysqlDialectChainOnOb() throws Exception {
		// 判空族
		assertEquals("none", querySingle(convertMy("select nvl(remark,'none') from sqltoy_probe_t1 where id=1"))
				.toString(), "nvl应转ifnull执行");
		assertEquals("n", querySingle(convertMy("select nvl2(remark,'y','n') from sqltoy_probe_t1 where id=1"))
				.toString(), "nvl2应转case when执行");
		assertEquals(1, ((Number) querySingle(convertMy("select isnull(remark) from sqltoy_probe_t1 where id=1")))
				.intValue(), "单参isnull判空执行");
		// 聚合:原生group_concat;listagg排序子句(suffix钩子链)转ORDER BY…SEPARATOR
		assertEquals("admin-user",
				querySingle(convertMy("select group_concat(name separator '-') from sqltoy_probe_t1")).toString(),
				"group_concat原生执行");
		assertEquals("user-admin", querySingle(convertMy(
				"select listagg(name,'-') within group (order by id desc) from sqltoy_probe_t1")).toString(),
				"listagg排序子句应转group_concat ORDER BY…SEPARATOR并按id降序拼接");
		// concat三参mysql原生
		assertEquals("admin-x", querySingle(convertMy("select concat(name,'-','x') from sqltoy_probe_t1 where id=1"))
				.toString(), "concat三参原生执行");
		// 日期族
		assertEquals("2026-01-15", querySingle(convertMy(
				"select to_char(create_time,'yyyy-MM-dd') from sqltoy_probe_t1 where id=1")).toString(),
				"to_char应转date_format执行");
		assertEquals("2026-01-15", querySingle(
				convertMy("select to_date(create_time) from sqltoy_probe_t1 where id=1")).toString(),
				"to_date单参应转DATE执行");
		assertNotNull(querySingle(convertMy("select sysdate from sqltoy_probe_t1 where id=1")),
				"sysdate应转now执行");
		// 字符串族:instr原生/pg写法strpos转instr
		assertEquals(2, ((Number) querySingle(convertMy("select instr(name,'dmi') from sqltoy_probe_t1 where id=1")))
				.intValue(), "instr原生执行");
		assertEquals(2, ((Number) querySingle(convertMy("select strpos(name,'dmi') from sqltoy_probe_t1 where id=1")))
				.intValue(), "strpos应转instr执行");
		// datediff两参mysql原生
		assertEquals(0, ((Number) querySingle(convertMy(
				"select datediff(create_time, create_time) from sqltoy_probe_t1 where id=1"))).intValue(),
				"datediff两参原生执行");
	}

	/**
	 * oceanbase方言(oracle系产物)在CE mysql模式的支持面边界(2026-10-9探针实测锁定):
	 * nvl/instr/substr负起点(mysql语义)原生可用并可执行;listagg/to_date/to_char/sysdate
	 * 不存在,||拼接被mysql模式按逻辑或求值静默得0——这些产物仅适用于oracle模式租户
	 * (企业版),mysql模式租户应配dialect=mysql(见类注释),故仅锁定可执行项与产物形态。
	 */
	@Test
	public void obDialectBoundaryOnMysqlMode() throws Exception {
		// 原生可用的oracle系函数:oceanbase方言透传后真库执行
		assertEquals("none", querySingle(convertOb("select nvl(remark,'none') from sqltoy_probe_t1 where id=1"))
				.toString(), "nvl在OB mysql模式原生可用");
		assertEquals(2, ((Number) querySingle(convertOb("select instr(name,'dmi') from sqltoy_probe_t1 where id=1")))
				.intValue(), "instr在OB mysql模式原生可用");
		assertEquals("in", querySingle(convertOb("select substr(name,-2) from sqltoy_probe_t1 where id=1"))
				.toString(), "substr负起点为mysql语义(取末n位)");
		// 其余oracle系产物仅oracle模式租户适用:mysql模式会报函数不存在,或||按逻辑或静默错值,
		// 不执行仅锁定产物形态(形态正确性由矩阵的oracle系行覆盖,oceanbase与其同分支)
		assertEquals("select  listagg(name,'-') within group (order by null)  from t",
				convertOb("select group_concat(name separator '-') from t"), "group_concat产物为oracle系listagg形态");
		assertEquals("select name||'-'||'x' from t where id=1",
				convertOb("select concat(name,'-','x') from t where id=1"), "concat三参产物为oracle系||形态");
	}
}
