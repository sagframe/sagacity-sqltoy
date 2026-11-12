package org.sagacity.sqltoy.config;

import static org.junit.jupiter.api.Assertions.assertEquals;
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
 * SQL XML三条扫描入口的物理来源保持与既有classes覆盖jar语义
 */
public class SqlResourceScannerPathsTest {

	@Test
	public void traditionalClasspathStarKeepsClassesAndJarSources(@TempDir Path tempDir) throws Exception {
		Path classesDir = Files.createDirectories(tempDir.resolve("classes"));
		writeXml(classesDir.resolve("demo/classes.sql.xml"), "traditional_classes", "select 1");
		Path jarPath = tempDir.resolve("traditional.jar");
		writeJar(jarPath, "demo/jar.sql.xml", sqlXml("traditional_jar", "select 2"));
		assertBothLoaded(classesDir, jarPath, "classpath*:demo", "traditional_classes", "traditional_jar");
	}

	@Test
	public void antClasspathStarKeepsClassesAndJarSources(@TempDir Path tempDir) throws Exception {
		Path classesDir = Files.createDirectories(tempDir.resolve("classes"));
		writeXml(classesDir.resolve("demo/sub/classes.sql.xml"), "ant_classes", "select 1");
		Path jarPath = tempDir.resolve("ant.jar");
		writeJar(jarPath, "demo/sub/jar.sql.xml", sqlXml("ant_jar", "select 2"));
		assertBothLoaded(classesDir, jarPath, "classpath*:demo/**/*.sql.xml", "ant_classes", "ant_jar");
	}

	@Test
	public void samePathClassesOverrideJarAndDuplicateIsReported(@TempDir Path tempDir) throws Exception {
		String resourcePath = "demo/dup.sql.xml";
		Path classesDir = Files.createDirectories(tempDir.resolve("classes"));
		writeXml(classesDir.resolve(resourcePath), "shared_id", "select 1");
		Path jarPath = tempDir.resolve("override.jar");
		writeJar(jarPath, resourcePath, sqlXml(new String[][] { { "shared_id", "select 2" }, { "jar_only", "select 3" } }));
		ClassLoader origin = Thread.currentThread().getContextClassLoader();
		try (URLClassLoader loader = new URLClassLoader(
				new URL[] { classesDir.toUri().toURL(), jarPath.toUri().toURL() }, null)) {
			Thread.currentThread().setContextClassLoader(loader);
			List resources = SqlResourceScanner.getSqlResources(null, Arrays.asList("classpath:" + resourcePath));
			assertEquals(2, resources.size(), "同名路径应保留classes和jar两个物理来源:" + resources);
			ConcurrentHashMap<String, SqlToyConfig> cache = new ConcurrentHashMap<String, SqlToyConfig>();
			ConcurrentHashMap<String, Long> lastModified = new ConcurrentHashMap<String, Long>();
			int repeatCount = 0;
			for (Object resource : resources) {
				repeatCount += SqlXMLConfigParse.parseSingleFile(resource, lastModified, cache, "UTF-8", null, false, -1)
						.size();
			}
			assertTrue(cache.containsKey("jar_only"), "jar独有sqlId不能丢失");
			assertTrue(cache.get("shared_id").getSql().contains("select 1"), "classes应覆盖jar中的同名sqlId");
			assertEquals(1, repeatCount, "同名sqlId覆盖应产生一次重复提示");
		} finally {
			Thread.currentThread().setContextClassLoader(origin);
		}
	}

	private static void assertBothLoaded(Path classesDir, Path jarPath, String resourceDir, String classesId,
			String jarId) throws Exception {
		ClassLoader origin = Thread.currentThread().getContextClassLoader();
		try (URLClassLoader loader = new URLClassLoader(
				new URL[] { classesDir.toUri().toURL(), jarPath.toUri().toURL() }, null)) {
			Thread.currentThread().setContextClassLoader(loader);
			List resources = SqlResourceScanner.getSqlResources(resourceDir, null);
			assertEquals(2, resources.size(), "classes与jar资源都应被扫描:" + resources);
			ConcurrentHashMap<String, SqlToyConfig> cache = new ConcurrentHashMap<String, SqlToyConfig>();
			ConcurrentHashMap<String, Long> lastModified = new ConcurrentHashMap<String, Long>();
			for (Object resource : resources) {
				SqlXMLConfigParse.parseSingleFile(resource, lastModified, cache, "UTF-8", null, false, -1);
			}
			assertTrue(cache.containsKey(classesId), "classes中的sqlId应被加载:" + cache.keySet());
			assertTrue(cache.containsKey(jarId), "jar中的sqlId应被加载:" + cache.keySet());
		} finally {
			Thread.currentThread().setContextClassLoader(origin);
		}
	}

	private static void writeXml(Path path, String id, String sql) throws Exception {
		Files.createDirectories(path.getParent());
		Files.write(path, sqlXml(id, sql).getBytes(StandardCharsets.UTF_8));
	}

	private static String sqlXml(String id, String sql) {
		return sqlXml(new String[][] { { id, sql } });
	}

	private static String sqlXml(String[][] sqls) {
		StringBuilder xml = new StringBuilder("<?xml version=\"1.0\" encoding=\"utf-8\"?>\n<sqltoy>\n");
		for (String[] sql : sqls) {
			xml.append("<sql id=\"").append(sql[0]).append("\"><value><![CDATA[").append(sql[1])
					.append("]]></value></sql>\n");
		}
		return xml.append("</sqltoy>\n").toString();
	}

	private static void writeJar(Path jarPath, String entryName, String content) throws Exception {
		try (OutputStream out = Files.newOutputStream(jarPath); JarOutputStream jarOut = new JarOutputStream(out)) {
			String[] parts = entryName.split("/");
			StringBuilder dir = new StringBuilder();
			for (int i = 0; i < parts.length - 1; i++) {
				dir.append(parts[i]).append('/');
				jarOut.putNextEntry(new JarEntry(dir.toString()));
				jarOut.closeEntry();
			}
			jarOut.putNextEntry(new JarEntry(entryName));
			jarOut.write(content.getBytes(StandardCharsets.UTF_8));
			jarOut.closeEntry();
		}
	}
}
