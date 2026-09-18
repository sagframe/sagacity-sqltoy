package org.sagacity.sqltoy.utils;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.Date;

import org.junit.jupiter.api.Test;

/**
 * 回归测试：(H9)DateUtil非法日期输入给出带输入值的IllegalArgumentException而非裸NPE
 * (addMilliSecond/getYear/getMonth/getDayOfMonth/getDayOfWeek/getInterval系列);
 * (#42)SqlScriptLoader解析失败后initialized未置位,重试不被静默跳过
 */
public class DateUtilAndScriptLoaderTest {

	private static final String BAD_DATE = "not-a-date";

	@Test
	public void addMilliSecondBadDateGivesClearError() {
		Throwable cause = assertThrows(IllegalArgumentException.class,
				() -> DateUtil.addMilliSecond(BAD_DATE, 1000L));
		assertTrue(cause.getMessage().contains(BAD_DATE), "实际:" + cause.getMessage());
	}

	@Test
	public void datePartMethodsBadDateGiveClearError() {
		assertTrue(assertThrows(IllegalArgumentException.class, () -> DateUtil.getYear(BAD_DATE)).getMessage()
				.contains(BAD_DATE));
		assertTrue(assertThrows(IllegalArgumentException.class, () -> DateUtil.getMonth(BAD_DATE)).getMessage()
				.contains(BAD_DATE));
		assertTrue(assertThrows(IllegalArgumentException.class, () -> DateUtil.getDayOfMonth(BAD_DATE)).getMessage()
				.contains(BAD_DATE));
		assertTrue(assertThrows(IllegalArgumentException.class, () -> DateUtil.getDayOfWeek(BAD_DATE)).getMessage()
				.contains(BAD_DATE));
	}

	@Test
	public void intervalMethodsBadDateGiveClearError() {
		Date now = new Date();
		assertTrue(assertThrows(IllegalArgumentException.class, () -> DateUtil.getIntervalMonths(BAD_DATE, now))
				.getMessage().contains(BAD_DATE));
		assertTrue(assertThrows(IllegalArgumentException.class, () -> DateUtil.getIntervalYears(now, BAD_DATE))
				.getMessage().contains(BAD_DATE));
		assertTrue(assertThrows(IllegalArgumentException.class, () -> DateUtil.getIntervalMillSeconds(BAD_DATE, now))
				.getMessage().contains(BAD_DATE));
	}

	@Test
	public void normalDateValuesUnchanged() {
		assertEquals(2025, DateUtil.getYear("2025-06-15"));
		assertEquals(6, DateUtil.getMonth("2025-06-15"));
		assertEquals(15, DateUtil.getDayOfMonth("2025-06-15"));
		assertEquals(0, DateUtil.getIntervalMonths("2025-01-01", "2025-01-31"));
		assertEquals(1, DateUtil.getIntervalYears("2024-01-01", "2025-01-01"));
		Date shifted = assertDoesNotThrow(() -> DateUtil.addMilliSecond("2025-06-15", 0L));
		assertEquals(2025, DateUtil.getYear(shifted));
		// null输入维持既有兜底语义(返回当前日期部件)
		assertEquals(java.time.LocalDate.now().getYear(), DateUtil.getYear(null));
	}

	@Test
	public void scriptLoaderFailureAllowsRetry() throws Exception {
		// 构造一个格式非法的sql.xml,让解析过程真实失败
		java.nio.file.Path dir = java.nio.file.Files.createTempDirectory("sqltoy_bad_sql");
		java.nio.file.Path badXml = dir.resolve("bad.sql.xml");
		java.nio.file.Files.write(badXml, "<sql-config><sql id=\\\"x\\\"><![CDATA[select 1".getBytes("UTF-8"));
		org.sagacity.sqltoy.config.SqlScriptLoader loader = new org.sagacity.sqltoy.config.SqlScriptLoader();
		loader.setSqlResourcesDir(dir.toString());
		try {
			loader.initialize(false, -1, -1, false);
		} catch (Throwable expected) {
			// 非法xml预期解析失败
		} finally {
			java.nio.file.Files.deleteIfExists(badXml);
			java.nio.file.Files.deleteIfExists(dir);
		}
		// 反射验证initialized未因失败被置位(修复前在try之前置位恒为true,重试被静默跳过)
		java.lang.reflect.Field field = org.sagacity.sqltoy.config.SqlScriptLoader.class
				.getDeclaredField("initialized");
		field.setAccessible(true);
		assertEquals(Boolean.FALSE, field.get(loader), "解析失败后initialized应保持false允许重试");
	}
}
