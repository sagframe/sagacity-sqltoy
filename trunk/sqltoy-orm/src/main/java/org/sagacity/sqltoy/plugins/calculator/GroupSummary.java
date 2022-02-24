package org.sagacity.sqltoy.plugins.calculator;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;

import org.sagacity.sqltoy.config.model.LabelIndexModel;
import org.sagacity.sqltoy.config.model.SummaryColMeta;
import org.sagacity.sqltoy.config.model.SummaryGroupMeta;
import org.sagacity.sqltoy.config.model.SummaryModel;
import org.sagacity.sqltoy.utils.CollectionUtil;
import org.sagacity.sqltoy.utils.ExpressionUtil;
import org.sagacity.sqltoy.utils.NumberUtil;
import org.sagacity.sqltoy.utils.StringUtil;

/**
 * @project sqltoy-orm
 * @description 对集合进行分组汇总计算
 * @author zhongxuchen
 * @version v1.0,Date:2020-3-25
 */
public class GroupSummary {
	public static void process(SummaryModel summaryModel, LabelIndexModel labelIndexMap, List result) {
		// 记录小于2条无需汇总计算
		if (result == null || result.size() < 2) {
			return;
		}
		// 计算的列，columns="1..result.width()-1"
		int dataWidth = ((List) result.get(0)).size();
		List<Integer> sumColList = parseColumns(labelIndexMap, summaryModel.getSummaryCols(), dataWidth);
		List<Integer> aveColList = parseColumns(labelIndexMap, summaryModel.getAverageCols(), dataWidth);
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
		boolean bothSumAverage = !sumColList.isEmpty() && !aveColList.isEmpty();
		// 组织分组配置
		String sumSite;
		for (SummaryGroupMeta groupMeta : summaryModel.getGroupMeta()) {
			sumSite = (summaryModel.getSumSite() == null) ? "bottom" : summaryModel.getSumSite().toLowerCase();
			List<Integer> groupColsList = parseColumns(labelIndexMap, groupMeta.getGroupColumn(), dataWidth);
			Integer[] groupCols = new Integer[groupColsList.size()];
			groupColsList.toArray(groupCols);
			// 分组列
			groupMeta.setGroupCols(groupCols);
			groupMeta.setBothSumAverage(bothSumAverage);
			groupMeta.setSumSite(sumSite);
			// 分组的标题列
			groupMeta.setLabelIndex(
					NumberUtil.isInteger(groupMeta.getLabelColumn()) ? Integer.parseInt(groupMeta.getLabelColumn())
							: labelIndexMap.get(groupMeta.getLabelColumn().toLowerCase()));
			// 汇总和求平均分两行组装
			if (bothSumAverage && (sumSite.equals("top") || sumSite.equals("bottom"))) {
				groupMeta.setRowSize(2);
			}
			groupMeta.setSummaryCols(createColMeta(summaryCols, summaryModel, sumColList, aveColList));
		}
		CollectionUtil.groupSummary(result, summaryModel.getGroupMeta(), summaryModel.isReverse(),
				summaryModel.getLinkSign());
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
			colMeta.setAveSkipNull(summaryModel.isAverageSkipNull());
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
				if (roundingSize > 1 && aveIndex < roundingSize) {
					if (roundingModes[aveIndex] != null) {
						colMeta.setRoundingMode(roundingModes[aveIndex]);
					}
				}
				if (radixSizeLen > 1 && aveIndex < radixSizeLen) {
					if (radixSizes[aveIndex] != null) {
						colMeta.setRadixSize(radixSizes[aveIndex]);
					}
				}
				aveIndex++;
			}
			colMetas[i] = colMeta;
		}
		return colMetas;
	}

	/**
	 * @TODO 将columns字符串解析成具体列的数组
	 * @param labelIndexMap
	 * @param columns
	 * @param dataWidth
	 * @return
	 */
	private static List<Integer> parseColumns(LabelIndexModel labelIndexMap, String columns, int dataWidth) {
		List<Integer> result = new ArrayList<Integer>();
		if (StringUtil.isBlank(columns)) {
			return result;
		}
		String cols = columns.replaceAll("result\\.width\\(\\)", Integer.toString(dataWidth))
				.replaceAll("(?i)\\$\\{dataWidth\\}", Integer.toString(dataWidth));
		String[] colsAry = cols.split("\\,");
		String column;
		String endColumnStr;
		int step;
		int stepIndex;
		for (int i = 0; i < colsAry.length; i++) {
			column = colsAry[i].toLowerCase();
			// like {1..20?2} ?step 用于数据间隔性汇总
			if (column.indexOf("..") != -1) {
				step = 1;
				String[] beginToEnd = column.split("\\.\\.");
				int begin = 0;
				int end = 0;
				if (NumberUtil.isInteger(beginToEnd[0])) {
					begin = Integer.parseInt(beginToEnd[0]);
				} else {
					begin = (new BigDecimal(ExpressionUtil.calculate(beginToEnd[0]).toString())).intValue();
				}
				endColumnStr = beginToEnd[1];
				if (NumberUtil.isInteger(endColumnStr)) {
					end = Integer.parseInt(endColumnStr);
					// 负数表示用列宽减去相应值
					if (end < 0) {
						end = dataWidth + end - 1;
					}
				} else {
					stepIndex = endColumnStr.indexOf("?");
					if (stepIndex != -1) {
						step = Integer.parseInt(endColumnStr.substring(stepIndex + 1).trim());
						endColumnStr = endColumnStr.substring(0, stepIndex);
					}
					end = (new BigDecimal(ExpressionUtil.calculate(endColumnStr).toString())).intValue();
				}
				for (int j = begin; j <= end; j += step) {
					if (!result.contains(j)) {
						result.add(j);
					}
				}
			} else if (NumberUtil.isInteger(column)) {
				if (!result.contains(Integer.parseInt(column))) {
					result.add(Integer.parseInt(column));
				}
			} else {
				Integer colIndex;
				if (labelIndexMap.containsKey(column)) {
					colIndex = labelIndexMap.get(column);
				} else {
					colIndex = (new BigDecimal(ExpressionUtil.calculate(column).toString())).intValue();
				}
				if (!result.contains(colIndex)) {
					result.add(colIndex);
				}
			}
		}
		return result;
	}
}
