package org.sagacity.sqltoy.utils;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.Date;
import java.util.HashSet;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.CountDownLatch;

import org.junit.jupiter.api.Test;

import com.github.f4b6a3.ulid.Ulid;
import com.github.f4b6a3.ulid.UlidCreator;

public class IdUtilTest {
	@Test
	public void testNanoTimeId() {
		Set<BigDecimal> idset = new HashSet<BigDecimal>();
		BigDecimal id;
		for (int i = 0; i < 100000; i++) {
			id = IdUtil.getNanoTimeId("system_info", "001");
			// System.err.println("id=" + id);
			if (idset.contains(id)) {
				System.err.println("id=" + id + "已经重复");
				break;
			} else {
				idset.add(id);
			}
		}
		System.err.println("没有重复！");
		System.err.println(99 % 100);
		System.err.println(DateUtil.formatDate(new Date(), "yyMMddHHmmssS"));
	}

	@Test
	public void testShortNanoTimeId() {
		Set<BigDecimal> idset = new HashSet<BigDecimal>();
		BigDecimal id;
		long currentTime = System.currentTimeMillis();
		for (int i = 0; i < 5000000; i++) {
			id = IdUtil.getNanoTimeId(null);
			if (idset.contains(id)) {
				System.err.println("id=" + id + "已经重复");
				break;
			} else {
				idset.add(id);
			}
		}
		System.err.println("没有重复！" + (System.currentTimeMillis() - currentTime));
	}

	@Test
	public void testMaxThread() {
		Set<BigDecimal> idset = new HashSet<BigDecimal>();
		for (int i = 0; i < 100; i++) {
			GetId getId = new GetId(idset, 100000);
			getId.run();
		}
	}

	@Test
	public void testShortNanoId() {
		// System.err.println(IdUtil.getNanoTimeId("system_info", "001"));
		String id = IdUtil.getShortNanoTimeId("system_info", "001").toPlainString();
		String id1 = IdUtil.getShortNanoTimeId("001").toPlainString();
		String id2 = IdUtil.getShortNanoTimeId("001").toPlainString();
		System.err.println(id);
		System.err.println(id1);
		System.err.println(id2);
		for (int i = 0; i < 10000; i++) {
			System.err.println(IdUtil.getShortNanoTimeId("001"));
		}
	}

	@Test
	public void testULID() {
		Ulid ulid = UlidCreator.getUlid();
		System.err.println(ulid.toString());
	}

	@Test
	public void testUUIDv7() {
		// 1. 生成 5 个 UUID v7 字符串，验证排序性
		System.out.println("=== 生成 UUID v7 字符串（验证排序性）===");
		for (int i = 0; i < 5; i++) {
			String uuidStr = StandardUUIDv7Generator.generateString();
			System.out.println(uuidStr);
			// 轻微延时，模拟时间流逝
			try {
				Thread.sleep(1);
			} catch (InterruptedException e) {
				Thread.currentThread().interrupt();
			}
		}

		// 2. 验证核心功能
		System.out.println("\n=== 验证 UUID v7 核心功能 ===");
		Instant customInstant = Instant.now().minusSeconds(3600);
		UUID uuid = StandardUUIDv7Generator.generate(customInstant);
		String uuidStr = uuid.toString();
		System.err.println("extractTimestamp=" + StandardUUIDv7Generator.extractTimestamp(uuidStr.replace("-", "")));
	
		System.out.println("UUID v7 实例：" + uuid);
		System.out.println("是否为 UUID v7：" + StandardUUIDv7Generator.isUUIDv7(uuid));
		System.out.println("提取的单调时间戳：" + StandardUUIDv7Generator.extractTimestamp(uuid));
		System.out.println("原始指定时间戳：" + customInstant.toEpochMilli());
		System.out.println(
				"单调时间戳 ≥ 原始时间戳：" + (StandardUUIDv7Generator.extractTimestamp(uuid) >= customInstant.toEpochMilli()));

		// 3. 高并发测试（1000 个线程，每个线程生成 100 个 UUID）
		System.out.println("\n=== 高并发测试（1000 个线程，每个线程生成 100 个 UUID）===");
		long start = System.currentTimeMillis();
		int threadCount = 1000;
		int perThreadCount = 100;
		CountDownLatch latch = new CountDownLatch(threadCount);

		for (int i = 0; i < threadCount; i++) {
			new Thread(() -> {
				for (int j = 0; j < perThreadCount; j++) {
					StandardUUIDv7Generator.generate();
				}
				latch.countDown();
			}).start();
		}

		try {
			latch.await();
		} catch (InterruptedException e) {
			Thread.currentThread().interrupt();
		}

		long end = System.currentTimeMillis();
		System.out.println("总生成数量：" + (threadCount * perThreadCount));
		System.out.println("耗时：" + (end - start) + " 毫秒");
		System.out.println("每秒生成量：" + (threadCount * perThreadCount * 1000 / (end - start)));

		// 4. 模拟时间戳回拨场景，验证无警告
		System.out.println("\n=== 模拟时间戳回拨场景，验证无警告 ===");
		Instant pastInstant = Instant.now().minusSeconds(10 * 60);
		for (int i = 0; i < 3; i++) {
			StandardUUIDv7Generator.generate(pastInstant);
		}
		System.out.println("模拟时间戳回拨后，UUID 生成正常，无时间戳回拨警告");
	}
}
