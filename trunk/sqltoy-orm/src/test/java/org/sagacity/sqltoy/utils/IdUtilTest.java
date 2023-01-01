package org.sagacity.sqltoy.utils;

import java.math.BigDecimal;
import java.util.Date;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.SynchronousQueue;
import java.util.concurrent.ThreadPoolExecutor;
import java.util.concurrent.TimeUnit;

import org.junit.jupiter.api.Test;
import org.sagacity.sqltoy.plugins.id.impl.SnowflakeIdGenerator;

import com.google.common.collect.Lists;
import com.google.common.collect.Sets;

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
	public void testSnowflakeId() throws Exception {
		// 并发时存在重复
		SnowflakeIdGenerator snowflakeIdGenerator = new SnowflakeIdGenerator();
		snowflakeIdGenerator.initialize(null);
		ExecutorService es = new ThreadPoolExecutor(0, Integer.MAX_VALUE, 60L, TimeUnit.SECONDS,
				new SynchronousQueue<Runnable>());
		List<CompletableFuture> list = Lists.newArrayList();
		Set<String> strSet = Sets.newCopyOnWriteArraySet();
		for (int i = 0; i < 10000; i++) {
			int finalI = i;
			list.add(CompletableFuture.runAsync(() -> {
				String str = (String) snowflakeIdGenerator.getId(null, null, null, null, null, "java.lang.string", 1,
						1);
				// System.out.println("val: " + str + ", length: " + str.length() + ", =>" +
				// finalI);
				strSet.add(str);
			}, es));
		}
		es.shutdownNow();
		CompletableFuture.allOf(list.stream().toArray(CompletableFuture[]::new)).join();
		System.out.println("end: " + strSet.size());

	}
}
