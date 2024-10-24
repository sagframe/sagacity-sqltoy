/**
 * 
 */
package org.sagacity.sqltoy.dialect.impl;

import java.io.Serializable;
import java.sql.Connection;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import org.sagacity.sqltoy.SqlToyConstants;
import org.sagacity.sqltoy.SqlToyContext;
import org.sagacity.sqltoy.callback.DecryptHandler;
import org.sagacity.sqltoy.callback.GenerateSavePKStrategy;
import org.sagacity.sqltoy.callback.GenerateSqlHandler;
import org.sagacity.sqltoy.callback.ReflectPropsHandler;
import org.sagacity.sqltoy.callback.UpdateRowHandler;
import org.sagacity.sqltoy.config.model.EntityMeta;
import org.sagacity.sqltoy.config.model.OperateType;
import org.sagacity.sqltoy.config.model.PKStrategy;
import org.sagacity.sqltoy.config.model.SqlToyConfig;
import org.sagacity.sqltoy.config.model.SqlToyResult;
import org.sagacity.sqltoy.dialect.Dialect;
import org.sagacity.sqltoy.dialect.model.SavePKStrategy;
import org.sagacity.sqltoy.dialect.utils.DefaultDialectUtils;
import org.sagacity.sqltoy.dialect.utils.DialectExtUtils;
import org.sagacity.sqltoy.dialect.utils.DialectUtils;
import org.sagacity.sqltoy.dialect.utils.OracleDialectUtils;
import org.sagacity.sqltoy.model.ColumnMeta;
import org.sagacity.sqltoy.model.LockMode;
import org.sagacity.sqltoy.model.QueryExecutor;
import org.sagacity.sqltoy.model.QueryResult;
import org.sagacity.sqltoy.model.StoreResult;
import org.sagacity.sqltoy.model.TableMeta;
import org.sagacity.sqltoy.model.inner.QueryExecutorExtend;
import org.sagacity.sqltoy.utils.BeanUtil;
import org.sagacity.sqltoy.utils.SqlUtil;
import org.sagacity.sqltoy.utils.SqlUtilsExt;
import org.sagacity.sqltoy.utils.StringUtil;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * @project sqltoy-orm
 * @description oracle11g以及以下版本数据库的各类分页、取随机数、saveOrUpdate,lock机制实现
 * @author zhongxuchen
 * @version v1.0,Date:2013-3-21
 */
@SuppressWarnings({ "rawtypes" })
public class Oracle11gDialect implements Dialect {
	/**
	 * 定义日志
	 */
	protected final Logger logger = LoggerFactory.getLogger(Oracle11gDialect.class);

	/**
	 * 判定为null的函数
	 */
	public static final String NVL_FUNCTION = "nvl";

	public static final String NEXTVAL = ".nextval";

	public static final String VIRTUAL_TABLE = "dual";

	@Override
	public boolean isUnique(SqlToyContext sqlToyContext, Serializable entity, String[] paramsNamed, Connection conn,
			final Integer dbType, String tableName) {
		return DialectUtils.isUnique(sqlToyContext, entity, paramsNamed, conn, dbType, tableName,
				(entityMeta, realParamNamed, table, topSize) -> {
					StringBuilder sql = new StringBuilder();
					sql.append("SELECT sag_uniqueTop.* FROM ( ");
					sql.append(DialectExtUtils.wrapUniqueSql(entityMeta, realParamNamed, dbType, table));
					sql.append(") sag_uniqueTop where ROWNUM <=");
					sql.append(topSize);
					return sql.toString();
				});
	}

	/*
	 * (non-Javadoc)
	 * 
	 * @see org.sagacity.sqltoy.dialect.Dialect#getRandomResult(org. sagacity
	 * .sqltoy.SqlToyContext, org.sagacity.sqltoy.config.model.SqlToyConfig,
	 * org.sagacity.sqltoy.model.QueryExecutor, java.lang.Long, java.lang.Long,
	 * java.sql.Connection)
	 */
	@Override
	public QueryResult getRandomResult(SqlToyContext sqlToyContext, SqlToyConfig sqlToyConfig,
			QueryExecutor queryExecutor, final DecryptHandler decryptHandler, Long totalCount, Long randomCount,
			Connection conn, final Integer dbType, final String dialect, final int fetchSize, final int maxRows)
			throws Exception {
		return OracleDialectUtils.getRandomResult(sqlToyContext, sqlToyConfig, queryExecutor, decryptHandler,
				totalCount, randomCount, conn, dbType, dialect, fetchSize, maxRows);
	}

	/*
	 * (non-Javadoc)
	 * 
	 * @see org.sagacity.sqltoy.dialect.Dialect#findPageBySql(org.sagacity
	 * .sqltoy.SqlToyContext, org.sagacity.sqltoy.config.model.SqlToyConfig,
	 * org.sagacity.sqltoy.model.QueryExecutor,
	 * org.sagacity.sqltoy.callback.RowCallbackHandler, java.lang.Long,
	 * java.lang.Integer, java.sql.Connection)
	 */
	@Override
	public QueryResult findPageBySql(SqlToyContext sqlToyContext, SqlToyConfig sqlToyConfig,
			QueryExecutor queryExecutor, final DecryptHandler decryptHandler, Long pageNo, Integer pageSize,
			Connection conn, final Integer dbType, final String dialect, final int fetchSize, final int maxRows)
			throws Exception {
		StringBuilder sql = new StringBuilder();
		boolean isNamed = sqlToyConfig.isNamedParam();
		String innerSql = sqlToyConfig.isHasFast() ? sqlToyConfig.getFastSql(dialect) : sqlToyConfig.getSql(dialect);
		boolean hasOrderBy = SqlUtil.hasOrderBy(innerSql, true);
		// 给原始sql标记上特殊的开始和结尾，便于sql拦截器快速定位到原始sql并进行条件补充
		innerSql = SqlUtilsExt.markOriginalSql(innerSql);
		int startIndex = 1;
		if (sqlToyConfig.isHasFast()) {
			sql.append(sqlToyConfig.getFastPreSql(dialect));
			if (!sqlToyConfig.isIgnoreBracket()) {
				sql.append(" (");
			}
			startIndex = 0;
		}
		sql.append("SELECT * FROM (SELECT ROWNUM page_row_id," + SqlToyConstants.INTERMEDIATE_TABLE + ".* FROM ( ");
		sql.append(innerSql);
		sql.append(") ");
		sql.append(SqlToyConstants.INTERMEDIATE_TABLE);
		sql.append(" ");

		// 判断sql中是否存在排序，因为oracle排序查询的机制通过ROWNUM<=?每次查出的结果可能不一样 ， 请参见ROWNUM机制以及oracle
		// SORT ORDER BY STOPKEY
		if (SqlToyConstants.oraclePageIgnoreOrder() || !hasOrderBy) {
			sql.append(" where ROWNUM <=");
			sql.append(isNamed ? ":" + SqlToyConstants.PAGE_FIRST_PARAM_NAME : "?");
			sql.append(" ) WHERE page_row_id>");
			sql.append(isNamed ? ":" + SqlToyConstants.PAGE_LAST_PARAM_NAME : "?");
		} else {
			sql.append(" ) WHERE page_row_id<=");
			sql.append(isNamed ? ":" + SqlToyConstants.PAGE_FIRST_PARAM_NAME : "?");
			sql.append(" and page_row_id >");
			sql.append(isNamed ? ":" + SqlToyConstants.PAGE_LAST_PARAM_NAME : "?");
		}

		if (sqlToyConfig.isHasFast()) {
			if (!sqlToyConfig.isIgnoreBracket()) {
				sql.append(") ");
			}
			sql.append(sqlToyConfig.getFastTailSql(dialect));
		}

		SqlToyResult queryParam = DialectUtils.wrapPageSqlParams(sqlToyContext, sqlToyConfig, queryExecutor,
				sql.toString(), pageNo * pageSize, (pageNo - 1) * pageSize, dialect);
		QueryExecutorExtend extend = queryExecutor.getInnerModel();
		// 增加sql执行拦截器 update 2022-9-10
		queryParam = DialectUtils.doInterceptors(sqlToyContext, sqlToyConfig,
				(extend.entityClass == null) ? OperateType.page : OperateType.singleTable, queryParam,
				extend.entityClass, dbType);
		return DialectUtils.findBySql(sqlToyContext, sqlToyConfig, queryParam.getSql(), queryParam.getParamsValue(),
				extend, decryptHandler, conn, dbType, startIndex, fetchSize, maxRows);
	}

	/*
	 * (non-Javadoc)
	 * 
	 * @see org.sagacity.sqltoy.dialect.Dialect#findTopBySql(org.sagacity.sqltoy.
	 * SqlToyContext, org.sagacity.sqltoy.config.model.SqlToyConfig,
	 * org.sagacity.sqltoy.model.QueryExecutor, double, java.sql.Connection)
	 */
	@Override
	public QueryResult findTopBySql(SqlToyContext sqlToyContext, SqlToyConfig sqlToyConfig, QueryExecutor queryExecutor,
			final DecryptHandler decryptHandler, Integer topSize, Connection conn, final Integer dbType,
			final String dialect, final int fetchSize, final int maxRows) throws Exception {
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
		sql.append("SELECT " + SqlToyConstants.INTERMEDIATE_TABLE + ".* FROM ( ");
		sql.append(innerSql);
		sql.append(") " + SqlToyConstants.INTERMEDIATE_TABLE + " where ROWNUM <=");
		sql.append(Double.valueOf(topSize).intValue());

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

	/*
	 * (non-Javadoc)
	 * 
	 * @see org.sagacity.sqltoy.dialect.Dialect#findBySql(org.sagacity.
	 * sqltoy.config.model.SqlToyConfig, java.lang.String[], java.lang.Object[],
	 * java.lang.reflect.Type, org.sagacity.sqltoy.callback.RowCallbackHandler,
	 * java.sql.Connection)
	 */
	@Override
	public QueryResult findBySql(final SqlToyContext sqlToyContext, final SqlToyConfig sqlToyConfig, final String sql,
			final Object[] paramsValue, final QueryExecutorExtend queryExecutorExtend,
			final DecryptHandler decryptHandler, final Connection conn, final LockMode lockMode, final Integer dbType,
			final String dialect, final int fetchSize, final int maxRows) throws Exception {
		String realSql = sql.concat(OracleDialectUtils.getLockSql(sql, dbType, lockMode));
		return DialectUtils.findBySql(sqlToyContext, sqlToyConfig, realSql, paramsValue, queryExecutorExtend,
				decryptHandler, conn, dbType, 0, fetchSize, maxRows);
	}

	/*
	 * (non-Javadoc)
	 * 
	 * @see org.sagacity.sqltoy.dialect.Dialect#getCountBySql(java.lang .String,
	 * java.lang.String[], java.lang.Object[], java.sql.Connection)
	 */
	@Override
	public Long getCountBySql(final SqlToyContext sqlToyContext, final SqlToyConfig sqlToyConfig, final String sql,
			final Object[] paramsValue, final boolean isLastSql, final Connection conn, final Integer dbType,
			final String dialect) throws Exception {
		return DialectUtils.getCountBySql(sqlToyContext, sqlToyConfig, sql, paramsValue, isLastSql, conn, dbType);
	}

	/*
	 * (non-Javadoc)
	 * 
	 * @see org.sagacity.sqltoy.dialect.Dialect#saveOrUpdate(org.sagacity.sqltoy.
	 * SqlToyContext, java.io.Serializable, java.sql.Connection)
	 */
	@Override
	public Long saveOrUpdate(SqlToyContext sqlToyContext, Serializable entity, final String[] forceUpdateFields,
			Connection conn, final Integer dbType, final String dialect, final Boolean autoCommit,
			final String tableName) throws Exception {
		List<Serializable> entities = new ArrayList<Serializable>();
		entities.add(entity);
		return saveOrUpdateAll(sqlToyContext, entities, sqlToyContext.getBatchSize(), null, forceUpdateFields, conn,
				dbType, dialect, autoCommit, tableName);
	}

	/*
	 * (non-Javadoc)
	 * 
	 * @see org.sagacity.sqltoy.dialect.Dialect#saveOrUpdateAll(org.sagacity.sqltoy
	 * .SqlToyContext, java.util.List, java.sql.Connection)
	 */
	@Override
	public Long saveOrUpdateAll(final SqlToyContext sqlToyContext, List<?> entities, final int batchSize,
			ReflectPropsHandler reflectPropsHandler, final String[] forceUpdateFields, Connection conn,
			final Integer dbType, final String dialect, final Boolean autoCommit, final String tableName)
			throws Exception {
		EntityMeta entityMeta = sqlToyContext.getEntityMeta(entities.get(0).getClass());
		return DialectUtils.saveOrUpdateAll(sqlToyContext, entities, batchSize, entityMeta, forceUpdateFields,
				new GenerateSqlHandler() {
					@Override
					public String generateSql(EntityMeta entityMeta, String[] forceUpdateFields) {
						PKStrategy pkStrategy = entityMeta.getIdStrategy();
						String sequence = entityMeta.getSequence() + NEXTVAL;
						if (pkStrategy != null && pkStrategy.equals(PKStrategy.IDENTITY)) {
							pkStrategy = PKStrategy.SEQUENCE;
							sequence = entityMeta.getFieldMeta(entityMeta.getIdArray()[0]).getDefaultValue();
						}
						return DialectUtils.getSaveOrUpdateSql(sqlToyContext, sqlToyContext.getUnifyFieldsHandler(),
								dbType, entityMeta, pkStrategy, forceUpdateFields, VIRTUAL_TABLE, NVL_FUNCTION,
								sequence, isAssignPKValue(pkStrategy), tableName);
					}
				}, reflectPropsHandler, conn, dbType, autoCommit);
	}

	/*
	 * (non-Javadoc)
	 * 
	 * @see org.sagacity.sqltoy.dialect.Dialect#saveAllNotExist(org.sagacity.sqltoy.
	 * SqlToyContext, java.util.List,
	 * org.sagacity.sqltoy.callback.ReflectPropsHandler, java.sql.Connection,
	 * java.lang.Boolean)
	 */
	@Override
	public Long saveAllIgnoreExist(SqlToyContext sqlToyContext, List<?> entities, final int batchSize,
			ReflectPropsHandler reflectPropsHandler, Connection conn, final Integer dbType, final String dialect,
			final Boolean autoCommit, final String tableName) throws Exception {
		EntityMeta entityMeta = sqlToyContext.getEntityMeta(entities.get(0).getClass());
		return DialectUtils.saveAllIgnoreExist(sqlToyContext, entities, batchSize, entityMeta,
				new GenerateSqlHandler() {
					@Override
					public String generateSql(EntityMeta entityMeta, String[] forceUpdateFields) {
						PKStrategy pkStrategy = entityMeta.getIdStrategy();
						String sequence = entityMeta.getSequence() + NEXTVAL;
						if (pkStrategy != null && pkStrategy.equals(PKStrategy.IDENTITY)) {
							pkStrategy = PKStrategy.SEQUENCE;
							sequence = entityMeta.getFieldMeta(entityMeta.getIdArray()[0]).getDefaultValue();
						}
						return DialectExtUtils.mergeIgnore(sqlToyContext.getUnifyFieldsHandler(), dbType, entityMeta,
								pkStrategy, VIRTUAL_TABLE, NVL_FUNCTION, sequence, isAssignPKValue(pkStrategy),
								tableName);
					}
				}, reflectPropsHandler, conn, dbType, autoCommit);
	}

	/*
	 * (non-Javadoc)
	 * 
	 * @see org.sagacity.sqltoy.dialect.Dialect#load(java.io.Serializable,
	 * java.util.List, java.sql.Connection)
	 */
	@Override
	public Serializable load(final SqlToyContext sqlToyContext, Serializable entity, boolean onlySubTables,
			List<Class> cascadeTypes, LockMode lockMode, Connection conn, final Integer dbType, final String dialect,
			final String tableName) throws Exception {
		return OracleDialectUtils.load(sqlToyContext, entity, onlySubTables, cascadeTypes, lockMode, conn, dbType,
				dialect, tableName);
	}

	/*
	 * (non-Javadoc)
	 * 
	 * @see org.sagacity.sqltoy.dialect.Dialect#loadAll(java.util.List,
	 * java.util.List, java.sql.Connection)
	 */
	@Override
	public List<?> loadAll(final SqlToyContext sqlToyContext, List<?> entities, boolean onlySubTables,
			List<Class> cascadeTypes, LockMode lockMode, Connection conn, final Integer dbType, final String dialect,
			final String tableName, final int fetchSize, final int maxRows) throws Exception {
		return OracleDialectUtils.loadAll(sqlToyContext, entities, onlySubTables, cascadeTypes, lockMode, conn, dbType,
				tableName, fetchSize, maxRows);
	}

	/*
	 * (non-Javadoc)
	 * 
	 * @see org.sagacity.sqltoy.dialect.Dialect#save(org.sagacity.sqltoy.
	 * SqlToyContext , java.io.Serializable, java.util.List, java.sql.Connection)
	 */
	@Override
	public Object save(SqlToyContext sqlToyContext, Serializable entity, Connection conn, final Integer dbType,
			final String dialect, final String tableName) throws Exception {
		EntityMeta entityMeta = sqlToyContext.getEntityMeta(entity.getClass());
		PKStrategy pkStrategy = entityMeta.getIdStrategy();
		String sequence = entityMeta.getSequence() + NEXTVAL;
		// oracle11 是不支持identity主键策略的
		if (pkStrategy != null && pkStrategy.equals(PKStrategy.IDENTITY)) {
			pkStrategy = PKStrategy.SEQUENCE;
			sequence = entityMeta.getFieldMeta(entityMeta.getIdArray()[0]).getDefaultValue();
		}
		// 主键值已经存在，则主键策略改为assign，避免跳号
		if (pkStrategy != null && pkStrategy.equals(PKStrategy.SEQUENCE)) {
			Object id = BeanUtil.getProperty(entity, entityMeta.getIdArray()[0]);
			if (StringUtil.isNotBlank(id)) {
				pkStrategy = PKStrategy.ASSIGN;
			}
		}
		boolean isAssignPK = isAssignPKValue(pkStrategy);
		String insertSql = DialectExtUtils.generateInsertSql(sqlToyContext.getUnifyFieldsHandler(), dbType, entityMeta,
				pkStrategy, NVL_FUNCTION, sequence, isAssignPK, tableName);
		return DialectUtils.save(sqlToyContext, entityMeta, pkStrategy, isAssignPK, insertSql, entity,
				new GenerateSqlHandler() {
					// 通过反调方式提供oracle insert语句
					@Override
					public String generateSql(EntityMeta entityMeta, String[] forceUpdateField) {
						PKStrategy pkStrategy = entityMeta.getIdStrategy();
						String sequence = entityMeta.getSequence() + NEXTVAL;
						// oracle sequence主键策略
						if (pkStrategy != null && pkStrategy.equals(PKStrategy.IDENTITY)) {
							pkStrategy = PKStrategy.SEQUENCE;
							sequence = entityMeta.getFieldMeta(entityMeta.getIdArray()[0]).getDefaultValue();
						}
						return DialectExtUtils.generateInsertSql(sqlToyContext.getUnifyFieldsHandler(), dbType,
								entityMeta, pkStrategy, NVL_FUNCTION, sequence, isAssignPKValue(pkStrategy), null);
					}
				}, new GenerateSavePKStrategy() {
					@Override
					public SavePKStrategy generate(EntityMeta entityMeta) {
						PKStrategy pkStrategy = entityMeta.getIdStrategy();
						if (pkStrategy != null && pkStrategy.equals(PKStrategy.IDENTITY)) {
							pkStrategy = PKStrategy.SEQUENCE;
						}
						return new SavePKStrategy(pkStrategy, isAssignPKValue(pkStrategy));
					}
				}, conn, dbType);
	}

	/*
	 * (non-Javadoc)
	 * 
	 * @see org.sagacity.sqltoy.dialect.Dialect#saveAll(org.sagacity.sqltoy.
	 * SqlToyContext , java.util.List,
	 * org.sagacity.sqltoy.callback.ReflectPropsHandler, java.sql.Connection)
	 */
	@Override
	public Long saveAll(SqlToyContext sqlToyContext, List<?> entities, final int batchSize,
			ReflectPropsHandler reflectPropsHandler, Connection conn, final Integer dbType, final String dialect,
			final Boolean autoCommit, final String tableName) throws Exception {
		// oracle12c 开始支持identity机制
		EntityMeta entityMeta = sqlToyContext.getEntityMeta(entities.get(0).getClass());
		PKStrategy pkStrategy = entityMeta.getIdStrategy();
		boolean isAssignPK = isAssignPKValue(pkStrategy);
		String sequence = entityMeta.getSequence() + NEXTVAL;
		if (pkStrategy != null && pkStrategy.equals(PKStrategy.IDENTITY)) {
			pkStrategy = PKStrategy.SEQUENCE;
			sequence = entityMeta.getFieldMeta(entityMeta.getIdArray()[0]).getDefaultValue();
		}
		String insertSql = DialectExtUtils.generateInsertSql(sqlToyContext.getUnifyFieldsHandler(), dbType, entityMeta,
				pkStrategy, NVL_FUNCTION, sequence, isAssignPK, tableName);
		return DialectUtils.saveAll(sqlToyContext, entityMeta, pkStrategy, isAssignPK, insertSql, entities, batchSize,
				reflectPropsHandler, conn, dbType, autoCommit);
	}

	/*
	 * (non-Javadoc)
	 * 
	 * @see org.sagacity.sqltoy.dialect.Dialect#update(org.sagacity.sqltoy.
	 * SqlToyContext , java.io.Serializable, java.lang.String[],
	 * java.sql.Connection)
	 */
	@Override
	public Long update(SqlToyContext sqlToyContext, Serializable entity, String[] forceUpdateFields,
			final boolean cascade, final Class[] forceCascadeClasses,
			final HashMap<Class, String[]> subTableForceUpdateProps, Connection conn, final Integer dbType,
			final String dialect, final String tableName) throws Exception {
		return DialectUtils.update(sqlToyContext, entity, NVL_FUNCTION, forceUpdateFields, cascade,
				(cascade == false) ? null : new GenerateSqlHandler() {
					@Override
					public String generateSql(EntityMeta entityMeta, String[] forceUpdateFields) {
						PKStrategy pkStrategy = entityMeta.getIdStrategy();
						String sequence = entityMeta.getSequence() + NEXTVAL;
						if (pkStrategy != null && pkStrategy.equals(PKStrategy.IDENTITY)) {
							pkStrategy = PKStrategy.SEQUENCE;
							sequence = entityMeta.getFieldMeta(entityMeta.getIdArray()[0]).getDefaultValue();
						}
						return DialectUtils.getSaveOrUpdateSql(sqlToyContext, sqlToyContext.getUnifyFieldsHandler(),
								dbType, entityMeta, pkStrategy, forceUpdateFields, VIRTUAL_TABLE, NVL_FUNCTION,
								sequence, isAssignPKValue(pkStrategy), null);
					}
				}, forceCascadeClasses, subTableForceUpdateProps, conn, dbType, tableName);
	}

	/*
	 * (non-Javadoc)
	 * 
	 * @see org.sagacity.sqltoy.dialect.Dialect#updateAll(org.sagacity.sqltoy.
	 * SqlToyContext, java.util.List,
	 * org.sagacity.sqltoy.callback.ReflectPropsHandler, java.sql.Connection)
	 */
	@Override
	public Long updateAll(SqlToyContext sqlToyContext, List<?> entities, final int batchSize,
			final String[] uniqueFields, final String[] forceUpdateFields, ReflectPropsHandler reflectPropsHandler,
			Connection conn, final Integer dbType, final String dialect, final Boolean autoCommit,
			final String tableName) throws Exception {
		return DialectUtils.updateAll(sqlToyContext, entities, batchSize, forceUpdateFields, reflectPropsHandler,
				NVL_FUNCTION, conn, dbType, autoCommit, tableName, false);
	}

	@Override
	public Serializable updateSaveFetch(SqlToyContext sqlToyContext, Serializable entity,
			UpdateRowHandler updateRowHandler, String[] uniqueProps, Connection conn, Integer dbType, String dialect,
			String tableName) throws Exception {
		return DefaultDialectUtils.updateSaveFetch(sqlToyContext, entity, updateRowHandler, uniqueProps, conn, dbType,
				dialect, tableName);
	}

	/*
	 * (non-Javadoc)
	 * 
	 * @see org.sagacity.sqltoy.dialect.Dialect#delete(org.sagacity.sqltoy.
	 * SqlToyContext , java.io.Serializable, java.sql.Connection)
	 */
	@Override
	public Long delete(SqlToyContext sqlToyContext, Serializable entity, Connection conn, final Integer dbType,
			final String dialect, final String tableName) throws Exception {
		return DialectUtils.delete(sqlToyContext, entity, conn, dbType, tableName);
	}

	/*
	 * (non-Javadoc)
	 * 
	 * @see org.sagacity.sqltoy.dialect.Dialect#deleteAll(org.sagacity.sqltoy.
	 * SqlToyContext, java.util.List, java.sql.Connection)
	 */
	@Override
	public Long deleteAll(SqlToyContext sqlToyContext, List<?> entities, final int batchSize, Connection conn,
			final Integer dbType, final String dialect, final Boolean autoCommit, final String tableName)
			throws Exception {
		return DialectUtils.deleteAll(sqlToyContext, entities, batchSize, conn, dbType, autoCommit, tableName);
	}

	/*
	 * (non-Javadoc)
	 * 
	 * @see org.sagacity.sqltoy.dialect.Dialect#updateFetch(org.sagacity.sqltoy.
	 * SqlToyContext, org.sagacity.sqltoy.config.model.SqlToyConfig,
	 * org.sagacity.sqltoy.model.QueryExecutor,
	 * org.sagacity.sqltoy.callback.UpdateRowHandler, java.sql.Connection)
	 */
	@Override
	public QueryResult updateFetch(SqlToyContext sqlToyContext, SqlToyConfig sqlToyConfig, String sql,
			Object[] paramsValue, UpdateRowHandler updateRowHandler, Connection conn, final Integer dbType,
			final String dialect, final LockMode lockMode, final int fetchSize, final int maxRows) throws Exception {
		String realSql = sql
				.concat(OracleDialectUtils.getLockSql(sql, dbType, (lockMode == null) ? LockMode.UPGRADE : lockMode));
		return DialectUtils.updateFetchBySql(sqlToyContext, sqlToyConfig, realSql, paramsValue, updateRowHandler, conn,
				dbType, 0, fetchSize, maxRows);
	}

	@Override
	public StoreResult executeStore(SqlToyContext sqlToyContext, final SqlToyConfig sqlToyConfig, final String sql,
			final Object[] inParamsValue, final Integer[] outParamsType, final boolean moreResult,
			final Connection conn, final Integer dbType, final String dialect, final int fetchSize) throws Exception {
		return OracleDialectUtils.executeStore(sqlToyConfig, sqlToyContext, sql, inParamsValue, outParamsType,
				moreResult, conn, dbType, fetchSize);
	}

	@Override
	public List<ColumnMeta> getTableColumns(String catalog, String schema, String tableName, Connection conn,
			Integer dbType, String dialect) throws Exception {
		List<ColumnMeta> tableColumns = OracleDialectUtils.getTableColumns(catalog, schema, tableName, conn, dbType,
				dialect);
		// 获取主键信息
		Map<String, ColumnMeta> pkMap = DefaultDialectUtils.getTablePrimaryKeys(catalog, schema, tableName, conn,
				dbType, dialect);
		if (pkMap == null || pkMap.isEmpty()) {
			return tableColumns;
		}
		ColumnMeta mapMeta;
		for (ColumnMeta colMeta : tableColumns) {
			mapMeta = pkMap.get(colMeta.getColName());
			if (mapMeta != null) {
				colMeta.setPK(true);
			}
		}
		return tableColumns;
	}

	@Override
	public List<TableMeta> getTables(String catalog, String schema, String tableName, Connection conn, Integer dbType,
			String dialect) throws Exception {
		return OracleDialectUtils.getTables(catalog, schema, tableName, conn, dbType, dialect);
	}

	private boolean isAssignPKValue(PKStrategy pkStrategy) {
		return true;
	}
}
