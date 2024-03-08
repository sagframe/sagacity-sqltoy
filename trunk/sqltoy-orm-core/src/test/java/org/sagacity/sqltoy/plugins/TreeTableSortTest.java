/**
 * 
 */
package org.sagacity.sqltoy.plugins;

import java.util.ArrayList;
import java.util.List;
import java.util.Set;

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
			   { 9, -1, 0, 0, 0,1 }, 
			  
			   { 2, 1, 1, 2, 7 ,1},
			   { 3, 1, 1, 2, 4 ,1},
				{ 4, 3, 6, 2, 5 ,1},
				{ 5, 3, 1, 8, 5,1 },
				{ 6, 5, 1, 0, 5 ,0},
				 { 31, -1, 0, 0, 0,1 }, 
				{ 38, 31, 31, 0, 5 ,0} };
		List treeList = CollectionUtil.arrayToDeepList(treeData);
		TreeSortModel treeModel=new TreeSortModel();
		treeModel.setCompareType("!=");
		treeModel.setFilterColumn("5");
		treeModel.setCompareValues("0");
		LabelIndexModel labelIndexModel=new LabelIndexModel();
		labelIndexModel.put("5", 5);
		Set topPids=TreeDataSort.getTopPids(treeList,0,1);
		List result = new ArrayList();
		List row;
		int pidSize = topPids.size();
		int meter = 0;
		// 提取第一层树节点
		for (int i = 0; i < treeList.size(); i++) {
			row = (List) treeList.get(i);
			if (topPids.contains(row.get(1))) {
				result.add(row);
				meter++;
				treeList.remove(i);
				i--;
			}
			if (pidSize == meter) {
				break;
			}
		}
		int beginIndex = 0;
		int addCount = 0;
		Object idValue;
		Object pidValue;
		while (treeList.size() != 0) {
			addCount = 0;
			// id
			idValue = ((List) result.get(beginIndex)).get(0);
			for (int i = 0; i < treeList.size(); i++) {
				pidValue = ((List) treeList.get(i)).get(1);
				if (idValue.equals(pidValue)) {
					result.add(beginIndex + addCount + 1, treeList.get(i));
					treeList.remove(i);
					addCount++;
					i--;
				}
			}
			// 下一个
			beginIndex++;
			// 防止因数据不符合规则造成的死循环
			if (beginIndex + 1 > result.size()) {
				break;
			}
		}
		treeList.clear();
		treeList.addAll(result);
		
		System.err.println(JSON.toJSONString(treeList));
//		TreeDataSort.summaryTreeList(treeModel, labelIndexModel, treeList, new Integer[] { 2, 3, 4 }, 0, 1);
//		for (Object row : treeList) {
//			System.err.println(JSON.toJSONString(row));
//		}
	}
	
	@Test
	public void testSort() {
		Object[][] treeData = new Object[][] {
			   { 1, -1, 0, 0, 0,1 }, 
			   { 9, -1, 0, 0, 0,1 }, 
			  
			   { 2, 1, 1, 2, 7 ,1},
			   { 3, 1, 1, 2, 4 ,1},
				{ 4, 3, 6, 2, 5 ,1},
				{ 5, 3, 1, 8, 5,1 },
				{ 6, 5, 1, 0, 5 ,0},
				  
				{ 38, 31, 31, 0, 5 ,0} ,
				{ 31, -1, 0, 0, 0,1 }};
		List treeList = CollectionUtil.arrayToDeepList(treeData);
		TreeSortModel treeModel=new TreeSortModel();
		treeModel.setCompareType("!=");
		treeModel.setFilterColumn("5");
		treeModel.setCompareValues("0");
		LabelIndexModel labelIndexModel=new LabelIndexModel();
		labelIndexModel.put("5", 5);
		Set topPids=TreeDataSort.getTopPids(treeList,0,1);
		List result = new ArrayList();
		List row;
		int pidSize = topPids.size();
		int meter = 0;
		// 提取第一层树节点
		for (int i = 0; i < treeList.size(); i++) {
			row = (List) treeList.get(i);
			if (topPids.contains(row.get(1))) {
				result.add(row);
				meter++;
				treeList.remove(i);
				i--;
			}
		}
		int beginIndex = 0;
		int addCount = 0;
		Object idValue;
		Object pidValue;
		while (treeList.size() != 0) {
			addCount = 0;
			// id
			idValue = ((List) result.get(beginIndex)).get(0);
			for (int i = 0; i < treeList.size(); i++) {
				pidValue = ((List) treeList.get(i)).get(1);
				if (idValue.equals(pidValue)) {
					result.add(beginIndex + addCount + 1, treeList.get(i));
					treeList.remove(i);
					addCount++;
					i--;
				}
			}
			// 下一个
			beginIndex++;
			// 防止因数据不符合规则造成的死循环
			if (beginIndex + 1 > result.size()) {
				break;
			}
		}
		treeList.clear();
		treeList.addAll(result);
		for(Object item:treeList) {
		System.err.println(JSON.toJSONString(item));
		}
	}
}
