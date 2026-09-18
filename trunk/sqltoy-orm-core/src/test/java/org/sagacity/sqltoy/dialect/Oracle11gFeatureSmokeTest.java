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
import java.util.HashSet;
import java.util.Properties;
import java.util.Set;

import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.sagacity.sqltoy.SqlToyContext;
import org.sagacity.sqltoy.config.model.SqlToyConfig;
import org.sagacity.sqltoy.dialect.impl.Oracle11gDialect;
import org.sagacity.sqltoy.model.DBProfile;
import org.sagacity.sqltoy.model.QueryExecutor;
import org.sagacity.sqltoy.model.QueryResult;
import org.sagacity.sqltoy.utils.DataSourceUtils;
import org.sagacity.sqltoy.utils.DataSourceUtils.DBType;

/**
 * Oracle11g方言(ROWNUM分页/dbms_random随机/rownum top)真实库冒烟——
 * 与SqlServerFeatureSmokeTest同型的特性矩阵:
 * <li>分页:rownum派生层page_row_id伪列必须被跳过(结果集从第2列开始,业务列首列),
 * 探针id特意取非连续值(11/22/33/44/55)使page_row_id(1,2,3...)不可能与业务id同值,
 * 列偏移回归将在此显式暴露;有序/无序/union三种SQL形态翻页无重叠无遗漏</li>
 * <li>top:rownum&lt;=N的截取与超量请求行为</li>
 * <li>随机:dbms_random.random排序+rownum截取,返回行数与行集归属</li>
 * 连接配置从target/oracle-probe.properties读取(不入库);探针表测完即删。
 * 11g XE默认已安装dbms_random包。
 */
public class Oracle11gFeatureSmokeTest {

	private static Connection conn;

	private static SqlToyContext context;

	private static boolean available = false;

	@BeforeAll
	public static void init() throws Exception {
		File cfg = new File("target/oracle-probe.properties");
		if (!cfg.exists()) {
			return;
		}
		Properties p = new Properties();
		try (InputStream in = new FileInputStream(cfg)) {
			p.load(in);
		}
		Class.forName("oracle.jdbc.driver.OracleDriver");
		conn = DriverManager.getConnection(p.getProperty("url"), p.getProperty("username"), p.getProperty("password"));
		try (Statement st = conn.createStatement()) {
			// 11g不支持drop table if exists,首跑表不存在时忽略
			try {
				st.execute("drop table sqltoy_probe_11g purge");
			} catch (Exception e) {
				// 首次运行表不存在,忽略
			}
			// id特意取非连续值:若分页派生层的page_row_id伪列未被跳过,首列值(1,2,3...)与
			// 业务id(11,22,...)必然不同,列偏移回归将显式暴露而非同值巧合掩盖
			st.execute("create table sqltoy_probe_11g (id int primary key, name varchar2(100), score number(10,2))");
			st.execute("insert into sqltoy_probe_11g values (11,'r1',10)");
			st.execute("insert into sqltoy_probe_11g values (22,'r2',20)");
			st.execute("insert into sqltoy_probe_11g values (33,'r3',30)");
			st.execute("insert into sqltoy_probe_11g values (44,'r4',40)");
			st.execute("insert into sqltoy_probe_11g values (55,'r5',50)");
		}
		context = new SqlToyContext();
		available = true;
	}

	@AfterAll
	public static void destroy() throws Exception {
		if (conn != null) {
			try (Statement st = conn.createStatement()) {
				st.execute("drop table sqltoy_probe_11g purge");
			} catch (Exception e) {
				// ignore
			}
			conn.close();
		}
	}

	private QueryResult runPage(String sql, long pageNo, int pageSize) throws Exception {
		SqlToyConfig config = new SqlToyConfig("oracle");
		config.setSql(sql);
		return new Oracle11gDialect().findPageBySql(context, config, new QueryExecutor(sql), null, pageNo, pageSize,
				conn, profile(), 500, -1);
	}

	private QueryResult runTop(String sql, int topSize) throws Exception {
		SqlToyConfig config = new SqlToyConfig("oracle");
		config.setSql(sql);
		return new Oracle11gDialect().findTopBySql(context, config, new QueryExecutor(sql), null, topSize, conn,
				profile(), 500, -1);
	}

	private QueryResult runRandom(String sql, long totalCount, long randomCount) throws Exception {
		SqlToyConfig config = new SqlToyConfig("oracle");
		config.setSql(sql);
		return new Oracle11gDialect().getRandomResult(context, config, new QueryExecutor(sql), null, totalCount,
				randomCount, conn, profile(), 500, -1);
	}

	/** 非vo查询默认行形态为List<List>(每行为列值集合);取首列(业务id) */
	private Set<Long> extractIds(QueryResult result) {
		Set<Long> ids = new HashSet<>();
		for (Object row : (java.util.List<?>) result.getRows()) {
			ids.add(Long.valueOf(String.valueOf(rowValue(row, "id"))));
		}
		return ids;
	}

	/** 取行内指定列(非vo行为List按下标,兼容数组行与Map行形态) */
	private Object rowValue(Object row, String key) {
		if (row instanceof java.util.List) {
			if ("id".equalsIgnoreCase(key)) {
				return ((java.util.List<?>) row).get(0);
			}
			return ((java.util.List<?>) row).get(1);
		}
		if (row instanceof Object[]) {
			return ((Object[]) row)["id".equalsIgnoreCase(key) ? 0 : 1];
		}
		if (row instanceof java.util.Map) {
			for (java.util.Map.Entry<?, ?> e : ((java.util.Map<?, ?>) row).entrySet()) {
				if (String.valueOf(e.getKey()).equalsIgnoreCase(key)) {
					return e.getValue();
				}
			}
		}
		return null;
	}

	private int rowCount(QueryResult result) {
		return ((java.util.List<?>) result.getRows()).size();
	}

	/**
	 * 列偏移专项(update 2026-9-17):rownum派生层暴露的page_row_id伪列必须被columnSkip跳过,
	 * 结果集从第2列(业务id)开始——每行应恰好2列,且首列值为非连续业务id(11/22...),
	 * 若伪列泄漏为首列则值必为1,2与业务id不同,本用例显式失败
	 */
	@Test
	public void paginationColumnsStartFromSecondColumn() throws Exception {
		org.junit.jupiter.api.Assumptions.assumeTrue(available, "oracle探针环境不可用,跳过");
		String sql = "select id,name from sqltoy_probe_11g order by id";
		QueryResult page1 = runPage(sql, 1L, 2);
		java.util.List<?> rows = (java.util.List<?>) page1.getRows();
		assertEquals(2, rows.size(), "第一页2行");
		Set<Long> ids = new HashSet<>();
		for (Object row : rows) {
			// 行宽断言:page_row_id伪列跳过后,行内应恰好为业务列(id,name)2列
			assertEquals(2, ((java.util.List<?>) row).size(), "行应恰好2列(伪列已跳过): " + row);
			Object id = rowValue(row, "id");
			Object name = rowValue(row, "name");
			// 首列即业务id而非rownum序号(1,2):非连续id使同值巧合不可能
			assertTrue(java.util.Arrays.asList(11L, 22L).contains(Long.valueOf(String.valueOf(id))),
					"首列应为业务id(11/22)而非page_row_id伪列(1/2),实为:" + id);
			// 第2列即业务name,与id对应
			assertEquals("r" + Long.valueOf(String.valueOf(id)) / 11, String.valueOf(name),
					"id与name应按业务数据对应: " + row);
			ids.add(Long.valueOf(String.valueOf(id)));
		}
		assertEquals(new HashSet<>(java.util.Arrays.asList(11L, 22L)), ids, "第一页应为id 11,22");
	}

	/**
	 * 分页:带order by的有序分页(走page_row_id<=?/>?分支),逐页无重叠无遗漏,末页之后空页
	 */
	@Test
	public void paginationOrdered() throws Exception {
		org.junit.jupiter.api.Assumptions.assumeTrue(available, "oracle探针环境不可用,跳过");
		String sql = "select id,name from sqltoy_probe_11g order by id";
		Set<Long> page1 = extractIds(runPage(sql, 1L, 2));
		Set<Long> page2 = extractIds(runPage(sql, 2L, 2));
		Set<Long> page3 = extractIds(runPage(sql, 3L, 2));
		assertEquals(2, page1.size(), "第一页2行");
		assertEquals(2, page2.size(), "第二页2行");
		assertEquals(1, page3.size(), "第三页1行");
		assertTrue(page1.contains(11L) && page1.contains(22L), "第一页应为id 11,22: " + page1);
		assertTrue(page2.contains(33L) && page2.contains(44L), "第二页应为id 33,44: " + page2);
		assertTrue(page3.contains(55L), "第三页应为id 55: " + page3);
		assertEquals(0, extractIds(runPage(sql, 4L, 2)).size(), "越界页应为空");
		// 无重叠无遗漏:三页并集恰为全量5行
		Set<Long> all = new HashSet<>(page1);
		all.addAll(page2);
		all.addAll(page3);
		assertEquals(new HashSet<>(java.util.Arrays.asList(11L, 22L, 33L, 44L, 55L)), all, "三页并集应覆盖全量");
	}

	/**
	 * 分页:无order by(走内层ROWNUM<=?截断分支)页大小正确,行为可执行
	 */
	@Test
	public void paginationUnordered() throws Exception {
		org.junit.jupiter.api.Assumptions.assumeTrue(available, "oracle探针环境不可用,跳过");
		assertEquals(2, rowCount(runPage("select id,name from sqltoy_probe_11g where score > 0", 1L, 2)),
				"无序分页第一页2行");
		assertEquals(2, rowCount(runPage("select id,name from sqltoy_probe_11g where score > 0", 2L, 2)),
				"无序分页第二页2行");
		assertEquals(1, rowCount(runPage("select id,name from sqltoy_probe_11g where score > 0", 3L, 2)),
				"无序分页(共5行)第三页1行");
		assertEquals(0, rowCount(runPage("select id,name from sqltoy_probe_11g where score > 0", 4L, 2)),
				"无序分页越界页应为空");
	}

	/**
	 * 分页:union all + 尾部order by的内层(inline view内union+order by在oracle合法)翻页正确
	 */
	@Test
	public void paginationUnion() throws Exception {
		org.junit.jupiter.api.Assumptions.assumeTrue(available, "oracle探针环境不可用,跳过");
		String sql = "select id,name from sqltoy_probe_11g where id <= 22 "
				+ "union all select id,name from sqltoy_probe_11g where id >= 44 order by id";
		Set<Long> page1 = extractIds(runPage(sql, 1L, 2));
		Set<Long> page2 = extractIds(runPage(sql, 2L, 2));
		assertEquals(2, page1.size(), "union分页第一页2行");
		assertEquals(2, page2.size(), "union分页第二页2行");
		assertTrue(page1.contains(11L) && page1.contains(22L), "union第一页应为id 11,22: " + page1);
		assertTrue(page2.contains(44L) && page2.contains(55L), "union第二页应为id 44,55: " + page2);
	}

	/**
	 * top:rownum<=N截取,带order by时取排序后前N行;请求量超匹配行数时返回全部匹配行
	 */
	@Test
	public void topQuery() throws Exception {
		org.junit.jupiter.api.Assumptions.assumeTrue(available, "oracle探针环境不可用,跳过");
		QueryResult top2 = runTop("select id,name from sqltoy_probe_11g where score >= 30 order by score desc", 2);
		assertEquals(2, rowCount(top2), "top 2应2行");
		assertEquals(new HashSet<>(java.util.Arrays.asList(55L, 44L)), extractIds(top2), "按score倒序top2应为id 55,44");
		QueryResult over = runTop("select id,name from sqltoy_probe_11g where score >= 30 order by score desc", 99);
		assertEquals(3, rowCount(over), "top请求量超匹配行数时返回全部匹配行");
	}

	/**
	 * 随机:dbms_random.random排序+rownum截取,单条与多条形态;返回行均属探针表且不重复
	 */
	@Test
	public void randomQuery() throws Exception {
		org.junit.jupiter.api.Assumptions.assumeTrue(available, "oracle探针环境不可用,跳过");
		String sql = "select id,name from sqltoy_probe_11g where score > 0";
		QueryResult single = runRandom(sql, 5L, 1L);
		assertEquals(1, rowCount(single), "随机1条应1行");
		Long oneId = extractIds(single).iterator().next();
		assertTrue(java.util.Arrays.asList(11L, 22L, 33L, 44L, 55L).contains(oneId), "随机行应属探针表: " + oneId);
		QueryResult multi = runRandom(sql, 5L, 3L);
		assertEquals(3, rowCount(multi), "随机3条应3行");
		Set<Long> three = extractIds(multi);
		assertEquals(3, three.size(), "随机3条不应重复");
		for (Long id : three) {
			assertTrue(java.util.Arrays.asList(11L, 22L, 33L, 44L, 55L).contains(id), "随机行应属探针表: " + id);
		}
	}

	/**
	 * 分页+随机的组合入口与真实方言路由一致:经getDBProfile识别oracle11(版本11归ORACLE11)
	 */
	@Test
	public void profileDetectsOracle11() throws Exception {
		org.junit.jupiter.api.Assumptions.assumeTrue(available, "oracle探针环境不可用,跳过");
		DBProfile profile = DataSourceUtils.getDBProfile(conn);
		assertEquals("oracle", profile.getDialect(), "产品名oracle");
		assertEquals(DBType.ORACLE11, profile.getDbType(), "11g XE(11.2)应归ORACLE11");
	}

	/** 最小DBProfile连接档案:dialect=oracle+dbType=ORACLE11+majorVersion=11 */
	private static DBProfile profile() {
		return new DBProfile(null, "oracle", DBType.ORACLE11, "Oracle", 11, null, null, false);
	}
}
