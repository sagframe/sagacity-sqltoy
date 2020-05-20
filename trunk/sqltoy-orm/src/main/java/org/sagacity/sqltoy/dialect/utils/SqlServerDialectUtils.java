/**
 * 
 */
package org.sagacity.sqltoy.dialect.utils;

import java.io.IOException;
import java.io.Serializable;
import java.lang.reflect.Type;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;

import org.sagacity.sqltoy.SqlToyContext;
import org.sagacity.sqltoy.callback.PreparedStatementResultHandler;
import org.sagacity.sqltoy.callback.ReflectPropertyHandler;
import org.sagacity.sqltoy.config.SqlConfigParseUtils;
import org.sagacity.sqltoy.config.model.EntityMeta;
import org.sagacity.sqltoy.config.model.FieldMeta;
import org.sagacity.sqltoy.config.model.OneToManyModel;
import org.sagacity.sqltoy.config.model.PKStrategy;
import org.sagacity.sqltoy.config.model.SqlToyConfig;
import org.sagacity.sqltoy.config.model.SqlToyResult;
import org.sagacity.sqltoy.dialect.handler.GenerateSqlHandler;
import org.sagacity.sqltoy.executor.QueryExecutor;
import org.sagacity.sqltoy.model.LockMode;
import org.sagacity.sqltoy.model.QueryResult;
import org.sagacity.sqltoy.utils.BeanUtil;
import org.sagacity.sqltoy.utils.ReservedWordsUtil;
import org.sagacity.sqltoy.utils.SqlUtil;
import org.sagacity.sqltoy.utils.StringUtil;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * @project sqltoy-orm
 * @description 提供基于sqlserver这种广泛应用的数据库通用的逻辑处理,避免大量重复代码
 * @author chenrenfei <a href="mailto:zhongxuchen@gmail.com">联系作者</a>
 * @version id:SqlServerDialectUtils.java,Revision:v1.0,Date:2014年12月26日
 * @Modification Date:2020-2-5 废弃对sqlserver2008 的支持,最低版本为2012版
 */
@SuppressWarnings({ "rawtypes" })
public class SqlServerDialectUtils {
	/**
	 * 定义日志
	 */
	protected final static Logger logger = LoggerFactory.getLogger(SqlServerDialectUtils.class);

	/**
	 * @todo 取随机记录
	 * @param sqlToyContext
	 * @param sqlToyConfig
	 * @param queryExecutor
	 * @param totalCount
	 * @param randomCount
	 * @param conn
	 * @param dbType
	 * @param dialect
	 * @return
	 * @throws Exception
	 */
	public static QueryResult getRandomResult(SqlToyContext sqlToyContext, SqlToyConfig sqlToyConfig,
			QueryExecutor queryExecutor, Long totalCount, Long randomCount, Connection conn, final Integer dbType,
			final String dialect) throws Exception {
		// sqlserver 不支持内部order by
		String innerSql = sqlToyConfig.isHasFast() ? sqlToyConfig.getFastSql(dialect) : sqlToyConfig.getSql(dialect);
		// sql中是否存在排序或union
		boolean hasOrderOrUnion = DialectUtils.hasOrderByOrUnion(innerSql);
		StringBuilder sql = new StringBuilder();

		if (sqlToyConfig.isHasFast()) {
			sql.append(sqlToyConfig.getFastPreSql(dialect)).append(" (");
		}
		// 存在order 或union 则在sql外包裹一层
		if (hasOrderOrUnion) {
			sql.append("select top " + randomCount);
			sql.append(" sag_random_table.* from (");
			sql.append(innerSql);
		} else {
			sql.append(innerSql.replaceFirst("(?i)select ", "select top " + randomCount + " "));
		}
		if (hasOrderOrUnion) {
			sql.append(") sag_random_table ");
		}
		sql.append(" order by NEWID() ");

		if (sqlToyConfig.isHasFast()) {
			sql.append(") ").append(sqlToyConfig.getFastTailSql(dialect));
		}
		SqlToyResult queryParam = SqlConfigParseUtils.processSql(sql.toString(),
				queryExecutor.getParamsName(sqlToyConfig), queryExecutor.getParamsValue(sqlToyContext, sqlToyConfig));
		return DialectUtils.findBySql(sqlToyContext, sqlToyConfig, queryParam.getSql(), queryParam.getParamsValue(),
				queryExecutor.getRowCallbackHandler(), conn, dbType, 0, queryExecutor.getFetchSize(),
				queryExecutor.getMaxRows());
	}

	/**
	 * @todo 批量保存或修改
	 * @param sqlToyContext
	 * @param entities
	 * @param batchSize
	 * @param reflectPropertyHandler
	 * @param forceUpdateFields
	 * @param conn
	 * @param dbType
	 * @param autoCommit
	 * @param tableName
	 * @return
	 * @throws Exception
	 */
	public static Long saveOrUpdateAll(SqlToyContext sqlToyContext, List<?> entities, final int batchSize,
			final ReflectPropertyHandler reflectPropertyHandler, final String[] forceUpdateFields, Connection conn,
			final Integer dbType, final Boolean autoCommit, final String tableName) throws Exception {
		EntityMeta entityMeta = sqlToyContext.getEntityMeta(entities.get(0).getClass());
		// sqlserver merge into must end with ";" charater
		Long updateCount = DialectUtils.saveOrUpdateAll(sqlToyContext, entities, batchSize, entityMeta,
				forceUpdateFields, new GenerateSqlHandler() {
					public String generateSql(EntityMeta entityMeta, String[] forceUpdateFields) {
						String sql = SqlServerDialectUtils.getSaveOrUpdateSql(dbType, entityMeta,
								entityMeta.getIdStrategy(), forceUpdateFields, tableName, "isnull", "@mySeqVariable",
								false);
						if (entityMeta.getIdStrategy() != null
								&& entityMeta.getIdStrategy().equals(PKStrategy.SEQUENCE)) {
							sql = "DECLARE @mySeqVariable as numeric(20)=NEXT VALUE FOR " + entityMeta.getSequence()
									+ " " + sql;
						}
						return sql.concat(";");
					}
				}, reflectPropertyHandler, conn, dbType, autoCommit);

		return updateCount;
	}

	/**
	 * @todo sqlserver 相对特殊不支持timestamp类型的插入，所以单独提供sql生成功能
	 * @param dbType
	 * @param entityMeta
	 * @param pkStrategy
	 * @param forceUpdateFields
	 * @param tableName
	 * @param isNullFunction
	 * @param sequence
	 * @param isAssignPK
	 * @return
	 */
	public static String getSaveOrUpdateSql(Integer dbType, EntityMeta entityMeta, PKStrategy pkStrategy,
			String[] forceUpdateFields, String tableName, String isNullFunction, String sequence, boolean isAssignPK) {
		// 在无主键的情况下产生insert sql语句
		if (entityMeta.getIdArray() == null) {
			return generateInsertSql(dbType, entityMeta, tableName, pkStrategy, isNullFunction, sequence, isAssignPK);
		}
		String realTable = entityMeta.getSchemaTable(tableName);
		int columnSize = entityMeta.getFieldsArray().length;
		StringBuilder sql = new StringBuilder(columnSize * 30 + 100);
		String columnName;
		sql.append("merge into ");
		sql.append(realTable);
		sql.append(" ta ");
		sql.append(" using (select ");
		for (int i = 0; i < columnSize; i++) {
			columnName = entityMeta.getColumnName(entityMeta.getFieldsArray()[i]);
			columnName = ReservedWordsUtil.convertWord(columnName, dbType);
			if (i > 0) {
				sql.append(",");
			}
			sql.append("? as ");
			sql.append(columnName);
		}
		sql.append(" from ").append(realTable);
		sql.append(") tv on (");
		StringBuilder idColumns = new StringBuilder();
		// 组织on部分的主键条件判断
		for (int i = 0, n = entityMeta.getIdArray().length; i < n; i++) {
			columnName = entityMeta.getColumnName(entityMeta.getIdArray()[i]);
			columnName = ReservedWordsUtil.convertWord(columnName, dbType);
			if (i > 0) {
				sql.append(" and ");
				idColumns.append(",");
			}
			sql.append(" ta.").append(columnName).append("=tv.").append(columnName);
			idColumns.append("ta.").append(columnName);
		}
		sql.append(" ) ");
		// 排除id的其他字段信息
		StringBuilder insertRejIdCols = new StringBuilder();
		StringBuilder insertRejIdColValues = new StringBuilder();
		// 是否全部是ID,匹配上则无需进行更新，只需将未匹配上的插入即可
		boolean allIds = (entityMeta.getRejectIdFieldArray() == null);
		if (!allIds) {
			// update 操作
			sql.append(" when matched then update set ");
			int rejectIdColumnSize = entityMeta.getRejectIdFieldArray().length;
			// 需要被强制修改的字段
			HashSet<String> fupc = new HashSet<String>();
			if (forceUpdateFields != null) {
				for (String field : forceUpdateFields) {
					fupc.add(ReservedWordsUtil.convertWord(entityMeta.getColumnName(field), dbType));
				}
			}
			FieldMeta fieldMeta;
			// update 只针对非主键字段进行修改
			boolean isStart = true;
			for (int i = 0; i < rejectIdColumnSize; i++) {
				fieldMeta = entityMeta.getFieldMeta(entityMeta.getRejectIdFieldArray()[i]);
				columnName = fieldMeta.getColumnName();
				columnName = ReservedWordsUtil.convertWord(columnName, dbType);
				if (i > 0) {
					sql.append(",");
				}
				sql.append(" ta.").append(columnName).append("=");
				// 强制修改
				if (fupc.contains(columnName)) {
					sql.append("tv.").append(columnName);
				} else {
					sql.append(isNullFunction);
					sql.append("(tv.").append(columnName);
					sql.append(",ta.").append(columnName);
					sql.append(")");
				}
				// sqlserver不支持timestamp类型的数据进行插入赋值
				if (fieldMeta.getType() != java.sql.Types.TIMESTAMP) {
					if (!isStart) {
						insertRejIdCols.append(",");
						insertRejIdColValues.append(",");
					}
					insertRejIdCols.append(columnName);
					isStart = false;

					// 存在默认值
					if (StringUtil.isNotBlank(fieldMeta.getDefaultValue())) {
						insertRejIdColValues.append(isNullFunction);
						insertRejIdColValues.append("(tv.").append(columnName).append(",");
						DialectUtils.processDefaultValue(insertRejIdColValues, dbType, fieldMeta.getType(),
								fieldMeta.getDefaultValue());
						insertRejIdColValues.append(")");
					} else {
						insertRejIdColValues.append("tv.").append(columnName);
					}
				}
			}
		}
		// 主键未匹配上则进行插入操作
		sql.append(" when not matched then insert (");
		String idsColumnStr = idColumns.toString();
		// 不考虑只有一个字段且还是主键的情况
		if (allIds) {
			sql.append(idsColumnStr.replaceAll("ta.", ""));
			sql.append(") values (");
			sql.append(idsColumnStr.replaceAll("ta.", "tv."));
		} else {
			sql.append(insertRejIdCols.toString());
			// sequence方式主键
			if (pkStrategy.equals(PKStrategy.SEQUENCE)) {
				columnName = entityMeta.getColumnName(entityMeta.getIdArray()[0]);
				columnName = ReservedWordsUtil.convertWord(columnName, dbType);
				sql.append(",");
				sql.append(columnName);
				sql.append(") values (");
				sql.append(insertRejIdColValues).append(",");
				if (isAssignPK) {
					sql.append(isNullFunction);
					sql.append("(tv.").append(columnName).append(",");
					sql.append(sequence).append(") ");
				} else {
					sql.append(sequence);
				}
			} else if (pkStrategy.equals(PKStrategy.IDENTITY)) {
				columnName = entityMeta.getColumnName(entityMeta.getIdArray()[0]);
				columnName = ReservedWordsUtil.convertWord(columnName, dbType);
				if (isAssignPK) {
					sql.append(",");
					sql.append(columnName);
				}
				sql.append(") values (");
				// identity 模式insert无需写插入该字段语句
				sql.append(insertRejIdColValues);
				if (isAssignPK) {
					sql.append(",").append("tv.").append(columnName);
				}
			} else {
				sql.append(",");
				sql.append(idsColumnStr.replaceAll("ta.", ""));
				sql.append(") values (");
				sql.append(insertRejIdColValues).append(",");
				sql.append(idsColumnStr.replaceAll("ta.", "tv."));
			}
		}
		sql.append(")");
		return sql.toString();
	}

	/**
	 * @todo sqlserver 相对特殊不支持timestamp类型的插入，所以单独提供sql生成功能
	 * @param dbType
	 * @param entityMeta
	 * @param pkStrategy
	 * @param tableName
	 * @param isNullFunction
	 * @param sequence
	 * @param isAssignPK
	 * @return
	 */
	public static String getSaveIgnoreExistSql(Integer dbType, EntityMeta entityMeta, PKStrategy pkStrategy,
			String tableName, String isNullFunction, String sequence, boolean isAssignPK) {
		// 在无主键的情况下产生insert sql语句
		if (entityMeta.getIdArray() == null) {
			return generateInsertSql(dbType, entityMeta, tableName, pkStrategy, isNullFunction, sequence, isAssignPK);
		}
		int columnSize = entityMeta.getFieldsArray().length;
		StringBuilder sql = new StringBuilder(columnSize * 30 + 100);
		String realTable = entityMeta.getSchemaTable(tableName);
		String columnName;
		sql.append("merge into ");
		sql.append(realTable);
		sql.append(" ta ");
		sql.append(" using (select ");
		for (int i = 0; i < columnSize; i++) {
			columnName = entityMeta.getColumnName(entityMeta.getFieldsArray()[i]);
			columnName = ReservedWordsUtil.convertWord(columnName, dbType);
			if (i > 0) {
				sql.append(",");
			}
			sql.append("? as ");
			sql.append(columnName);
		}
		sql.append(" from ").append(realTable);
		sql.append(") tv on (");
		StringBuilder idColumns = new StringBuilder();
		// 组织on部分的主键条件判断
		for (int i = 0, n = entityMeta.getIdArray().length; i < n; i++) {
			columnName = entityMeta.getColumnName(entityMeta.getIdArray()[i]);
			columnName = ReservedWordsUtil.convertWord(columnName, dbType);
			if (i > 0) {
				sql.append(" and ");
				idColumns.append(",");
			}
			sql.append(" ta.").append(columnName).append("=tv.").append(columnName);
			idColumns.append("ta.").append(columnName);
		}
		sql.append(" ) ");
		// 排除id的其他字段信息
		StringBuilder insertRejIdCols = new StringBuilder();
		StringBuilder insertRejIdColValues = new StringBuilder();
		// 是否全部是ID,匹配上则无需进行更新，只需将未匹配上的插入即可
		boolean allIds = (entityMeta.getRejectIdFieldArray() == null);
		if (!allIds) {
			int rejectIdColumnSize = entityMeta.getRejectIdFieldArray().length;
			FieldMeta fieldMeta;
			// update 只针对非主键字段进行修改
			boolean isStart = true;
			for (int i = 0; i < rejectIdColumnSize; i++) {
				fieldMeta = entityMeta.getFieldMeta(entityMeta.getRejectIdFieldArray()[i]);
				columnName = ReservedWordsUtil.convertWord(fieldMeta.getColumnName(), dbType);
				// sqlserver不支持timestamp类型的数据进行插入赋值
				if (fieldMeta.getType() != java.sql.Types.TIMESTAMP) {
					if (!isStart) {
						insertRejIdCols.append(",");
						insertRejIdColValues.append(",");
					}
					insertRejIdCols.append(columnName);
					isStart = false;

					// 存在默认值
					if (StringUtil.isNotBlank(fieldMeta.getDefaultValue())) {
						insertRejIdColValues.append(isNullFunction);
						insertRejIdColValues.append("(tv.").append(columnName).append(",");
						DialectUtils.processDefaultValue(insertRejIdColValues, dbType, fieldMeta.getType(),
								fieldMeta.getDefaultValue());
						insertRejIdColValues.append(")");
					} else {
						insertRejIdColValues.append("tv.").append(columnName);
					}
				}
			}
		}
		// 主键未匹配上则进行插入操作
		sql.append(" when not matched then insert (");
		String idsColumnStr = idColumns.toString();
		// 不考虑只有一个字段且还是主键的情况
		if (allIds) {
			sql.append(idsColumnStr.replaceAll("ta.", ""));
			sql.append(") values (");
			sql.append(idsColumnStr.replaceAll("ta.", "tv."));
		} else {
			sql.append(insertRejIdCols.toString());
			// sequence方式主键
			if (pkStrategy.equals(PKStrategy.SEQUENCE)) {
				columnName = entityMeta.getColumnName(entityMeta.getIdArray()[0]);
				columnName = ReservedWordsUtil.convertWord(columnName, dbType);
				sql.append(",");
				sql.append(columnName);
				sql.append(") values (");
				sql.append(insertRejIdColValues).append(",");
				if (isAssignPK) {
					sql.append(isNullFunction);
					sql.append("(tv.").append(columnName).append(",");
					sql.append(sequence).append(") ");
				} else {
					sql.append(sequence);
				}
			} else if (pkStrategy.equals(PKStrategy.IDENTITY)) {
				columnName = entityMeta.getColumnName(entityMeta.getIdArray()[0]);
				columnName = ReservedWordsUtil.convertWord(columnName, dbType);
				if (isAssignPK) {
					sql.append(",");
					sql.append(columnName);
				}
				sql.append(") values (");
				// identity 模式insert无需写插入该字段语句
				sql.append(insertRejIdColValues);
				if (isAssignPK) {
					sql.append(",").append("tv.").append(columnName);
				}
			} else {
				sql.append(",");
				sql.append(idsColumnStr.replaceAll("ta.", ""));
				sql.append(") values (");
				sql.append(insertRejIdColValues).append(",");
				sql.append(idsColumnStr.replaceAll("ta.", "tv."));
			}
		}
		sql.append(")");
		return sql.toString();
	}

	/**
	 * @todo 产生对象对应的insert sql语句
	 * @param dbType
	 * @param entityMeta
	 * @param tableName
	 * @param pkStrategy
	 * @param isNullFunction
	 * @param sequence
	 * @param isAssignPK
	 * @return
	 */
	public static String generateInsertSql(Integer dbType, EntityMeta entityMeta, String tableName,
			PKStrategy pkStrategy, String isNullFunction, String sequence, boolean isAssignPK) {
		int columnSize = entityMeta.getFieldsArray().length;
		StringBuilder sql = new StringBuilder(columnSize * 20 + 30);
		StringBuilder values = new StringBuilder(columnSize * 2 - 1);
		sql.append(" insert into ");
		sql.append(entityMeta.getSchemaTable(tableName));
		sql.append(" (");
		FieldMeta fieldMeta;
		String field;
		boolean isStart = true;
		String columnName;
		for (int i = 0; i < columnSize; i++) {
			field = entityMeta.getFieldsArray()[i];
			fieldMeta = entityMeta.getFieldMeta(field);
			columnName = ReservedWordsUtil.convertWord(fieldMeta.getColumnName(), dbType);
			if (fieldMeta.isPK()) {
				// identity主键策略，且支持主键手工赋值
				if (pkStrategy.equals(PKStrategy.IDENTITY)) {
					if (isAssignPK) {
						if (!isStart) {
							sql.append(",");
							values.append(",");
						}
						sql.append(columnName);
						values.append("?");
						isStart = false;
					}
				} else if (pkStrategy.equals(PKStrategy.SEQUENCE)) {
					if (!isStart) {
						sql.append(",");
						values.append(",");
					}
					sql.append(columnName);
					if (isAssignPK) {
						values.append(isNullFunction);
						values.append("(?,").append(sequence).append(")");
					} else {
						values.append(sequence);
					}
					isStart = false;
				} else if (fieldMeta.getType() != java.sql.Types.TIMESTAMP) {
					if (!isStart) {
						sql.append(",");
						values.append(",");
					}
					sql.append(columnName);
					values.append("?");
					isStart = false;
				}
			} else if (fieldMeta.getType() != java.sql.Types.TIMESTAMP) {
				if (!isStart) {
					sql.append(",");
					values.append(",");
				}
				sql.append(fieldMeta.getColumnName());
				if (StringUtil.isNotBlank(fieldMeta.getDefaultValue())) {
					values.append(isNullFunction);
					values.append("(?,");
					DialectUtils.processDefaultValue(values, dbType, fieldMeta.getType(), fieldMeta.getDefaultValue());
					values.append(")");
				} else {
					values.append("?");
				}
				isStart = false;
			}
		}

		sql.append(") values (");
		sql.append(values);
		sql.append(")");
		return sql.toString();
	}

	/**
	 * @todo 保存对象
	 * @param sqlToyContext
	 * @param entity
	 * @param conn
	 * @param dbType
	 * @param tableName
	 * @return
	 * @throws Exception
	 */
	public static Object save(SqlToyContext sqlToyContext, Serializable entity, final Connection conn,
			final Integer dbType, final String tableName) throws Exception {
		EntityMeta entityMeta = sqlToyContext.getEntityMeta(entity.getClass());
		final boolean isIdentity = entityMeta.getIdStrategy() != null
				&& entityMeta.getIdStrategy().equals(PKStrategy.IDENTITY);
		final boolean isSequence = entityMeta.getIdStrategy() != null
				&& entityMeta.getIdStrategy().equals(PKStrategy.SEQUENCE);

		String insertSql = generateInsertSql(dbType, entityMeta, tableName, entityMeta.getIdStrategy(), "isnull",
				"@mySeqVariable", isIdentity ? false : true);
		if (isSequence) {
			insertSql = "set nocount on DECLARE @mySeqVariable as numeric(20)=NEXT VALUE FOR "
					+ entityMeta.getSequence() + " " + insertSql + " select @mySeqVariable ";
		}
		int pkIndex = entityMeta.getIdIndex();
		ReflectPropertyHandler handler = DialectUtils.getAddReflectHandler(sqlToyContext, null);
		Object[] fullParamValues = BeanUtil.reflectBeanToAry(entity,
				(isIdentity) ? entityMeta.getRejectIdFieldArray() : entityMeta.getFieldsArray(), null, handler);
		boolean needUpdatePk = false;
		// 是否存在业务ID
		boolean hasBizId = (entityMeta.getBusinessIdGenerator() == null) ? false : true;
		int bizIdColIndex = hasBizId ? entityMeta.getFieldIndex(entityMeta.getBusinessIdField()) : 0;

		// 标识符
		String signature = entityMeta.getBizIdSignature();
		Integer[] relatedColumn = entityMeta.getBizIdRelatedColIndex();
		// 主键采用assign方式赋予，则调用generator产生id并赋予其值
		if (entityMeta.getIdStrategy() != null && null != entityMeta.getIdGenerator()) {
			int idLength = entityMeta.getIdLength();
			int bizIdLength = entityMeta.getBizIdLength();
			Object[] relatedColValue = null;
			String businessIdType = hasBizId ? entityMeta.getColumnJavaType(entityMeta.getBusinessIdField()) : "";
			if (relatedColumn != null) {
				relatedColValue = new Object[relatedColumn.length];
				for (int meter = 0; meter < relatedColumn.length; meter++) {
					relatedColValue[meter] = fullParamValues[relatedColumn[meter]];
					if (relatedColValue[meter] == null) {
						throw new IllegalArgumentException("对象:" + entityMeta.getEntityClass().getName()
								+ " 生成业务主键依赖的关联字段:" + relatedColumn[meter] + " 值为null!");
					}
				}
			}
			if (StringUtil.isBlank(fullParamValues[pkIndex])) {
				// id通过generator机制产生，设置generator产生的值
				fullParamValues[pkIndex] = entityMeta.getIdGenerator().getId(entityMeta.getTableName(), signature,
						entityMeta.getBizIdRelatedColumns(), relatedColValue, null, entityMeta.getIdType(), idLength,
						entityMeta.getBizIdSequenceSize());
				needUpdatePk = true;
			}
			if (hasBizId && StringUtil.isBlank(fullParamValues[bizIdColIndex])) {
				fullParamValues[bizIdColIndex] = entityMeta.getBusinessIdGenerator().getId(entityMeta.getTableName(),
						signature, entityMeta.getBizIdRelatedColumns(), relatedColValue, null, businessIdType,
						bizIdLength, entityMeta.getBizIdSequenceSize());
				// 回写业务主键值
				BeanUtil.setProperty(entity, entityMeta.getBusinessIdField(), fullParamValues[bizIdColIndex]);
			}
		}

		final Object[] paramValues = fullParamValues;
		final Integer[] paramsType = entityMeta.getFieldsTypeArray();

		if (sqlToyContext.isDebug()) {
			logger.debug(insertSql);
		}
		final String realInsertSql = insertSql;
		PreparedStatement pst = null;
		Object result = SqlUtil.preparedStatementProcess(null, pst, null, new PreparedStatementResultHandler() {
			public void execute(Object obj, PreparedStatement pst, ResultSet rs) throws SQLException, IOException {
				if (isIdentity) {
					pst = conn.prepareStatement(realInsertSql, PreparedStatement.RETURN_GENERATED_KEYS);
				} else {
					pst = conn.prepareStatement(realInsertSql);
				}
				if (null != paramValues && paramValues.length > 0) {
					int index = 0;
					for (int i = 0, n = paramValues.length; i < n; i++) {
						if (!paramsType[i].equals(java.sql.Types.TIMESTAMP)) {
							SqlUtil.setParamValue(conn, dbType, pst, paramValues[i], paramsType[i], index + 1);
							index++;
						}
					}
				}
				ResultSet keyResult = null;
				if (isSequence) {
					keyResult = pst.executeQuery();
				} else {
					pst.execute();
				}
				if (isIdentity) {
					keyResult = pst.getGeneratedKeys();
				}
				if (isSequence || isIdentity) {
					while (keyResult.next()) {
						this.setResult(keyResult.getObject(1));
					}
				}
			}
		});
		// 无主键直接返回null
		if (entityMeta.getIdArray() == null) {
			return null;
		}
		if (result == null) {
			result = fullParamValues[pkIndex];
		}
		// 回置到entity 主键值
		if (needUpdatePk || isIdentity || isSequence) {
			BeanUtil.setProperty(entity, entityMeta.getIdArray()[0], result);
		}
		// 是否有子表进行级联保存
		if (!entityMeta.getOneToManys().isEmpty()) {
			List subTableData;
			final Object[] idValues = BeanUtil.reflectBeanToAry(entity, entityMeta.getIdArray(), null, null);
			for (OneToManyModel oneToMany : entityMeta.getOneToManys()) {
				final String[] mappedFields = oneToMany.getMappedFields();
				subTableData = (List) BeanUtil.getProperty(entity, oneToMany.getProperty());
				if (subTableData != null && !subTableData.isEmpty()) {
					saveAll(sqlToyContext, subTableData, new ReflectPropertyHandler() {
						public void process() {
							for (int i = 0; i < mappedFields.length; i++) {
								this.setValue(mappedFields[i], idValues[i]);
							}
						}
					}, conn, dbType, null, null);
				}
			}
		}
		return result;
	}

	/**
	 * @todo 批量保存处理
	 * @param sqlToyContext
	 * @param entities
	 * @param reflectPropertyHandler
	 * @param conn
	 * @param dbType
	 * @param autoCommit
	 * @param tableName
	 * @return
	 * @throws Exception
	 */
	public static Long saveAll(SqlToyContext sqlToyContext, List<?> entities,
			ReflectPropertyHandler reflectPropertyHandler, Connection conn, final Integer dbType,
			final Boolean autoCommit, final String tableName) throws Exception {
		EntityMeta entityMeta = sqlToyContext.getEntityMeta(entities.get(0).getClass());
		boolean isAssignPK = isAssignPKValue(entityMeta.getIdStrategy());
		String insertSql = generateInsertSql(dbType, entityMeta, tableName, entityMeta.getIdStrategy(), "isnull",
				"@mySeqVariable", isAssignPK);
		if (entityMeta.getIdStrategy() != null && entityMeta.getIdStrategy().equals(PKStrategy.SEQUENCE)) {
			insertSql = "DECLARE @mySeqVariable as numeric(20)=NEXT VALUE FOR " + entityMeta.getSequence() + " "
					+ insertSql;
		}
		Long updateCount = saveAll(sqlToyContext, entityMeta, entityMeta.getIdStrategy(), isAssignPK, insertSql,
				entities, reflectPropertyHandler, conn, dbType, autoCommit);
		return updateCount;
	}

	/**
	 * @todo 保存批量对象数据
	 * @param sqlToyContext
	 * @param entityMeta
	 * @param pkStrategy
	 * @param isAssignPK
	 * @param insertSql
	 * @param entities
	 * @param reflectPropertyHandler
	 * @param conn
	 * @param dbType
	 * @param autoCommit
	 * @return
	 * @throws Exception
	 */
	private static Long saveAll(SqlToyContext sqlToyContext, EntityMeta entityMeta, PKStrategy pkStrategy,
			boolean isAssignPK, String insertSql, List<?> entities, ReflectPropertyHandler reflectPropertyHandler,
			Connection conn, final Integer dbType, final Boolean autoCommit) throws Exception {
		boolean isIdentity = pkStrategy != null && pkStrategy.equals(PKStrategy.IDENTITY);
		boolean isSequence = pkStrategy != null && pkStrategy.equals(PKStrategy.SEQUENCE);
		String[] reflectColumns;
		if ((isIdentity && !isAssignPK) || (isSequence && !isAssignPK)) {
			reflectColumns = entityMeta.getRejectIdFieldArray();
		} else {
			reflectColumns = entityMeta.getFieldsArray();
		}
		ReflectPropertyHandler handler = DialectUtils.getAddReflectHandler(sqlToyContext, reflectPropertyHandler);
		List<Object[]> paramValues = BeanUtil.reflectBeansToInnerAry(entities, reflectColumns, null, handler, false, 0);
		int pkIndex = entityMeta.getIdIndex();
		// 是否存在业务ID
		boolean hasBizId = (entityMeta.getBusinessIdGenerator() == null) ? false : true;
		int bizIdColIndex = hasBizId ? entityMeta.getFieldIndex(entityMeta.getBusinessIdField()) : 0;
		// 标识符
		String signature = entityMeta.getBizIdSignature();
		Integer[] relatedColumn = entityMeta.getBizIdRelatedColIndex();

		// 无主键值以及多主键以及assign或通过generator方式产生主键策略
		if (pkStrategy != null && null != entityMeta.getIdGenerator()) {
			int idLength = entityMeta.getIdLength();
			int bizIdLength = entityMeta.getBizIdLength();
			Object[] rowData;
			boolean isAssigned = true;
			List<Object[]> idSet = new ArrayList<Object[]>();
			String idJdbcType = entityMeta.getIdType();
			Object[] relatedColValue = null;
			String businessIdType = hasBizId ? entityMeta.getColumnJavaType(entityMeta.getBusinessIdField()) : "";
			for (int i = 0, s = paramValues.size(); i < s; i++) {
				rowData = (Object[]) paramValues.get(i);
				if (relatedColumn != null) {
					relatedColValue = new Object[relatedColumn.length];
					for (int meter = 0; meter < relatedColumn.length; meter++) {
						relatedColValue[meter] = rowData[relatedColumn[meter]];
						if (relatedColValue[meter] == null) {
							throw new IllegalArgumentException("对象:" + entityMeta.getEntityClass().getName()
									+ " 生成业务主键依赖的关联字段:" + relatedColumn[meter] + " 值为null!");
						}
					}
				}
				if (StringUtil.isBlank(rowData[pkIndex])) {
					isAssigned = false;
					rowData[pkIndex] = entityMeta.getIdGenerator().getId(entityMeta.getTableName(), signature,
							entityMeta.getBizIdRelatedColumns(), relatedColValue, null, idJdbcType, idLength,
							entityMeta.getBizIdSequenceSize());
				}
				if (hasBizId && StringUtil.isBlank(rowData[bizIdColIndex])) {
					rowData[bizIdColIndex] = entityMeta.getBusinessIdGenerator().getId(entityMeta.getTableName(),
							signature, entityMeta.getBizIdRelatedColumns(), relatedColValue, null, businessIdType,
							bizIdLength, entityMeta.getBizIdSequenceSize());
					// 回写业务主键值
					BeanUtil.setProperty(entities.get(i), entityMeta.getBusinessIdField(), rowData[bizIdColIndex]);
				}
				idSet.add(new Object[] { rowData[pkIndex] });
			}
			// 批量反向设置最终得到的主键值
			if (!isAssigned) {
				BeanUtil.mappingSetProperties(entities, entityMeta.getIdArray(), idSet, new int[] { 0 }, true);
			}
		}
		if (sqlToyContext.isDebug()) {
			logger.debug("batch insert sql:{}", insertSql);
		}
		return batchUpdateByJdbc(insertSql, paramValues, sqlToyContext.getBatchSize(), entityMeta.getFieldsTypeArray(),
				autoCommit, conn, dbType);
	}

	/**
	 * @todo 针对sqlserver timestamp不能保存情况,提供特殊的批量执行方式，跳过类型为timestamp的值
	 * @param updateSql
	 * @param rowDatas
	 * @param batchSize
	 * @param updateTypes
	 * @param autoCommit
	 * @param conn
	 * @param dbType
	 * @return
	 * @throws Exception
	 */
	private static Long batchUpdateByJdbc(final String updateSql, final List<Object[]> rowDatas, final int batchSize,
			final Integer[] updateTypes, final Boolean autoCommit, final Connection conn, final Integer dbType)
			throws Exception {
		if (rowDatas == null) {
			logger.error("batchUpdateByJdbc:{} 传递的数据为空!", updateSql);
			return 0L;
		}
		PreparedStatement pst = null;
		long updateCount = 0;
		try {
			boolean hasSetAutoCommit = false;
			// 是否自动提交
			if (autoCommit != null && !autoCommit == conn.getAutoCommit()) {
				conn.setAutoCommit(autoCommit);
				hasSetAutoCommit = true;
			}
			pst = conn.prepareStatement(updateSql);
			int totalRows = rowDatas.size();
			boolean useBatch = (totalRows > 1) ? true : false;
			Object[] rowData;
			// 批处理计数器
			int meter = 0;
			int pstIndex = 0;
			for (int i = 0; i < totalRows; i++) {
				rowData = rowDatas.get(i);
				if (rowData != null) {
					// 使用对象properties方式传值
					pstIndex = 0;
					for (int j = 0, n = rowData.length; j < n; j++) {
						// 类型为timestamp 则跳过
						if (!updateTypes[j].equals(java.sql.Types.TIMESTAMP)) {
							SqlUtil.setParamValue(conn, dbType, pst, rowData[j], updateTypes[j], pstIndex + 1);
							pstIndex++;
						}
					}
					meter++;
					if (useBatch) {
						pst.addBatch();
						if ((meter % batchSize) == 0 || i + 1 == totalRows) {
							int[] updateRows = pst.executeBatch();
							for (int t : updateRows) {
								updateCount = updateCount + ((t > 0) ? t : 0);
							}
							pst.clearBatch();
						}
					} else {
						pst.execute();
						updateCount = updateCount + ((pst.getUpdateCount() > 0) ? pst.getUpdateCount() : 0);
					}
				}
			}
			if (hasSetAutoCommit) {
				conn.setAutoCommit(!autoCommit);
			}
		} catch (Exception e) {
			logger.error(e.getMessage(), e);
			e.printStackTrace();
			throw e;
		} finally {
			try {
				if (pst != null) {
					pst.close();
					pst = null;
				}
			} catch (SQLException se) {
				logger.error(se.getMessage(), se);
				se.printStackTrace();
			}
		}
		return updateCount;
	}

	/**
	 * @todo 单个对象修改，包含级联修改
	 * @param sqlToyContext
	 * @param entity
	 * @param forceUpdateFields
	 * @param cascade
	 * @param emptyCascadeClasses
	 * @param subTableForceUpdateProps
	 * @param conn
	 * @param dbType
	 * @param tableName
	 * @return
	 * @throws Exception
	 */
	public static Long update(SqlToyContext sqlToyContext, Serializable entity, String[] forceUpdateFields,
			final boolean cascade, final Class[] emptyCascadeClasses,
			final HashMap<Class, String[]> subTableForceUpdateProps, Connection conn, final Integer dbType,
			final String tableName) throws Exception {
		EntityMeta entityMeta = sqlToyContext.getEntityMeta(entity.getClass());
		Long updateCount = DialectUtils.update(sqlToyContext, entity, entityMeta, "isnull", forceUpdateFields, conn,
				dbType, tableName);

		// 级联修改
		if (cascade && !entityMeta.getOneToManys().isEmpty()) {
			HashMap<Type, String> typeMap = new HashMap<Type, String>();
			if (emptyCascadeClasses != null)
				for (Type type : emptyCascadeClasses) {
					typeMap.put(type, "");
				}
			// 级联子表数据
			List subTableData;
			final Object[] IdValues = BeanUtil.reflectBeanToAry(entity, entityMeta.getIdArray(), null, null);
			String[] forceUpdateProps = null;
			for (OneToManyModel oneToMany : entityMeta.getOneToManys()) {
				forceUpdateProps = (subTableForceUpdateProps == null) ? null
						: subTableForceUpdateProps.get(oneToMany.getMappedType());
				subTableData = (List) BeanUtil.invokeMethod(entity,
						"get".concat(StringUtil.firstToUpperCase(oneToMany.getProperty())), null);
				final String[] mappedFields = oneToMany.getMappedFields();
				/**
				 * 针对存量子表数据,调用级联修改的语句，分delete 和update两种操作 1、删除存量数据;2、设置存量数据状态为停用
				 */
				if (oneToMany.getCascadeUpdateSql() != null && ((subTableData != null && !subTableData.isEmpty())
						|| typeMap.containsKey(oneToMany.getMappedType()))) {
					SqlToyResult sqlToyResult = SqlConfigParseUtils.processSql(oneToMany.getCascadeUpdateSql(),
							mappedFields, IdValues);
					DialectUtils.executeSql(sqlToyContext, sqlToyResult.getSql(), sqlToyResult.getParamsValue(), null,
							conn, dbType, null);
				}
				// 子表数据不为空,采取saveOrUpdateAll操作
				if (subTableData != null && !subTableData.isEmpty()) {
					saveOrUpdateAll(sqlToyContext, subTableData, sqlToyContext.getBatchSize(),
							// 设置关联外键字段的属性值(来自主表的主键)
							new ReflectPropertyHandler() {
								public void process() {
									for (int i = 0; i < mappedFields.length; i++) {
										this.setValue(mappedFields[i], IdValues[i]);
									}
								}
							}, forceUpdateProps, conn, dbType, null, null);
				}
			}
		}
		return updateCount;
	}

	/**
	 * @todo 组织基于sqlserver的锁记录查询sql语句
	 * @param loadSql
	 * @param tableName
	 * @param lockMode
	 * @return
	 */
	public static String lockSql(String loadSql, String tableName, LockMode lockMode) {
		if (lockMode != null) {
			int fromIndex = StringUtil.getSymMarkMatchIndex("(?i)select\\s+", "(?i)\\s+from[\\(\\s+]", loadSql, 0);
			String selectPart = loadSql.substring(0, fromIndex);
			String fromPart = loadSql.substring(fromIndex);
			String[] sqlChips = fromPart.trim().split("\\s+");
			String realTableName = (tableName == null) ? sqlChips[1] : tableName;
			if (realTableName.indexOf(",") != -1) {
				realTableName = realTableName.substring(0, realTableName.indexOf(","));
			}
			String tmp;
			int chipSize = sqlChips.length;
			String replaceStr = realTableName;
			String regex = realTableName;
			// sqlserver lock 必须在table 后面(如果有别名则在别名后面),这里实现对table和别名位置的查找
			for (int i = 0; i < chipSize; i++) {
				tmp = sqlChips[i];
				if (tmp.toLowerCase().indexOf(realTableName.toLowerCase()) != -1) {
					if (sqlChips[i + 1].equalsIgnoreCase("as")) {
						regex = realTableName.concat("\\s+as\\s+").concat(sqlChips[i + 2]);
						replaceStr = realTableName.concat(" as ").concat(sqlChips[i + 2]);
						break;
					} else if (sqlChips[i + 2].equalsIgnoreCase("where")) {
						regex = realTableName.concat("\\s+").concat(sqlChips[i + 1]);
						replaceStr = realTableName.concat(" ").concat(sqlChips[i + 1]);
						break;
					} else if (sqlChips[i + 2].equals(",")) {
						regex = realTableName.concat("\\s+").concat(sqlChips[i + 1]).concat(",");
						replaceStr = realTableName.concat(" ").concat(sqlChips[i + 1]);
						break;
					} else if (i + 3 < chipSize && sqlChips[i + 3].equalsIgnoreCase("join")) {
						regex = realTableName.concat("\\s+").concat(sqlChips[i + 1]);
						replaceStr = realTableName.concat(" ").concat(sqlChips[i + 1]);
						break;
					}
				}
			}
			switch (lockMode) {
			case UPGRADE_NOWAIT:
			case UPGRADE:
				loadSql = selectPart.concat(fromPart.replaceFirst("(?i)".concat(regex), replaceStr.replace(",", "")
						.concat(" with (rowlock xlock) ").concat((regex.endsWith(",") ? "," : ""))));
				break;
			}
		}
		return loadSql;
	}

	private static boolean isAssignPKValue(PKStrategy pkStrategy) {
		if (pkStrategy == null)
			return true;
		if (pkStrategy.equals(PKStrategy.SEQUENCE))
			return true;
		if (pkStrategy.equals(PKStrategy.IDENTITY))
			return false;
		return true;
	}
}
