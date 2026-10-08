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
 * HANA真实库冒烟:验证hana目标函数映射在真实hana(HXE 2.0 SPS08)上的语法与语义。
 * 重点覆盖近期改动:now/sysdate/getdate/systimestamp→CURRENT_TIMESTAMP、
 * strpos→locate(与instr同序,haystack在前)、nvl2/isnull单参判空、concat_ws转||逐段跳null、
 * group_concat→string_agg。连接配置从target/hana-probe.properties读取(不入库);
 * 探针表sqltoy_probe_t1测完即删。
 */
public class HanaRealDbSmokeTest {

	private static Connection conn;

	private static boolean available = false;

	@BeforeAll
	public static void init() throws Exception {
		File cfg = new File("target/hana-probe.properties");
		if (!cfg.exists()) {
			return;
		}
		Properties p = new Properties();
		try (InputStream in = new FileInputStream(cfg)) {
			p.load(in);
		}
		Class.forName("com.sap.db.jdbc.Driver");
		conn = DriverManager.getConnection(p.getProperty("url"), p.getProperty("username"), p.getProperty("password"));
		try (Statement st = conn.createStatement()) {
			// 首次运行表不存在时忽略报错(hana旧版本不支持DROP TABLE IF EXISTS)
			try {
				st.execute("drop table sqltoy_probe_t1");
			} catch (Exception e) {
				// ignore
			}
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

	private String convert(String sql, String dialect) {
		return FunctionUtils.getDialectSql(sql, dialect);
	}

	/** update 2026-10-4 now/sysdate/getdate/systimestamp→CURRENT_TIMESTAMP(本轮补systimestamp) */
	@Test
	public void nowFamilyToCurrentTimestamp() throws Exception {
		assertNotNull(querySingle(convert("select now() from sqltoy_probe_t1 where id=1", "hana")),
				"now应转CURRENT_TIMESTAMP");
		assertNotNull(querySingle(convert("select sysdate from sqltoy_probe_t1 where id=1", "hana")),
				"sysdate应转CURRENT_TIMESTAMP");
		assertNotNull(querySingle(convert("select getdate() from sqltoy_probe_t1 where id=1", "hana")),
				"getdate应转CURRENT_TIMESTAMP");
		// systimestamp为oracle源形态,hana目标按CURRENT_TIMESTAMP承接(2026-10-4)
		assertNotNull(querySingle(convert("select systimestamp from sqltoy_probe_t1 where id=1", "hana")),
				"systimestamp应转CURRENT_TIMESTAMP");
	}

	/** strpos/instr/charindex→locate(str,sub[,start]),注意与db2/mysql的locate(needle,haystack)参数序相反 */
	@Test
	public void instrFamilyToLocate() throws Exception {
		// strpos(haystack,needle)与instr同序,转locate同序(2026-10-4补)
		assertEquals(2, ((Number) querySingle(convert("select strpos(name,'dmi') from sqltoy_probe_t1 where id=1",
				"hana"))).intValue(), "strpos应转locate(源串,子串)并正确执行");
		// instr同序
		assertEquals(2, ((Number) querySingle(convert("select instr(name,'dmi') from sqltoy_probe_t1 where id=1",
				"hana"))).intValue(), "instr应转locate(源串,子串)并正确执行");
		// 三参instr(带起始位置)同序转locate(str,sub,start)
		assertEquals(2, ((Number) querySingle(convert("select instr(name,'dmi',1) from sqltoy_probe_t1 where id=1",
				"hana"))).intValue(), "三参instr应转locate(源串,子串,起始)并正确执行");
		// charindex(needle,haystack)参数序对调
		assertEquals(2, ((Number) querySingle(convert("select charindex('dmi',name) from sqltoy_probe_t1 where id=1",
				"hana"))).intValue(), "charindex应转locate(源串,子串)并正确执行");
	}

	/** nvl→ifnull、单参isnull判空、nvl2判非空(2026-10-4补) */
	@Test
	public void nullCheckConversions() throws Exception {
		assertEquals("admin", querySingle(convert("select nvl(name,'none') from sqltoy_probe_t1 where id=1", "hana"))
				.toString(), "nvl应转ifnull并正确执行");
		// 单参isnull判空语义:mysql的isnull(expr)返回0/1
		assertEquals(0, ((Number) querySingle(convert("select isnull(name) from sqltoy_probe_t1 where id=1", "hana")))
				.intValue(), "单参isnull应转判空表达式");
		assertEquals(1, ((Number) querySingle(convert("select isnull(remark) from sqltoy_probe_t1 where id=1", "hana")))
				.intValue(), "单参isnull对null列应返回1");
		// nvl2(非空取b否则c):remark为null的行取'n'
		assertEquals("n", querySingle(convert("select nvl2(remark,'y','n') from sqltoy_probe_t1 where id=1", "hana"))
				.toString(), "nvl2应转case when判非空");
		assertEquals("y", querySingle(convert("select nvl2(remark,'y','n') from sqltoy_probe_t1 where id=2", "hana"))
				.toString(), "nvl2非空应取第二参");
	}

	/** concat_ws转||逐段跳null(2026-9-15语义):null参数及分隔符一并跳过,不留悬挂分隔符 */
	@Test
	public void concatWsSkipsNull() throws Exception {
		// remark为null:结果应为'admin'而非'admin-'
		assertEquals("admin", querySingle(convert("select concat_ws('-',name,remark) from sqltoy_probe_t1 where id=1",
				"hana")).toString(), "concat_ws应跳过null参数及分隔符");
		assertEquals("user-r2", querySingle(convert("select concat_ws('-',name,remark) from sqltoy_probe_t1 where id=2",
				"hana")).toString(), "concat_ws非null应正常拼接");
		// concat三参转||拼接(hana的concat仅两参)
		assertEquals("admin-x", querySingle(convert("select concat(name,'-','x') from sqltoy_probe_t1 where id=1",
				"hana")).toString(), "concat三参应转||拼接");
	}

	/** group_concat→string_agg(2.0 SPS04+两参形态)、to_char原生oracle兼容模型 */
	@Test
	public void aggregateAndToCharConversions() throws Exception {
		assertEquals("admin,user", querySingle(convert("select group_concat(name) from sqltoy_probe_t1", "hana"))
				.toString().trim(), "group_concat应转string_agg并正确执行");
		assertEquals("2026-01-15", querySingle(convert(
				"select to_char(create_time,'yyyy-mm-dd') from sqltoy_probe_t1 where id=1", "hana")).toString(),
				"to_char原生保留并正确执行");
		// date_format(mysql源)转to_char(oracle兼容格式模型)
		assertEquals("2026-01-15 10:30:00", querySingle(convert(
				"select date_format(create_time,'%Y-%m-%d %H:%i:%s') from sqltoy_probe_t1 where id=1", "hana"))
				.toString(), "date_format应转to_char(oracle模型)并正确执行");
	}

	/** update 2026-10-4 substr负起点:hana原生为pg语义(真库实测substr('abcdef',-2)返回整串、
	 * substr('abcdef',-2,1)='a',与mysql不兼容),字面量负起点转RIGHT/CASE守卫按mysql语义执行 */
	@Test
	public void substrNegativeStart() throws Exception {
		assertEquals("in", querySingle(convert("select substr(name,-2) from sqltoy_probe_t1 where id=1", "hana"))
				.toString(), "负起点两参应转RIGHT取末n位");
		assertEquals("i", querySingle(convert("select substr(name,-2,1) from sqltoy_probe_t1 where id=1", "hana"))
				.toString(), "负起点三参应转case守卫形态(起点=串长-n+1)");
		// 非负起点substr为hana原生(SUBSTRING别名),保持透传
		assertEquals("adm", querySingle(convert("select substr(name,1,3) from sqltoy_probe_t1 where id=1", "hana"))
				.toString(), "非负起点substr应原生透传");
	}
}
