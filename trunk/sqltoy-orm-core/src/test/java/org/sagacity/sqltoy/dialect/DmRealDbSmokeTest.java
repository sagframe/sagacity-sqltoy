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
 * DM(达梦)真实库冒烟:验证本轮函数转换/字面量安全/union-all-count改动在真实dm上的最终效果。
 * 连接配置从target/dm-probe.properties读取(该目录不入库,避免凭据进入仓库),
 * 文件不存在或连接失败时整组用例自动跳过。
 * 探针表sqltoy_probe_t1/t2由用例创建并在结束时清理。
 * dm与oracle同为函数转换的oracle系归属,但存在差异点:单参to_date不转换(dm原生可执行)、
 * concat/concat_ws不转||(dm原生支持多参)、datediff不转换(dm原生三参datediff语义d2-d1恰与契约一致)、
 * dm不支持charindex,charindex转instr为必需转换。
 */
public class DmRealDbSmokeTest {

	private static Connection conn;

	private static SqlToyContext context;

	private static DataSource dataSource;

	private static boolean available = false;

	@BeforeAll
	public static void init() throws Exception {
		File cfg = new File("target/dm-probe.properties");
		if (!cfg.exists()) {
			return;
		}
		Properties p = new Properties();
		try (InputStream in = new FileInputStream(cfg)) {
			p.load(in);
		}
		Class.forName("dm.jdbc.driver.DmDriver");
		conn = DriverManager.getConnection(p.getProperty("url"), p.getProperty("username"), p.getProperty("password"));
		try (Statement st = conn.createStatement()) {
			dropProbeTables(st);
			// dm的date列类型仅存日期部分(与oracle DATE含时间不同),时间部分需用timestamp承载
			st.execute("create table sqltoy_probe_t1 (id number(10) primary key, name varchar(100), "
					+ "score number(10,2), remark varchar(200), create_time timestamp)");
			st.execute("insert into sqltoy_probe_t1 values (1,'admin',88.5,null,"
					+ "to_date('2026-01-15 10:30:00','yyyy-MM-dd hh24:mi:ss'))");
			st.execute("insert into sqltoy_probe_t1 values (2,'user',72.0,null,"
					+ "to_date('2026-03-20 14:00:00','yyyy-MM-dd hh24:mi:ss'))");
			st.execute("create table sqltoy_probe_t2 (id number(10) primary key, b number(10,2))");
			st.execute("insert into sqltoy_probe_t2 values (11,110.0)");
			st.execute("insert into sqltoy_probe_t2 values (12,120.0)");
		}
		dataSource = wrapDm(p);
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
			st.execute("drop table sqltoy_probe_t1");
		} catch (Exception e) {
			// 首次运行表不存在,忽略
		}
		try {
			st.execute("drop table sqltoy_probe_t2");
		} catch (Exception e) {
			// 忽略
		}
	}

	private static DataSource wrapDm(Properties p) throws Exception {
		dm.jdbc.driver.DmdbDataSource ds = new dm.jdbc.driver.DmdbDataSource();
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

	/** dm连接可用性(不可达时整组跳过而非报错) */
	@Test
	public void dmAvailable() {
		org.junit.jupiter.api.Assumptions.assumeTrue(available, "dm探针环境不可用,跳过");
	}

	@Test
	public void functionExecutionMatrix() throws Exception {
		org.junit.jupiter.api.Assumptions.assumeTrue(available, "dm探针环境不可用,跳过");
		// nvl原生保留;isnull/ifnull转nvl(dm无isnull/ifnull)
		assertEquals("admin",
				querySingle(convert("select nvl(name,'none') from sqltoy_probe_t1 where id=1", "dm")).toString());
		assertEquals("admin",
				querySingle(convert("select isnull(name,'none') from sqltoy_probe_t1 where id=1", "dm")).toString());
		assertEquals("admin",
				querySingle(convert("select ifnull(name,'none') from sqltoy_probe_t1 where id=1", "dm")).toString());
		// to_number为dm原生,原样保留
		assertEquals("select to_number('88.5') from dual", convert("select to_number('88.5') from dual", "dm"));
		assertEquals(88.5, ((Number) querySingle(convert("select to_number('88.5') from dual", "dm")))
				.doubleValue(), 0.000001, "单参to_number执行错误");
		assertEquals(88.5, ((Number) querySingle(
				convert("select to_number(score) from sqltoy_probe_t1 where id=1", "dm"))).doubleValue(),
				0.000001, "数值列to_number执行错误");
		// to_date在dm不转换(oracle系特有单参补格式逻辑不适用),dm原生支持两参与单参形态
		assertEquals("select to_date('2024-01-01','yyyy-MM-dd') from dual",
				convert("select to_date('2024-01-01','yyyy-MM-dd') from dual", "dm"), "两参to_date应原样保留");
		assertEquals("2024-01-01 00:00:00",
				queryDateStr(convert("select to_date('2024-01-01','yyyy-MM-dd') from dual", "dm")), "两参to_date执行");
		assertEquals("select to_date('2024-01-01') from dual", convert("select to_date('2024-01-01') from dual", "dm"),
				"单参to_date应原样保留");
		assertEquals("2024-01-01 00:00:00", queryDateStr(convert("select to_date('2024-01-01') from dual", "dm")),
				"dm原生单参to_date可执行");
		// to_char原生保留;mysql风格格式token转oracle格式模型
		assertEquals("2026-01-15", querySingle(
				convert("select to_char(create_time,'yyyy-MM-dd') from sqltoy_probe_t1 where id=1", "dm")).toString(),
				"dm原生to_char执行");
		assertEquals("select to_char(create_time,'yyyy-MM-dd') from sqltoy_probe_t1 where id=1",
				convert("select to_char(create_time,'%Y-%m-%d') from sqltoy_probe_t1 where id=1", "dm"),
				"mysql格式token应转oracle格式模型");
		// date_format转to_char,24小时制%H转hh24
		assertEquals("select to_char(create_time,'yyyy-MM-dd hh24:mi:ss') from sqltoy_probe_t1 where id=1",
				convert("select date_format(create_time,'%Y-%m-%d %H:%i:%s') from sqltoy_probe_t1 where id=1", "dm"),
				"date_format应转to_char");
		assertEquals("2026-01-15 10:30:00", querySingle(convert(
				"select date_format(create_time,'%Y-%m-%d %H:%i:%s') from sqltoy_probe_t1 where id=1", "dm"))
				.toString());
		// decode为dm原生保留
		assertEquals("select decode(score,88.5,'high','low') from sqltoy_probe_t1 where id=1",
				convert("select decode(score,88.5,'high','low') from sqltoy_probe_t1 where id=1", "dm"),
				"dm原生decode应保留");
		assertEquals("high", querySingle(
				convert("select decode(score,88.5,'high','low') from sqltoy_probe_t1 where id=1", "dm")).toString());
		assertEquals("low", querySingle(
				convert("select decode(score,88.5,'high','low') from sqltoy_probe_t1 where id=2", "dm")).toString());
		// group_concat转listagg(dm支持order by null形态);order by null不保证行序,两种拼接顺序均正确;
		// 多列拼接以||重组
		Object aggResult = querySingle(
				convert("select group_concat(name separator '-') from sqltoy_probe_t1", "dm"));
		org.junit.jupiter.api.Assertions.assertTrue(
				"admin-user".equals(aggResult.toString()) || "user-admin".equals(aggResult.toString()),
				"group_concat应转listagg并正确执行,实际:" + aggResult);
		assertEquals("admin1", querySingle(convert("select group_concat(name,id separator '-') from sqltoy_probe_t1 "
				+ "where id=1", "dm")).toString(), "group_concat多列拼接两列均应参与");
		// string_agg在dm非法,响亮保留原样(不执行,仅断言转换不误改)
		assertEquals("select string_agg(name,'-') from sqltoy_probe_t1",
				convert("select string_agg(name,'-') from sqltoy_probe_t1", "dm"), "string_agg应原样保留");
		// concat在dm原生支持多参,不转||:两参与三参均原样保留并正确执行
		assertEquals("select concat(name,'-') from sqltoy_probe_t1 where id=1",
				convert("select concat(name,'-') from sqltoy_probe_t1 where id=1", "dm"), "两参concat应原样保留");
		assertEquals("admin-", querySingle(
				convert("select concat(name,'-') from sqltoy_probe_t1 where id=1", "dm")).toString());
		assertEquals("admin-x", querySingle(
				convert("select concat(name,'-','x') from sqltoy_probe_t1 where id=1", "dm")).toString(),
				"dm原生三参concat可执行");
		// concat_ws在dm原生保留(仅双引号分割符修正为单引号)
		assertEquals("select concat_ws('-',name,'x') from sqltoy_probe_t1 where id=1",
				convert("select concat_ws('-',name,'x') from sqltoy_probe_t1 where id=1", "dm"),
				"concat_ws应原样保留");
		assertEquals("admin-x", querySingle(
				convert("select concat_ws('-',name,'x') from sqltoy_probe_t1 where id=1", "dm")).toString());
		assertEquals("select concat_ws('-',name,'x') from sqltoy_probe_t1 where id=1",
				convert("select concat_ws(\"-\",name,'x') from sqltoy_probe_t1 where id=1", "dm"),
				"双引号分割符应修正为单引号");
		// length家族:length/len/char_length一致;lengthb原生保留(ascii字符字节数等于字符数)
		assertEquals(5, ((Number) querySingle(
				convert("select length(name) from sqltoy_probe_t1 where id=1", "dm"))).intValue());
		assertEquals(5, ((Number) querySingle(
				convert("select len(name) from sqltoy_probe_t1 where id=1", "dm"))).intValue(), "len应转length");
		assertEquals(5, ((Number) querySingle(
				convert("select char_length(name) from sqltoy_probe_t1 where id=1", "dm"))).intValue(),
				"char_length应转length");
		assertEquals(5, ((Number) querySingle(
				convert("select lengthb(name) from sqltoy_probe_t1 where id=1", "dm"))).intValue(),
				"lengthb原生保留执行");
		// substr/substring统一为substr;负起点取末尾
		assertEquals("ad", querySingle(
				convert("select substr(name,1,2) from sqltoy_probe_t1 where id=1", "dm")).toString());
		assertEquals("ad", querySingle(
				convert("select substring(name,1,2) from sqltoy_probe_t1 where id=1", "dm")).toString(),
				"substring应转substr");
		assertEquals("in", querySingle(
				convert("select substr(name,-2) from sqltoy_probe_t1 where id=1", "dm")).toString(),
				"负起点substr取末2位");
		// trim修饰符形态dm原生支持,原样保留
		assertEquals("admin", querySingle(convert("select trim(both ' ' from ' admin ') from dual", "dm")).toString());
		assertEquals("admin", querySingle(convert("select trim(' admin ') from dual", "dm")).toString());
		// instr原生;charindex/position转instr(dm不支持charindex,此转换为必需)
		assertEquals(2, ((Number) querySingle(
				convert("select instr(name,'dmi') from sqltoy_probe_t1 where id=1", "dm"))).intValue());
		assertEquals("select instr(name,'dmi') from sqltoy_probe_t1 where id=1",
				convert("select charindex('dmi',name) from sqltoy_probe_t1 where id=1", "dm"),
				"charindex应转instr(源串,子串)");
		assertEquals(2, ((Number) querySingle(
				convert("select charindex('dmi',name) from sqltoy_probe_t1 where id=1", "dm"))).intValue());
		assertEquals(2, ((Number) querySingle(
				convert("select position('dmi' in name) from sqltoy_probe_t1 where id=1", "dm"))).intValue(),
				"position应转instr");
		// sysdate原样;now()/getdate()转sysdate
		assertNotNullNow(convert("select sysdate from dual", "dm"));
		assertEquals("select sysdate from dual", convert("select now() from dual", "dm"), "now()应转sysdate");
		assertNotNullNow(convert("select now() from dual", "dm"));
		assertNotNullNow(convert("select getdate() from dual", "dm"));
		// if转case when(dm虽原生支持if,转换后语义一致)
		assertEquals("high", querySingle(
				convert("select if(score > 80, 'high', 'low') from sqltoy_probe_t1 where id=1", "dm")).toString(),
				"if应转case when");
		assertEquals("low", querySingle(
				convert("select if(score > 80, 'high', 'low') from sqltoy_probe_t1 where id=2", "dm")).toString());
	}

	@Test
	public void datediffKeptNativeOnDm() throws Exception {
		org.junit.jupiter.api.Assumptions.assumeTrue(available, "dm探针环境不可用,跳过");
		// datediff对dm不转换:三参dm原生支持且语义d2-d1,恰与统一契约一致
		assertEquals("select datediff(day, to_date('2026-01-15','yyyy-MM-dd'), "
				+ "to_date('2026-01-20','yyyy-MM-dd')) from dual",
				convert("select datediff(day, to_date('2026-01-15','yyyy-MM-dd'), "
						+ "to_date('2026-01-20','yyyy-MM-dd')) from dual", "dm"), "datediff应原样保留");
		assertEquals(5, ((Number) querySingle(convert("select datediff(day, to_date('2026-01-15','yyyy-MM-dd'), "
				+ "to_date('2026-01-20','yyyy-MM-dd')) from dual", "dm"))).intValue(), "三参datediff原生执行=d2-d1");
		// 两参datediff在dm非法,响亮保留原样(不执行,交由目标库报错)
		assertEquals("select datediff(create_time, create_time) from sqltoy_probe_t1",
				convert("select datediff(create_time, create_time) from sqltoy_probe_t1", "dm"),
				"两参datediff应原样保留");
	}

	private void assertNotNullNow(String sql) throws Exception {
		Object v = querySingle(sql);
		assertNotNull(v, "时间函数执行结果不应为null: " + sql);
	}

	/** 字面量安全:字面量内的函数文本不得破坏语句,且语句在真实dm上正确执行 */
	@Test
	public void literalSafetyOnRealDm() throws Exception {
		org.junit.jupiter.api.Assumptions.assumeTrue(available, "dm探针环境不可用,跳过");
		String literalNvl = convert("select name from sqltoy_probe_t1 where name='nvl(a,b)'", "dm");
		assertEquals("select name from sqltoy_probe_t1 where name='nvl(a,b)'", literalNvl, "字面量nvl(a,b)不得被转换");
		assertEquals(0, countByQuery(literalNvl), "字面量比较语句应正常执行且无匹配");
		String literalSysdate = convert("select name from sqltoy_probe_t1 where remark='sysdate'", "dm");
		assertEquals("select name from sqltoy_probe_t1 where remark='sysdate'", literalSysdate,
				"sysdate字面量不得被转换");
		String literalIf = convert("select name from sqltoy_probe_t1 where remark='if(a,b)'", "dm");
		assertEquals("select name from sqltoy_probe_t1 where remark='if(a,b)'", literalIf, "if字面量不得转case when");
		// 参数字面量中的括号不得干扰参数终结符判定,语句在真实dm上正确执行
		assertEquals("(", querySingle(convert("select nvl(remark,'(') from sqltoy_probe_t1 where id=1", "dm"))
				.toString(), "含括号字面量的nvl应正确执行");
	}

	/** union-all-count真实工厂链路:DialectFactory.getCountBySql在真实dm上的计数 */
	@Test
	public void unionAllCountRealFactory() throws Exception {
		org.junit.jupiter.api.Assumptions.assumeTrue(available, "dm探针环境不可用,跳过");
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
		SqlToyConfig config = new SqlToyConfig("dm");
		config.setSql(sql);
		config.setUnionAllCount(true);
		QueryExecutor queryExecutor = new QueryExecutor(sql);
		return DialectFactory.getInstance().getCountBySql(context, queryExecutor, config, dataSource);
	}
}
