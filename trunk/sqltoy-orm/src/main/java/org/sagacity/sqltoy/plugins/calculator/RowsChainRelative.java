package org.sagacity.sqltoy.plugins.calculator;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.HashMap;
import java.util.List;

import org.sagacity.sqltoy.config.model.RowsChainRelativeModel;
import org.sagacity.sqltoy.utils.CollectionUtil;
import org.sagacity.sqltoy.utils.NumberUtil;

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
	public static void process(RowsChainRelativeModel rowsRelative, HashMap<String, Integer> labelIndexMap,
			List result) {
		if (result == null || result.size() < 2)
			return;
		int dataSize = result.size();
		// 5,3-2+0+1
		boolean isAppend = rowsRelative.isInsert();
		// 实际需要计算的列(如果环比值是插入模式,则需要调整列的对应index)
		Integer[] realRelativeCols = new Integer[rowsRelative.getRelativeColumns().length];
		Integer[] relativeIndexs = rowsRelative.getRelativeIndexs();
		CollectionUtil.sortArray(relativeIndexs, false);
		if (relativeIndexs == null || relativeIndexs.length == 0)
			relativeIndexs = new Integer[] { 0 };
		int max = NumberUtil.getMaxValue(relativeIndexs);
		int groupSize = rowsRelative.getGroupSize();
		if (groupSize < 1) {
			groupSize = 1;
		}
		int skipSize = groupSize - max - 1;
		int addIndex = 0;
		String relativeCol;
		for (int i = 0; i < rowsRelative.getRelativeColumns().length; i++) {
			relativeCol = rowsRelative.getRelativeColumns()[i].toLowerCase();
			realRelativeCols[i] = labelIndexMap.get(relativeCol) + addIndex;
			if (isAppend) {
				addIndex++;
			}
		}
		// 如果是插入，直接先将集合数据环比列补充上去，从而实现所有环比计算都是update
		if (isAppend) {
			List rowList;
			for (int i = 0, n = result.size(); i < n; i++) {
				rowList = (List) result.get(i);
				for (int j = 0; j < realRelativeCols.length; j++) {
					rowList.add(realRelativeCols[j] + 1, null);
				}
			}
		}

		double divData = 0;
		double divedData = 0;
		int radixSize = rowsRelative.getRadixSize();
		boolean isIncrement = rowsRelative.isReduceOne();
		double multiply = rowsRelative.getMultiply();

		List divRowList;
		List divedRowList;
		// 由下往上排序(第一组数据无需计算)
		int index;
		int colIndex;
		int start = rowsRelative.getStartRow() == null ? 0 : rowsRelative.getStartRow();
		int end = rowsRelative.getEndRow() == null ? dataSize - 1 : rowsRelative.getEndRow();
		if (end < 0) {
			end = dataSize - 1 + end;
		}
		if (end > dataSize - 1) {
			end = dataSize - 1;
		}
		String format = rowsRelative.getFormat();
		BigDecimal value;
		for (int i = start + skipSize; i < end; i = i + skipSize) {
			for (int j = 0; j < rowsRelative.getRelativeIndexs().length; j++) {
				index = rowsRelative.getRelativeIndexs()[j];
				divRowList = (List) result.get(i + index);
				divedRowList = (List) result.get(i + index - skipSize);
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
