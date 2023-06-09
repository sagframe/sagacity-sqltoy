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
import java.util.Map;

import org.sagacity.sqltoy.SqlExecuteStat;
import org.sagacity.sqltoy.SqlToyConstants;
import org.sagacity.sqltoy.SqlToyContext;
import org.sagacity.sqltoy.callback.DecryptHandler;
import org.sagacity.sqltoy.callback.GenerateSqlHandler;
import org.sagacity.sqltoy.callback.PreparedStatementResultHandler;
import org.sagacity.sqltoy.callback.ReflectPropsHandler;
import org.sagacity.sqltoy.config.SqlConfigParseUtils;
import org.sagacity.sqltoy.config.model.EntityMeta;
import org.sagacity.sqltoy.config.model.FieldMeta;
import org.sagacity.sqltoy.config.model.OperateType;
import org.sagacity.sqltoy.config.model.PKStrategy;
import org.sagacity.sqltoy.config.model.SqlToyConfig;
import org.sagacity.sqltoy.config.model.SqlToyResult;
import org.sagacity.sqltoy.config.model.SqlType;
import org.sagacity.sqltoy.config.model.SqlWithAnalysis;
import org.sagacity.sqltoy.config.model.TableCascadeModel;
import org.sagacity.sqltoy.model.ColumnMeta;
import org.sagacity.sqltoy.model.IgnoreCaseSet;
import org.sagacity.sqltoy.model.IgnoreKeyCaseMap;
import org.sagacity.sqltoy.model.LockMode;
import org.sagacity.sqltoy.model.QueryExecutor;
import org.sagacity.sqltoy.model.QueryResult;
import org.sagacity.sqltoy.model.TableMeta;
import org.sagacity.sqltoy.model.inner.QueryExecutorExtend;
import org.sagacity.sqltoy.plugins.IUnifyFieldsHandler;
import org.sagacity.sqltoy.utils.BeanUtil;
import org.sagacity.sqltoy.utils.CollectionUtil;
import org.sagacity.sqltoy.utils.DataSourceUtils.DBType;
import org.sagacity.sqltoy.utils.DataSourceUtils.Dialect;
import org.sagacity.sqltoy.utils.ReservedWordsUtil;
import org.sagacity.sqltoy.utils.SqlUtil;
import org.sagacity.sqltoy.utils.SqlUtilsExt;
import org.sagacity.sqltoy.utils.StringUtil;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * @project sqltoy-orm
 * @description 提供基于sqlserver这种广泛应用的数据库通用的逻辑处理,避免大量重复代码
 * @author zhongxuchen
 * @version v1.0,Date:2014年12月26日
 * @modify Date:2020-2-5 废弃对sqlserver2008 的支持,最低版本为2012版
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
	 * @param fetchSize
	 * @param maxRows
	 * @return
	 * @throws Exception
	 */
	public static QueryResult getRandomResult(SqlToyContext sqlToyContext, SqlToyConfig sqlToyConfig,
			QueryExecutor queryExecutor, final DecryptHandler decryptHandler, Long totalCount, Long randomCount,
			Connection conn, final Integer dbType, final String dialect, final int fetchSize, final int maxRows)
			throws Exception {
		StringBuilder sql = new StringBuilder();
		// sqlserver 不支持内部order by
		String innerSql = sqlToyConfig.isHasFast() ? sqlToyConfig.getFastSql(dialect) : sqlToyConfig.getSql(dialect);
		if (sqlToyConfig.isHasFast()) {
			sql.append(sqlToyConfig.getFastPreSql(dialect));
			if (!sqlToyConfig.isIgnoreBracket()) {
				sql.append(" (");
			}
		}
		String partSql = " select top " + randomCount + " ";
		if (sqlToyConfig.isHasWith()) {
			SqlWithAnalysis sqlWith = new SqlWithAnalysis(innerSql);
			sql.append(sqlWith.getWithSql());
			innerSql = sqlWith.getRejectWithSql();
		}
		// sql中是否存在排序或union
		boolean hasOrderOrUnion = DialectUtils.hasOrderByOrUnion(innerSql);
		// 给原始sql标记上特殊的开始和结尾，便于sql拦截器快速定位到原始sql并进行条件补充
		innerSql = SqlUtilsExt.markOriginalSql(innerSql);
		// 存在order 或union 则在sql外包裹一层
		if (hasOrderOrUnion) {
			sql.append(partSql);
			sql.append(" " + SqlToyConstants.INTERMEDIATE_TABLE + ".* from (");
			sql.append(innerSql);
			sql.append(") ");
			sql.append(SqlToyConstants.INTERMEDIATE_TABLE);
			sql.append(" ");
		} else {
			sql.append(innerSql.replaceFirst("(?i)select ", partSql));
		}
		sql.append(" order by NEWID() ");

		if (sqlToyConfig.isHasFast()) {
			if (!sqlToyConfig.isIgnoreBracket()) {
				sql.append(") ");
			}
			sql.append(sqlToyConfig.getFastTailSql(dialect));
		}
		QueryExecutorExtend extend = queryExecutor.getInnerModel();
		SqlToyResult queryParam = SqlConfigParseUtils.processSql(sql.toString(), extend.getParamsName(),
				extend.getParamsValue(sqlToyContext, sqlToyConfig), dialect);
		// 增加sql执行拦截器 update 2022-9-10
		queryParam = DialectUtils.doInterceptors(sqlToyContext, sqlToyConfig,
				(extend.entityClass == null) ? OperateType.random : OperateType.singleTable, queryParam,
				extend.entityClass, dbType);
		return DialectUtils.findBySql(sqlToyContext, sqlToyConfig, queryParam.getSql(), queryParam.getParamsValue(),
				extend, decryptHandler, conn, dbType, 0, fetchSize, maxRows);
	}

	/**
	 * @todo 批量保存或修改
	 * @param sqlToyContext
	 * @param entities
	 * @param batchSize
	 * @param reflectPropsHandler
	 * @param forceUpdateFields
	 * @param conn
	 * @param dbType
	 * @param autoCommit
	 * @param tableName
	 * @return
	 * @throws Exception
	 */
	public static Long saveOrUpdateAll(final SqlToyContext sqlToyContext, List<?> entities, final int batchSize,
			final ReflectPropsHandler reflectPropsHandler, final String[] forceUpdateFields, Connection conn,
			final Integer dbType, final Boolean autoCommit, final String tableName) throws Exception {
		EntityMeta entityMeta = sqlToyContext.getEntityMeta(entities.get(0).getClass());
		// sqlserver merge into must end with ";" charater
		// 返回记录变更量
		return DialectUtils.saveOrUpdateAll(sqlToyContext, entities, batchSize, entityMeta, forceUpdateFields,
				new GenerateSqlHandler() {
					@Override
					public String generateSql(EntityMeta entityMeta, String[] forceUpdateFields) {
						String sql = SqlServerDialectUtils.getSaveOrUpdateSql(sqlToyContext.getUnifyFieldsHandler(),
								dbType, entityMeta, entityMeta.getIdStrategy(), forceUpdateFields, tableName, "isnull",
								"@mySeqVariable", false);
						if (entityMeta.getIdStrategy() != null
								&& entityMeta.getIdStrategy().equals(PKStrategy.SEQUENCE)) {
							sql = "DECLARE @mySeqVariable as numeric(20)=NEXT VALUE FOR " + entityMeta.getSequence()
									+ " " + sql;
						}
						return sql.concat(";");
					}
				}, reflectPropsHandler, conn, dbType, autoCommit);
	}

	/**
	 * @todo sqlserver 相对特殊不支持timestamp类型的插入，所以单独提供sql生成功能
	 * @param unifyFieldsHandler
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
	public static String getSaveOrUpdateSql(IUnifyFieldsHandler unifyFieldsHandler, Integer dbType,
			EntityMeta entityMeta, PKStrategy pkStrategy, String[] forceUpdateFields, String tableName,
			String isNullFunction, String sequence, boolean isAssignPK) {
		// 在无主键的情况下产生insert sql语句
		if (entityMeta.getIdArray() == null) {
			return generateInsertSql(unifyFieldsHandler, dbType, entityMeta, tableName, pkStrategy, isNullFunction,
					sequence, isAssignPK);
		}
		// 将新增记录统一赋值属性模拟成默认值模式
		IgnoreKeyCaseMap<String, Object> createUnifyFields = null;
		if (unifyFieldsHandler != null && unifyFieldsHandler.createUnifyFields() != null
				&& !unifyFieldsHandler.createUnifyFields().isEmpty()) {
			createUnifyFields = new IgnoreKeyCaseMap<String, Object>();
			createUnifyFields.putAll(unifyFieldsHandler.createUnifyFields());
		}
		// 创建记录时，创建时间、最后修改时间等取数据库时间
		IgnoreCaseSet createSqlTimeFields = (unifyFieldsHandler == null
				|| unifyFieldsHandler.createSqlTimeFields() == null) ? new IgnoreCaseSet()
						: unifyFieldsHandler.createSqlTimeFields();
		// 修改记录时，最后修改时间等取数据库时间
		IgnoreCaseSet updateSqlTimeFields = (unifyFieldsHandler == null
				|| unifyFieldsHandler.updateSqlTimeFields() == null) ? new IgnoreCaseSet()
						: unifyFieldsHandler.updateSqlTimeFields();
		String currentTimeStr;
		String realTable = entityMeta.getSchemaTable(tableName, dbType);
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
		sql.append(SqlToyConstants.MERGE_ALIAS_ON);
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
			sql.append(SqlToyConstants.MERGE_UPDATE);
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
			int meter = 0;
			String defaultValue;
			int decimalLength;
			int decimalScale;
			for (int i = 0; i < rejectIdColumnSize; i++) {
				fieldMeta = entityMeta.getFieldMeta(entityMeta.getRejectIdFieldArray()[i]);
				// sqlserver不支持timestamp类型的数据进行插入赋值和变更
				if (fieldMeta.getType() != java.sql.Types.TIMESTAMP) {
					columnName = fieldMeta.getColumnName();
					columnName = ReservedWordsUtil.convertWord(columnName, dbType);
					if (meter > 0) {
						sql.append(",");
					}
					sql.append(" ta.").append(columnName).append("=");
					// 强制修改
					if (fupc.contains(columnName)) {
						sql.append("tv.").append(columnName);
					} else {
						sql.append(isNullFunction);
						// 解决decimal 类型小数位丢失问题
						if (fieldMeta.getType() == java.sql.Types.DECIMAL) {
							decimalLength = (fieldMeta.getLength() > 35) ? fieldMeta.getLength() : 35;
							decimalScale = (fieldMeta.getScale() > 5) ? fieldMeta.getScale() : 5;
							sql.append("(cast(tv.").append(columnName)
									.append(" as decimal(" + decimalLength + "," + decimalScale + "))");
						} else {
							sql.append("(tv.").append(columnName);
						}
						sql.append(",");
						// 修改时间设置数据库时间nvl(?,current_timestamp)
						currentTimeStr = SqlUtil.getDBTime(dbType, fieldMeta, updateSqlTimeFields);
						if (null != currentTimeStr) {
							sql.append(currentTimeStr);
						} else {
							sql.append("ta.").append(columnName);
						}
						sql.append(")");
					}
					if (!isStart) {
						insertRejIdCols.append(",");
						insertRejIdColValues.append(",");
					}
					insertRejIdCols.append(columnName);
					isStart = false;
					// 将创建人、创建时间等模拟成默认值
					defaultValue = DialectExtUtils.getInsertDefaultValue(createUnifyFields, dbType, fieldMeta);
					// 存在默认值
					if (null != defaultValue) {
						insertRejIdColValues.append(isNullFunction);
						insertRejIdColValues.append("(tv.").append(columnName).append(",");
						DialectExtUtils.processDefaultValue(insertRejIdColValues, dbType, fieldMeta, defaultValue);
						insertRejIdColValues.append(")");
					} else {
						currentTimeStr = SqlUtil.getDBTime(dbType, fieldMeta, createSqlTimeFields);
						if (null != currentTimeStr) {
							insertRejIdColValues.append(isNullFunction);
							insertRejIdColValues.append("(tv.").append(columnName).append(",");
							insertRejIdColValues.append(currentTimeStr);
							insertRejIdColValues.append(")");
						} else {
							insertRejIdColValues.append("tv.").append(columnName);
						}
					}
					meter++;
				}
			}
		}
		// 主键未匹配上则进行插入操作
		sql.append(SqlToyConstants.MERGE_INSERT);
		sql.append(" (");
		String idsColumnStr = idColumns.toString();
		// 不考虑只有一个字段且还是主键的情况
		if (allIds) {
			sql.append(idsColumnStr.replace("ta.", ""));
			sql.append(") values (");
			sql.append(idsColumnStr.replace("ta.", "tv."));
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
				sql.append(idsColumnStr.replace("ta.", ""));
				sql.append(") values (");
				sql.append(insertRejIdColValues).append(",");
				sql.append(idsColumnStr.replace("ta.", "tv."));
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
	public static String getSaveIgnoreExistSql(IUnifyFieldsHandler unifyFieldsHandler, Integer dbType,
			EntityMeta entityMeta, PKStrategy pkStrategy, String tableName, String isNullFunction, String sequence,
			boolean isAssignPK) {
		// 在无主键的情况下产生insert sql语句
		if (entityMeta.getIdArray() == null) {
			return generateInsertSql(unifyFieldsHandler, dbType, entityMeta, tableName, pkStrategy, isNullFunction,
					sequence, isAssignPK);
		}
		int columnSize = entityMeta.getFieldsArray().length;
		StringBuilder sql = new StringBuilder(columnSize * 30 + 100);
		String realTable = entityMeta.getSchemaTable(tableName, dbType);
		String columnName;
		// 创建记录时，创建时间、最后修改时间等取数据库时间
		IgnoreCaseSet createSqlTimeFields = (unifyFieldsHandler == null
				|| unifyFieldsHandler.createSqlTimeFields() == null) ? new IgnoreCaseSet()
						: unifyFieldsHandler.createSqlTimeFields();
		String currentTimeStr;
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
		sql.append(SqlToyConstants.MERGE_ALIAS_ON);
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
					// 2023-5-11 新增操作待增加对default值的处理,nvl(?,current_timestamp)
					currentTimeStr = SqlUtil.getDBTime(dbType, fieldMeta, createSqlTimeFields);
					if (null != currentTimeStr) {
						insertRejIdColValues.append(isNullFunction);
						insertRejIdColValues.append("(tv.").append(columnName);
						insertRejIdColValues.append(",").append(currentTimeStr);
						insertRejIdColValues.append(")");
					} else {
						insertRejIdColValues.append("tv.").append(columnName);
					}
				}
			}
		}
		// 主键未匹配上则进行插入操作
		sql.append(SqlToyConstants.MERGE_INSERT);
		sql.append(" (");
		String idsColumnStr = idColumns.toString();
		// 不考虑只有一个字段且还是主键的情况
		if (allIds) {
			sql.append(idsColumnStr.replace("ta.", ""));
			sql.append(") values (");
			sql.append(idsColumnStr.replace("ta.", "tv."));
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
				sql.append(idsColumnStr.replace("ta.", ""));
				sql.append(") values (");
				sql.append(insertRejIdColValues).append(",");
				sql.append(idsColumnStr.replace("ta.", "tv."));
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
	public static String generateInsertSql(IUnifyFieldsHandler unifyFieldsHandler, Integer dbType,
			EntityMeta entityMeta, String tableName, PKStrategy pkStrategy, String isNullFunction, String sequence,
			boolean isAssignPK) {
		int columnSize = entityMeta.getFieldsArray().length;
		StringBuilder sql = new StringBuilder(columnSize * 20 + 30);
		StringBuilder values = new StringBuilder(columnSize * 2 - 1);
		sql.append(" insert into ");
		sql.append(entityMeta.getSchemaTable(tableName, dbType));
		sql.append(" (");
		FieldMeta fieldMeta;
		String field;
		boolean isStart = true;
		String columnName;
		// 创建记录时，创建时间、最后修改时间等取数据库时间
		IgnoreCaseSet createSqlTimeFields = (unifyFieldsHandler == null
				|| unifyFieldsHandler.createSqlTimeFields() == null) ? new IgnoreCaseSet()
						: unifyFieldsHandler.createSqlTimeFields();
		String currentTimeStr;
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
				// 2023-5-11 新增操作待增加对default值的处理,nvl(?,current_timestamp)
				currentTimeStr = SqlUtil.getDBTime(dbType, fieldMeta, createSqlTimeFields);
				if (null != currentTimeStr) {
					values.append(isNullFunction).append("(?,").append(currentTimeStr).append(")");
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

		String insertSql = generateInsertSql(sqlToyContext.getUnifyFieldsHandler(), dbType, entityMeta, tableName,
				entityMeta.getIdStrategy(), "isnull", "@mySeqVariable", isIdentity ? false : true);
		if (isSequence) {
			insertSql = "set nocount on DECLARE @mySeqVariable as numeric(20)=NEXT VALUE FOR "
					+ entityMeta.getSequence() + " " + insertSql + " select @mySeqVariable ";
		}
		int pkIndex = entityMeta.getIdIndex();
		ReflectPropsHandler handler = DialectUtils.getAddReflectHandler(entityMeta, null,
				sqlToyContext.getUnifyFieldsHandler());
		handler = DialectUtils.getSecureReflectHandler(handler, sqlToyContext.getFieldsSecureProvider(),
				sqlToyContext.getDesensitizeProvider(), entityMeta.getSecureFields());
		String[] reflectColumns = (isIdentity) ? entityMeta.getRejectIdFieldArray() : entityMeta.getFieldsArray();
		Object[] fullParamValues = BeanUtil.reflectBeanToAry(entity, reflectColumns,
				SqlUtilsExt.getDefaultValues(entityMeta), handler);
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

		SqlToyConfig sqlToyConfig = new SqlToyConfig(Dialect.SQLSERVER);
		sqlToyConfig.setSqlType(SqlType.insert);
		sqlToyConfig.setSql(insertSql);
		sqlToyConfig.setParamsName(reflectColumns);
		SqlToyResult sqlToyResult = new SqlToyResult(insertSql, fullParamValues);
		sqlToyResult = DialectUtils.doInterceptors(sqlToyContext, sqlToyConfig, OperateType.insert, sqlToyResult,
				entity.getClass(), dbType);
		final Object[] paramValues = sqlToyResult.getParamsValue();
		final Integer[] paramsType = entityMeta.getFieldsTypeArray();
		final String realInsertSql = sqlToyResult.getSql();
		SqlExecuteStat.showSql("mssql单条记录插入", realInsertSql, null);
		PreparedStatement pst = null;
		Object result = SqlUtil.preparedStatementProcess(null, pst, null, new PreparedStatementResultHandler() {
			@Override
			public void execute(Object obj, PreparedStatement pst, ResultSet rs) throws SQLException, IOException {
				if (isIdentity) {
					pst = conn.prepareStatement(realInsertSql,
							new String[] { entityMeta.getColumnName(entityMeta.getIdArray()[0]) });
				} else {
					pst = conn.prepareStatement(realInsertSql);
				}
				if (null != paramValues && paramValues.length > 0) {
					int index = 0;
					for (int i = 0, n = paramValues.length; i < n; i++) {
						// sqlserver timestamp类型的字段无需赋值
						if (!paramsType[i].equals(java.sql.Types.TIMESTAMP)) {
							SqlUtil.setParamValue(sqlToyContext.getTypeHandler(), conn, dbType, pst, paramValues[i],
									paramsType[i], index + 1);
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
				if ((isSequence || isIdentity) && keyResult != null) {
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
		if (!entityMeta.getCascadeModels().isEmpty()) {
			List subTableData = null;
			EntityMeta subTableEntityMeta;
			for (TableCascadeModel cascadeModel : entityMeta.getCascadeModels()) {
				final Object[] mainFieldValues = BeanUtil.reflectBeanToAry(entity, cascadeModel.getFields());
				final String[] mappedFields = cascadeModel.getMappedFields();
				subTableEntityMeta = sqlToyContext.getEntityMeta(cascadeModel.getMappedType());
				if (cascadeModel.getCascadeType() == 1) {
					subTableData = (List) BeanUtil.getProperty(entity, cascadeModel.getProperty());
				} else {
					subTableData = new ArrayList();
					Object item = BeanUtil.getProperty(entity, cascadeModel.getProperty());
					if (item != null) {
						subTableData.add(item);
					}
				}
				if (subTableData != null && !subTableData.isEmpty()) {
					logger.info("执行save操作的级联子表{}批量保存!", subTableEntityMeta.getTableName());
					SqlExecuteStat.debug("执行子表级联保存操作", null);
					saveAll(sqlToyContext, subTableData, new ReflectPropsHandler() {
						@Override
						public void process() {
							for (int i = 0; i < mappedFields.length; i++) {
								this.setValue(mappedFields[i], mainFieldValues[i]);
							}
						}
					}, conn, dbType, null, null);
				} else {
					logger.info("未执行save操作的级联子表{}批量保存,子表数据为空!", subTableEntityMeta.getTableName());
				}
			}
		}
		return result;
	}

	/**
	 * @todo 批量保存处理
	 * @param sqlToyContext
	 * @param entities
	 * @param reflectPropsHandler
	 * @param conn
	 * @param dbType
	 * @param autoCommit
	 * @param tableName
	 * @return
	 * @throws Exception
	 */
	public static Long saveAll(SqlToyContext sqlToyContext, List<?> entities, ReflectPropsHandler reflectPropsHandler,
			Connection conn, final Integer dbType, final Boolean autoCommit, final String tableName) throws Exception {
		EntityMeta entityMeta = sqlToyContext.getEntityMeta(entities.get(0).getClass());
		boolean isAssignPK = isAssignPKValue(entityMeta.getIdStrategy());
		String insertSql = generateInsertSql(sqlToyContext.getUnifyFieldsHandler(), dbType, entityMeta, tableName,
				entityMeta.getIdStrategy(), "isnull", "@mySeqVariable", isAssignPK);
		if (entityMeta.getIdStrategy() != null && entityMeta.getIdStrategy().equals(PKStrategy.SEQUENCE)) {
			insertSql = "DECLARE @mySeqVariable as numeric(20)=NEXT VALUE FOR " + entityMeta.getSequence() + " "
					+ insertSql;
		}
		// 返回记录修改量
		return saveAll(sqlToyContext, entityMeta, entityMeta.getIdStrategy(), isAssignPK, insertSql, entities,
				reflectPropsHandler, conn, dbType, autoCommit);
	}

	/**
	 * @todo 保存批量对象数据
	 * @param sqlToyContext
	 * @param entityMeta
	 * @param pkStrategy
	 * @param isAssignPK
	 * @param insertSql
	 * @param entities
	 * @param reflectPropsHandler
	 * @param conn
	 * @param dbType
	 * @param autoCommit
	 * @return
	 * @throws Exception
	 */
	private static Long saveAll(SqlToyContext sqlToyContext, EntityMeta entityMeta, PKStrategy pkStrategy,
			boolean isAssignPK, String insertSql, List<?> entities, ReflectPropsHandler reflectPropsHandler,
			Connection conn, final Integer dbType, final Boolean autoCommit) throws Exception {
		boolean isIdentity = pkStrategy != null && pkStrategy.equals(PKStrategy.IDENTITY);
		boolean isSequence = pkStrategy != null && pkStrategy.equals(PKStrategy.SEQUENCE);
		String[] reflectColumns;
		if ((isIdentity && !isAssignPK) || (isSequence && !isAssignPK)) {
			reflectColumns = entityMeta.getRejectIdFieldArray();
		} else {
			reflectColumns = entityMeta.getFieldsArray();
		}
		ReflectPropsHandler handler = DialectUtils.getAddReflectHandler(entityMeta, reflectPropsHandler,
				sqlToyContext.getUnifyFieldsHandler());
		handler = DialectUtils.getSecureReflectHandler(handler, sqlToyContext.getFieldsSecureProvider(),
				sqlToyContext.getDesensitizeProvider(), entityMeta.getSecureFields());
		List<Object[]> paramValues = BeanUtil.reflectBeansToInnerAry(entities, reflectColumns,
				SqlUtilsExt.getDefaultValues(entityMeta), handler);
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

		List<Object[]> realParams = paramValues;
		String realSql = insertSql;
		if (sqlToyContext.hasSqlInterceptors()) {
			SqlToyConfig sqlToyConfig = new SqlToyConfig(Dialect.SQLSERVER);
			sqlToyConfig.setSqlType(SqlType.insert);
			sqlToyConfig.setSql(insertSql);
			sqlToyConfig.setParamsName(reflectColumns);
			SqlToyResult sqlToyResult = new SqlToyResult(insertSql, paramValues.toArray());
			sqlToyResult = DialectUtils.doInterceptors(sqlToyContext, sqlToyConfig, OperateType.insertAll, sqlToyResult,
					entities.get(0).getClass(), dbType);
			realSql = sqlToyResult.getSql();
			realParams = CollectionUtil.arrayToList(sqlToyResult.getParamsValue());
		}
		SqlExecuteStat.showSql("mssql批量保存", realSql, null);
		return SqlUtilsExt.batchUpdateForPOJO(sqlToyContext.getTypeHandler(), realSql, realParams,
				entityMeta.getFieldsTypeArray(), entityMeta.getFieldsDefaultValue(), entityMeta.getFieldsNullable(),
				sqlToyContext.getBatchSize(), autoCommit, conn, dbType);
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
		String realTable = entityMeta.getSchemaTable(tableName, dbType);
		Long updateCount = DialectUtils.update(sqlToyContext, entity, entityMeta, "isnull", forceUpdateFields, conn,
				dbType, realTable);
		// 级联修改
		if (cascade && !entityMeta.getCascadeModels().isEmpty()) {
			HashMap<Type, String> typeMap = new HashMap<Type, String>();
			if (emptyCascadeClasses != null) {
				for (Type type : emptyCascadeClasses) {
					typeMap.put(type, "");
				}
			}
			// 级联子表数据
			List subTableData = null;
			String[] forceUpdateProps = null;
			EntityMeta subTableEntityMeta;
			for (TableCascadeModel cascadeModel : entityMeta.getCascadeModels()) {
				final Object[] mainFieldValues = BeanUtil.reflectBeanToAry(entity, cascadeModel.getFields());
				subTableEntityMeta = sqlToyContext.getEntityMeta(cascadeModel.getMappedType());
				forceUpdateProps = (subTableForceUpdateProps == null) ? null
						: subTableForceUpdateProps.get(cascadeModel.getMappedType());
				if (cascadeModel.getCascadeType() == 1) {
					subTableData = (List) BeanUtil.getProperty(entity, cascadeModel.getProperty());
				} else {
					subTableData = new ArrayList();
					Object item = BeanUtil.getProperty(entity, cascadeModel.getProperty());
					if (item != null) {
						subTableData.add(item);
					}
				}
				final String[] mappedFields = cascadeModel.getMappedFields();
				// 针对存量子表数据,调用级联修改的语句，分delete 和update两种操作 1、删除存量数据;2、设置存量数据状态为停用
				if (cascadeModel.getCascadeUpdateSql() != null && ((subTableData != null && !subTableData.isEmpty())
						|| typeMap.containsKey(cascadeModel.getMappedType()))) {
					SqlExecuteStat.debug("执行子表级联更新前的存量数据更新", null);
					SqlToyResult sqlToyResult = SqlConfigParseUtils.processSql(cascadeModel.getCascadeUpdateSql(),
							mappedFields, mainFieldValues, null);
					SqlToyConfig sqlToyConfig = new SqlToyConfig(Dialect.SQLSERVER);
					sqlToyConfig.setSqlType(SqlType.update);
					sqlToyConfig.setSql(cascadeModel.getCascadeUpdateSql());
					sqlToyConfig.setParamsName(mappedFields);
					// 增加sql执行拦截器 update 2022-9-10
					sqlToyResult = DialectUtils.doInterceptors(sqlToyContext, sqlToyConfig, OperateType.execute,
							sqlToyResult, cascadeModel.getMappedType(), dbType);
					SqlUtil.executeSql(sqlToyContext.getTypeHandler(), sqlToyResult.getSql(),
							sqlToyResult.getParamsValue(), null, conn, dbType, null, true);
				}
				// 子表数据不为空,采取saveOrUpdateAll操作
				if (subTableData != null && !subTableData.isEmpty()) {
					logger.info("执行update主表:{} 对应级联子表: {} 更新操作!", tableName, subTableEntityMeta.getTableName());
					SqlExecuteStat.debug("执行子表级联更新操作", null);
					saveOrUpdateAll(sqlToyContext, subTableData, sqlToyContext.getBatchSize(),
							// 设置关联外键字段的属性值(来自主表的主键)
							new ReflectPropsHandler() {
								@Override
								public void process() {
									for (int i = 0; i < mappedFields.length; i++) {
										this.setValue(mappedFields[i], mainFieldValues[i]);
									}
								}
							}, forceUpdateProps, conn, dbType, null, null);
				} else {
					logger.info("未执行update主表:{} 对应级联子表: {} 更新操作,子表数据为空!", tableName, subTableEntityMeta.getTableName());
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
		// 锁为null直接返回
		if (lockMode == null || SqlUtil.hasLock(loadSql, DBType.SQLSERVER)) {
			return loadSql;
		}
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
				if ("as".equals(sqlChips[i + 1].toLowerCase())) {
					regex = realTableName.concat("\\s+as\\s+").concat(sqlChips[i + 2]);
					replaceStr = realTableName.concat(" as ").concat(sqlChips[i + 2]);
					break;
				} else if ("where".equals(sqlChips[i + 2].toLowerCase())) {
					regex = realTableName.concat("\\s+").concat(sqlChips[i + 1]);
					replaceStr = realTableName.concat(" ").concat(sqlChips[i + 1]);
					break;
				} else if (",".equals(sqlChips[i + 2])) {
					regex = realTableName.concat("\\s+").concat(sqlChips[i + 1]).concat(",");
					replaceStr = realTableName.concat(" ").concat(sqlChips[i + 1]);
					break;
				} else if (i + 3 < chipSize && "join".equals(sqlChips[i + 3].toLowerCase())) {
					regex = realTableName.concat("\\s+").concat(sqlChips[i + 1]);
					replaceStr = realTableName.concat(" ").concat(sqlChips[i + 1]);
					break;
				}
			}
		}
		switch (lockMode) {
		case UPGRADE:
			loadSql = selectPart.concat(fromPart.replaceFirst("(?i)".concat(regex), replaceStr.replace(",", "")
					.concat(" with (rowlock xlock) ").concat((regex.endsWith(",") ? "," : ""))));
			break;
		case UPGRADE_NOWAIT:
		case UPGRADE_SKIPLOCK:
			loadSql = selectPart.concat(fromPart.replaceFirst("(?i)".concat(regex), replaceStr.replace(",", "")
					.concat(" with (rowlock readpast) ").concat((regex.endsWith(",") ? "," : ""))));
			break;
		}
		return loadSql;
	}

	@SuppressWarnings("unchecked")
	public static List<TableMeta> getTables(String catalog, String schema, String tableName, Connection conn,
			Integer dbType, String dialect) throws Exception {
		String sql = "select d.name TABLE_NAME, cast(isnull(f.value,'') as varchar(1000)) COMMENTS,d.xtype TABLE_TYPE"
				+ " from syscolumns a "
				+ "		 inner join sysobjects d on a.id=d.id and d.xtype in ('U','V') and d.name<>'dtproperties' "
				+ "		 left join sys.extended_properties f on d.id=f.major_id and f.minor_id=0 "
				+ "		 where a.colorder=1 ";
		if (StringUtil.isNotBlank(tableName)) {
			sql = sql.concat(" and d.name like ?");
		}
		PreparedStatement pst = conn.prepareStatement(sql);
		ResultSet rs = null;
		// 通过preparedStatementProcess反调，第二个参数是pst
		return (List<TableMeta>) SqlUtil.preparedStatementProcess(null, pst, rs, new PreparedStatementResultHandler() {
			@Override
			public void execute(Object rowData, PreparedStatement pst, ResultSet rs) throws Exception {
				if (StringUtil.isNotBlank(tableName)) {
					if (tableName.contains("%")) {
						pst.setString(1, tableName);
					} else {
						pst.setString(1, "%" + tableName + "%");
					}
				}
				rs = pst.executeQuery();
				List<TableMeta> tables = new ArrayList<TableMeta>();
				while (rs.next()) {
					TableMeta tableMeta = new TableMeta();
					tableMeta.setTableName(rs.getString("TABLE_NAME"));
					tableMeta.setType(rs.getString("TABLE_TYPE"));
					if ("V".equals(tableMeta.getType())) {
						tableMeta.setType("VIEW");
					} else {
						tableMeta.setType("TABLE");
					}
					tableMeta.setRemarks(rs.getString("COMMENTS"));
					tables.add(tableMeta);
				}
				this.setResult(tables);
			}
		});
	}

	@SuppressWarnings("unchecked")
	public static List<ColumnMeta> getTableColumns(String catalog, String schema, String tableName, Connection conn,
			Integer dbType, String dialect) throws Exception {
		List<ColumnMeta> tableColumns = DefaultDialectUtils.getTableColumns(catalog, schema, tableName, conn, dbType,
				dialect);
		String sql = "SELECT a.name COLUMN_NAME,"
				+ "				 cast(isnull(g.[value],'') as varchar(1000)) as COMMENTS "
				+ "				 FROM syscolumns a  inner join sysobjects d on a.id=d.id "
				+ "				 and d.xtype='U' and d.name<>'dtproperties' "
				+ "				 left join syscomments e on a.cdefault=e.id"
				+ "				 left join sys.extended_properties g "
				+ "				 on a.id=g.major_id AND a.colid = g.minor_id   where d.name=? "
				+ "   order by a.id,a.colorder";
		PreparedStatement pst = conn.prepareStatement(sql);
		ResultSet rs = null;
		// 通过preparedStatementProcess反调，第二个参数是pst
		Map<String, String> colMap = (Map<String, String>) SqlUtil.preparedStatementProcess(null, pst, rs,
				new PreparedStatementResultHandler() {
					@Override
					public void execute(Object rowData, PreparedStatement pst, ResultSet rs) throws Exception {
						pst.setString(1, tableName);
						rs = pst.executeQuery();
						Map<String, String> colComments = new HashMap<String, String>();
						String comment;
						while (rs.next()) {
							comment = rs.getString("COMMENTS");
							if (comment != null) {
								colComments.put(rs.getString("COLUMN_NAME").toUpperCase(), comment);
							}
						}
						this.setResult(colComments);
					}
				});
		for (ColumnMeta col : tableColumns) {
			col.setComments(colMap.get(col.getColName().toUpperCase()));
		}
		return tableColumns;
	}

	// sqlserver identity主键是不允许写insert table (id,xxx) values (?,?)不能显式体现identity列
	private static boolean isAssignPKValue(PKStrategy pkStrategy) {
		if (pkStrategy == null) {
			return true;
		}
		if (pkStrategy.equals(PKStrategy.SEQUENCE)) {
			return true;
		}
		if (pkStrategy.equals(PKStrategy.IDENTITY)) {
			return false;
		}
		return true;
	}
}
