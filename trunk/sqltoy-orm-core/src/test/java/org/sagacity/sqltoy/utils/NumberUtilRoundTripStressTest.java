package org.sagacity.sqltoy.utils;

import static org.junit.jupiter.api.Assertions.assertTrue;

import java.math.BigDecimal;
import java.math.BigInteger;
import java.util.ArrayList;
import java.util.List;
import java.util.Random;

import org.junit.jupiter.api.Test;

/**
 * NumberUtil大写金额/中英文互转 海量压测与边缘验证:
 * 目标是"一次锤死"——系统遍历(全量小整数/万组/十的幂/零密集) + 22万次随机往返(固定种子可复现)
 * + 定向边缘(零值/纯零头/厘精度/负数/E计数法/量级边界/越界异常)全链路闭环
 */
public class NumberUtilRoundTripStressTest {

	private long passCnt = 0;

	private long failCnt = 0;

	private final List<String> failSamples = new ArrayList<String>();

	private void verify(String scene, String input, BigDecimal actual, BigDecimal expected) {
		if (actual != null && actual.compareTo(expected) == 0) {
			passCnt++;
		} else {
			failCnt++;
			if (failSamples.size() < 10) {
				failSamples.add("[" + scene + "] " + input + " 期望:" + expected.toPlainString() + " 实际:"
						+ (actual == null ? "null" : actual.toPlainString()));
			}
		}
	}

	private void section(String title, long cnt, long failBefore) {
		System.out.println("  " + (failCnt == failBefore ? "√" : "×") + " " + title + ": 共" + cnt + "项, 通过"
				+ (passCnt) + "项, 失败" + (failCnt - failBefore) + "项"
				+ (failCnt == failBefore ? "" : ",样本:" + failSamples));
	}

	@Test
	public void stressRoundTrip() {
		System.out.println("\n========== 一、系统遍历(全量覆盖关键形态) ==========");
		// 1. 全量1~9999:覆盖仟佰拾组内一切形态(中文大写+小写双链路)
		long failBefore = failCnt;
		long cnt = 0;
		for (int i = 1; i <= 9999; i++) {
			BigDecimal x = BigDecimal.valueOf(i);
			String capital = NumberUtil.toCapitalMoney(x);
			verify("全量整数大写", capital, NumberUtil.capitalMoneyToNum(capital), x);
			cnt++;
			String lower = NumberUtil.format(String.valueOf(i), "capital");
			verify("全量整数小写", lower, NumberUtil.capitalMoneyToNum(lower), x);
			cnt++;
		}
		section("1~9999全量整数 × (大写往返+小写往返)", cnt, failBefore);

		// 2. 万组全量:n*10000,覆盖万组与个组衔接的一切形态
		failBefore = failCnt;
		cnt = 0;
		for (int i = 1; i <= 9999; i++) {
			BigDecimal x = BigDecimal.valueOf(i * 10000L);
			String capital = NumberUtil.toCapitalMoney(x);
			verify("万组全量", capital, NumberUtil.capitalMoneyToNum(capital), x);
			cnt++;
		}
		section("1~9999万组(n×10000)大写往返", cnt, failBefore);

		// 3. 十的幂10^0~10^63:覆盖全部15种组合单位(万亿/兆/京/万亿兆京等)
		failBefore = failCnt;
		cnt = 0;
		for (int k = 0; k <= 63; k++) {
			BigDecimal x = new BigDecimal("1" + "0".repeat(k));
			String capital = NumberUtil.toCapitalMoney(x);
			verify("十的幂大写", capital, NumberUtil.capitalMoneyToNum(capital), x);
			cnt++;
			String lower = NumberUtil.format("1" + "0".repeat(k), "capital");
			verify("十的幂小写", lower, NumberUtil.capitalMoneyToNum(lower), x);
			cnt++;
			if (k <= 35) {
				String english = NumberUtil.convertToEnglishMoney(x);
				verify("十的幂英文", english, NumberUtil.englishMoneyToNum(english), x);
				cnt++;
			}
		}
		section("10^0~10^63幂 × (大写+小写),10^0~10^35 × 英文", cnt, failBefore);

		// 4. 单数字×幂:1~9 × 10^k,覆盖每位数字在各量级上的行为
		failBefore = failCnt;
		cnt = 0;
		for (int d = 1; d <= 9; d++) {
			for (int k = 0; k <= 63; k++) {
				BigDecimal x = new BigDecimal(String.valueOf(d) + "0".repeat(k));
				String capital = NumberUtil.toCapitalMoney(x);
				verify("单数字×幂大写", capital, NumberUtil.capitalMoneyToNum(capital), x);
				cnt++;
				if (k <= 35) {
					String english = NumberUtil.convertToEnglishMoney(x);
					verify("单数字×幂英文", english, NumberUtil.englishMoneyToNum(english), x);
					cnt++;
				}
			}
		}
		section("1~9 × 10^0~10^63 大写,1~9 × 10^0~10^35 英文", cnt, failBefore);

		// 5. 零密集模式:10^m±1、10^m+10^(m-1)等,覆盖跨组连续零
		failBefore = failCnt;
		cnt = 0;
		for (int m = 1; m <= 63; m++) {
			BigDecimal base = new BigDecimal("1" + "0".repeat(m));
			for (BigDecimal x : new BigDecimal[] { base.add(BigDecimal.ONE), base.subtract(BigDecimal.ONE),
					base.add(new BigDecimal("1" + "0".repeat(Math.max(0, m - 1)))) }) {
				String capital = NumberUtil.toCapitalMoney(x);
				verify("零密集大写", capital, NumberUtil.capitalMoneyToNum(capital), x);
				cnt++;
				if (x.compareTo(new BigDecimal("1" + "0".repeat(36))) < 0) {
					String english = NumberUtil.convertToEnglishMoney(x);
					verify("零密集英文", english, NumberUtil.englishMoneyToNum(english), x);
					cnt++;
				}
			}
		}
		section("零密集模式(10^m±1、10^m+10^(m-1),m=1~63)", cnt, failBefore);

		System.out.println("\n========== 二、随机海量压测(种子20260818,可复现) ==========");
		Random rnd = new Random(20260818L);
		// 6. 中文大写随机往返10万次:整数位1~63位,小数0~3位(角分厘),10%负数,偶发stripTrailingZeros
		failBefore = failCnt;
		cnt = 0;
		for (int i = 0; i < 100000; i++) {
			BigDecimal x = randomValue(rnd, 63, 3);
			String capital = NumberUtil.toCapitalMoney(x);
			verify("随机中文大写", capital, NumberUtil.capitalMoneyToNum(capital), x);
			cnt++;
		}
		section("中文大写随机往返 100000 次", cnt, failBefore);

		// 7. 小写中文随机往返2万次(整数,走numberToChina小写→capitalMoneyToNum映射链路)
		failBefore = failCnt;
		cnt = 0;
		for (int i = 0; i < 20000; i++) {
			BigDecimal x = randomValue(rnd, 63, 0);
			String lower = NumberUtil.format(x.toPlainString(), "capital");
			verify("随机小写", lower, NumberUtil.capitalMoneyToNum(lower), x);
			cnt++;
		}
		section("小写中文随机往返 20000 次", cnt, failBefore);

		// 8. 英文随机往返10万次:整数位1~36位(英文上限10^36-1),小数0~2位(角分),10%负数
		failBefore = failCnt;
		cnt = 0;
		for (int i = 0; i < 100000; i++) {
			BigDecimal x = randomValue(rnd, 36, 2);
			String english = NumberUtil.convertToEnglishMoney(x);
			verify("随机英文", english, NumberUtil.englishMoneyToNum(english), x);
			cnt++;
		}
		section("英文随机往返 100000 次", cnt, failBefore);

		System.out.println("\n========== 三、定向边缘(逐行判定) ==========");
		edge("零值", new BigDecimal("0"));
		edge("零值带小数", new BigDecimal("0.000"));
		edge("纯零头-分", new BigDecimal("0.05"));
		edge("纯零头-角", new BigDecimal("0.50"));
		edge("纯零头-厘", new BigDecimal("0.001"));
		edge("纯零头-九厘", new BigDecimal("0.009"));
		edge("纯零头-角分厘全9", new BigDecimal("0.999"));
		edge("壹元零伍厘", new BigDecimal("1.005"));
		edge("负数纯零头", new BigDecimal("-0.01"));
		edge("负数大额", new BigDecimal("-98765432109876543210.12"));
		edge("厘后截断(角分厘精度边界)", new BigDecimal("0.1234"), new BigDecimal("0.123"));
		edge("63位顶值", new BigDecimal("9".repeat(63)));
		edge("10^60组合单位顶值", new BigDecimal("1" + "0".repeat(60)));
		edge("兆亿万混合", new BigDecimal("12345678901234567890.12"));
		edge("E计数法1E15", new BigDecimal("1E15"));
		edge("stripTrailingZeros大整数", new BigDecimal("1000000000000000").stripTrailingZeros());
		edge("万亿边界10^12", new BigDecimal("1000000000000"));
		edge("千万亿顶值", new BigDecimal("999999999999999.99"));

		System.out.println("\n========== 四、固定输入与边界契约 ==========");
		check("固定输入", "零元整", "0", NumberUtil.capitalMoneyToNum("零元整").toPlainString());
		check("固定输入", "拾元整(历史写法)", "10", NumberUtil.capitalMoneyToNum("拾元整").toPlainString());
		check("固定输入", "人民币壹仟贰佰元整", "1200", NumberUtil.capitalMoneyToNum("人民币壹仟贰佰元整").toPlainString());
		check("固定输入", "壹仟圆整(圆异体)", "1000", NumberUtil.capitalMoneyToNum("壹仟圆整").toPlainString());
		check("固定输入", "两千零一万五千三百(小写带万)", "20015300.000",
				NumberUtil.capitalMoneyToNum("两千零一万五千三百").toPlainString());
		check("固定输入", "ZERO ONLY", "0", NumberUtil.englishMoneyToNum("ZERO ONLY").toPlainString());
		check("固定输入", "MINUS TWENTY-FIVE AND CENTS FIFTY ONLY", "-25.5",
				NumberUtil.englishMoneyToNum("MINUS TWENTY-FIVE AND CENTS FIFTY ONLY").stripTrailingZeros()
						.toPlainString());
		// 越界契约:中文10^64与英文10^36给出明确异常而非越界或静默错误
		String overCn = null;
		try {
			overCn = NumberUtil.toCapitalMoney(new BigDecimal("1" + "0".repeat(64))).toString();
		} catch (Exception e) {
			overCn = "异常:" + e.getMessage();
		}
		check("越界契约", "10^64中文", "异常:数字超出支持的转换范围(10^64)", overCn);
		String overEn = null;
		try {
			overEn = NumberUtil.convertToEnglishMoney(new BigDecimal("1" + "0".repeat(36)));
		} catch (Exception e) {
			overEn = "异常:" + e.getMessage();
		}
		check("越界契约", "10^36英文", "异常:数字超出支持的转换范围(10^36)", overEn);

		System.out.println("\n========== 压测总计:" + (passCnt + failCnt) + "项,通过" + passCnt + "项,失败" + failCnt
				+ "项 ==========");
		assertTrue(failCnt == 0, "压测存在失败样本:\n" + String.join("\n", failSamples));
	}

	private void edge(String title, BigDecimal x) {
		edge(title, x, x);
	}

	private void edge(String title, BigDecimal x, BigDecimal expect) {
		String capital = NumberUtil.toCapitalMoney(x);
		BigDecimal cnBack = NumberUtil.capitalMoneyToNum(capital);
		boolean cnOk = cnBack != null && cnBack.compareTo(expect) == 0;
		System.out.println("  " + (cnOk ? "√" : "×") + " [中文] " + title + ": " + x.toPlainString() + " --[" + capital
				+ "]--> " + (cnBack == null ? "null" : cnBack.toPlainString()));
		if (!cnOk) {
			failCnt++;
			failSamples.add("[定向边缘中文]" + title + " " + x.toPlainString() + " 期望:" + expect.toPlainString());
		} else {
			passCnt++;
		}
		if (x.abs().compareTo(new BigDecimal("1" + "0".repeat(36))) < 0
				&& x.scale() <= 2 && x.compareTo(BigDecimal.ZERO) != 0) {
			String english = NumberUtil.convertToEnglishMoney(x);
			BigDecimal enBack = NumberUtil.englishMoneyToNum(english);
			boolean enOk = enBack != null && enBack.compareTo(expect) == 0;
			System.out.println("  " + (enOk ? "√" : "×") + " [英文] " + title + ": " + x.toPlainString() + " --> "
					+ (enBack == null ? "null" : enBack.toPlainString()));
			if (!enOk) {
				failCnt++;
				failSamples.add("[定向边缘英文]" + title + " " + x.toPlainString());
			} else {
				passCnt++;
			}
		}
	}

	private void check(String scene, String input, String expected, String actual) {
		boolean ok = expected.equals(actual);
		System.out.println("  " + (ok ? "√" : "×") + " [" + scene + "] " + input + " --> " + actual + " | 期望: "
				+ expected);
		if (!ok) {
			failCnt++;
			failSamples.add("[" + scene + "]" + input + " 实际:" + actual + " 期望:" + expected);
		} else {
			passCnt++;
		}
	}

	/**
	 * 生成随机金额:整数位1~maxDigits位,小数0~maxFrac位,10%负数,
	 * 偶发stripTrailingZeros构造负scale的E计数法形式(BigDecimal科学计数法路径)
	 */
	private BigDecimal randomValue(Random rnd, int maxDigits, int maxFrac) {
		int digits = 1 + rnd.nextInt(maxDigits);
		BigInteger intPart = new BigInteger(digits, rnd);
		if (intPart.signum() == 0) {
			intPart = BigInteger.ONE;
		}
		String text = intPart.toString();
		if (maxFrac > 0) {
			int fracLen = rnd.nextInt(maxFrac + 1);
			if (fracLen > 0) {
				int frac = rnd.nextInt((int) Math.pow(10, fracLen));
				text = text + "." + String.format("%0" + fracLen + "d", frac);
			}
		}
		BigDecimal x = new BigDecimal(text);
		if (rnd.nextInt(10) == 0) {
			x = x.negate();
		}
		if (x.scale() == 0 && rnd.nextInt(50) == 0) {
			x = x.stripTrailingZeros();
		}
		return x;
	}
}
