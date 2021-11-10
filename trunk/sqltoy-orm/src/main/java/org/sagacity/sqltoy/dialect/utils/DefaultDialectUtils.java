/**
 * 
 */
package org.sagacity.sqltoy.dialect.utils;

import java.io.Serializable;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.ArrayList;
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
import org.sagacity.sqltoy.config.model.SqlToyConfig;
import org.sagacity.sqltoy.config.model.SqlToyResult;
import org.sagacity.sqltoy.exception.DataAccessException;
import org.sagacity.sqltoy.model.ColumnMeta;
import org.sagacity.sqltoy.model.LockMode;
import org.sagacity.sqltoy.model.QueryExecutor;
import org.sagacity.sqltoy.model.QueryResult;
import org.sagacity.sqltoy.model.TableMeta;
import org.sagacity.sqltoy.model.inner.QueryExecutorExtend;
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
		String innerSql = sqlToyConfig.isHasFast() ? sqlToyConfig.getFastSql(dialect) : sqlToyConfig.getSql(dialect);

		// select * from table order by rand() limit :randomCount 性能比较差,通过产生rand()
		// row_number 再排序方式性能稍好 同时也可以保证通用性
		StringBuilder sql = new StringBuilder();
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
				sql.toString(), null, null);
		QueryExecutorExtend extend = queryExecutor.getInnerModel();
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
		if (sqlToyConfig.isHasFast()) {
			sql.append(sqlToyConfig.getFastPreSql(dialect));
			if (!sqlToyConfig.isIgnoreBracket()) {
				sql.append(" (");
			}
			sql.append(sqlToyConfig.getFastSql(dialect));
		} else {
			sql.append(sqlToyConfig.getSql(dialect));
		}
		sql.append(" limit ");
		sql.append(isNamed ? ":" + SqlToyConstants.PAGE_FIRST_PARAM_NAME : "?");
		sql.append(" offset ");
		sql.append(isNamed ? ":" + SqlToyConstants.PAGE_LAST_PARAM_NAME : "?");
		if (sqlToyConfig.isHasFast()) {
			if (!sqlToyConfig.isIgnoreBracket()) {
				sql.append(") ");
			}
			sql.append(sqlToyConfig.getFastTailSql(dialect));
		}

		SqlToyResult queryParam = DialectUtils.wrapPageSqlParams(sqlToyContext, sqlToyConfig, queryExecutor,
				sql.toString(), Long.valueOf(pageSize), (pageNo - 1) * pageSize);
		QueryExecutorExtend extend = queryExecutor.getInnerModel();
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
		if (sqlToyConfig.isHasFast()) {
			sql.append(sqlToyConfig.getFastPreSql(dialect));
			if (!sqlToyConfig.isIgnoreBracket()) {
				sql.append(" (");
			}
			sql.append(sqlToyConfig.getFastSql(dialect));
		} else {
			sql.append(sqlToyConfig.getSql(dialect));
		}
		sql.append(" limit ");
		sql.append(topSize);

		if (sqlToyConfig.isHasFast()) {
			if (!sqlToyConfig.isIgnoreBracket()) {
				sql.append(") ");
			}
			sql.append(sqlToyConfig.getFastTailSql(dialect));
		}

		SqlToyResult queryParam = DialectUtils.wrapPageSqlParams(sqlToyContext, sqlToyConfig, queryExecutor,
				sql.toString(), null, null);
		QueryExecutorExtend extend = queryExecutor.getInnerModel();
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
			deleteSql.append("  where ");
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
			sqlToyResult = SqlConfigParseUtils.processSql(deleteSql.toString(), null, new Object[] { idValues });
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
			sqlToyResult = SqlConfigParseUtils.processSql(deleteSql.toString(), null, realValues);
		}
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
		final Object[] fieldValues = tempFieldValues;
		// 组织select * from table for update 语句
		String sql = wrapFetchSql(entityMeta, dbType, whereFields, tableName);
		SqlExecuteStat.showSql("执行锁记录查询", sql, whereParamValues);
		// 可编辑结果集
		PreparedStatement pst = conn.prepareStatement(sql, ResultSet.TYPE_FORWARD_ONLY, ResultSet.CONCUR_UPDATABLE);
		List updateResult = (List) SqlUtil.preparedStatementProcess(whereParamValues, pst, null,
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
							SqlExecuteStat.debug("执行updateRow", "记录存在调用updateRowHandler.updateRow!");
							// 执行update反调，实现锁定行记录值的修改
							updateRowHandler.updateRow(rs, index);
							// 执行update
							rs.updateRow();
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
									rs.updateObject(fieldMeta.getColumnName(), fieldValue);
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
		} else {
			List entities = BeanUtil.reflectListToBean(sqlToyContext.getTypeHandler(), updateResult,
					entityMeta.getFieldsArray(), entity.getClass());
			return (Serializable) entities.get(0);
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
		ReflectPropsHandler handler = DialectUtils.getAddReflectHandler(null, sqlToyContext.getUnifyFieldsHandler());
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
							isAutoIncrement = rs.getString("IS_AUTOINCREMENT");
							if (isAutoIncrement != null && (isAutoIncrement.equalsIgnoreCase("true")
									|| isAutoIncrement.equalsIgnoreCase("YES") || isAutoIncrement.equalsIgnoreCase("Y")
									|| isAutoIncrement.equals("1"))) {
								colMeta.setAutoIncrement(true);
							} else {
								colMeta.setAutoIncrement(false);
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
		// 获取主键信息
		Map<String, ColumnMeta> pkMap = getTablePrimaryKeys(catalog, schema, tableName, conn, dbType, dialect);
		if (pkMap == null || pkMap.isEmpty()) {
			return tableCols;
		}
		ColumnMeta mapMeta;
		for (ColumnMeta colMeta : tableCols) {
			mapMeta = pkMap.get(colMeta.getColName());
			if (mapMeta != null) {
				colMeta.setPK(true);
			}
		}
		return tableCols;
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
