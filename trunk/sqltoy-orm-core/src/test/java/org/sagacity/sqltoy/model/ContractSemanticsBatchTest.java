package org.sagacity.sqltoy.model;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.PrintWriter;
import java.io.StringWriter;
import java.util.Collections;
import java.util.Locale;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.sagacity.sqltoy.exception.DataAccessException;
import org.sagacity.sqltoy.utils.TranslateUtils;

/**
 * 语义/契约批次回归：Map.put null key返回值契约、Set.toArray(null)/containsAll(null)、
 * addAll空集合返回false、键归一Locale.ROOT(土耳其语locale模拟)、
 * DataAccessException不重复打印cause、translate比较值大小写归一
 */
public class ContractSemanticsBatchTest {

	private static final Locale TURKISH = new Locale("tr", "TR");
	private final Locale originalLocale = Locale.getDefault();

	@AfterEach
	public void restoreLocale() {
		Locale.setDefault(originalLocale);
	}

	@Test
	public void mapPutNullKeyReturnsNullPerContract() {
		IgnoreKeyCaseMap<String, Object> map = new IgnoreKeyCaseMap<String, Object>();
		// 修复前:key为null时返回value(违反Map契约,无先前映射应返回null)
		assertNull(map.put(null, "someValue"));
		// null value同样返回null,正常put返回旧值
		assertNull(map.put("k", "v1"));
		assertEquals("v1", map.put("k", "v2"));
	}

	@Test
	public void turkishLocaleKeyNormalization() {
		Locale.setDefault(TURKISH);
		// 土耳其语下裸toLowerCase将"I"转为"ı"(无点),与"i"不等,键映射错乱
		IgnoreCaseSet set = new IgnoreCaseSet();
		set.add("ID");
		assertTrue(set.contains("id"), "Locale.ROOT归一应保证跨大小写命中");
		IgnoreKeyCaseMap<String, Object> map = new IgnoreKeyCaseMap<String, Object>();
		map.put("STAFF_ID", "x");
		assertEquals("x", map.get("staff_id"));
		IgnoreCaseLinkedMap<String, Object> linked = new IgnoreCaseLinkedMap<String, Object>();
		linked.put("STATUS", "y");
		assertEquals("y", linked.get("status"));
	}

	@Test
	public void setToArrayNullThrowsPerContract() {
		IgnoreCaseSet set = new IgnoreCaseSet();
		set.add("a");
		// 修复前:返回null把NPE推迟到调用方;显式类型锁定toArray(T[])重载
		assertThrows(NullPointerException.class, () -> set.toArray((String[]) null));
	}

	@Test
	public void setContainsAllWithNullElementReturnsFalse() {
		IgnoreCaseSet set = new IgnoreCaseSet();
		set.add("a");
		set.add("b");
		// 修复前:null元素被过滤,containsAll([null])误报true
		assertFalse(set.containsAll(Collections.singletonList(null)));
		assertTrue(set.containsAll(java.util.Arrays.asList("A", "b")));
	}

	@Test
	public void addAllEmptyCollectionReturnsFalse() {
		PriorityLimitSizeQueue<String> queue = new PriorityLimitSizeQueue<String>(10);
		// 修复前:恒返回true,违反Collection.addAll契约(未变更应返回false)
		assertFalse(queue.addAll(Collections.<String>emptyList()));
		queue.add("x");
		assertTrue(queue.addAll(Collections.singletonList("y")));
	}

	@Test
	public void dataAccessExceptionCausePrintedOnce() {
		DataAccessException ex = new DataAccessException("outer", new IllegalStateException("cause-marker"));
		StringWriter writer = new StringWriter();
		ex.printStackTrace(new PrintWriter(writer));
		String output = writer.toString();
		// 旧实现额外调用getCause().printStackTrace,cause的异常头会完整出现两遍
		int causeHeaderCount = output.split("java.lang.IllegalStateException", -1).length - 1;
		assertEquals(1, causeHeaderCount, "实际输出:\n" + output);
	}

	@Test
	public void translateCompareValuesCaseNormalized() {
		// 修复前:比较值原样比较,配置大写永远不匹配已转小写的源值
		assertTrue(TranslateUtils.judgeTranslate("active", "eq", new String[] { "ACTIVE" }));
		assertFalse(TranslateUtils.judgeTranslate("active", "eq", new String[] { "DISABLED" }));
		assertTrue(TranslateUtils.judgeTranslate("US", "in", new String[] { "uk", "us" }));
		assertTrue(TranslateUtils.judgeTranslate("B", "out", new String[] { "A", "b" }) == false);
	}
}
