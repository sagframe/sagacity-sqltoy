package org.sagacity.sqltoy;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.junit.jupiter.api.Assertions.fail;

import java.lang.reflect.Field;
import java.util.Collections;
import java.util.HashMap;
import java.util.IdentityHashMap;
import java.util.List;
import java.util.Set;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;

import org.h2.jdbcx.JdbcDataSource;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.sagacity.sqltoy.translate.TranslateManager;
import org.sagacity.sqltoy.translate.cache.TranslateCacheManager;
import org.sagacity.sqltoy.translate.model.TranslateConfigModel;

/**
 * 端到端回归:多个SqlToyContext并发初始化(而非只并发解析translate配置)时,
 * 翻译缓存管理器必须按context实例隔离,不得共享/互相销毁。
 * <p>
 * 覆盖本轮"cacheCheckers改解析会话局部"与"TranslateEhcacheManager/TranslateCaffeineManager
 * 静态cacheManager改实例"两处修复:旧实现下并发初始化会出现duplicate checker误报,
 * 且一个context.destroy()会关闭另一个context的缓存管理器。
 * </p>
 */
public class SqlToyContextConcurrentInitTest {

	private static final String TRANSLATE_CONFIG = "classpath:translate/concurrent-init-translate.xml";

	private static final int CONTEXTS = 4;

	private static final int ROUNDS = 10;

	@BeforeEach
	@AfterEach
	public void resetStaticOwners() throws Exception {
		Field field = SqlToyContext.class.getDeclaredField("STATIC_STATE_OWNERS");
		field.setAccessible(true);
		((Set<?>) field.get(null)).clear();
	}

	@Test
	public void concurrentContextInitKeepsTranslateCachesIsolated() throws Exception {
		for (int round = 0; round < ROUNDS; round++) {
			SqlToyContext[] contexts = new SqlToyContext[CONTEXTS];
			for (int i = 0; i < CONTEXTS; i++) {
				contexts[i] = newContext(round, i);
			}
			CountDownLatch gate = new CountDownLatch(1);
			CountDownLatch done = new CountDownLatch(CONTEXTS);
			List<Throwable> failures = new CopyOnWriteArrayList<Throwable>();
			for (int i = 0; i < CONTEXTS; i++) {
				final SqlToyContext context = contexts[i];
				Thread thread = new Thread(() -> {
					try {
						gate.await();
						context.initialize();
					} catch (Throwable e) {
						failures.add(e);
					} finally {
						done.countDown();
					}
				});
				thread.start();
			}
			gate.countDown();
			assertTrue(done.await(120, TimeUnit.SECONDS), "round " + round + " 并发初始化超时");
			if (!failures.isEmpty()) {
				fail("round " + round + " 并发初始化异常:" + failures.get(0));
			}
			try {
				assertTranslateCachesIsolated(contexts, round);
			} finally {
				for (SqlToyContext context : contexts) {
					destroyQuietly(context);
				}
			}
		}
	}

	private static SqlToyContext newContext(int round, int index) {
		SqlToyContext context = new SqlToyContext();
		JdbcDataSource dataSource = new JdbcDataSource();
		dataSource.setURL("jdbc:h2:mem:ctxinit_" + round + "_" + index + ";DB_CLOSE_DELAY=-1");
		dataSource.setUser("sa");
		context.setDefaultDataSource(dataSource);
		context.setTranslateConfig(TRANSLATE_CONFIG);
		return context;
	}

	private static void assertTranslateCachesIsolated(SqlToyContext[] contexts, int round) {
		Set<TranslateCacheManager> managers = Collections
				.newSetFromMap(new IdentityHashMap<TranslateCacheManager, Boolean>());
		for (SqlToyContext context : contexts) {
			TranslateCacheManager manager = context.getTranslateManager().getTranslateCacheManager();
			assertNotNull(manager, "round " + round + " 翻译缓存管理器未创建");
			managers.add(manager);
		}
		assertEquals(CONTEXTS, managers.size(), "round " + round + " 各context必须持有独立缓存管理器");
		TranslateManager first = contexts[0].getTranslateManager();
		TranslateManager second = contexts[1].getTranslateManager();
		TranslateConfigModel cacheModel = first.getCacheConfig("ctxCache");
		assertNotNull(cacheModel, "round " + round + " ctxCache配置未解析");
		String cacheType = cacheModel.getType();
		HashMap<String, Object[]> firstData = new HashMap<String, Object[]>();
		firstData.put("k" + round, new Object[] { "v" + round });
		first.getTranslateCacheManager().put(cacheModel, "ctxCache", cacheType, firstData);
		HashMap<String, Object[]> firstRead = first.getTranslateCacheManager().getCache("ctxCache", cacheType);
		assertNotNull(firstRead);
		assertTrue(firstRead.containsKey("k" + round), "round " + round + " 本context写入应可读回");
		HashMap<String, Object[]> secondRead = second.getTranslateCacheManager().getCache("ctxCache", cacheType);
		assertTrue(secondRead == null || !secondRead.containsKey("k" + round),
				"round " + round + " 一个context的写入不得出现在另一个context");
		// ctx1先自行写入(建立自己的缓存),再销毁ctx0,验证ctx1的缓存不被连带关闭
		HashMap<String, Object[]> secondData = new HashMap<String, Object[]>();
		secondData.put("b", new Object[] { "1" });
		second.getTranslateCacheManager().put(cacheModel, "ctxCache", cacheType, secondData);
		contexts[0].destroy();
		assertTrue(second.getTranslateCacheManager().hasCache("ctxCache"),
				"round " + round + " 销毁ctx0后ctx1的缓存应仍存在");
		HashMap<String, Object[]> secondAfterDestroy = second.getTranslateCacheManager().getCache("ctxCache", cacheType);
		assertNotNull(secondAfterDestroy);
		assertTrue(secondAfterDestroy.containsKey("b"), "round " + round + " 销毁ctx0后ctx1的既有缓存数据应仍可读");
		HashMap<String, Object[]> secondData2 = new HashMap<String, Object[]>();
		secondData2.put("b2", new Object[] { "2" });
		second.getTranslateCacheManager().put(cacheModel, "ctxCache", cacheType, secondData2);
		HashMap<String, Object[]> secondAfterPut = second.getTranslateCacheManager().getCache("ctxCache", cacheType);
		assertNotNull(secondAfterPut);
		assertTrue(secondAfterPut.containsKey("b2"), "round " + round + " 销毁ctx0后ctx1仍应可正常写入");
	}

	private static void destroyQuietly(SqlToyContext context) {
		try {
			context.destroy();
		} catch (Throwable ignored) {
			// 已销毁的context再次destroy可能重复关闭缓存管理器,忽略即可
		}
	}
}
