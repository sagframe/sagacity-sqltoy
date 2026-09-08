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

import javax.sql.DataSource;

import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.sagacity.sqltoy.SqlToyContext;
import org.sagacity.sqltoy.config.model.SqlToyConfig;
import org.sagacity.sqltoy.dialect.DialectFactory;
import org.sagacity.sqltoy.model.QueryExecutor;
import org.sagacity.sqltoy.plugins.function.FunctionUtils;

/**
 * Oracle真实库冒烟:验证本轮函数转换/字面量安全/union-all-count改动在真实oracle上的最终效果。
 * 连接配置从target/oracle-probe.properties读取(该目录不入库,避免凭据进入仓库),
 * 文件不存在或连接失败时整组用例自动跳过。
 * 探针表sqltoy_probe_t1/t2由用例创建并在结束时清理。
 */
public class OracleRealDbSmokeTest {

	private static Connection conn;

	private static SqlToyContext context;

	private static DataSource dataSource;

	private static boolean available = false;

	@BeforeAll
	public static void init() throws Exception {
		File cfg = new File("target/oracle-probe.properties");
		if (!cfg.exists()) {
			return;
		}
		Properties p = new Properties();
		try (InputStream in = new FileInputStream(cfg)) {
			p.load(in);
		}
		Class.forName("oracle.jdbc.driver.OracleDriver");
		conn = DriverManager.getConnection(p.getProperty("url"), p.getProperty("username"), p.getProperty("password"));
		try (Statement st = conn.createStatement()) {
			dropProbeTables(st);
			st.execute("create table sqltoy_probe_t1 (id number(10) primary key, name varchar2(100), "
					+ "score number(10,2), remark varchar2(200), create_time date)");
			st.execute("insert into sqltoy_probe_t1 values (1,'admin',88.5,null,"
					+ "to_date('2026-01-15 10:30:00','yyyy-MM-dd hh24:mi:ss'))");
			st.execute("insert into sqltoy_probe_t1 values (2,'user',72.0,null,"
					+ "to_date('2026-03-20 14:00:00','yyyy-MM-dd hh24:mi:ss'))");
			st.execute("create table sqltoy_probe_t2 (id number(10) primary key, b number(10,2))");
			st.execute("insert into sqltoy_probe_t2 values (11,110.0)");
			st.execute("insert into sqltoy_probe_t2 values (12,120.0)");
		}
		dataSource = wrapOracle(p);
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
				"org.sagacity.sqltoy.plugins.function.impl.DateDiff",
				"org.sagacity.sqltoy.plugins.function.impl.Decode"));
		available = true;
	}

	private static void dropProbeTables(Statement st) {
		try {
			st.execute("drop table sqltoy_probe_t1 purge");
		} catch (Exception e) {
			// 首次运行表不存在,忽略
		}
		try {
			st.execute("drop table sqltoy_probe_t2 purge");
		} catch (Exception e) {
			// 忽略
		}
	}

	private static DataSource wrapOracle(Properties p) throws Exception {
		oracle.jdbc.pool.OracleDataSource ds = new oracle.jdbc.pool.OracleDataSource();
		ds.setURL(p.getProperty("url"));
		ds.setUser(p.getProperty("username"));
		ds.setPassword(p.getProperty("password"));
		return ds;
	}

	@AfterAll
	public static void destroy() throws Exception {
		if (conn != null) {
			try (Statement st = conn.createStatement()) {
				dropProbeTables(st);
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

	/** 将日期结果to_char后断言,规避JDBC驱动日期字符串格式差异 */
	private String queryDateStr(String convertedInnerSql) throws Exception {
		return querySingle("select to_char((" + convertedInnerSql + "),'yyyy-MM-dd hh24:mi:ss') from dual").toString();
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

	/** oracle连接可用性(不可达时整组跳过而非报错) */
	@Test
	public void oracleAvailable() {
		org.junit.jupiter.api.Assumptions.assumeTrue(available, "oracle探针环境不可用,跳过");
	}

	@Test
	public void functionExecutionMatrix() throws Exception {
		org.junit.jupiter.api.Assumptions.assumeTrue(available, "oracle探针环境不可用,跳过");
		// nvl原生保留;isnull/ifnull转nvl
		assertEquals("admin",
				querySingle(convert("select nvl(name,'none') from sqltoy_probe_t1 where id=1", "oracle")).toString());
		assertEquals("admin",
				querySingle(convert("select isnull(name,'none') from sqltoy_probe_t1 where id=1", "oracle"))
						.toString());
		assertEquals("admin",
				querySingle(convert("select ifnull(name,'none') from sqltoy_probe_t1 where id=1", "oracle"))
						.toString());
		// to_number为oracle原生,原样保留
		assertEquals("select to_number('88.5') from dual", convert("select to_number('88.5') from dual", "oracle"));
		assertEquals(88.5, ((Number) querySingle(convert("select to_number('88.5') from dual", "oracle")))
				.doubleValue(), 0.000001, "单参to_number执行错误");
		assertEquals(88.5, ((Number) querySingle(
				convert("select to_number(score) from sqltoy_probe_t1 where id=1", "oracle"))).doubleValue(),
				0.000001, "数值列to_number执行错误");
		// to_date两参原生保留;单参短串补'yyyy-MM-dd';单参长串补'yyyy-MM-dd HH24:mi:ss'
		assertEquals("2024-01-01 00:00:00", queryDateStr(
				convert("select to_date('2024-01-01','yyyy-MM-dd') from dual", "oracle")), "两参to_date原生保留执行");
		assertEquals("2024-01-01 00:00:00", queryDateStr(convert("select to_date('2024-01-01') from dual", "oracle")),
				"单参短串to_date应补日期格式");
		assertEquals("2024-01-01 10:30:00", queryDateStr(convert("select to_date('2024-01-01 10:30:00') from dual",
				"oracle")), "单参长串to_date应补日期时间格式");
		// to_char原生保留;mysql风格格式token转oracle格式
		assertEquals("2026-01-15", querySingle(
				convert("select to_char(create_time,'yyyy-MM-dd') from sqltoy_probe_t1 where id=1", "oracle"))
				.toString(), "oracle原生to_char执行");
		assertEquals("select to_char(create_time,'yyyy-MM-dd') from sqltoy_probe_t1 where id=1",
				convert("select to_char(create_time,'%Y-%m-%d') from sqltoy_probe_t1 where id=1", "oracle"),
				"mysql格式token应转oracle格式模型");
		assertEquals("2026-01-15", querySingle(
				convert("select to_char(create_time,'%Y-%m-%d') from sqltoy_probe_t1 where id=1", "oracle"))
				.toString());
		// date_format转to_char,24小时制%H转hh24
		assertEquals("select to_char(create_time,'yyyy-MM-dd hh24:mi:ss') from sqltoy_probe_t1 where id=1",
				convert("select date_format(create_time,'%Y-%m-%d %H:%i:%s') from sqltoy_probe_t1 where id=1",
						"oracle"),
				"date_format应转to_char");
		assertEquals("2026-01-15 10:30:00", querySingle(convert(
				"select date_format(create_time,'%Y-%m-%d %H:%i:%s') from sqltoy_probe_t1 where id=1", "oracle"))
				.toString());
		// decode为oracle原生保留
		assertEquals("select decode(score,88.5,'high','low') from sqltoy_probe_t1 where id=1",
				convert("select decode(score,88.5,'high','low') from sqltoy_probe_t1 where id=1", "oracle"),
				"oracle原生decode应保留");
		assertEquals("high", querySingle(
				convert("select decode(score,88.5,'high','low') from sqltoy_probe_t1 where id=1", "oracle"))
				.toString());
		assertEquals("low", querySingle(
				convert("select decode(score,88.5,'high','low') from sqltoy_probe_t1 where id=2", "oracle"))
				.toString());
		// group_concat转listagg;order by null不保证行序,两种拼接顺序均正确;多列拼接以||重组后作为单个expr
		Object aggResult = querySingle(
				convert("select group_concat(name separator '-') from sqltoy_probe_t1", "oracle"));
		org.junit.jupiter.api.Assertions.assertTrue(
				"admin-user".equals(aggResult.toString()) || "user-admin".equals(aggResult.toString()),
				"group_concat应转listagg并正确执行,实际:" + aggResult);
		assertEquals("admin1", querySingle(convert("select group_concat(name,id separator '-') from sqltoy_probe_t1 "
				+ "where id=1", "oracle")).toString(), "group_concat多列拼接两列均应参与");
		// string_agg在oracle非法,响亮保留原样(不执行,仅断言转换不误改)
		assertEquals("select string_agg(name,'-') from sqltoy_probe_t1",
				convert("select string_agg(name,'-') from sqltoy_probe_t1", "oracle"), "string_agg应原样保留");
		// concat两参原生保留;三参起转||拼接
		assertEquals("admin-", querySingle(
				convert("select concat(name,'-') from sqltoy_probe_t1 where id=1", "oracle")).toString(),
				"两参concat原生保留");
		assertEquals("admin-x", querySingle(
				convert("select concat(name,'-','x') from sqltoy_probe_t1 where id=1", "oracle")).toString(),
				"三参concat应转||拼接");
		// concat_ws转||拼接(oracle无concat_ws)
		assertEquals("admin-x", querySingle(
				convert("select concat_ws('-',name,'x') from sqltoy_probe_t1 where id=1", "oracle")).toString(),
				"concat_ws应转||拼接");
		// length家族:length/len/char_length一致;lengthb原生保留(ascii字符字节数等于字符数)
		assertEquals(5, ((Number) querySingle(
				convert("select length(name) from sqltoy_probe_t1 where id=1", "oracle"))).intValue());
		assertEquals(5, ((Number) querySingle(
				convert("select len(name) from sqltoy_probe_t1 where id=1", "oracle"))).intValue(), "len应转length");
		assertEquals(5, ((Number) querySingle(
				convert("select char_length(name) from sqltoy_probe_t1 where id=1", "oracle"))).intValue(),
				"char_length应转length");
		assertEquals(5, ((Number) querySingle(
				convert("select lengthb(name) from sqltoy_probe_t1 where id=1", "oracle"))).intValue(),
				"lengthb原生保留执行");
		// substr/substring统一为substr;负起点取末尾
		assertEquals("ad", querySingle(
				convert("select substr(name,1,2) from sqltoy_probe_t1 where id=1", "oracle")).toString());
		assertEquals("ad", querySingle(
				convert("select substring(name,1,2) from sqltoy_probe_t1 where id=1", "oracle")).toString(),
				"substring应转substr");
		assertEquals("in", querySingle(
				convert("select substr(name,-2) from sqltoy_probe_t1 where id=1", "oracle")).toString(),
				"负起点substr取末2位");
		// trim修饰符形态oracle原生支持,原样保留
		assertEquals("admin", querySingle(
				convert("select trim(both ' ' from ' admin ') from dual", "oracle")).toString());
		assertEquals("admin", querySingle(convert("select trim(' admin ') from dual", "oracle")).toString());
		// instr原生;charindex/position转instr(源串,子串)
		assertEquals(2, ((Number) querySingle(
				convert("select instr(name,'dmi') from sqltoy_probe_t1 where id=1", "oracle"))).intValue());
		assertEquals("select instr(name,'dmi') from sqltoy_probe_t1 where id=1",
				convert("select charindex('dmi',name) from sqltoy_probe_t1 where id=1", "oracle"),
				"charindex应转instr(源串,子串)");
		assertEquals(2, ((Number) querySingle(
				convert("select charindex('dmi',name) from sqltoy_probe_t1 where id=1", "oracle"))).intValue());
		assertEquals(2, ((Number) querySingle(
				convert("select position('dmi' in name) from sqltoy_probe_t1 where id=1", "oracle"))).intValue(),
				"position应转instr");
		// sysdate原样;now()/getdate()转sysdate
		assertNotNullNow(convert("select sysdate from dual", "oracle"));
		assertEquals("select sysdate from dual", convert("select now() from dual", "oracle"), "now()应转sysdate");
		assertNotNullNow(convert("select now() from dual", "oracle"));
		assertNotNullNow(convert("select getdate() from dual", "oracle"));
		// if转case when
		assertEquals("high", querySingle(
				convert("select if(score > 80, 'high', 'low') from sqltoy_probe_t1 where id=1", "oracle")).toString(),
				"if应转case when");
		assertEquals("low", querySingle(
				convert("select if(score > 80, 'high', 'low') from sqltoy_probe_t1 where id=2", "oracle")).toString());
	}

	@Test
	public void datediffExecutionMatrix() throws Exception {
		org.junit.jupiter.api.Assumptions.assumeTrue(available, "oracle探针环境不可用,跳过");
		// 两参datediff=d1-d2:2026-01-15 10:30与2026-01-10相差5天(TRUNC截断时间部分)
		assertEquals(5, ((Number) querySingle(convert(
				"select datediff(create_time, to_date('2026-01-10','yyyy-MM-dd')) from sqltoy_probe_t1 where id=1",
				"oracle"))).intValue(), "两参datediff=d1-d2");
		// 三参datediff=d2-d1
		assertEquals(5, ((Number) querySingle(convert("select datediff(day, to_date('2026-01-15','yyyy-MM-dd'), "
				+ "to_date('2026-01-20','yyyy-MM-dd')) from dual", "oracle"))).intValue(), "三参day=d2-d1");
		assertEquals(2, ((Number) querySingle(convert("select datediff(month, to_date('2026-01-15','yyyy-MM-dd'), "
				+ "to_date('2026-03-20','yyyy-MM-dd')) from dual", "oracle"))).intValue(), "三参month=d2-d1");
		assertEquals(2, ((Number) querySingle(convert(
				"select datediff(hour, to_date('2026-01-15 10:30:00','yyyy-MM-dd hh24:mi:ss'), "
						+ "to_date('2026-01-15 12:30:00','yyyy-MM-dd hh24:mi:ss')) from dual", "oracle")))
				.intValue(), "三参hour按天数*24换算");
		// timestampdiff同契约
		assertEquals(5, ((Number) querySingle(convert("select timestampdiff(DAY, to_date('2026-01-15','yyyy-MM-dd'), "
				+ "to_date('2026-01-20','yyyy-MM-dd')) from dual", "oracle"))).intValue(), "timestampdiff=d2-d1");
		// 裸字符串字面量:转换包to_date后在真实oracle上可执行(修复前TRUNC('...')报ORA-01722/ORA-01861)
		assertEquals(5, ((Number) querySingle(
				convert("select datediff('2026-01-20','2026-01-15') from dual", "oracle"))).intValue(),
				"两参字面量datediff应包to_date执行");
		assertEquals(5, ((Number) querySingle(
				convert("select datediff(day,'2026-01-15','2026-01-20') from dual", "oracle"))).intValue(),
				"三参day字面量datediff应包to_date执行");
		assertEquals(2, ((Number) querySingle(
				convert("select datediff(month,'2026-01-15 10:30:00','2026-03-20 10:30:00') from dual", "oracle")))
						.intValue(), "三参month含时间字面量执行");
	}

	private void assertNotNullNow(String sql) throws Exception {
		Object v = querySingle(sql);
		assertNotNull(v, "时间函数执行结果不应为null: " + sql);
	}

	/** 字面量安全:字面量内的函数文本不得破坏语句,且语句在真实oracle上正确执行 */
	@Test
	public void literalSafetyOnRealOracle() throws Exception {
		org.junit.jupiter.api.Assumptions.assumeTrue(available, "oracle探针环境不可用,跳过");
		String literalNvl = convert("select name from sqltoy_probe_t1 where name='nvl(a,b)'", "oracle");
		assertEquals("select name from sqltoy_probe_t1 where name='nvl(a,b)'", literalNvl, "字面量nvl(a,b)不得被转换");
		assertEquals(0, countByQuery(literalNvl), "字面量比较语句应正常执行且无匹配");
		String literalSysdate = convert("select name from sqltoy_probe_t1 where remark='sysdate'", "oracle");
		assertEquals("select name from sqltoy_probe_t1 where remark='sysdate'", literalSysdate,
				"sysdate字面量不得被转换");
		String literalIf = convert("select name from sqltoy_probe_t1 where remark='if(a,b)'", "oracle");
		assertEquals("select name from sqltoy_probe_t1 where remark='if(a,b)'", literalIf, "if字面量不得转case when");
		// 参数字面量中的括号不得干扰参数终结符判定,语句在真实oracle上正确执行
		assertEquals("(", querySingle(convert("select nvl(remark,'(') from sqltoy_probe_t1 where id=1", "oracle"))
				.toString(), "含括号字面量的nvl应正确执行");
	}

	/** union-all-count真实工厂链路:DialectFactory.getCountBySql在真实oracle上的计数 */
	@Test
	public void unionAllCountRealFactory() throws Exception {
		org.junit.jupiter.api.Assumptions.assumeTrue(available, "oracle探针环境不可用,跳过");
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
		SqlToyConfig config = new SqlToyConfig("oracle");
		config.setSql(sql);
		config.setUnionAllCount(true);
		QueryExecutor queryExecutor = new QueryExecutor(sql);
		return DialectFactory.getInstance().getCountBySql(context, queryExecutor, config, dataSource);
	}
}
