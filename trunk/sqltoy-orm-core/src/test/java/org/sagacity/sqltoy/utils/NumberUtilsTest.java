package org.sagacity.sqltoy.utils;

import java.math.BigDecimal;

import org.junit.jupiter.api.Test;

public class NumberUtilsTest {
	@Test
	public void testToEnglish() {
		BigDecimal value = new BigDecimal(256237.98);
		System.err.println(NumberUtil.convertToEnglishMoney(value));
	}

	@Test
	public void testToEnglish1() {
		BigDecimal value = new BigDecimal(3467893);
		System.err.println(NumberUtil.convertToEnglishMoney(value));
		value = new BigDecimal(3000000);
		System.err.println(NumberUtil.convertToEnglishMoney(value));
		value = new BigDecimal(256237.98);
		System.err.println(NumberUtil.convertToEnglishMoney(value));
		System.err.println(NumberUtil.convertToEnglishMoney("398,392,923.03"));
		System.err.println(NumberUtil.convertToEnglishMoney("-8,392,923.03"));
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
	
	@Test
	public void testNumFmtChina() {
		BigDecimal value = new BigDecimal(209.98);
		System.err.println(NumberUtil.toCapitalMoney(value));
		
		BigDecimal value1 = new BigDecimal(209);
		System.err.println(NumberUtil.toCapitalMoney(value1));
	}
}
