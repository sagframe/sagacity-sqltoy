package org.sagacity.sqltoy.config;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.net.URL;
import java.net.URLClassLoader;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.ConcurrentHashMap;
import java.util.jar.JarEntry;
import java.util.jar.JarOutputStream;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.sagacity.sqltoy.config.model.SqlToyConfig;

/**
 * update 2026-11-11 空根classpath*发现的对抗拓扑与边界深测(项目必经路径):
 * 在既有RootAndDuplicate/SystemClassPath测试之上补两类形态:
 * <ol>
 * <li>对抗拓扑:URLClassLoader(自有jar)父级为系统应用类加载器——层级同时满足
 * hasUrlClassLoader与reachesSystemClassLoader,按现行语义应补充java.class.path且
 * 与自有jar不产生重复扫描(rootKeys去重)</li>
 * <li>java.class.path边界:不存在路径静默跳过/中文与空格路径/zip根/重复项/尾分隔符/
 * 通配符指向空目录/整串为空——垃圾输入不炸、有效输入全收</li>
 * </ol>
 */
public class SqlResourceScannerAdversarialTest {

	// ==================== 对抗拓扑 ====================

	/**
	 * URLClassLoader(自有jar)+系统父级:自有jar与java.class.path中的资源都应可见,
	 * 且二者重叠时只扫一次(rootKeys按URL去重)——这是"委派即共享可见性"语义的锁定
	 */
	@Test
	public void urlChildWithOwnJarAndSystemParentScansBothWithoutDuplicates(@TempDir Path tempDir) throws Exception {
		Path ownDir = Files.createDirectories(tempDir.resolve("own"));
		writeXml(ownDir.resolve("demo/own.sql.xml"), "adv_own");
		Path ownJar = tempDir.resolve("own.jar");
		writeJar(ownJar, "demo/ownjar.sql.xml", "adv_own_jar");
		// java.class.path里故意再放一遍own.jar(与加载器URL重叠)——去重后只应扫出一份
		Path sysDir = Files.createDirectories(tempDir.resolve("sys"));
		writeXml(sysDir.resolve("demo/sys.sql.xml"), "adv_sys");
		String classPath = ownJar + File.pathSeparator + sysDir + File.pathSeparator + ownJar;
		ClassLoader origin = Thread.currentThread().getContextClassLoader();
		String originClassPath = System.getProperty("java.class.path");
		try (URLClassLoader loader = new URLClassLoader(
				new URL[] { ownDir.toUri().toURL(), ownJar.toUri().toURL() }, ClassLoader.getSystemClassLoader())) {
			Thread.currentThread().setContextClassLoader(loader);
			System.setProperty("java.class.path", classPath);
			List resources = SqlResourceScanner.getSqlResources("classpath*:**/*.sql.xml", null);
			ConcurrentHashMap<String, SqlToyConfig> cache = parseAll(resources);
			// 三个物理来源全部可见
			assertNotNull(cache.get("adv_own"), "加载器自有目录应可见: " + cache.keySet());
			assertNotNull(cache.get("adv_own_jar"), "加载器自有jar应可见: " + cache.keySet());
			assertNotNull(cache.get("adv_sys"), "系统classpath侧应可见(委派语义): " + cache.keySet());
			// own.jar在加载器URL与java.class.path中重叠,物理资源不得重复出现
			long ownJarCount = resources.stream()
					.filter(r -> r instanceof URL && ((URL) r).toExternalForm().contains("own.jar")).count();
			assertEquals(1, ownJarCount, "重叠的jar根只能产生一份物理资源: " + resources);
		} finally {
			Thread.currentThread().setContextClassLoader(origin);
			System.setProperty("java.class.path", originClassPath);
		}
	}

	// ==================== java.class.path 边界 ====================

	@Test
	public void garbageClassPathEntriesAreSkippedSilently(@TempDir Path tempDir) throws Exception {
		Path sysDir = Files.createDirectories(tempDir.resolve("sys"));
		writeXml(sysDir.resolve("demo/valid.sql.xml"), "adv_valid");
		Path emptyWildcardDir = Files.createDirectories(tempDir.resolve("nojars"));
		// 不存在路径 + 通配符指向无jar目录 + 尾分隔符 + 空白项 + 中文空格路径(有效)
		Path cnDir = Files.createDirectories(tempDir.resolve("中文 目录"));
		writeXml(cnDir.resolve("demo/cn.sql.xml"), "adv_cn");
		String classPath = tempDir.resolve("not-exists").toString() + File.pathSeparator
				+ emptyWildcardDir + File.separator + "*" + File.pathSeparator
				+ sysDir + File.pathSeparator + cnDir + File.pathSeparator + "  " + File.pathSeparator;
		ClassLoader origin = Thread.currentThread().getContextClassLoader();
		String originClassPath = System.getProperty("java.class.path");
		// 非URL委派包装(父级null):加载器层级无URLClassLoader且不委派系统加载器,
		// 可见根=受控fixture目录+java.class.path——系统侧(测试classpath)不混入,才能精确断言垃圾项零产出
		try (URLClassLoader fixtureLoader = new URLClassLoader(new URL[] { sysDir.toUri().toURL() }, null)) {
			ClassLoader plainLoader = new NonUrlClassLoader(fixtureLoader);
			Thread.currentThread().setContextClassLoader(plainLoader);
			System.setProperty("java.class.path", classPath);
			List resources = SqlResourceScanner.getSqlResources("classpath*:**/*.sql.xml", null);
			ConcurrentHashMap<String, SqlToyConfig> cache = parseAll(resources);
			assertNotNull(cache.get("adv_valid"), "有效目录须被发现: " + cache.keySet());
			assertNotNull(cache.get("adv_cn"), "中文空格路径须被发现: " + cache.keySet());
			assertEquals(2, cache.size(), "垃圾项不得产生资源: " + cache.keySet());
		} finally {
			Thread.currentThread().setContextClassLoader(origin);
			System.setProperty("java.class.path", originClassPath);
		}
	}

	/** 非URLClassLoader的委派包装:模拟JDK9+应用加载器形态(父级null,资源委派给被包装加载器) */
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
		public java.util.Enumeration<URL> getResources(String name) throws IOException {
			return delegate.getResources(name);
		}
	}

	@Test
	public void zipRootOnClassPathIsScanned(@TempDir Path tempDir) throws Exception {
		// zip后缀的依赖根:与jar同等处理(转jar: URL根)
		Path zipPath = tempDir.resolve("dep.zip");
		writeJar(zipPath, "demo/zipped.sql.xml", "adv_zip");
		Path sysDir = Files.createDirectories(tempDir.resolve("sys"));
		ClassLoader origin = Thread.currentThread().getContextClassLoader();
		String originClassPath = System.getProperty("java.class.path");
		try (URLClassLoader loader = new URLClassLoader(new URL[] { sysDir.toUri().toURL() },
				ClassLoader.getSystemClassLoader())) {
			Thread.currentThread().setContextClassLoader(loader);
			System.setProperty("java.class.path", zipPath.toString());
			List resources = SqlResourceScanner.getSqlResources("classpath*:**/*.sql.xml", null);
			ConcurrentHashMap<String, SqlToyConfig> cache = parseAll(resources);
			assertNotNull(cache.get("adv_zip"), "zip根的sql.xml应被发现: " + cache.keySet());
			assertTrue(resources.stream().anyMatch(r -> r instanceof URL
					&& ((URL) r).toExternalForm().contains("dep.zip")), "zip根应产生jar: URL资源: " + resources);
		} finally {
			Thread.currentThread().setContextClassLoader(origin);
			System.setProperty("java.class.path", originClassPath);
		}
	}

	@Test
	public void plusSignInJarFileNameIsNotDecodedAsSpace(@TempDir Path tempDir) throws Exception {
		// FileUtil修复回归:URLDecoder把+当表单空格,a+b.jar会被解成"a b.jar"而找不到文件;
		// 修复后先保护+为%2B再解码,带+的jar根可正常扫描(2026-11-11)
		Path plusJar = tempDir.resolve("a+b.jar");
		writeJar(plusJar, "demo/plus.sql.xml", "adv_plus");
		Path sysDir = Files.createDirectories(tempDir.resolve("sys"));
		ClassLoader origin = Thread.currentThread().getContextClassLoader();
		String originClassPath = System.getProperty("java.class.path");
		try (URLClassLoader loader = new URLClassLoader(new URL[] { sysDir.toUri().toURL() },
				ClassLoader.getSystemClassLoader())) {
			Thread.currentThread().setContextClassLoader(loader);
			System.setProperty("java.class.path", plusJar.toString());
			List resources = SqlResourceScanner.getSqlResources("classpath*:**/*.sql.xml", null);
			ConcurrentHashMap<String, SqlToyConfig> cache = parseAll(resources);
			assertNotNull(cache.get("adv_plus"), "带+文件名的jar应可扫描: " + cache.keySet());
		} finally {
			Thread.currentThread().setContextClassLoader(origin);
			System.setProperty("java.class.path", originClassPath);
		}
	}

	@Test
	public void emptyClassPathPropertyDoesNotBreakScan(@TempDir Path tempDir) throws Exception {
		// java.class.path为空串:回退路径直接短路,不产生系统侧根,也不抛异常
		Path sysDir = Files.createDirectories(tempDir.resolve("sys"));
		ClassLoader origin = Thread.currentThread().getContextClassLoader();
		String originClassPath = System.getProperty("java.class.path");
		try (URLClassLoader loader = new URLClassLoader(new URL[] { sysDir.toUri().toURL() }, null)) {
			Thread.currentThread().setContextClassLoader(loader);
			System.setProperty("java.class.path", "");
			List resources = SqlResourceScanner.getSqlResources("classpath*:**/*.sql.xml", null);
			// 空根+空classpath:无资源,静默空结果
			assertEquals(0, resources.size(), "空classpath应得到空结果: " + resources);
			assertFalse(containsSystemSideResource(resources), "不得混入系统侧资源");
		} finally {
			Thread.currentThread().setContextClassLoader(origin);
			System.setProperty("java.class.path", originClassPath);
		}
	}

	private static boolean containsSystemSideResource(List resources) {
		// 系统classpath(测试JVM的maven classpath)中存在大量jar,任何jar:资源即视为混入
		for (Object resource : resources) {
			if (resource instanceof URL && "jar".equals(((URL) resource).getProtocol())) {
				return true;
			}
		}
		return false;
	}

	private static ConcurrentHashMap<String, SqlToyConfig> parseAll(List resources) throws Exception {
		ConcurrentHashMap<String, SqlToyConfig> cache = new ConcurrentHashMap<String, SqlToyConfig>();
		ConcurrentHashMap<String, Long> lastModified = new ConcurrentHashMap<String, Long>();
		for (Object resource : resources) {
			SqlXMLConfigParse.parseSingleFile(resource, lastModified, cache, "UTF-8", null, false, -1);
		}
		return cache;
	}

	private static void writeXml(Path path, String id) throws IOException {
		Files.createDirectories(path.getParent());
		Files.write(path, (("<?xml version=\"1.0\" encoding=\"utf-8\"?>\n<sqltoy>\n<sql id=\"" + id
				+ "\"><value><![CDATA[select 1]]></value></sql>\n</sqltoy>\n")).getBytes(StandardCharsets.UTF_8));
	}

	private static void writeJar(Path jarPath, String entryName, String id) throws IOException {
		try (JarOutputStream jarOut = new JarOutputStream(new FileOutputStream(jarPath.toFile()))) {
			int slash = entryName.indexOf('/');
			while (slash >= 0) {
				jarOut.putNextEntry(new JarEntry(entryName.substring(0, slash + 1)));
				jarOut.closeEntry();
				slash = entryName.indexOf('/', slash + 1);
			}
			jarOut.putNextEntry(new JarEntry(entryName));
			jarOut.write(("<?xml version=\"1.0\" encoding=\"utf-8\"?>\n<sqltoy>\n<sql id=\"" + id
					+ "\"><value><![CDATA[select 2]]></value></sql>\n</sqltoy>\n").getBytes(StandardCharsets.UTF_8));
			jarOut.closeEntry();
		}
	}
}
