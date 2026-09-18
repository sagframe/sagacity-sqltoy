package org.sagacity.sqltoy.plugins.nosql;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.Arrays;

import org.junit.jupiter.api.Test;
import org.sagacity.sqltoy.config.model.NoSqlConfigModel;
import org.sagacity.sqltoy.config.model.SqlToyConfig;
import org.sagacity.sqltoy.model.inner.DataSetResult;

import com.alibaba.fastjson2.JSON;
import com.alibaba.fastjson2.JSONObject;

/**
 * es取值路径中间节点缺失的回归:valueRoot配置的路径在返回json中不存在时,
 * 必须给出配置错误提示并返回空结果,而不是在循环内对null继续get抛NPE
 */
public class ElasticSearchUtilsRootPathTest {

	private static SqlToyConfig config(boolean hasAggs, String... valueRoot) {
		SqlToyConfig config = new SqlToyConfig("esRootPathTest");
		NoSqlConfigModel noSqlConfig = new NoSqlConfigModel();
		noSqlConfig.setHasAggs(hasAggs);
		noSqlConfig.setValueRoot(valueRoot);
		config.setNoSqlConfigModel(noSqlConfig);
		return config;
	}

	private static void assertEmptyResult(DataSetResult result) {
		assertTrue(result.getRows() == null || result.getRows().isEmpty(),
				"应返回空结果而不是异常,实际:" + result.getRows());
	}

	@Test
	public void suggestMiddleNodeMissingReturnsEmpty() {
		JSONObject json = JSON.parseObject("{\"suggest\":{\"my_suggest\":{\"options\":[]}}}");
		// 路径中间节点missing不存在,其后还有deeper——修复前下一轮对null做get直接NPE
		DataSetResult result = assertDoesNotThrow(() -> ElasticSearchUtils.extractFieldValue(null,
				config(false, "suggest", "missing", "deeper"), json, new String[] { "key" }));
		assertEmptyResult(result);
	}

	@Test
	public void suggestLeafNodeMissingReturnsEmpty() {
		JSONObject json = JSON.parseObject("{\"suggest\":{\"my_suggest\":{}}}");
		DataSetResult result = assertDoesNotThrow(() -> ElasticSearchUtils.extractFieldValue(null,
				config(false, "suggest", "my_suggest", "no_such_field"), json, new String[] { "key" }));
		assertEmptyResult(result);
	}

	@Test
	public void aggsMiddleNodeMissingReturnsEmpty() {
		JSONObject json = JSON.parseObject("{\"aggregations\":{\"group_by\":{\"buckets\":[]}}}");
		// extractAggsFieldValue为公开方法:同样在循环内退出,由后续root==null分支给出配置提示
		DataSetResult result = assertDoesNotThrow(() -> ElasticSearchUtils.extractAggsFieldValue(null,
				config(true, "aggregations", "missing", "deeper"), json, new String[] { "key" }));
		assertEmptyResult(result);
	}

	@Test
	public void aggsExistingPathStillExtracted() {
		JSONObject json = JSON.parseObject(
				"{\"aggregations\":{\"group_by\":{\"buckets\":[{\"key\":\"A\",\"doc_count\":2}]}}}");
		DataSetResult result = ElasticSearchUtils.extractAggsFieldValue(null,
				config(true, "aggregations", "group_by", "buckets"), json, new String[] { "key" });
		assertTrue(result.getRows() != null && result.getRows().size() == 1,
				"路径存在时应正常取到数据,实际:" + result.getRows());
		assertTrue(result.getRows().toString().contains("A"), "实际:" + result.getRows());
		assertTrue(Arrays.asList(result.getLabelNames()).contains("key"), "实际:" + Arrays.toString(result.getLabelNames()));
	}
}
