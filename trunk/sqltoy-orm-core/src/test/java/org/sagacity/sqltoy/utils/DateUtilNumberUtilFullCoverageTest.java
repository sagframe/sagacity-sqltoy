package org.sagacity.sqltoy.utils;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.math.RoundingMode;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.time.ZonedDateTime;
import java.util.Locale;
import java.math.RoundingMode;

import org.junit.jupiter.api.Test;

/**
 * update 2026-9-14 全量覆盖补充:1、DateUtil未被既有测试引用的9个公开方法
 * (asDate/asTime/convertLocalDateTime/firstDayOfMonth/lastDayOfMonth/getDate/getDay/getTime/
 * processNano);2、本类Recent修改(线程内SDF缓存与DecimalFormat缓存)的命中路径回归——
 * 同线程多格式/locale/roundingMode交错调用,验证缓存命中结果与首次一致
 */
public class DateUtilNumberUtilFullCoverageTest {

	// ==================== DateUtil 未覆盖方法 ====================

	@Test
	public void firstAndLastDayOfMonth() {
		LocalDate first = DateUtil.asLocalDate(DateUtil.firstDayOfMonth("2026-09-14"));
		assertEquals(LocalDate.of(2026, 9, 1), first, "firstDayOfMonth");
		LocalDate last = DateUtil.asLocalDate(DateUtil.lastDayOfMonth("2026-09-14"));
		assertEquals(LocalDate.of(2026, 9, 30), last, "lastDayOfMonth(9月30天)");
		LocalDate leap = DateUtil.asLocalDate(DateUtil.lastDayOfMonth("2024-02-10"));
		assertEquals(LocalDate.of(2024, 2, 29), leap, "闰年2月29天");
		LocalDate nonLeap = DateUtil.asLocalDate(DateUtil.lastDayOfMonth("2026-02-10"));
		assertEquals(LocalDate.of(2026, 2, 28), nonLeap, "平年2月28天");
		assertNull(DateUtil.firstDayOfMonth(null), "null输入");
	}

	@Test
	public void getDateVariants() {
		LocalDate now = DateUtil.getDate();
		assertNotNull(now, "getDate()取当前日期");
		assertNull(DateUtil.getDate((Object) null), "null输入返回null");
		// 类型分支:LocalDate/LocalDateTime/ZonedDateTime/字符串
		LocalDate ld = LocalDate.of(2026, 1, 15);
		assertEquals(ld, DateUtil.getDate(ld), "LocalDate直返");
		assertEquals(ld, DateUtil.getDate(LocalDateTime.of(2026, 1, 15, 10, 30)), "LocalDateTime取日期部分");
		ZonedDateTime zdt = LocalDateTime.of(2026, 1, 15, 10, 30).atZone(java.time.ZoneId.systemDefault());
		assertEquals(ld, DateUtil.getDate(zdt), "ZonedDateTime取日期部分");
		assertEquals(ld, DateUtil.getDate("2026-01-15"), "字符串解析");
	}

	@Test
	public void getDayDelegatesToDayOfMonth() {
		assertEquals(14, DateUtil.getDay("2026-09-14"), "getDay(废弃)与getDayOfMonth同源");
		assertEquals(1, DateUtil.getDay("2026-09-01"), "月初");
	}

	@Test
	public void getTimeNow() {
		LocalTime before = LocalTime.now().minusMinutes(1);
		LocalTime now = DateUtil.getTime();
		LocalTime after = LocalTime.now().plusMinutes(1);
		assertTrue(!now.isBefore(before) && !now.isAfter(after), "getTime()取当前时刻");
	}

	@Test
	public void asDateFromLocalTime() {
		assertNull(DateUtil.asDate((LocalTime) null), "null输入");
		LocalTime time = LocalTime.of(10, 30, 0);
		java.util.Date date = DateUtil.asDate(time);
		// 语义:今天的日期部分+给定时刻
		assertEquals(time, DateUtil.asLocalDateTime(date).toLocalTime(), "时刻部分保留");
		assertEquals(LocalDate.now(), DateUtil.asLocalDateTime(date).toLocalDate(), "日期部分为当天");
	}

	@Test
	public void asTimeFromLocalTime() {
		assertNull(DateUtil.asTime(null), "null输入");
		LocalTime time = LocalTime.of(10, 30, 15);
		java.sql.Time sqlTime = DateUtil.asTime(time);
		assertEquals(time, sqlTime.toLocalTime(), "asTime往返");
	}

	@Test
	public void convertLocalDateTimeVariants() {
		assertNull(DateUtil.convertLocalDateTime(null), "null输入");
		LocalDateTime expected = LocalDateTime.of(2026, 1, 15, 10, 30, 0);
		assertEquals(expected, DateUtil.convertLocalDateTime("2026-01-15 10:30:00"), "字符串");
		assertEquals(LocalDateTime.of(2026, 1, 15, 0, 0), DateUtil.convertLocalDateTime("2026-01-15"), "纯日期串");
		assertEquals(expected, DateUtil.convertLocalDateTime(java.sql.Timestamp.valueOf("2026-01-15 10:30:00")),
				"Timestamp");
		assertEquals(LocalDateTime.of(2026, 1, 15, 0, 0), DateUtil.convertLocalDateTime(java.sql.Date.valueOf("2026-01-15")),
				"sqlDate");
	}

	@Test
	public void processNanoGranularity() {
		assertEquals("", DateUtil.processNano(0), "0纳秒到秒级");
		// 非零返回以'.'开头的秒小数形态(供时间串尾部拼接)
		assertEquals(".123456789", DateUtil.processNano(123456789), "纳秒全精度");
		assertEquals(".500", DateUtil.processNano(500000000), "整毫秒(后6位零)截取3位");
		assertEquals(".123456", DateUtil.processNano(123456000), "整微秒(后3位零)截取6位");
		assertEquals(".000000123", DateUtil.processNano(123), "左补零到9位");
	}

	// ==================== Recent修改的缓存命中路径回归 ====================

	/**
	 * DateUtil.getSdf线程内缓存:同线程多格式/多轮次交错调用,缓存命中结果与首次一致
	 * (覆盖parseString回退路径/parseLocalDateTime isDate分支/formatDate低精度路径/自动匹配循环)
	 */
	@Test
	public void sdfCacheHitConsistency() {
		LocalDateTime point = LocalDateTime.of(2026, 1, 15, 14, 30, 5);
		for (int round = 0; round < 3; round++) {
			// formatDate低精度路径(SimpleDateFormat format)
			assertEquals("15/01/2026", DateUtil.formatDate(point, "dd/MM/yyyy"), "round" + round + " format低精度");
			// parseString指定format回退路径(SimpleDateFormat parse,返回Date)
			assertEquals(point, DateUtil.asLocalDateTime(
					DateUtil.parseString("2026-01-15 14:30:05", "yyyy-MM-dd HH:mm:ss", null)), "round" + round + " parse指定格式");
			// parseLocalDateTime isDate分支(SimpleDateFormat parse date-only)
			assertEquals(LocalDateTime.of(2026, 1, 15, 0, 0), DateUtil.parseLocalDateTime("2026-01-15", "yyyy-MM-dd"),
					"round" + round + " isDate分支");
			// parseString自动匹配循环(DEFAULT_*_PATTERNS轮换,ENGLISH locale)
			assertEquals(point.toLocalDate().atStartOfDay(), DateUtil.parseLocalDateTime("2026-01-15").toLocalDate()
					.atStartOfDay(), "round" + round + " 自动匹配循环");
			// locale变体交错(GERMANY小数/分组符号不同,SDF符号表随locale不同)
			assertEquals("15.01.2026", DateUtil.formatDate(point.toLocalDate(), "dd.MM.yyyy"), "round" + round + " locale变体");
		}
	}

	/**
	 * NumberUtil.getDecimalFormat线程内缓存:pattern/roundingMode/locale/币种形态交错调用
	 * 缓存命中结果与首次一致,且roundingMode差异语义正确
	 */
	@Test
	public void decimalFormatCacheHitConsistency() {
		for (int round = 0; round < 3; round++) {
			assertEquals("1,234.57", NumberUtil.format(1234.567, "#,##0.00"), "round" + round + " 默认locale");
			assertEquals("1.234,57", NumberUtil.format(1234.567, "#,##0.00", RoundingMode.HALF_UP, Locale.GERMANY),
					"round" + round + " GERMANY分隔符");
			// roundingMode语义差异(相同格式串不同舍入→不同缓存条目)
			assertEquals("1235", NumberUtil.format(1234.5, "###0", RoundingMode.HALF_UP, Locale.US),
					"round" + round + " HALF_UP");
			assertEquals("1234", NumberUtil.format(1234.5, "###0", RoundingMode.DOWN, Locale.US),
					"round" + round + " DOWN");
			// currency实例形态
			assertTrue(NumberUtil.formatCurrency(1234.567, "#,##0.00", Locale.US).contains("1,234.57"),
					"round" + round + " currency实例");
		}
	}
}
