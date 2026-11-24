package org.sagacity.sqltoy.utils;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertEquals;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Date;
import java.util.List;

import org.junit.jupiter.api.Test;

/**
 * @project sagacity-sqltoy
 * @description CollectionUtil.sortList/groupSort 的排序列类型判定回归测试：
 *              原先 dataType 由首个非空值定死后对后续取值无条件强转，同一列混有字符串与数字时抛
 *              ClassCastException；改为"两侧都符合 dataType 才类型化比较，否则退回文本比较"后，
 *              混合类型列不再抛异常，而同构列的类型化排序结果必须保持不变。
 * @author zhongxuchen
 */
@SuppressWarnings({ "rawtypes", "unchecked" })
public class CollectionUtilSortMixedTypeTest {

	private static List row(Object... cells) {
		return new ArrayList(Arrays.asList(cells));
	}

	private static List<List> rows(List... rs) {
		return new ArrayList<List>(Arrays.asList(rs));
	}

	private static List firstColumn(List<List> data) {
		List column = new ArrayList(data.size());
		for (List row : data) {
			column.add(row.get(0));
		}
		return column;
	}

	@Test
	public void mixedTypeColumnDoesNotThrowClassCast() {
		List<List> data = rows(row(1), row("abc"), row(2));
		// 首个非空值是数字，dataType 被定为 2(数字)，后续的 "abc" 原先在强转处抛 ClassCastException
		assertEquals(2, CollectionUtil.getSortDataType(data, 0), "首个非空值为数字");
		assertDoesNotThrow(() -> {
			CollectionUtil.sortList(data, 0, 2, 0, data.size() - 1, true);
		}, "混合类型列排序不应抛 ClassCastException");
		assertEquals(3, data.size(), "排序不应丢行");
	}

	@Test
	public void numericSortStillUsesNumericOrder() {
		// 负数与长度不同的数字：数值序为 -5,3,10；纯文本(长度优先)序为 3,-5,10。
		// 断言数值序，确保 instanceof 守卫没有把同构数字列降级为文本比较
		List<List> data = rows(row(3), row(-5), row(10));
		assertEquals(2, CollectionUtil.getSortDataType(data, 0), "同构数字列");
		CollectionUtil.sortList(data, 0, 2, 0, data.size() - 1, true);
		assertEquals(Arrays.asList(-5, 3, 10), firstColumn(data), "数字升序应按数值大小");
		// 降序
		CollectionUtil.sortList(data, 0, 2, 0, data.size() - 1, false);
		assertEquals(Arrays.asList(10, 3, -5), firstColumn(data), "数字降序应按数值大小");
	}

	@Test
	public void dateSortStillUsesDateOrder() {
		Date early = new Date(1000L);
		Date late = new Date(9000L);
		List<List> data = rows(row(late), row(early));
		assertEquals(3, CollectionUtil.getSortDataType(data, 0), "同构日期列");
		CollectionUtil.sortList(data, 0, 3, 0, data.size() - 1, true);
		assertEquals(Arrays.asList(early, late), firstColumn(data), "日期升序应按时间先后");
	}

	@Test
	public void groupSortMixedSortColumnDoesNotThrow() {
		// 分组列含 null，排序列在同组内混合字符串与数字：原先在组内比较时抛 ClassCastException
		// (分组需连续，否则每组只有一行、根本不发生比较)
		List<List> data = rows(row(null, 5), row(null, 1), row("a", "x"), row("a", 9));
		assertDoesNotThrow(() -> {
			CollectionUtil.groupSort(data, new Integer[] { 0 }, 1, true);
		}, "分组排序列混合类型不应抛 ClassCastException");
		assertEquals(4, data.size(), "分组排序不应丢行");
	}
}
