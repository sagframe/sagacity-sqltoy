package org.sagacity.sqltoy.plugins.nosql;

import java.lang.reflect.Array;
import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.Locale;
import java.util.Map;

import org.sagacity.sqltoy.plugins.json.JsonArray;
import org.sagacity.sqltoy.plugins.json.JsonObject;
import org.sagacity.sqltoy.plugins.json.JsonProviders;
import org.sagacity.sqltoy.SqlToyContext;
import org.sagacity.sqltoy.config.model.ElasticEndpoint;
import org.sagacity.sqltoy.config.model.NoSqlConfigModel;
import org.sagacity.sqltoy.config.model.NoSqlFieldsModel;
import org.sagacity.sqltoy.config.model.SqlToyConfig;
import org.sagacity.sqltoy.model.inner.DataSetResult;
import org.sagacity.sqltoy.utils.BeanUtil;
import org.sagacity.sqltoy.utils.ResultUtils;
import org.sagacity.sqltoy.utils.StringUtil;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;


/**
 * @project sagacity-sqltoy
 * @description 提供es执行过程处理的工具方法
 * @author zhongxuchen
 * @version v1.0,Date:2018-01-08
 */
public class ElasticSearchUtils {
	/**
	 * 定义全局日志
	 */
	protected final static Logger logger = LoggerFactory.getLogger(ElasticSearchUtils.class);

	/**
	 * 执行实际查询处理
	 * 
	 * @param sqlToyContext
	 * @param sqlToyConfig
	 * @param sql
	 * @param resultClass
	 * @param humpMapLabel
	 * @return
	 * @throws Exception
	 */
	public static DataSetResult executeQuery(SqlToyContext sqlToyContext, SqlToyConfig sqlToyConfig, String sql,
			Class resultClass, Boolean humpMapLabel) throws Exception {
		NoSqlConfigModel noSqlModel = sqlToyConfig.getNoSqlConfigModel();
		ElasticEndpoint esConfig = sqlToyContext.getElasticEndpoint(noSqlModel.getEndpoint());
		// 执行请求并返回json结果
		JsonObject json = HttpClientUtils.doPost(sqlToyContext, noSqlModel, esConfig, sql);
		if (json == null || json.isEmpty()) {
			return new DataSetResult();
		}
		return executeQuery(sqlToyContext, sqlToyConfig, json, resultClass, humpMapLabel);
	}

	/**
	 * 执行实际查询处理(请求已发出的json结果映射重载,供es原生sql游标分页等场景复用)
	 *
	 * @param sqlToyContext
	 * @param sqlToyConfig
	 * @param json          es返回的json结果
	 * @param resultClass
	 * @param humpMapLabel
	 * @return
	 * @throws Exception
	 */
	public static DataSetResult executeQuery(SqlToyContext sqlToyContext, SqlToyConfig sqlToyConfig, JsonObject json,
			Class resultClass, Boolean humpMapLabel) throws Exception {
		NoSqlConfigModel noSqlModel = sqlToyConfig.getNoSqlConfigModel();
		ElasticEndpoint esConfig = sqlToyContext.getElasticEndpoint(noSqlModel.getEndpoint());
		// 原生sql支持(7.5.1 还未支持分页)
		boolean nativeSql = (esConfig.isNativeSql() && noSqlModel.isSqlMode());
		String[] fields = noSqlModel.getFields();
		if (fields == null) {
			if (json.containsKey("columns")) {
				JsonArray cols = json.getJSONArray("columns");
				fields = new String[cols.size()];
				int index = 0;
				for (Object col : cols) {
					fields[index] = (JsonObject.of(col)).getString("name");
					index++;
				}
			} else if (resultClass != null && !Array.class.isAssignableFrom(resultClass)
					&& !Collection.class.isAssignableFrom(resultClass) && !Map.class.isAssignableFrom(resultClass)) {
				fields = BeanUtil.matchSetMethodNames(resultClass);
			}
		}
		DataSetResult resultSet = null;
		if (nativeSql) {
			resultSet = extractSqlFieldValue(sqlToyContext, sqlToyConfig, json, fields);
		} else {
			resultSet = extractFieldValue(sqlToyContext, sqlToyConfig, json, fields);
		}
		MongoElasticOperations.processTranslate(sqlToyContext, sqlToyConfig, resultSet.getRows(),
				resultSet.getLabelNames());
		// 不支持指定查询集合的行列转换
		boolean changedCols = ResultUtils.calculate(sqlToyContext.getDesensitizeProvider(), sqlToyConfig, resultSet,
				null, null);
		// 将结果数据映射到具体对象类型中
		resultSet.setRows(ResultUtils.wrapQueryResult(sqlToyContext, resultSet.getRows(),
				StringUtil.humpFieldNames(resultSet.getLabelNames()), resultSet.getLabelTypes(), resultClass,
				changedCols, humpMapLabel, false, null, null));
		return resultSet;
	}

	/**
	 * elasticsearch6.3 sql
	 * 
	 * @param sqlToyContext
	 * @param sqlToyConfig
	 * @param json
	 * @param fields
	 * @return
	 */
	private static DataSetResult extractSqlFieldValue(SqlToyContext sqlToyContext, SqlToyConfig sqlToyConfig,
			JsonObject json, String[] fields) {
		DataSetResult resultModel = new DataSetResult();
		Object realRoot = json.get("rows");
		if (realRoot == null) {
			return resultModel;
		}
		NoSqlFieldsModel fieldModel = MongoElasticOperations.processFields(fields, null);
		JsonArray rows = JsonArray.of(realRoot);
		JsonArray item;
		List<List<Object>> resultSet = new ArrayList<List<Object>>();
		for (Object row : rows) {
			item = JsonArray.of(row);
			List<Object> result = new ArrayList<Object>();
			for (Object cel : item) {
				result.add(cel);
			}
			resultSet.add(result);
		}
		resultModel.setRows(resultSet);
		resultModel.setLabelNames(fieldModel.getAliasLabels());
		return resultModel;
	}

	/**
	 * 从返回的JSON对象中根据字段属性提取数据并以集合形式返回
	 * 
	 * @param sqlToyContext
	 * @param sqlToyConfig
	 * @param json
	 * @param fields
	 * @return
	 */
	public static DataSetResult extractFieldValue(SqlToyContext sqlToyContext, SqlToyConfig sqlToyConfig,
			JsonObject json, String[] fields) {
		// 聚合数据提取
		if (sqlToyConfig.getNoSqlConfigModel().isHasAggs() || json.getJSONObject("aggregations") != null) {
			return extractAggsFieldValue(sqlToyContext, sqlToyConfig, json, fields);
		} else if (json.containsKey("suggest")) {
			return extractSuggestFieldValue(sqlToyContext, sqlToyConfig, json, fields);
		}
		DataSetResult resultModel = new DataSetResult();
		// 设置总记录数量
		JsonObject hits = json.getJSONObject("hits");
		if (hits != null && hits.containsKey("total")) {
			Object total = hits.get("total");
			if (total instanceof java.util.Map) {
				resultModel.setRecordCount((JsonObject.of(total)).getLong("value"));
			} else {
				resultModel.setRecordCount(Long.parseLong(total.toString()));
			}
		}
		NoSqlConfigModel nosqlConfig = sqlToyConfig.getNoSqlConfigModel();
		List result = new ArrayList();
		String[] valuePath = (nosqlConfig.getValueRoot() == null) ? new String[] { "hits", "hits" }
				: nosqlConfig.getValueRoot();
		JsonObject root = json;
		String lastKey = valuePath[valuePath.length - 1];
		for (int i = 0; i < valuePath.length - 1; i++) {
			if (root != null) {
				root = root.getJSONObject(valuePath[i]);
			} else {
				return resultModel;
			}
		}
		// 路径中间节点缺失时getJSONObject返回null,循环内null检查只覆盖下一轮
		if (root == null) {
			return resultModel;
		}
		Object realRoot = root.get(lastKey);
		if (realRoot == null) {
			return resultModel;
		}
		NoSqlFieldsModel fieldModel = MongoElasticOperations.processFields(fields, null);
		String[] realFields = fieldModel.getFields();
		JsonObject rowJson, sourceData;
		if (realRoot instanceof java.util.List) {
			JsonArray array = JsonArray.of(realRoot);
			for (int i = 0; i < array.size(); i++) {
				rowJson = array.getJSONObject(i);
				// 非聚合,数据取_source
				sourceData = rowJson.getJSONObject("_source");
				addRow(result, sourceData, realFields);
			}
		} else if (realRoot instanceof java.util.Map) {
			addRow(result, JsonObject.of(realRoot), realFields);
		}
		resultModel.setRows(result);
		resultModel.setLabelNames(fieldModel.getAliasLabels());
		return resultModel;
	}

	/**
	 * 提取聚合数据
	 * 
	 * @param sqlToyContext
	 * @param sqlToyConfig
	 * @param json
	 * @param fields
	 * @return
	 */
	private static DataSetResult extractSuggestFieldValue(SqlToyContext sqlToyContext, SqlToyConfig sqlToyConfig,
			JsonObject json, String[] fields) {
		DataSetResult resultModel = new DataSetResult();
		// 切取实际字段{field:aliasName}模式,冒号前面的实际字段
		NoSqlFieldsModel fieldModel = MongoElasticOperations.processFields(fields, null);
		String[] realFields = fieldModel.getFields();
		// 获取json对象的根
		String[] rootPath = (sqlToyConfig.getNoSqlConfigModel().getValueRoot() == null) ? new String[] { "suggest" }
				: sqlToyConfig.getNoSqlConfigModel().getValueRoot();
		Object root = json;
		// 确保第一个路径是聚合统一的名词
		if (!"suggest".equals(rootPath[0].toLowerCase(Locale.ROOT))) {
			root = (JsonObject.of(root)).get("suggest");
		}
		for (String str : rootPath) {
			root = (JsonObject.of(root)).get(str);
			// update 2026-9-14 中间节点缺失时循环内即退出:原实现只在循环结束后判空,
			// 路径中间缺失时下一轮会对null做get(NPE),非对象节点则ClassCastException,
			// 都无法走到下面给出配置错误提示的分支
			if (root == null) {
				break;
			}
		}
		if (root == null) {
			logger.error(
					"please configure the es aggregation query correctly, including whether the fields configuration matches!");
			return resultModel;
		}
		List result = new ArrayList();
		if (root instanceof java.util.Map) {
			processRow(result, JsonObject.of(root), realFields, true);
		} else if (root instanceof java.util.List) {
			JsonArray array = JsonArray.of(root);
			for (Object tmp : array) {
				processRow(result, JsonObject.of(tmp), realFields, true);
			}
		}
		if (result != null) {
			resultModel.setRecordCount(Long.valueOf(result.size()));
		}
		resultModel.setRows(result);
		resultModel.setLabelNames(fieldModel.getAliasLabels());
		return resultModel;
	}

	/**
	 * 提取聚合数据
	 * 
	 * @param sqlToyContext
	 * @param sqlToyConfig
	 * @param json
	 * @param fields
	 * @return
	 */
	public static DataSetResult extractAggsFieldValue(SqlToyContext sqlToyContext, SqlToyConfig sqlToyConfig,
			JsonObject json, String[] fields) {
		DataSetResult resultModel = new DataSetResult();
		// 切取实际字段{field:aliasName}模式,冒号前面的实际字段
		NoSqlFieldsModel fieldModel = MongoElasticOperations.processFields(fields, null);
		String[] realFields = fieldModel.getFields();
		// 获取json对象的根
		String[] rootPath = (sqlToyConfig.getNoSqlConfigModel().getValueRoot() == null)
				? new String[] { "aggregations" }
				: sqlToyConfig.getNoSqlConfigModel().getValueRoot();
		Object root = json;
		// 确保第一个路径是聚合统一的名词
		if (!"aggregations".equals(rootPath[0].toLowerCase(Locale.ROOT))) {
			root = (JsonObject.of(root)).get("aggregations");
		}
		for (String str : rootPath) {
			root = (JsonObject.of(root)).get(str);
			// update 2026-9-14 与extractSuggestFieldValue同步:中间节点缺失时循环内退出,
			// 否则下一轮对null做get直接NPE,走不到下面root==null的配置错误提示分支
			if (root == null) {
				break;
			}
		}
		// 循环取根
		while (root != null && (root instanceof java.util.Map)) {
			JsonObject tmp = JsonObject.of(root);
			if (tmp.containsKey("buckets")) {
				root = tmp.get("buckets");
				break;
			}
			// 判断属性是否跟所取字段有交集，有则表示取到了根
			int matchCnt = 0;
			for (String key : tmp.keySet()) {
				for (String field : realFields) {
					if (key.equalsIgnoreCase(field)) {
						matchCnt++;
						break;
					}
				}
			}
			if (matchCnt > 0) {
				root = tmp;
				break;
			} else if (tmp.keySet().size() == 1) {
				root = tmp.values().iterator().next();
			}
		}
		if (root == null) {
			logger.error(
					"please configure the es aggregation query correctly, including whether the fields configuration matches!");
			return resultModel;
		}
		List result = new ArrayList();
		if (root instanceof java.util.Map) {
			processRow(result, JsonObject.of(root), realFields, false);
		} else if (root instanceof java.util.List) {
			JsonArray array = JsonArray.of(root);
			for (Object tmp : array) {
				processRow(result, JsonObject.of(tmp), realFields, false);
			}
		}
		if (result != null) {
			resultModel.setRecordCount(Long.valueOf(result.size()));
		}
		resultModel.setRows(result);
		resultModel.setLabelNames(fieldModel.getAliasLabels());
		return resultModel;
	}

	/**
	 * 数据集合提取
	 * 
	 * @param result
	 * @param rowJson
	 * @param realFields
	 * @param isSuggest
	 */
	private static void processRow(List result, JsonObject rowJson, String[] realFields, boolean isSuggest) {
		Object root = getRealJSONObject(rowJson, realFields, isSuggest);
		if (root instanceof java.util.Map) {
			JsonObject json = JsonObject.of(root);
			if ((json.containsKey("key") && json.containsKey("doc_count"))) {
				if (isRoot(json, realFields)) {
					addRow(result, json, realFields);
				} else {
					processRow(result, json, realFields, isSuggest);
				}
			} else {
				addRow(result, json, realFields);
			}
		} else if (root instanceof java.util.List) {
			JsonArray array = JsonArray.of(root);
			for (Object tmp : array) {
				processRow(result, JsonObject.of(tmp), realFields, isSuggest);
			}
		}
	}

	/**
	 * 判断是否包含所有字段
	 * 
	 * @param json
	 * @param realFields
	 * @return
	 */
	private static boolean isRoot(JsonObject json, String[] realFields) {
		int mapCnt = 0;
		for (String key : realFields) {
			if (json.containsKey(key)) {
				mapCnt = mapCnt + 1;
			}
		}
		if (mapCnt == 0) {
			return false;
		}
		// 增强兼容性
		if (mapCnt == realFields.length || mapCnt > 1) {
			return true;
		}
		return false;
	}

	/**
	 * 提取数据加入集合
	 * 
	 * @param result
	 * @param rowJson
	 * @param realFields
	 */
	private static void addRow(List result, JsonObject rowJson, String[] realFields) {
		Object cell;
		List row = new ArrayList();
		for (String str : realFields) {
			cell = rowJson.get(str);
			if (cell instanceof java.util.Map) {
				row.add((JsonObject.of(cell)).get("value"));
			} else {
				row.add(cell);
			}
		}
		result.add(row);
	}

	/**
	 * 提取实际json对象
	 * 
	 * @param rowJson
	 * @param realFields
	 * @param isSuggest
	 * @return
	 */
	private static Object getRealJSONObject(JsonObject rowJson, String[] realFields, boolean isSuggest) {
		Object result = rowJson.get("_source");
		if (result != null && result instanceof java.util.Map) {
			return result;
		}
		result = rowJson.get("buckets");
		if (result != null) {
			if (result instanceof java.util.List) {
				return result;
			} else if (result instanceof java.util.Map) {
				return getRealJSONObject(JsonObject.of(result), realFields, isSuggest);
			}
		}
		result = rowJson.get("hits");
		if (result != null) {
			if (result instanceof java.util.List) {
				return result;
			} else if (result instanceof java.util.Map) {
				return getRealJSONObject(JsonObject.of(result), realFields, isSuggest);
			}
		}

		// suggest模式
		if (isSuggest) {
			result = rowJson.get("options");
			if (result != null) {
				if (result instanceof java.util.List) {
					return result;
				} else if (result instanceof java.util.Map) {
					return getRealJSONObject(JsonObject.of(result), realFields, isSuggest);
				}
			}
		}
		if (rowJson.containsKey("key") && rowJson.containsKey("doc_count")) {
			if (isRoot(rowJson, realFields)) {
				return rowJson;
			}
			Object[] keys = rowJson.keySet().toArray();
			for (Object key : keys) {
				if (!"key".equals(key) && !"doc_count".equals(key)) {
					result = rowJson.get(key.toString());
					if (result instanceof java.util.Map) {
						return getRealJSONObject(JsonObject.of(result), realFields, isSuggest);
					}
					return result;
				}
			}
		} else if (rowJson.keySet().size() == 1) {
			// 单一取值
			if (rowJson.keySet().iterator().next().equalsIgnoreCase(realFields[0]) && realFields.length == 1) {
				return rowJson;
			}
			result = rowJson.values().iterator().next();
			if (result instanceof java.util.Map) {
				JsonObject tmp = JsonObject.of(result);
				// {value:xxx} 模式
				if (tmp.keySet().size() == 1
						&& "value".equals(tmp.keySet().iterator().next().toLowerCase(Locale.ROOT))) {
					return rowJson;
				}
				return getRealJSONObject(tmp, realFields, isSuggest);
			} else if (result instanceof java.util.List) {
				return result;
			}
		}
		return rowJson;
	}
}
