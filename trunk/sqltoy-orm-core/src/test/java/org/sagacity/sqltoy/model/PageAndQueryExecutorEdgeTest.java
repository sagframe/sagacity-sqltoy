package org.sagacity.sqltoy.model;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertEquals;

import org.junit.jupiter.api.Test;
import org.sagacity.sqltoy.exception.DataAccessException;
import org.sagacity.sqltoy.utils.HttpClientUtils;

import com.alibaba.fastjson2.JSONObject;

/**
 * 回归测试批次：(30)Page空结果时getLastPage与getTotalPage一致返回0;
 * (32)QueryExecutor.secureDecrypt(null)不再NPE;(29)elastic错误解析对
 * root_cause缺失/error为字符串/reason兜底等结构防御式提取,不因NPE掩盖真实错误
 */
public class PageAndQueryExecutorEdgeTest {

	@Test
	public void emptyPageLastPageMatchesTotalPage() {
		Page<Object> page = new Page<Object>(10, 1);
		page.setRecordCount(0);
		// 修复前:getLastPage()=1 与 getTotalPage()=0 矛盾
		assertEquals(0, page.getLastPage());
		assertEquals(0, page.getTotalPage());
		assertEquals(0, page.getNextPage());
	}

	@Test
	public void normalPageLastPageUnchanged() {
		Page<Object> page = new Page<Object>(10, 1);
		page.setRecordCount(25);
		assertEquals(3, page.getLastPage());
		assertEquals(3, page.getTotalPage());
		page.setRecordCount(30);
		assertEquals(3, page.getLastPage());
		assertEquals(1, page.getFirstPage());
	}

	@Test
	public void secureDecryptNullColumnsNoNpe() {
		// 修复前:columns.length直接NPE(secureMask等同类方法均有判空)
		QueryExecutor executor = assertDoesNotThrow(() -> new QueryExecutor("select * from t"));
		assertDoesNotThrow(() -> executor.secureDecrypt((String[]) null));
		assertDoesNotThrow(() -> executor.secureDecrypt("col1", "col2"));
	}

	@Test
	public void elasticErrorWithRootCauseExtracted() {
		String result = "{\"error\":{\"root_cause\":[{\"type\":\"x\",\"reason\":\"boom\"}]}}";
		try {
			HttpClientUtils.parseElasticResult(result);
			throw new AssertionError("应抛DataAccessException");
		} catch (DataAccessException ex) {
			// 标准结构:取root_cause[0]
			assertTrue(ex.getMessage().contains("boom"), "实际:" + ex.getMessage());
		}
	}

	@Test
	public void elasticErrorWithoutRootCauseFallsToReason() {
		// 修复前:getJSONArray("root_cause")为null直接NPE
		String result = "{\"error\":{\"type\":\"x\",\"reason\":\"only reason\"}}";
		try {
			HttpClientUtils.parseElasticResult(result);
			throw new AssertionError("应抛DataAccessException");
		} catch (DataAccessException ex) {
			assertTrue(ex.getMessage().contains("only reason"), "实际:" + ex.getMessage());
		}
	}

	@Test
	public void elasticErrorAsPlainString() {
		// error本身是字符串的版本/网关场景
		String result = "{\"error\":\"gateway timeout\"}";
		try {
			HttpClientUtils.parseElasticResult(result);
			throw new AssertionError("应抛DataAccessException");
		} catch (DataAccessException ex) {
			assertTrue(ex.getMessage().contains("gateway timeout"), "实际:" + ex.getMessage());
		}
	}

	@Test
	public void elasticNormalResultParsed() {
		JSONObject json = HttpClientUtils.parseElasticResult("{\"hits\":{\"total\":3}}");
		assertEquals(3, json.getJSONObject("hits").getIntValue("total"));
	}

	private static void assertTrue(boolean condition, String message) {
		org.junit.jupiter.api.Assertions.assertTrue(condition, message);
	}

	private static class AssertionError extends java.lang.AssertionError {
		AssertionError(String message) {
			super(message);
		}
	}
}
