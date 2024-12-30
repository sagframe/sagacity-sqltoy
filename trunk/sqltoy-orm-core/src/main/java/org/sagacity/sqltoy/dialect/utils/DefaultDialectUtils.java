/**
 * 
 */
package org.sagacity.sqltoy.dialect.utils;

import java.io.OutputStream;
import java.io.Serializable;
import java.math.BigDecimal;
import java.math.BigInteger;
import java.sql.Array;
import java.sql.Blob;
import java.sql.Clob;
import java.sql.Connection;
import java.sql.NClob;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Timestamp;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.util.ArrayList;
import java.util.Collection;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

import org.sagacity.sqltoy.SqlExecuteStat;
import org.sagacity.sqltoy.SqlToyConstants;
import org.sagacity.sqltoy.SqlToyContext;
import org.sagacity.sqltoy.SqlToyThreadDataHolder;
import org.sagacity.sqltoy.callback.DecryptHandler;
import org.sagacity.sqltoy.callback.PreparedStatementResultHandler;
import org.sagacity.sqltoy.callback.ReflectPropsHandler;
import org.sagacity.sqltoy.callback.UpdateRowHandler;
import org.sagacity.sqltoy.config.SqlConfigParseUtils;
import org.sagacity.sqltoy.config.model.DataVersionConfig;
import org.sagacity.sqltoy.config.model.EntityMeta;
import org.sagacity.sqltoy.config.model.FieldMeta;
import org.sagacity.sqltoy.config.model.OperateType;
import org.sagacity.sqltoy.config.model.SqlToyConfig;
import org.sagacity.sqltoy.config.model.SqlToyResult;
import org.sagacity.sqltoy.config.model.SqlType;
import org.sagacity.sqltoy.exception.DataAccessException;
import org.sagacity.sqltoy.model.ColumnMeta;
import org.sagacity.sqltoy.model.LockMode;
import org.sagacity.sqltoy.model.QueryExecutor;
import org.sagacity.sqltoy.model.QueryResult;
import org.sagacity.sqltoy.model.TableMeta;
import org.sagacity.sqltoy.model.inner.QueryExecutorExtend;
import org.sagacity.sqltoy.plugins.IUnifyFieldsHandler;
import org.sagacity.sqltoy.translate.DynamicCacheFetch;
import org.sagacity.sqltoy.utils.BeanUtil;
import org.sagacity.sqltoy.utils.DataSourceUtils;
import org.sagacity.sqltoy.utils.DataSourceUtils.DBType;
import org.sagacity.sqltoy.utils.DateUtil;
import org.sagacity.sqltoy.utils.ReservedWordsUtil;
import org.sagacity.sqltoy.utils.ResultUtils;
import org.sagacity.sqltoy.utils.SqlUtil;
import org.sagacity.sqltoy.utils.SqlUtilsExt;
import org.sagacity.sqltoy.utils.StringUtil;

/**
 * @project sagacity-sqltoy
 * @description 提供默认方言的通用处理工具
 * @author zhongxuchen
 * @version v1.0, Date:2021-5-20
 * @modify 2024-8-8 修复updateSaveFetch中uniqueProps对应属性值为null时构建的sql where
 *         id=null改为id is null
 */
public class DefaultDialectUtils {
	/**
	 * @TODO 取随机记录
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
		// 给原始sql标记上特殊的开始和结尾，便于sql拦截器快速定位到原始sql并进行条件补充
		innerSql = SqlUtilsExt.markOriginalSql(innerSql);
		if (sqlToyConfig.isHasFast()) {
			sql.append(sqlToyConfig.getFastPreSql(dialect));
			if (!sqlToyConfig.isIgnoreBracket()) {
				sql.append(" (");
			}
		}
		sql.append("select " + SqlToyConstants.INTERMEDIATE_TABLE1 + ".* from (");
		// sql中是否存在排序或union,存在order 或union 则在sql外包裹一层
		if (DialectUtils.hasOrderByOrUnion(innerSql)) {
			sql.append("select rand() as sag_row_number," + SqlToyConstants.INTERMEDIATE_TABLE + ".* from (");
			sql.append(innerSql);
			sql.append(") ");
			sql.append(SqlToyConstants.INTERMEDIATE_TABLE);
			sql.append(" ");
		} else {
			sql.append(innerSql.replaceFirst("(?i)select", "select rand() as sag_row_number,"));
		}
		sql.append(" )  as " + SqlToyConstants.INTERMEDIATE_TABLE1);
		sql.append(" order by " + SqlToyConstants.INTERMEDIATE_TABLE1 + ".sag_row_number limit ");
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
	 * @todo 分页查询
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
	 * @todo 实现top记录查询
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
	 * @todo 批量删除对象
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
			throw new IllegalArgumentException("delete/deleteAll 操作,表:" + realTable + "没有主键,请检查表设计!");
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

	/**
	 * @TODO 实现：1、锁查询；2、记录存在则修改；3、记录不存在则执行insert；4、返回修改或插入的记录信息，尽量不要使用identity、sequence主键
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
			final UpdateRowHandler updateRowHandler, String[] uniqueProps, final Connection conn, final Integer dbType,
			String dialect, String tableName) throws Exception {
		final EntityMeta entityMeta = sqlToyContext.getEntityMeta(entity.getClass());
		// 条件字段
		String[] whereFields = uniqueProps;
		if (whereFields == null || whereFields.length == 0) {
			whereFields = entityMeta.getIdArray();
		}
		if (whereFields == null || whereFields.length == 0) {
			throw new DataAccessException("updateSaveFetch操作的表:" + tableName + " 没有唯一获得一条记录的条件字段,请检查!");
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
				tempFieldValues = processFieldValues(sqlToyContext, entityMeta, entity);
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
		final boolean hasUpdateRow = (updateRowHandler == null) ? false : true;
		// 组织select * from table for update 语句
		SqlToyResult queryParam = wrapFetchSql(entityMeta, dbType, whereFields, whereParamValues, tableName);
		// 增加sql执行拦截器 update 2022-9-10
		queryParam = DialectUtils.doInterceptors(sqlToyContext, null, OperateType.singleTable, queryParam,
				entity.getClass(), dbType);
		SqlExecuteStat.showSql("执行锁记录查询", queryParam.getSql(), queryParam.getParamsValue());
		DynamicCacheFetch dynamicCacheFetch = sqlToyContext.getDynamicCacheFetch();
		// 可编辑结果集
		PreparedStatement pst = conn.prepareStatement(queryParam.getSql(), ResultSet.TYPE_FORWARD_ONLY,
				ResultSet.CONCUR_UPDATABLE);
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
							int index = 0;
							List result = new ArrayList();
							DataVersionConfig dataVersion = entityMeta.getDataVersion();
							final String dataVersionField = (dataVersion == null) ? null : dataVersion.getField();
							while (finalRs.next()) {
								if (index > 0) {
									throw new DataAccessException("updateSaveFetch操作只能针对单条记录进行操作,请检查uniqueProps参数设置!");
								}
								// 存在修改记录
								if (hasUpdateRow) {
									SqlExecuteStat.debug("执行updateRow", "记录存在调用updateRowHandler.updateRow!");
									// 存在数据版本:1、校验当前的版本是否为null(目前跳过)；2、对比传递过来的版本值跟数据库中的值是否一致；3、修改数据库中数据版本+1
									if (dataVersion != null) {
										String nowVersion = finalRs
												.getString(entityMeta.getColumnName(dataVersionField));
										if (entityVersion != null && !entityVersion.toString().equals(nowVersion)) {
											throw new IllegalArgumentException("表:" + entityMeta.getTableName()
													+ " 存在版本@DataVersion配置，在updateSaveFetch做更新时，属性:" + dataVersionField
													+ " 值不等于当前数据库中的值:" + entityVersion + "<>" + nowVersion
													+ ",说明数据已经被修改过!");
										}
										// 以日期开头
										if (dataVersion.isStartDate()) {
											String nowDate = DateUtil.formatDate(DateUtil.getNowTime(),
													DateUtil.FORMAT.DATE_8CHAR);
											if (nowVersion.startsWith(nowDate)) {
												nowVersion = nowDate + (Integer.parseInt(nowVersion.substring(8)) + 1);
											} else {
												nowVersion = nowDate + 1;
											}
										} else {
											nowVersion = "" + (Integer.parseInt(nowVersion) + 1);
										}
										// 修改数据版本
										resultUpdate(conn, finalRs, entityMeta.getFieldMeta(dataVersionField),
												nowVersion, dbType, false);
									}
									// 执行update反调，实现锁定行记录值的修改
									updateRowHandler.updateRow(finalRs, index);
									updateRowHandler.updateRow(finalRs, index, (fieldName, fieldValue) -> {
										// 排除dataVersionField字段避免被重复处理
										if (dataVersionField == null || !fieldName.equals(dataVersionField)) {
											Optional.ofNullable(entityMeta.getFieldMeta(fieldName))
													.ifPresent(fieldMeta -> {
														try {
															resultUpdate(conn, finalRs, fieldMeta, fieldValue, dbType,
																	false);
														} catch (Exception e) {
															throw new RuntimeException(e);
														}
													});
										}
									});
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
													resultUpdate(conn, finalRs, fieldMeta, fieldValue, dbType, false);
												} else {
													// 反射对象属性取值
													Object pojoFieldValue = BeanUtil.getProperty(entity, field);
													// 不为null，则以对象传递的值为准
													if (pojoFieldValue != null) {
														fieldValue = pojoFieldValue;
													}
													resultUpdate(conn, finalRs, fieldMeta, fieldValue, dbType, false);
												}
											}
										}
									}
									// 执行update
									finalRs.updateRow();
								}
								index++;
								// 重新获得修改后的值
								result.add(ResultUtils.processResultRow(dynamicCacheFetch, finalRs, null, rowCnt, null,
										null, false));
							}
							// 没有查询到记录，表示是需要首次插入
							if (index == 0) {
								SqlExecuteStat.debug("执行insertRow", "查询未匹配到结果则进行首次插入!");
								// 移到插入行
								finalRs.moveToInsertRow();
								FieldMeta fieldMeta;
								Object[] fullFieldvalues = (fieldValues == null)
										? processFieldValues(sqlToyContext, entityMeta, entity)
										: fieldValues;
								for (int i = 0; i < entityMeta.getFieldsArray().length; i++) {
									fieldMeta = entityMeta.getFieldMeta(entityMeta.getFieldsArray()[i]);
									resultUpdate(conn, finalRs, fieldMeta, fullFieldvalues[i], dbType, true);
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
		for (int i = 0; i < entityMeta.getFieldsArray().length; i++) {
			BeanUtil.setProperty(entity, entityMeta.getFieldsArray()[i], rowList.get(i));
		}
		return entity;
	}

	/**
	 * @TODO 插入对象
	 * @param conn
	 * @param rs
	 * @param fieldMeta
	 * @param paramValue
	 * @param dbType
	 * @param isInsert
	 * @throws Exception
	 */
	private static void resultUpdate(Connection conn, ResultSet rs, FieldMeta fieldMeta, Object paramValue,
			Integer dbType, boolean isInsert) throws Exception {
		if (!fieldMeta.isPK() && isInsert) {
			paramValue = SqlUtilsExt.getDefaultValue(paramValue, fieldMeta.getDefaultValue(), fieldMeta.getType(),
					fieldMeta.isNullable());
		}
		// 插入设置具体列的值
		if (paramValue == null) {
			return;
		}
		String tmpStr;
		int jdbcType = fieldMeta.getType();
		String columnName = fieldMeta.getColumnName();
		if (paramValue instanceof java.lang.String) {
			tmpStr = (String) paramValue;
			// clob 类型只有oracle、db2、dm、oceanBase等数据库支持
			if (jdbcType == java.sql.Types.CLOB) {
				if (DBType.ORACLE == dbType || DBType.DB2 == dbType || DBType.OCEANBASE == dbType
						|| DBType.ORACLE11 == dbType || DBType.DM == dbType || DBType.KINGBASE == dbType) {
					Clob clob = conn.createClob();
					clob.setString(1, tmpStr);
					rs.updateClob(columnName, clob);
				} else {
					rs.updateString(columnName, tmpStr);
				}
			} else if (jdbcType == java.sql.Types.NCLOB) {
				if (DBType.ORACLE == dbType || DBType.DB2 == dbType || DBType.OCEANBASE == dbType
						|| DBType.ORACLE11 == dbType || DBType.DM == dbType || DBType.KINGBASE == dbType) {
					NClob nclob = conn.createNClob();
					nclob.setString(1, tmpStr);
					rs.updateNClob(columnName, nclob);
				} else {
					rs.updateString(columnName, tmpStr);
				}
			} else {
				rs.updateString(columnName, tmpStr);
			}
		} else if (paramValue instanceof java.lang.Integer) {
			Integer paramInt = (Integer) paramValue;
			if (jdbcType == java.sql.Types.BOOLEAN) {
				if (paramInt == 1) {
					rs.updateBoolean(columnName, true);
				} else {
					rs.updateBoolean(columnName, false);
				}
			} else {
				rs.updateInt(columnName, paramInt);
			}
		} else if (paramValue instanceof java.time.LocalDateTime) {
			rs.updateTimestamp(columnName, Timestamp.valueOf((LocalDateTime) paramValue));
		} else if (paramValue instanceof BigDecimal) {
			rs.updateBigDecimal(columnName, (BigDecimal) paramValue);
		} else if (paramValue instanceof java.time.LocalDate) {
			rs.updateDate(columnName, java.sql.Date.valueOf((LocalDate) paramValue));
		} else if (paramValue instanceof java.sql.Timestamp) {
			rs.updateTimestamp(columnName, (java.sql.Timestamp) paramValue);
		} else if (paramValue instanceof java.util.Date) {
			if (dbType == DBType.CLICKHOUSE) {
				rs.updateDate(columnName, new java.sql.Date(((java.util.Date) paramValue).getTime()));
			} else {
				rs.updateTimestamp(columnName, new Timestamp(((java.util.Date) paramValue).getTime()));
			}
		} else if (paramValue instanceof java.math.BigInteger) {
			rs.updateBigDecimal(columnName, new BigDecimal(((BigInteger) paramValue)));
		} else if (paramValue instanceof java.lang.Double) {
			rs.updateDouble(columnName, ((Double) paramValue));
		} else if (paramValue instanceof java.lang.Long) {
			rs.updateLong(columnName, ((Long) paramValue));
		} else if (paramValue instanceof java.sql.Clob) {
			tmpStr = SqlUtil.clobToString((java.sql.Clob) paramValue);
			rs.updateString(columnName, tmpStr);
		} else if (paramValue instanceof byte[]) {
			if (jdbcType == java.sql.Types.BLOB) {
				Blob blob = null;
				try {
					blob = conn.createBlob();
					OutputStream out = blob.setBinaryStream(1);
					out.write((byte[]) paramValue);
					out.flush();
					out.close();
					rs.updateBlob(columnName, blob);
				} catch (Exception e) {
					rs.updateBytes(columnName, (byte[]) paramValue);
				}
			} else {
				rs.updateBytes(columnName, (byte[]) paramValue);
			}
		} else if (paramValue instanceof java.lang.Float) {
			rs.updateFloat(columnName, ((Float) paramValue));
		} else if (paramValue instanceof java.sql.Blob) {
			Blob blob = (java.sql.Blob) paramValue;
			int size = (int) blob.length();
			if (size > 0) {
				rs.updateBytes(columnName, blob.getBytes(1, size));
			} else {
				rs.updateBytes(columnName, new byte[0]);
			}
		} else if (paramValue instanceof java.sql.Date) {
			rs.updateDate(columnName, (java.sql.Date) paramValue);
		} else if (paramValue instanceof java.lang.Boolean) {
			if (jdbcType == java.sql.Types.VARCHAR || jdbcType == java.sql.Types.CHAR) {
				rs.updateString(columnName, ((Boolean) paramValue) ? "1" : "0");
			} else if (jdbcType == java.sql.Types.INTEGER || jdbcType == java.sql.Types.SMALLINT
					|| jdbcType == java.sql.Types.TINYINT) {
				rs.updateInt(columnName, ((Boolean) paramValue) ? 1 : 0);
			} else {
				rs.updateBoolean(columnName, (Boolean) paramValue);
			}
		} else if (paramValue instanceof java.time.LocalTime) {
			rs.updateTime(columnName, java.sql.Time.valueOf((LocalTime) paramValue));
		} else if (paramValue instanceof java.sql.Time) {
			rs.updateTime(columnName, (java.sql.Time) paramValue);
		} else if (paramValue instanceof java.lang.Character) {
			tmpStr = ((Character) paramValue).toString();
			rs.updateString(columnName, tmpStr);
		} else if (paramValue instanceof java.lang.Short) {
			rs.updateShort(columnName, (java.lang.Short) paramValue);
		} else if (paramValue instanceof java.lang.Byte) {
			rs.updateByte(columnName, (Byte) paramValue);
		} else if (paramValue instanceof Object[]) {
			setArray(dbType, conn, rs, columnName, paramValue);
		} else if (paramValue instanceof Enum) {
			rs.updateObject(columnName, BeanUtil.getEnumValue(paramValue));
		} else if (paramValue instanceof Collection) {
			Object[] values = ((Collection) paramValue).toArray();
			// 集合为空，无法判断具体类型，设置为null
			if (values.length > 0) {
				String type = null;
				for (Object val : values) {
					if (val != null) {
						type = val.getClass().getName().concat("[]");
						break;
					}
				}
				// 将Object[] 转为具体类型的数组(否则会抛异常)
				if (type != null) {
					setArray(dbType, conn, rs, columnName, BeanUtil.convertArray(values, type));
				}
			}
		} else {
			if (jdbcType != java.sql.Types.NULL) {
				rs.updateObject(columnName, paramValue, jdbcType);
			} else {
				rs.updateObject(columnName, paramValue);
			}
		}
	}

	private static void setArray(Integer dbType, Connection conn, ResultSet rs, String columnName, Object paramValue)
			throws SQLException {
		// 目前只支持Integer 和 String两种类型
		if (dbType == DBType.GAUSSDB || dbType == DBType.OPENGAUSS || dbType == DBType.MOGDB || dbType == DBType.OSCAR
				|| dbType == DBType.STARDB || dbType == DBType.VASTBASE) {
			if (paramValue instanceof Integer[]) {
				Array array = conn.createArrayOf("INTEGER", (Integer[]) paramValue);
				rs.updateArray(columnName, array);
			} else if (paramValue instanceof String[]) {
				Array array = conn.createArrayOf("VARCHAR", (String[]) paramValue);
				rs.updateArray(columnName, array);
			} else if (paramValue instanceof BigDecimal[]) {
				Array array = conn.createArrayOf("NUMBER", (BigDecimal[]) paramValue);
				rs.updateArray(columnName, array);
			} else if (paramValue instanceof BigInteger[]) {
				Array array = conn.createArrayOf("BIGINT", (BigInteger[]) paramValue);
				rs.updateArray(columnName, array);
			} else if (paramValue instanceof Float[]) {
				Array array = conn.createArrayOf("FLOAT", (Float[]) paramValue);
				rs.updateArray(columnName, array);
			} else if (paramValue instanceof Long[]) {
				Array array = conn.createArrayOf("INTEGER", (Long[]) paramValue);
				rs.updateArray(columnName, array);
			} else {
				rs.updateObject(columnName, paramValue, java.sql.Types.ARRAY);
			}
		} else {
			rs.updateObject(columnName, paramValue, java.sql.Types.ARRAY);
		}
	}

	/**
	 * @TODO 组织updateSaveFetch的锁查询sql
	 * @param entityMeta
	 * @param dbType
	 * @param uniqueProps
	 * @param whereParamValues
	 * @param tableName
	 * @return
	 */
	private static SqlToyResult wrapFetchSql(EntityMeta entityMeta, Integer dbType, String[] uniqueProps,
			Object[] whereParamValues, String tableName) {
		String realTable = entityMeta.getSchemaTable(tableName, dbType);
		StringBuilder sql = new StringBuilder("select ");
		String columnName;
		for (int i = 0; i < entityMeta.getFieldsArray().length; i++) {
			columnName = entityMeta.getColumnName(entityMeta.getFieldsArray()[i]);
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
		return new SqlToyResult(lastSql, realParamValues.toArray());
	}

	/**
	 * @TODO 反射实体对象的属性值到数组，并调用主键策略产生主键值并写回到entity中
	 * @param sqlToyContext
	 * @param entityMeta
	 * @param entity
	 * @return
	 * @throws Exception
	 */
	private static Object[] processFieldValues(final SqlToyContext sqlToyContext, EntityMeta entityMeta,
			Serializable entity) throws Exception {
		// 构造全新的新增记录参数赋值反射(覆盖之前的)，涉及数据版本、创建人、创建时间、租户等
		ReflectPropsHandler handler = DialectUtils.getAddReflectHandler(entityMeta, null,
				sqlToyContext.getUnifyFieldsHandler());
		handler = DialectUtils.getSecureReflectHandler(handler, sqlToyContext.getFieldsSecureProvider(),
				sqlToyContext.getDesensitizeProvider(), entityMeta.getSecureFields());
		// 这里不体现defaultValue 值，产生的insert sql语句中已经处理了default值问题
		Object[] fullParamValues = BeanUtil.reflectBeanToAry(entity, entityMeta.getFieldsArray(), null, handler);
		// 主键采用assign方式赋予，则调用generator产生id并赋予其值
		boolean hasId = (entityMeta.getIdStrategy() != null && null != entityMeta.getIdGenerator()) ? true : false;
		// 是否存在业务ID
		boolean hasBizId = (entityMeta.getBusinessIdGenerator() == null) ? false : true;
		int bizIdColIndex = hasBizId ? entityMeta.getFieldIndex(entityMeta.getBusinessIdField()) : 0;
		// 主键、业务主键生成并回写对象
		if (hasId || hasBizId) {
			int pkIndex = entityMeta.getIdIndex();
			Integer[] relatedColumn = entityMeta.getBizIdRelatedColIndex();
			Object[] relatedColValue = null;
			if (relatedColumn != null) {
				int relatedColumnSize = relatedColumn.length;
				relatedColValue = new Object[relatedColumnSize];
				for (int meter = 0; meter < relatedColumnSize; meter++) {
					relatedColValue[meter] = fullParamValues[relatedColumn[meter]];
					if (StringUtil.isBlank(relatedColValue[meter])) {
						throw new IllegalArgumentException("对象:" + entityMeta.getEntityClass().getName()
								+ " 生成业务主键依赖的关联字段:" + entityMeta.getBizIdRelatedColumns()[meter] + " 值为null!");
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
			int dataVersionIndex = entityMeta.getFieldIndex(dataVersionField);
			BeanUtil.setProperty(entity, dataVersionField, fullParamValues[dataVersionIndex]);
		}
		return fullParamValues;
	}

	@SuppressWarnings("unchecked")
	public static List<ColumnMeta> getTableColumns(String catalog, String schema, String tableName, Connection conn,
			Integer dbType, String dialect) throws Exception {
		ResultSet rs = conn.getMetaData().getColumns(catalog, schema, tableName, "%");
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
							colMeta.setComments(rs.getString("REMARKS"));
							colMeta.setAutoIncrement(false);
							// oracle autoincrement 取法不同
							if (dbType == DBType.ORACLE || dbType == DBType.ORACLE11) {
								if (colMeta.getDefaultValue() != null
										&& colMeta.getDefaultValue().toLowerCase().endsWith(".nextval")) {
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
								}
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
	 * @TODO 获取表的索引信息(这里只能用于标记字段是否是索引列)
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
		if (dbType == DBType.ORACLE || dbType == DBType.ORACLE11) {
			return getOracleTableIndexes(catalog, schema, tableName, conn, dbType, dialect);
		}
		Map<String, ColumnMeta> result = new HashMap<>();
		boolean[] uniqueAndNotUnique = { false, true };
		ResultSet rs;
		Map<String, ColumnMeta> tableIndexes;
		for (int i = 0; i < uniqueAndNotUnique.length; i++) {
			boolean isUnique = uniqueAndNotUnique[i];
			rs = conn.getMetaData().getIndexInfo(catalog, schema, tableName, false, false);
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
		String tableNameUp = tableName.toUpperCase();
		String sql = "SELECT t1.INDEX_NAME,t1.COLUMN_NAME,t0.UNIQUENESS FROM USER_IND_COLUMNS t1 LEFT JOIN "
				+ " (SELECT INDEX_NAME,UNIQUENESS FROM USER_INDEXES WHERE TABLE_NAME ='" + tableNameUp + "') t0 ON "
				+ " t1.INDEX_NAME = t0.INDEX_NAME WHERE TABLE_NAME ='" + tableNameUp + "'";
		ResultSet rs = conn.createStatement().executeQuery(sql);
		return (Map<String, ColumnMeta>) SqlUtil.preparedStatementProcess(null, null, rs,
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
	 * @TODO 获取表的主键字段
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
		ResultSet rs = null;
		try {
			rs = conn.getMetaData().getPrimaryKeys(catalog, schema, tableName);
		} catch (Exception e) {

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
		else if (dbType == DBType.MYSQL || dbType == DBType.MYSQL57) {
			rs = conn.createStatement().executeQuery("desc " + tableName);
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
		}
		return null;
	}

	@SuppressWarnings("unchecked")
	public static List<TableMeta> getTables(String catalogPattern, String schemaPattern, String tableNamePattern,
			Connection conn, Integer dbType, String dialect) throws Exception {
		// 可自定义 PreparedStatement pst=conn.xxx;
		ResultSet rs = conn.getMetaData().getTables(catalogPattern, schemaPattern, tableNamePattern,
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
					tableMeta.setRemarks(rs.getString("REMARKS"));
					tables.add(tableMeta);
				}
				this.setResult(tables);
			}
		});
	}
}
