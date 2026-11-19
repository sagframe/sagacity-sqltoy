package org.sagacity.sqltoy.translate.cache.impl;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.HashMap;

import org.junit.jupiter.api.Test;
import org.sagacity.sqltoy.translate.model.TranslateConfigModel;
import org.springframework.cache.caffeine.CaffeineCache;

/**
 * 回归:caffeine翻译缓存管理器必须按SqlToyContext实例隔离。
 * 修复前cacheManager为static,而cacheNameSet/registerLock是实例字段:后初始化上下文会对同名cache
 * 二次registerCustomCache,替换前一个上下文的缓存对象(规格以最后注册者为准、内容丢失);
 * 且任一上下文destroy会把共享manager置null,其它存活上下文随即失去缓存
 */
public class TranslateCaffeineManagerScopeTest {

	private static TranslateConfigModel cacheModel() {
		TranslateConfigModel cacheModel = new TranslateConfigModel();
		cacheModel.setCache("scope_cache");
		return cacheModel;
	}

	private static HashMap<String, Object[]> cacheValue(String value) {
		HashMap<String, Object[]> result = new HashMap<String, Object[]>();
		result.put("key", new Object[] { value });
		return result;
	}

	@Test
	public void managersAreIsolated() {
		TranslateCaffeineManager first = new TranslateCaffeineManager();
		TranslateCaffeineManager second = new TranslateCaffeineManager();
		assertTrue(first.init());
		assertTrue(second.init());
		first.put(cacheModel(), "scope_cache", null, cacheValue("first"));
		assertEquals("first", first.getCache("scope_cache", null).get("key")[0]);
		// 修复前共用一个CaffeineCacheManager,这里会读到first的数据
		assertNull(second.getCache("scope_cache", null), "不同上下文不得共享翻译缓存数据");
	}

	@Test
	public void destroyOnlyAffectsOwnManager() {
		TranslateCaffeineManager first = new TranslateCaffeineManager();
		TranslateCaffeineManager second = new TranslateCaffeineManager();
		assertTrue(first.init());
		assertTrue(second.init());
		first.put(cacheModel(), "scope_cache", null, cacheValue("first"));
		second.put(cacheModel(), "scope_cache", null, cacheValue("second"));
		second.destroy();
		// 修复前second.destroy()把静态manager置null,first的缓存随之失效
		assertFalse(second.hasCache("scope_cache"));
		HashMap<String, Object[]> cached = first.getCache("scope_cache", null);
		assertNotNull(cached, "其它上下文的翻译缓存不得被destroy连带失效");
		assertEquals("first", cached.get("key")[0]);
		first.destroy();
	}

	@Test
	public void rebuildKeepsCustomCacheSpecs() {
		TranslateCaffeineManager manager = new TranslateCaffeineManager();
		assertTrue(manager.init());
		TranslateConfigModel model = cacheModel();
		model.setHeap(500);
		model.setKeepAlive(60);
		manager.put(model, "scope_cache", null, cacheValue("v1"));
		assertCustomSpecs(manager);
		manager.destroy();
		assertTrue(manager.init());
		manager.put(model, "scope_cache", null, cacheValue("v2"));
		// 修复前destroy不清cacheNameSet,重建后put跳过registerCustomCache,
		// getCache按默认规格动态建缓存(无容量上限/无过期),keepAlive/heap静默丢失
		assertCustomSpecs(manager);
		assertEquals("v2", manager.getCache("scope_cache", null).get("key")[0]);
		manager.destroy();
	}

	private static void assertCustomSpecs(TranslateCaffeineManager manager) {
		CaffeineCache cache = (CaffeineCache) manager.cacheManager.getCache("scope_cache");
		assertNotNull(cache, "缓存应已注册");
		com.github.benmanes.caffeine.cache.Cache<Object, Object> nativeCache = cache.getNativeCache();
		assertTrue(nativeCache.policy().eviction().isPresent(), "heap配置应生效(容量受限)");
		assertEquals(500, nativeCache.policy().eviction().get().getMaximum());
		assertTrue(nativeCache.policy().expireAfterWrite().isPresent(), "keepAlive配置应生效(写后过期)");
		assertEquals(60, nativeCache.policy().expireAfterWrite().get().getExpiresAfter(java.util.concurrent.TimeUnit.SECONDS));
	}
}
