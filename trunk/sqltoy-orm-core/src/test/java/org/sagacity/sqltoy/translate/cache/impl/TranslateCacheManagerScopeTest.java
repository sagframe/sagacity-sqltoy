package org.sagacity.sqltoy.translate.cache.impl;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNotSame;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.HashMap;

import org.junit.jupiter.api.Test;
import org.sagacity.sqltoy.translate.model.TranslateConfigModel;

/**
 * 回归:翻译缓存管理器必须按SqlToyContext实例隔离。
 * 修复前TranslateEhcacheManager.cacheManager为static,同JVM多个上下文共用一个CacheManager:
 * 后初始化上下文配置(diskStorePath等)被静默忽略,且任一上下文destroy会关闭共享manager,
 * 导致其它存活上下文的翻译缓存整体失效(读取返回null后退化为每次实时查库)
 */
public class TranslateCacheManagerScopeTest {

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
		TranslateEhcacheManager first = new TranslateEhcacheManager();
		TranslateEhcacheManager second = new TranslateEhcacheManager();
		assertTrue(first.init());
		assertTrue(second.init());
		HashMap<String, Object[]> firstValue = cacheValue("first");
		first.put(cacheModel(), "scope_cache", null, firstValue);
		assertSame(firstValue, first.getCache("scope_cache", null));
		// 不同实例(不同SqlToyContext)不得共享缓存:修复前此处读到first的数据
		assertNull(second.getCache("scope_cache", null), "不同上下文不得共享翻译缓存数据");
		assertFalse(second.hasCache("scope_cache"));
		// 各自写入互不影响
		HashMap<String, Object[]> secondValue = cacheValue("second");
		second.put(cacheModel(), "scope_cache", null, secondValue);
		assertSame(firstValue, first.getCache("scope_cache", null));
		assertSame(secondValue, second.getCache("scope_cache", null));
		assertNotSame(first.getCache("scope_cache", null), second.getCache("scope_cache", null));
		first.destroy();
		second.destroy();
	}

	@Test
	public void destroyOnlyAffectsOwnManager() {
		TranslateEhcacheManager first = new TranslateEhcacheManager();
		TranslateEhcacheManager second = new TranslateEhcacheManager();
		assertTrue(first.init());
		assertTrue(second.init());
		first.put(cacheModel(), "scope_cache", null, cacheValue("first"));
		second.put(cacheModel(), "scope_cache", null, cacheValue("second"));
		// 销毁一个上下文不得影响其它上下文:修复前second.destroy()会关闭共享manager,
		// first.getCache随之返回null
		second.destroy();
		assertFalse(second.hasCache("scope_cache"));
		HashMap<String, Object[]> cached = first.getCache("scope_cache", null);
		assertNotNull(cached, "其它上下文的翻译缓存不得被destroy连带失效");
		assertEquals("first", cached.get("key")[0]);
		first.destroy();
	}

	@Test
	public void reinitAfterDestroy() {
		TranslateEhcacheManager manager = new TranslateEhcacheManager();
		assertTrue(manager.init());
		manager.put(cacheModel(), "scope_cache", null, cacheValue("value"));
		manager.destroy();
		assertFalse(manager.hasCache("scope_cache"));
		// destroy后可重新init并正常使用(destroy幂等且不破坏实例可复用性)
		assertTrue(manager.init());
		manager.put(cacheModel(), "scope_cache", null, cacheValue("value2"));
		assertEquals("value2", manager.getCache("scope_cache", null).get("key")[0]);
		assertDoesNotThrow(() -> manager.destroy());
		assertDoesNotThrow(() -> manager.destroy());
	}
}
