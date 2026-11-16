package org.sagacity.sqltoy.utils;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.HashSet;
import java.util.List;
import java.util.Queue;
import java.util.Set;

import org.junit.jupiter.api.Test;
import org.sagacity.sqltoy.ParamFilterProcessor;
import org.sagacity.sqltoy.config.SqlXMLConfigParse;
import org.sagacity.sqltoy.config.model.ParamFilterModel;
import org.sagacity.sqltoy.config.model.SqlToyConfig;
import org.sagacity.sqltoy.model.ParamsFilter;
import org.sagacity.sqltoy.utils.DataSourceUtils.DBType;

/**
 * 新<like>元素回归:raw单遍转义、mode方向、escape-percent、append-str、
 * 容器类型(CharSequence/Object[]/基本数组/List/Set/Queue)、XML解析与非法配置校验。
 */
public class LikeFilterElementTest {

	@Test
	public void rawEscapeSinglePass() {
		assertNull(SqlUtil.escapeLikeValueRaw(null, DBType.MYSQL, true));
		assertEquals("", SqlUtil.escapeLikeValueRaw("", DBType.MYSQL, true));
		assertEquals("a\\\\b", SqlUtil.escapeLikeValueRaw("a\\b", DBType.MYSQL, true));
		assertEquals("a\\\\\\\\b", SqlUtil.escapeLikeValueRaw("a\\\\b", DBType.MYSQL, true));
		assertEquals("a\\_b", SqlUtil.escapeLikeValueRaw("a_b", DBType.MYSQL, true));
		assertEquals("50\\%", SqlUtil.escapeLikeValueRaw("50%", DBType.MYSQL, true));
		assertEquals("50%", SqlUtil.escapeLikeValueRaw("50%", DBType.MYSQL, false));
		// SQL Server方括号是字符类通配符
		assertEquals("\\[a]", SqlUtil.escapeLikeValueRaw("[a]", DBType.SQLSERVER, true));
		assertEquals("[a]", SqlUtil.escapeLikeValueRaw("[a]", DBType.MYSQL, true));
	}

	@Test
	public void bothModeRaw() {
		Object result = process("C:\\Users\\_test", "both", "raw", true, null);
		// 原始值中\Users\_test同时含"字面反斜杠"和"字面下划线":需要\\ + \_ = 三个反斜杠
		assertEquals("%C:\\\\Users\\\\\\_test%", result);
		// 与旧实现不同:raw不会再吃掉原始\_这一层
		Object legacy = process("C:\\Users\\_test", "both", "legacy", true, null);
		assertEquals("%C:\\\\Users\\_test%", legacy);
	}

	@Test
	public void leftRightModeWithAppendStr() {
		assertEquals("%,100", process("100", "left", "raw", true, ","));
		assertEquals("%,100", process("100", "suffix", "raw", true, ","));
		assertEquals("100,%", process("100", "right", "raw", true, ","));
		assertEquals("100,%", process("100", "prefix", "raw", true, ","));
		assertEquals("%100%", process("100", "contains", "raw", true, null));
	}

	@Test
	public void escapePercentSwitch() {
		assertEquals("%50\\%%", process("50%", "both", "raw", true, null));
		assertEquals("%50%%", process("50%", "both", "raw", false, null));
	}

	@Test
	public void containerTypes() {
		assertEquals("%a\\_b%", process(new StringBuilder("a_b"), "both", "raw", true, null));
		assertEquals("%a\\_b%", process(new StringBuffer("a_b"), "both", "raw", true, null));
		Object[] objectArray = (Object[]) process(new Object[] { "a_b", "c\\d" }, "both", "raw", true, null);
		assertEquals(Arrays.asList("%a\\_b%", "%c\\\\d%"), Arrays.asList(objectArray));
		Object[] stringArray = (Object[]) process(new String[] { "a_b", "c\\d" }, "both", "raw", true, null);
		assertEquals(Arrays.asList("%a\\_b%", "%c\\\\d%"), Arrays.asList(stringArray));
		Object[] intArray = (Object[]) process(new int[] { 1, 2 }, "both", "raw", true, null);
		assertEquals(Arrays.asList("%1%", "%2%"), Arrays.asList(intArray));
		List<String> list = new ArrayList<String>(Arrays.asList("a_b", "c\\d"));
		Object listResult = process(list, "both", "raw", true, null);
		assertEquals(Arrays.asList("%a\\_b%", "%c\\\\d%"), listResult);
		Set<String> set = new HashSet<String>(Arrays.asList("a_b"));
		Object setResult = process(set, "both", "raw", true, null);
		assertTrue(((Set<?>) setResult).contains("%a\\_b%"));
		Queue<String> queue = new ArrayDeque<String>(Arrays.asList("a_b"));
		Object queueResult = process(queue, "both", "raw", true, null);
		assertEquals(Collections.singletonList("%a\\_b%"), queueResult);
	}

	@Test
	public void h2RawPatternMatchesExactValue() throws Exception {
		Class.forName("org.h2.Driver");
		Connection conn = DriverManager.getConnection("jdbc:h2:mem:likeFilter;MODE=Oracle", "sa", "");
		try {
			conn.createStatement().execute("create table t(v varchar(100))");
			String[] values = { "C:\\Users\\_test", "C:\\Users_test", "C:\\UsersX_test", "a\\b", "a\\\\b", "a_b",
					"a\\_b" };
			PreparedStatement insert = conn.prepareStatement("insert into t values(?)");
			for (String value : values) {
				insert.setString(1, value);
				insert.execute();
			}
			assertH2Match(conn, "C:\\Users\\_test", "C:\\Users\\_test");
			assertH2Match(conn, "a\\\\b", "a\\\\b");
			assertH2Match(conn, "a\\_b", "a\\_b");
		} finally {
			conn.close();
		}
	}

	private static void assertH2Match(Connection conn, String value, String expected) throws Exception {
		String pattern = (String) process(value, "both", "raw", true, null);
		PreparedStatement ps = conn.prepareStatement("select v from t where v like ? escape '\\'");
		ps.setString(1, pattern);
		ResultSet rs = ps.executeQuery();
		List<String> hits = new ArrayList<String>();
		while (rs.next()) {
			hits.add(rs.getString(1));
		}
		assertEquals(Collections.singletonList(expected), hits, "pattern=" + pattern);
	}

	@Test
	public void oldLikeKeepsHistoricalContainerScope() {
		StringBuilder value = new StringBuilder("a_b");
		ParamFilterModel old = new ParamFilterModel();
		old.setFilterType("l-like");
		old.setParams(new String[] { "keyword" });
		old.setParam("keyword");
		Object[] result = ParamFilterProcessor.filterValue(null, new String[] { "keyword" }, new Object[] { value },
				Collections.singletonList(old));
		assertTrue(result[0] == value, "old l-like should leave StringBuilder untouched");
		// 新like启用扩展容器
		assertEquals("%a\\_b%", process(value, "both", "raw", true, null));
	}

	@Test
	public void blankReturnsNull() {
		assertNull(process(null, "both", "raw", true, null));
		assertNull(process("", "both", "raw", true, null));
		assertNull(process("   ", "both", "raw", true, null));
	}

	@Test
	public void xmlParsesLikeAttributes() throws Exception {
		String xml = "<sql id=\"likeXml\"><filters><like params=\"keyword\" mode=\"left\" escape-mode=\"raw\""
				+ " escape-percent=\"false\" append-str=\",\"/></filters>"
				+ "<value>select * from t where name like :keyword</value></sql>";
		SqlToyConfig config = SqlXMLConfigParse.parseSagment(xml, "utf-8", "mysql");
		ParamFilterModel like = findLike(config.getFilters());
		assertEquals("like", like.getFilterType());
		assertEquals("left", like.getLikeMode());
		assertEquals("raw", like.getLikeEscapeMode());
		assertEquals(false, like.isLikeEscapePercent());
		assertEquals(",", like.getAppendStr());
		assertEquals(Arrays.asList("keyword"), Arrays.asList(like.getParams()));
	}

	@Test
	public void xmlUsesLikeDefaultsAndParamAlias() throws Exception {
		String xml = "<sql id=\"likeDefault\"><filters><like params=\"keyword\"/></filters>"
				+ "<value>select * from t where name like :keyword</value></sql>";
		ParamFilterModel like = findLike(SqlXMLConfigParse.parseSagment(xml, "utf-8", "mysql").getFilters());
		assertEquals("both", like.getLikeMode());
		assertEquals("raw", like.getLikeEscapeMode());
		assertEquals(true, like.isLikeEscapePercent());

		xml = "<sql id=\"likeParamAlias\"><filters><like param=\"keyword\" mode=\"right\"/></filters>"
				+ "<value>select * from t where name like :keyword</value></sql>";
		like = findLike(SqlXMLConfigParse.parseSagment(xml, "utf-8", "mysql").getFilters());
		assertEquals("right", like.getLikeMode());
		assertEquals(Arrays.asList("keyword"), Arrays.asList(like.getParams()));
	}

	@Test
	public void paramsFilterBuilderTransfersLikeAttributes() {
		ParamsFilter filter = new ParamsFilter("keyword").like("left", "raw").likeEscapePercent(false);
		List<ParamFilterModel> combined = ParamFilterProcessor.combineFilters(null,
				Collections.singletonList(filter));
		assertEquals(1, combined.size());
		assertEquals("like", combined.get(0).getFilterType());
		assertEquals("left", combined.get(0).getLikeMode());
		assertEquals("raw", combined.get(0).getLikeEscapeMode());
		assertEquals(false, combined.get(0).isLikeEscapePercent());
	}

	@Test
	public void xmlRejectsInvalidLikeConfig() {
		assertInvalid("<like/>");
		assertInvalid("<like params=\"*\" />");
		assertInvalid("<like params=\"keyword\" param=\"keyword\" />");
		assertInvalid("<like params=\"keyword\" mode=\"middle\" />");
		assertInvalid("<like params=\"keyword\" escape-mode=\"none\" />");
		assertInvalid("<like params=\"keyword\" escape-percent=\"yes\" />");
		assertInvalid("<like params=\"keyword\" mode=\"both\" append-str=\",\" />");
	}

	private static void assertInvalid(String likeXml) {
		String xml = "<sql id=\"badLike\"><filters>" + likeXml
				+ "</filters><value>select * from t where name like :keyword</value></sql>";
		Throwable error = assertThrows(Throwable.class,
				() -> SqlXMLConfigParse.parseSagment(xml, "utf-8", "mysql"));
		assertTrue(error.getMessage() != null && error.getMessage().contains("like"),
				"error should mention like, got: " + error.getMessage());
	}

	private static ParamFilterModel findLike(List<ParamFilterModel> filters) {
		for (ParamFilterModel filter : filters) {
			if ("like".equals(filter.getFilterType())) {
				return filter;
			}
		}
		throw new AssertionError("no like filter parsed");
	}

	private static Object process(Object value, String mode, String escapeMode, boolean escapePercent,
			String appendStr) {
		ParamFilterModel filter = new ParamFilterModel();
		filter.setFilterType("like");
		filter.setParams(new String[] { "keyword" });
		filter.setParam("keyword");
		filter.setLikeMode(mode);
		filter.setLikeEscapeMode(escapeMode);
		filter.setLikeEscapePercent(escapePercent);
		filter.setAppendStr(appendStr);
		Object[] result = ParamFilterProcessor.filterValue(null, new String[] { "keyword" }, new Object[] { value },
				Collections.singletonList(filter));
		return result[0];
	}
}
