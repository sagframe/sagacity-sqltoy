package org.sagacity.sqltoy.utils;

import java.lang.reflect.Array;
import java.lang.reflect.Method;
import java.lang.reflect.Modifier;
import java.lang.reflect.ParameterizedType;
import java.lang.reflect.Type;
import java.sql.Connection;
import java.sql.ResultSet;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Date;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Iterator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;

import org.sagacity.sqltoy.SqlExecuteStat;
import org.sagacity.sqltoy.SqlToyConstants;
import org.sagacity.sqltoy.SqlToyContext;
import org.sagacity.sqltoy.callback.DecryptHandler;
import org.sagacity.sqltoy.callback.StreamResultHandler;
import org.sagacity.sqltoy.callback.UpdateRowHandler;
import org.sagacity.sqltoy.config.SqlConfigParseUtils;
import org.sagacity.sqltoy.config.model.ColsChainRelativeModel;
import org.sagacity.sqltoy.config.model.DataType;
import org.sagacity.sqltoy.config.model.EntityMeta;
import org.sagacity.sqltoy.config.model.FieldTranslate;
import org.sagacity.sqltoy.config.model.FormatModel;
import org.sagacity.sqltoy.config.model.LabelIndexModel;
import org.sagacity.sqltoy.config.model.LinkModel;
import org.sagacity.sqltoy.config.model.OperateType;
import org.sagacity.sqltoy.config.model.PivotModel;
import org.sagacity.sqltoy.config.model.ReverseModel;
import org.sagacity.sqltoy.config.model.RowsChainRelativeModel;
import org.sagacity.sqltoy.config.model.SecureMask;
import org.sagacity.sqltoy.config.model.SqlToyConfig;
import org.sagacity.sqltoy.config.model.SqlToyResult;
import org.sagacity.sqltoy.config.model.SqlType;
import org.sagacity.sqltoy.config.model.SummaryModel;
import org.sagacity.sqltoy.config.model.TableCascadeModel;
import org.sagacity.sqltoy.config.model.Translate;
import org.sagacity.sqltoy.config.model.TreeSortModel;
import org.sagacity.sqltoy.config.model.UnpivotModel;
import org.sagacity.sqltoy.dialect.utils.DialectUtils;
import org.sagacity.sqltoy.exception.DataAccessException;
import org.sagacity.sqltoy.model.IgnoreCaseSet;
import org.sagacity.sqltoy.model.IgnoreKeyCaseMap;
import org.sagacity.sqltoy.model.QueryExecutor;
import org.sagacity.sqltoy.model.QueryResult;
import org.sagacity.sqltoy.model.inner.DataSetResult;
import org.sagacity.sqltoy.model.inner.QueryExecutorExtend;
import org.sagacity.sqltoy.plugins.calculator.ColsChainRelative;
import org.sagacity.sqltoy.plugins.calculator.GroupSummary;
import org.sagacity.sqltoy.plugins.calculator.ReverseList;
import org.sagacity.sqltoy.plugins.calculator.RowsChainRelative;
import org.sagacity.sqltoy.plugins.calculator.TreeDataSort;
import org.sagacity.sqltoy.plugins.calculator.UnpivotList;
import org.sagacity.sqltoy.plugins.secure.DesensitizeProvider;
import org.sagacity.sqltoy.translate.DynamicCacheFetch;
import org.sagacity.sqltoy.translate.FieldTranslateCacheHolder;
import org.sagacity.sqltoy.translate.TranslateConfigParse;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * @project sagacity-sqltoy
 * @description 提供查询结果的缓存key-value提取以及结果分组link功能
 * @author zhongxuchen
 * @version v1.0,Date:2013-4-18
 * @modify Date:2016-12-13 {对行转列分类参照集合进行了排序}
 * @modify Date:2020-05-29 {将脱敏和格式化转到calculate中,便于elastic和mongo查询提供同样的功能}
 * @modify Date:2024-03-15 {由俊华反馈，优化hiberarchySet支持逻辑业务主子关系，如单据中的创建人，审批人分别映射员工表}
 * @modify Date:2024-08-08 {修复hiberarchySet方法中遗漏对主对象集合进行缓存翻译的缺陷}
 * @modify Date:2025-03-31 {修复查询结果做link操作,结果为List、Set、Array的处理遗漏最后一条的处理缺陷}
 */
@SuppressWarnings({ "rawtypes", "unchecked" })
public class ResultUtils {
	/**
	 * 定义日志
	 */
	private final static Logger logger = LoggerFactory.getLogger(ResultUtils.class);

	private ResultUtils() {
	}

	/**
	 * @todo 处理sql查询时的结果集,当没有反调或voClass反射处理时以数组方式返回resultSet的数据
	 * @param sqlToyContext
	 * @param sqlToyConfig
	 * @param conn
	 * @param rs
	 * @param queryExecutorExtend
	 * @param updateRowHandler
	 * @param decryptHandler
	 * @param startColIndex
	 * @return
	 * @throws Exception
	 */
	public static QueryResult processResultSet(final SqlToyContext sqlToyContext, final SqlToyConfig sqlToyConfig,
			Connection conn, ResultSet rs, QueryExecutorExtend queryExecutorExtend, UpdateRowHandler updateRowHandler,
			DecryptHandler decryptHandler, int startColIndex) throws Exception {
		QueryResult result = new QueryResult();
		// 记录行记数器
		int index = 0;
		if (queryExecutorExtend != null && queryExecutorExtend.rowCallbackHandler != null) {
			while (rs.next()) {
				queryExecutorExtend.rowCallbackHandler.processRow(rs, index);
				index++;
			}
			result.setRows(queryExecutorExtend.rowCallbackHandler.getResult());
		} else {
			// 重新组合解密字段(entityMeta中的和sql自定义的合并)
			IgnoreCaseSet decryptColumns = (decryptHandler == null) ? null : decryptHandler.getColumns();
			if (sqlToyConfig.getDecryptColumns() != null) {
				if (decryptColumns == null) {
					decryptColumns = sqlToyConfig.getDecryptColumns();
				} else {
					decryptColumns.addAll(sqlToyConfig.getDecryptColumns());
				}
			}
			DecryptHandler realDecryptHandler = null;
			if (decryptColumns != null && !decryptColumns.isEmpty()) {
				realDecryptHandler = new DecryptHandler(sqlToyContext.getFieldsSecureProvider(), decryptColumns);
			}
			// 取得字段列数,在没有rowCallbackHandler時用数组返回
			int columnCnt = rs.getMetaData().getColumnCount();
			// 类型转成string的列
			Set<String> strTypeCols = getStringColumns(sqlToyConfig);
			boolean hasToStrCols = !strTypeCols.isEmpty();
			String[] labelNames = new String[columnCnt - startColIndex];
			String[] labelTypes = new String[columnCnt - startColIndex];
			HashMap<String, Integer> labelIndexMap = new HashMap<String, Integer>();
			String labeNameLow;
			String colLabelUpperOrLower = sqlToyContext.getColumnLabelUpperOrLower();
			for (int i = startColIndex; i < columnCnt; i++) {
				labelNames[index] = rs.getMetaData().getColumnLabel(i + 1);
				labeNameLow = labelNames[index].toLowerCase();
				if ("lower".equals(colLabelUpperOrLower)) {
					labelNames[index] = labelNames[index].toLowerCase();
				} else if ("upper".equals(colLabelUpperOrLower)) {
					labelNames[index] = labelNames[index].toUpperCase();
				}
				labelIndexMap.put(labeNameLow, index);
				labelTypes[index] = rs.getMetaData().getColumnTypeName(i + 1);
				// 类型因缓存翻译、格式化转为string
				if (hasToStrCols && strTypeCols.contains(labeNameLow)) {
					labelTypes[index] = "VARCHAR";
				}
				index++;
			}
			result.setLabelNames(labelNames);
			result.setLabelTypes(labelTypes);
			// 返回结果为非VO class时才可以应用旋转和汇总合计功能
			try {
				result.setRows(getResultSet(queryExecutorExtend, sqlToyConfig, sqlToyContext, conn, rs,
						updateRowHandler, realDecryptHandler, columnCnt, labelIndexMap, labelNames, startColIndex));
			} // update 2019-09-11 此处增加数组溢出异常是因为经常有开发设置缓存cache-indexs时写错误，为了增加错误提示信息的友好性增加此处理
			catch (Exception oie) {
				logger.error("sql={} 提取结果发生异常:{}!", sqlToyConfig.getId(), oie.getMessage());
				throw oie;
			}
		}
		// 填充记录数
		if (result.getRows() != null) {
			result.setRecordCount(Long.valueOf(result.getRows().size()));
		}
		return result;
	}

	/**
	 * @TODO 基于游标直接消费每行数据
	 * @param sqlToyContext
	 * @param extend
	 * @param sqlToyConfig
	 * @param conn
	 * @param rs
	 * @param streamResultHandler
	 * @param resultType
	 * @param humpMapLabel
	 * @param fieldsMap
	 * @throws Exception
	 */
	public static void consumeResult(final SqlToyContext sqlToyContext, final QueryExecutorExtend extend,
			final SqlToyConfig sqlToyConfig, Connection conn, ResultSet rs,
			final StreamResultHandler streamResultHandler, Class resultType, Boolean humpMapLabel,
			Map<Class, IgnoreKeyCaseMap<String, String>> fieldsMap) throws Exception {
		// 重新组合解密字段(entityMeta中的和sql自定义的合并)
		IgnoreCaseSet decryptColumns = sqlToyConfig.getDecryptColumns();
		DecryptHandler realDecryptHandler = null;
		if (decryptColumns != null && !decryptColumns.isEmpty()) {
			realDecryptHandler = new DecryptHandler(sqlToyContext.getFieldsSecureProvider(), decryptColumns);
		}
		// 取得字段列数
		int columnSize = rs.getMetaData().getColumnCount();
		// 类型转成string的列
		Set<String> strTypeCols = getStringColumns(sqlToyConfig);
		boolean hasToStrCols = !strTypeCols.isEmpty();
		String[] labelNames = new String[columnSize];
		String[] labelTypes = new String[columnSize];
		String labeNameLow;
		// 字段名称统一转大写或小写,默认为default,即不做任何处理
		String colLabelUpperOrLower = sqlToyContext.getColumnLabelUpperOrLower();
		int index = 0;
		for (int i = 0; i < columnSize; i++) {
			labelNames[index] = rs.getMetaData().getColumnLabel(i + 1);
			labeNameLow = labelNames[index].toLowerCase();
			if ("lower".equals(colLabelUpperOrLower)) {
				labelNames[index] = labelNames[index].toLowerCase();
			} else if ("upper".equals(colLabelUpperOrLower)) {
				labelNames[index] = labelNames[index].toUpperCase();
			}
			labelTypes[index] = rs.getMetaData().getColumnTypeName(i + 1);
			// 类型因缓存翻译、格式化转为string
			if (hasToStrCols && strTypeCols.contains(labeNameLow)) {
				labelTypes[index] = "VARCHAR";
			}
			index++;
		}
		HashMap<String, FieldTranslate> translateMap = sqlToyConfig.getTranslateMap();
		// 判断是否有缓存翻译器定义
		Boolean hasTranslate = (translateMap.isEmpty()) ? false : true;
		HashMap<String, FieldTranslateCacheHolder> translateCache = null;
		if (hasTranslate) {
			translateCache = sqlToyContext.getTranslateManager().getTranslates(translateMap);
		}

		LabelIndexModel labelIndexModel = wrapLabelIndexMap(labelNames);
		// 是否判断全部为null的行记录
		boolean ignoreAllEmpty = sqlToyConfig.isIgnoreEmpty();
		List<SecureMask> secureMasks = sqlToyConfig.getSecureMasks();
		List<FormatModel> formatModels = sqlToyConfig.getFormatModels();
		boolean sqlSecure = !secureMasks.isEmpty();
		boolean sqlFormat = !formatModels.isEmpty();
		boolean extSecure = (extend != null && !extend.secureMask.isEmpty());
		boolean extFormat = (extend != null && !extend.colsFormat.isEmpty());
		DesensitizeProvider desensitizeProvider = sqlToyContext.getDesensitizeProvider();
		// 1:List;2:array;3:map;4:voClass
		int type = 1;
		boolean isMap = false;
		boolean isConMap = false;
		HashMap<String, String> columnFieldMap = null;
		Method[] realMethods = null;
		String[] methodTypes = null;
		int[] methodTypeValues = null;
		Class[] genericTypes = null;
		String[] realProps = null;
		int[] indexs = null;
		HashMap<String, String> lowKeyLabelNameMap = labelLowKeyMap(labelNames);
		HashMap<String, FieldTranslateCacheHolder> cacheDatas = null;
		HashMap<String, FieldTranslate> translateConfig = null;
		DynamicCacheFetch dynamicCacheFetch = sqlToyContext.getDynamicCacheFetch();
		String[] mapLabelNames = labelNames;
		if (resultType != null && resultType != ArrayList.class && resultType != Collection.class
				&& resultType != List.class && !BeanUtil.isBaseDataType(resultType)) {
			if (resultType == Array.class) {
				type = 2;
			} else if (Map.class.isAssignableFrom(resultType)) {
				type = 3;
				isMap = resultType.equals(Map.class);
				isConMap = resultType.equals(ConcurrentMap.class);
				boolean isHumpLabel = (humpMapLabel == null ? sqlToyContext.isHumpMapResultTypeLabel() : humpMapLabel);
				// 驼峰处理
				if (isHumpLabel) {
					mapLabelNames = humpFieldNames(labelNames, null);
				}
			} else {
				type = 4;
				if (Modifier.isAbstract(resultType.getModifiers()) || Modifier.isInterface(resultType.getModifiers())) {
					throw new IllegalArgumentException("resultType:" + resultType.getName() + " 是抽象类或接口,非法参数!");
				}
				if (sqlToyContext.isEntity(resultType)) {
					EntityMeta entityMeta = sqlToyContext.getEntityMeta(resultType);
					columnFieldMap = entityMeta.getColumnFieldMap();
				}
				realProps = convertRealProps(wrapMapFields(labelNames, fieldsMap, resultType), columnFieldMap);
				realMethods = BeanUtil.matchSetMethods(resultType, realProps);
				methodTypes = new String[columnSize];
				methodTypeValues = new int[columnSize];
				genericTypes = new Class[columnSize];
				indexs = new int[columnSize];
				Type[] types;
				Class methodType;
				// 自动适配属性的数据类型
				for (int i = 0; i < columnSize; i++) {
					indexs[i] = i;
					if (null != realMethods[i]) {
						methodType = realMethods[i].getParameterTypes()[0];
						methodTypes[i] = methodType.getTypeName();
						methodTypeValues[i] = DataType.getType(methodType);
						types = realMethods[i].getGenericParameterTypes();
						if (types.length > 0) {
							if (types[0] instanceof ParameterizedType) {
								genericTypes[i] = (Class) ((ParameterizedType) types[0]).getActualTypeArguments()[0];
							}
						}
					}
				}
				translateConfig = TranslateConfigParse.getClassTranslates(resultType);
				if (translateConfig != null && !translateConfig.isEmpty()) {
					cacheDatas = sqlToyContext.getTranslateManager().getTranslates(translateConfig);
				}
			}
		}
		// 执行开始
		streamResultHandler.start(labelNames, labelTypes);
		index = 0;
		List rowTemp;
		while (rs.next()) {
			rowTemp = processResultRow(dynamicCacheFetch, rs, labelNames, lowKeyLabelNameMap, columnSize,
					translateCache, realDecryptHandler, ignoreAllEmpty);
			if (rowTemp != null) {
				// 字段脱敏
				if (sqlSecure) {
					secureMaskRow(desensitizeProvider, rowTemp, secureMasks.iterator(), labelIndexModel);
				}
				// 自动格式化
				if (sqlFormat) {
					formatRowColumn(rowTemp, formatModels.iterator(), labelIndexModel);
				}
				// 扩展脱敏和格式化处理
				if (extSecure) {
					secureMaskRow(desensitizeProvider, rowTemp, extend.secureMask.values().iterator(), labelIndexModel);
				}
				if (extFormat) {
					formatRowColumn(rowTemp, extend.colsFormat.values().iterator(), labelIndexModel);
				}
				// 消费每行数据
				if (type == 1) {
					streamResultHandler.consume(rowTemp, index);
				} // 数组
				else if (type == 2) {
					Object[] rowAry = new Object[rowTemp.size()];
					rowTemp.toArray(rowAry);
					streamResultHandler.consume(rowAry, index);
				} // map
				else if (type == 3) {
					Map rowMap;
					if (isMap) {
						rowMap = new HashMap();
					} else if (isConMap) {
						rowMap = new ConcurrentHashMap();
					} else {
						rowMap = (Map) resultType.getDeclaredConstructor().newInstance();
					}
					for (int j = 0; j < columnSize; j++) {
						rowMap.put(mapLabelNames[j], rowTemp.get(j));
					}
					streamResultHandler.consume(rowMap, index);
				} // 封装成VO对象形式
				else {
					Object bean = BeanUtil.reflectRowToBean(sqlToyContext.getTypeHandler(), realMethods,
							methodTypeValues, methodTypes, genericTypes, rowTemp, indexs, realProps, resultType);
					// 有基于注解@Translate的缓存翻译
					if (cacheDatas != null) {
						wrapBeanTranslate(dynamicCacheFetch, cacheDatas, bean);
					}
					streamResultHandler.consume(bean, index);
				}
				index++;
			}
		}
		// 完成消费
		streamResultHandler.end();
		SqlExecuteStat.debug("操作提示", "流式查询累计获取:{} 条记录!", index);
	}

	/**
	 * @todo 对字段进行安全脱敏
	 * @param desensitizeProvider
	 * @param rows
	 * @param masks
	 * @param labelIndexMap
	 */
	private static void secureMask(DesensitizeProvider desensitizeProvider, List<List> rows, Iterator<SecureMask> masks,
			LabelIndexModel labelIndexMap) {
		Integer index;
		Object value;
		SecureMask mask;
		int columnIndex;
		while (masks.hasNext()) {
			mask = masks.next();
			index = labelIndexMap.get(mask.getColumn());
			if (index != null) {
				columnIndex = index.intValue();
				for (List row : rows) {
					value = row.get(columnIndex);
					if (value != null) {
						row.set(columnIndex, desensitizeProvider.desensitize(value.toString(), mask));
					}
				}
			}
		}
	}

	private static void secureMaskRow(DesensitizeProvider desensitizeProvider, List row, Iterator<SecureMask> masks,
			LabelIndexModel labelIndexMap) {
		Integer index;
		Object value;
		SecureMask mask;
		int columnIndex;
		while (masks.hasNext()) {
			mask = masks.next();
			index = labelIndexMap.get(mask.getColumn());
			if (index != null) {
				columnIndex = index.intValue();
				value = row.get(columnIndex);
				if (value != null) {
					row.set(columnIndex, desensitizeProvider.desensitize(value.toString(), mask));
				}
			}
		}
	}

	/**
	 * @todo 对字段进行格式化
	 * @param rows
	 * @param formats
	 * @param labelIndexMap
	 */
	private static void formatColumn(List<List> rows, Iterator<FormatModel> formats, LabelIndexModel labelIndexMap) {
		Integer index;
		Object value;
		FormatModel fmt;
		int columnIndex;
		while (formats.hasNext()) {
			fmt = formats.next();
			index = labelIndexMap.get(fmt.getColumn());
			if (index == null && NumberUtil.isInteger(fmt.getColumn())) {
				index = Integer.parseInt(fmt.getColumn());
			}
			if (index != null) {
				columnIndex = index.intValue();
				for (List row : rows) {
					value = row.get(columnIndex);
					if (value != null) {
						// 日期格式
						if (fmt.getType() == 1) {
							row.set(columnIndex, DateUtil.formatDate(value, fmt.getFormat(),
									(fmt.getLocale() == null) ? null : new Locale(fmt.getLocale())));
						}
						// 数字格式化
						else {
							row.set(columnIndex, NumberUtil.format(value, fmt.getFormat(), fmt.getRoundingMode(),
									(fmt.getLocale() == null) ? null : new Locale(fmt.getLocale())));
						}
					}
				}
			}
		}
	}

	private static void formatRowColumn(List row, Iterator<FormatModel> formats, LabelIndexModel labelIndexMap) {
		Integer index;
		Object value;
		FormatModel fmt;
		int columnIndex;
		while (formats.hasNext()) {
			fmt = formats.next();
			index = labelIndexMap.get(fmt.getColumn());
			if (index == null && NumberUtil.isInteger(fmt.getColumn())) {
				index = Integer.parseInt(fmt.getColumn());
			}
			if (index != null) {
				columnIndex = index.intValue();
				value = row.get(columnIndex);
				if (value != null) {
					// 日期格式
					if (fmt.getType() == 1) {
						row.set(columnIndex, DateUtil.formatDate(value, fmt.getFormat(),
								(fmt.getLocale() == null) ? null : new Locale(fmt.getLocale())));
					}
					// 数字格式化
					else {
						row.set(columnIndex, NumberUtil.format(value, fmt.getFormat(), fmt.getRoundingMode(),
								(fmt.getLocale() == null) ? null : new Locale(fmt.getLocale())));
					}
				}
			}
		}
	}

	private static List getResultSet(QueryExecutorExtend queryExtend, SqlToyConfig sqlToyConfig,
			SqlToyContext sqlToyContext, Connection conn, ResultSet rs, UpdateRowHandler updateRowHandler,
			DecryptHandler decryptHandler, int columnCnt, HashMap<String, Integer> labelIndexMap, String[] labelNames,
			int startColIndex) throws Exception {
		// 字段连接(多行数据拼接成一个数据,以一行显示)
		LinkModel linkModel = sqlToyConfig.getLinkModel();
		if (queryExtend != null && queryExtend.linkModel != null) {
			linkModel = queryExtend.linkModel;
		}
		// update 2020-09-13 存在多列link(独立出去编写,避免对单列产生影响)
		if (linkModel != null && linkModel.getColumns().length > 1) {
			return getMoreLinkResultSet(sqlToyConfig, sqlToyContext, decryptHandler, conn, rs, columnCnt, labelIndexMap,
					labelNames, startColIndex);
		}

		List<List> items = new ArrayList();
		// 判断是否有缓存翻译器定义
		Boolean hasTranslate = (sqlToyConfig.getTranslateMap().isEmpty()) ? false : true;
		HashMap<String, String> lowKeyLabelNameMap = labelLowKeyMap(labelNames);
		HashMap<String, FieldTranslate> translateMap = sqlToyConfig.getTranslateMap();
		HashMap<String, FieldTranslateCacheHolder> fieldTranslateCacheHolders = null;
		// 动态获取缓存的实现
		DynamicCacheFetch dynamicCacheFetch = sqlToyContext.getDynamicCacheFetch();
		if (hasTranslate) {
			validateCacheConfig(translateMap, lowKeyLabelNameMap);
			fieldTranslateCacheHolders = sqlToyContext.getTranslateManager().getTranslates(translateMap);
		}
		// 单个字段link运算
		int columnSize = labelNames.length;
		int index = 0;
		// 警告阀值
		int warnThresholds = SqlToyConstants.getWarnThresholds();
		boolean warnLimit = false;
		// 最大阀值
		long maxThresholds = SqlToyConstants.getMaxThresholds();
		boolean maxLimit = false;
		// 是否判断全部为null的行记录
		boolean ignoreAllEmpty = sqlToyConfig.isIgnoreEmpty();
		// 最大值要大于等于警告阀值
		if (maxThresholds > 1 && maxThresholds <= warnThresholds) {
			maxThresholds = warnThresholds;
		}
		List itemRow;
		// 单列link
		if (linkModel != null) {
			Object identity = null;
			String linkColumn = linkModel.getColumns()[0];
			String linkColumnLow = linkColumn.toLowerCase();
			if (!labelIndexMap.containsKey(linkColumnLow)) {
				throw new DataAccessException("做link操作时,查询结果字段中没有字段:" + linkColumn + ",请检查sql或link 配置的正确性!");
			}
			// 转换成实际sql as的名称,避免手写字符大小写差异
			linkColumn = lowKeyLabelNameMap.get(linkColumnLow);
			Set<String> linkSet = new HashSet<String>();
			int linkIndex = labelIndexMap.get(linkColumnLow);
			StringBuilder linkBuffer = new StringBuilder();
			List linkList = new ArrayList();
			boolean hasDecorate = (linkModel.getDecorateAppendChar() == null) ? false : true;
			boolean isLeft = true;
			if (hasDecorate) {
				isLeft = "left".equals(linkModel.getDecorateAlign()) ? true : false;
			}
			Object preIdentity = null;
			Object linkValue;
			String linkStr;
			boolean translateLink = hasTranslate ? translateMap.containsKey(linkColumnLow) : false;
			FieldTranslateCacheHolder fieldTranslateHandler = null;
			if (translateLink) {
				fieldTranslateHandler = fieldTranslateCacheHolders.get(linkColumnLow);
			}
			boolean doLink = true;
			// 0:字符拼接，1:List;2:Array;3:HashSet
			int linkResultType = linkModel.getResultType();
			Object tmpObject;
			int notEqualCnt = 0;
			while (rs.next()) {
				linkValue = rs.getObject(linkColumn);
				if (linkValue == null) {
					linkStr = "";
				} else if (translateLink) {
					tmpObject = fieldTranslateHandler.getRSCacheValue(dynamicCacheFetch, rs, lowKeyLabelNameMap,
							linkValue.toString());
					linkStr = (tmpObject == null) ? linkValue.toString() : tmpObject.toString();
				} else {
					linkStr = linkValue.toString();
				}
				// groupColumns为null即表示全部集合合并
				identity = (linkModel.getGroupColumns() == null) ? "default"
						: getLinkColumnsId(rs, linkModel.getGroupColumns());
				// 不相等
				if (!identity.equals(preIdentity)) {
					itemRow = processResultRow(dynamicCacheFetch, rs, labelNames, lowKeyLabelNameMap, columnSize,
							fieldTranslateCacheHolders, decryptHandler, ignoreAllEmpty);
					if (itemRow != null) {
						// 只要有过一次不等，避免是第一行记录
						if (notEqualCnt > 0) {
							// List
							if (linkResultType == 1) {
								items.get(items.size() - 1).set(linkIndex, linkList);
								linkList = new ArrayList();
							} // Array
							else if (linkResultType == 2) {
								items.get(items.size() - 1).set(linkIndex, linkList.toArray());
								linkList = new ArrayList();
							} // Set
							else if (linkResultType == 3) {
								items.get(items.size() - 1).set(linkIndex, new HashSet(linkList));
								linkList = new ArrayList();
							} // String
							else {
								items.get(items.size() - 1).set(linkIndex, linkBuffer.toString());
								linkBuffer.delete(0, linkBuffer.length());
							}
							linkSet.clear();
						}
						// 非字符拼接模式
						if (linkResultType > 0) {
							if (translateLink) {
								linkList.add(linkStr);
							} else {
								linkList.add(linkValue);
							}
						} else {
							linkBuffer.append(linkStr);
						}
						linkSet.add(linkStr);
						items.add(itemRow);
						preIdentity = identity;
						notEqualCnt++;
					}
				} else {
					// identity相同，组织数据拼接
					doLink = true;
					if (linkModel.isDistinct() && linkSet.contains(linkStr)) {
						doLink = false;
					}
					linkSet.add(linkStr);
					if (doLink) {
						if (linkResultType > 0) {
							if (translateLink) {
								linkList.add(linkStr);
							} else {
								linkList.add(linkValue);
							}
						} else {
							if (linkBuffer.length() > 0) {
								linkBuffer.append(linkModel.getSign());
							}
							linkBuffer.append(hasDecorate ? StringUtil.appendStr(linkStr,
									linkModel.getDecorateAppendChar(), linkModel.getDecorateSize(), isLeft) : linkStr);
						}
					}
				}
				index++;
				// 存在超出25000条数据的查询
				if (index == warnThresholds) {
					warnLimit = true;
				}
				// 提取数据超过上限(-1表示不限制)
				if (index == maxThresholds) {
					maxLimit = true;
					break;
				}
			}
			// 只要存在记录，都对最后一条写入循环值
			if (notEqualCnt > 0) {
				// 0:字符拼接，1:List;2:Array;3:HashSet
				if (linkResultType == 1) {
					items.get(items.size() - 1).set(linkIndex, linkList);
				} else if (linkResultType == 2) {
					items.get(items.size() - 1).set(linkIndex, linkList.toArray());
				} else if (linkResultType == 3) {
					items.get(items.size() - 1).set(linkIndex, new HashSet(linkList));
				} else {
					items.get(items.size() - 1).set(linkIndex, linkBuffer.toString());
				}
			}
		} else {
			// 修改操作不支持link操作
			boolean isUpdate = false;
			if (updateRowHandler != null) {
				isUpdate = true;
			}
			while (rs.next()) {
				if (isUpdate) {
					updateRowHandler.updateRow(rs, index);
					rs.updateRow();
				}
				itemRow = processResultRow(dynamicCacheFetch, rs, labelNames, lowKeyLabelNameMap, columnSize,
						fieldTranslateCacheHolders, decryptHandler, ignoreAllEmpty);
				if (itemRow != null) {
					items.add(itemRow);
				}
				index++;
				// 存在超出25000条数据的查询(具体数据规模可以通过参数进行定义)
				if (index == warnThresholds) {
					warnLimit = true;
				}
				// 提取数据超过上限(-1表示不限制)
				if (index == maxThresholds) {
					maxLimit = true;
					break;
				}
			}
		}
		// 超出警告阀值
		if (warnLimit) {
			warnLog(sqlToyConfig, index);
		}
		// 超过最大提取数据阀值
		if (maxLimit) {
			logger.error(
					"MaxLargeResult:执行sql提取数据超出最大阀值限制{}(可通过[spring.sqltoy.pageFetchSizeLimit]参数调整),sqlId={},具体语句={}",
					index, sqlToyConfig.getId(), sqlToyConfig.getSql(null));
		}
		return items;
	}

	/**
	 * 校验缓存翻译配置正确性
	 * 
	 * @param fieldTranslateMap
	 * @param lowKeyLabelNameMap
	 */
	private static void validateCacheConfig(HashMap<String, FieldTranslate> fieldTranslateMap,
			HashMap<String, String> lowKeyLabelNameMap) {
		if (fieldTranslateMap == null || fieldTranslateMap.isEmpty()) {
			return;
		}
		fieldTranslateMap.forEach((fieldName, fieldTranslate) -> {
			for (Translate translate : fieldTranslate.translates) {
				if (translate.getExtend().hasLogic) {
					// compareColumn 设置时已经小写
					if (!lowKeyLabelNameMap.containsKey(translate.getExtend().compareColumn)) {
						throw new DataAccessException("查询结果字段中没有:[" + translate.getExtend().compareColumn
								+ "]字段,请检查cache=" + translate.getExtend().cache + "的缓存翻译,其逻辑表达式where的配置!");
					}
				}
			}
		});
	}

	/**
	 * @TODO 组合link 多列值作为对比值
	 * @param rs
	 * @param columns
	 * @return
	 * @throws Exception
	 */
	private static Object getLinkColumnsId(ResultSet rs, String[] columns) throws Exception {
		if (columns.length == 1) {
			return rs.getObject(columns[0]);
		}
		StringBuilder result = new StringBuilder();
		Object colValue;
		int index = 0;
		for (String column : columns) {
			if (index > 0) {
				result.append("_");
			}
			colValue = rs.getObject(column);
			result.append(colValue == null ? "null" : colValue.toString());
			index++;
		}
		return result.toString();
	}

	/**
	 * @TODO 实现多列link
	 * @param sqlToyConfig
	 * @param sqlToyContext
	 * @param decryptHandler
	 * @param conn
	 * @param rs
	 * @param columnCnt
	 * @param labelIndexMap
	 * @param labelNames
	 * @param startColIndex
	 * @return
	 * @throws Exception
	 */
	private static List getMoreLinkResultSet(SqlToyConfig sqlToyConfig, SqlToyContext sqlToyContext,
			DecryptHandler decryptHandler, Connection conn, ResultSet rs, int columnCnt,
			HashMap<String, Integer> labelIndexMap, String[] labelNames, int startColIndex) throws Exception {
		// 字段连接(多行数据拼接成一个数据,以一行显示)
		LinkModel linkModel = sqlToyConfig.getLinkModel();
		List<List> items = new ArrayList();
		// 判断是否有缓存翻译器定义
		Boolean hasTranslate = (sqlToyConfig.getTranslateMap().isEmpty()) ? false : true;
		HashMap<String, String> lowKeyLabelNameMap = labelLowKeyMap(labelNames);
		HashMap<String, FieldTranslate> translateMap = sqlToyConfig.getTranslateMap();
		HashMap<String, FieldTranslateCacheHolder> fieldTranslateCacheHolders = null;
		DynamicCacheFetch dynamicCacheFetch = sqlToyContext.getDynamicCacheFetch();
		if (hasTranslate) {
			validateCacheConfig(translateMap, lowKeyLabelNameMap);
			fieldTranslateCacheHolders = sqlToyContext.getTranslateManager().getTranslates(translateMap);
		}
		int columnSize = labelNames.length;
		// 警告阀值
		int warnThresholds = SqlToyConstants.getWarnThresholds();
		boolean warnLimit = false;
		// 最大阀值
		long maxThresholds = SqlToyConstants.getMaxThresholds();
		boolean maxLimit = false;
		// 是否判断全部为null的行记录
		boolean ignoreAllEmpty = sqlToyConfig.isIgnoreEmpty();
		// 最大值要大于等于警告阀值
		if (maxThresholds > 1 && maxThresholds <= warnThresholds) {
			maxThresholds = warnThresholds;
		}
		int linkColCnt = linkModel.getColumns().length;
		String[] linkColumns = linkModel.getColumns();
		int[] linkIndexs = new int[linkColCnt];
		String[] linkRealLabels = new String[linkColCnt];
		// link字段是否存在缓存翻译行为
		boolean[] translateLinks = new boolean[linkColCnt];
		StringBuilder[] linkBuffers = new StringBuilder[linkColCnt];
		Set<String>[] linkSets = linkModel.isDistinct() ? new HashSet[linkColCnt] : null;
		String linkColumnLow;
		String[] linkLowColumns = new String[linkColCnt];
		for (int i = 0; i < linkColCnt; i++) {
			linkBuffers[i] = new StringBuilder();
			linkColumnLow = linkColumns[i].toLowerCase();
			linkLowColumns[i] = linkColumnLow;
			if (!labelIndexMap.containsKey(linkColumnLow)) {
				throw new DataAccessException("做link操作时,查询结果字段中没有字段:" + linkColumnLow + ",请检查sql或link 配置的正确性!");
			}
			linkRealLabels[i] = lowKeyLabelNameMap.get(linkColumnLow);
			linkIndexs[i] = labelIndexMap.get(linkColumnLow);
			if (hasTranslate) {
				translateLinks[i] = translateMap.containsKey(linkColumnLow);
			}
			if (linkModel.isDistinct()) {
				linkSets[i] = new HashSet<String>();
			}
		}
		// link是否有修饰器
		boolean hasDecorate = (linkModel.getDecorateAppendChar() == null) ? false : true;
		boolean isLeft = true;
		if (hasDecorate) {
			isLeft = "left".equals(linkModel.getDecorateAlign()) ? true : false;
		}
		Object preIdentity = null;
		Object[] linkValues = new Object[linkColCnt];
		String[] linkStrs = new String[linkColCnt];
		List itemRow;
		List preItemRow;
		Object identity = null;
		boolean doLink = false;
		FieldTranslateCacheHolder fieldTranslateCacheHolder;
		Object tmpObject;
		int index = 0;
		int notEqualCnt = 0;
		while (rs.next()) {
			// 对多个link字段取值并进行翻译转义
			for (int i = 0; i < linkColCnt; i++) {
				linkValues[i] = rs.getObject(linkRealLabels[i]);
				if (linkValues[i] == null) {
					linkStrs[i] = "";
				} else if (translateLinks[i]) {
					fieldTranslateCacheHolder = fieldTranslateCacheHolders.get(linkLowColumns[i]);
					tmpObject = fieldTranslateCacheHolder.getRSCacheValue(dynamicCacheFetch, rs, lowKeyLabelNameMap,
							linkValues[i].toString());
					linkStrs[i] = (tmpObject == null) ? linkValues[i].toString() : tmpObject.toString();
				} else {
					linkStrs[i] = linkValues[i].toString();
				}
			}
			// 取分组列的值,groupColumns为null，即全部集合进行合并
			identity = (linkModel.getGroupColumns() == null) ? "default"
					: getLinkColumnsId(rs, linkModel.getGroupColumns());
			// 不相等
			if (!identity.equals(preIdentity)) {
				// 提取result中的数据(identity相等时不需要提取)
				itemRow = processResultRow(dynamicCacheFetch, rs, labelNames, lowKeyLabelNameMap, columnSize,
						fieldTranslateCacheHolders, decryptHandler, ignoreAllEmpty);
				if (itemRow != null) {
					// 不相等时先对最后一条记录修改，写入拼接后的字符串
					if (notEqualCnt > 0) {
						preItemRow = items.get(items.size() - 1);
						for (int i = 0; i < linkColCnt; i++) {
							preItemRow.set(linkIndexs[i], linkBuffers[i].toString());
							linkBuffers[i].delete(0, linkBuffers[i].length());
							// 清除
							if (linkModel.isDistinct()) {
								linkSets[i].clear();
							}
						}
					}
					// 再写入新的拼接串
					for (int i = 0; i < linkColCnt; i++) {
						linkBuffers[i].append(linkStrs[i]);
						if (linkModel.isDistinct()) {
							linkSets[i].add(linkStrs[i]);
						}
					}
					items.add(itemRow);
					notEqualCnt++;
					preIdentity = identity;
				}
			} else {
				// identity相同，表示还在同一组内，直接拼接link字符
				for (int i = 0; i < linkColCnt; i++) {
					doLink = true;
					// 判断是否已经重复
					if (linkModel.isDistinct()) {
						if (linkSets[i].contains(linkStrs[i])) {
							doLink = false;
						}
						linkSets[i].add(linkStrs[i]);
					}
					if (doLink) {
						if (linkBuffers[i].length() > 0) {
							linkBuffers[i].append(linkModel.getSign());
						}
						linkBuffers[i].append(hasDecorate ? StringUtil.appendStr(linkStrs[i],
								linkModel.getDecorateAppendChar(), linkModel.getDecorateSize(), isLeft) : linkStrs[i]);
					}
				}
			}
			index++;
			// 存在超出25000条数据的查询
			if (index == warnThresholds) {
				warnLimit = true;
			}
			// 提取数据超过上限(-1表示不限制)
			if (index == maxThresholds) {
				maxLimit = true;
				break;
			}
		}
		// 数据集合不为空,对最后一条记录写入循环值
		if (notEqualCnt > 0) {
			preItemRow = items.get(items.size() - 1);
			for (int i = 0; i < linkColCnt; i++) {
				preItemRow.set(linkIndexs[i], linkBuffers[i].toString());
			}
		}
		// 超出警告阀值
		if (warnLimit) {
			warnLog(sqlToyConfig, index);
		}
		// 超过最大提取数据阀值
		if (maxLimit) {
			logger.error(
					"MaxLargeResult:执行sql提取数据超出最大阀值限制{}(可通过[spring.sqltoy.pageFetchSizeLimit]参数调整),sqlId={},具体语句={}",
					index, sqlToyConfig.getId(), sqlToyConfig.getSql(null));
		}
		return items;
	}

	/**
	 * @todo 对结果进行数据旋转
	 * @param pivotModel
	 * @param labelIndexMap
	 * @param result
	 * @param pivotCategorySet
	 * @return
	 */
	private static List pivotResult(PivotModel pivotModel, LabelIndexModel labelIndexMap, List result,
			List pivotCategorySet) {
		if (result == null || result.isEmpty()) {
			return result;
		}
		// 行列转换
		if (pivotModel.getGroupCols() == null || pivotModel.getCategoryCols().length == 0) {
			return CollectionUtil.convertColToRow(result, null);
		}
		// 参照列，如按年份进行旋转
		Integer[] categoryCols = mappingLabelIndex(pivotModel.getCategoryCols(), labelIndexMap);
		// 旋转列，如按年份进行旋转，则旋转列为：年份下面的合格数量、不合格数量等子分类数据
		Integer[] pivotCols = mappingLabelIndex(pivotModel.getStartEndCols(), labelIndexMap);
		// 分组主键列（以哪几列为基准）
		Integer[] groupCols = mappingLabelIndex(pivotModel.getGroupCols(), labelIndexMap);
		// update 2016-12-13 提取category后进行了排序
		List categoryList = (pivotCategorySet == null) ? extractCategory(result, categoryCols) : pivotCategorySet;
		return CollectionUtil.pivotList(result, categoryList, null, groupCols, categoryCols, pivotCols[0],
				pivotCols[pivotCols.length - 1], pivotModel.getDefaultValue());
	}

	/**
	 * @todo 将label别名换成对应的列编号(select name,sex from xxxTable，name别名对应的列则为0)
	 * @param columnLabels
	 * @param labelIndexMap
	 * @return
	 */
	private static Integer[] mappingLabelIndex(String[] columnLabels, LabelIndexModel labelIndexMap) {
		Integer[] result = new Integer[columnLabels.length];
		for (int i = 0; i < result.length; i++) {
			if (NumberUtil.isInteger(columnLabels[i])) {
				result[i] = Integer.parseInt(columnLabels[i]);
			} else {
				result[i] = labelIndexMap.get(columnLabels[i].toLowerCase());
			}
		}
		return result;
	}

	/**
	 * 针对resultSet label提供小写key map
	 * 
	 * @param labelNames
	 * @return
	 */
	private static HashMap<String, String> labelLowKeyMap(String[] labelNames) {
		HashMap<String, String> lowKeyMap = new HashMap<>();
		for (String label : labelNames) {
			lowKeyMap.put(label.toLowerCase(), label);
		}
		return lowKeyMap;
	}

	/**
	 * @todo 提取出选择的横向分类信息
	 * @param items
	 * @param categoryCols
	 * @return
	 */
	private static List extractCategory(List items, Integer[] categoryCols) {
		List categoryList = new ArrayList();
		Set<String> identitySet = new HashSet<>();
		String tmpStr;
		int categorySize = categoryCols.length;
		Object obj;
		List categoryRow;
		List row;
		for (int i = 0, size = items.size(); i < size; i++) {
			row = (List) items.get(i);
			tmpStr = "";
			categoryRow = new ArrayList();
			for (int j = 0; j < categorySize; j++) {
				obj = row.get(categoryCols[j]);
				categoryRow.add(obj);
				tmpStr = tmpStr.concat(obj == null ? "null" : obj.toString());
			}
			// 不存在
			if (!identitySet.contains(tmpStr)) {
				categoryList.add(categoryRow);
				identitySet.add(tmpStr);
			}
		}
		// 分组排序输出
		if (categoryCols.length > 1) {
			categoryList = sortList(categoryList, 0, 0, categoryList.size() - 1, true);
			for (int i = 1; i < categoryCols.length; i++) {
				categoryList = sortGroupList(categoryList, i - 1, i, true);
			}
		}
		return CollectionUtil.convertColToRow(categoryList, null);
	}

	/**
	 * @todo 分组排序
	 * @param sortList
	 * @param groupCol
	 * @param orderCol
	 * @param ascend
	 * @return
	 */
	private static List sortGroupList(List<List> sortList, int groupCol, int orderCol, boolean ascend) {
		int length = sortList.size();
		// 1:string,2:数字;3:日期
		int start = 0;
		int end;
		Object compareValue = null;
		Object tempObj;
		for (int i = 0; i < length; i++) {
			tempObj = sortList.get(i).get(groupCol);
			if (!tempObj.equals(compareValue)) {
				end = i - 1;
				sortList(sortList, orderCol, start, end, ascend);
				start = i;
				compareValue = tempObj;
			}
			if (i == length - 1) {
				sortList(sortList, orderCol, start, i, ascend);
			}
		}
		return sortList;
	}

	/**
	 * @todo 对二维数据进行排序
	 * @param sortList
	 * @param orderCol
	 * @param start
	 * @param end
	 * @param ascend
	 * @return
	 */
	private static List sortList(List<List> sortList, int orderCol, int start, int end, boolean ascend) {
		if (end <= start) {
			return sortList;
		}
		Object iData;
		Object jData;
		// 1:string,2:数字;3:日期
		boolean lessThen = false;
		String str1, str2;
		int dataType = 1;
		// 是否已经判断过数据类型
		boolean finishedJudgeType = false;
		for (int i = start; i < end; i++) {
			for (int j = i + 1; j < end + 1; j++) {
				iData = sortList.get(i).get(orderCol);
				jData = sortList.get(j).get(orderCol);
				if ((iData == null && jData == null) || (iData != null && jData == null)) {
					lessThen = false;
				} else if (iData == null && jData != null) {
					lessThen = true;
				} else {
					// 首次判断数据类型
					if (!finishedJudgeType) {
						if (iData instanceof java.lang.Number) {
							dataType = 2;
						} else if (iData instanceof java.util.Date) {
							dataType = 3;
						} else if (iData instanceof LocalDate) {
							dataType = 4;
						} else if (iData instanceof LocalDateTime) {
							dataType = 5;
						} else if (iData instanceof LocalTime) {
							dataType = 6;
						}
						finishedJudgeType = true;
					}
					// 字符串
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
				}
				if ((ascend && !lessThen) || (!ascend && lessThen)) {
					List tempList = sortList.get(i);
					sortList.set(i, sortList.get(j));
					sortList.set(j, tempList);
				}
			}
		}
		return sortList;
	}

	/**
	 * @todo 处理Result单行数据
	 * @param dynamicCacheFetch
	 * @param rs
	 * @param labelNames
	 * @param lowKeyLabelNameMap
	 * @param size
	 * @param translateCaches
	 * @param decryptHandler
	 * @param ignoreAllEmptySet
	 * @return
	 * @throws Exception
	 */
	public static List processResultRow(DynamicCacheFetch dynamicCacheFetch, ResultSet rs, String[] labelNames,
			HashMap<String, String> lowKeyLabelNameMap, int size,
			HashMap<String, FieldTranslateCacheHolder> translateCaches, DecryptHandler decryptHandler,
			boolean ignoreAllEmptySet) throws Exception {
		List rowData = new ArrayList();
		Object fieldValue;
		// 单行所有字段结果为null
		boolean allNull = true;
		String label = null;
		int blobSize;
		boolean isLabel = (labelNames == null) ? false : true;
		boolean doTranslate = (translateCaches == null) ? false : true;
		FieldTranslateCacheHolder fieldTranslateHandler;
		for (int i = 0; i < size; i++) {
			if (isLabel) {
				label = labelNames[i];
				fieldValue = rs.getObject(label);
				label = label.toLowerCase();
			} else {
				fieldValue = rs.getObject(i + 1);
			}
			if (null != fieldValue) {
				if (fieldValue instanceof java.sql.Clob) {
					fieldValue = SqlUtil.clobToString((java.sql.Clob) fieldValue);
				} else if (fieldValue instanceof java.sql.Blob) {
					java.sql.Blob blob = (java.sql.Blob) fieldValue;
					blobSize = (int) blob.length();
					if (blobSize > 0) {
						fieldValue = blob.getBytes(1, blobSize);
					} else {
						fieldValue = new byte[0];
					}
				}
				// 解密
				if (decryptHandler != null) {
					fieldValue = decryptHandler.decrypt(label, fieldValue);
				}
				if (doTranslate) {
					fieldTranslateHandler = translateCaches.get(label);
					if (fieldTranslateHandler != null) {
						fieldValue = fieldTranslateHandler.getRSCacheValue(dynamicCacheFetch, rs, lowKeyLabelNameMap,
								fieldValue.toString());
					}
				}
				// 有一个非null
				allNull = false;
			}
			rowData.add(fieldValue);
		}
		// 全null返回null结果，外围判断结果为null则不加入结果集合
		if (allNull && ignoreAllEmptySet) {
			return null;
		}
		return rowData;
	}

	/**
	 * @todo 提取数据旋转对应的sql查询结果
	 * @param sqlToyContext
	 * @param sqlToyConfig
	 * @param queryExecutor
	 * @param conn
	 * @param dbType
	 * @param dialect
	 * @return
	 * @throws Exception
	 */
	public static List getPivotCategory(SqlToyContext sqlToyContext, SqlToyConfig sqlToyConfig,
			QueryExecutor queryExecutor, Connection conn, final Integer dbType, String dialect) throws Exception {
		List resultProcessors = new ArrayList();
		QueryExecutorExtend extend = queryExecutor.getInnerModel();
		if (!sqlToyConfig.getResultProcessor().isEmpty()) {
			resultProcessors.addAll(sqlToyConfig.getResultProcessor());
		}
		// QueryExecutor中扩展的计算
		if (extend != null && !extend.calculators.isEmpty()) {
			resultProcessors.addAll(extend.calculators);
		}
		Object processor;
		for (int i = 0; i < resultProcessors.size(); i++) {
			processor = resultProcessors.get(i);
			// 数据旋转只能存在一个
			if (processor instanceof PivotModel) {
				PivotModel pivotModel = (PivotModel) processor;
				if (pivotModel.getCategorySql() != null) {
					SqlToyConfig pivotSqlConfig = DialectUtils.getUnifyParamsNamedConfig(sqlToyContext,
							sqlToyContext.getSqlToyConfig(pivotModel.getCategorySql(), SqlType.search, "", null),
							queryExecutor, dialect, false);
					SqlToyResult pivotSqlToyResult = SqlConfigParseUtils.processSql(pivotSqlConfig.getSql(dialect),
							extend.getParamsName(), extend.getParamsValue(sqlToyContext, pivotSqlConfig), dialect);
					// 增加sql执行拦截器 update 2022-9-10
					pivotSqlToyResult = DialectUtils.doInterceptors(sqlToyContext, pivotSqlConfig, OperateType.search,
							pivotSqlToyResult, null, dbType);
					List pivotCategory = SqlUtil.findByJdbcQuery(sqlToyContext.getTypeHandler(),
							pivotSqlToyResult.getSql(), pivotSqlToyResult.getParamsValue(), null, null, null, conn,
							dbType, sqlToyConfig.isIgnoreEmpty(), null, SqlToyConstants.FETCH_SIZE, -1);
					// 行转列返回
					return CollectionUtil.convertColToRow(pivotCategory, null);
				}
			}
		}
		return null;
	}

	/**
	 * @todo 对查询结果进行计算处理:字段脱敏、格式化、数据旋转、同步环比、分组汇总等
	 * @param desensitizeProvider
	 * @param sqlToyConfig
	 * @param dataSetResult
	 * @param pivotCategorySet
	 * @param extend
	 * @return
	 */
	public static boolean calculate(DesensitizeProvider desensitizeProvider, SqlToyConfig sqlToyConfig,
			DataSetResult dataSetResult, List pivotCategorySet, QueryExecutorExtend extend) {
		List items = dataSetResult.getRows();
		// 数据为空直接跳出处理
		if (items == null || items.isEmpty()) {
			return false;
		}
		// 是否会导致列名称和数据完全不对应,导致无法映射到pojo或map
		boolean changedCols = false;
		List<SecureMask> secureMasks = sqlToyConfig.getSecureMasks();
		List<FormatModel> formatModels = sqlToyConfig.getFormatModels();
		List resultProcessors = new ArrayList();
		if (!sqlToyConfig.getResultProcessor().isEmpty()) {
			resultProcessors.addAll(sqlToyConfig.getResultProcessor());
		}
		if (extend != null && !extend.calculators.isEmpty()) {
			resultProcessors.addAll(extend.calculators);
		}
		// 整理列名称跟index的对照map
		LabelIndexModel labelIndexMap = null;
		if (!secureMasks.isEmpty() || !formatModels.isEmpty()
				|| (extend != null && (!extend.secureMask.isEmpty() || !extend.colsFormat.isEmpty()))
				|| !resultProcessors.isEmpty()) {
			labelIndexMap = wrapLabelIndexMap(dataSetResult.getLabelNames());
		}
		// 字段脱敏
		if (!secureMasks.isEmpty()) {
			secureMask(desensitizeProvider, items, secureMasks.iterator(), labelIndexMap);
		}

		// 自动格式化
		if (!formatModels.isEmpty()) {
			formatColumn(items, formatModels.iterator(), labelIndexMap);
		}
		// 扩展脱敏和格式化处理
		if (extend != null) {
			if (!extend.secureMask.isEmpty()) {
				secureMask(desensitizeProvider, items, extend.secureMask.values().iterator(), labelIndexMap);
			}
			if (!extend.colsFormat.isEmpty()) {
				formatColumn(items, extend.colsFormat.values().iterator(), labelIndexMap);
			}
		}
		// 计算
		if (!resultProcessors.isEmpty()) {
			Object processor;
			for (int i = 0; i < resultProcessors.size(); i++) {
				processor = resultProcessors.get(i);
				// 数据旋转(行转列)
				if (processor instanceof PivotModel) {
					items = pivotResult((PivotModel) processor, labelIndexMap, items, pivotCategorySet);
					changedCols = true;
				} // 列转行
				else if (processor instanceof UnpivotModel) {
					items = UnpivotList.process((UnpivotModel) processor, dataSetResult, labelIndexMap, items);
				} else if (processor instanceof SummaryModel) {
					// 数据汇总合计
					GroupSummary.process((SummaryModel) processor, labelIndexMap, items);
				} else if (processor instanceof ColsChainRelativeModel) {
					// 列数据环比
					ColsChainRelative.process((ColsChainRelativeModel) processor, labelIndexMap, items);
					changedCols = true;
				} else if (processor instanceof RowsChainRelativeModel) {
					RowsChainRelativeModel rowChainModel = (RowsChainRelativeModel) processor;
					// 行数据环比
					RowsChainRelative.process(rowChainModel, labelIndexMap, items);
					// 环比值作为新的列插入，则改变了列
					if (rowChainModel.isInsert()) {
						changedCols = true;
					}
				} else if (processor instanceof ReverseModel) {
					// 数据反序
					ReverseList.process((ReverseModel) processor, labelIndexMap, items);
				} else if (processor instanceof TreeSortModel) {
					// 树形结构排序组织
					TreeDataSort.process((TreeSortModel) processor, labelIndexMap, items);
				}
			}
			dataSetResult.setRows(items);
		}
		return changedCols;
	}

	/**
	 * @TODO 建立列名称跟列index的对应关系
	 * @param fields
	 * @return
	 */
	private static LabelIndexModel wrapLabelIndexMap(String[] fields) {
		LabelIndexModel result = new LabelIndexModel();
		if (fields != null && fields.length > 0) {
			String realLabelName;
			int index;
			for (int i = 0, n = fields.length; i < n; i++) {
				realLabelName = fields[i].toLowerCase();
				index = realLabelName.indexOf(":");
				if (index != -1) {
					realLabelName = realLabelName.substring(index + 1).trim();
				}
				result.put(realLabelName, i);
			}
		}
		return result;
	}

	/**
	 * @todo 根据查询结果的类型，构造相应对象集合(增加map形式的结果返回机制)
	 * @param sqlToyContext
	 * @param queryResultRows
	 * @param labelNames
	 * @param resultType
	 * @param changedCols
	 * @param humpMapLabel
	 * @param hiberarchy        返回结果是否按层次化对象封装
	 * @param hiberarchyClasses
	 * @param fieldsMap
	 * @return
	 * @throws Exception
	 */
	public static List wrapQueryResult(SqlToyContext sqlToyContext, List queryResultRows, String[] labelNames,
			Class resultType, boolean changedCols, Boolean humpMapLabel, boolean hiberarchy, Class[] hiberarchyClasses,
			Map<Class, IgnoreKeyCaseMap<String, String>> fieldsMap) throws Exception {
		if (queryResultRows == null || queryResultRows.isEmpty() || resultType == null) {
			return queryResultRows;
		}
		// 类型为null就默认返回二维List
		if (resultType.equals(List.class) || resultType.equals(ArrayList.class) || resultType.equals(Collection.class)
				|| BeanUtil.isBaseDataType(resultType)) {
			// update 2022-4-22
			// 如果查询单列数据，且返回结果类型为原始类型，则切取单列数据
			if (BeanUtil.isBaseDataType(resultType) && labelNames != null && labelNames.length == 1) {
				return getFirstColumn(queryResultRows, resultType);
			}
			return queryResultRows;
		}
		// 返回数组类型
		if (Array.class.equals(resultType)) {
			return CollectionUtil.innerListToArray(queryResultRows);
		}
		// 已经存在pivot、unpivot、列环比计算等
		if (changedCols) {
			logger.warn("查询中存在类似pivot、列同比环比计算导致结果'列'数不固定，因此不支持转map或VO对象!");
			SqlExecuteStat.debug("映射结果类型错误", "查询中存在类似pivot、列同比环比计算导致结果'列'数不固定，因此不支持转map或VO对象!");
		}
		if (null == labelNames) {
			throw new DataAccessException(
					"wrapQueryResult封装数据到[" + resultType.getTypeName() + "]时数据labelNames为null,无法提供属性名称映射!");
		}
		// 如果结果类型是hashMap
		if (Map.class.isAssignableFrom(resultType)) {
			int width = labelNames.length;
			String[] realLabels = labelNames;
			boolean isHumpLabel = (humpMapLabel == null ? sqlToyContext.isHumpMapResultTypeLabel() : humpMapLabel);
			// 驼峰处理
			if (isHumpLabel) {
				realLabels = humpFieldNames(labelNames, null);
			}
			List result = new ArrayList();
			List rowList;
			boolean isMap = resultType.equals(Map.class);
			boolean isConMap = resultType.equals(ConcurrentMap.class);
			for (int i = 0, n = queryResultRows.size(); i < n; i++) {
				rowList = (List) queryResultRows.get(i);
				Map rowMap;
				if (isMap) {
					rowMap = new HashMap();
				} else if (isConMap) {
					rowMap = new ConcurrentHashMap();
				} else {
					rowMap = (Map) resultType.getDeclaredConstructor().newInstance();
				}
				for (int j = 0; j < width; j++) {
					rowMap.put(realLabels[j], rowList.get(j));
				}
				result.add(rowMap);
			}
			return result;
		}
		HashMap<String, String> columnFieldMap = null;
		EntityMeta entityMeta = null;
		if (sqlToyContext.isEntity(resultType)) {
			entityMeta = sqlToyContext.getEntityMeta(resultType);
			columnFieldMap = entityMeta.getColumnFieldMap();
		}
		boolean hasCascade = false;
		List<TableCascadeModel> cascadeModel = null;
		if (hiberarchy) {
			if (entityMeta != null) {
				cascadeModel = entityMeta.getCascadeModels();
			} else {
				cascadeModel = BeanUtil.getCascadeModels(resultType);
			}
			if (cascadeModel != null && !cascadeModel.isEmpty()) {
				hasCascade = true;
			}
		}
		List result = null;
		// 非层次结构
		if (!hasCascade) {
			// 封装成VO对象形式
			result = BeanUtil.reflectListToBean(sqlToyContext.getTypeHandler(), queryResultRows,
					convertRealProps(wrapMapFields(labelNames, fieldsMap, resultType), columnFieldMap), resultType);
			// update 2021-11-16 支持VO或POJO 属性上@Translate注解,进行缓存翻译
			wrapResultTranslate(sqlToyContext, result, resultType);
		} else {
			// 内部完成了wrapResultTranslate行为
			result = hiberarchySet(sqlToyContext, entityMeta, columnFieldMap, queryResultRows, labelNames, resultType,
					cascadeModel, hiberarchyClasses, fieldsMap);
		}
		return result;
	}

	/**
	 * @TODO 提取二维集合第一列数据转换类型变成一维List返回
	 * @param <T>
	 * @param rows
	 * @param classType
	 * @return
	 */
	public static <T> List<T> getFirstColumn(List rows, Class<T> classType) {
		List<T> result = new ArrayList<T>();
		if (rows == null || rows.isEmpty()) {
			return result;
		}
		Object cell;
		String typeName = classType.getTypeName();
		int typeValue = DataType.getType(classType);
		try {
			for (Object row : rows) {
				cell = ((List) row).get(0);
				result.add((T) BeanUtil.convertType(cell, typeValue, typeName));
			}
			return result;
		} catch (Exception e) {
			throw new DataAccessException("切取单列查询结果进行类型转换时发生异常!" + e.getMessage());
		}
	}

	/**
	 * @TODO 解决DTO或POJO上存在@aliasName将sql字段名称跟类属性名称建立的对应关系(非简单的去除下划线骆驼命名规则)
	 * @param labelNames
	 * @param colFieldMap
	 * @return
	 */
	private static String[] convertRealProps(String[] labelNames, HashMap<String, String> colFieldMap) {
		String[] result = labelNames.clone();
		if (colFieldMap != null && !colFieldMap.isEmpty()) {
			String key;
			for (int i = 0; i < result.length; i++) {
				key = result[i].toLowerCase();
				if (colFieldMap.containsKey(key)) {
					result[i] = colFieldMap.get(key);
				}
			}
		}
		return result;
	}

	/**
	 * @TODO 将集合数据反射到java对象并建立层次关系
	 * @param sqlToyContext
	 * @param entityMeta
	 * @param columnFieldMap
	 * @param queryResultRows
	 * @param labelNames
	 * @param resultType
	 * @param cascadeModels
	 * @param hiberarchyClasses
	 * @param fieldsMap
	 * @return
	 * @throws Exception
	 */
	private static List hiberarchySet(SqlToyContext sqlToyContext, EntityMeta entityMeta,
			HashMap<String, String> columnFieldMap, List queryResultRows, String[] labelNames, Class resultType,
			List<TableCascadeModel> cascadeModels, Class[] hiberarchyClasses,
			Map<Class, IgnoreKeyCaseMap<String, String>> fieldsMap) throws Exception {
		IgnoreKeyCaseMap<String, Integer> labelIndexs = new IgnoreKeyCaseMap<String, Integer>();
		int index = 0;
		// 去除下划线，便于跟对象属性匹配
		for (String label : labelNames) {
			labelIndexs.put(label, index);
			labelIndexs.put(label.replace("_", ""), index);
			index++;
		}
		// 获取oneToMany级联
		TableCascadeModel oneToMany = getOneToManyCascade(cascadeModels, hiberarchyClasses);
		int[] oneToManyGroupColIndexs = null;
		// 分组的master数据
		List masterData;
		LinkedHashMap<String, List> groupListMap = null;
		Iterator<List> groupListIter;
		// 存在oneToMany 则将数据进行分组
		if (oneToMany != null) {
			oneToManyGroupColIndexs = getGroupColIndexs(oneToMany, labelIndexs);
			groupListMap = hashGroupList(queryResultRows, oneToManyGroupColIndexs);
			// 提取每组的第一条数据作为master数据
			groupListIter = groupListMap.values().iterator();
			masterData = new ArrayList();
			while (groupListIter.hasNext()) {
				masterData.add(groupListIter.next().get(0));
			}
		} else {
			masterData = queryResultRows;
		}
		// 构造主对象集合
		List result = BeanUtil.reflectListToBean(sqlToyContext.getTypeHandler(), masterData,
				convertRealProps(wrapMapFields(labelNames, fieldsMap, resultType), columnFieldMap), resultType);
		// add 2024-8-7 (用户:一颗开心果反馈)在一个查询封装成对象级联平铺模式，主对象上未处理类上@Translate缓存翻译注解
		wrapResultTranslate(sqlToyContext, result, resultType);
		List<List> oneToOnes = new ArrayList();
		List<String> oneToOneProps = new ArrayList<String>();
		List<String> oneToOneNotNullField = new ArrayList<String>();
		boolean hasCascade;
		String[] realLabelNames;
		for (TableCascadeModel cascade : cascadeModels) {
			// oneToOne模式
			if (cascade.getCascadeType() == 2) {
				hasCascade = false;
				// 首先依据指定的层次级联对象
				if (hiberarchyClasses != null) {
					for (Class hiberarchyClass : hiberarchyClasses) {
						if (hiberarchyClass.equals(cascade.getMappedType())) {
							hasCascade = true;
							break;
						}
					}
				} else {
					hasCascade = true;
				}
				// 将多个oneToOne的数据批量构造
				if (hasCascade) {
					realLabelNames = labelNames.clone();
					// 主对象字段属性转化为级联对象属性
					if (cascade.getMappedFields() != null && cascade.getMappedFields().length > 0) {
						int groupSize = cascade.getFields().length;
						int[] colIndexs = getGroupColIndexs(cascade, labelIndexs);
						for (int i = 0; i < groupSize; i++) {
							realLabelNames[colIndexs[i]] = cascade.getMappedFields()[i];
						}
					}
					columnFieldMap = null;
					if (entityMeta != null && sqlToyContext.isEntity(cascade.getMappedType())) {
						columnFieldMap = sqlToyContext.getEntityMeta(cascade.getMappedType()).getColumnFieldMap();
					}
					List oneToOneList = BeanUtil.reflectListToBean(sqlToyContext.getTypeHandler(), masterData,
							convertRealProps(wrapMapFields(realLabelNames, fieldsMap, cascade.getMappedType()),
									columnFieldMap),
							cascade.getMappedType());
					// 处理OneToOne子类上@Translate注解进行缓存翻译
					wrapResultTranslate(sqlToyContext, oneToOneList, cascade.getMappedType());
					oneToOnes.add(oneToOneList);
					oneToOneProps.add(cascade.getProperty());
					oneToOneNotNullField.add(cascade.getNotNullField());
				}
			}
		}

		Object masterBean;
		// 循环将oneToOne 的一一通过反射赋值到主对象属性上
		if (!oneToOneProps.isEmpty()) {
			int oneToOneSize = oneToOneProps.size();
			String notNullField;
			Object oneToOneBean;
			String property;
			for (int i = 0, n = result.size(); i < n; i++) {
				masterBean = result.get(i);
				for (int j = 0; j < oneToOneSize; j++) {
					property = oneToOneProps.get(j);
					notNullField = oneToOneNotNullField.get(j);
					oneToOneBean = oneToOnes.get(j).get(i);
					// 判断非空字段的值，值为null则表示级联查询数据为null，无需设置
					if (notNullField != null) {
						if (null != BeanUtil.getProperty(oneToOneBean, notNullField)) {
							BeanUtil.setProperty(masterBean, property, oneToOneBean);
						}
					} else {
						BeanUtil.setProperty(masterBean, property, oneToOneBean);
					}
				}
			}
		}

		// 处理oneToMany
		if (oneToMany != null) {
			realLabelNames = labelNames.clone();
			// 变化级联子对象的属性
			if (oneToMany.getMappedFields() != null && oneToMany.getMappedFields().length > 0) {
				for (int i = 0; i < oneToManyGroupColIndexs.length; i++) {
					realLabelNames[oneToManyGroupColIndexs[i]] = oneToMany.getMappedFields()[i];
				}
			}
			Class oneToManyClass = oneToMany.getMappedType();
			columnFieldMap = null;
			if (entityMeta != null && sqlToyContext.isEntity(oneToManyClass)) {
				columnFieldMap = sqlToyContext.getEntityMeta(oneToManyClass).getColumnFieldMap();
			}
			List item;
			String property = oneToMany.getProperty();
			String notNullField = oneToMany.getNotNullField();
			// 循环分组Map
			groupListIter = groupListMap.values().iterator();
			index = 0;
			while (groupListIter.hasNext()) {
				masterBean = result.get(index);
				item = BeanUtil.reflectListToBean(sqlToyContext.getTypeHandler(), groupListIter.next(),
						convertRealProps(wrapMapFields(realLabelNames, fieldsMap, oneToManyClass), columnFieldMap),
						oneToManyClass);
				// 移除属性值为null的空对象记录
				if (notNullField != null) {
					for (int k = 0; k < item.size(); k++) {
						if (BeanUtil.getProperty(item.get(k), notNullField) == null) {
							item.remove(k);
							k--;
						}
					}
				}
				if (!item.isEmpty()) {
					// 处理类上@Translate注解进行缓存翻译
					wrapResultTranslate(sqlToyContext, item, oneToManyClass);
					// 将子对象集合写到主对象属性上
					BeanUtil.setProperty(masterBean, property, item);
				}
				index++;
			}
		}
		return result;
	}

	/**
	 * @TODO 提取单个级联模型的分组字段对应的查询结果列
	 * @param cascade
	 * @param labelIndexs
	 * @return
	 */
	private static int[] getGroupColIndexs(TableCascadeModel cascade, IgnoreKeyCaseMap<String, Integer> labelIndexs) {
		if (cascade == null) {
			return null;
		}
		// 获得所有层次关系的分组字段
		String[] groupFields = cascade.getFields();
		int groupSize = groupFields.length;
		int[] colIndexs = new int[groupSize];
		String cascadeType = (cascade.getCascadeType() == 1) ? "OneToMany" : "OneToOne";
		for (int i = 0; i < groupSize; i++) {
			if (labelIndexs.containsKey(groupFields[i])) {
				colIndexs[i] = labelIndexs.get(groupFields[i]);
			} else {
				throw new DataAccessException(
						"层次结构封装操作,查询结果中未包含" + cascadeType + "的分组属性(对象属性名称,正常不包含下划线):" + groupFields[i] + " 对应的值!");
			}
		}
		return colIndexs;
	}

	/**
	 * @TODO 判断并获取oneToMany的级联配置
	 * @param cascadeModels
	 * @param hiberarchyClasses
	 * @return
	 */
	public static TableCascadeModel getOneToManyCascade(List<TableCascadeModel> cascadeModels,
			Class[] hiberarchyClasses) {
		TableCascadeModel oneToMany = null;
		int oneToManySize = 0;
		for (TableCascadeModel cascade : cascadeModels) {
			// oneToMany模式
			if (cascade.getCascadeType() == 1) {
				// 指定了级联对象
				if (hiberarchyClasses != null) {
					for (Class hiberarchyClass : hiberarchyClasses) {
						if (hiberarchyClass.equals(cascade.getMappedType())) {
							oneToMany = cascade;
							break;
						}
					}
				} else {
					// 不指定则以第一个为准
					if (oneToMany == null) {
						oneToMany = cascade;
					}
					oneToManySize++;
				}
			}
		}
		if (oneToManySize > 1 && hiberarchyClasses == null) {
			throw new IllegalArgumentException("返回依照层次结构结果时，存在多个oneToMany映射关系，必须要指明hiberarchyClasses!");
		}
		return oneToMany;
	}

	/**
	 * @TODO 针对具体映射对象设置sql查询的label对应的对象属性
	 * @param labelNames
	 * @param resultTypeFieldsMap
	 * @param resultType
	 * @return
	 */
	private static String[] wrapMapFields(String[] labelNames,
			Map<Class, IgnoreKeyCaseMap<String, String>> resultTypeFieldsMap, Class resultType) {
		if (resultTypeFieldsMap == null || resultTypeFieldsMap.isEmpty()) {
			return labelNames.clone();
		}
		// 指定sql查询出的label对应dto对象属性名称的映射关系
		IgnoreKeyCaseMap<String, String> fieldsMap = resultTypeFieldsMap.get(resultType);
		if (fieldsMap == null || fieldsMap.isEmpty()) {
			return labelNames.clone();
		}
		String[] result = labelNames.clone();
		String fieldName;
		int size = result.length;
		for (int i = 0; i < size; i++) {
			fieldName = fieldsMap.get(result[i]);
			// 存在映射
			if (fieldName != null) {
				// 将其它位置label名称跟映射结果一致的全部改名(确保不被映射)
				for (int j = 0; j < size; j++) {
					if (result[j].equalsIgnoreCase(fieldName)
							|| result[j].replace("_", "").equalsIgnoreCase(fieldName)) {
						result[j] = result[j] + "SqlToyIgnoreField";
					}
				}
				// 设置当前位置属性名为映射属性
				result[i] = fieldName;
			}
		}
		return result;
	}

	/**
	 * @TODO 根据分组字段将集合分组
	 * @param queryResultRows
	 * @param groupIndexes
	 * @return
	 */
	private static LinkedHashMap<String, List> hashGroupList(List queryResultRows, int[] groupIndexes) {
		LinkedHashMap<String, List> groupListMap = new LinkedHashMap<String, List>();
		List row;
		String key = "";
		List groupList;
		for (int i = 0; i < queryResultRows.size(); i++) {
			row = (List) queryResultRows.get(i);
			key = "";
			for (int j = 0; j < groupIndexes.length; j++) {
				key = key + "," + row.get(groupIndexes[j]);
			}
			groupList = groupListMap.get(key);
			if (groupList == null) {
				groupList = new ArrayList();
			}
			groupList.add(row);
			groupListMap.put(key, groupList);
		}
		return groupListMap;
	}

	/**
	 * @todo 加工字段名称，将数据库sql查询的columnName转成对应对象的属性名称(去除下划线)
	 * @param labelNames
	 * @param colFieldMap
	 * @return
	 */
	public static String[] humpFieldNames(String[] labelNames, HashMap<String, String> colFieldMap) {
		if (labelNames == null) {
			return null;
		}
		String[] result = new String[labelNames.length];
		if (colFieldMap == null) {
			for (int i = 0, n = labelNames.length; i < n; i++) {
				result[i] = StringUtil.toHumpStr(labelNames[i], false);
			}
		} else {
			for (int i = 0, n = labelNames.length; i < n; i++) {
				result[i] = colFieldMap.get(labelNames[i].toLowerCase());
				if (result[i] == null) {
					result[i] = StringUtil.toHumpStr(labelNames[i], false);
				}
			}
		}
		return result;
	}

	/**
	 * @todo 警告性日志记录,凡是单次获取超过一定规模数据的操作记录日志
	 * @param sqlToyConfig
	 * @param totalCount
	 */
	private static void warnLog(SqlToyConfig sqlToyConfig, int totalCount) {
		logger.warn("Large Result:totalCount={},sqlId={},sql={}", totalCount, sqlToyConfig.getId(),
				sqlToyConfig.getSql(null));
	}

	/**
	 * @TODO 组织因做缓存翻译、link、日期和数字格式化改变类型为VARCHAR的列
	 * @param sqlToyConfig
	 * @return
	 */
	private static Set<String> getStringColumns(SqlToyConfig sqlToyConfig) {
		Set<String> strSet = new HashSet<String>();
		// 本身key是小写
		if (sqlToyConfig.getTranslateMap() != null && !sqlToyConfig.getTranslateMap().isEmpty()) {
			strSet.addAll(sqlToyConfig.getTranslateMap().keySet());
		}
		if (sqlToyConfig.getLinkModel() != null) {
			for (String col : sqlToyConfig.getLinkModel().getColumns()) {
				strSet.add(col.toLowerCase());
			}
		}
		// column在解析时已经是小写
		if (sqlToyConfig.getFormatModels() != null && !sqlToyConfig.getFormatModels().isEmpty()) {
			for (FormatModel fmt : sqlToyConfig.getFormatModels()) {
				strSet.add(fmt.getColumn());
			}
		}
		return strSet;
	}

	/**
	 * @TODO 对返回POJO(或DTO)含@Translate 配置的结果进行缓存翻译处理，通过key属性的值翻译成名称反射到当前名称属性上
	 * @param sqlToyContext
	 * @param result
	 * @param resultType
	 */
	public static void wrapResultTranslate(SqlToyContext sqlToyContext, Object result, Class resultType) {
		HashMap<String, FieldTranslate> translateConfig = TranslateConfigParse.getClassTranslates(resultType);
		if (result == null || translateConfig == null || translateConfig.isEmpty()) {
			return;
		}
		List voList;
		if (result instanceof List) {
			voList = (List) result;
		} else {
			voList = new ArrayList();
			voList.add(result);
		}
		if (voList.isEmpty()) {
			return;
		}
		// 获取缓存数据
		HashMap<String, FieldTranslateCacheHolder> fieldTranslateHandlers = sqlToyContext.getTranslateManager()
				.getTranslates(translateConfig);
		DynamicCacheFetch dynamicCacheFetch = sqlToyContext.getDynamicCacheFetch();
		for (int i = 0, n = voList.size(); i < n; i++) {
			wrapBeanTranslate(dynamicCacheFetch, fieldTranslateHandlers, voList.get(i));
		}
	}

	/**
	 * @TODO 处理基于pojo或dto上@Translate注解，进行实际缓存调用给属性赋值
	 * @param dynamicCacheFetch
	 * @param fieldTranslateHandlers
	 * @param item
	 */
	private static void wrapBeanTranslate(DynamicCacheFetch dynamicCacheFetch,
			HashMap<String, FieldTranslateCacheHolder> fieldTranslateHandlers, Object item) {
		fieldTranslateHandlers.forEach((fieldName, fieldTranslateHandler) -> {
			Object srcFieldValue = BeanUtil.getProperty(item, fieldTranslateHandler.getKeyField());
			Object fieldValue = BeanUtil.getProperty(item, fieldName);
			if (srcFieldValue != null && !"".equals(srcFieldValue.toString()) && fieldValue == null) {
				BeanUtil.setProperty(item, fieldName,
						fieldTranslateHandler.getBeanCacheValue(dynamicCacheFetch, item, srcFieldValue.toString()));
			}
		});
	}
}
