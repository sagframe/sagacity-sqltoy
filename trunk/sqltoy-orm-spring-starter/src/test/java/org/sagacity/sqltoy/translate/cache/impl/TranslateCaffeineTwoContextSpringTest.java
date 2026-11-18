package org.sagacity.sqltoy.translate.cache.impl;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotSame;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.HashMap;

import org.junit.jupiter.api.Test;
import org.sagacity.sqltoy.SqlToyContext;
import org.sagacity.sqltoy.translate.cache.TranslateCacheManager;
import org.sagacity.sqltoy.translate.model.TranslateConfigModel;
import org.springframework.context.annotation.AnnotationConfigApplicationContext;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * 端到端回归(Boot starter 模块):两个真实 Spring ApplicationContext 各自持有 SqlToyContext,
 * 使用 caffeine 翻译缓存,验证缓存管理器按容器/上下文隔离,关闭一个容器不影响另一个。
 * <p>
 * 覆盖本轮"TranslateCaffeineManager 静态 cacheManager 改实例"修复:旧实现两个容器共享同一个
 * org.caffeine...Cache 实例,一个容器关闭后另一个容器的翻译缓存随之失效。
 * </p>
 */
public class TranslateCaffeineTwoContextSpringTest {

	private static final String TRANSLATE_CONFIG = "classpath:translate/caffeine-two-context-translate.xml";

	@Configuration
	static class CtxConfig {
		@Bean(name = "sqlToyContext", initMethod = "initialize", destroyMethod = "destroy")
		public SqlToyContext sqlToyContext() {
			SqlToyContext context = new SqlToyContext();
			context.setCacheType("caffeine");
			context.setTranslateConfig(TRANSLATE_CONFIG);
			return context;
		}
	}

	@Test
	public void twoSpringContextsKeepCaffeineCachesIsolated() {
		AnnotationConfigApplicationContext firstContainer = new AnnotationConfigApplicationContext(CtxConfig.class);
		AnnotationConfigApplicationContext secondContainer = new AnnotationConfigApplicationContext(CtxConfig.class);
		try {
			SqlToyContext firstContext = firstContainer.getBean(SqlToyContext.class);
			SqlToyContext secondContext = secondContainer.getBean(SqlToyContext.class);
			TranslateCacheManager firstManager = firstContext.getTranslateManager().getTranslateCacheManager();
			TranslateCacheManager secondManager = secondContext.getTranslateManager().getTranslateCacheManager();
			assertNotNull(firstManager);
			assertNotNull(secondManager);
			assertNotSame(firstManager, secondManager, "两个Spring容器的caffeine缓存管理器必须独立");
			assertTrue(firstManager.getClass().getName().endsWith("TranslateCaffeineManager"),
					"应走caffeine缓存实现,实际:" + firstManager.getClass().getName());
			TranslateConfigModel cacheModel = firstContext.getTranslateManager().getCacheConfig("caffeineCache");
			assertNotNull(cacheModel, "caffeineCache配置未解析");
			String cacheType = cacheModel.getType();
			HashMap<String, Object[]> firstData = new HashMap<String, Object[]>();
			firstData.put("a", new Object[] { "1" });
			firstManager.put(cacheModel, "caffeineCache", cacheType, firstData);
			HashMap<String, Object[]> firstRead = firstManager.getCache("caffeineCache", cacheType);
			assertNotNull(firstRead);
			assertTrue(firstRead.containsKey("a"));
			HashMap<String, Object[]> secondRead = secondManager.getCache("caffeineCache", cacheType);
			assertTrue(secondRead == null || !secondRead.containsKey("a"), "容器1的缓存不得出现在容器2");
			// 关闭容器1:容器2的caffeine缓存必须仍可用(旧静态实现会被一起关闭)
			firstContainer.close();
			HashMap<String, Object[]> secondData = new HashMap<String, Object[]>();
			secondData.put("b", new Object[] { "2" });
			secondManager.put(cacheModel, "caffeineCache", cacheType, secondData);
			HashMap<String, Object[]> secondAfterPut = secondManager.getCache("caffeineCache", cacheType);
			assertNotNull(secondAfterPut);
			assertTrue(secondAfterPut.containsKey("b"), "关闭容器1后容器2仍应可正常读写caffeine缓存");
		} finally {
			secondContainer.close();
		}
	}
}
