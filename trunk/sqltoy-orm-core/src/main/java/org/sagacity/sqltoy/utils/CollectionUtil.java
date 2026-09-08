package org.sagacity.sqltoy.utils;

import java.lang.reflect.Array;
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
import java.util.Locale;
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
 * @modify Date:2011-08-11 修复了pivotList设置旋转数据的初始值错误
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
	 * 数组转换为List集合,此转换只适用于一维和二维数组
	 * 
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
	 * 此转换只适用于一维数组(建议使用Arrays.asList())
	 * 
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
			logger.warn("arySource is not Array! it type is :{}", arySource.getClass());
			resultList.add(arySource);
		}
		return resultList;
	}

	/**
	 * 对简单对象进行排序(此方法不建议使用，请用Collections中的排序)
	 * 
	 * @param aryData
	 * @param descend
	 */
	public static void sortArray(Object[] aryData, boolean descend) {
		if (aryData != null && aryData.length > 1) {
			int length = aryData.length;
			Object iData;
			Object jData;
			// 1:string,2:数字;3:日期
			int dataType = 1;
			if (aryData[0] instanceof java.lang.Number) {
				dataType = 2;
			} else if (aryData[0] instanceof java.util.Date) {
				dataType = 3;
			} else if (aryData[0] instanceof LocalDate) {
				dataType = 4;
			} else if (aryData[0] instanceof LocalDateTime) {
				dataType = 5;
			} else if (aryData[0] instanceof LocalTime) {
				dataType = 6;
			}
			String str1, str2;
			boolean lessThen = false;
			for (int i = 0; i < length - 1; i++) {
				for (int j = i + 1; j < length; j++) {
					iData = aryData[i];
					jData = aryData[j];
					// 字符
					if (dataType == 1) {
						str1 = iData.toString();
						str2 = jData.toString();
						if (str1.length() < str2.length()) {
							lessThen = true;
						} else if (str1.length() > str2.length()) {
							lessThen = false;
						} else {
							lessThen = str1.compareTo(str2) < 0;
						}
					} else if (dataType == 2) {
						lessThen = ((Number) iData).doubleValue() < ((Number) jData).doubleValue();
					} else if (dataType == 3) {
						lessThen = ((Date) iData).before((Date) jData);
					} else if (dataType == 4) {
						lessThen = ((LocalDate) iData).compareTo((LocalDate) jData) < 0;
					} else if (dataType == 5) {
						lessThen = ((LocalDateTime) iData).compareTo((LocalDateTime) jData) < 0;
					} else if (dataType == 6) {
						lessThen = ((LocalTime) iData).compareTo((LocalTime) jData) < 0;
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
	 * 剔除对象数组中的部分数据,简单采用List remove方式实现
	 * 
	 * @param sourceAry 原始数组，null或空返回null
	 * @param begin     剔除的起始位置
	 * @param length    剔除的元素个数，为0或超出数组范围时原数组返回
	 * @return 剔除指定区段后的新数组
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
	 * 二维list转换为数组对象
	 * 
	 * @param source 二维集合，元素可为Collection、数组或Map(取values)
	 * @return 对应的二维数组，source为null或空返回null
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
	 * 判断list的维度
	 * 
	 * @param obj 待判断的集合、数组或Map
	 * @return 维度：0表示非集合类型，1表示一维，2表示元素仍是集合/数组/Map的二维；obj为null返回-1
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
				// Set等非List集合不能强转List,经迭代器取首个元素
				firstCellValue = tmp.iterator().next();
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
	 * 将集合数据转成hashMap
	 * 
	 * @param data      源数据，支持一维/二维的List或数组，元素可为List行或bean
	 * @param keyProp   作为key的属性名称或列序号(纯数字视为列序号)
	 * @param valueProp 作为value的属性名称或列序号，null时value取整行数据
	 * @param keyToStr  将key统一转成字符串
	 * @return key为属性值、value为对应属性值或整行数据的HashMap，异常时返回已处理部分的空Map
	 */
	public static HashMap hashList(Object data, Object keyProp, Object valueProp, boolean keyToStr) {
		return hashList(data, keyProp, valueProp, keyToStr, false);
	}

	/**
	 * 将集合数据转成hashMap
	 * 
	 * @param data         源数据，支持一维/二维的List或数组，元素可为List行或bean
	 * @param keyProp      作为key的属性名称或列序号(纯数字视为列序号)
	 * @param valueProp    作为value的属性名称或列序号，null时value取整行数据
	 * @param keyToStr     将key统一转成字符串
	 * @param isLinkedHash 返回的是否为LinkedHashMap
	 * @return key为属性值、value为对应属性值或整行数据的HashMap，异常时返回已处理部分的空Map
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
			logger.error("hashList method execution failed", e);
		}
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
	 * 将内部list转换为数组
	 * 
	 * @param source 二维集合，元素须为Collection类型，null或空原样返回
	 * @return 内部每个子集合转换为数组后的List
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
				logger.error("the data type must be Collection");
				break;
			}
		}
		return result;
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

	/**
	 * 列转行
	 * 
	 * @param data      源数据集合(二维List，每行为一行记录)，null或空原样返回
	 * @param colIndexs 保留哪些列进行旋转(其它的列数据忽略)
	 * @return 以列数据为行、原行数据为列的新集合
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
	 * 判断字符串是否在给定的数组中
	 * 
	 * @param compareStr 待比较的字符串，null返回false
	 * @param compareAry 待比较的字符串数组，null或空返回false
	 * @param ignoreCase true忽略大小写比较
	 * @return 数组中存在相等元素返回true，否则返回false
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
	 * 字符串数组按照类型转换
	 * 
	 * @param values  待转换的字符串数组，null返回null
	 * @param argType 目标类型名称，如string、int、long、date、boolean、double、float、short、class，大小写不敏感
	 * @return 转换后的对应类型数组，类型未识别时原样返回字符串数组
	 */
	public static Object[] toArray(String[] values, String argType) {
		if (values == null) {
			return null;
		}
		String type = argType.toLowerCase(Locale.ROOT);
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
		} else {
			// 未识别类型按原数组返回,避免result为null时后续取result.length抛NPE
			return values;
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

	/**
	 * 清除集合中的null值
	 * 
	 * @param dataSet 待清理的集合，直接在原集合上移除null元素，null或空不做处理
	 */
	public static void removeNull(Collection dataSet) {
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
	 * 分组排序
	 * 
	 * @param dataSet      二维数据集合，分组列值相同的连续行作为一个分组，方法直接在原集合上排序
	 * @param groupIndexes 分组列的序号数组
	 * @param sortIndex    组内排序列的序号
	 * @param desc         true降序，false升序
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
	 * 提取排序列的具体数据类型
	 * 
	 * @param dataSet   二维数据集合
	 * @param sortIndex 排序列的序号
	 * @return 数据类型：1字符串、2数字、3Date、4LocalDate、5LocalDateTime、6LocalTime；列值全为null时返回1
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
	 * List集合排序
	 * 
	 * @param sortList 二维数据集合，直接在原集合上排序
	 * @param orderCol 排序列的序号
	 * @param dataType 列数据类型：1字符串、2数字、3日期等，参见getSortDataType返回值
	 * @param start    排序范围起始行序号(含)
	 * @param end      排序范围结束行序号(含)
	 * @param ascend   true升序，false降序
	 * @return 排序后的原集合
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
	 * 数据大小比较，用于排序
	 * 
	 * @param iData    待比较的数据
	 * @param jData    待比较的数据
	 * @param dataType 数据类型：1字符串、2数字、3日期等
	 * @param ascend   true升序，false降序
	 * @return iData小于jData时升序返回-1、降序返回1，相等返回0，null值排在前面(升序时)
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
		} else if (iData != null && jData != null) {
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
	 * 分组计算
	 * 
	 * @param dataSet       二维数据集合，直接在原集合上修改(每组各行末尾追加计算结果)
	 * @param groupIndexes  分组列的序号数组
	 * @param calcuateIndex 参与计算的列序号
	 * @param isSum         true求和，false求平均值(保留4位小数)
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
				// 求平均值:组区间[start,end]闭区间共end-start+1行,分母不能差一
				if (!isSum && end > start) {
					calculateValue = calculateValue.divide(BigDecimal.valueOf(end - start + 1L), 4,
							RoundingMode.HALF_DOWN);
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
				// 求平均值:组区间[start,end]闭区间共end-start+1行,分母不能差一
				if (!isSum && end > start) {
					calculateValue = calculateValue.divide(BigDecimal.valueOf(end - start + 1L), 4,
							RoundingMode.HALF_DOWN);
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
	 * 去除in中的重复数据
	 * 
	 * @param inArgsList in条件对应的参数值数组集合，null或首行少于2个元素时原样返回
	 * @return 去除重复参数组合后的新集合(与输入等参数个数，每行为去重后的参数值数组)
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

	/**
	 * 将Iterator转为Array数组
	 * 
	 * @param iterable 可迭代对象，null返回null
	 * @return 包含全部迭代元素的Object数组
	 */
	public static Object[] iterableToArray(Iterable iterable) {
		if (iterable == null) {
			return null;
		}
		Iterator iters = iterable.iterator();
		List resultList = new ArrayList<>();
		while (iters.hasNext()) {
			resultList.add(iters.next());
		}
		return resultList.toArray();
	}

	/**
	 * 切取集合中的某列值组成数组对象返回，一般用于sql in (:values) 条件数组数据提取
	 * 
	 * @param dataSet  源数据集合，元素可为Map、List行或bean，null或空返回null
	 * @param column   可以是数字也可以是一个字段名称
	 * @param distinct 是否去除重复
	 * @return 指定列的值组成的数组
	 */
	public static Object[] sliceColumn(List dataSet, String column, boolean distinct) {
		if (dataSet == null || dataSet.isEmpty()) {
			return null;
		}
		if (NumberUtil.isInteger(column)) {
			return sliceColumn(dataSet, Integer.parseInt(column), distinct).toArray();
		}
		if (dataSet.get(0) instanceof Map) {
			List result = new ArrayList();
			Object value;
			for (int i = 0; i < dataSet.size(); i++) {
				value = ((Map) dataSet.get(i)).get(column);
				if (distinct) {
					if (!result.contains(value)) {
						result.add(value);
					}
				} else {
					result.add(value);
				}
			}
			return result.toArray();
		}
		try {
			List tmpResult = BeanUtil.reflectBeansToList(dataSet, new String[] { column });
			return sliceColumn(tmpResult, 0, distinct).toArray();
		} catch (Exception e) {
			logger.error("sliceColumn method execution failed", e);
		}
		return null;
	}

	/**
	 * 切取集合的单一列（切片）
	 * 
	 * @param source      源数据集合，元素为List行或数组，null或空返回null
	 * @param columnIndex 列序号
	 * @param distinct    是否去除重复
	 * @return 该列的值组成的List
	 */
	public static List sliceColumn(List source, int columnIndex, boolean distinct) {
		if (source == null || source.isEmpty()) {
			return null;
		}
		boolean isArray = (source.get(0).getClass().isArray()) ? true : false;
		List result = new ArrayList();
		Object cell;
		for (int i = 0, n = source.size(); i < n; i++) {
			cell = isArray ? convertArray(source.get(i))[columnIndex] : ((List) source.get(i)).get(columnIndex);
			if (distinct) {
				if (!result.contains(cell)) {
					result.add(cell);
				}
			} else {
				result.add(cell);
			}
		}
		return result;
	}

	/**
	 * 判断是否存在，不存在则加入
	 * 
	 * @param notRepeatSet 判重用的Set集合，直接在其上添加元素
	 * @param value        待判断的值
	 * @return true表示原集合中不存在且已加入，false表示已存在未加入
	 */
	public static boolean notContainsAdd(Set notRepeatSet, Object value) {
		if (notRepeatSet.contains(value)) {
			return false;
		} else {
			notRepeatSet.add(value);
			return true;
		}
	}

	public static boolean isNotEmpty(Object set) {
		return !isEmpty(set);
	}

	/**
	 * 判断对象是否为null或为空
	 * 
	 * @param set 待判断的对象，支持Collection、Map、数组和字符串类型
	 * @return true表示为null、空集合、空Map、长度为0的数组或空白字符串
	 */
	public static boolean isEmpty(Object set) {
		if (null == set) {
			return true;
		}
		if ((set instanceof Collection) && ((Collection) set).isEmpty()) {
			return true;
		}
		if ((set instanceof Map) && ((Map) set).isEmpty()) {
			return true;
		}
		if (set.getClass().isArray() && Array.getLength(set) == 0) {
			return true;
		}
		if ((set instanceof CharSequence) && set.toString().trim().equals("")) {
			return true;
		}
		return false;
	}
}
