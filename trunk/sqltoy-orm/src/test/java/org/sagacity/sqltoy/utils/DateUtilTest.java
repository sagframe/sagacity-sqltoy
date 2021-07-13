package org.sagacity.sqltoy.utils;

import java.sql.SQLException;
import java.sql.Timestamp;
import java.time.LocalTime;
import java.util.Date;

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
}
