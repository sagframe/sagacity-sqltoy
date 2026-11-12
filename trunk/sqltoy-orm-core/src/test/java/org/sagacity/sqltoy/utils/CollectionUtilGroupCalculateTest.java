package org.sagacity.sqltoy.utils;

import static org.junit.jupiter.api.Assertions.assertEquals;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

import org.junit.jupiter.api.Test;

/**
 * 回归测试：分组求平均的分母是组内行数(闭区间[start,end]共end-start+1行), 修复前除以end-start导致平均值系统性偏大
 */
public class CollectionUtilGroupCalculateTest {

	private static List<Object> row(Object group, Object value) {
		return new ArrayList<Object>(Arrays.asList(group, value));
	}

	@Test
	public void averageDividedByActualRowCount() {
		// 组A:1+2+3=6,平均2;组B:10+20=30,平均15(组B走最后一行收尾分支)
		List<List> dataSet = new ArrayList<List>();
		dataSet.add(row("A", 1));
		dataSet.add(row("A", 2));
		dataSet.add(row("A", 3));
		dataSet.add(row("B", 10));
		dataSet.add(row("B", 20));
		CollectionUtil.groupCalculate(dataSet, new Integer[] { 0 }, 1, false);
		// 修复前:A组除以2得3.0,B组除以1得30.0,平均值系统性偏大
		assertEquals(0, new java.math.BigDecimal("2").compareTo((java.math.BigDecimal) dataSet.get(0).get(2)));
		assertEquals(0, new java.math.BigDecimal("2").compareTo((java.math.BigDecimal) dataSet.get(1).get(2)));
		assertEquals(0, new java.math.BigDecimal("2").compareTo((java.math.BigDecimal) dataSet.get(2).get(2)));
		assertEquals(0, new java.math.BigDecimal("15").compareTo((java.math.BigDecimal) dataSet.get(3).get(2)));
		assertEquals(0, new java.math.BigDecimal("15").compareTo((java.math.BigDecimal) dataSet.get(4).get(2)));
	}

	@Test
	public void sumModeUnchanged() {
		List<List> dataSet = new ArrayList<List>();
		dataSet.add(row("A", 1));
		dataSet.add(row("A", 2));
		dataSet.add(row("A", 3));
		CollectionUtil.groupCalculate(dataSet, new Integer[] { 0 }, 1, true);
		assertEquals(0, new java.math.BigDecimal("6").compareTo((java.math.BigDecimal) dataSet.get(0).get(2)));
	}

	@Test
	public void singleRowGroupUnchanged() {
		List<List> dataSet = new ArrayList<List>();
		dataSet.add(row("C", 7));
		CollectionUtil.groupCalculate(dataSet, new Integer[] { 0 }, 1, false);
		assertEquals(0, new java.math.BigDecimal("7").compareTo((java.math.BigDecimal) dataSet.get(0).get(2)));
	}
}
