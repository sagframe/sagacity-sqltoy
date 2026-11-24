package org.sagacity.sqltoy.utils;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.Serializable;
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
import org.sagacity.sqltoy.config.annotation.OneToMany;
import org.sagacity.sqltoy.config.model.SqlToyConfig;
import org.sagacity.sqltoy.dialect.DialectFactory;
import org.sagacity.sqltoy.integration.impl.SimpleConnectionFactory;
import org.sagacity.sqltoy.model.QueryExecutor;
import org.sagacity.sqltoy.model.QueryResult;

/**
 * ResultUtils 集合处理修复的 H2 真实查询链路全场景回归(非反射,经 findByQuery 完整管线):
 * - hashGroupList: 一对多级联(@OneToMany)正常/复合键逗号碰撞/单键/null分组;
 * - getLinkColumnsId: 多列link下划线碰撞/正常/单列数字/null分组;
 * - sortList/sortGroupList: 多类别行转列类别排序/数字类别排序。
 * 这些均为核心功能点,逐场景断言结果正确。
 */
public class RealScenarioH2Test {
	private SqlToyContext context;
	private JdbcDataSource dataSource;

	// ---------- 级联 POJO ----------
	public static class Item implements Serializable {
		private String oid;
		private String customer;
		private String product;
		private Integer qty;
		public String getOid() { return oid; }
		public void setOid(String oid) { this.oid = oid; }
		public String getCustomer() { return customer; }
		public void setCustomer(String customer) { this.customer = customer; }
		public String getProduct() { return product; }
		public void setProduct(String product) { this.product = product; }
		public Integer getQty() { return qty; }
		public void setQty(Integer qty) { this.qty = qty; }
	}

	public static class Ord implements Serializable {
		private String oid;
		private String customer;
		@OneToMany(fields = { "oid", "customer" }, mappedFields = { "oid", "customer" })
		private List<Item> items = new ArrayList<>();
		public String getOid() { return oid; }
		public void setOid(String oid) { this.oid = oid; }
		public String getCustomer() { return customer; }
		public void setCustomer(String customer) { this.customer = customer; }
		public List<Item> getItems() { return items; }
		public void setItems(List<Item> items) { this.items = items; }
	}

	public static class SItem implements Serializable {
		private String oid;
		private String product;
		private Integer qty;
		public String getOid() { return oid; }
		public void setOid(String oid) { this.oid = oid; }
		public String getProduct() { return product; }
		public void setProduct(String product) { this.product = product; }
		public Integer getQty() { return qty; }
		public void setQty(Integer qty) { this.qty = qty; }
	}

	public static class SOrd implements Serializable {
		private String oid;
		@OneToMany(fields = "oid", mappedFields = "oid")
		private List<SItem> items = new ArrayList<>();
		public String getOid() { return oid; }
		public void setOid(String oid) { this.oid = oid; }
		public List<SItem> getItems() { return items; }
		public void setItems(List<SItem> items) { this.items = items; }
	}

	@BeforeEach
	public void setUp() throws Exception {
		String url = "jdbc:h2:mem:realh2_" + UUID.randomUUID().toString().replace("-", "") + ";DB_CLOSE_DELAY=-1";
		try (Connection conn = DriverManager.getConnection(url, "sa", ""); Statement st = conn.createStatement()) {
			st.execute("create table casc_normal (oid varchar(32), customer varchar(32), product varchar(32), qty int)");
			st.execute("create table casc_comma (oid varchar(32), customer varchar(32), product varchar(32), qty int)");
			st.execute("create table casc_single (oid varchar(32), product varchar(32), qty int)");
			st.execute("create table link_t (c1 varchar(32), c2 varchar(32), val varchar(32))");
			st.execute("create table link_num (gid int, val varchar(32))");
			st.execute("create table link_null (c1 varchar(32), c2 varchar(32), val varchar(32))");
			st.execute("create table piv_t (g varchar(8), c1 varchar(8), c2 varchar(8), v int)");
			st.execute("create table piv_num (g varchar(8), c int, v int)");
			ins(conn, "insert into casc_normal values (?,?,?,?)", new Object[][] {
					{ "O1", "Alice", "apple", 1 }, { "O1", "Alice", "banana", 2 }, { "O2", "Bob", "cherry", 3 } });
			ins(conn, "insert into casc_comma values (?,?,?,?)", new Object[][] {
					{ "a,b", "c", "apple", 1 }, { "a", "b,c", "banana", 2 } });
			ins(conn, "insert into casc_single values (?,?,?)", new Object[][] {
					{ "S1", "apple", 1 }, { "S1", "banana", 2 }, { "S2", "cherry", 3 }, { null, "orphan", 9 } });
			ins(conn, "insert into link_t values (?,?,?)", new Object[][] {
					{ "a_b", "c", "v1" }, { "a", "b_c", "v2" } });
			ins(conn, "insert into link_num values (?,?)", new Object[][] { { 1, "a" }, { 1, "b" }, { 2, "c" } });
			ins(conn, "insert into link_null values (?,?,?)", new Object[][] {
					{ null, "x", "v1" }, { null, "x", "v2" }, { "k", "y", "v3" } });
			ins(conn, "insert into piv_t values (?,?,?,?)", new Object[][] {
					{ "G1", "x", "2", 10 }, { "G1", "x", "1", 20 }, { "G1", "y", "1", 30 }, { "G2", "x", "1", 40 } });
			ins(conn, "insert into piv_num values (?,?,?)", new Object[][] {
					{ "G1", 10, 100 }, { "G1", 2, 200 }, { "G1", 1, 300 } });
		}
		dataSource = new JdbcDataSource();
		dataSource.setURL(url);
		dataSource.setUser("sa");
		context = new SqlToyContext();
		context.setConnectionFactory(new SimpleConnectionFactory());
	}

	@AfterEach
	public void tearDown() throws Exception {
		try (Connection c = dataSource.getConnection(); Statement s = c.createStatement()) {
			s.execute("shutdown");
		}
	}

	private static void ins(Connection c, String sql, Object[][] rows) throws Exception {
		try (PreparedStatement p = c.prepareStatement(sql)) {
			for (Object[] r : rows) {
				for (int i = 0; i < r.length; i++) p.setObject(i + 1, r[i]);
				p.addBatch();
			}
			p.executeBatch();
		}
	}

	private QueryResult query(String sql, String xmlTail, Class<?> resultType, boolean hiberarchy) throws Exception {
		String xml = "<sql id=\"q\"><value><![CDATA[" + sql + "]]></value>" + (xmlTail == null ? "" : xmlTail) + "</sql>";
		SqlToyConfig cfg = SqlXMLConfigParse.parseSagment(xml, "UTF-8", null);
		QueryExecutor exec = new QueryExecutor(sql);
		if (resultType != null) {
			exec.resultType(resultType);
		}
		if (hiberarchy) {
			exec.hiberarchy(true);
		}
		return DialectFactory.getInstance().findByQuery(context, exec, cfg, null, dataSource);
	}

	private static List<Object> row(Object... cells) {
		return new ArrayList<>(Arrays.asList(cells));
	}

	// ==================== 一对多级联 (hashGroupList) ====================
	@Test
	public void cascadeCompositeKeyNormal() throws Exception {
		QueryResult r = query("select oid,customer,product,qty from casc_normal", null, Ord.class, true);
		List<?> rows = r.getRows();
		assertEquals(2, rows.size(), "两个主对象");
		Ord o1 = (Ord) rows.get(0);
		assertEquals("O1", o1.getOid());
		assertEquals("Alice", o1.getCustomer());
		assertEquals(2, o1.getItems().size(), "O1两个子项");
		assertEquals("apple", o1.getItems().get(0).getProduct());
		assertEquals("banana", o1.getItems().get(1).getProduct());
		Ord o2 = (Ord) rows.get(1);
		assertEquals("O2", o2.getOid());
		assertEquals(1, o2.getItems().size());
		assertEquals("cherry", o2.getItems().get(0).getProduct());
	}

	@Test
	public void cascadeCompositeKeyWithCommaNotMerged() throws Exception {
		QueryResult r = query("select oid,customer,product,qty from casc_comma", null, Ord.class, true);
		List<?> rows = r.getRows();
		assertEquals(2, rows.size(), "含逗号的不同复合键不应合并主对象");
		Ord o1 = (Ord) rows.get(0);
		assertEquals("a,b", o1.getOid());
		assertEquals("c", o1.getCustomer());
		assertEquals(1, o1.getItems().size());
		assertEquals("apple", o1.getItems().get(0).getProduct());
		Ord o2 = (Ord) rows.get(1);
		assertEquals("a", o2.getOid());
		assertEquals("b,c", o2.getCustomer());
		assertEquals(1, o2.getItems().size());
		assertEquals("banana", o2.getItems().get(0).getProduct());
	}

	@Test
	public void cascadeSingleKeyWithNullGroup() throws Exception {
		QueryResult r = query("select oid,product,qty from casc_single", null, SOrd.class, true);
		List<?> rows = r.getRows();
		// S1(2子项), S2(1子项), null(1子项orphan) → 3组
		assertEquals(3, rows.size(), "单键分组含null组共3个主对象");
		SOrd s1 = (SOrd) rows.get(0);
		assertEquals("S1", s1.getOid());
		assertEquals(2, s1.getItems().size());
		SOrd s2 = (SOrd) rows.get(1);
		assertEquals("S2", s2.getOid());
		assertEquals(1, s2.getItems().size());
		SOrd sn = (SOrd) rows.get(2);
		assertEquals(null, sn.getOid(), "null键自成一组");
		assertEquals(1, sn.getItems().size());
		assertEquals("orphan", sn.getItems().get(0).getProduct());
	}

	// ==================== 多列/单列 link (getLinkColumnsId) ====================
	@Test
	public void linkMultiColumnWithUnderscoreNotMerged() throws Exception {
		QueryResult r = query("select c1,c2,val from link_t order by c1,c2",
				"<link columns=\"val\" group-columns=\"c1,c2\" sign=\",\"/>", null, false);
		List<?> rows = r.getRows();
		assertEquals(2, rows.size(), "含下划线的不同多列键不应合并link组");
		assertEquals(row("a", "b_c", "v2"), rows.get(0));
		assertEquals(row("a_b", "c", "v1"), rows.get(1));
	}

	@Test
	public void linkSingleColumnNumericGroup() throws Exception {
		QueryResult r = query("select gid,val from link_num order by gid",
				"<link columns=\"val\" group-columns=\"gid\" sign=\",\"/>", null, false);
		List<?> rows = r.getRows();
		assertEquals(2, rows.size());
		assertEquals(row(1, "a,b"), rows.get(0), "gid=1拼接a,b");
		assertEquals(row(2, "c"), rows.get(1));
	}

	@Test
	public void linkMultiColumnNullGroup() throws Exception {
		QueryResult r = query("select c1,c2,val from link_null order by c1,c2",
				"<link columns=\"val\" group-columns=\"c1,c2\" sign=\",\"/>", null, false);
		List<?> rows = r.getRows();
		// (null,x)两行合并为v1,v2; (k,y)一行v3 → 2组
		assertEquals(2, rows.size(), "null分组与正常分组");
		boolean foundNullGroup = false;
		for (Object o : rows) {
			List<?> lr = (List<?>) o;
			if (lr.get(0) == null && "x".equals(lr.get(1))) {
				assertEquals("v1,v2", lr.get(2), "null键同组拼接");
				foundNullGroup = true;
			}
		}
		assertTrue(foundNullGroup, "应存在null分组");
	}

	// ==================== 行转列类别排序 (sortList/sortGroupList) ====================
	@Test
	public void pivotMultiCategorySorted() throws Exception {
		QueryResult r = query("select g,c1,c2,v from piv_t order by g,c1,c2",
				"<pivot group-columns=\"g\" category-columns=\"c1,c2\" start-column=\"v\" end-column=\"v\"/>", null, false);
		List<?> rows = r.getRows();
		// 类别排序后 (x,1),(x,2),(y,1)
		assertEquals(2, rows.size());
		assertEquals(row("G1", 20, 10, 30), rows.get(0), "G1按类别(x,1)=20,(x,2)=10,(y,1)=30");
		assertEquals(row("G2", 40, null, null), rows.get(1), "G2仅(x,1)=40,其余默认null");
	}

	@Test
	public void pivotNumericCategorySorted() throws Exception {
		QueryResult r = query("select g,c,v from piv_num order by g,c",
				"<pivot group-columns=\"g\" category-columns=\"c\" start-column=\"v\" end-column=\"v\"/>", null, false);
		List<?> rows = r.getRows();
		// 数字类别升序 1,2,10 → G1: v(1)=300,v(2)=200,v(10)=100
		assertEquals(1, rows.size());
		assertEquals(row("G1", 300, 200, 100), rows.get(0), "数字类别按数值升序1,2,10而非文本1,10,2");
	}

	// ==================== 真实查询 VO 封装 (MapperUtils 子类映射) ====================
	public static class WideVo implements Serializable {
		private Number num;
		private Object obj;
		private java.util.Date dt;
		public Number getNum() { return num; }
		public void setNum(Number num) { this.num = num; }
		public Object getObj() { return obj; }
		public void setObj(Object obj) { this.obj = obj; }
		public java.util.Date getDt() { return dt; }
		public void setDt(java.util.Date dt) { this.dt = dt; }
	}

	@Test
	public void voMappingKeepsSubclassValues() throws Exception {
		QueryResult r = query("select 42 as num, 'hello' as obj, timestamp '2023-11-15 06:13:20' as dt from (values(1))",
				null, WideVo.class, false);
		List<?> rows = r.getRows();
		assertEquals(1, rows.size());
		WideVo vo = (WideVo) rows.get(0);
		assertNotNull(vo.getNum(), "Number<-Integer不应丢失");
		assertEquals(42, vo.getNum());
		assertNotNull(vo.getObj(), "Object<-String不应丢失");
		assertEquals("hello", vo.getObj());
		assertNotNull(vo.getDt(), "Date<-Timestamp不应丢失");
		assertEquals(java.sql.Timestamp.valueOf("2023-11-15 06:13:20").getTime(), vo.getDt().getTime());
	}
}