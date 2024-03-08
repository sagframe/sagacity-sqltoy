package org.sagacity.sqltoy.plugins;

import javax.sql.DataSource;

import org.sagacity.sqltoy.SqlToyConstants;
import org.sagacity.sqltoy.SqlToyContext;
import org.sagacity.sqltoy.callback.DbAdapterHandler;
import org.sagacity.sqltoy.config.model.SqlToyConfig;
import org.sagacity.sqltoy.config.model.SqlType;
import org.sagacity.sqltoy.dialect.DialectFactory;
import org.sagacity.sqltoy.exception.DataAccessException;
import org.sagacity.sqltoy.model.Page;
import org.sagacity.sqltoy.model.QueryExecutor;
import org.sagacity.sqltoy.utils.DataSourceUtils;
import org.sagacity.sqltoy.utils.QueryExecutorBuilder;
import org.sagacity.sqltoy.utils.StringUtil;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * @project sagacity-sqltoy
 * @description 提供sql查询语句在需要适配的数据库下进行执行校验，检验sql能否在不同数据库下可以正确执行
 *              一般提供给针对多种数据库做产品化项目使用
 * @author zhongxuchen
 * @version v1.0,Date:2022-8-13
 */
public class CrossDbAdapter {
	/**
	 * 定义日志
	 */
	protected final static Logger logger = LoggerFactory.getLogger(CrossDbAdapter.class);

	/**
	 * @TODO 执行count查询
	 * @param sqlToyContext
	 * @param dialectFactory
	 * @param queryExecutor
	 */
	public static void redoCountQuery(SqlToyContext sqlToyContext, DialectFactory dialectFactory,
			QueryExecutor queryExecutor) {
		doQuery(sqlToyContext, queryExecutor, (sqlToyConfig, dataSource) -> {
			dialectFactory.getCountBySql(sqlToyContext, queryExecutor, sqlToyConfig, dataSource);
		});
	}

	/**
	 * @TODO 执行分页查询
	 * @param sqlToyContext
	 * @param dialectFactory
	 * @param queryExecutor
	 * @param page
	 */
	public static void redoPageQuery(SqlToyContext sqlToyContext, DialectFactory dialectFactory,
			QueryExecutor queryExecutor, Page page) {
		doQuery(sqlToyContext, queryExecutor, (sqlToyConfig, dataSource) -> {
			if (page.getSkipQueryCount() != null && page.getSkipQueryCount()) {
				dialectFactory.findSkipTotalCountPage(sqlToyContext, queryExecutor, sqlToyConfig, page.getPageNo(),
						page.getPageSize(), dataSource);
			} else {
				dialectFactory.findPage(sqlToyContext, queryExecutor, sqlToyConfig, page.getPageNo(),
						page.getPageSize(), page.getOverPageToFirst(), dataSource);
			}
		});
	}

	/**
	 * @TODO 执行普通查询
	 * @param sqlToyContext
	 * @param dialectFactory
	 * @param queryExecutor
	 */
	public static void redoQuery(SqlToyContext sqlToyContext, DialectFactory dialectFactory,
			QueryExecutor queryExecutor) {
		doQuery(sqlToyContext, queryExecutor, (sqlToyConfig, dataSource) -> {
			dialectFactory.findByQuery(sqlToyContext, queryExecutor, sqlToyConfig, null, dataSource);
		});
	}

	/**
	 * @TODO 执行取top记录查询
	 * @param sqlToyContext
	 * @param dialectFactory
	 * @param queryExecutor
	 * @param topSize
	 */
	public static void redoTopQuery(SqlToyContext sqlToyContext, DialectFactory dialectFactory,
			QueryExecutor queryExecutor, double topSize) {
		doQuery(sqlToyContext, queryExecutor, (sqlToyConfig, dataSource) -> {
			dialectFactory.findTop(sqlToyContext, queryExecutor, sqlToyConfig, topSize, dataSource);
		});
	}

	/**
	 * @TODO 执行取随机记录查询
	 * @param sqlToyContext
	 * @param dialectFactory
	 * @param queryExecutor
	 * @param randomCount
	 */
	public static void redoRandomQuery(SqlToyContext sqlToyContext, DialectFactory dialectFactory,
			QueryExecutor queryExecutor, double randomCount) {
		doQuery(sqlToyContext, queryExecutor, (sqlToyConfig, dataSource) -> {
			dialectFactory.getRandomResult(sqlToyContext, queryExecutor, sqlToyConfig, randomCount, dataSource);
		});
	}

	/**
	 * @TODO 在产品化所需适配的数据库下执行查询，检验sql的适配性
	 * @param sqlToyContext
	 * @param queryExecutor
	 * @param dbAdapterHandler
	 */
	private static void doQuery(SqlToyContext sqlToyContext, QueryExecutor queryExecutor,
			DbAdapterHandler dbAdapterHandler) {
		// 适配验证的数据源为空
		if (null == sqlToyContext.getRedoDataSources() || sqlToyContext.getRedoDataSources().length == 0) {
			return;
		}
		DataSource dataSource;
		SqlToyConfig sqlToyConfig = null;
		String dataSourceName = null;
		String dialect;
		String errMsg;
		// 循环获取需要适配验证的数据库，执行sql
		for (int i = 0; i < sqlToyContext.getRedoDataSources().length; i++) {
			dataSourceName = sqlToyContext.getRedoDataSources()[i];
			dataSource = sqlToyContext.getDataSourceSelector().getDataSourceBean(sqlToyContext.getAppContext(),
					dataSourceName);
			if (null == dataSource) {
				throw new IllegalArgumentException("跨库查询适配验证,数据源:" + dataSourceName + " 不存在,请检查配置!");
			}
			dialect = DataSourceUtils.getDialect(sqlToyContext, dataSource);
			// 获得相关方言的sql(函数自动替换等)
			sqlToyConfig = sqlToyContext.getSqlToyConfig(queryExecutor, SqlType.search, dialect);
			// 自定义countsql
			String countSql = queryExecutor.getInnerModel().countSql;
			if (StringUtil.isNotBlank(countSql)) {
				// 存在@include(sqlId) 或 @include(:sqlScript)
				if (StringUtil.matches(countSql, SqlToyConstants.INCLUDE_PATTERN)) {
					SqlToyConfig countSqlConfig = sqlToyContext.getSqlToyConfig(countSql, SqlType.search, dialect,
							QueryExecutorBuilder.getParamValues(queryExecutor));
					sqlToyConfig.setCountSql(countSqlConfig.getSql());
				} else {
					sqlToyConfig.setCountSql(countSql);
				}
			}
			try {
				dbAdapterHandler.query(sqlToyConfig, dataSource);
			} catch (Exception e) {
				errMsg = "查询语句:" + sqlToyConfig.getId() + " 不适配:" + dialect + " 的数据源:" + dataSourceName + ";errorMsg="
						+ e.getMessage();
				logger.error(errMsg);
				throw new DataAccessException(errMsg);
			}
		}
	}
}
