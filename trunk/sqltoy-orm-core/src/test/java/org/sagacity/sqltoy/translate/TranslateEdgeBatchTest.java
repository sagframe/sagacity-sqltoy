package org.sagacity.sqltoy.translate;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.Test;
import org.sagacity.sqltoy.SqlToyContext;
import org.sagacity.sqltoy.translate.cache.impl.TranslateEhcacheManager;
import org.sagacity.sqltoy.exception.DataAccessException;
import org.sagacity.sqltoy.translate.TranslateUtils;

/**
 * 回归测试批次：(37)hasCache对未init的cacheManager判空返回false;
 * (38)非租户${xxx}占位符cacheType给出指向配置的明确报错而非concat NPE
 */
public class TranslateEdgeBatchTest {

	@Test
	public void hasCacheWithoutInitReturnsFalse() {
		// update 2026-11-15 管理器改为按SqlToyContext实例化(不再JVM共享),无需反射置空静态字段
		TranslateEhcacheManager manager = new TranslateEhcacheManager();
		// init前:cacheManager为null,判空返回false(修复前直接NPE)
		assertFalse(manager.hasCache("anyCache"));
		assertTrue(manager.init());
		// 已init但未创建该缓存
		assertFalse(manager.hasCache("anyCache"));
		// destroy后恢复无缓存状态
		manager.destroy();
		assertFalse(manager.hasCache("anyCache"));
	}

	@Test
	public void nonTenantPlaceholderCacheTypeGivesConfigError() {
		SqlToyContext context = new SqlToyContext();
		// ${orgId}不是支持的占位符,修复前返回null后调用方concat("_")NPE
		DataAccessException ex = assertThrows(DataAccessException.class,
				() -> TranslateUtils.getRealCacheType(context, "${orgId}"));
		assertTrue(ex.getMessage().contains("${orgId}"), "实际:" + ex.getMessage());
		assertTrue(ex.getMessage().contains("tenantid"), "实际:" + ex.getMessage());
	}

	@Test
	public void plainCacheTypeAndNullUnchanged() {
		SqlToyContext context = new SqlToyContext();
		assertEquals("dictType", TranslateUtils.getRealCacheType(context, "dictType"));
		assertNull(TranslateUtils.getRealCacheType(context, null));
	}

	private static void assertEquals(String expected, String actual) {
		org.junit.jupiter.api.Assertions.assertEquals(expected, actual);
	}

	private static void assertNull(String value) {
		org.junit.jupiter.api.Assertions.assertNull(value);
	}

	@SuppressWarnings("unused")
	private static void unused() {
		assertDoesNotThrow(() -> {
		});
	}
}
