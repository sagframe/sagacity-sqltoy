package org.sagacity.sqltoy.dialect;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.junit.jupiter.api.Assertions.assertNotNull;

import java.io.File;
import java.io.FileInputStream;
import java.io.InputStream;
import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.Statement;
import java.util.List;
import java.util.Properties;

import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.sagacity.sqltoy.SqlToyContext;
import org.sagacity.sqltoy.config.model.SqlToyConfig;
import org.sagacity.sqltoy.dialect.impl.ClickHouseDialect;
import org.sagacity.sqltoy.model.DBProfile;
import org.sagacity.sqltoy.model.QueryExecutor;
import org.sagacity.sqltoy.model.QueryResult;
import org.sagacity.sqltoy.plugins.function.FunctionUtils;
import org.sagacity.sqltoy.utils.DataSourceUtils;

/**
 * ClickHouse真实库冒烟:验证本轮Now补的clickhouse→now()映射,以及CH目标的
 * IGNORE边界(未做CH专属映射的函数原样保留,执行报错属已知文档化缺口)。
 * 连接配置从target/clickhouse-probe.properties读取。
 */
public class ClickHouseRealDbSmokeTest {

	private static Connection conn;

	private static boolean available = false;

	@BeforeAll
	public static void init() throws Exception {
		File cfg = new File("target/clickhouse-probe.properties");
		if (!cfg.exists()) {
			return;
		}
		Properties p = new Properties();
		try (InputStream in = new FileInputStream(cfg)) {
			p.load(in);
		}
		Class.forName("com.clickhouse.jdbc.ClickHouseDriver");
		// update 2026-11-10 clickhouse-jdbc为懒连接:容器已停时getConnection不报错,DDL执行期才抛连接异常,
		// 故先做TCP可达性判定,避免整类在init的DDL阶段报错而不是跳过
		org.junit.jupiter.api.Assumptions.assumeTrue(
				org.sagacity.sqltoy.support.RealDbProbeSupport.isReachable(p.getProperty("url")),
				"clickhouse not reachable, skipped (see docker/README.md)");
		try {
			conn = DriverManager.getConnection(p.getProperty("url"));
		} catch (Exception e) {
			// update 2026-11-10 探针在但库不可达(容器已停)时整类跳过
			org.junit.jupiter.api.Assumptions.abort("clickhouse not reachable, skipped: " + e.getMessage());
		}
		try (Statement st = conn.createStatement()) {
			st.execute("drop table if exists sqltoy_probe_t1");
			st.execute("create table sqltoy_probe_t1 (id UInt8, name String, score Decimal(10,2), "
					+ "create_time DateTime) engine = MergeTree() order by id");
			st.execute("insert into sqltoy_probe_t1 values (1,'admin',88.5,'2026-01-15 10:30:00')");
			st.execute("insert into sqltoy_probe_t1 values (2,'user',72.0,'2026-03-20 14:00:00')");
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
		try (Statement st = conn.createStatement(); ResultSet rs = st.executeQuery(sql)) {
			rs.next();
			return rs.getObject(1);
		} catch (Exception e) {
			Throwable root = e;
			while (root.getCause() != null) {
				root = root.getCause();
			}
			System.out.println("[CH_ERR] " + root.getMessage());
			throw e;
		}
	}

	@Test
	public void chAvailable() {
		org.junit.jupiter.api.Assumptions.assumeTrue(available, "clickhouse探针环境不可用,跳过");
	}

	/** Now的clickhouse映射:sysdate/now/getdate均转now()并正确执行 */
	@Test
	public void nowConversions() throws Exception {
		org.junit.jupiter.api.Assumptions.assumeTrue(available, "clickhouse探针环境不可用,跳过");
		assertEquals("select now() from sqltoy_probe_t1 where id=1".replaceAll("\\s+", " "),
				convert("select sysdate from sqltoy_probe_t1 where id=1", "clickhouse").replaceAll("\\s+", " ")
						.trim().isEmpty() ? "" : convert("select sysdate from sqltoy_probe_t1 where id=1", "clickhouse")
								.replaceAll("\\s+", " ").trim(),
				"sysdate应转now()");
		assertEquals("admin", querySingle(convert(
				"select name from sqltoy_probe_t1 where id=1 and create_time > now() - INTERVAL 1 YEAR", "clickhouse"))
				.toString());
	}

	/** CH原生函数在IGNORE下原样执行:concat/length/substr/if */
	@Test
	public void chNativeFunctions() throws Exception {
		org.junit.jupiter.api.Assumptions.assumeTrue(available, "clickhouse探针环境不可用,跳过");
		assertEquals("admin-x", querySingle(convert(
				"select concat(name,'-','x') from sqltoy_probe_t1 where id=1", "clickhouse")).toString());
		assertEquals(5, ((Number) querySingle(convert("select length(name) from sqltoy_probe_t1 where id=1",
				"clickhouse"))).intValue());
		assertEquals("adm", querySingle(convert("select substr(name,1,3) from sqltoy_probe_t1 where id=1",
				"clickhouse")).toString());
		assertEquals("high", querySingle(convert(
				"select if(score > 80, 'high', 'low') from sqltoy_probe_t1 where id=1", "clickhouse")).toString());
		// update 2026-10-4 curdate转today()执行返回今天日期
		String chCurdate = querySingle(convert("select curdate() from sqltoy_probe_t1 where id=1", "clickhouse"))
				.toString();
		org.junit.jupiter.api.Assertions.assertTrue(chCurdate.matches("\\d{4}-\\d{2}-\\d{2}"),
				"curdate应转today()并返回今天日期: " + chCurdate);
	}

	/** update 2026-10-4 nvl/group_concat在CH目标的转换已补齐(Nvl 9-9/GroupConcat 9-11),
	 * 原"文档化缺口"断言随之过期,此处锁定当前转换形态 */
	@Test
	public void chDocumentedGaps() throws Exception {
		org.junit.jupiter.api.Assumptions.assumeTrue(available, "clickhouse探针环境不可用,跳过");
		String nvl = convert("select nvl(name,'none') from sqltoy_probe_t1 where id=1", "clickhouse");
		assertTrue(nvl.contains("coalesce(name,'none')"), "nvl在CH目标应转coalesce: " + nvl);
		String gf = convert("select group_concat(name) from sqltoy_probe_t1", "clickhouse");
		assertTrue(gf.contains("arrayStringConcat(groupArray(name)"),
				"group_concat在CH目标应转arrayStringConcat(groupArray): " + gf);
	}

	/**
	 * update 2026-11-10 集合语句的top/分页:CH的limit/offset在集合运算尾部绑定右侧SELECT
	 * (A except B limit N 的N修饰B,整体不截断),DefaultDialectUtils原先无条件追加limit/offset,
	 * 导致 top(2) 返回全量、Page(2,2) 的offset整体失效;修复为对集合语句先包派生表再挂limit/offset。
	 * 断言值(不只断行数)才能区分"offset未生效"(返回首两条)与"offset生效"(返回第3、4条)
	 */
	@Test
	public void setOperationTopAndPaging() throws Exception {
		org.junit.jupiter.api.Assumptions.assumeTrue(available, "clickhouse探针环境不可用,跳过");
		ClickHouseDialect dialect = new ClickHouseDialect();
		// A=0..9(10行) except B=0..4(5行) => {5,6,7,8,9}
		String sql = "select number from numbers(10) except select number from numbers(5)";
		SqlToyContext sqlToyContext = new SqlToyContext();
		DBProfile profile = new DBProfile(null, "clickhouse", DataSourceUtils.DBType.CLICKHOUSE, null, 0, null, null,
				false);
		SqlToyConfig topConfig = new SqlToyConfig("clickhouse");
		topConfig.setSql(sql);
		QueryResult top = dialect.findTopBySql(sqlToyContext, topConfig, new QueryExecutor(sql), null, 2, conn, profile,
				500, -1);
		assertEquals(2, ((List<?>) top.getRows()).size(), "集合语句top(2)应截断整体为2行(LIMIT绑右分支时为8行)");
		assertEquals("5", String.valueOf(firstCell(top)), "top(2)首行应为集合结果第1条");

		SqlToyConfig pageConfig = new SqlToyConfig("clickhouse");
		pageConfig.setSql(sql);
		QueryResult page = dialect.findPageBySql(sqlToyContext, pageConfig, new QueryExecutor(sql), null, 2L, 2, conn,
				profile, 500, -1);
		assertEquals(2, ((List<?>) page.getRows()).size(), "集合语句Page(2,2)应为2行(offset失效时为8行)");
		assertEquals("7", String.valueOf(firstCell(page)), "Page(2,2)首行应为第3条(7),验证offset生效");
	}

	/**
	 * update 2026-11-10 @fast 与集合语句叠加:包裹必须发生在 fast 括号内层,
	 * 生成形态 select * from ( select * from (A except B) SAG_INTERMEDIATE_TABLE limit N ) ft,
	 * 否则 limit 仍落在 fast 内层集合语句的右分支上
	 */
	@Test
	public void setOperationTopWithFastWrapper() throws Exception {
		org.junit.jupiter.api.Assumptions.assumeTrue(available, "clickhouse探针环境不可用,跳过");
		ClickHouseDialect dialect = new ClickHouseDialect();
		String innerSql = "select number from numbers(10) except select number from numbers(5)";
		SqlToyConfig config = new SqlToyConfig("clickhouse");
		// 模拟@fast形态:preSql + "(" + innerSql + ")" + tail
		config.setHasFast(true);
		config.setFastPreSql("select * from");
		config.setFastSql(innerSql);
		config.setFastTailSql("ft");
		DBProfile profile = new DBProfile(null, "clickhouse", DataSourceUtils.DBType.CLICKHOUSE, null, 0, null, null,
				false);
		QueryResult top = dialect.findTopBySql(new SqlToyContext(), config, new QueryExecutor(innerSql), null, 2, conn,
				profile, 500, -1);
		assertEquals(2, ((List<?>) top.getRows()).size(), "@fast+集合语句:top(2)应截断整体为2行");
		assertEquals("5", String.valueOf(firstCell(top)), "@fast+集合语句:top(2)首行应为集合结果第1条");
	}

	/** 取结果首行首列(兼容List行/Object[]行/单值行) */
	private static Object firstCell(QueryResult result) {
		Object row = ((List<?>) result.getRows()).get(0);
		if (row instanceof Object[]) {
			return ((Object[]) row)[0];
		}
		if (row instanceof List) {
			return ((List<?>) row).get(0);
		}
		return row;
	}
}
