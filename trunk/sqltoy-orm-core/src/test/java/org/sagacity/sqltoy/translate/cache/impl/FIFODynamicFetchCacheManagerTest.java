package org.sagacity.sqltoy.translate.cache.impl;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertNotSame;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

import org.junit.jupiter.api.AfterEach;
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

	// 调度器为static单例,destroy的shutdownNow存在线程退出窗口:用例后停止并等待检测线程退出,
	// 避免"destroy→重建"用例与"检测线程计数"用例之间因残留线程计数抖动导致偶发失败
	@AfterEach
	public void stopScheduler() throws Exception {
		Field started = FIFODynamicFetchCacheManager.class.getDeclaredField("schedulerStarted");
		started.setAccessible(true);
		if (Boolean.TRUE.equals(started.get(null))) {
			new FIFODynamicFetchCacheManager().destroy();
			for (int i = 0; i < 100 && countCheckerThreads() > 0; i++) {
				Thread.sleep(20);
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

	private static void invokeTimeoutCheck() throws Exception {
		Method method = FIFODynamicFetchCacheManager.class.getDeclaredMethod("checkAndRemoveTimeoutData");
		method.setAccessible(true);
		method.invoke(null);
	}

	@Test
	public void timeoutCheckToleratesMissingOuterEntry() throws Exception {
		// 构造:过期登记存在、外层缓存条目不存在(原实现 dynamicFetchCacheMap.get(...).remove(...) 直接NPE;
		// 而scheduleAtFixedRate任务一旦抛出未捕获异常,后续执行会被永久取消→keepAlive检测全部失效)
		cacheInitTime().put("missingcache", new Long[] { System.currentTimeMillis() - 10000, 1L });
		assertDoesNotThrow(FIFODynamicFetchCacheManagerTest::invokeTimeoutCheck);
		// 外层条目不存在时仍需清理过期登记
		assertFalse(cacheInitTime().containsKey("missingcache"));
	}

	@Test
	public void timeoutCheckIsolatesCorruptEntryFromOthers() throws Exception {
		// 损坏登记(时间戳缺失,旧实现在此处NPE并中断整轮清理)
		cacheInitTime().put("badcache", new Long[] { null, 1L });
		// 正常的过期登记:外层条目存在,应被清理
		FIFODynamicFetchCacheManager manager = new FIFODynamicFetchCacheManager();
		manager.getDynamicCache(cacheModel("goodcache", 1), null);
		cacheInitTime().put("goodcache", new Long[] { System.currentTimeMillis() - 10000, 1L });
		assertDoesNotThrow(FIFODynamicFetchCacheManagerTest::invokeTimeoutCheck);
		assertFalse(cacheInitTime().containsKey("goodcache"), "单条损坏数据不得阻断其它过期登记清理");
	}

	/* ==================== 取值/淘汰/过期/清除语义 ==================== */

	@Test
	public void getOrCreateRegistersAndIsCaseInsensitive() {
		FIFODynamicFetchCacheManager manager = new FIFODynamicFetchCacheManager();
		assertFalse(manager.hasCache("audit_fifo_a"));
		Map<String, Object[]> cache = manager.getDynamicCache(cacheModel("audit_fifo_a", 0), null);
		org.junit.jupiter.api.Assertions.assertNotNull(cache);
		cache.put("k1", new Object[] { "k1", "v1" });
		assertEquals("v1", cache.get("k1")[1]);
		// 登记后hasCache为true且忽略大小写
		assertTrue(manager.hasCache("audit_fifo_a"));
		assertTrue(manager.hasCache("AUDIT_FIFO_A"));
	}

	@Test
	public void cacheNameAndTypeTwoLevelIsolation() {
		FIFODynamicFetchCacheManager manager = new FIFODynamicFetchCacheManager();
		TranslateConfigModel model = cacheModel("audit_fifo_b", 0);
		Map<String, Object[]> typeA = manager.getDynamicCache(model, "TYPE_A");
		Map<String, Object[]> typeB = manager.getDynamicCache(model, "TYPE_B");
		Map<String, Object[]> noType = manager.getDynamicCache(model, null);
		assertNotSame(typeA, typeB);
		assertNotSame(typeA, noType);
		typeA.put("k1", new Object[] { "k1", "A" });
		assertTrue(typeB.isEmpty());
		assertTrue(noType.isEmpty());
		// 同名同type(忽略大小写)再次获取返回同一实例
		assertSame(typeA, manager.getDynamicCache(model, "type_a"));
	}

	@Test
	public void fifoCapacityEvictsLeastRecentlyUsed() {
		FIFODynamicFetchCacheManager manager = new FIFODynamicFetchCacheManager();
		TranslateConfigModel model = new TranslateConfigModel();
		model.setCache("audit_fifo_c");
		model.setDynamicCacheInitSize(10);
		model.setDynamicCacheMaxSize(3);
		model.setDynamicCacheLoadFactor(0.75F);
		model.setKeepAlive(0);
		Map<String, Object[]> cache = manager.getDynamicCache(model, null);
		cache.put("k1", new Object[] { "k1" });
		cache.put("k2", new Object[] { "k2" });
		cache.put("k3", new Object[] { "k3" });
		// accessOrder=true:访问k1使其变为最近使用,再放入k4应挤掉最久未用的k2
		cache.get("k1");
		cache.put("k4", new Object[] { "k4" });
		assertEquals(3, cache.size(), "容量应为3");
		assertTrue(cache.containsKey("k1") && cache.containsKey("k3") && cache.containsKey("k4"),
				"应挤掉最久未用的k2,实际:" + cache.keySet());
		assertFalse(cache.containsKey("k2"));
	}

	@Test
	public void keepAliveExpiryClearsEntryAndRegistration() throws Exception {
		FIFODynamicFetchCacheManager manager = new FIFODynamicFetchCacheManager();
		String expired = "audit_fifo_d";
		String alive = "audit_fifo_e";
		manager.getDynamicCache(cacheModel(expired, 1), null).put("k1", new Object[] { "k1" });
		manager.getDynamicCache(cacheModel(alive, 3600), null).put("k2", new Object[] { "k2" });
		// 把expired的登记时间拨到keepAlive之前,确定性触发过期(避免真实等待调度周期)
		Map<String, Long[]> registry = cacheInitTime();
		Long[] registration = registry.get(expired);
		org.junit.jupiter.api.Assertions.assertNotNull(registration, "keepAlive>0应有过期登记");
		registry.put(expired, new Long[] { System.currentTimeMillis() - 5000L, registration[1] });
		invokeTimeoutCheck();
		// 过期缓存条目与登记均被清除,未过期的保留
		Field outerField = FIFODynamicFetchCacheManager.class.getDeclaredField("dynamicFetchCacheMap");
		outerField.setAccessible(true);
		Map<String, ConcurrentHashMap<String, Object>> outer = castOuter(outerField.get(null));
		assertTrue(outer.get(expired).isEmpty(), "过期缓存条目应被清除");
		assertTrue(outer.get(alive).size() == 1, "未过期缓存应保留");
		assertFalse(registry.containsKey(expired), "过期登记应被清除");
		assertTrue(registry.containsKey(alive), "未过期登记应保留");
	}

	@SuppressWarnings("unchecked")
	private static Map<String, ConcurrentHashMap<String, Object>> castOuter(Object outer) {
		return (Map<String, ConcurrentHashMap<String, Object>>) outer;
	}

	@Test
	public void clearUnknownOrNullNameIsNoOp() {
		FIFODynamicFetchCacheManager manager = new FIFODynamicFetchCacheManager();
		assertDoesNotThrow(() -> manager.clear("no_such_cache", null));
		assertDoesNotThrow(() -> manager.clear(null, null));
	}
}
