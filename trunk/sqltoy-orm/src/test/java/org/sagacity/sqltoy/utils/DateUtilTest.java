package org.sagacity.sqltoy.utils;

import java.sql.SQLException;
import java.sql.Timestamp;
import java.text.ParseException;
import java.text.SimpleDateFormat;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.time.format.DateTimeFormatter;
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
	public void testOracleTimestampAsLocalDate() throws SQLException {
		TIMESTAMP timestamp = new TIMESTAMP(DateUtil.getTimestamp(null));
		LocalTime date = DateUtil.asLocalTime(timestamp.timestampValue());
		System.err.println(date);
	}

	@Test
	public void testFormat() throws SQLException {
		TIMESTAMP timestamp = new TIMESTAMP(DateUtil.getTimestamp(null));
		LocalTime date = DateUtil.asLocalTime(timestamp.timestampValue());

		System.err.println(DateUtil.formatDate(date, "HH:mm:ss"));
	}

	@Test
	public void testParseUsDate() throws ParseException {
		String a = "March 8th,2004";
		SimpleDateFormat sdf = new SimpleDateFormat("EEE MMM dd HH:mm:ss Z yyyy", Locale.US);// MMM dd hh:mm:ss Z yyyy
		System.out.println(sdf.parse(a));
	}

	@Test
	public void testDateAdd() throws ParseException {
		System.err.println(DateUtil.addMonth("2021-12-11", 2));
		System.err.println(DateUtil.addYear("2018-12-11", 2));

	}

	@Test
	public void testLocalDateTime() throws ParseException {
//		System.err.println(DateUtil.parseString("2023-11-22 12:22:11"));
//		LocalDateTime dateValue = LocalDateTime.parse("2023-11-29T20:23:23.123456");
//		System.err.println(DateUtil.formatDate(dateValue, "yyyy-MM-dd HH:mm:ss.SSSSSSSSS"));
//		System.err.println(dateValue.getNano());
//		String nanoStr = "" + dateValue.getNano();
//		if (nanoStr.length() == 9) {
//			if (nanoStr.endsWith("000000")) {
//				nanoStr = nanoStr.substring(0, 3);
//			} else if (nanoStr.endsWith("000")) {
//				nanoStr = nanoStr.substring(0, 6);
//			}
//		} else if (nanoStr.length() == 6) {
//			if (nanoStr.endsWith("000")) {
//				nanoStr = nanoStr.substring(0, 3);
//			}
//		}
//		System.err.println("[" + nanoStr + "]");

//		System.err.println(
//				DateUtil.formatDate(DateUtil.parseString("20231130112031033456789"), "yyyy-MM-dd HH:mm:ss.SSSSSS"));
		// System.err.println(DateUtil.formatDate(DateUtil.parseString("2023-10-22
		// 12:45:20.6"), "yyyy-MM-dd HH:mm:ss.S"));
		// System.err.println(DateUtil.formatDate(DateUtil.parseString("12:45:20.666"),
		// "HH:mm:ss.SSS"));
		// System.err.println(LocalDateTime.parse("20231210203322111",
		// DateTimeFormatter.ofPattern("yyyyMMddHHmmssSSS")));

//		LocalTime localTime = DateUtil.parseLocalDateTime("12:22:30").toLocalTime();
//		LocalDateTime result = LocalDateTime.of(LocalDate.now(), localTime);
//		result.plusNanos(302);
//		System.err.println(result);
		LocalDate localDate = DateUtil.parseLocalDateTime("2023-10-22 12").toLocalDate();

		System.err.println(localDate);
//		LocalDateTime localDateTime = LocalDateTime.of(0, 0, 0, 12, 23, 20);
//		System.err.println(localDateTime);
		LocalDateTime localDateTime1 = LocalDateTime.of(2023, 10, 10, 11, 12, 40);
		System.err.println(localDateTime1.toLocalTime());
		System.err.println(DateUtil.formatDate("2020-11-20 21:34:22.234", "yyyy-MM-dd HH:mm:ss.SSSSSS", null));
		System.err.println(DateUtil.formatDate("2020-11-20 21:34:22.2", "yyyy-MM-dd HH:mm:ss.SSSSSS", null));
		System.err.println(DateUtil.formatDate(LocalDateTime.now(), "yyyy-MM-dd HH:mm:ss.SSSSSS", null));
		System.err.println(DateUtil.formatDate(LocalDateTime.now(), "yy-MM-dd HH:mm", null));
		System.err.println(DateUtil.formatDate(LocalDate.now(), "yyyy-MM-dd", null));
		System.err.println(DateUtil.formatDate(LocalDate.now(), "yyyy", null));
		System.err.println(DateUtil.formatDate(LocalDate.now(), "yyyy-MM", null));
		System.err.println(DateUtil.formatDate(LocalTime.now(), "HH:mm:ss.SSSSSS", null));
		
		System.err.println(DateUtil.parseLocalDateTime("12:30:30.123"));
		System.err.println(DateUtil.parseLocalDateTime("12:30:30.123456"));
		System.err.println(DateUtil.parseLocalDateTime("12:30:30.123456789"));
	}
}
