package org.sagacity.sqltoy.plugins.calculator;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.List;

import org.sagacity.sqltoy.config.model.ColsChainRelativeModel;
import org.sagacity.sqltoy.config.model.LabelIndexModel;
import org.sagacity.sqltoy.utils.CollectionUtil;
import org.sagacity.sqltoy.utils.ExpressionUtil;
import org.sagacity.sqltoy.utils.NumberUtil;
import org.sagacity.sqltoy.utils.StringUtil;

/**
 * @project sqltoy-orm
 * @description 对集合数据进行列与列之间的比较(环比计算)
 * @author zhongxuchen
 * @version v1.0,Date:2020-3-25
 */
@SuppressWarnings({ "rawtypes", "unchecked" })
public class ColsChainRelative {
	public static void process(ColsChainRelativeModel relativeModel, LabelIndexModel labelIndexMap, List result) {
		if (result == null || result.isEmpty()) {
			return;
		}
		// |------- 1月-------|------- 2月 ------|------ 3月--------|
		// |交易笔数 | 金额 | 收入 |交易笔数 | 金额 | 收入 |交易笔数 | 金额 | 收入 |
		int dataSize = result.size();
		int dataWidth = ((List) result.get(0)).size();
		boolean isAppend = relativeModel.isInsert();
		int groupSize = relativeModel.getGroupSize();
		if (groupSize < 1) {
			groupSize = 1;
		}
		Integer[] relativeIndexs = relativeModel.getRelativeIndexs();
		if (relativeIndexs == null || relativeIndexs.length == 0) {
			relativeIndexs = new Integer[groupSize];
			for (int i = 0; i < groupSize; i++) {
				relativeIndexs[i] = i;
			}
		}
		// 从大到小排序
		CollectionUtil.sortArray(relativeIndexs, true);

		int relativeSize = relativeIndexs.length;
		BigDecimal divData;
		BigDecimal divedData;
		int radixSize = relativeModel.getRadixSize();
		boolean isIncrement = relativeModel.isReduceOne();
		int divIndex;
		int divedIndex;
		BigDecimal multiply = BigDecimal.valueOf(relativeModel.getMultiply());
		// 输出格式
		String format = relativeModel.getFormat();
		List rowList;
		// 开始列
		int start = 0;
		if (StringUtil.isBlank(relativeModel.getStartColumn())) {
			start = 0;
		} else if (NumberUtil.isInteger(relativeModel.getStartColumn())) {
			start = Integer.parseInt(relativeModel.getStartColumn());
		} else if (labelIndexMap.containsKey(relativeModel.getStartColumn())) {
			start = labelIndexMap.get(relativeModel.getStartColumn());
		}

		// 截止列(支持:${dataWidth}-x 模式)
		String endCol = relativeModel.getEndColumn();
		int end;
		if (StringUtil.isBlank(endCol)) {
			end = dataWidth - 1;
		} else if (NumberUtil.isInteger(endCol)) {
			end = Integer.parseInt(endCol);
		} else if (labelIndexMap.containsKey(endCol)) {
			end = labelIndexMap.get(endCol);
		} else {
			endCol = endCol.replaceAll("result\\.width\\(\\)", Integer.toString(dataWidth))
					.replaceAll("(?i)\\$\\{dataWidth\\}", Integer.toString(dataWidth));
			if (NumberUtil.isInteger(endCol)) {
				end = Integer.parseInt(endCol);
			} else {
				end = new BigDecimal(ExpressionUtil.calculate(endCol).toString()).intValue();
			}
		}
		// 负数等同于${dataWidth}-x 模式
		if (end < 0) {
			end = dataWidth - 1 + end;
		}
		if (end > dataWidth - 1) {
			end = dataWidth - 1;
		}
		BigDecimal value;
		String defaultValue = relativeModel.getDefaultValue();
		boolean divIsZero;
		for (int i = end; i > start; i = i - groupSize) {
			for (int j = 0; j < dataSize; j++) {
				rowList = (List) result.get(j);
				for (int k = 0; k < relativeSize; k++) {
					divIndex = i - groupSize + relativeIndexs[k] + 1;
					divedIndex = i - 2 * groupSize + relativeIndexs[k] + 1;
					if (i - groupSize <= start) {
						if (isAppend) {
							rowList.add(divIndex + 1, defaultValue);
						} else// (11-4+3+1)
						{
							rowList.set(divIndex + 1, defaultValue);
						}
					} else {
						divData = BigDecimal.ZERO;
						divedData = BigDecimal.ZERO;
						if (rowList.get(divIndex) != null) {
							divData = new BigDecimal(rowList.get(divIndex).toString());
						}
						if (rowList.get(divedIndex) != null) {
							divedData = new BigDecimal(rowList.get(divedIndex).toString());
						}
						if (divedData.equals(BigDecimal.ZERO)) {
							divIsZero = divData.equals(BigDecimal.ZERO);
							// 插入(8-3+2+2)
							if (isAppend) {
								if (format == null) {
									rowList.add(divIndex + 1, divIsZero ? BigDecimal.ZERO : defaultValue);
								} else {
									rowList.add(divIndex + 1,
											divIsZero ? NumberUtil.format(BigDecimal.ZERO, format) : defaultValue);
								}
							} else// (11-4+3+1)
							{
								if (format == null) {
									rowList.set(divIndex + 1, divIsZero ? BigDecimal.ZERO : defaultValue);
								} else {
									rowList.set(divIndex + 1,
											divIsZero ? NumberUtil.format(BigDecimal.ZERO, format) : defaultValue);
								}
							}
						} else {
							if (isIncrement) {
								value = divData.subtract(divedData).multiply(multiply).divide(divedData, radixSize,
										RoundingMode.FLOOR);
							} else {
								value = divData.multiply(multiply).divide(divedData, radixSize, RoundingMode.FLOOR);
							}
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
