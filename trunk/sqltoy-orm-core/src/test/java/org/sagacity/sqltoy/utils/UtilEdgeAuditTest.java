package org.sagacity.sqltoy.utils;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.math.BigDecimal;
import java.util.HashSet;
import java.util.Set;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;

import org.junit.jupiter.api.Test;
import org.sagacity.sqltoy.config.model.DataType;

/**
 * 工具类边界审计轮:BeanUtil/DateUtil/NumberUtil/StringUtil/IdUtil/FileUtil/StandardUUIDv7Generator
 * 在边界输入下的行为固化。其中部分行为是"已知设计"(如日期宽松解析、Boolean窄集合、
 * 英文金额单参不带货币单位),以断言固化,将来有意变更时由测试提醒。
 */
public class UtilEdgeAuditTest {

	/* ==================== DateUtil ==================== */

	@Test
	public void dateParseForms() {
		// 标准日期/ISO带T/紧凑/斜杠/中文/英文 均可解析
		for (String text : new String[] { "2026-09-14", "2026-09-14 10:20:30", "2026-09-14T10:20:30", "20260914",
				"2026/09/14", "2026年9月14日", "14 Sep 2026" }) {
			assertEquals("2026-09-14", DateUtil.formatDate(DateUtil.parseString(text), "yyyy-MM-dd"), "text=" + text);
		}
	}

	/**
	 * 已知设计:日期解析是宽松模式,越界字段会向后进位(2026-02-30解析为2026-03-02),
	 * 而不是报错。固化为断言,将来改为lenient(false)严格校验时由测试提醒。
	 */
	@Test
	public void lenientDateParsingRollsOver() {
		assertEquals("2026-03-02", DateUtil.formatDate(DateUtil.parseString("2026-02-30"), "yyyy-MM-dd"));
		// 非日期文本返回null不抛错
		assertNull(DateUtil.parseString("abc"));
	}

	@Test
	public void addMonthAndYearClampAtMonthEnd() {
		// 1月31日加1个月钳制到2月28日;闰日加1年钳制到2月28日
		assertEquals("2026-02-28",
				DateUtil.formatDate(DateUtil.addMonth(DateUtil.parseString("2026-01-31"), 1), "yyyy-MM-dd"));
		assertEquals("2025-02-28",
				DateUtil.formatDate(DateUtil.addYear(DateUtil.parseString("2024-02-29"), 1), "yyyy-MM-dd"));
		// 闰年内加1年保持2月29日
		assertEquals("2028-02-29",
				DateUtil.formatDate(DateUtil.addYear(DateUtil.parseString("2024-02-29"), 4), "yyyy-MM-dd"));
	}

	@Test
	public void intervalDaysAndMonths() {
		Object floor = DateUtil.parseString("2026-09-14");
		Object goal = DateUtil.parseString("2026-09-15");
		assertEquals(1, DateUtil.getIntervalDays(floor, goal));
		assertEquals(-1, DateUtil.getIntervalDays(goal, floor));
		// 按自然月差计算:1月31日到3月1日为2个自然月
		assertEquals(2, DateUtil.getIntervalMonths(DateUtil.parseString("2026-01-31"),
				DateUtil.parseString("2026-03-01")));
	}

	/* ==================== NumberUtil ==================== */

	@Test
	public void capitalMoneyRoundTrip() {
		String[] values = { "0", "0.00", "0.004", "10.50", "100.01", "1000000.00", "-12.34", "99999999999.99", "20",
				"0.10", "110", "1010", "10010" };
		for (String value : values) {
			BigDecimal money = new BigDecimal(value);
			String capital = NumberUtil.toCapitalMoney(money);
			assertNotNull(capital, "value=" + value);
			assertTrue(NumberUtil.capitalMoneyToNum(capital).compareTo(money) == 0,
					"人民币大写往返不一致:value=" + value + " capital=" + capital);
		}
		// 关键形态:零、厘、负数、零的插入(壹万零壹拾)、万/亿
		assertEquals("零元整", NumberUtil.toCapitalMoney(BigDecimal.ZERO));
		assertEquals("壹万零壹拾元整", NumberUtil.toCapitalMoney(new BigDecimal("10010")));
		assertEquals("负壹拾贰元叁角肆分", NumberUtil.toCapitalMoney(new BigDecimal("-12.34")));
		assertEquals("玖佰玖拾玖亿玖仟玖佰玖拾玖万玖仟玖佰玖拾玖元玖角玖分",
				NumberUtil.toCapitalMoney(new BigDecimal("99999999999.99")));
		assertEquals("肆厘", NumberUtil.toCapitalMoney(new BigDecimal("0.004")));
	}

	@Test
	public void englishMoneyForm() {
		// 单参版本刻意不带货币单位;双参版本输出票据口径(SAY + 货币)
		assertEquals("ONE THOUSAND ONE HUNDRED AND TWENTY-THREE AND CENTS FORTY-FIVE ONLY",
				NumberUtil.convertToEnglishMoney(new BigDecimal("1123.45")));
		assertEquals("SAY DOLLARS ONE HUNDRED AND CENTS FIFTY ONLY",
				NumberUtil.convertToEnglishMoney(new BigDecimal("100.5"), "DOLLARS"));
		assertTrue(NumberUtil.englishMoneyToNum(NumberUtil.convertToEnglishMoney(new BigDecimal("100.5")))
				.compareTo(new BigDecimal("100.5")) == 0, "英文金额应可往返");
	}

	@Test
	public void aggregateNullSemantics() {
		// max/min跳过null;average/summary把null按0计(与SQL的AVG忽略null语义不同,属既有设计)
		assertEquals(0, NumberUtil.getMax(new BigDecimal[] { new BigDecimal("1"), null }).compareTo(BigDecimal.ONE));
		assertEquals(0, NumberUtil.getMin(new BigDecimal[] { new BigDecimal("1"), null }).compareTo(BigDecimal.ONE));
		assertNull(NumberUtil.getMax(new BigDecimal[] {}));
		assertEquals(0, NumberUtil.getAverage(new BigDecimal[] { new BigDecimal("1"), null })
				.compareTo(new BigDecimal("0.5")));
		assertEquals(0, NumberUtil.summary(new BigDecimal[] { new BigDecimal("1"), null }).compareTo(BigDecimal.ONE));
	}

	@Test
	public void percentAndRandomBoundaries() throws Exception {
		assertEquals(0.1234f, NumberUtil.parsePercent("12.34%"), 0.000001f);
		assertNull(NumberUtil.parsePercent("  "));
		// randomArray: size超过maxValue时按maxValue截断,返回[0,maxValue)不重复乱序
		Object[] arr = NumberUtil.randomArray(5, 10);
		assertEquals(5, arr.length);
		Set<Integer> distinct = new HashSet<Integer>();
		for (Object item : arr) {
			int v = ((Integer) item).intValue();
			assertTrue(v >= 0 && v < 5, "随机数越界:" + v);
			assertTrue(distinct.add(Integer.valueOf(v)), "随机数重复:" + v);
		}
		// getRandomNum为[start,end)区间,start>=end给出明确报错
		assertEquals(3, NumberUtil.getRandomNum(3, 4));
		assertThrows(IllegalArgumentException.class, () -> NumberUtil.getRandomNum(3, 3));
		// 全零概率数组不挂死且返回合法下标(带超时保护)
		ExecutorService pool = Executors.newSingleThreadExecutor();
		Future<Integer> future = pool.submit(() -> Integer.valueOf(NumberUtil.getProbabilityIndex(new int[] { 0, 0, 0 })));
		int index = future.get(5, TimeUnit.SECONDS).intValue();
		pool.shutdownNow();
		assertTrue(index >= 0 && index < 3, "下标越界:" + index);
	}

	/* ==================== BeanUtil.convertType ==================== */

	private static Object convert(Object value, Class<?> target) throws Exception {
		return BeanUtil.convertType(value, java.sql.Types.OTHER, DataType.getType(target.getName()), target.getName());
	}

	@Test
	public void convertTypeMatrix() throws Exception {
		assertEquals(Integer.valueOf(123), convert("123", Integer.class));
		// 空白按null处理
		assertNull(convert("", Integer.class));
		assertNull(convert("  ", Integer.class));
		// 文本小数转整型按截断处理(既有设计)
		assertEquals(Integer.valueOf(1), convert("1.5", Integer.class));
		// 越界给出响亮报错
		assertThrows(NumberFormatException.class, () -> convert("99999999999", Integer.class));
		// Boolean窄集合:仅true(忽略大小写)与"1"为true,"Y"/"yes"等为false(既有设计)
		assertEquals(Boolean.TRUE, convert("true", Boolean.class));
		assertEquals(Boolean.TRUE, convert("TRUE", Boolean.class));
		assertEquals(Boolean.TRUE, convert("1", Boolean.class));
		assertEquals(Boolean.FALSE, convert("Y", Boolean.class));
		assertEquals(Boolean.FALSE, convert("yes", Boolean.class));
		// 数字与字符串互转
		assertEquals("123", convert(Integer.valueOf(123), String.class));
		assertEquals(Long.valueOf(123), convert(Integer.valueOf(123), Long.class));
		assertEquals(Integer.valueOf(1), convert(Double.valueOf(1.5), Integer.class));
		assertNull(convert(null, String.class));
		assertEquals("2026-09-14",
				DateUtil.formatDate(convert("2026-09-14", java.util.Date.class), "yyyy-MM-dd"));
	}

	/* ==================== StringUtil ==================== */

	@Test
	public void symMarkIndexIgnoreCaseNormalText() {
		// 返回结束标记所在下标
		assertEquals(5, StringUtil.getSymMarkIndexIgnoreCase("#[", "]", "aB#[c]d", 0));
		// 大小写不同的标记同样命中
		assertEquals(5, StringUtil.getSymMarkIndexIgnoreCase("#[", "]", "aB#[C]D", 0));
		assertTrue(StringUtil.hasChinese("中文"));
		assertFalse(StringUtil.hasChinese("abc"));
		assertFalse(StringUtil.hasChinese(""));
		assertFalse(StringUtil.hasChinese(null));
	}

	/**
	 * 忽略大小写定位必须以原字符串的下标为准:前缀含"小写后长度会变化"的字符(土耳其İ→"i"+组合点
	 * 共2字符)时,旧实现整串toLowerCase会产生+1漂移(İ#[c]d中]的真实下标是4,旧实现返回5)
	 */
	@Test
	public void ignoreCaseKeepsOriginalIndexes() {
		String source = "İ#[c]d";
		assertEquals(4, StringUtil.getSymMarkIndexIgnoreCase("#[", "]", source, 0));
		assertEquals(4, source.indexOf(']'));
		// İ 位于标记内容内部、其后为配对结束标记时同样不漂移
		String inner = "#[İc]d";
		assertEquals(4, StringUtil.getSymMarkIndexIgnoreCase("#[", "]", inner, 0));
		assertEquals(4, inner.indexOf(']'));
		// 正常文本行为不变
		assertEquals(5, StringUtil.getSymMarkIndexIgnoreCase("#[", "]", "aB#[c]d", 0));
	}

	/* ==================== IdUtil / FileUtil / UUIDv7 ==================== */

	@Test
	public void shortNanoTimeIdUniqueAndMonotonic() {
		Set<String> ids = new HashSet<String>();
		String previous = null;
		for (int i = 0; i < 10000; i++) {
			String id = IdUtil.getShortNanoTimeId(null).toPlainString();
			assertTrue(id.matches("\\d+"), "应为纯数字:" + id);
			assertTrue(ids.add(id), "短ID重复:" + id);
			if (previous != null) {
				assertTrue(id.compareTo(previous) > 0, "短ID应单调递增:" + previous + " -> " + id);
			}
			previous = id;
		}
	}

	@Test
	public void idUtilOtherForms() {
		assertTrue(IdUtil.getNanoTimeId(null).toPlainString().matches("\\d+"));
		assertTrue(IdUtil.getDebugId().matches("\\d{2}:\\d{2}:\\d{2}\\.\\d+"));
		// UUIDv7字符串形态:36位、4个横杠
		String uuid = StandardUUIDv7Generator.generateString();
		assertEquals(36, uuid.length());
		assertEquals(4, uuid.chars().filter(c -> c == '-').count());
	}

	@Test
	public void putFileToInputStream() throws Exception {
		java.io.InputStream in = FileUtil
				.putFileToInputStream("src/test/resources/scripts/markSql.txt".replace('/', java.io.File.separatorChar));
		assertNotNull(in);
		assertTrue(in.available() > 0);
		in.close();
		assertThrows(Exception.class, () -> FileUtil.putFileToInputStream("no/such/file_xyz_audit.txt"));
	}
}
