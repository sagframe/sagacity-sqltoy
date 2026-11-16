package org.sagacity.sqltoy.utils;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.File;
import java.lang.ref.WeakReference;
import java.net.URL;
import java.net.URLClassLoader;
import java.nio.charset.StandardCharsets;
import java.sql.Types;
import java.util.ArrayList;
import java.util.List;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

/**
 * 回归:BeanUtil/EnumUtil中按Class做key的静态缓存改为ClassValue后,不得阻滞热部署类卸载。
 * 覆盖集合:noArgConstructors、setMethods/getMethods、matchSetMethodsCache/matchGetMethodsCache、
 * mappingPlanCache、recordProperties(此处以反射成员缓存路径代表)、EnumUtil.withoutCustomFieldEnum。
 * <p>
 * 证明方式同ClassValueCacheRepeatTest.t3:动态编译+独立URLClassLoader加载,建立缓存条目后释放
 * Class/加载器的全部强引用,WeakReference应最终清空;旧的静态Map<Class,...>方案在此会泄漏。
 * </p>
 */
public class BeanUtilClassValueUnloadTest {

	@Test
	public void migratedClassValueCachesDoNotPinReloadedClasses(@TempDir File tempDir) throws Exception {
		String voName = "DynCacheVo" + System.nanoTime();
		String enumName = "DynCacheEnum" + System.nanoTime();
		writeSource(tempDir, voName + ".java",
				"public class " + voName + " { private Integer id; public Integer getId() { return id; } "
						+ "public void setId(Integer id) { this.id = id; } }");
		writeSource(tempDir, enumName + ".java", "public enum " + enumName + " { A, B }");
		javax.tools.JavaCompiler compiler = javax.tools.ToolProvider.getSystemJavaCompiler();
		assertEquals(0, compiler.run(null, null, null, "-d", tempDir.getAbsolutePath(),
				new File(tempDir, voName + ".java").getAbsolutePath(),
				new File(tempDir, enumName + ".java").getAbsolutePath()), "动态类编译失败");
		URLClassLoader loader = new URLClassLoader(new URL[] { tempDir.toURI().toURL() },
				BeanUtilClassValueUnloadTest.class.getClassLoader());
		Class<?> voClass = Class.forName(voName, true, loader);
		Class<?> enumClass = Class.forName(enumName, true, loader);
		// 建立各缓存条目
		touchCaches(voClass, enumClass);
		// 释放Class与加载器的全部强引用(缓存条目应挂在Class自身,不阻滞回收)
		WeakReference<ClassLoader> loaderRef = new WeakReference<ClassLoader>(loader);
		WeakReference<Class<?>> voClassRef = new WeakReference<Class<?>>(voClass);
		WeakReference<Class<?>> enumClassRef = new WeakReference<Class<?>>(enumClass);
		loader = null;
		voClass = null;
		enumClass = null;
		boolean reclaimed = false;
		// 负载高的CI环境下GC/类卸载可能延后,给足重试窗口(通常1~2次即回收)
		for (int i = 0; i < 80 && !reclaimed; i++) {
			System.gc();
			Thread.sleep(100);
			reclaimed = (loaderRef.get() == null && voClassRef.get() == null && enumClassRef.get() == null);
		}
		assertTrue(reclaimed, "ClassValue缓存不得阻滞类/加载器回收; loader=" + loaderRef.get() + ", voClass="
				+ voClassRef.get() + ", enumClass=" + enumClassRef.get());
	}

	private static void writeSource(File dir, String fileName, String content) throws Exception {
		// (jre8同步适配:FileWriter无Charset构造,改OutputStreamWriter)
		try (java.io.Writer writer = new java.io.OutputStreamWriter(
				new java.io.FileOutputStream(new File(dir, fileName)), StandardCharsets.UTF_8)) {
			writer.write(content);
		}
	}

	@SuppressWarnings({ "rawtypes", "unchecked" })
	private static void touchCaches(Class<?> voClass, Class<?> enumClass) throws Exception {
		// noArgConstructors
		Object bean = BeanUtil.newBean(voClass);
		assertNotNull(bean);
		// setMethods/getMethods + matchSetMethodsCache/matchGetMethodsCache
		BeanUtil.setProperty(bean, "id", 1, Types.INTEGER);
		assertEquals(Integer.valueOf(1), BeanUtil.getProperty(bean, "id"));
		// mappingPlanCache(内含matchSetMethodsCache)
		List<Object[]> rows = new ArrayList<Object[]>();
		rows.add(new Object[] { 2 });
		List mapped = BeanUtil.reflectListToBean(null, rows, new int[] { 0 }, new String[] { "id" },
				new String[] { "INTEGER" }, voClass, true);
		assertEquals(1, mapped.size());
		// EnumUtil.withoutCustomFieldEnum
		assertTrue(EnumUtil.isEnumWithoutCustomField(enumClass));
		assertFalse(EnumUtil.isEnumWithoutCustomField(String.class));
	}
}
