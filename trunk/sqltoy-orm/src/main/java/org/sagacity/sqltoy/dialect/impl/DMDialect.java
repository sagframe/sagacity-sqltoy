package org.sagacity.sqltoy.dialect.impl;

import java.io.Serializable;
import java.sql.Connection;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;

import org.sagacity.sqltoy.SqlToyContext;
import org.sagacity.sqltoy.callback.DecryptHandler;
import org.sagacity.sqltoy.callback.GenerateSavePKStrategy;
import org.sagacity.sqltoy.callback.GenerateSqlHandler;
import org.sagacity.sqltoy.callback.ReflectPropsHandler;
import org.sagacity.sqltoy.callback.UpdateRowHandler;
import org.sagacity.sqltoy.config.model.EntityMeta;
import org.sagacity.sqltoy.config.model.PKStrategy;
import org.sagacity.sqltoy.config.model.SqlToyConfig;
import org.sagacity.sqltoy.dialect.Dialect;
import org.sagacity.sqltoy.dialect.model.SavePKStrategy;
import org.sagacity.sqltoy.dialect.utils.DMDialectUtils;
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
import org.sagacity.sqltoy.utils.SqlUtil;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * @project sqltoy-orm
 * @description 国产达梦数据库方言支持(从DM8开始支持),问题: 1、dm数据库跟oracle差异:在merge
 *              into里面使用nvl报错;目前采取了先更新后saveIgnore的操作模式 2、updateFetch操作报游标错误
 * @author zhongxuchen
 * @version v1.0,Date:2020-06-05
 * @modify Date:2020-06-05 初始创建达梦数据库支持
 */
@SuppressWarnings({ "rawtypes" })
public class DMDialect implements Dialect {
	/**
	 * 定义日志
	 */
	protected final Logger logger = LoggerFactory.getLogger(DMDialect.class);

	/**
	 * 判定为null的函数
	 */
	public static final String NVL_FUNCTION = "nvl";

	@Override
	public boolean isUnique(SqlToyContext sqlToyContext, Serializable entity, String[] paramsNamed, Connection conn,
			final Integer dbType, String tableName) {
		return DialectUtils.isUnique(sqlToyContext, entity, paramsNamed, conn, dbType, tableName,
				(entityMeta, realParamNamed, table, topSize) -> {
					String queryStr = DialectExtUtils.wrapUniqueSql(entityMeta, realParamNamed, dbType, table);
					return queryStr + " limit " + topSize;
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
		return OracleDialectUtils.findPageBySql(sqlToyContext, sqlToyConfig, queryExecutor, decryptHandler, pageNo,
				pageSize, conn, dbType, dialect, fetchSize, maxRows);
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
		return OracleDialectUtils.findTopBySql(sqlToyContext, sqlToyConfig, queryExecutor, decryptHandler, topSize,
				conn, dbType, dialect, fetchSize, maxRows);
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
		String realSql = sql.concat(getLockSql(sql, dbType, lockMode));
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
	public Long saveOrUpdateAll(SqlToyContext sqlToyContext, List<?> entities, final int batchSize,
			ReflectPropsHandler reflectPropsHandler, final String[] forceUpdateFields, Connection conn,
			final Integer dbType, final String dialect, final Boolean autoCommit, final String tableName)
			throws Exception {
		EntityMeta entityMeta = sqlToyContext.getEntityMeta(entities.get(0).getClass());
		return DialectUtils.saveOrUpdateAll(sqlToyContext, entities, batchSize, entityMeta, forceUpdateFields,
				new GenerateSqlHandler() {
					@Override
					public String generateSql(EntityMeta entityMeta, String[] forceUpdateFields) {
						PKStrategy pkStrategy = entityMeta.getIdStrategy();
						String sequence = entityMeta.getSequence() + ".nextval";
						boolean isAssignPK = DMDialectUtils.isAssignPKValue(pkStrategy);
						return DialectUtils.getSaveOrUpdateSql(sqlToyContext, sqlToyContext.getUnifyFieldsHandler(),
								dbType, entityMeta, pkStrategy, forceUpdateFields, "dual", NVL_FUNCTION, sequence,
								isAssignPK, tableName);
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
			Boolean autoCommit, final String tableName) throws Exception {
		EntityMeta entityMeta = sqlToyContext.getEntityMeta(entities.get(0).getClass());
		return DialectUtils.saveAllIgnoreExist(sqlToyContext, entities, batchSize, entityMeta,
				new GenerateSqlHandler() {
					@Override
					public String generateSql(EntityMeta entityMeta, String[] forceUpdateFields) {
						PKStrategy pkStrategy = entityMeta.getIdStrategy();
						String sequence = entityMeta.getSequence() + ".nextval";
						boolean isAssignPK = DMDialectUtils.isAssignPKValue(pkStrategy);
						return DialectExtUtils.mergeIgnore(sqlToyContext.getUnifyFieldsHandler(), dbType, entityMeta,
								pkStrategy, "dual", NVL_FUNCTION, sequence, isAssignPK, tableName);
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
		// save行为根据主键是否赋值情况调整最终的主键策略
		PKStrategy pkStrategy = DialectUtils.getSavePKStrategy(entityMeta, entity, dbType);
		boolean isAssignPK = DMDialectUtils.isAssignPKValue(pkStrategy);
		String sequence = entityMeta.getSequence().concat(".nextval");
		String insertSql = DialectExtUtils.generateInsertSql(sqlToyContext.getUnifyFieldsHandler(), dbType, entityMeta,
				pkStrategy, NVL_FUNCTION, sequence, isAssignPK, tableName);
		return DialectUtils.save(sqlToyContext, entityMeta, pkStrategy, isAssignPK, insertSql, entity,
				new GenerateSqlHandler() {
					@Override
					public String generateSql(EntityMeta entityMeta, String[] forceUpdateField) {
						PKStrategy pkStrategy = entityMeta.getIdStrategy();
						String sequence = entityMeta.getSequence().concat(".nextval");
						return DialectExtUtils.generateInsertSql(sqlToyContext.getUnifyFieldsHandler(), dbType,
								entityMeta, pkStrategy, NVL_FUNCTION, sequence,
								DMDialectUtils.isAssignPKValue(pkStrategy), null);
					}
				}, new GenerateSavePKStrategy() {
					@Override
					public SavePKStrategy generate(EntityMeta entityMeta) {
						return new SavePKStrategy(entityMeta.getIdStrategy(),
								DMDialectUtils.isAssignPKValue(entityMeta.getIdStrategy()));
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
		String sequence = entityMeta.getSequence().concat(".nextval");
		boolean isAssignPK = DMDialectUtils.isAssignPKValue(pkStrategy);
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
			final boolean cascade, final Class[] forceCascadeClass,
			final HashMap<Class, String[]> subTableForceUpdateProps, Connection conn, final Integer dbType,
			final String dialect, final String tableName) throws Exception {
		return DialectUtils.update(sqlToyContext, entity, NVL_FUNCTION, forceUpdateFields, cascade,
				(cascade == false) ? null : new GenerateSqlHandler() {
					@Override
					public String generateSql(EntityMeta entityMeta, String[] forceUpdateFields) {
						PKStrategy pkStrategy = entityMeta.getIdStrategy();
						String sequence = entityMeta.getSequence().concat(".nextval");
						boolean isAssignPK = DMDialectUtils.isAssignPKValue(pkStrategy);
						return DialectUtils.getSaveOrUpdateSql(sqlToyContext, sqlToyContext.getUnifyFieldsHandler(),
								dbType, entityMeta, pkStrategy, forceUpdateFields, "dual", NVL_FUNCTION, sequence,
								isAssignPK, null);
					}
				}, forceCascadeClass, subTableForceUpdateProps, conn, dbType, tableName);
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

	// dm 数据库会报游标错误
	@Override
	public QueryResult updateFetch(SqlToyContext sqlToyContext, SqlToyConfig sqlToyConfig, String sql,
			Object[] paramsValue, UpdateRowHandler updateRowHandler, Connection conn, final Integer dbType,
			final String dialect, final LockMode lockMode, final int fetchSize, final int maxRows) throws Exception {
		String realSql = sql.concat(getLockSql(sql, dbType, (lockMode == null) ? LockMode.UPGRADE : lockMode));
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
		return DefaultDialectUtils.getTableColumns(catalog, schema, tableName, conn, dbType, dialect);
	}

	@Override
	public List<TableMeta> getTables(String catalog, String schema, String tableName, Connection conn, Integer dbType,
			String dialect) throws Exception {
		return DefaultDialectUtils.getTables(catalog, schema, tableName, conn, dbType, dialect);
	}

	private String getLockSql(String sql, Integer dbType, LockMode lockMode) {
		// 判断是否已经包含for update
		if (lockMode == null || SqlUtil.hasLock(sql, dbType)) {
			return "";
		}
		if (lockMode == LockMode.UPGRADE_NOWAIT) {
			return " for update nowait ";
		}
		if (lockMode == LockMode.UPGRADE_SKIPLOCK) {
			return " for update skip locked";
		}
		return " for update ";
	}
}
