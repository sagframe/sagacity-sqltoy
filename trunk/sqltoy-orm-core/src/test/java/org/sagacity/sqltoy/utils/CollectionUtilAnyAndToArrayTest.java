package org.sagacity.sqltoy.utils;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.Test;

/**
 * 回归测试：(a)any()比对到null元素跳过继续,不提前短路结束整个判定(修复前
 * 数组首个null元素直接决定结果,后续可匹配元素被漏判);(b)toArray未识别类型 原样返回而非result为null时取length抛NPE
 */
public class CollectionUtilAnyAndToArrayTest {

	// any存在(Object,Object...)重载,用显式数组锁定被测的(Object,boolean,Object...)重载
	private static boolean any(Object value, boolean ignoreCase, Object... ary) {
		return CollectionUtil.any(value, ignoreCase, ary);
	}

	@Test
	public void nullElementSkippedNotShortCircuit() {
		// 修复前:首个元素为null即返回value==s=false,漏判后面的"a"
		assertTrue(any("a", false, null, "a"));
		assertTrue(any("a", true, null, "A"));
		assertFalse(any("a", false, null, "b"));
	}

	@Test
	public void nullValueMatchesNullElementAnywhere() {
		// 修复前:value为null时只看第一个元素,后面有null也判false
		assertTrue(any(null, false, "a", null));
		assertTrue(any(null, false, null, "a"));
		assertFalse(any(null, false, "a", "b"));
	}

	@Test
	public void normalMatchingUnchanged() {
		assertTrue(any("a", false, "x", "a"));
		assertFalse(any("a", false, "x", "b"));
		assertTrue(any("a", true, "A"));
		assertFalse(any(null, false));
	}

	@Test
	public void unknownArrayTypeReturnsOriginal() {
		String[] values = { "1", "2" };
		// 修复前:未识别类型result为null,取result.length抛NPE
		assertArrayEquals(values, CollectionUtil.toArray(values, "foo-type"));
	}

	@Test
	public void knownArrayTypesUnchanged() {
		Object[] ints = CollectionUtil.toArray(new String[] { "1", "2" }, "integer");
		assertEquals(2, ints.length);
		assertEquals(1, ((Number) ints[0]).intValue());
		assertEquals(2, ((Number) ints[1]).intValue());
	}
}
