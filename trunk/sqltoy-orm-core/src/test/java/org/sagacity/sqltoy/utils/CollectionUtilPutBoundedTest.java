package org.sagacity.sqltoy.utils;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.concurrent.ConcurrentHashMap;

import org.junit.jupiter.api.Test;

/**
 * 回归测试:有界缓存的超限回收(CollectionUtil.putBounded)
 *
 * <p>
 * 演进史:先是"size() &lt; MAX 才 put"——写满即永久失效(动态列/动态sql继续增长时每次全量重算,
 * 是性能悬崖而非缓存淘汰);中间版改成"超限整体clear"(断崖没了,但热键全部丢弃);
 * 现为"达上限回收约1/4后写入新键",既保证新键真正入缓存,也保留约3/4既有条目。
 */
public class CollectionUtilPutBoundedTest {

	private static final int MAX = 40;

	@Test
	public void belowLimitKeepsEveryKey() {
		ConcurrentHashMap<Integer, String> cache = new ConcurrentHashMap<>();
		for (int i = 0; i < MAX; i++) {
			CollectionUtil.putBounded(cache, i, "v" + i, MAX);
		}
		assertEquals(MAX, cache.size());
		for (int i = 0; i < MAX; i++) {
			assertEquals("v" + i, cache.get(i));
		}
	}

	@Test
	public void overflowEvictsQuarterThenCachesNewKey() {
		ConcurrentHashMap<String, String> cache = new ConcurrentHashMap<>();
		for (int i = 0; i < MAX; i++) {
			CollectionUtil.putBounded(cache, "k" + i, "v" + i, MAX);
		}
		// 新键必须真正写入(旧口径会直接丢弃,后续每次查询都重新计算)
		CollectionUtil.putBounded(cache, "new", "vNew", MAX);
		assertEquals("vNew", cache.get("new"));
		// MAX - MAX/4 + 1:回收1/4后写入1个
		assertEquals(MAX - (MAX >>> 2) + 1, cache.size());
		// 保留项的值未串位,且确实保留了3/4
		int survived = 0;
		for (int i = 0; i < MAX; i++) {
			String value = cache.get("k" + i);
			if (value != null) {
				assertEquals("v" + i, value);
				survived++;
			}
		}
		assertEquals(MAX - (MAX >>> 2), survived);
	}

	@Test
	public void repeatedOverflowNeverExceedsLimit() {
		ConcurrentHashMap<Integer, Integer> cache = new ConcurrentHashMap<>();
		for (int i = 0; i < 500; i++) {
			CollectionUtil.putBounded(cache, i, i, 10);
			// 单线程写入:任何时刻都不能突破上限,否则等于无界
			assertTrue(cache.size() <= 10, "size=" + cache.size() + " at insert " + i);
			// 本次写入的键必须在缓存中(不是"超限即不缓存")
			assertEquals(Integer.valueOf(i), cache.get(i));
		}
	}

	@Test
	public void nonPositiveLimitMeansUnbounded() {
		ConcurrentHashMap<Integer, Integer> cache = new ConcurrentHashMap<>();
		for (int i = 0; i < 100; i++) {
			CollectionUtil.putBounded(cache, i, i, 0);
		}
		assertEquals(100, cache.size());
	}

	@Test
	public void nullInputsAreIgnored() {
		ConcurrentHashMap<String, String> cache = new ConcurrentHashMap<>();
		CollectionUtil.putBounded(cache, null, "v", MAX);
		CollectionUtil.putBounded(cache, "k", null, MAX);
		CollectionUtil.putBounded(null, "k", "v", MAX);
		assertTrue(cache.isEmpty());
		assertNull(cache.get("k"));
	}

	@Test
	public void keepsPutIfAbsentSemantics() {
		// 与既有缓存用法一致:已存在的键不被覆盖(不引入"后写覆盖"的行为变化)
		ConcurrentHashMap<String, String> cache = new ConcurrentHashMap<>();
		cache.put("k", "old");
		CollectionUtil.putBounded(cache, "k", "new", MAX);
		assertEquals("old", cache.get("k"));
	}
}
