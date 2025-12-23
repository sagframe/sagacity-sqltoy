package org.sagacity.sqltoy.utils;

import java.sql.SQLException;
import java.sql.Timestamp;
import java.text.ParseException;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.time.OffsetTime;
import java.time.ZoneOffset;
import java.time.ZonedDateTime;
import java.util.Date;
import java.util.Locale;

import org.junit.jupiter.api.Test;

import oracle.sql.TIMESTAMP;

public class DateUtilTest {
	@Test
	public void testTimestamp() {
		Timestamp timestamp = DateUtil.getTimestamp(null);
		Date date = DateUtil.convertDateObject(timestamp, null, null);
		System.err.println(date);
	}

	@Test
	public void testOracleTimestamp() {
		TIMESTAMP timestamp = new TIMESTAMP(DateUtil.getTimestamp(null));
		Date date = DateUtil.convertDateObject(timestamp, null, null);
		System.err.println(date);
	}

	@Test
	public void testGetOffsetDateTime() {
		TIMESTAMP timestamp = new TIMESTAMP(DateUtil.getTimestamp(null));
		Date date = DateUtil.convertDateObject(timestamp, null, null);
		System.err.println(date);
	}

	@Test
	public void testOracleTimestampAsLocalDate() throws SQLException {
		TIMESTAMP timestamp = new TIMESTAMP(DateUtil.getTimestamp(null));
		LocalTime date = DateUtil.asLocalTime(timestamp.timestampValue());
		System.err.println(date);
	}

	@Test
	public void testFormat() throws SQLException {
		TIMESTAMP timestamp = new TIMESTAMP(DateUtil.getTimestamp(null));
		LocalTime date = DateUtil.asLocalTime(timestamp.timestampValue());

		System.err.println(DateUtil.formatDate(date, "HH:mm:ss", Locale.US));
	}

	@Test
	public void testOther() throws SQLException {
		System.err.println(DateUtil.getDayOfMonth("2023-12-9"));
		System.err.println(DateUtil.getMonth("2023-12-9"));
		System.err.println(DateUtil.getYear("2023-12-9"));
	}

	@Test
	public void testFormat2China() throws SQLException {
		System.err.println(DateUtil.format2China(LocalDateTime.now()));
		System.err.println(DateUtil.parseChinaDate("二零二三年十二月五日"));
		System.err.println(DateUtil.parseChinaDate("二零二三年十二月五日十五时五十五分十秒"));
		System.err.println(DateUtil.parseChinaDate("二零二三年十二月五日一十五时五十五分十秒"));
		System.err.println(DateUtil.parseChinaDate("二零二三年十二月五日一十五时五十五分五十五秒"));
	}

	@Test
	public void testParseUsDate() throws ParseException {
		System.out.println(DateUtil.parseString("Fri 15 Dec 2023 10:20:43 CEST"));
		System.out.println(DateUtil.parseString("Thu 14 Dec 2023 10:20:43 CEST"));
		System.out.println(DateUtil.parseString("Thur 14 Dec 2023 10:20:43 CEST"));
		System.out.println(DateUtil.parseString("Tue 12 Dec 2023 10:20:43 CEST"));
		System.out.println(DateUtil.parseString("Tues 12 Dec 2023 10:20:43 CEST"));
//		System.err.println(new SimpleDateFormat("EEE MMM dd yyyy HH:mm:ss z", Locale.ENGLISH)
//				.parse("Fri Dec 15 2023 10:20:43 CEST"));

		System.out.println(DateUtil.parseString("Dec 15 10.20.43 CEST 2023"));
		System.out.println(DateUtil.parseString("15th,DEC 10:20:43 CEST 2023"));
		System.out.println(DateUtil.parseString("January 18th,2004"));
		System.out.println(DateUtil.parseString("Sept 8th,2004"));
		System.out.println(DateUtil.parseString("Sep 8th,2004"));
		System.out.println(DateUtil.parseString("Sep 8th,2004"));
		System.out.println(DateUtil.parseString("Sep. 8th,2004"));
		System.out.println(DateUtil.parseString("15 DEC 10:20:43 CEST 2023"));
		System.out.println(DateUtil.parseString("Fri 15 DEC 10:20:43 CEST 2023"));
		System.out.println(DateUtil.parseString("Fri 15 DEC 2023 10:20:43 CEST"));

//		System.err.println(new SimpleDateFormat("EEE MMM dd HH:mm:ss z yyyy", Locale.ENGLISH)
//				.parse("Fri Dec 15 10:20:43 CEST 2023"));
	}

	@Test
	public void testParseUsDate1() throws ParseException {
		System.out.println(DateUtil.parseString("Thur 14 Dec 2023 10:20:43 CEST"));
	}

	@Test
	public void testDateAdd() throws ParseException {
		System.err.println(DateUtil.addMonth("2021-12-11", 2));
		System.err.println(DateUtil.addYear("2018-12-11", 2));
		System.err.println(DateUtil.addYear(LocalDate.now(), 2));
	}

	@Test
	public void testLocalDateTime() throws ParseException {
		System.err.println(DateUtil.parseString("20231130112031033456789"));
		System.err.println(DateUtil.parseString("20231130112031033456"));
		System.err.println(DateUtil.parseString("20231130112031033"));
		System.err.println(DateUtil.parseString("202311301120311"));
		System.err.println(DateUtil.parseString("2023-11-22 12:22:11"));
		LocalDateTime dateValue = LocalDateTime.parse("2023-11-29T20:23:23.123456");
		System.err.println(DateUtil.formatDate(dateValue, "yyyy-MM-dd HH:mm:ss.SSSSSSSSS"));

		System.err.println(
				DateUtil.formatDate(DateUtil.parseString("20231130112031033456789"), "yyyy-MM-dd HH:mm:ss.SSSSSS"));
		System.err.println(DateUtil.formatDate(DateUtil.parseString("202311301120311"), "yyyy-MM-dd HH:mm:ss.SSSSSS"));
		System.err.println(DateUtil.formatDate(DateUtil.parseString("2023-10-22 12:45:20.6"), "yyyy-MM-dd HH:mm:ss.S"));
		System.err.println(DateUtil.formatDate(DateUtil.parseString("12:45:20.666"), "HH:mm:ss.SSS"));

		LocalTime localTime = DateUtil.parseLocalDateTime("12:22:30").toLocalTime();
//		LocalDateTime result = LocalDateTime.of(LocalDate.now(), localTime);
//		result.plusNanos(302);
//		System.err.println(result);
		LocalDate localDate = DateUtil.parseLocalDateTime("2023-10-22 12").toLocalDate();

		// System.err.println(localDate);
//		LocalDateTime localDateTime = LocalDateTime.of(0, 0, 0, 12, 23, 20);
//		System.err.println(localDateTime);
		LocalDateTime localDateTime1 = LocalDateTime.of(2023, 10, 10, 11, 12, 40);
		System.err.println(localDateTime1.toLocalTime());
		System.err.println(DateUtil.formatDate("2020-11-20 21:34:22.234", "yyyy-MM-dd HH:mm:ss.SSSSSS", null));
		System.err.println(DateUtil.formatDate("2020-11-20 21:34:22.2", "yyyy-MM-dd HH:mm:ss.SSSSSS", null));
		System.err.println(DateUtil.formatDate(LocalDateTime.now(), "yyyy-MM-dd HH:mm:ss.SSSSSS", null));
		System.err.println(DateUtil.formatDate(LocalDateTime.now(), "yy-MM-dd HH:mm", null));
		System.err.println(DateUtil.formatDate(LocalDateTime.now(), "HH:mm:ss", null));
		System.err.println(DateUtil.formatDate(LocalDateTime.now(), "yy-MM-dd", null));
		System.err.println(DateUtil.formatDate(LocalDateTime.now(), "yyyy-MM-dd", null));
		System.err.println(DateUtil.formatDate(LocalDate.now(), "yyyy-MM-dd", null));
		System.err.println(DateUtil.formatDate(LocalDate.now(), "yyyy", null));
		System.err.println(DateUtil.formatDate(LocalDate.now(), "yyyy-MM", null));
		System.err.println(DateUtil.formatDate(LocalTime.now(), "HH:mm:ss.SSSSSS", null));

		System.err.println(DateUtil.parseLocalDateTime("12:30:30.123"));
		System.err.println(DateUtil.parseLocalDateTime("12:30:30.123456"));
		System.err.println(DateUtil.parseLocalDateTime("12:30:30.123456789"));
		System.err.println(DateUtil.parse("2023-10-22", "yyyy-MM-dd HH:mm:ss"));
	}

	@Test
	public void testParseLocalDateTime() throws ParseException {
		System.err.println(DateUtil.parseLocalDateTime("20231121 123030.123345321"));
		System.err.println(DateUtil.parseString("20231121 123030.123345321"));
		System.err.println(DateUtil.parseLocalDateTime("2023-11-21 12:30:30.123345321"));
		System.err.println(DateUtil.parseLocalDateTime("2023-11-21 12:30:30.123345"));
		System.err.println(DateUtil.parseLocalDateTime("2023-11-21 12:30:30.123"));
		System.err.println(DateUtil.parseLocalDateTime("2023-11-21 12:30:30.1"));
		System.err.println(DateUtil.parseLocalDateTime("2023-11-21 12:30:30"));
		System.err.println(DateUtil.parseLocalDateTime("2023-11-21 12"));
		System.err.println(DateUtil.parseLocalDateTime("2023-11-21 12:30"));
		System.err.println(DateUtil.parseLocalDateTime("23-11-21 12:30"));
		System.err.println(DateUtil.parseLocalDateTime("23-11-21"));
		System.err.println(DateUtil.parseLocalDateTime("2023-11-21"));
		System.err.println(DateUtil.parseLocalDateTime("12:30:30.123"));
		System.err.println(DateUtil.parseLocalDateTime("12:30:30.123321"));
		System.err.println(DateUtil.parseLocalDateTime("12:30:30.123323212"));
		System.err.println(DateUtil.parseLocalDateTime("12:30:30.123"));
		System.err.println(DateUtil.parseLocalDateTime("12:30:30.1"));
		System.err.println(DateUtil.parseLocalDateTime("12:30:30"));
		System.err.println(DateUtil.parseString("江苏华海通上海分"));

	}

	@Test
	public void testFormatLocalDate() throws ParseException {
		System.err.println(DateUtil.formatDate(LocalDate.now(), "yyyy-MM-dd", null));
		System.err.println(DateUtil.formatDate(LocalDate.now(), "yyyy-MM", null));
		System.err.println(DateUtil.formatDate(LocalDate.now(), "yyyy", null));
		System.err.println(DateUtil.formatDate(LocalDateTime.now(), "yyyy-MM-dd", null));
		System.err.println(DateUtil.formatDate(LocalDateTime.now(), "yyyy-MM-dd HH:mm:ss", null));
		System.err.println(DateUtil.formatDate(LocalTime.now(), "HH:mm:ss", null));

	}

	@Test
	public void testFormatStr() throws ParseException {
		System.err.println(DateUtil.formatDate("2023-11-12 23:23:12", "yyyy", null));
		System.err.println(DateUtil.formatDate("2023-11-12 23:23:12.343231123", "yyyy-MM-dd HH:mm:ss.SSSSSS", null));
		System.err.println(DateUtil.formatDate("2023-11-12 23:23:12.343231123", "yyyy-MM-dd HH:mm:ss.SSS", null));
		System.err.println(DateUtil.formatDate("2023-11-12 23:23:12", "yyyy-MM-dd HH:mm:ss.SSSSSS", null));
		System.err.println(DateUtil.formatDate("2023-11-12 23:23:12", "yyyy-MM-dd HH:mm:ss.SSS", null));
		System.err.println(DateUtil.formatDate("2023-11-12 23:23:12", "yyyy-MM-dd HH:mm:ss.S", null));
		System.err.println(DateUtil.formatDate("2023-11-12 23:23:12", "yyyy-MM-dd HH:mm:ss", null));
		System.err.println(DateUtil.formatDate("2023-11-12 23:23:12", "HH:mm:ss", null));
		System.err.println(DateUtil.formatDate("2023-11-12 23:23:12.345", "HH:mm:ss.S", null));
		System.err.println(DateUtil.formatDate("2023-11-12 23:23:12.345", "HH:mm:ss.SSS", null));
		System.err.println(DateUtil.formatDate("2023-11-12 23:23:12", "HH:mm:ss.SSS", null));
		System.err.println(DateUtil.formatDate("2023-11-12 23:23:12", "yyyy-MM-dd", null));
		System.err.println(DateUtil.formatDate("2023-11-12 23:23:12", "yyyy-MM", null));
		System.err.println(DateUtil.formatDate("2023-11-12 23:23:12", "yyyy", null));
		System.err.println(DateUtil.formatDate("2023-11-12 23:23:12.343231123", "HH:mm:ss.S", null));
		System.err.println(DateUtil.formatDate("2023-11-12 23:23:12.343231123", "HH:mm:ss.SSS", null));

	}

	@Test
	public void testFormatLocalTime() throws ParseException {
		System.err.println(DateUtil.formatDate("23:23:12.343231123", "HH:mm:ss.SSSSSSSSS", null));
		System.err.println(DateUtil.formatDate(LocalTime.now(), "HH:mm:ss.SSSSSSSSS", null));
		System.err.println(DateUtil.formatDate(LocalTime.now(), "HH:mm:ss.SSSSSS", null));
		System.err.println(DateUtil.formatDate(LocalTime.now(), "HH:mm:ss.SSS", null));
		System.err.println(DateUtil.formatDate(LocalTime.now(), "HH:mm:ss.S", null));
		System.err.println(DateUtil.formatDate(LocalTime.now(), "HH:mm:ss", null));
		System.err.println(DateUtil.formatDate(LocalTime.now(), "HH:mm", null));
		System.err.println(DateUtil.formatDate(LocalTime.now(), "HH", null));

	}

	@Test
	public void testParseDateString() throws ParseException {
		DateUtil.parse("21:34:22.2", null);
		System.err.println(DateUtil.parse(LocalDate.now(), "yyyy"));
		System.err.println(DateUtil.asLocalDateTime(DateUtil.parse("21:34:22.2", null)).getNano());
		System.err.println(DateUtil.parse("2023-10-22", null));
		System.err.println(DateUtil.parse("2023-10-22", "yyyy-MM-dd"));
		System.err.println(DateUtil.parse(new Date(), "yyyy-MM-dd"));
		System.err.println(DateUtil.parse(new Date(), "yyyy-MM-dd HH:mm:ss"));
		System.err.println(DateUtil.parse(LocalDateTime.now(), "yyyy-MM-dd HH:mm:ss"));
		System.err.println(DateUtil.parse(LocalDate.now(), "yyyy-MM-dd"));

		System.err.println(DateUtil.parse(LocalTime.now(), "HH:mm:ss"));
		System.err.println(DateUtil.parse("2023-10-22", "yyyy-MM-dd HH:mm:ss"));
		System.err.println(DateUtil.parse("12:23:21", "yyyy-MM-dd HH:mm:ss"));
		System.err.println(DateUtil.parse("12:23:21", "HH:mm:ss.SSS"));
		System.err.println(DateUtil.parse("2023-10-22", "yyyy-MM-dd"));
		System.err.println(DateUtil.parse("2023-10-22", "HH:mm:ss.SSSSSS"));
		System.err.println(DateUtil.parse("2020-11-20 21:34:22.234", null));
		System.err.println(DateUtil.parse("2020-11-20 21:34:22.234", "HH:mm:ss.SSSSSS"));
		System.err.println(DateUtil.parse("Fri 15 DEC 10:20:43 2023", "HH:mm:ss.SSSSSS"));
		System.err.println(DateUtil.parse("2020-11-20 21:34:22.2", null));
		System.err.println(DateUtil.parse("2020-11-20 21:34:22.234", null));
		System.err.println(DateUtil.asLocalDateTime(DateUtil.parse("21:34:22.2", null)).getNano());
		System.err.println(DateUtil.formatDate(DateUtil.parse("21:34:22.221", null), "HH:mm:ss.SSS"));
		System.err.println(
				DateUtil.formatDate(DateUtil.parseLocalDateTime("21:34:22.2", null).toLocalTime(), "HH:mm:ss.SSS"));
	}

	@Test
	public void testFmtLocalDateTime() throws ParseException {
		System.err.println(DateUtil.formatDate(LocalDateTime.now(), "yyyy-MM-dd HH:mm:ss.SSSSSS", null));
		System.err.println(DateUtil.formatDate(LocalDateTime.now(), "yy-MM-dd HH:mm", null));
		System.err.println(DateUtil.formatDate(LocalDateTime.now(), "HH:mm:ss", null));
		System.err.println(DateUtil.formatDate(LocalDateTime.now(), "HH:mm:ss.S", null));
		System.err.println(DateUtil.formatDate(LocalDateTime.now(), "yy-MM-dd", null));
		System.err.println(DateUtil.formatDate(LocalDateTime.now(), "yyyy-MM-dd", null));
		System.err.println(DateUtil.formatDate(LocalDateTime.now(), "yy", null));
		System.err.println(DateUtil.formatDate(LocalDateTime.now(), "yyyy", null));
	}

	@Test
	public void testParseString() throws ParseException {
		System.err.println(DateUtil.formatDate(LocalDateTime.now(), "yyyy-MM-dd HH:mm:ss.SSSSSS", null));
		System.err.println(DateUtil.formatDate(LocalDateTime.now(), "yy-MM-dd HH:mm", null));
		System.err.println(DateUtil.formatDate(LocalDateTime.now(), "HH:mm:ss", null));
		System.err.println(DateUtil.formatDate(LocalDateTime.now(), "HH:mm:ss.S", null));
		System.err.println(DateUtil.formatDate(LocalDateTime.now(), "yy-MM-dd", null));
		System.err.println(DateUtil.formatDate(LocalDateTime.now(), "yyyy-MM-dd", null));
		System.err.println(DateUtil.formatDate(LocalDateTime.now(), "yy", null));
		System.err.println(DateUtil.parseString("2020-11-20 21:34:22.234345876"));
		System.err.println(DateUtil.parseString("2020-11-20 21:34:22.234345"));
		System.err.println(DateUtil.parseString("2020-11-20 21:34:22.234"));
		System.err.println(DateUtil.parseString("2020-11-20 21:34:22.2"));
		System.err.println(DateUtil.parseString("2020-11-20 21:34:22"));
		System.err.println(DateUtil.parseString("21:34:22.234345321"));
		System.err.println(DateUtil.parseString("21:34:22.234345"));
		System.err.println(DateUtil.parseString("21:34:22.234"));
		System.err.println(DateUtil.parseString("21:34:22.2"));
		System.err.println(DateUtil.formatDate(DateUtil.parseString("2020-11-20 21:34:22.234345+8:00"),
				"yyyy-MM-dd HH:mm:ss.SSS"));
		// System.err.println(DateUtil.parseLocalDateTime("2020-11-20 21:34:22.234",
		// "yyyy-MM-dd HH:mm:ss.SSS"));
	}

	@Test
	public void asDate() throws ParseException {
		String dateStr = "20231130112031033456789";
		System.err.println(DateUtil.parseString("20231130112031033456789"));
//		dateStr = dateStr.substring(0, 8).concat(" ").concat(dateStr.substring(8, 14)).concat(".")
//				.concat(dateStr.substring(14));
//		System.err.println(dateStr);
	}

	@Test
	public void parseZonedDateTime() throws ParseException {
		String dateVar = ZonedDateTime.of(LocalDateTime.now(), ZoneOffset.of("-05:00")).toString();

		dateVar = OffsetTime.now().toString();
		// System.err.println(dateVar);
		// System.err.println(DateUtil.parseZonedDateTime(dateVar));
		System.err.println(DateUtil.parseZonedDateTime("21:34:22.234345+8:00"));

	}

	@Test
	public void testAddDate() throws ParseException {
		System.err.println(DateUtil.addDay(DateUtil.getNowTime(), 2));

	}
}
