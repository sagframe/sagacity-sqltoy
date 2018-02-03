/**
 * 
 */
package org.sagacity.sqltoy.plugin.nosql;

import java.util.ArrayList;
import java.util.HashMap;
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
import org.sagacity.sqltoy.utils.StringUtil;

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
		if (sqlToyConfig.getNoSqlConfigModel().isHasAggs())
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

		JSONArray array = root.getJSONArray(lastKey);
		if (array.isEmpty())
			return resultModel;

		String[] realFields = new String[fields.length];
		System.arraycopy(fields, 0, realFields, 0, fields.length);
		int aliasIndex = 0;
		for (int i = 0; i < realFields.length; i++) {
			aliasIndex = realFields[i].indexOf(":");
			if (aliasIndex != -1)
				realFields[i] = realFields[i].substring(0, aliasIndex).trim();
		}
		boolean assignField = (nosqlConfig.getFields() == null) ? false : true;
		HashMap<String, String[]> realFieldsMap = new HashMap<String, String[]>();
		if (!assignField) {
			for (String field : realFields) {
				realFieldsMap.put(field,
						new String[] { field, field.toLowerCase(), field.toUpperCase(),
								StringUtil.humpToSplitStr(field, "_").toLowerCase(),
								StringUtil.humpToSplitStr(field, "_").toUpperCase() });
			}
		}
		JSONObject rowJson, sourceData;
		Object value = null;
		String[] tmpFields;
		for (int i = 0; i < array.size(); i++) {
			rowJson = (JSONObject) array.get(i);
			// 非聚合,数据取_source
			sourceData = rowJson.getJSONObject("_source");
			List row = new ArrayList();
			for (String field : realFields) {
				if (assignField)
					row.add(sourceData.getString(field));
				else {
					tmpFields = realFieldsMap.get(field);
					if (tmpFields != null) {
						for (String s : tmpFields) {
							value = sourceData.getString(s);
							// 字段匹配
							if (value != null) {
								realFieldsMap.put(field, new String[] { s });
								break;
							}
						}
						row.add(value);
					} else
						row.add(null);
				}
			}
			result.add(row);
		}
		resultModel.setRows(result);

		if (!assignField) {
			String[] resultFields = new String[realFields.length];
			String s;
			String[] aliasNames;
			for (int i = 0; i < resultFields.length; i++) {
				s = resultFields[i];
				aliasNames = realFieldsMap.get(s);
				if (aliasNames != null && aliasNames.length == 1) {
					resultFields[i] = aliasNames[0];
				} else
					resultFields[i] = s;
			}
			resultModel.setLabelNames(resultFields);
		} else
			resultModel.setLabelNames(fields);
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
		int index = 0;
		for (String field : fields) {
			if (field.indexOf(":") != -1)
				realFields[index] = field.substring(0, field.indexOf(":"));
			else
				realFields[index] = field;
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
			result.add(processRow((JSONObject) root, realFields));
		} else if (root instanceof JSONArray) {
			JSONArray array = (JSONArray) root;
			for (Object tmp : array) {
				result.add(processRow((JSONObject) tmp, realFields));
			}
		}
		resultModel.setRows(result);
		resultModel.setLabelNames(fields);
		return resultModel;
	}

	private static List processRow(JSONObject rowJson, String[] realFields) {
		Object cell;
		List row = new ArrayList();
		for (String str : realFields) {
			cell = rowJson.get(str);
			if (cell instanceof JSONObject) {
				row.add(((JSONObject) cell).get("value"));
			} else
				row.add(cell);
		}
		return row;
	}
}
