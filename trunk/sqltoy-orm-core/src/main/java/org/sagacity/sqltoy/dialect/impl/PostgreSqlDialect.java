package org.sagacity.sqltoy.dialect.impl;

import java.io.Serializable;
import java.sql.Connection;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;

import org.sagacity.sqltoy.SqlExecuteStat;
import org.sagacity.sqltoy.SqlToyContext;
import org.sagacity.sqltoy.callback.DecryptHandler;
import org.sagacity.sqltoy.callback.GenerateSqlHandler;
import org.sagacity.sqltoy.callback.ReflectPropsHandler;
import org.sagacity.sqltoy.callback.UpdateRowCallback;
import org.sagacity.sqltoy.callback.UpdateRowHandler;
import org.sagacity.sqltoy.config.model.EntityMeta;
import org.sagacity.sqltoy.config.model.PKStrategy;
import org.sagacity.sqltoy.config.model.SqlToyConfig;
import org.sagacity.sqltoy.config.model.SqlType;
import org.sagacity.sqltoy.dialect.Dialect;
import org.sagacity.sqltoy.dialect.utils.DefaultDialectUtils;
import org.sagacity.sqltoy.dialect.utils.DialectExtUtils;
import org.sagacity.sqltoy.dialect.utils.DialectUtils;
import org.sagacity.sqltoy.dialect.utils.PostgreSqlDialectUtils;
import org.sagacity.sqltoy.model.ColumnMeta;
import org.sagacity.sqltoy.model.DBProfile;
import org.sagacity.sqltoy.model.LockMode;
import org.sagacity.sqltoy.model.QueryExecutor;
import org.sagacity.sqltoy.model.QueryResult;
import org.sagacity.sqltoy.model.StoreResult;
import org.sagacity.sqltoy.model.TableMeta;
import org.sagacity.sqltoy.model.inner.QueryExecutorExtend;
import org.sagacity.sqltoy.utils.DataSourceUtils.DBType;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * @project sagacity-sqltoy
 * @description 基于postgresql9.5+版本的方言实现,9.5开始insert into [ON CONFLICT DO
 *              NOTHING/UPDATE]功能生效
 * @author zhongxuchen
 * @version v1.0,Date:2015-08-10
 * @modify Date:2019-03-12
 *         修复saveOrUpdate的缺陷,改为先update后saveIgnore，因为其跟mysql一样存在bug
 * @modify Date:2020-06-12 修复10+版本对identity主键生成的策略
 */
@SuppressWarnings({ "rawtypes" })
public class PostgreSqlDialect implements Dialect {
	/**
	 * 定义日志
	 */
	protected final Logger logger = LoggerFactory.getLogger(PostgreSqlDialect.class);

	@Override
	public boolean isUnique(SqlToyContext sqlToyContext, Serializable entity, String[] paramsNamed, Connection conn,
			DBProfile profile, String tableName, final Integer queryTimeout) {
		return DialectUtils.isUnique(sqlToyContext, entity, paramsNamed, conn, profile, tableName,
				(entityMeta, realParamNamed, table, topSize) -> {
					String queryStr = DialectExtUtils.wrapUniqueSql(entityMeta, realParamNamed, profile, table);
					return queryStr + " limit " + topSize;
				}, queryTimeout);
	}

	/*
	 * (non-Javadoc)
	 * 
	 * @see org.sagacity.sqltoy.dialect.Dialect#getRandomResult(org.sagacity.sqltoy.
	 * SqlToyContext, org.sagacity.sqltoy.config.model.SqlToyConfig,
	 * org.sagacity.sqltoy.model.QueryExecutor, java.lang.Long, java.lang.Long,
	 * java.sql.Connection)
	 */
	@Override
	public QueryResult getRandomResult(SqlToyContext sqlToyContext, SqlToyConfig sqlToyConfig,
			QueryExecutor queryExecutor, final DecryptHandler decryptHandler, Long totalCount, Long randomCount,
			Connection conn, DBProfile profile, final int fetchSize, final int maxRows) throws Exception {
		return PostgreSqlDialectUtils.getRandomResult(sqlToyContext, sqlToyConfig, queryExecutor, decryptHandler,
				totalCount, randomCount, conn, profile, fetchSize, maxRows);
	}

	/*
	 * (non-Javadoc)
	 * 
	 * @see org.sagacity.sqltoy.dialect.Dialect#findPageBySql(org.sagacity.sqltoy.
	 * SqlToyContext, org.sagacity.sqltoy.config.model.SqlToyConfig,
	 * org.sagacity.sqltoy.model.QueryExecutor, java.lang.Long, java.lang.Integer,
	 * java.sql.Connection)
	 */
	@Override
	public QueryResult findPageBySql(SqlToyContext sqlToyContext, SqlToyConfig sqlToyConfig,
			QueryExecutor queryExecutor, final DecryptHandler decryptHandler, Long pageNo, Integer pageSize,
			Connection conn, DBProfile profile, final int fetchSize, final int maxRows) throws Exception {
		return DefaultDialectUtils.findPageBySql(sqlToyContext, sqlToyConfig, queryExecutor, decryptHandler, pageNo,
				pageSize, conn, profile, fetchSize, maxRows);
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
			final DecryptHandler decryptHandler, Integer topSize, Connection conn, DBProfile profile,
			final int fetchSize, final int maxRows) throws Exception {
		return DefaultDialectUtils.findTopBySql(sqlToyContext, sqlToyConfig, queryExecutor, decryptHandler, topSize,
				conn, profile, fetchSize, maxRows);
	}

	/*
	 * (non-Javadoc)
	 * 
	 * @see org.sagacity.sqltoy.dialect.Dialect#findBySql(org.sagacity.sqltoy.
	 * SqlToyContext, org.sagacity.sqltoy.config.model.SqlToyConfig,
	 * java.lang.String, java.lang.Object[],
	 * org.sagacity.sqltoy.callback.RowCallbackHandler, java.sql.Connection)
	 */
	@Override
	public QueryResult findBySql(SqlToyContext sqlToyContext, SqlToyConfig sqlToyConfig, String sql,
			Object[] paramsValue, QueryExecutorExtend queryExecutorExtend, final DecryptHandler decryptHandler,
			final Connection conn, final LockMode lockMode, DBProfile profile, final int fetchSize, final int maxRows)
			throws Exception {
		Integer dbType = profile.getDbType();
		String realSql = sql.concat(getLockSql(sql, dbType, lockMode, queryExecutorExtend.lockWaitTimeout));
		return DialectUtils.findBySql(sqlToyContext, sqlToyConfig, realSql, paramsValue, queryExecutorExtend,
				decryptHandler, conn, profile, 0, fetchSize, maxRows);
	}

	/*
	 * (non-Javadoc)
	 * 
	 * @see org.sagacity.sqltoy.dialect.Dialect#getCountBySql(org.sagacity.sqltoy.
	 * SqlToyContext, java.lang.String, java.lang.Object[], boolean,
	 * java.sql.Connection)
	 */
	@Override
	public Long getCountBySql(SqlToyContext sqlToyContext, final SqlToyConfig sqlToyConfig, String sql,
			Object[] paramsValue, boolean isLastSql, final QueryExecutorExtend extend, Connection conn,
			DBProfile profile) throws Exception {
		return DialectUtils.getCountBySql(sqlToyContext, sqlToyConfig, sql, paramsValue, isLastSql, extend, conn,
				profile);
	}

	/*
	 * (non-Javadoc)
	 * 
	 * @see org.sagacity.sqltoy.dialect.Dialect#load(org.sagacity.sqltoy.
	 * SqlToyContext, java.io.Serializable, java.util.List,
	 * org.sagacity.sqltoy.lock.LockMode, java.sql.Connection)
	 */
	@Override
	public Serializable load(SqlToyContext sqlToyContext, Serializable entity, boolean onlySubTables,
			List<Class> cascadeTypes, LockMode lockMode, int lockWaitTimeout, Connection conn, DBProfile profile,
			final String tableName, final Integer queryTimeout) throws Exception {
		Integer dbType = profile.getDbType();
		String dialect = profile.getDialect();
		EntityMeta entityMeta = sqlToyContext.getEntityMeta(entity.getClass());
		// 获取loadsql(loadsql 可以通过@loadSql进行改变，所以需要sqltoyContext重新获取)
		SqlToyConfig sqlToyConfig = sqlToyContext.getSqlToyConfig(entityMeta.getLoadSql(tableName), SqlType.search,
				dialect, null);
		String loadSql = sqlToyConfig.getSql(dialect);
		loadSql = loadSql.concat(getLockSql(loadSql, dbType, lockMode, lockWaitTimeout));
		return (Serializable) DialectUtils.load(sqlToyContext, sqlToyConfig, loadSql, entityMeta, entity, onlySubTables,
				cascadeTypes, conn, profile, queryTimeout);
	}

	/*
	 * (non-Javadoc)
	 * 
	 * @see org.sagacity.sqltoy.dialect.Dialect#loadAll(org.sagacity.sqltoy.
	 * SqlToyContext, java.util.List, java.util.List,
	 * org.sagacity.sqltoy.lock.LockMode, java.sql.Connection)
	 */
	@Override
	public List<?> loadAll(SqlToyContext sqlToyContext, List<?> entities, boolean onlySubTables,
			List<Class> cascadeTypes, LockMode lockMode, final int lockWaitTimeout, Connection conn, DBProfile profile,
			final String tableName, final int fetchSize, final int maxRows, final Integer queryTimeout)
			throws Exception {
		return DialectUtils.loadAll(sqlToyContext, entities, onlySubTables, cascadeTypes, lockMode, conn, profile,
				tableName, (sql, dbTypeValue, lockedMode) -> {
					return getLockSql(sql, dbTypeValue, lockedMode, lockWaitTimeout);
				}, fetchSize, maxRows, queryTimeout);
	}

	/*
	 * (non-Javadoc)
	 * 
	 * @see org.sagacity.sqltoy.dialect.Dialect#save(org.sagacity.sqltoy.
	 * SqlToyContext, java.io.Serializable, java.sql.Connection)
	 */
	@Override
	public Object save(SqlToyContext sqlToyContext, Serializable entity, Connection conn, DBProfile profile,
			final String tableName) throws Exception {
		return PostgreSqlDialectUtils.save(sqlToyContext, entity, conn, profile, tableName);
	}

	/*
	 * (non-Javadoc)
	 * 
	 * @see org.sagacity.sqltoy.dialect.Dialect#saveAll(org.sagacity.sqltoy.
	 * SqlToyContext, java.util.List,
	 * org.sagacity.sqltoy.callback.ReflectPropsHandler, java.sql.Connection)
	 */
	@Override
	public Long saveAll(SqlToyContext sqlToyContext, List<?> entities, final int batchSize,
			ReflectPropsHandler reflectPropsHandler, Connection conn, DBProfile profile, final Boolean autoCommit,
			final String tableName) throws Exception {
		return PostgreSqlDialectUtils.saveAll(sqlToyContext, entities, batchSize, reflectPropsHandler, conn, profile,
				autoCommit, tableName);
	}

	/*
	 * (non-Javadoc)
	 * 
	 * @see org.sagacity.sqltoy.dialect.Dialect#update(org.sagacity.sqltoy.
	 * SqlToyContext, java.io.Serializable, java.lang.String[], boolean,
	 * java.lang.Class[], java.util.HashMap, java.sql.Connection)
	 */
	@Override
	public Long update(SqlToyContext sqlToyContext, Serializable entity, String[] forceUpdateFields, boolean cascade,
			Class[] forceCascadeClasses, HashMap<Class, String[]> subTableForceUpdateProps, Connection conn,
			DBProfile profile, final String tableName) throws Exception {
		return DialectUtils.update(sqlToyContext, entity, forceUpdateFields, cascade,
				(cascade == false) ? null : new GenerateSqlHandler() {
					@Override
					public String generateSql(EntityMeta entityMeta, String[] forceUpdateFields) {
						PKStrategy pkStrategy = entityMeta.getIdStrategy();
						String sequence = "nextval('" + entityMeta.getSequence() + "')";
						boolean isAssignPK = PostgreSqlDialectUtils.allowAssignPKValue(pkStrategy);
						// update 级联操作过程中会自动判断postgresql15，而采用不同策略(这里统一按15的规则提供，14之前版本并不用到)
						return DialectUtils.getSaveOrUpdateSql(sqlToyContext, sqlToyContext.getUnifyFieldsHandler(),
								profile, entityMeta, pkStrategy, forceUpdateFields, null, sequence, isAssignPK, null);
					}
				}, forceCascadeClasses, subTableForceUpdateProps, conn, profile, tableName);
	}

	/*
	 * (non-Javadoc)
	 * 
	 * @see org.sagacity.sqltoy.dialect.Dialect#updateAll(org.sagacity.sqltoy.
	 * SqlToyContext, java.util.List, java.lang.String[],
	 * org.sagacity.sqltoy.callback.ReflectPropsHandler, java.sql.Connection)
	 */
	@Override
	public Long updateAll(SqlToyContext sqlToyContext, List<?> entities, final int batchSize,
			final String[] uniqueFields, String[] forceUpdateFields, ReflectPropsHandler reflectPropsHandler,
			Connection conn, DBProfile profile, final Boolean autoCommit, final String tableName) throws Exception {
		return DialectUtils.updateAll(sqlToyContext, entities, batchSize, forceUpdateFields, reflectPropsHandler, conn,
				profile, autoCommit, tableName, false);
	}

	@Override
	public Serializable updateSaveFetch(SqlToyContext sqlToyContext, Serializable entity,
			UpdateRowHandler updateRowHandler, int lockWaitTimeout, String[] uniqueProps, Connection conn,
			DBProfile profile, String tableName) throws Exception {
		return DefaultDialectUtils.updateSaveFetch(sqlToyContext, entity, updateRowHandler, uniqueProps, conn, profile,
				tableName, lockWaitTimeout);
	}

	public Serializable updateSaveFetch(SqlToyContext sqlToyContext, Serializable entity,
			UpdateRowCallback updateRowCallback, int lockWaitTimeout, String[] uniqueProps, Connection conn,
			DBProfile profile, String tableName) throws Exception {
		return DefaultDialectUtils.updateSaveFetch(sqlToyContext, entity, updateRowCallback, uniqueProps, conn, profile,
				tableName, lockWaitTimeout);
	}

	// postgres的ON CONFLICT ON CONSTRAINT() DO UPDATE SET特性跟mysql一样存在bug
	/*
	 * (non-Javadoc)
	 * 
	 * @see org.sagacity.sqltoy.dialect.Dialect#saveOrUpdate(org.sagacity.sqltoy.
	 * SqlToyContext, java.io.Serializable, java.lang.String[], java.sql.Connection,
	 * java.lang.Boolean)
	 */
	@Override
	public Long saveOrUpdate(SqlToyContext sqlToyContext, Serializable entity, String[] forceUpdateFields,
			Connection conn, DBProfile profile, final Boolean autoCommit, final String tableName) throws Exception {
		List<Serializable> entities = new ArrayList<Serializable>();
		entities.add(entity);
		return saveOrUpdateAll(sqlToyContext, entities, sqlToyContext.getBatchSize(), null, forceUpdateFields, conn,
				profile, autoCommit, tableName);
	}

	/*
	 * (non-Javadoc)
	 * 
	 * @see org.sagacity.sqltoy.dialect.Dialect#saveOrUpdateAll(org.sagacity.sqltoy.
	 * SqlToyContext, java.util.List,
	 * org.sagacity.sqltoy.callback.ReflectPropsHandler, java.lang.String[],
	 * java.sql.Connection, java.lang.Boolean)
	 */
	@Override
	public Long saveOrUpdateAll(SqlToyContext sqlToyContext, List<?> entities, final int batchSize,
			ReflectPropsHandler reflectPropsHandler, String[] forceUpdateFields, Connection conn, DBProfile profile,
			final Boolean autoCommit, final String tableName) throws Exception {
		Integer dbType = profile.getDbType();
		// 暂时不开放，postgresql类型太多,merge 语句中需要case(? as type) as columnName
		// 目前type类型无法完整适配支持
		// postgresql15 支持merge into
		if (dbType.equals(DBType.POSTGRESQL14)) {
			// DBType.POSTGRESQL14
			Long updateCnt = DialectUtils.updateAll(sqlToyContext, entities, batchSize, forceUpdateFields,
					reflectPropsHandler, conn, profile, autoCommit, tableName, true);
			// 如果修改的记录数量跟总记录数量一致,表示全部是修改
			if (updateCnt >= entities.size()) {
				SqlExecuteStat.debug("update record", "update rows:" + updateCnt
						+ " equals the size of entities collection, skip the insert operation!");
				return updateCnt;
			}
			Long saveCnt = saveAllIgnoreExist(sqlToyContext, entities, batchSize, reflectPropsHandler, conn, profile,
					autoCommit, tableName);
			SqlExecuteStat.debug("insert record", "insert rows:" + saveCnt + "!");
			return updateCnt + saveCnt;
		}
		return PostgreSqlDialectUtils.saveOrUpdateAll(sqlToyContext, entities, batchSize, reflectPropsHandler,
				forceUpdateFields, conn, profile, autoCommit, tableName);
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
			ReflectPropsHandler reflectPropsHandler, Connection conn, DBProfile profile, final Boolean autoCommit,
			final String tableName) throws Exception {
		Integer dbType = profile.getDbType();
		EntityMeta entityMeta = sqlToyContext.getEntityMeta(entities.get(0).getClass());
		// pg14通过on conflict do nothing实现saveAllIgnoreExist语义,改走saveAll:
		// identity主键按rejectId反射,与insertIgnore省略主键列的sql保持占位符与参数对齐
		// (原走saveAllIgnoreExist按全字段反射,identity时参数比?多一个导致绑定越界;
		// 且伪造成sequence+DEFAULT会产生COALESCE(?,DEFAULT)非法sql,pg的DEFAULT只能裸用于VALUES项)
		if (dbType != null && dbType.intValue() == DBType.POSTGRESQL14) {
			PKStrategy pkStrategy = entityMeta.getIdStrategy();
			boolean isAssignPK = PostgreSqlDialectUtils.allowAssignPKValue(pkStrategy);
			String insertSql = DialectExtUtils.insertIgnore(sqlToyContext.getUnifyFieldsHandler(), profile, entityMeta,
					pkStrategy, "nextval('" + entityMeta.getSequence() + "')", isAssignPK, tableName);
			return DialectUtils.saveAll(sqlToyContext, entityMeta, pkStrategy, isAssignPK, insertSql, entities,
					batchSize, reflectPropsHandler, conn, profile, autoCommit);
		}
		return DialectUtils.saveAllIgnoreExist(sqlToyContext, entities, batchSize, entityMeta,
				new GenerateSqlHandler() {
					@Override
					public String generateSql(EntityMeta entityMeta, String[] forceUpdateFields) {
						PKStrategy pkStrategy = entityMeta.getIdStrategy();
						String sequence = "nextval('" + entityMeta.getSequence() + "')";
						boolean isAssignPK = PostgreSqlDialectUtils.allowAssignPKValue(pkStrategy);
						return DialectUtils.mergeIgnore(sqlToyContext.getUnifyFieldsHandler(), profile, entityMeta,
								pkStrategy, null, sequence, isAssignPK, tableName);
					}
				}, reflectPropsHandler, conn, profile, autoCommit);
	}

	/*
	 * (non-Javadoc)
	 * 
	 * @see org.sagacity.sqltoy.dialect.Dialect#delete(org.sagacity.sqltoy.
	 * SqlToyContext, java.io.Serializable, java.sql.Connection)
	 */
	@Override
	public Long delete(SqlToyContext sqlToyContext, Serializable entity, Connection conn, DBProfile profile,
			final String tableName) throws Exception {
		return DialectUtils.delete(sqlToyContext, entity, conn, profile, tableName);
	}

	/*
	 * (non-Javadoc)
	 * 
	 * @see org.sagacity.sqltoy.dialect.Dialect#deleteAll(org.sagacity.sqltoy.
	 * SqlToyContext, java.util.List, java.sql.Connection)
	 */
	@Override
	public Long deleteAll(SqlToyContext sqlToyContext, List<?> entities, final int batchSize, Connection conn,
			DBProfile profile, final Boolean autoCommit, final String tableName) throws Exception {
		return DialectUtils.deleteAll(sqlToyContext, entities, batchSize, conn, profile, autoCommit, tableName);
	}

	/*
	 * (non-Javadoc)
	 * 
	 * @see org.sagacity.sqltoy.dialect.Dialect#updateFetch(org.sagacity.sqltoy.
	 * SqlToyContext, org.sagacity.sqltoy.config.model.SqlToyConfig,
	 * java.lang.String, java.lang.Object[],
	 * org.sagacity.core.database.callback.UpdateRowHandler, java.sql.Connection)
	 */
	@Override
	public QueryResult updateFetch(SqlToyContext sqlToyContext, SqlToyConfig sqlToyConfig, String sql,
			Object[] paramValues, UpdateRowHandler updateRowHandler, Connection conn, DBProfile profile,
			final LockMode lockMode, int lockWaitTimeout, final int fetchSize, final int maxRows) throws Exception {
		Integer dbType = profile.getDbType();
		String realSql = sql
				.concat(getLockSql(sql, dbType, (lockMode == null) ? LockMode.UPGRADE : lockMode, lockWaitTimeout));
		return DialectUtils.updateFetchBySql(sqlToyContext, sqlToyConfig, realSql, paramValues, updateRowHandler, conn,
				profile, 0, fetchSize, maxRows);
	}

	@Override
	public StoreResult executeStore(SqlToyContext sqlToyContext, final SqlToyConfig sqlToyConfig, final String sql,
			final Object[] inParamsValue, final Integer[] outParamsType, final boolean moreResult,
			final Connection conn, DBProfile profile, final int fetchSize, final Integer timeout) throws Exception {
		return DialectUtils.executeStore(sqlToyConfig, sqlToyContext, sql, inParamsValue, outParamsType, moreResult,
				conn, profile, fetchSize, timeout);
	}

	@Override
	public List<ColumnMeta> getTableColumns(String catalog, String schema, String tableName, Connection conn,
			DBProfile profile) throws Exception {
		return DefaultDialectUtils.getTableColumns(catalog, schema, tableName, conn, profile);
	}

	@Override
	public List<TableMeta> getTables(String catalog, String schema, String tableName, Connection conn,
			DBProfile profile) throws Exception {
		return PostgreSqlDialectUtils.getTables(catalog, schema, tableName, conn, profile);
	}

	/**
	 * update 2026-9-13 private改protected:子类(Kingbase)覆写wait N形态时,本类findBySql/load等
	 * 继承方法的调用须经虚分派命中子类实现
	 */
	protected String getLockSql(String sql, Integer dbType, LockMode lockMode, int lockWaitTimeout) {
		return DefaultDialectUtils.getLockSql(sql, dbType, lockMode, 0, true, " for update skip locked", false);
	}
}
