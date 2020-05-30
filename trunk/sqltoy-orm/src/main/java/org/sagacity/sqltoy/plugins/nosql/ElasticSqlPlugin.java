/**
 * 
 */
package org.sagacity.sqltoy.plugins.nosql;

import java.util.List;

import org.sagacity.sqltoy.SqlToyContext;
import org.sagacity.sqltoy.config.model.SqlToyConfig;
import org.sagacity.sqltoy.executor.QueryExecutor;
import org.sagacity.sqltoy.model.DataSetResult;
import org.sagacity.sqltoy.model.PaginationModel;
import org.sagacity.sqltoy.utils.MongoElasticUtils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * @project sagacity-sqltoy4.1
 * @description elasticsearch-sql 或elasticsearch6.3.x 版本支持xpack sql查询
 * @author chenrenfei <a href="mailto:zhongxuchen@gmail.com">联系作者</a>
 * @version id:ElasticSqlPlugin.java,Revision:v1.0,Date:2018年1月3日
 */
public class ElasticSqlPlugin {
	/**
	 * 定义全局日志
	 */
	protected final static Logger logger = LoggerFactory.getLogger(ElasticSqlPlugin.class);

	/**
	 * @todo 基于es的分页查询
	 * @param sqlToyContext
	 * @param sqlToyConfig
	 * @param pageModel
	 * @param queryExecutor
	 * @return
	 * @throws Exception
	 */
	public static PaginationModel findPage(SqlToyContext sqlToyContext, SqlToyConfig sqlToyConfig,
			PaginationModel pageModel, QueryExecutor queryExecutor) throws Exception {
		String realSql = MongoElasticUtils.wrapES(sqlToyConfig, queryExecutor.getParamsName(sqlToyConfig),
				queryExecutor.getParamsValue(sqlToyContext, sqlToyConfig)).trim();
		// sql模式
		realSql = realSql + " limit " + (pageModel.getPageNo() - 1) * pageModel.getPageSize() + ","
				+ pageModel.getPageSize();
		if (sqlToyContext.isDebug()) {
			if (logger.isDebugEnabled()) {
				logger.debug("findPageByElastic sql=" + realSql);
			} else {
				System.out.println("findPageByElastic sql=" + realSql);
			}
		}
		PaginationModel page = new PaginationModel();
		page.setPageNo(pageModel.getPageNo());
		page.setPageSize(pageModel.getPageSize());
		DataSetResult result = ElasticSearchUtils.executeQuery(sqlToyContext, sqlToyConfig, realSql,
				(Class) queryExecutor.getResultType());
		page.setRows(result.getRows());
		page.setRecordCount(result.getTotalCount());
		return page;
	}

	/**
	 * @todo 提取符合条件的前多少条记录
	 * @param sqlToyContext
	 * @param sqlToyConfig
	 * @param queryExecutor
	 * @param topSize
	 * @return
	 * @throws Exception
	 */
	public static List<?> findTop(SqlToyContext sqlToyContext, SqlToyConfig sqlToyConfig, QueryExecutor queryExecutor,
			Integer topSize) throws Exception {
		String realSql = MongoElasticUtils.wrapES(sqlToyConfig, queryExecutor.getParamsName(sqlToyConfig),
				queryExecutor.getParamsValue(sqlToyContext, sqlToyConfig)).trim();
		// sql模式
		if (topSize != null) {
			realSql = realSql + " limit 0," + topSize;
		}
		if (sqlToyContext.isDebug()) {
			if (logger.isDebugEnabled()) {
				logger.debug("findTopByElastic sql=" + realSql);
			} else {
				System.out.println("findTopByElastic sql=" + realSql);
			}
		}
		DataSetResult result = ElasticSearchUtils.executeQuery(sqlToyContext, sqlToyConfig, realSql,
				(Class) queryExecutor.getResultType());
		return result.getRows();
	}

}
