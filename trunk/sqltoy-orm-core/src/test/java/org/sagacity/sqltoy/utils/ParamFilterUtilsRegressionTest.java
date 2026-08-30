package org.sagacity.sqltoy.utils;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotSame;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Date;
import java.util.List;

import org.junit.jupiter.api.Test;
import org.sagacity.sqltoy.config.model.ParamFilterModel;

/**
 * 回归测试：(a)clone filter对原始类型数组不再CCE且产出独立副本;
 * (b)排他filter的update-value含时区偏移(如+08:00)不再NumberFormatException,增减日期表达式语义不变;
 * (c)not-equals对比值无法解析为日期时跳过对比不再NPE(与filterEquals防护对称)
 */
public class ParamFilterUtilsRegressionTest {

	@Test
	public void cloneFilterHandlesPrimitiveArray() {
		int[] ids = { 1, 2, 3 };
		ParamFilterModel filter = new ParamFilterModel();
		filter.setFilterType("clone");
		filter.setParams(new String[] { "ids" });
		filter.setParam("ids");
		filter.setUpdateParams(new String[] { "idsCopy" });
		Object[] result = ParamFilterUtils.filterValue(null, new String[] { "ids", "idsCopy" },
				new Object[] { ids, null }, Collections.singletonList(filter));
		// 修复前:(Object[])强转int[]直接ClassCastException
		Object cloned = result[1];
		assertTrue(cloned instanceof Object[]);
		Object[] clonedAry = (Object[]) cloned;
		assertEquals(3, clonedAry.length);
		assertEquals(1, ((Number) clonedAry[0]).intValue());
		assertEquals(3, ((Number) clonedAry[2]).intValue());
		assertNotSame(ids, cloned);
	}

	@Test
	public void cloneFilterStillClonesObjectArray() {
		String[] names = { "a", "b" };
		ParamFilterModel filter = new ParamFilterModel();
		filter.setFilterType("clone");
		filter.setParams(new String[] { "names" });
		filter.setParam("names");
		filter.setUpdateParams(new String[] { "namesCopy" });
		Object[] result = ParamFilterUtils.filterValue(null, new String[] { "names", "namesCopy" },
				new Object[] { names, null }, Collections.singletonList(filter));
		assertNotSame(names, result[1]);
		assertEquals("a", ((Object[]) result[1])[0]);
	}

	@Test
	public void exclusiveUpdateValueWithTimezoneOffsetNoException() {
		ParamFilterModel filter = new ParamFilterModel();
		filter.setFilterType("exclusive");
		filter.setParams(new String[] { "status" });
		filter.setParam("status");
		filter.setCompareType("==");
		filter.setCompareValues(new String[] { "A" });
		filter.setUpdateParams(new String[] { "endDate" });
		filter.setUpdateValue("2023-05-20 10:20:30.500+08:00");
		Object[] result = ParamFilterUtils.filterValue(null, new String[] { "status", "endDate" },
				new Object[] { "A", new Date() }, Collections.singletonList(filter));
		// 修复前:parseDateStr对"+08:00"执行Integer.parseInt抛NumberFormatException
		assertNotNull(result);
		assertEquals("A", result[0]);
	}

	@Test
	public void exclusiveDateArithmeticUnchanged() {
		ParamFilterModel filter = new ParamFilterModel();
		filter.setFilterType("exclusive");
		filter.setParams(new String[] { "status" });
		filter.setParam("status");
		filter.setCompareType("==");
		filter.setCompareValues(new String[] { "A" });
		filter.setUpdateParams(new String[] { "endDate" });
		filter.setUpdateValue("sysdate()-2d");
		long before = System.currentTimeMillis();
		Object[] result = ParamFilterUtils.filterValue(null, new String[] { "status", "endDate" },
				new Object[] { "A", new Date() }, Collections.singletonList(filter));
		assertTrue(result[1] instanceof Date);
		long diffDays = (before - ((Date) result[1]).getTime()) / (1000L * 3600 * 24);
		assertTrue(diffDays == 1 || diffDays == 2, "应约等于2天前,实际:" + diffDays);
	}

	@Test
	public void notEqualsWithUnparseableDateContrastSkipsInsteadOfNpe() {
		ParamFilterModel filter = new ParamFilterModel();
		filter.setFilterType("exclusive");
		filter.setParams(new String[] { "status" });
		filter.setParam("status");
		filter.setCompareType("<>");
		filter.setCompareValues(new String[] { "not-a-date-xx" });
		filter.setUpdateParams(new String[] { "endDate" });
		Object[] result = ParamFilterUtils.filterValue(null, new String[] { "status", "endDate" },
				new Object[] { new Date(), new Date() }, Collections.singletonList(filter));
		// 修复前:参照日期解析为null后compareTo抛NPE;修复后跳过对比,排他成立,endDate置null
		assertNotNull(result);
		assertTrue(result[0] instanceof Date);
		assertNull(result[1]);
	}

	private static ParamFilterModel toDateFilter(String param, String format, String incrementTime) {
		ParamFilterModel filter = new ParamFilterModel();
		filter.setFilterType("to-date");
		filter.setParams(new String[] { param });
		filter.setFormat(format);
		if (incrementTime != null) {
			filter.setIncrementTime(incrementTime);
		}
		return filter;
	}

	@Test
	public void firstOfYearAndLastOfYearBehaviorUnchanged() {
		// first_of_year/last_of_year内部由拼字符串改为直接构造Date,对外结果类型和值必须保持一致
		Object[] result = ParamFilterUtils.filterValue(null, new String[] { "beginDate" },
				new Object[] { "20260826" }, Collections.singletonList(toDateFilter("beginDate", "first_of_year", null)));
		assertTrue(result[0] instanceof LocalDate);
		assertEquals(LocalDate.of(2026, 1, 1), result[0]);

		// 年末+1天跨年到次年1月1日
		result = ParamFilterUtils.filterValue(null, new String[] { "endDate" },
				new Object[] { "20260826" }, Collections.singletonList(toDateFilter("endDate", "last_of_year", "1")));
		assertTrue(result[0] instanceof LocalDate);
		assertEquals(LocalDate.of(2027, 1, 1), result[0]);
	}

	@Test
	@SuppressWarnings("unchecked")
	public void nullElementWithFirstOfWeekNoNpe() {
		// 2026-08-26是周三,所在周周一为2026-08-24
		List<Object> dates = new ArrayList<Object>();
		dates.add(null);
		dates.add("20260826");
		// 修复前:集合含null元素,first_of_week分支asLocalDate(null).with(...)抛NPE
		Object[] result = ParamFilterUtils.filterValue(null, new String[] { "queryDate" },
				new Object[] { dates }, Collections.singletonList(toDateFilter("queryDate", "first_of_week", null)));
		List<Object> processed = (List<Object>) result[0];
		assertNull(processed.get(0));
		assertEquals(LocalDate.of(2026, 8, 24), processed.get(1));
	}

	@Test
	@SuppressWarnings("unchecked")
	public void nullElementWithIncrementNoException() {
		// null元素配合增量时间:修复前addDay(null,...)抛IllegalArgumentException
		List<Object> dates = new ArrayList<Object>();
		dates.add(null);
		dates.add("20260826");
		Object[] result = ParamFilterUtils.filterValue(null, new String[] { "queryDate" },
				new Object[] { dates },
				Collections.singletonList(toDateFilter("queryDate", "yyyyMMdd", "1")));
		List<Object> processed = (List<Object>) result[0];
		assertNull(processed.get(0));
		assertEquals(LocalDate.of(2026, 8, 27), processed.get(1));
	}

	@Test
	public void formatMismatchFallsBackToAutoParsing() {
		// format与实际字符串存在差异时,按format解析失败自动回退智能识别,结果与不配format一致
		// 分隔符不匹配
		Object[] result = ParamFilterUtils.filterValue(null, new String[] { "d1" },
				new Object[] { "2026/08/26" }, Collections.singletonList(toDateFilter("d1", "yyyy-MM-dd", null)));
		assertEquals(LocalDate.of(2026, 8, 26), result[0]);
		// 纯数字串配分隔符format
		result = ParamFilterUtils.filterValue(null, new String[] { "d2" },
				new Object[] { "20260826" }, Collections.singletonList(toDateFilter("d2", "yyyy-MM-dd", null)));
		assertEquals(LocalDate.of(2026, 8, 26), result[0]);
		// 字符串长于format(带时间,format只有日期):前缀截断到天
		result = ParamFilterUtils.filterValue(null, new String[] { "d3" },
				new Object[] { "2026-08-26 13:22:11" },
				Collections.singletonList(toDateFilter("d3", "yyyy-MM-dd", null)));
		assertEquals(LocalDate.of(2026, 8, 26), result[0]);
		// 字符串短于format(只有日期,format含时间):回退自动识别补零点,类型按format推断为localdatetime
		result = ParamFilterUtils.filterValue(null, new String[] { "d4" },
				new Object[] { "2026-08-26" },
				Collections.singletonList(toDateFilter("d4", "yyyy-MM-dd HH:mm:ss", null)));
		assertEquals(LocalDateTime.of(2026, 8, 26, 0, 0), result[0]);
		// 13位毫秒时间戳不受format影响
		result = ParamFilterUtils.filterValue(null, new String[] { "d5" },
				new Object[] { "1693020000000" },
				Collections.singletonList(toDateFilter("d5", "yyyy-MM-dd", null)));
		assertEquals(LocalDate.of(2023, 8, 26), result[0]);
		// 日期对象输入忽略format
		result = ParamFilterUtils.filterValue(null, new String[] { "d6" },
				new Object[] { LocalDate.of(2026, 8, 26) },
				Collections.singletonList(toDateFilter("d6", "yyyy-MM-dd", null)));
		assertEquals(LocalDate.of(2026, 8, 26), result[0]);
	}

	@Test
	public void ambiguousDateStringHonorsDeclaredFormat() {
		// 歧义串05-03-2024:修复前自动识别按yy/MM/dd猜测出错误日期,修复后按声明的dd-MM-yyyy解析为2024年3月5日
		Object[] result = ParamFilterUtils.filterValue(null, new String[] { "d" },
				new Object[] { "05-03-2024" }, Collections.singletonList(toDateFilter("d", "dd-MM-yyyy", null)));
		assertEquals(LocalDate.of(2024, 3, 5), result[0]);
	}
}
