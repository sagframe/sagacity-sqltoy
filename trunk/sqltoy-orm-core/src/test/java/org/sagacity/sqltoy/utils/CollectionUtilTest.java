/**
 * 
 */
package org.sagacity.sqltoy.utils;

import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.HashMap;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.Queue;

import org.junit.jupiter.api.Test;
import org.sagacity.sqltoy.model.OverTimeSql;
import org.sagacity.sqltoy.model.PriorityLimitSizeQueue;

import com.alibaba.fastjson.JSON;

/**
 * @project sagacity-sqltoy
 * @description 请在此说明类的功能
 * @author zhong
 * @version v1.0, Date:2020-9-27
 * @modify 2020-9-27,修改说明
 */
public class CollectionUtilTest {
	@Test
	public void testSortTreeFalse() {
		try {
			Object[][] treeArray = new Object[][] { { 2, 1, "" }, { 3, 2, "" }, { 4, 2, "" }, { 5, 3, "" },
					{ 6, 0, "" }, { 7, 0, "" }, { 8, 6, "" }, { 10, 6, "" }, { 9, 8, "" } };

			List treeList = CollectionUtil.arrayToDeepList(treeArray);
			List result = CollectionUtil.sortTreeList(treeList, (obj) -> {
				return new Object[] { ((List) obj).get(0), ((List) obj).get(1) };
			}, -1);
			System.err.println(JSON.toJSONString(result));
		} catch (Exception e) {
			System.err.println(e.getMessage());
		}
	}

	@Test
	public void testSortTreeTrue() {
		try {
			Object[][] treeArray = new Object[][] { { 2, 1, "" }, { 3, 2, "" }, { 4, 2, "" }, { 5, 3, "" },
					{ 6, 0, "" }, { 7, 0, "" }, { 8, 6, "" }, { 10, 6, "" }, { 9, 8, "" } };

			List treeList = CollectionUtil.arrayToDeepList(treeArray);
			List result = CollectionUtil.sortTreeList(treeList, (obj) -> {
				return new Object[] { ((List) obj).get(0), ((List) obj).get(1) };
			}, 1, 0);
			System.err.println(JSON.toJSONString(result));
		} catch (Exception e) {
			System.err.println(e.getMessage());
		}
	}

	@Test
	public void testPivotList() {
		try {
			Object[][] values = { { "5月", "香蕉", 2000 } };

			List dataList = CollectionUtil.arrayToDeepList(values);
			// 参照列，如按年份进行旋转
			Integer[] categoryCols = new Integer[] { 1 };

			// 旋转列，如按年份进行旋转，则旋转列为：年份下面的合格数量、不合格数量等子分类数据
			Integer[] pivotCols = new Integer[] { 2 };
			// 分组主键列（以哪几列为基准）
			Integer[] groupCols = new Integer[] { 0 };
			List categoryList = new ArrayList();
			categoryList.add("香蕉");
			// update 2016-12-13 提取category后进行了排序
			List result = CollectionUtil.pivotList(dataList, categoryList, null, groupCols, categoryCols, pivotCols[0],
					pivotCols[pivotCols.length - 1], null);
			System.err.println(JSON.toJSONString(result));
		} catch (Exception e) {
			System.err.println(e.getMessage());
		}
	}

	@Test
	public void testQueue() {
		Queue<OverTimeSql> priorityQueue = new PriorityLimitSizeQueue(4, new Comparator<OverTimeSql>() {
			@Override
			public int compare(OverTimeSql o1, OverTimeSql o2) {
				return Long.valueOf(o1.getTakeTime() - o2.getTakeTime()).intValue();
			}
		});
		long[] time = new long[] { 10, 28, 7, 49, 8, 32, 82, 71, 90, 29 };
		for (int i = 0; i < time.length; i++) {
			priorityQueue.offer(new OverTimeSql("" + i, "sql" + i, time[i], ""));
		}
		OverTimeSql[] overSqls = new OverTimeSql[priorityQueue.size()];
		priorityQueue.toArray(overSqls);
		// System.err.println(priorityQueue.size());
		for (int i = 0; i < overSqls.length; i++) {
			System.err.println(overSqls[i].getTakeTime());
		}
		List<OverTimeSql> result = CollectionUtilTest.getSlowest(priorityQueue, 2);
		for (OverTimeSql iter : result) {
			System.err.println("耗时=" + iter.getTakeTime());
		}
	}

	public static List<OverTimeSql> getSlowest(Queue<OverTimeSql> queues, int size) {
		List<OverTimeSql> result = new ArrayList<OverTimeSql>();
		Iterator<OverTimeSql> iter = queues.iterator();
		int index = 0;
		int start = queues.size() - size;
		if (start < 0) {
			start = 0;
		}
		OverTimeSql sql;
		while (iter.hasNext()) {
			sql = iter.next();
			if (index >= start) {
				result.add(0, sql);
			}
			index++;
		}
		return result;
	}

	@Test
	public void testQueueTopSize() {
		int size = 15;
		Map<String, OverTimeSql> map = new HashMap<String, OverTimeSql>();
		long[] time = new long[] { 10, 28, 7, 49, 8, 32, 82, 71, 90, 29 };
		for (int i = 0; i < time.length; i++) {
			map.put("sql_" + i, new OverTimeSql("sql_" + i, "sql" + i, time[i], ""));
		}
		List<OverTimeSql> result = new ArrayList<OverTimeSql>();
		Iterator<OverTimeSql> iter = map.values().iterator();
		while (iter.hasNext()) {
			result.add(iter.next());
		}
		Collections.sort(result, new Comparator<OverTimeSql>() {
			public int compare(OverTimeSql o1, OverTimeSql o2) {
				return Long.valueOf(o2.getTakeTime() - o1.getTakeTime()).intValue();
			}
		});
		if (size < result.size()) {
			result = result.subList(0, size - 1);
		}

		for (OverTimeSql sql : result) {
			System.err.println(sql.getTakeTime());
		}

	}

	@Test
	public void testRemove() {
		List dataSet = new ArrayList();
		dataSet.add(1);
		dataSet.add(null);
		dataSet.add(2);
		dataSet.add(null);
		dataSet.add(null);
		dataSet.add(2);
		dataSet.add(null);
		dataSet.add(2);
		CollectionUtil.removeNull(dataSet);
		System.err.println(JSON.toJSONString(dataSet));
	}
}
