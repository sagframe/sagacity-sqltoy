/**
 * 
 */
package org.sagacity.sqltoy.dialect.impl;

import java.io.Serializable;
import java.sql.Connection;
import java.util.HashMap;
import java.util.List;

import org.sagacity.sqltoy.SqlToyContext;
import org.sagacity.sqltoy.callback.ReflectPropertyHandler;
import org.sagacity.sqltoy.callback.RowCallbackHandler;
import org.sagacity.sqltoy.callback.UpdateRowHandler;
import org.sagacity.sqltoy.config.model.SqlToyConfig;
import org.sagacity.sqltoy.dialect.Dialect;
import org.sagacity.sqltoy.executor.QueryExecutor;
import org.sagacity.sqltoy.model.LockMode;
import org.sagacity.sqltoy.model.QueryResult;
import org.sagacity.sqltoy.model.StoreResult;

/**
 * @project sagacity-sqltoy
 * @description 请在此说明类的功能
 * @author zhong
 * @version v1.0, Date:2019年9月11日
 * @modify 2019年9月11日,修改说明
 */
public class HiveDialect implements Dialect {

	/* (non-Javadoc)
	 * @see org.sagacity.sqltoy.dialect.Dialect#getRandomResult(org.sagacity.sqltoy.SqlToyContext, org.sagacity.sqltoy.config.model.SqlToyConfig, org.sagacity.sqltoy.executor.QueryExecutor, java.lang.Long, java.lang.Long, java.sql.Connection, java.lang.Integer)
	 */
	@Override
	public QueryResult getRandomResult(SqlToyContext sqlToyContext, SqlToyConfig sqlToyConfig,
			QueryExecutor queryExecutor, Long totalCount, Long randomCount, Connection conn, Integer dbType)
			throws Exception {
		// TODO Auto-generated method stub
		return null;
	}

	/* (non-Javadoc)
	 * @see org.sagacity.sqltoy.dialect.Dialect#findPageBySql(org.sagacity.sqltoy.SqlToyContext, org.sagacity.sqltoy.config.model.SqlToyConfig, org.sagacity.sqltoy.executor.QueryExecutor, java.lang.Long, java.lang.Integer, java.sql.Connection, java.lang.Integer)
	 */
	@Override
	public QueryResult findPageBySql(SqlToyContext sqlToyContext, SqlToyConfig sqlToyConfig,
			QueryExecutor queryExecutor, Long pageNo, Integer pageSize, Connection conn, Integer dbType)
			throws Exception {
		// TODO Auto-generated method stub
		return null;
	}

	/* (non-Javadoc)
	 * @see org.sagacity.sqltoy.dialect.Dialect#findTopBySql(org.sagacity.sqltoy.SqlToyContext, org.sagacity.sqltoy.config.model.SqlToyConfig, org.sagacity.sqltoy.executor.QueryExecutor, java.lang.Integer, java.sql.Connection, java.lang.Integer)
	 */
	@Override
	public QueryResult findTopBySql(SqlToyContext sqlToyContext, SqlToyConfig sqlToyConfig, QueryExecutor queryExecutor,
			Integer topSize, Connection conn, Integer dbType) throws Exception {
		// TODO Auto-generated method stub
		return null;
	}

	/* (non-Javadoc)
	 * @see org.sagacity.sqltoy.dialect.Dialect#findBySql(org.sagacity.sqltoy.SqlToyContext, org.sagacity.sqltoy.config.model.SqlToyConfig, java.lang.String, java.lang.Object[], org.sagacity.sqltoy.callback.RowCallbackHandler, java.sql.Connection, java.lang.Integer, int, int)
	 */
	@Override
	public QueryResult findBySql(SqlToyContext sqlToyContext, SqlToyConfig sqlToyConfig, String sql,
			Object[] paramsValue, RowCallbackHandler rowCallbackHandler, Connection conn, Integer dbType, int fetchSize,
			int maxRows) throws Exception {
		// TODO Auto-generated method stub
		return null;
	}

	/* (non-Javadoc)
	 * @see org.sagacity.sqltoy.dialect.Dialect#getCountBySql(org.sagacity.sqltoy.SqlToyContext, org.sagacity.sqltoy.config.model.SqlToyConfig, java.lang.String, java.lang.Object[], boolean, java.sql.Connection, java.lang.Integer)
	 */
	@Override
	public Long getCountBySql(SqlToyContext sqlToyContext, SqlToyConfig sqlToyConfig, String sql, Object[] paramsValue,
			boolean isLastSql, Connection conn, Integer dbType) throws Exception {
		// TODO Auto-generated method stub
		return null;
	}

	/* (non-Javadoc)
	 * @see org.sagacity.sqltoy.dialect.Dialect#load(org.sagacity.sqltoy.SqlToyContext, java.io.Serializable, java.util.List, org.sagacity.sqltoy.model.LockMode, java.sql.Connection, java.lang.Integer, java.lang.String)
	 */
	@Override
	public Serializable load(SqlToyContext sqlToyContext, Serializable entity, List<Class> cascadeTypes,
			LockMode lockMode, Connection conn, Integer dbType, String tableName) throws Exception {
		// TODO Auto-generated method stub
		return null;
	}

	/* (non-Javadoc)
	 * @see org.sagacity.sqltoy.dialect.Dialect#loadAll(org.sagacity.sqltoy.SqlToyContext, java.util.List, java.util.List, org.sagacity.sqltoy.model.LockMode, java.sql.Connection, java.lang.Integer, java.lang.String)
	 */
	@Override
	public List<?> loadAll(SqlToyContext sqlToyContext, List<?> entities, List<Class> cascadeTypes, LockMode lockMode,
			Connection conn, Integer dbType, String tableName) throws Exception {
		// TODO Auto-generated method stub
		return null;
	}

	/* (non-Javadoc)
	 * @see org.sagacity.sqltoy.dialect.Dialect#save(org.sagacity.sqltoy.SqlToyContext, java.io.Serializable, java.sql.Connection, java.lang.Integer, java.lang.String)
	 */
	@Override
	public Object save(SqlToyContext sqlToyContext, Serializable entity, Connection conn, Integer dbType,
			String tableName) throws Exception {
		// TODO Auto-generated method stub
		return null;
	}

	/* (non-Javadoc)
	 * @see org.sagacity.sqltoy.dialect.Dialect#saveAll(org.sagacity.sqltoy.SqlToyContext, java.util.List, int, org.sagacity.sqltoy.callback.ReflectPropertyHandler, java.sql.Connection, java.lang.Integer, java.lang.Boolean, java.lang.String)
	 */
	@Override
	public Long saveAll(SqlToyContext sqlToyContext, List<?> entities, int batchSize,
			ReflectPropertyHandler reflectPropertyHandler, Connection conn, Integer dbType, Boolean autoCommit,
			String tableName) throws Exception {
		// TODO Auto-generated method stub
		return null;
	}

	/* (non-Javadoc)
	 * @see org.sagacity.sqltoy.dialect.Dialect#update(org.sagacity.sqltoy.SqlToyContext, java.io.Serializable, java.lang.String[], boolean, java.lang.Class[], java.util.HashMap, java.sql.Connection, java.lang.Integer, java.lang.String)
	 */
	@Override
	public Long update(SqlToyContext sqlToyContext, Serializable entity, String[] forceUpdateFields, boolean cascade,
			Class[] forceCascadeClass, HashMap<Class, String[]> subTableForceUpdateProps, Connection conn,
			Integer dbType, String tableName) throws Exception {
		// TODO Auto-generated method stub
		return null;
	}

	/* (non-Javadoc)
	 * @see org.sagacity.sqltoy.dialect.Dialect#updateAll(org.sagacity.sqltoy.SqlToyContext, java.util.List, int, java.lang.String[], org.sagacity.sqltoy.callback.ReflectPropertyHandler, java.sql.Connection, java.lang.Integer, java.lang.Boolean, java.lang.String)
	 */
	@Override
	public Long updateAll(SqlToyContext sqlToyContext, List<?> entities, int batchSize, String[] forceUpdateFields,
			ReflectPropertyHandler reflectPropertyHandler, Connection conn, Integer dbType, Boolean autoCommit,
			String tableName) throws Exception {
		// TODO Auto-generated method stub
		return null;
	}

	/* (non-Javadoc)
	 * @see org.sagacity.sqltoy.dialect.Dialect#saveOrUpdate(org.sagacity.sqltoy.SqlToyContext, java.io.Serializable, java.lang.String[], java.sql.Connection, java.lang.Integer, java.lang.Boolean, java.lang.String)
	 */
	@Override
	public Long saveOrUpdate(SqlToyContext sqlToyContext, Serializable entity, String[] forceUpdateFields,
			Connection conn, Integer dbType, Boolean autoCommit, String tableName) throws Exception {
		// TODO Auto-generated method stub
		return null;
	}

	/* (non-Javadoc)
	 * @see org.sagacity.sqltoy.dialect.Dialect#saveOrUpdateAll(org.sagacity.sqltoy.SqlToyContext, java.util.List, int, org.sagacity.sqltoy.callback.ReflectPropertyHandler, java.lang.String[], java.sql.Connection, java.lang.Integer, java.lang.Boolean, java.lang.String)
	 */
	@Override
	public Long saveOrUpdateAll(SqlToyContext sqlToyContext, List<?> entities, int batchSize,
			ReflectPropertyHandler reflectPropertyHandler, String[] forceUpdateFields, Connection conn, Integer dbType,
			Boolean autoCommit, String tableName) throws Exception {
		// TODO Auto-generated method stub
		return null;
	}

	/* (non-Javadoc)
	 * @see org.sagacity.sqltoy.dialect.Dialect#saveAllIgnoreExist(org.sagacity.sqltoy.SqlToyContext, java.util.List, int, org.sagacity.sqltoy.callback.ReflectPropertyHandler, java.sql.Connection, java.lang.Integer, java.lang.Boolean, java.lang.String)
	 */
	@Override
	public Long saveAllIgnoreExist(SqlToyContext sqlToyContext, List<?> entities, int batchSize,
			ReflectPropertyHandler reflectPropertyHandler, Connection conn, Integer dbType, Boolean autoCommit,
			String tableName) throws Exception {
		// TODO Auto-generated method stub
		return null;
	}

	/* (non-Javadoc)
	 * @see org.sagacity.sqltoy.dialect.Dialect#delete(org.sagacity.sqltoy.SqlToyContext, java.io.Serializable, java.sql.Connection, java.lang.Integer, java.lang.String)
	 */
	@Override
	public Long delete(SqlToyContext sqlToyContext, Serializable entity, Connection conn, Integer dbType,
			String tableName) throws Exception {
		// TODO Auto-generated method stub
		return null;
	}

	/* (non-Javadoc)
	 * @see org.sagacity.sqltoy.dialect.Dialect#deleteAll(org.sagacity.sqltoy.SqlToyContext, java.util.List, int, java.sql.Connection, java.lang.Integer, java.lang.Boolean, java.lang.String)
	 */
	@Override
	public Long deleteAll(SqlToyContext sqlToyContext, List<?> entities, int batchSize, Connection conn, Integer dbType,
			Boolean autoCommit, String tableName) throws Exception {
		// TODO Auto-generated method stub
		return null;
	}

	/* (non-Javadoc)
	 * @see org.sagacity.sqltoy.dialect.Dialect#updateFetch(org.sagacity.sqltoy.SqlToyContext, org.sagacity.sqltoy.config.model.SqlToyConfig, java.lang.String, java.lang.Object[], org.sagacity.sqltoy.callback.UpdateRowHandler, java.sql.Connection, java.lang.Integer)
	 */
	@Override
	public QueryResult updateFetch(SqlToyContext sqlToyContext, SqlToyConfig sqlToyConfig, String sql,
			Object[] paramValues, UpdateRowHandler updateRowHandler, Connection conn, Integer dbType) throws Exception {
		// TODO Auto-generated method stub
		return null;
	}

	/* (non-Javadoc)
	 * @see org.sagacity.sqltoy.dialect.Dialect#updateFetchTop(org.sagacity.sqltoy.SqlToyContext, org.sagacity.sqltoy.config.model.SqlToyConfig, java.lang.String, java.lang.Object[], java.lang.Integer, org.sagacity.sqltoy.callback.UpdateRowHandler, java.sql.Connection, java.lang.Integer)
	 */
	@Override
	public QueryResult updateFetchTop(SqlToyContext sqlToyContext, SqlToyConfig sqlToyConfig, String sql,
			Object[] paramsValue, Integer topSize, UpdateRowHandler updateRowHandler, Connection conn, Integer dbType)
			throws Exception {
		// TODO Auto-generated method stub
		return null;
	}

	/* (non-Javadoc)
	 * @see org.sagacity.sqltoy.dialect.Dialect#updateFetchRandom(org.sagacity.sqltoy.SqlToyContext, org.sagacity.sqltoy.config.model.SqlToyConfig, java.lang.String, java.lang.Object[], java.lang.Integer, org.sagacity.sqltoy.callback.UpdateRowHandler, java.sql.Connection, java.lang.Integer)
	 */
	@Override
	public QueryResult updateFetchRandom(SqlToyContext sqlToyContext, SqlToyConfig sqlToyConfig, String sql,
			Object[] paramsValue, Integer random, UpdateRowHandler updateRowHandler, Connection conn, Integer dbType)
			throws Exception {
		// TODO Auto-generated method stub
		return null;
	}

	/* (non-Javadoc)
	 * @see org.sagacity.sqltoy.dialect.Dialect#executeStore(org.sagacity.sqltoy.SqlToyContext, org.sagacity.sqltoy.config.model.SqlToyConfig, java.lang.String, java.lang.Object[], java.lang.Integer[], java.sql.Connection, java.lang.Integer)
	 */
	@Override
	public StoreResult executeStore(SqlToyContext sqlToyContext, SqlToyConfig sqlToyConfig, String sql,
			Object[] inParamsValue, Integer[] outParamsType, Connection conn, Integer dbType) throws Exception {
		// TODO Auto-generated method stub
		return null;
	}

}
