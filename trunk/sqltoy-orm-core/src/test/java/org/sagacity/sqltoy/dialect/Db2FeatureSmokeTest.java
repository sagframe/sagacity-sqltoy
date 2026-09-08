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
import org.sagacity.sqltoy.dialect.impl.DB2Dialect;
import org.sagacity.sqltoy.model.QueryExecutor;
import org.sagacity.sqltoy.model.QueryResult;
import org.sagacity.sqltoy.model.TreeTableModel;
import org.sagacity.sqltoy.plugins.function.FunctionUtils;
import org.sagacity.sqltoy.utils.DataSourceUtils;
import org.sagacity.sqltoy.utils.SqlUtil;

/**
 * DB2真实库方言特性冒烟:分页(offset fetch first)、top(fetch first)、随机(rand)、
 * 树表路由(wrapTreeTableRoute)。listagg/CURRENT TIMESTAMP已在Db2RealDbSmokeTest验证。
 * 连接配置从target/db2-probe.properties读取;探针表测完即删。
 */
public class Db2FeatureSmokeTest {

	private static Connection conn;

	private static SqlToyContext context;

	private static boolean available = false;

	@BeforeAll
	public static void init() throws Exception {
		File cfg = new File("target/db2-probe.properties");
		if (!cfg.exists()) {
			return;
		}
		Properties p = new Properties();
		try (InputStream in = new FileInputStream(cfg)) {
			p.load(in);
		}
		Class.forName("com.ibm.db2.jcc.DB2Driver");
		conn = DriverManager.getConnection(p.getProperty("url"), p.getProperty("username"), p.getProperty("password"));
		try (Statement st = conn.createStatement()) {
			tryDrop(st, "drop table sqltoy_probe_t1");
			tryDrop(st, "drop table sqltoy_probe_tree");
			st.execute("create table sqltoy_probe_t1 (id integer primary key not null, name varchar(100), "
					+ "score decimal(10,2))");
			st.execute("insert into sqltoy_probe_t1 values (1,'admin',88.5)");
			st.execute("insert into sqltoy_probe_t1 values (2,'user',72.0)");
			st.execute("create table sqltoy_probe_tree (id integer primary key not null, pid integer, "
					+ "node_route varchar(50), node_level integer, is_leaf integer)");
			st.execute("insert into sqltoy_probe_tree values (1,null,'001',1,0)");
			st.execute("insert into sqltoy_probe_tree values (2,1,'001',2,1)");
			st.execute("insert into sqltoy_probe_tree values (3,2,'001002',3,1)");
		}
		FunctionUtils.setFunctionConverts(java.util.Arrays.asList("default",
				"org.sagacity.sqltoy.plugins.function.impl.DateDiff"));
		context = new SqlToyContext();
		available = true;
	}

	private static void tryDrop(Statement st, String ddl) {
		try {
			st.execute(ddl);
		} catch (Exception e) {
			// db2的DROP TABLE不支持IF EXISTS,表不存在时忽略
		}
	}

	@AfterAll
	public static void destroy() throws Exception {
		if (conn != null) {
			try (Statement st = conn.createStatement()) {
				tryDrop(st, "drop table sqltoy_probe_t1");
				tryDrop(st, "drop table sqltoy_probe_tree");
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
		try (PreparedStatement pst = conn.prepareStatement(sql); ResultSet rs = pst.executeQuery()) {
			rs.next();
			return rs.getObject(1);
		}
	}

	@Test
	public void db2Available() {
		org.junit.jupiter.api.Assumptions.assumeTrue(available, "db2探针环境不可用,跳过");
	}

	/** 分页:db2的offset fetch first语法的真实执行与页数据正确性 */
	@Test
	public void pagination() throws Exception {
		org.junit.jupiter.api.Assumptions.assumeTrue(available, "db2探针环境不可用,跳过");
		DB2Dialect dialect = new DB2Dialect();
		SqlToyConfig config = new SqlToyConfig("db2");
		String sql = "select id,name from sqltoy_probe_t1 where id > 0 order by id";
		config.setSql(sql);
		QueryExecutor queryExecutor = new QueryExecutor(sql);
		QueryResult page1 = dialect.findPageBySql(context, config, queryExecutor, null, 1L, 1, conn,
				DataSourceUtils.DBType.DB2, "db2", 500, -1);
		assertEquals(1, ((java.util.List<?>) page1.getRows()).size(), "第一页应1行");
		QueryResult page2 = dialect.findPageBySql(context, config, queryExecutor, null, 2L, 1, conn,
				DataSourceUtils.DBType.DB2, "db2", 500, -1);
		assertEquals(1, ((java.util.List<?>) page2.getRows()).size(), "第二页应1行");
	}

	/** top查询:fetch first rows only */
	@Test
	public void topQuery() throws Exception {
		org.junit.jupiter.api.Assumptions.assumeTrue(available, "db2探针环境不可用,跳过");
		DB2Dialect dialect = new DB2Dialect();
		SqlToyConfig config = new SqlToyConfig("db2");
		String sql = "select name from sqltoy_probe_t1 where score > 80 order by id";
		config.setSql(sql);
		QueryExecutor queryExecutor = new QueryExecutor(sql);
		QueryResult top = dialect.findTopBySql(context, config, queryExecutor, null, 1, conn,
				DataSourceUtils.DBType.DB2, "db2", 500, -1);
		assertEquals(1, ((java.util.List<?>) top.getRows()).size(), "top 1应只返回1行");
	}

	/** 随机记录:rand()排序 */
	@Test
	public void randomQuery() throws Exception {
		org.junit.jupiter.api.Assumptions.assumeTrue(available, "db2探针环境不可用,跳过");
		DB2Dialect dialect = new DB2Dialect();
		SqlToyConfig config = new SqlToyConfig("db2");
		String sql = "select name from sqltoy_probe_t1 where score > 0";
		config.setSql(sql);
		QueryExecutor queryExecutor = new QueryExecutor(sql);
		QueryResult random = dialect.getRandomResult(context, config, queryExecutor, null, 2L, 1L, conn,
				DataSourceUtils.DBType.DB2, "db2", 500, -1);
		assertEquals(1, ((java.util.List<?>) random.getRows()).size(), "随机应返回1行");
	}

	/** 树表路由:递归更新层级/路径/叶子标志在真实db2上执行 */
	@Test
	public void treeTableRoute() throws Exception {
		org.junit.jupiter.api.Assumptions.assumeTrue(available, "db2探针环境不可用,跳过");
		TreeTableModel model = new TreeTableModel().table("sqltoy_probe_tree").idField("id").pidField("pid")
				.nodeRouteField("node_route").nodeLevelField("node_level").isLeafField("is_leaf")
				.pidValue(1L).idLength(3).idTypeIsChar(false);
		SqlUtil.wrapTreeTableRoute(null, model, conn, DataSourceUtils.DBType.DB2, -1);
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
}
