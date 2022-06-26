/**
 * 
 */
package org.sagacity.sqltoy.utils;

import java.util.ArrayList;
import java.util.List;
import java.util.Queue;

import org.junit.jupiter.api.Test;
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
		Queue priorityQueue = new PriorityLimitSizeQueue(10);
		for (int i = 0; i < 20; i++) {
			priorityQueue.offer(i);
		}
		System.err.println(priorityQueue.size());
	}

}
