package org.sagacity.sqltoy.utils;

import static org.junit.jupiter.api.Assertions.assertEquals;

import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.Statement;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.sagacity.sqltoy.ParamFilterProcessor;
import org.sagacity.sqltoy.config.SqlConfigParseUtils;
import org.sagacity.sqltoy.config.model.ParamFilterModel;
import org.sagacity.sqltoy.config.model.SqlToyResult;

/**
 * like过滤链矩阵回归:XML filters(新like/raw/legacy、l-like/r-like、escapeLike)→
 * 中央解析链processLike→H2真实执行。锁定"过滤器先转义值最后补%"与processLike
 * 反斜杠奇偶判定的衔接语义:偶数反斜杠后的%是通配符(右补%不被吞),奇数反斜杠后的%是字面量
 *
 * @author zhongxuchen
 * @version v1.0,Date:2026-11-19
 */
@DisplayName("like过滤链→processLike→H2执行矩阵")
public class LikeFilterChainParityTest {

	private static final String URL = "jdbc:h2:mem:likeChainParity;DB_CLOSE_DELAY=-1;MODE=Oracle";

	@BeforeAll
	public static void init() throws Exception {
		try (Connection conn = DriverManager.getConnection(URL, "sa", ""); Statement st = conn.createStatement()) {
			st.execute("drop table if exists t");
			st.execute("create table t(v varchar(100))");
			String[] rows = { "C:\\Users", "C:\\Windows", "C:\\Users\\_x", "D:\\Users", "end:C:\\", "50%off",
					"50Xoff", "a_b", "axb", "a\\%b", "aXb", "100,x", "100x", "100%off", "100\\y", "a%b" };
			PreparedStatement ps = conn.prepareStatement("insert into t values(?)");
			for (String row : rows) {
				ps.setString(1, row);
				ps.execute();
			}
		}
	}

	/**
	 * 驱动完整链路:过滤器加工参数值→processSql(含processLike)→H2执行,返回命中值(排序)
	 */
	private static List<String> chain(Object value, ParamFilterModel filter) throws Exception {
		Object[] filtered = ParamFilterProcessor.filterValue(null, new String[] { "keyword" },
				new Object[] { value }, Collections.singletonList(filter));
		SqlToyResult r = SqlConfigParseUtils.processSql("select v from t where v like ?", null, filtered);
		try (Connection conn = DriverManager.getConnection(URL, "sa", "")) {
			PreparedStatement ps = conn.prepareStatement(r.getSql());
			ps.setString(1, (String) r.getParamsValue()[0]);
			ResultSet rs = ps.executeQuery();
			List<String> hits = new ArrayList<String>();
			while (rs.next()) {
				hits.add(rs.getString(1));
			}
			Collections.sort(hits);
			return hits;
		}
	}

	private static ParamFilterModel likeFilter(String mode, String escapeMode, boolean escapePercent,
			String appendStr) {
		ParamFilterModel filter = new ParamFilterModel();
		filter.setFilterType("like");
		filter.setParams(new String[] { "keyword" });
		filter.setParam("keyword");
		filter.setLikeMode(mode);
		filter.setLikeEscapeMode(escapeMode);
		filter.setLikeEscapePercent(escapePercent);
		filter.setAppendStr(appendStr);
		return filter;
	}

	private static ParamFilterModel oldFilter(String type) {
		ParamFilterModel filter = new ParamFilterModel();
		filter.setFilterType(type);
		filter.setParams(new String[] { "keyword" });
		filter.setParam("keyword");
		return filter;
	}

	@Test
	@DisplayName("right模式尾部反斜杠:raw与legacy均保持前缀语义")
	public void rightModeTrailingBackslash() throws Exception {
		List<String> expected = java.util.Arrays.asList("C:\\Users", "C:\\Users\\_x", "C:\\Windows");
		assertEquals(expected, chain("C:\\", likeFilter("right", "raw", true, null)));
		assertEquals(expected, chain("C:\\", likeFilter("right", "legacy", true, null)));
		// 旧r-like同链路同语义
		assertEquals(expected, chain("C:\\", oldFilter("r-like")));
	}

	@Test
	@DisplayName("both/left模式尾部反斜杠与escapeLike过滤器")
	public void bothLeftAndEscapeLikeTrailingBackslash() throws Exception {
		List<String> contains = java.util.Arrays.asList("C:\\Users", "C:\\Users\\_x", "C:\\Windows", "end:C:\\");
		assertEquals(contains, chain("C:\\", likeFilter("both", "raw", true, null)));
		assertEquals(contains, chain("C:\\", oldFilter("escapeLike")));
		assertEquals(java.util.Arrays.asList("end:C:\\"), chain("C:\\", likeFilter("left", "raw", true, null)));
		assertEquals(java.util.Arrays.asList("end:C:\\"), chain("C:\\", oldFilter("l-like")));
	}

	@Test
	@DisplayName("escape-percent=false:用户%保留为通配符")
	public void escapePercentFalseKeepsUserWildcard() throws Exception {
		assertEquals(java.util.Arrays.asList("50%off", "50Xoff"), chain("50%", likeFilter("right", "raw", false, null)));
	}

	@Test
	@DisplayName("escape-percent=true:用户%转义为字面量")
	public void escapePercentTrueLiteralizesUserPercent() throws Exception {
		assertEquals(java.util.Arrays.asList("50%off"), chain("50%", likeFilter("both", "raw", true, null)));
		assertEquals(java.util.Arrays.asList("50%off"), chain("50%", oldFilter("escapeLike")));
	}

	@Test
	@DisplayName("下划线字面量与raw/legacy对原始\\%的不同语义")
	public void underscoreAndRawLegacyDivergence() throws Exception {
		assertEquals(java.util.Arrays.asList("a_b"), chain("a_b", likeFilter("right", "raw", true, null)));
		// raw保留用户原始反斜杠:a\%b匹配字面量a\%b
		assertEquals(java.util.Arrays.asList("a\\%b"), chain("a\\%b", likeFilter("both", "raw", true, null)));
		// legacy幂等剥离已有转义:a\%b的\%被剥为%再转义,匹配字面量a%b
		assertEquals(java.util.Arrays.asList("a%b"), chain("a\\%b", likeFilter("both", "legacy", true, null)));
	}

	@Test
	@DisplayName("append-str拼接:普通字符/百分号/反斜杠均先转义再补%")
	public void appendStrVariants() throws Exception {
		assertEquals(java.util.Arrays.asList("100,x"), chain("100", likeFilter("right", "raw", true, ",")));
		assertEquals(java.util.Arrays.asList("100%off"), chain("100", likeFilter("right", "raw", true, "%")));
		assertEquals(java.util.Arrays.asList("100\\y"), chain("100", likeFilter("right", "raw", true, "\\")));
	}
}