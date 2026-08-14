package org.sagacity.sqltoy.utils;

import static org.junit.jupiter.api.Assertions.assertEquals;

import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

/**
 * 回归测试：批量更新集合尾部为null的行时，已addBatch的语句仍必须被执行，防止数据静默丢失
 * 
 * @see SqlUtil#batchUpdateByJdbc
 * @see SqlUtilsExt#batchUpdateForPOJO
 */
public class SqlUtilBatchUpdateNullTailTest {
	private static Connection conn;

	private final static String INSERT_SQL = "insert into SQLTOY_BATCH_TEST values(?,?)";

	@BeforeAll
	public static void setUp() throws Exception {
		conn = DriverManager.getConnection("jdbc:h2:mem:batchNullTail;DB_CLOSE_DELAY=-1", "sa", "");
		try (Statement st = conn.createStatement()) {
			st.execute("CREATE TABLE SQLTOY_BATCH_TEST(ID INT PRIMARY KEY, NAME VARCHAR(50))");
		}
	}

	@AfterAll
	public static void tearDown() throws Exception {
		if (conn != null) {
			conn.close();
		}
	}

	private static int countRows() throws SQLException {
		try (Statement st = conn.createStatement();
				ResultSet rs = st.executeQuery("select count(*) from SQLTOY_BATCH_TEST")) {
			rs.next();
			return rs.getInt(1);
		}
	}

	private static void cleanTable() throws SQLException {
		try (Statement st = conn.createStatement()) {
			st.execute("delete from SQLTOY_BATCH_TEST");
		}
	}

	private static List<Object[]> rows(Object[]... dataArray) {
		return new ArrayList<Object[]>(Arrays.asList(dataArray));
	}

	@Test
	public void batchUpdateByJdbcTailNull() throws Exception {
		cleanTable();
		Long cnt = SqlUtil.batchUpdateByJdbc(null, INSERT_SQL,
				rows(new Object[] { 1, "a" }, new Object[] { 2, "b" }, null), 100, null, null, null, conn, null);
		assertEquals(2, countRows());
		assertEquals(2L, cnt.longValue());
	}

	@Test
	public void batchUpdateByJdbcBatchAlignedThenNull() throws Exception {
		// 前2条触发一次整批次执行，第3条为尾部残量，末尾null行之前会跳过残量执行
		cleanTable();
		Long cnt = SqlUtil.batchUpdateByJdbc(null, INSERT_SQL,
				rows(new Object[] { 1, "a" }, new Object[] { 2, "b" }, new Object[] { 3, "c" }, null), 2, null, null,
				null, conn, null);
		assertEquals(3, countRows());
		assertEquals(3L, cnt.longValue());
	}

	@Test
	public void batchUpdateByJdbcAllNotNull() throws Exception {
		// 原有行为不回归：无null行、批次量整除时由循环内触发，不整除时由循环外补齐
		cleanTable();
		Long cnt = SqlUtil.batchUpdateByJdbc(null, INSERT_SQL,
				rows(new Object[] { 1, "a" }, new Object[] { 2, "b" }, new Object[] { 3, "c" }), 2, null, null, null,
				conn, null);
		assertEquals(3, countRows());
		assertEquals(3L, cnt.longValue());
	}

	@Test
	public void batchUpdateForPOJOTailNull() throws Exception {
		cleanTable();
		Long cnt = SqlUtilsExt.batchUpdateForPOJO(null, INSERT_SQL,
				rows(new Object[] { 1, "a" }, new Object[] { 2, "b" }, null), null, null, null, 100, null, conn, null);
		assertEquals(2, countRows());
		assertEquals(2L, cnt.longValue());
	}

	@Test
	public void batchUpdateForPOJOAllNull() throws Exception {
		cleanTable();
		Long cnt = SqlUtilsExt.batchUpdateForPOJO(null, INSERT_SQL, rows(null, null), null, null, null, 100, null, conn,
				null);
		assertEquals(0, countRows());
		assertEquals(0L, cnt.longValue());
	}
}
