/**
 * 
 */
package org.sagacity.sqltoy.plugin.nosql;

import java.util.ArrayList;
import java.util.List;

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.sagacity.sqltoy.SqlToyContext;
import org.sagacity.sqltoy.config.model.NoSqlConfigModel;
import org.sagacity.sqltoy.config.model.SqlToyConfig;
import org.sagacity.sqltoy.model.DataSetResult;
import org.sagacity.sqltoy.utils.BeanUtil;
import org.sagacity.sqltoy.utils.CollectionUtil;
import org.sagacity.sqltoy.utils.HttpClientUtils;
import org.sagacity.sqltoy.utils.MongoElasticUtils;
import org.sagacity.sqltoy.utils.ResultUtils;

import com.alibaba.fastjson.JSONArray;
import com.alibaba.fastjson.JSONObject;

/**
 * @project sagacity-sqltoy4.1
 * @description 提供es执行过程处理的工具方法
 * @author chenrenfei <a href="mailto:zhongxuchen@gmail.com">联系作者</a>
 * @version id:ElasticSearchUtils.java,Revision:v1.0,Date:2018年1月8日
 */
public class ElasticSearchUtils {
	/**
	 * 定义全局日志
	 */
	protected final static Logger logger = LogManager.getLogger(ElasticSearchUtils.class);

	/**
	 * @todo 执行实际查询处理
	 * @param sqlToyContext
	 * @param sqlToyConfig
	 * @param noSqlModel
	 * @param jsonQuery
	 * @param resultClass
	 * @return
	 * @throws Exception
	 */
	public static DataSetResult executeQuery(SqlToyContext sqlToyContext, SqlToyConfig sqlToyConfig, String sql,
			String resultClass) throws Exception {
		NoSqlConfigModel noSqlModel = sqlToyConfig.getNoSqlConfigModel();
		// 执行请求并返回json结果
		JSONObject json = HttpClientUtils.doPost(sqlToyContext, noSqlModel, sql);
		if (json == null || json.isEmpty()) {
			return new DataSetResult();
		}
		String[] fields = noSqlModel.getFields();
		if (fields == null && resultClass != null) {
			if (!CollectionUtil.any(resultClass.toLowerCase(),
					new String[] { "map", "hashmap", "linkedhashmap", "linkedmap" }, false)) {
				fields = BeanUtil.matchSetMethodNames(Class.forName(resultClass));
			}
		}
		DataSetResult resultSet = extractFieldValue(sqlToyContext, sqlToyConfig, json, fields);
		MongoElasticUtils.processTranslate(sqlToyContext, sqlToyConfig, resultSet.getRows(), resultSet.getLabelNames());

		// 不支持指定查询集合的行列转换
		ResultUtils.calculate(sqlToyConfig, resultSet, null, sqlToyContext.isDebug());

		// 将结果数据映射到具体对象类型中
		resultSet.setRows(
				MongoElasticUtils.wrapResultClass(resultSet.getRows(), resultSet.getLabelNames(), resultClass));
		return resultSet;
	}

	/**
	 * @todo 从返回的JSON对象中根据字段属性提取数据并以集合形式返回
	 * @param sqlToyContext
	 * @param sqlToyConfig
	 * @param json
	 * @param fields
	 * @return
	 */
	public static DataSetResult extractFieldValue(SqlToyContext sqlToyContext, SqlToyConfig sqlToyConfig,
			JSONObject json, String[] fields) {
		// 聚合数据提取
		if (sqlToyConfig.getNoSqlConfigModel().isHasAggs() || json.getJSONObject("aggregations") != null)
			return extractAggsFieldValue(sqlToyContext, sqlToyConfig, json, fields);
		DataSetResult resultModel = new DataSetResult();
		// 设置总记录数量
		if (json.getJSONObject("hits") != null) {
			Long total = json.getJSONObject("hits").getLong("total");
			resultModel.setTotalCount(total);
		}
		NoSqlConfigModel nosqlConfig = sqlToyConfig.getNoSqlConfigModel();
		List result = new ArrayList();
		String[] valuePath = (nosqlConfig.getValueRoot() == null) ? new String[] { "hits", "hits" }
				: nosqlConfig.getValueRoot();
		JSONObject root = json;
		String lastKey = valuePath[valuePath.length - 1];
		for (int i = 0; i < valuePath.length - 1; i++) {
			if (root != null)
				root = root.getJSONObject(valuePath[i]);
			else
				return resultModel;
		}
		Object realRoot = root.get(lastKey);
		if (realRoot == null)
			return resultModel;
		String[] realFields = new String[fields.length];
		String[] translateFields = new String[fields.length];
		System.arraycopy(fields, 0, realFields, 0, fields.length);
		System.arraycopy(fields, 0, translateFields, 0, fields.length);
		int aliasIndex = 0;
		for (int i = 0; i < realFields.length; i++) {
			aliasIndex = realFields[i].indexOf(":");
			if (aliasIndex != -1) {
				realFields[i] = realFields[i].substring(0, aliasIndex).trim();
				translateFields[i] = translateFields[i].substring(aliasIndex + 1).trim();
			}
		}
		JSONObject rowJson, sourceData;
		if (realRoot instanceof JSONArray) {
			JSONArray array = (JSONArray) realRoot;
			for (int i = 0; i < array.size(); i++) {
				rowJson = (JSONObject) array.get(i);
				// 非聚合,数据取_source
				sourceData = rowJson.getJSONObject("_source");
				addRow(result, sourceData, realFields);
			}
		} else if (realRoot instanceof JSONObject) {
			addRow(result, (JSONObject) realRoot, realFields);
		}
		resultModel.setRows(result);
		resultModel.setLabelNames(translateFields);
		return resultModel;
	}

	/**
	 * @todo 提取聚合数据
	 * @param sqlToyContext
	 * @param sqlToyConfig
	 * @param json
	 * @param fields
	 * @return
	 */
	public static DataSetResult extractAggsFieldValue(SqlToyContext sqlToyContext, SqlToyConfig sqlToyConfig,
			JSONObject json, String[] fields) {
		DataSetResult resultModel = new DataSetResult();
		// 切取实际字段{field:aliasName}模式,冒号前面的实际字段
		String[] realFields = new String[fields.length];
		String[] translateFields = new String[fields.length];
		int index = 0;
		for (String field : fields) {
			if (field.indexOf(":") != -1) {
				realFields[index] = field.substring(0, field.indexOf(":")).trim();
				translateFields[index] = field.substring(field.indexOf(":") + 1).trim();
			} else {
				realFields[index] = field;
				translateFields[index] = field;
			}
			index++;
		}
		// 获取json对象的根
		String[] rootPath = (sqlToyConfig.getNoSqlConfigModel().getValueRoot() == null)
				? new String[] { "aggregations" }
				: sqlToyConfig.getNoSqlConfigModel().getValueRoot();
		Object root = json;
		// 确保第一个路径是聚合统一的名词
		if (!rootPath[0].equalsIgnoreCase("aggregations"))
			root = ((JSONObject) root).get("aggregations");
		for (String str : rootPath) {
			root = ((JSONObject) root).get(str);
		}
		// 循环取根
		while (root != null && (root instanceof JSONObject)) {
			JSONObject tmp = (JSONObject) root;
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
			} else {
				if (tmp.keySet().size() == 1) {
					root = tmp.values().iterator().next();
				}
			}
		}
		if (root == null) {
			logger.error("请正确配置es聚合查询,包括:fields配置是否匹配等!");
			return resultModel;
		}
		List result = new ArrayList();
		if (root instanceof JSONObject) {
			processRow(result, (JSONObject) root, realFields);
		} else if (root instanceof JSONArray) {
			JSONArray array = (JSONArray) root;
			for (Object tmp : array) {
				processRow(result, (JSONObject) tmp, realFields);
			}
		}
		if (result != null)
			resultModel.setTotalCount(Long.valueOf(result.size()));
		resultModel.setRows(result);
		resultModel.setLabelNames(translateFields);
		return resultModel;
	}

	/**
	 * 数据集合提取
	 * 
	 * @param result
	 * @param rowJson
	 * @param realFields
	 */
	private static void processRow(List result, JSONObject rowJson, String[] realFields) {
		Object root = getRealJSONObject(rowJson, realFields);
		if (root instanceof JSONObject) {
			JSONObject json = (JSONObject) root;
			if ((json.containsKey("key") && json.containsKey("doc_count"))) {
				if (isRoot(json, realFields)) {
					addRow(result, json, realFields);
				} else
					processRow(result, json, realFields);
			} else {
				addRow(result, json, realFields);
			}
		} else if (root instanceof JSONArray) {
			JSONArray array = (JSONArray) root;
			for (Object tmp : array) {
				processRow(result, (JSONObject) tmp, realFields);
			}
		}
	}

	/**
	 * @todo 判断是否包含所有字段
	 * @param json
	 * @param realFields
	 * @return
	 */
	private static boolean isRoot(JSONObject json, String[] realFields) {
		int mapCnt = 0;
		for (String key : realFields) {
			if (json.containsKey(key))
				mapCnt = mapCnt + 1;
		}
		if (mapCnt == 0)
			return false;
		// 增强兼容性
		if (mapCnt == realFields.length || mapCnt > 1)
			return true;
		return false;
	}

	/**
	 * @todo 提取数据加入集合
	 * @param result
	 * @param rowJson
	 * @param realFields
	 */
	private static void addRow(List result, JSONObject rowJson, String[] realFields) {
		Object cell;
		List row = new ArrayList();
		for (String str : realFields) {
			cell = rowJson.get(str);
			if (cell instanceof JSONObject) {
				row.add(((JSONObject) cell).get("value"));
			} else
				row.add(cell);
		}
		result.add(row);
	}

	/**
	 * @todo 提取实际json对象
	 * @param rowJson
	 * @param realFields
	 * @return
	 */
	private static Object getRealJSONObject(JSONObject rowJson, String[] realFields) {
		Object result = rowJson.get("_source");
		if (result != null && result instanceof JSONObject)
			return result;
		result = rowJson.get("buckets");
		if (result != null) {
			if (result instanceof JSONArray)
				return result;
			else if (result instanceof JSONObject)
				return getRealJSONObject((JSONObject) result, realFields);
		}
		result = rowJson.get("hits");
		if (result != null) {
			if (result instanceof JSONArray)
				return result;
			else if (result instanceof JSONObject)
				return getRealJSONObject((JSONObject) result, realFields);
		}
		if (rowJson.containsKey("key") && rowJson.containsKey("doc_count")) {
			if (isRoot(rowJson, realFields))
				return rowJson;
			Object[] keys = rowJson.keySet().toArray();
			for (Object key : keys) {
				if (!key.equals("key") && !key.equals("doc_count")) {
					result = rowJson.get(key.toString());
					if (result instanceof JSONObject)
						return getRealJSONObject((JSONObject) result, realFields);
					return result;
				}
			}
		} else if (rowJson.keySet().size() == 1) {
			// 单一取值
			if (rowJson.keySet().iterator().next().equalsIgnoreCase(realFields[0]) && realFields.length == 1)
				return rowJson;
			result = rowJson.values().iterator().next();
			if (result instanceof JSONObject)
				return getRealJSONObject((JSONObject) result, realFields);
		}
		return rowJson;
	}
}
