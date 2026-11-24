package org.sagacity.sqltoy.recheck;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.fail;

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
 * @description 2026-11-24 rows-chain-relative 死循环修复的真实链路回归：sql xml 解析 → H2 查询 → ResultUtils 分发 →
 *              RowsChainRelative。触发条件是"指定了分组列，且起止行之间没有数据(恰好 start == end + 1)"，
 *              最容易从配置上触发的写法是结果只有 2 行而 end-row="-3"。查询放在独立线程里限时等待，避免回归时卡死测试进程。
 * @author zhongxuchen
 */
public class RowsChainRelativeH2RecheckTest {
	private SqlToyContext context;
	private JdbcDataSource dataSource;
	private String url;

	@BeforeEach
	public void setUp() throws Exception {
		url = "jdbc:h2:mem:rows_chain_" + UUID.randomUUID().toString().replace("-", "") + ";DB_CLOSE_DELAY=-1";
		try (Connection conn = DriverManager.getConnection(url, "sa", ""); Statement st = conn.createStatement()) {
			st.execute("create table rows_chain (mon varchar(8), product varchar(8), cnt int)");
			try (PreparedStatement pst = conn.prepareStatement("insert into rows_chain values (?,?,?)")) {
				Object[][] rows = { { "01", "A", 100 }, { "01", "B", 200 }, { "02", "A", 150 }, { "02", "B", 180 },
						{ "03", "A", 120 }, { "03", "B", 90 } };
				for (Object[] row : rows) {
					for (int i = 0; i < row.length; i++) {
						pst.setObject(i + 1, row[i]);
					}
					pst.addBatch();
				}
				pst.executeBatch();
			}
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

	private static List<Object> row(Object... cells) {
		return new ArrayList<Object>(Arrays.asList(cells));
	}

	/**
	 * 执行查询并限时等待
	 *
	 * @param whereSql 查询条件，用来控制结果的行数
	 */
	private List<?> query(String whereSql, String calculatorXml, String label) throws Exception {
		String xml = "<sql id=\"rowsChain\"><value><![CDATA[select mon,product,cnt from rows_chain " + whereSql
				+ " order by mon desc, product desc]]></value>" + calculatorXml + "</sql>";
		SqlToyConfig config = SqlXMLConfigParse.parseSagment(xml, "UTF-8", null);
		assertNotNull(config, "sql xml 应能解析");
		final SqlToyContext queryContext = context;
		final List[] holder = new List[1];
		final Throwable[] error = new Throwable[1];
		Thread worker = new Thread(() -> {
			try {
				QueryResult result = DialectFactory.getInstance().findByQuery(queryContext,
						new QueryExecutor(config.getSql()), config, null, dataSource);
				holder[0] = result == null ? null : result.getRows();
			} catch (Throwable e) {
				error[0] = e;
			}
		});
		worker.setDaemon(true);
		worker.start();
		worker.join(10_000);
		if (worker.isAlive()) {
			fail(label + ": 查询在 10 秒内没有返回(陷入死循环)");
		}
		if (error[0] != null) {
			throw new AssertionError(label + ": 查询抛出异常", error[0]);
		}
		return holder[0];
	}

	@Test
	public void endRowBeyondLastRowWithGroupColumnReturns() throws Exception {
		// 只取 2 行(1 个月 x 2 个产品)，end-row=-3 表示截止行超出末尾 1 行：没有可比较的行
		String calculator = "<rows-chain-relative group-column=\"product\" relative-columns=\"cnt\" end-row=\"-3\"/>";
		List<?> result = query("where mon = '02'", calculator, "2行 end-row=-3");
		// 插入模式下环比列仍然补上，值为默认值 null
		assertEquals(Arrays.asList(row("02", "B", 180, null), row("02", "A", 150, null)), result);
	}

	@Test
	public void startRowBeyondLastRowWithGroupColumnReturns() throws Exception {
		// start-row=2 超出最后一行(下标 1)
		String calculator = "<rows-chain-relative group-column=\"product\" relative-columns=\"cnt\" start-row=\"2\"/>";
		List<?> result = query("where mon = '02'", calculator, "2行 start-row=2");
		assertEquals(Arrays.asList(row("02", "B", 180, null), row("02", "A", 150, null)), result);
	}

	@Test
	public void normalMultiGroupChainStillCalculated() throws Exception {
		// 对照：正常的 3 个月 x 2 个产品，从新到旧，本月/上月(默认乘数 1、保留 3 位小数)；最早的 1 月是基准
		String calculator = "<rows-chain-relative group-column=\"product\" relative-columns=\"cnt\"/>";
		List<?> result = query("", calculator, "6行");
		assertEquals(Arrays.asList(row("03", "B", 90, new java.math.BigDecimal("0.500")),
				row("03", "A", 120, new java.math.BigDecimal("0.800")),
				row("02", "B", 180, new java.math.BigDecimal("0.900")),
				row("02", "A", 150, new java.math.BigDecimal("1.500")), row("01", "B", 200, null),
				row("01", "A", 100, null)), result);
	}
}
