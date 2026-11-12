package org.sagacity.sqltoy.dialect;

import static org.junit.jupiter.api.Assertions.assertEquals;

import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.Statement;
import java.util.List;

import org.h2.jdbcx.JdbcDataSource;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.sagacity.sqltoy.SqlToyContext;
import org.sagacity.sqltoy.config.model.PageOptimize;
import org.sagacity.sqltoy.config.model.SqlToyConfig;
import org.sagacity.sqltoy.integration.impl.SimpleConnectionFactory;
import org.sagacity.sqltoy.model.QueryExecutor;
import org.sagacity.sqltoy.model.QueryResult;

/**
 * F1回归(端到端,H2内存库):并行分页(parallelPage)与串行分页在"页号超出总页数"时必须行为一致。
 * 修复前并行路径仅处理recordCount==0回首页,超界页仍按请求页号取数且保留页号;串行在overPageToFirst=true
 * 时改从第1页取数、=false时结果置空——同一配置下并行/串行分叉(超界页拿到空数据且页号不回退)
 */
public class ParallelPageOverPageH2Test {

	private static final String URL = "jdbc:h2:mem:parallelpage;DB_CLOSE_DELAY=-1";
	private static JdbcDataSource dataSource;
	private static SqlToyContext context;

	@BeforeAll
	public static void init() throws Exception {
		try (Connection conn = DriverManager.getConnection(URL, "sa", ""); Statement st = conn.createStatement()) {
			st.execute("drop table if exists t");
			st.execute("create table t (id int primary key, name varchar(50))");
			for (int i = 1; i <= 25; i++) {
				st.execute("insert into t values (" + i + ",'n" + i + "')");
			}
		}
		dataSource = new JdbcDataSource();
		dataSource.setURL(URL);
		dataSource.setUser("sa");
		context = new SqlToyContext();
		// 非spring场景的连接获取(测试内无applicationContext,需显式设置)
		context.setConnectionFactory(new SimpleConnectionFactory());
	}

	private static QueryResult page(String sql, long pageNo, int pageSize, boolean parallel, Boolean overPageToFirst)
			throws Exception {
		SqlToyConfig config = new SqlToyConfig("p_" + Math.abs(sql.hashCode()), sql);
		if (parallel) {
			// aliveSeconds(0)关闭count缓存,否则第二次调用命中缓存会绕过parallelPage
			config.setPageOptimize(new PageOptimize().parallel(true).aliveSeconds(0));
		}
		return DialectFactory.getInstance().findPage(context, new QueryExecutor(sql), config, pageNo, pageSize,
				overPageToFirst, dataSource);
	}

	private static int rowCount(QueryResult result) {
		List rows = result.getRows();
		return (rows == null) ? 0 : rows.size();
	}

	@Test
	public void overPageToFirstTrueFallsBackToFirstPage() throws Exception {
		String sql = "select * from t order by id";
		// 25条/每页10条=3页,pageNo=4超界:两条路径都应回第1页取数
		QueryResult serial = page(sql, 4, 10, false, Boolean.TRUE);
		QueryResult parallel = page(sql, 4, 10, true, Boolean.TRUE);
		assertEquals(1L, serial.getPageNo());
		assertEquals(10, rowCount(serial));
		// 修复前:parallel.pageNo=4且rows为空
		assertEquals(serial.getPageNo(), parallel.getPageNo());
		assertEquals(rowCount(serial), rowCount(parallel));
		assertEquals(serial.getRecordCount(), parallel.getRecordCount());
	}

	@Test
	public void overPageToFirstFalseKeepsEmptyPage() throws Exception {
		String sql = "select * from t order by id";
		QueryResult serial = page(sql, 4, 10, false, Boolean.FALSE);
		QueryResult parallel = page(sql, 4, 10, true, Boolean.FALSE);
		assertEquals(4L, serial.getPageNo());
		assertEquals(0, rowCount(serial));
		assertEquals(serial.getPageNo(), parallel.getPageNo());
		assertEquals(rowCount(serial), rowCount(parallel));
		assertEquals(serial.getRecordCount(), parallel.getRecordCount());
	}

	@Test
	public void lastPageIsNotTreatedAsOverPage() throws Exception {
		String sql = "select * from t order by id";
		// 恰好最后一页:不得回退,返回剩余5条
		QueryResult serial = page(sql, 3, 10, false, Boolean.TRUE);
		QueryResult parallel = page(sql, 3, 10, true, Boolean.TRUE);
		assertEquals(3L, serial.getPageNo());
		assertEquals(5, rowCount(serial));
		assertEquals(serial.getPageNo(), parallel.getPageNo());
		assertEquals(rowCount(serial), rowCount(parallel));
	}

	@Test
	public void firstPageRequestIsUnaffected() throws Exception {
		// pageNo=1时 isPageOverTotal 化简为 0>=recordCount,有数据必然false:不进入超界处理
		String sql = "select * from t order by id";
		QueryResult serial = page(sql, 1, 10, false, Boolean.TRUE);
		QueryResult parallel = page(sql, 1, 10, true, Boolean.TRUE);
		assertEquals(1L, parallel.getPageNo());
		assertEquals(10, rowCount(parallel));
		assertEquals(serial.getPageNo(), parallel.getPageNo());
		assertEquals(rowCount(serial), rowCount(parallel));
		assertEquals(25L, parallel.getRecordCount());

		// pageNo=1且无数据:超界成立但只走"回退页号"分支,setPageNo(1)幂等,不得触发第1页回退取数
		String emptySql = "select * from t where id > 1000 order by id";
		QueryResult emptySerial = page(emptySql, 1, 10, false, Boolean.TRUE);
		QueryResult emptyParallel = page(emptySql, 1, 10, true, Boolean.TRUE);
		assertEquals(1L, emptyParallel.getPageNo());
		assertEquals(0, rowCount(emptyParallel));
		assertEquals(emptySerial.getPageNo(), emptyParallel.getPageNo());
		assertEquals(0L, emptyParallel.getRecordCount());

		// overPageToFirst=false时同样保持请求页1
		QueryResult emptyKeep = page(emptySql, 1, 10, true, Boolean.FALSE);
		assertEquals(1L, emptyKeep.getPageNo());
		assertEquals(0, rowCount(emptyKeep));
	}

	@Test
	public void zeroRecordOverPageOnlyFallsBackPageNo() throws Exception {
		String sql = "select * from t where id > 1000 order by id";
		QueryResult serial = page(sql, 2, 10, false, Boolean.TRUE);
		QueryResult parallel = page(sql, 2, 10, true, Boolean.TRUE);
		assertEquals(1L, serial.getPageNo());
		assertEquals(serial.getPageNo(), parallel.getPageNo());
		assertEquals(0, rowCount(parallel));
		assertEquals(0L, parallel.getRecordCount());
	}
}
