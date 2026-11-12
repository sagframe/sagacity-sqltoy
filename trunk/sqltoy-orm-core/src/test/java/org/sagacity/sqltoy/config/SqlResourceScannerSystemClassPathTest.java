package org.sagacity.sqltoy.config;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.File;
import java.io.IOException;
import java.io.OutputStream;
import java.net.URL;
import java.net.URLClassLoader;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Enumeration;
import java.util.List;
import java.util.concurrent.ConcurrentHashMap;
import java.util.jar.JarEntry;
import java.util.jar.JarOutputStream;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.sagacity.sqltoy.config.model.SqlToyConfig;

/**
 * 空根classpath*:在“非URLClassLoader”的普通JDK部署形态下也必须发现依赖jar。
 * <p>
 * JDK9+的应用类加载器是jdk.internal.loader.ClassLoaders$AppClassLoader,不是URLClassLoader,
 * 且getResources("")只返回目录;此时只能通过java.class.path补充jar根。
 * 用例用委托型ClassLoader模拟该形态,避免surefire自身的URLClassLoader掩盖问题。
 * </p>
 */
public class SqlResourceScannerSystemClassPathTest {

	/**
	 * 只做委托、本身不是URLClassLoader的加载器,模拟JDK9+ AppClassLoader
	 */
	private static final class NonUrlClassLoader extends ClassLoader {
		private final ClassLoader delegate;

		private NonUrlClassLoader(ClassLoader delegate) {
			super(null);
			this.delegate = delegate;
		}

		@Override
		protected Class<?> loadClass(String name, boolean resolve) throws ClassNotFoundException {
			return delegate.loadClass(name);
		}

		@Override
		public URL getResource(String name) {
			return delegate.getResource(name);
		}

		@Override
		public Enumeration<URL> getResources(String name) throws IOException {
			return delegate.getResources(name);
		}
	}

	@Test
	public void emptyRootDiscoversJarFromSystemClassPath(@TempDir Path tempDir) throws Exception {
		Path classesDir = Files.createDirectories(tempDir.resolve("classes"));
		writeXml(classesDir.resolve("demo/classes.sql.xml"), "cp_classes");
		Path jarPath = tempDir.resolve("cp-dep.jar");
		writeJar(jarPath, "demo/jar.sql.xml", "cp_jar");
		assertRootScanFindsBoth(classesDir, classesDir.toString() + File.pathSeparator + jarPath.toString());
	}

	@Test
	public void wildcardClassPathEntryExpandsJars(@TempDir Path tempDir) throws Exception {
		Path classesDir = Files.createDirectories(tempDir.resolve("classes"));
		writeXml(classesDir.resolve("demo/classes.sql.xml"), "cp_classes");
		Path libsDir = Files.createDirectories(tempDir.resolve("libs"));
		writeJar(libsDir.resolve("cp-dep.jar"), "demo/jar.sql.xml", "cp_jar");
		assertRootScanFindsBoth(classesDir,
				classesDir.toString() + File.pathSeparator + libsDir.toString() + File.separator + "*");
	}

	private static void assertRootScanFindsBoth(Path classesDir, String classPath) throws Exception {
		ClassLoader origin = Thread.currentThread().getContextClassLoader();
		String originClassPath = System.getProperty("java.class.path");
		try (URLClassLoader urlLoader = new URLClassLoader(new URL[] { classesDir.toUri().toURL() }, null)) {
			ClassLoader plainLoader = new NonUrlClassLoader(urlLoader);
			// 前置条件:必须是非URLClassLoader,否则测不到真实部署形态
			assertFalse(plainLoader instanceof URLClassLoader, "用例必须模拟非URLClassLoader的加载器");
			Thread.currentThread().setContextClassLoader(plainLoader);
			System.setProperty("java.class.path", classPath);

			List resources = SqlResourceScanner.getSqlResources("classpath*:**/*.sql.xml", null);
			assertEquals(2, resources.size(), "空根应同时发现classes与依赖jar:" + resources);
			boolean hasJarUrl = false;
			for (Object resource : resources) {
				if (resource instanceof URL && "jar".equals(((URL) resource).getProtocol())) {
					hasJarUrl = true;
				}
			}
			assertTrue(hasJarUrl, "jar资源应保留为jar: URL:" + resources);
			ConcurrentHashMap<String, SqlToyConfig> cache = parse(resources);
			assertTrue(cache.containsKey("cp_classes"), "classes内sqlId应加载");
			assertTrue(cache.containsKey("cp_jar"), "依赖jar内sqlId不能被漏掉");
		} finally {
			Thread.currentThread().setContextClassLoader(origin);
			if (originClassPath == null) {
				System.clearProperty("java.class.path");
			} else {
				System.setProperty("java.class.path", originClassPath);
			}
		}
	}

	private static ConcurrentHashMap<String, SqlToyConfig> parse(List resources) throws Exception {
		ConcurrentHashMap<String, SqlToyConfig> cache = new ConcurrentHashMap<String, SqlToyConfig>();
		ConcurrentHashMap<String, Long> lastModified = new ConcurrentHashMap<String, Long>();
		for (Object resource : resources) {
			SqlXMLConfigParse.parseSingleFile(resource, lastModified, cache, "UTF-8", null, false, -1);
		}
		return cache;
	}

	private static void writeXml(Path file, String sqlId) throws Exception {
		Files.createDirectories(file.getParent());
		Files.write(file, ("<?xml version=\"1.0\" encoding=\"utf-8\"?>\n<sqltoy>\n<sql id=\"" + sqlId
				+ "\"><value><![CDATA[select 1]]></value></sql>\n</sqltoy>").getBytes(StandardCharsets.UTF_8));
	}

	private static void writeJar(Path jarPath, String entryName, String sqlId) throws Exception {
		try (OutputStream outputStream = Files.newOutputStream(jarPath);
				JarOutputStream jarOutputStream = new JarOutputStream(outputStream)) {
			jarOutputStream.putNextEntry(new JarEntry(entryName));
			jarOutputStream.write(("<?xml version=\"1.0\" encoding=\"utf-8\"?>\n<sqltoy>\n<sql id=\"" + sqlId
					+ "\"><value><![CDATA[select 2]]></value></sql>\n</sqltoy>").getBytes(StandardCharsets.UTF_8));
			jarOutputStream.closeEntry();
		}
	}
}
