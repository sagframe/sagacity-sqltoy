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
import org.sagacity.sqltoy.model.Page;
import org.sagacity.sqltoy.model.QueryExecutor;
import org.sagacity.sqltoy.model.inner.DataSetResult;
import org.sagacity.sqltoy.model.inner.QueryExecutorExtend;
import org.sagacity.sqltoy.utils.BeanUtil;
import org.sagacity.sqltoy.utils.HttpClientUtils;
import org.sagacity.sqltoy.utils.MongoElasticUtils;
import org.sagacity.sqltoy.utils.QueryExecutorBuilder;
import org.sagacity.sqltoy.utils.ResultUtils;
import org.sagacity.sqltoy.utils.StringUtil;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.alibaba.fastjson.JSON;
import com.alibaba.fastjson.JSONArray;
import com.alibaba.fastjson.JSONObject;

/**
 * @project sagacity-sqltoy
 * @description elasticSearch的插件
 * @author zhongxuchen
 * @version v1.0,Date:2018年1月3日
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
	public static Page findPage(SqlToyContext sqlToyContext, SqlToyConfig sqlToyConfig, Page pageModel,
			QueryExecutor queryExecutor) throws Exception {
		String realMql = "";
		JSONObject jsonQuery = null;
		QueryExecutorExtend extend = queryExecutor.getInnerModel();
		// update 2022-6-16 补全参数统一构造处理
		QueryExecutorBuilder.initQueryExecutor(sqlToyContext, extend, sqlToyConfig, false);
		try {
			realMql = MongoElasticUtils.wrapES(sqlToyConfig, extend.getParamsName(sqlToyConfig),
					extend.getParamsValue(sqlToyContext, sqlToyConfig)).trim();
			jsonQuery = JSON.parseObject(realMql);
			jsonQuery.remove("from");
			jsonQuery.remove("FROM");
			jsonQuery.remove("size");
			jsonQuery.remove("SIZE");
			jsonQuery.put("from", (pageModel.getPageNo() - 1) * pageModel.getPageSize());
			jsonQuery.put("size", pageModel.getPageSize());
		} catch (Exception e) {
			logger.error("分页解析es原生json错误,请检查json串格式是否正确!错误信息:{},json={}", e.getMessage(), realMql);
			throw e;
		}

		Page page = new Page();
		page.setPageNo(pageModel.getPageNo());
		page.setPageSize(pageModel.getPageSize());
		DataSetResult result = executeQuery(sqlToyContext, sqlToyConfig, jsonQuery, (Class) extend.resultType,
				extend.humpMapLabel);
		page.setRows(result.getRows());
		page.setRecordCount(result.getRecordCount());
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
		QueryExecutorExtend extend = queryExecutor.getInnerModel();
		// update 2022-6-16 补全参数统一构造处理
		QueryExecutorBuilder.initQueryExecutor(sqlToyContext, extend, sqlToyConfig, false);
		try {
			realMql = MongoElasticUtils.wrapES(sqlToyConfig, extend.getParamsName(sqlToyConfig),
					extend.getParamsValue(sqlToyContext, sqlToyConfig)).trim();
			jsonQuery = JSON.parseObject(realMql);
			if (topSize != null) {
				jsonQuery.remove("from");
				jsonQuery.remove("FROM");
				jsonQuery.remove("size");
				jsonQuery.remove("SIZE");
				jsonQuery.put("from", 0);
				jsonQuery.put("size", topSize);
			}
		} catch (Exception e) {
			logger.error("解析es原生json错误,请检查json串格式是否正确!错误信息:{},json={}", e.getMessage(), realMql);
			throw e;
		}
		DataSetResult result = executeQuery(sqlToyContext, sqlToyConfig, jsonQuery, (Class) extend.resultType,
				extend.humpMapLabel);
		return result.getRows();
	}

	/**
	 * @todo 执行实际查询处理
	 * @param sqlToyContext
	 * @param sqlToyConfig
	 * @param jsonQuery
	 * @param resultClass
	 * @param humpMapLabel
	 * @return
	 * @throws Exception
	 */
	private static DataSetResult executeQuery(SqlToyContext sqlToyContext, SqlToyConfig sqlToyConfig,
			JSONObject jsonQuery, Class resultClass, Boolean humpMapLabel) throws Exception {
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
		} else if (resultClass != null && !Array.class.isAssignableFrom(resultClass)
				&& !Collection.class.isAssignableFrom(resultClass) && !Map.class.isAssignableFrom(resultClass)) {
			fields = BeanUtil.matchSetMethodNames(resultClass);
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
		boolean changedCols = ResultUtils.calculate(sqlToyContext.getDesensitizeProvider(), sqlToyConfig, resultSet,
				null, null);
		// 将结果数据映射到具体对象类型中
		resultSet.setRows(ResultUtils.wrapQueryResult(sqlToyContext, resultSet.getRows(),
				StringUtil.humpFieldNames(resultSet.getLabelNames()), resultClass, changedCols, humpMapLabel, false,
				null, null));
		return resultSet;
	}
}
