package org.sagacity.sqltoy.dialect.utils;

import java.io.Serializable;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;

import org.sagacity.sqltoy.SqlExecuteStat;
import org.sagacity.sqltoy.SqlToyConstants;
import org.sagacity.sqltoy.SqlToyContext;
import org.sagacity.sqltoy.SqlToyThreadDataHolder;
import org.sagacity.sqltoy.callback.DecryptHandler;
import org.sagacity.sqltoy.callback.PreparedStatementResultHandler;
import org.sagacity.sqltoy.callback.ReflectPropsHandler;
import org.sagacity.sqltoy.callback.ThreeBiConsumer;
import org.sagacity.sqltoy.callback.UpdateRowCallback;
import org.sagacity.sqltoy.callback.UpdateRowHandler;
import org.sagacity.sqltoy.config.SqlConfigParseUtils;
import org.sagacity.sqltoy.config.model.DataVersionConfig;
import org.sagacity.sqltoy.config.model.EntityMeta;
import org.sagacity.sqltoy.config.model.FieldMeta;
import org.sagacity.sqltoy.config.model.GeneratedType;
import org.sagacity.sqltoy.config.model.OperateType;
import org.sagacity.sqltoy.config.model.SqlToyConfig;
import org.sagacity.sqltoy.config.model.SqlToyResult;
import org.sagacity.sqltoy.config.model.SqlType;
import org.sagacity.sqltoy.config.model.SqlWithAnalysis;
import org.sagacity.sqltoy.exception.DataAccessException;
import org.sagacity.sqltoy.model.ColumnMeta;
import org.sagacity.sqltoy.model.JdbcTypes;
import org.sagacity.sqltoy.model.LockMode;
import org.sagacity.sqltoy.model.QueryExecutor;
import org.sagacity.sqltoy.model.QueryResult;
import org.sagacity.sqltoy.model.TableMeta;
import org.sagacity.sqltoy.model.inner.QueryExecutorExtend;
import org.sagacity.sqltoy.plugins.IUnifyFieldsHandler;
import org.sagacity.sqltoy.plugins.TypeHandler;
import org.sagacity.sqltoy.translate.DynamicCacheFetch;
import org.sagacity.sqltoy.translate.model.DynamicCacheHolder;
import org.sagacity.sqltoy.utils.BeanUtil;
import org.sagacity.sqltoy.utils.DataSourceUtils;
import org.sagacity.sqltoy.utils.DataSourceUtils.DBType;
import org.sagacity.sqltoy.utils.DateUtil;
import org.sagacity.sqltoy.utils.NumberUtil;
import org.sagacity.sqltoy.utils.ReservedWordsUtil;
import org.sagacity.sqltoy.utils.ResultUtils;
import org.sagacity.sqltoy.utils.SqlUtil;
import org.sagacity.sqltoy.utils.SqlUtilsExt;
import org.sagacity.sqltoy.utils.StringUtil;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * @project sagacity-sqltoy
 * @description 提供默认方言的通用处理工具
 * @author zhongxuchen
 * @version v1.0,Date:2021-05-20
 * @modify Date:2024-08-08 修复updateSaveFetch中uniqueProps对应属性值为null时构建的sql where
 *         id=null改为id is null
 */
public class DefaultDialectUtils {
	private final static Logger logger = LoggerFactory.getLogger(DefaultDialectUtils.class);

	/**
	 * 取随机记录
	 * 
	 * @param sqlToyContext
	 * @param sqlToyConfig
	 * @param queryExecutor
	 * @param decryptHandler
	 * @param totalCount
	 * @param randomCount
	 * @param conn
	 * @param dbType
	 * @param dialect
	 * @param fetchSize
	 * @param maxRows
	 * @return
	 * @throws Exception
	 */
	public static QueryResult getRandomResult(SqlToyContext sqlToyContext, SqlToyConfig sqlToyConfig,
			QueryExecutor queryExecutor, final DecryptHandler decryptHandler, Long totalCount, Long randomCount,
			Connection conn, final Integer dbType, final String dialect, final int fetchSize, final int maxRows)
			throws Exception {
		// select * from table order by rand() limit :randomCount 性能比较差,通过产生rand()
		// row_number 再排序方式性能稍好 同时也可以保证通用性
		StringBuilder sql = new StringBuilder();
		String innerSql = sqlToyConfig.isHasFast() ? sqlToyConfig.getFastSql(dialect) : sqlToyConfig.getSql(dialect);
		if (sqlToyConfig.isHasFast()) {
			sql.append(sqlToyConfig.getFastPreSql(dialect));
			if (!sqlToyConfig.isIgnoreBracket()) {
				sql.append(" (");
			}
		}
		// 存在with语句,将with部分剥离置前,避免replaceFirst将随机列误插入with定义内部
		if (sqlToyConfig.isHasWith()) {
			SqlWithAnalysis sqlWith = new SqlWithAnalysis(innerSql);
			sql.append(sqlWith.getWithSql());
			innerSql = sqlWith.getRejectWithSql();
		}
		// 给原始sql标记上特殊的开始和结尾，便于sql拦截器快速定位到原始sql并进行条件补充
		innerSql = SqlUtilsExt.markOriginalSql(innerSql);
		// update 2026-9-10 移除sag_row_number随机列,改派生表+order by rand():原形态将随机列
		// 混入结果集(注入形态居首/包裹形态亦含),单值映射(findRandom的resultType=String/Long
		// 等按第1列取值)读到随机数而非业务列,多列行更触发ArrayList cannot be cast(mysql系
		// 实测);order by rand() limit n为mysql/clickhouse/impala系取随机行的标准形态,结果集
		// 与原始sql列完全一致,VO/Map映射也不再混入sag_row_number键;派生表包裹天然中和
		// 内层order by/union(原hasOrderByOrUnion双分支统一为单一包裹形态)
		sql.append("select " + SqlToyConstants.INTERMEDIATE_TABLE1 + ".* from (");
		sql.append(innerSql);
		sql.append(" )  as " + SqlToyConstants.INTERMEDIATE_TABLE1);
		sql.append(" order by rand() limit ");
		sql.append(randomCount);
		if (sqlToyConfig.isHasFast()) {
			if (!sqlToyConfig.isIgnoreBracket()) {
				sql.append(") ");
			}
			sql.append(sqlToyConfig.getFastTailSql(dialect));
		}
		SqlToyResult queryParam = DialectUtils.wrapPageSqlParams(sqlToyContext, sqlToyConfig, queryExecutor,
				sql.toString(), null, null, dialect);
		QueryExecutorExtend extend = queryExecutor.getInnerModel();
		// 增加sql执行拦截器 update 2022-9-10
		queryParam = DialectUtils.doInterceptors(sqlToyContext, sqlToyConfig,
				(extend.entityClass == null) ? OperateType.random : OperateType.singleTable, queryParam,
				extend.entityClass, dbType);
		return DialectUtils.findBySql(sqlToyContext, sqlToyConfig, queryParam.getSql(), queryParam.getParamsValue(),
				extend, decryptHandler, conn, dbType, 0, fetchSize, maxRows);
	}

	/**
	 * 分页查询
	 * 
	 * @param sqlToyContext
	 * @param sqlToyConfig
	 * @param queryExecutor
	 * @param pageNo
	 * @param pageSize
	 * @param conn
	 * @param dbType
	 * @param dialect
	 * @param fetchSize
	 * @param maxRows
	 * @return
	 * @throws Exception
	 */
	public static QueryResult findPageBySql(SqlToyContext sqlToyContext, SqlToyConfig sqlToyConfig,
			QueryExecutor queryExecutor, final DecryptHandler decryptHandler, Long pageNo, Integer pageSize,
			Connection conn, final Integer dbType, final String dialect, final int fetchSize, final int maxRows)
			throws Exception {
		StringBuilder sql = new StringBuilder();
		String innerSql = sqlToyConfig.isHasFast() ? sqlToyConfig.getFastSql(dialect) : sqlToyConfig.getSql(dialect);
		// 给原始sql标记上特殊的开始和结尾，便于sql拦截器快速定位到原始sql并进行条件补充
		innerSql = SqlUtilsExt.markOriginalSql(innerSql);
		boolean isNamed = sqlToyConfig.isNamedParam();
		if (sqlToyConfig.isHasFast()) {
			sql.append(sqlToyConfig.getFastPreSql(dialect));
			if (!sqlToyConfig.isIgnoreBracket()) {
				sql.append(" (");
			}
		}
		sql.append(innerSql);
		sql.append(" limit ");
		sql.append(isNamed ? ":" + SqlToyConstants.PAGE_FIRST_PARAM_NAME : "?");
		boolean useDefault = true;
		// 未匹配数据库类型
		if (dbType == DBType.UNDEFINE && !sqlToyContext.isDefaultPageOffset()) {
			useDefault = false;
		}
		if (useDefault) {
			sql.append(" offset ");
		} else {
			sql.append(" , ");
		}
		sql.append(isNamed ? ":" + SqlToyConstants.PAGE_LAST_PARAM_NAME : "?");
		if (sqlToyConfig.isHasFast()) {
			if (!sqlToyConfig.isIgnoreBracket()) {
				sql.append(") ");
			}
			sql.append(sqlToyConfig.getFastTailSql(dialect));
		}
		SqlToyResult queryParam;
		// 将Long类型尽量转Integer，避免部分数据库setLong不支持(hive)
		Long startIndex = (pageNo - 1) * pageSize;
		Object start = startIndex.intValue();
		if (startIndex > Integer.MAX_VALUE) {
			start = startIndex;
		}
		// limit ? offset ?模式
		if (useDefault) {
			queryParam = DialectUtils.wrapPageSqlParams(sqlToyContext, sqlToyConfig, queryExecutor, sql.toString(),
					pageSize, start, dialect);
		} else {
			queryParam = DialectUtils.wrapPageSqlParams(sqlToyContext, sqlToyConfig, queryExecutor, sql.toString(),
					start, pageSize, dialect);
		}
		QueryExecutorExtend extend = queryExecutor.getInnerModel();
		// 增加sql执行拦截器 update 2022-9-10
		queryParam = DialectUtils.doInterceptors(sqlToyContext, sqlToyConfig,
				(extend.entityClass == null) ? OperateType.page : OperateType.singleTable, queryParam,
				extend.entityClass, dbType);
		return DialectUtils.findBySql(sqlToyContext, sqlToyConfig, queryParam.getSql(), queryParam.getParamsValue(),
				extend, decryptHandler, conn, dbType, 0, fetchSize, maxRows);
	}

	/**
	 * 实现top记录查询
	 * 
	 * @param sqlToyContext
	 * @param sqlToyConfig
	 * @param queryExecutor
	 * @param topSize
	 * @param conn
	 * @param dbType
	 * @param dialect
	 * @param fetchSize
	 * @param maxRows
	 * @return
	 * @throws Exception
	 */
	public static QueryResult findTopBySql(SqlToyContext sqlToyContext, SqlToyConfig sqlToyConfig,
			QueryExecutor queryExecutor, final DecryptHandler decryptHandler, Integer topSize, Connection conn,
			final Integer dbType, final String dialect, final int fetchSize, final int maxRows) throws Exception {
		StringBuilder sql = new StringBuilder();
		String innerSql = sqlToyConfig.isHasFast() ? sqlToyConfig.getFastSql(dialect) : sqlToyConfig.getSql(dialect);
		// 给原始sql标记上特殊的开始和结尾，便于sql拦截器快速定位到原始sql并进行条件补充
		innerSql = SqlUtilsExt.markOriginalSql(innerSql);
		if (sqlToyConfig.isHasFast()) {
			sql.append(sqlToyConfig.getFastPreSql(dialect));
			if (!sqlToyConfig.isIgnoreBracket()) {
				sql.append(" (");
			}
		}
		sql.append(innerSql);
		sql.append(" limit ");
		sql.append(topSize);
		if (sqlToyConfig.isHasFast()) {
			if (!sqlToyConfig.isIgnoreBracket()) {
				sql.append(") ");
			}
			sql.append(sqlToyConfig.getFastTailSql(dialect));
		}
		SqlToyResult queryParam = DialectUtils.wrapPageSqlParams(sqlToyContext, sqlToyConfig, queryExecutor,
				sql.toString(), null, null, dialect);
		QueryExecutorExtend extend = queryExecutor.getInnerModel();
		// 增加sql执行拦截器 update 2022-9-10
		queryParam = DialectUtils.doInterceptors(sqlToyContext, sqlToyConfig,
				(extend.entityClass == null) ? OperateType.top : OperateType.singleTable, queryParam,
				extend.entityClass, dbType);
		return DialectUtils.findBySql(sqlToyContext, sqlToyConfig, queryParam.getSql(), queryParam.getParamsValue(),
				extend, decryptHandler, conn, dbType, 0, fetchSize, maxRows);
	}

	/**
	 * 批量删除对象
	 * 
	 * @param sqlToyContext
	 * @param entities
	 * @param batchSize
	 * @param conn
	 * @param dbType
	 * @param autoCommit
	 * @param tableName
	 * @return
	 * @throws Exception
	 */
	public static Long deleteAll(SqlToyContext sqlToyContext, List<?> entities, final int batchSize, Connection conn,
			final Integer dbType, final Boolean autoCommit, final String tableName) throws Exception {
		if (null == entities || entities.isEmpty()) {
			return 0L;
		}
		EntityMeta entityMeta = sqlToyContext.getEntityMeta(entities.get(0).getClass());
		String realTable = entityMeta.getSchemaTable(tableName, dbType);
		if (null == entityMeta.getIdArray() || entityMeta.getIdArray().length == 0) {
			throw new IllegalArgumentException("delete/deleteAll operation, table [" + realTable
					+ "] has no primary key, please check the table design!");
		}
		int idSize = entityMeta.getIdArray().length;
		// 构造delete 语句
		StringBuilder deleteSql = new StringBuilder();
		// clickhouse 删除语法特殊
		if (dbType == DBType.CLICKHOUSE) {
			deleteSql.append("alter table ");
			deleteSql.append(realTable);
			deleteSql.append(" delete where ");
		} else {
			deleteSql.append("delete from ");
			deleteSql.append(realTable);
			deleteSql.append(" where ");
		}
		String field;
		SqlToyResult sqlToyResult = null;
		String colName;
		// 单主键
		if (idSize == 1) {
			Object[] idValues = BeanUtil.sliceToArray(entities, entityMeta.getIdArray()[0]);
			if (idValues == null || idValues.length == 0) {
				throw new IllegalArgumentException(
						tableName + " deleteAll method must assign value for pk field:" + entityMeta.getIdArray()[0]);
			}
			field = entityMeta.getIdArray()[0];
			colName = ReservedWordsUtil.convertWord(entityMeta.getColumnName(field), dbType);
			deleteSql.append(colName);
			deleteSql.append(" in (?) ");
			sqlToyResult = SqlConfigParseUtils.processSql(deleteSql.toString(), null, new Object[] { idValues }, null);
		} else {
			List<Object[]> idValues = BeanUtil.reflectBeansToInnerAry(entities, entityMeta.getIdArray(), null, null);
			int dataSize = idValues.size();
			Object[] rowData;
			Object cellValue;
			// 将条件构造成一个数组
			Object[] realValues = new Object[idValues.size() * idSize];
			int index = 0;
			for (int i = 0; i < dataSize; i++) {
				rowData = idValues.get(i);
				for (int j = 0; j < idSize; j++) {
					cellValue = rowData[j];
					// 验证主键值是否合法
					if (StringUtil.isBlank(cellValue)) {
						throw new IllegalArgumentException(tableName + " deleteAll method must assign value for pk,row:"
								+ i + " pk field:" + entityMeta.getIdArray()[j]);
					}
					realValues[index] = cellValue;
					index++;
				}
			}
			// 复合主键构造 (field1=? and field2=?)
			String condition = " (";
			for (int i = 0, n = idSize; i < n; i++) {
				field = entityMeta.getIdArray()[i];
				colName = ReservedWordsUtil.convertWord(entityMeta.getColumnName(field), dbType);
				if (i > 0) {
					condition = condition.concat(" and ");
				}
				condition = condition.concat(colName).concat("=?");
			}
			condition = condition.concat(")");
			// 构造 (field1=? and field2=?) or (field1=? and field2=?)
			for (int i = 0; i < dataSize; i++) {
				if (i > 0) {
					deleteSql.append(" or ");
				}
				deleteSql.append(condition);
			}
			sqlToyResult = SqlConfigParseUtils.processSql(deleteSql.toString(), null, realValues, null);
		}
		// 增加sql执行拦截器 update 2022-9-10
		SqlToyConfig sqlToyConfig = new SqlToyConfig(DataSourceUtils.getDialect(dbType));
		sqlToyConfig.setSqlType(SqlType.delete);
		sqlToyConfig.setSql(sqlToyResult.getSql());
		sqlToyResult = DialectUtils.doInterceptors(sqlToyContext, sqlToyConfig, OperateType.execute, sqlToyResult,
				entities.get(0).getClass(), dbType);
		return SqlUtil.executeSql(sqlToyContext.getTypeHandler(), sqlToyResult.getSql(), sqlToyResult.getParamsValue(),
				null, conn, dbType, autoCommit, false);
	}

	public static Serializable updateSaveFetch(final SqlToyContext sqlToyContext, final Serializable entity,
			final UpdateRowHandler updateRowHandler, String[] uniqueProps, final Connection conn, final Integer dbType,
			String dialect, String tableName, int lockWaitTimeout) throws Exception {
		return updateSaveFetch(sqlToyContext, entity, updateRowHandler, null, uniqueProps, conn, dbType, dialect,
				tableName, lockWaitTimeout);
	}

	public static Serializable updateSaveFetch(final SqlToyContext sqlToyContext, final Serializable entity,
			final UpdateRowCallback updateRowCallback, String[] uniqueProps, final Connection conn,
			final Integer dbType, String dialect, String tableName, int lockWaitTimeout) throws Exception {
		return updateSaveFetch(sqlToyContext, entity, null, updateRowCallback, uniqueProps, conn, dbType, dialect,
				tableName, lockWaitTimeout);
	}

	/**
	 * 实现：1、锁查询；2、记录存在则修改；3、记录不存在则执行insert；4、返回修改或插入的记录信息，尽量不要使用identity、sequence主键
	 * 
	 * @param sqlToyContext
	 * @param entity
	 * @param updateRowHandler
	 * @param uniqueProps
	 * @param conn
	 * @param dbType
	 * @param dialect
	 * @param tableName
	 * @return
	 * @throws Exception
	 */
	public static Serializable updateSaveFetch(final SqlToyContext sqlToyContext, final Serializable entity,
			final UpdateRowHandler updateRowHandler, final UpdateRowCallback updateRowCallback, String[] uniqueProps,
			final Connection conn, final Integer dbType, String dialect, String tableName, int lockWaitTimeout)
			throws Exception {
		final EntityMeta entityMeta = sqlToyContext.getEntityMeta(entity.getClass());
		// 条件字段
		String[] whereFields = uniqueProps;
		if (whereFields == null || whereFields.length == 0) {
			whereFields = entityMeta.getIdArray();
		}
		if (whereFields == null || whereFields.length == 0) {
			throw new DataAccessException("updateSaveFetch table [" + tableName
					+ "] has no condition fields to uniquely fetch a single record, please check!");
		}
		// 全部字段的值
		Object[] tempFieldValues = null;
		// 条件字段值
		Object[] whereParamValues = BeanUtil.reflectBeanToAry(entity, whereFields);
		Object tmpVersionValue = null;
		// 提取数据版本字段的值
		if (entityMeta.getDataVersion() != null) {
			tmpVersionValue = BeanUtil.getProperty(entity, entityMeta.getDataVersion().getField());
		}
		for (int i = 0; i < whereParamValues.length; i++) {
			// 唯一性属性值存在空，则表示首次插入
			if (StringUtil.isBlank(whereParamValues[i])) {
				// 调用默认值、主键策略等
				tempFieldValues = processFieldValues(sqlToyContext, entityMeta, entity, false);
				// 重新反射获取主键等字段值
				whereParamValues = BeanUtil.reflectBeanToAry(entity, whereFields);
				break;
			}
		}
		// 统一字段赋值处理
		IUnifyFieldsHandler unifyFieldsHandler = SqlToyThreadDataHolder.useUnifyFields()
				? sqlToyContext.getUnifyFieldsHandler()
				: null;
		final Object[] fieldValues = tempFieldValues;
		final Object entityVersion = tmpVersionValue;
		TypeHandler typeHandler = sqlToyContext.getTypeHandler();
		final boolean hasUpdateRow = (updateRowHandler == null && updateRowCallback == null) ? false : true;
		// 组织select * from table for update 语句
		SqlToyResult queryParam = wrapFetchSql(entityMeta, dbType, whereFields, whereParamValues, tableName,
				lockWaitTimeout);
		// 增加sql执行拦截器 update 2022-9-10
		queryParam = DialectUtils.doInterceptors(sqlToyContext, null, OperateType.singleTable, queryParam,
				entity.getClass(), dbType);
		SqlExecuteStat.showSql("execute lock records query", queryParam.getSql(), queryParam.getParamsValue());
		// 可编辑结果集
		PreparedStatement pst = conn.prepareStatement(queryParam.getSql(), ResultSet.TYPE_FORWARD_ONLY,
				ResultSet.CONCUR_UPDATABLE);
		// 设置全局statementTimeout，默认为null
		if (SqlToyConstants.defaultStatementTimeout != null && SqlToyConstants.defaultStatementTimeout > 0) {
			pst.setQueryTimeout(SqlToyConstants.defaultStatementTimeout);
		}
		DynamicCacheFetch dynamicCacheFetch = sqlToyContext.getDynamicCacheFetch();
		DynamicCacheHolder dynamicCacheHolder = new DynamicCacheHolder();
		List updateResult = (List) SqlUtil.preparedStatementProcess(queryParam.getParamsValue(), pst, null,
				new PreparedStatementResultHandler() {
					@Override
					public void execute(Object rowData, PreparedStatement pst, ResultSet rs) throws Exception {
						SqlUtil.setParamsValue(sqlToyContext.getTypeHandler(), conn, dbType, pst, (Object[]) rowData,
								null, 0);
						// 执行类似 select xxx from table for update(sqlserver语法有差异)
						ResultSet finalRs = pst.executeQuery();
						try {
							int rowCnt = finalRs.getMetaData().getColumnCount();
							// 列类型名与读取策略查询级一次预计算(结果集元数据恒定,置于行循环外;
							// oracle的vector/json列getObject直接报ORA-17004,TEXT_READ预分类改走getString)
							String[] usfTypeNames = ResultUtils.readColumnTypeNames(finalRs, rowCnt);
							int[] usfKinds = ResultUtils.buildColumnKinds(dbType, usfTypeNames);
							int index = 0;
							List result = new ArrayList();
							DataVersionConfig dataVersion = entityMeta.getDataVersion();
							final String dataVersionField = (dataVersion == null) ? null : dataVersion.getField();
							ThreeBiConsumer<String, String[], Object> setValConsumer = (updateRowHandler == null) ? null
									: (fieldName, forcedFieldNames, fieldValue) -> {
										// 排除dataVersionField字段避免被重复处理
										if (dataVersionField == null || !fieldName.equals(dataVersionField)) {
											Optional.ofNullable(entityMeta.getFieldMeta(fieldName))
													.ifPresent(fieldMeta -> {
														try {
															SqlUtilsExt.resultUpdate(typeHandler, conn, finalRs,
																	fieldMeta, fieldValue, dbType, false,
																	(forcedFieldNames != null && Arrays
																			.stream(forcedFieldNames)
																			.anyMatch(x -> fieldName != null
																					&& fieldName.equalsIgnoreCase(x))));
														} catch (Exception e) {
															throw new RuntimeException(e);
														}
													});
										}
									};
							while (finalRs.next()) {
								if (index > 0) {
									throw new DataAccessException(
											"updateSaveFetch can only operate on a single record, please check the uniqueProps setting!");
								}
								// 存在修改记录
								if (hasUpdateRow) {
									SqlExecuteStat.debug("execute updateRow",
											"record exists, invoke updateRowHandler.updateRow!");
									// 存在数据版本:1、校验当前的版本是否为null;2、对比传递过来的版本值跟数据库中的值是否一致；3、修改数据库中数据版本+1
									if (dataVersion != null) {
										String nowVersion = finalRs
												.getString(entityMeta.getColumnName(dataVersionField));
										if (nowVersion == null) {
											throw new IllegalArgumentException("table [" + entityMeta.getTableName()
													+ "] data version field [" + dataVersionField
													+ "] is null in database, unable to verify and update the version, please complete the version value of historical data!");
										}
										if (entityVersion != null && !entityVersion.toString().equals(nowVersion)) {
											throw new IllegalArgumentException("table [" + entityMeta.getTableName()
													+ "] has @DataVersion configuration, when updateSaveFetch updates, the property ["
													+ dataVersionField
													+ "] value does not equal the current value in database:"
													+ entityVersion + "<>" + nowVersion
													+ ", the data has been modified by others!");
										}
										// 以日期开头
										if (dataVersion.isStartDate()) {
											String nowDate = DateUtil.formatDate(DateUtil.getNowTime(),
													DateUtil.FORMAT.DATE_8CHAR);
											if (nowVersion.startsWith(nowDate)
													&& NumberUtil.isInteger(nowVersion.substring(8))) {
												nowVersion = nowDate + (Integer.parseInt(nowVersion.substring(8)) + 1);
											} else {
												nowVersion = nowDate + 1;
											}
										} else {
											nowVersion = "" + (Integer.parseInt(nowVersion.trim()) + 1);
										}
										// 修改数据版本
										SqlUtilsExt.resultUpdate(typeHandler, conn, finalRs,
												entityMeta.getFieldMeta(dataVersionField), nowVersion, dbType, false);
									}
									// 执行update反调，实现锁定行记录值的修改
									if (updateRowHandler != null) {
										updateRowHandler.updateRow(finalRs, index);
										updateRowHandler.updateRow(finalRs, index, (fieldName, fieldValue) -> {
											setValConsumer.accept(fieldName, null, fieldValue);
										});
										updateRowHandler.updateRow(finalRs, index,
												(fieldName, forcedFieldNames, fieldValue) -> {
													setValConsumer.accept(fieldName, forcedFieldNames, fieldValue);
												});
									} else if (updateRowCallback != null) {
										updateRowCallback.updateRow(typeHandler, dbType, conn, finalRs, index);
									}
									// 考虑公共字段修改
									if (unifyFieldsHandler != null && unifyFieldsHandler.updateUnifyFields() != null) {
										Map<String, Object> updateProps = unifyFieldsHandler.updateUnifyFields();
										String field;
										FieldMeta fieldMeta;
										Object fieldValue;
										for (Map.Entry<String, Object> entry : updateProps.entrySet()) {
											field = entry.getKey();
											fieldValue = entry.getValue();
											fieldMeta = entityMeta.getFieldMeta(field);
											// 存在公共的修改属性
											if (fieldMeta != null) {
												// 强制修改
												if (unifyFieldsHandler.forceUpdateFields() != null
														&& unifyFieldsHandler.forceUpdateFields().contains(field)) {
													SqlUtilsExt.resultUpdate(typeHandler, conn, finalRs, fieldMeta,
															fieldValue, dbType, false);
												} else {
													// 反射对象属性取值
													Object pojoFieldValue = BeanUtil.getProperty(entity, field);
													// 不为null，则以对象传递的值为准
													if (pojoFieldValue != null) {
														fieldValue = pojoFieldValue;
													}
													SqlUtilsExt.resultUpdate(typeHandler, conn, finalRs, fieldMeta,
															fieldValue, dbType, false);
												}
											}
										}
									}
									// 执行update
									finalRs.updateRow();
								}
								index++;
								// 重新获得修改后的值(列类型名与读取策略已在循环前查询级预计算)
								result.add(ResultUtils.processResultRow(dbType, typeHandler, dynamicCacheFetch,
										dynamicCacheHolder, finalRs, null, null, rowCnt, null, null, false,
										usfTypeNames, usfKinds, 0));
							}
							// 没有查询到记录，表示是需要首次插入
							if (index == 0) {
								SqlExecuteStat.debug("execute insertRow",
										"perform the first insert when the query does not match any result!");
								// 移到插入行
								finalRs.moveToInsertRow();
								FieldMeta fieldMeta;
								// 过滤掉计算列
								Object[] fullFieldvalues = (fieldValues == null)
										? processFieldValues(sqlToyContext, entityMeta, entity, false)
										: fieldValues;
								String[] fieldsArray = entityMeta.getFieldsArray(false);
								for (int i = 0; i < fieldsArray.length; i++) {
									fieldMeta = entityMeta.getFieldMeta(fieldsArray[i]);
									SqlUtilsExt.resultUpdate(typeHandler, conn, finalRs, fieldMeta, fullFieldvalues[i],
											dbType, true);
								}
								// 执行插入
								finalRs.insertRow();
							}
							this.setResult(result);
						} catch (Exception e) {
							throw e;
						} finally {
							if (finalRs != null) {
								finalRs.close();
							}
						}
					}
				});
		// 记录不存在首次保存，返回entity自身
		if (updateResult == null || updateResult.isEmpty()) {
			return entity;
		}
		List rowList = (List) updateResult.get(0);
		// 覆盖返回值
		// update 2026-9-10 传入字段注解的jdbcType:回读行值已归一为文本形态(json列=String),
		// 原OTHER类型转换不触发json→POJO/List反序列化,String直设对象属性报argument type
		// mismatch(vastbase G100真库updateSaveFetch的json对象列实爆,vector/geometry属性同理受益)
		String[] overrideFields = entityMeta.getFieldsArray(false);
		for (int i = 0; i < overrideFields.length; i++) {
			FieldMeta overrideFieldMeta = entityMeta.getFieldMeta(overrideFields[i]);
			BeanUtil.setProperty(entity, overrideFields[i], rowList.get(i),
					(overrideFieldMeta == null) ? JdbcTypes.OTHER : overrideFieldMeta.getType());
		}
		return entity;
	}

	/**
	 * 组织updateSaveFetch的锁查询sql
	 * 
	 * @param entityMeta
	 * @param dbType
	 * @param uniqueProps
	 * @param whereParamValues
	 * @param tableName
	 * @param lockWaitTimeout
	 * @return
	 */
	private static SqlToyResult wrapFetchSql(EntityMeta entityMeta, Integer dbType, String[] uniqueProps,
			Object[] whereParamValues, String tableName, int lockWaitTimeout) {
		String realTable = entityMeta.getSchemaTable(tableName, dbType);
		StringBuilder sql = new StringBuilder("select ");
		String columnName;
		String[] fieldsArray = entityMeta.getFieldsArray(false);
		for (int i = 0; i < fieldsArray.length; i++) {
			columnName = entityMeta.getColumnName(fieldsArray[i]);
			if (i > 0) {
				sql.append(",");
			}
			// 含关键字处理
			sql.append(ReservedWordsUtil.convertWord(columnName, dbType));
		}
		sql.append(" from ").append(realTable).append(" where ");
		int index = 0;
		List<Object> realParamValues = new ArrayList<>();
		for (String field : uniqueProps) {
			if (index > 0) {
				sql.append(" and ");
			}
			columnName = entityMeta.getColumnName(field);
			sql.append(ReservedWordsUtil.convertWord(columnName, dbType));
			// update 2024-8-7 rabbit 反馈,条件值为null的场景
			if (whereParamValues[index] == null) {
				sql.append(" is null ");
			} else {
				sql.append("=?");
				realParamValues.add(whereParamValues[index]);
			}
			index++;
		}
		String lastSql;
		// 设置锁
		if (dbType == DBType.SQLSERVER) {
			lastSql = SqlServerDialectUtils.lockSql(sql.toString(), realTable, LockMode.UPGRADE);
		} else if (dbType == DBType.DB2) {
			lastSql = sql.append(" for update with rs").toString();
		} else {
			lastSql = sql.append(" for update").toString();
		}
		// 设置锁等待超时时长(oracle、dm、oceanbase、kingbase)
		if (lockWaitTimeout > 0 && (dbType == DBType.ORACLE || dbType == DBType.ORACLE11 || dbType == DBType.DM
				|| dbType == DBType.KINGBASE || dbType == DBType.OCEANBASE)) {
			lastSql = lastSql.concat(" wait " + lockWaitTimeout);
		}
		return new SqlToyResult(lastSql, realParamValues.toArray());
	}

	/**
	 * 反射实体对象的属性值到数组，并调用主键策略产生主键值并写回到entity中
	 * 
	 * @param sqlToyContext
	 * @param entityMeta
	 * @param entity
	 * @param excludeGeneratedCols
	 * @return
	 * @throws Exception
	 */
	private static Object[] processFieldValues(final SqlToyContext sqlToyContext, EntityMeta entityMeta,
			Serializable entity, boolean excludeGeneratedCols) throws Exception {
		// 构造全新的新增记录参数赋值反射(覆盖之前的)，涉及数据版本、创建人、创建时间、租户等
		ReflectPropsHandler handler = DialectUtils.getAddReflectHandler(entityMeta, null,
				sqlToyContext.getUnifyFieldsHandler());
		handler = DialectUtils.getSecureReflectHandler(handler, sqlToyContext.getFieldsSecureProvider(),
				sqlToyContext.getDesensitizeProvider(), entityMeta.getSecureFields());
		int generatedColCnt = excludeGeneratedCols ? entityMeta.getGeneratedColsCnt() : 0;
		// 这里不体现defaultValue 值，产生的insert sql语句中已经处理了default值问题
		Object[] fullParamValues = BeanUtil.reflectBeanToAry(entity, entityMeta.getFieldsArray(excludeGeneratedCols),
				null, handler);
		// 主键采用assign方式赋予，则调用generator产生id并赋予其值
		boolean hasId = (entityMeta.getIdStrategy() != null && null != entityMeta.getIdGenerator()) ? true : false;
		// 是否存在业务ID
		boolean hasBizId = (entityMeta.getBusinessIdGenerator() == null) ? false : true;
		int bizIdColIndex = hasBizId ? entityMeta.getFieldIndex(entityMeta.getBusinessIdField()) - generatedColCnt : 0;
		// 主键、业务主键生成并回写对象
		if (hasId || hasBizId) {
			int pkIndex = entityMeta.getIdIndex() - generatedColCnt;
			Integer[] relatedColumn = entityMeta.getBizIdRelatedColIndex();
			Object[] relatedColValue = null;
			if (relatedColumn != null) {
				int relatedColumnSize = relatedColumn.length;
				relatedColValue = new Object[relatedColumnSize];
				for (int meter = 0; meter < relatedColumnSize; meter++) {
					relatedColValue[meter] = fullParamValues[relatedColumn[meter] - generatedColCnt];
					if (StringUtil.isBlank(relatedColValue[meter])) {
						throw new IllegalArgumentException("generate business id for entity ["
								+ entityMeta.getEntityClass().getName() + "], the related field ["
								+ entityMeta.getBizIdRelatedColumns()[meter] + "] value is null, please check!");
					}
				}
			}
			// 主键
			if (hasId && StringUtil.isBlank(fullParamValues[pkIndex])) {
				// id通过generator机制产生，设置generator产生的值
				fullParamValues[pkIndex] = entityMeta.getIdGenerator().getId(entityMeta.getTableName(),
						entityMeta.getBizIdSignature(), entityMeta.getBizIdRelatedColumns(), relatedColValue, null,
						entityMeta.getIdType(), entityMeta.getIdLength(), entityMeta.getBizIdSequenceSize());
				// 回写主键值
				BeanUtil.setProperty(entity, entityMeta.getIdArray()[0], fullParamValues[pkIndex]);
			}
			// 业务主键
			if (hasBizId && StringUtil.isBlank(fullParamValues[bizIdColIndex])) {
				String businessIdType = entityMeta.getColumnJavaType(entityMeta.getBusinessIdField());
				fullParamValues[bizIdColIndex] = entityMeta.getBusinessIdGenerator().getId(entityMeta.getTableName(),
						entityMeta.getBizIdSignature(), entityMeta.getBizIdRelatedColumns(), relatedColValue, null,
						businessIdType, entityMeta.getBizIdLength(), entityMeta.getBizIdSequenceSize());
				// 回写业务主键值
				BeanUtil.setProperty(entity, entityMeta.getBusinessIdField(), fullParamValues[bizIdColIndex]);
			}
		}
		// 回写数据版本号
		if (entityMeta.getDataVersion() != null) {
			String dataVersionField = entityMeta.getDataVersion().getField();
			int dataVersionIndex = entityMeta.getFieldIndex(dataVersionField) - generatedColCnt;
			BeanUtil.setProperty(entity, dataVersionField, fullParamValues[dataVersionIndex]);
		}
		return fullParamValues;
	}

	@SuppressWarnings("unchecked")
	public static List<ColumnMeta> getTableColumns(String catalog, String schema, String tableName, Connection conn,
			Integer dbType, String dialect) throws Exception {
		String realCatalog = SqlToyConstants.getDialectLowcaseStrategyName(catalog, dialect);
		String realSchema = SqlToyConstants.getDialectLowcaseStrategyName(schema, dialect);
		String realTableName = SqlToyConstants.getDialectLowcaseStrategyName(tableName, dialect);
		ResultSet rs = conn.getMetaData().getColumns(realCatalog, realSchema, realTableName, "%");
		// 通过preparedStatementProcess反调，第二个参数是pst
		List<ColumnMeta> tableCols = (List<ColumnMeta>) SqlUtil.preparedStatementProcess(null, null, rs,
				new PreparedStatementResultHandler() {
					@Override
					public void execute(Object rowData, PreparedStatement pst, ResultSet rs) throws Exception {
						List<ColumnMeta> colMetas = new ArrayList<ColumnMeta>();
						String isAutoIncrement;
						while (rs.next()) {
							ColumnMeta colMeta = new ColumnMeta();
							colMeta.setColName(rs.getString("COLUMN_NAME"));
							colMeta.setDataType(rs.getInt("DATA_TYPE"));
							colMeta.setTypeName(rs.getString("TYPE_NAME"));
							colMeta.setDefaultValue(SqlUtil.clearDefaultValue(rs.getString("COLUMN_DEF")));
							colMeta.setColumnSize(rs.getInt("COLUMN_SIZE"));
							colMeta.setDecimalDigits(rs.getInt("DECIMAL_DIGITS"));
							colMeta.setNumPrecRadix(rs.getInt("NUM_PREC_RADIX"));
							colMeta.setComments(StringUtil.escapeComment(readRemarks(rs, "REMARKS")));
							// colMeta.setReadOnly(rs.getBoolean("READ_ONLY"));
							colMeta.setAutoIncrement(false);
							// oracle autoincrement 取法不同
							if (dbType == DBType.ORACLE || dbType == DBType.ORACLE11) {
								if (colMeta.getDefaultValue() != null
										&& colMeta.getDefaultValue().toLowerCase(Locale.ROOT).endsWith(".nextval")) {
									colMeta.setAutoIncrement(true);
									colMeta.setDefaultValue(colMeta.getDefaultValue().replaceAll("\"", "\\\\\""));
								}
							} else {
								try {
									isAutoIncrement = rs.getString("IS_AUTOINCREMENT");
									if (isAutoIncrement != null && ("true".equalsIgnoreCase(isAutoIncrement)
											|| "YES".equalsIgnoreCase(isAutoIncrement)
											|| "Y".equalsIgnoreCase(isAutoIncrement) || "1".equals(isAutoIncrement))) {
										colMeta.setAutoIncrement(true);
									}
								} catch (Exception e) {
									// 部分驱动不支持IS_AUTOINCREMENT伪列,保持默认非自增
									logger.debug(
											"failed to read the IS_AUTOINCREMENT column info (the driver may not support it)!",
											e);
								}
							}
							// update 2026-9-11 计算列(生成列)识别:JDBC4.3可选伪列IS_GENERATEDCOLUMN
							// (mysql/pg/sqlserver/h2等驱动实测支持),命中置generatedType=STORED——
							// JDBC元数据无法区分VIRTUAL/STORED,统一按STORED语义(不可参与insert/update
							// 写链路,与实体注解GeneratedType.STORED的框架处理一致);sqlite-jdbc等
							// 无此伪列的驱动保持DEFAULT(0),VO侧可用@Column(generatedType=...)显式声明
							try {
								String isGenerated = rs.getString("IS_GENERATEDCOLUMN");
								if (isGenerated != null
										&& ("true".equalsIgnoreCase(isGenerated) || "YES".equalsIgnoreCase(isGenerated)
												|| "Y".equalsIgnoreCase(isGenerated) || "1".equals(isGenerated))) {
									colMeta.setGeneratedType(GeneratedType.STORED.getValue());
								}
							} catch (Exception e) {
								// 部分驱动不支持IS_GENERATEDCOLUMN伪列,保持默认非计算列
								logger.debug(
										"failed to read the IS_GENERATEDCOLUMN column info (the driver may not support it)!",
										e);
							}
							if (rs.getInt("NULLABLE") == 1) {
								colMeta.setNullable(true);
							} else {
								colMeta.setNullable(false);
							}
							colMetas.add(colMeta);
						}
						this.setResult(colMetas);
					}
				});
		ColumnMeta mapMeta;
		// 获取主键信息
		Map<String, ColumnMeta> pkMap = getTablePrimaryKeys(catalog, schema, tableName, conn, dbType, dialect);
		if (pkMap != null && !pkMap.isEmpty()) {
			for (ColumnMeta colMeta : tableCols) {
				mapMeta = pkMap.get(colMeta.getColName());
				if (mapMeta != null) {
					colMeta.setPK(true);
				}
			}
		}
		// 获取索引信息
		Map<String, ColumnMeta> indexsMap = getTableIndexes(catalog, schema, tableName, conn, dbType, dialect);
		if (indexsMap != null && !indexsMap.isEmpty()) {
			for (ColumnMeta colMeta : tableCols) {
				mapMeta = indexsMap.get(colMeta.getColName());
				if (mapMeta != null) {
					colMeta.setIndexName(mapMeta.getIndexName());
					colMeta.setUnique(mapMeta.isUnique());
					colMeta.setIndex(true);
				}
			}
		}
		return tableCols;
	}

	/**
	 * update 2026-9-11 备注列容错读取:oceanbase等mariadb系驱动的getTables将REMARKS以byte[]
	 * 返回(rs.getString被驱动toString为"[B@hash"形态,ob 4.3.5表备注实测),统一按UTF-8归一为文本
	 *
	 * @param rs         元数据结果集
	 * @param columnName 备注列名
	 * @return 备注文本,null保持null
	 * @throws SQLException
	 */
	static String readRemarks(ResultSet rs, String columnName) throws SQLException {
		Object raw = rs.getObject(columnName);
		if (raw == null) {
			return null;
		}
		if (raw instanceof byte[]) {
			return new String((byte[]) raw, java.nio.charset.StandardCharsets.UTF_8);
		}
		String str = raw.toString();
		// oceanbase驱动实测getObject已将byte[]备注预先toString为"[B@hash"形态,
		// 按字节重取并UTF-8解码还原(getBytes为字节访问器,不经过驱动的字符串转换)
		if (str.matches("\\[B@[0-9a-fA-F]+")) {
			byte[] bytes = rs.getBytes(columnName);
			if (bytes != null) {
				return new String(bytes, java.nio.charset.StandardCharsets.UTF_8);
			}
		}
		return str;
	}

	/**
	 * 获取表的索引信息(这里只能用于标记字段是否是索引列)
	 * 
	 * @param catalog
	 * @param schema
	 * @param tableName
	 * @param conn
	 * @param dbType
	 * @param dialect
	 * @return
	 * @throws Exception
	 */
	@SuppressWarnings("unchecked")
	public static Map<String, ColumnMeta> getTableIndexes(String catalog, String schema, String tableName,
			Connection conn, final Integer dbType, String dialect) throws Exception {
		String realCatalog = SqlToyConstants.getDialectLowcaseStrategyName(catalog, dialect);
		String realSchema = SqlToyConstants.getDialectLowcaseStrategyName(schema, dialect);
		String realTableName = SqlToyConstants.getDialectLowcaseStrategyName(tableName, dialect);
		if (dbType == DBType.ORACLE || dbType == DBType.ORACLE11) {
			return getOracleTableIndexes(realCatalog, realSchema, realTableName, conn, dbType, dialect);
		}
		Map<String, ColumnMeta> result = new HashMap<>();
		boolean[] uniqueAndNotUnique = { false, true };
		ResultSet rs;
		Map<String, ColumnMeta> tableIndexes;
		for (int i = 0; i < uniqueAndNotUnique.length; i++) {
			boolean isUnique = uniqueAndNotUnique[i];
			rs = conn.getMetaData().getIndexInfo(realCatalog, realSchema, realTableName, isUnique, false);
			tableIndexes = (Map<String, ColumnMeta>) SqlUtil.preparedStatementProcess(null, null, rs,
					new PreparedStatementResultHandler() {
						@Override
						public void execute(Object rowData, PreparedStatement pst, ResultSet rs) throws Exception {
							Map<String, ColumnMeta> indexsMeta = new HashMap<String, ColumnMeta>();
							while (rs.next()) {
								ColumnMeta colMeta = new ColumnMeta();
								colMeta.setColName(rs.getString("COLUMN_NAME"));
								colMeta.setIndex(true);
								colMeta.setUnique(isUnique);
								colMeta.setIndexName(rs.getString("INDEX_NAME"));
								indexsMeta.put(colMeta.getColName(), colMeta);
							}
							this.setResult(indexsMeta);
						}
					});
			if (tableIndexes != null) {
				result.putAll(tableIndexes);
			}
		}
		return result;
	}

	@SuppressWarnings("unchecked")
	private static Map<String, ColumnMeta> getOracleTableIndexes(String catalog, String schema, String tableName,
			Connection conn, final Integer dbType, String dialect) throws Exception {
		String tableNameUp = tableName.toUpperCase(Locale.ROOT);
		// 表名经?参数绑定,避免直接拼接形成注入面(同文件其他元数据查询一致)
		String sql = "SELECT t1.INDEX_NAME,t1.COLUMN_NAME,t0.UNIQUENESS FROM USER_IND_COLUMNS t1 LEFT JOIN "
				+ " (SELECT INDEX_NAME,UNIQUENESS FROM USER_INDEXES WHERE TABLE_NAME =?) t0 ON "
				+ " t1.INDEX_NAME = t0.INDEX_NAME WHERE TABLE_NAME =?";
		PreparedStatement pst = conn.prepareStatement(sql, ResultSet.TYPE_FORWARD_ONLY, ResultSet.CONCUR_READ_ONLY);
		pst.setString(1, tableNameUp);
		pst.setString(2, tableNameUp);
		ResultSet rs = pst.executeQuery();
		return (Map<String, ColumnMeta>) SqlUtil.preparedStatementProcess(null, pst, rs,
				new PreparedStatementResultHandler() {
					@Override
					public void execute(Object obj, PreparedStatement pst, ResultSet rs) throws SQLException {
						Map<String, ColumnMeta> indexsMeta = new HashMap<String, ColumnMeta>();
						while (rs.next()) {
							ColumnMeta colMeta = new ColumnMeta();
							colMeta.setColName(rs.getString("COLUMN_NAME"));
							colMeta.setIndex(true);
							if ("UNIQUE".equalsIgnoreCase(rs.getString("UNIQUENESS"))) {
								colMeta.setUnique(true);
							}
							colMeta.setIndexName(rs.getString("INDEX_NAME"));
							indexsMeta.put(colMeta.getColName(), colMeta);
						}
						this.setResult(indexsMeta);
					}
				});
	}

	/**
	 * 获取表的主键字段
	 * 
	 * @param catalog
	 * @param schema
	 * @param tableName
	 * @param conn
	 * @param dbType
	 * @param dialect
	 * @return
	 * @throws Exception
	 */
	@SuppressWarnings("unchecked")
	public static Map<String, ColumnMeta> getTablePrimaryKeys(String catalog, String schema, String tableName,
			Connection conn, final Integer dbType, String dialect) throws Exception {
		String realCatalog = SqlToyConstants.getDialectLowcaseStrategyName(catalog, dialect);
		String realSchema = SqlToyConstants.getDialectLowcaseStrategyName(schema, dialect);
		String realTableName = SqlToyConstants.getDialectLowcaseStrategyName(tableName, dialect);
		ResultSet rs = null;
		try {
			rs = conn.getMetaData().getPrimaryKeys(realCatalog, realSchema, realTableName);
		} catch (Exception e) {
			// 部分库(如starrocks)不支持getPrimaryKeys,失败后走mysql desc等回退路径
			logger.debug("failed to get the primary keys of table:{} via getMetaData, will try the fallback way!",
					realTableName, e);
		}
		if (rs != null) {
			return (Map<String, ColumnMeta>) SqlUtil.preparedStatementProcess(null, null, rs,
					new PreparedStatementResultHandler() {
						@Override
						public void execute(Object rowData, PreparedStatement pst, ResultSet rs) throws Exception {
							Map<String, ColumnMeta> pkMeta = new HashMap<String, ColumnMeta>();
							while (rs.next()) {
								ColumnMeta colMeta = new ColumnMeta();
								colMeta.setColName(rs.getString("COLUMN_NAME"));
								colMeta.setPK(true);
								pkMeta.put(colMeta.getColName(), colMeta);
							}
							this.setResult(pkMeta);
						}
					});
		} // 针对starrocks(用的mysql驱动)
		else if (dbType == DBType.MYSQL || dbType == DBType.MYSQL57 || dbType == DBType.DORIS
				|| dbType == DBType.STARROCKS) {
			Statement stmt = conn.createStatement();
			rs = stmt.executeQuery("desc " + tableName);
			try {
				return (Map<String, ColumnMeta>) SqlUtil.preparedStatementProcess(null, null, rs,
						new PreparedStatementResultHandler() {
							@Override
							public void execute(Object obj, PreparedStatement pst, ResultSet rs) throws SQLException {
								Map<String, ColumnMeta> pkMeta = new HashMap<String, ColumnMeta>();
								while (rs.next()) {
									ColumnMeta colMeta = new ColumnMeta();
									colMeta.setColName(rs.getString("FIELD"));
									colMeta.setPK(rs.getBoolean("KEY"));
									if (colMeta.isPK()) {
										pkMeta.put(colMeta.getColName(), colMeta);
									}
								}
								this.setResult(pkMeta);
							}
						});
			} finally {
				try {
					stmt.close();
				} catch (SQLException e) {
				}
			}
		}
		return null;
	}

	@SuppressWarnings("unchecked")
	public static List<TableMeta> getTables(String catalogPattern, String schemaPattern, String tableNamePattern,
			Connection conn, Integer dbType, String dialect) throws Exception {
		// update 2026-9-11 oceanbase驱动的getTables元数据构造缺陷:TABLE_COMMENT的byte[]被
		// 预先toString为"[B@hash"形态写入结果行(getString/getObject/getBytes均取不回原始
		// 字节,ob 4.3.5中文表备注实测不可恢复),绕行JDBC元数据直查information_schema
		if (dbType != null && (dbType.intValue() == DBType.OCEANBASE || DialectExtUtils.isOceanBaseAsMysql())) {
			return getTablesFromInformationSchema(conn, tableNamePattern);
		}
		String realCatalogPattern = SqlToyConstants.getDialectLowcaseStrategyName(catalogPattern, dialect);
		String realSchemaPattern = SqlToyConstants.getDialectLowcaseStrategyName(schemaPattern, dialect);
		String realTableNamePattern = SqlToyConstants.getDialectLowcaseStrategyName(tableNamePattern, dialect);
		// pg会拿出全部子分区表，需要排除掉
		ResultSet rs = conn.getMetaData().getTables(realCatalogPattern, realSchemaPattern, realTableNamePattern,
				new String[] { "TABLE", "VIEW" });
		// 通过preparedStatementProcess反调，第二个参数是pst
		return (List<TableMeta>) SqlUtil.preparedStatementProcess(null, null, rs, new PreparedStatementResultHandler() {
			@Override
			public void execute(Object rowData, PreparedStatement pst, ResultSet rs) throws Exception {
				List<TableMeta> tables = new ArrayList<TableMeta>();
				while (rs.next()) {
					TableMeta tableMeta = new TableMeta();
					tableMeta.setTableName(rs.getString("TABLE_NAME"));
					tableMeta.setSchema(rs.getString("TABLE_SCHEM"));
					tableMeta.setType(rs.getString("TABLE_TYPE"));
					tableMeta.setRemarks(StringUtil.escapeComment(readRemarks(rs, "REMARKS")));
					tables.add(tableMeta);
				}
				this.setResult(tables);
			}
		});
	}

	/**
	 * update 2026-9-11 oceanbase专用:直查information_schema.tables获取表元数据(规避驱动
	 * getTables将TABLE_COMMENT预先toString为"[B@hash"的构造缺陷),TABLE_TYPE按JDBC惯例 归一(BASE
	 * TABLE→TABLE);表名匹配语义与默认实现一致(无%时按contains包裹)
	 *
	 * @param conn             数据库连接
	 * @param tableNamePattern 表名匹配串,支持%通配符
	 * @return 表元数据集合
	 * @throws Exception
	 */
	private static List<TableMeta> getTablesFromInformationSchema(Connection conn, String tableNamePattern)
			throws Exception {
		StringBuilder sql = new StringBuilder(
				"select table_name,table_comment,table_type from information_schema.tables"
						+ " where table_schema=database()");
		final String pattern;
		if (StringUtil.isNotBlank(tableNamePattern)) {
			sql.append(" and table_name like ?");
			pattern = tableNamePattern.contains("%") ? tableNamePattern : "%" + tableNamePattern + "%";
		} else {
			pattern = null;
		}
		PreparedStatement pst = conn.prepareStatement(sql.toString());
		// 设置全局statementTimeout，默认为null
		if (SqlToyConstants.defaultStatementTimeout != null && SqlToyConstants.defaultStatementTimeout > 0) {
			pst.setQueryTimeout(SqlToyConstants.defaultStatementTimeout);
		}
		ResultSet rs = null;
		// 通过preparedStatementProcess反调，第二个参数是pst
		return (List<TableMeta>) SqlUtil.preparedStatementProcess(null, pst, rs, new PreparedStatementResultHandler() {
			@Override
			public void execute(Object rowData, PreparedStatement pst, ResultSet rs) throws Exception {
				try {
					if (pattern != null) {
						pst.setString(1, pattern);
					}
					rs = pst.executeQuery();
					List<TableMeta> tables = new ArrayList<TableMeta>();
					while (rs.next()) {
						TableMeta tableMeta = new TableMeta();
						tableMeta.setTableName(rs.getString("table_name"));
						String tableType = rs.getString("table_type");
						tableMeta.setType(
								(tableType != null && tableType.toUpperCase(Locale.ROOT).contains("VIEW")) ? "VIEW"
										: "TABLE");
						tableMeta.setRemarks(StringUtil.escapeComment(rs.getString("table_comment")));
						tables.add(tableMeta);
					}
					this.setResult(tables);
				} catch (Exception e) {
					throw e;
				} finally {
					if (rs != null) {
						rs.close();
					}
				}
			}
		});
	}

	/**
	 * 设置会话级锁超时
	 *
	 * @param dbType
	 * @param conn
	 * @param lockMode
	 * @param lockWaitTimeout 单位秒
	 * @throws Exception
	 */
	public static void setSessionLockWait(Integer dbType, Connection conn, LockMode lockMode, int lockWaitTimeout)
			throws Exception {
		if (lockWaitTimeout < 1 || lockMode == null || lockMode != LockMode.UPGRADE) {
			return;
		}
		if (dbType == DBType.MYSQL || dbType == DBType.MYSQL57 || dbType == DBType.TIDB) {
			try (Statement setLockStmt = conn.createStatement()) {
				setLockStmt.execute("SET SESSION innodb_lock_wait_timeout =" + lockWaitTimeout);
			}
		} else if (dbType == DBType.SQLSERVER) {
			try (Statement setLockStmt = conn.createStatement()) {
				setLockStmt.execute("SET LOCK_TIMEOUT " + 1000 * lockWaitTimeout);
			}
		} else if (dbType == DBType.DB2) {
			try (Statement setLockStmt = conn.createStatement()) {
				setLockStmt.execute("SET CURRENT LOCK TIMEOUT=" + lockWaitTimeout);
			}
		} else if (dbType == DBType.POSTGRESQL || dbType == DBType.POSTGRESQL14 || dbType == DBType.GAUSSDB
				|| dbType == DBType.OPENGAUSS || dbType == DBType.STARDB || dbType == DBType.VASTBASE
				|| dbType == DBType.OSCAR || dbType == DBType.MOGDB) {
			try (Statement setLockStmt = conn.createStatement()) {
				setLockStmt.execute("SET lock_timeout='" + 1000 * lockWaitTimeout + "ms'");
			}
		}
	}
}