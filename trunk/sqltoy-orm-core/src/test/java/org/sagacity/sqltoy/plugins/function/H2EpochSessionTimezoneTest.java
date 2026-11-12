package org.sagacity.sqltoy.plugins.function;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.ResultSet;
import java.sql.Statement;
import java.util.Arrays;

import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

/**
 * update 2026-11-11 epoch族(H2)会话时区一致性回归(内存库,无需容器)
 *
 * <p>
 * H2 的 {@code EXTRACT(EPOCH FROM TIMESTAMP)} 对无时区类型恒按 UTC 解释;修复为显式转为
 * {@code TIMESTAMP WITH TIME ZONE},由数据库按会话命名时区和入参日期计算偏移,反向转换同理。
 * 带时区入参必须保持原绝对时刻,历史夏令时不能套用当前偏移。
 *
 * <p>
 * 断言口径:+08 会话下墙钟 2026-11-11 10:00:00 的 epoch=1794362400,UTC 会话下=1794391200;
 * America/New_York 夏季墙钟 2026-07-01 12:00:00 的 epoch=1782921600,且双向转换保持一致。
 */
public class H2EpochSessionTimezoneTest {

	private static final long EPOCH_08 = 1794362400L;
	private static final long EPOCH_UTC = 1794391200L;

	private static final long EPOCH_NY_SUMMER = 1782921600L;

	@BeforeAll
	public static void init() {
		FunctionUtils.setFunctionConverts(Arrays.asList("default"));
	}

	private static String convert(String sql) {
		return FunctionUtils.getDialectSql(sql, "h2");
	}

	private static Object one(Connection conn, String sql) throws Exception {
		try (Statement st = conn.createStatement(); ResultSet rs = st.executeQuery(sql)) {
			rs.next();
			return rs.getObject(1);
		}
	}

	@Test
	public void unixTimestampFollowsSessionTimezone() throws Exception {
		try (Connection conn = DriverManager.getConnection("jdbc:h2:mem:epochtz;DB_CLOSE_DELAY=-1", "sa", "");
				Statement st = conn.createStatement()) {
			// +08 会话:必须与 MySQL 会话时区语义一致(不是 UTC 值)
			st.execute("set time zone '+08:00'");
			assertEquals(EPOCH_08, ((Number) one(conn,
					convert("select unix_timestamp(timestamp '2026-11-11 10:00:00')"))).longValue(),
					"H2 +08会话的epoch应等于MySQL +08口径");
			// 带时区入参已是绝对时刻,不得再减一次会话偏移
			assertEquals(EPOCH_08, ((Number) one(conn,
					convert("select unix_timestamp(timestamp with time zone '2026-11-11 10:00:00+08')"))).longValue(),
					"H2带时区入参应保持原绝对时刻");
			// 半时区(+05:30)同样按分钟补偿:墙钟10:00在+05:30即UTC 04:30
			st.execute("set time zone '+05:30'");
			assertEquals(EPOCH_UTC - 5 * 3600 - 30 * 60, ((Number) one(conn,
					convert("select unix_timestamp(timestamp '2026-11-11 10:00:00')"))).longValue(),
					"H2 +05:30会话epoch应按分钟偏移");
			// 负半时区由数据库处理符号:墙钟10:00在-05:30即UTC 15:30
			st.execute("set time zone '-05:30'");
			assertEquals(EPOCH_UTC + 5 * 3600 + 30 * 60, ((Number) one(conn,
					convert("select unix_timestamp(timestamp '2026-11-11 10:00:00')"))).longValue(),
					"H2 -05:30会话epoch应正确处理负偏移");
			// UTC 会话:偏移0,与原行为一致
			st.execute("set time zone 'UTC'");
			assertEquals(EPOCH_UTC, ((Number) one(conn,
					convert("select unix_timestamp(timestamp '2026-11-11 10:00:00')"))).longValue(),
					"H2 UTC会话epoch");
			// 无参形态:带时区的current_timestamp,本身即绝对epoch,不应再补偿
			long nowDiff = Math.abs(
					((Number) one(conn, convert("select unix_timestamp()"))).longValue() - System.currentTimeMillis() / 1000);
			assertTrue(nowDiff <= 3, "无参形态应返回当前绝对epoch, 实际差秒=" + nowDiff);
		}
	}

	@Test
	public void fromUnixtimeRendersSessionWallClock() throws Exception {
		try (Connection conn = DriverManager.getConnection("jdbc:h2:mem:epochtz2;DB_CLOSE_DELAY=-1", "sa", "");
				Statement st = conn.createStatement()) {
			// EPOCH_08 = 2026-11-11T02:00Z:在+08会话呈现为10:00,在UTC会话呈现为02:00
			for (String[] tz : new String[][] { { "+08:00", "2026-11-11 10:00:00" }, { "UTC", "2026-11-11 02:00:00" } }) {
				st.execute("set time zone '" + tz[0] + "'");
				String sql = convert("select from_unixtime(" + EPOCH_08 + ")");
				String rendered = one(conn, "select to_char((" + sql.replaceFirst("(?s)^select\\s+", "")
						+ "),'yyyy-mm-dd hh24:mi:ss')").toString();
				assertEquals(tz[1], rendered, tz[0] + " 会话下 from_unixtime 应按会话时区呈现");
			}
		}
	}

	@Test
	public void epochUsesTargetDateDstRules() throws Exception {
		try (Connection conn = DriverManager.getConnection("jdbc:h2:mem:epochdst;DB_CLOSE_DELAY=-1", "sa", "");
				Statement st = conn.createStatement()) {
			st.execute("set time zone 'America/New_York'");
			// 2026-07-01纽约为夏令时UTC-04;当前日期为11月(UTC-05),不能套用当前偏移
			assertEquals(EPOCH_NY_SUMMER, ((Number) one(conn,
					convert("select unix_timestamp(timestamp '2026-07-01 12:00:00')"))).longValue(),
					"H2应按入参日期的DST规则计算epoch");
			String sql = convert("select from_unixtime(" + EPOCH_NY_SUMMER + ")");
			String rendered = one(conn, "select to_char((" + sql.replaceFirst("(?s)^select\\s+", "")
					+ "),'yyyy-mm-dd hh24:mi:ss')").toString();
			assertEquals("2026-07-01 12:00:00", rendered, "H2反向转换应按目标日期的DST规则呈现墙钟");
			assertEquals(EPOCH_NY_SUMMER,
					((Number) one(conn, convert("select unix_timestamp(from_unixtime(" + EPOCH_NY_SUMMER + "))")))
							.longValue(),
					"H2 epoch双向转换应保持绝对时刻不变");
		}
	}
}
