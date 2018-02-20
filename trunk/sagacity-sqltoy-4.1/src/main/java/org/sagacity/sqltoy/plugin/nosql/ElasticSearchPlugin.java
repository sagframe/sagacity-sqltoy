/**
 * 
 */
package org.sagacity.sqltoy.plugin.nosql;

import static java.lang.System.out;

import java.util.List;

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.sagacity.sqltoy.SqlToyContext;
import org.sagacity.sqltoy.config.model.NoSqlConfigModel;
import org.sagacity.sqltoy.config.model.SqlToyConfig;
import org.sagacity.sqltoy.executor.QueryExecutor;
import org.sagacity.sqltoy.model.DataSetResult;
import org.sagacity.sqltoy.model.PaginationModel;
import org.sagacity.sqltoy.utils.BeanUtil;
import org.sagacity.sqltoy.utils.HttpClientUtils;
import org.sagacity.sqltoy.utils.MongoElasticUtils;
import org.sagacity.sqltoy.utils.ResultUtils;

import com.alibaba.fastjson.JSON;
import com.alibaba.fastjson.JSONArray;
import com.alibaba.fastjson.JSONObject;

/**
 * @project sagacity-sqltoy4.1
 * @description elasticSearch的插件
 * @author chenrenfei <a href="mailto:zhongxuchen@gmail.com">联系作者</a>
 * @version id:ElasticSearchPlugin.java,Revision:v1.0,Date:2018年1月3日
 */
public class ElasticSearchPlugin {
	/**
	 * 定义全局日志
	 */
	protected final static Logger logger = LogManager.getLogger(ElasticSearchPlugin.class);

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
		JSONObject jsonQuery = JSON.parseObject(realMql);
		jsonQuery.fluentRemove("from");
		jsonQuery.fluentRemove("FROM");
		jsonQuery.fluentRemove("size");
		jsonQuery.fluentRemove("SIZE");
		jsonQuery.fluentPut("from", (pageModel.getPageNo() - 1) * pageModel.getPageSize());
		jsonQuery.fluentPut("size", pageModel.getPageSize());

		if (sqlToyContext.isDebug()) {
			out.println("execute eql={" + jsonQuery.toJSONString() + "}");
		}
		PaginationModel page = new PaginationModel();
		page.setPageNo(pageModel.getPageNo());
		page.setPageSize(pageModel.getPageSize());
		DataSetResult result = executeQuery(sqlToyContext, sqlToyConfig, jsonQuery,
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
		JSONObject jsonQuery = JSON.parseObject(realMql);
		if (topSize != null) {
			jsonQuery.fluentRemove("from");
			jsonQuery.fluentRemove("FROM");
			jsonQuery.fluentRemove("size");
			jsonQuery.fluentRemove("SIZE");
			jsonQuery.fluentPut("from", 0);
			jsonQuery.fluentPut("size", topSize);
		}
		if (sqlToyContext.isDebug()) {
			out.println("execute eql={" + jsonQuery.toJSONString() + "}");
		}
		DataSetResult result = executeQuery(sqlToyContext, sqlToyConfig, jsonQuery,
				(queryExecutor.getResultType() == null) ? null : queryExecutor.getResultType().getTypeName());
		return result.getRows();
	}

	/**
	 * @todo 执行实际查询处理
	 * @param sqlToyContext
	 * @param sqlToyConfig
	 * @param jsonQuery
	 * @param resultClass
	 * @return
	 * @throws Exception
	 */
	private static DataSetResult executeQuery(SqlToyContext sqlToyContext, SqlToyConfig sqlToyConfig,
			JSONObject jsonQuery, String resultClass) throws Exception {
		NoSqlConfigModel noSqlModel = sqlToyConfig.getNoSqlConfigModel();
		String source = "_source";
		// 是否设置了fields
		boolean hasFields = false;
		if (jsonQuery.containsKey(source)) {
			hasFields = true;
		} else if (jsonQuery.containsKey(source.toUpperCase())) {
			hasFields = true;
			source = source.toUpperCase();
		}
		String[] fields = null;
		if (noSqlModel.getFields() != null) {
			fields = noSqlModel.getFields();
			// 没有设置显示字段,且是非聚合查询时将配置的fields设置到查询json中
			if (!hasFields && !noSqlModel.isHasAggs()) {
				JSONArray array = new JSONArray();
				for (String field : fields)
					array.add(field);
				jsonQuery.fluentPut("_source", array);
			}
		} else if (hasFields) {
			Object[] array = (Object[]) jsonQuery.getJSONArray(source).toArray();
			fields = new String[array.length];
			for (int i = 0; i < fields.length; i++)
				fields[i] = array[i].toString();
		} else if (resultClass != null && !resultClass.equalsIgnoreCase("map")
				&& !resultClass.equalsIgnoreCase("hashmap") && !resultClass.equalsIgnoreCase("linkedHashMap")
				&& !resultClass.equalsIgnoreCase("linkedMap")) {
			fields = BeanUtil.matchSetMethodNames(Class.forName(resultClass));
		}
		// 执行请求
		JSONObject json = HttpClientUtils.doPost(sqlToyContext, noSqlModel, jsonQuery);
		DataSetResult resultSet = ElasticSearchUtils.extractFieldValue(sqlToyContext, sqlToyConfig, json, fields);
		MongoElasticUtils.processTranslate(sqlToyContext, sqlToyConfig, resultSet.getRows(), fields);

		// 不支持指定查询集合的行列转换
		ResultUtils.calculate(sqlToyConfig, resultSet, null, sqlToyContext.isDebug());
		// 将结果数据映射到具体对象类型中
		resultSet.setRows(MongoElasticUtils.wrapResultClass(resultSet.getRows(), fields, resultClass));
		return resultSet;
	}

}
