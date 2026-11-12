package org.sagacity.sqltoy.config;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.OutputStream;
import java.net.URL;
import java.net.URLClassLoader;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Arrays;
import java.util.List;
import java.util.concurrent.ConcurrentHashMap;
import java.util.jar.JarEntry;
import java.util.jar.JarOutputStream;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.sagacity.sqltoy.config.model.SqlToyConfig;

/**
 * 同名.sql.xml同时存在于classes与依赖jar时,扫描结果必须保留各自的物理来源,
 * 解析按物理URL开流,不能经TCCL取首个同名资源(否则jar独有的sqlId会静默缺失)
 */
public class SqlResourceScannerJarUrlTest {

	private static final String RESOURCE_PATH = "demo/dup.sql.xml";

	@Test
	public void jarAndClassesWithSamePathKeepBothPhysicalSources(@TempDir Path tempDir) throws Exception {
		Path classesDir = Files.createDirectories(tempDir.resolve("classes"));
		Path classesXml = classesDir.resolve(RESOURCE_PATH);
		Files.createDirectories(classesXml.getParent());
		Files.write(classesXml, sqlXml("from_classes").getBytes(StandardCharsets.UTF_8));
		Path jarPath = tempDir.resolve("demo-dep.jar");
		writeJar(jarPath, RESOURCE_PATH, sqlXml("from_jar"));

		ClassLoader originClassLoader = Thread.currentThread().getContextClassLoader();
		try (URLClassLoader classLoader = new URLClassLoader(
				new URL[] { classesDir.toUri().toURL(), jarPath.toUri().toURL() }, null)) {
			Thread.currentThread().setContextClassLoader(classLoader);

			List resources = SqlResourceScanner.getSqlResources(null, Arrays.asList("classpath:" + RESOURCE_PATH));
			assertEquals(2, resources.size(), "classes与jar同名资源应各保留一个物理来源:" + resources);
			boolean hasFile = false;
			boolean hasUrl = false;
			for (Object resource : resources) {
				hasFile = hasFile || resource instanceof java.io.File;
				hasUrl = hasUrl || resource instanceof URL;
			}
			assertTrue(hasFile, "classes目录资源应保留为File:" + resources);
			assertTrue(hasUrl, "jar内资源应保留物理URL:" + resources);

			ConcurrentHashMap<String, SqlToyConfig> cache = new ConcurrentHashMap<String, SqlToyConfig>();
			ConcurrentHashMap<String, Long> lastModified = new ConcurrentHashMap<String, Long>();
			for (Object resource : resources) {
				SqlXMLConfigParse.parseSingleFile(resource, lastModified, cache, "UTF-8", null, false, -1);
			}
			assertTrue(cache.containsKey("from_classes"), "classes中的sqlId应被加载");
			assertTrue(cache.containsKey("from_jar"), "jar独有的sqlId不能因TCCL读到classes同名文件而丢失");
		} finally {
			Thread.currentThread().setContextClassLoader(originClassLoader);
		}
	}

	/**
	 * jar entry名含URL特殊字符(#/?/空格/中文)时,物理URL必须按路径规则转义后再开流,
	 * 否则#会被当作fragment截断、读到错误entry
	 */
	@Test
	public void jarEntriesWithUrlSpecialCharactersAreReadable(@TempDir Path tempDir) throws Exception {
		Path jarPath = tempDir.resolve("special-names.jar");
		String[][] entries = { { "demo/ok.sql.xml", "id_ok" }, { "demo/a b.sql.xml", "id_space" },
				{ "demo/c#d.sql.xml", "id_hash" }, { "demo/e?f.sql.xml", "id_question" },
				{ "demo/中文.sql.xml", "id_cjk" } };
		try (OutputStream outputStream = Files.newOutputStream(jarPath);
				JarOutputStream jarOutputStream = new JarOutputStream(outputStream)) {
			for (String[] entry : entries) {
				jarOutputStream.putNextEntry(new JarEntry(entry[0]));
				jarOutputStream.write(sqlXml(entry[1]).getBytes(StandardCharsets.UTF_8));
				jarOutputStream.closeEntry();
			}
		}
		ClassLoader originClassLoader = Thread.currentThread().getContextClassLoader();
		try (URLClassLoader classLoader = new URLClassLoader(new URL[] { jarPath.toUri().toURL() }, null)) {
			Thread.currentThread().setContextClassLoader(classLoader);
			List resources = SqlResourceScanner.getSqlResources("classpath*:**/*.sql.xml", null);
			assertEquals(entries.length, resources.size(), "特殊字符entry应全部被扫描:" + resources);
			ConcurrentHashMap<String, SqlToyConfig> cache = new ConcurrentHashMap<String, SqlToyConfig>();
			ConcurrentHashMap<String, Long> lastModified = new ConcurrentHashMap<String, Long>();
			for (Object resource : resources) {
				SqlXMLConfigParse.parseSingleFile(resource, lastModified, cache, "UTF-8", null, false, -1);
			}
			for (String[] entry : entries) {
				assertTrue(cache.containsKey(entry[1]),
						"entry内sqlId应能解析:" + entry[0] + ",实际:" + cache.keySet());
			}
		} finally {
			Thread.currentThread().setContextClassLoader(originClassLoader);
		}
	}

	private static String sqlXml(String id) {
		return "<?xml version=\"1.0\" encoding=\"utf-8\"?>\n<sqltoy>\n<sql id=\"" + id
				+ "\"><value><![CDATA[select 1 from dual]]></value></sql>\n</sqltoy>";
	}

	private static void writeJar(Path jarPath, String entryName, String content) throws Exception {
		try (OutputStream outputStream = Files.newOutputStream(jarPath);
				JarOutputStream jarOutputStream = new JarOutputStream(outputStream)) {
			jarOutputStream.putNextEntry(new JarEntry(entryName));
			jarOutputStream.write(content.getBytes(StandardCharsets.UTF_8));
			jarOutputStream.closeEntry();
		}
	}

	/**
	 * update 2026-11-11 嵌套jar判定:Spring Boot 3.0/3.1(多个"!/")与3.2+(nested:协议)为嵌套jar,
	 * 其JarFile由加载器缓存共享不能主动关闭;普通jar判定为非嵌套(禁用共享缓存后可安全close)
	 */
	@Test
	public void nestedJarUrlDetection() throws Exception {
		java.lang.reflect.Method m = SqlResourceScanner.class.getDeclaredMethod("isNestedJarUrl", URL.class);
		m.setAccessible(true);
		assertFalse((Boolean) m.invoke(null, new URL("jar:file:/app/app.jar!/")));
		assertTrue((Boolean) m.invoke(null, new URL("jar:file:/app/app.jar!/BOOT-INF/lib/dep.jar!/")));
		assertTrue((Boolean) m.invoke(null, new URL("jar:file:/app/app.jar!/BOOT-INF/classes!/")));
		// jar:nested:... 需Spring Boot注册nested协议处理器才能构造URL,JDK原生无法实例化,该分支由运行时覆盖
	}
}