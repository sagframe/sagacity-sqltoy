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
import org.sagacity.sqltoy.SqlToyContext;
import org.sagacity.sqltoy.config.model.SqlToyConfig;
import org.sagacity.sqltoy.dialect.impl.SqlServerDialect;
import org.sagacity.sqltoy.model.DBProfile;
import org.sagacity.sqltoy.model.LockMode;
import org.sagacity.sqltoy.model.QueryExecutor;
import org.sagacity.sqltoy.model.QueryResult;
import org.sagacity.sqltoy.model.TreeTableModel;
import org.sagacity.sqltoy.plugins.function.FunctionUtils;
import org.sagacity.sqltoy.utils.DataSourceUtils;
import org.sagacity.sqltoy.utils.SqlUtil;

/**
 * SQLServer方言特性真实库冒烟:分页(offset fetch)、top、随机、锁(with rowlock xlock)、
 * 树表路由(wrapTreeTableRoute)、geometry类型cast。
 * 连接配置从target/sqlserver-probe.properties读取;探针表测完即删。
 */
public class SqlServerFeatureSmokeTest {

	private static Connection conn;

	private static SqlToyContext context;

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
			st.execute("if object_id('sqltoy_probe_tree') is not null drop table sqltoy_probe_tree");
			st.execute("if object_id('sqltoy_probe_geom') is not null drop table sqltoy_probe_geom");
			st.execute("create table sqltoy_probe_t1 (id int primary key, name varchar(100), score decimal(10,2))");
			st.execute("insert into sqltoy_probe_t1 values (1,'admin',88.5)");
			st.execute("insert into sqltoy_probe_t1 values (2,'user',72.0)");
			st.execute("create table sqltoy_probe_tree (id int primary key, pid int, "
					+ "node_route varchar(50), node_level int, is_leaf int)");
			st.execute("insert into sqltoy_probe_tree values (1,null,'001',1,0)");
			st.execute("insert into sqltoy_probe_tree values (2,1,'001',2,1)");
			st.execute("insert into sqltoy_probe_tree values (3,2,'001002',3,1)");
			st.execute("create table sqltoy_probe_geom (id int primary key, geo geometry)");
		}
		context = new SqlToyContext();
		available = true;
	}

	@AfterAll
	public static void destroy() throws Exception {
		if (conn != null) {
			try (Statement st = conn.createStatement()) {
				st.execute("if object_id('sqltoy_probe_t1') is not null drop table sqltoy_probe_t1");
				st.execute("if object_id('sqltoy_probe_tree') is not null drop table sqltoy_probe_tree");
				st.execute("if object_id('sqltoy_probe_geom') is not null drop table sqltoy_probe_geom");
			} catch (Exception e) {
				// ignore
			}
			conn.close();
		}
	}

	private int countRows(String sql) throws Exception {
		try (PreparedStatement pst = conn.prepareStatement(sql); ResultSet rs = pst.executeQuery()) {
			int n = 0;
			while (rs.next()) {
				n++;
			}
			return n;
		}
	}

	/** 分页:offset fetch语法的真实执行与页数据正确性 */
	@Test
	public void pagination() throws Exception {
		org.junit.jupiter.api.Assumptions.assumeTrue(available, "sqlserver探针环境不可用,跳过");
		SqlServerDialect dialect = new SqlServerDialect();
		SqlToyConfig config = new SqlToyConfig("sqlserver");
		String sql = "select id,name from sqltoy_probe_t1 where id > 0 order by id";
		config.setSql(sql);
		QueryExecutor queryExecutor = new QueryExecutor(sql);
		QueryResult page1 = dialect.findPageBySql(context, config, queryExecutor, null, 1L, 1, conn, profile(), 500, -1);
		assertEquals(1, ((java.util.List<?>) page1.getRows()).size(), "第一页应1行");
		QueryResult page2 = dialect.findPageBySql(context, config, queryExecutor, null, 2L, 1, conn, profile(), 500, -1);
		assertEquals(1, ((java.util.List<?>) page2.getRows()).size(), "第二页应1行");
	}

	/** top查询 */
	@Test
	public void topQuery() throws Exception {
		org.junit.jupiter.api.Assumptions.assumeTrue(available, "sqlserver探针环境不可用,跳过");
		SqlServerDialect dialect = new SqlServerDialect();
		SqlToyConfig config = new SqlToyConfig("sqlserver");
		String sql = "select name from sqltoy_probe_t1 where score > 80 order by id";
		config.setSql(sql);
		QueryExecutor queryExecutor = new QueryExecutor(sql);
		QueryResult top = dialect.findTopBySql(context, config, queryExecutor, null, 1, conn, profile(), 500, -1);
		assertEquals(1, ((java.util.List<?>) top.getRows()).size(), "top 1应只返回1行");
	}

	/** 随机记录 */
	@Test
	public void randomQuery() throws Exception {
		org.junit.jupiter.api.Assumptions.assumeTrue(available, "sqlserver探针环境不可用,跳过");
		SqlServerDialect dialect = new SqlServerDialect();
		SqlToyConfig config = new SqlToyConfig("sqlserver");
		String sql = "select name from sqltoy_probe_t1 where score > 0";
		config.setSql(sql);
		QueryExecutor queryExecutor = new QueryExecutor(sql);
		QueryResult random = dialect.getRandomResult(context, config, queryExecutor, null, 2L, 1L, conn, profile(), 500, -1);
		assertEquals(1, ((java.util.List<?>) random.getRows()).size(), "随机应返回1行");
	}

	/** 锁:lockSql注入with rowlock xlock后真实执行,且hasLock幂等 */
	@Test
	public void lockSqlExecution() throws Exception {
		org.junit.jupiter.api.Assumptions.assumeTrue(available, "sqlserver探针环境不可用,跳过");
		String loadSql = "select name from sqltoy_probe_t1 where id=1";
		String locked = org.sagacity.sqltoy.dialect.utils.SqlServerDialectUtils.lockSql(loadSql, null,
				LockMode.UPGRADE);
		// 锁语句真实执行
		querySingle(locked);
		// 加锁后hasLock应识别(幂等)
		String again = org.sagacity.sqltoy.dialect.utils.SqlServerDialectUtils.lockSql(locked, null,
				LockMode.UPGRADE);
		assertEquals(locked, again, "已加锁的语句重复加锁应保持不变(幂等)");
	}

	/** 树表路由:递归更新层级/路径/叶子标志在真实sqlserver上执行 */
	@Test
	public void treeTableRoute() throws Exception {
		org.junit.jupiter.api.Assumptions.assumeTrue(available, "sqlserver探针环境不可用,跳过");
		try (Statement st = conn.createStatement()) {
			st.execute("drop table if exists sqltoy_probe_tree");
			st.execute("create table sqltoy_probe_tree (id int primary key, pid int, "
					+ "node_route varchar(50), node_level int, is_leaf int)");
			st.execute("insert into sqltoy_probe_tree values (1,null,'001',1,0)");
			st.execute("insert into sqltoy_probe_tree values (2,1,'001',2,1)");
			st.execute("insert into sqltoy_probe_tree values (3,2,'001002',3,1)");
		}
		TreeTableModel model = new TreeTableModel().table("sqltoy_probe_tree").idField("id").pidField("pid")
				.nodeRouteField("node_route").nodeLevelField("node_level").isLeafField("is_leaf")
				.pidValue(1L).idLength(3).idTypeIsChar(false);
		SqlUtil.wrapTreeTableRoute(null, model, conn, profile(), -1);
		// 子树路由重算后:level=2 route=001002;叶子标志:全表置1后主干(有子节点)置0
		try (PreparedStatement pst = conn.prepareStatement(
				"select node_level, node_route, is_leaf from sqltoy_probe_tree where id=?")) {
			pst.setInt(1, 2);
			ResultSet rs = pst.executeQuery();
			rs.next();
			assertEquals(2, rs.getInt(1), "子节点层级");
			assertEquals("001,002", rs.getString(2), "子节点路由(默认splitSign逗号分段)");
			assertEquals(0, rs.getInt(3), "id=2有子节点,叶子标志应为0");
		}
	}

	/** geometry类型:cast(? as geometry)的真实执行(类型包装层产物形态) */
	@Test
	public void geometryCast() throws Exception {
		org.junit.jupiter.api.Assumptions.assumeTrue(available, "sqlserver探针环境不可用,跳过");
		try (PreparedStatement pst = conn.prepareStatement(
				"insert into sqltoy_probe_geom values (1, cast(? as geometry))")) {
			pst.setString(1, "POINT(1 2)");
			pst.executeUpdate();
		}
		String converted = convert("select geo.STAsText() from sqltoy_probe_geom where id=1", "sqlserver");
		String v = querySingle(converted).toString();
		assertTrue(v.contains("POINT"), "geometry应正确存储与读取: " + v);
	}

	private String convert(String sql, String dialect) {
		return FunctionUtils.getDialectSql(sql, dialect);
	}

	// ======================== CTE/union 分页(Msg 319/104回归锁定) ========================

	/**
	 * update 2026-10-12 补齐CTE+union真库用例(2026-9-18修复此前仅人工验证):WITH必须留在
	 * 语句最外层,无顶层order by时union体包派生表挂分页;原实现insert(0)整条包派生表导致
	 * mssql报Msg 319 "Incorrect syntax near the keyword 'with'"
	 */
	@Test
	public void paginationCteUnionNoOrder() throws Exception {
		org.junit.jupiter.api.Assumptions.assumeTrue(available, "sqlserver探针环境不可用,跳过");
		SqlServerDialect dialect = new SqlServerDialect();
		SqlToyConfig config = new SqlToyConfig("sqlserver");
		String sql = "with cte as (select id,name from sqltoy_probe_t1 where id=1) "
				+ "select id,name from cte union select id,name from sqltoy_probe_t1 where id=2";
		config.setSql(sql);
		config.setHasWith(true);
		QueryExecutor queryExecutor = new QueryExecutor(sql);
		QueryResult page = dialect.findPageBySql(context, config, queryExecutor, null, 1L, 2, conn, profile(), 500,
				-1);
		assertEquals(2, ((java.util.List<?>) page.getRows()).size(), "CTE+union无序:应2行(修复前Msg 319)");
	}

	/** CTE+union+顶层order by:排序属于union整体,分页参数直接追加 */
	@Test
	public void paginationCteUnionWithOrder() throws Exception {
		org.junit.jupiter.api.Assumptions.assumeTrue(available, "sqlserver探针环境不可用,跳过");
		SqlServerDialect dialect = new SqlServerDialect();
		SqlToyConfig config = new SqlToyConfig("sqlserver");
		String sql = "with cte as (select id,name from sqltoy_probe_t1) "
				+ "select id,name from cte union select id,name from sqltoy_probe_t1 where id=2 order by id desc";
		config.setSql(sql);
		config.setHasWith(true);
		QueryExecutor queryExecutor = new QueryExecutor(sql);
		QueryResult page = dialect.findPageBySql(context, config, queryExecutor, null, 1L, 2, conn, profile(), 500,
				-1);
		java.util.List<?> rows = (java.util.List<?>) page.getRows();
		assertEquals(2, rows.size(), "CTE+union有序:去重后应2行");
		Object firstId = (rows.get(0) instanceof Object[]) ? ((Object[]) rows.get(0))[0]
				: ((java.util.List<?>) rows.get(0)).get(0);
		assertEquals(2, ((Number) firstId).intValue(), "order by id desc:首行应为id=2");
	}

	/** 裸union无order by:union直接追加order by会报Msg 104,须包派生表(2026-9-5修复回归锁) */
	@Test
	public void paginationBareUnionNoOrder() throws Exception {
		org.junit.jupiter.api.Assumptions.assumeTrue(available, "sqlserver探针环境不可用,跳过");
		SqlServerDialect dialect = new SqlServerDialect();
		SqlToyConfig config = new SqlToyConfig("sqlserver");
		String sql = "select id,name from sqltoy_probe_t1 where id=1 "
				+ "union select id,name from sqltoy_probe_t1 where id=2";
		config.setSql(sql);
		QueryExecutor queryExecutor = new QueryExecutor(sql);
		QueryResult page = dialect.findPageBySql(context, config, queryExecutor, null, 1L, 2, conn, profile(), 500,
				-1);
		assertEquals(2, ((java.util.List<?>) page.getRows()).size(), "裸union:应2行(修复前Msg 104)");
	}

	private Object querySingle(String sql) throws Exception {
		try (PreparedStatement pst = conn.prepareStatement(sql); ResultSet rs = pst.executeQuery()) {
			rs.next();
			return rs.getObject(1);
		}
	}

	/** 最小DBProfile连接档案:仅承载dbType/dialect,满足直调方言方法的用例构造 */
	private static DBProfile profile() {
		return new DBProfile(null, "sqlserver", DataSourceUtils.DBType.SQLSERVER, null, 0, null, null, false);
	}
}
