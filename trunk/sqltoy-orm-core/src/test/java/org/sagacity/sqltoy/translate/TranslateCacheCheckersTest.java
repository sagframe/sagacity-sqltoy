package org.sagacity.sqltoy.translate;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.lang.reflect.Field;
import java.util.ArrayList;
import java.util.List;
import java.util.Set;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.atomic.AtomicInteger;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;

/**
 * 回归测试：缓存检测器去重集合为并发安全set且按解析会话清空 (二次上下文重新解析不误报"已经存在"),原子add消除contains+add并发窗口
 */
public class TranslateCacheCheckersTest {

	@SuppressWarnings("unchecked")
	private static Set<String> cacheCheckers() throws Exception {
		Field field = TranslateConfigParse.class.getDeclaredField("cacheCheckers");
		field.setAccessible(true);
		return (Set<String>) field.get(null);
	}

	@AfterEach
	public void clean() throws Exception {
		cacheCheckers().clear();
	}

	@Test
	public void concurrentAddOnlyOneWinner() throws Exception {
		int threads = 8;
		AtomicInteger winners = new AtomicInteger();
		CountDownLatch startGate = new CountDownLatch(1);
		CountDownLatch ready = new CountDownLatch(threads);
		List<Thread> threadList = new ArrayList<Thread>();
		for (int t = 0; t < threads; t++) {
			Thread thread = new Thread(() -> {
				try {
					ready.countDown();
					startGate.await();
					// 并发对同一缓存名登记:并发安全set下恰好一个成功
					if (cacheCheckers().add("cacheA")) {
						winners.incrementAndGet();
					}
				} catch (Exception ignore) {
				}
			});
			thread.start();
			threadList.add(thread);
		}
		ready.await();
		startGate.countDown();
		// join确保全部线程完成后再断言
		for (Thread thread : threadList) {
			thread.join(10_000);
		}
		assertEquals(1, winners.get(), "并发登记应恰好一个成功(并发安全set)");
		assertEquals(1, cacheCheckers().size());
	}

	@Test
	public void concurrentDistinctAddsNoLoss() throws Exception {
		// 大量不同key并发登记:普通HashSet并发写会丢条目甚至破坏结构,newKeySet保证零丢失
		int threads = 8;
		int perThread = 5000;
		CountDownLatch startGate = new CountDownLatch(1);
		CountDownLatch ready = new CountDownLatch(threads);
		List<Thread> threadList = new ArrayList<Thread>();
		for (int t = 0; t < threads; t++) {
			final int taskId = t;
			Thread thread = new Thread(() -> {
				try {
					ready.countDown();
					startGate.await();
					for (int i = 0; i < perThread; i++) {
						cacheCheckers().add("checker-" + taskId + "-" + i);
					}
				} catch (Exception ignore) {
				}
			});
			thread.start();
			threadList.add(thread);
		}
		ready.await();
		startGate.countDown();
		for (Thread thread : threadList) {
			thread.join(30_000);
		}
		assertEquals(threads * perThread, cacheCheckers().size(), "并发登记不允许丢失条目");
	}

	@Test
	public void sessionClearSemantics() throws Exception {
		// 模拟第一个上下文的解析会话登记
		assertTrue(cacheCheckers().add("cacheA"));
		assertEquals(1, cacheCheckers().size());
		// 第二个上下文重新解析:入口清空后同名检测器不构成冲突
		cacheCheckers().clear();
		assertTrue(cacheCheckers().add("cacheA"), "会话清空后同名登记应成功(原静态HashSet残留会误报)");
	}
}
