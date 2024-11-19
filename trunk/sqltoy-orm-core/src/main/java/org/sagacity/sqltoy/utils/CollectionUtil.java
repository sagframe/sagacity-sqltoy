package org.sagacity.sqltoy.utils;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.Comparator;
import java.util.Date;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Iterator;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

import org.sagacity.sqltoy.callback.TreeIdAndPidGet;
import org.sagacity.sqltoy.config.model.SummaryColMeta;
import org.sagacity.sqltoy.config.model.SummaryGroupMeta;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * @project sagacity-sqltoy
 * @description 数组集合的公用方法
 * @author zhongxuchen
 * @version v1.0,Date:2008-10-22
 * @modify Date:2011-8-11 {修复了pivotList设置旋转数据的初始值错误}
 */
@SuppressWarnings({ "rawtypes", "unchecked" })
public class CollectionUtil {
	/**
	 * 定义日志
	 */
	private final static Logger logger = LoggerFactory.getLogger(CollectionUtil.class);

	public static final String ILLEGAL_NUM_REGEX = "%|‰|\\$|¥";

	// 静态方法避免实例化和继承
	private CollectionUtil() {

	}

	/**
	 * @todo 转换数组类型数据为对象数组,解决原始类型无法强制转换的问题
	 * @param obj
	 * @return
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
	 * @todo 数组转换为List集合,此转换只适用于一维和二维数组
	 * @param arySource Object
	 * @return List
	 */
	public static List arrayToDeepList(Object arySource) {
		if (null == arySource) {
			logger.error("arrayToDeepList:the array is Null");
			return null;
		}
		List resultList = new ArrayList();
		if (arySource instanceof Object[][]) {
			Object[][] aryObject = (Object[][]) arySource;
			if (null != aryObject && 0 < aryObject.length) {
				int rowLength;
				for (int i = 0, n = aryObject.length; i < n; i++) {
					List tmpList = new ArrayList();
					rowLength = aryObject[i].length;
					for (int j = 0; j < rowLength; j++) {
						tmpList.add(aryObject[i][j]);
					}
					resultList.add(tmpList);
				}
			}
		} else {
			if (arySource.getClass().isArray()) {
				Object[] aryObject = convertArray(arySource);
				if (null != aryObject && 0 < aryObject.length) {
					for (int i = 0, n = aryObject.length; i < n; i++) {
						resultList.add(aryObject[i]);
					}
				}
			} else {
				logger.error("error define the Array! please sure the array is one or two dimension!");
			}
		}
		return resultList;
	}

	/**
	 * @todo 此转换只适用于一维数组(建议使用Arrays.asList())
	 * @param arySource Object
	 * @return List
	 */
	public static List arrayToList(Object arySource) {
		if (null == arySource) {
			logger.error("arrayToList:the Ary Source is Null");
			return null;
		}
		if (arySource instanceof List) {
			return (List) arySource;
		}
		List resultList = new ArrayList();
		if (arySource.getClass().isArray()) {
			Object[] aryObject = convertArray(arySource);
			if (null != aryObject && 0 < aryObject.length) {
				for (int i = 0, n = aryObject.length; i < n; i++) {
					resultList.add(aryObject[i]);
				}
			}
		} else {
			logger.warn("arySource is not Array! it type is :" + arySource.getClass());
			resultList.add(arySource);
		}
		return resultList;
	}

	/**
	 * @todo 对简单对象进行排序(此方法不建议使用，请用Collections中的排序)
	 * @param aryData
	 * @param descend
	 */
	public static void sortArray(Object[] aryData, boolean descend) {
		if (aryData != null && aryData.length > 1) {
			int length = aryData.length;
			Object iData;
			Object jData;
			// 1:string,2:数字;3:日期
			Integer dataType = 1;
			if (aryData[0] instanceof java.util.Date) {
				dataType = 3;
			} else if (aryData[0] instanceof java.lang.Number) {
				dataType = 2;
			}
			String str1, str2;
			boolean lessThen = false;
			for (int i = 0; i < length - 1; i++) {
				for (int j = i + 1; j < length; j++) {
					iData = aryData[i];
					jData = aryData[j];
					if (dataType == 2) {
						lessThen = ((Number) iData).doubleValue() < ((Number) jData).doubleValue();
					} else if (dataType == 3) {
						lessThen = ((Date) iData).before((Date) jData);
					} else {
						str1 = iData.toString();
						str2 = jData.toString();
						if (str1.length() < str2.length()) {
							lessThen = true;
						} else if (str1.length() > str2.length()) {
							lessThen = false;
						} else {
							lessThen = str1.compareTo(str2) < 0;
						}
					}
					// 小于
					if ((descend && lessThen) || (!descend && !lessThen)) {
						aryData[i] = jData;
						aryData[j] = iData;
					}
				}
			}
		}
	}

	/**
	 * @todo 处理树形数据，将子节点紧靠父节点排序
	 * @param treeList
	 * @param treeIdAndPidGet
	 * @param pids
	 * @return
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
			throw new IllegalArgumentException("排序树形数据集合中没有对应的父ids:" + StringUtil.linkAry(",", false, pids));
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
			logger.error("sortTreeList操作发现部分数据不符合树形结构规则,请检查!");
		}
		return result;
	}

	/**
	 * @todo 剔除对象数组中的部分数据,简单采用List remove方式实现
	 * @param sourceAry
	 * @param begin
	 * @param length
	 * @return
	 */
	public static Object[] subtractArray(Object[] sourceAry, int begin, int length) {
		if (sourceAry == null || sourceAry.length == 0) {
			return null;
		}
		if (begin + length > sourceAry.length || length == 0) {
			return sourceAry;
		}
		Object[] distinctAry = new Object[sourceAry.length - length];
		if (begin == 0) {
			System.arraycopy(sourceAry, length, distinctAry, 0, sourceAry.length - length);
		} else {
			System.arraycopy(sourceAry, 0, distinctAry, 0, begin);
			System.arraycopy(sourceAry, begin + length, distinctAry, begin, sourceAry.length - length - begin);
		}
		return distinctAry;
	}

	/**
	 * @todo 二维list转换为数组对象
	 * @param source
	 * @return
	 */
	public static Object[][] twoDimenlistToArray(Collection source) {
		if (source == null || source.isEmpty()) {
			return null;
		}
		Object[][] result = new Object[source.size()][];
		int index = 0;
		Object obj;
		for (Iterator iter = source.iterator(); iter.hasNext();) {
			obj = iter.next();
			if (obj instanceof Collection) {
				result[index] = ((Collection) obj).toArray();
			} else if (obj.getClass().isArray()) {
				result[index] = convertArray(obj);
			} else if (obj instanceof Map) {
				result[index] = ((Map) obj).values().toArray();
			}
			index++;
		}
		return result;
	}

	/**
	 * @todo 判断list的维度
	 * @param obj
	 * @return
	 */
	public static int judgeObjectDimen(Object obj) {
		int result = 0;
		if (obj == null) {
			return -1;
		}
		Object firstCellValue;
		if (obj instanceof Collection || obj.getClass().isArray() || obj instanceof Map) {
			result = 1;
			if (obj instanceof Collection) {
				Collection tmp = (Collection) obj;
				if (tmp.isEmpty()) {
					return result;
				}
				firstCellValue = ((List) obj).get(0);
				if (firstCellValue != null && (firstCellValue instanceof Collection
						|| firstCellValue.getClass().isArray() || firstCellValue instanceof Map)) {
					result = 2;
				}
			} else if (obj.getClass().isArray()) {
				Object[] tmp = convertArray(obj);
				if (tmp.length == 0) {
					return result;
				}
				firstCellValue = tmp[0];
				if (firstCellValue != null && (firstCellValue instanceof Collection
						|| firstCellValue.getClass().isArray() || firstCellValue instanceof Map)) {
					result = 2;
				}
			} else if (obj instanceof Map) {
				Map tmp = (Map) obj;
				if (tmp.isEmpty()) {
					return result;
				}
				firstCellValue = tmp.values().iterator().next();
				if (firstCellValue != null && (firstCellValue instanceof Collection
						|| firstCellValue.getClass().isArray() || firstCellValue instanceof Map)) {
					result = 2;
				}
			}
		}
		return result;
	}

	/**
	 * @todo 数据进行旋转
	 * @param data
	 * @param categorys
	 * @param categCol
	 * @param pkColumn
	 * @param categCompareCol
	 * @param startCol
	 * @param endCol
	 * @param defaultValue
	 * @return
	 */
	public static List pivotList(List data, List categorys, int categCol, int pkColumn, int categCompareCol,
			int startCol, int endCol, Object defaultValue) {
		return pivotList(data, categorys, new Integer[] { categCol }, new Integer[] { pkColumn },
				new Integer[] { categCompareCol }, startCol, endCol, defaultValue);
	}

	/**
	 * @todo 集合进行数据旋转
	 * @param data
	 * @param categorys
	 * @param categoryCol
	 * @param pkColumns
	 * @param categCompareCol
	 * @param startCol
	 * @param endCol
	 * @param defaultValue
	 * @return
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
			throw new IllegalArgumentException("多维旋转参照数据行数跟参照列的数量要一致,categCol.length == categCompareCol.length!");
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
	 * @todo 将集合数据转成hashMap
	 * @param data
	 * @param keyProp
	 * @param valueProp
	 * @param keyToStr  将key统一转成字符串
	 * @return
	 */
	public static HashMap hashList(Object data, Object keyProp, Object valueProp, boolean keyToStr) {
		return hashList(data, keyProp, valueProp, keyToStr, false);
	}

	/**
	 * @todo 将集合数据转成hashMap
	 * @param data
	 * @param keyProp
	 * @param valueProp
	 * @param keyToStr     将key统一转成字符串
	 * @param isLinkedHash 返回的是否为LinkedHashMap
	 * @return
	 */
	public static HashMap hashList(Object data, Object keyProp, Object valueProp, boolean keyToStr,
			boolean isLinkedHash) {
		int dimen = judgeObjectDimen(data);
		boolean isBean = NumberUtil.isInteger(keyProp.toString()) ? false : true;
		int keyIndex = -1;
		int valueIndex = -1;
		String valueProperty = null;
		String keyProperty = "";
		if (!isBean) {
			keyIndex = Integer.parseInt(keyProp.toString());
			valueIndex = (valueProp == null) ? -1 : Integer.parseInt(valueProp.toString());
		} else {
			keyProperty = keyProp.toString();
			valueProperty = (String) valueProp;
		}
		HashMap result = isLinkedHash ? new LinkedHashMap() : new HashMap();
		try {
			List<List> hashValues = null;
			String[] hashProperties = null;
			if (isBean) {
				hashProperties = (valueProperty == null) ? new String[] { keyProperty }
						: new String[] { keyProperty, valueProperty };
			}
			switch (dimen) {
			case -1:
			case 0: {
				break;
			}
			// 一维
			case 1: {
				if (data.getClass().isArray()) {
					Object[] hashObj = convertArray(data);
					List rowData;
					if (isBean) {
						hashValues = BeanUtil.reflectBeansToList(arrayToList(hashObj), hashProperties);
					}
					for (int i = 0, n = hashObj.length; i < n; i++) {
						if (isBean) {
							result.put(keyToStr ? hashValues.get(i).get(0).toString() : hashValues.get(i).get(0),
									valueProperty == null ? hashObj[i] : hashValues.get(i).get(1));
						} else {
							rowData = (List) hashObj[i];
							result.put(keyToStr ? rowData.get(keyIndex).toString() : rowData.get(keyIndex),
									valueIndex == -1 ? hashObj[i] : rowData.get(valueIndex));
						}
					}
				} else if (data instanceof List) {
					List hashObj = (List) data;
					Object[] rowData;
					if (isBean) {
						hashValues = BeanUtil.reflectBeansToList(hashObj, hashProperties);
					}
					for (int i = 0, n = hashObj.size(); i < n; i++) {
						if (isBean) {
							result.put(keyToStr ? hashValues.get(i).get(0).toString() : hashValues.get(i).get(0),
									valueProperty == null ? hashObj.get(i) : hashValues.get(i).get(1));
						} else {
							rowData = convertArray(hashObj.get(i));
							result.put(keyToStr ? rowData[keyIndex].toString() : rowData[keyIndex],
									valueIndex == -1 ? hashObj.get(i) : rowData[valueIndex]);
						}
					}
				}
				break;
			} // 2维
			case 2: {
				if (data.getClass().isArray()) {
					Object[] hashObj = convertArray(data);
					Object[] rowData;
					for (int i = 0, n = hashObj.length; i < n; i++) {
						rowData = convertArray(hashObj[i]);
						result.put(keyToStr ? rowData[keyIndex].toString() : rowData[keyIndex],
								valueIndex == -1 ? hashObj[i] : rowData[valueIndex]);
					}
				} else if (data instanceof List) {
					List hashObj = (List) data;
					List rowData;
					for (int i = 0, n = hashObj.size(); i < n; i++) {
						rowData = (List) hashObj.get(i);
						result.put(keyToStr ? rowData.get(keyIndex).toString() : rowData.get(keyIndex),
								valueIndex == -1 ? hashObj.get(i) : rowData.get(valueIndex));
					}
				}
				break;
			}
			}
		} catch (Exception e) {
			e.printStackTrace();
		}
		return result;
	}

	/**
	 * @todo 将内部的数组转换为list
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
	 * @todo 将内部list转换为数组
	 * @param source
	 * @return
	 */
	public static List innerListToArray(List source) {
		if (source == null || source.isEmpty()) {
			return source;
		}
		List result = new ArrayList();
		Object sonList;
		for (int i = 0, n = source.size(); i < n; i++) {
			sonList = source.get(i);
			if (null == sonList) {
				result.add(null);
			} else if (sonList instanceof Collection) {
				result.add(((Collection) sonList).toArray());
			} else if (sonList.getClass().isArray()) {
				result.add(sonList);
			} else {
				logger.error("数据类型必须为Collection");
				break;
			}
		}
		return result;
	}

	/**
	 * @TODO 分组汇总计算
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
				Integer[] cols = new Integer[groupCols.size()];
				groupCols.toArray(cols);
				groupMeta.setGroupCols(cols);
			}
			// 转小写
			if (groupMeta.getSumSite() != null) {
				groupMeta.setSumSite(groupMeta.getSumSite().toLowerCase());
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
	 * @TODO 进行汇总计算
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
	 * @TODO 比较分组字段的值是否相等
	 * @param currentRow
	 * @param preRow
	 * @param index
	 * @param columns
	 * @return
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
	 * @TODO 计算汇总
	 * @param row
	 * @param groupMeta
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
	 * @TODO 构造分组汇总行数据
	 * @param row
	 * @param groupMeta
	 * @param linkSign
	 * @return
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

	/**
	 * @todo <b>列转行</b>
	 * @param data
	 * @param colIndexs 保留哪些列进行旋转(其它的列数据忽略)
	 * @return
	 */
	public static List convertColToRow(List data, Integer[] colIndexs) {
		if (data == null || data.isEmpty()) {
			return data;
		}
		boolean innerAry = data.get(0).getClass().isArray();
		int newResultRowCnt = 0;
		if (colIndexs == null) {
			newResultRowCnt = innerAry ? convertArray(data.get(0)).length : ((List) data.get(0)).size();
		} else {
			newResultRowCnt = colIndexs.length;
		}
		// 构造结果集
		Object[][] resultAry = new Object[newResultRowCnt][data.size()];
		Object[] rowAry = null;
		List rowList = null;
		for (int i = 0, n = data.size(); i < n; i++) {
			if (innerAry) {
				rowAry = convertArray(data.get(i));
			} else {
				rowList = (List) data.get(i);
			}
			if (colIndexs != null) {
				for (int j = 0, k = colIndexs.length; j < k; j++) {
					resultAry[j][i] = innerAry ? rowAry[colIndexs[j]] : rowList.get(colIndexs[j]);
				}
			} else {
				for (int j = 0; j < newResultRowCnt; j++) {
					resultAry[j][i] = innerAry ? rowAry[j] : rowList.get(j);
				}
			}
		}
		return arrayToDeepList(resultAry);
	}

	/**
	 * @todo 判断字符串是否在给定的数组中
	 * @param compareStr
	 * @param compareAry
	 * @param ignoreCase
	 * @return
	 */
	public static boolean any(String compareStr, String[] compareAry, boolean ignoreCase) {
		if (compareStr == null || (compareAry == null || compareAry.length == 0)) {
			return false;
		}
		for (String s : compareAry) {
			if (ignoreCase) {
				if (compareStr.equalsIgnoreCase(s)) {
					return true;
				}
			} else if (compareStr.equals(s)) {
				return true;
			}
		}
		return false;
	}

	/**
	 * @todo 字符串数组按照类型转换
	 * @param values
	 * @param argType
	 * @return
	 */
	public static Object[] toArray(String[] values, String argType) {
		if (values == null) {
			return null;
		}
		String type = argType.toLowerCase();
		Object[] result = null;
		if ("string".equals(type)) {
			result = new String[values.length];
		} else if ("int".equals(type) || "integer".equals(type)) {
			result = new Integer[values.length];
		} else if ("long".equals(type)) {
			result = new Long[values.length];
		} else if ("date".equals(type)) {
			result = new Date[values.length];
		} else if ("boolean".equals(type)) {
			result = new Boolean[values.length];
		} else if ("double".equals(type)) {
			result = new Double[values.length];
		} else if ("float".equals(type)) {
			result = new Float[values.length];
		} else if ("short".equals(type)) {
			result = new Short[values.length];
		} else if ("java.lang.class".equals(type) || "class".equals(type)) {
			result = new Class[values.length];
		}
		for (int i = 0; i < result.length; i++) {
			if (values[i] != null) {
				if ("string".equals(type)) {
					result[i] = values[i];
				} else if ("int".equals(type) || "integer".equals(type)) {
					result[i] = Integer.valueOf(values[i]);
				} else if ("long".equals(type)) {
					result[i] = Long.valueOf(values[i]);
				} else if ("date".equals(type)) {
					result[i] = DateUtil.parseString(values[i]);
				} else if ("boolean".equals(type)) {
					result[i] = Boolean.parseBoolean(values[i]);
				} else if ("double".equals(type)) {
					result[i] = Double.valueOf(values[i]);
				} else if ("float".equals(type)) {
					result[i] = Float.valueOf(values[i]);
				} else if ("short".equals(type)) {
					result[i] = Short.valueOf(values[i]);
				} else if ("java.lang.class".equals(type) || "class".equals(type)) {
					try {
						result[i] = Class.forName(values[i]);
					} catch (ClassNotFoundException e) {
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
	 * @todo 判断字符串或对象数据是否在给定的数组中
	 * @param value
	 * @param ignoreCase
	 * @param compareAry
	 * @return
	 */
	public static boolean any(Object value, boolean ignoreCase, Object... compareAry) {
		if (value == null || (compareAry == null || compareAry.length == 0)) {
			return false;
		}
		String valueStr = value.toString();
		for (Object s : compareAry) {
			if (s == null) {
				return false;
			}
			if (value.equals(s)) {
				return true;
			}
			if (ignoreCase && valueStr.equalsIgnoreCase(s.toString())) {
				return true;
			}
		}
		return false;
	}

	/**
	 * @TODO 清除集合中的null值
	 * @param dataSet
	 */
	public static void removeNull(List dataSet) {
		if (dataSet != null && !dataSet.isEmpty()) {
			Iterator iter = dataSet.iterator();
			while (iter.hasNext()) {
				if (null == iter.next()) {
					iter.remove();
				}
			}
		}
	}

	/**
	 * @TODO 分组排序
	 * @param dataSet
	 * @param groupIndexes
	 * @param sortIndex
	 * @param desc
	 */
	public static void groupSort(List<List> dataSet, Integer[] groupIndexes, int sortIndex, boolean desc) {
		if (dataSet == null || dataSet.size() < 2) {
			return;
		}
		int length = dataSet.size();
		int dataType = getSortDataType(dataSet, sortIndex);
		// 1:string,2:数字;3:日期
		int start = 0;
		int end;
		int groupSize = groupIndexes.length;
		Object[] compareValue = new Object[groupSize];
		Object[] tempObj = new Object[groupSize];
		List row;
		boolean isEqual = false;
		for (int i = 0; i < length; i++) {
			row = dataSet.get(i);
			isEqual = true;
			for (int j = 0; j < groupSize; j++) {
				tempObj[j] = row.get(groupIndexes[j]);
				if (i == 0) {
					compareValue[j] = tempObj[j];
				}
				isEqual = isEqual && (tempObj[j].equals(compareValue[j]));
			}
			if (!isEqual) {
				end = i - 1;
				sortList(dataSet, sortIndex, dataType, start, end, !desc);
				start = i;
				for (int j = 0; j < groupSize; j++) {
					compareValue[j] = tempObj[j];
				}
			}
			if (i == length - 1) {
				sortList(dataSet, sortIndex, dataType, start, i, !desc);
			}
		}
	}

	/**
	 * @TODO 提取排序列的具体数据类型
	 * @param dataSet
	 * @param sortIndex
	 * @return
	 */
	public static int getSortDataType(List<List> dataSet, int sortIndex) {
		int dataType = 1;
		Object dataValue;
		for (List item : dataSet) {
			dataValue = item.get(sortIndex);
			if (dataValue != null) {
				if (dataValue instanceof String) {
					dataType = 1;
				} else if (dataValue instanceof Number) {
					dataType = 2;
				} else if (dataValue instanceof Date) {
					dataType = 3;
				} else if (dataValue instanceof LocalDate) {
					dataType = 4;
				} else if (dataValue instanceof LocalDateTime) {
					dataType = 5;
				} else if (dataValue instanceof LocalTime) {
					dataType = 6;
				}
				break;
			}
		}
		return dataType;
	}

	/**
	 * @TODO List集合排序
	 * @param sortList
	 * @param orderCol
	 * @param dataType
	 * @param start
	 * @param end
	 * @param ascend
	 * @return
	 */
	public static List sortList(List<List> sortList, int orderCol, int dataType, int start, int end, boolean ascend) {
		if (start == end) {
			return sortList;
		}
		List subList = sortList.subList(start, end + 1);
		Collections.sort(subList, new Comparator<List>() {
			@Override
			public int compare(List o1, List o2) {
				return compareValue(o1.get(orderCol), o2.get(orderCol), dataType, ascend);
			}
		});
		return sortList;
	}

	/**
	 * @TODO 数据大小比较，用于排序
	 * @param iData
	 * @param jData
	 * @param dataType
	 * @param ascend
	 * @return
	 */
	private static int compareValue(Object iData, Object jData, int dataType, boolean ascend) {
		// 1:string,2:数字;3:日期
		boolean lessThen = false;
		boolean isEqual = false;
		String str1, str2;
		if (iData == null && jData == null) {
			isEqual = true;
		}
		if (iData != null && jData == null) {
			lessThen = false;
		} else if (iData == null && jData != null) {
			lessThen = true;
		} else {
			if (iData.equals(jData)) {
				isEqual = true;
			} else {
				if (dataType == 2) {
					lessThen = ((Number) iData).doubleValue() < ((Number) jData).doubleValue();
				} else if (dataType == 3) {
					lessThen = ((Date) iData).before((Date) jData);
				} else if (dataType == 4) {
					lessThen = ((LocalDate) iData).compareTo((LocalDate) jData) < 0;
				} else if (dataType == 5) {
					lessThen = ((LocalDateTime) iData).compareTo((LocalDateTime) jData) < 0;
				} else if (dataType == 6) {
					lessThen = ((LocalTime) iData).compareTo((LocalTime) jData) < 0;
				} else {
					str1 = iData.toString();
					str2 = jData.toString();
					if (str1.length() < str2.length()) {
						lessThen = true;
					} else if (str1.length() > str2.length()) {
						lessThen = false;
					} else {
						lessThen = str1.compareTo(str2) < 0;
					}
				}
			}
		}
		if (isEqual) {
			return 0;
		}
		if (ascend) {
			return lessThen ? -1 : 1;
		} else {
			return lessThen ? 1 : -1;
		}
	}

	/**
	 * @TODO 分组计算
	 * @param dataSet
	 * @param groupIndexes
	 * @param calcuateIndex
	 * @param isSum
	 */
	public static void groupCalculate(List<List> dataSet, Integer[] groupIndexes, int calcuateIndex, boolean isSum) {
		int groupSize = groupIndexes.length;
		int length = dataSet.size();
		Object[] compareValue = new Object[groupSize];
		Object[] tempObj = new Object[groupSize];
		List row;
		boolean isEqual = false;
		int start = 0;
		int end = 0;
		BigDecimal calculateValue = new BigDecimal(0);
		Object tmpCellValue;
		BigDecimal cellValue;
		for (int i = 0; i < length; i++) {
			row = dataSet.get(i);
			tmpCellValue = row.get(calcuateIndex);
			cellValue = toDecimal(tmpCellValue);
			isEqual = true;
			// 判断分组值是否相同，不相同表示下一个分组
			for (int j = 0; j < groupSize; j++) {
				tempObj[j] = row.get(groupIndexes[j]);
				if (i == 0) {
					compareValue[j] = row.get(groupIndexes[j]);
				}
				isEqual = isEqual && (tempObj[j].equals(compareValue[j]));
			}
			if (isEqual) {
				calculateValue = calculateValue.add(cellValue);
			} else {
				end = i - 1;
				// 求平均值
				if (!isSum && end > start) {
					calculateValue = calculateValue.divide(BigDecimal.valueOf(end - start), 4, RoundingMode.HALF_DOWN);
				}
				// 将平均值插入到分组记录的最后一列，用于排序
				for (int k = start; k <= end; k++) {
					dataSet.get(k).add(calculateValue);
				}
				for (int j = 0; j < groupSize; j++) {
					compareValue[j] = tempObj[j];
				}
				calculateValue = new BigDecimal(0).add(cellValue);
				start = i;
			}
			// 最后一行
			if (i == length - 1) {
				end = i;
				// 求平均值
				if (!isSum && end > start) {
					calculateValue = calculateValue.divide(BigDecimal.valueOf(end - start), 4, RoundingMode.HALF_DOWN);
				}
				for (int k = start; k <= end; k++) {
					dataSet.get(k).add(calculateValue);
				}
			}
		}
	}

	private static BigDecimal toDecimal(Object tmpCellValue) {
		if (tmpCellValue == null) {
			return BigDecimal.ZERO;
		} else if (tmpCellValue instanceof BigDecimal) {
			return (BigDecimal) tmpCellValue;
		} else if (tmpCellValue instanceof Number) {
			return new BigDecimal(tmpCellValue.toString());
		} else {
			return new BigDecimal(tmpCellValue.toString().replaceAll(",", "").replaceFirst(ILLEGAL_NUM_REGEX, ""));
		}
	}

	/**
	 * @todo 去除in中的重复数据
	 * @param inArgsList
	 * @return
	 */
	public static List<Object[]> clearRepeat(List<Object[]> inArgsList) {
		if (inArgsList == null || inArgsList.isEmpty() || inArgsList.get(0).length < 2) {
			return inArgsList;
		}
		int size = inArgsList.size();
		List<List> middleList = new ArrayList<>();
		List<Object[]> result = new ArrayList<>();
		for (int i = 0; i < size; i++) {
			middleList.add(new ArrayList<>());
		}
		HashSet<String> notRepeatSet = new HashSet<>();
		String key;
		int loopSize = inArgsList.get(0).length;
		for (int i = 0; i < loopSize; i++) {
			key = "";
			for (int j = 0; j < size; j++) {
				key = key + ",{" + inArgsList.get(j)[i] + "}";
			}
			if (!notRepeatSet.contains(key)) {
				notRepeatSet.add(key);
				for (int j = 0; j < size; j++) {
					middleList.get(j).add(inArgsList.get(j)[i]);
				}
			}
		}
		for (int i = 0; i < size; i++) {
			result.add(middleList.get(i).toArray());
		}
		return result;
	}
}
