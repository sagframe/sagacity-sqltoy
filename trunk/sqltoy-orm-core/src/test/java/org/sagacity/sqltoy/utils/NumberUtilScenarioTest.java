package org.sagacity.sqltoy.utils;

import static org.junit.jupiter.api.Assertions.assertTrue;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

/**
 * NumberUtil 大量场景覆盖测试:逐行输出"输入 --操作--> 实际输出 | 期望输出"并判定是否正确
 * 场景:1、数字格式化(千分位/百分号/科学计数/舍入模式/多locale);
 * 2、数字转英文金额;3、数字转中文(GB/T 15835读法规范);
 * 4、数字转大写金额(人行《正确填写票据和结算凭证的基本规定》);
 * 5、大写中文转数字;6、英文/西文数字转数字;7、大写金额转数字(含角分厘、圆异体字、负数、大额)
 */
public class NumberUtilScenarioTest {

	private List<String> failures;

	@BeforeEach
	public void setUp() {
		failures = new ArrayList<String>();
	}

	private void check(String no, String input, String action, String expected, Object actual) {
		String actualStr = (actual == null) ? "null" : actual.toString();
		boolean ok = expected.equals(actualStr);
		System.out.println("  " + (ok ? "√" : "×") + " [" + no + "] " + input + " --" + action + "--> " + actualStr
				+ " | 期望: " + expected);
		if (!ok) {
			failures.add("[" + no + "] " + input + " --" + action + "--> " + actualStr + " | 期望: " + expected);
		}
	}

	private void assertNoFailure(String scene) {
		assertTrue(failures.isEmpty(), scene + " 存在失败场景:\n" + String.join("\n", failures));
	}

	/** 场景一:数字格式化(千分位、百分号/千分号、货币符号、科学计数、舍入模式、多区域) */
	@Test
	public void scenario1Format() {
		System.out.println("\n========== 场景一:数字格式化 ==========");
		check("1-01", "1234567.891", "format(#,###.00)", "1,234,567.89", NumberUtil.format("1234567.891", "#,###.00"));
		check("1-02", "1234567.891", "format(#,###)", "1,234,568", NumberUtil.format("1234567.891", "#,###"));
		check("1-03", "3.14159", "format(0.00)", "3.14", NumberUtil.format("3.14159", "0.00"));
		check("1-04", "0.1234", "format(#.00%)", "12.34%", NumberUtil.format("0.1234", "#.00%"));
		check("1-05", "0.1234", "format(#.00‰)", "123.40‰", NumberUtil.format("0.1234", "#.00‰"));
		check("1-06", "1234.5", "format(¥#,##0.00)", "¥1,234.50", NumberUtil.format("1234.5", "¥#,##0.00"));
		check("1-07", "1234.5", "format(#,##0.##)", "1,234.5", NumberUtil.format("1234.5", "#,##0.##"));
		check("1-08", "1234.567", "format(0.###E0)", "1.235E3", NumberUtil.format("1234.567", "0.###E0"));
		// 舍入模式(标准BigDecimal语义)
		check("1-09", "2.345", "format(#.##,HALF_UP)", "2.35",
				NumberUtil.format("2.345", "#.##", RoundingMode.HALF_UP, null));
		check("1-10", "2.345", "format(#.##,DOWN)", "2.34", NumberUtil.format("2.345", "#.##", RoundingMode.DOWN, null));
		check("1-11", "2.341", "format(#.##,CEILING)", "2.35",
				NumberUtil.format("2.341", "#.##", RoundingMode.CEILING, null));
		check("1-12", "2.335", "format(#.##,HALF_EVEN)", "2.34",
				NumberUtil.format("2.335", "#.##", RoundingMode.HALF_EVEN, null));
		check("1-13", "2.325", "format(#.##,HALF_EVEN)", "2.32",
				NumberUtil.format("2.325", "#.##", RoundingMode.HALF_EVEN, null));
		// 不同输入类型
		check("1-14", "Integer 1234567", "format(#,###)", "1,234,567", NumberUtil.format(1234567, "#,###"));
		check("1-15", "Double 1234567.89", "format(#,##0.00)", "1,234,567.89",
				NumberUtil.format(1234567.89d, "#,##0.00"));
		check("1-16", "BigDecimal 1234.56", "format(#,##0.00)", "1,234.56",
				NumberUtil.format(new BigDecimal("1234.56"), "#,##0.00"));
		check("1-17", "带千分位串1,234,567.891", "format(#,###.00)", "1,234,567.89",
				NumberUtil.format("1,234,567.891", "#,###.00"));
		// locale对千分位/小数点符号的影响(国际标准)
		check("1-18", "1234567.891", "format(#,###.00,US)", "1,234,567.89",
				NumberUtil.format("1234567.891", "#,###.00", null, Locale.US));
		check("1-19", "1234567.891", "format(#,###.00,GERMANY)", "1.234.567,89",
				NumberUtil.format("1234567.891", "#,###.00", null, Locale.GERMANY));
		// 币种格式化与边界
		check("1-20", "1234.56", "formatCurrency(¤#,##0.00,US)", "$1,234.56",
				NumberUtil.formatCurrency("1234.56", "¤#,##0.00", Locale.US));
		check("1-21", "null", "format(#,###.00)", "null", NumberUtil.format(null, "#,###.00"));
		check("1-22", "1234.56", "format(null pattern)", "1234.56", NumberUtil.format("1234.56", null));
		check("1-23", "abc(非法值)", "format(#,###.00)", "abc", NumberUtil.format("abc", "#,###.00"));
		assertNoFailure("场景一:数字格式化");
	}

	/** 场景二:数字转英文金额(convertToEnglishMoney,国际英文大写惯例,AND CENTS/ONLY) */
	@Test
	public void scenario2English() {
		System.out.println("\n========== 场景二:数字转英文 ==========");
		check("2-01", "0", "convertToEnglishMoney", "ZERO ONLY", NumberUtil.convertToEnglishMoney(new BigDecimal("0")));
		check("2-02", "5", "convertToEnglishMoney", "FIVE ONLY", NumberUtil.convertToEnglishMoney(new BigDecimal("5")));
		check("2-03", "10", "convertToEnglishMoney", "TEN ONLY",
				NumberUtil.convertToEnglishMoney(new BigDecimal("10")));
		check("2-04", "15", "convertToEnglishMoney", "FIFTEEN ONLY",
				NumberUtil.convertToEnglishMoney(new BigDecimal("15")));
		check("2-05", "20", "convertToEnglishMoney", "TWENTY ONLY",
				NumberUtil.convertToEnglishMoney(new BigDecimal("20")));
		check("2-06", "25", "convertToEnglishMoney", "TWENTY-FIVE ONLY",
				NumberUtil.convertToEnglishMoney(new BigDecimal("25")));
		check("2-07", "100", "convertToEnglishMoney", "ONE HUNDRED ONLY",
				NumberUtil.convertToEnglishMoney(new BigDecimal("100")));
		check("2-08", "105", "convertToEnglishMoney", "ONE HUNDRED AND FIVE ONLY",
				NumberUtil.convertToEnglishMoney(new BigDecimal("105")));
		check("2-09", "115", "convertToEnglishMoney", "ONE HUNDRED AND FIFTEEN ONLY",
				NumberUtil.convertToEnglishMoney(new BigDecimal("115")));
		check("2-10", "1000", "convertToEnglishMoney", "ONE THOUSAND ONLY",
				NumberUtil.convertToEnglishMoney(new BigDecimal("1000")));
		check("2-11", "1001", "convertToEnglishMoney", "ONE THOUSAND ONE ONLY",
				NumberUtil.convertToEnglishMoney(new BigDecimal("1001")));
		check("2-12", "1234", "convertToEnglishMoney", "ONE THOUSAND TWO HUNDRED AND THIRTY-FOUR ONLY",
				NumberUtil.convertToEnglishMoney(new BigDecimal("1234")));
		check("2-13", "12345", "convertToEnglishMoney",
				"TWELVE THOUSAND THREE HUNDRED AND FORTY-FIVE ONLY",
				NumberUtil.convertToEnglishMoney(new BigDecimal("12345")));
		check("2-14", "1000000", "convertToEnglishMoney", "ONE MILLION ONLY",
				NumberUtil.convertToEnglishMoney(new BigDecimal("1000000")));
		check("2-15", "1000001", "convertToEnglishMoney", "ONE MILLION ONE ONLY",
				NumberUtil.convertToEnglishMoney(new BigDecimal("1000001")));
		check("2-16", "1000000000", "convertToEnglishMoney", "ONE BILLION ONLY",
				NumberUtil.convertToEnglishMoney(new BigDecimal("1000000000")));
		check("2-17", "1234567.89", "convertToEnglishMoney",
				"ONE MILLION TWO HUNDRED AND THIRTY-FOUR THOUSAND FIVE HUNDRED AND SIXTY-SEVEN AND CENTS EIGHTY-NINE ONLY",
				NumberUtil.convertToEnglishMoney(new BigDecimal("1234567.89")));
		check("2-18", "0.56", "convertToEnglishMoney", "ZERO AND CENTS FIFTY-SIX ONLY",
				NumberUtil.convertToEnglishMoney(new BigDecimal("0.56")));
		check("2-19", "-25.5", "convertToEnglishMoney", "MINUS TWENTY-FIVE AND CENTS FIFTY ONLY",
				NumberUtil.convertToEnglishMoney(new BigDecimal("-25.5")));
		check("2-20", "1234.567", "convertToEnglishMoney(厘截断到分)",
				"ONE THOUSAND TWO HUNDRED AND THIRTY-FOUR AND CENTS FIFTY-SIX ONLY",
				NumberUtil.convertToEnglishMoney(new BigDecimal("1234.567")));
		check("2-21", "1234.56", "format(capital-en)",
				"ONE THOUSAND TWO HUNDRED AND THIRTY-FOUR AND CENTS FIFTY-SIX ONLY",
				NumberUtil.format("1234.56", "capital-en"));
		check("2-22", "0.5", "convertToEnglishMoney(单位小数=50分)", "ZERO AND CENTS FIFTY ONLY",
				NumberUtil.convertToEnglishMoney(new BigDecimal("0.5")));
		assertNoFailure("场景二:数字转英文");
	}

	/** 场景三:数字转中文(GB/T 15835规范:最高位十位不加"一"、中间零补位、组单位万亿/亿) */
	@Test
	public void scenario3ChinaNumber() {
		System.out.println("\n========== 场景三:数字转大写中文 ==========");
		check("3-01", "0", "numberToChina", "零", NumberUtil.numberToChina(0));
		check("3-02", "5", "numberToChina", "五", NumberUtil.numberToChina(5));
		check("3-03", "10", "numberToChina", "十", NumberUtil.numberToChina(10));
		check("3-04", "15", "numberToChina", "十五", NumberUtil.numberToChina(15));
		check("3-05", "110", "numberToChina", "一百一十", NumberUtil.numberToChina(110));
		check("3-06", "111", "numberToChina", "一百一十一", NumberUtil.numberToChina(111));
		check("3-07", "105", "numberToChina", "一百零五", NumberUtil.numberToChina(105));
		check("3-08", "1005", "numberToChina", "一千零五", NumberUtil.numberToChina(1005));
		check("3-09", "1010", "numberToChina", "一千零一十", NumberUtil.numberToChina(1010));
		check("3-10", "1100", "numberToChina", "一千一百", NumberUtil.numberToChina(1100));
		check("3-11", "1234", "numberToChina", "一千二百三十四", NumberUtil.numberToChina(1234));
		check("3-12", "10000", "numberToChina", "一万", NumberUtil.numberToChina(10000));
		check("3-13", "10001", "numberToChina", "一万零一", NumberUtil.numberToChina(10001));
		check("3-14", "100000", "numberToChina", "十万", NumberUtil.numberToChina(100000));
		check("3-15", "1000000", "numberToChina", "一百万", NumberUtil.numberToChina(1000000));
		check("3-16", "123456789", "numberToChina", "一亿二千三百四十五万六千七百八十九",
				NumberUtil.numberToChina(123456789));
		check("3-17", "-123", "numberToChina", "负一百二十三", NumberUtil.numberToChina(-123));
		check("3-18", "-10", "numberToChina", "负十", NumberUtil.numberToChina(-10));
		check("3-19", "1234567890", "numberToChina", "十二亿三千四百五十六万七千八百九十",
				NumberUtil.numberToChina(1234567890));
		check("3-20", "100050000", "format(capital)", "一亿零五万", NumberUtil.format("100050000", "capital"));
		check("3-21", "1000000000000", "format(capital)", "一万亿", NumberUtil.format("1000000000000", "capital"));
		check("3-22", "123", "format(capital)", "一百二十三", NumberUtil.format("123", "capital"));
		assertNoFailure("场景三:数字转大写中文");
	}

	/** 场景四:数字转大写金额(人行规范:壹拾开头、零补位、不足一元从角分写起、整字规则) */
	@Test
	public void scenario4CapitalMoney() {
		System.out.println("\n========== 场景四:数字转大写金额 ==========");
		check("4-01", "0", "toCapitalMoney", "零元整", NumberUtil.toCapitalMoney(new BigDecimal("0")));
		check("4-02", "10", "toCapitalMoney", "壹拾元整", NumberUtil.toCapitalMoney(new BigDecimal("10")));
		check("4-03", "15.3", "toCapitalMoney", "壹拾伍元叁角", NumberUtil.toCapitalMoney(new BigDecimal("15.3")));
		check("4-04", "1409.50", "toCapitalMoney(人行示例)", "壹仟肆佰零玖元伍角",
				NumberUtil.toCapitalMoney(new BigDecimal("1409.50")));
		check("4-05", "6007.14", "toCapitalMoney(人行示例)", "陆仟零柒元壹角肆分",
				NumberUtil.toCapitalMoney(new BigDecimal("6007.14")));
		check("4-06", "325.04", "toCapitalMoney(角零分非零补零)", "叁佰贰拾伍元零肆分",
				NumberUtil.toCapitalMoney(new BigDecimal("325.04")));
		// 人行规定:万位为0、千位非0时"可写一个零字,也可不写零字",实现统一采用写零形式(合规)
		check("4-07", "107000.53", "toCapitalMoney(人行示例,零字两可)", "壹拾万零柒仟元伍角叁分",
				NumberUtil.toCapitalMoney(new BigDecimal("107000.53")));
		check("4-08", "0.05", "toCapitalMoney(不足一元)", "伍分", NumberUtil.toCapitalMoney(new BigDecimal("0.05")));
		check("4-09", "0.56", "toCapitalMoney(不足一元)", "伍角陆分", NumberUtil.toCapitalMoney(new BigDecimal("0.56")));
		check("4-10", "1234.56", "toCapitalMoney", "壹仟贰佰叁拾肆元伍角陆分",
				NumberUtil.toCapitalMoney(new BigDecimal("1234.56")));
		check("4-11", "1000000000", "toCapitalMoney", "壹拾亿元整",
				NumberUtil.toCapitalMoney(new BigDecimal("1000000000")));
		check("4-12", "-100.5", "toCapitalMoney(负数)", "负壹佰元伍角",
				NumberUtil.toCapitalMoney(new BigDecimal("-100.5")));
		check("4-13", "100.00", "toCapitalMoney", "壹佰元整", NumberUtil.toCapitalMoney(new BigDecimal("100.00")));
		check("4-14", "0.005", "toCapitalMoney(到厘)", "伍厘", NumberUtil.toCapitalMoney(new BigDecimal("0.005")));
		check("4-15", "123.005", "toCapitalMoney(角分零厘五)", "壹佰贰拾叁元零伍厘",
				NumberUtil.toCapitalMoney(new BigDecimal("123.005")));
		check("4-16", "6007.14", "format(capital-rmb)", "陆仟零柒元壹角肆分",
				NumberUtil.format("6007.14", "capital-rmb"));
		check("4-17", "1000000000000", "toCapitalMoney", "壹万亿元整",
				NumberUtil.toCapitalMoney(new BigDecimal("1000000000000")));
		check("4-18", "100050000", "toCapitalMoney(组间补零)", "壹亿零伍万元整",
				NumberUtil.toCapitalMoney(new BigDecimal("100050000")));
		assertNoFailure("场景四:数字转大写金额");
	}

	/** 场景五:大写中文转数字(capitalMoneyToNum,无元/角分的纯大写数字形式) */
	@Test
	public void scenario5ChinaToNum() {
		System.out.println("\n========== 场景五:大写中文转数字 ==========");
		check("5-01", "壹仟贰佰叁拾肆", "capitalMoneyToNum", "1234.000",
				NumberUtil.capitalMoneyToNum("壹仟贰佰叁拾肆").toPlainString());
		check("5-02", "叁仟", "capitalMoneyToNum", "3000.000",
				NumberUtil.capitalMoneyToNum("叁仟").toPlainString());
		check("5-03", "壹佰万", "capitalMoneyToNum", "1000000.000",
				NumberUtil.capitalMoneyToNum("壹佰万").toPlainString());
		check("5-04", "玖拾捌亿柒仟陆佰伍拾肆万叁仟贰佰壹拾元整", "capitalMoneyToNum", "9876543210",
				NumberUtil.capitalMoneyToNum("玖拾捌亿柒仟陆佰伍拾肆万叁仟贰佰壹拾元整").toPlainString());
		check("5-05", "壹拾亿零壹佰元整", "capitalMoneyToNum", "1000000100",
				NumberUtil.capitalMoneyToNum("壹拾亿零壹佰元整").toPlainString());
		// 票据历史写法:拾/佰/仟前无数字按壹拾/壹佰/壹仟解析
		check("5-06", "拾元整", "capitalMoneyToNum(历史写法)", "10",
				NumberUtil.capitalMoneyToNum("拾元整").toPlainString());
		// 小写中文数字(一千二百三十四)映射为大写后解析(与numberToChina形成往返)
		check("5-07", "一千二百三十四", "capitalMoneyToNum(小写中文)", "1234.000",
				NumberUtil.capitalMoneyToNum("一千二百三十四").toPlainString());
		check("5-09", "两千零一万五千三百", "capitalMoneyToNum(小写中文带万)", "20015300.000",
				NumberUtil.capitalMoneyToNum("两千零一万五千三百").toPlainString());
		check("5-10", "两角五分", "capitalMoneyToNum(小写零头)", "0.250",
				NumberUtil.capitalMoneyToNum("两角五分").toPlainString());
		// 票据可带"人民币"前缀(解析按单位锚定,前缀应不影响)
		check("5-08", "人民币壹仟贰佰元整", "capitalMoneyToNum(币种前缀)", "1200",
				NumberUtil.capitalMoneyToNum("人民币壹仟贰佰元整").toPlainString());
		assertNoFailure("场景五:大写中文转数字");
	}

	/** 场景六:英文/西文数字转数字(parseDecimal/parseDouble/parseFloat/parsePercent) */
	@Test
	public void scenario6Parse() {
		System.out.println("\n========== 场景六:英文(西文)数字转数字 ==========");
		check("6-01", "1234.56", "parseDecimal", "1234.56",
				NumberUtil.parseDecimal("1234.56", null, null) == null ? null
						: NumberUtil.parseDecimal("1234.56", null, null).stripTrailingZeros().toPlainString());
		check("6-02", "1,234.56(千分位)", "parseDecimal", "1234.56",
				NumberUtil.parseDecimal("1,234.56", null, null) == null ? null
						: NumberUtil.parseDecimal("1,234.56", null, null).stripTrailingZeros().toPlainString());
		check("6-03", "1,234.56", "parseDouble", "1234.56", NumberUtil.parseDouble("1,234.56", null, null));
		check("6-04", "3.14159", "parseFloat", "3.14159", NumberUtil.parseFloat("3.14159", null, null));
		check("6-05", "90%", "parsePercent", "0.9", NumberUtil.parsePercent("90%"));
		check("6-06", "abc(非法值)", "parseDecimal", "null", NumberUtil.parseDecimal("abc", null, null));
		// 英文金额大写转数字(englishMoneyToNum,2026-08-18新增实现)
		check("6-07", "ONE THOUSAND TWO HUNDRED AND THIRTY-FOUR", "englishMoneyToNum", "1234",
				NumberUtil.englishMoneyToNum("ONE THOUSAND TWO HUNDRED AND THIRTY-FOUR") == null ? null
						: NumberUtil.englishMoneyToNum("ONE THOUSAND TWO HUNDRED AND THIRTY-FOUR")
								.stripTrailingZeros().toPlainString());
		check("6-08", "ONE THOUSAND ... AND CENTS FIFTY-SIX ONLY(完整形式)", "englishMoneyToNum", "1234.56",
				NumberUtil.englishMoneyToNum("ONE THOUSAND TWO HUNDRED AND THIRTY-FOUR AND CENTS FIFTY-SIX ONLY")
						.stripTrailingZeros().toPlainString());
		check("6-09", "ZERO ONLY", "englishMoneyToNum", "0",
				NumberUtil.englishMoneyToNum("ZERO ONLY").stripTrailingZeros().toPlainString());
		check("6-10", "MINUS TWENTY-FIVE AND CENTS FIFTY ONLY(负数)", "englishMoneyToNum", "-25.5",
				NumberUtil.englishMoneyToNum("MINUS TWENTY-FIVE AND CENTS FIFTY ONLY").stripTrailingZeros()
						.toPlainString());
		check("6-11", "ONE MILLION ... AND CENTS EIGHTY-NINE ONLY(大额)", "englishMoneyToNum", "1234567.89",
				NumberUtil.englishMoneyToNum(
						"ONE MILLION TWO HUNDRED AND THIRTY-FOUR THOUSAND FIVE HUNDRED AND SIXTY-SEVEN AND CENTS EIGHTY-NINE ONLY")
						.stripTrailingZeros().toPlainString());
		check("6-12", "one thousand and thirty-four(小写混合)", "englishMoneyToNum", "1034",
				NumberUtil.englishMoneyToNum("one thousand and thirty-four").stripTrailingZeros().toPlainString());
		check("6-13", "98765432.11", "convertToEnglishMoney→englishMoneyToNum往返", "98765432.11",
				NumberUtil.englishMoneyToNum(NumberUtil.convertToEnglishMoney(new BigDecimal("98765432.11")))
						.stripTrailingZeros().toPlainString());
		check("6-14", "HELLO WORLD(非法值)", "englishMoneyToNum", "null",
				NumberUtil.englishMoneyToNum("HELLO WORLD") == null ? null
						: NumberUtil.englishMoneyToNum("HELLO WORLD").toPlainString());
		assertNoFailure("场景六:英文(西文)数字转数字");
	}

	/** 场景七:大写金额转数字(含角分厘、圆异体字、负数、零头、大额往返) */
	@Test
	public void scenario7MoneyToNum() {
		System.out.println("\n========== 场景七:大写金额转数字 ==========");
		check("7-01", "壹仟贰佰叁拾肆元伍角陆分", "capitalMoneyToNum", "1234.560",
				NumberUtil.capitalMoneyToNum("壹仟贰佰叁拾肆元伍角陆分").toPlainString());
		check("7-02", "壹佰元整", "capitalMoneyToNum", "100",
				NumberUtil.capitalMoneyToNum("壹佰元整").toPlainString());
		check("7-03", "壹拾元整", "capitalMoneyToNum", "10", NumberUtil.capitalMoneyToNum("壹拾元整").toPlainString());
		check("7-04", "伍分", "capitalMoneyToNum", "0.050", NumberUtil.capitalMoneyToNum("伍分").toPlainString());
		check("7-05", "玖角捌分", "capitalMoneyToNum", "0.980", NumberUtil.capitalMoneyToNum("玖角捌分").toPlainString());
		check("7-06", "壹仟肆佰零玖元伍角", "capitalMoneyToNum", "1409.500",
				NumberUtil.capitalMoneyToNum("壹仟肆佰零玖元伍角").toPlainString());
		check("7-07", "壹佰亿壹角(亿位带零头无元)", "capitalMoneyToNum", "10000000000.100",
				NumberUtil.capitalMoneyToNum("壹佰亿壹角").toPlainString());
		check("7-08", "壹仟万元整", "capitalMoneyToNum", "10000000",
				NumberUtil.capitalMoneyToNum("壹仟万元整").toPlainString());
		check("7-09", "零元整", "capitalMoneyToNum", "0", NumberUtil.capitalMoneyToNum("零元整").toPlainString());
		check("7-10", "壹仟圆整(圆异体字)", "capitalMoneyToNum", "1000",
				NumberUtil.capitalMoneyToNum("壹仟圆整").toPlainString());
		check("7-11", "负壹佰元伍角", "capitalMoneyToNum", "-100.500",
				NumberUtil.capitalMoneyToNum("负壹佰元伍角").toPlainString());
		// 往返一致性:数字→大写金额→数字
		check("7-12", "98765432.11", "toCapitalMoney→capitalMoneyToNum往返", "98765432.110",
				NumberUtil.capitalMoneyToNum(NumberUtil.toCapitalMoney(new BigDecimal("98765432.11"))).toPlainString());
		check("7-13", "100050000.56", "toCapitalMoney→capitalMoneyToNum往返", "100050000.560",
				NumberUtil.capitalMoneyToNum(NumberUtil.toCapitalMoney(new BigDecimal("100050000.56"))).toPlainString());
		assertNoFailure("场景七:大写金额转数字");
	}

	/** 场景八:超大数字互转(千万亿/兆/京级,中文万亿组合与英文QUADRILLION~DECILLION) */
	@Test
	public void scenario8HugeNumbers() {
		System.out.println("\n========== 场景八:超大数字互转 ==========");
		// 中文:千万亿级(10^15)双向
		check("8-01", "10^15(一千万亿)", "format(capital)", "一千万亿",
				NumberUtil.format("1" + "0".repeat(15), "capital"));
		check("8-02", "1234567890123456", "format(capital)", "一千二百三十四万亿五千六百七十八亿九千零一十二万三千四百五十六",
				NumberUtil.format("1234567890123456", "capital"));
		check("8-03", "1234567890123456.78", "toCapitalMoney",
				"壹仟贰佰叁拾肆万亿伍仟陆佰柒拾捌亿玖仟零壹拾贰万叁仟肆佰伍拾陆元柒角捌分",
				NumberUtil.toCapitalMoney(new BigDecimal("1234567890123456.78")));
		check("8-04", "1234567890123456.78", "toCapitalMoney→capitalMoneyToNum往返", "1234567890123456.780",
				NumberUtil.capitalMoneyToNum(NumberUtil.toCapitalMoney(new BigDecimal("1234567890123456.78")))
						.toPlainString());
		check("8-05", "999999999999999.99(千万亿级顶值)", "toCapitalMoney→capitalMoneyToNum往返",
				"999999999999999.990",
				NumberUtil.capitalMoneyToNum(NumberUtil.toCapitalMoney(new BigDecimal("999999999999999.99")))
						.toPlainString());
		// 中文:兆(10^16,组单位体系:万=10^4组、亿=10^8组、兆=10^16组、京=10^32组)、京(10^32)与上限(10^64)
		check("8-06", "10^16(壹兆)", "toCapitalMoney", "壹兆元整",
				NumberUtil.toCapitalMoney(new BigDecimal("1" + "0".repeat(16))));
		// 兆/京级反解(单位幂累加:万亿=10^12、万京=10^36、兆京=10^48、万亿兆京=10^60按幂相乘)
		check("8-07", "壹兆元整", "capitalMoneyToNum(兆级)", "10000000000000000",
				NumberUtil.capitalMoneyToNum("壹兆元整").toPlainString());
		check("8-16", "壹拾兆元整", "capitalMoneyToNum", "100000000000000000",
				NumberUtil.capitalMoneyToNum("壹拾兆元整").toPlainString());
		check("8-17", "壹万京元整(万京=10^36)", "capitalMoneyToNum",
				"1" + "0".repeat(36), NumberUtil.capitalMoneyToNum("壹万京元整").toPlainString());
		check("8-18", "壹兆京元整(兆京=10^48)", "capitalMoneyToNum",
				"1" + "0".repeat(48), NumberUtil.capitalMoneyToNum("壹兆京元整").toPlainString());
		check("8-19", "壹万亿兆京元整(上限组合单位10^60)", "toCapitalMoney→capitalMoneyToNum往返",
				"1" + "0".repeat(60),
				NumberUtil.capitalMoneyToNum(NumberUtil.toCapitalMoney(new BigDecimal("1" + "0".repeat(60))))
						.toPlainString());
		check("8-20", "12345678901234567890.12(兆/万亿/亿/万混合)", "toCapitalMoney→capitalMoneyToNum往返",
				"12345678901234567890.120",
				NumberUtil.capitalMoneyToNum(NumberUtil.toCapitalMoney(new BigDecimal("12345678901234567890.12")))
						.toPlainString());
		check("8-21", "10^32(一京)", "toCapitalMoney→capitalMoneyToNum往返", "1" + "0".repeat(32),
				NumberUtil.capitalMoneyToNum(NumberUtil.toCapitalMoney(new BigDecimal("1" + "0".repeat(32))))
						.toPlainString());
		check("8-22", "10^32(一京)", "format(capital)", "一京", NumberUtil.format("1" + "0".repeat(32), "capital"));
		String overflow = null;
		try {
			overflow = NumberUtil.toCapitalMoney(new BigDecimal("1" + "0".repeat(64))).toString();
		} catch (Exception e) {
			overflow = "异常:" + e.getMessage();
		}
		check("8-09", "10^64(超出上限)", "toCapitalMoney", "异常:数字超出支持的转换范围(10^64)", overflow);
		// 英文:QUADRILLION(千万亿)~DECILLION(10^33)双向
		check("8-10", "10^15(QUADRILLION)", "convertToEnglishMoney", "ONE QUADRILLION ONLY",
				NumberUtil.convertToEnglishMoney(new BigDecimal("1" + "0".repeat(15))));
		check("8-11", "1234567890123456.78", "convertToEnglishMoney",
				"ONE QUADRILLION TWO HUNDRED AND THIRTY-FOUR TRILLION FIVE HUNDRED AND SIXTY-SEVEN BILLION EIGHT HUNDRED AND NINETY MILLION ONE HUNDRED AND TWENTY-THREE THOUSAND FOUR HUNDRED AND FIFTY-SIX AND CENTS SEVENTY-EIGHT ONLY",
				NumberUtil.convertToEnglishMoney(new BigDecimal("1234567890123456.78")));
		check("8-12", "1234567890123456.78", "convertToEnglishMoney→englishMoneyToNum往返", "1234567890123456.78",
				NumberUtil.englishMoneyToNum(NumberUtil.convertToEnglishMoney(new BigDecimal("1234567890123456.78")))
						.stripTrailingZeros().toPlainString());
		check("8-13", "ONE QUADRILLION ONLY", "englishMoneyToNum", "1" + "0".repeat(15),
				NumberUtil.englishMoneyToNum("ONE QUADRILLION ONLY").toPlainString());
		check("8-14", "ONE DECILLION ONLY(10^33)", "englishMoneyToNum", "1" + "0".repeat(33),
				NumberUtil.englishMoneyToNum("ONE DECILLION ONLY").toPlainString());
		// 边界:BigDecimal科学计数法形式的超大数(toString产生1E+33)
		String sciNotation = null;
		try {
			sciNotation = NumberUtil.convertToEnglishMoney(new BigDecimal("1E33"));
		} catch (Exception e) {
			sciNotation = "异常:" + e.getMessage();
		}
		check("8-15", "1E33(科学计数法)", "convertToEnglishMoney", "ONE DECILLION ONLY", sciNotation);
		assertNoFailure("场景八:超大数字互转");
	}
}
