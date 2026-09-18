package org.sagacity.sqltoy.plugins.nosql;

import java.util.ArrayList;
import java.util.List;

import org.sagacity.sqltoy.SqlToyContext;
import org.sagacity.sqltoy.config.model.ElasticEndpoint;
import org.sagacity.sqltoy.config.model.NoSqlConfigModel;
import org.sagacity.sqltoy.config.model.SqlToyConfig;
import org.sagacity.sqltoy.dialect.QueryExecutorBuilder;
import org.sagacity.sqltoy.model.Page;
import org.sagacity.sqltoy.model.QueryExecutor;
import org.sagacity.sqltoy.model.inner.DataSetResult;
import org.sagacity.sqltoy.model.inner.QueryExecutorExtend;
import org.sagacity.sqltoy.utils.StringUtil;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.alibaba.fastjson2.JSONObject;

/**
 * @project sagacity-sqltoy
 * @description elasticsearch-sql 或elasticsearch6.3.x 版本支持xpack sql查询
 * @author zhongxuchen
 * @version v1.0,Date:2018-01-03
 */
public class ElasticSqlPlugin {
	/**
	 * 定义全局日志
	 */
	protected final static Logger logger = LoggerFactory.getLogger(ElasticSqlPlugin.class);

	/**
	 * 基于es的分页查询
	 * 
	 * @param sqlToyContext
	 * @param sqlToyConfig
	 * @param pageModel
	 * @param queryExecutor
	 * @return
	 * @throws Exception
	 */
	public static Page findPage(SqlToyContext sqlToyContext, SqlToyConfig sqlToyConfig, Page pageModel,
			QueryExecutor queryExecutor) throws Exception {
		QueryExecutorExtend extend = queryExecutor.getInnerModel();
		QueryExecutorBuilder.initQueryExecutor(sqlToyContext, extend, sqlToyConfig, false);
		String realSql = MongoElasticOperations
				.wrapES(sqlToyConfig, extend.getParamsName(), extend.getParamsValue(sqlToyContext, sqlToyConfig))
				.trim();
		// sql模式
		realSql = realSql + " limit " + (pageModel.getPageNo() - 1) * pageModel.getPageSize() + ","
				+ pageModel.getPageSize();
		if (sqlToyContext.isDebug()) {
			logger.debug("findPageByElastic sql={}", realSql);
		}
		Page page = new Page();
		page.setPageNo(pageModel.getPageNo());
		page.setPageSize(pageModel.getPageSize());
		DataSetResult result = ElasticSearchUtils.executeQuery(sqlToyContext, sqlToyConfig, realSql,
				(Class) extend.resultType, extend.humpMapLabel);
		page.setRows(result.getRows());
		page.setRecordCount(result.getRecordCount());
		return page;
	}

	/**
	 * es原生sql游标分页(ES SQL不支持limit/offset语法,官方分页方式为fetch_size+cursor游标):
	 * 首页发送query+fetch_size,后续页发送cursor+fetch_size顺序取页;
	 * 只支持顺序翻页不支持跳页(pageNo不参与定位),服务端不提供总数,recordCount保持0;
	 * 游标由内部ElasticPage携带并随返回对象给出:调用方把上次findPage返回的Page传回即续取下一页,
	 * 数据取尽(响应无cursor)后再传回返回空页(不再发起请求),以rows为空作为循环终止条件是安全的;
	 * 游标页的响应不含columns,因此eql需配置fields属性以完成字段映射;
	 * 多地址端点逐请求轮询不同协调节点不影响游标续页(游标编码了分片上下文及其所在数据节点,
	 * 同集群任意协调节点均可恢复,经双节点集群实测交替协调节点连续翻页正常), 前提是url中配置的多个地址必须属于同一集群(不同集群的index
	 * uuid不一致会导致游标无法解析)
	 *
	 * @param sqlToyContext
	 * @param sqlToyConfig
	 * @param pageModel     分页模型(pageSize为每页行数;续页传回上次调用的返回对象)
	 * @param queryExecutor
	 * @return
	 * @throws Exception
	 */
	public static Page findNativeSqlPage(SqlToyContext sqlToyContext, SqlToyConfig sqlToyConfig, Page pageModel,
			QueryExecutor queryExecutor) throws Exception {
		QueryExecutorExtend extend = queryExecutor.getInnerModel();
		QueryExecutorBuilder.initQueryExecutor(sqlToyContext, extend, sqlToyConfig, false);
		boolean finished = false;
		String cursor = null;
		if (pageModel instanceof ElasticPage) {
			ElasticPage prior = (ElasticPage) pageModel;
			finished = prior.finished;
			cursor = prior.cursor;
		}
		// 数据已取尽后再传回同一Page仍返回空页,避免重新从首页发起查询导致调用方循环无法终止
		if (finished) {
			ElasticPage exhausted = new ElasticPage();
			exhausted.setPageNo(pageModel.getPageNo());
			exhausted.setPageSize(pageModel.getPageSize());
			exhausted.setRows(new ArrayList());
			exhausted.finished = true;
			return exhausted;
		}
		JSONObject body = new JSONObject();
		body.put("fetch_size", pageModel.getPageSize());
		if (StringUtil.isNotBlank(cursor)) {
			// 取下一页
			body.put("cursor", cursor);
		} else {
			String realSql = MongoElasticOperations
					.wrapES(sqlToyConfig, extend.getParamsName(), extend.getParamsValue(sqlToyContext, sqlToyConfig))
					.trim();
			body.put("query", realSql);
			if (sqlToyContext.isDebug()) {
				logger.debug("findNativeSqlPage first-page sql={}", realSql);
			}
		}
		NoSqlConfigModel noSqlConfig = sqlToyConfig.getNoSqlConfigModel();
		ElasticEndpoint esConfig = sqlToyContext.getElasticEndpoint(noSqlConfig.getEndpoint());
		JSONObject json = HttpClientUtils.doPostBody(sqlToyContext, noSqlConfig, esConfig, body);
		ElasticPage page = new ElasticPage();
		page.setPageNo(pageModel.getPageNo());
		page.setPageSize(pageModel.getPageSize());
		// 游标分页服务端不提供总记录数,recordCount保持默认0
		if (json == null || json.isEmpty()) {
			page.setRows(new ArrayList());
			page.finished = true;
			return page;
		}
		page.cursor = json.getString("cursor");
		// 无cursor表示数据已取完,再次传回该Page将直接返回空页
		page.finished = StringUtil.isBlank(page.cursor);
		DataSetResult result = ElasticSearchUtils.executeQuery(sqlToyContext, sqlToyConfig, json,
				(Class) extend.resultType, extend.humpMapLabel);
		page.setRows(result.getRows());
		return page;
	}

	/**
	 * 携带游标的内部分页模型:cursor/finished仅为es原生sql游标分页的实现细节,
	 * 对外(Elastic.findPage签名与调用方视角)统一是Page
	 */
	private static class ElasticPage extends Page {
		private static final long serialVersionUID = 1L;
		/**
		 * 服务端游标,null表示首页请求或数据已取完
		 */
		private String cursor;
		/**
		 * 数据是否已取完,取完后再次传回直接返回空页
		 */
		private boolean finished;
	}

	/**
	 * 提取符合条件的前多少条记录
	 * 
	 * @param sqlToyContext
	 * @param sqlToyConfig
	 * @param queryExecutor
	 * @param topSize
	 * @return
	 * @throws Exception
	 */
	public static List<?> findTop(SqlToyContext sqlToyContext, SqlToyConfig sqlToyConfig, QueryExecutor queryExecutor,
			Integer topSize) throws Exception {
		QueryExecutorExtend extend = queryExecutor.getInnerModel();
		QueryExecutorBuilder.initQueryExecutor(sqlToyContext, extend, sqlToyConfig, false);
		String realSql = MongoElasticOperations
				.wrapES(sqlToyConfig, extend.getParamsName(), extend.getParamsValue(sqlToyContext, sqlToyConfig))
				.trim();
		// sql模式
		if (topSize != null) {
			realSql = realSql + " limit " + topSize;
		}
		if (sqlToyContext.isDebug()) {
			logger.debug("findTopByElastic sql={}", realSql);
		}
		DataSetResult result = ElasticSearchUtils.executeQuery(sqlToyContext, sqlToyConfig, realSql,
				(Class) extend.resultType, extend.humpMapLabel);
		return result.getRows();
	}

}
