package org.sagacity.sqltoy;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.lang.reflect.Field;
import java.util.Collections;
import java.util.HashMap;

import org.junit.jupiter.api.Test;
import org.sagacity.sqltoy.config.model.ParamFilterModel;
import org.sagacity.sqltoy.translate.TranslateManager;

/**
 * cache-arg 过滤的匹配语义与“比较列小写值预计算”改动:
 * 大小写不敏感相等、like 兜底、多参数匹配、缓存行不被修改、预计算助手边界。
 */
public class ParamFilterProcessorCacheArgTest {

	private static final HashMap<String, Object[]> CACHE = new HashMap<String, Object[]>();

	static {
		CACHE.put("u1", new Object[] { "u1", "Alice", "OrgA", 1 });
		CACHE.put("u2", new Object[] { "u2", "Bob", "OrgB", 2 });
		CACHE.put("u3", new Object[] { "u3", "ALICE2", "OrgC", 1 });
	}

	@Test
	public void exactMatchIsCaseInsensitive() throws Exception {
		Object[] values = new Object[] { "alice" };
		Object[] result = ParamFilterProcessor.filterValue(contextWithCache(), new String[] { "userName" }, values,
				Collections.singletonList(cacheArgModel()));
		assertMatchedKeys(result[0], "u1");
	}

	@Test
	public void likeMatchFallsBackWhenNoExactMatch() throws Exception {
		Object[] values = new Object[] { "bo" };
		Object[] result = ParamFilterProcessor.filterValue(contextWithCache(), new String[] { "userName" }, values,
				Collections.singletonList(cacheArgModel()));
		assertMatchedKeys(result[0], "u2");
	}

	@Test
	public void multipleParamValuesMatchTheirOwnKeys() throws Exception {
		Object[] values = new Object[] { new Object[] { "ALICE", "bob" } };
		Object[] result = ParamFilterProcessor.filterValue(contextWithCache(), new String[] { "userName" }, values,
				Collections.singletonList(cacheArgModel()));
		assertMatchedKeys(result[0], "u1", "u2");
	}

	@Test
	public void cacheRowsAreNotModified() throws Exception {
		Object[] before1 = CACHE.get("u1").clone();
		Object[] before2 = CACHE.get("u2").clone();
		ParamFilterProcessor.filterValue(contextWithCache(), new String[] { "userName" },
				new Object[] { "alice" }, Collections.singletonList(cacheArgModel()));
		assertArrayEquals(before1, CACHE.get("u1"), "缓存行不能被过滤过程原地修改");
		assertArrayEquals(before2, CACHE.get("u2"), "缓存行不能被过滤过程原地修改");
	}

	@Test
	public void lowMatchValuesPrecomputeHandlesNullAndNonString() {
		String[] lowValues = ParamFilterProcessor.toLowMatchValues(new Object[] { "AbC", null, 12, "İstanbul" },
				new int[] { 0, 1, 2, 3 });
		assertArrayEquals(new String[] { "abc", null, "12", "i̇stanbul" }, lowValues);
		assertEquals(0, ParamFilterProcessor.toLowMatchValues(new Object[] { "x" }, new int[0]).length);
		// 与改造前保持一致:matchIndexes为null属非法配置,仍按NPE暴露(由filterCache统一捕获降级)
		org.junit.jupiter.api.Assertions.assertThrows(NullPointerException.class,
				() -> ParamFilterProcessor.toLowMatchValues(new Object[] { "x" }, null));
	}

	// ============ 辅助 ============

	private static void assertMatchedKeys(Object matched, String... expected) {
		Object[] keys = (Object[]) matched;
		assertEquals(expected.length, keys.length, "匹配key数量不符");
		for (String key : expected) {
			boolean found = false;
			for (Object item : keys) {
				if (key.equals(item)) {
					found = true;
					break;
				}
			}
			assertTrue(found, "应匹配到key:" + key + ",实际:" + java.util.Arrays.toString(keys));
		}
	}

	private static SqlToyContext contextWithCache() throws Exception {
		SqlToyContext context = new SqlToyContext();
		TranslateManager fake = new TranslateManager() {
			@Override
			public HashMap<String, Object[]> getCacheData(String cacheName, String cacheType) {
				return CACHE;
			}
		};
		Field field = SqlToyContext.class.getDeclaredField("translateManager");
		field.setAccessible(true);
		field.set(context, fake);
		return context;
	}

	private static ParamFilterModel cacheArgModel() {
		ParamFilterModel model = new ParamFilterModel("cache-arg", new String[] { "userName" });
		model.setParam("userName");
		model.setCacheName("user_cache");
		model.setCacheKeyIndex(0);
		model.setCacheMappingIndexes(new int[] { 1, 2 });
		model.setCacheMappingMax(10);
		model.setPriorMatchEqual(true);
		return model;
	}
}
