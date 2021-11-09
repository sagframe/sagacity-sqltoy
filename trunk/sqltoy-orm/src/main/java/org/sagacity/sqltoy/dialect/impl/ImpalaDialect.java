package org.sagacity.sqltoy.dialect.impl;

import java.io.Serializable;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import org.sagacity.sqltoy.SqlToyConstants;
import org.sagacity.sqltoy.SqlToyContext;
import org.sagacity.sqltoy.callback.DecryptHandler;
import org.sagacity.sqltoy.callback.PreparedStatementResultHandler;
import org.sagacity.sqltoy.callback.ReflectPropsHandler;
import org.sagacity.sqltoy.callback.RowCallbackHandler;
import org.sagacity.sqltoy.callback.UpdateRowHandler;
import org.sagacity.sqltoy.config.model.EntityMeta;
import org.sagacity.sqltoy.config.model.PKStrategy;
import org.sagacity.sqltoy.config.model.SqlToyConfig;
import org.sagacity.sqltoy.config.model.SqlType;
import org.sagacity.sqltoy.dialect.Dialect;
import org.sagacity.sqltoy.dialect.model.ReturnPkType;
import org.sagacity.sqltoy.dialect.utils.DefaultDialectUtils;
import org.sagacity.sqltoy.dialect.utils.DialectExtUtils;
import org.sagacity.sqltoy.dialect.utils.DialectUtils;
import org.sagacity.sqltoy.executor.QueryExecutor;
import org.sagacity.sqltoy.model.ColumnMeta;
import org.sagacity.sqltoy.model.LockMode;
import org.sagacity.sqltoy.model.QueryResult;
import org.sagacity.sqltoy.model.StoreResult;
import org.sagacity.sqltoy.model.TableMeta;
import org.sagacity.sqltoy.utils.SqlUtil;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * @project sqltoy-orm
 * @description 提供基于impala对kudu的支持
 * @author zhongxuchen
 * @version v1.0,Date:2021-8-1
 */
public class ImpalaDialect implements Dialect {
	/**
	 * 定义日志
	 */
	protected final Logger logger = LoggerFactory.getLogger(ImpalaDialect.class);

	/**
	 * 判定为null的函数
	 */
	public static final String NVL_FUNCTION = "ifnull";

	@Override
	public boolean isUnique(final SqlToyContext sqlToyContext, Serializable entity, String[] paramsNamed,
			Connection conn, final Integer dbType, final String tableName) {
		return DialectUtils.isUnique(sqlToyContext, entity, paramsNamed, conn, dbType, tableName,
				(entityMeta, realParamNamed, table, topSize) -> {
					String queryStr = DialectExtUtils.wrapUniqueSql(entityMeta, realParamNamed, dbType, table);
					return queryStr + " limit " + topSize;
				});
	}

	@Override
	public QueryResult getRandomResult(SqlToyContext sqlToyContext, SqlToyConfig sqlToyConfig,
			QueryExecutor queryExecutor, final DecryptHandler decryptHandler, Long totalCount, Long randomCount,
			Connection conn, final Integer dbType, final String dialect, final int fetchSize, final int maxRows)
			throws Exception {
		return DefaultDialectUtils.getRandomResult(sqlToyContext, sqlToyConfig, queryExecutor, decryptHandler,
				totalCount, randomCount, conn, dbType, dialect, fetchSize, maxRows);
	}

	@Override
	public QueryResult findPageBySql(SqlToyContext sqlToyContext, SqlToyConfig sqlToyConfig,
			QueryExecutor queryExecutor, final DecryptHandler decryptHandler, Long pageNo, Integer pageSize,
			Connection conn, final Integer dbType, final String dialect, final int fetchSize, final int maxRows)
			throws Exception {
		return DefaultDialectUtils.findPageBySql(sqlToyContext, sqlToyConfig, queryExecutor, decryptHandler, pageNo,
				pageSize, conn, dbType, dialect, fetchSize, maxRows);
	}

	@Override
	public QueryResult findTopBySql(SqlToyContext sqlToyContext, SqlToyConfig sqlToyConfig, QueryExecutor queryExecutor,
			final DecryptHandler decryptHandler, Integer topSize, Connection conn, final Integer dbType,
			final String dialect, final int fetchSize, final int maxRows) throws Exception {
		return DefaultDialectUtils.findTopBySql(sqlToyContext, sqlToyConfig, queryExecutor, decryptHandler, topSize,
				conn, dbType, dialect, fetchSize, maxRows);
	}

	@Override
	public QueryResult findBySql(final SqlToyContext sqlToyContext, final SqlToyConfig sqlToyConfig, final String sql,
			final Object[] paramsValue, final RowCallbackHandler rowCallbackHandler,
			final DecryptHandler decryptHandler, final Connection conn, final LockMode lockMode, final Integer dbType,
			final String dialect, final int fetchSize, final int maxRows) throws Exception {
		return DialectUtils.findBySql(sqlToyContext, sqlToyConfig, sql, paramsValue, rowCallbackHandler, decryptHandler,
				conn, dbType, 0, fetchSize, maxRows);
	}

	@Override
	public Long getCountBySql(final SqlToyContext sqlToyContext, final SqlToyConfig sqlToyConfig, String sql,
			Object[] paramsValue, boolean isLastSql, final Connection conn, final Integer dbType, final String dialect)
			throws Exception {
		return DialectUtils.getCountBySql(sqlToyContext, sqlToyConfig, sql, paramsValue, isLastSql, conn, dbType);
	}

	@Override
	public Long saveOrUpdate(SqlToyContext sqlToyContext, Serializable entity, final String[] forceUpdateFields,
			Connection conn, final Integer dbType, final String dialect, final Boolean autoCommit,
			final String tableName) throws Exception {
		List<Serializable> entities = new ArrayList<Serializable>();
		entities.add(entity);
		return saveOrUpdateAll(sqlToyContext, entities, sqlToyContext.getBatchSize(), null, forceUpdateFields, conn,
				dbType, dialect, autoCommit, tableName);
	}

	@Override
	public Long saveOrUpdateAll(SqlToyContext sqlToyContext, List<?> entities, final int batchSize,
			ReflectPropsHandler reflectPropsHandler, final String[] forceUpdateFields, Connection conn,
			final Integer dbType, final String dialect, final Boolean autoCommit, final String tableName)
			throws Exception {
		EntityMeta entityMeta = sqlToyContext.getEntityMeta(entities.get(0).getClass());
		boolean isAssignPK = isAssignPKValue(entityMeta.getIdStrategy());
		String insertSql = DialectExtUtils
				.generateInsertSql(dbType, entityMeta, entityMeta.getIdStrategy(), NVL_FUNCTION,
						"NEXTVAL FOR " + entityMeta.getSequence(), isAssignPK, tableName)
				.replaceFirst("(?i)insert ", "upsert ");
		return DialectUtils.saveAll(sqlToyContext, entityMeta, entityMeta.getIdStrategy(), isAssignPK, insertSql,
				entities, batchSize, reflectPropsHandler, conn, dbType, autoCommit);
	}

	@Override
	public Long saveAllIgnoreExist(SqlToyContext sqlToyContext, List<?> entities, final int batchSize,
			ReflectPropsHandler reflectPropsHandler, Connection conn, final Integer dbType, final String dialect,
			final Boolean autoCommit, final String tableName) throws Exception {
		EntityMeta entityMeta = sqlToyContext.getEntityMeta(entities.get(0).getClass());
		boolean isAssignPK = isAssignPKValue(entityMeta.getIdStrategy());
		String insertSql = DialectExtUtils.generateInsertSql(dbType, entityMeta, entityMeta.getIdStrategy(),
				NVL_FUNCTION, "NEXTVAL FOR " + entityMeta.getSequence(), isAssignPK, tableName);
		return DialectUtils.saveAll(sqlToyContext, entityMeta, entityMeta.getIdStrategy(), isAssignPK, insertSql,
				entities, batchSize, reflectPropsHandler, conn, dbType, autoCommit);
	}

	@Override
	public Serializable load(final SqlToyContext sqlToyContext, Serializable entity, List<Class> cascadeTypes,
			LockMode lockMode, Connection conn, final Integer dbType, final String dialect, final String tableName)
			throws Exception {
		EntityMeta entityMeta = sqlToyContext.getEntityMeta(entity.getClass());
		// 获取loadsql(loadsql 可以通过@loadSql进行改变，所以需要sqltoyContext重新获取)
		SqlToyConfig sqlToyConfig = sqlToyContext.getSqlToyConfig(entityMeta.getLoadSql(tableName), SqlType.search,
				dialect);
		String loadSql = sqlToyConfig.getSql(dialect);
		return (Serializable) DialectUtils.load(sqlToyContext, sqlToyConfig, loadSql, entityMeta, entity, cascadeTypes,
				conn, dbType);
	}

	@Override
	public List<?> loadAll(final SqlToyContext sqlToyContext, List<?> entities, List<Class> cascadeTypes,
			LockMode lockMode, Connection conn, final Integer dbType, final String dialect, final String tableName,
			final int fetchSize, final int maxRows) throws Exception {
		return DialectUtils.loadAll(sqlToyContext, entities, cascadeTypes, lockMode, conn, dbType, tableName, null,
				fetchSize, maxRows);
	}

	@Override
	public Object save(SqlToyContext sqlToyContext, Serializable entity, Connection conn, final Integer dbType,
			final String dialect, final String tableName) throws Exception {
		EntityMeta entityMeta = sqlToyContext.getEntityMeta(entity.getClass());
		boolean isAssignPK = isAssignPKValue(entityMeta.getIdStrategy());
		String insertSql = DialectExtUtils.generateInsertSql(dbType, entityMeta, entityMeta.getIdStrategy(),
				NVL_FUNCTION, "NEXTVAL FOR " + entityMeta.getSequence(), isAssignPK, tableName);
		ReturnPkType returnPkType = (entityMeta.getIdStrategy() != null
				&& entityMeta.getIdStrategy().equals(PKStrategy.SEQUENCE)) ? ReturnPkType.GENERATED_KEYS
						: ReturnPkType.PREPARD_ID;
		return DialectUtils.save(sqlToyContext, entityMeta, entityMeta.getIdStrategy(), isAssignPK, returnPkType,
				insertSql, entity, null, null, conn, dbType);
	}

	@Override
	public Long saveAll(SqlToyContext sqlToyContext, List<?> entities, final int batchSize,
			ReflectPropsHandler reflectPropsHandler, Connection conn, final Integer dbType, final String dialect,
			final Boolean autoCommit, final String tableName) throws Exception {
		EntityMeta entityMeta = sqlToyContext.getEntityMeta(entities.get(0).getClass());
		boolean isAssignPK = isAssignPKValue(entityMeta.getIdStrategy());
		String insertSql = DialectExtUtils.generateInsertSql(dbType, entityMeta, entityMeta.getIdStrategy(),
				NVL_FUNCTION, "NEXTVAL FOR " + entityMeta.getSequence(), isAssignPK, tableName);
		return DialectUtils.saveAll(sqlToyContext, entityMeta, entityMeta.getIdStrategy(), isAssignPK, insertSql,
				entities, batchSize, reflectPropsHandler, conn, dbType, autoCommit);
	}

	@Override
	public Long update(SqlToyContext sqlToyContext, Serializable entity, String[] forceUpdateFields,
			final boolean cascade, final Class[] emptyCascadeClasses,
			final HashMap<Class, String[]> subTableForceUpdateProps, Connection conn, final Integer dbType,
			final String dialect, final String tableName) throws Exception {
		return saveOrUpdate(sqlToyContext, entity, forceUpdateFields, conn, dbType, dialect, null, tableName);
	}

	@Override
	public Long updateAll(SqlToyContext sqlToyContext, List<?> entities, final int batchSize,
			final String[] forceUpdateFields, ReflectPropsHandler reflectPropsHandler, Connection conn,
			final Integer dbType, final String dialect, final Boolean autoCommit, final String tableName)
			throws Exception {
		return saveOrUpdateAll(sqlToyContext, entities, batchSize, reflectPropsHandler, forceUpdateFields, conn, dbType,
				dialect, autoCommit, tableName);
	}

	@Override
	public Serializable updateSaveFetch(SqlToyContext sqlToyContext, Serializable entity,
			UpdateRowHandler updateRowHandler, String[] uniqueProps, Connection conn, Integer dbType, String dialect,
			String tableName) throws Exception {
		// 不支持
		throw new UnsupportedOperationException(SqlToyConstants.UN_SUPPORT_MESSAGE);
	}

	@Override
	public Long delete(SqlToyContext sqlToyContext, Serializable entity, Connection conn, final Integer dbType,
			final String dialect, final String tableName) throws Exception {
		return DialectUtils.delete(sqlToyContext, entity, conn, dbType, tableName);
	}

	@Override
	public Long deleteAll(SqlToyContext sqlToyContext, List<?> entities, final int batchSize, Connection conn,
			final Integer dbType, final String dialect, final Boolean autoCommit, final String tableName)
			throws Exception {
		return DefaultDialectUtils.deleteAll(sqlToyContext, entities, batchSize, conn, dbType, autoCommit, tableName);
	}

	@Override
	public QueryResult updateFetch(SqlToyContext sqlToyContext, SqlToyConfig sqlToyConfig, String sql,
			Object[] paramsValue, UpdateRowHandler updateRowHandler, Connection conn, final Integer dbType,
			final String dialect, final LockMode lockMode, final int fetchSize, final int maxRows) throws Exception {
		// 不支持
		throw new UnsupportedOperationException(SqlToyConstants.UN_SUPPORT_MESSAGE);
	}

	@Override
	public StoreResult executeStore(SqlToyContext sqlToyContext, final SqlToyConfig sqlToyConfig, final String sql,
			final Object[] inParamsValue, final Integer[] outParamsType, final Connection conn, final Integer dbType,
			final String dialect, final int fetchSize) throws Exception {
		return DialectUtils.executeStore(sqlToyConfig, sqlToyContext, sql, inParamsValue, outParamsType, conn, dbType,
				fetchSize);
	}

	@Override
	public QueryResult updateFetchRandom(SqlToyContext sqlToyContext, SqlToyConfig sqlToyConfig, String sql,
			Object[] paramsValue, Integer random, UpdateRowHandler updateRowHandler, Connection conn, Integer dbType,
			String dialect) throws Exception {
		// 不支持
		throw new UnsupportedOperationException(SqlToyConstants.UN_SUPPORT_MESSAGE);
	}

	@Override
	public QueryResult updateFetchTop(SqlToyContext sqlToyContext, SqlToyConfig sqlToyConfig, String sql,
			Object[] paramsValue, Integer topSize, UpdateRowHandler updateRowHandler, Connection conn, Integer dbType,
			String dialect) throws Exception {
		// 不支持
		throw new UnsupportedOperationException(SqlToyConstants.UN_SUPPORT_MESSAGE);
	}

	@SuppressWarnings("unchecked")
	@Override
	public List<ColumnMeta> getTableColumns(String catalog, String schema, String tableName, Connection conn,
			Integer dbType, String dialect) throws Exception {
		List<ColumnMeta> tableCols = DefaultDialectUtils.getTableColumns(catalog, schema, tableName, conn, dbType,
				dialect);
		// 获取主键信息
		ResultSet rs = conn.createStatement().executeQuery("DESCRIBE " + tableName);
		Map<String, ColumnMeta> colMap = (Map<String, ColumnMeta>) SqlUtil.preparedStatementProcess(null, null, rs,
				new PreparedStatementResultHandler() {
					@Override
					public void execute(Object rowData, PreparedStatement pst, ResultSet rs) throws Exception {
						Map<String, ColumnMeta> colComments = new HashMap<String, ColumnMeta>();
						while (rs.next()) {
							ColumnMeta colMeta = new ColumnMeta();
							colMeta.setColName(rs.getString("NAME"));
							colMeta.setPK(rs.getBoolean("PRIMARY_KEY"));
							colComments.put(colMeta.getColName(), colMeta);
						}
						this.setResult(colComments);
					}
				});
		ColumnMeta mapColMeta;
		for (ColumnMeta col : tableCols) {
			mapColMeta = colMap.get(col.getColName());
			if (mapColMeta != null) {
				col.setPK(mapColMeta.isPK());
			}
		}
		return tableCols;
	}

	@Override
	public List<TableMeta> getTables(String catalog, String schema, String tableName, Connection conn, Integer dbType,
			String dialect) throws Exception {
		return DefaultDialectUtils.getTables(catalog, schema, tableName, conn, dbType, dialect);
	}

	public static boolean isAssignPKValue(PKStrategy pkStrategy) {
		if (pkStrategy == null) {
			return true;
		}
		// 目前不支持sequence模式
		if (pkStrategy.equals(PKStrategy.SEQUENCE)) {
			return false;
		}
		if (pkStrategy.equals(PKStrategy.IDENTITY)) {
			return true;
		}
		return true;
	}
}
