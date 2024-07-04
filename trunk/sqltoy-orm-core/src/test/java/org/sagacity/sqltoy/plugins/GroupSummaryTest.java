package org.sagacity.sqltoy.plugins;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.List;

import org.junit.jupiter.api.Test;
import org.sagacity.sqltoy.config.model.LabelIndexModel;
import org.sagacity.sqltoy.config.model.SummaryColMeta;
import org.sagacity.sqltoy.config.model.SummaryGroupMeta;
import org.sagacity.sqltoy.utils.CollectionUtil;
import org.sagacity.sqltoy.utils.ExpressionUtil;
import org.sagacity.sqltoy.utils.NumberUtil;
import org.sagacity.sqltoy.utils.StringUtil;

import com.alibaba.fastjson2.JSON;

public class GroupSummaryTest {
	@Test
	public void testSummary() {
		Object[][] values = new Object[][] { { "202101", "手机", 100, 2000 }, { "202101", "电脑", 90, null },
				{ "202102", "手机", 80, 1700 }, { "202102", "电脑", 60, 7900 } ,{ "202103", "电脑", 60, 7900 }};
		List dataSet = new ArrayList();
		for (int i = 0; i < values.length; i++) {
			List row = new ArrayList();
			for (int j = 0; j < values[i].length; j++) {
				row.add(values[i][j]);
			}
			dataSet.add(row);
		}

		SummaryColMeta[] colMetas = new SummaryColMeta[2];
		SummaryColMeta colMeta1 = new SummaryColMeta();
		colMeta1.setColIndex(2);
		colMeta1.setAveSkipNull(true);
		colMeta1.setSummaryType(1);
		SummaryColMeta colMeta2 = new SummaryColMeta();
		colMeta2.setColIndex(3);
		colMeta2.setAveSkipNull(true);
		colMeta2.setSummaryType(3);
		colMetas[0] = colMeta1;
		colMetas[1] = colMeta2;

		SummaryColMeta[] colMetas1 = new SummaryColMeta[2];
		SummaryColMeta colMeta11 = new SummaryColMeta();
		colMeta11.setColIndex(2);
		colMeta11.setAveSkipNull(true);
		colMeta11.setSummaryType(1);
		SummaryColMeta colMeta21 = new SummaryColMeta();
		colMeta21.setColIndex(3);
		colMeta21.setAveSkipNull(true);
		colMeta21.setSummaryType(3);
		colMetas1[0] = colMeta11;
		colMetas1[1] = colMeta21;

		SummaryGroupMeta[] groupMetas = new SummaryGroupMeta[2];
		SummaryGroupMeta globalMeta = new SummaryGroupMeta();
		globalMeta.setLabelIndex(0);
		globalMeta.setAverageTitle("平均值");
		globalMeta.setSumTitle("总计");
		globalMeta.setSumSite("top");
		globalMeta.setRowSize(2);
		globalMeta.setSummaryType(3);
		globalMeta.setSummaryCols(colMetas);
		groupMetas[0] = globalMeta;

		SummaryGroupMeta groupMeta = new SummaryGroupMeta();
		groupMeta.setGroupCols(new Integer[] { 0 });
		// groupMeta.setAverageTitle("平均值");
		groupMeta.setSumTitle("小计");
		groupMeta.setLabelIndex(0);
		globalMeta.setSummaryType(1);
		groupMeta.setRowSize(1);
		groupMeta.setSummaryCols(colMetas1);
		groupMeta.setSumSite("left");
		groupMetas[1] = groupMeta;

		CollectionUtil.groupSummary(dataSet, groupMetas, false, null,true);
		for (int i = 0; i < dataSet.size(); i++) {
			System.err.println(JSON.toJSONString(dataSet.get(i)));
		}
	}

	public static List<Integer> parseColumns(LabelIndexModel labelIndexMap, String columns, int dataWidth) {
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
				} else if (labelIndexMap.containsKey(beginToEnd[0])) {
					begin = labelIndexMap.get(beginToEnd[0]);
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
					if (NumberUtil.isInteger(endColumnStr)) {
						end = Integer.parseInt(endColumnStr);
					} else if (labelIndexMap.containsKey(endColumnStr)) {
						end = labelIndexMap.get(endColumnStr);
					} else {
						end = (new BigDecimal(ExpressionUtil.calculate(endColumnStr).toString())).intValue();
					}
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

	@Test
	public void testParseColumns() {
		String columns = "1..column9?2";
		LabelIndexModel labelIndexMap = new LabelIndexModel();
		for (int i = 0; i < 10; i++) {
			labelIndexMap.put("column" + i, i);
		}
		int width = 10;
		List<Integer> result = parseColumns(labelIndexMap, columns, width);
		System.err.println(JSON.toJSONString(result));
	}
}
