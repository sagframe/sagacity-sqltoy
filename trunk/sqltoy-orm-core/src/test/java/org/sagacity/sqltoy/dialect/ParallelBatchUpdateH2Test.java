package org.sagacity.sqltoy.dialect;

import static org.junit.jupiter.api.Assertions.assertEquals;

import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.ResultSet;
import java.sql.Statement;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import org.h2.jdbcx.JdbcDataSource;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.sagacity.sqltoy.SqlToyContext;
import org.sagacity.sqltoy.config.model.SqlToyConfig;
import org.sagacity.sqltoy.integration.impl.SimpleConnectionFactory;
import org.sagacity.sqltoy.model.ParallelConfig;

/**
 * 并行batchUpdate回归:每个分组只能执行自身数据,不能重复执行原始全集
 */
public class ParallelBatchUpdateH2Test {

	private static final int ROW_COUNT = 500;
	private static final String URL = "jdbc:h2:mem:parallelbatch;DB_CLOSE_DELAY=-1";
	private static JdbcDataSource dataSource;
	private static SqlToyContext context;

	@BeforeAll
	public static void init() {
		dataSource = new JdbcDataSource();
		dataSource.setURL(URL);
		dataSource.setUser("sa");
		context = new SqlToyContext();
		context.setConnectionFactory(new SimpleConnectionFactory());
	}

	@Test
	public void positionalParamsExecuteEachGroupOnlyOnce() throws Exception {
		String table = "t_positional";
		resetTable(table);
		List<Object[]> dataSet = new ArrayList<Object[]>();
		for (int i = 1; i <= ROW_COUNT; i++) {
			dataSet.add(new Object[] { "positional-" + i, i });
		}
		Long updateCount = batchUpdate(table, "update " + table + " set name=? where id=?", dataSet, false);
		assertEquals(ROW_COUNT, updateCount.longValue());
		assertRows(table, "positional-");
	}

	@Test
	public void namedParamsExecuteEachGroupOnlyOnce() throws Exception {
		String table = "t_named";
		resetTable(table);
		List<Map<String, Object>> dataSet = new ArrayList<Map<String, Object>>();
		for (int i = 1; i <= ROW_COUNT; i++) {
			Map<String, Object> row = new HashMap<String, Object>();
			row.put("name", "named-" + i);
			row.put("id", i);
			dataSet.add(row);
		}
		Long updateCount = batchUpdate(table, "update " + table + " set name=:name where id=:id", dataSet, true);
		assertEquals(ROW_COUNT, updateCount.longValue());
		assertRows(table, "named-");
	}

	private static Long batchUpdate(String table, String sql, List dataSet, boolean named) throws Exception {
		SqlToyConfig config = new SqlToyConfig("parallel_batch_" + table, sql);
		if (named) {
			config.setParamsName(new String[] { "name", "id" });
		}
		// groupSize下限为201,500条记录配合2个线程形成两个250条分组
		ParallelConfig parallelConfig = ParallelConfig.create().maxThreads(2).groupSize(201);
		return DialectFactory.getInstance().batchUpdate(context, config, dataSet, 100, null, null, parallelConfig,
				Boolean.TRUE, dataSource);
	}

	private static void resetTable(String table) throws Exception {
		try (Connection conn = DriverManager.getConnection(URL, "sa", ""); Statement st = conn.createStatement()) {
			st.execute("drop table if exists " + table);
			st.execute("create table " + table + " (id int primary key, name varchar(50))");
			for (int i = 1; i <= ROW_COUNT; i++) {
				st.execute("insert into " + table + " values (" + i + ",'old-" + i + "')");
			}
		}
	}

	private static void assertRows(String table, String namePrefix) throws Exception {
		try (Connection conn = DriverManager.getConnection(URL, "sa", "");
				Statement st = conn.createStatement();
				ResultSet rs = st.executeQuery("select id,name from " + table + " order by id")) {
			int rowCount = 0;
			while (rs.next()) {
				rowCount++;
				assertEquals(rowCount, rs.getInt("id"));
				assertEquals(namePrefix + rowCount, rs.getString("name"));
			}
			assertEquals(ROW_COUNT, rowCount);
		}
	}
}
