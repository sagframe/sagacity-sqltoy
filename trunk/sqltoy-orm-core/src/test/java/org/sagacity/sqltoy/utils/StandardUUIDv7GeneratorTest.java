package org.sagacity.sqltoy.utils;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.time.Instant;
import java.util.Collections;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicReference;

import org.junit.jupiter.api.Test;

/**
 * UUIDv7生成的并发正确性回归:随机数取数改造为方法内局部缓冲区后(去掉静态共享缓冲区与其外层锁),
 * 并发场景下仍必须满足版本/变体位、时间戳一致、同批无重复
 */
public class StandardUUIDv7GeneratorTest {

	@Test
	public void versionAndVariantBits() {
		UUID uuid = StandardUUIDv7Generator.generate();
		assertEquals(7, uuid.version(), "版本位应为7");
		assertEquals(2, uuid.variant(), "变体位应为RFC 4122(IETF)");
		assertTrue(StandardUUIDv7Generator.isUUIDv7(uuid));
	}

	@Test
	public void timestampIsEmbeddedAndMonotonic() {
		// 传入明显大于当前单调时钟的时刻:时间戳直接内嵌该毫秒值
		Instant future = Instant.now().plusMillis(5000);
		long futureTs = future.toEpochMilli();
		UUID uuid = StandardUUIDv7Generator.generate(future);
		assertEquals(futureTs, StandardUUIDv7Generator.extractTimestamp(uuid));
		assertEquals(futureTs, StandardUUIDv7Generator.extractTimestamp(uuid.toString()));
		// 传入更早时刻(时间回拨)时单调时钟不得倒退:取上次值+1
		long afterRollback = StandardUUIDv7Generator.extractTimestamp(
				StandardUUIDv7Generator.generate(Instant.EPOCH));
		assertTrue(afterRollback >= futureTs, "单调时钟不得倒退,实际:" + afterRollback + ",基准:" + futureTs);
	}

	@Test
	public void stringFormWithoutDashes() {
		String id = IdUtil.getUUID();
		assertFalse(id.contains("-"));
		assertEquals(32, id.length());
	}

	/**
	 * 30线程并发取ID:不得出现重复,且每个ID都是合法v7
	 */
	@Test
	public void concurrentGenerationHasNoDuplicates() throws Exception {
		int threads = 30;
		int perThread = 500;
		final CountDownLatch start = new CountDownLatch(1);
		final List<String> all = Collections.synchronizedList(new java.util.ArrayList<String>());
		final AtomicReference<Throwable> failure = new AtomicReference<Throwable>();
		Thread[] workers = new Thread[threads];
		for (int i = 0; i < threads; i++) {
			workers[i] = new Thread(() -> {
				try {
					start.await();
					for (int j = 0; j < perThread; j++) {
						all.add(StandardUUIDv7Generator.generate().toString());
					}
				} catch (Throwable t) {
					failure.compareAndSet(null, t);
				}
			});
			workers[i].start();
		}
		start.countDown();
		for (Thread worker : workers) {
			worker.join(60000);
			assertFalse(worker.isAlive(), "生成线程未在超时内结束");
		}
		assertTrue(failure.get() == null, "生成过程不应抛异常:" + failure.get());
		assertEquals(threads * perThread, all.size());
		Set<String> distinct = new HashSet<String>(all);
		assertEquals(all.size(), distinct.size(), "并发生成的ID出现重复");
	}
}
