package org.sagacity.sqltoy.utils;

import static org.junit.jupiter.api.Assertions.assertTrue;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Random;

import org.junit.jupiter.api.Test;

/**
 * DateUtil海量压测与边缘验证(与NumberUtilRoundTripStressTest同风格的"一次锤死"):
 * 1)系统遍历:全年365天×多形态、12个月英文全称/缩称、1~31日序数词、0~23时、闰年体系;
 * 2)随机压测(种子20260818可复现):随机日期时间(1583~9999年)显式格式往返、
 *   跨10种格式自动解析等价、毫秒/纳秒精度、13位毫秒时间戳、中文数字日期、英文日期、format2China;
 * 3)定向边缘:null/空白/非法值/时区后缀/ISO/单位数补零/纯时间/宽松回滚契约/1582历法分界说明
 * 注:1582-10-15之前为儒略历/前推格里历差异区(业务不建议),不在契约范围
 */
public class DateUtilRoundTripStressTest {

	private long passCnt = 0;

	private long failCnt = 0;

	private final List<String> failSamples = new ArrayList<String>();

	private static final String[] CN_DIGITS = { "零", "一", "二", "三", "四", "五", "六", "七", "八", "九" };

	private void verify(String scene, String input, String actual, String expected) {
		if (expected.equals(actual)) {
			passCnt++;
		} else {
			failCnt++;
			if (failSamples.size() < 10) {
				failSamples.add("[" + scene + "] " + input + " 期望:" + expected + " 实际:" + actual);
			}
		}
	}

	private void section(String title, long cnt, long failBefore) {
		System.out.println("  " + (failCnt == failBefore ? "√" : "×") + " " + title + ": 共" + cnt + "项, 本区失败"
				+ (failCnt - failBefore) + "项" + (failCnt == failBefore ? "" : ",样本:" + failSamples));
	}

	/** 数值(0~99)转中文数字,如15=十五、20=二十、21=二十一 */
	private static String toCn(int value) {
		if (value <= 0) {
			return CN_DIGITS[0];
		}
		if (value < 10) {
			return CN_DIGITS[value];
		}
		if (value < 20) {
			return "十" + (value % 10 == 0 ? "" : CN_DIGITS[value % 10]);
		}
		return CN_DIGITS[value / 10] + "十" + (value % 10 == 0 ? "" : CN_DIGITS[value % 10]);
	}

	private static String ordinal(int day) {
		if (day % 10 == 1 && day != 11) {
			return day + "st";
		}
		if (day % 10 == 2 && day != 12) {
			return day + "nd";
		}
		if (day % 10 == 3 && day != 13) {
			return day + "rd";
		}
		return day + "th";
	}

	private LocalDateTime randomDateTime(Random rnd) {
		int year = 1583 + rnd.nextInt(8417);
		int month = 1 + rnd.nextInt(12);
		int day = 1 + rnd.nextInt(LocalDate.of(year, month, 1).lengthOfMonth());
		return LocalDateTime.of(year, month, day, rnd.nextInt(24), rnd.nextInt(60), rnd.nextInt(60));
	}

	@Test
	public void stressRoundTrip() {
		Random rnd = new Random(20260818L);
		System.out.println("\n========== 一、系统遍历 ==========");
		// 1. 2026全年365天×6种形态:canonical/斜杠/点号/紧凑/中文数字/英文
		long failBefore = failCnt;
		long cnt = 0;
		LocalDate cursor = LocalDate.of(2026, 1, 1);
		while (cursor.getYear() == 2026) {
			LocalDateTime dt = cursor.atTime(12, 30, 45);
			String canonical = DateUtil.formatDate(dt, "yyyy-MM-dd");
			for (String pattern : new String[] { "yyyy-MM-dd", "yyyy/MM/dd", "yyyy.MM.dd", "yyyyMMdd" }) {
				verify("全年形态", pattern, DateUtil.formatDate(DateUtil.parse(DateUtil.formatDate(dt, pattern), pattern),
						"yyyy-MM-dd"), canonical);
				cnt++;
			}
			String china = String.join("", "二〇二六", "年", toCn(cursor.getMonthValue()), "月", toCn(cursor.getDayOfMonth()),
					"日");
			verify("全年中文数字", china, DateUtil.parseChinaDate(china, "yyyy-MM-dd"), canonical);
			cnt++;
			String english = DateUtil.formatDate(dt, "MMM d, yyyy", Locale.ENGLISH);
			verify("全年英文", english, DateUtil.formatDate(DateUtil.parseString(english), "yyyy-MM-dd"), canonical);
			cnt++;
			cursor = cursor.plusDays(1);
		}
		section("2026全年365天 × (横杠/斜杠/点号/紧凑/中文数字/英文)", cnt, failBefore);

		// 2. 12个月英文全称/缩称/日在前/横杠形式
		failBefore = failCnt;
		cnt = 0;
		for (int month = 1; month <= 12; month++) {
			LocalDateTime dt = LocalDate.of(2026, month, 18).atTime(8, 9, 10);
			String expect = DateUtil.formatDate(dt, "yyyy-MM-dd");
			String[][] forms = { { "MMMM d, yyyy", "August 18, 2026" }, { "MMM d, yyyy", "Aug 18, 2026" },
					{ "d MMMM yyyy", "18 August 2026" }, { "d-MMM-yyyy", "18-Aug-2026" } };
			for (String[] form : forms) {
				String text = DateUtil.formatDate(dt, form[0], Locale.ENGLISH);
				verify("英文月", text, DateUtil.formatDate(DateUtil.parseString(text), "yyyy-MM-dd"), expect);
				cnt++;
			}
		}
		section("12个月 × (英文全称/缩称/日在前/横杠)", cnt, failBefore);

		// 3. 1~31日序数词形式(1st/2nd/3rd/4th.../21st/31st)
		failBefore = failCnt;
		cnt = 0;
		for (int day = 1; day <= 31; day++) {
			LocalDateTime dt = LocalDate.of(2026, 8, day).atStartOfDay();
			String text = ordinal(day) + " August 2026";
			verify("序数词", text, DateUtil.formatDate(DateUtil.parseString(text), "yyyy-MM-dd"),
					DateUtil.formatDate(dt, "yyyy-MM-dd"));
			cnt++;
		}
		section("1~31日序数词(1st~31st)", cnt, failBefore);

		// 4. 0~23时全量与分秒边界
		failBefore = failCnt;
		cnt = 0;
		for (int hour = 0; hour <= 23; hour++) {
			String text = String.format("2026-08-18 %02d:30:45", hour);
			verify("小时全量", text, DateUtil.formatDate(DateUtil.parseString(text), "yyyy-MM-dd HH:mm:ss"), text);
			cnt++;
		}
		for (int minute : new int[] { 0, 1, 9, 10, 29, 30, 31, 59 }) {
			String text = String.format("2026-08-18 12:%02d:45", minute);
			verify("分钟边界", text, DateUtil.formatDate(DateUtil.parseString(text), "yyyy-MM-dd HH:mm:ss"), text);
			cnt++;
		}
		for (int second : new int[] { 0, 1, 9, 10, 29, 30, 31, 59 }) {
			String text = String.format("2026-08-18 12:30:%02d", second);
			verify("秒钟边界", text, DateUtil.formatDate(DateUtil.parseString(text), "yyyy-MM-dd HH:mm:ss"), text);
			cnt++;
		}
		section("0~23时全量 + 分/秒边界值", cnt, failBefore);

		// 5. 闰年体系(1900/2100非闰、2000/2024/2028闰)
		failBefore = failCnt;
		cnt = 0;
		for (String[] leap : new String[][] { { "20240229", "2024-02-29" }, { "20280229", "2028-02-29" },
				{ "20000229", "2000-02-29" }, { "19000229", "1900-03-01" }, { "20260229", "2026-03-01" },
				{ "20260228", "2026-02-28" } }) {
			verify("闰年体系", leap[0], DateUtil.formatDate(DateUtil.parse(leap[0], null), "yyyy-MM-dd"), leap[1]);
			cnt++;
		}
		section("闰年体系(含1900非闰/2000闰及宽松回滚契约)", cnt, failBefore);

		System.out.println("\n========== 二、随机海量压测(种子20260818) ==========");
		// 6. 随机日期时间显式格式往返(模式池含中文单位/时分/毫秒/时间专格式)
		failBefore = failCnt;
		cnt = 0;
		String[][] pool = { { "yyyy-MM-dd HH:mm:ss", "yyyy-MM-dd HH:mm:ss" },
				{ "yyyy/MM/dd HH:mm:ss", "yyyy-MM-dd HH:mm:ss" }, { "yyyy.MM.dd HH:mm:ss", "yyyy-MM-dd HH:mm:ss" },
				{ "yyyy-MM-dd HH:mm", "yyyy-MM-dd HH:mm" }, { "yyyyMMddHHmmss", "yyyy-MM-dd HH:mm:ss" },
				{ "MM/dd/yyyy", "yyyy-MM-dd" }, { "dd/MM/yyyy", "yyyy-MM-dd" },
				{ "yyyy年MM月dd日", "yyyy-MM-dd" }, { "yyyy-MM-dd", "yyyy-MM-dd" },
				{ "HH:mm:ss", "HH:mm:ss" } };
		for (int i = 0; i < 15000; i++) {
			LocalDateTime dt = randomDateTime(rnd);
			String[] form = pool[rnd.nextInt(pool.length)];
			String text = DateUtil.formatDate(dt, form[0]);
			verify("随机格式往返", text, DateUtil.formatDate(DateUtil.parse(text, form[0]), form[1]),
					DateUtil.formatDate(dt, form[1]));
			cnt++;
		}
		section("随机显式格式往返 15000 次", cnt, failBefore);

		// 7. 跨10种格式自动解析等价(同一时刻任意形态进出结果一致)
		failBefore = failCnt;
		cnt = 0;
		for (int i = 0; i < 15000; i++) {
			LocalDateTime dt = randomDateTime(rnd);
			String expectDate = DateUtil.formatDate(dt, "yyyy-MM-dd");
			String expectTime = DateUtil.formatDate(dt, "yyyy-MM-dd HH:mm:ss");
			List<String[]> forms = new ArrayList<String[]>();
			forms.add(new String[] { DateUtil.formatDate(dt, "yyyy-MM-dd"), expectDate });
			forms.add(new String[] { DateUtil.formatDate(dt, "yyyy/MM/dd"), expectDate });
			forms.add(new String[] { DateUtil.formatDate(dt, "yyyy.MM.dd"), expectDate });
			forms.add(new String[] { DateUtil.formatDate(dt, "yyyyMMdd"), expectDate });
			forms.add(new String[] { dt.getYear() + "年" + dt.getMonthValue() + "月" + dt.getDayOfMonth() + "日",
					expectDate });
			forms.add(new String[] { DateUtil.formatDate(dt, "yyyy-MM-dd HH:mm:ss"), expectTime });
			forms.add(new String[] { DateUtil.formatDate(dt, "yyyy-MM-dd'T'HH:mm:ss"), expectTime });
			forms.add(new String[] { DateUtil.formatDate(dt, "yyyyMMddHHmmss"), expectTime });
			forms.add(new String[] { DateUtil.formatDate(dt, "MMM d, yyyy", Locale.ENGLISH), expectDate });
			forms.add(new String[] { DateUtil.formatDate(dt, "d-MMM-yyyy", Locale.ENGLISH), expectDate });
			for (String[] form : forms) {
				verify("跨格式等价", form[0], DateUtil.formatDate(DateUtil.parseString(form[0]), form[1]), form[1]);
				cnt++;
			}
		}
		section("跨10种格式自动解析等价 15000×10 次", cnt, failBefore);

		// 8. 毫秒/纳秒精度往返
		failBefore = failCnt;
		cnt = 0;
		for (int i = 0; i < 10000; i++) {
			LocalDateTime dt = randomDateTime(rnd).withNano(1 + rnd.nextInt(999999999));
			String text = DateUtil.formatDate(dt, "yyyy-MM-dd HH:mm:ss.SSS");
			verify("毫秒精度", text, DateUtil.formatDate(DateUtil.parseString(text), "yyyy-MM-dd HH:mm:ss.SSS"),
					DateUtil.formatDate(dt, "yyyy-MM-dd HH:mm:ss.SSS"));
			cnt++;
		}
		section("毫秒/纳秒精度往返 10000 次", cnt, failBefore);

		// 9. 13位毫秒时间戳(2001~2286年范围,13位为时间戳唯一无歧义区间)
		failBefore = failCnt;
		cnt = 0;
		for (int i = 0; i < 10000; i++) {
			long millis = (long) (1e12 + rnd.nextDouble() * 9e12);
			String text = String.valueOf(millis);
			verify("毫秒时间戳", text, DateUtil.formatDate(DateUtil.parseString(text), "yyyy-MM-dd HH:mm:ss"),
					DateUtil.formatDate(new java.util.Date(millis), "yyyy-MM-dd HH:mm:ss"));
			cnt++;
		}
		section("13位毫秒时间戳 10000 次", cnt, failBefore);

		// 10. 随机中文数字日期(年月日中文数字全形态)
		failBefore = failCnt;
		cnt = 0;
		for (int i = 0; i < 10000; i++) {
			LocalDate d = randomDateTime(rnd).toLocalDate();
			StringBuilder china = new StringBuilder();
			for (char c : String.valueOf(d.getYear()).toCharArray()) {
				china.append(CN_DIGITS[c - '0']);
			}
			china.append("年").append(toCn(d.getMonthValue())).append("月").append(toCn(d.getDayOfMonth())).append("日");
			String text = china.toString();
			verify("随机中文数字", text, DateUtil.parseChinaDate(text, "yyyy-MM-dd"),
					DateUtil.formatDate(d, "yyyy-MM-dd"));
			cnt++;
		}
		section("随机中文数字日期 10000 次", cnt, failBefore);

		// 11. 随机英文日期形式(含星期全称/缩称、日在前、分钟精度、序数词)
		failBefore = failCnt;
		cnt = 0;
		String[][] enForms = { { "MMMM d, yyyy", "yyyy-MM-dd" }, { "d MMMM yyyy", "yyyy-MM-dd" },
				{ "EEEE, MMMM d, yyyy", "yyyy-MM-dd" }, { "EEE MMM d yyyy", "yyyy-MM-dd" },
				{ "MMM d yyyy HH:mm:ss", "yyyy-MM-dd HH:mm:ss" }, { "MMM d yyyy HH:mm", "yyyy-MM-dd HH:mm" } };
		for (int i = 0; i < 10000; i++) {
			LocalDateTime dt = randomDateTime(rnd);
			String[] form = enForms[rnd.nextInt(enForms.length)];
			String text = DateUtil.formatDate(dt, form[0], Locale.ENGLISH);
			verify("随机英文", text, DateUtil.formatDate(DateUtil.parseString(text), form[1]),
					DateUtil.formatDate(dt, form[1]));
			cnt++;
			if (rnd.nextInt(4) == 0) {
				String ord = ordinal(dt.getDayOfMonth()) + " "
						+ DateUtil.formatDate(dt, "MMMM yyyy", Locale.ENGLISH);
				verify("随机英文序数", ord, DateUtil.formatDate(DateUtil.parseString(ord), "yyyy-MM-dd"),
						DateUtil.formatDate(dt, "yyyy-MM-dd"));
				cnt++;
			}
		}
		section("随机英文日期(含星期/日在前/分钟精度/序数词) 10000+ 次", cnt, failBefore);

		// 12. format2China→parseString往返(比较精度与源输入粒度对齐)
		failBefore = failCnt;
		cnt = 0;
		for (int i = 0; i < 10000; i++) {
			LocalDateTime dt = randomDateTime(rnd);
			String source;
			String compareFormat;
			switch (rnd.nextInt(3)) {
			case 0:
				source = DateUtil.formatDate(dt, "yyyy-M-d");
				compareFormat = "yyyy-M-d";
				break;
			case 1:
				source = DateUtil.formatDate(dt, "yyyy-M-d H:m");
				compareFormat = "yyyy-M-d H:m";
				break;
			default:
				source = DateUtil.formatDate(dt, "yyyy-M-d H:m:s");
				compareFormat = "yyyy-M-d H:m:s";
				break;
			}
			String china = DateUtil.format2China(source);
			verify("format2China往返", china,
					DateUtil.formatDate(DateUtil.parseString(china), compareFormat),
					DateUtil.formatDate(dt, compareFormat));
			cnt++;
		}
		section("format2China→自动解析往返 10000 次", cnt, failBefore);

		System.out.println("\n========== 三、定向边缘(逐行判定) ==========");
		check("null安全", "formatDate(null)", "null", DateUtil.formatDate(null, "yyyy-MM-dd"));
		check("null安全", "parse(null)", "null", DateUtil.parse(null, "yyyy-MM-dd"));
		check("空串", "parseString(\"\")", "null", DateUtil.parseString(""));
		check("null字面量", "parseString(\"null\")", "null", DateUtil.parseString("null"));
		check("空白trim", "\" 2026-08-18 \"", "2026-08-18",
				DateUtil.formatDate(DateUtil.parseString(" 2026-08-18 "), "yyyy-MM-dd"));
		check("非法值", "parseString(\"abc\")", "null", DateUtil.parseString("abc"));
		check("单位数补零", "2026-8-9", "20260809",
				DateUtil.formatDate(DateUtil.parseString("2026-8-9"), "yyyyMMdd"));
		check("单位数补零2", "2026-08-9 8:05", "2026-08-09 08:05:00",
				DateUtil.formatDate(DateUtil.parseString("2026-08-9 8:05"), "yyyy-MM-dd HH:mm:ss"));
		check("ISO形式", "2026-08-18T12:30:45", "2026-08-18 12:30:45",
				DateUtil.formatDate(DateUtil.parseString("2026-08-18T12:30:45"), "yyyy-MM-dd HH:mm:ss"));
		check("时区后缀剥离", "2026-08-18 12:30:45+08:00", "2026-08-18 12:30:45",
				DateUtil.formatDate(DateUtil.parseString("2026-08-18 12:30:45+08:00"), "yyyy-MM-dd HH:mm:ss"));
		check("纯时间", "08:09:10", "08:09:10",
				DateUtil.formatDate(DateUtil.parseString("08:09:10"), "HH:mm:ss"));
		check("纯时间到分", "08:09", "08:09", DateUtil.formatDate(DateUtil.parseString("08:09"), "HH:mm"));
		check("中文单位带时间", "2026年8月18日12时30分45秒", "2026-08-18 12:30:45",
				DateUtil.formatDate(DateUtil.parseString("2026年8月18日12时30分45秒"), "yyyy-MM-dd HH:mm:ss"));
		check("紧凑带毫秒", "20260818123045123", "2026-08-18 12:30:45.123",
				DateUtil.formatDate(DateUtil.parseString("20260818123045123"), "yyyy-MM-dd HH:mm:ss.SSS"));
		check("宽松回滚契约", "2026-13-01(13月)", "2027-01-01",
				DateUtil.formatDate(DateUtil.parseString("2026-13-01"), "yyyy-MM-dd"));
		check("宽松回滚契约", "2026-02-30(非闰2月30)", "2026-03-02",
				DateUtil.formatDate(DateUtil.parseString("2026-02-30"), "yyyy-MM-dd"));
		check("上界", "9999-12-31 23:59:59", "9999-12-31 23:59:59",
				DateUtil.formatDate(DateUtil.parseString("9999-12-31 23:59:59"), "yyyy-MM-dd HH:mm:ss"));
		check("下界(1582历法切换后)", "1583-01-01", "1583-01-01",
				DateUtil.formatDate(DateUtil.parseString("1583-01-01"), "yyyy-MM-dd"));
		check("format2China纯时间", "12:30:45", "12时30分45秒", DateUtil.format2China("12:30:45"));
		check("parseChinaDate带时间", "二〇二六年八月十八日十二时三十分五十六秒", "2026-08-18 12:30:56",
				DateUtil.parseChinaDate("二〇二六年八月十八日十二时三十分五十六秒", "yyyy-MM-dd HH:mm:ss"));
		System.out.println("  [契约说明] 1582-10-15之前属儒略历/前推格里历差异区,业务不建议,不在契约范围;"
				+ "13位为毫秒时间戳唯一无歧义区间(2001~2286年)");

		System.out.println("\n========== 压测总计:" + (passCnt + failCnt) + "项,通过" + passCnt + "项,失败" + failCnt
				+ "项 ==========");
		assertTrue(failCnt == 0, "压测存在失败样本:\n" + String.join("\n", failSamples));
	}

	private void check(String scene, String input, String expected, Object actual) {
		String actualStr = (actual == null) ? "null" : actual.toString();
		boolean ok = expected.equals(actualStr);
		System.out.println("  " + (ok ? "√" : "×") + " [" + scene + "] " + input + " --> " + actualStr + " | 期望: "
				+ expected);
		if (!ok) {
			failCnt++;
			failSamples.add("[" + scene + "]" + input + " 实际:" + actualStr + " 期望:" + expected);
		} else {
			passCnt++;
		}
	}
}
