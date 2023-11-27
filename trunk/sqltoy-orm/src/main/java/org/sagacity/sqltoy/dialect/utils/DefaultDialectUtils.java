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

import org.sagacity.sqltoy.SqlExecuteStat;
import org.sagacity.sqltoy.SqlToyConstants;
import org.sagacity.sqltoy.SqlToyContext;
import org.sagacity.sqltoy.callback.DecryptHandler;
import org.sagacity.sqltoy.callback.PreparedStatementResultHandler;
import org.sagacity.sqltoy.callback.ReflectPropsHandler;
import org.sagacity.sqltoy.callback.UpdateRowHandler;
import org.sagacity.sqltoy.config.SqlConfigParseUtils;
import org.sagacity.sqltoy.config.model.EntityMeta;
import org.sagacity.sqltoy.config.model.FieldMeta;
import org.sagacity.sqltoy.config.model.OperateType;
import org.sagacity.sqltoy.config.model.SqlToyConfig;
import org.sagacity.sqltoy.config.model.SqlToyResult;
import org.sagacity.sqltoy.exception.DataAccessException;
import org.sagacity.sqltoy.executor.QueryExecutor;
import org.sagacity.sqltoy.model.ColumnMeta;
import org.sagacity.sqltoy.model.LockMode;
import org.sagacity.sqltoy.model.QueryResult;
import org.sagacity.sqltoy.model.TableMeta;
import org.sagacity.sqltoy.model.inner.QueryExecutorExtend;
import org.sagacity.sqltoy.plugins.IUnifyFieldsHandler;
import org.sagacity.sqltoy.plugins.UnifyUpdateFieldsController;
import org.sagacity.sqltoy.utils.BeanUtil;
import org.sagacity.sqltoy.utils.DataSourceUtils.DBType;
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
 * @modify 2021-5-20,修改说明
 */
public class DefaultDialectUtils {
	/**
	 * @TODO 取随机记录
	 * @param sqlToyContext
	 * @param sqlToyConfig
	 * @param queryExecutor
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
		sql.append("select sag_random_table1.* from (");
		// sql中是否存在排序或union,存在order 或union 则在sql外包裹一层
		if (DialectUtils.hasOrderByOrUnion(innerSql)) {
			sql.append("select rand() as sag_row_number,sag_random_table.* from (");
			sql.append(innerSql);
			sql.append(") sag_random_table ");
		} else {
			sql.append(innerSql.replaceFirst("(?i)select", "select rand() as sag_row_number,"));
		}
		sql.append(" )  as sag_random_table1 ");
		sql.append(" order by sag_random_table1.sag_row_number limit ");
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
				extend.rowCallbackHandler, decryptHandler, conn, dbType, 0, fetchSize, maxRows);
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
		boolean isNamed = sqlToyConfig.isNamedParam();
		// 是否使用limit ? offset ? 模式，常规情况都是支持的，但特殊情况下部分未匹配的数据库采用limit ?,? 模式
		boolean useDefault = true;
		if (dbType == DBType.UNDEFINE && !sqlToyContext.isDefaultPageOffset()) {
			useDefault = false;
		}
		String innerSql = sqlToyConfig.isHasFast() ? sqlToyConfig.getFastSql(dialect) : sqlToyConfig.getSql(dialect);
		if (sqlToyConfig.isHasFast()) {
			sql.append(sqlToyConfig.getFastPreSql(dialect));
			if (!sqlToyConfig.isIgnoreBracket()) {
				sql.append(" (");
			}
		}
		sql.append(innerSql);
		sql.append(" limit ");
		sql.append(isNamed ? ":" + SqlToyConstants.PAGE_FIRST_PARAM_NAME : "?");
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
		Long startIndex = (pageNo - 1) * pageSize;
		// limit ? offset ?模式
		if (useDefault) {
			queryParam = DialectUtils.wrapPageSqlParams(sqlToyContext, sqlToyConfig, queryExecutor, sql.toString(),
					pageSize, (startIndex > Integer.MAX_VALUE) ? startIndex : startIndex.intValue(), dialect);
		} else {
			queryParam = DialectUtils.wrapPageSqlParams(sqlToyContext, sqlToyConfig, queryExecutor, sql.toString(),
					(startIndex > Integer.MAX_VALUE) ? startIndex : startIndex.intValue(), pageSize, dialect);
		}
		QueryExecutorExtend extend = queryExecutor.getInnerModel();
		// 增加sql执行拦截器 update 2022-9-10
		queryParam = DialectUtils.doInterceptors(sqlToyContext, sqlToyConfig,
				(extend.entityClass == null) ? OperateType.page : OperateType.singleTable, queryParam,
				extend.entityClass, dbType);
		return DialectUtils.findBySql(sqlToyContext, sqlToyConfig, queryParam.getSql(), queryParam.getParamsValue(),
				extend.rowCallbackHandler, decryptHandler, conn, dbType, 0, fetchSize, maxRows);
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
				extend.rowCallbackHandler, decryptHandler, conn, dbType, 0, fetchSize, maxRows);
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
		sqlToyResult = DialectUtils.doInterceptors(sqlToyContext, null, OperateType.deleteAll, sqlToyResult,
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
		for (int i = 0; i < whereParamValues.length; i++) {
			// 唯一性属性值存在空，则表示首次插入
			if (StringUtil.isBlank(whereParamValues[i])) {
				tempFieldValues = processFieldValues(sqlToyContext, entityMeta, entity);
				whereParamValues = BeanUtil.reflectBeanToAry(entity, whereFields);
				break;
			}
		}

		// 统一字段赋值处理
		IUnifyFieldsHandler unifyFieldsHandler = UnifyUpdateFieldsController.useUnifyFields()
				? sqlToyContext.getUnifyFieldsHandler()
				: null;
		final Object[] fieldValues = tempFieldValues;
		final boolean hasUpdateRow = (updateRowHandler == null) ? false : true;
		// 组织select * from table for update 语句
		String sql = wrapFetchSql(entityMeta, dbType, whereFields, tableName);
		SqlToyResult queryParam = new SqlToyResult(sql, whereParamValues);
		// 增加sql执行拦截器 update 2022-9-10
		queryParam = DialectUtils.doInterceptors(sqlToyContext, null, OperateType.singleTable, queryParam,
				entity.getClass(), dbType);
		SqlExecuteStat.showSql("执行锁记录查询", queryParam.getSql(), queryParam.getParamsValue());
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
						rs = pst.executeQuery();
						int rowCnt = rs.getMetaData().getColumnCount();
						int index = 0;
						List result = new ArrayList();
						while (rs.next()) {
							if (index > 0) {
								throw new DataAccessException("updateSaveFetch操作只能针对单条记录进行操作,请检查uniqueProps参数设置!");
							}
							// 存在修改记录
							if (hasUpdateRow) {
								SqlExecuteStat.debug("执行updateRow", "记录存在调用updateRowHandler.updateRow!");
								// 执行update反调，实现锁定行记录值的修改
								updateRowHandler.updateRow(rs, index);
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
												resultUpdate(conn, rs, fieldMeta, fieldValue, dbType, false);
											} else {
												// 反射对象属性取值
												Object pojoFieldValue = BeanUtil.getProperty(entity, field);
												// 不为null，则以对象传递的值为准
												if (pojoFieldValue != null) {
													fieldValue = pojoFieldValue;
												}
												resultUpdate(conn, rs, fieldMeta, fieldValue, dbType, false);
											}
										}
									}
								}
								// 执行update
								rs.updateRow();
							}
							index++;
							// 重新获得修改后的值
							result.add(ResultUtils.processResultRow(rs, 0, rowCnt, false));
						}
						// 没有查询到记录，表示是需要首次插入
						if (index == 0) {
							SqlExecuteStat.debug("执行insertRow", "查询未匹配到结果则进行首次插入!");
							// 移到插入行
							rs.moveToInsertRow();
							FieldMeta fieldMeta;
							Object[] fullFieldvalues = (fieldValues == null)
									? processFieldValues(sqlToyContext, entityMeta, entity)
									: fieldValues;
							Object fieldValue;
							for (int i = 0; i < entityMeta.getFieldsArray().length; i++) {
								fieldMeta = entityMeta.getFieldMeta(entityMeta.getFieldsArray()[i]);
								if (fieldMeta.isPK()) {
									fieldValue = fullFieldvalues[i];
								} else {
									fieldValue = SqlUtilsExt.getDefaultValue(fullFieldvalues[i],
											fieldMeta.getDefaultValue(), fieldMeta.getType(), fieldMeta.isNullable());
								}
								// 插入设置具体列的值
								if (fieldValue != null) {
									resultUpdate(conn, rs, fieldMeta, fieldValue, dbType, true);
								}
							}
							// 执行插入
							rs.insertRow();
						}
						this.setResult(result);
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
			Blob tmp = (java.sql.Blob) paramValue;
			rs.updateBytes(columnName, tmp.getBytes(0, Long.valueOf(tmp.length()).intValue()));
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
		if (dbType == DBType.GAUSSDB) {
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
	 * @param tableName
	 * @return
	 */
	private static String wrapFetchSql(EntityMeta entityMeta, Integer dbType, String[] uniqueProps, String tableName) {
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
		for (String field : uniqueProps) {
			if (index > 0) {
				sql.append(" and ");
			}
			columnName = entityMeta.getColumnName(field);
			sql.append(ReservedWordsUtil.convertWord(columnName, dbType)).append("=?");
			index++;
		}
		// 设置锁
		if (dbType == DBType.SQLSERVER) {
			return SqlServerDialectUtils.lockSql(sql.toString(), realTable, LockMode.UPGRADE);
		} else if (dbType == DBType.DB2) {
			return sql.append(" for update with rs").toString();
		} else {
			return sql.append(" for update").toString();
		}
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
		// 构造全新的新增记录参数赋值反射(覆盖之前的)
		ReflectPropsHandler handler = DialectUtils.getAddReflectHandler(entityMeta, null,
				sqlToyContext.getUnifyFieldsHandler());
		handler = DialectUtils.getSecureReflectHandler(handler, sqlToyContext.getFieldsSecureProvider(),
				sqlToyContext.getDesensitizeProvider(), entityMeta.getSecureFields());
		// 这里不体现defaultValue 值，产生的insert sql语句中已经处理了default值问题
		Object[] fullParamValues = BeanUtil.reflectBeanToAry(entity, entityMeta.getFieldsArray(), null, handler);
		// 主键采用assign方式赋予，则调用generator产生id并赋予其值
		if (entityMeta.getIdStrategy() != null && null != entityMeta.getIdGenerator()) {
			int bizIdLength = entityMeta.getBizIdLength();
			int idLength = entityMeta.getIdLength();
			int pkIndex = entityMeta.getIdIndex();
			// 是否存在业务ID
			boolean hasBizId = (entityMeta.getBusinessIdGenerator() == null) ? false : true;
			int bizIdColIndex = hasBizId ? entityMeta.getFieldIndex(entityMeta.getBusinessIdField()) : 0;
			// 标识符
			String signature = entityMeta.getBizIdSignature();
			Integer[] relatedColumn = entityMeta.getBizIdRelatedColIndex();
			String[] relatedColumnNames = entityMeta.getBizIdRelatedColumns();
			int relatedColumnSize = (relatedColumn == null) ? 0 : relatedColumn.length;
			Object[] relatedColValue = null;
			String businessIdType = hasBizId ? entityMeta.getColumnJavaType(entityMeta.getBusinessIdField()) : "";
			if (StringUtil.isBlank(fullParamValues[pkIndex]) || StringUtil.isBlank(fullParamValues[bizIdColIndex])) {
				if (relatedColumn != null) {
					relatedColValue = new Object[relatedColumnSize];
					for (int meter = 0; meter < relatedColumnSize; meter++) {
						relatedColValue[meter] = fullParamValues[relatedColumn[meter]];
						if (StringUtil.isBlank(relatedColValue[meter])) {
							throw new IllegalArgumentException("对象:" + entityMeta.getEntityClass().getName()
									+ " 生成业务主键依赖的关联字段:" + relatedColumnNames[meter] + " 值为null!");
						}
					}
				}
			}
			if (StringUtil.isBlank(fullParamValues[pkIndex])) {
				// id通过generator机制产生，设置generator产生的值
				fullParamValues[pkIndex] = entityMeta.getIdGenerator().getId(entityMeta.getTableName(), signature,
						entityMeta.getBizIdRelatedColumns(), relatedColValue, null, entityMeta.getIdType(), idLength,
						entityMeta.getBizIdSequenceSize());
				// 回写主键值
				BeanUtil.setProperty(entity, entityMeta.getIdArray()[0], fullParamValues[pkIndex]);
			}
			if (hasBizId && StringUtil.isBlank(fullParamValues[bizIdColIndex])) {
				fullParamValues[bizIdColIndex] = entityMeta.getBusinessIdGenerator().getId(entityMeta.getTableName(),
						signature, entityMeta.getBizIdRelatedColumns(), relatedColValue, null, businessIdType,
						bizIdLength, entityMeta.getBizIdSequenceSize());
				// 回写业务主键值
				BeanUtil.setProperty(entity, entityMeta.getBusinessIdField(), fullParamValues[bizIdColIndex]);
			}
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
	 * @TODO 获取表的索引信息
	 * @param catalog
	 * @param schema
	 * @param tableName
	 * @param conn
	 * @param dbType
	 * @param dialect
	 * @return
	 * @throws Exception
	 */
	private static Map<String, ColumnMeta> getTableIndexes(String catalog, String schema, String tableName,
			Connection conn, final Integer dbType, String dialect) throws Exception {
		ResultSet rs = null;
		try {
			rs = conn.getMetaData().getIndexInfo(catalog, schema, tableName, false, false);
		} catch (Exception e) {

		}
		if (rs != null) {
			return (Map<String, ColumnMeta>) SqlUtil.preparedStatementProcess(null, null, rs,
					new PreparedStatementResultHandler() {
						@Override
						public void execute(Object rowData, PreparedStatement pst, ResultSet rs) throws Exception {
							Map<String, ColumnMeta> indexsMeta = new HashMap<String, ColumnMeta>();
							while (rs.next()) {
								ColumnMeta colMeta = new ColumnMeta();
								colMeta.setColName(rs.getString("COLUMN_NAME"));
								colMeta.setIndex(true);
								colMeta.setUnique(!rs.getBoolean("NON_UNIQUE"));
								colMeta.setIndexName(rs.getString("INDEX_NAME"));
								indexsMeta.put(colMeta.getColName(), colMeta);
							}
							this.setResult(indexsMeta);
						}
					});
		} // 针对旧版本jdbc驱动起作用
		else if (dbType == DBType.ORACLE || dbType == DBType.ORACLE11) {
			String tableNameUp = tableName.toUpperCase();
			String sql = "SELECT t1.INDEX_NAME,t1.COLUMN_NAME,t0.UNIQUENESS FROM USER_IND_COLUMNS t1 LEFT JOIN "
					+ " (SELECT INDEX_NAME,UNIQUENESS FROM USER_INDEXES WHERE TABLE_NAME ='" + tableNameUp + "') t0 ON "
					+ " t1.INDEX_NAME = t0.INDEX_NAME WHERE TABLE_NAME ='" + tableNameUp + "'";
			rs = conn.createStatement().executeQuery(sql);
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
		return null;
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
		} else if (dbType == DBType.MYSQL || dbType == DBType.MYSQL57) {
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
	public static List<TableMeta> getTables(String catalog, String schema, String tableName, Connection conn,
			Integer dbType, String dialect) throws Exception {
		// 可自定义 PreparedStatement pst=conn.xxx;
		ResultSet rs = conn.getMetaData().getTables(catalog, schema, tableName, new String[] { "TABLE", "VIEW" });
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
