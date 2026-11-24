package org.sagacity.sqltoy.oracle;

import java.lang.reflect.Array;
import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;

import org.sagacity.sqltoy.callback.TreeIdAndPidGet;
import org.sagacity.sqltoy.config.model.SummaryColMeta;
import org.sagacity.sqltoy.config.model.SummaryGroupMeta;
import org.sagacity.sqltoy.utils.BeanUtil;
import org.sagacity.sqltoy.utils.StringUtil;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * @project sagacity-sqltoy
 * @description 【行为基准，请勿修改】CollectionUtil 中树形排序、数据旋转、分组汇总在优化之前(提交 766bdb937)的实现，由脚本从当时的源码原样抽取冻结。
 *              只服务于差分对拍测试：优化后的实现必须在相同输入上得到完全相同的结果(含原地修改、异常)。
 *              手工改动此文件会让对拍失去意义；要调整基准，只能回到对应提交重新抽取。
 * @author zhongxuchen
 */
@SuppressWarnings({ "rawtypes", "unchecked" })
public class CollectionUtilLegacyOracle {
	private final static Logger logger = LoggerFactory.getLogger(CollectionUtilLegacyOracle.class);

	/**
	 * 转换数组类型数据为对象数组,解决原始类型无法强制转换的问题
	 * 
	 * @param obj 数组、集合或单个对象，支持int[]、long[]、double[]等原始类型数组
	 * @return 对应的Object[]数组，obj为null返回null，非数组对象返回单元素数组
	 */
	public static Object[] convertArray(Object obj) {
		if (obj == null) {
			return null;
		}
		if (obj instanceof Object[]) {
			return (Object[]) obj;
		}
		if (obj instanceof Collection) {
			return ((Collection) obj).toArray();
		}
		// 原始数组类型判断,原始类型直接(Object[])强制转换会发生错误
		if (obj instanceof int[]) {
			int[] tmp = (int[]) obj;
			Integer[] result = new Integer[tmp.length];
			for (int i = 0; i < tmp.length; i++) {
				result[i] = tmp[i];
			}
			return result;
		}
		if (obj instanceof short[]) {
			short[] tmp = (short[]) obj;
			Short[] result = new Short[tmp.length];
			for (int i = 0; i < tmp.length; i++) {
				result[i] = tmp[i];
			}
			return result;
		}
		if (obj instanceof long[]) {
			long[] tmp = (long[]) obj;
			Long[] result = new Long[tmp.length];
			for (int i = 0; i < tmp.length; i++) {
				result[i] = tmp[i];
			}
			return result;
		}
		if (obj instanceof float[]) {
			float[] tmp = (float[]) obj;
			Float[] result = new Float[tmp.length];
			for (int i = 0; i < tmp.length; i++) {
				result[i] = tmp[i];
			}
			return result;
		}
		if (obj instanceof double[]) {
			double[] tmp = (double[]) obj;
			Double[] result = new Double[tmp.length];
			for (int i = 0; i < tmp.length; i++) {
				result[i] = tmp[i];
			}
			return result;
		}
		if (obj instanceof boolean[]) {
			boolean[] tmp = (boolean[]) obj;
			Boolean[] result = new Boolean[tmp.length];
			for (int i = 0; i < tmp.length; i++) {
				result[i] = tmp[i];
			}
			return result;
		}
		if (obj instanceof char[]) {
			char[] tmp = (char[]) obj;
			String[] result = new String[tmp.length];
			for (int i = 0; i < tmp.length; i++) {
				result[i] = String.valueOf(tmp[i]);
			}
			return result;
		}
		if (obj instanceof byte[]) {
			byte[] tmp = (byte[]) obj;
			Byte[] result = new Byte[tmp.length];
			for (int i = 0; i < tmp.length; i++) {
				result[i] = tmp[i];
			}
			return result;
		}
		return new Object[] { obj };
	}

	/**
	 * 处理树形数据，将子节点紧靠父节点排序
	 * 
	 * @param treeList        树形数据集合，方法内部会被修改(移除已排序节点)
	 * @param treeIdAndPidGet 获取节点id和pid的回调接口，[0]为id、[1]为pid
	 * @param pids            根节点的父id(可多个，支持多根节点)，必须存在于treeList的pid中
	 * @return 子节点紧靠父节点排列后的新集合，不合规数据(无法挂到树上的节点)不会包含在结果中
	 */
	public static <T> List<T> sortTreeList(List<T> treeList, TreeIdAndPidGet<T> treeIdAndPidGet, Object... pids) {
		if (treeList == null || treeList.isEmpty() || pids == null || pids.length == 0) {
			return treeList;
		}
		int totalRecord = treeList.size();
		// 支持多根节点
		List<T> result = new ArrayList<T>();
		T row;
		Object pid;
		for (int i = 0; i < treeList.size(); i++) {
			row = treeList.get(i);
			pid = treeIdAndPidGet.getIdAndPid(row)[1];
			if (any(pid, pids)) {
				result.add(row);
				treeList.remove(i);
				i--;
			}
		}
		if (result.isEmpty()) {
			throw new IllegalArgumentException("the sorted tree collection does not contain the parent ids ["
					+ StringUtil.linkAry(",", false, pids) + "]!");
		}
		int beginIndex = 0;
		int addCount = 0;
		Object idValue, pidValue;
		while (treeList.size() != 0) {
			addCount = 0;
			// id
			idValue = treeIdAndPidGet.getIdAndPid(result.get(beginIndex))[0];
			for (int i = 0; i < treeList.size(); i++) {
				pidValue = treeIdAndPidGet.getIdAndPid(treeList.get(i))[1];
				if (idValue.equals(pidValue)) {
					result.add(beginIndex + addCount + 1, treeList.get(i));
					treeList.remove(i);
					addCount++;
					i--;
				}
			}
			// 下一个
			beginIndex++;
			// 防止因数据不符合规则造成的死循环
			if (beginIndex + 1 > result.size()) {
				break;
			}
		}
		if (result.size() != totalRecord) {
			logger.error("sortTreeList found some data that does not match the tree structure rules, please check!");
		}
		return result;
	}

	/**
	 * 数据进行旋转
	 * 
	 * @param data            源数据集合(二维List，每行为一行记录)
	 * @param categorys       旋转参照类别值，支持一维或多维(二维)形式
	 * @param categCol        类别列在参照数据中的序号
	 * @param pkColumn        主键列序号
	 * @param categCompareCol 数据中与类别值比对的列序号
	 * @param startCol        旋转起始列序号(含)
	 * @param endCol          旋转结束列序号(含)
	 * @param defaultValue    旋转后无对应数据的单元格默认值，null则留空
	 * @return 旋转(行转列)后的结果集合
	 */
	public static List pivotList(List data, List categorys, int categCol, int pkColumn, int categCompareCol,
			int startCol, int endCol, Object defaultValue) {
		return pivotList(data, categorys, new Integer[] { categCol }, new Integer[] { pkColumn },
				new Integer[] { categCompareCol }, startCol, endCol, defaultValue);
	}

	/**
	 * 集合进行数据旋转
	 * 
	 * @param data            源数据集合(二维List，每行为一行记录)
	 * @param categorys       旋转参照类别值，支持一维或多维(二维)形式
	 * @param categoryCol     类别列在参照数据中的序号数组，null时按参照数据自然序号
	 * @param pkColumns       主键列序号数组，用于判定是否同一行记录
	 * @param categCompareCol 数据中与类别值比对的列序号数组
	 * @param startCol        旋转起始列序号(含)
	 * @param endCol          旋转结束列序号(含)
	 * @param defaultValue    旋转后无对应数据的单元格默认值，null则留空
	 * @return 旋转(行转列)后的结果集合，data为null或空时原样返回
	 */
	public static List pivotList(List data, List categorys, Integer[] categoryCol, Integer[] pkColumns,
			Integer[] categCompareCol, int startCol, int endCol, Object defaultValue) {
		if (data == null || data.isEmpty()) {
			return data;
		}
		Integer[] categCol;
		if (categoryCol == null) {
			categCol = new Integer[categCompareCol.length];
			for (int i = 0; i < categCompareCol.length; i++) {
				categCol[i] = i;
			}
		} else {
			categCol = categoryCol;
		}
		boolean isTwoDimensionCategory = (categorys.get(0) instanceof Collection
				|| categorys.get(0).getClass().isArray());
		// 多维旋转参照数据行数跟参照列的数量要一致
		if (isTwoDimensionCategory
				&& (categCompareCol.length > categorys.size() || categCompareCol.length != categCol.length)) {
			throw new IllegalArgumentException(
					"the pivot reference rows must match the number of reference columns, expect categCol.length == categCompareCol.length!");
		}
		List result = new ArrayList();
		// 数据宽度
		int dataWidth = ((List) data.get(0)).size();
		int cateItemSize = isTwoDimensionCategory ? ((Collection) categorys.get(0)).size() : categorys.size();
		int rotateWith = endCol - startCol + 1;
		int lastRowWidth = dataWidth - categCompareCol.length + (cateItemSize - 1) * rotateWith;
		int rotateTotalCount = cateItemSize * rotateWith;
		int count = 0;
		boolean isRotaCol = false;
		Object[] rowData = null;
		int indexLength = pkColumns.length;
		boolean categoryColEqual = false;
		int categColSize = categCompareCol.length;
		// 主键列是否相等
		boolean pkColumnsEqual = false;
		List compareRow = null;
		List rowList;
		int rowSize = data.size();
		Object pkColValue;
		Object compareValue;
		for (int i = 0; i < rowSize; i++) {
			rowList = (List) data.get(i);
			pkColumnsEqual = true;
			if (i == 0) {
				pkColumnsEqual = false;
			} else {
				for (int k = 0; k < indexLength; k++) {
					pkColValue = rowList.get(pkColumns[k]);
					if (pkColValue == null) {
						pkColValue = "null";
					}
					compareValue = compareRow.get(pkColumns[k]);
					if (compareValue == null) {
						compareValue = "null";
					}
					pkColumnsEqual = pkColumnsEqual && BeanUtil.equalsIgnoreType(pkColValue, compareValue, false);
					if (!pkColumnsEqual) {
						break;
					}
				}
			}

			// 不同指标，构建新的行数据
			if (!pkColumnsEqual) {
				compareRow = rowList;
				if (i != 0) {
					result.add(rowData);
				}
				rowData = new Object[lastRowWidth];
				// 设置旋转部分的数据的默认值
				if (defaultValue != null) {
					for (int j = 0; j < rotateTotalCount; j++) {
						rowData[dataWidth - rotateWith - categCompareCol.length + j] = defaultValue;
					}
				}
				count = 0;
				for (int k = 0; k < dataWidth; k++) {
					isRotaCol = false;
					for (int m = 0; m < categColSize; m++) {
						if (k == categCompareCol[m]) {
							isRotaCol = true;
							break;
						}
					}
					if (k >= startCol && k <= endCol) {
						isRotaCol = true;
					}
					if (!isRotaCol) {
						rowData[count] = rowList.get(k);
						count++;
					}
				}
			}
			for (int j = 0; j < cateItemSize; j++) {
				// 单个数据
				if (categColSize == 1) {
					pkColValue = rowList.get(categCompareCol[0]);
					if (pkColValue == null) {
						pkColValue = "null";
					}
					compareValue = isTwoDimensionCategory ? ((List) categorys.get(categCol[0])).get(j)
							: categorys.get(j);
					if (compareValue == null) {
						compareValue = "null";
					}
					if (BeanUtil.equalsIgnoreType(pkColValue, compareValue, false)) {
						for (int t = 0; t < rotateWith; t++) {
							rowData[count + j * rotateWith + t] = rowList.get(startCol + t);
						}
					}
				} else {
					categoryColEqual = true;
					for (int k = 0; k < categColSize; k++) {
						pkColValue = rowList.get(categCompareCol[k]);
						if (pkColValue == null) {
							pkColValue = "null";
						}
						compareValue = isTwoDimensionCategory ? ((List) categorys.get(categCol[k])).get(j)
								: categorys.get(j);
						if (compareValue == null) {
							compareValue = "null";
						}
						categoryColEqual = categoryColEqual
								&& BeanUtil.equalsIgnoreType(pkColValue, compareValue, false);
					}
					if (categoryColEqual) {
						for (int t = 0; t < rotateWith; t++) {
							rowData[count + j * rotateWith + t] = rowList.get(startCol + t);
						}
					}
				}
			}
			// 最后一行
			if (i == rowSize - 1) {
				result.add(rowData);
			}
		}
		innerArrayToList(result);
		return result;
	}

	/**
	 * 将内部的数组转换为list
	 * 
	 * @param source
	 */
	public static void innerArrayToList(List source) {
		if (source == null || source.isEmpty()) {
			return;
		}
		if (source.get(0).getClass().isArray()) {
			Object[] rowAry;
			for (int i = 0, n = source.size(); i < n; i++) {
				List rowList = new ArrayList();
				rowAry = convertArray(source.get(i));
				for (int j = 0, k = rowAry.length; j < k; j++) {
					rowList.add(rowAry[j]);
				}
				source.remove(i);
				source.add(i, rowList);
			}
		}
	}

	/**
	 * 分组汇总计算
	 * 
	 * @param sumData
	 * @param groupMetas
	 * @param isReverse
	 * @param linkSign
	 * @param skipSingleRowSummary 分组数据是单行是否忽略汇总求平均计算
	 */
	public static void groupSummary(List sumData, SummaryGroupMeta[] groupMetas, boolean isReverse, String linkSign,
			boolean skipSingleRowSummary) {
		// 分组计算，数据集合少于2条没有必要计算
		if (sumData == null || sumData.size() < 2 || groupMetas == null || groupMetas.length == 0) {
			return;
		}
		// 内部子分组自动加上父级分组的列,规避重复
		Set<Integer> groupCols = new LinkedHashSet<Integer>();
		for (SummaryGroupMeta groupMeta : groupMetas) {
			if (groupMeta.getGroupCols() != null && groupMeta.getGroupCols().length > 0) {
				for (int index : groupMeta.getGroupCols()) {
					groupCols.add(index);
				}
			}
			// 子分组合并父分组的列
			if (groupCols.size() > 0) {
				Integer[] cols = groupCols.toArray(new Integer[0]);
				groupMeta.setGroupCols(cols);
			}
			// 转小写
			if (groupMeta.getSumSite() != null) {
				groupMeta.setSumSite(groupMeta.getSumSite().toLowerCase(Locale.ROOT));
			} else {
				groupMeta.setSumSite("");
			}
		}

		// 进行数据逆转，然后统一按照顺序计算，结果再进行逆向处理
		if (isReverse) {
			Collections.reverse(sumData);
			for (SummaryGroupMeta groupMeta : groupMetas) {
				if (groupMeta.getSumSite() != null) {
					// sum和ave 是两行上下模式，逆序
					if ("top".equals(groupMeta.getSumSite())) {
						groupMeta.setSumSite("bottom");
					} else if ("bottom".equals(groupMeta.getSumSite())) {
						groupMeta.setSumSite("top");
					}
				}
			}
		}
		summaryList(sumData, groupMetas, StringUtil.isBlank(linkSign) ? " / " : linkSign, skipSingleRowSummary);
		// 将结果反转
		if (isReverse) {
			Collections.reverse(sumData);
		}
	}

	/**
	 * 进行汇总计算
	 * 
	 * @param dataSet
	 * @param groupMetas
	 * @param linkSign
	 * @param skipSingleRowSummary
	 */
	private static void summaryList(List<List> dataSet, SummaryGroupMeta[] groupMetas, String linkSign,
			boolean skipSingleRowSummary) {
		List<List> iterList = new ArrayList();
		for (List item : dataSet) {
			iterList.add(item);
		}
		int groupSize = groupMetas.length;
		SummaryGroupMeta groupMeta;
		List row;
		List preRow = iterList.get(0);
		int dataSize = iterList.size();
		int addRows = 0;
		List sumRows;
		for (int i = 0; i < dataSize; i++) {
			row = iterList.get(i);
			// 从最明细分组开始(从里而外)
			for (int j = groupSize; j > 0; j--) {
				groupMeta = groupMetas[j - 1];
				// 判断分组字段值是否相同
				if (isEquals(row, preRow, i, groupMeta.getGroupCols())) {
					// 汇总计算
					calculateTotal(row, groupMeta);
				} else {
					// 参与计算的行>1 或 单行也计算
					if (groupMeta.getSummaryCols()[0].getRowCount() > 1 || !skipSingleRowSummary) {
						sumRows = createSummaryRow(preRow, groupMeta, linkSign);
						// 插入汇总行(可能存在sum、ave 两行数据)
						dataSet.addAll(i + addRows, sumRows);
						// 累加增加的记录行数
						addRows = addRows + sumRows.size();
					}
					// 重置分组的列计算的汇总相关的值(sum、rowCount、nullRowCount)
					for (SummaryColMeta colMeta : groupMeta.getSummaryCols()) {
						colMeta.setNullCount(0);
						colMeta.setSumValue(BigDecimal.ZERO);
						colMeta.setRowCount(0);
					}
					calculateTotal(row, groupMeta);
				}
				// 最后一行
				if (i == dataSize - 1) {
					if (groupMeta.getSummaryCols()[0].getRowCount() > 1 || !skipSingleRowSummary) {
						// 全局汇总的置顶
						if (groupMeta.isGlobalReverse()) {
							dataSet.addAll(0, createSummaryRow(row, groupMeta, linkSign));
						} else {
							dataSet.addAll(createSummaryRow(row, groupMeta, linkSign));
						}
					}
				}
			}
			preRow = row;
		}
	}

	/**
	 * 比较分组字段的值是否相等
	 * 
	 * @param currentRow 当前行数据
	 * @param preRow     上一行数据
	 * @param index      当前行序号，第一行(0)视为相等
	 * @param columns    参与比较的分组列序号数组，null或空表示全局分组(视为相等)
	 * @return 所有分组列的值均相等返回true，任一不等返回false
	 */
	private static boolean isEquals(List currentRow, List preRow, int index, Integer[] columns) {
		// 全局分组、第一行数据
		if (columns == null || columns.length == 0 || index == 0) {
			return true;
		}
		int cellIndex;
		for (int i = 0; i < columns.length; i++) {
			cellIndex = columns[i];
			if (!BeanUtil.equals(currentRow.get(cellIndex), preRow.get(cellIndex))) {
				return false;
			}
		}
		return true;
	}

	/**
	 * 计算汇总
	 * 
	 * @param row       当前行数据
	 * @param groupMeta 分组汇总元数据，累加其汇总列的合计值并更新行数、空值计数
	 */
	private static void calculateTotal(List row, SummaryGroupMeta groupMeta) {
		Object cellValue;
		for (SummaryColMeta colMeta : groupMeta.getSummaryCols()) {
			cellValue = row.get(colMeta.getColIndex());
			colMeta.setRowCount(colMeta.getRowCount() + 1);
			// 空值
			if (cellValue == null || "".equals(cellValue.toString().trim())) {
				colMeta.setNullCount(colMeta.getNullCount() + 1);
			} else {
				colMeta.setSumValue(colMeta.getSumValue().add(new BigDecimal(cellValue.toString().replace(",", ""))));
			}
		}
	}

	/**
	 * 构造分组汇总行数据
	 * 
	 * @param row       参与汇总的最后一行数据，用于补齐分组列前面的数据
	 * @param groupMeta 分组汇总元数据(汇总/平均标题、小数位、汇总位置等)
	 * @param linkSign  汇总与平均共一行时的标题连接符号
	 * @return 汇总行数据集合，汇总和平均分两行时返回两行，共一行时返回一行
	 */
	private static List createSummaryRow(List row, SummaryGroupMeta groupMeta, String linkSign) {
		List<List> result = new ArrayList();
		// 汇总类别
		int rowSize = groupMeta.getRowSize();
		int labelIndex = groupMeta.getLabelIndex();
		// 构造sum、ave数据行
		for (int i = 0; i < rowSize; i++) {
			List rowData = new ArrayList();
			// 填充空值构造等长集合
			for (int j = 0; j < row.size(); j++) {
				rowData.add(null);
			}
			// 补充分组列前面的上层分组的数据
			if (labelIndex > 0) {
				for (int k = 0; k < labelIndex; k++) {
					rowData.set(k, row.get(k));
				}
			}
			result.add(rowData);
		}
		List sumList = null;
		List aveList = null;
		// 平均和汇总分两行展示
		if (rowSize == 2) {
			if ("top".equals(groupMeta.getSumSite())) {
				sumList = result.get(0);
				aveList = result.get(1);
			} else {
				sumList = result.get(1);
				aveList = result.get(0);
			}
			// 设置标题
			sumList.set(labelIndex, groupMeta.getSumTitle());
			aveList.set(labelIndex, groupMeta.getAverageTitle());
		} else {
			// 单行，平均和汇总共一行数据
			sumList = result.get(0);
			if (groupMeta.getSummaryType() == 3) {
				if ("left".equals(groupMeta.getSumSite())) {
					sumList.set(labelIndex, groupMeta.getSumTitle() + linkSign + groupMeta.getAverageTitle());
				} else {
					sumList.set(labelIndex, groupMeta.getAverageTitle() + linkSign + groupMeta.getSumTitle());
				}
			} else if (groupMeta.getSummaryType() == 1) {
				sumList.set(labelIndex, groupMeta.getSumTitle());
			} else if (groupMeta.getSummaryType() == 2) {
				sumList.set(labelIndex, groupMeta.getAverageTitle());
			}
		}
		// 汇总值、平均值
		BigDecimal sumValue;
		BigDecimal aveValue;
		String sumStr = "--";
		String aveStr = "--";
		for (SummaryColMeta colMeta : groupMeta.getSummaryCols()) {
			sumValue = colMeta.getSumValue();
			// 计算平均值
			if (sumValue.compareTo(BigDecimal.ZERO) == 0) {
				aveValue = BigDecimal.ZERO;
			} else {
				if (colMeta.isAveSkipNull()) {
					aveValue = sumValue.divide(BigDecimal.valueOf(colMeta.getRowCount() - colMeta.getNullCount()),
							colMeta.getRadixSize(), colMeta.getRoundingMode());
				} else {
					aveValue = sumValue.divide(BigDecimal.valueOf(colMeta.getRowCount()), colMeta.getRadixSize(),
							colMeta.getRoundingMode());
				}
			}
			// 汇总和平均为2行记录
			if (rowSize == 2) {
				// 汇总
				if (colMeta.getSummaryType() == 1 || colMeta.getSummaryType() == 3) {
					sumList.set(colMeta.getColIndex(), sumValue);
				}
				// 求平均
				if (colMeta.getSummaryType() == 2 || colMeta.getSummaryType() == 3) {
					aveList.set(colMeta.getColIndex(), aveValue);
				}
			} else {
				// 单行数据同时存在平均和汇总
				if (groupMeta.getSummaryType() == 3) {
					if (colMeta.getSummaryType() == 1) {
						sumStr = sumValue.toPlainString();
						aveStr = "--";
					} else if (colMeta.getSummaryType() == 2) {
						aveStr = aveValue.toPlainString();
						sumStr = "--";
					} else if (colMeta.getSummaryType() == 3) {
						sumStr = sumValue.toPlainString();
						aveStr = aveValue.toPlainString();
					}
					if ("left".equals(groupMeta.getSumSite())) {
						// {总计 / 平均 } 或 { -- / 平均 } 风格
						sumList.set(colMeta.getColIndex(), sumStr + linkSign + aveStr);
					} else {
						sumList.set(colMeta.getColIndex(), aveStr + linkSign + sumStr);
					}
				} else {
					if (colMeta.getSummaryType() == 1) {
						sumList.set(colMeta.getColIndex(), sumValue);
					} else if (colMeta.getSummaryType() == 2) {
						sumList.set(colMeta.getColIndex(), aveValue);
					}
				}
			}
		}
		return result;
	}

	public static boolean any(Object value, Object... compareAry) {
		return any(value, false, compareAry);
	}

	/**
	 * 判断字符串或对象数据是否在给定的数组中
	 * 
	 * @param value      待比较的对象，null仅与数组中的null元素匹配
	 * @param ignoreCase true按字符串忽略大小写比较
	 * @param compareAry 待比较的对象数组，null或空返回false
	 * @return 数组中存在相等元素返回true，否则返回false
	 */
	public static boolean any(Object value, boolean ignoreCase, Object... compareAry) {
		if (compareAry == null || compareAry.length == 0) {
			return false;
		}
		String valueStr = (value == null) ? "" : value.toString();
		for (Object s : compareAry) {
			// 值为null时仅null元素匹配;比对到null元素时跳过继续后续元素,不能提前短路结束整个判定
			if (value == null) {
				if (s == null) {
					return true;
				}
			} else if (s != null) {
				if (value.equals(s)) {
					return true;
				}
				if (ignoreCase && valueStr.equalsIgnoreCase(s.toString())) {
					return true;
				}
			}
		}
		return false;
	}

}
