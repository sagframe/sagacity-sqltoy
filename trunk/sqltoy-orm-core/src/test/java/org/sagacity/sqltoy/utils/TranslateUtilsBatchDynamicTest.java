package org.sagacity.sqltoy.utils;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import org.junit.jupiter.api.Test;
import org.sagacity.sqltoy.config.model.Translate;
import org.sagacity.sqltoy.integration.AppContext;
import org.sagacity.sqltoy.translate.DynamicCacheFetch;
import org.sagacity.sqltoy.translate.FieldTranslateCacheHolder;
import org.sagacity.sqltoy.translate.TranslateManager;
import org.sagacity.sqltoy.translate.model.BatchDynamicCache;
import org.sagacity.sqltoy.translate.model.DynamicCacheHolder;

/**
 * 回归测试:动态缓存挂起攒批机制下两遍翻译的完整链路
 * <li>场景:organId为keyField,organName为翻译字段;ORG1已在本地缓存,ORG2缺失走批量取数</li>
 * <li>验证:第一遍miss时字段被写入裸key(不是null),第二遍批量翻译后全部正确;
 * 已缓存行OG1的翻译结果不被二次翻译覆盖回原始key</li>
 */
public class TranslateUtilsBatchDynamicTest {

	public static class StaffVO {
		private String organId;
		private String organName;

		public StaffVO(String organId, String organName) {
			this.organId = organId;
			this.organName = organName;
		}

		public String getOrganId() {
			return organId;
		}

		public void setOrganId(String organId) {
			this.organId = organId;
		}

		public String getOrganName() {
			return organName;
		}

		public void setOrganName(String organName) {
			this.organName = organName;
		}
	}

	/** 模拟动态缓存取数:批量请求只返回ORG2 */
	static class MockFetch implements DynamicCacheFetch {
		List<String[]> batchRequests = new ArrayList<String[]>();

		@Override
		public void initialize(AppContext appContext) {
		}

		@Override
		public Object[] getCache(String cacheName, String cacheType, String sid, String[] properties, String key) {
			// 挂起模式下不应走单key实时取数
			throw new UnsupportedOperationException("paused mode should not fetch single key!");
		}

		@Override
		public Map<String, Object[]> getCache(String cacheName, String cacheType, String sid, String[] properties,
				String[] keys) {
			batchRequests.add(keys);
			Map<String, Object[]> result = new HashMap<String, Object[]>();
			for (String key : keys) {
				if ("ORG2".equals(key)) {
					result.put(key, new Object[] { "ORG2", "Org Two" });
				}
			}
			return result;
		}
	}

	@SuppressWarnings("serial")
	private static TranslateManager mockTranslateManager(final HashMap<String, Object[]> localCache) {
		return new TranslateManager() {
			@Override
			public HashMap<String, Object[]> getCacheData(String cacheName, String cacheType) {
				return localCache;
			}
		};
	}

	@Test
	public void batchDynamicTranslateFullFlow() throws Exception {
		// 本地缓存中ORG1已有翻译值(index=1取Object[]第2列)
		HashMap<String, Object[]> localCache = new HashMap<String, Object[]>();
		localCache.put("ORG1", new Object[] { "ORG1", "Org One" });

		Translate translate = new Translate("dictCache");
		translate.getExtend().dynamicCache = true;
		// 对应TranslateConfigParse.parseAnnotaTrans:411,注解keyField必填,keyColumn与其同值
		translate.setKeyColumn("organId");
		FieldTranslateCacheHolder fieldHolder = new FieldTranslateCacheHolder();
		fieldHolder.setKeyField("organId");
		fieldHolder.setTranslates(new Translate[] { translate });
		fieldHolder.setCacheArray(new HashMap[] { localCache });

		HashMap<String, FieldTranslateCacheHolder> fieldTranslateHandlers = new HashMap<String, FieldTranslateCacheHolder>();
		fieldTranslateHandlers.put("organName", fieldHolder);

		MockFetch fetch = new MockFetch();
		BatchDynamicCache batch = TranslateUtils.getBatchTranslates(null, fieldTranslateHandlers);
		DynamicCacheHolder dynamicCacheHolder = new DynamicCacheHolder(batch.getCacheAndTypeForRealMap(),
				batch.getCacheAndTypeForRealType(), batch.getDynamicCaches());

		List<StaffVO> rows = new ArrayList<StaffVO>();
		rows.add(new StaffVO("ORG1", null));
		rows.add(new StaffVO("ORG2", null));

		// 第一遍逐行翻译(等价于ResultUtils.wrapBeanTranslate的私有逻辑)
		for (StaffVO row : rows) {
			Object srcFieldValue = BeanUtil.getProperty(row, fieldHolder.getKeyField());
			Object fieldValue = BeanUtil.getProperty(row, "organName");
			if (srcFieldValue != null && !"".equals(srcFieldValue.toString()) && fieldValue == null) {
				BeanUtil.setProperty(row, "organName", fieldHolder.getBeanCacheValue(fetch, dynamicCacheHolder, row,
						srcFieldValue.toString()));
			}
		}
		// 关键验证1:第一遍miss的行字段值是裸key,不是null(挂起模式的占位设计)
		assertNotNull(rows.get(1).getOrganName(), "miss后字段不应为null!");
		assertEquals("ORG2", rows.get(1).getOrganName());
		// 关键验证2:命中的行第一遍已完成翻译
		assertEquals("Org One", rows.get(0).getOrganName());
		// 挂起模式下未发生任何单key远程取数
		assertEquals(0, fetch.batchRequests.size());

		// 第二遍批量翻译
		TranslateUtils.translateDTOListByDynamicCache(mockTranslateManager(localCache), batch, dynamicCacheHolder,
				fetch, rows);

		// 批量取数只发生一次,且只取未命中的ORG2
		assertEquals(1, fetch.batchRequests.size());
		assertArrayEquals(new String[] { "ORG2" }, fetch.batchRequests.get(0));
		// 关键验证3:两行的最终翻译结果均正确
		assertEquals("Org One", rows.get(0).getOrganName());
		assertEquals("Org Two", rows.get(1).getOrganName());
	}
}
