/**
 * 
 */
package org.sagacity.sqltoy.plugins.calculator;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

import org.sagacity.sqltoy.config.model.LabelIndexModel;
import org.sagacity.sqltoy.config.model.TreeSortModel;

/**
 * @project sagacity-sqltoy
 * @description 对树型表结构数据进行排序
 * @author zhongxuchen
 * @version v1.0, Date:2022年10月28日
 * @modify 2022年10月28日,修改说明
 */
public class TreeDataSort {
	public static void process(TreeSortModel treeTableSortModel, LabelIndexModel labelIndexMap, List treeList) {
		if (treeList == null) {
			return;
		}
		Integer idColIndex = labelIndexMap.get(treeTableSortModel.getIdColumn());
		Integer pidColIndex = labelIndexMap.get(treeTableSortModel.getPidColumn());
		if (idColIndex == null || pidColIndex == null) {
			throw new RuntimeException("对树形结构数据进行排序,未正确指定id-column和pid-column!");
		}
		// 获取根节点值
		Set topPids = getTopPids(treeList, idColIndex, pidColIndex);
		List result = new ArrayList();
		List row;
		int pidSize = topPids.size();
		int meter = 0;
		//提取第一层树节点
		for (int i = 0; i < treeList.size(); i++) {
			row = (List) treeList.get(i);
			if (topPids.contains(row.get(pidColIndex))) {
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
			idValue = ((List) result.get(beginIndex)).get(idColIndex);
			for (int i = 0; i < treeList.size(); i++) {
				pidValue = ((List) treeList.get(i)).get(pidColIndex);
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
	}

	/**
	 * @TODO 提取根节点
	 * @param treeList
	 * @param idIndex
	 * @param pidIndex
	 * @return
	 */
	private static Set getTopPids(List treeList, int idIndex, int pidIndex) {
		Set<Object> idSet = new HashSet<Object>();
		Set<Object> pidSet = new HashSet<Object>();
		List row;
		for (int i = 0, n = treeList.size(); i < n; i++) {
			row = (List) treeList.get(i);
			idSet.add(row.get(idIndex));
			pidSet.add(row.get(pidIndex));
		}
		Set topPids = new HashSet();
		for (Object pid : pidSet) {
			if (!idSet.contains(pid)) {
				topPids.add(pid);
			}
		}
		return topPids;
	}
}
