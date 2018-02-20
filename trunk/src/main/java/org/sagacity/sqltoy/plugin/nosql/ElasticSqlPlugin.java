/**
 * 
 */
package org.sagacity.sqltoy.plugin.nosql;

import static java.lang.System.out;

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
 * @description elasticSearch的插件
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
				queryExecutor.getParamsValue(sqlToyConfig)).trim();
		// sql模式
		realMql = realMql + " limit " + (pageModel.getPageNo() - 1) * pageModel.getPageSize() + ","
				+ pageModel.getPageSize();
		if (sqlToyContext.isDebug()) {
			out.println("execute eql={" + realMql + "}");
		}
		PaginationModel page = new PaginationModel();
		page.setPageNo(pageModel.getPageNo());
		page.setPageSize(pageModel.getPageSize());
		DataSetResult result = ElasticSearchUtils.executeQuery(sqlToyContext, sqlToyConfig, realMql,
				(queryExecutor.getResultType() == null) ? null : queryExecutor.getResultType().getTypeName());
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
	public static List findTop(SqlToyContext sqlToyContext, SqlToyConfig sqlToyConfig, QueryExecutor queryExecutor,
			Integer topSize) throws Exception {
		String realMql = MongoElasticUtils.wrapES(sqlToyConfig, queryExecutor.getParamsName(sqlToyConfig),
				queryExecutor.getParamsValue(sqlToyConfig)).trim();
		// sql模式
		if (topSize != null) {
			realMql = realMql + " limit 0," + topSize;
		}
		if (sqlToyContext.isDebug()) {
			out.println("execute eql={" + realMql + "}");
		}
		DataSetResult result = ElasticSearchUtils.executeQuery(sqlToyContext, sqlToyConfig, realMql,
				(queryExecutor.getResultType() == null) ? null : queryExecutor.getResultType().getTypeName());
		return result.getRows();
	}

}
