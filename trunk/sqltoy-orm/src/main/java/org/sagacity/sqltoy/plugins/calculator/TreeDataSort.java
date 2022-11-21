/**
 * 
 */
package org.sagacity.sqltoy.plugins.calculator;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

import org.sagacity.sqltoy.config.model.LabelIndexModel;
import org.sagacity.sqltoy.config.model.TreeSortModel;
import org.sagacity.sqltoy.plugins.utils.CalculateUtils;

/**
 * @project sagacity-sqltoy
 * @description 对树型表结构数据进行排序
 * @author zhongxuchen
 * @version v1.0, Date:2022年10月28日
 * @modify 2022年11月19日,增加树结构汇总计算
 */
public class TreeDataSort {
	public static void process(TreeSortModel treeTableSortModel, LabelIndexModel labelIndexMap, List treeList) {
		if (treeList == null || treeList.isEmpty()) {
			return;
		}
		Integer idColIndex = labelIndexMap.get(treeTableSortModel.getIdColumn());
		Integer pidColIndex = labelIndexMap.get(treeTableSortModel.getPidColumn());
		if (idColIndex == null || pidColIndex == null) {
			throw new RuntimeException("对树形结构数据进行排序,未正确指定id-column和pid-column!");
		}
		int dataWidth = ((List) treeList.get(0)).size();
		// 汇总列
		List<Integer> sumColList = CalculateUtils.parseColumns(labelIndexMap, treeTableSortModel.getSumColumns(),
				dataWidth);
		// 获取根节点值
		Set topPids = getTopPids(treeList, idColIndex, pidColIndex);
		List result = new ArrayList();
		List row;
		int pidSize = topPids.size();
		int meter = 0;
		// 提取第一层树节点
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
		// 树结构从底层往上级汇总
		if (!sumColList.isEmpty()) {
			Integer[] sumIndexes = new Integer[sumColList.size()];
			sumColList.toArray(sumIndexes);
			summaryTreeList(treeList, sumIndexes, idColIndex, pidColIndex);
		}
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

	/**
	 * @TODO 对排序后的树结构数据进行汇总，将子级数据汇总到父级上
	 * @param treeList
	 * @param sumIndexes
	 * @param idColIndex
	 * @param pidColIndex
	 */
	public static void summaryTreeList(List treeList, Integer[] sumIndexes, Integer idColIndex, Integer pidColIndex) {
		List idRow;
		Object pid;
		Object id;
		List pidRow;
		Object pidCellValue, idCellValue;
		// 从最后一行开始
		for (int i = treeList.size() - 1; i > 0; i--) {
			idRow = (List) treeList.get(i);
			pid = idRow.get(pidColIndex);
			// 上一行开始寻找父节点
			for (int j = i - 1; j >= 0; j--) {
				pidRow = (List) treeList.get(j);
				id = pidRow.get(idColIndex);
				if (id.equals(pid)) {
					// 汇总列
					for (int sumIndex : sumIndexes) {
						pidCellValue = pidRow.get(sumIndex);
						idCellValue = idRow.get(sumIndex);
						// 父节点汇总列的值为null,将子节点的值转BigDecimal赋上
						if (pidCellValue == null) {
							if (idCellValue == null) {
								pidRow.set(sumIndex, BigDecimal.ZERO);
							} else {
								pidRow.set(sumIndex, new BigDecimal(idCellValue.toString().replace(",", "")));
							}
						} else if (pidCellValue instanceof BigDecimal) {
							// 子节点值+ 父节点值
							if (idCellValue != null) {
								pidRow.set(sumIndex, ((BigDecimal) pidCellValue)
										.add(new BigDecimal(idCellValue.toString().replace(",", ""))));
							}
						} else if (idCellValue != null) {
							// 子节点值+ 父节点值
							pidRow.set(sumIndex, new BigDecimal(pidCellValue.toString().replace(",", ""))
									.add(new BigDecimal(idCellValue.toString().replace(",", ""))));
						} else {
							// 子节点值转BigDecimal
							pidRow.set(sumIndex, new BigDecimal(pidCellValue.toString().replace(",", "")));
						}
					}
					break;
				}
			}
		}
	}
}
