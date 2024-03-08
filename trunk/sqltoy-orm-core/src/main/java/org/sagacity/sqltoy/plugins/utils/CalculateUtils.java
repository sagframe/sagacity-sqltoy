/**
 * 
 */
package org.sagacity.sqltoy.plugins.utils;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.List;

import org.sagacity.sqltoy.config.model.LabelIndexModel;
import org.sagacity.sqltoy.utils.ExpressionUtil;
import org.sagacity.sqltoy.utils.NumberUtil;
import org.sagacity.sqltoy.utils.StringUtil;

/**
 * @project sagacity-sqltoy
 * @description 提供计算扩展的一些工具
 * @author zhongxuchen
 * @version v1.0, Date:2022年11月18日
 * @modify 2022年11月18日,修改说明
 */
public class CalculateUtils {
	/**
	 * @TODO 将columns字符串解析成具体列的数组
	 * @param labelIndexMap
	 * @param columns
	 * @param dataWidth
	 * @return
	 */
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
}
