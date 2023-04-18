/**
 * 
 */
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
import org.sagacity.sqltoy.config.model.SqlToyConfig;
import org.sagacity.sqltoy.model.LockMode;
import org.sagacity.sqltoy.model.QueryExecutor;
import org.sagacity.sqltoy.model.QueryResult;
import org.sagacity.sqltoy.model.StoreResult;
import org.sagacity.sqltoy.model.inner.QueryExecutorExtend;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * @project sagacity-sqltoy
 * @description TDengine Iot物联网时序数据库支持(待实现)
 * @author zhongxuchen
 * @version v1.0, Date:2022年9月13日
 * @modify 2022年9月13日,修改说明
 */
@SuppressWarnings({ "rawtypes" })
public class TDengineDialect extends DefaultDialect {

	/**
	 * 定义日志
	 */
	protected final Logger logger = LoggerFactory.getLogger(TDengineDialect.class);

	@Override
	public boolean isUnique(SqlToyContext sqlToyContext, Serializable entity, String[] paramsNamed, Connection conn,
			Integer dbType, String tableName) {
		// 不支持
		throw new UnsupportedOperationException(SqlToyConstants.UN_SUPPORT_MESSAGE);
	}

	@Override
	public QueryResult getRandomResult(SqlToyContext sqlToyContext, SqlToyConfig sqlToyConfig,
			QueryExecutor queryExecutor, DecryptHandler decryptHandler, Long totalCount, Long randomCount,
			Connection conn, Integer dbType, String dialect, int fetchSize, int maxRows) throws Exception {
		return super.getRandomResult(sqlToyContext, sqlToyConfig, queryExecutor, decryptHandler, totalCount,
				randomCount, conn, dbType, dialect, fetchSize, maxRows);
	}

	@Override
	public QueryResult findPageBySql(SqlToyContext sqlToyContext, SqlToyConfig sqlToyConfig,
			QueryExecutor queryExecutor, DecryptHandler decryptHandler, Long pageNo, Integer pageSize, Connection conn,
			Integer dbType, String dialect, int fetchSize, int maxRows) throws Exception {
		return super.findPageBySql(sqlToyContext, sqlToyConfig, queryExecutor, decryptHandler, pageNo, pageSize, conn,
				dbType, dialect, fetchSize, maxRows);
	}

	@Override
	public QueryResult findTopBySql(SqlToyContext sqlToyContext, SqlToyConfig sqlToyConfig, QueryExecutor queryExecutor,
			DecryptHandler decryptHandler, Integer topSize, Connection conn, Integer dbType, String dialect,
			int fetchSize, int maxRows) throws Exception {
		return super.findTopBySql(sqlToyContext, sqlToyConfig, queryExecutor, decryptHandler, topSize, conn, dbType,
				dialect, fetchSize, maxRows);
	}

	@Override
	public QueryResult findBySql(SqlToyContext sqlToyContext, SqlToyConfig sqlToyConfig, String sql,
			Object[] paramsValue, QueryExecutorExtend queryExecutorExtend, DecryptHandler decryptHandler,
			Connection conn, LockMode lockMode, Integer dbType, String dialect, int fetchSize, int maxRows)
			throws Exception {
		return super.findBySql(sqlToyContext, sqlToyConfig, sql, paramsValue, queryExecutorExtend, decryptHandler, conn,
				lockMode, dbType, dialect, fetchSize, maxRows);
	}

	@Override
	public Long getCountBySql(SqlToyContext sqlToyContext, SqlToyConfig sqlToyConfig, String sql, Object[] paramsValue,
			boolean isLastSql, Connection conn, Integer dbType, String dialect) throws Exception {
		return super.getCountBySql(sqlToyContext, sqlToyConfig, sql, paramsValue, isLastSql, conn, dbType, dialect);
	}

	@Override
	public Serializable load(SqlToyContext sqlToyContext, Serializable entity, List<Class> cascadeTypes,
			LockMode lockMode, Connection conn, Integer dbType, String dialect, String tableName) throws Exception {
		return super.load(sqlToyContext, entity, cascadeTypes, lockMode, conn, dbType, dialect, tableName);
	}

	@Override
	public List<?> loadAll(SqlToyContext sqlToyContext, List<?> entities, List<Class> cascadeTypes, LockMode lockMode,
			Connection conn, Integer dbType, String dialect, String tableName, int fetchSize, int maxRows)
			throws Exception {
		return super.loadAll(sqlToyContext, entities, cascadeTypes, lockMode, conn, dbType, dialect, tableName,
				fetchSize, maxRows);
	}

	@Override
	public Object save(SqlToyContext sqlToyContext, Serializable entity, Connection conn, Integer dbType,
			String dialect, String tableName) throws Exception {
		return super.save(sqlToyContext, entity, conn, dbType, dialect, tableName);
	}

	@Override
	public Long saveAll(SqlToyContext sqlToyContext, List<?> entities, int batchSize,
			ReflectPropsHandler reflectPropsHandler, Connection conn, Integer dbType, String dialect,
			Boolean autoCommit, String tableName) throws Exception {
		return super.saveAll(sqlToyContext, entities, batchSize, reflectPropsHandler, conn, dbType, dialect, autoCommit,
				tableName);
	}

	@Override
	public Long update(SqlToyContext sqlToyContext, Serializable entity, String[] forceUpdateFields, boolean cascade,
			Class[] forceCascadeClass, HashMap<Class, String[]> subTableForceUpdateProps, Connection conn,
			Integer dbType, String dialect, String tableName) throws Exception {
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
	public Long updateAll(SqlToyContext sqlToyContext, List<?> entities, int batchSize, String[] uniqueFields,
			String[] forceUpdateFields, ReflectPropsHandler reflectPropsHandler, Connection conn, Integer dbType,
			String dialect, Boolean autoCommit, String tableName) throws Exception {
		// 不支持
		throw new UnsupportedOperationException(SqlToyConstants.UN_SUPPORT_MESSAGE);
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
	public Long saveAllIgnoreExist(SqlToyContext sqlToyContext, List<?> entities, int batchSize,
			ReflectPropsHandler reflectPropsHandler, Connection conn, Integer dbType, String dialect,
			Boolean autoCommit, String tableName) throws Exception {
		// 不支持
		throw new UnsupportedOperationException(SqlToyConstants.UN_SUPPORT_MESSAGE);
	}

	@Override
	public Long delete(SqlToyContext sqlToyContext, Serializable entity, Connection conn, Integer dbType,
			String dialect, String tableName) throws Exception {
		// 不支持
		throw new UnsupportedOperationException(SqlToyConstants.UN_SUPPORT_MESSAGE);
	}

	@Override
	public Long deleteAll(SqlToyContext sqlToyContext, List<?> entities, int batchSize, Connection conn, Integer dbType,
			String dialect, Boolean autoCommit, String tableName) throws Exception {
		// 不支持
		throw new UnsupportedOperationException(SqlToyConstants.UN_SUPPORT_MESSAGE);
	}

	@Override
	public QueryResult updateFetch(SqlToyContext sqlToyContext, SqlToyConfig sqlToyConfig, String sql,
			Object[] paramValues, UpdateRowHandler updateRowHandler, Connection conn, Integer dbType, String dialect,
			LockMode lockMode, int fetchSize, int maxRows) throws Exception {
		// 不支持
		throw new UnsupportedOperationException(SqlToyConstants.UN_SUPPORT_MESSAGE);
	}

	@Override
	public StoreResult executeStore(SqlToyContext sqlToyContext, SqlToyConfig sqlToyConfig, String sql,
			Object[] inParamsValue, Integer[] outParamsType, Connection conn, Integer dbType, String dialect,
			int fetchSize) throws Exception {
		// 不支持
		throw new UnsupportedOperationException(SqlToyConstants.UN_SUPPORT_MESSAGE);
	}

}
