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
import org.sagacity.sqltoy.callback.ReflectPropertyHandler;
import org.sagacity.sqltoy.callback.RowCallbackHandler;
import org.sagacity.sqltoy.callback.UpdateRowHandler;
import org.sagacity.sqltoy.config.model.EntityMeta;
import org.sagacity.sqltoy.config.model.PKStrategy;
import org.sagacity.sqltoy.config.model.SqlToyConfig;
import org.sagacity.sqltoy.config.model.SqlToyResult;
import org.sagacity.sqltoy.config.model.SqlType;
import org.sagacity.sqltoy.dialect.Dialect;
import org.sagacity.sqltoy.dialect.handler.GenerateSavePKStrategy;
import org.sagacity.sqltoy.dialect.handler.GenerateSqlHandler;
import org.sagacity.sqltoy.dialect.model.ReturnPkType;
import org.sagacity.sqltoy.dialect.model.SavePKStrategy;
import org.sagacity.sqltoy.dialect.utils.DB2DialectUtils;
import org.sagacity.sqltoy.dialect.utils.DialectUtils;
import org.sagacity.sqltoy.executor.QueryExecutor;
import org.sagacity.sqltoy.model.LockMode;
import org.sagacity.sqltoy.model.QueryResult;
import org.sagacity.sqltoy.model.StoreResult;
import org.sagacity.sqltoy.utils.ReservedWordsUtil;
import org.sagacity.sqltoy.utils.StringUtil;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * @project sqltoy-orm
 * @description db2 10.x版本支持limit offset分页机制
 * @author chenrenfei <a href="mailto:zhongxuchen@gmail.com">联系作者</a>
 * @version id:DB2Dialect.java,Revision:v1.0,Date:2015年2月28日
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
		if (SqlToyConstants.db2WithUR())
			return StringUtil.matches(sql, DB2_QUERY_UR_PATTERN) ? sql : sql + DB2_QUERY_APPEND;
		return sql;
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
	public Serializable load(final SqlToyContext sqlToyContext, Serializable entity, List<Class> cascadeTypes,
			LockMode lockMode, Connection conn, final Integer dbType, final String dialect, final String tableName)
			throws Exception {
		EntityMeta entityMeta = sqlToyContext.getEntityMeta(entity.getClass());
		// 获取loadsql(loadsql 可以通过@loadSql进行改变，所以需要sqltoyContext重新获取)
		SqlToyConfig sqlToyConfig = sqlToyContext.getSqlToyConfig(entityMeta.getLoadSql(tableName), SqlType.search);
		String loadSql = ReservedWordsUtil.convertSql(sqlToyConfig.getSql(dialect), dbType);
		if (lockMode != null) {
			switch (lockMode) {
			case UPGRADE_NOWAIT:
			case UPGRADE:
				loadSql = loadSql + " for update with rs";
				break;
			}
		}
		return (Serializable) DialectUtils.load(sqlToyContext, sqlToyConfig, loadSql, entityMeta, entity, cascadeTypes,
				conn, dbType);
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
	public List<?> loadAll(final SqlToyContext sqlToyContext, List<?> entities, List<Class> cascadeTypes,
			LockMode lockMode, Connection conn, final Integer dbType, final String dialect, final String tableName)
			throws Exception {
		if (null == entities || entities.isEmpty())
			return null;
		EntityMeta entityMeta = sqlToyContext.getEntityMeta(entities.get(0).getClass());
		// 判断是否存在主键
		if (null == entityMeta.getIdArray()) {
			throw new IllegalArgumentException(
					entityMeta.getEntityClass().getName() + "Entity Object hasn't primary key,cann't use load method!");
		}
		StringBuilder loadSql = new StringBuilder();
		loadSql.append("select ").append(ReservedWordsUtil.convertSimpleSql(entityMeta.getAllColumnNames(), dbType));
		loadSql.append(" from ");
		// sharding 分表情况下会传递表名
		loadSql.append(entityMeta.getSchemaTable(tableName));
		loadSql.append(" where ");
		String field;
		for (int i = 0, n = entityMeta.getIdArray().length; i < n; i++) {
			field = entityMeta.getIdArray()[i];
			if (i > 0) {
				loadSql.append(" and ");
			}
			loadSql.append(ReservedWordsUtil.convertWord(entityMeta.getColumnName(field), dbType));
			loadSql.append(" in (:").append(field).append(") ");
		}
		if (lockMode != null) {
			switch (lockMode) {
			case UPGRADE_NOWAIT:
			case UPGRADE:
				loadSql.append(" for update with rs ");
				break;
			}
		}
		return DialectUtils.loadAll(sqlToyContext, loadSql.toString(), entities, cascadeTypes, conn, dbType);
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
		boolean isAssignPK = isAssignPKValue(entityMeta.getIdStrategy());
		// db2 identity 和sequence 都支持手工赋值
		String insertSql = DialectUtils.generateInsertSql(dbType, entityMeta, entityMeta.getIdStrategy(), NVL_FUNCTION,
				"NEXTVAL FOR " + entityMeta.getSequence(), isAssignPK, tableName);
		ReturnPkType returnPkType = (entityMeta.getIdStrategy() != null
				&& entityMeta.getIdStrategy().equals(PKStrategy.SEQUENCE)) ? ReturnPkType.PREPARD_ID
						: ReturnPkType.PREPARD_ID;
		return DialectUtils.save(sqlToyContext, entityMeta, entityMeta.getIdStrategy(),
				isAssignPKValue(entityMeta.getIdStrategy()), returnPkType, insertSql, entity, new GenerateSqlHandler() {
					public String generateSql(EntityMeta entityMeta, String[] forceUpdateField) {
						return DialectUtils.generateInsertSql(dbType, entityMeta, entityMeta.getIdStrategy(),
								NVL_FUNCTION, "NEXTVAL FOR " + entityMeta.getSequence(),
								isAssignPKValue(entityMeta.getIdStrategy()), null);
					}
				}, new GenerateSavePKStrategy() {
					public SavePKStrategy generate(EntityMeta entityMeta) {
						return new SavePKStrategy(entityMeta.getIdStrategy(),
								isAssignPKValue(entityMeta.getIdStrategy()));
					}
				}, conn, dbType);
	}

	/*
	 * (non-Javadoc)
	 * 
	 * @see org.sagacity.sqltoy.dialect.Dialect#saveAll(org.sagacity.sqltoy.
	 * SqlToyContext , java.util.List,
	 * org.sagacity.core.utils.callback.ReflectPropertyHandler, java.sql.Connection)
	 */
	@Override
	public Long saveAll(SqlToyContext sqlToyContext, List<?> entities, final int batchSize,
			ReflectPropertyHandler reflectPropertyHandler, Connection conn, final Integer dbType, final String dialect,
			final Boolean autoCommit, final String tableName) throws Exception {
		EntityMeta entityMeta = sqlToyContext.getEntityMeta(entities.get(0).getClass());
		boolean isAssignPK = isAssignPKValue(entityMeta.getIdStrategy());
		String insertSql = DialectUtils.generateInsertSql(dbType, entityMeta, entityMeta.getIdStrategy(), NVL_FUNCTION,
				"NEXTVAL FOR " + entityMeta.getSequence(), isAssignPK, tableName);
		return DialectUtils.saveAll(sqlToyContext, entityMeta, entityMeta.getIdStrategy(), isAssignPK, insertSql,
				entities, batchSize, reflectPropertyHandler, conn, dbType, autoCommit);
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
	public Long update(SqlToyContext sqlToyContext, Serializable entity, String[] forceUpdateFields,
			final boolean cascade, final Class[] emptyCascadeClasses,
			final HashMap<Class, String[]> subTableForceUpdateProps, Connection conn, final Integer dbType,
			final String dialect, final String tableName) throws Exception {
		return DialectUtils.update(sqlToyContext, entity, NVL_FUNCTION, forceUpdateFields, cascade,
				(cascade == false) ? null : new GenerateSqlHandler() {
					public String generateSql(EntityMeta entityMeta, String[] forceUpdateFields) {
						return DB2DialectUtils.getSaveOrUpdateSql(dbType, entityMeta, entityMeta.getIdStrategy(),
								forceUpdateFields, VIRTUAL_TABLE, NVL_FUNCTION,
								"NEXTVAL FOR " + entityMeta.getSequence(), isAssignPKValue(entityMeta.getIdStrategy()),
								null);
					}
				}, emptyCascadeClasses, subTableForceUpdateProps, conn, dbType, tableName);
	}

	/*
	 * (non-Javadoc)
	 * 
	 * @see org.sagacity.sqltoy.dialect.Dialect#updateAll(org.sagacity.sqltoy.
	 * SqlToyContext, java.util.List, int, java.lang.String[],
	 * org.sagacity.sqltoy.callback.ReflectPropertyHandler, java.sql.Connection,
	 * java.lang.Integer, java.lang.String, java.lang.Boolean, java.lang.String)
	 */
	@Override
	public Long updateAll(SqlToyContext sqlToyContext, List<?> entities, final int batchSize,
			final String[] forceUpdateFields, ReflectPropertyHandler reflectPropertyHandler, Connection conn,
			final Integer dbType, final String dialect, final Boolean autoCommit, final String tableName)
			throws Exception {
		return DialectUtils.updateAll(sqlToyContext, entities, batchSize, forceUpdateFields, reflectPropertyHandler,
				NVL_FUNCTION, conn, dbType, autoCommit, tableName, false);
	}

	/*
	 * (non-Javadoc)
	 * 
	 * @see org.sagacity.sqltoy.dialect.Dialect#getRandomResult(org.sagacity.sqltoy.
	 * SqlToyContext, org.sagacity.sqltoy.config.model.SqlToyConfig,
	 * org.sagacity.sqltoy.executor.QueryExecutor, java.lang.Long, java.lang.Long,
	 * java.sql.Connection, java.lang.Integer, java.lang.String)
	 */
	@Override
	public QueryResult getRandomResult(SqlToyContext sqlToyContext, SqlToyConfig sqlToyConfig,
			QueryExecutor queryExecutor, Long totalCount, Long randomCount, Connection conn, final Integer dbType,
			final String dialect) throws Exception {
		return DB2DialectUtils.getRandomResult(sqlToyContext, sqlToyConfig, queryExecutor, totalCount, randomCount,
				conn, dbType, dialect);
	}

	/*
	 * (non-Javadoc)
	 * 
	 * @see org.sagacity.sqltoy.dialect.Dialect#findPageBySql(org.sagacity.sqltoy.
	 * SqlToyContext, org.sagacity.sqltoy.config.model.SqlToyConfig,
	 * org.sagacity.sqltoy.executor.QueryExecutor, java.lang.Long,
	 * java.lang.Integer, java.sql.Connection, java.lang.Integer, java.lang.String)
	 */
	@Override
	public QueryResult findPageBySql(SqlToyContext sqlToyContext, SqlToyConfig sqlToyConfig,
			QueryExecutor queryExecutor, Long pageNo, Integer pageSize, Connection conn, final Integer dbType,
			final String dialect) throws Exception {
		StringBuilder sql = new StringBuilder();
		boolean isNamed = sqlToyConfig.isNamedParam();
		if (sqlToyConfig.isHasFast()) {
			sql.append(sqlToyConfig.getFastPreSql(dialect));
			sql.append(" (").append(sqlToyConfig.getFastSql(dialect));
		} else {
			sql.append(sqlToyConfig.getSql(dialect));
		}
		sql.append(" limit ");
		sql.append(isNamed ? ":" + SqlToyConstants.PAGE_FIRST_PARAM_NAME : "?");
		sql.append(" offset ");
		sql.append(isNamed ? ":" + SqlToyConstants.PAGE_LAST_PARAM_NAME : "?");
		if (sqlToyConfig.isHasFast()) {
			sql.append(") ").append(sqlToyConfig.getFastTailSql(dialect));
		}

		SqlToyResult queryParam = DialectUtils.wrapPageSqlParams(sqlToyContext, sqlToyConfig, queryExecutor,
				sql.toString(), Long.valueOf(pageSize), (pageNo - 1) * pageSize);
		return findBySql(sqlToyContext, sqlToyConfig, queryParam.getSql(), queryParam.getParamsValue(),
				queryExecutor.getRowCallbackHandler(), conn, null, dbType, dialect, queryExecutor.getFetchSize(),
				queryExecutor.getMaxRows());
	}

	/*
	 * (non-Javadoc)
	 * 
	 * @see org.sagacity.sqltoy.dialect.Dialect#findTopBySql(org.sagacity.sqltoy.
	 * SqlToyContext, org.sagacity.sqltoy.config.model.SqlToyConfig,
	 * org.sagacity.sqltoy.executor.QueryExecutor, java.lang.Integer,
	 * java.sql.Connection, java.lang.Integer, java.lang.String)
	 */
	@Override
	public QueryResult findTopBySql(SqlToyContext sqlToyContext, SqlToyConfig sqlToyConfig, QueryExecutor queryExecutor,
			Integer topSize, Connection conn, final Integer dbType, final String dialect) throws Exception {
		StringBuilder sql = new StringBuilder();
		if (sqlToyConfig.isHasFast()) {
			sql.append(sqlToyConfig.getFastPreSql(dialect));
			sql.append(" (").append(sqlToyConfig.getFastSql(dialect));
		} else {
			sql.append(sqlToyConfig.getSql(dialect));
		}
		sql.append(" limit ");
		sql.append(topSize);

		if (sqlToyConfig.isHasFast()) {
			sql.append(") ").append(sqlToyConfig.getFastTailSql(dialect));
		}

		SqlToyResult queryParam = DialectUtils.wrapPageSqlParams(sqlToyContext, sqlToyConfig, queryExecutor,
				sql.toString(), null, null);
		return findBySql(sqlToyContext, sqlToyConfig, queryParam.getSql(), queryParam.getParamsValue(),
				queryExecutor.getRowCallbackHandler(), conn, null, dbType, dialect, queryExecutor.getFetchSize(),
				queryExecutor.getMaxRows());
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
	public QueryResult findBySql(final SqlToyContext sqlToyContext, final SqlToyConfig sqlToyConfig, final String sql,
			final Object[] paramsValue, final RowCallbackHandler rowCallbackHandler, final Connection conn,
			final LockMode lockMode, final Integer dbType, final String dialect, final int fetchSize, final int maxRows)
			throws Exception {
		String realSql;
		// db2 锁记录
		if (lockMode != null) {
			realSql = sql;
			switch (lockMode) {
			case UPGRADE_NOWAIT:
			case UPGRADE:
				realSql = realSql.concat(" for update with rs");
				break;
			}
		} else {
			realSql = appendWithUR(sql);
		}

		return DialectUtils.findBySql(sqlToyContext, sqlToyConfig, realSql, paramsValue, rowCallbackHandler, conn,
				dbType, 0, fetchSize, maxRows);
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
	 * org.sagacity.sqltoy.callback.ReflectPropertyHandler, java.lang.String[],
	 * java.sql.Connection, java.lang.Integer, java.lang.String, java.lang.Boolean,
	 * java.lang.String)
	 */
	@Override
	public Long saveOrUpdateAll(SqlToyContext sqlToyContext, List<?> entities, final int batchSize,
			final ReflectPropertyHandler reflectPropertyHandler, final String[] forceUpdateFields, Connection conn,
			final Integer dbType, final String dialect, final Boolean autoCommit, final String tableName)
			throws Exception {
		EntityMeta entityMeta = sqlToyContext.getEntityMeta(entities.get(0).getClass());
		return DialectUtils.saveOrUpdateAll(sqlToyContext, entities, batchSize, entityMeta, forceUpdateFields,
				new GenerateSqlHandler() {
					public String generateSql(EntityMeta entityMeta, String[] forceUpdateFields) {
						return DB2DialectUtils.getSaveOrUpdateSql(dbType, entityMeta, entityMeta.getIdStrategy(),
								forceUpdateFields, VIRTUAL_TABLE, NVL_FUNCTION,
								"NEXTVAL FOR " + entityMeta.getSequence(), isAssignPKValue(entityMeta.getIdStrategy()),
								tableName);
					}
				}, reflectPropertyHandler, conn, dbType, autoCommit);
	}

	/*
	 * (non-Javadoc)
	 * 
	 * @see
	 * org.sagacity.sqltoy.dialect.Dialect#saveAllIgnoreExist(org.sagacity.sqltoy.
	 * SqlToyContext, java.util.List, int,
	 * org.sagacity.sqltoy.callback.ReflectPropertyHandler, java.sql.Connection,
	 * java.lang.Integer, java.lang.String, java.lang.Boolean, java.lang.String)
	 */
	@Override
	public Long saveAllIgnoreExist(SqlToyContext sqlToyContext, List<?> entities, final int batchSize,
			ReflectPropertyHandler reflectPropertyHandler, Connection conn, final Integer dbType, final String dialect,
			final Boolean autoCommit, final String tableName) throws Exception {
		EntityMeta entityMeta = sqlToyContext.getEntityMeta(entities.get(0).getClass());
		return DialectUtils.saveAllIgnoreExist(sqlToyContext, entities, batchSize, entityMeta,
				new GenerateSqlHandler() {
					public String generateSql(EntityMeta entityMeta, String[] forceUpdateFields) {
						return DB2DialectUtils.getSaveIgnoreExistSql(dbType, entityMeta, entityMeta.getIdStrategy(),
								VIRTUAL_TABLE, NVL_FUNCTION, "NEXTVAL FOR " + entityMeta.getSequence(),
								isAssignPKValue(entityMeta.getIdStrategy()), tableName);
					}
				}, reflectPropertyHandler, conn, dbType, autoCommit);

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
			final String dialect) throws Exception {
		String realSql = sql + " for update with rs";
		return DialectUtils.updateFetchBySql(sqlToyContext, sqlToyConfig, realSql, paramsValue, updateRowHandler, conn,
				dbType, 0);
	}

	/*
	 * (non-Javadoc)
	 * 
	 * @see org.sagacity.sqltoy.dialect.Dialect#updateFetchTop(org.sagacity.sqltoy.
	 * SqlToyContext, org.sagacity.sqltoy.config.model.SqlToyConfig,
	 * java.lang.String, java.lang.Object[], java.lang.Integer,
	 * org.sagacity.sqltoy.callback.UpdateRowHandler, java.sql.Connection,
	 * java.lang.Integer, java.lang.String)
	 */
	@Override
	public QueryResult updateFetchTop(SqlToyContext sqlToyContext, SqlToyConfig sqlToyConfig, String sql,
			Object[] paramsValue, Integer topSize, UpdateRowHandler updateRowHandler, Connection conn,
			final Integer dbType, final String dialect) throws Exception {
		String realSql = sql + " fetch first " + topSize + " rows only for update with rs";
		return DialectUtils.updateFetchBySql(sqlToyContext, sqlToyConfig, realSql, paramsValue, updateRowHandler, conn,
				dbType, 0);
	}

	/*
	 * (non-Javadoc)
	 * 
	 * @see
	 * org.sagacity.sqltoy.dialect.Dialect#updateFetchRandom(org.sagacity.sqltoy.
	 * SqlToyContext, org.sagacity.sqltoy.config.model.SqlToyConfig,
	 * java.lang.String, java.lang.Object[], java.lang.Integer,
	 * org.sagacity.sqltoy.callback.UpdateRowHandler, java.sql.Connection,
	 * java.lang.Integer, java.lang.String)
	 */
	@Override
	public QueryResult updateFetchRandom(SqlToyContext sqlToyContext, SqlToyConfig sqlToyConfig, String sql,
			Object[] paramsValue, Integer random, UpdateRowHandler updateRowHandler, Connection conn,
			final Integer dbType, final String dialect) throws Exception {
		String realSql = sql + " order by rand() fetch first " + random + " rows only for update with rs";
		return DialectUtils.updateFetchBySql(sqlToyContext, sqlToyConfig, realSql, paramsValue, updateRowHandler, conn,
				dbType, 0);
	}

	/*
	 * (non-Javadoc)
	 * 
	 * @see org.sagacity.sqltoy.dialect.Dialect#executeStore(org.sagacity.sqltoy.
	 * SqlToyContext, org.sagacity.sqltoy.config.model.SqlToyConfig,
	 * java.lang.String, java.lang.Object[], java.lang.Integer[],
	 * java.sql.Connection, java.lang.Integer, java.lang.String)
	 */
	@Override
	public StoreResult executeStore(SqlToyContext sqlToyContext, final SqlToyConfig sqlToyConfig, final String sql,
			final Object[] inParamsValue, final Integer[] outParamsType, final Connection conn, final Integer dbType,
			final String dialect) throws Exception {
		return DialectUtils.executeStore(sqlToyConfig, sqlToyContext, sql, inParamsValue, outParamsType, conn, dbType);
	}

	private boolean isAssignPKValue(PKStrategy pkStrategy) {
		if (pkStrategy == null)
			return true;
		if (pkStrategy.equals(PKStrategy.SEQUENCE))
			return true;
		if (pkStrategy.equals(PKStrategy.IDENTITY))
			return false;
		return true;
	}
}
