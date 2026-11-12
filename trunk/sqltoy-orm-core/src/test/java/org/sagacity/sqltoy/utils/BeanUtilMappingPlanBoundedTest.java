package org.sagacity.sqltoy.utils;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.lang.reflect.Field;
import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.Date;
import java.util.List;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.atomic.AtomicInteger;

import org.junit.jupiter.api.Test;

/**
 * update 2026-11-11 BeanUtil映射计划缓存的深测补充(项目必经路径:每个查询结果映射都经过):
 * 在既有BeanUtilMappingPlanTest(等值/复用/列类型隔离/属性序/并发)之上补三类边界:
 * <ol>
 * <li>有界性:MAPPING_PLAN_CACHE_MAX_SIZE=20000,超过后putBounded回收约1/4——洪泛2.5万个
 * 不同(列类型序列)键后缓存不得无限增长,且回收churn后映射结果仍全部正确</li>
 * <li>跨类隔离:同名属性、不同类型的两个VO不得共享映射计划(键含Class)</li>
 * <li>洪泛+并发混合:多线程对洪泛后的缓存做混合键访问,结果稳定一致</li>
 */
public class BeanUtilMappingPlanBoundedTest {

	private static final String[] COLUMNS = { "id", "name" };
	private static final int[] INDEXES = { 0, 1 };

	public static class VoA {
		private int id;
		private String name;

		public int getId() {
			return id;
		}

		public void setId(int id) {
			this.id = id;
		}

		public String getName() {
			return name;
		}

		public void setName(String name) {
			this.name = name;
		}
	}

	/** 与VoA同名属性、不同类型(id为String):计划不得跨类串用 */
	public static class VoB {
		private String id;
		private String name;

		public String getId() {
			return id;
		}

		public void setId(String id) {
			this.id = id;
		}

		public String getName() {
			return name;
		}

		public void setName(String name) {
			this.name = name;
		}
	}

	@Test
	public void floodBeyondMaxKeepsCacheBoundedAndMappingsCorrect() throws Exception {
		planCache().clear();
		List<Object[]> rows = new ArrayList<Object[]>();
		rows.add(new Object[] { 7, "name-7" });
		// 洪泛2.5万个互异的列类型序列键(超过20000上限,触发putBounded回收)
		for (int i = 0; i < 25000; i++) {
			String[] columnTypes = { "FLOOD" + i, "VARCHAR" };
			List result = BeanUtil.reflectListToBean(null, rows, INDEXES, COLUMNS, columnTypes, VoA.class, true);
			assertEquals(1, result.size());
		}
		int sizeAfterFlood = planCache().size();
		assertTrue(sizeAfterFlood <= 20000, "洪泛后缓存不得超过上限20000,实际:" + sizeAfterFlood);
		// 回收churn后既有形态的映射仍正确(重建或命中皆须正确)
		for (int round = 0; round < 3; round++) {
			List result = BeanUtil.reflectListToBean(null, rows, INDEXES, COLUMNS,
					new String[] { "INTEGER", "VARCHAR" }, VoA.class, true);
			VoA vo = (VoA) result.get(0);
			assertEquals(7, vo.getId(), "洪泛后映射值必须保持正确(第" + round + "轮)");
			assertEquals("name-7", vo.getName());
		}
	}

	@Test
	public void sameShapeDifferentClassDoesNotSharePlan() throws Exception {
		planCache().clear();
		List<Object[]> rows = new ArrayList<Object[]>();
		rows.add(new Object[] { 42, "x" });
		VoA a = (VoA) BeanUtil.reflectListToBean(null, rows, INDEXES, COLUMNS,
				new String[] { "INTEGER", "VARCHAR" }, VoA.class, true).get(0);
		VoB b = (VoB) BeanUtil.reflectListToBean(null, rows, INDEXES, COLUMNS,
				new String[] { "INTEGER", "VARCHAR" }, VoB.class, true).get(0);
		assertEquals(42, a.getId(), "VoA的int id按数值映射");
		assertEquals("42", b.getId(), "VoB的String id按文本映射,不得沿用VoA的计划");
		assertEquals("x", a.getName());
		assertEquals("x", b.getName());
		assertTrue(planCache().size() >= 2, "两个类应各建计划");
	}

	@Test
	public void concurrentMixedKeysAfterFloodStayConsistent() throws Exception {
		planCache().clear();
		int threads = 8;
		int perThread = 3000;
		// 每线程用互异键(洪泛)+共享键(命中)混合访问
		CountDownLatch latch = new CountDownLatch(threads);
		AtomicInteger failures = new AtomicInteger();
		for (int t = 0; t < threads; t++) {
			final int threadIndex = t;
			Thread thread = new Thread(() -> {
				try {
					List<Object[]> rows = new ArrayList<Object[]>();
					rows.add(new Object[] { threadIndex, "t" + threadIndex });
					for (int i = 0; i < perThread; i++) {
						String[] uniqueTypes = { "MIX" + threadIndex + "-" + i, "VARCHAR" };
						List unique = BeanUtil.reflectListToBean(null, rows, INDEXES, COLUMNS, uniqueTypes, VoA.class,
								true);
						VoA u = (VoA) unique.get(0);
						if (u.getId() != threadIndex || !("t" + threadIndex).equals(u.getName())) {
							failures.incrementAndGet();
						}
						List shared = BeanUtil.reflectListToBean(null, rows, INDEXES, COLUMNS,
								new String[] { "INTEGER", "VARCHAR" }, VoA.class, true);
						VoA s = (VoA) shared.get(0);
						if (s.getId() != threadIndex || !("t" + threadIndex).equals(s.getName())) {
							failures.incrementAndGet();
						}
					}
				} catch (Throwable e) {
					failures.incrementAndGet();
				} finally {
					latch.countDown();
				}
			});
			thread.start();
		}
		latch.await();
		assertEquals(0, failures.get(), "并发混合键访问不得出现错值");
		assertTrue(planCache().size() <= 20000, "并发洪泛后缓存仍须有界");
	}

	private static ConcurrentHashMap<?, ?> planCache() throws Exception {
		Field field = BeanUtil.class.getDeclaredField("mappingPlanCache");
		field.setAccessible(true);
		return (ConcurrentHashMap<?, ?>) field.get(null);
	}
}
