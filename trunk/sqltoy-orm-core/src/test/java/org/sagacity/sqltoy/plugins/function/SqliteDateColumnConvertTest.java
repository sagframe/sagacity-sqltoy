package org.sagacity.sqltoy.plugins.function;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.Arrays;

import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

/**
 * sqlite 日期列形态守卫(2026-11-10,update 2026-11-11 补 TEXT 双形态):
 * ORM 的 setTimestamp 在 sqlite 落库为毫秒 INTEGER(真库实证 typeof=integer 1794285296000),
 * 而 sqlite 日期函数把数值当儒略日,直接 strftime('%w', ts) 返回 NULL。因此统一按运行时
 * typeof 判别:INTEGER/REAL 走 /1000 归一,TEXT 列按墙钟文本原样解析——
 * 原实现一律 /1000,遇到 TEXT 列(外部工具写入/DDL声明TEXT)会把 '2026-11-11 10:00:00'
 * 数值化成 2,week/weekday/dayofweek/unix_timestamp 全部静默错值(真库实测 0/3/5/2,
 * 正确 45/2/4/1794391200)。
 * <ul>
 * <li>week 族按会话时区取墙钟(与 MySQL weekday(墙钟)语义一致)→ integer 分支 'localtime'</li>
 * <li>unix_timestamp 取绝对 epoch:integer 分支按 UTC 文本(不带 localtime,否则 %s 偏一个时区);
 * TEXT 分支用 'utc' 修饰按本地墙钟解释,与 MySQL 会话时区语义对齐</li>
 * </ul>
 * week字面量/函数表达式按墙钟文本透传;unix_timestamp统一按本地墙钟转UTC。
 */
public class SqliteDateColumnConvertTest {

	/** 墙钟归一(week 族用):INTEGER毫秒→本地墙钟文本,TEXT→原样墙钟文本 */
	private static final String TEXT_CASE = "case when typeof(ts) in ('integer','real') then "
			+ "datetime(ts/1000,'unixepoch','localtime') else datetime(ts) end";
	/** 绝对epoch归一(unix_timestamp用):INTEGER毫秒→UTC文本,TEXT→本地墙钟转UTC文本 */
	private static final String EPOCH_CASE = "case when typeof(ts) in ('integer','real') then "
			+ "datetime(ts/1000,'unixepoch') else datetime(ts,'utc') end";

	@BeforeAll
	public static void init() {
		FunctionUtils.setFunctionConverts(Arrays.asList("default"));
	}

	private String c(String sql) {
		return FunctionUtils.getDialectSql(sql, "sqlite");
	}

	@Test
	public void weekdayColumnIsNormalized() {
		String out = c("select weekday(ts) from t");
		assertTrue(out.contains(TEXT_CASE), out);
		assertTrue(out.contains("strftime('%w', " + TEXT_CASE + ")"), out);
	}

	@Test
	public void dayofweekColumnIsNormalized() {
		String out = c("select dayofweek(ts) from t");
		assertTrue(out.contains("strftime('%w', " + TEXT_CASE + ")"), out);
	}

	@Test
	public void weekColumnIsNormalized() {
		String out = c("select week(ts) from t");
		assertTrue(out.contains("strftime('%j', " + TEXT_CASE + ")"), out);
		assertTrue(out.contains("date(" + TEXT_CASE + ", 'start of year')"), out);
	}

	@Test
	public void unixTimestampColumnUsesUtcBasis() {
		String out = c("select unix_timestamp(ts) from t");
		assertTrue(out.contains("strftime('%s', " + EPOCH_CASE + ")"), out);
		// 绝对epoch基准:integer分支(毫秒)不能带localtime(否则% s按UTC解析本地文本会偏一个时区)
		assertFalse(out.contains("strftime('%s', datetime(ts/1000,'unixepoch','localtime'))"), out);
		// TEXT分支必须按本地墙钟解释(MySQL会话时区语义),不能恒按UTC
		assertTrue(out.contains("datetime(ts,'utc')"), out);
	}

	@Test
	public void literalsAndExpressionsUseWallClockSemantics() {
		assertTrue(c("select weekday('1999-12-31') from t")
				.contains("strftime('%w', '1999-12-31')"));
		assertTrue(c("select weekday(date('2024-01-01')) from t")
				.contains("strftime('%w', date('2024-01-01'))"));
		assertTrue(c("select unix_timestamp('2026-11-10 12:34:56') from t")
				.contains("strftime('%s', datetime('2026-11-10 12:34:56','utc'))"));
		// 命名参数(毫秒绑定)按列同一口径归一(TEXT/INTEGER 双形态)
		assertTrue(c("select unix_timestamp(:dt) from t")
				.contains("strftime('%s', case when typeof(:dt) in ('integer','real') then "
						+ "datetime(:dt/1000,'unixepoch') else datetime(:dt,'utc') end)"));
	}

	/**
	 * update 2026-11-11 真库值断言(内存库,无需容器):TEXT列与INTEGER毫秒列都必须得到正确结果,
	 * 修复前TEXT列会被 /1000 数值化成2 → week=0/weekday=3/dow=5/unix=2(静默错值)
	 */
	@Test
	public void textAndIntegerColumnsReturnSameCalendarValues() throws Exception {
		java.time.LocalDateTime local = java.time.LocalDateTime.parse("2026-11-11T10:00:00");
		long epochExpect = local.atZone(java.time.ZoneId.systemDefault()).toEpochSecond();
		try (java.sql.Connection conn = java.sql.DriverManager.getConnection("jdbc:sqlite::memory:");
				java.sql.Statement st = conn.createStatement()) {
			st.execute("create table p_text (ts text)");
			st.execute("insert into p_text values ('2026-11-11 10:00:00')");
			st.execute("create table p_ms (ts integer)");
			st.execute("insert into p_ms values (" + epochExpect * 1000 + ")");
			for (String table : new String[] { "p_text", "p_ms" }) {
				assertEquals(45, intOf(conn, c("select week(ts) from " + table)), table + " week 应为45");
				assertEquals(2, intOf(conn, c("select weekday(ts) from " + table)), table + " weekday 应为2(周三)");
				assertEquals(4, intOf(conn, c("select dayofweek(ts) from " + table)), table + " dayofweek 应为4");
			}
			// TEXT按本地墙钟解释(与MySQL会话时区语义一致);INTEGER毫秒为绝对epoch
			assertEquals(epochExpect, longOf(conn, c("select unix_timestamp(ts) from p_text")),
					"TEXT列epoch应按本地墙钟解释");
			assertEquals(epochExpect, longOf(conn, c("select unix_timestamp(ts) from p_ms")),
					"INTEGER毫秒列epoch应为绝对值");
			assertEquals(epochExpect, longOf(conn, c("select unix_timestamp('2026-11-11 10:00:00')")),
					"文本字面量应与TEXT列采用相同时区语义");
			assertEquals(epochExpect, longOf(conn, c("select unix_timestamp(datetime('2026-11-11 10:00:00'))")),
					"文本表达式应与TEXT列采用相同时区语义");
			assertEquals(epochExpect, longOf(conn, c("select unix_timestamp(from_unixtime(" + epochExpect + "))")),
					"epoch往返不应叠加本地时区偏移");
		}
	}

	private static int intOf(java.sql.Connection conn, String sql) throws Exception {
		try (java.sql.Statement st = conn.createStatement(); java.sql.ResultSet rs = st.executeQuery(sql)) {
			rs.next();
			return rs.getInt(1);
		}
	}

	private static long longOf(java.sql.Connection conn, String sql) throws Exception {
		try (java.sql.Statement st = conn.createStatement(); java.sql.ResultSet rs = st.executeQuery(sql)) {
			rs.next();
			return rs.getLong(1);
		}
	}
}
