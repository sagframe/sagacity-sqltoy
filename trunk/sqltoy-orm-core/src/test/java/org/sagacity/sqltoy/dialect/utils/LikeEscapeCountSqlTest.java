package org.sagacity.sqltoy.dialect.utils;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.Statement;

import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.sagacity.sqltoy.SqlToyContext;
import org.sagacity.sqltoy.config.SqlConfigParseUtils;
import org.sagacity.sqltoy.config.model.SqlToyConfig;
import org.sagacity.sqltoy.config.model.SqlToyResult;
import org.sagacity.sqltoy.utils.DataSourceUtils;
import org.sagacity.sqltoy.utils.SqlUtil;
import org.sagacity.sqltoy.utils.StringUtil;

/**
 * 验证like经processLike追加ESCAPE子句('\'或'\\')后,再进入getCountBySql的
 * 字面量掩码检测(maskLiterals)、order by定位剔除、count改写与参数切片是否正确;
 * 并以H2端到端执行验证count语句可执行且计数正确。
 *
 * 表数据:xa_y(仅含a_)、bx_y(仅含x_)、'order by 1'、普通、x%y(字面%)
 */
public class LikeEscapeCountSqlTest {

	private static Connection conn;

	private static SqlToyContext context;

	// 与DialectUtils内私有常量一致的String正则
	private static final String L_SELECT = "select\\s+";
	private static final String L_FROM = "\\s+from[\\(|\\s+]";

	@BeforeAll
	public static void init() throws Exception {
		conn = DriverManager.getConnection("jdbc:h2:mem:likeescape;DB_CLOSE_DELAY=-1", "sa", "");
		try (Statement st = conn.createStatement()) {
			st.execute("drop table if exists t");
			st.execute("create table t (id int, remark varchar(100), a int)");
			st.execute("insert into t values (1,'xa_y',10)");
			st.execute("insert into t values (2,'order by 1',20)");
			st.execute("insert into t values (3,'普通',30)");
			st.execute("insert into t values (4,'x%y',40)");
			st.execute("insert into t values (5,'bx_y',50)");
		}
		context = new SqlToyContext();
	}

	/**
	 * 简单场景:like ? + order by,参数含_触发escapeLikeValue转义与ESCAPE追加,
	 * 随后count改写剔除order by并在H2执行,参数切片与计数必须正确
	 */
	@Test
	public void likeEscapeThenCountSimple() throws Exception {
		String sql = "select * from t where remark like ? order by id";
		SqlToyResult r = SqlConfigParseUtils.processSql(sql, new String[] {}, new Object[] { "a_" }, "h2");
		// h2为标准SQL方言,追加的应是单反斜杠形态 ESCAPE '\'
		assertTrue(r.getSql().contains("like ? ESCAPE '\\'"), "应追加ESCAPE子句: " + r.getSql());
		assertEquals(1, r.getParamsValue().length, "参数应仍为1个");
		assertEquals("%a\\_%", r.getParamsValue()[0], "参数应被加工为通配包裹且_已转义");
		SqlToyConfig config = new SqlToyConfig(null);
		// %a\_%仅命中xa_y一行(精确匹配a_字面序,bx_y不含a_)
		Long count = DialectUtils.getCountBySql(context, config, r.getSql(), r.getParamsValue(), false, null, conn,
				DataSourceUtils.DBType.H2);
		assertEquals(1L, count.longValue(), "转义后的_应只匹配xa_y一行");
		// x_仅命中bx_y一行
		SqlToyResult r2 = SqlConfigParseUtils.processSql(sql, new String[] {}, new Object[] { "x_" }, "h2");
		Long count2 = DialectUtils.getCountBySql(context, config, r2.getSql(), r2.getParamsValue(), false, null, conn,
				DataSourceUtils.DBType.H2);
		assertEquals(1L, count2.longValue(), "x_应只匹配bx_y一行");
		// 命不中的值返回0,验证空结果路径
		SqlToyResult r3 = SqlConfigParseUtils.processSql(sql, new String[] {}, new Object[] { "q_" }, "h2");
		Long count3 = DialectUtils.getCountBySql(context, config, r3.getSql(), r3.getParamsValue(), false, null, conn,
				DataSourceUtils.DBType.H2);
		assertEquals(0L, count3.longValue(), "命不中时应返回0");
	}

	/**
	 * ESCAPE子句在前、后续字面量内含'order by 1':字面量不得干扰order by定位与剔除;
	 * 若掩码错位将导致count sql破碎,H2执行会抛异常或计数错误
	 */
	@Test
	public void likeEscapeWithLiteralOrderBy() throws Exception {
		String sql = "select * from t where remark like ? or remark='order by 1' order by id";
		SqlToyResult r = SqlConfigParseUtils.processSql(sql, new String[] {}, new Object[] { "a_" }, "h2");
		assertTrue(r.getSql().contains("like ? ESCAPE '\\'"), "应追加ESCAPE子句: " + r.getSql());
		SqlToyConfig config = new SqlToyConfig(null);
		// like命中xa_y一行 + 字面量精确命中id=2一行 = 2
		Long count = DialectUtils.getCountBySql(context, config, r.getSql(), r.getParamsValue(), false, null, conn,
				DataSourceUtils.DBType.H2);
		assertEquals(2L, count.longValue(), "字面量干扰下count仍应正确");
	}

	/**
	 * 子查询+distinct+group by包裹场景与ESCAPE共存
	 */
	@Test
	public void likeEscapeWithWrapScenes() throws Exception {
		SqlToyConfig config = new SqlToyConfig(null);
		// 子查询内like,外层order by;x_仅命中bx_y
		SqlToyResult r1 = SqlConfigParseUtils.processSql("select * from (select * from t where remark like ?) z order by id",
				new String[] {}, new Object[] { "x_" }, "h2");
		assertTrue(r1.getSql().contains("ESCAPE '\\'"), "参数含_应触发ESCAPE: " + r1.getSql());
		assertEquals(1L, DialectUtils.getCountBySql(context, config, r1.getSql(), r1.getParamsValue(), false, null,
				conn, DataSourceUtils.DBType.H2).longValue(), "子查询场景count应命中bx_y一行");
		// distinct包裹
		SqlToyResult r2 = SqlConfigParseUtils.processSql("select distinct remark from t where remark like ? order by remark",
				new String[] {}, new Object[] { "x_" }, "h2");
		assertEquals(1L, DialectUtils.getCountBySql(context, config, r2.getSql(), r2.getParamsValue(), false, null,
				conn, DataSourceUtils.DBType.H2).longValue(), "distinct包裹场景count应正确");
		// group by包裹
		SqlToyResult r3 = SqlConfigParseUtils.processSql(
				"select remark, count(1) cnt from t where remark like ? group by remark order by remark",
				new String[] {}, new Object[] { "x_" }, "h2");
		assertEquals(1L, DialectUtils.getCountBySql(context, config, r3.getSql(), r3.getParamsValue(), false, null,
				conn, DataSourceUtils.DBType.H2).longValue(), "group by包裹场景count应正确");
	}

	/**
	 * order by片段带参数:count改写须剔除order by并从参数数组尾部剔除对应值,
	 * 与ESCAPE子句共存时参数切片不能错位
	 */
	@Test
	public void likeEscapeWithOrderByParam() throws Exception {
		String sql = "select * from t where remark like ? order by case when ? = 1 then id else a end";
		SqlToyResult r = SqlConfigParseUtils.processSql(sql, new String[] {}, new Object[] { "a_", 1 }, "h2");
		assertTrue(r.getSql().contains("ESCAPE '\\'"), "应追加ESCAPE子句: " + r.getSql());
		assertEquals(2, r.getParamsValue().length, "参数应为2个");
		SqlToyConfig config = new SqlToyConfig(null);
		Long count = DialectUtils.getCountBySql(context, config, r.getSql(), r.getParamsValue(), false, null, conn,
				DataSourceUtils.DBType.H2);
		assertEquals(1L, count.longValue(), "剔除order by参数后count应正确执行(仅xa_y命中)");
	}

	/**
	 * 双形态掩码完整性:追加的ESCAPE字面量与后续其他字面量共存时,
	 * 两种backslashEscape设定下掩码都必须正确闭合每个字面量,
	 * order by定位必须与原串在ORDER_BY_PATTERN上的定位一致(字面量内的order by已被掩掉)
	 */
	@Test
	public void escapeClauseMaskIntegrity() {
		// {sql, backslashEscape, 期望模式};sql为processLike产物形态:h2/Oracle为'\',mysql系为'\\'
		// 期望模式sql=掩码定位与原串真实order by一致;-1=无真实order by,掩码上必须找不到order by
		String[][] cases = {
				{ "select * from t where remark like ? escape '\\' and remark='order by 1' order by id", "false",
						"sql" },
				{ "select * from t where remark like ? escape '\\\\' and remark='order by 1' order by id", "true",
						"sql" },
				{ "select * from t where remark like ? escape '\\' and remark='it''s ok' "
						+ "and a in (select a from t where remark like ?) order by id desc", "false", "sql" },
				{ "select * from t where remark like ? escape '\\\\' and remark='it''s ok' "
						+ "and a in (select a from t where remark like ?) order by id desc", "true", "sql" },
				// 字面量内含order by与(:掩码错位时会被误判为语法特征
				{ "select * from t where remark like ? escape '\\' and remark='order by (1' order by id", "false",
						"sql" },
				// escape字面量后随字面量收尾且无真实order by:定位必须为-1(引号不得被吞)
				{ "select * from t where remark like ? escape '\\' and remark='order by 1'", "false", "-1" },
				{ "select * from t where remark like ? escape '\\\\' and remark='order by 1'", "true", "-1" } };
		for (String[] item : cases) {
			String sql = item[0];
			boolean backslashEscape = Boolean.parseBoolean(item[1]);
			String expectMode = item[2];
			String masked = SqlConfigParseUtils.maskLiterals(sql, backslashEscape);
			assertEquals(sql.length(), masked.length(), "掩码串必须与原串等长: " + sql);
			// 每个字面量内容已被掩为空白:掩码串上不应再出现字面量内特征词
			assertTrue(!masked.contains("order by 1"), "字面量内的order by 1应被掩掉: " + masked);
			assertTrue(!masked.contains("it''s"), "成对转义引号应被掩掉: " + masked);
			// 真实order by定位:有真实order by时,末尾真实order by位于字面量之后,
			// 是原串的最后匹配,掩码串定位必须与其一致;无真实order by时必须为-1
			int maskedOrderBy = StringUtil.matchLastIndex(masked, DialectUtils.ORDER_BY_PATTERN, 1);
			if ("sql".equals(expectMode)) {
				int sqlOrderBy = StringUtil.matchLastIndex(sql, DialectUtils.ORDER_BY_PATTERN, 1);
				assertEquals(sqlOrderBy, maskedOrderBy, "order by定位不得因ESCAPE子句错位: " + masked);
			} else {
				assertEquals(-1, maskedOrderBy, "不应存在可定位的order by: " + masked);
			}
			// select...from定位与参数计数在掩码上同样正确
			int fromIndex = SqlUtil.getSymMarkIndexExcludeKeyWords(masked, L_SELECT, L_FROM, 0);
			assertTrue(fromIndex > 0, "select...from定位应正确: " + masked);
			int expectParams = DialectUtils.getParamsCount(sql, backslashEscape);
			assertTrue(expectParams >= 1, "参数计数应正确: " + sql);
		}
	}

	/**
	 * count改写后的sql形态复核:复刻getCountBySql掩码检测逻辑,断言
	 * order by剔除位置与ESCAPE字面量保留完整性(字符串手术仍用原串)
	 */
	@Test
	public void countRewriteShapeWithEscape() {
		String sql = "select * from t where remark like ? escape '\\' and remark='order by 1' order by id";
		String masked = SqlConfigParseUtils.maskLiterals(sql, false);
		int lastBracketIndex = masked.lastIndexOf(")");
		int sql_from_index = SqlUtil.getSymMarkIndexExcludeKeyWords(masked, L_SELECT, L_FROM, 0);
		int orderByIndex = StringUtil.matchLastIndex(masked, DialectUtils.ORDER_BY_PATTERN, 1);
		assertTrue(orderByIndex > sql_from_index, "order by在from之后");
		assertTrue(orderByIndex > lastBracketIndex, "order by在最后一个括号之后,可整体剔除");
		String kept = sql.substring(0, orderByIndex + 1);
		assertTrue(!kept.contains("order by id"), "剔除后不应再含order by排序项");
		// ESCAPE字面量在保留段中完整(引号未丢)
		assertTrue(kept.contains("escape '\\'"), "ESCAPE子句必须完整保留在count sql中: " + kept);
		assertEquals(1, DialectUtils.getParamsCount(kept, false), "保留段参数应为1个");
	}

	/**
	 * 批量组合矩阵:多种like参数值在h2与mysql两种dialect下的ESCAPE子句形态与参数计数。
	 * 语义约定:值中已有%(用户自带通配符)不转义%本身;仅当加工结果含反斜杠
	 * (含_被转义、或已有\%转义标记)时才追加ESCAPE子句
	 */
	@Test
	public void escapeClauseFormMatrix() {
		String sql = "select * from t where remark like ? order by id";
		// {值, 是否应追加ESCAPE}
		String[][] values = { { "a_", "true" }, { "x%y", "false" }, { "普通", "false" }, { "plain", "false" },
				{ "a\\%b", "true" } };
		for (String[] v : values) {
			String value = v[0];
			boolean expectEscape = Boolean.parseBoolean(v[1]);
			// h2:false形态'\'
			SqlToyResult r = SqlConfigParseUtils.processSql(sql, new String[] {}, new Object[] { value }, "h2");
			assertEquals(expectEscape, r.getSql().contains("ESCAPE '\\'"), "h2形态ESCAPE判定错误: " + value);
			assertEquals(1, DialectUtils.getParamsCount(r.getSql(), false), "h2形态参数计数错误: " + value);
			// mysql系:true形态'\\'
			SqlToyResult rm = SqlConfigParseUtils.processSql(sql, new String[] {}, new Object[] { value }, "mysql");
			assertEquals(expectEscape, rm.getSql().contains("ESCAPE '\\\\'"), "mysql形态ESCAPE判定错误: " + value);
			assertEquals(1, DialectUtils.getParamsCount(rm.getSql(), true), "mysql形态参数计数错误: " + value);
		}
	}
}
