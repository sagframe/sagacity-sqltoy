package org.sagacity.sqltoy.utils;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNotSame;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.lang.reflect.Constructor;
import java.lang.reflect.Method;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;

import org.junit.jupiter.api.Test;

/**
 * BeanUtil全局缓存限额(GlobalCacheBound)与ClassValue迁移后的逻辑正确性专项(2026-11-16):
 * ①GlobalCacheBound小容量直接语义测试(putIfAbsent不覆盖/超限跨类回收/并发收敛);
 * ②集成洪泛下映射结果正确且总量有界;③set/get属性路径在淘汰压力下仍正确(写入即淘汰兜底);
 * ④枚举key双向转换/record映射/newBean失败缓存等ClassValue迁移路径回归。
 */
public class BeanUtilGlobalCacheBoundLogicTest {

	public static class VoA {
		private int id;
		private String name;

		public int getId() { return id; }
		public void setId(int id) { this.id = id; }
		public String getName() { return name; }
		public void setName(String name) { this.name = name; }
	}

	public static class VoB {
		private int id;
		private String name;

		public int getId() { return id; }
		public void setId(int id) { this.id = id; }
		public String getName() { return name; }
		public void setName(String name) { this.name = name; }
	}

	public static class NoDefaultCtor {
		private final int x;

		public NoDefaultCtor(int x) { this.x = x; }
		public int getX() { return x; }
	}

	public record TestRecord(int id, String name) {
	}

	public enum KeyedEnum {
		ACTIVE(1), INACTIVE(2);

		private final int value;

		KeyedEnum(int value) { this.value = value; }
		public int getValue() { return value; }
	}

	public enum PlainEnum {
		RED, GREEN
	}

	private static final int[] INDEXES = { 0, 1 };
	private static final String[] COLUMNS = { "ID", "NAME" };

	private static List<Object[]> rows(int id, String name) {
		List<Object[]> rows = new ArrayList<>();
		rows.add(new Object[] { id, name });
		return rows;
	}

	// ==================== GlobalCacheBound直接语义(小容量反射) ====================

	private static Object newBound(int maxSize) throws Exception {
		Class<?> boundClass = Class.forName("org.sagacity.sqltoy.utils.BeanUtil$GlobalCacheBound");
		Constructor<?> ctor = boundClass.getDeclaredConstructor(int.class);
		ctor.setAccessible(true);
		return ctor.newInstance(maxSize);
	}

	private static void boundPut(Object bound, ConcurrentHashMap<String, Object> cache, String key, Object value)
			throws Exception {
		Method put = bound.getClass().getDeclaredMethod("put", ConcurrentHashMap.class, String.class, Object.class);
		put.setAccessible(true);
		put.invoke(bound, cache, key, value);
	}

	private static void boundRegister(Object bound, Class<?> type, ConcurrentHashMap<String, Object> cache)
			throws Exception {
		Method register = bound.getClass().getDeclaredMethod("register", Class.class, ConcurrentHashMap.class);
		register.setAccessible(true);
		register.invoke(bound, type, cache);
	}

	private static int boundTotal(Object bound) throws Exception {
		Method total = bound.getClass().getDeclaredMethod("getTotalSize");
		total.setAccessible(true);
		return (Integer) total.invoke(bound);
	}

	@Test
	public void putIfAbsentDoesNotOverwrite() throws Exception {
		Object bound = newBound(1000);
		ConcurrentHashMap<String, Object> cache = new ConcurrentHashMap<>();
		boundRegister(bound, VoA.class, cache);
		boundPut(bound, cache, "k", "first");
		boundPut(bound, cache, "k", "second");
		assertEquals("first", cache.get("k"), "已存在键不得覆盖(putIfAbsent语义)");
		assertEquals(1, boundTotal(bound), "重复写入不增加计数");
	}

	@Test
	public void evictsAcrossClassesToWatermark() throws Exception {
		Object bound = newBound(40);
		ConcurrentHashMap<String, Object> a = new ConcurrentHashMap<>();
		ConcurrentHashMap<String, Object> b = new ConcurrentHashMap<>();
		ConcurrentHashMap<String, Object> c = new ConcurrentHashMap<>();
		boundRegister(bound, VoA.class, a);
		boundRegister(bound, VoB.class, b);
		boundRegister(bound, NoDefaultCtor.class, c);
		for (int i = 0; i < 20; i++) {
			boundPut(bound, a, "a" + i, i);
			boundPut(bound, b, "b" + i, i);
			boundPut(bound, c, "c" + i, i);
		}
		assertTrue(boundTotal(bound) <= 40, "跨类合计超限后必须收敛到上限内,实际:" + boundTotal(bound));
		// 回收后仍存在的键值不得串位
		for (ConcurrentHashMap<String, Object> cache : new ConcurrentHashMap[] { a, b, c }) {
			for (java.util.Map.Entry<String, Object> e : cache.entrySet()) {
				assertEquals(Integer.parseInt(e.getKey().substring(1)), e.getValue(), "回收不得使键值串位");
			}
		}
		// 回收后新写入仍生效
		boundPut(bound, a, "after", "ok");
		assertEquals("ok", a.get("after"));
	}

	@Test
	public void concurrentPutsStayBounded() throws Exception {
		Object bound = newBound(200);
		int threads = 8;
		int perThread = 500;
		ConcurrentHashMap<String, Object>[] caches = new ConcurrentHashMap[threads];
		for (int t = 0; t < threads; t++) {
			caches[t] = new ConcurrentHashMap<>();
			boundRegister(bound, t % 2 == 0 ? VoA.class : VoB.class, caches[t]);
		}
		CountDownLatch gate = new CountDownLatch(1);
		CountDownLatch done = new CountDownLatch(threads);
		AtomicInteger failures = new AtomicInteger();
		for (int t = 0; t < threads; t++) {
			final int idx = t;
			Thread thread = new Thread(() -> {
				try {
					gate.await();
					for (int i = 0; i < perThread; i++) {
						boundPut(bound, caches[idx], "k" + idx + "-" + i, i);
					}
				} catch (Throwable e) {
					failures.incrementAndGet();
				} finally {
					done.countDown();
				}
			});
			thread.start();
		}
		gate.countDown();
		assertTrue(done.await(60, TimeUnit.SECONDS), "并发写入须在超时内完成");
		assertEquals(0, failures.get(), "并发写入不得抛异常");
		assertTrue(boundTotal(bound) <= 200 + threads, "并发写入后总量须收敛(软上限),实际:" + boundTotal(bound));
	}

	// ==================== 集成:洪泛下映射正确且总量有界 ====================

	@Test
	public void concurrentFloodMappingCorrectAndBounded() throws Exception {
		int threads = 6;
		int perThread = 2000;
		CountDownLatch gate = new CountDownLatch(1);
		CountDownLatch done = new CountDownLatch(threads);
		AtomicInteger failures = new AtomicInteger();
		for (int t = 0; t < threads; t++) {
			final int idx = t;
			Thread thread = new Thread(() -> {
				try {
					gate.await();
					Class<?> voClass = idx % 2 == 0 ? VoA.class : VoB.class;
					for (int i = 0; i < perThread; i++) {
						List result = BeanUtil.reflectListToBean(null, rows(7, "v" + i), INDEXES, COLUMNS,
								new String[] { "FLOOD" + idx + "-" + i, "VARCHAR" }, voClass, true);
						Object vo = result.get(0);
						assertEquals(7, BeanUtil.getProperty(vo, "id"), "洪泛下id映射必须正确");
						assertEquals("v" + i, BeanUtil.getProperty(vo, "name"), "洪泛下name映射必须正确");
					}
				} catch (Throwable e) {
					failures.incrementAndGet();
				} finally {
					done.countDown();
				}
			});
			thread.start();
		}
		gate.countDown();
		assertTrue(done.await(120, TimeUnit.SECONDS), "洪泛须在超时内完成");
		assertEquals(0, failures.get(), "洪泛映射不得出错");
		assertTrue(BeanUtil.mappingPlanCacheTotalSize() <= 20000,
				"洪泛后全局总量须有界,实际:" + BeanUtil.mappingPlanCacheTotalSize());
	}

	@Test
	public void matchMethodsCacheHitIsStable() {
		Method[] first = BeanUtil.matchSetMethods(VoA.class, "id", "name");
		Method[] second = BeanUtil.matchSetMethods(VoA.class, "id", "name");
		assertSame(first, second, "同形态命中缓存应返回同一数组");
		Method[] reversed = BeanUtil.matchSetMethods(VoA.class, "name", "id");
		assertNotSame(first, reversed, "属性顺序不同是不同缓存键");
		assertEquals("setId", first[0].getName());
		assertEquals("setName", reversed[0].getName());
		Method[] withNull = BeanUtil.matchGetMethods(VoA.class, "id", "missingProp");
		assertNotNull(withNull[0]);
		assertNull(withNull[1], "未匹配属性位置为null");
	}

	// ==================== ClassValue迁移路径回归 ====================

	@Test
	public void enumKeyRoundTrip() {
		assertEquals(1, BeanUtil.getEnumValue(KeyedEnum.ACTIVE), "有getValue的枚举取key");
		assertEquals(KeyedEnum.INACTIVE, BeanUtil.newEnumInstance(2, KeyedEnum.class), "按key反查枚举");
		assertEquals(KeyedEnum.ACTIVE, BeanUtil.newEnumInstance("active", KeyedEnum.class), "key未命中回退name(忽略大小写)");
		assertEquals("RED", BeanUtil.getEnumValue(PlainEnum.RED), "无自定义字段枚举取name");
		assertEquals(PlainEnum.GREEN, BeanUtil.newEnumInstance("green", PlainEnum.class), "plain枚举按name反查");
		assertNull(BeanUtil.newEnumInstance(999, KeyedEnum.class), "无匹配返回null");
	}

	@Test
	public void newBeanCachesNoSuchMethodFailure() {
		assertThrows(NoSuchMethodException.class, () -> BeanUtil.newBean(NoDefaultCtor.class));
		// 第二次命中缓存的异常holder,仍抛出同类型异常(不重复反射探测)
		assertThrows(NoSuchMethodException.class, () -> BeanUtil.newBean(NoDefaultCtor.class));
	}

	@Test
	public void recordMappingRepeatedStable() {
		for (int i = 0; i < 50; i++) {
			List result = BeanUtil.reflectListToBean(null, rows(42, "rec"), INDEXES, COLUMNS,
					new String[] { "INTEGER", "VARCHAR" }, TestRecord.class, true);
			TestRecord record = (TestRecord) result.get(0);
			assertEquals(42, record.id());
			assertEquals("rec", record.name());
		}
	}
}