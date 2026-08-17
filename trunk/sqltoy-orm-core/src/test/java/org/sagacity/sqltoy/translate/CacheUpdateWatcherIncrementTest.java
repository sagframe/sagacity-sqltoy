package org.sagacity.sqltoy.translate;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotSame;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

import org.junit.jupiter.api.Test;
import org.sagacity.sqltoy.translate.cache.TranslateCacheManager;
import org.sagacity.sqltoy.translate.model.CacheCheckResult;
import org.sagacity.sqltoy.translate.model.TranslateConfigModel;

/**
 * 回归测试：ehcache路径的翻译缓存增量更新采用"复制-整体原子替换"——
 * 旧map(业务线程可能正在遍历)不被修改(冻结发布),新map含全部更新;
 * 修复前watcher线程直接对共享HashMap做put,与业务读线程并发造成数据竞争
 */
public class CacheUpdateWatcherIncrementTest {

	// 内存版缓存管理器stub:记录put的替换行为
	static class StubCacheManager extends TranslateCacheManager {
		Map<String, HashMap<String, Object[]>> store = new ConcurrentHashMap<String, HashMap<String, Object[]>>();

		@Override
		public boolean hasCache(String cacheName) {
			return true;
		}

		@Override
		public HashMap<String, Object[]> getCache(String cacheName, String cacheType) {
			return store.get(cacheType == null ? cacheName : cacheType);
		}

		@Override
		public void put(TranslateConfigModel cacheModel, String cacheName, String cacheType,
				HashMap<String, Object[]> cacheValue) {
			store.put(cacheType == null ? cacheName : cacheType, cacheValue);
		}

		@Override
		public void clear(String cacheName, String cacheType) {
			store.remove(cacheType == null ? cacheName : cacheType);
		}

		@Override
		public boolean init() {
			return true;
		}

		@Override
		public void destroy() {
		}
	}

	private static CacheCheckResult result(String cacheType, String key, Object... columns) {
		CacheCheckResult r = new CacheCheckResult();
		r.setCacheType(cacheType);
		Object[] item = new Object[columns.length + 1];
		item[0] = key;
		System.arraycopy(columns, 0, item, 1, columns.length);
		r.setItem(item);
		return r;
	}

	@Test
	public void incrementReplacesMapWithoutMutatingOldOne() {
		StubCacheManager manager = new StubCacheManager();
		HashMap<String, Object[]> original = new HashMap<String, Object[]>();
		original.put("a", new Object[] { "a", "旧值A" });
		original.put("b", new Object[] { "b", "旧值B" });
		manager.store.put("dictCache", original);

		List<CacheCheckResult> results = new ArrayList<CacheCheckResult>();
		results.add(result(null, "a", "新值A"));
		results.add(result(null, "c", "新值C"));

		int count = CacheUpdateWatcher.applyStandCacheIncrement(manager, new TranslateConfigModel(), "dictCache", null,
				results);

		assertEquals(2, count);
		HashMap<String, Object[]> current = manager.store.get("dictCache");
		assertNotSame(original, current, "缓存条目应被整体替换为新map");
		// 旧map冻结:业务线程持有的旧引用不被修改(修复前直接put修改共享map)
		assertEquals("旧值A", original.get("a")[1]);
		assertNull(original.get("c"), "旧map不应出现新增键");
		// 新map含全部更新与既有数据
		assertEquals("新值A", current.get("a")[1]);
		assertEquals("旧值B", current.get("b")[1]);
		assertEquals("新值C", current.get("c")[1]);
	}

	@Test
	public void groupedIncrementSwapsPerCacheType() {
		StubCacheManager manager = new StubCacheManager();
		HashMap<String, Object[]> type1 = new HashMap<String, Object[]>();
		type1.put("k1", new Object[] { "k1", "v1" });
		HashMap<String, Object[]> type2 = new HashMap<String, Object[]>();
		type2.put("k2", new Object[] { "k2", "v2" });
		manager.store.put("type1", type1);
		manager.store.put("type2", type2);

		List<CacheCheckResult> t1Results = new ArrayList<CacheCheckResult>();
		t1Results.add(result("type1", "k1", "v1新"));
		List<CacheCheckResult> t2Results = new ArrayList<CacheCheckResult>();
		t2Results.add(result("type2", "k3", "v3新"));

		assertEquals(1, CacheUpdateWatcher.applyStandCacheIncrement(manager, new TranslateConfigModel(), "dict", "type1",
				t1Results));
		assertEquals(1, CacheUpdateWatcher.applyStandCacheIncrement(manager, new TranslateConfigModel(), "dict", "type2",
				t2Results));

		assertNotSame(type1, manager.store.get("type1"));
		assertEquals("v1新", manager.store.get("type1").get("k1")[1]);
		assertEquals("v1", type1.get("k1")[1]);
		assertEquals("v3新", manager.store.get("type2").get("k3")[1]);
		assertTrue(manager.store.get("type2").containsKey("k2"));
	}

	@Test
	public void watcherIsDaemonAndNamed() {
		CacheUpdateWatcher watcher = new CacheUpdateWatcher(new org.sagacity.sqltoy.SqlToyContext(),
				new StubCacheManager(), null,
				new java.util.concurrent.CopyOnWriteArrayList<org.sagacity.sqltoy.translate.model.CheckerConfigModel>(),
				0, 0);
		assertTrue(watcher.isDaemon());
		assertEquals("sqltoy-cache-update-watcher", watcher.getName());
	}
}
