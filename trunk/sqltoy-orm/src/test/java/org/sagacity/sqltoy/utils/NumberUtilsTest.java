package org.sagacity.sqltoy.utils;

import java.math.BigDecimal;

import org.junit.jupiter.api.Test;

public class NumberUtilsTest {
	@Test
	public void testToEnglish() {
		BigDecimal value = new BigDecimal(207.98);
		System.err.println(NumberUtil.convertToEnglishMoney(value));
	}

	@Test
	public void testToMinuseEnglish() {
		BigDecimal value = new BigDecimal(-209.98);
		System.err.println(NumberUtil.convertToEnglishMoney(value));
	}

	@Test
	public void testNumFmt() {
		BigDecimal value = new BigDecimal(-209.98);
		System.err.println(NumberUtil.format(value,"##,###.00"));
	}
}
