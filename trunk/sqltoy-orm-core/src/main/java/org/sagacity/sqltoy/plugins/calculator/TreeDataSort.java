package org.sagacity.sqltoy.plugins.calculator;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

import org.sagacity.sqltoy.config.MacroIfLogic;
import org.sagacity.sqltoy.config.model.LabelIndexModel;
import org.sagacity.sqltoy.config.model.TreeSortModel;
import org.sagacity.sqltoy.plugins.utils.CalculateUtils;
import org.sagacity.sqltoy.utils.BeanUtil;
import org.sagacity.sqltoy.utils.CollectionUtil;
import org.sagacity.sqltoy.utils.StringUtil;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * @project sagacity-sqltoy
 * @description 对树型表结构数据进行排序
 * @author zhongxuchen
 * @version v1.0,Date:2022-10-28
 * @modify Date:2022-10-28,修改说明
 * @modify Date:2023-07-23 增加level-order-column属性，支持同层级内数据排序
 * @modify Date:2026-11-24 树形排序、逐层汇总中查找父节点的方式由逐个扫描改为索引，计算结果与原先完全一致
 */
@SuppressWarnings({ "rawtypes", "unchecked" })
public class TreeDataSort {
	private final static Logger logger = LoggerFactory.getLogger(TreeDataSort.class);

	/**
	 * 逐层汇总时先向前探测的行数：父节点通常紧挨在子节点前面，探测命中就不必建立索引
	 */
	private static final int PARENT_PROBE_SIZE = 16;

	public static void process(TreeSortModel treeTableSortModel, LabelIndexModel labelIndexMap, List treeList) {
		if (treeList == null || treeList.isEmpty()) {
			return;
		}
		Integer idColIndex = labelIndexMap.get(treeTableSortModel.getIdColumn());
		Integer pidColIndex = labelIndexMap.get(treeTableSortModel.getPidColumn());
		if (idColIndex == null || pidColIndex == null) {
			throw new RuntimeException(
					"sorting tree structure data does not correctly specify id-column and pid-column, please check!");
		}
		int dataWidth = ((List) treeList.get(0)).size();
		// 汇总列
		List<Integer> sumColList = CalculateUtils.parseColumns(labelIndexMap, treeTableSortModel.getSumColumns(),
				dataWidth);
		// 组织树形结构
		sortTree(treeList, idColIndex, pidColIndex);
		// 树结构从底层往上级汇总
		if (!sumColList.isEmpty()) {
			Integer[] sumIndexes = sumColList.toArray(new Integer[0]);
			summaryTreeList(treeTableSortModel, labelIndexMap, treeList, sumIndexes, idColIndex, pidColIndex);
		}
		// 对每层的数据进行排序
		if (StringUtil.isNotBlank(treeTableSortModel.getLevelOrderColumn())) {
			Integer sortColIndex = labelIndexMap.get(treeTableSortModel.getLevelOrderColumn());
			if (sortColIndex == null) {
				throw new RuntimeException(
						"sorting inside each level of the tree structure does not correctly specify the level order column:levelOrderColumn="
								+ treeTableSortModel.getLevelOrderColumn() + ", please check!");
			}
			int dataType = CollectionUtil.getSortDataType(treeList, sortColIndex);
			boolean desc = treeTableSortModel.getOrderWay().equalsIgnoreCase("desc") ? true : false;
			// 整个集合按某列的值进行排序
			CollectionUtil.sortList(treeList, sortColIndex, dataType, 0, treeList.size() - 1, !desc);
			// 再重新进行树节点组织
			sortTree(treeList, idColIndex, pidColIndex);
		}
	}

	/**
	 * 按照树的父子关系组织顺序
	 * 
	 * @param treeList
	 * @param idColIndex
	 * @param pidColIndex
	 */
	private static void sortTree(List treeList, Integer idColIndex, Integer pidColIndex) {
		// 获取根节点值
		int totalRows = treeList.size();
		Set topPids = getTopPids(treeList, idColIndex, pidColIndex);
		List result = new ArrayList();
		List rest = new ArrayList(totalRows);
		List row;
		// update 2026-11-24 根节点提取改为一次遍历分摊：原先对命中根的行调用 remove(i)，根节点很多时是平方级；
		// 其余行保持原有先后顺序
		for (int i = 0; i < totalRows; i++) {
			row = (List) treeList.get(i);
			if (topPids.contains(row.get(pidColIndex))) {
				result.add(row);
			} else {
				rest.add(row);
			}
		}
		treeList.clear();
		treeList.addAll(rest);
		// update 2026-11-24 没有根节点(pid 全部能在 id 中找到，典型是成环数据，或 id/pid 取值类型不一致)时，
		// 原先在取第一个根节点处抛数组越界、提示语看不出原因，这里先给出带说明的异常
		if (result.isEmpty() && !treeList.isEmpty()) {
			throw new IllegalArgumentException("invalid tree data: no root node found (rows=" + treeList.size()
					+ ", id column index=" + idColIndex + ", pid column index=" + pidColIndex
					+ "), please check that the id and pid values are of comparable types and that pid really references id");
		}
		// update 2026-11-24 id 与 pid 取值类型完全无法匹配时，任何 pid 都不会等于任何 id，所有行都会被当作根节点，
		// 树形结构与逐层汇总实际不会发生却不报错，这里给出提示(不改变结果)。
		// 用debug而非warn:按条件过滤后的树形查询(如只查顶层)本来就会"所有行都是根",属正常用法,
		// 若每次查询都告警,大结果集场景会持续刷日志
		if (result.size() == totalRows && totalRows > 1
				&& hasIncompatibleIdPidTypes(result, idColIndex, pidColIndex)) {
			logger.debug(
					"TreeDataSort: no pid value matches any id value (rows={}, id column index={}, pid column index={}), every row is treated as a root node and the tree roll-up will not happen, please check that pid really references id",
					totalRows, idColIndex, pidColIndex);
		}
		// update 2026-11-24 原实现对result中的每个节点都全量扫描剩余节点(O(n²))，改为按pid分组后前序挂接一次(O(n))，挂接顺序与原先一致
		CollectionUtil.appendTreeChildren(result, treeList,
				(item) -> new Object[] { ((List) item).get(idColIndex), ((List) item).get(pidColIndex) });
		// update 2026-11-24 环上的行挂不到任何根上，原先被丢弃且没有任何提示，这里给出告警(丢弃是既有行为，不变)
		int unlinked = treeList.size();
		if (unlinked > 0) {
			logger.warn(
					"TreeDataSort: {} row(s) can not be linked to any root node (circular reference or missing parent) and are removed from the result",
					unlinked);
		}
		treeList.clear();
		treeList.addAll(result);
	}

	/**
	 * id 列与 pid 列的取值类型是否完全无法匹配：两组类型之间既不相同，也无法相互赋值。
	 * 仅用于诊断提示——类型不相交时任何 pid 都不会等于任何 id，所有行都会被当作根节点
	 * 
	 * @param treeList   树形数据集合(取值不为null的行参与判断)
	 * @param idColIndex id列序号
	 * @param pidColIndex pid列序号
	 * @return true表示两组取值类型完全无法匹配；任一侧没有非null取值时返回false
	 */
	static boolean hasIncompatibleIdPidTypes(List treeList, Integer idColIndex, Integer pidColIndex) {
		Set<Class<?>> idTypes = new HashSet<Class<?>>();
		Set<Class<?>> pidTypes = new HashSet<Class<?>>();
		Object idValue;
		Object pidValue;
		List row;
		for (int i = 0, n = treeList.size(); i < n; i++) {
			row = (List) treeList.get(i);
			idValue = row.get(idColIndex);
			pidValue = row.get(pidColIndex);
			if (idValue != null) {
				idTypes.add(idValue.getClass());
			}
			if (pidValue != null) {
				pidTypes.add(pidValue.getClass());
			}
		}
		if (idTypes.isEmpty() || pidTypes.isEmpty()) {
			return false;
		}
		for (Class<?> idType : idTypes) {
			for (Class<?> pidType : pidTypes) {
				// 类型相同或可相互赋值(如时间戳与日期)都视为有匹配的可能
				if (idType == pidType || idType.isAssignableFrom(pidType) || pidType.isAssignableFrom(idType)) {
					return false;
				}
			}
		}
		return true;
	}

	/**
	 * 提取根节点
	 * 
	 * @param treeList
	 * @param idIndex
	 * @param pidIndex
	 * @return
	 */
	public static Set getTopPids(List treeList, int idIndex, int pidIndex) {
		int size = treeList.size();
		List idValues = new ArrayList(size);
		Set<Object> pidSet = new HashSet<Object>();
		List row;
		for (int i = 0; i < size; i++) {
			row = (List) treeList.get(i);
			idValues.add(row.get(idIndex));
			pidSet.add(row.get(pidIndex));
		}
		// update 2026-11-24 id 与 pid 的匹配改为宽松比较(类型不同按文本，见 BeanUtil.equalsIgnoreType)：
		// 原先用 HashSet 的 equals 判断，id 为 bigint、pid 为 int 这类类型差异会让所有 pid 都"不在 id 中"，
		// 结果整批数据都被当成根节点、树形结构与逐层汇总实际不发生。这里用"文本 -> id值"的索引定位候选再校验
		Map<String, List<Object>> idByText = new HashMap<String, List<Object>>(size * 2);
		boolean allIdIndexable = true;
		Object idValue;
		String idText;
		List<Object> bucket;
		for (int i = 0; i < size; i++) {
			idValue = idValues.get(i);
			if (idValue != null && !CollectionUtil.isIndexKey(idValue)) {
				allIdIndexable = false;
			}
			idText = treeKeyText(idValue);
			bucket = idByText.get(idText);
			if (bucket == null) {
				bucket = new ArrayList<Object>();
				idByText.put(idText, bucket);
			}
			bucket.add(idValue);
		}
		Set topPids = new HashSet();
		for (Object pid : pidSet) {
			if (!containsId(idByText, allIdIndexable, idValues, pid)) {
				topPids.add(pid);
			}
		}
		return topPids;
	}

	/**
	 * pid 是否存在与之宽松相等的 id(类型相同按 equals，类型不同按文本比较)
	 */
	private static boolean containsId(Map<String, List<Object>> idByText, boolean allIdIndexable, List idValues,
			Object pidValue) {
		List<Object> bucket = idByText.get(treeKeyText(pidValue));
		if (bucket != null) {
			for (int i = 0; i < bucket.size(); i++) {
				if (BeanUtil.equalsIgnoreType(bucket.get(i), pidValue, false)) {
					return true;
				}
			}
		}
		if (allIdIndexable) {
			return false;
		}
		// id 中存在非常规取值类型(如自定义对象)：equals 相等但文本可能不同，逐个比对
		for (int i = 0; i < idValues.size(); i++) {
			if (BeanUtil.equalsIgnoreType(idValues.get(i), pidValue, false)) {
				return true;
			}
		}
		return false;
	}

	/**
	 * 树形数据中取值用于索引的文本
	 */
	private static String treeKeyText(Object value) {
		return (value == null) ? "null" : value.toString();
	}

	/**
	 * 对排序后的树结构数据进行汇总，将子级数据汇总到父级上
	 * 
	 * @param treeTableSortModel
	 * @param labelIndexMap
	 * @param treeList
	 * @param sumIndexes
	 * @param idColIndex
	 * @param pidColIndex
	 */
	public static void summaryTreeList(TreeSortModel treeTableSortModel, LabelIndexModel labelIndexMap, List treeList,
			Integer[] sumIndexes, Integer idColIndex, Integer pidColIndex) {
		List idRow;
		Object pid;
		boolean hasFilter = false;
		if (StringUtil.isNotBlank(treeTableSortModel.getFilterColumn())
				&& StringUtil.isNotBlank(treeTableSortModel.getCompareValues())
				&& StringUtil.isNotBlank(treeTableSortModel.getCompareType())) {
			hasFilter = true;
		}
		boolean doSum = true;
		// 从最后一行开始
		Object filterValue;
		// update 2026-11-24 父节点查找优化：原先每一行都从上一行起逐行向前找父节点，同一层级节点很多时是O(n²)。
		// 现先向前探测紧邻的若干行(父节点通常紧挨在子节点前面)，探测范围内没有、且前面还有行时才建立一次 id -> 行号 的索引；
		// id为null或不是常规取值类型时不建索引，仍按原先的方式逐行扫描，行为保持不变
		boolean indexUsable = true;
		// 汇总列正好是 id 列时，id 会在汇总过程中被改写：原先每次查找读到的都是最新值，而索引会保留旧值，因此这种情况不建索引
		if (idColIndex != null && sumIndexes != null) {
			for (Integer sumIndex : sumIndexes) {
				if (sumIndex != null && sumIndex.intValue() == idColIndex.intValue()) {
					indexUsable = false;
					break;
				}
			}
		}
		Map<String, List<Integer>> idIndex = null;
		for (int i = treeList.size() - 1; i > 0; i--) {
			idRow = (List) treeList.get(i);
			pid = idRow.get(pidColIndex);
			doSum = true;
			if (hasFilter) {
				filterValue = idRow.get(labelIndexMap.get(treeTableSortModel.getFilterColumn()));
				doSum = MacroIfLogic.compare(filterValue, treeTableSortModel.getCompareType(),
						treeTableSortModel.getCompareValues(), null);
			}
			// 上一行开始寻找父节点
			if (doSum) {
				int parentIndex = -1;
				if (indexUsable) {
					for (int j = i - 1, limit = Math.max(0, i - PARENT_PROBE_SIZE); j >= limit; j--) {
						// 宽松比较：id 与 pid 类型不同时按文本(如 bigint 对 int)
						if (BeanUtil.equalsIgnoreType(((List) treeList.get(j)).get(idColIndex), pid, false)) {
							parentIndex = j;
							break;
						}
					}
					if (parentIndex < 0 && i > PARENT_PROBE_SIZE) {
						if (idIndex == null) {
							idIndex = new HashMap<String, List<Integer>>(i * 2);
							for (int j = 0; j < i; j++) {
								Object idValue = ((List) treeList.get(j)).get(idColIndex);
								if (!CollectionUtil.isIndexKey(idValue)) {
									indexUsable = false;
									idIndex = null;
									break;
								}
								String idText = treeKeyText(idValue);
								List<Integer> positions = idIndex.get(idText);
								if (positions == null) {
									positions = new ArrayList<Integer>();
									idIndex.put(idText, positions);
								}
								positions.add(j);
							}
						}
						if (idIndex != null) {
							List<Integer> positions = idIndex.get(treeKeyText(pid));
							if (positions != null) {
								// 取当前行之前最近的那个，且需通过宽松比较校验(文本相同的不同取值，如毫秒不同的日期)
								for (int p = positions.size() - 1; p >= 0; p--) {
									int candidate = positions.get(p);
									if (candidate < i && BeanUtil
											.equalsIgnoreType(((List) treeList.get(candidate)).get(idColIndex), pid, false)) {
										parentIndex = candidate;
										break;
									}
								}
							}
						}
					}
				}
				if (parentIndex < 0 && !indexUsable) {
					parentIndex = findParentIndex(treeList, i, idColIndex, pid);
				}
				if (parentIndex >= 0) {
					sumToParent((List) treeList.get(parentIndex), idRow, sumIndexes);
				}
			}
		}
	}

	/**
	 * 从当前行的上一行起逐行向前查找父节点(第一个id与当前行pid相等的行)
	 * 
	 * @param treeList    树形数据集合(已按树形前序排列)
	 * @param currentIndex 当前行序号
	 * @param idColIndex  id列序号
	 * @param pid         当前行的pid
	 * @return 父节点的行序号，没有找到返回-1
	 */
	private static int findParentIndex(List treeList, int currentIndex, Integer idColIndex, Object pid) {
		List pidRow;
		Object id;
		for (int j = currentIndex - 1; j >= 0; j--) {
			pidRow = (List) treeList.get(j);
			id = pidRow.get(idColIndex);
			// 宽松比较：id 与 pid 类型不同时按文本
			if (BeanUtil.equalsIgnoreType(id, pid, false)) {
				return j;
			}
		}
		return -1;
	}

	/**
	 * 将子节点的汇总列数值累加到父节点上
	 * 
	 * @param pidRow     父节点行(直接在其上修改)
	 * @param idRow      子节点行
	 * @param sumIndexes 参与汇总的列序号
	 */
	private static void sumToParent(List pidRow, List idRow, Integer[] sumIndexes) {
		Object pidCellValue, idCellValue;
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
	}
}
