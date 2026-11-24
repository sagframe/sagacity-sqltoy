package org.sagacity.sqltoy.utils;

import static org.junit.jupiter.api.Assertions.assertEquals;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

import org.junit.jupiter.api.Test;

/**
 * @project sagacity-sqltoy
 * @description 分组列值为 null 时的"分组排序"与"分组计算"：原先直接抛空指针(未做空安全比较)，修复后 null 自成一组成员，
 *              与其它分组同样参与组内排序与分组计算；null 与非 null 视为不同组。
 *              两个方法都要求相同分组值的行在集合中连续(由调用方保证)，这是它们一贯的前提。
 * @author zhongxuchen
 */
public class CollectionUtilNullGroupValueTest {
	private static List<List<Object>> rows(Object[]... values) {
		List<List<Object>> rows = new ArrayList<>();
		for (Object[] value : values) {
			rows.add(new ArrayList<>(Arrays.asList(value)));
		}
		return rows;
	}

	/** 取某一列，便于书写期望 */
	private static List<Object> column(List<List<Object>> rows, int index) {
		List<Object> values = new ArrayList<>(rows.size());
		for (List<Object> row : rows) {
			values.add(row.get(index));
		}
		return values;
	}

	@Test
	public void groupSortTreatsNullGroupAsItsOwnGroup() {
		List<List<Object>> data = rows(new Object[] { null, 3 }, new Object[] { null, 1 },
				new Object[] { null, 2 }, new Object[] { "x", 2 });
		CollectionUtil.groupSort((List) data, new Integer[] { 0 }, 1, false);
		assertEquals(Arrays.asList(null, null, null, "x"), column(data, 0), "分组列的值不变(含 null)");
		assertEquals(Arrays.asList(1, 2, 3, 2), column(data, 1), "null 组内按第 1 列升序，非 null 组保持原样");
	}

	@Test
	public void groupSortHandlesNullGroupBetweenOtherGroups() {
		List<List<Object>> data = rows(new Object[] { "a", 5 }, new Object[] { null, 4 },
				new Object[] { null, 2 }, new Object[] { "b", 1 });
		CollectionUtil.groupSort((List) data, new Integer[] { 0 }, 1, true);
		assertEquals(Arrays.asList(5, 4, 2, 1), column(data, 1), "降序：各分组内部按第 1 列降序，分组之间的先后不变");
		assertEquals(Arrays.asList("a", null, null, "b"), column(data, 0));
	}

	@Test
	public void groupCalculateSumsNullGroupWithoutThrowing() {
		List<List<Object>> data = rows(new Object[] { null, 100L }, new Object[] { null, 50L },
				new Object[] { "x", 7L });
		CollectionUtil.groupCalculate((List) data, new Integer[] { 0 }, 1, true);
		assertEquals(Arrays.asList(new BigDecimal("150"), new BigDecimal("150"), new BigDecimal("7")),
				column(data, 2), "null 组的汇总值追加到该组每一行末尾，其它分组各自汇总");
	}

	@Test
	public void groupCalculateAveragesNullGroupWithoutThrowing() {
		List<List<Object>> data = rows(new Object[] { null, 10L }, new Object[] { null, 20L },
				new Object[] { "x", 5L });
		CollectionUtil.groupCalculate((List) data, new Integer[] { 0 }, 1, false);
		// null 组两行：平均=(10+20)/2=15.0000(保留 4 位小数、HALF_DOWN)；单行分组不参与平均，直接是原始汇总值 5
		assertEquals(Arrays.asList(new BigDecimal("15.0000"), new BigDecimal("15.0000"),
				new BigDecimal("5")), column(data, 2));
	}

	@Test
	public void nullAndNonNullGroupValuesAreDifferentGroups() {
		List<List<Object>> data = rows(new Object[] { null, 1 }, new Object[] { "null", 2 });
		CollectionUtil.groupSort((List) data, new Integer[] { 0 }, 1, false);
		assertEquals(Arrays.asList(null, "null"), column(data, 0));
		assertEquals(Arrays.asList(1, 2), column(data, 1), "null 与字符串\"null\"是两个分组，各自单行不排序");
	}
}