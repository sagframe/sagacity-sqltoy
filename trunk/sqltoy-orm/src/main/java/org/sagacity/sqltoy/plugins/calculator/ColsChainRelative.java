package org.sagacity.sqltoy.plugins.calculator;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.HashMap;
import java.util.List;

import org.sagacity.sqltoy.config.model.ColsChainRelativeModel;
import org.sagacity.sqltoy.utils.CollectionUtil;
import org.sagacity.sqltoy.utils.NumberUtil;

/**
 * @project sqltoy-orm
 * @description 对集合数据进行列与列之间的比较(环比计算)
 * @author renfei.chen <a href="mailto:zhongxuchen@hotmail.com">联系作者</a>
 * @version id:ColsChainRelative.java,Revision:v1.0,Date:2020-3-25 上午10:08:15
 */
public class ColsChainRelative {
	public static void process(ColsChainRelativeModel relativeModel, HashMap<String, Integer> labelIndexMap,
			List result) {
		if (result == null || result.isEmpty())
			return;
		// |------- 1月-------|------- 2月 ------|------ 3月--------|
		// |交易笔 | 金额 | 收入 |交易笔 | 金额 | 收入 |交易笔 | 金额 | 收入 |
		int dataSize = result.size();
		int dataWidth = ((List) result.get(0)).size();
		boolean isAppend = relativeModel.isInsert();
		int skipSize = relativeModel.getSkipSize();
		int relativeSize = relativeModel.getRelativeIndexs().length;
		Integer[] relativeIndexs = relativeModel.getRelativeIndexs();
		if (relativeIndexs == null || relativeIndexs.length == 0) {
			relativeIndexs = new Integer[] { 0 };
		}
		CollectionUtil.sortArray(relativeIndexs, false);
		double divData;
		double divedData;
		int radixSize = relativeModel.getRadixSize();
		boolean isIncrement = relativeModel.isReduceOne();
		int divIndex;
		int divedIndex;
		double multiply = relativeModel.getMultiply();
		String format = relativeModel.getFormat();
		List rowList;
		int start = relativeModel.getStartColumn() == null ? 0 : relativeModel.getStartColumn();
		int end = relativeModel.getEndColumn() == null ? dataWidth - 1 : relativeModel.getEndColumn();
		if (end < 0) {
			end = dataWidth - 1 + end;
		}
		if (end > dataWidth - 1) {
			end = dataWidth - 1;
		}
		BigDecimal value;
		for (int i = start; i > end; i = i - skipSize) {
			for (int j = 0; j < dataSize; j++) {
				rowList = (List) result.get(j);
				for (int k = 0; k < relativeSize; k++) {
					divIndex = i - skipSize + relativeIndexs[k] + 1;
					divedIndex = i - 2 * skipSize + relativeIndexs[k] + 1;
					if (i - skipSize <= start) {
						if (isAppend) {
							rowList.add(divIndex + 1, 0);
						} else// (11-4+3+1)
						{
							rowList.set(divIndex + 1, 0);
						}
					} else {
						divData = 0;
						divedData = 0;
						if (rowList.get(divIndex) != null) {
							divData = Double.valueOf(rowList.get(divIndex).toString());
						}
						if (rowList.get(divedIndex) != null) {
							divedData = Double.valueOf(rowList.get(divedIndex).toString());
						}
						if (divedData == 0) {
							// 插入(8-3+2+2)
							if (isAppend) {
								rowList.add(divIndex + 1, (divData == 0) ? 0 : "");
							} else// (11-4+3+1)
							{
								rowList.set(divIndex + 1, (divData == 0) ? 0 : "");
							}
						} else {
							value = new BigDecimal(((divData - ((isIncrement) ? divedData : 0)) * multiply) / divedData)
									.setScale(radixSize, RoundingMode.FLOOR);
							if (isAppend) {
								rowList.add(divIndex + 1, format == null ? value : NumberUtil.format(value, format));
							} else {
								rowList.set(divIndex + 1, format == null ? value : NumberUtil.format(value, format));
							}
						}
					}
				}
			}
		}
	}
}
