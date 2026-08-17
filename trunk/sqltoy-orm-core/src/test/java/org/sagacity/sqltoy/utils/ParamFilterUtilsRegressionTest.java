package org.sagacity.sqltoy.utils;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotSame;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

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
}
