package org.sagacity.sqltoy.utils;

import static org.junit.jupiter.api.Assertions.assertEquals;

import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.atomic.AtomicInteger;

import org.junit.jupiter.api.Test;

/**
 * 回归测试：并发场景下不同线程持有可能不同引用的同名表字符串,必须锁同一monitor, 保证序列读改写的原子性,不产生重复ID
 */
public class SnowflakeIdWorkerTest {

	/**
	 * 每次调用产生一个内容相同但引用不同的表名字符串,模拟运行期动态构造表名的场景
	 */
	private static String distinctInstanceTableName() {
		return new StringBuilder("ORDER_INFO").append("").toString();
	}

	@Test
	public void concurrentDistinctStringInstancesNoDuplicateId() throws Exception {
		SnowflakeIdWorker worker = new SnowflakeIdWorker(1, 1);
		int threads = 8;
		int loops = 50_000;
		Set<Long> ids = ConcurrentHashMap.newKeySet();
		AtomicInteger failures = new AtomicInteger();
		CountDownLatch latch = new CountDownLatch(threads);
		for (int t = 0; t < threads; t++) {
			new Thread(() -> {
				try {
					for (int i = 0; i < loops; i++) {
						// 故意不做任何缓存,每批次取一个新的同名不同引用String
						String tableName = (i % 1000 == 0) ? distinctInstanceTableName() : "ORDER_INFO";
						if (!ids.add(worker.nextId(tableName))) {
							failures.incrementAndGet();
						}
					}
				} catch (Exception e) {
					failures.incrementAndGet();
				} finally {
					latch.countDown();
				}
			}).start();
		}
		latch.await();
		assertEquals(0, failures.get());
		assertEquals(threads * loops, ids.size(), "并发产生的ID存在重复");
	}

	@Test
	public void singleThreadIdUniqueness() {
		SnowflakeIdWorker worker = new SnowflakeIdWorker(1, 1);
		Set<Long> ids = ConcurrentHashMap.newKeySet();
		for (int i = 0; i < 100_000; i++) {
			ids.add(worker.nextId());
		}
		assertEquals(100_000, ids.size());
	}
}
