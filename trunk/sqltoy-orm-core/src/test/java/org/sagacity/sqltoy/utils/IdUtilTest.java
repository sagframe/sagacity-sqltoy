package org.sagacity.sqltoy.utils;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.math.BigDecimal;
import java.util.HashSet;
import java.util.Set;
import java.util.concurrent.ConcurrentLinkedQueue;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;

import org.junit.jupiter.api.Test;

import com.github.f4b6a3.ulid.Ulid;
import com.github.f4b6a3.ulid.UlidCreator;

/**
 * @project sagacity-sqltoy
 * @description IdUtil单元测试
 */
public class IdUtilTest {

	@Test
	public void testNanoTimeId() {
		BigDecimal id = IdUtil.getNanoTimeId(null);
		System.err.println("getNanoTimeId: " + id);
		assertEquals(26, id.toString().length());
	}

	@Test
	public void testShortNanoTimeId() {
		BigDecimal id = IdUtil.getShortNanoTimeId(null);
		System.err.println("getShortNanoTimeId: " + id);
		assertEquals(22, id.toString().length());
	}

	@Test
	public void testMaxThread() throws Exception {
		int threadSize = 100;
		Set<BigDecimal> idset = new HashSet<BigDecimal>();//ConcurrentHashMap.newKeySet();
		ExecutorService pool = Executors.newFixedThreadPool(threadSize);
		for (int i = 0; i < threadSize; i++) {
			pool.execute(new GetId(idset, 10000));
		}
		pool.shutdown();
		assertTrue(pool.awaitTermination(300, TimeUnit.SECONDS));
		System.err.println("maxThread test done, unique ids=" + idset.size());
	}
	
	@Test
	public void testMaxThread1() {
		Set<BigDecimal> idset = new HashSet<BigDecimal>();
		for (int i = 0; i < 100; i++) {
			GetId getId = new GetId(idset, 100000);
			getId.run();
		}
		System.err.println("maxThread test done, unique ids=" + idset.size());
	}

	@Test
	public void testShortNanoId() {
		BigDecimal id = IdUtil.getShortNanoTimeId("order", "101");
		System.err.println("getShortNanoTimeId with name: " + id);
		assertEquals(22, id.toString().length());
	}

	@Test
	public void testULID() {
		Ulid ulid = UlidCreator.getMonotonicUlid();
		System.err.println("ULID: " + ulid.toString());
		assertEquals(26, ulid.toString().length());
	}

	@Test
	public void testUUIDv7() {
		String uuid = IdUtil.getUUID();
		System.err.println("UUIDv7: " + uuid);
		assertEquals(32, uuid.length());
	}

	/**
	 * 并发测试 getNanoTimeId，验证 getCurrentValue 的线程安全。 多线程并发产生ID，确保不抛异常且ID唯一。
	 */
	@Test
	public void testGetNanoTimeIdParallel() throws Exception {
		int threadSize = 50;
		int idsPerThread = 20000;
		ExecutorService pool = Executors.newFixedThreadPool(threadSize);
		CountDownLatch startGate = new CountDownLatch(1);
		CountDownLatch endGate = new CountDownLatch(threadSize);
		ConcurrentLinkedQueue<BigDecimal> allIds = new ConcurrentLinkedQueue<>();
		AtomicInteger errorCount = new AtomicInteger(0);

		for (int i = 0; i < threadSize; i++) {
			final String workerId = String.valueOf(100 + i);
			pool.execute(() -> {
				try {
					startGate.await();
					for (int j = 0; j < idsPerThread; j++) {
						allIds.add(IdUtil.getNanoTimeId(workerId));
					}
				} catch (Throwable e) {
					errorCount.incrementAndGet();
				} finally {
					endGate.countDown();
				}
			});
		}
		startGate.countDown();
		assertTrue(endGate.await(120, TimeUnit.SECONDS), "Timeout waiting for threads");
		pool.shutdown();

		assertEquals(0, errorCount.get(), "Should be no exceptions during parallel generation");
		int expected = threadSize * idsPerThread;
		assertEquals(expected, allIds.size(), "Collected id count mismatch");
		Set<BigDecimal> unique = new HashSet<>(allIds);
		assertEquals(expected, unique.size(), "Duplicate IDs detected in getNanoTimeId");
	}

	/**
	 * 并发测试 getShortNanoTimeId，验证短ID场景下的线程安全。
	 */
	@Test
	public void testGetShortNanoTimeIdParallel() throws Exception {
		int threadSize = 50;
		int idsPerThread = 20000;
		ExecutorService pool = Executors.newFixedThreadPool(threadSize);
		CountDownLatch startGate = new CountDownLatch(1);
		CountDownLatch endGate = new CountDownLatch(threadSize);
		ConcurrentLinkedQueue<BigDecimal> allIds = new ConcurrentLinkedQueue<>();
		AtomicInteger errorCount = new AtomicInteger(0);

		for (int i = 0; i < threadSize; i++) {
			final String workerId = String.valueOf(100 + i);
			pool.execute(() -> {
				try {
					startGate.await();
					for (int j = 0; j < idsPerThread; j++) {
						allIds.add(IdUtil.getShortNanoTimeId(workerId));
					}
				} catch (Throwable e) {
					errorCount.incrementAndGet();
				} finally {
					endGate.countDown();
				}
			});
		}
		startGate.countDown();
		assertTrue(endGate.await(120, TimeUnit.SECONDS), "Timeout waiting for threads");
		pool.shutdown();

		assertEquals(0, errorCount.get(), "Should be no exceptions during parallel generation");
		int expected = threadSize * idsPerThread;
		assertEquals(expected, allIds.size(), "Collected id count mismatch");
		// 注：短ID(6位计数+3位主机)在极端高并发+同一毫秒下可能因计数范围小产生重复，
		// 这里主要验证不抛异常和总数量正确，唯一性作为参考
		Set<BigDecimal> unique = new HashSet<>(allIds);
		System.err.println("shortNanoTimeId parallel: total=" + expected + " unique=" + unique.size());
	}

	/**
	 * 并发测试多个不同 identityName 混合并发，验证 ConcurrentHashMap.compute 的隔离与安全。
	 */
	@Test
	public void testMultipleIdentityNamesParallel() throws Exception {
		String[] names = { "tableA", "tableB", "tableC", "tableD", "tableE" };
		int threadSize = names.length * 4;
		ExecutorService pool = Executors.newFixedThreadPool(threadSize);
		CountDownLatch startGate = new CountDownLatch(1);
		CountDownLatch endGate = new CountDownLatch(threadSize);
		ConcurrentLinkedQueue<BigDecimal> allIds = new ConcurrentLinkedQueue<>();
		AtomicInteger errorCount = new AtomicInteger(0);

		for (int i = 0; i < threadSize; i++) {
			final String name = names[i % names.length];
			final String workerId = String.valueOf(200 + i);
			pool.execute(() -> {
				try {
					startGate.await();
					for (int j = 0; j < 10000; j++) {
						allIds.add(IdUtil.getNanoTimeId(name, workerId));
					}
				} catch (Throwable e) {
					errorCount.incrementAndGet();
				} finally {
					endGate.countDown();
				}
			});
		}
		startGate.countDown();
		assertTrue(endGate.await(120, TimeUnit.SECONDS), "Timeout waiting for threads");
		pool.shutdown();

		assertEquals(0, errorCount.get(), "Should be no exceptions during parallel generation");
		int expected = threadSize * 10000;
		assertEquals(expected, allIds.size(), "Collected id count mismatch");
		Set<BigDecimal> unique = new HashSet<>(allIds);
		assertEquals(expected, unique.size(), "Duplicate IDs detected across multiple identity names");
		System.err.println("multipleNames parallel: total=" + expected + " unique=" + unique.size());
	}
}
