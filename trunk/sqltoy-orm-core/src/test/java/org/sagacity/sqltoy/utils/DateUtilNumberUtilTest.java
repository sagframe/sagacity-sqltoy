package org.sagacity.sqltoy.utils;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.DayOfWeek;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.temporal.WeekFields;
import java.util.HashSet;
import java.util.Set;

import org.junit.jupiter.api.Test;

/**
 * DateUtil/NumberUtil 双项目同步改动的多场景回归测试
 */
public class DateUtilNumberUtilTest {

	private static String fmt(Object date, String format) {
		return DateUtil.formatDate(date, format);
	}

	// ==================== DateUtil：英文日期解析 ====================

	@Test
	public void englishDateBasic() {
		assertEquals("2024-01-05", fmt(DateUtil.parseString("Jan 5 2024"), "yyyy-MM-dd"));
		assertEquals("2024-01-05", fmt(DateUtil.parseString("January 5, 2024"), "yyyy-MM-dd"));
		assertEquals("2024-01-05", fmt(DateUtil.parseString("5 January 2024"), "yyyy-MM-dd"));
		assertEquals("2024-01-05", fmt(DateUtil.parseString("Monday, January 5, 2024"), "yyyy-MM-dd"));
		assertEquals("2024-01-05", fmt(DateUtil.parseString("Mon Jan 5 2024"), "yyyy-MM-dd"));
		assertEquals("2024-09-05", fmt(DateUtil.parseString("Sept 5 2024"), "yyyy-MM-dd"));
		assertEquals("2024-09-05", fmt(DateUtil.parseString("September 5 2024"), "yyyy-MM-dd"));
	}

	@Test
	public void englishDateThursAndTues() {
		assertEquals("2024-01-05", fmt(DateUtil.parseString("Thurs, Jan 5 2024"), "yyyy-MM-dd"));
		assertEquals("2024-01-05", fmt(DateUtil.parseString("Thur, Jan 5 2024"), "yyyy-MM-dd"));
		assertEquals("2024-01-05", fmt(DateUtil.parseString("Thursday Jan 5 2024"), "yyyy-MM-dd"));
		assertEquals("2024-01-05", fmt(DateUtil.parseString("Tues, Jan 5 2024"), "yyyy-MM-dd"));
		assertEquals("2024-01-05", fmt(DateUtil.parseString("Tuesday, Jan 5 2024"), "yyyy-MM-dd"));
	}

	@Test
	public void englishDateOrdinalSuffix() {
		assertEquals("2024-01-02", fmt(DateUtil.parseString("January 2nd 2024"), "yyyy-MM-dd"));
		assertEquals("2024-02-03", fmt(DateUtil.parseString("Feb 3rd 2024"), "yyyy-MM-dd"));
		assertEquals("2024-01-01", fmt(DateUtil.parseString("Jan 1st 2024"), "yyyy-MM-dd"));
		assertEquals("2024-01-21", fmt(DateUtil.parseString("Jan 21st 2024"), "yyyy-MM-dd"));
		assertEquals("2024-01-03", fmt(DateUtil.parseString("3rd Jan 2024"), "yyyy-MM-dd"));
		assertEquals("2024-01-05", fmt(DateUtil.parseString("5th January 2024"), "yyyy-MM-dd"));
	}

	@Test
	public void englishDateWithTime() {
		assertEquals("2024-01-05 10:30:00", fmt(DateUtil.parseString("Jan 5 2024 10:30:00"), "yyyy-MM-dd HH:mm:ss"));
		// 逗号替换成空格后产生双空格，依赖英文分支空白压缩
		assertEquals("2024-01-05 10:30:00",
				fmt(DateUtil.parseString("Monday, January 5, 2024 10:30:00"), "yyyy-MM-dd HH:mm:ss"));
		// 星期+日期+时间且无时区(时区被removeZoneInfo剥离后的形态)
		assertEquals("2024-01-05 10:30:00",
				fmt(DateUtil.parseString("Mon Jan 5 2024 10:30:00"), "yyyy-MM-dd HH:mm:ss"));
		assertEquals("2024-01-05 10:30:00",
				fmt(DateUtil.parseString("Mon Jan 5 2024 10:30:00 +08:00"), "yyyy-MM-dd HH:mm:ss"));
		// EEEEE dd-MMM-yyyy 横杠格式
		assertEquals("2024-01-05 10:30:00",
				fmt(DateUtil.parseString("Thursday 05-Jan-2024 10:30:00"), "yyyy-MM-dd HH:mm:ss"));
	}

	@Test
	public void englishDateInvalid() {
		assertNull(DateUtil.parseString("NotARealDate"));
		assertNull(DateUtil.parseString("NotARealDate", "yyyy-MM-dd", null));
	}

	@Test
	public void englishDateLocalDateTime() {
		assertEquals("2024-01-05 10:30:00", fmt(DateUtil.parseLocalDateTime("Jan 5 2024 10:30:00"), "yyyy-MM-dd HH:mm:ss"));
	}

	// ==================== DateUtil：中文日期 ====================

	@Test
	public void chineseDateConvert() {
		assertEquals("1949-10-1", DateUtil.parseChinaDate("一九四九年十月一日"));
		assertEquals("2025-12-31", DateUtil.parseChinaDate("二〇二五年十二月三十一日"));
		assertEquals("2024-8-18", DateUtil.parseChinaDate("二零二四年八月十八日"));
		assertEquals("10-21", DateUtil.parseChinaDate("十月二十一日"));
		assertEquals("15", DateUtil.parseChinaDate("十五日"));
		assertEquals("20", DateUtil.parseChinaDate("二十日"));
		assertEquals("30", DateUtil.parseChinaDate("三十日"));
		// 六十~九十整十形式(前缀支持一~九)
		assertEquals("60", DateUtil.parseChinaDate("六十日"));
		assertEquals("90", DateUtil.parseChinaDate("九十日"));
		assertEquals("10", DateUtil.parseChinaDate("十"));
		assertEquals("59", DateUtil.parseChinaDate("五十九分"));
		assertEquals("23:59", DateUtil.parseChinaDate("二十三时五十九分"));
	}

	@Test
	public void chineseDateFullParse() {
		assertEquals("2024-01-05 10:30", fmt(DateUtil.parseString("二零二四年一月五日 十时三十分"), "yyyy-MM-dd HH:mm"));
		assertEquals("2024-01-05", fmt(DateUtil.parseString("二〇二四年一月五日"), "yyyy-MM-dd"));
		assertNull(DateUtil.parseString("你好"));
	}

	// ==================== DateUtil：format2China 英文防误判 ====================

	@Test
	public void format2ChinaGuard() {
		assertEquals("2024年1月5日", DateUtil.format2China("Jan 5, 2024"));
		assertEquals("2024年1月1日12时30分45秒", DateUtil.format2China("20240101123045"));
		assertEquals("2024年1月1日12时30分45秒", DateUtil.format2China("2024-01-01 12:30:45"));
		assertEquals("2024年1月5日10时30分0秒", DateUtil.format2China("Jan 5, 2024 10:30:00"));
		assertEquals("2024年1月5日10时30分0秒", DateUtil.format2China(LocalDateTime.of(2024, 1, 5, 10, 30, 0)));
		// 粒度按输入形态判断：中文按年月日时分秒字出现，数字按日期段位数/分隔组数
		assertEquals("2024年", DateUtil.format2China("2024"));
		assertEquals("2024年1月", DateUtil.format2China("2024-01"));
		assertEquals("2024年1月5日", DateUtil.format2China("2024/1/5"));
		// 中文长度不等于数字位数，按单位字判断
		assertEquals("2024年12月", DateUtil.format2China("二零二四年十二月"));
		assertEquals("2024年12月5日", DateUtil.format2China("二零二四年十二月五日"));
		// 纯时间输入不再编造"1970年1月1日"
		assertEquals("12时30分45秒", DateUtil.format2China("12:30:45"));
	}

	// ==================== DateUtil：时间奇数位补零 ====================

	@Test
	public void oddDigitTimePadding() {
		assertEquals("2024-01-05 03:04", fmt(DateUtil.parseString("20240105 304"), "yyyy-MM-dd HH:mm"));
		assertEquals("2024-01-05 03:00", fmt(DateUtil.parseString("20240105 3"), "yyyy-MM-dd HH:mm"));
		assertEquals("2024-01-05 03:04:05", fmt(DateUtil.parseString("2024-01-05 3:04:05"), "yyyy-MM-dd HH:mm:ss"));
		assertEquals("2024-01-05 03:04", fmt(DateUtil.parseLocalDateTime("20240105 304"), "yyyy-MM-dd HH:mm"));
		// 偶数位时间不受影响
		assertEquals("2024-01-05 13:04", fmt(DateUtil.parseString("2024-01-05 13:04"), "yyyy-MM-dd HH:mm"));
	}

	@Test
	public void fractionalSecondParse() {
		// 含小数秒的时间部分奇数位来自小数部分，不能按单位数小时补零(否则破坏高精度格式匹配)；
		// parseString返回java.util.Date仅毫秒精度，纳秒级场景用parseLocalDateTime验证
		assertEquals("2020-11-20 21:34:22.234",
				fmt(DateUtil.parseString("2020-11-20 21:34:22.234345876"), "yyyy-MM-dd HH:mm:ss.SSS"));
		assertEquals("2020-11-20 21:34:22.234",
				fmt(DateUtil.parseString("2020-11-20 21:34:22.234"), "yyyy-MM-dd HH:mm:ss.SSS"));
		assertEquals("2020-11-20 21:34:22.2",
				fmt(DateUtil.parseString("2020-11-20 21:34:22.2"), "yyyy-MM-dd HH:mm:ss.S"));
		assertEquals("2023-11-21 12:30:30.123345321",
				fmt(DateUtil.parseLocalDateTime("2023-11-21 12:30:30.123345321"), "yyyy-MM-dd HH:mm:ss.SSSSSSSSS"));
		assertEquals("2023-11-21 12:30:30.1",
				fmt(DateUtil.parseLocalDateTime("2023-11-21 12:30:30.1"), "yyyy-MM-dd HH:mm:ss.S"));
		assertEquals("2023-11-21 12:30:30.123345321",
				fmt(DateUtil.parseLocalDateTime("20231121 123030.123345321"), "yyyy-MM-dd HH:mm:ss.SSSSSSSSS"));
		assertEquals("21:34:22.234345", fmt(DateUtil.parseLocalDateTime("21:34:22.234345"), "HH:mm:ss.SSSSSS"));
		assertEquals("2023-11-21 12:30:30.123",
				fmt(DateUtil.parseString("20231121 123030.123"), "yyyy-MM-dd HH:mm:ss.SSS"));
	}

	// ==================== DateUtil：常规解析回归 ====================

	@Test
	public void generalParse() {
		assertEquals("2024-01-05", fmt(DateUtil.parseString("2024-01-05"), "yyyy-MM-dd"));
		assertEquals("2024-01-05", fmt(DateUtil.parseString("2024/01/05"), "yyyy-MM-dd"));
		assertEquals("2024-01-05", fmt(DateUtil.parseString("2024.01.05"), "yyyy-MM-dd"));
		assertEquals("2024-01-05", fmt(DateUtil.parseString("20240105"), "yyyy-MM-dd"));
		assertEquals("2024-01-05 10:30:00", fmt(DateUtil.parseString("2024-01-05T10:30:00"), "yyyy-MM-dd HH:mm:ss"));
		assertEquals("12:30:45", fmt(DateUtil.parseString("12:30:45"), "HH:mm:ss"));
		assertEquals(1704440400000L, DateUtil.parseString("1704440400000").getTime());
		assertNull(DateUtil.parseString(null));
		assertNull(DateUtil.parseString(""));
		assertNull(DateUtil.parseString("null"));
	}

	@Test
	public void parseWithFormatAndFallback() {
		assertEquals("2024-01-05", fmt(DateUtil.parseString("2024-01-05", "yyyy-MM-dd", null), "yyyy-MM-dd"));
		// 指定format解析失败后自动格式补偿
		assertEquals("2024-01-05", fmt(DateUtil.parseString("Jan 5 2024", "yyyy-MM-dd", null), "yyyy-MM-dd"));
		assertEquals("2024-01-05 00:00:00",
				fmt(DateUtil.parseLocalDateTime("Jan 5 2024", "yyyy-MM-dd"), "yyyy-MM-dd HH:mm:ss"));
		assertNull(DateUtil.parseString("abc", "yyyy-MM-dd", null));
	}

	// ==================== DateUtil：周数 ====================

	@Test
	public void weekOfYear() {
		assertEquals(1, DateUtil.getWeekOfYear("2024-01-01"));
		assertEquals(1, DateUtil.getWeekOfYear("2024-01-07"));
		assertEquals(2, DateUtil.getWeekOfYear("2024-01-08"));
		assertEquals(1, DateUtil.getWeekOfYear("2025-01-01"));
		assertEquals(53, DateUtil.getWeekOfYear("2024-12-31"));
		assertEquals(LocalDate.now().get(WeekFields.of(DayOfWeek.MONDAY, 1).weekOfYear()),
				DateUtil.getWeekOfYear(null));
		assertEquals(LocalDate.of(2024, 6, 15).get(WeekFields.of(DayOfWeek.MONDAY, 1).weekOfYear()),
				DateUtil.getWeekOfYear("2024-06-15"));
	}

	// ==================== DateUtil：取值与判空守卫 ====================

	@Test
	public void gettersAndGuards() {
		assertEquals(2024, DateUtil.getYear("2024-05-06"));
		assertEquals(5, DateUtil.getMonth("2024-05-06"));
		assertEquals(6, DateUtil.getDayOfMonth("2024-05-06"));
		assertEquals(5, DateUtil.getDayOfWeek("2024-01-05"));
		assertEquals(LocalDate.now().getYear(), DateUtil.getYear(null));
		assertThrows(IllegalArgumentException.class, () -> DateUtil.getYear("bad date"));
		assertThrows(IllegalArgumentException.class, () -> DateUtil.getMonth("bad date"));
		assertThrows(IllegalArgumentException.class, () -> DateUtil.getDayOfMonth("bad date"));
		assertThrows(IllegalArgumentException.class, () -> DateUtil.getDayOfWeek("bad date"));
		assertThrows(IllegalArgumentException.class, () -> DateUtil.addMilliSecond("bad date", 100));
	}

	@Test
	public void dateAdd() {
		assertEquals("2024-02-01", fmt(DateUtil.addDay("2024-01-31", 1), "yyyy-MM-dd"));
		// 闰年月末加一月收敛到2月29日
		assertEquals("2024-02-29", fmt(DateUtil.addMonth("2024-01-31", 1), "yyyy-MM-dd"));
		assertEquals("2025-01-31", fmt(DateUtil.addYear("2024-01-31", 1), "yyyy-MM-dd"));
		assertEquals("2024-01-05 11:30", fmt(DateUtil.addHour("2024-01-05 10:00:00", 1.5), "yyyy-MM-dd HH:mm"));
		assertEquals("2024-01-05 10:02", fmt(DateUtil.addMinute("2024-01-05 10:00:00", 2), "yyyy-MM-dd HH:mm"));
		assertEquals("2024-01-05 10:00:30", fmt(DateUtil.addSecond("2024-01-05 10:00:00", 30), "yyyy-MM-dd HH:mm:ss"));
	}

	// ==================== DateUtil：间隔计算与守卫 ====================

	@Test
	public void intervals() {
		assertEquals(30, DateUtil.getIntervalDays("2024-01-01", "2024-01-31"));
		assertEquals(2, DateUtil.getIntervalDays("2024-02-28", "2024-03-01"));
		assertEquals(-30, DateUtil.getIntervalDays("2024-01-31", "2024-01-01"));
		assertEquals(0, DateUtil.getIntervalDays("bad", "bad"));
		assertEquals(2, DateUtil.getIntervalMonths("2024-01-15", "2024-03-10"));
		assertEquals(2, DateUtil.getIntervalYears("2023-05-01", "2025-01-01"));
		assertEquals(5.5, DateUtil.getIntervalHours("2024-01-01 00:00:00", "2024-01-01 05:30:00"));
		assertEquals(90.0, DateUtil.getIntervalMinutes("2024-01-01 00:00:00", "2024-01-01 01:30:00"));
		assertEquals(5400.0, DateUtil.getIntervalSeconds("2024-01-01 00:00:00", "2024-01-01 01:30:00"));
		assertEquals(5400000L, DateUtil.getIntervalMillSeconds("2024-01-01 00:00:00", "2024-01-01 01:30:00"));
		assertEquals(2.0, DateUtil.getIntervalWeeks("2024-01-01", "2024-01-15"));
		assertThrows(IllegalArgumentException.class, () -> DateUtil.getIntervalYears("bad", "2024-01-01"));
		assertThrows(IllegalArgumentException.class, () -> DateUtil.getIntervalMonths("bad", "2024-01-01"));
		assertThrows(IllegalArgumentException.class,
				() -> DateUtil.getIntervalMillSeconds("bad", "2024-01-01"));
	}

	// ==================== DateUtil：时区解析 ====================

	@Test
	public void zonedParse() {
		assertEquals(LocalDateTime.of(2024, 1, 5, 10, 30, 0),
				DateUtil.parseZonedDateTime("2024-01-05 10:30:00 +08:00").toLocalDateTime());
		assertEquals(8 * 3600, DateUtil.parseZonedDateTime("2024-01-05 10:30:00 +08:00").getOffset().getTotalSeconds());
		assertEquals("Asia/Shanghai",
				DateUtil.parseZonedDateTime("2024-01-05 10:30:00 +08:00[Asia/Shanghai]").getZone().getId());
		// 基础时间无法解析时返回null而非NPE(纯[zoneId]形式无offset信息不支持)
		assertNull(DateUtil.parseZonedDateTime("abc +08:00"));
		assertNull(DateUtil.parseZonedDateTime("2024-01-05 12:30:00[Asia/Shanghai]"));
	}

	// ==================== DateUtil：单位数月日的分隔符补零 ====================

	@Test
	public void singleDigitMonthDayWithSeparators() {
		assertEquals("2024-01-05 12:30:45", fmt(DateUtil.parseString("2024-1-5 12:30:45"), "yyyy-MM-dd HH:mm:ss"));
		assertEquals("2024-01-05 12:30:45", fmt(DateUtil.parseString("2024/1/5 12:30:45"), "yyyy-MM-dd HH:mm:ss"));
		assertEquals("2024-01-05 12:30:45", fmt(DateUtil.parseString("2024.1.5 12:30:45"), "yyyy-MM-dd HH:mm:ss"));
		assertEquals("2024-01-05", fmt(DateUtil.parseString("2024/1/5"), "yyyy-MM-dd"));
		assertEquals("2024-01-05 03:04", fmt(DateUtil.parseString("2024/1/5 3:04"), "yyyy-MM-dd HH:mm"));
		assertEquals("2024-01-05 03:04", fmt(DateUtil.parseString("2024.1.5 3:04"), "yyyy-MM-dd HH:mm"));
	}

	@Test
	public void asSqlDateTest() {
		assertNull(DateUtil.asSqlDate(null));
		java.sql.Date sqlDate = DateUtil.asSqlDate(LocalDate.of(2024, 1, 5));
		assertEquals("2024-01-05", sqlDate.toString());
		assertEquals(LocalDate.of(2024, 1, 5), sqlDate.toLocalDate());
	}

	// ==================== NumberUtil：数字转中文 ====================

	@Test
	public void numberToChina() {
		assertEquals("五", NumberUtil.format("5", "capital"));
		// 0按规范读"零"(此前返回空串，导致report中capital列0值显示为空白)
		assertEquals("零", NumberUtil.format("0", "capital"));
		assertEquals("零", NumberUtil.numberToChina(0));
		// GB/T 15835：普通数字最高位为十位时"十"前不加"一"，中间位置保留；金额另按人行规定保留"壹拾"
		assertEquals("十", NumberUtil.format("10", "capital"));
		assertEquals("十一", NumberUtil.format("11", "capital"));
		assertEquals("十五", NumberUtil.format("15", "capital"));
		assertEquals("十九", NumberUtil.format("19", "capital"));
		assertEquals("二十", NumberUtil.format("20", "capital"));
		assertEquals("二十一", NumberUtil.format("21", "capital"));
		assertEquals("一百", NumberUtil.format("100", "capital"));
		assertEquals("一百零五", NumberUtil.format("105", "capital"));
		// 十位处于中间位置保留"一十"
		assertEquals("一百一十", NumberUtil.format("110", "capital"));
		assertEquals("一千零一十", NumberUtil.format("1010", "capital"));
		assertEquals("一千一百", NumberUtil.format("1100", "capital"));
		assertEquals("一万", NumberUtil.format("10000", "capital"));
		assertEquals("一万零一", NumberUtil.format("10001", "capital"));
		assertEquals("十万", NumberUtil.format("100000", "capital"));
		assertEquals("十一万", NumberUtil.format("110000", "capital"));
		assertEquals("二万零五十", NumberUtil.format("20050", "capital"));
		assertEquals("一亿", NumberUtil.format("100000000", "capital"));
		assertEquals("一亿二千三百四十五万零六百七十八", NumberUtil.format("123450678", "capital"));
		assertEquals("一百亿零二千万零三千", NumberUtil.format("10020003000", "capital"));
		// 复合单位：万亿、兆、万兆、十万兆
		assertEquals("一万亿", NumberUtil.format("1000000000000", "capital"));
		assertEquals("一兆", NumberUtil.format("10000000000000000", "capital"));
		assertEquals("一万兆", NumberUtil.format("100000000000000000000", "capital"));
		assertEquals("十万兆", NumberUtil.format("1000000000000000000000", "capital"));
		assertEquals("负一百二十三", NumberUtil.numberToChina(-123));
		assertEquals("负十五", NumberUtil.numberToChina(-15));
	}

	// ==================== NumberUtil：大写金额 ====================

	@Test
	public void toCapitalMoney() {
		// 人行《正确填写票据和结算凭证的基本规定》：到"元"为止应写"整"；不足一元直接从角分写起
		assertEquals("零元整", NumberUtil.toCapitalMoney(new BigDecimal("0")));
		assertEquals("壹拾元整", NumberUtil.toCapitalMoney(new BigDecimal("10")));
		assertEquals("壹佰元整", NumberUtil.toCapitalMoney(new BigDecimal("100")));
		assertEquals("壹佰零壹元整", NumberUtil.toCapitalMoney(new BigDecimal("101")));
		assertEquals("壹佰壹拾元整", NumberUtil.toCapitalMoney(new BigDecimal("110")));
		// 无整数部分零头不带前导零(人行规定："零"仅用于数字中间补位)
		assertEquals("伍分", NumberUtil.toCapitalMoney(new BigDecimal("0.05")));
		assertEquals("伍角陆分", NumberUtil.toCapitalMoney(new BigDecimal("0.56")));
		assertEquals("壹角伍分", NumberUtil.toCapitalMoney(new BigDecimal("0.15")));
		assertEquals("壹角零伍厘", NumberUtil.toCapitalMoney(new BigDecimal("0.105")));
		assertEquals("伍厘", NumberUtil.toCapitalMoney(new BigDecimal("0.005")));
		// 有整数部分时角分之间补零
		assertEquals("壹佰元零伍分", NumberUtil.toCapitalMoney(new BigDecimal("100.05")));
		assertEquals("壹佰元伍角", NumberUtil.toCapitalMoney(new BigDecimal("100.50")));
		assertEquals("壹佰贰拾叁元肆角伍分", NumberUtil.toCapitalMoney(new BigDecimal("123.45")));
		assertEquals("负伍分", NumberUtil.toCapitalMoney(new BigDecimal("-0.05")));
		assertEquals("负壹佰贰拾叁元肆角伍分", NumberUtil.toCapitalMoney(new BigDecimal("-123.45")));
		// 角位是0分位非0时"元"后应写"零"(人行规定示例￥325.04)
		assertEquals("壹仟陆佰肆拾元零贰分", NumberUtil.toCapitalMoney(new BigDecimal("1640.02")));
		// 万位是0千位非0时"零"可写可不写(人行规定示例￥107000.53)
		assertEquals("壹拾万零柒仟元伍角叁分", NumberUtil.toCapitalMoney(new BigDecimal("107000.53")));
		// 角位非0无需补零
		assertEquals("壹仟陆佰捌拾元叁角贰分", NumberUtil.toCapitalMoney(new BigDecimal("1680.32")));
		assertEquals("壹万亿元整", NumberUtil.toCapitalMoney(new BigDecimal("1000000000000")));
		assertEquals("壹仟贰佰叁拾肆亿伍仟陆佰柒拾捌万玖仟零壹拾贰元叁角肆分",
				NumberUtil.toCapitalMoney(new BigDecimal("123456789012.34")));
	}

	// ==================== NumberUtil：大写金额转数字 ====================

	@Test
	public void capitalMoneyToNum() {
		assertEquals(0, NumberUtil.capitalMoneyToNum("零元").compareTo(new BigDecimal("0")));
		assertEquals(0, NumberUtil.capitalMoneyToNum("壹拾元整").compareTo(new BigDecimal("10")));
		assertEquals(0, NumberUtil.capitalMoneyToNum("拾元整").compareTo(new BigDecimal("10")));
		assertEquals(0, NumberUtil.capitalMoneyToNum("叁仟").compareTo(new BigDecimal("3000")));
		assertEquals(0, NumberUtil.capitalMoneyToNum("负壹拾元贰角叁分").compareTo(new BigDecimal("-10.23")));
		// 不含"元"的输入
		assertEquals(0, NumberUtil.capitalMoneyToNum("壹佰万").compareTo(new BigDecimal("1000000")));
		assertEquals(0, NumberUtil.capitalMoneyToNum("玖角捌分").compareTo(new BigDecimal("0.98")));
		assertEquals(0, NumberUtil.capitalMoneyToNum("壹亿元整").compareTo(new BigDecimal("100000000")));
		assertEquals(0, NumberUtil.capitalMoneyToNum("壹亿贰仟叁佰肆拾伍万零陆佰柒拾捌元玖角")
				.compareTo(new BigDecimal("123450678.9")));
	}

	@Test
	public void capitalMoneyRoundTrip() {
		String[] values = { "0.05", "10", "123.45", "1000000", "123450678.9" };
		for (String v : values) {
			BigDecimal money = new BigDecimal(v);
			assertEquals(0, NumberUtil.capitalMoneyToNum(NumberUtil.toCapitalMoney(money)).compareTo(money),
					"round-trip失败:" + v);
		}
	}

	// ==================== NumberUtil：英文金额 ====================

	@Test
	public void englishMoney() {
		assertEquals("ZERO ONLY", NumberUtil.convertToEnglishMoney(BigDecimal.ZERO));
		assertEquals("ZERO ONLY", NumberUtil.convertToEnglishMoney("0"));
		assertEquals("ZERO ONLY", NumberUtil.convertToEnglishMoney("0.00"));
		assertEquals("ZERO AND CENTS FIFTY ONLY", NumberUtil.convertToEnglishMoney("0.50"));
		assertEquals("ONE HUNDRED ONLY", NumberUtil.convertToEnglishMoney("100"));
		assertEquals("ONE MILLION ONLY", NumberUtil.convertToEnglishMoney("1000000"));
		assertEquals("ONE THOUSAND,TWO HUNDRED AND THIRTY-FOUR AND CENTS FIFTY-SIX ONLY",
				NumberUtil.convertToEnglishMoney("1,234.56"));
		assertEquals("ONE THOUSAND TWO HUNDRED AND THIRTY-FOUR AND CENTS FIFTY-SIX ONLY",
				NumberUtil.convertToEnglishMoney("1234.56"));
		assertEquals("MINUS FIVE AND CENTS TWENTY-FIVE ONLY", NumberUtil.convertToEnglishMoney("-5.25"));
		// 扩展单位：QUINTILLION(10^18)及以上不越界
		assertEquals("ONE QUINTILLION ONLY", NumberUtil.convertToEnglishMoney("1000000000000000000"));
		assertTrue(NumberUtil.convertToEnglishMoney("123456789012345678901234").contains("SEXTILLION"));
		assertTrue(NumberUtil.convertToEnglishMoney("123456789012345678901234567").contains("SEPTILLION"));
		assertEquals(NumberUtil.convertToEnglishMoney("1234.56"), NumberUtil.format("1234.56", "capital-en"));
	}

	// ==================== NumberUtil：数组工具 ====================

	@Test
	public void arrayUtils() {
		assertNull(NumberUtil.getMax(null));
		assertNull(NumberUtil.getMin(null));
		assertNull(NumberUtil.getMax(new BigDecimal[0]));
		assertEquals(0, NumberUtil.getMax(new BigDecimal[] { null, new BigDecimal("5"), new BigDecimal("9") })
				.compareTo(new BigDecimal("9")));
		assertEquals(0, NumberUtil.getMin(new BigDecimal[] { null, new BigDecimal("5"), new BigDecimal("9") })
				.compareTo(new BigDecimal("5")));
		assertEquals(0, NumberUtil
				.getAverage(new BigDecimal[] { new BigDecimal("1"), new BigDecimal("2"), null, new BigDecimal("4") })
				.compareTo(new BigDecimal("1.75")));
		assertEquals(0, NumberUtil.summary(new BigDecimal[] { null, new BigDecimal("2"), new BigDecimal("3") })
				.compareTo(new BigDecimal("5")));
	}

	@Test
	public void randomUtils() {
		int n = NumberUtil.getRandomNum(5, 10);
		assertTrue(n >= 5 && n < 10);
		assertThrows(IllegalArgumentException.class, () -> NumberUtil.getRandomNum(5, 5));
		// 全量场景返回打乱后的0..max-1
		Object[] all = NumberUtil.randomArray(10, 10);
		assertEquals(10, all.length);
		int sum = 0;
		for (Object o : all) {
			sum += ((Integer) o).intValue();
		}
		assertEquals(45, sum);
		// 抽样场景：不重复且在范围内
		Object[] part = NumberUtil.randomArray(100, 5);
		assertEquals(5, part.length);
		Set<Integer> distinct = new HashSet<>();
		for (Object o : part) {
			int v = ((Integer) o).intValue();
			assertTrue(v >= 0 && v < 100);
			distinct.add(v);
		}
		assertEquals(5, distinct.size());
		int idx = NumberUtil.getProbabilityIndex(new int[] { 80, 20 });
		assertTrue(idx == 0 || idx == 1);
	}

	// ==================== NumberUtil：其它工具 ====================

	@Test
	public void miscUtils() {
		assertEquals(RoundingMode.HALF_EVEN, NumberUtil.parseRoundingMode("HALF_EVEN"));
		assertEquals(RoundingMode.UP, NumberUtil.parseRoundingMode("up"));
		assertNull(NumberUtil.parseRoundingMode(null));
		assertNull(NumberUtil.parseRoundingMode(""));
		assertEquals(RoundingMode.HALF_UP, NumberUtil.parseRoundingMode("not-a-mode"));
		assertTrue(NumberUtil.isInteger("123"));
		assertTrue(NumberUtil.isInteger("-5"));
		assertFalse(NumberUtil.isInteger("12.3"));
		assertFalse(NumberUtil.isInteger(null));
		assertTrue(NumberUtil.isNumber("12.3"));
		assertFalse(NumberUtil.isNumber("12a"));
		assertEquals(0.9f, NumberUtil.parsePercent("90%"), 0.0001f);
		assertEquals(1234.56, NumberUtil.parseDouble("1,234.56", null, null), 0.0001);
	}
}
