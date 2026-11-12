package org.sagacity.sqltoy.dialect;

import static org.junit.jupiter.api.Assertions.assertEquals;

import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.Statement;
import java.util.HashMap;
import java.util.Map;

import javax.sql.DataSource;

import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.sagacity.sqltoy.SqlToyContext;
import org.sagacity.sqltoy.config.SqlConfigParseUtils;
import org.sagacity.sqltoy.config.model.SqlToyConfig;
import org.sagacity.sqltoy.config.model.SqlType;
import org.sagacity.sqltoy.model.QueryExecutor;
import org.sagacity.sqltoy.utils.DataSourceUtils;

/**
 * union-all-count优化(UNION_ALL_REGEX修复后首次真正生效)的真实链路场景矩阵:
 * 全部通过 DialectFactory.getCountBySql(context, queryExecutor, config, dataSource) 真实入口执行,
 * 覆盖:多分支、命名参数绑定、with前置、字面量陷阱、大小写、多行格式、group by分支、
 * union(非all)回退、distinct分支契约、尾部分支order by
 * count友好的场景同时与"整体包裹count"的直接结果互为印证
 */
public class UnionAllCountEndToEndTest {

	private static Connection conn;

	private static SqlToyContext context;

	private static DataSource dataSource;

	@BeforeAll
	public static void init() throws Exception {
		conn = DriverManager.getConnection("jdbc:h2:mem:unionallcount;DB_CLOSE_DELAY=-1", "sa", "");
		try (Statement st = conn.createStatement()) {
			st.execute("drop table if exists t1");
			st.execute("drop table if exists t2");
			st.execute("drop table if exists t3");
			st.execute("create table t1 (id int, remark varchar(100), a int)");
			st.execute("insert into t1 values (1,'x',10)");
			st.execute("insert into t1 values (2,'y',20)");
			st.execute("insert into t1 values (3,'z',30)");
			st.execute("create table t2 (id int, b int)");
			st.execute("insert into t2 values (11,110)");
			st.execute("insert into t2 values (12,120)");
			st.execute("create table t3 (id int, v int)");
			st.execute("insert into t3 values (1,1)");
			st.execute("insert into t3 values (2,1)");
			st.execute("insert into t3 values (3,2)");
		}
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
		org.h2.jdbcx.JdbcDataSource h2 = new org.h2.jdbcx.JdbcDataSource();
		h2.setURL("jdbc:h2:mem:unionallcount;DB_CLOSE_DELAY=-1");
		h2.setUser("sa");
		dataSource = h2;
	}

	/**
	 * 通过DialectFactory真实入口执行union-all-count
	 */
	private Long unionAllCount(String sql, Map<String, Object> params) {
		SqlToyConfig config = new SqlToyConfig("h2");
		config.setSql(sql);
		config.setUnionAllCount(true);
		QueryExecutor queryExecutor = (params == null) ? new QueryExecutor(sql) : new QueryExecutor(sql, params);
		return DialectFactory.getInstance().getCountBySql(context, queryExecutor, config, dataSource);
	}

	/**
	 * 整体包裹count的直接结果(count友好场景的基准值)
	 */
	private Long directCount(String sql) throws Exception {
		try (PreparedStatement pst = conn.prepareStatement("select count(1) from (" + sql + ") sag_direct_count");
				ResultSet rs = pst.executeQuery()) {
			rs.next();
			return rs.getLong(1);
		}
	}

	@Test
	public void twoBranch() throws Exception {
		String sql = "select a from t1 union all select b from t2";
		assertEquals(directCount(sql), unionAllCount(sql, null), "双分支union-all-count应与直接包裹一致");
	}

	@Test
	public void threeBranchWithPositionalParams() throws Exception {
		// ?位置参数经getUnifyParamsNamedConfig统一转named后绑定,分支参数按序不串位
		String sql = "select a from t1 where a > ? union all select b from t2 where b >= ? "
				+ "union all select a from t1 where id = 3";
		QueryExecutor queryExecutor = new QueryExecutor(sql, new String[] {}, new Object[] { 10, 110 });
		SqlToyConfig config = new SqlToyConfig("h2");
		config.setSql(sql);
		config.setUnionAllCount(true);
		// 分支1命中2行 + 分支2命中2行 + 分支3命中1行 = 5
		assertEquals(5L, DialectFactory.getInstance()
				.getCountBySql(context, queryExecutor, config, dataSource).longValue(), "多分支+位置参数绑定应正确");
	}

	@Test
	public void withPrefixAndUnionAll() throws Exception {
		String sql = "with w as (select a from t1) select a from w union all select b from t2";
		// 与xml解析同款的构建链路:hasWith标志自动置位,工厂先剥离with再拆分分支
		SqlToyConfig config = SqlConfigParseUtils.parseSqlToyConfig(sql, "h2", SqlType.search);
		config.setUnionAllCount(true);
		QueryExecutor queryExecutor = new QueryExecutor(sql);
		assertEquals(directCount(sql),
				DialectFactory.getInstance().getCountBySql(context, queryExecutor, config, dataSource).longValue(),
				"with前置+union-all-count应与直接包裹一致");
	}

	@Test
	public void literalUnionAllTextInBranch() throws Exception {
		// 字面量内的' union all '不得作为拆分点:branch1过滤0行 + branch2两行 = 2
		// (若字面量被拆开会产出残缺分支,报错或数量错误)
		String sql = "select remark from t1 where remark=' union all ' union all select b from t2";
		assertEquals(2L, unionAllCount(sql, null).longValue(), "字面量内的union all不得参与拆分");
	}

	@Test
	public void literalParenAndFromInBranch() throws Exception {
		String sql = "select concat(a,'(') c1, a from t1 union all select ' from (' c1, b from t2";
		assertEquals(directCount(sql), unionAllCount(sql, null), "分支含'('与' from ('字面量应正确");
	}

	@Test
	public void uppercaseUnionAll() throws Exception {
		String sql = "select a from t1 UNION ALL select b from t2";
		assertEquals(directCount(sql), unionAllCount(sql, null), "大写UNION ALL应被识别(修复前永不触发)");
	}

	@Test
	public void multiLineFormat() throws Exception {
		String sql = "select a from t1\n        union all\n        select b from t2";
		assertEquals(directCount(sql), unionAllCount(sql, null), "多行格式化sql应正确拆分");
	}

	@Test
	public void groupByBranch() throws Exception {
		// group by分支:count(1)按组计数,sum(row_count)=总行数
		String sql = "select a, count(1) cnt from t1 group by a union all select b, count(1) cnt from t2 group by b";
		assertEquals(directCount(sql), unionAllCount(sql, null), "group by分支应正确");
	}

	@Test
	public void unionNotAllFallsBack() throws Exception {
		// union(非all)不满足union-all-count:走常规包裹count,结果仍正确
		String sql = "select a from t1 union select b from t2";
		assertEquals(directCount(sql), unionAllCount(sql, null), "union(非all)应回退常规count且结果正确");
	}

	/**
	 * distinct分支回退为整体包裹计数:该分支按去重后行数计数,其余分支仍走from裁剪的最优count,
	 * 混合场景与直接包裹结果一致
	 */
	@Test
	public void distinctBranchFallsBackToWrappedCount() throws Exception {
		String sql = "select distinct v from t3 union all select v from t3";
		assertEquals(directCount(sql), unionAllCount(sql, null), "distinct分支应按去重后行数计数(5)");
		// 混合场景:distinct分支(2行) + 普通分支(2行) = 4
		String mixed = "select distinct v from t3 union all select b from t2";
		assertEquals(directCount(mixed), unionAllCount(mixed, null), "distinct与普通分支混合应正确");
	}

	@Test
	public void subqueryOrderByInBranchPreserved() throws Exception {
		// 子查询内的order by不属于外层排序,不得被剔除(其后有收括号保护),包裹后合法且计数正确
		String sql = "select a from t1 where a > 10 union all select b from (select b from t2 order by b) x";
		assertEquals(4L, unionAllCount(sql, null).longValue(), "子查询内order by应保留且计数正确");
	}

	@Test
	public void trailingOrderByInLastBranch() throws Exception {
		// 尾部分支携带order by:属于整个union查询,对count无意义且派生表内order by
		// 在H2/标准库不合法,union-all-count必须剔除后执行(分支1命中2行+分支2两行=4)
		String sql = "select a from t1 where a > 10 union all select b from t2 order by b";
		assertEquals(4L, unionAllCount(sql, null).longValue(), "尾部分支order by应被剔除且count正确");
	}
}
