/**
 * 
 */
package org.sagacity.sqltoy.dialect.impl;

import java.io.Serializable;
import java.sql.Connection;
import java.util.List;

import org.sagacity.sqltoy.SqlToyConstants;
import org.sagacity.sqltoy.SqlToyContext;
import org.sagacity.sqltoy.callback.DecryptHandler;
import org.sagacity.sqltoy.callback.GenerateSqlHandler;
import org.sagacity.sqltoy.callback.ReflectPropsHandler;
import org.sagacity.sqltoy.callback.RowCallbackHandler;
import org.sagacity.sqltoy.callback.UpdateRowHandler;
import org.sagacity.sqltoy.config.model.EntityMeta;
import org.sagacity.sqltoy.config.model.PKStrategy;
import org.sagacity.sqltoy.config.model.SqlToyConfig;
import org.sagacity.sqltoy.config.model.SqlType;
import org.sagacity.sqltoy.dialect.utils.DialectExtUtils;
import org.sagacity.sqltoy.dialect.utils.DialectUtils;
import org.sagacity.sqltoy.dialect.utils.PostgreSqlDialectUtils;
import org.sagacity.sqltoy.model.LockMode;
import org.sagacity.sqltoy.model.QueryResult;
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
	    h2 不支持skip locked
	 */
    @Override
    public QueryResult findBySql(SqlToyContext sqlToyContext, SqlToyConfig sqlToyConfig, String sql,
                                 Object[] paramsValue, RowCallbackHandler rowCallbackHandler, final DecryptHandler decryptHandler,
                                 final Connection conn, final LockMode lockMode, final Integer dbType, final String dialect,
                                 final int fetchSize, final int maxRows) throws Exception {
        if (LockMode.UPGRADE_SKIPLOCK == lockMode) {
            throw new UnsupportedOperationException("h2 lock search," + SqlToyConstants.UN_SUPPORT_MESSAGE);
        }
        String realSql = sql.concat(getLockSql(sql, dbType, lockMode));
        return DialectUtils.findBySql(sqlToyContext, sqlToyConfig, realSql, paramsValue, rowCallbackHandler,
                decryptHandler, conn, dbType, 0, fetchSize, maxRows);
    }

    @Override
    public Serializable load(SqlToyContext sqlToyContext, Serializable entity, List<Class> cascadeTypes,
                             LockMode lockMode, Connection conn, final Integer dbType, final String dialect, final String tableName)
            throws Exception {
        if (LockMode.UPGRADE_SKIPLOCK == lockMode) {
            throw new UnsupportedOperationException("h2 lock search," + SqlToyConstants.UN_SUPPORT_MESSAGE);
        }
        EntityMeta entityMeta = sqlToyContext.getEntityMeta(entity.getClass());
        // 获取loadsql(loadsql 可以通过@loadSql进行改变，所以需要sqltoyContext重新获取)
        SqlToyConfig sqlToyConfig = sqlToyContext.getSqlToyConfig(entityMeta.getLoadSql(tableName), SqlType.search,
                dialect);
        String loadSql = sqlToyConfig.getSql(dialect);
        loadSql = loadSql.concat(getLockSql(loadSql, dbType, lockMode));
        return (Serializable) DialectUtils.load(sqlToyContext, sqlToyConfig, loadSql, entityMeta, entity, cascadeTypes,
                conn, dbType);
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
                        String sequence = "nextval('" + entityMeta.getSequence()+"')";
                        if (pkStrategy != null && pkStrategy.equals(PKStrategy.IDENTITY)) {
                            // 伪造成sequence模式
                            pkStrategy = PKStrategy.SEQUENCE;
                            sequence = "DEFAULT";
                        }
                        return DialectExtUtils.mergeIgnore(dbType, entityMeta, pkStrategy, VIRTUAL_TABLE, NVL_FUNCTION,
                                sequence, PostgreSqlDialectUtils.isAssignPKValue(pkStrategy), tableName);
                    }
                }, reflectPropsHandler, conn, dbType, autoCommit);
    }

    @Override
    public QueryResult updateFetch(SqlToyContext sqlToyContext, SqlToyConfig sqlToyConfig, String sql,
                                   Object[] paramsValue, UpdateRowHandler updateRowHandler, Connection conn, final Integer dbType,
                                   final String dialect, final LockMode lockMode, final int fetchSize, final int maxRows) throws Exception {
        if (LockMode.UPGRADE_SKIPLOCK == lockMode) {
            throw new UnsupportedOperationException("h2 lock search," + SqlToyConstants.UN_SUPPORT_MESSAGE);
        }
        return DialectUtils.updateFetchBySql(sqlToyContext, sqlToyConfig, sql, paramsValue, updateRowHandler, conn,
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
