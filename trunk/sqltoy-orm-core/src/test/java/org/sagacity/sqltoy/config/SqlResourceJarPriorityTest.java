package org.sagacity.sqltoy.config;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.File;
import java.io.FileOutputStream;
import java.net.URL;
import java.net.URLClassLoader;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.util.List;
import java.util.concurrent.ConcurrentHashMap;

import org.junit.jupiter.api.Test;

/**
 * update 2026-11-11 jar与classes同名sql资源的加载优先级回归(对应SqlResourceScanner保留物理jar URL
 * +SqlXMLConfigParse按URL开流的修复):
 * <p>
 * 修复背景:扫描结果原先只存逻辑路径,解析时经TCCL.getResourceAsStream取资源——classes与依赖jar存在
 * 同名sql.xml时TCCL只会读到classes下的第一份,jar独有的sqlId被静默丢弃。修复后jar命中保留
 * jar:file:/x.jar!/entry物理URL,解析按该URL开流,两份资源的sqlId全部可见;加载顺序保持
 * "jar在前、classes在后"(classes可覆盖jar,支持增量发版)。
 * <p>
 * 用临时classes目录+fixture jar构造双同名资源,断言三件事:①jar独有sqlId可见(修复点);
 * ②classes独有sqlId可见;③同名sqlId以classes内容生效(既有覆盖语义不变)。
 */
public class SqlResourceJarPriorityTest {

	private static final String RESOURCE_NAME = "sqltoy-jarprio-probe.sql.xml";

	private static String xmlOf(String sharedContent, String uniqueId, String uniqueContent) {
		return "<?xml version=\"1.0\" encoding=\"utf-8\"?>\r\n"
				+ "<sqltoy xmlns=\"http://www.sagframe.com/schema/sqltoy\"\r\n"
				+ "    xmlns:xsi=\"http://www.w3.org/2001/XMLSchema-instance\"\r\n"
				+ "    xsi:schemaLocation=\"http://www.sagframe.com/schema/sqltoy "
				+ "http://www.sagframe.com/schema/sqltoy/sqltoy.xsd\">\r\n"
				+ "    <sql id=\"jarprio_shared\"><![CDATA[" + sharedContent + "]]></sql>\r\n"
				+ "    <sql id=\"" + uniqueId + "\"><![CDATA[" + uniqueContent + "]]></sql>\r\n"
				+ "</sqltoy>";
	}

	@Test
	public void jarUniqueSqlIdVisibleWhenClassesHasSameNamedFile() throws Exception {
		File classesDir = Files.createTempDirectory("jarprio-classes").toFile();
		File jarFile = new File(Files.createTempDirectory("jarprio-jar").toFile(), "fixture-sql.jar");
		try {
			// classes侧:shared=classes内容 + classes独有sqlId
			Files.write(new File(classesDir, RESOURCE_NAME).toPath(),
					xmlOf("select 1 as from_classes", "jarprio_classes_only", "select 11 as classes_only")
							.getBytes(StandardCharsets.UTF_8));
			// jar侧:shared=jar内容 + jar独有sqlId
			try (java.util.jar.JarOutputStream jarOut = new java.util.jar.JarOutputStream(
					new FileOutputStream(jarFile))) {
				jarOut.putNextEntry(new java.util.jar.JarEntry(RESOURCE_NAME));
				jarOut.write(xmlOf("select 2 as from_jar", "jarprio_jar_only", "select 22 as jar_only")
						.getBytes(StandardCharsets.UTF_8));
				jarOut.closeEntry();
			}
			// classes在前(模拟TCCL先命中classes的常态),jar在后
			ClassLoader fixtureLoader = new URLClassLoader(
					new URL[] { classesDir.toURI().toURL(), jarFile.toURI().toURL() },
					Thread.currentThread().getContextClassLoader());
			ClassLoader original = Thread.currentThread().getContextClassLoader();
			Thread.currentThread().setContextClassLoader(fixtureLoader);
			List resources;
			ConcurrentHashMap<String, org.sagacity.sqltoy.config.model.SqlToyConfig> cache = new ConcurrentHashMap<>();
			try {
				resources = SqlResourceScanner.getSqlResources("classpath*:" + RESOURCE_NAME, null);
			} finally {
				Thread.currentThread().setContextClassLoader(original);
			}
			// 双份资源都命中(classes的File + jar的物理URL)
			assertEquals(2, resources.size(), "classes与jar的同名资源都应被扫描命中: " + resources);
			boolean hasFile = false;
			boolean hasJarUrl = false;
			for (Object res : resources) {
				if (res instanceof File) {
					hasFile = true;
				} else if (res instanceof URL && ((URL) res).toExternalForm().startsWith("jar:file:")) {
					hasJarUrl = true;
				}
			}
			assertTrue(hasFile, "classes侧资源应为File形态: " + resources);
			assertTrue(hasJarUrl, "jar侧资源应保留jar:file:物理URL(修复点): " + resources);
			// 按扫描顺序解析(jar在前classes在后),同名sqlId后者覆盖前者
			ConcurrentHashMap<String, Long> lastModify = new ConcurrentHashMap<>();
			for (Object res : resources) {
				SqlXMLConfigParse.parseSingleFile(res, lastModify, cache, "utf-8", null, true, -1);
			}
			// ①jar独有sqlId可见(修复前经TCCL只读classes,此sqlId静默缺失)
			assertNotNull(cache.get("jarprio_jar_only"), "jar独有的sqlId必须可见(本轮修复核心)");
			assertTrue(cache.get("jarprio_jar_only").getSql(null).contains("jar_only"));
			// ②classes独有sqlId可见
			assertNotNull(cache.get("jarprio_classes_only"), "classes独有sqlId可见");
			// ③同名sqlId以classes内容生效(既有覆盖语义:jar先载、classes后载覆盖)
			assertNotNull(cache.get("jarprio_shared"), "同名sqlId存在");
			assertTrue(cache.get("jarprio_shared").getSql(null).contains("from_classes"),
					"同名sqlId应由classes内容覆盖jar: " + cache.get("jarprio_shared").getSql(null));
		} finally {
			deleteTree(classesDir);
			jarFile.delete();
		}
	}

	private static void deleteTree(File dir) {
		if (dir == null || !dir.exists()) {
			return;
		}
		File[] children = dir.listFiles();
		if (children != null) {
			for (File child : children) {
				deleteTree(child);
			}
		}
		dir.delete();
	}
}
