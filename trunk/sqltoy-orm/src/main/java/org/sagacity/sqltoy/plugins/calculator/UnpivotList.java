/**
 * 
 */
package org.sagacity.sqltoy.plugins.calculator;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;

import org.sagacity.sqltoy.config.model.UnpivotModel;
import org.sagacity.sqltoy.model.DataSetResult;
import org.sagacity.sqltoy.utils.CollectionUtil;
import org.sagacity.sqltoy.utils.NumberUtil;

/**
 * @project sqltoy-orm
 * @description 对集合进行列转行处理
 * @author renfei.chen <a href="mailto:zhongxuchen@hotmail.com">联系作者</a>
 * @version id:UnpivotList.java,Revision:v1.0,Date:2020-3-25 上午10:08:15
 */
public class UnpivotList {
	public static List process(UnpivotModel unpivotModel, DataSetResult resultModel,
			HashMap<String, Integer> labelIndexMap, List result) {
		if (result == null || result.isEmpty()) {
			return result;
		}
		int cols = unpivotModel.getColumnsToRows().length;
		List newResult = new ArrayList();
		Integer[] unpivotCols = new Integer[cols];
		Integer[] sortUnpivotCols = new Integer[cols];
		String[] indexColValues = new String[cols];
		String[] colsAndIndexValue = null;

		String colIndex;
		for (int i = 0; i < cols; i++) {
			// columnsToRows配置时格式为:colName1:rowValue1,colName2:rowValue2格式,经过解析每个里面是colName:rowValue
			// 格式
			colsAndIndexValue = unpivotModel.getColumnsToRows()[i].replaceFirst("\\：", ":").split("\\:");
			colIndex = colsAndIndexValue[0].toLowerCase().trim();
			if (NumberUtil.isInteger(colIndex)) {
				unpivotCols[i] = Integer.parseInt(colIndex);
			} else {
				unpivotCols[i] = labelIndexMap.get(colIndex);
			}
			indexColValues[i] = colsAndIndexValue[1].trim();
			sortUnpivotCols[i] = unpivotCols[i];
		}
		// 判断select 中是否已经预留了旋转列label和value对应的字段，如果有则update相关列的数据
		// 如:select bizDate,'' as unpivot_label,null as
		// unpivot_value,unpivot_col1,unpivot_col2
		// 结果:bizDate unpivot_label unpivot_value
		// ==== 2015-12-1--- 个人 -------1000
		// ==== 2015-12-1--- 企业 -------4000
		// 排序,从大到小排
		CollectionUtil.sortArray(sortUnpivotCols, true);
		// 插在最开始被旋转的列位置前
		int addIndex = sortUnpivotCols[sortUnpivotCols.length - 1].intValue();
		if (addIndex < 0) {
			addIndex = 0;
		}
		// 将多列转成行，记录数量是列的倍数
		int size = result.size() * cols;
		for (int i = 0; i < size; i++) {
			List row = (List) ((ArrayList) result.get(i / cols)).clone();
			// 标题列
			row.add(addIndex, indexColValues[i % cols]);
			row.add(addIndex + 1, row.get(unpivotCols[i % cols] + 1));
			// 从最大列进行删除被旋转的列
			for (int j = 0; j < cols; j++) {
				row.remove(sortUnpivotCols[j].intValue() + 2);
			}
			newResult.add(row);
		}

		// 标题的名称
		String[] labelNames = resultModel.getLabelNames();
		String[] labelTypes = resultModel.getLabelTypes();
		List<String> labelList = new ArrayList<String>();
		List<String> labelTypeList = new ArrayList<String>();
		for (int i = 0; i < labelNames.length; i++) {
			labelList.add(labelNames[i]);
			labelTypeList.add(labelTypes[i]);
		}

		// 变成行的列标题是否作为一列
		String[] newColsLabels = unpivotModel.getNewColumnsLabels();
		// 设置默认新列的标题
		if (newColsLabels == null || newColsLabels.length == 0) {
			newColsLabels = new String[] { "indexName", "indexValue" };
		}
		labelList.add(addIndex, newColsLabels[0]);
		labelTypeList.add(addIndex, "string");
		labelList.add(addIndex + 1, newColsLabels[1]);
		labelTypeList.add(addIndex + 1, "object");
		// 移除转变成行的列
		for (int j = 0; j < cols; j++) {
			labelList.remove(sortUnpivotCols[j].intValue() + 2);
			labelTypeList.remove(sortUnpivotCols[j].intValue() + 2);
		}
		String[] newLabelNames = new String[labelList.size()];
		String[] newLabelTypes = new String[labelList.size()];
		labelList.toArray(newLabelNames);
		labelTypeList.toArray(newLabelTypes);
		resultModel.setLabelNames(newLabelNames);
		resultModel.setLabelTypes(newLabelTypes);
		return newResult;
	}
}
