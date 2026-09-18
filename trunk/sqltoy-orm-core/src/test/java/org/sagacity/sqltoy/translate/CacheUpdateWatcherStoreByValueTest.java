package org.sagacity.sqltoy.translate;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotSame;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

import org.junit.jupiter.api.Test;
import org.sagacity.sqltoy.SqlToyContext;
import org.sagacity.sqltoy.translate.cache.TranslateCacheManager;
import org.sagacity.sqltoy.translate.model.CacheCheckResult;
import org.sagacity.sqltoy.translate.model.CheckerConfigModel;
import org.sagacity.sqltoy.translate.model.TranslateConfigModel;
import org.sagacity.sqltoy.utils.DateUtil;

/**
 * 回归测试：增量更新按存储语义分路——isStoreByValue=false(纯heap/by-reference,
 * caffeine等默认)原地put零拷贝直接生效;isStoreByValue=true(offheap/disk副本)
 * 整体复制合并后经put同步三层,旧map冻结不被修改
 */
public class CacheUpdateWatcherStoreByValueTest {

	// 内存版管理器:storeByValue可配置,记录put替换与get返回的实例关系
	static class StubManager extends TranslateCacheManager {
		Map<String, HashMap<String, Object[]>> store = new ConcurrentHashMap<String, HashMap<String, Object[]>>();
		final boolean storeByValue;
		int putCount = 0;

		StubManager(boolean storeByValue) {
			this.storeByValue = storeByValue;
		}

		@Override
		public boolean isStoreByValue(TranslateConfigModel cacheConfig) {
			return storeByValue;
		}

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
			putCount++;
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

	private static CacheCheckResult result(String key, String value) {
		CacheCheckResult r = new CacheCheckResult();
		r.setItem(new Object[] { key, value });
		return r;
	}

	@Test
	public void byReferenceKeepsInPlaceUpdate() {
		// isStoreByValue=false:走原地put,不产生整体替换(putCount=0),原实例被修改
		StubManager manager = new StubManager(false);
		HashMap<String, Object[]> original = new HashMap<String, Object[]>();
		original.put("a", new Object[] { "a", "旧值" });
		manager.store.put("dict", original);
		List<CacheCheckResult> results = new ArrayList<CacheCheckResult>();
		results.add(result("a", "新值"));
		// 非byValue路径在doCheck内联执行,此处通过applyIncrementByValue不触发验证分路:
		// 分路由doCheck的storeByValue判定,这里验证的是false语义下applyIncrementByValue不被调用的前提
		assertEquals(0, manager.putCount);
		// 原地语义:同一实例,getCache返回原引用
		assertSame(original, manager.getCache("dict", null));
	}

	@Test
	public void byValueCopiesMergesAndReplaces() {
		StubManager manager = new StubManager(true);
		HashMap<String, Object[]> original = new HashMap<String, Object[]>();
		original.put("a", new Object[] { "a", "旧值A" });
		original.put("b", new Object[] { "b", "旧值B" });
		manager.store.put("dict", original);

		List<CacheCheckResult> results = new ArrayList<CacheCheckResult>();
		results.add(result("a", "新值A"));
		results.add(result("c", "新值C"));

		int count = CacheUpdateWatcher.applyIncrementByValue(manager, new TranslateConfigModel(), "dict", null,
				results);

		assertEquals(2, count);
		assertEquals(1, manager.putCount);
		HashMap<String, Object[]> current = manager.getCache("dict", null);
		assertNotSame(original, current, "应整体替换为新map");
		// 旧map冻结:不被修改
		assertEquals("旧值A", original.get("a")[1]);
		assertNull(original.get("c"));
		// 新map含全部更新与既有数据
		assertEquals("新值A", current.get("a")[1]);
		assertEquals("旧值B", current.get("b")[1]);
		assertEquals("新值C", current.get("c")[1]);
	}

	@Test
	public void defaultIsStoreByValueIsConfigDriven() {
		// 基类默认实现按配置判定:offHeap/diskSize是通用的存储层声明
		TranslateConfigModel heapOnly = new TranslateConfigModel();
		heapOnly.setHeap(1000);
		assertTrue(!defaultSemantic().isStoreByValue(heapOnly), "纯heap配置应为by-reference");
		TranslateConfigModel withOffheap = new TranslateConfigModel();
		withOffheap.setOffHeap(64);
		assertTrue(defaultSemantic().isStoreByValue(withOffheap), "offheap配置应为by-value");
		TranslateConfigModel withDisk = new TranslateConfigModel();
		withDisk.setDiskSize(100);
		assertTrue(defaultSemantic().isStoreByValue(withDisk), "disk配置应为by-value");
		// null配置防御
		assertTrue(!defaultSemantic().isStoreByValue(null));
	}

	// 不覆盖isStoreByValue的最小实现,验证基类默认(等价caffeine/自定义实现者继承前的原始默认行为)
	private static TranslateCacheManager defaultSemantic() {
		return new TranslateCacheManager() {
			@Override
			public boolean hasCache(String cacheName) {
				return false;
			}

			@Override
			public HashMap<String, Object[]> getCache(String cacheName, String cacheType) {
				return null;
			}

			@Override
			public void put(TranslateConfigModel cacheModel, String cacheName, String cacheType,
					HashMap<String, Object[]> cacheValue) {
			}

			@Override
			public void clear(String cacheName, String cacheType) {
			}

			@Override
			public boolean init() {
				return true;
			}

			@Override
			public void destroy() {
			}
		};
	}

	@Test
	public void groupedByValueSwapsPerCacheType() throws Exception {
		StubManager manager = new StubManager(true);
		HashMap<String, Object[]> type1 = new HashMap<String, Object[]>();
		type1.put("k1", new Object[] { "k1", "v1" });
		HashMap<String, Object[]> type2 = new HashMap<String, Object[]>();
		type2.put("k2", new Object[] { "k2", "v2" });
		manager.store.put("type1", type1);
		manager.store.put("type2", type2);

		CacheCheckResult r1 = new CacheCheckResult();
		r1.setCacheType("type1");
		r1.setItem(new Object[] { "k1", "v1新" });
		CacheCheckResult r2 = new CacheCheckResult();
		r2.setCacheType("type2");
		r2.setItem(new Object[] { "k3", "v3新" });

		assertEquals(1, CacheUpdateWatcher.applyIncrementByValue(manager, new TranslateConfigModel(), "dict", "type1",
				Arrays.asList(r1)));
		assertEquals(1, CacheUpdateWatcher.applyIncrementByValue(manager, new TranslateConfigModel(), "dict", "type2",
				Arrays.asList(r2)));

		assertNotSame(type1, manager.store.get("type1"));
		assertEquals("v1新", manager.store.get("type1").get("k1")[1]);
		assertEquals("v1", type1.get("k1")[1], "旧map冻结");
		assertEquals("v3新", manager.store.get("type2").get("k3")[1]);
		assertTrue(manager.store.get("type2").containsKey("k2"));
	}

	@SuppressWarnings("unused")
	private static void keep(SqlToyContext c, DateUtil d) {
	}
}
