package org.sagacity.sqltoy.config;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.net.JarURLConnection;
import java.net.URL;
import java.net.URLClassLoader;
import java.net.URLConnection;
import java.net.URLStreamHandler;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Collections;
import java.util.Enumeration;
import java.util.List;
import java.util.jar.JarEntry;
import java.util.jar.JarFile;
import java.util.jar.JarOutputStream;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

/**
 * 实体扫描的三种物理形态:classes目录、依赖jar、Spring Boot嵌套jar(fat jar)。
 * 三者必须共用同一套Ant匹配语义(**可匹配0层目录),且嵌套jar必须经JarURLConnection定位,
 * 不能按第一个"!"截断路径(否则会把BOOT-INF/classes或内层jar错当成外层app.jar)
 */
public class EntityScannerFormatsTest {

	private static final String ENTITY_PACKAGE = "org.sagacity.sqltoy.demo.domain";
	private static final String ENTITY_PACKAGE_PATH = "org/sagacity/sqltoy/demo/domain";
	private static final String ENTITY_CLASS_NAME = "StaffInfo";

	@Test
	public void scansClassesDirectory(@TempDir Path tempDir) throws Exception {
		Path packageDir = Files.createDirectories(tempDir.resolve(ENTITY_PACKAGE_PATH));
		Files.write(packageDir.resolve(ENTITY_CLASS_NAME + ".class"), entityClassBytes());
		assertEntityScanned(packageDir.toUri().toURL());
	}

	@Test
	public void scansDependencyJar(@TempDir Path tempDir) throws Exception {
		Path jarPath = tempDir.resolve("entity-dep.jar");
		writeJar(jarPath, ENTITY_PACKAGE_PATH + "/" + ENTITY_CLASS_NAME + ".class", entityClassBytes());
		URL packageUrl = new URL("jar:" + jarPath.toUri().toURL().toExternalForm() + "!/" + ENTITY_PACKAGE_PATH);
		assertEntityScanned(packageUrl);
	}

	@Test
	public void scansDependencyJarWithPlusInPath(@TempDir Path tempDir) throws Exception {
		Path plusDir = Files.createDirectories(tempDir.resolve("a+b"));
		Path jarPath = plusDir.resolve("entity-dep.jar");
		writeJar(jarPath, ENTITY_PACKAGE_PATH + "/" + ENTITY_CLASS_NAME + ".class", entityClassBytes());
		URL packageUrl = new URL("jar:" + jarPath.toUri().toURL().toExternalForm() + "!/" + ENTITY_PACKAGE_PATH);
		assertEntityScanned(packageUrl);
	}

	@Test
	public void scansSpringBootNestedJarEntryRoot(@TempDir Path tempDir) throws Exception {
		Path innerJar = tempDir.resolve("entities.jar");
		writeJar(innerJar, ENTITY_PACKAGE_PATH + "/" + ENTITY_CLASS_NAME + ".class", entityClassBytes());
		NestedJarStreamHandler handler = new NestedJarStreamHandler(innerJar.toFile());
		// 伪造Spring Boot嵌套jar形态:jar:file:/app.jar!/BOOT-INF/lib/entities.jar!/com/...
		// 外层app.jar并不存在,只有真正经JarURLConnection.getJarFile()才能拿到内层jar;
		// 旧实现按第一个"!"截断会去打开不存在的app.jar,结果为空
		URL packageUrl = new URL(null,
				"jar:file:/no/such/path/app.jar!/BOOT-INF/lib/entities.jar!/" + ENTITY_PACKAGE_PATH, handler);
		try {
			assertEntityScanned(packageUrl);
		} finally {
			handler.close();
		}
	}

	@Test
	public void intermediateGlobstarMatchesZeroDirectories(@TempDir Path tempDir) throws Exception {
		String pattern = "org.sagacity.sqltoy.demo.**.domain";
		String basePath = "org/sagacity/sqltoy/demo";
		Path classesRoot = Files.createDirectories(tempDir.resolve("classes"));
		Path packageDir = Files.createDirectories(classesRoot.resolve(ENTITY_PACKAGE_PATH));
		Files.write(packageDir.resolve(ENTITY_CLASS_NAME + ".class"), entityClassBytes());
		URL classesUrl = classesRoot.resolve(basePath).toUri().toURL();
		assertPatternScanned(classesUrl, pattern);

		Path jarPath = tempDir.resolve("intermediate-globstar.jar");
		writeJar(jarPath, ENTITY_PACKAGE_PATH + "/" + ENTITY_CLASS_NAME + ".class", entityClassBytes());
		URL jarUrl = new URL("jar:" + jarPath.toUri().toURL().toExternalForm() + "!/" + basePath);
		assertPatternScanned(jarUrl, pattern);
	}

	private static void assertPatternScanned(URL packageUrl, String pattern) throws Exception {
		ClassLoader originClassLoader = Thread.currentThread().getContextClassLoader();
		try {
			Thread.currentThread().setContextClassLoader(resourceOnlyLoader(packageUrl));
			List<Class<?>> scanned = EntityScanner.scanEntityClasses(pattern, true, "UTF-8");
			assertEquals(1, scanned.size(), "中间**必须匹配0层目录:" + scanned);
			assertEquals(ENTITY_CLASS_NAME, scanned.get(0).getSimpleName());
		} finally {
			Thread.currentThread().setContextClassLoader(originClassLoader);
		}
	}

	/**
	 * 目录、jar、嵌套jar三种形态下:递归扫描与"包.**"扫描都应命中基包直属实体
	 */
	private static void assertEntityScanned(URL packageUrl) throws Exception {
		ClassLoader originClassLoader = Thread.currentThread().getContextClassLoader();
		try {
			Thread.currentThread().setContextClassLoader(resourceOnlyLoader(packageUrl));
			List<Class<?>> recursive = EntityScanner.scanEntityClasses(ENTITY_PACKAGE, true, "UTF-8");
			assertEquals(1, recursive.size(), "应扫描到实体:" + recursive);
			assertEquals(ENTITY_CLASS_NAME, recursive.get(0).getSimpleName());

			List<Class<?>> starStar = EntityScanner.scanEntityClasses(ENTITY_PACKAGE + ".**", true, "UTF-8");
			assertEquals(1, starStar.size(), "** 必须匹配0层目录(基包直属类):" + starStar);
			assertTrue(starStar.get(0).getSimpleName().equals(ENTITY_CLASS_NAME));
		} finally {
			Thread.currentThread().setContextClassLoader(originClassLoader);
		}
	}

	/**
	 * 只返回指定URL的资源,类加载仍委派给父加载器(保证实体注解可解析)
	 */
	private static ClassLoader resourceOnlyLoader(URL packageUrl) {
		return new URLClassLoader(new URL[0], EntityScannerFormatsTest.class.getClassLoader()) {
			@Override
			public Enumeration<URL> getResources(String name) {
				return Collections.enumeration(Collections.singletonList(packageUrl));
			}
		};
	}

	private static byte[] entityClassBytes() throws IOException {
		try (InputStream inputStream = EntityScannerFormatsTest.class.getClassLoader()
				.getResourceAsStream(ENTITY_PACKAGE_PATH + "/" + ENTITY_CLASS_NAME + ".class")) {
			if (inputStream == null) {
				throw new IllegalStateException("test class StaffInfo not found in test classpath");
			}
			return readAll(inputStream);
		}
	}

	// (jre8同步适配:InputStream.readAllBytes为JDK9+,等价循环读取)
	private static byte[] readAll(InputStream in) throws IOException {
		java.io.ByteArrayOutputStream buffer = new java.io.ByteArrayOutputStream();
		byte[] chunk = new byte[8192];
		int read;
		while ((read = in.read(chunk)) != -1) {
			buffer.write(chunk, 0, read);
		}
		return buffer.toByteArray();
	}

	private static void writeJar(Path jarPath, String entryName, byte[] content) throws Exception {
		try (OutputStream outputStream = Files.newOutputStream(jarPath);
				JarOutputStream jarOutputStream = new JarOutputStream(outputStream)) {
			jarOutputStream.putNextEntry(new JarEntry(entryName));
			jarOutputStream.write(content);
			jarOutputStream.closeEntry();
		}
	}

	/**
	 * 模拟Spring Boot嵌套jar的URLStreamHandler:外层路径可以不存在,
	 * getJarFile()直接返回内层jar(entry已相对内层jar根)
	 */
	private static final class NestedJarStreamHandler extends URLStreamHandler {
		private final java.io.File innerJar;
		private JarFile jarFile;

		private NestedJarStreamHandler(java.io.File innerJar) {
			this.innerJar = innerJar;
		}

		@Override
		protected URLConnection openConnection(URL url) throws IOException {
			return new JarURLConnection(url) {
				@Override
				public void connect() throws IOException {
					if (jarFile == null) {
						jarFile = new JarFile(innerJar);
					}
				}

				@Override
				public JarFile getJarFile() throws IOException {
					connect();
					return jarFile;
				}

				@Override
				public JarEntry getJarEntry() {
					return null;
				}

				@Override
				public String getEntryName() {
					return ENTITY_PACKAGE_PATH;
				}
			};
		}

		private void close() throws IOException {
			if (jarFile != null) {
				jarFile.close();
			}
		}
	}
}
