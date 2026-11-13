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

	@Test
	public void concurrentBurstEvictsOnlyOnce() throws Exception {
		// update 2026-11-12 CAS单飞门闩:写满后16线程同时put新键,只允许一个线程执行1/4回收。
		// 修复前每个线程各自回收1/4,16线程近乎清空缓存(旧键存活≈0);修复后旧键存活≈3/4
		int max = 400;
		int threads = 16;
		ConcurrentHashMap<String, String> cache = new ConcurrentHashMap<>();
		for (int i = 0; i < max; i++) {
			cache.put("old" + i, "v" + i);
		}
		java.util.concurrent.CyclicBarrier barrier = new java.util.concurrent.CyclicBarrier(threads);
		java.util.concurrent.ExecutorService pool = java.util.concurrent.Executors.newFixedThreadPool(threads);
		try {
			java.util.List<java.util.concurrent.Future<?>> futures = new java.util.ArrayList<>();
			for (int t = 0; t < threads; t++) {
				final int idx = t;
				futures.add(pool.submit(() -> {
					try {
						barrier.await(10, java.util.concurrent.TimeUnit.SECONDS);
					} catch (Exception e) {
						throw new RuntimeException(e);
					}
					CollectionUtil.putBounded(cache, "new" + idx, "n" + idx, max);
				}));
			}
			for (java.util.concurrent.Future<?> f : futures) {
				f.get(30, java.util.concurrent.TimeUnit.SECONDS);
			}
		} finally {
			pool.shutdownNow();
		}
		// 新键写入不被门闩阻塞:回收窗口内写入的新键可能被同轮回收移除(竞态固有,非缺陷),
		// 但绝大多数应留存(100/416的移除比例下16个新键期望留存~12,半数断言余量充分)
		int newPresent = 0;
		for (int t = 0; t < threads; t++) {
			if (cache.get("new" + t) != null) {
				newPresent++;
			}
		}
		assertTrue(newPresent >= threads / 2, "新键写入不应被门闩阻塞,留存=" + newPresent);
		int survived = 0;
		for (int i = 0; i < max; i++) {
			if (cache.get("old" + i) != null) {
				survived++;
			}
		}
		// 单飞回收只删1/4(100),旧键存活≈300;修复前并发过度淘汰存活≈0。留N的容差
		assertTrue(survived >= max - (max >>> 2) - threads,
				"并发突发只允许一次1/4回收,旧键存活=" + survived + "(修复前近乎0)");
		assertTrue(cache.size() <= max + threads, "软上限:超出量不超过并发写入数,size=" + cache.size());
	}

	@Test
	public void sustainedConcurrentWritesKeepSoftBound() throws Exception {
		// 持续高并发写入:容量始终收敛在软上限内(门闩卡死会退化为无界增长,此用例兜底),
		// 且缓存保持有效(不被清空)
		int max = 100;
		int threads = 8;
		int perThread = 200;
		ConcurrentHashMap<Integer, Integer> cache = new ConcurrentHashMap<>();
		java.util.concurrent.CyclicBarrier barrier = new java.util.concurrent.CyclicBarrier(threads);
		java.util.concurrent.ExecutorService pool = java.util.concurrent.Executors.newFixedThreadPool(threads);
		try {
			java.util.List<java.util.concurrent.Future<?>> futures = new java.util.ArrayList<>();
			for (int t = 0; t < threads; t++) {
				final int base = t * perThread;
				futures.add(pool.submit(() -> {
					try {
						barrier.await(10, java.util.concurrent.TimeUnit.SECONDS);
					} catch (Exception e) {
						throw new RuntimeException(e);
					}
					for (int i = 0; i < perThread; i++) {
						CollectionUtil.putBounded(cache, base + i, i, max);
					}
				}));
			}
			for (java.util.concurrent.Future<?> f : futures) {
				f.get(60, java.util.concurrent.TimeUnit.SECONDS);
			}
		} finally {
			pool.shutdownNow();
		}
		assertTrue(cache.size() <= max + threads, "持续并发写入不得突破软上限,size=" + cache.size());
		assertTrue(cache.size() >= max >>> 1, "缓存须保持有效不被清空,size=" + cache.size());
	}

	@Test
	public void gateSharedAcrossCachesStillCachesAll() throws Exception {
		// 门闩全局共享:一个缓存回收期间另一缓存的写入不被阻塞丢弃(跳过回收也直接put)
		int max = 40;
		ConcurrentHashMap<String, String> a = new ConcurrentHashMap<>();
		ConcurrentHashMap<String, String> b = new ConcurrentHashMap<>();
		for (int i = 0; i < max; i++) {
			a.put("a" + i, "v");
			b.put("b" + i, "v");
		}
		Thread ta = new Thread(() -> CollectionUtil.putBounded(a, "aNew", "v", max));
		Thread tb = new Thread(() -> CollectionUtil.putBounded(b, "bNew", "v", max));
		ta.start();
		tb.start();
		ta.join(10000);
		tb.join(10000);
		assertEquals("v", a.get("aNew"), "缓存A新键须写入");
		assertEquals("v", b.get("bNew"), "缓存B新键须写入(不受A回收门闩影响)");
	}
}
