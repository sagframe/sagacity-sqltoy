package org.sagacity.sqltoy.utils;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import org.junit.jupiter.api.Test;

/**
 * J9回归:sliceColumn的distinct判重改为LinkedHashSet(原ArrayList.contains为O(n²))。
 * 语义必须保持:去重且保留首次出现顺序,null同样只保留一个;distinct=false时保持全量
 */
public class CollectionUtilSliceColumnDistinctTest {

	private static List<Map<String, Object>> mapRows() {
		List<Map<String, Object>> rows = new ArrayList<Map<String, Object>>();
		for (Object value : new Object[] { 3, 1, 3, 2, 1, null, null }) {
			Map<String, Object> row = new HashMap<String, Object>();
			row.put("id", value);
			rows.add(row);
		}
		return rows;
	}

	private static List<List<Object>> listRows() {
		List<List<Object>> rows = new ArrayList<List<Object>>();
		for (Object value : new Object[] { 3, 1, 3, 2, 1, null, null }) {
			rows.add(new ArrayList<Object>(Arrays.asList(value)));
		}
		return rows;
	}

	private static List<Object[]> arrayRows() {
		List<Object[]> rows = new ArrayList<Object[]>();
		for (Object value : new Object[] { 3, 1, 3, 2, 1, null, null }) {
			rows.add(new Object[] { value });
		}
		return rows;
	}

	@Test
	public void mapColumnDistinctKeepsFirstOccurrenceOrder() {
		List<Map<String, Object>> rows = mapRows();
		// 去重 + 首次出现顺序 + null只留一个
		assertArrayEquals(new Object[] { 3, 1, 2, null }, CollectionUtil.sliceColumn(rows, "id", true));
		// distinct=false 保持全量(含重复与多个null)
		assertArrayEquals(new Object[] { 3, 1, 3, 2, 1, null, null }, CollectionUtil.sliceColumn(rows, "id", false));
	}

	@Test
	public void indexColumnDistinctKeepsFirstOccurrenceOrder() {
		// List行形态
		assertEquals(Arrays.asList(3, 1, 2, null), CollectionUtil.sliceColumn(listRows(), 0, true));
		// 数组行形态
		assertEquals(Arrays.asList(3, 1, 2, null), CollectionUtil.sliceColumn(arrayRows(), 0, true));
		// distinct=false 保持全量
		assertEquals(7, CollectionUtil.sliceColumn(listRows(), 0, false).size());
	}
}
