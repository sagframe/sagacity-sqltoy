package org.sagacity.sqltoy.dialect;

import static org.junit.jupiter.api.Assertions.assertEquals;

import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.Statement;

import org.h2.jdbcx.JdbcDataSource;
import org.junit.jupiter.api.Test;
import org.sagacity.sqltoy.SqlToyContext;
import org.sagacity.sqltoy.config.model.PageOptimize;
import org.sagacity.sqltoy.config.model.SqlToyConfig;
import org.sagacity.sqltoy.integration.impl.SimpleConnectionFactory;
import org.sagacity.sqltoy.model.QueryExecutor;
import org.sagacity.sqltoy.model.QueryResult;

/**
 * 真实分页链路验证:同一sqlId和查询条件命中count缓存时,不同实际数据源仍各自取count
 */
public class PageOptimizeNamespaceH2Test {

	@Test
	public void cachedCountIsIsolatedByActualDataSource() throws Exception {
		String sqlId = "page_optimize_namespace_h2_test";
		JdbcDataSource dataSource1 = createDataSource("pageoptimize1", 3);
		JdbcDataSource dataSource2 = createDataSource("pageoptimize2", 8);
		SqlToyContext context = new SqlToyContext();
		context.setConnectionFactory(new SimpleConnectionFactory());
		SqlToyConfig config = new SqlToyConfig(sqlId, "select * from t order by id");
		config.setPageOptimize(new PageOptimize().aliveSeconds(60).aliveMax(10));
		PageOptimizeUtils.remove(sqlId);
		try {
			assertEquals(3L, findPage(context, config, dataSource1).getRecordCount());
			assertEquals(3L, findPage(context, config, dataSource1).getRecordCount(), "第二次查询应命中本库缓存");

			QueryResult otherDataSource = findPage(context, config, dataSource2);
			assertEquals(8L, otherDataSource.getRecordCount(), "第二个库不能复用第一个库的count");
			assertEquals(8, otherDataSource.getRows().size());
			assertEquals(3L, findPage(context, config, dataSource1).getRecordCount());
		} finally {
			PageOptimizeUtils.remove(sqlId);
		}
	}

	private static QueryResult findPage(SqlToyContext context, SqlToyConfig config, JdbcDataSource dataSource)
			throws Exception {
		return DialectFactory.getInstance().findPage(context, new QueryExecutor(config.getSql()), config, 1, 10,
				Boolean.FALSE, dataSource);
	}

	private static JdbcDataSource createDataSource(String database, int rowCount) throws Exception {
		String url = "jdbc:h2:mem:" + database + ";DB_CLOSE_DELAY=-1";
		try (Connection conn = DriverManager.getConnection(url, "sa", ""); Statement st = conn.createStatement()) {
			st.execute("create table t (id int primary key)");
			for (int i = 1; i <= rowCount; i++) {
				st.execute("insert into t values (" + i + ")");
			}
		}
		JdbcDataSource dataSource = new JdbcDataSource();
		dataSource.setURL(url);
		dataSource.setUser("sa");
		return dataSource;
	}
}
