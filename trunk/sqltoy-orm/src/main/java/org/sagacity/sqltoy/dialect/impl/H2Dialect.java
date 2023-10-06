/**
 * 
 */
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
import org.sagacity.sqltoy.config.model.SqlType;
import org.sagacity.sqltoy.dialect.model.SavePKStrategy;
import org.sagacity.sqltoy.dialect.utils.DialectExtUtils;
import org.sagacity.sqltoy.dialect.utils.DialectUtils;
import org.sagacity.sqltoy.dialect.utils.H2DialectUtils;
import org.sagacity.sqltoy.model.LockMode;
import org.sagacity.sqltoy.model.QueryResult;
import org.sagacity.sqltoy.model.inner.QueryExecutorExtend;
import org.sagacity.sqltoy.utils.SqlUtil;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * @Author junhua
 * @Description 增加对h2数据库的支持，h2与pg类似,saveAllIgnoreExist与Oracle类似
 * @Date 2022/08/29 下午17:32
 **/
@SuppressWarnings({ "rawtypes" })
public class H2Dialect extends PostgreSqlDialect {
	/**
	 * 定义日志
	 */
	protected final Logger logger = LoggerFactory.getLogger(H2Dialect.class);

	/**
	 * 虚表
	 */
	public static final String VIRTUAL_TABLE = "dual";

	/*
	 * h2 不支持skip locked
	 */
	@Override
	public QueryResult findBySql(SqlToyContext sqlToyContext, SqlToyConfig sqlToyConfig, String sql,
			Object[] paramsValue, QueryExecutorExtend queryExecutorExtend, final DecryptHandler decryptHandler,
			final Connection conn, final LockMode lockMode, final Integer dbType, final String dialect,
			final int fetchSize, final int maxRows) throws Exception {
		String realSql = sql.concat(getLockSql(sql, dbType, lockMode));
		return DialectUtils.findBySql(sqlToyContext, sqlToyConfig, realSql, paramsValue, queryExecutorExtend,
				decryptHandler, conn, dbType, 0, fetchSize, maxRows);
	}

	@Override
	public Serializable load(SqlToyContext sqlToyContext, Serializable entity, List<Class> cascadeTypes,
			LockMode lockMode, Connection conn, final Integer dbType, final String dialect, final String tableName)
			throws Exception {
		EntityMeta entityMeta = sqlToyContext.getEntityMeta(entity.getClass());
		// 获取loadsql(loadsql 可以通过@loadSql进行改变，所以需要sqltoyContext重新获取)
		SqlToyConfig sqlToyConfig = sqlToyContext.getSqlToyConfig(entityMeta.getLoadSql(tableName), SqlType.search,
				dialect, null);
		String loadSql = sqlToyConfig.getSql(dialect);
		loadSql = loadSql.concat(getLockSql(loadSql, dbType, lockMode));
		return (Serializable) DialectUtils.load(sqlToyContext, sqlToyConfig, loadSql, entityMeta, entity, cascadeTypes,
				conn, dbType);
	}

	@Override
	public Long update(SqlToyContext sqlToyContext, Serializable entity, String[] forceUpdateFields, boolean cascade,
			final Class[] forceCascadeClasses, HashMap<Class, String[]> subTableForceUpdateProps, Connection conn,
			Integer dbType, String dialect, String tableName) throws Exception {
		return DialectUtils.update(sqlToyContext, entity, NVL_FUNCTION, forceUpdateFields, cascade,
				(cascade == false) ? null : new GenerateSqlHandler() {
					@Override
					public String generateSql(EntityMeta entityMeta, String[] forceUpdateFields) {
						PKStrategy pkStrategy = entityMeta.getIdStrategy();
						String sequence = "nextval('" + entityMeta.getSequence() + "')";
						// virtual_table为dual
						return DialectUtils.getSaveOrUpdateSql(sqlToyContext.getUnifyFieldsHandler(), dbType,
								entityMeta, pkStrategy, forceUpdateFields, VIRTUAL_TABLE, NVL_FUNCTION, sequence,
								H2DialectUtils.isAssignPKValue(pkStrategy), null);
					}
				}, forceCascadeClasses, subTableForceUpdateProps, conn, dbType, tableName);
	}

	@Override
	public Object save(SqlToyContext sqlToyContext, Serializable entity, Connection conn, Integer dbType,
			String dialect, String tableName) throws Exception {
		// 只支持sequence模式
		EntityMeta entityMeta = sqlToyContext.getEntityMeta(entity.getClass());
		PKStrategy pkStrategy = entityMeta.getIdStrategy();
		String sequence = "nextval('" + entityMeta.getSequence() + "')";
		boolean isAssignPK = H2DialectUtils.isAssignPKValue(pkStrategy);
		String insertSql = DialectExtUtils.generateInsertSql(sqlToyContext.getUnifyFieldsHandler(), dbType, entityMeta,
				pkStrategy, NVL_FUNCTION, sequence, isAssignPK, tableName);
		return DialectUtils.save(sqlToyContext, entityMeta, pkStrategy, isAssignPK, insertSql, entity,
				new GenerateSqlHandler() {
					@Override
					public String generateSql(EntityMeta entityMeta, String[] forceUpdateField) {
						PKStrategy pkStrategy = entityMeta.getIdStrategy();
						String sequence = "nextval('" + entityMeta.getSequence() + "')";
						return DialectExtUtils.generateInsertSql(sqlToyContext.getUnifyFieldsHandler(), dbType,
								entityMeta, pkStrategy, NVL_FUNCTION, sequence,
								H2DialectUtils.isAssignPKValue(pkStrategy), null);
					}
				}, new GenerateSavePKStrategy() {
					@Override
					public SavePKStrategy generate(EntityMeta entityMeta) {
						return new SavePKStrategy(entityMeta.getIdStrategy(),
								H2DialectUtils.isAssignPKValue(entityMeta.getIdStrategy()));
					}
				}, conn, dbType);
	}

	@Override
	public Long saveAll(SqlToyContext sqlToyContext, List<?> entities, int batchSize,
			ReflectPropsHandler reflectPropsHandler, Connection conn, Integer dbType, String dialect,
			Boolean autoCommit, String tableName) throws Exception {
		EntityMeta entityMeta = sqlToyContext.getEntityMeta(entities.get(0).getClass());
		PKStrategy pkStrategy = entityMeta.getIdStrategy();
		String sequence = "nextval('" + entityMeta.getSequence() + "')";
		boolean isAssignPK = H2DialectUtils.isAssignPKValue(pkStrategy);
		String insertSql = DialectExtUtils.generateInsertSql(sqlToyContext.getUnifyFieldsHandler(), dbType, entityMeta,
				pkStrategy, NVL_FUNCTION, sequence, isAssignPK, tableName);
		return DialectUtils.saveAll(sqlToyContext, entityMeta, pkStrategy, isAssignPK, insertSql, entities, batchSize,
				reflectPropsHandler, conn, dbType, autoCommit);
	}

	@Override
	public Long saveOrUpdate(SqlToyContext sqlToyContext, Serializable entity, String[] forceUpdateFields,
			Connection conn, Integer dbType, String dialect, Boolean autoCommit, String tableName) throws Exception {
		List<Serializable> entities = new ArrayList<Serializable>();
		entities.add(entity);
		return saveOrUpdateAll(sqlToyContext, entities, sqlToyContext.getBatchSize(), null, forceUpdateFields, conn,
				dbType, dialect, autoCommit, tableName);
	}

	@Override
	public Long saveOrUpdateAll(SqlToyContext sqlToyContext, List<?> entities, int batchSize,
			ReflectPropsHandler reflectPropsHandler, String[] forceUpdateFields, Connection conn, Integer dbType,
			String dialect, Boolean autoCommit, String tableName) throws Exception {
		EntityMeta entityMeta = sqlToyContext.getEntityMeta(entities.get(0).getClass());
		return DialectUtils.saveOrUpdateAll(sqlToyContext, entities, batchSize, entityMeta, forceUpdateFields,
				new GenerateSqlHandler() {
					@Override
					public String generateSql(EntityMeta entityMeta, String[] forceUpdateFields) {
						PKStrategy pkStrategy = entityMeta.getIdStrategy();
						String sequence = "nextval('" + entityMeta.getSequence() + "')";
						return DialectUtils.getSaveOrUpdateSql(sqlToyContext.getUnifyFieldsHandler(), dbType,
								entityMeta, pkStrategy, forceUpdateFields, VIRTUAL_TABLE, NVL_FUNCTION, sequence,
								H2DialectUtils.isAssignPKValue(pkStrategy), tableName);
					}
				}, reflectPropsHandler, conn, dbType, autoCommit);
	}

	@Override
	public Long saveAllIgnoreExist(SqlToyContext sqlToyContext, List<?> entities, int batchSize,
			ReflectPropsHandler reflectPropsHandler, Connection conn, Integer dbType, String dialect,
			Boolean autoCommit, String tableName) throws Exception {
		EntityMeta entityMeta = sqlToyContext.getEntityMeta(entities.get(0).getClass());
		return DialectUtils.saveAllIgnoreExist(sqlToyContext, entities, batchSize, entityMeta,
				new GenerateSqlHandler() {
					@Override
					public String generateSql(EntityMeta entityMeta, String[] forceUpdateFields) {
						PKStrategy pkStrategy = entityMeta.getIdStrategy();
						String sequence = "nextval('" + entityMeta.getSequence() + "')";
						return DialectExtUtils.mergeIgnore(sqlToyContext.getUnifyFieldsHandler(), dbType, entityMeta,
								pkStrategy, VIRTUAL_TABLE, NVL_FUNCTION, sequence,
								H2DialectUtils.isAssignPKValue(pkStrategy), tableName);
					}
				}, reflectPropsHandler, conn, dbType, autoCommit);
	}

	@Override
	public QueryResult updateFetch(SqlToyContext sqlToyContext, SqlToyConfig sqlToyConfig, String sql,
			Object[] paramsValue, UpdateRowHandler updateRowHandler, Connection conn, final Integer dbType,
			final String dialect, final LockMode lockMode, final int fetchSize, final int maxRows) throws Exception {
		String realSql = sql.concat(getLockSql(sql, dbType, (lockMode == null) ? LockMode.UPGRADE : lockMode));
		return DialectUtils.updateFetchBySql(sqlToyContext, sqlToyConfig, realSql, paramsValue, updateRowHandler, conn,
				dbType, 0, fetchSize, maxRows);
	}

	private String getLockSql(String sql, Integer dbType, LockMode lockMode) {
		// 判断是否已经包含for update
		if (lockMode == null || SqlUtil.hasLock(sql, dbType)) {
			return "";
		}
		if (lockMode == LockMode.UPGRADE_NOWAIT) {
			return " for update nowait ";
		}
		return " for update ";
	}
}
