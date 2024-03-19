package org.sagacity.sqltoy.plugins.calculator;

import java.math.RoundingMode;
import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;

import org.sagacity.sqltoy.config.model.LabelIndexModel;
import org.sagacity.sqltoy.config.model.SummaryColMeta;
import org.sagacity.sqltoy.config.model.SummaryGroupMeta;
import org.sagacity.sqltoy.config.model.SummaryModel;
import org.sagacity.sqltoy.plugins.utils.CalculateUtils;
import org.sagacity.sqltoy.utils.CollectionUtil;
import org.sagacity.sqltoy.utils.NumberUtil;
import org.sagacity.sqltoy.utils.StringUtil;

/**
 * @project sqltoy-orm
 * @description 对集合进行分组汇总计算
 * @author zhongxuchen
 * @version v1.0,Date:2020-3-25
 * @modify 2022-3-3,完成算法重构，支持分别指定求和、求平均的列，不同分组可以根据averageLabel、sumLabel来判断是否只求和或求平均
 * @modify 2022-5-19,增加skipSingleRow特性，针对单行数据可配置不进行汇总、求平均
 * @modify 2022-11-24,修复汇总计算结果存放于SummaryModel导致的并发场景下的线程安全问题
 * @modify 2023-7-23，增加order-column:
 *         分组排序列，order-with-sum:默认为true，order-way:desc/asc
 */
@SuppressWarnings({ "rawtypes" })
public class GroupSummary {
	public static void process(SummaryModel summaryModel, LabelIndexModel labelIndexMap, List result) {
		// 记录小于2条无需汇总计算
		if (result == null || result.size() < 2) {
			return;
		}
		// 计算的列，columns="1..result.width()-1"
		int dataWidth = ((List) result.get(0)).size();
		List<Integer> sumColList = CalculateUtils.parseColumns(labelIndexMap, summaryModel.getSumColumns(), dataWidth);
		List<Integer> aveColList = CalculateUtils.parseColumns(labelIndexMap, summaryModel.getAveColumns(), dataWidth);
		Set<Integer> summaryColsSet = new LinkedHashSet<Integer>();
		for (Integer index : sumColList) {
			summaryColsSet.add(index);
		}
		for (Integer index : aveColList) {
			summaryColsSet.add(index);
		}
		// 未设置分组和汇总计算列信息
		if (summaryModel.getGroupMeta() == null || summaryModel.getGroupMeta().length == 0
				|| summaryColsSet.size() == 0) {
			throw new IllegalArgumentException("summary计算未正确配置sum-columns或average-columns或group分组信息!");
		}
		// 全部计算列
		Integer[] summaryCols = new Integer[summaryColsSet.size()];
		summaryColsSet.toArray(summaryCols);
		// 同时存在求和、求平均
		boolean bothSumAverage = !sumColList.isEmpty() && !aveColList.isEmpty();
		// 组织分组配置
		String sumSite;
		// 定义分组汇总计算的模型(2022-11-24)
		SummaryGroupMeta[] sumMetas = new SummaryGroupMeta[summaryModel.getGroupMeta().length];
		int i = 0;
		int dataSize = result.size();
		SummaryGroupMeta groupMeta;
		List<Integer> preAllGroups = new ArrayList<Integer>();
		// 判断第几个分组为最后的分组
		int lastGroupOrderIndex = 0;
		for (SummaryGroupMeta meta : summaryModel.getGroupMeta()) {
			if (meta.getOrderColumn() != null) {
				lastGroupOrderIndex++;
			}
		}
		int meter = 0;
		for (SummaryGroupMeta meta : summaryModel.getGroupMeta()) {
			groupMeta = meta.clone();
			sumSite = (summaryModel.getSumSite() == null) ? "top" : summaryModel.getSumSite().toLowerCase();
			List<Integer> groupColsList = CalculateUtils.parseColumns(labelIndexMap, groupMeta.getGroupColumn(),
					dataWidth);
			Integer[] groupCols = new Integer[groupColsList.size()];
			groupColsList.toArray(groupCols);
			// 分组列
			groupMeta.setGroupCols(groupCols);
			preAllGroups.addAll(groupColsList);
			// 需要对数据先分组计算排序
			if (groupMeta.getOrderColumn() != null) {
				meter++;
				Integer sortIndex = labelIndexMap.get(groupMeta.getOrderColumn());
				boolean isSum = true;
				if (groupMeta.getOrderWithSum() != null) {
					isSum = groupMeta.getOrderWithSum();
				} else {
					if (sumColList.contains(sortIndex)) {
						isSum = true;
					} else if (aveColList.contains(sortIndex)) {
						isSum = false;
					}
				}
				// 排序方式
				boolean desc = groupMeta.getOrderWay().equalsIgnoreCase("desc") ? true : false;
				Integer[] groupIndexes = new Integer[preAllGroups.size()];
				preAllGroups.toArray(groupIndexes);
				// 以新增加的末尾列排序
				if (groupIndexes.length == 1) {
					// 先用分组列排序，再通过分组的计算值排序，避免存在不同分组之间计算列的值一样，产生顺序混乱
					if (!summaryModel.isHasGrouped()) {
						int dataType = CollectionUtil.getSortDataType(result, groupIndexes[0]);
						CollectionUtil.sortList(result, groupIndexes[0], dataType, 0, dataSize - 1, !desc);
					}
					// 在每行增加一列计算值，用于排序
					CollectionUtil.groupCalculate(result, groupIndexes, sortIndex, isSum);
					CollectionUtil.sortList(result, dataWidth, 2, 0, dataSize - 1, !desc);
				} else {
					// 先根据上级分组做下级数据的分组，便于下一步的分组计算
					Integer[] sortGroupIndexes = new Integer[groupIndexes.length - 1];
					System.arraycopy(groupIndexes, 0, sortGroupIndexes, 0, groupIndexes.length - 1);
					if (!summaryModel.isHasGrouped()) {
						CollectionUtil.groupSort(result, sortGroupIndexes, groupIndexes[groupIndexes.length - 1], desc);
					}
					// 做分组计算
					CollectionUtil.groupCalculate(result, groupIndexes, sortIndex, isSum);
					// 对分组计算的结果进行排序
					CollectionUtil.groupSort(result, sortGroupIndexes, dataWidth, desc);
					// 最后一个分组调整明细项顺序
					if (meter == lastGroupOrderIndex && !summaryModel.isHasGrouped()) {
						CollectionUtil.groupSort(result, groupIndexes, sortIndex, desc);
					}
				}
				// 剔除新增计算排序列
				for (int k = 0; k < dataSize; k++) {
					((List) result.get(k)).remove(dataWidth);
				}
			}
			if (bothSumAverage) {
				if (StringUtil.isNotBlank(groupMeta.getSumTitle())
						&& StringUtil.isNotBlank(groupMeta.getAverageTitle())) {
					groupMeta.setSummaryType(3);
				} else if (StringUtil.isNotBlank(groupMeta.getAverageTitle())) {
					groupMeta.setSummaryType(2);
				} // summaryType默认为1即sum计算
				else {
					groupMeta.setSummaryType(1);
				}
			} else if (!sumColList.isEmpty()) {
				groupMeta.setSummaryType(1);
			} else if (!aveColList.isEmpty()) {
				groupMeta.setSummaryType(2);
			}

			groupMeta.setSumSite(sumSite);
			// 分组的标题列(默认第一列)
			if (groupMeta.getLabelColumn() == null) {
				groupMeta.setLabelIndex(0);
			} else {
				groupMeta.setLabelIndex(
						NumberUtil.isInteger(groupMeta.getLabelColumn()) ? Integer.parseInt(groupMeta.getLabelColumn())
								: labelIndexMap.get(groupMeta.getLabelColumn().toLowerCase()));
			}
			// 汇总和求平均分两行组装,update 2022-2-28 增加每个分组是否同时有汇总标题和求平均标题，允许不同分组只有汇总或求平均
			if (groupMeta.getSummaryType() == 3 && ("top".equals(sumSite) || "bottom".equals(sumSite))) {
				groupMeta.setRowSize(2);
			}
			groupMeta.setSummaryCols(createColMeta(summaryCols, summaryModel, sumColList, aveColList));
			sumMetas[i] = groupMeta;
			i++;
		}
		CollectionUtil.groupSummary(result, sumMetas, summaryModel.isReverse(), summaryModel.getLinkSign(),
				summaryModel.isSkipSingleRow());
	}

	/**
	 * @TODO 创建每个分组的汇总列配置信息(其中存放了汇总值、汇总的数据个数，所以必须每个分组创建独立的实例)
	 * @param summaryCols
	 * @param summaryModel
	 * @param sumColList
	 * @param aveColList
	 * @return
	 */
	private static SummaryColMeta[] createColMeta(Integer[] summaryCols, SummaryModel summaryModel,
			List<Integer> sumColList, List<Integer> aveColList) {
		SummaryColMeta[] colMetas = new SummaryColMeta[summaryCols.length];
		RoundingMode[] roundingModes = summaryModel.getRoundingModes();
		int roundingSize = (roundingModes == null) ? 0 : roundingModes.length;
		int aveIndex = 0;
		Integer[] radixSizes = summaryModel.getRadixSize();
		int radixSizeLen = (radixSizes == null) ? 0 : radixSizes.length;
		for (int i = 0; i < summaryCols.length; i++) {
			SummaryColMeta colMeta = new SummaryColMeta();
			colMeta.setAveSkipNull(summaryModel.isAveSkipNull());
			colMeta.setSummaryType(0);
			colMeta.setColIndex(summaryCols[i]);
			if (radixSizeLen == 1) {
				colMeta.setRadixSize(radixSizes[0]);
			}
			if (roundingSize == 1) {
				colMeta.setRoundingMode(roundingModes[0]);
			}
			// 存在汇总:1
			if (sumColList.contains(summaryCols[i])) {
				colMeta.setSummaryType(colMeta.getSummaryType() + 1);
			}
			// 存在求平均:2
			if (aveColList.contains(summaryCols[i])) {
				colMeta.setSummaryType(colMeta.getSummaryType() + 2);
				if (roundingSize > 1 && aveIndex < roundingSize && roundingModes[aveIndex] != null) {
					colMeta.setRoundingMode(roundingModes[aveIndex]);
				}
				if (radixSizeLen > 1 && aveIndex < radixSizeLen && radixSizes[aveIndex] != null) {
					colMeta.setRadixSize(radixSizes[aveIndex]);
				}
				aveIndex++;
			}
			colMetas[i] = colMeta;
		}
		return colMetas;
	}
}
