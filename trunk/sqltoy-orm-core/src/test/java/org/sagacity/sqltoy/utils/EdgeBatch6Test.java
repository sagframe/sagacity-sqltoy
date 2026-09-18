package org.sagacity.sqltoy.utils;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.ByteArrayInputStream;
import java.nio.charset.StandardCharsets;
import java.util.HashSet;
import java.util.Set;

import org.junit.jupiter.api.Test;
import org.sagacity.sqltoy.utils.CollectionUtil;
import org.sagacity.sqltoy.utils.DateUtil;
import org.sagacity.sqltoy.utils.ExpressionUtil;
import org.sagacity.sqltoy.utils.XMLUtil;

/**
 * 回归测试：(24)Set传入combineQueryInStr不再CCE;(25)脏日期两级解析失败有日志(行为不变返回null);
 * (28)calculate失败返回原表达式(契约不变)但日志带表达式内容;(56)XXE防护——含doctype的xml被拒
 */
public class EdgeBatch6Test {

	@Test
	public void setInputToCombineQueryInStrNoCce() {
		Set<String> values = new HashSet<String>();
		values.add("A1");
		values.add("B2");
		// 修复前:judgeObjectDimen将HashSet强转List直接ClassCastException
		String result = assertDoesNotThrow(() -> SqlUtil.combineQueryInStr(values, null, null, true));
		assertTrue(result.contains("'A1'") && result.contains("'B2'"), "实际:" + result);
	}

	@Test
	public void dirtyDateParseFailureStillReturnsNull() {
		// 契约不变:完全无法解析的日期返回null(修复后补了warn日志)
		assertNull(DateUtil.parse("not-a-date-at-all", "yyyy-MM-dd"));
		// 正常日期不受影响
		assertEquals(2025, DateUtil.getYear(DateUtil.parse("2025-06-15", "yyyy-MM-dd")));
	}

	@Test
	public void calculateFailureReturnsOriginalWithLogging() {
		// 契约不变:计算失败返回原表达式字符串(调用方容错依赖此行为)
		assertEquals("1++2", ExpressionUtil.calculate("1++2"));
		// 正常计算
		assertEquals("3.0", ExpressionUtil.calculate("1+2"));
	}

	@Test
	public void xxeDoctypeDeclRejected() {
		// 含DOCTYPE声明的xml(XXE载荷标准形态):加固后解析被拒
		String xxePayload = "<?xml version=\"1.0\"?><!DOCTYPE foo [<!ENTITY xxe SYSTEM \"file:///etc/passwd\">]>"
				+ "<root>&xxe;</root>";
		assertThrows(Exception.class, () -> XMLUtil.readXML(
				new ByteArrayInputStream(xxePayload.getBytes(StandardCharsets.UTF_8)), "UTF-8", false,
				(doc, root) -> "ok"));
	}

	@Test
	public void normalXmlStillParses() throws Exception {
		String normalXml = "<?xml version=\"1.0\"?><root><item>value</item></root>";
		Object result = XMLUtil.readXML(new ByteArrayInputStream(normalXml.getBytes(StandardCharsets.UTF_8)),
				"UTF-8", false, (doc, root) -> root.getNodeName());
		assertEquals("root", result);
	}

	@SuppressWarnings("unused")
	private static void keep(CollectionUtil cu) {
	}
}
