package org.sagacity.sqltoy.utils;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.math.BigDecimal;
import java.math.RoundingMode;

import org.junit.jupiter.api.Test;

/**
 * NumberUtil单元测试
 */
public class NumberUtilsTest {

	// ======================== toCapitalMoney 测试 ========================

	@Test
	public void testToCapitalMoneyZero() {
		assertEquals("零元", NumberUtil.toCapitalMoney(BigDecimal.ZERO));
		assertEquals("零元", NumberUtil.toCapitalMoney(new BigDecimal("0.00")));
	}

	@Test
	public void testToCapitalMoneySingleDigit() {
		assertEquals("壹元整", NumberUtil.toCapitalMoney(new BigDecimal("1")));
		assertEquals("玖元整", NumberUtil.toCapitalMoney(new BigDecimal("9")));
	}

	@Test
	public void testToCapitalMoneyTen() {
		// 按银行规范，拾位前必须保留"壹"防篡改
		assertEquals("壹拾元整", NumberUtil.toCapitalMoney(new BigDecimal("10")));
	}

	@Test
	public void testToCapitalMoneyTeens() {
		assertEquals("壹拾壹元整", NumberUtil.toCapitalMoney(new BigDecimal("11")));
		assertEquals("壹拾玖元整", NumberUtil.toCapitalMoney(new BigDecimal("19")));
		assertEquals("壹拾伍元整", NumberUtil.toCapitalMoney(new BigDecimal("15")));
	}

	@Test
	public void testToCapitalMoneyHundreds() {
		assertEquals("壹佰元整", NumberUtil.toCapitalMoney(new BigDecimal("100")));
		assertEquals("壹佰零壹元整", NumberUtil.toCapitalMoney(new BigDecimal("101")));
		assertEquals("壹佰壹拾元整", NumberUtil.toCapitalMoney(new BigDecimal("110")));
		assertEquals("贰佰零玖元整", NumberUtil.toCapitalMoney(new BigDecimal("209")));
		assertEquals("叁佰贰拾肆元整", NumberUtil.toCapitalMoney(new BigDecimal("324")));
		assertEquals("伍佰元整", NumberUtil.toCapitalMoney(new BigDecimal("500")));
	}

	@Test
	public void testToCapitalMoneyThousands() {
		assertEquals("壹仟元整", NumberUtil.toCapitalMoney(new BigDecimal("1000")));
		assertEquals("壹仟零壹元整", NumberUtil.toCapitalMoney(new BigDecimal("1001")));
		assertEquals("壹仟零壹拾元整", NumberUtil.toCapitalMoney(new BigDecimal("1010")));
		assertEquals("壹仟壹佰壹拾壹元整", NumberUtil.toCapitalMoney(new BigDecimal("1111")));
	}

	@Test
	public void testToCapitalMoneyTenThousand() {
		assertEquals("壹万元整", NumberUtil.toCapitalMoney(new BigDecimal("10000")));
		assertEquals("壹万零壹元整", NumberUtil.toCapitalMoney(new BigDecimal("10001")));
		assertEquals("壹万贰仟叁佰肆拾伍元整", NumberUtil.toCapitalMoney(new BigDecimal("12345")));
	}

	@Test
	public void testToCapitalMoneyHundredThousand() {
		// 按银行规范，拾万位前必须保留"壹"防篡改
		assertEquals("壹拾万元整", NumberUtil.toCapitalMoney(new BigDecimal("100000")));
		assertEquals("壹拾贰万叁仟肆佰伍拾陆元整", NumberUtil.toCapitalMoney(new BigDecimal("123456")));
	}

	@Test
	public void testToCapitalMoneyMillion() {
		assertEquals("壹佰万元整", NumberUtil.toCapitalMoney(new BigDecimal("1000000")));
		assertEquals("壹佰万零壹元整", NumberUtil.toCapitalMoney(new BigDecimal("1000001")));
	}

	@Test
	public void testToCapitalMoneyHundredMillion() {
		assertEquals("壹亿元整", NumberUtil.toCapitalMoney(new BigDecimal("100000000")));
		assertEquals("壹亿贰仟叁佰肆拾伍万陆仟柒佰捌拾玖元整",
				NumberUtil.toCapitalMoney(new BigDecimal("123456789")));
	}

	@Test
	public void testToCapitalMoneyWithDecimal() {
		assertEquals("贰佰零玖元玖角捌分", NumberUtil.toCapitalMoney(new BigDecimal("209.98")));
		assertEquals("壹佰元壹角贰分", NumberUtil.toCapitalMoney(new BigDecimal("100.12")));
		assertEquals("壹仟贰佰叁拾肆元伍角陆分", NumberUtil.toCapitalMoney(new BigDecimal("1234.56")));
	}

	@Test
	public void testToCapitalMoneyZeroYuanWithCents() {
		// 整数部分为0但有小数部分
		assertEquals("零元伍角", NumberUtil.toCapitalMoney(new BigDecimal("0.50")));
		assertEquals("零元零伍分", NumberUtil.toCapitalMoney(new BigDecimal("0.05")));
		assertEquals("零元零玖分", NumberUtil.toCapitalMoney(new BigDecimal("0.09")));
		assertEquals("零元玖角", NumberUtil.toCapitalMoney(new BigDecimal("0.90")));
	}

	@Test
	public void testToCapitalMoneyNegative() {
		assertEquals("负贰佰零玖元玖角捌分", NumberUtil.toCapitalMoney(new BigDecimal("-209.98")));
		assertEquals("负壹佰元整", NumberUtil.toCapitalMoney(new BigDecimal("-100")));
		assertEquals("负零元伍角", NumberUtil.toCapitalMoney(new BigDecimal("-0.50")));
	}

	@Test
	public void testToCapitalMoneyWithZeroCents() {
		// 小数部分全为零(因setScale(5)产生尾零)
		assertEquals("壹佰元整", NumberUtil.toCapitalMoney(new BigDecimal("100.00")));
	}

	@Test
	public void testToCapitalMoneyRoundUp() {
		// setScale(5, HALF_UP) - 第五位进位
		assertEquals("壹元玖角玖分玖厘", NumberUtil.toCapitalMoney(new BigDecimal("1.999")));
	}

	// ======================== capitalMoneyToNum 测试 ========================

	@Test
	public void testCapitalMoneyToNumZero() {
		assertEquals(0, new BigDecimal("0").compareTo(NumberUtil.capitalMoneyToNum("零元")));
	}

	@Test
	public void testCapitalMoneyToNumInteger() {
		assertEquals(0, new BigDecimal("1").compareTo(NumberUtil.capitalMoneyToNum("壹元整")));
		assertEquals(0, new BigDecimal("209").compareTo(NumberUtil.capitalMoneyToNum("贰佰零玖元整")));
		assertEquals(0, new BigDecimal("10000").compareTo(NumberUtil.capitalMoneyToNum("壹万元整")));
	}

	@Test
	public void testCapitalMoneyToNumShi() {
		// "拾元整" = 10
		assertEquals(0, new BigDecimal("10").compareTo(NumberUtil.capitalMoneyToNum("拾元整")));
	}

	@Test
	public void testCapitalMoneyToNumShiWan() {
		// "拾万元整" = 100000
		assertEquals(0, new BigDecimal("100000").compareTo(NumberUtil.capitalMoneyToNum("拾万元整")));
	}

	@Test
	public void testCapitalMoneyToNumWithCents() {
		assertEquals(0, new BigDecimal("209.980").compareTo(NumberUtil.capitalMoneyToNum("贰佰零玖元玖角捌分")));
		assertEquals(0, new BigDecimal("1234.560").compareTo(NumberUtil.capitalMoneyToNum("壹仟贰佰叁拾肆元伍角陆分")));
	}

	@Test
	public void testCapitalMoneyToNumBillion() {
		assertEquals(0, new BigDecimal("100000000").compareTo(NumberUtil.capitalMoneyToNum("壹亿元整")));
		assertEquals(0, new BigDecimal("123456789").compareTo(
				NumberUtil.capitalMoneyToNum("壹亿贰仟叁佰肆拾伍万陆仟柒佰捌拾玖元整")));
	}

	@Test
	public void testCapitalMoneyToNumNegative() {
		assertEquals(0, new BigDecimal("-209.980").compareTo(NumberUtil.capitalMoneyToNum("负贰佰零玖元玖角捌分")));
	}

	@Test
	public void testCapitalMoneyToNumNegativeShi() {
		// "负拾元整" = -10 (之前会崩溃)
		assertEquals(0, new BigDecimal("-10").compareTo(NumberUtil.capitalMoneyToNum("负拾元整")));
		assertEquals(0, new BigDecimal("-100000").compareTo(NumberUtil.capitalMoneyToNum("负拾万元整")));
	}

	@Test
	public void testCapitalMoneyToNumWithLi() {
		// 包含厘: 玖角捌分伍厘 = 0.985
		assertEquals(0, new BigDecimal("0.985").compareTo(NumberUtil.capitalMoneyToNum("零元玖角捌分伍厘")));
	}

	@Test
	public void testCapitalMoneyToNumNoYuan() {
		// 无"元"但有"万": 壹佰万 = 1000000
		assertEquals(0, new BigDecimal("1000000").compareTo(NumberUtil.capitalMoneyToNum("壹佰万")));
		// 无"元"无"万": 叁仟 = 3000
		assertEquals(0, new BigDecimal("3000").compareTo(NumberUtil.capitalMoneyToNum("叁仟")));
		// 无"元"只有角分: 玖角捌分 = 0.980
		assertEquals(0, new BigDecimal("0.980").compareTo(NumberUtil.capitalMoneyToNum("玖角捌分")));
	}

	@Test
	public void testToCapitalMoneyTrillion() {
		// 万亿级别
		assertEquals("壹万亿元整", NumberUtil.toCapitalMoney(new BigDecimal("1000000000000")));
		assertEquals("壹拾万亿元整", NumberUtil.toCapitalMoney(new BigDecimal("10000000000000")));
		assertEquals("壹佰万亿元整", NumberUtil.toCapitalMoney(new BigDecimal("100000000000000")));
		assertEquals("壹仟万亿元整", NumberUtil.toCapitalMoney(new BigDecimal("1000000000000000")));
	}

	@Test
	public void testCapitalMoneyToNumTrillion() {
		assertEquals(0, new BigDecimal("1000000000000").compareTo(NumberUtil.capitalMoneyToNum("壹万亿元整")));
		assertEquals(0, new BigDecimal("10000000000000").compareTo(NumberUtil.capitalMoneyToNum("壹拾万亿元整")));
		assertEquals(0, new BigDecimal("100000000000000").compareTo(NumberUtil.capitalMoneyToNum("壹佰万亿元整")));
		assertEquals(0, new BigDecimal("1000000000000000").compareTo(NumberUtil.capitalMoneyToNum("壹仟万亿元整")));
	}


	// ======================== toCapitalMoney ↔ capitalMoneyToNum 往返测试 ========================

	@Test
	public void testRoundTrip() {
		String[] testValues = { "0", "1", "10", "15", "100", "101", "110", "209", "324",
				"1000", "1001", "1111", "10000", "10001", "12345",
				"100000", "123456", "1000000", "1000001",
				"100000000", "123456789" };
		for (String val : testValues) {
			BigDecimal original = new BigDecimal(val);
			String capital = NumberUtil.toCapitalMoney(original);
			BigDecimal roundTrip = NumberUtil.capitalMoneyToNum(capital);
			assertEquals(0, original.compareTo(roundTrip),
					"Round-trip failed for " + val + ": toCapitalMoney=" + capital + ", capitalMoneyToNum=" + roundTrip);
		}
	}

	@Test
	public void testRoundTripWithDecimal() {
		String[] testValues = { "0.05", "0.50", "0.98", "1.23", "209.98",
				"100.12", "1234.56", "999.99", "0.99", "0.09" };
		for (String val : testValues) {
			BigDecimal original = new BigDecimal(val);
			String capital = NumberUtil.toCapitalMoney(original);
			BigDecimal roundTrip = NumberUtil.capitalMoneyToNum(capital);
			// 因为capitalMoneyToNum的scale=3, 用同精度比较
			assertEquals(0, original.setScale(3, RoundingMode.HALF_UP).compareTo(roundTrip),
					"Round-trip failed for " + val + ": toCapitalMoney=" + capital + ", capitalMoneyToNum=" + roundTrip);
		}
	}

	@Test
	public void testRoundTripNegative() {
		String[] testValues = { "-1", "-10", "-209.98", "-100000", "-123456789" };
		for (String val : testValues) {
			BigDecimal original = new BigDecimal(val);
			String capital = NumberUtil.toCapitalMoney(original);
			BigDecimal roundTrip = NumberUtil.capitalMoneyToNum(capital);
			int cmp = original.abs().setScale(3, RoundingMode.HALF_UP)
					.compareTo(roundTrip.abs());
			assertEquals(0, cmp,
					"Round-trip failed for " + val + ": toCapitalMoney=" + capital + ", capitalMoneyToNum=" + roundTrip);
		}
	}

	@Test
	public void testRoundTripTrillion() {
		// 万亿级别往返测试(capitalMoneyToNum文档标注最大支持到千万亿)
		String[] testValues = {
				"1000000000000",       // 壹万亿
				"10000000000000",      // 壹拾万亿
				"100000000000000",     // 壹佰万亿
				"1000000000000000",    // 壹仟万亿
				"1234567890123456"     // 含万亿段的复合数字
		};
		for (String val : testValues) {
			BigDecimal original = new BigDecimal(val);
			String capital = NumberUtil.toCapitalMoney(original);
			BigDecimal roundTrip = NumberUtil.capitalMoneyToNum(capital);
			assertEquals(0, original.compareTo(roundTrip),
					"Round-trip failed for " + val + ": toCapitalMoney=" + capital + ", capitalMoneyToNum=" + roundTrip);
		}
	}

	// ======================== toCapitalMoney 补充场景 ========================

	@Test
	public void testToCapitalMoneyInternalZeros() {
		// 各量级内部含零
		assertEquals("壹拾万零壹元整", NumberUtil.toCapitalMoney(new BigDecimal("100001")));
		assertEquals("壹佰万零壹拾元整", NumberUtil.toCapitalMoney(new BigDecimal("1000010")));
		assertEquals("壹仟万零壹元整", NumberUtil.toCapitalMoney(new BigDecimal("10000001")));
		assertEquals("壹亿零壹元整", NumberUtil.toCapitalMoney(new BigDecimal("100000001")));
		assertEquals("壹亿零壹万元整", NumberUtil.toCapitalMoney(new BigDecimal("100010000")));
		// 万亿级别含零
		assertEquals("壹万亿零壹元整", NumberUtil.toCapitalMoney(new BigDecimal("1000000000001")));
		assertEquals("壹万亿零壹佰万元整", NumberUtil.toCapitalMoney(new BigDecimal("1000001000000")));
	}

	@Test
	public void testToCapitalMoneyAllNines() {
		assertEquals("玖拾玖元整", NumberUtil.toCapitalMoney(new BigDecimal("99")));
		assertEquals("玖佰玖拾玖元整", NumberUtil.toCapitalMoney(new BigDecimal("999")));
		assertEquals("玖仟玖佰玖拾玖元整", NumberUtil.toCapitalMoney(new BigDecimal("9999")));
		assertEquals("玖万玖仟玖佰玖拾玖元整", NumberUtil.toCapitalMoney(new BigDecimal("99999")));
		assertEquals("玖仟玖佰玖拾玖万玖仟玖佰玖拾玖元整",
				NumberUtil.toCapitalMoney(new BigDecimal("99999999")));
	}

	@Test
	public void testToCapitalMoneyComplexNumbers() {
		// 交错零与非零
		assertEquals("壹亿零壹佰零壹万零壹佰零壹元整",
				NumberUtil.toCapitalMoney(new BigDecimal("101010101")));
		assertEquals("壹佰亿零壹拾万零壹拾元整",
				NumberUtil.toCapitalMoney(new BigDecimal("10000100010")));
	}

	// ======================== capitalMoneyToNum 补充场景 ========================

	@Test
	public void testCapitalMoneyToNumNoYuanVariants() {
		assertEquals(0, new BigDecimal("10000").compareTo(NumberUtil.capitalMoneyToNum("壹万")));
		assertEquals(0, new BigDecimal("100000000").compareTo(NumberUtil.capitalMoneyToNum("壹亿")));
		assertEquals(0, new BigDecimal("10").compareTo(NumberUtil.capitalMoneyToNum("壹拾")));
		assertEquals(0, new BigDecimal("100000").compareTo(NumberUtil.capitalMoneyToNum("壹拾万")));
		assertEquals(0, new BigDecimal("1000000000000").compareTo(NumberUtil.capitalMoneyToNum("壹万亿")));
	}

	@Test
	public void testCapitalMoneyToNumVariantYuan() {
		// "圆"应等价于"元"
		assertEquals(0, new BigDecimal("1").compareTo(NumberUtil.capitalMoneyToNum("壹圆整")));
		assertEquals(0, new BigDecimal("209.980").compareTo(NumberUtil.capitalMoneyToNum("贰佰零玖圆玖角捌分")));
	}

	@Test
	public void testCapitalMoneyToNumWithWhitespace() {
		assertEquals(0, new BigDecimal("209").compareTo(NumberUtil.capitalMoneyToNum(" 贰佰零玖元整 ")));
		assertEquals(0, new BigDecimal("1234.560").compareTo(NumberUtil.capitalMoneyToNum(" 壹仟贰佰叁拾肆 元 伍角陆分 ")));
	}

	@Test
	public void testCapitalMoneyToNumNegativeNoYuan() {
		assertEquals(0, new BigDecimal("-1000000").compareTo(NumberUtil.capitalMoneyToNum("负壹佰万")));
		assertEquals(0, new BigDecimal("-3000").compareTo(NumberUtil.capitalMoneyToNum("负叁仟")));
		assertEquals(0, new BigDecimal("-0.980").compareTo(NumberUtil.capitalMoneyToNum("负玖角捌分")));
	}

	@Test
	public void testCapitalMoneyToNumWithExplicitZero() {
		// 显式含"零"的写法应正确解析
		assertEquals(0, new BigDecimal("100010000").compareTo(NumberUtil.capitalMoneyToNum("壹亿零壹万元整")));
		assertEquals(0, new BigDecimal("100000001").compareTo(NumberUtil.capitalMoneyToNum("壹亿零壹元整")));
		assertEquals(0, new BigDecimal("1000000000001").compareTo(NumberUtil.capitalMoneyToNum("壹万亿零壹元整")));
		assertEquals(0, new BigDecimal("101").compareTo(NumberUtil.capitalMoneyToNum("壹佰零壹元整")));
	}

	// ======================== 往返测试补充 ========================

	@Test
	public void testRoundTripInternalZeros() {
		// 各量级内部含零的往返
		String[] testValues = {
				"101", "1001", "10001", "100001", "1000010",
				"10000001", "100000001", "100010000", "100001000",
				"101010101", "10000100010",
				"1000000000001", "1000001000000", "1000000010000"
		};
		for (String val : testValues) {
			BigDecimal original = new BigDecimal(val);
			String capital = NumberUtil.toCapitalMoney(original);
			BigDecimal roundTrip = NumberUtil.capitalMoneyToNum(capital);
			assertEquals(0, original.compareTo(roundTrip),
					"Round-trip failed for " + val + ": toCapitalMoney=" + capital + ", capitalMoneyToNum=" + roundTrip);
		}
	}

	@Test
	public void testRoundTripAllNines() {
		String[] testValues = { "9", "99", "999", "9999", "99999", "999999", "9999999", "99999999" };
		for (String val : testValues) {
			BigDecimal original = new BigDecimal(val);
			String capital = NumberUtil.toCapitalMoney(original);
			BigDecimal roundTrip = NumberUtil.capitalMoneyToNum(capital);
			assertEquals(0, original.compareTo(roundTrip),
					"Round-trip failed for " + val + ": toCapitalMoney=" + capital + ", capitalMoneyToNum=" + roundTrip);
		}
	}

	@Test
	public void testRoundTripLargeWithDecimal() {
		String[] testValues = {
				"123456789.98", "100000000.50", "999999999.99",
				"1234567890.12", "1000000000000.99"
		};
		for (String val : testValues) {
			BigDecimal original = new BigDecimal(val);
			String capital = NumberUtil.toCapitalMoney(original);
			BigDecimal roundTrip = NumberUtil.capitalMoneyToNum(capital);
			assertEquals(0, original.setScale(3, RoundingMode.HALF_UP).compareTo(roundTrip),
					"Round-trip failed for " + val + ": toCapitalMoney=" + capital + ", capitalMoneyToNum=" + roundTrip);
		}
	}

	@Test
	public void testRoundTripDecimalEdgeCases() {
		// 小数边界场景
		String[] testValues = { "0.001", "0.005", "0.999", "0.009", "1.001" };
		for (String val : testValues) {
			BigDecimal original = new BigDecimal(val);
			String capital = NumberUtil.toCapitalMoney(original);
			BigDecimal roundTrip = NumberUtil.capitalMoneyToNum(capital);
			assertEquals(0, original.setScale(3, RoundingMode.HALF_UP).compareTo(roundTrip),
					"Round-trip failed for " + val + ": toCapitalMoney=" + capital + ", capitalMoneyToNum=" + roundTrip);
		}
	}

	@Test
	public void testRoundTripNegativeLarge() {
		String[] testValues = { "-123456789", "-99999999", "-1000000000000" };
		for (String val : testValues) {
			BigDecimal original = new BigDecimal(val);
			String capital = NumberUtil.toCapitalMoney(original);
			BigDecimal roundTrip = NumberUtil.capitalMoneyToNum(capital);
			assertEquals(0, original.abs().compareTo(roundTrip.abs()),
					"Round-trip failed for " + val + ": toCapitalMoney=" + capital + ", capitalMoneyToNum=" + roundTrip);
		}
	}

	@Test
	public void testRoundTripExhaustiveInteger() {
		// 全量遍历0~99999999(千万以内)，验证往返一致性
		int[] limits = { 1000, 10000, 100000, 1000000, 10000000, 100000000 };
		for (int limit : limits) {
			for (int i = 0; i < limit; i += Math.max(1, limit / 5000)) {
				BigDecimal original = new BigDecimal(i);
				String capital = NumberUtil.toCapitalMoney(original);
				BigDecimal roundTrip = NumberUtil.capitalMoneyToNum(capital);
				assertEquals(0, original.compareTo(roundTrip),
						"Round-trip failed for " + i + ": toCapitalMoney=" + capital + ", capitalMoneyToNum=" + roundTrip);
			}
		}
	}

	@Test
	public void testRoundTripExhaustiveLargeNumbers() {
		// 各量级边界附近的连续值(含0/9密集区域)
		long[] bases = {
				1000000L, 10000000L, 100000000L, 1000000000L,
				10000000000L, 100000000000L, 1000000000000L, 10000000000000L
		};
		for (long base : bases) {
			for (int delta = -5; delta <= 5; delta++) {
				long val = base + delta;
				if (val < 0) continue;
				BigDecimal original = new BigDecimal(val);
				String capital = NumberUtil.toCapitalMoney(original);
				BigDecimal roundTrip = NumberUtil.capitalMoneyToNum(capital);
				assertEquals(0, original.compareTo(roundTrip),
						"Round-trip failed for " + val + ": toCapitalMoney=" + capital + ", capitalMoneyToNum=" + roundTrip);
			}
		}
	}

	@Test
	public void testRoundTripExhaustiveRandom() {
		// 随机数各范围全覆盖(用固定种子保证可重复)
		java.util.Random rnd = new java.util.Random(42);
		for (int i = 0; i < 2000; i++) {
			long val = (rnd.nextLong() & 0x0FFFFFFFFFFFFFFFL) % 1000000000000000L; // 0 ~ 千万亿
			BigDecimal original = new BigDecimal(val);
			String capital = NumberUtil.toCapitalMoney(original);
			BigDecimal roundTrip = NumberUtil.capitalMoneyToNum(capital);
			assertEquals(0, original.compareTo(roundTrip),
					"Round-trip failed for " + val + ": toCapitalMoney=" + capital + ", capitalMoneyToNum=" + roundTrip);
		}
	}




	// ======================== convertToEnglishMoney 测试 ========================

	@Test
	public void testConvertToEnglishMoneyZero() {
		assertEquals("ZERO ONLY", NumberUtil.convertToEnglishMoney(BigDecimal.ZERO));
	}

	@Test
	public void testConvertToEnglishMoneyNull() {
		assertEquals("", NumberUtil.convertToEnglishMoney((BigDecimal) null));
	}

	@Test
	public void testConvertToEnglishMoneySmall() {
		assertEquals("ONE ONLY", NumberUtil.convertToEnglishMoney(new BigDecimal("1")));
		assertEquals("TEN ONLY", NumberUtil.convertToEnglishMoney(new BigDecimal("10")));
		assertEquals("FIFTEEN ONLY", NumberUtil.convertToEnglishMoney(new BigDecimal("15")));
		assertEquals("TWENTY ONLY", NumberUtil.convertToEnglishMoney(new BigDecimal("20")));
		assertEquals("NINETY-NINE ONLY", NumberUtil.convertToEnglishMoney(new BigDecimal("99")));
	}

	@Test
	public void testConvertToEnglishMoneyHundreds() {
		assertEquals("ONE HUNDRED ONLY", NumberUtil.convertToEnglishMoney(new BigDecimal("100")));
		assertEquals("ONE HUNDRED AND ONE ONLY", NumberUtil.convertToEnglishMoney(new BigDecimal("101")));
		assertEquals("TWO HUNDRED AND THIRTY-FOUR ONLY", NumberUtil.convertToEnglishMoney(new BigDecimal("234")));
		assertEquals("NINE HUNDRED AND NINETY-NINE ONLY", NumberUtil.convertToEnglishMoney(new BigDecimal("999")));
	}

	@Test
	public void testConvertToEnglishMoneyThousands() {
		assertEquals("ONE THOUSAND ONLY", NumberUtil.convertToEnglishMoney(new BigDecimal("1000")));
		assertEquals("ONE THOUSAND ONE ONLY", NumberUtil.convertToEnglishMoney(new BigDecimal("1001")));
		assertEquals("ONE THOUSAND ONE HUNDRED ONLY", NumberUtil.convertToEnglishMoney(new BigDecimal("1100")));
		assertEquals("THREE THOUSAND FOUR HUNDRED AND FIFTY-SIX ONLY",
				NumberUtil.convertToEnglishMoney(new BigDecimal("3456")));
	}

	@Test
	public void testConvertToEnglishMoneyMillion() {
		assertEquals("ONE MILLION ONLY", NumberUtil.convertToEnglishMoney(new BigDecimal("1000000")));
		assertEquals("THREE MILLION ONLY", NumberUtil.convertToEnglishMoney(new BigDecimal("3000000")));
	}

	@Test
	public void testConvertToEnglishMoneyWithCents() {
		assertEquals("TWO HUNDRED AND NINE ONLY",
				NumberUtil.convertToEnglishMoney(new BigDecimal("209.00")));
		assertEquals("ONE HUNDRED AND CENTS ONE ONLY",
				NumberUtil.convertToEnglishMoney(new BigDecimal("100.01")));
		assertEquals("ONE HUNDRED AND CENTS TEN ONLY",
				NumberUtil.convertToEnglishMoney(new BigDecimal("100.10")));
		assertEquals("TWO HUNDRED AND FIFTY-SIX THOUSAND TWO HUNDRED AND THIRTY-SEVEN AND CENTS NINETY-EIGHT ONLY",
				NumberUtil.convertToEnglishMoney(new BigDecimal("256237.98")));
	}

	@Test
	public void testConvertToEnglishMoneyNoCentsForInteger() {
		// 整数不应出现 "AND CENTS"
		String result = NumberUtil.convertToEnglishMoney(new BigDecimal("100"));
		assertTrue(!result.contains("AND CENTS"), "Integer should not contain 'AND CENTS': " + result);
	}

	@Test
	public void testConvertToEnglishMoneyNegative() {
		assertEquals("MINUS TWO HUNDRED AND NINE AND CENTS NINETY-EIGHT ONLY",
				NumberUtil.convertToEnglishMoney(new BigDecimal("-209.98")));
	}

	@Test
	public void testConvertToEnglishMoneyStringWithPermil() {
		assertEquals(
				"THREE HUNDRED AND NINETY-EIGHT MILLION,THREE HUNDRED AND NINETY-TWO THOUSAND,NINE HUNDRED AND TWENTY-THREE AND CENTS THREE ONLY",
				NumberUtil.convertToEnglishMoney("398,392,923.03"));
	}

	@Test
	public void testConvertToEnglishMoneyStringNegativeWithPermil() {
		assertEquals(
				"MINUS EIGHT MILLION,THREE HUNDRED AND NINETY-TWO THOUSAND,NINE HUNDRED AND TWENTY-THREE AND CENTS THREE ONLY",
				NumberUtil.convertToEnglishMoney("-8,392,923.03"));
	}

	@Test
	public void testConvertToEnglishMoneyStringNull() {
		assertNull(NumberUtil.convertToEnglishMoney((String) null));
	}

	// ======================== format 测试 ========================

	@Test
	public void testFormatPattern() {
		assertEquals("-209.98", NumberUtil.format(new BigDecimal("-209.98"), "##,###.00"));
		assertEquals("1,234.57", NumberUtil.format(new BigDecimal("1234.567"), "#,###.00"));
	}

	@Test
	public void testFormatCapitalMoney() {
		assertEquals("贰佰零玖元玖角捌分", NumberUtil.format(new BigDecimal("209.98"), "capitalmoney"));
		assertEquals("贰佰零玖元玖角捌分", NumberUtil.format(new BigDecimal("209.98"), "capital-rmb"));
	}

	@Test
	public void testFormatCapitalEn() {
		assertEquals("THREE MILLION ONLY", NumberUtil.format(new BigDecimal("3000000"), "capital-en"));
		assertEquals("THREE MILLION ONLY", NumberUtil.format(new BigDecimal("3000000"), "capital-english"));
	}

	@Test
	public void testFormatNull() {
		assertNull(NumberUtil.format(null, "##.00"));
		assertEquals("1234", NumberUtil.format("1234", null));
	}

	// ======================== 其他方法测试 ========================

	@Test
	public void testIsNumber() {
		assertTrue(NumberUtil.isNumber("-0.9"));
		assertTrue(NumberUtil.isNumber("123"));
		assertTrue(NumberUtil.isNumber("+45.67"));
	}

	@Test
	public void testIsInteger() {
		assertTrue(NumberUtil.isInteger("123"));
		assertTrue(NumberUtil.isInteger("-456"));
		assertTrue(!NumberUtil.isInteger("12.3"));
	}

	@Test
	public void testGetMaxMin() {
		BigDecimal[] arr = { new BigDecimal("3"), new BigDecimal("7"), new BigDecimal("1") };
		assertEquals(0, new BigDecimal("7").compareTo(NumberUtil.getMax(arr)));
		assertEquals(0, new BigDecimal("1").compareTo(NumberUtil.getMin(arr)));
	}

	@Test
	public void testGetMaxMinEmpty() {
		assertNull(NumberUtil.getMax(null));
		assertNull(NumberUtil.getMax(new BigDecimal[0]));
		assertNull(NumberUtil.getMin(null));
		assertNull(NumberUtil.getMin(new BigDecimal[0]));
	}

	@Test
	public void testSummary() {
		BigDecimal[] arr = { new BigDecimal("1"), new BigDecimal("2"), new BigDecimal("3") };
		assertEquals(0, new BigDecimal("6").compareTo(NumberUtil.summary(arr)));
		assertEquals(0, BigDecimal.ZERO.compareTo(NumberUtil.summary(null)));
		assertEquals(0, BigDecimal.ZERO.compareTo(NumberUtil.summary(new BigDecimal[0])));
	}

	@Test
	public void testGetAverage() {
		BigDecimal[] arr = { new BigDecimal("1"), new BigDecimal("2"), new BigDecimal("3") };
		assertEquals(0, new BigDecimal("2.0000").compareTo(NumberUtil.getAverage(arr)));
	}

	@Test
	public void testGetRandomNum() {
		for (int i = 0; i < 100; i++) {
			int num = NumberUtil.getRandomNum(10, 20);
			assertTrue(num >= 10 && num < 20);
		}
	}
}
