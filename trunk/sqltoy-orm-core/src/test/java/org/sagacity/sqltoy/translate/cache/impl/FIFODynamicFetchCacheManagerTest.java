package org.sagacity.sqltoy.translate.cache.impl;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotSame;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.sagacity.sqltoy.translate.model.TranslateConfigModel;

/**
 * 回归:动态缓存管理器必须按SqlToyContext实例隔离。
 * 修复前状态Map与检测线程调度器均为static:多上下文同名cache会串用数据/以首个创建者的keepAlive为准,
 * 且任一上下文destroy会停掉全局过期检测线程并保留静态数据(context重建后沿用旧数据)
 */
public class FIFODynamicFetchCacheManagerTest {

	private final List<FIFODynamicFetchCacheManager> managers = new ArrayList<FIFODynamicFetchCacheManager>();

	private FIFODynamicFetchCacheManager newManager() {
		FIFODynamicFetchCacheManager manager = new FIFODynamicFetchCacheManager();
		managers.add(manager);
		return manager;
	}

	@AfterEach
	public void tearDown() throws Exception {
		for (FIFODynamicFetchCacheManager manager : managers) {
			try {
				manager.destroy();
			} catch (Exception ignore) {
			}
		}
		managers.clear();
		waitForCheckerThreads(0);
	}

	@SuppressWarnings("unchecked")
	private static ConcurrentHashMap<String, Long[]> cacheInitTime(FIFODynamicFetchCacheManager manager)
			throws Exception {
		Field field = FIFODynamicFetchCacheManager.class.getDeclaredField("cacheInitTime");
		field.setAccessible(true);
		return (ConcurrentHashMap<String, Long[]>) field.get(manager);
	}

	@SuppressWarnings("unchecked")
	private static Map<String, ConcurrentHashMap<String, Object>> dynamicFetchCacheMap(
			FIFODynamicFetchCacheManager manager) throws Exception {
		Field field = FIFODynamicFetchCacheManager.class.getDeclaredField("dynamicFetchCacheMap");
		field.setAccessible(true);
		return (Map<String, ConcurrentHashMap<String, Object>>) field.get(manager);
	}

	private static void invokeTimeoutCheck(FIFODynamicFetchCacheManager manager) throws Exception {
		Method method = FIFODynamicFetchCacheManager.class.getDeclaredMethod("checkAndRemoveTimeoutData");
		method.setAccessible(true);
		method.invoke(manager);
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

	private static void waitForCheckerThreads(int expected) throws Exception {
		for (int i = 0; i < 100 && countCheckerThreads() != expected; i++) {
			Thread.sleep(20);
		}
	}

	/* ==================== 上下文隔离 ==================== */

	@Test
	public void instancesDoNotShareCacheData() {
		FIFODynamicFetchCacheManager first = newManager();
		FIFODynamicFetchCacheManager second = newManager();
		TranslateConfigModel model = cacheModel("scope_cache", 0);
		Map<String, Object[]> firstCache = first.getDynamicCache(model, null);
		firstCache.put("k1", new Object[] { "k1", "first" });
		Map<String, Object[]> secondCache = second.getDynamicCache(model, null);
		assertNotSame(firstCache, secondCache, "不同上下文不得共享同一个动态缓存实例");
		assertTrue(secondCache.isEmpty(), "不同上下文不得读到对方的动态缓存数据");
		secondCache.put("k1", new Object[] { "k1", "second" });
		assertEquals("first", firstCache.get("k1")[1]);
		assertEquals("second", secondCache.get("k1")[1]);
	}

	@Test
	public void destroyClearsOwnStateOnly() {
		FIFODynamicFetchCacheManager first = newManager();
		FIFODynamicFetchCacheManager second = newManager();
		TranslateConfigModel model = cacheModel("destroy_scope", 0);
		first.getDynamicCache(model, null).put("k1", new Object[] { "k1", "first" });
		second.getDynamicCache(model, null).put("k1", new Object[] { "k1", "second" });
		second.destroy();
		// 销毁自身:缓存登记与数据清空
		assertFalse(second.hasCache("destroy_scope"));
		// 不得影响其它上下文
		assertTrue(first.hasCache("destroy_scope"));
		assertEquals("first", first.getDynamicCache(model, null).get("k1")[1]);
	}

	@Test
	public void eachInstanceOwnsDaemonSchedulerAndDestroyIsolated() throws Exception {
		FIFODynamicFetchCacheManager first = newManager();
		FIFODynamicFetchCacheManager second = newManager();
		first.initialize();
		second.initialize();
		first.getDynamicCache(cacheModel("sched_a", 600), null);
		second.getDynamicCache(cacheModel("sched_b", 600), null);
		waitForCheckerThreads(2);
		assertEquals(2, countCheckerThreads(), "每个使用动态缓存的上下文应各自持有过期检测线程");
		Thread checker = Thread.getAllStackTraces().keySet().stream()
				.filter(t -> "sqltoy-fifo-dynamic-cache-checker".equals(t.getName())).findFirst().orElse(null);
		assertTrue(checker != null && checker.isDaemon(), "检测线程应为daemon");
		first.destroy();
		waitForCheckerThreads(1);
		assertEquals(1, countCheckerThreads(), "销毁一个上下文不得停掉其它上下文的过期检测线程");
		second.getDynamicCache(cacheModel("sched_b", 600), null).put("k1", new Object[] { "k1" });
		assertTrue(second.hasCache("sched_b"));
	}

	@Test
	public void destroyThenInitializeRecreatesScheduler() throws Exception {
		FIFODynamicFetchCacheManager manager = newManager();
		manager.initialize();
		manager.getDynamicCache(cacheModel("reinit_cache", 600), null);
		waitForCheckerThreads(1);
		assertEquals(1, countCheckerThreads());
		manager.destroy();
		waitForCheckerThreads(0);
		// 重新初始化不应因executor已终止而抛RejectedExecutionException
		assertDoesNotThrow(() -> manager.initialize());
		manager.getDynamicCache(cacheModel("reinit_cache", 600), null);
		waitForCheckerThreads(1);
		assertEquals(1, countCheckerThreads());
	}

	/* ==================== 过期登记与清理 ==================== */

	@Test
	public void clearAllRemovesEveryExpireRegistration() throws Exception {
		FIFODynamicFetchCacheManager manager = newManager();
		manager.getDynamicCache(cacheModel("cacheA", 600), null);
		manager.getDynamicCache(cacheModel("cacheA", 600), "t1");
		manager.getDynamicCache(cacheModel("cacheA", 600), "t2");
		ConcurrentHashMap<String, Long[]> registry = cacheInitTime(manager);
		assertEquals(3, registry.size());
		manager.clear("cacheA", null);
		assertEquals(0, registry.size());
		assertFalse(manager.getDynamicCache(cacheModel("cacheA", 600), null).size() > 0);
	}

	@Test
	public void clearSingleTypeRemovesOnlyItsRegistration() throws Exception {
		FIFODynamicFetchCacheManager manager = newManager();
		manager.getDynamicCache(cacheModel("cacheB", 600), null);
		manager.getDynamicCache(cacheModel("cacheB", 600), "t1");
		manager.clear("cacheB", "t1");
		ConcurrentHashMap<String, Long[]> registry = cacheInitTime(manager);
		assertEquals(1, registry.size());
		assertTrue(registry.containsKey("cacheb"));
	}

	@Test
	public void timeoutCheckToleratesMissingOuterEntry() throws Exception {
		// 构造"过期登记存在、外层缓存条目不存在"的场景:不得NPE,且仍清理过期登记
		FIFODynamicFetchCacheManager manager = newManager();
		cacheInitTime(manager).put("missingcache", new Long[] { System.currentTimeMillis() - 10000, 1L });
		assertDoesNotThrow(() -> invokeTimeoutCheck(manager));
		assertFalse(cacheInitTime(manager).containsKey("missingcache"));
	}

	@Test
	public void timeoutCheckIsolatesCorruptEntryFromOthers() throws Exception {
		FIFODynamicFetchCacheManager manager = newManager();
		// 损坏登记(时间戳缺失)不得中断整轮清理
		cacheInitTime(manager).put("badcache", new Long[] { null, 1L });
		manager.getDynamicCache(cacheModel("goodcache", 1), null);
		cacheInitTime(manager).put("goodcache", new Long[] { System.currentTimeMillis() - 10000, 1L });
		assertDoesNotThrow(() -> invokeTimeoutCheck(manager));
		assertFalse(cacheInitTime(manager).containsKey("goodcache"), "单条损坏数据不得阻断其它过期登记清理");
	}

	/* ==================== 取值/淘汰/清除语义 ==================== */

	@Test
	public void getOrCreateRegistersAndIsCaseInsensitive() {
		FIFODynamicFetchCacheManager manager = newManager();
		assertFalse(manager.hasCache("audit_fifo_a"));
		Map<String, Object[]> cache = manager.getDynamicCache(cacheModel("audit_fifo_a", 0), null);
		cache.put("k1", new Object[] { "k1", "v1" });
		assertEquals("v1", cache.get("k1")[1]);
		assertTrue(manager.hasCache("audit_fifo_a"));
		assertTrue(manager.hasCache("AUDIT_FIFO_A"));
	}

	@Test
	public void cacheNameAndTypeTwoLevelIsolation() {
		FIFODynamicFetchCacheManager manager = newManager();
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
		FIFODynamicFetchCacheManager manager = newManager();
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
		cache.get("k1");
		cache.put("k4", new Object[] { "k4" });
		assertEquals(3, cache.size(), "容量应为3");
		assertTrue(cache.containsKey("k1") && cache.containsKey("k3") && cache.containsKey("k4"),
				"应挤掉最久未使用的k2,实际:" + cache.keySet());
		assertFalse(cache.containsKey("k2"));
	}

	@Test
	public void keepAliveExpiryClearsEntryAndRegistration() throws Exception {
		FIFODynamicFetchCacheManager manager = newManager();
		String expired = "audit_fifo_d";
		String alive = "audit_fifo_e";
		manager.getDynamicCache(cacheModel(expired, 1), null).put("k1", new Object[] { "k1" });
		manager.getDynamicCache(cacheModel(alive, 3600), null).put("k2", new Object[] { "k2" });
		// 把expired的登记时间拨到keepAlive之前,确定性触发过期
		ConcurrentHashMap<String, Long[]> registry = cacheInitTime(manager);
		Long[] registration = registry.get(expired);
		org.junit.jupiter.api.Assertions.assertNotNull(registration, "keepAlive>0应有过期登记");
		registry.put(expired, new Long[] { System.currentTimeMillis() - 5000L, registration[1] });
		invokeTimeoutCheck(manager);
		Map<String, ConcurrentHashMap<String, Object>> outer = dynamicFetchCacheMap(manager);
		assertTrue(outer.get(expired).isEmpty(), "过期缓存条目应被清除");
		assertEquals(1, outer.get(alive).size(), "未过期缓存应保留");
		assertFalse(registry.containsKey(expired), "过期登记应被清除");
		assertTrue(registry.containsKey(alive), "未过期登记应保留");
	}

	@Test
	public void clearUnknownOrNullNameIsNoOp() {
		FIFODynamicFetchCacheManager manager = newManager();
		assertDoesNotThrow(() -> manager.clear("no_such_cache", null));
		assertDoesNotThrow(() -> manager.clear(null, null));
	}
}
