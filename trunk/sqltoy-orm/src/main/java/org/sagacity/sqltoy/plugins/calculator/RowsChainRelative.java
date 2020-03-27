package org.sagacity.sqltoy.plugins.calculator;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;

import org.sagacity.sqltoy.config.model.RowsChainRelativeModel;
import org.sagacity.sqltoy.utils.CollectionUtil;
import org.sagacity.sqltoy.utils.NumberUtil;
import org.sagacity.sqltoy.utils.StringUtil;

/**
 * @project sqltoy-orm
 * @description 对集合数据以行与行之间的比较(环比计算)
 * @author renfei.chen <a href="mailto:zhongxuchen@hotmail.com">联系作者</a>
 * @version id:RowsChainRelative.java,Revision:v1.0,Date:2020-3-25 上午10:08:15
 */
public class RowsChainRelative {
	// |月份 | 产品 |交易笔数 | 环比 | 金额 | 环比 | 收入 | 环比 |
	// | 5月 | 香蕉 | 2000 | 环比 | 金额 | 环比 | 收入 | 环比 |
	// | 5月 | 苹果 | 2000 | 环比 | 金额 | 环比 | 收入 | 环比 |
	// | 4月 | 香蕉 | 2000 | 环比 | 金额 | 环比 | 收入 | 环比 |0
	// | 4月 | 苹果 | 2000 | 环比 | 金额 | 环比 | 收入 | 环比 |1
	// | 3月 | 香蕉 | 2000 | 环比 | 金额 | 环比 | 收入 | 环比 |
	// | 3月 | 苹果 | 2000 | 环比 | 金额 | 环比 | 收入 | 环比 |
	/**
	 * <rows-chain-relative group-size="3" relative-index="0,1,2"
	 */
	public static void process(RowsChainRelativeModel rowsRelative, HashMap<String, Integer> labelIndexMap,
			List result) {
		if (result == null || result.size() < 2)
			return;
		int dataSize = result.size();
		// 5,3-2+0+1
		boolean isAppend = rowsRelative.isInsert();
		// 实际需要计算的列(如果环比值是插入模式,则需要调整列的对应index)
		Integer[] realRelativeCols = new Integer[rowsRelative.getRelativeColumns().length];
		int start = rowsRelative.getStartRow() == null ? 0 : rowsRelative.getStartRow();
		int end = rowsRelative.getEndRow() == null ? dataSize - 1 : rowsRelative.getEndRow();
		if (end < 0) {
			end = dataSize - 1 + end;
		}
		if (end > dataSize - 1) {
			end = dataSize - 1;
		}
		int groupSize = 1;
		//通过分组列来提取分组对比的数据长度(如不同品类不同月份的对比,品类数量即为groupSize)
		//如单品类则groupSize为1
		if (StringUtil.isNotBlank(rowsRelative.getGroupColumn())) {
			int groupColIndex;
			if (NumberUtil.isInteger(rowsRelative.getGroupColumn())) {
				groupColIndex = Integer.parseInt(rowsRelative.getGroupColumn());
			} else {
				groupColIndex = labelIndexMap.get(rowsRelative.getGroupColumn().toLowerCase());
			}
			if (groupColIndex >= 0 && groupColIndex < ((List) result.get(0)).size()) {
				HashSet map = new HashSet();
				List rowData;
				for (int i = start; i <= end; i++) {
					rowData = (List) result.get(i);
					map.add(rowData.get(groupColIndex));
				}
				groupSize = map.size();
			}
		}
		Integer[] relativeIndexs = rowsRelative.getRelativeIndexs();
		if (relativeIndexs == null || relativeIndexs.length == 0) {
			relativeIndexs = new Integer[groupSize];
			for (int i = 0; i < groupSize; i++) {
				relativeIndexs[i] = i;
			}
		} else {
			// 从低到高排列
			CollectionUtil.sortArray(relativeIndexs, false);
		}
		int addIndex = 0;
		String relativeCol;
		for (int i = 0; i < rowsRelative.getRelativeColumns().length; i++) {
			relativeCol = rowsRelative.getRelativeColumns()[i].toLowerCase();
			if (NumberUtil.isInteger(relativeCol)) {
				realRelativeCols[i] = Integer.parseInt(relativeCol) + addIndex;
			} else {
				realRelativeCols[i] = labelIndexMap.get(relativeCol) + addIndex;
			}
			if (isAppend) {
				addIndex++;
			}
		}
		String defaultValue = rowsRelative.getDefaultValue();
		// 如果是插入，直接先将集合数据环比列补充上去，从而实现所有环比计算都是update
		if (isAppend) {
			List rowList;
			for (int i = 0, n = result.size(); i < n; i++) {
				rowList = (List) result.get(i);
				for (int j = 0; j < realRelativeCols.length; j++) {
					rowList.add(realRelativeCols[j] + 1, defaultValue);
				}
			}
		}

		double divData = 0;
		double divedData = 0;
		int radixSize = rowsRelative.getRadixSize();
		boolean isIncrement = rowsRelative.isReduceOne();
		double multiply = rowsRelative.getMultiply();
		// 由下往上排序(第一组数据无需计算)
		int index;
		int colIndex;
		String format = rowsRelative.getFormat();
		BigDecimal value;
		int divIndex;
		int divedIndex;
		List divRowList;
		List divedRowList;
		// 逆序(从下到上)
		if (rowsRelative.isReverse()) {
			for (int i = end - groupSize; i > start; i = i - groupSize) {
				for (int j = 0; j < relativeIndexs.length; j++) {
					index = relativeIndexs[j];
					divIndex = i - groupSize + index + 1;
					divedIndex = i + index + 1;
					divRowList = (List) result.get(divIndex);
					divedRowList = (List) result.get(divedIndex);
					// 对环比列进行数据运算(环比结果列必须是在比较值列的后面第一列)
					for (int k = 0; k < realRelativeCols.length; k++) {
						colIndex = realRelativeCols[k];
						if (divRowList.get(colIndex) != null) {
							divData = Double.parseDouble(divRowList.get(colIndex).toString());
						}
						if (divedRowList.get(colIndex) != null) {
							divedData = Double.parseDouble(divedRowList.get(colIndex).toString());
						}
						if (divedData == 0) {
							divRowList.set(colIndex + 1, (divData == 0) ? 0 : defaultValue);
						} else {
							value = new BigDecimal(((divData - ((isIncrement) ? divedData : 0)) * multiply) / divedData)
									.setScale(radixSize, RoundingMode.FLOOR);
							if (format == null) {
								divRowList.set(colIndex + 1, value);
							} else {
								divRowList.set(colIndex + 1, NumberUtil.format(value, format));
							}
						}
					}
				}
			}
		} else {
			for (int i = start + groupSize; i < end; i = i + groupSize) {
				for (int j = 0; j < relativeIndexs.length; j++) {
					index = relativeIndexs[j];
					divIndex = i + index;
					divedIndex = i + index - groupSize;
					divRowList = (List) result.get(divIndex);
					divedRowList = (List) result.get(divedIndex);
					// 对环比列进行数据运算(环比结果列必须是在比较值列的后面第一列)
					for (int k = 0; k < realRelativeCols.length; k++) {
						colIndex = realRelativeCols[k];
						if (divRowList.get(colIndex) != null) {
							divData = Double.parseDouble(divRowList.get(colIndex).toString());
						}
						if (divedRowList.get(colIndex) != null) {
							divedData = Double.parseDouble(divedRowList.get(colIndex).toString());
						}
						if (divedData == 0) {
							divRowList.set(colIndex + 1, (divData == 0) ? 0 : "");
						} else {
							value = new BigDecimal(((divData - ((isIncrement) ? divedData : 0)) * multiply) / divedData)
									.setScale(radixSize, RoundingMode.FLOOR);
							if (format == null) {
								divRowList.set(colIndex + 1, value);
							} else {
								divRowList.set(colIndex + 1, NumberUtil.format(value, format));
							}
						}
					}
				}
			}
		}
	}
}
