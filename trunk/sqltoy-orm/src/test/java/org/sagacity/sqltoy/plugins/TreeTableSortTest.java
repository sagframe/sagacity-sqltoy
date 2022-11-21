/**
 * 
 */
package org.sagacity.sqltoy.plugins;

import java.util.List;

import org.junit.jupiter.api.Test;
import org.sagacity.sqltoy.plugins.calculator.TreeDataSort;
import org.sagacity.sqltoy.utils.CollectionUtil;

import com.alibaba.fastjson.JSON;

/**
 * @author zhongxuchen
 *
 */
public class TreeTableSortTest {
	@Test
	public void testSummary() {
		Object[][] treeData = new Object[][] {
			{ 1, -1, 0, 0, 0 },
			{ 2, 1, 1, 2, 7 },
			{ 3, 1, 1, 2, 4 },
			{ 4, 3, 6, 2, 5 }, 
			{ 5, 3, 1, 8, 5 },
			{ 6, 5, 1, 0, 5 } };
		List treeList = CollectionUtil.arrayToDeepList(treeData);
		TreeDataSort.summaryTreeList(treeList, new Integer[] { 2, 3, 4 }, 0, 1);
		for (Object row : treeList) {
			System.err.println(JSON.toJSONString(row));
		}
	}
}
