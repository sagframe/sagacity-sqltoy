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
import java.util.Objects;
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
import org.sagacity.sqltoy.model.JdbcTypes;
import org.sagacity.sqltoy.model.QueryExecutor;
import org.sagacity.sqltoy.model.QueryResult;
import org.sagacity.sqltoy.model.inner.DataSetResult;
import org.sagacity.sqltoy.model.inner.QueryExecutorExtend;
import org.sagacity.sqltoy.plugins.TypeHandler;
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
import org.sagacity.sqltoy.translate.model.BatchDynamicCache;
import org.sagacity.sqltoy.translate.model.DynamicCacheHolder;
import org.sagacity.sqltoy.utils.DataSourceUtils.DBType;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * @project sagacity-sqltoy
 * @description 提供查询结果的缓存key-value提取以及结果分组link功能
 * @author zhongxuchen
 * @version v1.0,Date:2013-04-18
 * @modify Date:2016-12-13 对行转列分类参照集合进行了排序
 * @modify Date:2020-05-29 将脱敏和格式化转到calculate中,便于elastic和mongo查询提供同样的功能
 * @modify Date:2024-03-15 由俊华反馈，优化hiberarchySet支持逻辑业务主子关系，如单据中的创建人，审批人分别映射员工表
 * @modify Date:2024-08-08 修复hiberarchySet方法中遗漏对主对象集合进行缓存翻译的缺陷
 * @modify Date:2025-03-31 修复查询结果做link操作,结果为List、Set、Array的处理遗漏最后一条的处理缺陷
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
	 * 处理sql查询时的结果集,当没有反调或voClass反射处理时以数组方式返回resultSet的数据
	 * 
	 * @param sqlToyContext       sqltoy上下文
	 * @param sqlToyConfig        sql配置信息(含解密列、翻译、link、脱敏、格式化等配置)
	 * @param conn                数据库连接对象
	 * @param rs                  ResultSet结果集对象
	 * @param queryExecutorExtend 查询扩展模型(含行处理回调、扩展脱敏格式化等)
	 * @param updateRowHandler    行数据更新回调(可更新结果集)，null表示非修改操作
	 * @param decryptHandler      字段解密处理器，非null时对指定列做解密处理
	 * @param startColIndex       起始提取列下标(从0开始)
	 * @return 包含列名、列类型、行数据和记录总数的查询结果对象
	 * @throws Exception
	 */
	public static QueryResult processResultSet(final Integer dbType, final SqlToyContext sqlToyContext,
			final SqlToyConfig sqlToyConfig, Connection conn, ResultSet rs, QueryExecutorExtend queryExecutorExtend,
			UpdateRowHandler updateRowHandler, DecryptHandler decryptHandler, int startColIndex) throws Exception {
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
			IgnoreCaseSet decryptColumns = new IgnoreCaseSet();
			// 这里注意，要保留，主要是load、loadAll等对象查询时POJO注解有解密配置
			if (decryptHandler != null && decryptHandler.getColumns() != null) {
				decryptColumns.addAll(decryptHandler.getColumns());
			}
			if (sqlToyConfig.getDecryptColumns() != null) {
				decryptColumns.addAll(sqlToyConfig.getDecryptColumns());
			}
			// update 2025-12-27 增加代码中指定解密的列
			if (queryExecutorExtend != null && queryExecutorExtend.decryptColumns != null) {
				decryptColumns.addAll(queryExecutorExtend.decryptColumns);
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
			// 列真实类型名(与labelNames同下标对齐,供byte[]扩展类型归一判定;
			// 不受strTypeCols对labelTypes的VARCHAR覆写影响,否则json/空间列会跳过归一)
			String[] columnTypeNames = new String[columnCnt - startColIndex];
			HashMap<String, Integer> labelIndexMap = new HashMap<String, Integer>();
			String labeNameLow;
			String colLabelUpperOrLower = sqlToyContext.getColumnLabelUpperOrLower();
			// 元数据提取到循环外,避免每列重复调用getMetaData()
			java.sql.ResultSetMetaData resultSetMetaData = rs.getMetaData();
			for (int i = startColIndex; i < columnCnt; i++) {
				labelNames[index] = resultSetMetaData.getColumnLabel(i + 1);
				labeNameLow = labelNames[index].toLowerCase(Locale.ROOT);
				if ("lower".equals(colLabelUpperOrLower)) {
					labelNames[index] = labelNames[index].toLowerCase(Locale.ROOT);
				} else if ("upper".equals(colLabelUpperOrLower)) {
					labelNames[index] = labelNames[index].toUpperCase(Locale.ROOT);
				}
				labelIndexMap.put(labeNameLow, index);
				labelTypes[index] = resultSetMetaData.getColumnTypeName(i + 1);
				columnTypeNames[index] = labelTypes[index];
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
				result.setRows(getResultSet(dbType, queryExecutorExtend, sqlToyConfig, sqlToyContext, conn, rs,
						updateRowHandler, realDecryptHandler, columnCnt, labelIndexMap, labelNames, startColIndex,
						columnTypeNames));
			} // update 2019-09-11 此处增加数组溢出异常是因为经常有开发设置缓存cache-indexs时写错误，为了增加错误提示信息的友好性增加此处理
			catch (Exception oie) {
				logger.error("sql={} exception occurred while extracting the result:{}!", sqlToyConfig.getId(),
						oie.getMessage());
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
	 * 基于游标直接消费每行数据
	 * 
	 * @param sqlToyContext       sqltoy上下文
	 * @param extend              查询扩展模型(含扩展脱敏格式化等)
	 * @param sqlToyConfig        sql配置信息
	 * @param conn                数据库连接对象
	 * @param rs                  ResultSet结果集对象
	 * @param streamResultHandler 流式结果处理回调，逐行消费数据
	 * @param resultType          每行数据的目标类型(List/数组/Map/VO)，null按List行处理
	 * @param humpMapLabel        Map结果key是否驼峰处理，null时取sqltoy全局配置
	 * @param fieldsMap           sql查询label与对象属性的映射关系
	 * @throws Exception
	 */
	public static void consumeResult(final Integer dbType, final SqlToyContext sqlToyContext,
			final QueryExecutorExtend extend, final SqlToyConfig sqlToyConfig, Connection conn, ResultSet rs,
			final StreamResultHandler streamResultHandler, Class resultType, Boolean humpMapLabel,
			Map<Class, IgnoreKeyCaseMap<String, String>> fieldsMap) throws Exception {
		// 重新组合解密字段(entityMeta中的和sql自定义的合并)
		IgnoreCaseSet decryptColumns = new IgnoreCaseSet();
		if (sqlToyConfig.getDecryptColumns() != null) {
			decryptColumns.addAll(sqlToyConfig.getDecryptColumns());
		}
		// update 2025-12-27 增加代码中指定解密的列
		if (extend != null && extend.decryptColumns != null) {
			decryptColumns.addAll(extend.decryptColumns);
		}
		DecryptHandler realDecryptHandler = null;
		if (decryptColumns != null && !decryptColumns.isEmpty()) {
			realDecryptHandler = new DecryptHandler(sqlToyContext.getFieldsSecureProvider(), decryptColumns);
		}
		// 取得字段列数
		// update 2026-9-8 元数据对象提取到列循环外(驱动getMetaData返回缓存对象仍有一次
		// 调用开销,循环内每列重复调用属无谓消耗)
		java.sql.ResultSetMetaData resultSetMD = rs.getMetaData();
		int columnSize = resultSetMD.getColumnCount();
		// 类型转成string的列
		Set<String> strTypeCols = getStringColumns(sqlToyConfig);
		boolean hasToStrCols = !strTypeCols.isEmpty();
		String[] labelNames = new String[columnSize];
		String[] labelTypes = new String[columnSize];
		// 列真实类型名(与labelNames同下标对齐,供byte[]扩展类型归一判定;
		// 不受strTypeCols对labelTypes的VARCHAR覆写影响,否则json/空间列会跳过归一)
		String[] columnTypeNames = new String[columnSize];
		String labeNameLow;
		// 字段名称统一转大写或小写,默认为default,即不做任何处理
		String colLabelUpperOrLower = sqlToyContext.getColumnLabelUpperOrLower();
		int index = 0;
		for (int i = 0; i < columnSize; i++) {
			labelNames[index] = resultSetMD.getColumnLabel(i + 1);
			labeNameLow = labelNames[index].toLowerCase(Locale.ROOT);
			if ("lower".equals(colLabelUpperOrLower)) {
				labelNames[index] = labelNames[index].toLowerCase(Locale.ROOT);
			} else if ("upper".equals(colLabelUpperOrLower)) {
				labelNames[index] = labelNames[index].toUpperCase(Locale.ROOT);
			}
			labelTypes[index] = resultSetMD.getColumnTypeName(i + 1);
			columnTypeNames[index] = labelTypes[index];
			// 类型因缓存翻译、格式化转为string
			if (hasToStrCols && strTypeCols.contains(labeNameLow)) {
				labelTypes[index] = "VARCHAR";
			}
			index++;
		}
		HashMap<String, FieldTranslate> translateMap = sqlToyConfig.getTranslateMap();
		// 判断是否有缓存翻译器定义
		boolean hasTranslate = !translateMap.isEmpty();
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
		// update 2026-9-8 VO映射的列jdbcTypes标记(null表示非VO场景,内部回退OTHER)
		int[] columnJdbcTypes = null;
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
					throw new IllegalArgumentException("resultType [" + resultType.getName()
							+ "] is an abstract class or interface, illegal argument!");
				}
				if (sqlToyContext.isEntity(resultType)) {
					EntityMeta entityMeta = sqlToyContext.getEntityMeta(resultType);
					columnFieldMap = entityMeta.getColumnFieldMap();
				}
				realProps = convertRealProps(wrapMapFields(labelNames, fieldsMap, resultType), columnFieldMap);
				realMethods = BeanUtil.matchSetMethods(resultType, realProps);
				// update 2026-9-8 构建列jdbcTypes标记(字段注解JSON/JSONB标注优先,查询列类型名
				// 兜底),供reflectRowToBean按JSON等扩展类型做jdbc值到POJO的转换(原固定OTHER导致
				// json列到对象字段的反序列化不触发)
				Map<String, Integer> fieldJdbcTypeMap = BeanUtil.getClassFieldMap(resultType, realProps);
				columnJdbcTypes = new int[columnSize];
				for (int i = 0; i < columnSize; i++) {
					String propLow = (realProps[i] == null) ? null : realProps[i].toLowerCase(Locale.ROOT);
					if (propLow != null && fieldJdbcTypeMap.containsKey(propLow)) {
						columnJdbcTypes[i] = fieldJdbcTypeMap.get(propLow);
					} else if (columnTypeNames != null && i < columnTypeNames.length && columnTypeNames[i] != null) {
						String tn = columnTypeNames[i].toUpperCase(Locale.ROOT);
						if (tn.equals("JSON")) {
							columnJdbcTypes[i] = org.sagacity.sqltoy.model.JdbcTypes.JSON;
						} else if (tn.equals("JSONB")) {
							columnJdbcTypes[i] = org.sagacity.sqltoy.model.JdbcTypes.JSONB;
						} else if (tn.equals("GEOMETRY") || GeometryTypeUtil.isGeometryTypeName(tn)) {
							columnJdbcTypes[i] = org.sagacity.sqltoy.model.JdbcTypes.GEOMETRY;
						} else if (tn.equals("VECTOR") || tn.equals("FLOATVECTOR")) {
							columnJdbcTypes[i] = org.sagacity.sqltoy.model.JdbcTypes.VECTOR;
						}
					}
				}
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
		TypeHandler typeHandler = sqlToyContext.getTypeHandler();
		// 基于游标流模式的数据查询和翻译必须逐行翻译,所以只需定义一个实例即可
		DynamicCacheHolder dynamicCacheHolder = new DynamicCacheHolder();
		boolean doNext = true;
		// 列读取策略查询级预分类(oracle文本化/db2空间/h2 json/mysql系空间与向量)
		int[] columnKinds = buildColumnKinds(dbType, columnTypeNames);
		while (rs.next()) {
			rowTemp = processResultRow(dbType, typeHandler, dynamicCacheFetch, dynamicCacheHolder, rs, labelNames,
					lowKeyLabelNameMap, columnSize, translateCache, realDecryptHandler, ignoreAllEmpty, columnTypeNames,
					columnKinds, 0);
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
					doNext = streamResultHandler.doNextConsume(rowTemp, index);
				} // 数组
				else if (type == 2) {
					Object[] rowAry = new Object[rowTemp.size()];
					rowTemp.toArray(rowAry);
					streamResultHandler.consume(rowAry, index);
					doNext = streamResultHandler.doNextConsume(rowAry, index);
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
					doNext = streamResultHandler.doNextConsume(rowMap, index);
				} // 封装成VO对象形式
				else {
					Object bean = BeanUtil.reflectRowToBean(sqlToyContext.getTypeHandler(), realMethods,
							methodTypeValues, methodTypes, genericTypes, rowTemp, indexs, realProps, resultType,
							columnJdbcTypes);
					// 有基于注解@Translate的缓存翻译
					if (cacheDatas != null) {
						wrapBeanTranslate(dynamicCacheFetch, dynamicCacheHolder, cacheDatas, bean);
					}
					streamResultHandler.consume(bean, index);
					doNext = streamResultHandler.doNextConsume(bean, index);
				}
				index++;
			}
			// 终止消费
			if (!doNext) {
				break;
			}
		}
		// 完成消费
		streamResultHandler.end();
		SqlExecuteStat.debug("operation hint", "stream query accumulated fetched:{} rows!", index);
	}

	/**
	 * 对List<List> 二维集合字段进行安全脱敏
	 * 
	 * @param desensitizeProvider 脱敏处理器提供者
	 * @param rows                二维数据集合，直接在原集合上脱敏
	 * @param masks               脱敏规则迭代器(目标列及脱敏策略)
	 * @param labelIndexMap       列名与列下标的对照关系
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

	/**
	 * 对单行记录进行安全脱敏
	 * 
	 * @param desensitizeProvider 脱敏处理器提供者
	 * @param row                 单行数据，直接在原数据上脱敏
	 * @param masks               脱敏规则迭代器(目标列及脱敏策略)
	 * @param labelIndexMap       列名与列下标的对照关系
	 */
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
	 * 对字段进行格式化
	 * 
	 * @param rows          二维数据集合，直接在原集合上格式化
	 * @param formats       格式化规则迭代器(日期格式或数字格式)
	 * @param labelIndexMap 列名与列下标的对照关系
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
				// 数字列下标越界时给出指向format配置的明确错误,而非深处的IndexOutOfBoundsException
				if (!rows.isEmpty()) {
					int colSize = rows.get(0).size();
					if (columnIndex < 0 || columnIndex >= colSize) {
						throw new IllegalArgumentException("the format column [" + fmt.getColumn()
								+ "] is beyond the query result column count [" + colSize
								+ "], please check the date-format/number-format columns config in the sql!");
					}
				}
				for (List row : rows) {
					value = row.get(columnIndex);
					if (value != null) {
						// 日期格式
						if (fmt.getType() == 1) {
							row.set(columnIndex, DateUtil.formatDate(value, fmt.getFormat(), fmt.getLocale()));
						}
						// 数字格式化
						else {
							row.set(columnIndex, NumberUtil.format(value, fmt.getFormat(), fmt.getRoundingMode(),
									fmt.getLocale(), fmt.getCurrency()));
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
				// 数字列下标越界时给出指向format配置的明确错误,而非深处的IndexOutOfBoundsException
				if (columnIndex < 0 || columnIndex >= row.size()) {
					throw new IllegalArgumentException("the format column [" + fmt.getColumn()
							+ "] is beyond the query result column count [" + row.size()
							+ "], please check the date-format/number-format columns config in the sql!");
				}
				value = row.get(columnIndex);
				if (value != null) {
					// 日期格式
					if (fmt.getType() == 1) {
						row.set(columnIndex, DateUtil.formatDate(value, fmt.getFormat(), fmt.getLocale()));
					}
					// 数字格式化
					else {
						row.set(columnIndex,
								NumberUtil.format(value, fmt.getFormat(), fmt.getRoundingMode(), fmt.getLocale()));
					}
				}
			}
		}
	}

	private static List getResultSet(Integer dbType, QueryExecutorExtend queryExtend, SqlToyConfig sqlToyConfig,
			SqlToyContext sqlToyContext, Connection conn, ResultSet rs, UpdateRowHandler updateRowHandler,
			DecryptHandler decryptHandler, int columnCnt, HashMap<String, Integer> labelIndexMap, String[] labelNames,
			int startColIndex, String[] columnTypeNames) throws Exception {
		// 字段连接(多行数据拼接成一个数据,以一行显示)
		LinkModel linkModel = sqlToyConfig.getLinkModel();
		if (queryExtend != null && queryExtend.linkModel != null) {
			linkModel = queryExtend.linkModel;
		}
		// update 2020-09-13 存在多列link(独立出去编写,避免对单列产生影响)
		if (linkModel != null && linkModel.getColumns().length > 1) {
			return getMoreLinkResultSet(dbType, sqlToyConfig, sqlToyContext, decryptHandler, conn, rs, columnCnt,
					labelIndexMap, labelNames, startColIndex, columnTypeNames);
		}
		List<List> items = new ArrayList();
		// 判断是否有缓存翻译器定义
		boolean hasTranslate = !sqlToyConfig.getTranslateMap().isEmpty();
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
		// 列读取策略查询级预分类(oracle文本化/db2空间/h2 json/mysql系空间与向量)
		int[] columnKinds = buildColumnKinds(dbType, columnTypeNames);
		int index = 0;
		// 警告阀值
		int warnThresholds = SqlToyConstants.getWarnThresholds();
		boolean warnLimit = false;
		// 最大阀值
		long maxThresholds = SqlToyConstants.getMaxThresholds();
		boolean maxLimit = false;
		// 是否判断全部为null的行记录
		boolean ignoreAllEmpty = sqlToyConfig.isIgnoreEmpty();
		TypeHandler typeHandler = sqlToyContext.getTypeHandler();
		// 最大值要大于等于警告阀值
		if (maxThresholds > 1 && maxThresholds <= warnThresholds) {
			maxThresholds = warnThresholds;
		}
		// 提取可批量查询数据的缓存翻译
		BatchDynamicCache batchDynamicCache = null;
		DynamicCacheHolder dynamicCacheHolder = null;
		List itemRow;
		// 单列link
		if (linkModel != null) {
			Object identity = null;
			String linkColumn = linkModel.getColumns()[0];
			String linkColumnLow = linkColumn.toLowerCase(Locale.ROOT);
			if (!labelIndexMap.containsKey(linkColumnLow)) {
				throw new DataAccessException("the link column [" + linkColumn
						+ "] does not exist in the query result fields, please check the sql or link config!");
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
			batchDynamicCache = TranslateUtils.getBatchTranslates(sqlToyContext, fieldTranslateCacheHolders,
					linkColumn);
			dynamicCacheHolder = new DynamicCacheHolder(batchDynamicCache.getCacheAndTypeForRealMap(),
					batchDynamicCache.getCacheAndTypeForRealType(), batchDynamicCache.getDynamicCaches());
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
					tmpObject = fieldTranslateHandler.getRSCacheValue(dynamicCacheFetch, dynamicCacheHolder, rs,
							lowKeyLabelNameMap, linkValue.toString());
					linkStr = (tmpObject == null) ? linkValue.toString() : tmpObject.toString();
				} else {
					linkStr = linkValue.toString();
				}
				// groupColumns为null即表示全部集合合并
				identity = (linkModel.getGroupColumns() == null) ? "default"
						: getLinkColumnsId(rs, linkModel.getGroupColumns());
				// 不相等
				if (!identity.equals(preIdentity)) {
					itemRow = processResultRow(dbType, typeHandler, dynamicCacheFetch, dynamicCacheHolder, rs,
							labelNames, lowKeyLabelNameMap, columnSize, fieldTranslateCacheHolders, decryptHandler,
							ignoreAllEmpty, columnTypeNames, columnKinds, startColIndex);
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
			batchDynamicCache = TranslateUtils.getBatchTranslates(sqlToyContext, fieldTranslateCacheHolders);
			dynamicCacheHolder = new DynamicCacheHolder(batchDynamicCache.getCacheAndTypeForRealMap(),
					batchDynamicCache.getCacheAndTypeForRealType(), batchDynamicCache.getDynamicCaches());
			while (rs.next()) {
				if (isUpdate) {
					updateRowHandler.updateRow(rs, index);
					rs.updateRow();
				}
				itemRow = processResultRow(dbType, typeHandler, dynamicCacheFetch, dynamicCacheHolder, rs, labelNames,
						lowKeyLabelNameMap, columnSize, fieldTranslateCacheHolders, decryptHandler, ignoreAllEmpty,
						columnTypeNames, columnKinds, startColIndex);
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
					"MaxLargeResult:the extracted data of the sql exceeds the max threshold:{} (adjustable via the [spring.sqltoy.pageFetchSizeLimit] config), sqlId={}, the sql={}",
					index, sqlToyConfig.getId(), sqlToyConfig.getSql(null));
		}
		// 对集合进行批量获取未匹配的缓存数据进行翻译
		TranslateUtils.translateArrayListByDynamicCache(sqlToyContext.getTranslateManager(), batchDynamicCache,
				dynamicCacheHolder, dynamicCacheFetch, labelIndexMap, items, false);
		return items;
	}

	/**
	 * 校验缓存翻译配置正确性
	 * 
	 * @param fieldTranslateMap  翻译字段配置(字段名与翻译器对照)
	 * @param lowKeyLabelNameMap 查询结果列名小写key的对照map
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
						throw new DataAccessException("the field [" + translate.getExtend().compareColumn
								+ "] does not exist in the query result fields, please check the where config of the translate for cache ["
								+ translate.getExtend().cache + "]!");
					}
				}
			}
		});
	}

	/**
	 * 组合link 多列值作为对比值
	 * 
	 * @param rs      ResultSet结果集对象(当前行)
	 * @param columns 分组对比的列名称数组
	 * @return 单列时为该列值(null转"null"文本)，多列时为下划线连接的拼接字符串
	 * @throws Exception
	 */
	private static Object getLinkColumnsId(ResultSet rs, String[] columns) throws Exception {
		if (columns.length == 1) {
			// 分组列值为null时返回"null"文本(与多列拼接分支的null文本化一致),
			// 避免调用方identity.equals(preIdentity)对null调用equals抛NPE
			Object singleValue = rs.getObject(columns[0]);
			return (singleValue == null) ? "null" : singleValue;
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
	 * 实现多列link
	 * 
	 * @param sqlToyConfig   sql配置信息(含linkModel配置)
	 * @param sqlToyContext  sqltoy上下文
	 * @param decryptHandler 字段解密处理器，非null时对指定列做解密处理
	 * @param conn           数据库连接对象
	 * @param rs             ResultSet结果集对象
	 * @param columnCnt      结果集总列数
	 * @param labelIndexMap  列名与列下标的对照关系
	 * @param labelNames     列名称数组
	 * @param startColIndex  起始提取列下标(从0开始)
	 * @return 各link列按link-sign拼接成字符串后的二维List结果
	 * @throws Exception
	 */
	private static List getMoreLinkResultSet(Integer dbType, SqlToyConfig sqlToyConfig, SqlToyContext sqlToyContext,
			DecryptHandler decryptHandler, Connection conn, ResultSet rs, int columnCnt,
			HashMap<String, Integer> labelIndexMap, String[] labelNames, int startColIndex, String[] columnTypeNames)
			throws Exception {
		// 字段连接(多行数据拼接成一个数据,以一行显示)
		LinkModel linkModel = sqlToyConfig.getLinkModel();
		List<List> items = new ArrayList();
		// 判断是否有缓存翻译器定义
		boolean hasTranslate = !sqlToyConfig.getTranslateMap().isEmpty();
		HashMap<String, String> lowKeyLabelNameMap = labelLowKeyMap(labelNames);
		HashMap<String, FieldTranslate> translateMap = sqlToyConfig.getTranslateMap();
		HashMap<String, FieldTranslateCacheHolder> fieldTranslateCacheHolders = null;
		DynamicCacheFetch dynamicCacheFetch = sqlToyContext.getDynamicCacheFetch();
		if (hasTranslate) {
			validateCacheConfig(translateMap, lowKeyLabelNameMap);
			fieldTranslateCacheHolders = sqlToyContext.getTranslateManager().getTranslates(translateMap);
		}

		int columnSize = labelNames.length;
		// 列读取策略查询级预分类(oracle文本化/db2空间/h2 json/mysql系空间与向量)
		int[] columnKinds = buildColumnKinds(dbType, columnTypeNames);
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
			linkColumnLow = linkColumns[i].toLowerCase(Locale.ROOT);
			linkLowColumns[i] = linkColumnLow;
			if (!labelIndexMap.containsKey(linkColumnLow)) {
				throw new DataAccessException("the link column [" + linkColumnLow
						+ "] does not exist in the query result fields, please check the sql or link config!");
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
		TypeHandler typeHandler = sqlToyContext.getTypeHandler();
		// 提取可批量获取的动态缓存翻译配置
		BatchDynamicCache batchDynamicCache = TranslateUtils.getBatchTranslates(sqlToyContext,
				fieldTranslateCacheHolders, linkLowColumns);
		DynamicCacheHolder dynamicCacheHolder = new DynamicCacheHolder(batchDynamicCache.getCacheAndTypeForRealMap(),
				batchDynamicCache.getCacheAndTypeForRealType(), batchDynamicCache.getDynamicCaches());
		while (rs.next()) {
			// 对多个link字段取值并进行翻译转义
			for (int i = 0; i < linkColCnt; i++) {
				linkValues[i] = rs.getObject(linkRealLabels[i]);
				if (linkValues[i] == null) {
					linkStrs[i] = "";
				} else if (translateLinks[i]) {
					fieldTranslateCacheHolder = fieldTranslateCacheHolders.get(linkLowColumns[i]);
					tmpObject = fieldTranslateCacheHolder.getRSCacheValue(dynamicCacheFetch, dynamicCacheHolder, rs,
							lowKeyLabelNameMap, linkValues[i].toString());
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
				itemRow = processResultRow(dbType, typeHandler, dynamicCacheFetch, dynamicCacheHolder, rs, labelNames,
						lowKeyLabelNameMap, columnSize, fieldTranslateCacheHolders, decryptHandler, ignoreAllEmpty,
						columnTypeNames, columnKinds, startColIndex);
				if (itemRow != null) {
					// 不相等时先对最后一条记录修改，写入拼接后的字符串
					// 注:多列link按link-sign拼接为字符串返回,不支持单列link的result-type(List/Array等)配置
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
					"MaxLargeResult:the extracted data of the sql exceeds the max threshold:{} (adjustable via the [spring.sqltoy.pageFetchSizeLimit] config), sqlId={}, the sql={}",
					index, sqlToyConfig.getId(), sqlToyConfig.getSql(null));
		}
		// 对集合进行批量获取未匹配的缓存数据进行翻译
		TranslateUtils.translateArrayListByDynamicCache(sqlToyContext.getTranslateManager(), batchDynamicCache,
				dynamicCacheHolder, dynamicCacheFetch, labelIndexMap, items, false);
		return items;
	}

	/**
	 * 对结果进行数据旋转
	 * 
	 * @param pivotModel       数据旋转配置模型(分类列、旋转列、分组列等)
	 * @param labelIndexMap    列名与列下标的对照关系
	 * @param result           待旋转的二维数据集合
	 * @param pivotCategorySet 旋转参照类别集合，null时从数据中提取
	 * @return 旋转(行转列)后的结果集合
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
	 * 将label别名换成对应的列编号(select name,sex from xxxTable，name别名对应的列则为0)
	 * 
	 * @param columnLabels  列名(或列序号字符串)数组
	 * @param labelIndexMap 列名与列下标的对照关系
	 * @return 各列对应的下标数组，纯数字直接解析，否则按列名小写查表
	 */
	private static Integer[] mappingLabelIndex(String[] columnLabels, LabelIndexModel labelIndexMap) {
		Integer[] result = new Integer[columnLabels.length];
		for (int i = 0; i < result.length; i++) {
			if (NumberUtil.isInteger(columnLabels[i])) {
				result[i] = Integer.parseInt(columnLabels[i]);
			} else {
				result[i] = labelIndexMap.get(columnLabels[i].toLowerCase(Locale.ROOT));
			}
		}
		return result;
	}

	/**
	 * 针对resultSet label提供小写key map
	 * 
	 * @param labelNames 列名称数组
	 * @return key为小写列名、value为原始列名的HashMap
	 */
	private static HashMap<String, String> labelLowKeyMap(String[] labelNames) {
		HashMap<String, String> lowKeyMap = new HashMap<>();
		for (String label : labelNames) {
			lowKeyMap.put(label.toLowerCase(Locale.ROOT), label);
		}
		return lowKeyMap;
	}

	/**
	 * 提取出选择的横向分类信息
	 * 
	 * @param items        二维数据集合
	 * @param categoryCols 分类列的下标数组
	 * @return 去重并排序后的分类组合集合(已行转列，供旋转参照使用)
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
	 * 分组排序
	 * 
	 * @param sortList 二维数据集合，直接在原集合上排序
	 * @param groupCol 分组列的下标(分组列值相同的连续行为一组)
	 * @param orderCol 组内排序列的下标
	 * @param ascend   true升序，false降序
	 * @return 排序后的原集合
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
			if (!Objects.equals(tempObj, compareValue)) {
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
	 * 对二维数据进行排序
	 * 
	 * @param sortList 二维数据集合，直接在原集合上排序
	 * @param orderCol 排序列的下标
	 * @param start    排序范围起始行下标(含)
	 * @param end      排序范围结束行下标(含)
	 * @param ascend   true升序，false降序
	 * @return 排序后的原集合
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
	 * 处理Result单行数据
	 * 
	 * @param dynamicCacheFetch  动态缓存数据抓取接口，翻译配置为动态缓存时逐key实时查询
	 * @param rs                 ResultSet结果集对象(当前行)
	 * @param labelNames         列名称数组(对应rs第startColIndex+i+1列)，null时无label定位
	 * @param lowKeyLabelNameMap 列名小写key的对照map(翻译定位用)
	 * @param size               本行提取的列数
	 * @param translateCaches    翻译字段缓存配置，null表示无翻译
	 * @param decryptHandler     字段解密处理器，非null时对指定列做解密处理
	 * @param ignoreAllEmptySet  true表示整行数据全为空值时返回null
	 * @param startColIndex      列起始下标(从0开始,oracle11g分页经此跳过包装的page_row_id列)
	 * @return 单行的值组成的List，整行为空且ignoreAllEmptySet为true返回null
	 * @throws Exception
	 */
	public static List processResultRow(Integer dbType, TypeHandler typeHandler, DynamicCacheFetch dynamicCacheFetch,
			DynamicCacheHolder dynamicCacheHolder, ResultSet rs, String[] labelNames,
			HashMap<String, String> lowKeyLabelNameMap, int size,
			HashMap<String, FieldTranslateCacheHolder> translateCaches, DecryptHandler decryptHandler,
			boolean ignoreAllEmptySet, String[] columnTypeNames, int[] columnKinds, int startColIndex)
			throws Exception {
		List rowData = new ArrayList();
		Object fieldValue;
		// 单行所有字段结果为null
		boolean allNull = true;
		String label = null;
		int blobSize;
		boolean isLabel = (labelNames == null) ? false : true;
		boolean doTranslate = (translateCaches == null) ? false : true;
		// oracle 的时间戳非标准java类型
		boolean convertOracleTimestamp = SqlToyConstants.convertOracleTimestamp();
		FieldTranslateCacheHolder fieldTranslateHandler;
		// 列读取策略由调用方按dbType+列元数据类型名预分类(columnKinds,查询级一次),
		// 本循环内零字符串判定:TEXT_READ列直接文本化读取,EXT_BYTE列取值后按列类型归一,
		// 常规列getObject后经normalizeExtTypeValue的常规类型快速通道零开销直通
		// update 2026-9-8 列名小写化同步提升到查询级预计算(原每行每列toLowerCase重复分配)
		String[] lowLabelNames = null;
		if (isLabel) {
			lowLabelNames = new String[size];
			for (int i = 0; i < size; i++) {
				lowLabelNames[i] = labelNames[i].toLowerCase(Locale.ROOT);
			}
		}
		for (int i = 0; i < size; i++) {
			int kind = (columnKinds == null) ? COLUMN_NORMAL : columnKinds[i];
			if (isLabel) {
				label = lowLabelNames[i];
			}
			// TEXT_READ:oracle的JSON/VECTOR列getObject直接抛错(ORA-17004/18722)、
			// db2(db2gse)的ST_Geometry读回驱动内部混淆对象,均改走getString文本化
			// update 2026-9-8 统一按下标取值,驱动免label查找;列下标须叠加startColIndex:
			// labelNames[i]/columnTypeNames[i]/columnKinds[i]均对应rs第startColIndex+i+1列
			// (oracle11g分页包装的page_row_id列经startColIndex跳过,直接i+1会整体错位)
			fieldValue = (kind == COLUMN_TEXT_READ) ? rs.getString(startColIndex + i + 1)
					: rs.getObject(startColIndex + i + 1);
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
				} else {
					// oracle 的时间戳非标准java类型
					if (convertOracleTimestamp && fieldValue.getClass().getTypeName().equals("oracle.sql.TIMESTAMP")) {
						fieldValue = BeanUtil.oracleTimeStampConvert(fieldValue);
					}
					// update 2026-9-6 扩展类型(json/vector/geometry)归一化:Map/数组行不经
					// BeanUtil.convertType;常规类型(String/数值/日期时间/布尔)在归一化入口
					// 快速短路,仅EXT_BYTE列(byte[]形态的json/vector/geometry)与驱动专属
					// 对象(STRUCT/PGobject等非常规类型)进入识别链
					fieldValue = normalizeExtTypeValue(fieldValue, dbType,
							(kind == COLUMN_EXT_BYTE && columnTypeNames != null) ? columnTypeNames[i] : null);
				}
				// java 特定类型处理
				if (typeHandler != null) {
					fieldValue = typeHandler.toJavaType(dbType, fieldValue);
				}
				// 解密
				if (decryptHandler != null) {
					fieldValue = decryptHandler.decrypt(label, fieldValue);
				}
				if (doTranslate) {
					fieldTranslateHandler = translateCaches.get(label);
					if (fieldTranslateHandler != null) {
						fieldValue = fieldTranslateHandler.getRSCacheValue(dynamicCacheFetch, dynamicCacheHolder, rs,
								lowKeyLabelNameMap, fieldValue.toString());
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
	 * 提取结果集各列的元数据类型名(与列下标一一对应,供buildColumnKinds/归一化判定使用)
	 * 
	 * @param rs   结果集
	 * @param size 列数量
	 * @return 各列类型名数组,元数据获取失败时元素为null
	 */
	public static String[] readColumnTypeNames(ResultSet rs, int size) {
		String[] typeNames = new String[size];
		try {
			java.sql.ResultSetMetaData md = rs.getMetaData();
			for (int i = 0; i < size && i < md.getColumnCount(); i++) {
				typeNames[i] = md.getColumnTypeName(i + 1);
			}
		} catch (Exception e) {
			// 元数据获取失败不阻断主流程
		}
		return typeNames;
	}

	// 列读取策略标记(查询级预分类,processResultRow行循环内仅做int比较)
	// update 2026-9-9 提升为public:SqlUtil的VO直映射路径(reflectResultRowToVOClass)复用同一套策略
	/** 常规列:getObject取值,常规类型经归一化入口快速通道直通 */
	public static final int COLUMN_NORMAL = 0;
	/** 文本化读取列:oracle的JSON/VECTOR(getObject直接抛错)与db2(db2gse)的ST_Geometry(驱动混淆对象) */
	public static final int COLUMN_TEXT_READ = 1;
	/**
	 * byte[]扩展列:h2的JSON、sqlserver/mysql系的VECTOR与GEOMETRY(getObject返回byte[]按列类型解码)
	 */
	public static final int COLUMN_EXT_BYTE = 2;

	/**
	 * update 2026-9-6 按dbType与列元数据类型名对结果集各列预分类(查询级一次,行循环零字符串判定):
	 * <li>TEXT_READ(1):oracle的JSON/VECTOR列getObject无默认转换(实测ORA-17004/ORA-18722)、
	 * db2(db2gse)的ST_Geometry读回驱动内部混淆对象,均改走getString文本化读取</li>
	 * <li>EXT_BYTE(2):h2的JSON列(UTF-8文本byte[])、sqlserver的GEOMETRY/VECTOR(驱动内部格式
	 * byte[])、mysql系的GEOMETRY(4字节SRID前缀+WKB)/VECTOR(float32小端直排),取值后按列类型解码</li>
	 * <li>NORMAL(0):其余列(pg系的json/vector/geometry读回PGobject等驱动对象,由归一化按值形状识别)</li>
	 * 
	 * @param dbType          数据库类型,参见DataSourceUtils.DBType
	 * @param columnTypeNames 列元数据类型名(与labelNames同下标对齐),null返回null
	 * @return 各列读取策略标记数组
	 */
	public static int[] buildColumnKinds(Integer dbType, String[] columnTypeNames) {
		if (columnTypeNames == null) {
			return null;
		}
		int[] kinds = new int[columnTypeNames.length];
		if (dbType == null) {
			return kinds;
		}
		final int dt = dbType;
		boolean oracle = (dt == DBType.ORACLE || dt == DBType.ORACLE11);
		boolean db2gse = (dt == DBType.DB2);
		boolean h2 = (dt == DBType.H2);
		boolean mssql = (dt == DBType.SQLSERVER);
		boolean mysqlLike = (dt == DBType.MYSQL || dt == DBType.MYSQL57 || dt == DBType.OCEANBASE || dt == DBType.TIDB);
		if (!(oracle || db2gse || h2 || mssql || mysqlLike)) {
			return kinds;
		}
		for (int i = 0; i < kinds.length; i++) {
			String tn = columnTypeNames[i];
			if (tn == null || tn.isEmpty()) {
				continue;
			}
			String t = tn.toUpperCase(Locale.ROOT).replace("\"", "");
			if (t.isEmpty()) {
				continue;
			}
			if (oracle && ("JSON".equals(t) || "VECTOR".equals(t))) {
				kinds[i] = COLUMN_TEXT_READ;
			} else if (db2gse && t.contains("ST_GEOMETRY")) {
				// update 2026-9-10 已知边界:db2gse的ST_Geometry列getString返回WKT文本,而12.1+
				// 内置空间引擎的ST_GEOMETRY列getString返回驱动内部格式hex文本(无公开格式文档不做
				// 客户端解码),内置列读回WKT请在查询侧显式ST_AsText(geom)
				kinds[i] = COLUMN_TEXT_READ;
			} else if (h2 && "GEOMETRY".equals(t)) {
				// update 2026-9-8 h2的geometry列getObject返回JTS Geometry对象(非String),
				// getString返回WKT文本,按TEXT_READ归一化为String参与Map/VO映射
				// (与oracle JSON/VECTOR同策略)
				kinds[i] = COLUMN_TEXT_READ;
			} else if ((h2 && "JSON".equals(t))
					|| ((mssql || mysqlLike) && (GeometryTypeUtil.isGeometryTypeName(t) || "VECTOR".equals(t)))) {
				kinds[i] = COLUMN_EXT_BYTE;
			}
		}
		return kinds;
	}

	// pg系衍生驱动扩展类型对象的getType()方法缓存(类名→Method,按类解析一次规避逐单元格反射开销)
	private static final java.util.concurrent.ConcurrentHashMap<String, java.util.Optional<java.lang.reflect.Method>> PG_GET_TYPE_METHODS = new java.util.concurrent.ConcurrentHashMap<>();
	// mssql-jdbc的Geometry类与解码方法缓存(microsoft.sqlserver.jdbc.Geometry为驱动内置,
	// 类不存在时AVAIL=false整体跳过;static Method线程安全)
	private static final boolean MSSQL_GEO_AVAIL;
	private static final java.lang.reflect.Method MSSQL_GEO_DESERIALIZE;
	private static final java.lang.reflect.Method MSSQL_GEO_ASTEXT;

	static {
		java.lang.reflect.Method deserialize = null;
		java.lang.reflect.Method asText = null;
		try {
			Class<?> geoCls = Class.forName("com.microsoft.sqlserver.jdbc.Geometry");
			deserialize = geoCls.getMethod("deserialize", byte[].class);
			asText = geoCls.getMethod("STAsText");
		} catch (Throwable ignore) {
			// mssql-jdbc不在classpath时跳过sqlserver几何解码
		}
		MSSQL_GEO_DESERIALIZE = deserialize;
		MSSQL_GEO_ASTEXT = asText;
		MSSQL_GEO_AVAIL = (deserialize != null && asText != null);
	}

	/**
	 * sqlserver 2025+ vector列读回的驱动内部格式byte[]解码为向量文本:内部格式为 4字节头(A9
	 * 01签名+1字节维度+1字节0)+维度个float32小端(实测vector(2)/(3)),
	 * 数值以去尾零形式输出保持与pg系'[1,2,3]'文本形态一致
	 * 
	 * @param bytes sqlserver vector内部格式字节
	 * @return '[1,2,3]'形态向量文本,签名不符或解码失败返回null
	 */
	private static String mssqlVectorBytesToText(byte[] bytes) {
		// 头部8字节:A9 01签名+1字节维度+5字节0,其后为维度个float32小端(实测vector(2)/(3))
		if (bytes == null || bytes.length < 12 || (bytes.length - 8) % 4 != 0) {
			return null;
		}
		// 头部签名A9 01校验(误判防御,普通二进制列不会以该签名开头)
		if ((bytes[0] & 0xFF) != 0xA9 || (bytes[1] & 0xFF) != 0x01) {
			return null;
		}
		int dims = (bytes.length - 8) / 4;
		if (dims != (bytes[2] & 0xFF)) {
			return null;
		}
		StringBuilder sb = new StringBuilder("[");
		java.nio.ByteBuffer buf = java.nio.ByteBuffer.wrap(bytes, 8, dims * 4).order(java.nio.ByteOrder.LITTLE_ENDIAN);
		for (int i = 0; i < dims; i++) {
			if (i > 0) {
				sb.append(",");
			}
			sb.append(new java.math.BigDecimal(String.valueOf(buf.getFloat())).stripTrailingZeros().toPlainString());
		}
		return sb.append("]").toString();
	}

	/**
	 * mysql 9.x vector列读回的byte[]解码为向量文本:内部格式为维度个float32小端
	 * 直排(无头部,实测vector(3)为12字节),数值以去尾零形式输出保持与pg系文本形态一致
	 * 
	 * @param bytes mysql vector内部格式字节
	 * @return '[1,2,3]'形态向量文本,长度非4倍数或解码失败返回null
	 */
	private static String mysqlVectorBytesToText(byte[] bytes) {
		if (bytes == null || bytes.length < 4 || bytes.length % 4 != 0) {
			return null;
		}
		int dims = bytes.length / 4;
		StringBuilder sb = new StringBuilder("[");
		java.nio.ByteBuffer buf = java.nio.ByteBuffer.wrap(bytes).order(java.nio.ByteOrder.LITTLE_ENDIAN);
		for (int i = 0; i < dims; i++) {
			if (i > 0) {
				sb.append(",");
			}
			sb.append(new java.math.BigDecimal(String.valueOf(buf.getFloat())).stripTrailingZeros().toPlainString());
		}
		return sb.append("]").toString();
	}

	/**
	 * sqlserver空间列读回的驱动内部格式byte[]解码为WKT文本:经mssql-jdbc内置的
	 * com.microsoft.sqlserver.jdbc.Geometry.deserialize反序列化后STAsText输出标准WKT
	 * (实测POINT/LINESTRING/POLYGON),失败返回null保留原值
	 * 
	 * @param bytes sqlserver geometry内部格式字节
	 * @return WKT文本,驱动不可用或解码失败返回null
	 */
	private static String mssqlGeometryBytesToWKT(byte[] bytes) {
		if (!MSSQL_GEO_AVAIL) {
			return null;
		}
		try {
			Object geometry = MSSQL_GEO_DESERIALIZE.invoke(null, bytes);
			if (geometry == null) {
				return null;
			}
			Object wkt = MSSQL_GEO_ASTEXT.invoke(geometry);
			return (wkt instanceof String) ? (String) wkt : null;
		} catch (Exception e) {
			return null;
		}
	}

	/**
	 * update 2026-9-6 扩展类型(json/vector/geometry)读回值归一化,供Map/数组/流式行调用
	 * (这些行形态不经BeanUtil.convertType),按值形状识别类型后统一处理,不区分具体数据库 (oracle
	 * 21c原生JSON与geometry、pg 18.6+pgvector的json/jsonb/vector均已实库验证):
	 * <li>SDO兼容STRUCT(oracle,以及达梦SYSGEO.SDO_GEOMETRY等兼容实现,类型名以SDO_GEOMETRY结尾)
	 * →geometry,解码为WKT文本(jts解析,失败保留原值)</li>
	 * <li>达梦ST_GEOMETRY
	 * STRUCT(DMGEO/sysgeo.ST_GEOMETRY,读回dm.jdbc.driver.DmdbStruct, 属性布局:(版本,标准OGC
	 * WKB blob,srid))→geometry,解码为WKT文本(jts解析,失败保留原值)</li>
	 * <li>oracle
	 * vector(oracle.sql.VECTOR,类名含.vector)→vector,'[1,2,3]'文本('[]'形态校验通过才替换)</li>
	 * <li>oracle原生JSON实现(oracle.jdbc.driver.json.binary.Oson*Impl与oracle.sql.json.*,类名含.json)
	 * →JSON,归一为JSON文本</li>
	 * <li>几何对象类(org.postgis.PGgeometry、microsoft.sql.Geometry等,类名含geometry)→geometry,
	 * 转WKT文本(GeometryTypeUtil.parse支持WKT/EWKT/EWKB hex,失败保留原值)</li>
	 * <li>pg系衍生驱动扩展类型对象(org.postgresql.util.PGobject及opengauss/gaussdb/vastbase/
	 * stardb/kingbase的金仓KBObject等同构类型,反射探测getType()):geometry/geography→WKT文本,
	 * json/jsonb→JSON文本,vector/floatvector→向量文本;gaussdb驱动(华为PGobject)读回对象
	 * 未填充type(getType()为null),按类名兜底转文本(toString恒为getValue()文本,无损)</li>
	 * <li>h2 json列getObject返回byte[](UTF-8 JSON文本):值形状与blob/varbinary同为byte[]无法区分,
	 * 需借助列元数据类型名(带columnTypeName的重载,processResultRow已按列传入)精确归一为JSON文本</li>
	 * <li>空间列byte[]按dbType路由(带dbType的重载):sqlserver(驱动内部格式,经mssql-jdbc的
	 * Geometry.deserialize客户端解码为WKT)、mysql系(4字节小端SRID前缀+标准WKB,强制剥前缀解码)</li>
	 * 均不匹配时原样返回;db2(db2gse)的ST_Geometry读回驱动内部混淆对象(非Struct无法客户端解码)
	 * 由processResultRow按列元数据类型名改走getString转WKT文本
	 * 
	 * @param value jdbc读回值
	 * @return 归一化后的值,不匹配或转换失败时返回原值
	 */
	public static Object normalizeExtTypeValue(Object value) {
		return normalizeExtTypeValue(value, null, null);
	}

	/**
	 * 带列元数据类型名的扩展类型归一化: byte[]形态借助列类型名区分h2 json列(类型名JSON)与blob/binary列,其余逻辑与单参重载一致
	 *
	 * @param value          jdbc读回值
	 * @param columnTypeName 当前列的ResultSetMetaData类型名(如JSON/BLOB),null时不做byte[]归一
	 * @return 归一化后的值,不匹配或转换失败时返回原值
	 */
	public static Object normalizeExtTypeValue(Object value, String columnTypeName) {
		return normalizeExtTypeValue(value, null, columnTypeName);
	}

	/**
	 * 带数据库类型与列元数据类型名的扩展类型归一化主实现(processResultRow调用):
	 * byte[]形态按dbType路由:sqlserver空间列(驱动内部格式,经mssql-jdbc的Geometry类客户端解码
	 * 为WKT)、mysql系空间列(4字节SRID前缀+标准WKB,GeometryTypeUtil解码)、h2 json列(UTF-8文本)
	 *
	 * @param value          jdbc读回值
	 * @param dbType         数据库类型,参见DataSourceUtils.DBType,null时不做byte[]空间归一
	 * @param columnTypeName 当前列的ResultSetMetaData类型名,null时不做byte[]归一
	 * @return 归一化后的值,不匹配或转换失败时返回原值
	 */
	public static Object normalizeExtTypeValue(Object value, Integer dbType, String columnTypeName) {
		// 快速通道:常规类型(String/数值/日期时间/布尔/字符)直接返回,绝大多数单元格零额外开销
		// (java.sql.Timestamp等继承java.util.Date;java.time全系实现java.time.temporal.Temporal,
		// 一个instanceof覆盖LocalDateTime/LocalDate/Instant/OffsetDateTime等)
		if (value instanceof CharSequence || value instanceof Number || value instanceof java.util.Date
				|| value instanceof Boolean || value instanceof Character
				|| value instanceof java.time.temporal.Temporal) {
			return value;
		}
		// STRUCT形态:SDO兼容布局(oracle/达梦SYSGEO)与达梦WKB布局(DMGEO/sysgeo.ST_GEOMETRY)
		if (value instanceof java.sql.Struct) {
			String typeName = structTypeName((java.sql.Struct) value);
			if (typeName != null) {
				if (typeName.endsWith("SDO_GEOMETRY")) {
					String wkt = GeometryTypeUtil.toWKTString(value);
					return (wkt != null) ? wkt : value;
				}
				if (typeName.endsWith("ST_GEOMETRY")) {
					String wkt = GeometryTypeUtil.wkbStructToWKT((java.sql.Struct) value);
					return (wkt != null) ? wkt : value;
				}
			}
			return value;
		}
		// byte[]形态按列元数据类型名精确归一:blob(已提前转byte[])/varbinary等二进制列不受影响
		if (value instanceof byte[]) {
			if (columnTypeName != null) {
				String colType = columnTypeName.toUpperCase(Locale.ROOT).replace("\"", "");
				// h2 json列:getObject返回UTF-8 JSON文本byte[]
				// update 2026-9-9 对称剥除JSON字符串标量外层引号(h2的json列setString绑定会整体包一层
				// 引号,VO路径经JSONTypeUtil.extractJsonString已剥除,Map路径原样返回导致两路径形态不一致)
				if ("JSON".equals(colType)) {
					return JSONTypeUtil.unwrapJsonStringScalar(
							new String((byte[]) value, java.nio.charset.StandardCharsets.UTF_8));
				}
				// vector列读回byte[]的客户端解码:sqlserver 2025+(8字节头+float32小端,
				// getString/getBytes均报不支持转换);mysql 9.x(float32小端直排无头部)
				if ("VECTOR".equals(colType) && dbType != null) {
					String text = null;
					if (dbType == DataSourceUtils.DBType.SQLSERVER) {
						text = mssqlVectorBytesToText((byte[]) value);
					} else if (dbType == DataSourceUtils.DBType.MYSQL || dbType == DataSourceUtils.DBType.MYSQL57) {
						text = mysqlVectorBytesToText((byte[]) value);
					} else if (dbType == DataSourceUtils.DBType.OCEANBASE || dbType == DataSourceUtils.DBType.TIDB) {
						// update 2026-9-9 OB/TiDB与mysql同列EXT_BYTE预分类,但读回形态未实库验证:
						// 文本形态优先(ob的vector为字符串隐式转换承载),否则按mysql内部格式试解码,
						// 均不匹配原样返回
						byte[] vecBytes = (byte[]) value;
						if (vecBytes.length > 0 && vecBytes[0] == '[') {
							return new String(vecBytes, java.nio.charset.StandardCharsets.UTF_8);
						}
						text = mysqlVectorBytesToText(vecBytes);
					}
					if (text != null) {
						return text;
					}
				}
				// 空间列:sqlserver为驱动内部格式(SRID+版本/属性位,非标准WKB,经mssql-jdbc
				// 的Geometry.deserialize客户端解码);mysql系为4字节小端SRID前缀+标准WKB
				// (强制剥前缀解码,SRID=0时嗅探式判定会与大端WKB误判);其余库按通用嗅探
				if (GeometryTypeUtil.isGeometryTypeName(colType)) {
					String wkt = null;
					if (dbType != null && dbType == DataSourceUtils.DBType.SQLSERVER) {
						wkt = mssqlGeometryBytesToWKT((byte[]) value);
					} else if (dbType != null && (dbType == DataSourceUtils.DBType.MYSQL
							|| dbType == DataSourceUtils.DBType.MYSQL57 || dbType == DataSourceUtils.DBType.OCEANBASE
							|| dbType == DataSourceUtils.DBType.TIDB)) {
						wkt = GeometryTypeUtil.mysqlGeometryBytesToWKT((byte[]) value);
					} else {
						wkt = GeometryTypeUtil.toWKTString(value);
					}
					if (wkt != null) {
						return wkt;
					}
				}
			}
			return value;
		}
		String className = value.getClass().getName().toLowerCase(Locale.ROOT);
		if (className.contains(".vector")) {
			// vector统一为'[1,2,3]'文本,'[]'形态校验通过才替换(防御toString形态不符的未知子类)
			String text = String.valueOf(value).trim();
			return (text.startsWith("[") && text.endsWith("]")) ? text : value;
		}
		if (className.contains(".json")) {
			// JSON类实现对象的toString均为JSON文本形态
			return String.valueOf(value);
		}
		if (className.contains("geometry")) {
			// postgis-jdbc的PGgeometry、sqlserver的microsoft.sql.Geometry等几何对象
			String wkt = GeometryTypeUtil.toWKTString(value);
			return (wkt != null) ? wkt : value;
		}
		// pg系衍生驱动扩展类型对象(同构getType()方法,按类缓存反射句柄)
		String pgType = extObjectPgType(value);
		if (pgType != null) {
			String typeLow = pgType.toLowerCase(Locale.ROOT);
			if (typeLow.contains("geometry") || typeLow.contains("geography")) {
				String wkt = GeometryTypeUtil.toWKTString(value);
				return (wkt != null) ? wkt : value;
			}
			if (typeLow.contains("json") || typeLow.contains("vector")) {
				// getType()包含判断兼容vector(3)等typmod形态,PGobject.toString()=getValue()
				// 文本,转String恒无损
				return String.valueOf(value);
			}
		} else if (value.getClass().getName().toLowerCase(Locale.ROOT).contains("pgobject")) {
			// gaussdb驱动(华为com.huawei.gaussdb.jdbc.util.PGobject)读回对象未填充type
			// (getType()为null,无其他类型访问器),PGobject.toString()恒为getValue()文本,
			// 转String无损(实测json列),未知类型(hstore等文本形态)同样受益
			return String.valueOf(value);
		}
		return value;
	}

	/**
	 * pg系衍生驱动扩展类型对象的getType()反射探测(按类缓存,规避逐单元格反射开销):
	 * pg/opengauss/gaussdb/vastbase/stardb/kingbase(金仓KBObject)等同构对象均提供getType()
	 * 
	 * @param value jdbc读回值
	 * @return getType()返回的字符串,null表示无该方法或返回非字符串
	 */
	private static String extObjectPgType(Object value) {
		Class<?> clazz = value.getClass();
		java.util.Optional<java.lang.reflect.Method> method = PG_GET_TYPE_METHODS.computeIfAbsent(clazz.getName(),
				k -> {
					try {
						return java.util.Optional.of(clazz.getMethod("getType"));
					} catch (Exception e) {
						return java.util.Optional.empty();
					}
				});
		if (!method.isPresent()) {
			return null;
		}
		try {
			Object typeValue = method.get().invoke(value);
			return (typeValue instanceof String) ? (String) typeValue : null;
		} catch (Exception e) {
			return null;
		}
	}

	/**
	 * 提取Struct值的UDT类型名(统一大写,按类型名路由空间解码避免误伤其他自定义UDT列)
	 * 
	 * @param struct java.sql.Struct值
	 * @return 大写类型名(如MDSYS.SDO_GEOMETRY/SYSGEO.ST_GEOMETRY),获取失败返回null
	 */
	private static String structTypeName(java.sql.Struct struct) {
		try {
			String typeName = struct.getSQLTypeName();
			return (typeName == null) ? null : typeName.toUpperCase(Locale.ROOT);
		} catch (Exception e) {
			return null;
		}
	}

	/**
	 * 提取数据旋转对应的sql查询结果
	 * 
	 * @param sqlToyContext sqltoy上下文
	 * @param sqlToyConfig  sql配置信息
	 * @param queryExecutor 查询执行器(含参数和扩展计算配置)
	 * @param conn          数据库连接对象
	 * @param dbType        数据库类型，参见DataSourceUtils.DBType
	 * @param dialect       数据库方言
	 * @return 旋转参照类别数据(已行转列)，无pivot配置或未定义category-sql返回null
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
					Integer queryTimeout = null;
					if (pivotSqlConfig.getQueryTimeout() != null && pivotSqlConfig.getQueryTimeout() > 0) {
						queryTimeout = pivotSqlConfig.getQueryTimeout();
					}
					if ((queryTimeout == null || queryTimeout <= 0)
							&& (extend != null && extend.timeout != null && extend.timeout > 0)) {
						queryTimeout = extend.timeout;
					}
					SqlToyResult pivotSqlToyResult = SqlConfigParseUtils.processSql(pivotSqlConfig.getSql(dialect),
							extend.getParamsName(), extend.getParamsValue(sqlToyContext, pivotSqlConfig), dialect);
					// 增加sql执行拦截器 update 2022-9-10
					pivotSqlToyResult = DialectUtils.doInterceptors(sqlToyContext, pivotSqlConfig, OperateType.search,
							pivotSqlToyResult, null, dbType);
					List pivotCategory = SqlUtil.findByJdbcQuery(sqlToyContext.getTypeHandler(),
							pivotSqlToyResult.getSql(), pivotSqlToyResult.getParamsValue(), null, null, null, conn,
							dbType, sqlToyConfig.isIgnoreEmpty(), null, SqlToyConstants.FETCH_SIZE, -1, queryTimeout);
					// 行转列返回
					return CollectionUtil.convertColToRow(pivotCategory, null);
				}
			}
		}
		return null;
	}

	/**
	 * 对查询结果进行计算处理:字段脱敏、格式化、数据旋转、同步环比、分组汇总等
	 * 
	 * @param desensitizeProvider 脱敏处理器提供者
	 * @param sqlToyConfig        sql配置信息(含脱敏、格式化、计算器配置)
	 * @param dataSetResult       查询结果数据集，直接在其上完成计算处理
	 * @param pivotCategorySet    数据旋转的参照类别集合，null时自动提取
	 * @param extend              查询扩展模型(含代码级计算器配置)
	 * @return true表示计算过程改变了列结构(旋转、环比插入新列等)，映射map或VO前需注意
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
	 * 建立列名称跟列index的对应关系
	 * 
	 * @param fields 列名称数组(可带"name:alias"别名形式，取别名部分)
	 * @return 小写列名与列下标(从0开始)的对照模型
	 */
	private static LabelIndexModel wrapLabelIndexMap(String[] fields) {
		LabelIndexModel result = new LabelIndexModel();
		if (fields != null && fields.length > 0) {
			String realLabelName;
			int index;
			for (int i = 0, n = fields.length; i < n; i++) {
				realLabelName = fields[i].toLowerCase(Locale.ROOT);
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
	 * 根据查询结果的类型，构造相应对象集合(增加map形式的结果返回机制)
	 * 
	 * @param sqlToyContext     sqltoy上下文
	 * @param queryResultRows   查询结果的二维行数据集合
	 * @param labelNames        列名称数组
	 * @param resultType        目标结果类型(List/数组/Map/VO/基本类型)
	 * @param changedCols       true表示计算已改变列结构，不支持转map或VO
	 * @param humpMapLabel      Map结果key是否驼峰处理，null时取sqltoy全局配置
	 * @param hiberarchy        返回结果是否按层次化对象封装
	 * @param hiberarchyClasses
	 * @param fieldsMap
	 * @return 按resultType封装后的结果集合，无数据或resultType为null时原样返回
	 * @throws Exception
	 */
	public static List wrapQueryResult(SqlToyContext sqlToyContext, List queryResultRows, String[] labelNames,
			String[] columnTypes, Class resultType, boolean changedCols, Boolean humpMapLabel, boolean hiberarchy,
			Class[] hiberarchyClasses, Map<Class, IgnoreKeyCaseMap<String, String>> fieldsMap) throws Exception {
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
			logger.warn(
					"the query contains pivot-like or column-on-column ratio calculations which make the number of result 'columns' unstable, so converting to map or VO object is not supported!");
			SqlExecuteStat.debug("mapping result type error",
					"the query contains pivot-like or column-on-column ratio calculations which make the number of result 'columns' unstable, so converting to map or VO object is not supported!");
		}
		if (null == labelNames) {
			throw new DataAccessException("wrapQueryResult: labelNames is null when wrapping data to ["
					+ resultType.getTypeName() + "], can not provide property name mapping!");
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
					// 这里支持IgnoreKeyCaseMap等类型
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
					convertRealProps(wrapMapFields(labelNames, fieldsMap, resultType), columnFieldMap), columnTypes,
					resultType);
			// update 2021-11-16 支持VO或POJO 属性上@Translate注解,进行缓存翻译
			wrapResultTranslate(sqlToyContext, result, resultType);
		} else {
			// 内部完成了wrapResultTranslate行为
			result = hiberarchySet(sqlToyContext, entityMeta, columnFieldMap, queryResultRows, labelNames, columnTypes,
					resultType, cascadeModel, hiberarchyClasses, fieldsMap);
		}
		return result;
	}

	/**
	 * 提取二维集合第一列数据转换类型变成一维List返回
	 * 
	 * @param <T>       目标元素类型
	 * @param rows      二维数据集合，null或空返回空List
	 * @param classType 目标元素类型
	 * @return 第一列数据按目标类型转换后的一维List，转换失败抛出DataAccessException
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
				result.add((T) BeanUtil.convertType(cell, JdbcTypes.OTHER, typeValue, typeName));
			}
			return result;
		} catch (Exception e) {
			throw new DataAccessException(
					"error occurred during type conversion of the extracted single column query result!"
							+ e.getMessage());
		}
	}

	/**
	 * 解决DTO或POJO上存在@aliasName将sql字段名称跟类属性名称建立的对应关系(非简单的去除下划线骆驼命名规则)
	 * 
	 * @param labelNames  列名称数组
	 * @param colFieldMap 数据库列名与对象属性的对照映射，null或空时原样返回
	 * @return 替换为实际对象属性名后的数组
	 */
	private static String[] convertRealProps(String[] labelNames, HashMap<String, String> colFieldMap) {
		String[] result = labelNames.clone();
		if (colFieldMap != null && !colFieldMap.isEmpty()) {
			String key;
			for (int i = 0; i < result.length; i++) {
				key = result[i].toLowerCase(Locale.ROOT);
				if (colFieldMap.containsKey(key)) {
					result[i] = colFieldMap.get(key);
				}
			}
		}
		return result;
	}

	/**
	 * 将集合数据反射到java对象并建立层次关系
	 * 
	 * @param sqlToyContext     sqltoy上下文
	 * @param entityMeta        实体对象元数据，非实体类型为null
	 * @param columnFieldMap    数据库列名与对象属性的对照映射
	 * @param queryResultRows   查询结果的二维行数据集合
	 * @param labelNames        列名称数组
	 * @param resultType        目标主对象类型
	 * @param cascadeModels     级联配置模型(oneToOne、oneToMany)
	 * @param hiberarchyClasses 指定参与层次封装的级联对象类型
	 * @param fieldsMap         sql查询label与对象属性的映射关系
	 * @return 建立了一对一、一对多层关系的对象集合
	 * @throws Exception
	 */
	private static List hiberarchySet(SqlToyContext sqlToyContext, EntityMeta entityMeta,
			HashMap<String, String> columnFieldMap, List queryResultRows, String[] labelNames, String[] columnTypes,
			Class resultType, List<TableCascadeModel> cascadeModels, Class[] hiberarchyClasses,
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
				convertRealProps(wrapMapFields(labelNames, fieldsMap, resultType), columnFieldMap), columnTypes,
				resultType);
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
							columnTypes, cascade.getMappedType());
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
						columnTypes, oneToManyClass);
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
	 * 提取单个级联模型的分组字段对应的查询结果列
	 * 
	 * @param cascade     级联配置模型，null返回null
	 * @param labelIndexs 属性名与列下标的对照关系(忽略大小写)
	 * @return 分组字段对应的列下标数组，字段不存在于查询结果时抛出DataAccessException
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
				throw new DataAccessException("hierarchy wrapping: the query result does not contain the value of the "
						+ cascadeType + " group property (object property name, normally without underscore) ["
						+ groupFields[i] + "]!");
			}
		}
		return colIndexs;
	}

	/**
	 * 判断并获取oneToMany的级联配置
	 * 
	 * @param cascadeModels     级联配置模型列表
	 * @param hiberarchyClasses 指定参与层次封装的级联对象类型，null时取第一个oneToMany
	 * @return oneToMany级联配置，不存在返回null；存在多个且未指定类型抛出IllegalArgumentException
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
			throw new IllegalArgumentException(
					"multiple oneToMany mappings exist when returning the hierarchy result, hiberarchyClasses must be specified!");
		}
		return oneToMany;
	}

	/**
	 * 针对具体映射对象设置sql查询的label对应的对象属性
	 * 
	 * @param labelNames          列名称数组
	 * @param resultTypeFieldsMap 各结果类型与其label属性映射的对照表，null或空时原样返回
	 * @param resultType          目标结果类型
	 * @return 映射替换后的属性名称数组(冲突的label改名为xxxSqlToyIgnoreField以跳过映射)
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
	 * 根据分组字段将集合分组
	 * 
	 * @param queryResultRows 查询结果的二维行数据集合
	 * @param groupIndexes    分组列的下标数组
	 * @return key为分组列值拼接串、value为同组行数据列表的LinkedHashMap(保持出现顺序)
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
	 * 加工字段名称，将数据库sql查询的columnName转成对应对象的属性名称(去除下划线)
	 * 
	 * @param labelNames  数据库查询结果的列名数组，null返回null
	 * @param colFieldMap 数据库列名与对象属性的对照映射，命中时优先使用映射，null时按驼峰转换
	 * @return 首字母小写的驼峰属性名数组，与输入数组等长且位置对应
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
				result[i] = colFieldMap.get(labelNames[i].toLowerCase(Locale.ROOT));
				if (result[i] == null) {
					result[i] = StringUtil.toHumpStr(labelNames[i], false);
				}
			}
		}
		return result;
	}

	/**
	 * 警告性日志记录,凡是单次获取超过一定规模数据的操作记录日志
	 * 
	 * @param sqlToyConfig sql配置信息(用于输出sqlId和sql内容)
	 * @param totalCount   实际提取的数据总行数
	 */
	private static void warnLog(SqlToyConfig sqlToyConfig, int totalCount) {
		logger.warn("Large Result:totalCount={},sqlId={},sql={}", totalCount, sqlToyConfig.getId(),
				sqlToyConfig.getSql(null));
	}

	/**
	 * 组织因做缓存翻译、link、日期和数字格式化改变类型为VARCHAR的列
	 * 
	 * @param sqlToyConfig sql配置信息
	 * @return 需要按字符串处理的列名小写集合
	 */
	private static Set<String> getStringColumns(SqlToyConfig sqlToyConfig) {
		Set<String> strSet = new HashSet<String>();
		// 本身key是小写
		if (sqlToyConfig.getTranslateMap() != null && !sqlToyConfig.getTranslateMap().isEmpty()) {
			strSet.addAll(sqlToyConfig.getTranslateMap().keySet());
		}
		if (sqlToyConfig.getLinkModel() != null) {
			for (String col : sqlToyConfig.getLinkModel().getColumns()) {
				strSet.add(col.toLowerCase(Locale.ROOT));
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
	 * 对返回POJO(或DTO)含@Translate 配置的结果进行缓存翻译处理，通过key属性的值翻译成名称反射到当前名称属性上
	 * 
	 * @param sqlToyContext sqltoy上下文
	 * @param result        单个对象或对象集合，直接在其上回写翻译结果
	 * @param resultType    对象类型(用于提取@Translate注解配置)
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
		// 提取可批量获取的动态缓存翻译配置
		BatchDynamicCache batchDynamicCache = TranslateUtils.getBatchTranslates(sqlToyContext, fieldTranslateHandlers);
		// 批量翻译，暂停逐行动态获取缓存数据
		DynamicCacheHolder dynamicCacheHolder = new DynamicCacheHolder(batchDynamicCache.getCacheAndTypeForRealMap(),
				batchDynamicCache.getCacheAndTypeForRealType(), batchDynamicCache.getDynamicCaches());
		// 逐行翻译
		for (int i = 0, n = voList.size(); i < n; i++) {
			wrapBeanTranslate(dynamicCacheFetch, dynamicCacheHolder, fieldTranslateHandlers, voList.get(i));
		}
		// 对集合进行批量获取未匹配的缓存数据进行翻译
		TranslateUtils.translateDTOListByDynamicCache(sqlToyContext.getTranslateManager(), batchDynamicCache,
				dynamicCacheHolder, dynamicCacheFetch, voList);
	}

	/**
	 * 处理基于pojo或dto上@Translate注解，进行实际缓存调用给属性赋值
	 * 
	 * @param dynamicCacheFetch      动态缓存数据抓取接口
	 * @param fieldTranslateHandlers 翻译字段缓存配置(key为翻译目标属性名)
	 * @param item                   待翻译的单个对象，直接在其上回写翻译结果
	 */
	private static void wrapBeanTranslate(DynamicCacheFetch dynamicCacheFetch, DynamicCacheHolder dynamicCacheHolder,
			HashMap<String, FieldTranslateCacheHolder> fieldTranslateHandlers, Object item) {
		fieldTranslateHandlers.forEach((fieldName, fieldTranslateHandler) -> {
			Object srcFieldValue = BeanUtil.getProperty(item, fieldTranslateHandler.getKeyField());
			Object fieldValue = BeanUtil.getProperty(item, fieldName);
			if (srcFieldValue != null && !"".equals(srcFieldValue.toString()) && fieldValue == null) {
				BeanUtil.setProperty(item, fieldName, fieldTranslateHandler.getBeanCacheValue(dynamicCacheFetch,
						dynamicCacheHolder, item, srcFieldValue.toString()));
			}
		});
	}
}
