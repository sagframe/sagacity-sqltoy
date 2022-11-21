/**
 * 
 */
package org.sagacity.sqltoy.plugins;

import java.util.List;

import org.junit.jupiter.api.Test;
import org.sagacity.sqltoy.config.model.LabelIndexModel;
import org.sagacity.sqltoy.config.model.TreeSortModel;
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
			   { 1, -1, 0, 0, 0,1 }, 
			   { 2, 1, 1, 2, 7 ,1},
			   { 3, 1, 1, 2, 4 ,1},
				{ 4, 3, 6, 2, 5 ,1},
				{ 5, 3, 1, 8, 5,1 },
				{ 6, 5, 1, 0, 5 ,0} };
		List treeList = CollectionUtil.arrayToDeepList(treeData);
		TreeSortModel treeModel=new TreeSortModel();
		treeModel.setCompareType("!=");
		treeModel.setFilterColumn("5");
		treeModel.setCompareValues("0");
		LabelIndexModel labelIndexModel=new LabelIndexModel();
		labelIndexModel.put("5", 5);
		TreeDataSort.summaryTreeList(treeModel, labelIndexModel, treeList, new Integer[] { 2, 3, 4 }, 0, 1);
		for (Object row : treeList) {
			System.err.println(JSON.toJSONString(row));
		}
	}
}
