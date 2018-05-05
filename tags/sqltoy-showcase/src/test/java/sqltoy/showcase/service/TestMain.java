/**
 * 
 */
package sqltoy.showcase.service;

import java.io.File;
import java.util.ArrayList;
import java.util.List;

import org.aspectj.util.FileUtil;
import org.sagacity.sqltoy.config.model.NoSqlConfigModel;

import com.alibaba.fastjson.JSON;
import com.alibaba.fastjson.JSONArray;
import com.alibaba.fastjson.JSONObject;

/**
 * @project sqltoy-showcase
 * @description 请在此说明类的功能
 * @author chenrenfei <a href="mailto:zhongxuchen@gmail.com">联系作者</a>
 * @version id:TestMain.java,Revision:v1.0,Date:2017年10月28日
 */
public class TestMain {

	public static List<List> parse(Object aggs, NoSqlConfigModel noSqlConfigModel, String[] fields) {
		String[] realFields = new String[fields.length];
		int index = 0;
		for (String field : fields) {
			if (field.indexOf(":") != -1)
				realFields[index] = field.substring(0, field.indexOf(":"));
			else
				realFields[index] = field;
			index++;
		}
		String[] rootPath = (noSqlConfigModel.getValueRoot() == null) ? new String[] { "aggregations" }
				: noSqlConfigModel.getValueRoot();
		Object root = aggs;
		if (!rootPath[0].equalsIgnoreCase("aggregations"))
			root = ((JSONObject) root).get("aggregations");
		for (String str : rootPath) {
			root = ((JSONObject) root).get(str);
			// System.err.println(root);
		}
		while (root != null && (root instanceof JSONObject)) {
			JSONObject tmp = (JSONObject) root;
			if (tmp.containsKey("buckets")) {
				root = tmp.get("buckets");
				break;
			}
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

		List result = new ArrayList();
		String fieldName;
		boolean isField;
		String alias;
		JSONObject rowJson;
		Object cell;
		if (root instanceof JSONObject) {
			rowJson = (JSONObject) root;
			List row = new ArrayList();
			for (String str : realFields) {
				cell = rowJson.get(str);
				if (cell instanceof JSONObject) {
					row.add(((JSONObject) cell).get("value"));
				} else
					row.add(cell);

			}
			result.add(row);
		} else if (root instanceof JSONArray) {
			JSONArray array = (JSONArray) root;
			for (Object tmp : array) {
				rowJson = (JSONObject) tmp;
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
		}
		return result;
	}

	public static void main(String[] args) throws Exception {
		String compareValue="15+4";
		
		System.err.println(Double.parseDouble(compareValue));
	}
}
