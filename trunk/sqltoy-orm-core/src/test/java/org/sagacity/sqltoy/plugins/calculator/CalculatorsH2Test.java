package org.sagacity.sqltoy.plugins.calculator;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;

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
 * calculator 包 ReverseList / RowsChainRelative / UnpivotList 的 H2 真实链路回归测试：
 * sql xml 解析 → H2 内存库查询 → ResultUtils 分发 → 计算器。
 * 重点固化两处缺陷修复:ReverseList 偶数长度(含2行)反转、RowsChainRelative 单分组(groupSize=1)端点行环比;
 * 同时覆盖多分组(groupSize=2/3)确保边界修正不影响原有行为。
 * (GroupSummary/pivot/ColsChainRelative/TreeDataSort 已由 ResultProcessorsH2Test 覆盖)
 */
public class CalculatorsH2Test {
	private SqlToyContext context;
	private JdbcDataSource dataSource;

	@BeforeEach
	public void setUp() throws Exception {
		String url = "jdbc:h2:mem:calc_h2_" + UUID.randomUUID().toString().replace("-", "") + ";DB_CLOSE_DELAY=-1";
		try (Connection conn = DriverManager.getConnection(url, "sa", ""); Statement st = conn.createStatement()) {
			st.execute("create table calc_rev2 (id int, val int)");
			st.execute("create table calc_rev4 (id int, val int)");
			st.execute("create table calc_rev5 (id int, val int)");
			st.execute("create table calc_rev6 (id int, val int)");
			st.execute("create table calc_rows (mon varchar(8), product varchar(8), cnt int)");
			st.execute("create table calc_rows1 (mon varchar(8), cnt int)");
			st.execute("create table calc_rows3 (mon varchar(8), product varchar(8), cnt int)");
			st.execute("create table calc_unpivot (name varchar(8), q1 int, q2 int, q3 int)");
			st.execute("create table calc_unpivot_grp (name varchar(8), q1 int, q2 int, q3 int, q4 int)");
			st.execute("create table calc_rows2 (mon varchar(8), cnt int)");
			st.execute("create table calc_rows5 (mon varchar(8), cnt int)");
			st.execute("create table calc_grp1 (mon varchar(8), product varchar(8), cnt int)");
			st.execute("create table calc_wide (name varchar(8), a int, b int, c int)");
			insert(conn, "insert into calc_rev2 values (?,?)", new Object[][] { { 1, 10 }, { 2, 20 } });
			insert(conn, "insert into calc_rev4 values (?,?)", new Object[][] { { 1, 10 }, { 2, 20 }, { 3, 30 }, { 4, 40 } });
			insert(conn, "insert into calc_rev5 values (?,?)",
					new Object[][] { { 1, 10 }, { 2, 20 }, { 3, 30 }, { 4, 40 }, { 5, 50 } });
			insert(conn, "insert into calc_rev6 values (?,?)",
					new Object[][] { { 1, 10 }, { 2, 20 }, { 3, 30 }, { 4, 40 }, { 5, 50 }, { 6, 60 } });
			insert(conn, "insert into calc_rows values (?,?,?)", new Object[][] { { "05", "B", 2000 }, { "05", "A", 1900 },
					{ "04", "B", 1800 }, { "04", "A", 1800 }, { "03", "B", 1600 }, { "03", "A", 1700 } });
			insert(conn, "insert into calc_rows1 values (?,?)", new Object[][] { { "03", 100 }, { "04", 150 }, { "05", 120 } });
			insert(conn, "insert into calc_rows3 values (?,?,?)",
					new Object[][] { { "05", "C", 300 }, { "05", "B", 200 }, { "05", "A", 100 }, { "04", "C", 250 },
							{ "04", "B", 150 }, { "04", "A", 80 }, { "03", "C", 200 }, { "03", "B", 100 }, { "03", "A", 50 } });
			insert(conn, "insert into calc_unpivot values (?,?,?,?)", new Object[][] { { "A", 10, 20, 30 }, { "B", 40, 50, 60 } });
			insert(conn, "insert into calc_unpivot_grp values (?,?,?,?,?)",
					new Object[][] { { "A", 10, 20, 30, 40 }, { "B", 50, 60, 70, 80 } });
			insert(conn, "insert into calc_rows2 values (?,?)", new Object[][] { { "03", 100 }, { "04", 150 } });
			insert(conn, "insert into calc_rows5 values (?,?)",
					new Object[][] { { "01", 10 }, { "02", 20 }, { "03", 30 }, { "04", 40 }, { "05", 50 } });
			insert(conn, "insert into calc_grp1 values (?,?,?)",
					new Object[][] { { "05", "A", 100 }, { "04", "B", 200 }, { "03", "C", 300 } });
			insert(conn, "insert into calc_wide values (?,?,?,?)", new Object[][] { { "A", 10, 20, 40 }, { "B", 5, 10, 20 } });
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

	private List<?> query(String xml) throws Exception {
		SqlToyConfig config = SqlXMLConfigParse.parseSagment(xml, "UTF-8", null);
		assertNotNull(config, "sql xml 应能解析");
		QueryResult result = DialectFactory.getInstance().findByQuery(context, new QueryExecutor(config.getSql()), config,
				null, dataSource);
		assertNotNull(result, "查询应有返回");
		return result.getRows();
	}

	private static List<Object> row(Object... cells) {
		return new ArrayList<>(Arrays.asList(cells));
	}

	@SafeVarargs
	private static List<List<Object>> rows(List<Object>... rs) {
		return new ArrayList<>(Arrays.asList(rs));
	}

	// ==================== ReverseList ====================
	@Test
	public void reverseEvenLengthIncludingTwoRows() throws Exception {
		List<?> r2 = query("<sql id=\"r2\"><value><![CDATA[select id,val from calc_rev2 order by id]]></value><reverse/></sql>");
		assertEquals(rows(row(2, 20), row(1, 10)), r2, "2行(偶数)必须完全反转");

		List<?> r4 = query("<sql id=\"r4\"><value><![CDATA[select id,val from calc_rev4 order by id]]></value><reverse/></sql>");
		assertEquals(rows(row(4, 40), row(3, 30), row(2, 20), row(1, 10)), r4, "4行(偶数)必须完全反转");

		List<?> r6 = query("<sql id=\"r6\"><value><![CDATA[select id,val from calc_rev6 order by id]]></value><reverse/></sql>");
		assertEquals(rows(row(6, 60), row(5, 50), row(4, 40), row(3, 30), row(2, 20), row(1, 10)), r6, "6行(偶数)必须完全反转");
	}

	@Test
	public void reverseOddLengthAndSubRange() throws Exception {
		List<?> r5 = query("<sql id=\"r5\"><value><![CDATA[select id,val from calc_rev5 order by id]]></value><reverse/></sql>");
		assertEquals(rows(row(5, 50), row(4, 40), row(3, 30), row(2, 20), row(1, 10)), r5, "5行(奇数)全反转");

		List<?> subOdd = query("<sql id=\"so\"><value><![CDATA[select id,val from calc_rev5 order by id]]></value>"
				+ "<reverse start-row=\"1\" end-row=\"3\"/></sql>");
		assertEquals(rows(row(1, 10), row(4, 40), row(3, 30), row(2, 20), row(5, 50)), subOdd, "子区间[1,3](3个,奇数)反转");

		List<?> subEven = query("<sql id=\"se\"><value><![CDATA[select id,val from calc_rev6 order by id]]></value>"
				+ "<reverse start-row=\"1\" end-row=\"4\"/></sql>");
		assertEquals(rows(row(1, 10), row(5, 50), row(4, 40), row(3, 30), row(2, 20), row(6, 60)), subEven,
				"子区间[1,4](4个,偶数)反转");
	}

	@Test
	public void reverseListEdgeCases() throws Exception {
		String base5 = "<value><![CDATA[select id,val from calc_rev5 order by id]]></value>";
		// 2元素子区间(首/尾)
		List<?> front = query("<sql id=\"rf\">" + base5 + "<reverse start-row=\"0\" end-row=\"1\"/></sql>");
		assertEquals(rows(row(2, 20), row(1, 10), row(3, 30), row(4, 40), row(5, 50)), front, "首部2元素子区间反转");
		List<?> tail = query("<sql id=\"rt\">" + base5 + "<reverse start-row=\"3\" end-row=\"4\"/></sql>");
		assertEquals(rows(row(1, 10), row(2, 20), row(3, 30), row(5, 50), row(4, 40)), tail, "尾部2元素子区间反转");
		// start==end: 单元素不变
		List<?> same = query("<sql id=\"rs\">" + base5 + "<reverse start-row=\"2\" end-row=\"2\"/></sql>");
		assertEquals(rows(row(1, 10), row(2, 20), row(3, 30), row(4, 40), row(5, 50)), same, "start==end单元素不变");
		// start>end: 非法区间不动
		List<?> invalid = query("<sql id=\"ri\">" + base5 + "<reverse start-row=\"3\" end-row=\"1\"/></sql>");
		assertEquals(rows(row(1, 10), row(2, 20), row(3, 30), row(4, 40), row(5, 50)), invalid, "start>end非法区间不变");
	}

	// ==================== RowsChainRelative ====================
	@Test
	public void rowsChainSingleGroupAscAndDesc() throws Exception {
		// 单分组升序 reverse=false: 每行除以上一行, 末行也要算
		List<?> asc = query("<sql id=\"asc\"><value><![CDATA[select mon,cnt from calc_rows1 order by mon]]></value>"
				+ "<rows-chain-relative relative-columns=\"cnt\" reverse=\"false\" reduce-one=\"false\" radix-size=\"4\" format=\"#.00%\"/></sql>");
		assertEquals(rows(row("03", 100, null), row("04", 150, "150.00%"), row("05", 120, "80.00%")), asc,
				"单分组升序: 04=150/100, 05=120/150, 首行为基准");

		// 单分组降序 reverse=true: 每行除以下一行, 首行也要算
		List<?> desc = query("<sql id=\"desc\"><value><![CDATA[select mon,cnt from calc_rows1 order by mon desc]]></value>"
				+ "<rows-chain-relative relative-columns=\"cnt\" reverse=\"true\" reduce-one=\"false\" radix-size=\"4\" format=\"#.00%\"/></sql>");
		assertEquals(rows(row("05", 120, "80.00%"), row("04", 150, "150.00%"), row("03", 100, null)), desc,
				"单分组降序: 05=120/150, 04=150/100, 末行为基准");
	}

	@Test
	public void rowsChainSingleGroupIncrementMode() throws Exception {
		// reduce-one=true: (本期-上期)/上期
		List<?> inc = query("<sql id=\"inc\"><value><![CDATA[select mon,cnt from calc_rows1 order by mon]]></value>"
				+ "<rows-chain-relative relative-columns=\"cnt\" reverse=\"false\" reduce-one=\"true\" radix-size=\"4\" format=\"#.00%\"/></sql>");
		assertEquals(rows(row("03", 100, null), row("04", 150, "50.00%"), row("05", 120, "-20.00%")), inc,
				"单分组增长率: 04=(150-100)/100=50%, 05=(120-150)/150=-20%");
	}

	@Test
	public void rowsChainMultiGroupUnchanged() throws Exception {
		// groupSize=2 文档示例: 边界修正后行为不变
		List<?> g2 = query("<sql id=\"g2\"><value><![CDATA[select mon,product,cnt from calc_rows order by mon desc, product desc]]></value>"
				+ "<rows-chain-relative group-column=\"product\" relative-columns=\"cnt\" reverse=\"true\" reduce-one=\"false\" radix-size=\"4\" format=\"#.00%\"/></sql>");
		assertEquals(rows(row("05", "B", 2000, "111.11%"), row("05", "A", 1900, "105.56%"), row("04", "B", 1800, "112.50%"),
				row("04", "A", 1800, "105.88%"), row("03", "B", 1600, null), row("03", "A", 1700, null)), g2,
				"2分组行环比(本月/上月)");

		// groupSize=3: 更大分组同样不受边界修正影响
		List<?> g3 = query("<sql id=\"g3\"><value><![CDATA[select mon,product,cnt from calc_rows3 order by mon desc, product desc]]></value>"
				+ "<rows-chain-relative group-column=\"product\" relative-columns=\"cnt\" reverse=\"true\" reduce-one=\"false\" radix-size=\"4\" format=\"#.00%\"/></sql>");
		assertEquals(rows(row("05", "C", 300, "120.00%"), row("05", "B", 200, "133.33%"), row("05", "A", 100, "125.00%"),
				row("04", "C", 250, "125.00%"), row("04", "B", 150, "150.00%"), row("04", "A", 80, "160.00%"),
				row("03", "C", 200, null), row("03", "B", 100, null), row("03", "A", 50, null)), g3, "3分组行环比");
	}

	@Test
	public void rowsChainRelativeEdgeCases() throws Exception {
		String rc = "<rows-chain-relative relative-columns=\"cnt\" reduce-one=\"false\" radix-size=\"4\" format=\"#.00%\"";
		// 2行单分组: 旧实现完全不算, 修复后应有1个环比值
		List<?> asc2 = query("<sql id=\"a2\"><value><![CDATA[select mon,cnt from calc_rows2 order by mon]]></value>"
				+ rc + " reverse=\"false\"/></sql>");
		assertEquals(rows(row("03", 100, null), row("04", 150, "150.00%")), asc2, "2行单分组升序");
		List<?> desc2 = query("<sql id=\"d2\"><value><![CDATA[select mon,cnt from calc_rows2 order by mon desc]]></value>"
				+ rc + " reverse=\"true\"/></sql>");
		assertEquals(rows(row("04", 150, "150.00%"), row("03", 100, null)), desc2, "2行单分组降序");

		// 子区间[1,3]单分组升序: 区间内首行为基准, 区间外不动
		List<?> sub = query("<sql id=\"sub\"><value><![CDATA[select mon,cnt from calc_rows5 order by mon]]></value>"
				+ rc + " reverse=\"false\" start-row=\"1\" end-row=\"3\"/></sql>");
		assertEquals(rows(row("01", 10, null), row("02", 20, null), row("03", 30, "150.00%"), row("04", 40, "133.33%"),
				row("05", 50, null)), sub, "子区间[1,3]单分组升序");

		// insert=false 更新模式(Select已预留rel列): 端点行同样要算
		List<?> update = query("<sql id=\"upd\"><value><![CDATA[select mon,cnt,null as rel from calc_rows5 order by mon]]></value>"
				+ "<rows-chain-relative relative-columns=\"cnt\" insert=\"false\" reduce-one=\"false\" radix-size=\"4\" format=\"#.00%\" reverse=\"false\"/></sql>");
		assertEquals(rows(row("01", 10, null), row("02", 20, "200.00%"), row("03", 30, "150.00%"), row("04", 40, "133.33%"),
				row("05", 50, "125.00%")), update, "insert=false单分组升序");

		// 仅1组(groupSize=行数): 无对比, 全为null
		List<?> oneGroup = query("<sql id=\"og\"><value><![CDATA[select mon,product,cnt from calc_grp1 order by mon desc]]></value>"
				+ "<rows-chain-relative group-column=\"product\" relative-columns=\"cnt\" radix-size=\"4\" format=\"#.00%\" reverse=\"true\"/></sql>");
		assertEquals(rows(row("05", "A", 100, null), row("04", "B", 200, null), row("03", "C", 300, null)), oneGroup,
				"仅1组无环比");
	}

	// ==================== 负数下标(三计算器统一修复) ====================
	@Test
	public void reverseNegativeIndex() throws Exception {
		String base5 = "<value><![CDATA[select id,val from calc_rev5 order by id]]></value>";
		// end-row=-1 指最后一行 → 全反转
		List<?> last = query("<sql id=\"nl\">" + base5 + "<reverse end-row=\"-1\"/></sql>");
		assertEquals(rows(row(5, 50), row(4, 40), row(3, 30), row(2, 20), row(1, 10)), last, "end-row=-1指最后一行,全反转");
		// end-row=-2 指倒数第二行 → 反转[0,3]
		List<?> last2 = query("<sql id=\"nl2\">" + base5 + "<reverse end-row=\"-2\"/></sql>");
		assertEquals(rows(row(4, 40), row(3, 30), row(2, 20), row(1, 10), row(5, 50)), last2, "end-row=-2指倒数第二行");
		// start-row=-1 钳到0, 不崩溃 → 全反转
		List<?> negStart = query("<sql id=\"ns\">" + base5 + "<reverse start-row=\"-1\"/></sql>");
		assertEquals(rows(row(5, 50), row(4, 40), row(3, 30), row(2, 20), row(1, 10)), negStart, "start-row=-1钳到0不崩溃");
	}

	@Test
	public void rowsChainNegativeIndex() throws Exception {
		String rc = "<rows-chain-relative relative-columns=\"cnt\" reduce-one=\"false\" radix-size=\"4\" format=\"#.00%\"";
		// end-row=-1 指最后一行 → 整个降序区间都算(含倒数第二行02)
		List<?> desc = query("<sql id=\"nd\"><value><![CDATA[select mon,cnt from calc_rows5 order by mon desc]]></value>"
				+ rc + " reverse=\"true\" end-row=\"-1\"/></sql>");
		assertEquals(rows(row("05", 50, "125.00%"), row("04", 40, "133.33%"), row("03", 30, "150.00%"),
				row("02", 20, "200.00%"), row("01", 10, null)), desc, "end-row=-1指最后一行,全区间环比");
		// start-row=-1 钳到0, 不崩溃
		List<?> asc = query("<sql id=\"na\"><value><![CDATA[select mon,cnt from calc_rows5 order by mon]]></value>"
				+ rc + " reverse=\"false\" start-row=\"-1\"/></sql>");
		assertEquals(rows(row("01", 10, null), row("02", 20, "200.00%"), row("03", 30, "150.00%"), row("04", 40, "133.33%"),
				row("05", 50, "125.00%")), asc, "start-row=-1钳到0不崩溃");
	}

	@Test
	public void colsChainNegativeIndexMatchesExplicit() throws Exception {
		String base = "<value><![CDATA[select name,a,b,c from calc_wide order by name]]></value>";
		String head = "<cols-chain-relative group-size=\"1\" start-column=\"1\" format=\"#.00%\"";
		// end-column=-1 应与显式末列(3)及${dataWidth}-1表达式完全一致
		List<?> neg = query("<sql id=\"cn\">" + base + head + " end-column=\"-1\"/></sql>");
		List<?> explicit = query("<sql id=\"ce\">" + base + head + " end-column=\"3\"/></sql>");
		List<?> expr = query("<sql id=\"cx\">" + base + head + " end-column=\"${dataWidth}-1\"/></sql>");
		assertEquals(explicit, neg, "end-column=-1应等同显式末列");
		assertEquals(expr, neg, "end-column=-1应等同${dataWidth}-1表达式");
		// start-column=-1 钳到0, 应等同start-column=0
		List<?> negStart = query("<sql id=\"cns\"><value><![CDATA[select name,a,b,c from calc_wide order by name]]></value>"
				+ "<cols-chain-relative group-size=\"1\" start-column=\"-1\" end-column=\"3\" format=\"#.00%\"/></sql>");
		List<?> zeroStart = query("<sql id=\"czs\"><value><![CDATA[select name,a,b,c from calc_wide order by name]]></value>"
				+ "<cols-chain-relative group-size=\"1\" start-column=\"0\" end-column=\"3\" format=\"#.00%\"/></sql>");
		assertEquals(zeroStart, negStart, "start-column=-1钳到0应等同start-column=0");
	}

	// ==================== UnpivotList ====================
	@Test
	public void unpivotSingleGroup() throws Exception {
		List<?> u1 = query("<sql id=\"u1\"><value><![CDATA[select name,q1,q2,q3 from calc_unpivot order by name]]></value>"
				+ "<unpivot columns-to-rows=\"q1:一季度,q2:二季度,q3:三季度\" new-columns-labels=\"季度,数值\"/></sql>");
		assertEquals(rows(row("A", "一季度", 10), row("A", "二季度", 20), row("A", "三季度", 30), row("B", "一季度", 40),
				row("B", "二季度", 50), row("B", "三季度", 60)), u1, "单组列转行");
	}

	@Test
	public void unpivotMultiGroup() throws Exception {
		List<?> u2 = query("<sql id=\"u2\"><value><![CDATA[select name,q1,q2,q3,q4 from calc_unpivot_grp order by name]]></value>"
				+ "<unpivot columns-to-rows=\"{q1:一,q2:二},{q3:三,q4:四}\" new-columns-labels=\"季度,值1,值2\"/></sql>");
		assertEquals(rows(row("A", "一", 10, 30), row("A", "二", 20, 40), row("B", "一", 50, 70), row("B", "二", 60, 80)), u2,
				"多组列转行");
	}
}