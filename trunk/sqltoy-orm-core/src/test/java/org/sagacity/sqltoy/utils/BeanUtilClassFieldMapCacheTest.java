package org.sagacity.sqltoy.utils;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.lang.ref.WeakReference;
import java.lang.reflect.Field;
import java.net.URL;
import java.net.URLClassLoader;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Map;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicReference;

import org.junit.jupiter.api.Test;
import org.sagacity.sqltoy.config.annotation.Column;
import org.sagacity.sqltoy.model.JdbcTypes;

/**
 * BeanUtil.getClassFieldMap 类级注解缓存的正确性:
 * 返回契约不变(仅请求属性中带@Column的项)、不同属性集合共用同一份类级缓存、返回Map与缓存隔离、并发安全。
 */
public class BeanUtilClassFieldMapCacheTest {

	static class ProbeVO {
		@Column(name = "json_col", type = JdbcTypes.JSON)
		private String jsonCol;
		@Column(name = "jsonb_col", type = JdbcTypes.JSONB)
		private String jsonbCol;
		@Column(name = "uuid_col", type = JdbcTypes.UUID)
		private String uuidCol;
		@Column(name = "plain_col")
		private String plainCol;
		@SuppressWarnings("unused")
		private String noAnnoField;
	}

	static class NoAnnoVO {
		private String name;
		private int age;
	}

	@Test
	public void returnsOnlyRequestedAnnotatedFields() {
		Map<String, Integer> fieldMap = BeanUtil.getClassFieldMap(ProbeVO.class,
				new String[] { "jsonCol", "plainCol", "noAnnoField" });
		assertEquals(2, fieldMap.size(), "只应返回请求属性中带@Column的项:" + fieldMap);
		assertEquals(JdbcTypes.JSON, fieldMap.get("jsoncol").intValue());
		assertEquals(java.sql.Types.OTHER, fieldMap.get("plaincol").intValue());
		assertFalse(fieldMap.containsKey("noannofield"), "无注解字段不应包含");
		assertFalse(fieldMap.containsKey("jsonbcol"), "未请求的字段不应包含");
	}

	@Test
	public void classLevelCacheIsReusedAcrossPropertySets() throws Exception {
		Map<String, Integer> json = BeanUtil.getClassFieldMap(ProbeVO.class, new String[] { "jsonCol" });
		assertEquals(1, json.size());
		Map<String, Integer> classLevelFirst = classLevelCache();
		Map<String, Integer> others = BeanUtil.getClassFieldMap(ProbeVO.class,
				new String[] { "jsonbCol", "uuidCol" });
		assertEquals(2, others.size());
		// update 2026-11-11 缓存改ClassValue承载后按实例身份断言共用:同一Class的类级缓存为同一实例
		assertTrue(classLevelFirst == classLevelCache(), "同一Class不同属性集合应共用同一份类级缓存实例");
	}

	@Test
	public void returnedMapIsIsolatedFromCache() throws Exception {
		Map<String, Integer> first = BeanUtil.getClassFieldMap(ProbeVO.class, new String[] { "jsonCol" });
		first.clear();
		Map<String, Integer> second = BeanUtil.getClassFieldMap(ProbeVO.class, new String[] { "jsonCol" });
		assertEquals(1, second.size(), "修改返回Map不应污染类级缓存");
		assertEquals(JdbcTypes.JSON, second.get("jsoncol").intValue());
	}

	@Test
	public void emptyOrNullPropertiesReturnEmptyMap() {
		assertTrue(BeanUtil.getClassFieldMap(ProbeVO.class, new String[0]).isEmpty());
		assertTrue(BeanUtil.getClassFieldMap(ProbeVO.class, null).isEmpty());
	}

	@Test
	public void classWithoutAnnotationsReturnsEmptyMap() {
		assertTrue(BeanUtil.getClassFieldMap(NoAnnoVO.class, new String[] { "name", "age" }).isEmpty());
	}

	@Test
	public void concurrentCallsAreConsistent() throws Exception {
		int threads = 8;
		CountDownLatch start = new CountDownLatch(1);
		CountDownLatch done = new CountDownLatch(threads);
		AtomicReference<Throwable> failure = new AtomicReference<Throwable>();
		for (int t = 0; t < threads; t++) {
			final int threadNo = t;
			new Thread(() -> {
				try {
					start.await();
					for (int i = 0; i < 2000; i++) {
						Map<String, Integer> map = (threadNo % 2 == 0)
								? BeanUtil.getClassFieldMap(ProbeVO.class, new String[] { "jsonCol", "plainCol" })
								: BeanUtil.getClassFieldMap(ProbeVO.class, new String[] { "jsonbCol" });
						if (threadNo % 2 == 0) {
							if (map.size() != 2 || map.get("jsoncol").intValue() != JdbcTypes.JSON) {
								throw new IllegalStateException("并发读取结果错误:" + map);
							}
						} else if (map.size() != 1 || map.get("jsonbcol").intValue() != JdbcTypes.JSONB) {
							throw new IllegalStateException("并发读取结果错误:" + map);
						}
					}
				} catch (Throwable e) {
					failure.compareAndSet(null, e);
				} finally {
					done.countDown();
				}
			}).start();
		}
		start.countDown();
		assertTrue(done.await(60, TimeUnit.SECONDS), "并发用例超时");
		if (failure.get() != null) {
			throw new AssertionError("并发读取失败", failure.get());
		}
		// update 2026-11-11 ClassValue形态按实例身份断言单次登记
		assertTrue(classLevelCache() == classLevelCache(), "并发下同一Class只应登记一份缓存");
	}

	/**
	 * update 2026-11-11 缓存ClassValue化的核心收益:类卸载不被静态缓存钉住。
	 * 动态编译实体→独立URLClassLoader加载→getClassFieldMap登记→丢弃加载器与强引用→
	 * GC后类应被回收(WeakReference清空)——旧ConcurrentHashMap<Class,...>形态会强引用Class导致本用例失败。
	 */
	@Test
	public void classUnloadingIsNotPinnedByCache() throws Exception {
		Path dir = Files.createTempDirectory("classfield-unload");
		Path src = dir.resolve("DynamicProbeVO.java");
		Files.write(src, ("import org.sagacity.sqltoy.config.annotation.Column;"
				+ "import org.sagacity.sqltoy.model.JdbcTypes;"
				+ "public class DynamicProbeVO {"
				+ "@Column(name = \"dyn_col\", type = JdbcTypes.JSON) private String dynCol;"
				+ "}").getBytes(java.nio.charset.StandardCharsets.UTF_8));
		javax.tools.JavaCompiler compiler = javax.tools.ToolProvider.getSystemJavaCompiler();
		assertEquals(0, compiler.run(null, null, null, "-encoding", "UTF-8",
				"-cp", System.getProperty("java.class.path"), "-d", dir.toString(), src.toString()),
				"动态fixture编译失败");
		URLClassLoader loader = new URLClassLoader(new URL[] { dir.toUri().toURL() },
				BeanUtilClassFieldMapCacheTest.class.getClassLoader());
		WeakReference<Class<?>> weakClass;
		try {
			Class<?> dynamicClass = Class.forName("DynamicProbeVO", false, loader);
			weakClass = new WeakReference<Class<?>>(dynamicClass);
			Map<String, Integer> fieldMap = BeanUtil.getClassFieldMap(dynamicClass, new String[] { "dynCol" });
			assertEquals(1, fieldMap.size(), "动态类字段映射应生效: " + fieldMap);
			assertEquals(JdbcTypes.JSON, fieldMap.get("dyncol").intValue());
			dynamicClass = null;
			fieldMap = null;
		} finally {
			loader.close();
		}
		// 加载器本身也须不可达:类加载器强可达则其类永不卸载
		loader = null;
		// GC后类应被回收:ClassValue条目随类卸载消亡,不构成强引用
		for (int i = 0; i < 30 && weakClass.get() != null; i++) {
			System.gc();
			Thread.sleep(50);
		}
		assertTrue(weakClass.get() == null, "类级字段缓存不得阻止动态类的卸载(静态Map<Class,...>形态会钉住类)");
	}

	@SuppressWarnings("unchecked")
	private static Map<String, Integer> classLevelCache() throws Exception {
		Field field = BeanUtil.class.getDeclaredField("classFieldTypeCache");
		field.setAccessible(true);
		return ((ClassValue<Map<String, Integer>>) field.get(null)).get(ProbeVO.class);
	}
}
