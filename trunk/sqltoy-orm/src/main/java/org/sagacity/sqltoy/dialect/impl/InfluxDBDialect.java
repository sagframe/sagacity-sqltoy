package org.sagacity.sqltoy.dialect.impl;

import java.io.Serializable;
import java.sql.Connection;
import java.util.HashMap;
import java.util.List;

import org.sagacity.sqltoy.SqlToyContext;
import org.sagacity.sqltoy.callback.DecryptHandler;
import org.sagacity.sqltoy.callback.ReflectPropsHandler;
import org.sagacity.sqltoy.callback.RowCallbackHandler;
import org.sagacity.sqltoy.callback.UpdateRowHandler;
import org.sagacity.sqltoy.config.model.SqlToyConfig;
import org.sagacity.sqltoy.dialect.Dialect;
import org.sagacity.sqltoy.model.ColumnMeta;
import org.sagacity.sqltoy.model.LockMode;
import org.sagacity.sqltoy.model.QueryExecutor;
import org.sagacity.sqltoy.model.QueryResult;
import org.sagacity.sqltoy.model.StoreResult;
import org.sagacity.sqltoy.model.TableMeta;

/**
 * @project sqltoy-orm
 * @description 支持InfluxDB时序数据库
 * @author zhongxuchen
 * @version v1.0,Date:2022-8-20
 */
public class InfluxDBDialect implements Dialect {

	@Override
	public boolean isUnique(SqlToyContext sqlToyContext, Serializable entity, String[] paramsNamed, Connection conn,
			Integer dbType, String tableName) {
		// TODO Auto-generated method stub
		return false;
	}

	@Override
	public QueryResult getRandomResult(SqlToyContext sqlToyContext, SqlToyConfig sqlToyConfig,
			QueryExecutor queryExecutor, DecryptHandler decryptHandler, Long totalCount, Long randomCount,
			Connection conn, Integer dbType, String dialect, int fetchSize, int maxRows) throws Exception {
		// TODO Auto-generated method stub
		return null;
	}

	@Override
	public QueryResult findPageBySql(SqlToyContext sqlToyContext, SqlToyConfig sqlToyConfig,
			QueryExecutor queryExecutor, DecryptHandler decryptHandler, Long pageNo, Integer pageSize, Connection conn,
			Integer dbType, String dialect, int fetchSize, int maxRows) throws Exception {
		// TODO Auto-generated method stub
		return null;
	}

	@Override
	public QueryResult findTopBySql(SqlToyContext sqlToyContext, SqlToyConfig sqlToyConfig, QueryExecutor queryExecutor,
			DecryptHandler decryptHandler, Integer topSize, Connection conn, Integer dbType, String dialect,
			int fetchSize, int maxRows) throws Exception {
		// TODO Auto-generated method stub
		return null;
	}

	@Override
	public QueryResult findBySql(SqlToyContext sqlToyContext, SqlToyConfig sqlToyConfig, String sql,
			Object[] paramsValue, RowCallbackHandler rowCallbackHandler, DecryptHandler decryptHandler, Connection conn,
			LockMode lockMode, Integer dbType, String dialect, int fetchSize, int maxRows) throws Exception {
		// TODO Auto-generated method stub
		return null;
	}

	@Override
	public Long getCountBySql(SqlToyContext sqlToyContext, SqlToyConfig sqlToyConfig, String sql, Object[] paramsValue,
			boolean isLastSql, Connection conn, Integer dbType, String dialect) throws Exception {
		// TODO Auto-generated method stub
		return null;
	}

	@Override
	public Serializable load(SqlToyContext sqlToyContext, Serializable entity, List<Class> cascadeTypes,
			LockMode lockMode, Connection conn, Integer dbType, String dialect, String tableName) throws Exception {
		// TODO Auto-generated method stub
		return null;
	}

	@Override
	public List<?> loadAll(SqlToyContext sqlToyContext, List<?> entities, List<Class> cascadeTypes, LockMode lockMode,
			Connection conn, Integer dbType, String dialect, String tableName, int fetchSize, int maxRows)
			throws Exception {
		// TODO Auto-generated method stub
		return null;
	}

	@Override
	public Object save(SqlToyContext sqlToyContext, Serializable entity, Connection conn, Integer dbType,
			String dialect, String tableName) throws Exception {
		// TODO Auto-generated method stub
		return null;
	}

	@Override
	public Long saveAll(SqlToyContext sqlToyContext, List<?> entities, int batchSize,
			ReflectPropsHandler reflectPropsHandler, Connection conn, Integer dbType, String dialect,
			Boolean autoCommit, String tableName) throws Exception {
		// TODO Auto-generated method stub
		return null;
	}

	@Override
	public Long update(SqlToyContext sqlToyContext, Serializable entity, String[] forceUpdateFields, boolean cascade,
			Class[] forceCascadeClass, HashMap<Class, String[]> subTableForceUpdateProps, Connection conn,
			Integer dbType, String dialect, String tableName) throws Exception {
		// TODO Auto-generated method stub
		return null;
	}

	@Override
	public Serializable updateSaveFetch(SqlToyContext sqlToyContext, Serializable entity,
			UpdateRowHandler updateRowHandler, String[] uniqueProps, Connection conn, Integer dbType, String dialect,
			String tableName) throws Exception {
		// TODO Auto-generated method stub
		return null;
	}

	@Override
	public Long updateAll(SqlToyContext sqlToyContext, List<?> entities, int batchSize, String[] uniqueFields,
			String[] forceUpdateFields, ReflectPropsHandler reflectPropsHandler, Connection conn, Integer dbType,
			String dialect, Boolean autoCommit, String tableName) throws Exception {
		// TODO Auto-generated method stub
		return null;
	}

	@Override
	public Long saveOrUpdate(SqlToyContext sqlToyContext, Serializable entity, String[] forceUpdateFields,
			Connection conn, Integer dbType, String dialect, Boolean autoCommit, String tableName) throws Exception {
		// TODO Auto-generated method stub
		return null;
	}

	@Override
	public Long saveOrUpdateAll(SqlToyContext sqlToyContext, List<?> entities, int batchSize,
			ReflectPropsHandler reflectPropsHandler, String[] forceUpdateFields, Connection conn, Integer dbType,
			String dialect, Boolean autoCommit, String tableName) throws Exception {
		// TODO Auto-generated method stub
		return null;
	}

	@Override
	public Long saveAllIgnoreExist(SqlToyContext sqlToyContext, List<?> entities, int batchSize,
			ReflectPropsHandler reflectPropsHandler, Connection conn, Integer dbType, String dialect,
			Boolean autoCommit, String tableName) throws Exception {
		// TODO Auto-generated method stub
		return null;
	}

	@Override
	public Long delete(SqlToyContext sqlToyContext, Serializable entity, Connection conn, Integer dbType,
			String dialect, String tableName) throws Exception {
		// TODO Auto-generated method stub
		return null;
	}

	@Override
	public Long deleteAll(SqlToyContext sqlToyContext, List<?> entities, int batchSize, Connection conn, Integer dbType,
			String dialect, Boolean autoCommit, String tableName) throws Exception {
		// TODO Auto-generated method stub
		return null;
	}

	@Override
	public QueryResult updateFetch(SqlToyContext sqlToyContext, SqlToyConfig sqlToyConfig, String sql,
			Object[] paramValues, UpdateRowHandler updateRowHandler, Connection conn, Integer dbType, String dialect,
			LockMode lockMode, int fetchSize, int maxRows) throws Exception {
		// TODO Auto-generated method stub
		return null;
	}

	@Override
	public StoreResult executeStore(SqlToyContext sqlToyContext, SqlToyConfig sqlToyConfig, String sql,
			Object[] inParamsValue, Integer[] outParamsType, Connection conn, Integer dbType, String dialect,
			int fetchSize) throws Exception {
		// TODO Auto-generated method stub
		return null;
	}

	@Override
	public List<ColumnMeta> getTableColumns(String catalog, String schema, String tableName, Connection conn,
			Integer dbType, String dialect) throws Exception {
		// TODO Auto-generated method stub
		return null;
	}

	@Override
	public List<TableMeta> getTables(String catalog, String schema, String tableName, Connection conn, Integer dbType,
			String dialect) throws Exception {
		// TODO Auto-generated method stub
		return null;
	}

}
