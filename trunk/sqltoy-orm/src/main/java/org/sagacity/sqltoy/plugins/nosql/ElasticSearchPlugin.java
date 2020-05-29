/**
 * 
 */
package org.sagacity.sqltoy.plugins.nosql;

import java.lang.reflect.Array;
import java.util.ArrayList;
import java.util.Collection;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;

import org.sagacity.sqltoy.SqlToyContext;
import org.sagacity.sqltoy.config.model.ElasticEndpoint;
import org.sagacity.sqltoy.config.model.NoSqlConfigModel;
import org.sagacity.sqltoy.config.model.SqlToyConfig;
import org.sagacity.sqltoy.executor.QueryExecutor;
import org.sagacity.sqltoy.model.DataSetResult;
import org.sagacity.sqltoy.model.PaginationModel;
import org.sagacity.sqltoy.utils.BeanUtil;
import org.sagacity.sqltoy.utils.HttpClientUtils;
import org.sagacity.sqltoy.utils.MongoElasticUtils;
import org.sagacity.sqltoy.utils.ResultUtils;
import org.sagacity.sqltoy.utils.StringUtil;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

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
	protected final static Logger logger = LoggerFactory.getLogger(ElasticSearchPlugin.class);

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
		String realMql = "";
		JSONObject jsonQuery = null;
		try {
			realMql = MongoElasticUtils.wrapES(sqlToyConfig, queryExecutor.getParamsName(sqlToyConfig),
					queryExecutor.getParamsValue(sqlToyContext, sqlToyConfig)).trim();
			jsonQuery = JSON.parseObject(realMql);
			jsonQuery.fluentRemove("from");
			jsonQuery.fluentRemove("FROM");
			jsonQuery.fluentRemove("size");
			jsonQuery.fluentRemove("SIZE");
			jsonQuery.fluentPut("from", (pageModel.getPageNo() - 1) * pageModel.getPageSize());
			jsonQuery.fluentPut("size", pageModel.getPageSize());
		} catch (Exception e) {
			logger.error("分页解析es原生json错误,请检查json串格式是否正确!错误信息:{},json={}", e.getMessage(), realMql);
			throw e;
		}

		PaginationModel page = new PaginationModel();
		page.setPageNo(pageModel.getPageNo());
		page.setPageSize(pageModel.getPageSize());
		DataSetResult result = executeQuery(sqlToyContext, sqlToyConfig, jsonQuery,
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
		String realMql = "";
		JSONObject jsonQuery = null;
		try {
			realMql = MongoElasticUtils.wrapES(sqlToyConfig, queryExecutor.getParamsName(sqlToyConfig),
					queryExecutor.getParamsValue(sqlToyContext, sqlToyConfig)).trim();
			jsonQuery = JSON.parseObject(realMql);
			if (topSize != null) {
				jsonQuery.fluentRemove("from");
				jsonQuery.fluentRemove("FROM");
				jsonQuery.fluentRemove("size");
				jsonQuery.fluentRemove("SIZE");
				jsonQuery.fluentPut("from", 0);
				jsonQuery.fluentPut("size", topSize);
			}
		} catch (Exception e) {
			logger.error("解析es原生json错误,请检查json串格式是否正确!错误信息:{},json={}", e.getMessage(), realMql);
			throw e;
		}
		DataSetResult result = executeQuery(sqlToyContext, sqlToyConfig, jsonQuery,
				(Class) queryExecutor.getResultType());
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
			JSONObject jsonQuery, Class resultClass) throws Exception {
		NoSqlConfigModel noSqlModel = sqlToyConfig.getNoSqlConfigModel();
		ElasticEndpoint esConfig = sqlToyContext.getElasticEndpoint(noSqlModel.getEndpoint());
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
				int aliasIndex;
				for (String field : fields) {
					aliasIndex = field.indexOf(":");
					if (aliasIndex == -1) {
						array.add(field);
					} else {
						array.add(field.substring(0, aliasIndex).trim());
					}
				}
				jsonQuery.fluentPut("_source", array);
			}
		} else if (hasFields) {
			Object[] array = (Object[]) jsonQuery.getJSONArray(source).toArray();
			fields = new String[array.length];
			for (int i = 0; i < fields.length; i++) {
				fields[i] = array[i].toString();
			}
		} else if (resultClass != null) {
			Class superClass = resultClass.getSuperclass();
			if (!resultClass.equals(ArrayList.class) && !resultClass.equals(List.class)
					&& !resultClass.equals(Array.class) && !resultClass.equals(Collection.class)
					&& !resultClass.equals(HashMap.class) && !resultClass.equals(Map.class)
					&& !resultClass.equals(ConcurrentMap.class) && !resultClass.equals(ConcurrentHashMap.class)
					&& !HashMap.class.equals(superClass) && !Map.class.equals(superClass)
					&& !LinkedHashMap.class.equals(superClass) && !ConcurrentHashMap.class.equals(superClass)) {
				fields = BeanUtil.matchSetMethodNames(resultClass);
			}
		}
		if (sqlToyContext.isDebug()) {
			if (logger.isDebugEnabled()) {
				logger.debug("execute elastic eql=" + jsonQuery.toJSONString());
			} else {
				System.out.println("execute elastic eql=" + jsonQuery.toJSONString());
			}
		}

		// 执行请求
		JSONObject json = HttpClientUtils.doPost(sqlToyContext, noSqlModel, esConfig, jsonQuery);
		if (json == null || json.isEmpty()) {
			return new DataSetResult();
		}
		DataSetResult resultSet = ElasticSearchUtils.extractFieldValue(sqlToyContext, sqlToyConfig, json, fields);
		MongoElasticUtils.processTranslate(sqlToyContext, sqlToyConfig, resultSet.getRows(), resultSet.getLabelNames());

		// 不支持指定查询集合的行列转换
		ResultUtils.calculate(sqlToyConfig, resultSet, null);
		// 将结果数据映射到具体对象类型中
		resultSet.setRows(ResultUtils.wrapQueryResult(resultSet.getRows(),
				StringUtil.humpFieldNames(resultSet.getLabelNames()), resultClass));
		return resultSet;
	}
}
