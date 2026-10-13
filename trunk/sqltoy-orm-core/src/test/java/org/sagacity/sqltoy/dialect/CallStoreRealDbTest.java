package org.sagacity.sqltoy.dialect;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.Statement;
import java.util.List;

import javax.sql.DataSource;

import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.Assumptions;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.sagacity.sqltoy.SqlToyContext;
import org.sagacity.sqltoy.config.model.SqlToyConfig;
import org.sagacity.sqltoy.model.DBProfile;
import org.sagacity.sqltoy.model.StoreResult;
import org.sagacity.sqltoy.plugins.function.FunctionUtils;
import org.sagacity.sqltoy.utils.DataSourceUtils.DBType;

/**
 * 瀚高存储过程经sqltoy框架全链路验证(lightDao级executeStore):
 * 1)返回结果集函数 2)输出参数函数(PG plpgsql函数族,{call}/{? = call}全通)
 */
public class CallStoreRealDbTest {

	private static SqlToyContext ctx;
	private static boolean hgOk = false;
	private static Connection hgConn;

	@BeforeAll
	public static void init() throws Exception {
		ctx = new SqlToyContext();
		ctx.setConnectionFactory(new org.sagacity.sqltoy.integration.ConnectionFactory() {
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
					if (c != null)
						c.close();
				} catch (Exception e) {
					// ignore
				}
			}
		});
		FunctionUtils.setFunctionConverts(java.util.Arrays.asList("default"));
		try {
			Class.forName("org.postgresql.Driver");
			hgConn = DriverManager.getConnection("jdbc:postgresql://localhost:5867/highgo", "highgo", "Sqltoy@2026");
			try (Statement st = hgConn.createStatement()) {
				execQuiet(st, "drop table if exists nd_cs_t");
				execQuiet(st, "drop function if exists nd_fn_query(int)");
				execQuiet(st, "drop function if exists nd_fn_scalar(int)");
				st.execute("create table nd_cs_t (id int primary key, nm varchar(50))");
				st.execute("insert into nd_cs_t values (1,'row1'),(2,'row2'),(3,'row3')");
				st.execute("create function nd_fn_query(p_limit int) returns table(r_id int, r_nm text) as $$ "
						+ "select id, nm from nd_cs_t where id <= p_limit $$ language sql");
				st.execute("create function nd_fn_scalar(x int) returns int as 'select x*2' language sql");
			}
			hgOk = true;
		} catch (Exception e) {
			System.out.println("[CS] highgo不可用: " + e.getMessage());
		}
	}

	@AfterAll
	public static void destroy() throws Exception {
		if (hgConn != null) {
			try (Statement st = hgConn.createStatement()) {
				execQuiet(st, "drop function if exists nd_fn_query(int)");
				execQuiet(st, "drop function if exists nd_fn_scalar(int)");
				execQuiet(st, "drop table if exists nd_cs_t");
			}
			hgConn.close();
		}
	}

	private static void execQuiet(Statement st, String sql) {
		try {
			st.execute(sql);
		} catch (Exception e) {
			// ignore
		}
	}

	private static DBProfile hgProfile() {
		return new DBProfile(null, "highgo", DBType.HIGHGO, null, 12, null, null, false);
	}

	/** highgo:结果集函数经executeStore({call func(?)}) */
	@Test
	public void highgoResultSetFunction() throws Exception {
		Assumptions.assumeTrue(hgOk, "highgo不可用");
		SqlToyConfig cfg = new SqlToyConfig("highgo");
		StoreResult result = org.sagacity.sqltoy.dialect.utils.DialectUtils.executeStore(cfg, ctx,
				"{call nd_fn_query(?)}", new Object[] { 2 }, null, false, hgConn, hgProfile(), 500, null);
		List<?> rows = result.getRows();
		assertNotNull(rows, "结果集非null");
		assertTrue(rows.size() >= 1, "结果集至少1行: " + rows.size());
		System.out.println("[CS-HG] 结果集函数=" + rows.size() + "行, labels="
				+ java.util.Arrays.toString(result.getLabelNames()));
	}

	/** highgo:标量函数经executeStore({? = call func(?)})带输出参数 */
	@Test
	public void highgoScalarFunction() throws Exception {
		Assumptions.assumeTrue(hgOk, "highgo不可用");
		SqlToyConfig cfg = new SqlToyConfig("highgo");
		StoreResult result = org.sagacity.sqltoy.dialect.utils.DialectUtils.executeStore(cfg, ctx,
				"{? = call nd_fn_scalar(?)}", new Object[] { 21 }, new Integer[] { java.sql.Types.INTEGER }, false,
				hgConn, hgProfile(), 500, null);
		Object[] out = result.getOutResult();
		assertNotNull(out, "输出参数非null");
		assertEquals(42, ((Number) out[0]).intValue(), "标量函数双倍");
	}
}
