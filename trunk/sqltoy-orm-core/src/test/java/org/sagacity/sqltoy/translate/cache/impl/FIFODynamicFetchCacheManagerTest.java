package org.sagacity.sqltoy.translate.cache.impl;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.lang.reflect.Field;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.sagacity.sqltoy.translate.model.TranslateConfigModel;

/**
 * 回归测试：检测线程为daemon且多实例只共享一个调度任务(状态Map为static,调度器同步static);
 * destroy后可重新initialize;clear同步清理cacheInitTime过期登记
 */
public class FIFODynamicFetchCacheManagerTest {

	@SuppressWarnings("unchecked")
	private static ConcurrentHashMap<String, Long[]> cacheInitTime() throws Exception {
		Field field = FIFODynamicFetchCacheManager.class.getDeclaredField("cacheInitTime");
		field.setAccessible(true);
		return (ConcurrentHashMap<String, Long[]>) field.get(null);
	}

	// 状态Map为static,每个用例前清理避免跨用例残留
	@BeforeEach
	public void cleanStaticState() throws Exception {
		for (String fieldName : new String[] { "dynamicFetchCacheMap", "cacheInitTime", "registCaches" }) {
			Field field = FIFODynamicFetchCacheManager.class.getDeclaredField(fieldName);
			field.setAccessible(true);
			Object state = field.get(null);
			if (state instanceof Map) {
				((Map<?, ?>) state).clear();
			} else if (state instanceof java.util.Set) {
				((java.util.Set<?>) state).clear();
			}
		}
	}

	private static TranslateConfigModel cacheModel(String cacheName, int keepAlive) {
		TranslateConfigModel model = new TranslateConfigModel();
		model.setCache(cacheName);
		model.setKeepAlive(keepAlive);
		return model;
	}

	private static long countCheckerThreads() {
		return Thread.getAllStackTraces().keySet().stream()
				.filter(t -> "sqltoy-fifo-dynamic-cache-checker".equals(t.getName())).count();
	}

	@Test
	public void multiInstanceSharesSingleDaemonScheduler() throws Exception {
		FIFODynamicFetchCacheManager first = new FIFODynamicFetchCacheManager();
		FIFODynamicFetchCacheManager second = new FIFODynamicFetchCacheManager();
		first.initialize();
		second.initialize();
		// 调度器为static,多实例只应有一个检测线程
		long threads = 0;
		for (int i = 0; i < 50 && threads == 0; i++) {
			threads = countCheckerThreads();
			if (threads == 0) {
				Thread.sleep(20);
			}
		}
		assertEquals(1, threads, "多实例应共享单个检测线程");
		// 检测线程必须为daemon,destroy未调用时不阻止JVM退出
		Thread checker = Thread.getAllStackTraces().keySet().stream()
				.filter(t -> "sqltoy-fifo-dynamic-cache-checker".equals(t.getName())).findFirst().orElse(null);
		assertTrue(checker != null && checker.isDaemon(), "检测线程应为daemon");
	}

	@Test
	public void destroyThenInitializeRecreatesScheduler() {
		FIFODynamicFetchCacheManager manager = new FIFODynamicFetchCacheManager();
		manager.initialize();
		manager.destroy();
		// 重新初始化不应因executor已终止而抛RejectedExecutionException
		assertDoesNotThrow(() -> manager.initialize());
	}

	@Test
	public void clearAllRemovesEveryExpireRegistration() throws Exception {
		FIFODynamicFetchCacheManager manager = new FIFODynamicFetchCacheManager();
		manager.getDynamicCache(cacheModel("cacheA", 600), null);
		manager.getDynamicCache(cacheModel("cacheA", 600), "t1");
		manager.getDynamicCache(cacheModel("cacheA", 600), "t2");
		// 修复前:clear不清cacheInitTime,3条过期登记常驻内存
		assertEquals(3, cacheInitTime().size());
		manager.clear("cacheA", null);
		assertEquals(0, cacheInitTime().size());
		assertFalse(manager.getDynamicCache(cacheModel("cacheA", 600), null).size() > 0);
	}

	@Test
	public void clearSingleTypeRemovesOnlyItsRegistration() throws Exception {
		FIFODynamicFetchCacheManager manager = new FIFODynamicFetchCacheManager();
		manager.getDynamicCache(cacheModel("cacheB", 600), null);
		manager.getDynamicCache(cacheModel("cacheB", 600), "t1");
		manager.clear("cacheB", "t1");
		assertEquals(1, cacheInitTime().size());
		assertTrue(cacheInitTime().containsKey("cacheb"));
	}
}
