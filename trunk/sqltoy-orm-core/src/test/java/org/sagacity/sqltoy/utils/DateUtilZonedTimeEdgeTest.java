package org.sagacity.sqltoy.utils;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;

import java.time.ZoneId;
import java.time.ZonedDateTime;
import java.util.Date;

import org.junit.jupiter.api.Test;

/**
 * update 2026-10-9 锁定ZONED_TIME_PATTERN的末尾时区形态解析(裸Z/z纳入系今日改动):
 * parseString剥离时区后按本地墙钟解析;parseZonedDateTime按真实偏移解析(Z=UTC)。
 * 含2026-10-9修复:小写z经ZoneOffset.of抛DateTimeException,已归一为大写Z。
 */
public class DateUtilZonedTimeEdgeTest {

	private static final long LOCAL_1015_1030 = ZonedDateTime
			.of(2026, 1, 15, 10, 30, 0, 0, ZoneId.systemDefault()).toInstant().toEpochMilli();

	@Test
	public void parseStringStripsZoneToLocalWallClock() {
		// 裸Z(大写/小写)与±HH:mm、[时区ID]均剥离后按本地墙钟解析
		assertEquals(LOCAL_1015_1030, DateUtil.parseString("2026-01-15T10:30:00Z").getTime(), "裸Z剥离后按本地墙钟");
		assertEquals(LOCAL_1015_1030, DateUtil.parseString("2026-01-15T10:30:00z").getTime(), "小写z同Z");
		assertEquals(LOCAL_1015_1030, DateUtil.parseString("2026-01-15 10:30:00Z").getTime(), "空格分隔+裸Z");
		assertEquals(LOCAL_1015_1030, DateUtil.parseString("2026-01-15 10:30:00+08:00").getTime(), "+08:00剥离");
		assertEquals(LOCAL_1015_1030, DateUtil.parseString("2026-01-15 10:30:00+8:00").getTime(), "短偏移+8:00剥离");
		assertEquals(LOCAL_1015_1030, DateUtil.parseString("2026-01-15 10:30:00+08:00[Asia/Shanghai]").getTime(),
				"[时区ID]剥离");
		// 无时区形态回归不受影响
		assertEquals(LOCAL_1015_1030, DateUtil.parseString("2026-01-15 10:30:00").getTime(), "无时区原样");
	}

	@Test
	public void parseZonedDateTimeHonorsOffset() {
		// 裸Z=UTC
		ZonedDateTime utc = DateUtil.parseZonedDateTime("2026-01-15T10:30:00Z");
		assertEquals("2026-01-15T10:30:00Z", utc.toInstant().toString(), "裸Z按UTC");
		// update 2026-10-9 修复:小写z归一为Z(原抛DateTimeException: Invalid ID for ZoneOffset)
		assertEquals(utc.toInstant(), DateUtil.parseZonedDateTime("2026-01-15T10:30:00z").toInstant(), "小写z同Z");
		// 短偏移+8:00补零为+08:00
		ZonedDateTime plus8 = DateUtil.parseZonedDateTime("2026-01-15 10:30:00+8:00");
		assertEquals("2026-01-15T10:30+08:00", plus8.toString().substring(0, 22), "短偏移补零为+08:00");
		// 时区ID形态
		ZonedDateTime sh = DateUtil.parseZonedDateTime("2026-01-15 10:30:00+08:00[Asia/Shanghai]");
		assertNotNull(sh.getZone());
		assertEquals(plus8.toInstant(), sh.toInstant(), "[Asia/Shanghai]与+08:00同一时刻");
	}
}
