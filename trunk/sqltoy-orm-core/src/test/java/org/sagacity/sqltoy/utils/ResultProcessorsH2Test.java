package org.sagacity.sqltoy.utils;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;

import java.math.BigDecimal;
import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.PreparedStatement;
import java.sql.Statement;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.UUID;

import org.h2.jdbcx.JdbcDataSource;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.sagacity.sqltoy.SqlToyContext;
import org.sagacity.sqltoy.config.SqlXMLConfigParse;
import org.sagacity.sqltoy.config.model.SqlToyConfig;
import org.sagacity.sqltoy.dialect.DialectFactory;
import org.sagacity.sqltoy.integration.impl.SimpleConnectionFactory;
import org.sagacity.sqltoy.model.QueryExecutor;
import org.sagacity.sqltoy.model.QueryResult;

/**
 * @project sagacity-sqltoy
 * @description 结果集计算处理器(分组汇总、行转列、树形排序与逐层汇总)的真实链路验证：sql xml 解析 → H2 内存库查询 → ResultUtils 分发 →
 *              底层算法。数据与 xml 配置取自文档"复杂查询与数据分析"一节，期望值即文档给出的结果表(行转列的类别按首次出现顺序排列)。
 *              这三个处理器此前没有任何经过 sql xml 入口的测试覆盖。
 * @author zhongxuchen
 */
public class ResultProcessorsH2Test {
	private static final Object[][] FRUIT_ORDERS = { { "苹果", "2019年3月", 13, 2000, 2500 },
			{ "苹果", "2019年4月", 11, 1900, 2600 }, { "苹果", "2019年5月", 12, 2000, 2400 },
			{ "香蕉", "2019年3月", 13, 2300, 2700 }, { "香蕉", "2019年4月", 12, 2400, 2700 },
			{ "香蕉", "2019年5月", 10, 2000, 2000 } };

	private static final Object[][] AREA_SALES = { { "松江", "上海", 120 }, { "杨浦", "上海", 116 },
			{ "上海", "中国", null }, { "南京", "江苏", 110 }, { "浦东", "上海", 64 }, { "江苏", "中国", null },
			{ "苏州", "江苏", 90 }, { "无锡", "江苏", 70 } };

	/** 两个类别列的组合 (1,23) 与 (12,3)：不加分隔拼接后都是 "123" */
	private static final Object[][] CATEGORY_COLLISION = { { "p", "1", "23", 111 }, { "p", "12", "3", 222 } };

	private SqlToyContext context;
	private JdbcDataSource dataSource;

	@BeforeEach
	public void setUp() throws Exception {
		String url = "jdbc:h2:mem:result_processors_" + UUID.randomUUID().toString().replace("-", "")
				+ ";DB_CLOSE_DELAY=-1";
		try (Connection conn = DriverManager.getConnection(url, "sa", ""); Statement st = conn.createStatement()) {
			st.execute("create table sqltoy_fruit_order (fruit_name varchar(32), order_month varchar(32), "
					+ "sale_count int, sale_quantity int, total_amt int)");
			st.execute("create table sqltoy_area_sales (area_code varchar(32), pid_area varchar(32), sale_cnt int)");
			st.execute("create table sqltoy_cat_collision (grp varchar(8), c1 varchar(8), c2 varchar(8), v int)");
			insert(conn, "insert into sqltoy_fruit_order values (?,?,?,?,?)", FRUIT_ORDERS);
			insert(conn, "insert into sqltoy_area_sales values (?,?,?)", AREA_SALES);
			insert(conn, "insert into sqltoy_cat_collision values (?,?,?,?)", CATEGORY_COLLISION);
		}
		dataSource = new JdbcDataSource();
		dataSource.setURL(url);
		dataSource.setUser("sa");
		context = new SqlToyContext();
		context.setConnectionFactory(new SimpleConnectionFactory());
	}

	@AfterEach
	public void tearDown() throws Exception {
		try (Connection conn = dataSource.getConnection(); Statement st = conn.createStatement()) {
			st.execute("shutdown");
		}
	}

	private static void insert(Connection conn, String sql, Object[][] rows) throws Exception {
		try (PreparedStatement pst = conn.prepareStatement(sql)) {
			for (Object[] row : rows) {
				for (int i = 0; i < row.length; i++) {
					pst.setObject(i + 1, row[i]);
				}
				pst.addBatch();
			}
			pst.executeBatch();
		}
	}

	/** 解析 sql xml 片段并在 H2 上执行，返回经过结果集计算处理后的行 */
	private List<?> query(String xml) throws Exception {
		SqlToyConfig config = SqlXMLConfigParse.parseSagment(xml, "UTF-8", null);
		assertNotNull(config, "sql xml 应能解析");
		QueryResult result = DialectFactory.getInstance().findByQuery(context,
				new QueryExecutor(config.getSql()), config, null, dataSource);
		assertNotNull(result, "查询应有返回");
		return result.getRows();
	}

	private static List<Object> row(Object... cells) {
		return new ArrayList<>(Arrays.asList(cells));
	}

	private static final String FRUIT_SQL = "<value><![CDATA["
			+ "select t.fruit_name,t.order_month,t.sale_count,t.sale_quantity,t.total_amt "
			+ "from sqltoy_fruit_order t order by t.fruit_name,t.order_month" + "]]></value>";

	@Test
	public void summaryMatchesDocumentation() throws Exception {
		List<?> rows = query("<sql id=\"fruit_summary\">" + FRUIT_SQL
				+ "<summary sum-columns=\"sale_count,sale_quantity,total_amt\" reverse=\"true\">"
				+ "<global label-column=\"fruit_name\" sum-label=\"总计\" />"
				+ "<group group-column=\"fruit_name\" label-column=\"fruit_name\" sum-label=\"小计\" />"
				+ "</summary></sql>");
		List<List<Object>> expected = new ArrayList<>();
		expected.add(row("总计", null, new BigDecimal("71"), new BigDecimal("12600"), new BigDecimal("14900")));
		expected.add(row("小计", null, new BigDecimal("36"), new BigDecimal("5900"), new BigDecimal("7500")));
		expected.add(row("苹果", "2019年3月", 13, 2000, 2500));
		expected.add(row("苹果", "2019年4月", 11, 1900, 2600));
		expected.add(row("苹果", "2019年5月", 12, 2000, 2400));
		expected.add(row("小计", null, new BigDecimal("35"), new BigDecimal("6700"), new BigDecimal("7400")));
		expected.add(row("香蕉", "2019年3月", 13, 2300, 2700));
		expected.add(row("香蕉", "2019年4月", 12, 2400, 2700));
		expected.add(row("香蕉", "2019年5月", 10, 2000, 2000));
		assertEquals(expected, rows);
	}

	@Test
	public void pivotMatchesDocumentation() throws Exception {
		List<?> rows = query("<sql id=\"fruit_pivot\">" + FRUIT_SQL
				+ "<pivot group-columns=\"fruit_name\" category-columns=\"order_month\" "
				+ "start-column=\"sale_count\" end-column=\"total_amt\" /></sql>");
		List<List<Object>> expected = new ArrayList<>();
		// 类别(月份)按首次出现的先后排列：3月、4月、5月
		expected.add(row("苹果", 13, 2000, 2500, 11, 1900, 2600, 12, 2000, 2400));
		expected.add(row("香蕉", 13, 2300, 2700, 12, 2400, 2700, 10, 2000, 2000));
		assertEquals(expected, rows);
	}

	@Test
	public void pivotShorthandTransposesTheWholeResult() throws Exception {
		List<?> rows = query("<sql id=\"fruit_pivot_shorthand\">" + FRUIT_SQL + "<pivot /></sql>");
		// 不带属性的 <pivot/> 是纯行列转置：原来的一列变成一行
		List<List<Object>> expected = new ArrayList<>();
		expected.add(row("苹果", "苹果", "苹果", "香蕉", "香蕉", "香蕉"));
		expected.add(row("2019年3月", "2019年4月", "2019年5月", "2019年3月", "2019年4月", "2019年5月"));
		expected.add(row(13, 11, 12, 13, 12, 10));
		expected.add(row(2000, 1900, 2000, 2300, 2400, 2000));
		expected.add(row(2500, 2600, 2400, 2700, 2700, 2000));
		assertEquals(expected, rows);
	}

	/**
	 * 两个类别列的组合 (1,23) 与 (12,3) 不加分隔拼接后都是 "123"：修复前会被判成同一个类别，结果只剩一列、另一组数据丢失
	 */
	@Test
	public void multiCategoryColumnsWithAmbiguousTextStayApart() throws Exception {
		List<?> rows = query("<sql id=\"cat_collision\">"
				+ "<value><![CDATA[select t.grp,t.c1,t.c2,t.v from sqltoy_cat_collision t]]></value>"
				+ "<pivot group-columns=\"grp\" category-columns=\"c1,c2\" start-column=\"v\" end-column=\"v\" />"
				+ "</sql>");
		List<List<Object>> expected = new ArrayList<>();
		expected.add(row("p", 111, 222));
		assertEquals(expected, rows, "两组类别都应保留；(1,23) 文本更短，排在前面");
	}

	@Test
	public void pivotFeedsColumnChainRelative() throws Exception {
		List<?> rows = query("<sql id=\"fruit_pivot_relative\">" + FRUIT_SQL
				+ "<pivot group-columns=\"fruit_name\" category-columns=\"order_month\" "
				+ "start-column=\"sale_count\" end-column=\"total_amt\" />"
				+ "<cols-chain-relative group-size=\"3\" relative-indexs=\"1,2\" start-column=\"1\" format=\"#.00%\" />"
				+ "</sql>");
		// 行转列的输出行必须是 List，才能被后续的环比计算按列消费。未写 reduce-one 时环比是 B/A(不减 1)，首组无对比值
		List<List<Object>> expected = new ArrayList<>();
		expected.add(row("苹果", 13, 2000, null, 2500, null, 11, 1900, "95.00%", 2600, "104.00%", 12, 2000,
				"105.30%", 2400, "92.30%"));
		expected.add(row("香蕉", 13, 2300, null, 2700, null, 12, 2400, "104.30%", 2700, "100.00%", 10, 2000,
				"83.30%", 2000, "74.10%"));
		assertEquals(expected, rows);
	}

	@Test
	public void treeSortWithSumAndLevelOrderMatchesDocumentation() throws Exception {
		List<?> rows = query("<sql id=\"area_tree_sort\">"
				+ "<value><![CDATA[select t.area_code,t.pid_area,sale_cnt from sqltoy_area_sales t]]></value>"
				+ "<tree-sort id-column=\"area_code\" pid-column=\"pid_area\" sum-columns=\"sale_cnt\" "
				+ "level-order-column=\"sale_cnt\" order-way=\"desc\" /></sql>");
		// 上层节点汇总所有子孙节点的数据，同一层级按销售量降序
		List<List<Object>> expected = new ArrayList<>();
		expected.add(row("上海", "中国", new BigDecimal("300")));
		expected.add(row("松江", "上海", 120));
		expected.add(row("杨浦", "上海", 116));
		expected.add(row("浦东", "上海", 64));
		expected.add(row("江苏", "中国", new BigDecimal("270")));
		expected.add(row("南京", "江苏", 110));
		expected.add(row("苏州", "江苏", 90));
		expected.add(row("无锡", "江苏", 70));
		assertEquals(expected, rows);
	}
}
