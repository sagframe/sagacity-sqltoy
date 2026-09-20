package org.sagacity.sqltoy.dialect.impl;

import java.io.Serializable;
import java.sql.Connection;

import org.sagacity.sqltoy.SqlToyConstants;
import org.sagacity.sqltoy.SqlToyContext;
import org.sagacity.sqltoy.callback.DecryptHandler;
import org.sagacity.sqltoy.config.model.OperateType;
import org.sagacity.sqltoy.config.model.SqlToyConfig;
import org.sagacity.sqltoy.dialect.utils.DialectExtUtils;
import org.sagacity.sqltoy.dialect.utils.DialectUtils;
import org.sagacity.sqltoy.model.DBProfile;
import org.sagacity.sqltoy.model.QueryExecutor;
import org.sagacity.sqltoy.model.QueryResult;
import org.sagacity.sqltoy.utils.SqlUtil;
import org.sagacity.sqltoy.utils.SqlUtilsExt;

/**
 * @project sagacity-sqltoy
 * @description oracle11g以及以下版本数据库的各类分页、取随机数、saveOrUpdate,lock机制实现
 * @author zhongxuchen
 * @version v1.0,Date:2013-03-21
 */
@SuppressWarnings({ "rawtypes" })
public class Oracle11gDialect extends OracleDialect {

	@Override
	public boolean isUnique(SqlToyContext sqlToyContext, Serializable entity, String[] paramsNamed, Connection conn,
			DBProfile profile, String tableName, final Integer queryTimeout) {
		return DialectUtils.isUnique(sqlToyContext, entity, paramsNamed, conn, profile, tableName,
				(entityMeta, realParamNamed, table, topSize) -> {
					StringBuilder sql = new StringBuilder();
					sql.append("SELECT sag_uniqueTop.* FROM ( ");
					sql.append(DialectExtUtils.wrapUniqueSql(entityMeta, realParamNamed, profile, table));
					sql.append(") sag_uniqueTop where ROWNUM <=");
					sql.append(topSize);
					return sql.toString();
				}, queryTimeout);
	}

	/*
	 * (non-Javadoc)
	 * 
	 * @see org.sagacity.sqltoy.dialect.Dialect#findPageBySql(org.sagacity
	 * .sqltoy.SqlToyContext, org.sagacity.sqltoy.config.model.SqlToyConfig,
	 * org.sagacity.sqltoy.model.QueryExecutor,
	 * org.sagacity.sqltoy.callback.RowCallbackHandler, java.lang.Long,
	 * java.lang.Integer, java.sql.Connection)
	 */
	@Override
	public QueryResult findPageBySql(SqlToyContext sqlToyContext, SqlToyConfig sqlToyConfig,
			QueryExecutor queryExecutor, final DecryptHandler decryptHandler, Long pageNo, Integer pageSize,
			Connection conn, DBProfile profile, final int fetchSize, final int maxRows) throws Exception {
		String dialect = profile.getDialect();
		boolean isNamed = sqlToyConfig.isNamedParam();
		String innerSql = sqlToyConfig.isHasFast() ? sqlToyConfig.getFastSql(dialect) : sqlToyConfig.getSql(dialect);
		boolean hasOrderBy = SqlUtil.hasOrderBy(innerSql, true);
		// 给原始sql标记上特殊的开始和结尾，便于sql拦截器快速定位到原始sql并进行条件补充
		innerSql = SqlUtilsExt.markOriginalSql(innerSql);
		// rownum派生层暴露的page_row_id伪列须跳过;@fast外层select只取引用列无需跳过
		int columnSkip = sqlToyConfig.isHasFast() ? 0 : 1;
		StringBuilder sql = DialectUtils.openFastWrap(sqlToyConfig, dialect);
		sql.append("SELECT * FROM (SELECT ROWNUM page_row_id," + SqlToyConstants.INTERMEDIATE_TABLE + ".* FROM ( ");
		sql.append(innerSql);
		sql.append(") ");
		sql.append(SqlToyConstants.INTERMEDIATE_TABLE);
		sql.append(" ");

		// 判断sql中是否存在排序，因为oracle排序查询的机制通过ROWNUM<=?每次查出的结果可能不一样 ， 请参见ROWNUM机制以及oracle
		// SORT ORDER BY STOPKEY
		if (SqlToyConstants.oraclePageIgnoreOrder() || !hasOrderBy) {
			sql.append(" where ROWNUM <=");
			sql.append(isNamed ? ":" + SqlToyConstants.PAGE_FIRST_PARAM_NAME : "?");
			sql.append(" ) WHERE page_row_id>");
			sql.append(isNamed ? ":" + SqlToyConstants.PAGE_LAST_PARAM_NAME : "?");
		} else {
			sql.append(" ) WHERE page_row_id<=");
			sql.append(isNamed ? ":" + SqlToyConstants.PAGE_FIRST_PARAM_NAME : "?");
			sql.append(" and page_row_id >");
			sql.append(isNamed ? ":" + SqlToyConstants.PAGE_LAST_PARAM_NAME : "?");
		}
		DialectUtils.closeFastWrap(sqlToyConfig, dialect, sql);
		return DialectUtils.executeWrappedQuery(sqlToyContext, sqlToyConfig, queryExecutor, decryptHandler, conn,
				profile, sql.toString(), pageNo * pageSize, (pageNo - 1) * pageSize,
				(queryExecutor.getInnerModel().entityClass == null) ? OperateType.page : OperateType.singleTable,
				columnSkip, fetchSize, maxRows);
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
		String dialect = profile.getDialect();
		String innerSql = sqlToyConfig.isHasFast() ? sqlToyConfig.getFastSql(dialect) : sqlToyConfig.getSql(dialect);
		// 给原始sql标记上特殊的开始和结尾，便于sql拦截器快速定位到原始sql并进行条件补充
		innerSql = SqlUtilsExt.markOriginalSql(innerSql);
		StringBuilder sql = DialectUtils.openFastWrap(sqlToyConfig, dialect);
		sql.append("SELECT " + SqlToyConstants.INTERMEDIATE_TABLE + ".* FROM ( ");
		sql.append(innerSql);
		sql.append(") " + SqlToyConstants.INTERMEDIATE_TABLE + " where ROWNUM <=");
		sql.append(Double.valueOf(topSize).intValue());
		DialectUtils.closeFastWrap(sqlToyConfig, dialect, sql);
		return DialectUtils.executeWrappedQuery(sqlToyContext, sqlToyConfig, queryExecutor, decryptHandler, conn,
				profile, sql.toString(), null, null,
				(queryExecutor.getInnerModel().entityClass == null) ? OperateType.top : OperateType.singleTable,
				fetchSize, maxRows);
	}
}
