/**
 * @Copyright 2008 版权归陈仁飞，不要肆意侵权抄袭，如引用请注明出处保留作者信息。
 */
package org.sagacity.sqltoy.utils;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Date;
import java.util.HashMap;
import java.util.Iterator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * @project sagacity-sqltoy4.0
 * @description 数组集合的公用方法
 * @author zhongxuchen <a href="mailto:zhongxuchen@hotmail.com">联系作者</a>
 * @version id:CollectionUtil.java,Revision:v1.0,Date:2008-10-22 上午10:57:00
 * @modify Date:2011-8-11 {修复了pivotList设置旋转数据的初始值错误}
 */
@SuppressWarnings({ "rawtypes", "unchecked" })
public class CollectionUtil {
	/**
	 * 定义日志
	 */
	private final static Logger logger = LoggerFactory.getLogger(CollectionUtil.class);

	/**
	 * @todo 转换数组类型数据为对象数组,解决原始类型无法强制转换的问题
	 * @param obj
	 * @return
	 */
	public static Object[] convertArray(Object obj) {
		if (obj == null)
			return null;
		if (obj instanceof Object[])
			return (Object[]) obj;
		if (obj instanceof Collection)
			return ((Collection) obj).toArray();
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
			logger.error("arrayToDeepList:the Ary Source is Null");
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
	 * 此方法不建议使用，请用Collections中的排序
	 * 
	 * @todo 对简单对象进行排序
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
						lessThen = (iData.toString()).compareTo(jData.toString()) < 0;
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
	 * @todo 剔除对象数组中的部分数据,简单采用List remove方式实现
	 * @param targetAry
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

		if (obj instanceof Collection || obj.getClass().isArray() || obj instanceof Map) {
			result = 1;
			if (obj instanceof Collection) {
				Collection tmp = (Collection) obj;
				if (tmp.isEmpty())
					return result;
				if (((List) obj).get(0) != null && ((List) obj).get(0) instanceof List) {
					result = 2;
				}
			} else if (obj.getClass().isArray()) {
				Object[] tmp = convertArray(obj);
				if (tmp.length == 0)
					return result;
				if (tmp[0] != null && tmp[0].getClass().isArray()) {
					result = 2;
				}
			} else if (obj instanceof Map) {
				Map tmp = (Map) obj;
				if (tmp.isEmpty())
					return result;
				Object setItem = tmp.values().iterator().next();
				if (setItem.getClass().isArray() || setItem instanceof Collection || setItem instanceof Map) {
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
	 * @throws Exception
	 */
	public static List pivotList(List data, List categorys, int categCol, int pkColumn, int categCompareCol,
			int startCol, int endCol, Object defaultValue) throws Exception {
		return pivotList(data, categorys, new Integer[] { categCol }, new Integer[] { pkColumn },
				new Integer[] { categCompareCol }, startCol, endCol, defaultValue);
	}

	/**
	 * @todo 集合进行数据旋转
	 * @Modification $Date:2011-8-11 修改了设置初始值的bug
	 * @param data
	 * @param categorys
	 * @param categCol
	 * @param pkColumns
	 * @param categCompareCol
	 * @param startCol
	 * @param endCol
	 * @param defaultValue
	 * @return
	 * @throws Exception
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
					if (!pkColumnsEqual)
						break;
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
				break;
			case 0:
				break;
			// 一维
			case 1: {
				if (data.getClass().isArray()) {
					Object[] hashObj = convertArray(data);
					List rowData;
					if (isBean)
						hashValues = BeanUtil.reflectBeansToList(arrayToList(hashObj), hashProperties);
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
				System.err.println("数据类型必须为Collection");
				break;
			}
		}
		return result;
	}

	public static void groupSummary(List sumData, Object[][] groupIndexs, Integer[] sumColumns, int totalColumn,
			String totalLabel, boolean hasAverage, String averageLabel, String sumRecordSite) {
		groupSummary(sumData, groupIndexs, sumColumns, totalColumn, totalLabel, hasAverage, averageLabel, 2,
				sumRecordSite == null ? "bottom" : sumRecordSite, false);
	}

	public static void groupSummary(List sumData, Object[][] groupIndexs, Integer[] sumColumns, int totalColumn,
			String totalLabel, boolean hasAverage, String averageLabel, String sumRecordSite,
			boolean globalSumReverse) {
		groupSummary(sumData, groupIndexs, sumColumns, totalColumn, totalLabel, hasAverage, averageLabel, 2,
				sumRecordSite == null ? "bottom" : sumRecordSite, globalSumReverse);
	}

	public static void groupReverseSummary(List sumData, Object[][] groupIndexs, Integer[] sumColumns, int totalColumn,
			String totalLabel, boolean hasAverage, String averageLabel, String sumRecordSite) {
		groupReverseSummary(sumData, groupIndexs, sumColumns, totalColumn, totalLabel, hasAverage, averageLabel, 2,
				sumRecordSite == null ? "top" : sumRecordSite);
	}

	/**
	 * @todo 分组合计
	 * @param sumData
	 * @param groupIndexs   {汇总列，汇总标题，平均标题，汇总相对平均的位置(left/right/top/bottom)}
	 * @param sumColumns
	 * @param globalSumSite 存在全局汇总时，总计标题存放的列
	 * @param totalLabel
	 * @param hasAverage
	 * @param averageLabel
	 * @param averageFormat
	 * @param sumRecordSite
	 */
	public static void groupSummary(List sumData, Object[][] groupIndexs, Integer[] sumColumns, int globalSumSite,
			String totalLabel, boolean hasAverage, String averageLabel, int radixSize, String sumRecordSite,
			boolean totalSumReverse) {
		boolean hasTotalSum = false;
		if (globalSumSite >= 0 || hasAverage) {
			hasTotalSum = true;
		}
		List rowList;
		int groupTotal = (groupIndexs == null) ? 0 : groupIndexs.length;
		int columns = ((List) sumData.get(0)).size();
		int dataSize = sumData.size();
		// 总数
		Object[] totalSum = new Object[columns];
		// 记录各个分组的汇总小计
		HashMap groupSumMap = new HashMap();
		HashMap groupPreIndexMap = new HashMap();
		for (int i = 0; i < groupTotal; i++) {
			groupSumMap.put(groupIndexs[i][0], new Object[columns]);
			groupPreIndexMap.put(groupIndexs[i][0], 0);
		}
		groupSumMap.put(-1, totalSum);
		groupPreIndexMap.put(-1, 0);
		boolean isEqual = true;
		int preIndex = 0;
		int dymSize = 0;
		boolean isLast = false;
		// 分组对比值
		HashMap preGroupCompareIndexs = new HashMap();
		HashMap nowGroupCompareIndexs = new HashMap();
		String preGroupIndexs = "";
		String nowGroupIndexs = "";
		for (int i = 0; i < sumData.size(); i++) {
			nowGroupIndexs = "";
			dymSize = sumData.size();
			isLast = (i == dymSize - 1);
			isEqual = false;
			rowList = (List) sumData.get(i);
			// 构造同质比较条件
			// 第一个
			if (i == 0) {
				for (int k = 0; k < groupTotal; k++) {
					preGroupIndexs = preGroupIndexs + rowList.get((Integer) groupIndexs[k][0]).toString();
					preGroupCompareIndexs.put((Integer) groupIndexs[k][0], preGroupIndexs);
				}
			}

			for (int k = 0; k < groupTotal; k++) {
				nowGroupIndexs += rowList.get((Integer) groupIndexs[k][0]).toString();
				nowGroupCompareIndexs.put((Integer) groupIndexs[k][0], nowGroupIndexs);
			}

			for (int j = groupTotal - 1; j >= 0; j--) {
				// 不相等
				if (!BeanUtil.equals(nowGroupCompareIndexs.get((Integer) groupIndexs[j][0]),
						preGroupCompareIndexs.get((Integer) groupIndexs[j][0]))) {
					// 最后一条
					if (j == groupTotal - 1) {
						isEqual = true;
					}
					sumData.addAll(i,
							createSummaryRow((Object[]) groupSumMap.get((Integer) groupIndexs[j][0]),
									(List) sumData.get(preIndex), (Integer) groupIndexs[j][0], groupIndexs[j],
									(Integer) groupPreIndexMap.get(groupIndexs[j][0]), radixSize));
					groupPreIndexMap.put(groupIndexs[j][0], 0);
					preGroupCompareIndexs.put((Integer) groupIndexs[j][0],
							nowGroupCompareIndexs.get((Integer) groupIndexs[j][0]));
					// 汇总之后重新置值
					groupSumMap.put((Integer) groupIndexs[j][0], new Object[columns]);
					// 同时存在汇总和平均
					if (groupIndexs[j][1] != null && groupIndexs[j][2] != null
							&& (groupIndexs[j][3].equals("top") || groupIndexs[j][3].equals("bottom"))) {
						i = i + 2;
					}
					// (必须要有一个汇总或平均)
					else {
						i++;
					}
				} else {
					break;
				}
			}
			// 汇总计算（含求平均）
			calculateTotal(groupSumMap, rowList, sumColumns, radixSize);
			for (int m = 0; m < groupTotal; m++) {
				groupPreIndexMap.put(groupIndexs[m][0], (Integer) groupPreIndexMap.get(groupIndexs[m][0]) + 1);
			}
			// 相等
			if (isEqual) {
				preIndex = i;
			}
			// 最后一条记录
			if (isLast) {
				for (int j = groupTotal - 1; j >= 0; j--) {
					sumData.addAll(createSummaryRow((Object[]) groupSumMap.get((Integer) groupIndexs[j][0]),
							(List) sumData.get(preIndex), (Integer) groupIndexs[j][0], groupIndexs[j],
							(Integer) groupPreIndexMap.get(groupIndexs[j][0]), radixSize));
				}
				break;
			}
		}
		// 存在总的求和或平均
		if (hasTotalSum) {
			if (totalSumReverse) {
				sumData.addAll(0, createSummaryRow((Object[]) groupSumMap.get(-1), (List) sumData.get(preIndex),
						globalSumSite,
						new Object[] { -1, totalLabel, averageLabel, sumRecordSite == null ? "bottom" : sumRecordSite },
						dataSize, radixSize));
			} else {
				sumData.addAll(createSummaryRow((Object[]) groupSumMap.get(-1), (List) sumData.get(preIndex),
						globalSumSite,
						new Object[] { -1, totalLabel, averageLabel, sumRecordSite == null ? "bottom" : sumRecordSite },
						dataSize, radixSize));
			}
		}
	}

	/**
	 * @todo 逆向分组合计
	 * @param sumData
	 * @param groupIndexs
	 * @param sumColumns
	 * @param totalColumnIndex
	 * @param totalTitle
	 * @param hasAverage
	 * @param averageTitle
	 * @param radixSize        小数位长度
	 * @param firstSummary
	 */
	public static void groupReverseSummary(List sumData, Object[][] groupIndexs, Integer[] sumColumns,
			int globalSumSite, String totalLabel, boolean hasAverage, String averageLabel, int radixSize,
			String sumRecordSite) {
		boolean hasTotalSum = false;
		if (globalSumSite >= 0 || hasAverage) {
			hasTotalSum = true;
		}
		int groupTotal = (groupIndexs == null) ? 0 : groupIndexs.length;
		int columns = ((List) sumData.get(0)).size();
		if (sumRecordSite == null) {
			sumRecordSite = "bottom";
		}
		// 总数
		Object[] totalSum = new Object[columns];
		int dataSize = sumData.size();
		// 记录各个分组的汇总小计
		HashMap groupSumMap = new HashMap();
		HashMap groupPreIndexMap = new HashMap();
		for (int i = 0; i < groupTotal; i++) {
			groupSumMap.put(groupIndexs[i][0], new Object[columns]);
			groupPreIndexMap.put(groupIndexs[i][0], dataSize - 1);
		}
		groupSumMap.put(-1, totalSum);
		groupPreIndexMap.put(-1, dataSize - 1);
		String preGroupIndexs = "";
		String nowGroupIndexs = "";
		boolean isEqual = true;
		int preIndex = dataSize - 1;
		List rowList;
		// 分组对比值
		HashMap preGroupCompareIndexs = new HashMap();
		HashMap nowGroupCompareIndexs = new HashMap();
		for (int i = dataSize - 1; i >= 0; i--) {
			nowGroupIndexs = "";
			isEqual = false;
			rowList = (List) sumData.get(i);
			// 制造同质比较条件
			// 第一个
			if (i == dataSize - 1) {
				for (int k = 0; k < groupTotal; k++) {
					preGroupIndexs = preGroupIndexs + rowList.get((Integer) groupIndexs[k][0]).toString();
					preGroupCompareIndexs.put((Integer) groupIndexs[k][0], preGroupIndexs);
				}
			}
			for (int k = 0; k < groupTotal; k++) {
				nowGroupIndexs += rowList.get((Integer) groupIndexs[k][0]).toString();
				nowGroupCompareIndexs.put((Integer) groupIndexs[k][0], nowGroupIndexs);
			}

			for (int j = groupTotal - 1; j >= 0; j--) {
				// 不相等
				if (!BeanUtil.equals(nowGroupCompareIndexs.get((Integer) groupIndexs[j][0]),
						preGroupCompareIndexs.get((Integer) groupIndexs[j][0]))) {
					if (j == groupTotal - 1)
						isEqual = true;
					sumData.addAll(i + 1,
							createSummaryRow((Object[]) groupSumMap.get((Integer) groupIndexs[j][0]),
									(List) sumData.get(preIndex), (Integer) groupIndexs[j][0], groupIndexs[j],
									(Integer) groupPreIndexMap.get(groupIndexs[j][0]) - i, radixSize));
					preGroupCompareIndexs.put((Integer) groupIndexs[j][0],
							nowGroupCompareIndexs.get((Integer) groupIndexs[j][0]));
					groupPreIndexMap.put(groupIndexs[j][0], i);
					// 汇总之后重新置值
					groupSumMap.put((Integer) groupIndexs[j][0], new Object[columns]);
				} else {
					break;
				}
			}
			calculateTotal(groupSumMap, rowList, sumColumns, radixSize);
			if (isEqual) {
				preIndex = i;
			}
			if (i == 0) {
				for (int j = groupTotal - 1; j >= 0; j--) {
					sumData.addAll(0,
							createSummaryRow((Object[]) groupSumMap.get((Integer) groupIndexs[j][0]),
									(List) sumData.get(preIndex), (Integer) groupIndexs[j][0], groupIndexs[j],
									(Integer) groupPreIndexMap.get(groupIndexs[j][0]) + 1, radixSize));
				}
			}
		}

		// 存在总的求和或平均
		if (hasTotalSum) {
			sumData.addAll(0, createSummaryRow((Object[]) groupSumMap.get(-1), (List) sumData.get(preIndex),
					globalSumSite, new Object[] { -1, totalLabel, averageLabel, sumRecordSite }, dataSize, radixSize));
		}
	}

	/**
	 * @todo 创建汇总行
	 * @param rowSummaryData
	 * @param rowList
	 * @param groupIndex
	 * @param title
	 * @param rowCount
	 * @param radixSize      小数位长度
	 * @return
	 */
	private static List createSummaryRow(Object[] rowSummaryData, List rowList, int groupIndex, Object[] title,
			int rowCount, int radixSize) {
		List result = new ArrayList();
		List summary = null;
		List average = null;
		int titleIndex = groupIndex;
		if (title.length == 5 && title[4] != null) {
			titleIndex = (Integer) title[4];
		}
		// 汇总
		if (title[1] != null || (title[3].equals("left") || title[3].equals("right"))) {
			summary = new ArrayList();
			// 汇总数据加入新的数据行中
			for (int i = 0, n = rowSummaryData.length; i < n; i++) {
				summary.add(i, rowSummaryData[i]);
			}
			// 设置分组列前面的数据
			for (int i = 0; i <= titleIndex; i++) {
				summary.set(i, rowList.get(i));
			}

			// 设置标题
			if (title[1] != null && !title[1].toString().trim().equals("")) {
				summary.set(titleIndex, title[1]);
			}
		}
		// 平均
		if (title[2] != null || (title[3].equals("left") || title[3].equals("right"))) {
			average = new ArrayList();
			// 平均数据加入新的数据行中
			Double averageValue;
			for (int i = 0, n = rowSummaryData.length; i < n; i++) {
				if (rowSummaryData[i] == null) {
					average.add(i, null);
				} else {
					averageValue = Double.valueOf(rowSummaryData[i].toString().replace(",", "")) / rowCount;
					if (radixSize >= 0) {
						average.add(i, BigDecimal.valueOf(averageValue).setScale(radixSize, RoundingMode.FLOOR));
					} else {
						average.add(i, BigDecimal.valueOf(averageValue));
					}
				}
			}
			// 设置分组列前面的数据
			for (int i = 0; i <= titleIndex; i++) {
				average.set(i, rowList.get(i));
			}

			// 设置标题
			if (title[2] != null && !title[2].toString().trim().equals("")) {
				average.set(titleIndex, title[2]);
			}
		}
		// 汇总或求平均
		if (summary == null || average == null) {
			if (summary != null) {
				result.add(summary);
			}
			if (average != null) {
				result.add(average);
			}
		} else {
			if (title[3].equals("top") || title[3].equals("bottom")) {
				result.add(summary);
				// 平均数据优先显示
				if (title[3].equals("bottom")) {
					result.add(0, average);
				} else {
					result.add(average);
				}
			} else {
				// 汇总数据是否左边显示
				boolean isLeft = title[3].equals("left");
				String sumCellValue;
				String averageValue;
				String linkSign = " / ";
				if (title.length == 6 && title[5] != null) {
					linkSign = title[5].toString();
				}
				summary.set(titleIndex, isLeft ? (summary.get(titleIndex) + linkSign + average.get(titleIndex))
						: (average.get(titleIndex) + linkSign + summary.get(titleIndex)));
				for (int i = 0, n = rowSummaryData.length; i < n; i++) {
					if (rowSummaryData[i] != null) {
						sumCellValue = (summary.get(i) == null) ? ""
								: ((BigDecimal) summary.get(i)).stripTrailingZeros().toPlainString();
						averageValue = (average.get(i) == null) ? ""
								: ((BigDecimal) average.get(i)).stripTrailingZeros().toPlainString();
						summary.set(i, isLeft ? (sumCellValue + linkSign + averageValue)
								: (averageValue + linkSign + sumCellValue));
					}
				}
				result.add(summary);
			}
		}
		return result;
	}

	/**
	 * @todo 汇总计算
	 * @param groupSumMap
	 * @param rowList
	 * @param summaryColumns
	 * @param radixSize
	 */
	private static void calculateTotal(HashMap groupSumMap, List rowList, Integer[] summaryColumns, int radixSize) {
		Object[] groupSums;
		int size = summaryColumns.length;
		Object cellValue;
		Object sumCellValue;
		int columnIndex;
		// new BigDecimal()
		for (Iterator iter = groupSumMap.values().iterator(); iter.hasNext();) {
			groupSums = (Object[]) iter.next();
			for (int i = 0; i < size; i++) {
				columnIndex = summaryColumns[i];
				sumCellValue = groupSums[columnIndex];
				cellValue = rowList.get(columnIndex);
				if (radixSize >= 0) {
					groupSums[columnIndex] = new BigDecimal(
							StringUtil.isBlank(sumCellValue) ? "0" : sumCellValue.toString().replace(",", ""))
									.add(new BigDecimal(StringUtil.isBlank(cellValue) ? "0"
											: cellValue.toString().replace(",", "")))
									.setScale(radixSize, RoundingMode.FLOOR);
				} else {
					groupSums[columnIndex] = new BigDecimal(
							StringUtil.isBlank(sumCellValue) ? "0" : sumCellValue.toString().replace(",", ""))
									.add(new BigDecimal(StringUtil.isBlank(cellValue) ? "0"
											: cellValue.toString().replace(",", "")));
				}
			}
		}
	}

	/**
	 * @todo <b>列转行</b>
	 * @param data
	 * @param colIndex 保留哪些列进行旋转(其它的列数据忽略)
	 * @return
	 */
	public static List convertColToRow(List data, Integer[] colIndex) {
		if (data == null || data.isEmpty()) {
			return data;
		}
		boolean innerAry = data.get(0).getClass().isArray();
		int newResultRowCnt = 0;
		if (colIndex == null) {
			newResultRowCnt = innerAry ? convertArray(data.get(0)).length : ((List) data.get(0)).size();
		} else {
			newResultRowCnt = colIndex.length;
		}

		/**
		 * 构造结果集
		 */
		Object[][] resultAry = new Object[newResultRowCnt][data.size()];
		Object[] rowAry = null;
		List rowList = null;
		for (int i = 0, n = data.size(); i < n; i++) {
			if (innerAry) {
				rowAry = convertArray(data.get(i));
			} else {
				rowList = (List) data.get(i);
			}
			if (colIndex != null) {
				for (int j = 0, k = colIndex.length; j < k; j++) {
					resultAry[j][i] = innerAry ? rowAry[colIndex[j]] : rowList.get(colIndex[j]);
				}
			} else {
				for (int j = 0; j < newResultRowCnt; j++) {
					resultAry[j][i] = innerAry ? rowAry[j] : rowList.get(j);
				}
			}
		}
		return arrayToDeepList(resultAry);
	}

	public static class SummarySite {
		public static String top = "top";
		public static String bottom = "bottom";
		public static String left = "left";
		public static String right = "right";
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
		if (type.equals("string")) {
			result = new String[values.length];
		} else if (type.equals("int") || type.equals("integer")) {
			result = new Integer[values.length];
		} else if (type.equals("long")) {
			result = new Long[values.length];
		} else if (type.equals("date")) {
			result = new Date[values.length];
		} else if (type.equals("boolean")) {
			result = new Boolean[values.length];
		} else if (type.equals("double")) {
			result = new Double[values.length];
		} else if (type.equals("float")) {
			result = new Float[values.length];
		} else if (type.equals("short")) {
			result = new Short[values.length];
		} else if (type.equals("java.lang.class") || type.equals("class")) {
			result = new Class[values.length];
		}
		for (int i = 0; i < result.length; i++) {
			if (values[i] != null) {
				if (type.equals("string")) {
					result[i] = values[i];
				} else if (type.equals("int") || type.equals("integer")) {
					result[i] = Integer.valueOf(values[i]);
				} else if (type.equals("long")) {
					result[i] = Long.valueOf(values[i]);
				} else if (type.equals("date")) {
					result[i] = DateUtil.parseString(values[i]);
				} else if (type.equals("boolean")) {
					result[i] = Boolean.parseBoolean(values[i]);
				} else if (type.equals("double")) {
					result[i] = Double.valueOf(values[i]);
				} else if (type.equals("float")) {
					result[i] = Float.valueOf(values[i]);
				} else if (type.equals("short")) {
					result[i] = Short.valueOf(values[i]);
				} else if (type.equals("java.lang.class") || type.equals("class")) {
					try {
						result[i] = Class.forName(values[i]);
					} catch (ClassNotFoundException e) {
					}
				}
			}
		}
		return result;
	}

	public static boolean any(Object value, Object[] compareAry) {
		return any(value, compareAry, false);
	}

	/**
	 * @todo 判断字符串或对象数据是否在给定的数组中
	 * @param compareStr
	 * @param compareAry
	 * @param ignoreCase
	 * @return
	 */
	public static boolean any(Object value, Object[] compareAry, boolean ignoreCase) {
		if (value == null || (compareAry == null || compareAry.length == 0)) {
			return false;
		}
		for (Object s : compareAry) {
			if (s == null) {
				return false;
			}
			if (value.equals(s)) {
				return true;
			}
			if (ignoreCase && value.toString().equalsIgnoreCase(s.toString())) {
				return true;
			}
		}
		return false;
	}
}
