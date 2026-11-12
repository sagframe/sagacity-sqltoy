package org.sagacity.sqltoy.utils;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.Statement;
import java.util.List;

import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.sagacity.sqltoy.model.DBProfile;
import org.sagacity.sqltoy.utils.DataSourceUtils.DBType;

/**
 * update 2026-10-10 回归测试:SqlUtil.processResultSet裸List路径的ext类型归一补齐。
 * 此前该路径无列读取策略预分类与normalizeExtTypeValue,h2的JSON列读回byte[]原样进入
 * 结果行(Map/VO路径早已归一为文本);补齐后裸List路径与ResultUtils.processResultRow、
 * SqlUtil.reflectResultToVO同口径。h2的JSON列getObject返回byte[](EXT_BYTE策略),
 * 是无需真实外部库即可覆盖归一链路的最小场景
 */
public class SqlUtilExtTypeRawPathTest {

	private static Connection conn;

	private static DBProfile profile;

	@BeforeAll
	public static void init() throws Exception {
		conn = DriverManager.getConnection("jdbc:h2:mem:extrawpath;DB_CLOSE_DELAY=-1", "sa", "");
		try (Statement st = conn.createStatement()) {
			st.execute("drop table if exists t_ext");
			st.execute("create table t_ext (id int, doc json, remark varchar(50))");
			st.execute("insert into t_ext values (1, '{\"a\":1,\"b\":\"x\"}' format json, '普通列')");
		}
		profile = DataSourceUtils.getDBProfile(conn);
	}

	@Test
	public void jsonColumnNormalizedToTextOnRawListPath() throws Exception {
		List rows = SqlUtil.findByJdbcQuery(null, "select id, doc, remark from t_ext where id=?", new Object[] { 1 },
				null, null, null, conn, profile, true, null, 0, 0, null);
		assertEquals(1, rows.size());
		List row = (List) rows.get(0);
		// JSON列归一为文本而非byte[](修复前为驱动原生byte[]形态)
		assertTrue(row.get(1) instanceof String, "h2 JSON列在裸List路径应归一为String文本, 实际:"
				+ (row.get(1) == null ? "null" : row.get(1).getClass().getName()));
		String json = (String) row.get(1);
		assertTrue(json.contains("\"a\"") && json.contains("1"), "归一后的json文本应保留内容: " + json);
		// 常规列不受影响
		assertEquals("普通列", row.get(2));
	}

	@Test
	public void plainColumnsUnaffected() throws Exception {
		List rows = SqlUtil.findByJdbcQuery(null, "select id, remark from t_ext", null, null, null, null, conn, profile,
				true, null, 0, 0, null);
		assertEquals(1, rows.size());
		List row = (List) rows.get(0);
		assertEquals(1, row.get(0));
		assertEquals("普通列", row.get(1));
	}

	@Test
	public void dbProfileIsH2() {
		// 前置条件自检:h2连接的探测方言/类型,保证EXT_BYTE判定走h2+JSON分支
		assertEquals(DBType.H2, profile.getDbType());
	}
}
