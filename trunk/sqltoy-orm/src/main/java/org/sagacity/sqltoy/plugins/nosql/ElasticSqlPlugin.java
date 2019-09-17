/**
 * 
 */
package org.sagacity.sqltoy.plugins.nosql;

import java.util.List;

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.sagacity.sqltoy.SqlToyContext;
import org.sagacity.sqltoy.config.model.SqlToyConfig;
import org.sagacity.sqltoy.executor.QueryExecutor;
import org.sagacity.sqltoy.model.DataSetResult;
import org.sagacity.sqltoy.model.PaginationModel;
import org.sagacity.sqltoy.utils.MongoElasticUtils;

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
	protected final static Logger logger = LogManager.getLogger(ElasticSqlPlugin.class);

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
		String realMql = MongoElasticUtils.wrapES(sqlToyConfig, queryExecutor.getParamsName(sqlToyConfig),
				queryExecutor.getParamsValue(sqlToyContext, sqlToyConfig)).trim();
		// sql模式
		realMql = realMql + " limit " + (pageModel.getPageNo() - 1) * pageModel.getPageSize() + ","
				+ pageModel.getPageSize();
		if (sqlToyContext.isDebug()) {
			logger.debug("execute eql={" + realMql + "}");
		}
		PaginationModel page = new PaginationModel();
		page.setPageNo(pageModel.getPageNo());
		page.setPageSize(pageModel.getPageSize());
		DataSetResult result = ElasticSearchUtils.executeQuery(sqlToyContext, sqlToyConfig, realMql,
				queryExecutor.getResultTypeName());
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
		String realMql = MongoElasticUtils.wrapES(sqlToyConfig, queryExecutor.getParamsName(sqlToyConfig),
				queryExecutor.getParamsValue(sqlToyContext, sqlToyConfig)).trim();
		// sql模式
		if (topSize != null) {
			realMql = realMql + " limit 0," + topSize;
		}
		if (sqlToyContext.isDebug()) {
			logger.debug("execute eql={" + realMql + "}");
		}
		DataSetResult result = ElasticSearchUtils.executeQuery(sqlToyContext, sqlToyConfig, realMql,
				queryExecutor.getResultTypeName());
		return result.getRows();
	}

}
