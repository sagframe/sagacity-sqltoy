/**
 * 
 */
package org.sagacity.sqltoy.dialect.impl;

import java.io.Serializable;
import java.sql.Connection;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.regex.Pattern;

import org.sagacity.sqltoy.SqlToyConstants;
import org.sagacity.sqltoy.SqlToyContext;
import org.sagacity.sqltoy.callback.DecryptHandler;
import org.sagacity.sqltoy.callback.GenerateSavePKStrategy;
import org.sagacity.sqltoy.callback.GenerateSqlHandler;
import org.sagacity.sqltoy.callback.ReflectPropsHandler;
import org.sagacity.sqltoy.callback.UpdateRowHandler;
import org.sagacity.sqltoy.config.model.EntityMeta;
import org.sagacity.sqltoy.config.model.PKStrategy;
import org.sagacity.sqltoy.config.model.SqlToyConfig;
import org.sagacity.sqltoy.config.model.SqlType;
import org.sagacity.sqltoy.dialect.Dialect;
import org.sagacity.sqltoy.dialect.model.SavePKStrategy;
import org.sagacity.sqltoy.dialect.utils.DB2DialectUtils;
import org.sagacity.sqltoy.dialect.utils.DefaultDialectUtils;
import org.sagacity.sqltoy.dialect.utils.DialectExtUtils;
import org.sagacity.sqltoy.dialect.utils.DialectUtils;
import org.sagacity.sqltoy.model.ColumnMeta;
import org.sagacity.sqltoy.model.LockMode;
import org.sagacity.sqltoy.model.QueryExecutor;
import org.sagacity.sqltoy.model.QueryResult;
import org.sagacity.sqltoy.model.StoreResult;
import org.sagacity.sqltoy.model.TableMeta;
import org.sagacity.sqltoy.model.inner.QueryExecutorExtend;
import org.sagacity.sqltoy.utils.SqlUtil;
import org.sagacity.sqltoy.utils.StringUtil;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * @project sqltoy-orm
 * @description db2 10.x版本支持limit offset分页机制
 * @author zhongxuchen
 * @version v1.0,Date:2015年2月28日
 */
@SuppressWarnings({ "rawtypes" })
public class DB2Dialect implements Dialect {
	/**
	 * 定义日志
	 */
	protected final Logger logger = LoggerFactory.getLogger(DB2Dialect.class);

	/**
	 * db2 脏读sql查询语句正则表达式
	 */
	public static final Pattern DB2_QUERY_UR_PATTERN = Pattern.compile("(?i)\\s+with\\s+ur\\s*$");

	public static final String DB2_QUERY_APPEND = " with ur";

	/**
	 * 判定为null的函数
	 */
	public static final String NVL_FUNCTION = "nvl";

	/**
	 * 虚表
	 */
	public static final String VIRTUAL_TABLE = "SYSIBM.SYSDUMMY1";

	private String appendWithUR(String sql) {
		if (SqlToyConstants.db2WithUR()) {
			return StringUtil.matches(sql, DB2_QUERY_UR_PATTERN) ? sql : sql + DB2_QUERY_APPEND;
		}
		return sql;
	}

	@Override
	public boolean isUnique(final SqlToyContext sqlToyContext, Serializable entity, String[] paramsNamed,
			Connection conn, final Integer dbType, final String tableName) {
		return DialectUtils.isUnique(sqlToyContext, entity, paramsNamed, conn, dbType, tableName,
				(entityMeta, realParamNamed, table, topSize) -> {
					String queryStr = DialectExtUtils.wrapUniqueSql(entityMeta, realParamNamed, dbType, table);
					return queryStr + " limit " + topSize;
				});
	}

	/*
	 * (non-Javadoc)
	 * 
	 * @see
	 * org.sagacity.sqltoy.dialect.Dialect#load(org.sagacity.sqltoy.SqlToyContext,
	 * java.io.Serializable, java.util.List, org.sagacity.sqltoy.model.LockMode,
	 * java.sql.Connection, java.lang.Integer, java.lang.String, java.lang.String)
	 */
	@Override
	public Serializable load(final SqlToyContext sqlToyContext, Serializable entity, boolean onlySubTables,
			List<Class> cascadeTypes, LockMode lockMode, Connection conn, final Integer dbType, final String dialect,
			final String tableName) throws Exception {
		EntityMeta entityMeta = sqlToyContext.getEntityMeta(entity.getClass());
		// 获取loadsql(loadsql 可以通过@loadSql进行改变，所以需要sqltoyContext重新获取)
		SqlToyConfig sqlToyConfig = sqlToyContext.getSqlToyConfig(entityMeta.getLoadSql(tableName), SqlType.search,
				dialect, null);
		String loadSql = sqlToyConfig.getSql(dialect);
		loadSql = loadSql.concat(getLockSql(loadSql, dbType, lockMode));
		return (Serializable) DialectUtils.load(sqlToyContext, sqlToyConfig, loadSql, entityMeta, entity, onlySubTables,
				cascadeTypes, conn, dbType);
	}

	/*
	 * (non-Javadoc)
	 * 
	 * @see org.sagacity.sqltoy.dialect.Dialect#loadAll(org.sagacity.sqltoy.
	 * SqlToyContext, java.util.List, java.util.List,
	 * org.sagacity.sqltoy.model.LockMode, java.sql.Connection, java.lang.Integer,
	 * java.lang.String, java.lang.String)
	 */
	@Override
	public List<?> loadAll(final SqlToyContext sqlToyContext, List<?> entities, boolean onlySubTables,
			List<Class> cascadeTypes, final LockMode lockMode, Connection conn, final Integer dbType,
			final String dialect, final String tableName, final int fetchSize, final int maxRows) throws Exception {
		return DialectUtils.loadAll(sqlToyContext, entities, onlySubTables, cascadeTypes, lockMode, conn, dbType,
				tableName, (sql, dbTypeValue, lockedMode) -> {
					return getLockSql(sql, dbTypeValue, lockedMode);
				}, fetchSize, maxRows);
	}

	/*
	 * (non-Javadoc)
	 * 
	 * @see
	 * org.sagacity.sqltoy.dialect.Dialect#save(org.sagacity.sqltoy.SqlToyContext,
	 * java.io.Serializable, java.sql.Connection, java.lang.Integer,
	 * java.lang.String, java.lang.String)
	 */
	@Override
	public Object save(SqlToyContext sqlToyContext, Serializable entity, final Connection conn, final Integer dbType,
			final String dialect, final String tableName) throws Exception {
		EntityMeta entityMeta = sqlToyContext.getEntityMeta(entity.getClass());
		// save行为根据主键是否赋值情况调整最终的主键策略
		PKStrategy pkStrategy = DialectUtils.getSavePKStrategy(entityMeta, entity, dbType);
		boolean isAssignPK = DB2DialectUtils.isAssignPKValue(pkStrategy);
		// db2 sequence 支持手工赋值
		String insertSql = DialectExtUtils.generateInsertSql(sqlToyContext.getUnifyFieldsHandler(), dbType, entityMeta,
				pkStrategy, NVL_FUNCTION, "NEXTVAL FOR " + entityMeta.getSequence(), isAssignPK, tableName);
		return DialectUtils.save(sqlToyContext, entityMeta, pkStrategy, isAssignPK, insertSql, entity,
				new GenerateSqlHandler() {
					@Override
					public String generateSql(EntityMeta entityMeta, String[] forceUpdateField) {
						return DialectExtUtils.generateInsertSql(sqlToyContext.getUnifyFieldsHandler(), dbType,
								entityMeta, entityMeta.getIdStrategy(), NVL_FUNCTION,
								"NEXTVAL FOR " + entityMeta.getSequence(),
								DB2DialectUtils.isAssignPKValue(entityMeta.getIdStrategy()), null);
					}
				}, new GenerateSavePKStrategy() {
					@Override
					public SavePKStrategy generate(EntityMeta entityMeta) {
						return new SavePKStrategy(entityMeta.getIdStrategy(),
								DB2DialectUtils.isAssignPKValue(entityMeta.getIdStrategy()));
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
		EntityMeta entityMeta = sqlToyContext.getEntityMeta(entities.get(0).getClass());
		boolean isAssignPK = DB2DialectUtils.isAssignPKValue(entityMeta.getIdStrategy());
		String insertSql = DialectExtUtils.generateInsertSql(sqlToyContext.getUnifyFieldsHandler(), dbType, entityMeta,
				entityMeta.getIdStrategy(), NVL_FUNCTION, "NEXTVAL FOR " + entityMeta.getSequence(), isAssignPK,
				tableName);
		return DialectUtils.saveAll(sqlToyContext, entityMeta, entityMeta.getIdStrategy(), isAssignPK, insertSql,
				entities, batchSize, reflectPropsHandler, conn, dbType, autoCommit);
	}

	/*
	 * (non-Javadoc)
	 * 
	 * @see
	 * org.sagacity.sqltoy.dialect.Dialect#update(org.sagacity.sqltoy.SqlToyContext,
	 * java.io.Serializable, java.lang.String[], boolean, java.lang.Class[],
	 * java.util.HashMap, java.sql.Connection, java.lang.Integer, java.lang.String,
	 * java.lang.String)
	 */
	@Override
	public Long update(final SqlToyContext sqlToyContext, Serializable entity, String[] forceUpdateFields,
			final boolean cascade, final Class[] forceCascadeClasses,
			final HashMap<Class, String[]> subTableForceUpdateProps, Connection conn, final Integer dbType,
			final String dialect, final String tableName) throws Exception {
		return DialectUtils.update(sqlToyContext, entity, NVL_FUNCTION, forceUpdateFields, cascade,
				(cascade == false) ? null : new GenerateSqlHandler() {
					@Override
					public String generateSql(EntityMeta entityMeta, String[] forceUpdateFields) {
						return DialectUtils.getSaveOrUpdateSql(sqlToyContext, sqlToyContext.getUnifyFieldsHandler(),
								dbType, entityMeta, entityMeta.getIdStrategy(), forceUpdateFields, VIRTUAL_TABLE,
								NVL_FUNCTION, "NEXTVAL FOR " + entityMeta.getSequence(),
								DB2DialectUtils.isAssignPKValue(entityMeta.getIdStrategy()), null);
					}
				}, forceCascadeClasses, subTableForceUpdateProps, conn, dbType, tableName);
	}

	/*
	 * (non-Javadoc)
	 * 
	 * @see org.sagacity.sqltoy.dialect.Dialect#updateAll(org.sagacity.sqltoy.
	 * SqlToyContext, java.util.List, int, java.lang.String[],
	 * org.sagacity.sqltoy.callback.ReflectPropsHandler, java.sql.Connection,
	 * java.lang.Integer, java.lang.String, java.lang.Boolean, java.lang.String)
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
	 * @see org.sagacity.sqltoy.dialect.Dialect#getRandomResult(org.sagacity.sqltoy.
	 * SqlToyContext, org.sagacity.sqltoy.config.model.SqlToyConfig,
	 * org.sagacity.sqltoy.model.QueryExecutor, java.lang.Long, java.lang.Long,
	 * java.sql.Connection, java.lang.Integer, java.lang.String)
	 */
	@Override
	public QueryResult getRandomResult(SqlToyContext sqlToyContext, SqlToyConfig sqlToyConfig,
			QueryExecutor queryExecutor, final DecryptHandler decryptHandler, Long totalCount, Long randomCount,
			Connection conn, final Integer dbType, final String dialect, final int fetchSize, final int maxRows)
			throws Exception {
		return DB2DialectUtils.getRandomResult(sqlToyContext, sqlToyConfig, queryExecutor, decryptHandler, totalCount,
				randomCount, conn, dbType, dialect, fetchSize, maxRows);
	}

	/*
	 * (non-Javadoc)
	 * 
	 * @see org.sagacity.sqltoy.dialect.Dialect#findPageBySql(org.sagacity.sqltoy.
	 * SqlToyContext, org.sagacity.sqltoy.config.model.SqlToyConfig,
	 * org.sagacity.sqltoy.model.QueryExecutor, java.lang.Long, java.lang.Integer,
	 * java.sql.Connection, java.lang.Integer, java.lang.String)
	 */
	@Override
	public QueryResult findPageBySql(SqlToyContext sqlToyContext, SqlToyConfig sqlToyConfig,
			QueryExecutor queryExecutor, final DecryptHandler decryptHandler, Long pageNo, Integer pageSize,
			Connection conn, final Integer dbType, final String dialect, final int fetchSize, final int maxRows)
			throws Exception {
		return DefaultDialectUtils.findPageBySql(sqlToyContext, sqlToyConfig, queryExecutor, decryptHandler, pageNo,
				pageSize, conn, dbType, dialect, fetchSize, maxRows);
	}

	/*
	 * (non-Javadoc)
	 * 
	 * @see org.sagacity.sqltoy.dialect.Dialect#findTopBySql(org.sagacity.sqltoy.
	 * SqlToyContext, org.sagacity.sqltoy.config.model.SqlToyConfig,
	 * org.sagacity.sqltoy.model.QueryExecutor, java.lang.Integer,
	 * java.sql.Connection, java.lang.Integer, java.lang.String)
	 */
	@Override
	public QueryResult findTopBySql(SqlToyContext sqlToyContext, SqlToyConfig sqlToyConfig, QueryExecutor queryExecutor,
			final DecryptHandler decryptHandler, Integer topSize, Connection conn, final Integer dbType,
			final String dialect, final int fetchSize, final int maxRows) throws Exception {
		return DefaultDialectUtils.findTopBySql(sqlToyContext, sqlToyConfig, queryExecutor, decryptHandler, topSize,
				conn, dbType, dialect, fetchSize, maxRows);
	}

	/*
	 * (non-Javadoc)
	 * 
	 * @see org.sagacity.sqltoy.dialect.Dialect#findBySql(org.sagacity.sqltoy.
	 * SqlToyContext, org.sagacity.sqltoy.config.model.SqlToyConfig,
	 * java.lang.String, java.lang.Object[],
	 * org.sagacity.sqltoy.callback.RowCallbackHandler, java.sql.Connection,
	 * java.lang.Integer, java.lang.String, int, int)
	 */
	@Override
	public QueryResult findBySql(final SqlToyContext sqlToyContext, final SqlToyConfig sqlToyConfig, final String sql,
			final Object[] paramsValue, final QueryExecutorExtend queryExecutorExtend,
			final DecryptHandler decryptHandler, final Connection conn, final LockMode lockMode, final Integer dbType,
			final String dialect, final int fetchSize, final int maxRows) throws Exception {
		String realSql;
		// db2 锁记录
		if (lockMode != null) {
			realSql = sql.concat(getLockSql(sql, dbType, lockMode));
		} else {
			realSql = appendWithUR(sql);
		}
		return DialectUtils.findBySql(sqlToyContext, sqlToyConfig, realSql, paramsValue, queryExecutorExtend,
				decryptHandler, conn, dbType, 0, fetchSize, maxRows);
	}

	/*
	 * (non-Javadoc)
	 * 
	 * @see org.sagacity.sqltoy.dialect.Dialect#getCountBySql(org.sagacity.sqltoy.
	 * SqlToyContext, org.sagacity.sqltoy.config.model.SqlToyConfig,
	 * java.lang.String, java.lang.Object[], boolean, java.sql.Connection,
	 * java.lang.Integer, java.lang.String)
	 */
	@Override
	public Long getCountBySql(final SqlToyContext sqlToyContext, final SqlToyConfig sqlToyConfig, final String sql,
			final Object[] paramsValue, final boolean isLastSql, Connection conn, final Integer dbType,
			final String dialect) throws Exception {
		return DialectUtils.getCountBySql(sqlToyContext, sqlToyConfig, appendWithUR(sql), paramsValue, isLastSql, conn,
				dbType);
	}

	/*
	 * (non-Javadoc)
	 * 
	 * @see org.sagacity.sqltoy.dialect.Dialect#saveOrUpdate(org.sagacity.sqltoy.
	 * SqlToyContext, java.io.Serializable, java.lang.String[], java.sql.Connection,
	 * java.lang.Integer, java.lang.String, java.lang.Boolean, java.lang.String)
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
	 * @see org.sagacity.sqltoy.dialect.Dialect#saveOrUpdateAll(org.sagacity.sqltoy.
	 * SqlToyContext, java.util.List, int,
	 * org.sagacity.sqltoy.callback.ReflectPropsHandler, java.lang.String[],
	 * java.sql.Connection, java.lang.Integer, java.lang.String, java.lang.Boolean,
	 * java.lang.String)
	 */
	@Override
	public Long saveOrUpdateAll(final SqlToyContext sqlToyContext, List<?> entities, final int batchSize,
			final ReflectPropsHandler reflectPropsHandler, final String[] forceUpdateFields, Connection conn,
			final Integer dbType, final String dialect, final Boolean autoCommit, final String tableName)
			throws Exception {
		EntityMeta entityMeta = sqlToyContext.getEntityMeta(entities.get(0).getClass());
		return DialectUtils.saveOrUpdateAll(sqlToyContext, entities, batchSize, entityMeta, forceUpdateFields,
				new GenerateSqlHandler() {
					@Override
					public String generateSql(EntityMeta entityMeta, String[] forceUpdateFields) {
						return DialectUtils.getSaveOrUpdateSql(sqlToyContext, sqlToyContext.getUnifyFieldsHandler(),
								dbType, entityMeta, entityMeta.getIdStrategy(), forceUpdateFields, VIRTUAL_TABLE,
								NVL_FUNCTION, "NEXTVAL FOR " + entityMeta.getSequence(),
								DB2DialectUtils.isAssignPKValue(entityMeta.getIdStrategy()), tableName);
					}
				}, reflectPropsHandler, conn, dbType, autoCommit);
	}

	/*
	 * (non-Javadoc)
	 * 
	 * @see
	 * org.sagacity.sqltoy.dialect.Dialect#saveAllIgnoreExist(org.sagacity.sqltoy.
	 * SqlToyContext, java.util.List, int,
	 * org.sagacity.sqltoy.callback.ReflectPropsHandler, java.sql.Connection,
	 * java.lang.Integer, java.lang.String, java.lang.Boolean, java.lang.String)
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
						return DialectExtUtils.mergeIgnore(sqlToyContext.getUnifyFieldsHandler(), dbType, entityMeta,
								pkStrategy, VIRTUAL_TABLE, NVL_FUNCTION, "NEXTVAL FOR " + entityMeta.getSequence(),
								DB2DialectUtils.isAssignPKValue(pkStrategy), tableName);
					}
				}, reflectPropsHandler, conn, dbType, autoCommit);
	}

	/*
	 * (non-Javadoc)
	 * 
	 * @see
	 * org.sagacity.sqltoy.dialect.Dialect#delete(org.sagacity.sqltoy.SqlToyContext,
	 * java.io.Serializable, java.sql.Connection, java.lang.Integer,
	 * java.lang.String, java.lang.String)
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
	 * SqlToyContext, java.util.List, int, java.sql.Connection, java.lang.Integer,
	 * java.lang.String, java.lang.Boolean, java.lang.String)
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
	 * java.lang.String, java.lang.Object[],
	 * org.sagacity.sqltoy.callback.UpdateRowHandler, java.sql.Connection,
	 * java.lang.Integer, java.lang.String)
	 */
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
		return DialectUtils.executeStore(sqlToyConfig, sqlToyContext, sql, inParamsValue, outParamsType, moreResult,
				conn, dbType, fetchSize);
	}

	private String getLockSql(String sql, Integer dbType, LockMode lockMode) {
		// 判断是否已经包含for update
		if (lockMode == null || SqlUtil.hasLock(sql, dbType)) {
			return "";
		}
		// if (lockMode == null || lockMode == LockMode.UPGRADE) {
		// return " for update ";
		// }
		return " for update with rs ";
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
}
