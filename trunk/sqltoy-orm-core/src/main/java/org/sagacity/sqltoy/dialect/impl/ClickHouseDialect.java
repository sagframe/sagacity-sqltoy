package org.sagacity.sqltoy.dialect.impl;

import java.io.Serializable;
import java.sql.Connection;
import java.util.HashMap;
import java.util.List;

import org.sagacity.sqltoy.SqlToyConstants;
import org.sagacity.sqltoy.SqlToyContext;
import org.sagacity.sqltoy.callback.DecryptHandler;
import org.sagacity.sqltoy.callback.ReflectPropsHandler;
import org.sagacity.sqltoy.callback.UpdateRowHandler;
import org.sagacity.sqltoy.config.model.EntityMeta;
import org.sagacity.sqltoy.config.model.PKStrategy;
import org.sagacity.sqltoy.config.model.SqlToyConfig;
import org.sagacity.sqltoy.config.model.SqlType;
import org.sagacity.sqltoy.dialect.Dialect;
import org.sagacity.sqltoy.dialect.utils.ClickHouseDialectUtils;
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

/**
 * @project sqltoy-orm
 * @description clickhouse 19.x版本,clickhouse 不支持updateAll,更多面向查询
 * @author zhongxuchen
 * @version v1.0,Date:2020年1月20日
 */
@SuppressWarnings({ "rawtypes" })
public class ClickHouseDialect implements Dialect {
	/**
	 * 判定为null的函数
	 */
	public static final String NVL_FUNCTION = "ifnull";

	@Override
	public boolean isUnique(SqlToyContext sqlToyContext, Serializable entity, String[] paramsNamed, Connection conn,
			final Integer dbType, final String tableName) {
		return DialectUtils.isUnique(sqlToyContext, entity, paramsNamed, conn, dbType, tableName,
				(entityMeta, realParamNamed, table, topSize) -> {
					String queryStr = DialectExtUtils.wrapUniqueSql(entityMeta, realParamNamed, dbType, table);
					return queryStr + " limit " + topSize;
				});
	}

	@Override
	public QueryResult getRandomResult(SqlToyContext sqlToyContext, SqlToyConfig sqlToyConfig,
			QueryExecutor queryExecutor, final DecryptHandler decryptHandler, Long totalCount, Long randomCount,
			Connection conn, Integer dbType, String dialect, final int fetchSize, final int maxRows) throws Exception {
		return DefaultDialectUtils.getRandomResult(sqlToyContext, sqlToyConfig, queryExecutor, decryptHandler,
				totalCount, randomCount, conn, dbType, dialect, fetchSize, maxRows);
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
			Connection conn, Integer dbType, String dialect, final int fetchSize, final int maxRows) throws Exception {
		return DefaultDialectUtils.findPageBySql(sqlToyContext, sqlToyConfig, queryExecutor, decryptHandler, pageNo,
				pageSize, conn, dbType, dialect, fetchSize, maxRows);
	}

	@Override
	public QueryResult findTopBySql(SqlToyContext sqlToyContext, SqlToyConfig sqlToyConfig, QueryExecutor queryExecutor,
			final DecryptHandler decryptHandler, Integer topSize, Connection conn, Integer dbType, String dialect,
			final int fetchSize, final int maxRows) throws Exception {
		return DefaultDialectUtils.findTopBySql(sqlToyContext, sqlToyConfig, queryExecutor, decryptHandler, topSize,
				conn, dbType, dialect, fetchSize, maxRows);
	}

	@Override
	public QueryResult findBySql(SqlToyContext sqlToyContext, SqlToyConfig sqlToyConfig, String sql,
			Object[] paramsValue, QueryExecutorExtend queryExecutorExtend, final DecryptHandler decryptHandler,
			Connection conn, final LockMode lockMode, Integer dbType, String dialect, int fetchSize, int maxRows)
			throws Exception {
		// clickhouse目前不支持锁查询
		if (null != lockMode) {
			throw new UnsupportedOperationException("clickHouse lock search," + SqlToyConstants.UN_SUPPORT_MESSAGE);
		}
		return DialectUtils.findBySql(sqlToyContext, sqlToyConfig, sql, paramsValue, queryExecutorExtend,
				decryptHandler, conn, dbType, 0, fetchSize, maxRows);
	}

	@Override
	public Long getCountBySql(SqlToyContext sqlToyContext, SqlToyConfig sqlToyConfig, String sql, Object[] paramsValue,
			boolean isLastSql, Connection conn, Integer dbType, String dialect) throws Exception {
		return DialectUtils.getCountBySql(sqlToyContext, sqlToyConfig, sql, paramsValue, isLastSql, conn, dbType);
	}

	@Override
	public Serializable load(SqlToyContext sqlToyContext, Serializable entity, boolean onlySubTables,
			List<Class> cascadeTypes, LockMode lockMode, Connection conn, Integer dbType, String dialect,
			String tableName) throws Exception {
		EntityMeta entityMeta = sqlToyContext.getEntityMeta(entity.getClass());
		// 获取loadsql(loadsql 可以通过@loadSql进行改变，所以需要sqltoyContext重新获取)
		SqlToyConfig sqlToyConfig = sqlToyContext.getSqlToyConfig(entityMeta.getLoadSql(tableName), SqlType.search,
				dialect, null);
		String loadSql = sqlToyConfig.getSql(dialect);
		return (Serializable) DialectUtils.load(sqlToyContext, sqlToyConfig, loadSql, entityMeta, entity, onlySubTables,
				cascadeTypes, conn, dbType);
	}

	@Override
	public List<?> loadAll(SqlToyContext sqlToyContext, List<?> entities, boolean onlySubTables,
			List<Class> cascadeTypes, LockMode lockMode, Connection conn, Integer dbType, String dialect,
			String tableName, final int fetchSize, final int maxRows) throws Exception {
		return DialectUtils.loadAll(sqlToyContext, entities, onlySubTables, cascadeTypes, lockMode, conn, dbType,
				tableName, null, fetchSize, maxRows);
	}

	@Override
	public Object save(SqlToyContext sqlToyContext, Serializable entity, Connection conn, Integer dbType,
			String dialect, String tableName) throws Exception {
		EntityMeta entityMeta = sqlToyContext.getEntityMeta(entity.getClass());
		// save行为根据主键是否赋值情况调整最终的主键策略
		PKStrategy pkStrategy = DialectUtils.getSavePKStrategy(entityMeta, entity, dbType);
		// clickhouse 不支持sequence，支持identity自增模式
		String insertSql = DialectExtUtils.generateInsertSql(sqlToyContext.getUnifyFieldsHandler(), dbType, entityMeta,
				pkStrategy, NVL_FUNCTION, "NEXTVAL FOR " + entityMeta.getSequence(),
				ClickHouseDialectUtils.isAssignPKValue(pkStrategy), tableName);
		return ClickHouseDialectUtils.save(sqlToyContext, entityMeta, pkStrategy, insertSql, entity, conn, dbType);
	}

	@Override
	public Long saveAll(SqlToyContext sqlToyContext, List<?> entities, int batchSize,
			ReflectPropsHandler reflectPropsHandler, Connection conn, Integer dbType, String dialect,
			Boolean autoCommit, String tableName) throws Exception {
		EntityMeta entityMeta = sqlToyContext.getEntityMeta(entities.get(0).getClass());
		// clickhouse 不支持sequence，支持identity自增模式
		String insertSql = DialectExtUtils.generateInsertSql(sqlToyContext.getUnifyFieldsHandler(), dbType, entityMeta,
				entityMeta.getIdStrategy(), NVL_FUNCTION, "NEXTVAL FOR " + entityMeta.getSequence(),
				ClickHouseDialectUtils.isAssignPKValue(entityMeta.getIdStrategy()), tableName);
		return ClickHouseDialectUtils.saveAll(sqlToyContext, entityMeta, insertSql, entities, batchSize,
				reflectPropsHandler, conn, dbType, autoCommit);
	}

	@Override
	public Long update(SqlToyContext sqlToyContext, Serializable entity, String[] forceUpdateFields, boolean cascade,
			Class[] forceCascadeClass, HashMap<Class, String[]> subTableForceUpdateProps, Connection conn,
			Integer dbType, String dialect, String tableName) throws Exception {
		return ClickHouseDialectUtils.update(sqlToyContext, entity, NVL_FUNCTION, forceUpdateFields, conn, dbType,
				tableName);
	}

	@Override
	public Long updateAll(SqlToyContext sqlToyContext, List<?> entities, int batchSize, final String[] uniqueFields,
			String[] forceUpdateFields, ReflectPropsHandler reflectPropsHandler, Connection conn, Integer dbType,
			String dialect, Boolean autoCommit, String tableName) throws Exception {
		return ClickHouseDialectUtils.updateAll(sqlToyContext, entities, batchSize, forceUpdateFields,
				reflectPropsHandler, NVL_FUNCTION, conn, dbType, autoCommit, tableName, false);
	}

	@Override
	public Long saveOrUpdate(SqlToyContext sqlToyContext, Serializable entity, String[] forceUpdateFields,
			Connection conn, Integer dbType, String dialect, Boolean autoCommit, String tableName) throws Exception {
		// 不支持
		throw new UnsupportedOperationException(SqlToyConstants.UN_SUPPORT_MESSAGE);
	}

	@Override
	public Long saveOrUpdateAll(SqlToyContext sqlToyContext, List<?> entities, int batchSize,
			ReflectPropsHandler reflectPropsHandler, String[] forceUpdateFields, Connection conn, Integer dbType,
			String dialect, Boolean autoCommit, String tableName) throws Exception {
		// 不支持
		throw new UnsupportedOperationException(SqlToyConstants.UN_SUPPORT_MESSAGE);
	}

	@Override
	public Serializable updateSaveFetch(SqlToyContext sqlToyContext, Serializable entity,
			UpdateRowHandler updateRowHandler, String[] uniqueProps, Connection conn, Integer dbType, String dialect,
			String tableName) throws Exception {
		// 不支持
		throw new UnsupportedOperationException(SqlToyConstants.UN_SUPPORT_MESSAGE);
	}

	@Override
	public Long saveAllIgnoreExist(SqlToyContext sqlToyContext, List<?> entities, int batchSize,
			ReflectPropsHandler reflectPropsHandler, Connection conn, Integer dbType, String dialect,
			Boolean autoCommit, String tableName) throws Exception {
		// 不支持
		throw new UnsupportedOperationException(SqlToyConstants.UN_SUPPORT_MESSAGE);
	}

	@Override
	public Long delete(SqlToyContext sqlToyContext, Serializable entity, Connection conn, Integer dbType,
			String dialect, String tableName) throws Exception {
		return ClickHouseDialectUtils.delete(sqlToyContext, entity, conn, dbType, tableName);
	}

	@Override
	public Long deleteAll(SqlToyContext sqlToyContext, List<?> entities, int batchSize, Connection conn, Integer dbType,
			String dialect, Boolean autoCommit, String tableName) throws Exception {
		return DefaultDialectUtils.deleteAll(sqlToyContext, entities, batchSize, conn, dbType, autoCommit, tableName);
	}

	@Override
	public QueryResult updateFetch(SqlToyContext sqlToyContext, SqlToyConfig sqlToyConfig, String sql,
			Object[] paramValues, UpdateRowHandler updateRowHandler, Connection conn, Integer dbType, String dialect,
			final LockMode lockMode, final int fetchSize, final int maxRows) throws Exception {
		// 不支持
		throw new UnsupportedOperationException(SqlToyConstants.UN_SUPPORT_MESSAGE);
	}

	@Override
	public StoreResult executeStore(SqlToyContext sqlToyContext, SqlToyConfig sqlToyConfig, String sql,
			Object[] inParamsValue, Integer[] outParamsType, final boolean moreResult, Connection conn, Integer dbType,
			String dialect, final int fetchSize) throws Exception {
		// 不支持
		throw new UnsupportedOperationException(SqlToyConstants.UN_SUPPORT_MESSAGE);
	}

	@Override
	public List<ColumnMeta> getTableColumns(String catalog, String schema, String tableName, Connection conn,
			Integer dbType, String dialect) throws Exception {
		return ClickHouseDialectUtils.getTableColumns(catalog, schema, tableName, conn, dbType, dialect);
	}

	@Override
	public List<TableMeta> getTables(String catalog, String schema, String tableName, Connection conn, Integer dbType,
			String dialect) throws Exception {
		return DefaultDialectUtils.getTables(catalog, schema, tableName, conn, dbType, dialect);
	}

}
