package org.sagacity.sqltoy.utils;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.Test;

/**
 * 回归测试：nanoTime为负值(契约允许,JVM原点任意)时debugId不含负号;
 * 修复前负号剥离只在短字符串分支,恰好9字符负数且以00结尾时substring(0,7)带入'-'
 */
public class IdUtilDebugIdTest {

	@Test
	public void negativeNanoTimeProducesCleanId() {
		// 触发原缺陷的窗口:9字符负数(含负号)且以00结尾,旧算法substring(0,7)="-123456"
		String debugId = IdUtil.buildDebugId("12:34:56", -12345600L);
		assertFalse(debugId.contains("-"), "实际:" + debugId);
		assertTrue(debugId.startsWith("12:34:56."), "实际:" + debugId);
	}

	@Test
	public void variousNegativeValuesAllClean() {
		// 覆盖负值的不同位数:极短、短、9字符、长负数、Long最小值
		long[] negatives = { -5L, -100L, -12345678L, -12345600L, -98765432100L, Long.MIN_VALUE };
		for (long nanoTime : negatives) {
			String debugId = IdUtil.buildDebugId("00:00:00", nanoTime);
			assertFalse(debugId.contains("-"), "nanoTime=" + nanoTime + " 实际:" + debugId);
		}
	}

	@Test
	public void positiveAndRealCallFormatUnchanged() {
		// 正值场景格式不变:HH:mm:ss.7位数字
		String debugId = IdUtil.buildDebugId("08:09:10", 192734843723600L);
		assertTrue(debugId.matches("\\d{2}:\\d{2}:\\d{2}\\.\\d{7}"), "实际:" + debugId);
		// 真实调用(本机正值nanoTime)正常
		String real = IdUtil.getDebugId();
		assertTrue(real.matches("\\d{2}:\\d{2}:\\d{2}\\.\\d{7}"), "实际:" + real);
	}
}
