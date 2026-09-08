package org.sagacity.sqltoy.plugins.calculator;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

import org.sagacity.sqltoy.config.model.LabelIndexModel;
import org.sagacity.sqltoy.config.model.UnpivotModel;
import org.sagacity.sqltoy.model.inner.DataSetResult;
import org.sagacity.sqltoy.utils.CollectionUtil;
import org.sagacity.sqltoy.utils.NumberUtil;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * @project sagacity-sqltoy
 * @description 对集合进行列转行处理
 * @author zhongxuchen
 * @version v1.0,Date:2020-03-25
 * @modify Date:2022-05-11 支持多组列转行
 */
@SuppressWarnings({ "rawtypes", "unchecked" })
public class UnpivotList {
	private final static Logger logger = LoggerFactory.getLogger(UnpivotList.class);

	/**
	 * 对集合进行单组、多组列转行处理
	 * 
	 * @param unpivotModel
	 * @param resultModel   提供label、labelType 进行重写
	 * @param labelIndexMap sql查询出来的集合:标题 对应 列的map,通过label定义第几列
	 * @param result
	 * @return
	 */
	public static List process(UnpivotModel unpivotModel, DataSetResult resultModel, LabelIndexModel labelIndexMap,
			List result) {
		if (result == null || result.isEmpty()) {
			return result;
		}
		try {
			// 多组旋转，避免影响原本单组，独立一个方法进行处理
			if (unpivotModel.getGroupSize() > 1) {
				return multGroupUnpivot(unpivotModel, resultModel, labelIndexMap, result);
			}
			int cols = unpivotModel.getColumnsToRows().length;
			List newResult = new ArrayList();
			Integer[] unpivotCols = new Integer[cols];
			Integer[] sortUnpivotCols = new Integer[cols];
			String[] indexColValues = new String[cols];
			String[] colsAndIndexValue = null;
			String colIndex;
			for (int i = 0; i < cols; i++) {
				// columnsToRows配置时格式为:colName1:rowValue1,colName2:rowValue2格式
				// 经过解析每个里面是colName:rowValue格式
				colsAndIndexValue = unpivotModel.getColumnsToRows()[i].replaceFirst("\\：", ":").split("\\:");
				colIndex = colsAndIndexValue[0].toLowerCase(Locale.ROOT).trim();
				if (NumberUtil.isInteger(colIndex)) {
					unpivotCols[i] = Integer.parseInt(colIndex);
				} else {
					unpivotCols[i] = labelIndexMap.get(colIndex);
				}
				indexColValues[i] = (colsAndIndexValue.length > 1) ? colsAndIndexValue[1].trim()
						: colsAndIndexValue[0].trim();
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
			int resultSize = result.size() * cols;
			for (int i = 0; i < resultSize; i++) {
				List row = (List) ((ArrayList) result.get(i / cols)).clone();
				// 标题列
				row.add(addIndex, indexColValues[i % cols]);
				// 值列
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
			// 移除转变成行的列，为什么+2，标题列和值列
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
		} catch (IndexOutOfBoundsException iot) {
			logger.error("process method execution failed", iot);
			throw new RuntimeException(
					"Array index out of bounds in columns-to-rows processing, please check whether the columns-to-rows and new-columns-labels (e.g. =indexName,indexValue are two attributes) configuration is valid!"
							+ iot.getMessage());
		}
	}

	/**
	 * 2022-5-11 支持将多组列进行旋转，每组对应一列
	 * 
	 * @param unpivotModel
	 * @param resultModel
	 * @param labelIndexMap
	 * @param result
	 * @return
	 */
	private static List multGroupUnpivot(UnpivotModel unpivotModel, DataSetResult resultModel,
			LabelIndexModel labelIndexMap, List result) {
		// 每组的旋转列的数量n(必须相同)，形成最终原本每行数据变成n行
		int pivotRows = unpivotModel.getColumnsToRows()[0].replace("{", "").replace("}", "").trim().split("\\,").length;
		int groupSize = unpivotModel.getGroupSize();
		int totalUnpivotCols = groupSize * pivotRows;
		Integer[][] unpivotCols = new Integer[groupSize][pivotRows];
		// 将旋转的所有列放在一个数组中，然后进行排序，确定旋转新增的列插入位置
		Integer[] sortUnpivotCols = new Integer[totalUnpivotCols];
		// 旋转后多行对应的指标名称值
		String[] indexColValues = new String[pivotRows];
		String[] colsAndIndexValue = null;
		String colIndex;
		String[] groupCols;
		for (int i = 0; i < groupSize; i++) {
			// 剔除分组符号{}
			groupCols = unpivotModel.getColumnsToRows()[i].replace("{", "").replace("}", "").trim().split("\\,");
			if (groupCols.length != pivotRows) {
				throw new IllegalArgumentException(
						"unpivot multiple groups columns-to-rows requires each group {col1,col2} to have the same length, group ["
								+ (i + 1) + "] has [" + groupCols.length + "] pivot columns but expect [" + pivotRows
								+ "], please check the columns-to-rows configuration!");
			}
			for (int j = 0; j < pivotRows; j++) {
				colsAndIndexValue = groupCols[j].replaceFirst("\\：", ":").split("\\:");
				colIndex = colsAndIndexValue[0].toLowerCase(Locale.ROOT).trim();
				if (NumberUtil.isInteger(colIndex)) {
					unpivotCols[i][j] = Integer.parseInt(colIndex);
				} else {
					unpivotCols[i][j] = labelIndexMap.get(colIndex);
				}
				if (i == 0) {
					indexColValues[j] = (colsAndIndexValue.length > 1) ? colsAndIndexValue[1].trim()
							: colsAndIndexValue[0].trim();
				}
				sortUnpivotCols[i * pivotRows + j] = unpivotCols[i][j];
			}
		}
		// 排序,从大到小排
		CollectionUtil.sortArray(sortUnpivotCols, true);
		// 插在最开始被旋转的列位置前
		int addIndex = sortUnpivotCols[sortUnpivotCols.length - 1].intValue();
		if (addIndex < 0) {
			addIndex = 0;
		}
		// 将多列转成行，记录数量是列的倍数
		int resultSize = result.size() * pivotRows;
		List newResult = new ArrayList();
		for (int i = 0; i < resultSize; i++) {
			List row = (List) ((ArrayList) result.get(i / pivotRows)).clone();
			// 标题列
			row.add(addIndex, indexColValues[i % pivotRows]);
			// 值列
			for (int j = 0; j < groupSize; j++) {
				row.add(addIndex + 1 + j, row.get(unpivotCols[j][i % pivotRows] + 1 + j));
			}
			// 从最大列进行删除被旋转的列
			for (int j = 0; j < totalUnpivotCols; j++) {
				row.remove(sortUnpivotCols[j].intValue() + 1 + groupSize);
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
		// 变成行的列标题是否作为一列，指标列，最小值列、最大值列 等
		String[] newColsLabels = unpivotModel.getNewColumnsLabels();
		// 设置默认新列的标题
		if (newColsLabels == null || newColsLabels.length == 0) {
			newColsLabels = new String[1 + groupSize];
			newColsLabels[0] = "indexName";
			for (int i = 0; i < groupSize; i++) {
				newColsLabels[1 + i] = "indexValue" + ((i == 0) ? "" : i);
			}
		} else if (newColsLabels.length != groupSize + 1) {
			throw new IllegalArgumentException(
					"invalid new-columns-labels configuration for unpivot multiple groups columns-to-rows: 1 index name column + "
							+ groupSize + " pivot columns, expect [" + (1 + groupSize)
							+ "] column labels! e.g. \"quarter,minTurnover,maxTurnover\"");
		}

		labelList.add(addIndex, newColsLabels[0]);
		labelTypeList.add(addIndex, "string");
		for (int i = 0; i < groupSize; i++) {
			labelList.add(addIndex + 1 + i, newColsLabels[1 + i]);
			labelTypeList.add(addIndex + 1 + i, "object");
		}
		// 移除转变成行的列，为什么1+groupSize,1列标题，多组数据旋转增加groupSize列
		for (int i = 0; i < totalUnpivotCols; i++) {
			labelList.remove(sortUnpivotCols[i].intValue() + 1 + groupSize);
			labelTypeList.remove(sortUnpivotCols[i].intValue() + 1 + groupSize);
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
