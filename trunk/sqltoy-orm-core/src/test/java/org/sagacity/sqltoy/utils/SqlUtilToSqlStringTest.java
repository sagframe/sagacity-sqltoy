package org.sagacity.sqltoy.utils;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.math.BigDecimal;
import java.sql.Time;
import java.sql.Timestamp;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.util.Arrays;
import java.util.List;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

/**
 * toSqlString渲染矩阵:null/字符串/数值/布尔/日期时间各族×是否加单引号×数组集合递归。
 * 契约:null恒返回"null";字符与日期时间按需加引号;数值布尔恒裸;数组集合递归逗号拼接。
 */
public class SqlUtilToSqlStringTest {

	private String savedLdtFormat;
	private String savedLtFormat;

	@BeforeEach
	public void setUp() {
		// 全量套件下其它用例可能配置了固定格式(静态全局),本类纳秒断言依赖auto语义,显式归零并恢复
		savedLdtFormat = org.sagacity.sqltoy.SqlToyConstants.localDateTimeFormat;
		savedLtFormat = org.sagacity.sqltoy.SqlToyConstants.localTimeFormat;
		org.sagacity.sqltoy.SqlToyConstants.localDateTimeFormat = null;
		org.sagacity.sqltoy.SqlToyConstants.localTimeFormat = null;
	}

	@AfterEach
	public void tearDown() {
		org.sagacity.sqltoy.SqlToyConstants.localDateTimeFormat = savedLdtFormat;
		org.sagacity.sqltoy.SqlToyConstants.localTimeFormat = savedLtFormat;
	}

	@Test
	public void nullAlwaysLiteralNull() {
		assertEquals("null", SqlUtil.toSqlString(null, true));
		assertEquals("null", SqlUtil.toSqlString(null, false));
	}

	@Test
	public void stringQuotingByFlag() {
		assertEquals("'abc'", SqlUtil.toSqlString("abc", true));
		assertEquals("abc", SqlUtil.toSqlString("abc", false));
		assertEquals("''", SqlUtil.toSqlString("", true));
	}

	@Test
	public void numberAndBooleanAlwaysRaw() {
		assertEquals("123", SqlUtil.toSqlString(123, true), "整数即使要求引号也裸输出");
		assertEquals("123.45", SqlUtil.toSqlString(new BigDecimal("123.45"), false));
		assertEquals("true", SqlUtil.toSqlString(Boolean.TRUE, true), "布尔即使要求引号也裸输出");
	}

	@Test
	public void timestampFormat() {
		Timestamp ts = Timestamp.valueOf("2026-01-15 10:30:00");
		assertEquals("'2026-01-15 10:30:00.000'", SqlUtil.toSqlString(ts, true));
		assertEquals("2026-01-15 10:30:00.000", SqlUtil.toSqlString(ts, false));
	}

	@Test
	public void localDateTimeWithoutNano() {
		LocalDateTime dt = LocalDateTime.of(2026, 1, 15, 10, 30, 0);
		assertEquals("'2026-01-15 10:30:00'", SqlUtil.toSqlString(dt, true));
		assertEquals("2026-01-15 10:30:00", SqlUtil.toSqlString(dt, false));
	}

	@Test
	public void localDateTimeWithNanoHasFraction() {
		LocalDateTime dt = LocalDateTime.of(2026, 1, 15, 10, 30, 0, 500_000_000);
		String rendered = SqlUtil.toSqlString(dt, false);
		assertTrue(rendered.startsWith("2026-01-15 10:30:00.5"), "含纳秒应带小数: " + rendered);
	}

	@Test
	public void localDateFormat() {
		LocalDate d = LocalDate.of(2026, 1, 15);
		assertEquals("'2026-01-15'", SqlUtil.toSqlString(d, true));
		assertEquals("2026-01-15", SqlUtil.toSqlString(d, false));
	}

	@Test
	public void localTimeAndSqlTimeFormat() {
		LocalTime t = LocalTime.of(10, 30, 0);
		assertEquals("'10:30:00'", SqlUtil.toSqlString(t, true));
		Time st = Time.valueOf("08:15:00");
		assertEquals("'08:15:00'", SqlUtil.toSqlString(st, true));
	}

	@Test
	public void utilDateAsDatetime() {
		// 纯java.util.Date(非Timestamp子类实例)走yyyy-MM-dd HH:mm:ss形态
		java.util.Date d = new java.util.Date(java.sql.Timestamp.valueOf("2026-01-15 10:30:00").getTime());
		assertEquals("'2026-01-15 10:30:00'", SqlUtil.toSqlString(d, true));
	}

	@Test
	public void arrayAndCollectionCommaSeparated() {
		// update 2026-9-15 数组/集合为in列表素材,字符串元素恒加引号(flag不影响,引号由
		// combineArray按元素类型决定),数值元素恒裸
		Object[] ary = new Object[] { 1, 2, 3 };
		assertEquals("1,2,3", SqlUtil.toSqlString(ary, false), "数组数值裸拼接");
		List<String> list = Arrays.asList("a", "b");
		assertEquals("'a','b'", SqlUtil.toSqlString(list, true), "集合字符串加引号拼接");
		assertEquals("'a','b'", SqlUtil.toSqlString(list, false), "集合字符串引号与flag无关");
	}
}
