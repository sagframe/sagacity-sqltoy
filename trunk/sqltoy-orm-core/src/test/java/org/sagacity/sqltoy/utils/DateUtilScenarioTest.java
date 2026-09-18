package org.sagacity.sqltoy.utils;

import static org.junit.jupiter.api.Assertions.assertTrue;

import java.sql.Timestamp;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.Date;
import java.util.List;
import java.util.Locale;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

/**
 * DateUtil 大量场景覆盖测试:逐行输出"输入 --操作--> 实际输出 | 期望输出"并判定是否正确
 * 场景:1、日期格式化(横杠/斜杠/点号/冒号/全数字/中文单位等);
 * 2、日期数字转中文日期(format2China)与英文日期展示;
 * 3、中文日期、英文日期字符串转日期类型(parseChinaDate/parse/parseString);
 * 4、带中文/英文等locale的格式化(月份、星期名称随区域变化)
 * 基准时间:2026-08-18 12:30:45(星期二),高精度基准含123456789纳秒
 */
public class DateUtilScenarioTest {

	private static final Date BASE = DateUtil.parse("2026-08-18 12:30:45", "yyyy-MM-dd HH:mm:ss");

	private static final LocalDateTime BASE_NANO = LocalDateTime.of(2026, 8, 18, 12, 30, 45, 123456789);

	private List<String> failures;

	/** 基准日期是星期二,如环境异常第一时间暴露 */
	@BeforeEach
	public void setUp() {
		failures = new ArrayList<String>();
		String week = DateUtil.formatDate(BASE, "EEEE", Locale.ENGLISH);
		System.out.println("== 环境确认: 2026-08-12:30:45 的星期 = " + week + " (期望 Tuesday) ==");
		assertTrue("Tuesday".equals(week), "基准日期星期应为Tuesday,实际:" + week);
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

	/** 场景一:日期格式化(横杠、斜杠、点号、冒号、全数字、中文单位、简写、各种输入类型) */
	@Test
	public void scenario1Format() {
		System.out.println("\n========== 场景一:日期格式化 ==========");
		String base = "2026-08-18 12:30:45";
		check("1-01", base, "formatDate(yyyy-MM-dd)", "2026-08-18", DateUtil.formatDate(BASE, "yyyy-MM-dd"));
		check("1-02", base, "formatDate(yyyy/MM/dd)", "2026/08/18", DateUtil.formatDate(BASE, "yyyy/MM/dd"));
		check("1-03", base, "formatDate(yyyy.MM.dd)", "2026.08.18", DateUtil.formatDate(BASE, "yyyy.MM.dd"));
		check("1-04", base, "formatDate(yyyyMMdd)", "20260818", DateUtil.formatDate(BASE, "yyyyMMdd"));
		check("1-05", base, "formatDate(yyMMdd)", "260818", DateUtil.formatDate(BASE, "yyMMdd"));
		check("1-06", base, "formatDate(yyyyMM)", "202608", DateUtil.formatDate(BASE, "yyyyMM"));
		check("1-07", base, "formatDate(yyyy)", "2026", DateUtil.formatDate(BASE, "yyyy"));
		check("1-08", base, "formatDate(MM/dd/yyyy)", "08/18/2026", DateUtil.formatDate(BASE, "MM/dd/yyyy"));
		check("1-09", base, "formatDate(dd/MM/yyyy)", "18/08/2026", DateUtil.formatDate(BASE, "dd/MM/yyyy"));
		check("1-10", base, "formatDate(dd.MM.yy)", "18.08.26", DateUtil.formatDate(BASE, "dd.MM.yy"));
		check("1-11", base, "formatDate(yyyy-MM-dd HH:mm:ss)", "2026-08-18 12:30:45",
				DateUtil.formatDate(BASE, "yyyy-MM-dd HH:mm:ss"));
		check("1-12", base, "formatDate(yyyy/MM/dd HH:mm:ss)", "2026/08/18 12:30:45",
				DateUtil.formatDate(BASE, "yyyy/MM/dd HH:mm:ss"));
		check("1-13", base, "formatDate(yyyy.MM.dd HH:mm:ss)", "2026.08.18 12:30:45",
				DateUtil.formatDate(BASE, "yyyy.MM.dd HH:mm:ss"));
		check("1-14", base, "formatDate(HH:mm:ss)", "12:30:45", DateUtil.formatDate(BASE, "HH:mm:ss"));
		check("1-15", base, "formatDate(HH:mm)", "12:30", DateUtil.formatDate(BASE, "HH:mm"));
		check("1-16", base, "formatDate(HHmmss)", "123045", DateUtil.formatDate(BASE, "HHmmss"));
		check("1-17", base, "formatDate(yyyy-MM-dd HH:mm)", "2026-08-18 12:30",
				DateUtil.formatDate(BASE, "yyyy-MM-dd HH:mm"));
		check("1-18", base, "formatDate(MM-dd)", "08-18", DateUtil.formatDate(BASE, "MM-dd"));
		check("1-19", base, "formatDate(yyyy年MM月dd日)", "2026年08月18日",
				DateUtil.formatDate(BASE, "yyyy年MM月dd日"));
		check("1-20", base, "formatDate(yyyy年M月d日)", "2026年8月18日", DateUtil.formatDate(BASE, "yyyy年M月d日"));
		check("1-21", base, "formatDate(yyyy年MM月dd日 HH时mm分ss秒)", "2026年08月18日 12时30分45秒",
				DateUtil.formatDate(BASE, "yyyy年MM月dd日 HH时mm分ss秒"));
		// 不同输入类型
		check("1-22", "LocalDateTime(纳秒123456789)", "formatDate(yyyy-MM-dd HH:mm:ss.SSS)",
				"2026-08-18 12:30:45.123", DateUtil.formatDate(BASE_NANO, "yyyy-MM-dd HH:mm:ss.SSS"));
		check("1-23", "LocalDateTime", "formatDate(yyyy-MM-dd HH:mm:ss)", "2026-08-18 12:30:45",
				DateUtil.formatDate(BASE_NANO, "yyyy-MM-dd HH:mm:ss"));
		check("1-24", "LocalDate", "formatDate(yyyy-MM-dd)", "2026-08-18",
				DateUtil.formatDate(LocalDate.of(2026, 8, 18), "yyyy-MM-dd"));
		check("1-25", "Timestamp", "formatDate(yyyy-MM-dd HH:mm:ss.SSS)", "2026-08-18 12:30:45.000",
				DateUtil.formatDate(Timestamp.valueOf("2026-08-18 12:30:45"), "yyyy-MM-dd HH:mm:ss.SSS"));
		check("1-26", "字符串2026-08-18 12:30:45", "formatDate(yyyy/MM/dd)", "2026/08/18",
				DateUtil.formatDate("2026-08-18 12:30:45", "yyyy/MM/dd"));
		// 解析各种形态后再格式化
		check("1-27", "2026-8-9", "parse→formatDate(yyyyMMdd)", "20260809",
				DateUtil.formatDate(DateUtil.parse("2026-8-9", null), "yyyyMMdd"));
		check("1-28", "2026/08/18 12:30:45", "parse→formatDate(yyyy-MM-dd HH:mm:ss)", "2026-08-18 12:30:45",
				DateUtil.formatDate(DateUtil.parse("2026/08/18 12:30:45", null), "yyyy-MM-dd HH:mm:ss"));
		check("1-29", "2026.08.18", "parse→formatDate(yyyy-MM-dd)", "2026-08-18",
				DateUtil.formatDate(DateUtil.parse("2026.08.18", null), "yyyy-MM-dd"));
		check("1-30", "20260818", "parse→formatDate(yyyy-MM-dd)", "2026-08-18",
				DateUtil.formatDate(DateUtil.parse("20260818", null), "yyyy-MM-dd"));
		check("1-31", "20260818123045", "parse→formatDate(yyyy-MM-dd HH:mm:ss)", "2026-08-18 12:30:45",
				DateUtil.formatDate(DateUtil.parse("20260818123045", null), "yyyy-MM-dd HH:mm:ss"));
		check("1-32", "20260818 123045", "parse→formatDate(yyyy-MM-dd HH:mm:ss)", "2026-08-18 12:30:45",
				DateUtil.formatDate(DateUtil.parse("20260818 123045", null), "yyyy-MM-dd HH:mm:ss"));
		check("1-33", "2026-08-18T12:30:45", "parse→formatDate(yyyy-MM-dd HH:mm:ss)", "2026-08-18 12:30:45",
				DateUtil.formatDate(DateUtil.parse("2026-08-18T12:30:45", null), "yyyy-MM-dd HH:mm:ss"));
		check("1-34", "12:30:45", "parse→formatDate(HH:mm)", "12:30",
				DateUtil.formatDate(DateUtil.parse("12:30:45", null), "HH:mm"));
		check("1-35", "2026-08", "parse→formatDate(yyyy-MM)", "2026-08",
				DateUtil.formatDate(DateUtil.parse("2026-08", null), "yyyy-MM"));
		// 简写格式快捷通道
		check("1-36", base, "formatDate(YYYY)", "2026", DateUtil.formatDate(BASE, "YYYY"));
		check("1-37", base, "formatDate(YY)", "26", DateUtil.formatDate(BASE, "YY"));
		check("1-38", base, "formatDate(MM)", "08", DateUtil.formatDate(BASE, "MM"));
		check("1-39", base, "formatDate(DD)", "18", DateUtil.formatDate(BASE, "DD"));
		check("1-40", "null", "formatDate(yyyy-MM-dd)", "null", DateUtil.formatDate(null, "yyyy-MM-dd"));
		assertNoFailure("场景一:日期格式化");
	}

	/** 场景二:数字/日期转中文日期(format2China)与英文日期展示 */
	@Test
	public void scenario2ChinaAndEnglish() {
		System.out.println("\n========== 场景二:数字转中文日期、英文日期 ==========");
		check("2-01", "Date(2026-08-18 12:30:45)", "format2China", "2026年8月18日12时30分45秒",
				DateUtil.format2China(BASE));
		check("2-02", "2026-08-18", "format2China", "2026年8月18日", DateUtil.format2China("2026-08-18"));
		check("2-03", "2026/08/18", "format2China", "2026年8月18日", DateUtil.format2China("2026/08/18"));
		check("2-04", "2026-8-9", "format2China", "2026年8月9日", DateUtil.format2China("2026-8-9"));
		check("2-05", "20260818", "format2China", "2026年8月18日", DateUtil.format2China("20260818"));
		check("2-06", "202608", "format2China", "2026年8月", DateUtil.format2China("202608"));
		check("2-07", "2026", "format2China", "2026年", DateUtil.format2China("2026"));
		check("2-08", "2026-08", "format2China", "2026年8月", DateUtil.format2China("2026-08"));
		check("2-09", "2026-08-18 12:30", "format2China", "2026年8月18日12时30分0秒",
				DateUtil.format2China("2026-08-18 12:30"));
		check("2-10", "2026-08-18 12:30:45", "format2China", "2026年8月18日12时30分45秒",
				DateUtil.format2China("2026-08-18 12:30:45"));
		check("2-11", "20260818 123045", "format2China", "2026年8月18日12时30分45秒",
				DateUtil.format2China("20260818 123045"));
		check("2-12", "12:30:45", "format2China", "12时30分45秒", DateUtil.format2China("12:30:45"));
		check("2-13", "LocalDateTime(纳秒123456789)", "format2China", "2026年8月18日12时30分45秒",
				DateUtil.format2China(BASE_NANO));
		check("2-14", "Aug 18, 2026", "format2China", "2026年8月18日", DateUtil.format2China("Aug 18, 2026"));
		check("2-15", "Aug 18, 2026 12:30:45", "format2China", "2026年8月18日12时30分45秒",
				DateUtil.format2China("Aug 18, 2026 12:30:45"));
		check("2-16", "Aug 18, 2026 12:30", "format2China", "2026年8月18日12时30分0秒",
				DateUtil.format2China("Aug 18, 2026 12:30"));
		// 英文日期展示
		check("2-17", "Date(2026-08-18 12:30:45)", "formatDate(MMM dd, yyyy,ENGLISH)", "Aug 18, 2026",
				DateUtil.formatDate(BASE, "MMM dd, yyyy", Locale.ENGLISH));
		check("2-18", "Date(2026-08-18 12:30:45)", "formatDate(MMMM dd, yyyy,ENGLISH)", "August 18, 2026",
				DateUtil.formatDate(BASE, "MMMM dd, yyyy", Locale.ENGLISH));
		check("2-19", "Date(2026-08-18 12:30:45)", "formatDate(EEE, MMMM dd, yyyy,ENGLISH)",
				"Tue, August 18, 2026", DateUtil.formatDate(BASE, "EEE, MMMM dd, yyyy", Locale.ENGLISH));
		check("2-20", "Date(2026-08-18 12:30:45)", "formatDate(dd MMMM yyyy,ENGLISH)", "18 August 2026",
				DateUtil.formatDate(BASE, "dd MMMM yyyy", Locale.ENGLISH));
		assertNoFailure("场景二:数字转中文日期、英文日期");
	}

	/** 场景三:中文日期、英文日期字符串转日期类型 */
	@Test
	public void scenario3Parse() {
		System.out.println("\n========== 场景三:中文/英文字符转日期 ==========");
		// 中文数字日期
		check("3-01", "二〇二六年八月十八日", "parseChinaDate(yyyy-MM-dd)", "2026-08-18",
				DateUtil.parseChinaDate("二〇二六年八月十八日", "yyyy-MM-dd"));
		check("3-02", "二零二六年十月二十一日", "parseChinaDate(yyyy-MM-dd)", "2026-10-21",
				DateUtil.parseChinaDate("二零二六年十月二十一日", "yyyy-MM-dd"));
		check("3-03", "二〇二六年十月十九日", "parseChinaDate(yyyy-MM-dd)", "2026-10-19",
				DateUtil.parseChinaDate("二〇二六年十月十九日", "yyyy-MM-dd"));
		check("3-04", "二〇二六年八月十八日十二时三十分五十六秒", "parseChinaDate(yyyy-MM-dd HH:mm:ss)",
				"2026-08-18 12:30:56",
				DateUtil.parseChinaDate("二〇二六年八月十八日十二时三十分五十六秒", "yyyy-MM-dd HH:mm:ss"));
		check("3-05", "二〇二六年八月", "parseChinaDate(yyyy-MM)", "2026-08",
				DateUtil.parseChinaDate("二〇二六年八月", "yyyy-MM"));
		check("3-06", "二〇二六年", "parseChinaDate(yyyy)", "2026", DateUtil.parseChinaDate("二〇二六年", "yyyy"));
		check("3-07", "二〇二六年八月十八日", "parseChinaDate(默认数字串)", "2026-8-18",
				DateUtil.parseChinaDate("二〇二六年八月十八日"));
		// 阿拉伯数字+中文单位
		check("3-08", "2026年8月18日", "parse(yyyy-MM-dd)→formatDate", "2026-08-18",
				DateUtil.formatDate(DateUtil.parse("2026年8月18日", "yyyy-MM-dd"), "yyyy-MM-dd"));
		check("3-09", "2026年8月18日 12:30:45", "parseString→formatDate", "2026-08-18 12:30:45",
				DateUtil.formatDate(DateUtil.parseString("2026年8月18日 12:30:45"), "yyyy-MM-dd HH:mm:ss"));
		check("3-10", "二〇二六年八月十八日", "parse(yyyy-MM-dd)→formatDate", "2026-08-18",
				DateUtil.formatDate(DateUtil.parse("二〇二六年八月十八日", "yyyy-MM-dd"), "yyyy-MM-dd"));
		// 英文日期字符串(自动识别)
		check("3-11", "Aug 18, 2026", "parseString→formatDate", "2026-08-18",
				DateUtil.formatDate(DateUtil.parseString("Aug 18, 2026"), "yyyy-MM-dd"));
		check("3-12", "August 18, 2026", "parseString→formatDate", "2026-08-18",
				DateUtil.formatDate(DateUtil.parseString("August 18, 2026"), "yyyy-MM-dd"));
		check("3-13", "18 Aug 2026", "parseString→formatDate", "2026-08-18",
				DateUtil.formatDate(DateUtil.parseString("18 Aug 2026"), "yyyy-MM-dd"));
		check("3-14", "18th August 2026", "parseString→formatDate", "2026-08-18",
				DateUtil.formatDate(DateUtil.parseString("18th August 2026"), "yyyy-MM-dd"));
		check("3-15", "September 21, 2026", "parseString→formatDate", "2026-09-21",
				DateUtil.formatDate(DateUtil.parseString("September 21, 2026"), "yyyy-MM-dd"));
		check("3-16", "Sept 21, 2026", "parseString→formatDate", "2026-09-21",
				DateUtil.formatDate(DateUtil.parseString("Sept 21, 2026"), "yyyy-MM-dd"));
		check("3-17", "Tue Aug 18 2026", "parseString→formatDate", "2026-08-18",
				DateUtil.formatDate(DateUtil.parseString("Tue Aug 18 2026"), "yyyy-MM-dd"));
		check("3-18", "Aug 18 2026 12:30:45", "parseString→formatDate", "2026-08-18 12:30:45",
				DateUtil.formatDate(DateUtil.parseString("Aug 18 2026 12:30:45"), "yyyy-MM-dd HH:mm:ss"));
		check("3-19", "Aug 18, 2026 12:30:45", "parseString→formatDate", "2026-08-18 12:30:45",
				DateUtil.formatDate(DateUtil.parseString("Aug 18, 2026 12:30:45"), "yyyy-MM-dd HH:mm:ss"));
		check("3-20", "Thu Aug 18 12:30:45 2026", "parseString→formatDate", "2026-08-18 12:30:45",
				DateUtil.formatDate(DateUtil.parseString("Thu Aug 18 12:30:45 2026"), "yyyy-MM-dd HH:mm:ss"));
		check("3-21", "Tuesday, 18 August 2026", "parseString→formatDate", "2026-08-18",
				DateUtil.formatDate(DateUtil.parseString("Tuesday, 18 August 2026"), "yyyy-MM-dd"));
		check("3-22", "18-Aug-2026", "parseString→formatDate", "2026-08-18",
				DateUtil.formatDate(DateUtil.parseString("18-Aug-2026"), "yyyy-MM-dd"));
		// 指定英文格式解析
		check("3-23", "Aug 18, 2026", "parse(MMM dd, yyyy,ENGLISH)→formatDate", "2026-08-18",
				DateUtil.formatDate(DateUtil.parse("Aug 18, 2026", "MMM dd, yyyy", Locale.ENGLISH), "yyyy-MM-dd"));
		// 2026-08-18修复:横杠分隔英文日期、到分钟精度的英文日期时间
		check("3-24", "18-Aug-2026 12:30:45", "parseString→formatDate", "2026-08-18 12:30:45",
				DateUtil.formatDate(DateUtil.parseString("18-Aug-2026 12:30:45"), "yyyy-MM-dd HH:mm:ss"));
		check("3-25", "Tue Aug 18 2026 12:30", "parseString→formatDate(HH:mm)", "2026-08-18 12:30",
				DateUtil.formatDate(DateUtil.parseString("Tue Aug 18 2026 12:30"), "yyyy-MM-dd HH:mm"));
		check("3-26", "Aug-18-2026", "parseString→formatDate", "2026-08-18",
				DateUtil.formatDate(DateUtil.parseString("Aug-18-2026"), "yyyy-MM-dd"));
		assertNoFailure("场景三:中文/英文字符转日期");
	}

	/** 场景四:格式化带入中文、英文、日文等locale(月份、星期名称) */
	@Test
	public void scenario4Locale() {
		System.out.println("\n========== 场景四:locale格式化 ==========");
		String base = "Date(2026-08-18 12:30:45)";
		check("4-01", base, "formatDate(yyyy-MM-dd MMM,CHINA)", "2026-08-18 8月",
				DateUtil.formatDate(BASE, "yyyy-MM-dd MMM", Locale.CHINA));
		check("4-02", base, "formatDate(yyyy-MM-dd MMM,ENGLISH)", "2026-08-18 Aug",
				DateUtil.formatDate(BASE, "yyyy-MM-dd MMM", Locale.ENGLISH));
		check("4-03", base, "formatDate(MMMM dd, yyyy,CHINA)", "八月 18, 2026",
				DateUtil.formatDate(BASE, "MMMM dd, yyyy", Locale.CHINA));
		check("4-04", base, "formatDate(MMMM dd, yyyy,US)", "August 18, 2026",
				DateUtil.formatDate(BASE, "MMMM dd, yyyy", Locale.US));
		check("4-05", base, "formatDate(EEEE,CHINA)", "星期二", DateUtil.formatDate(BASE, "EEEE", Locale.CHINA));
		check("4-06", base, "formatDate(EEEE,ENGLISH)", "Tuesday", DateUtil.formatDate(BASE, "EEEE", Locale.ENGLISH));
		check("4-07", base, "formatDate(yyyy-MM-dd EEE,CHINA)", "2026-08-18 周二",
				DateUtil.formatDate(BASE, "yyyy-MM-dd EEE", Locale.CHINA));
		check("4-08", base, "formatDate(yyyy-MM-dd EEE,ENGLISH)", "2026-08-18 Tue",
				DateUtil.formatDate(BASE, "yyyy-MM-dd EEE", Locale.ENGLISH));
		check("4-09", base, "formatDate(yyyy/MM/dd HH:mm:ss EEE,US)", "2026/08/18 12:30:45 Tue",
				DateUtil.formatDate(BASE, "yyyy/MM/dd HH:mm:ss EEE", Locale.US));
		check("4-10", base, "formatDate(yyyy-MM-dd MMMM,JAPAN)", "2026-08-18 8月",
				DateUtil.formatDate(BASE, "yyyy-MM-dd MMMM", Locale.JAPAN));
		check("4-11", base, "formatDate(yyyy-MM-dd EEEE,JAPAN)", "2026-08-18 火曜日",
				DateUtil.formatDate(BASE, "yyyy-MM-dd EEEE", Locale.JAPAN));
		check("4-12", base, "formatDate(yyyy-MM-dd,CHINA)", "2026-08-18",
				DateUtil.formatDate(BASE, "yyyy-MM-dd", Locale.CHINA));
		check("4-13", base, "formatDate(yyyy-MM-dd,ENGLISH)", "2026-08-18",
				DateUtil.formatDate(BASE, "yyyy-MM-dd", Locale.ENGLISH));
		assertNoFailure("场景四:locale格式化");
	}
}
