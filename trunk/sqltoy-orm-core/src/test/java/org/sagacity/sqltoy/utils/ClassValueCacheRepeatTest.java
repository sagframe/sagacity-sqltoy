package org.sagacity.sqltoy.utils;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.File;
import java.io.FileWriter;
import java.lang.ref.WeakReference;
import java.net.URL;
import java.net.URLClassLoader;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.Callable;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.atomic.AtomicInteger;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.sagacity.sqltoy.config.annotation.OneToMany;
import org.sagacity.sqltoy.config.annotation.OneToOne;
import org.sagacity.sqltoy.model.PropsMapperConfig;
import org.sagacity.sqltoy.config.model.TableCascadeModel;

/**
 * ClassValue 缓存改动(2026-11-10,cascadeModels与dtoEntityMapperCache)的反复深测:
 * 补齐基础身份语义之外的四个维度——真实级联注解内容、大次数重复、多线程并发、
 * 以及ClassValue的核心卖点"类卸载即回收"(动态类加载+WeakReference实证)。
 *
 * 旧静态ConcurrentHashMap&lt;Class,...&gt;方案在本测试的t3下会失败:静态Map强引用Class,
 * 类加载器永远不可回收(devtools/OSGi热重载泄漏场景)。
 */
public class ClassValueCacheRepeatTest {

	// ==================== BeanUtil.cascadeModels(ClassValue) ====================

	/** 含真实级联注解的实体:一对多+一对一,验证解析内容与缓存共享 */
	public static class OrderEntity {
		@OneToMany(fields = { "orderId" }, mappedFields = { "orderPid" })
		private List<OrderItem> items;

		@OneToOne(fields = { "userId" }, mappedFields = { "userPid" })
		private UserExt ext;

		public List<OrderItem> getItems() {
			return items;
		}

		public void setItems(List<OrderItem> items) {
			this.items = items;
		}

		public UserExt getExt() {
			return ext;
		}

		public void setExt(UserExt ext) {
			this.ext = ext;
		}
	}

	public static class OrderItem {
		private Long orderPid;

		public Long getOrderPid() {
			return orderPid;
		}

		public void setOrderPid(Long orderPid) {
			this.orderPid = orderPid;
		}
	}

	public static class UserExt {
		private Long userPid;

		public Long getUserPid() {
			return userPid;
		}

		public void setUserPid(Long userPid) {
			this.userPid = userPid;
		}
	}

	public static class PlainEntity {
		private Integer id;
	}

	@Test
	public void t1_annotatedEntityContentAndRepeatHit() {
		List<TableCascadeModel> first = BeanUtil.getCascadeModels(OrderEntity.class);
		assertNotNull(first);
		assertEquals(2, first.size(), "一对多+一对一共2个级联模型");
		// 内容正确性(按字段顺序:items一对多,ext一对一)
		TableCascadeModel oneToMany = first.get(0);
		assertEquals("items", oneToMany.getProperty());
		assertEquals(1, oneToMany.getCascadeType(), "@OneToMany→cascadeType=1");
		assertEquals("orderId", oneToMany.getFields()[0]);
		assertEquals("orderPid", oneToMany.getMappedFields()[0]);
		assertEquals(OrderItem.class, oneToMany.getMappedType());
		TableCascadeModel oneToOne = first.get(1);
		assertEquals("ext", oneToOne.getProperty());
		assertEquals(2, oneToOne.getCascadeType(), "@OneToOne→cascadeType=2");
		assertEquals(UserExt.class, oneToOne.getMappedType());
		// 大次数重复:始终同一实例,内容稳定
		for (int i = 0; i < 1000; i++) {
			assertSame(first, BeanUtil.getCascadeModels(OrderEntity.class), "第" + i + "次调用应命中缓存");
		}
	}

	@Test
	public void t2_concurrentMixedClasses() throws Exception {
		final List<TableCascadeModel> orderRef = BeanUtil.getCascadeModels(OrderEntity.class);
		final List<TableCascadeModel> plainRef = BeanUtil.getCascadeModels(PlainEntity.class);
		int threads = 8;
		int iterations = 2000;
		ExecutorService pool = Executors.newFixedThreadPool(threads);
		CountDownLatch latch = new CountDownLatch(threads);
		AtomicInteger errors = new AtomicInteger();
		for (int t = 0; t < threads; t++) {
			pool.execute(() -> {
				try {
					for (int i = 0; i < iterations; i++) {
						// 混合两类交替访问(同一实例并发只读,ClassValue.get无锁读路径)
						assertSame(orderRef, BeanUtil.getCascadeModels(OrderEntity.class));
						assertSame(plainRef, BeanUtil.getCascadeModels(PlainEntity.class));
					}
				} catch (Throwable e) {
					errors.incrementAndGet();
				} finally {
					latch.countDown();
				}
			});
		}
		assertTrue(latch.await(60, java.util.concurrent.TimeUnit.SECONDS), "并发执行超时");
		pool.shutdown();
		assertEquals(0, errors.get(), "并发访问不应有异常");
	}

	/**
	 * 核心维度:类卸载即回收。动态编译并经独立URLClassLoader加载实体类,调用getCascadeModels
	 * 建立缓存条目后,释放类与加载器的全部强引用——ClassValue条目挂在Class自身,不应阻滞
	 * 加载器回收(WeakReference最终清空);旧静态Map<Class,...>方案在此会泄漏(条目永存)。
	 */
	@Test
	public void t3_classUnloadReclaimsEntry(@TempDir File tempDir) throws Exception {
		String className = "DynUnload" + System.nanoTime();
		File src = new File(tempDir, className + ".java");
		try (FileWriter w = new FileWriter(src, StandardCharsets.UTF_8)) {
			w.write("public class " + className + " { private Integer id; "
					+ "public Integer getId() { return id; } public void setId(Integer id) { this.id = id; } }");
		}
		// 动态编译(无外部依赖,系统编译器)
		javax.tools.JavaCompiler compiler = javax.tools.ToolProvider.getSystemJavaCompiler();
		assertEquals(0, compiler.run(null, null, null, "-d", tempDir.getAbsolutePath(), src.getAbsolutePath()),
				"动态实体编译失败");
		// 独立URLClassLoader加载(双亲为系统加载器,实体本身在独立加载器命名空间)
		URLClassLoader loader = new URLClassLoader(new URL[] { tempDir.toURI().toURL() },
				ClassValueCacheRepeatTest.class.getClassLoader());
		Class<?> dynClass = Class.forName(className, true, loader);
		// 建立缓存条目并验证行为正常
		List<TableCascadeModel> models = BeanUtil.getCascadeModels(dynClass);
		assertNotNull(models);
		assertTrue(models.isEmpty(), "无注解动态实体应为空级联列表");
		assertSame(models, BeanUtil.getCascadeModels(dynClass), "动态类同样命中缓存");
		// 隔离引用后验证回收:静态结构(ClassValue)不得强引用该Class/加载器
		WeakReference<ClassLoader> loaderRef = new WeakReference<>(loader);
		WeakReference<Class<?>> classRef = new WeakReference<>(dynClass);
		loader = null;
		dynClass = null;
		models = null;
		boolean reclaimed = false;
		for (int i = 0; i < 30 && !reclaimed; i++) {
			System.gc();
			Thread.sleep(50);
			reclaimed = (loaderRef.get() == null && classRef.get() == null);
		}
		assertTrue(reclaimed, "类与加载器应可被回收(ClassValue不得阻滞卸载); "
				+ "loader=" + loaderRef.get() + ", class=" + classRef.get());
	}

	// ==================== MapperUtils.dtoEntityMapperCache(ClassValue<CHM>两级) ====================

	public static class SourceVO implements java.io.Serializable {
		private String name;
		private Integer age;

		public String getName() {
			return name;
		}

		public void setName(String name) {
			this.name = name;
		}

		public Integer getAge() {
			return age;
		}

		public void setAge(Integer age) {
			this.age = age;
		}
	}

	public static class TargetVO implements java.io.Serializable {
		private String name;
		private Integer age;

		public String getName() {
			return name;
		}

		public void setName(String name) {
			this.name = name;
		}

		public Integer getAge() {
			return age;
		}

		public void setAge(Integer age) {
			this.age = age;
		}
	}

	public static class OtherTargetVO implements java.io.Serializable {
		private String name;

		public String getName() {
			return name;
		}

		public void setName(String name) {
			this.name = name;
		}
	}

	/** 反射读取两级缓存中的条目(sourceClass → innerMap → (resultType,mapKey)) */
	@SuppressWarnings("unchecked")
	private static Object cachedEntry(Class source, Object resultType, String mapKey) throws Exception {
		java.lang.reflect.Field f = MapperUtils.class.getDeclaredField("dtoEntityMapperCache");
		f.setAccessible(true);
		Object classValue = f.get(null);
		java.lang.reflect.Method get = ClassValue.class.getMethod("get", Class.class);
		ConcurrentHashMap<List, Object> inner = (ConcurrentHashMap<List, Object>) get.invoke(classValue, source);
		return (inner == null) ? null : inner.get(java.util.Arrays.asList(resultType, mapKey));
	}

	@Test
	public void t4_sameKeyRepeatHitAndNullEmptyMapEquivalence() throws Exception {
		// 公共入口先建立缓存(MapperUtils.map走getDTOEntityMap)
		SourceVO s = new SourceVO();
		s.setName("n");
		TargetVO t = MapperUtils.map(s, TargetVO.class);
		assertEquals("n", t.getName(), "映射行为正常");
		Object entryNullMap = cachedEntry(SourceVO.class, TargetVO.class, "");
		assertNotNull(entryNullMap, "null fieldsNameMap → mapKey=\"\" 应有条目");
		// null 与 empty map 同条目(等价键)
		MapperUtils.map(s, TargetVO.class, new PropsMapperConfig().fieldsMap(new HashMap<String, String>()));
		Object entryEmptyMap = cachedEntry(SourceVO.class, TargetVO.class, "");
		assertSame(entryNullMap, entryEmptyMap, "null与empty的fieldsNameMap应命中同一缓存条目");
		// 大次数重复仍同实例
		for (int i = 0; i < 500; i++) {
			TargetVO again = MapperUtils.map(s, TargetVO.class);
			assertEquals("n", again.getName());
		}
		assertSame(entryNullMap, cachedEntry(SourceVO.class, TargetVO.class, ""), "重复调用应命中同一实例");
	}

	@Test
	public void t5_keySeparationAndTreeMapDeterminism() throws Exception {
		SourceVO s = new SourceVO();
		s.setName("n");
		s.setAge(1);
		// 不同resultType → 独立条目
		MapperUtils.map(s, TargetVO.class);
		MapperUtils.map(s, OtherTargetVO.class);
		Object e1 = cachedEntry(SourceVO.class, TargetVO.class, "");
		Object e2 = cachedEntry(SourceVO.class, OtherTargetVO.class, "");
		assertNotNull(e1);
		assertNotNull(e2);
		// 等值map不同插入顺序 → TreeMap确定性 → 同一条目
		Map<String, String> m1 = new LinkedHashMap<>();
		m1.put("name", "nick");
		m1.put("age", "years");
		Map<String, String> m2 = new LinkedHashMap<>();
		m2.put("age", "years");
		m2.put("name", "nick");
		MapperUtils.map(s, TargetVO.class, new PropsMapperConfig().fieldsMap(m1));
		MapperUtils.map(s, TargetVO.class, new PropsMapperConfig().fieldsMap(m2));
		String mapKey = new java.util.TreeMap<>(m1).toString();
		Object k1 = cachedEntry(SourceVO.class, TargetVO.class, mapKey);
		assertNotNull(k1, "显式fieldsNameMap应按TreeMap序建立条目");
		assertSame(k1, cachedEntry(SourceVO.class, TargetVO.class, new java.util.TreeMap<>(m2).toString()),
				"等值map不同插入序应命中同一条目(TreeMap确定性)");
		// 不同mapKey → 独立条目
		Map<String, String> m3 = new LinkedHashMap<>();
		m3.put("name", "other");
		MapperUtils.map(s, TargetVO.class, new PropsMapperConfig().fieldsMap(m3));
		String mapKey3 = new java.util.TreeMap<>(m3).toString();
		Object k3 = cachedEntry(SourceVO.class, TargetVO.class, mapKey3);
		assertNotNull(k3);
		assertTrue(k1 != k3, "不同fieldsNameMap应独立成条目");
	}

	@Test
	public void t6_concurrentMixedKeys() throws Exception {
		SourceVO s = new SourceVO();
		s.setName("n");
		s.setAge(1);
		// 预热三种键
		MapperUtils.map(s, TargetVO.class);
		MapperUtils.map(s, OtherTargetVO.class);
		Map<String, String> fm = new LinkedHashMap<>();
		fm.put("name", "nick");
		MapperUtils.map(s, TargetVO.class, new PropsMapperConfig().fieldsMap(fm));
		final Object refA = cachedEntry(SourceVO.class, TargetVO.class, "");
		final Object refB = cachedEntry(SourceVO.class, OtherTargetVO.class, "");
		final String fmKey = new java.util.TreeMap<>(fm).toString();
		final Object refC = cachedEntry(SourceVO.class, TargetVO.class, fmKey);
		int threads = 8;
		ExecutorService pool = Executors.newFixedThreadPool(threads);
		List<Future<Void>> futures = new ArrayList<>();
		for (int t = 0; t < threads; t++) {
			futures.add(pool.submit((Callable<Void>) () -> {
				for (int i = 0; i < 1000; i++) {
					// 三键并发交替:各自命中同一实例,映射结果正确
					assertEquals("n", MapperUtils.map(s, TargetVO.class).getName());
					assertEquals("n", MapperUtils.map(s, OtherTargetVO.class).getName());
					TargetVO mapped = MapperUtils.map(s, TargetVO.class, new PropsMapperConfig().fieldsMap(fm));
					assertEquals("n", mapped.getName());
				}
				return null;
			}));
		}
		for (Future<Void> f : futures) {
			f.get(60, java.util.concurrent.TimeUnit.SECONDS);
		}
		pool.shutdown();
		// 并发后条目实例不变(ClassValue/CHM双层的稳定性)
		assertSame(refA, cachedEntry(SourceVO.class, TargetVO.class, ""));
		assertSame(refB, cachedEntry(SourceVO.class, OtherTargetVO.class, ""));
		assertSame(refC, cachedEntry(SourceVO.class, TargetVO.class, fmKey));
	}
}
