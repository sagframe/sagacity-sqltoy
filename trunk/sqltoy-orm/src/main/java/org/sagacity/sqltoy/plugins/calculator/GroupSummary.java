package org.sagacity.sqltoy.plugins.calculator;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;

import org.sagacity.sqltoy.config.model.GroupMeta;
import org.sagacity.sqltoy.config.model.SummaryModel;
import org.sagacity.sqltoy.utils.CollectionUtil;
import org.sagacity.sqltoy.utils.ExpressionUtil;
import org.sagacity.sqltoy.utils.NumberUtil;

/**
 * @project sqltoy-orm
 * @description 对集合进行分组汇总计算
 * @author renfei.chen <a href="mailto:zhongxuchen@hotmail.com">联系作者</a>
 * @version id:GroupSummary.java,Revision:v1.0,Date:2020-3-25 上午10:08:15
 */
public class GroupSummary {
	public static void process(SummaryModel summaryModel, HashMap<String, Integer> labelIndexMap, List result) {
		if (result == null || result.size() < 2)
			return;
		List<Integer> sumColList = new ArrayList<Integer>();
		// 参照列，如按年份进行旋转(columns="1..result.width()-1")
		int dataWidth = ((List) result.get(0)).size();
		String cols = summaryModel.getSummaryCols().replaceAll("result\\.width\\(\\)", Integer.toString(dataWidth));
		cols = cols.replaceAll("\\$\\{dataWidth\\}", Integer.toString(dataWidth));
		String[] columns = cols.split(",");
		String column;
		String endColumnStr;
		int step;
		int stepIndex;
		for (int i = 0; i < columns.length; i++) {
			column = columns[i].toLowerCase();
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
					if (!sumColList.contains(j)) {
						sumColList.add(j);
					}
				}
			} else if (NumberUtil.isInteger(column)) {
				if (!sumColList.contains(Integer.parseInt(column))) {
					sumColList.add(Integer.parseInt(column));
				}
			} else {
				Integer colIndex;
				if (labelIndexMap.containsKey(column)) {
					colIndex = labelIndexMap.get(column);
				} else {
					colIndex = (new BigDecimal(ExpressionUtil.calculate(column).toString())).intValue();
				}
				if (!sumColList.contains(colIndex)) {
					sumColList.add(colIndex);
				}
			}
		}
		Integer[] summaryCols = new Integer[sumColList.size()];
		sumColList.toArray(summaryCols);
		boolean hasAverage = false;
		if (summaryModel.getGlobalAverageTitle() != null || summaryModel.getSumSite().equals("left")
				|| summaryModel.getSumSite().equals("right")) {
			hasAverage = true;
		}
		Object[][] groupIndexs = null;
		if (summaryModel.getGroupMeta() != null) {
			groupIndexs = new Object[summaryModel.getGroupMeta().length][5];
			GroupMeta groupMeta;
			// {{汇总列，汇总标题，平均标题，汇总相对平均的位置(left/right/top/bottom)}}
			for (int i = 0; i < groupIndexs.length; i++) {
				Object[] group = new Object[5];
				groupMeta = summaryModel.getGroupMeta()[i];
				group[0] = NumberUtil.isInteger(groupMeta.getGroupColumn())
						? Integer.parseInt(groupMeta.getGroupColumn())
						: labelIndexMap.get(groupMeta.getGroupColumn().toLowerCase());
				group[1] = groupMeta.getSumTitle();
				group[2] = groupMeta.getAverageTitle();
				group[3] = summaryModel.getSumSite();
				if (groupMeta.getLabelColumn() != null) {
					group[4] = NumberUtil.isInteger(groupMeta.getLabelColumn())
							? Integer.parseInt(groupMeta.getLabelColumn())
							: labelIndexMap.get(groupMeta.getLabelColumn().toLowerCase());
				}
				groupIndexs[i] = group;
			}
		}
		int globalLabelIndex = -1;
		if (summaryModel.getGlobalLabelColumn() != null) {
			if (NumberUtil.isInteger(summaryModel.getGlobalLabelColumn())) {
				globalLabelIndex = Integer.parseInt(summaryModel.getGlobalLabelColumn());
			} else {
				globalLabelIndex = labelIndexMap.get(summaryModel.getGlobalLabelColumn().toLowerCase());
			}
		}
		// 逆向汇总
		if (summaryModel.isReverse()) {
			CollectionUtil.groupReverseSummary(result, groupIndexs, summaryCols, globalLabelIndex,
					summaryModel.getGlobalSumTitle(), hasAverage, summaryModel.getGlobalAverageTitle(),
					summaryModel.getRadixSize(), summaryModel.getSumSite());
		} else {
			CollectionUtil.groupSummary(result, groupIndexs, summaryCols, globalLabelIndex,
					summaryModel.getGlobalSumTitle(), hasAverage, summaryModel.getGlobalAverageTitle(),
					summaryModel.getRadixSize(), summaryModel.getSumSite(), summaryModel.isGlobalReverse());
		}
	}
}
