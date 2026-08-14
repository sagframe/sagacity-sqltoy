package org.sagacity.sqltoy.model;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.Test;

/**
 * 回归测试：containsKey(null)与get/put/remove的判空语义对齐返回false,
 * 修复前内部ConcurrentHashMap.containsKey(null)抛NPE
 */
public class IgnoreKeyCaseMapTest {

	@Test
	public void containsNullKeyReturnsFalseInsteadOfNpe() {
		IgnoreKeyCaseMap<String, Object> map = new IgnoreKeyCaseMap<String, Object>();
		map.put("abc", 1);
		// 修复前:NullPointerException
		assertFalse(map.containsKey(null));
		assertNull(map.get(null));
	}

	@Test
	public void containsKeyStillCaseInsensitive() {
		IgnoreKeyCaseMap<String, Object> map = new IgnoreKeyCaseMap<String, Object>();
		map.put("abc", 1);
		assertTrue(map.containsKey("ABC"));
		assertTrue(map.containsKey("aBc"));
		assertFalse(map.containsKey("xyz"));
	}
}
