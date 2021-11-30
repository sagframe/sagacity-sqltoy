package org.sagacity.sqltoy.utils;

import java.sql.SQLException;
import java.sql.Timestamp;
import java.text.ParseException;
import java.text.SimpleDateFormat;
import java.time.LocalTime;
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
	public void testParseUsDate() throws ParseException {
		String a = "March 8th,2004";
		SimpleDateFormat sdf = new SimpleDateFormat("EEE MMM dd HH:mm:ss Z yyyy", Locale.US);// MMM dd hh:mm:ss Z yyyy
		System.out.println(sdf.parse(a));
	}
}
