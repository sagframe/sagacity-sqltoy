package org.sagacity.sqltoy.config;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.File;
import java.io.OutputStream;
import java.lang.reflect.Method;
import java.net.URL;
import java.net.URLClassLoader;
import java.net.URLConnection;
import java.net.URLStreamHandler;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.concurrent.ConcurrentHashMap;
import java.util.jar.JarEntry;
import java.util.jar.JarOutputStream;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.sagacity.sqltoy.config.model.SqlToyConfig;

/**
 * 空根classpath*:必须发现jar根;多jar同名entry必须全部加载,重复sqlId仍由breakWhenSqlRepeat决定报错或覆盖
 */
public class SqlResourceScannerRootAndDuplicateTest {

	@Test
	public void emptyRootAntLoadsClassesAndJarSql(@TempDir Path tempDir) throws Exception {
		Path classesDir = Files.createDirectories(tempDir.resolve("classes"));
		writeXml(classesDir.resolve("demo/classes.sql.xml"), "empty_classes", "select 1");
		Path jarPath = tempDir.resolve("empty-root.jar");
		writeJar(jarPath, new String[][] { { "demo/jar.sql.xml", "empty_jar", "select 2" },
				{ "root.sql.xml", "empty_root", "select 3" } });
		ClassLoader origin = Thread.currentThread().getContextClassLoader();
		try (URLClassLoader loader = new URLClassLoader(
				new URL[] { classesDir.toUri().toURL(), jarPath.toUri().toURL() }, null)) {
			Thread.currentThread().setContextClassLoader(loader);
			List resources = SqlResourceScanner.getSqlResources("classpath*:**/*.sql.xml", null);
			assertEquals(3, resources.size(), "空根Ant应扫到classes和jar内全部sql资源:" + resources);
			ConcurrentHashMap<String, SqlToyConfig> cache = parse(resources);
			assertTrue(cache.containsKey("empty_classes"));
			assertTrue(cache.containsKey("empty_jar"));
			assertTrue(cache.containsKey("empty_root"));

			List rootOnly = SqlResourceScanner.getSqlResources("classpath*:*.sql.xml", null);
			assertEquals(1, rootOnly.size(), "空根单层匹配应扫到jar根目录sql:" + rootOnly);
			assertTrue(parse(rootOnly).containsKey("empty_root"));
		} finally {
			Thread.currentThread().setContextClassLoader(origin);
		}
	}

	@Test
	public void multipleJarsWithSameEntryLoadDifferentSqlIds(@TempDir Path tempDir) throws Exception {
		Path jar1 = tempDir.resolve("one.jar");
		Path jar2 = tempDir.resolve("two.jar");
		writeJar(jar1, "demo/dup.sql.xml", "jar1_only", "select 1");
		writeJar(jar2, "demo/dup.sql.xml", "jar2_only", "select 2");
		ClassLoader origin = Thread.currentThread().getContextClassLoader();
		try (URLClassLoader loader = new URLClassLoader(new URL[] { jar1.toUri().toURL(), jar2.toUri().toURL() },
				null)) {
			Thread.currentThread().setContextClassLoader(loader);
			List resources = SqlResourceScanner.getSqlResources("classpath*:demo", null);
			assertEquals(2, resources.size(), "不同jar的同名entry都应保留:" + resources);
			ConcurrentHashMap<String, SqlToyConfig> cache = parse(resources);
			assertTrue(cache.containsKey("jar1_only"), "第一个jar独有sqlId不能丢失");
			assertTrue(cache.containsKey("jar2_only"), "第二个jar独有sqlId不能丢失");
		} finally {
			Thread.currentThread().setContextClassLoader(origin);
		}
	}

	@Test
	public void duplicateSqlIdBreaksByDefaultAndOverridesWhenAllowed(@TempDir Path tempDir) throws Exception {
		Path jar1 = tempDir.resolve("one.jar");
		Path jar2 = tempDir.resolve("two.jar");
		writeJar(jar1, "demo/dup.sql.xml", "shared_id", "select 1");
		writeJar(jar2, "demo/dup.sql.xml", "shared_id", "select 2");
		ClassLoader origin = Thread.currentThread().getContextClassLoader();
		try (URLClassLoader loader = new URLClassLoader(new URL[] { jar1.toUri().toURL(), jar2.toUri().toURL() },
				null)) {
			Thread.currentThread().setContextClassLoader(loader);

			SqlScriptLoader breakLoader = new SqlScriptLoader();
			breakLoader.setSqlResourcesDir("classpath*:demo");
			Exception error = assertThrows(Exception.class,
					() -> breakLoader.initialize(false, 0, null, true));
			assertTrue(error.getMessage().contains("重复的sqlId"), "默认重复sqlId应报错:" + error.getMessage());

			SqlScriptLoader allowLoader = new SqlScriptLoader();
			allowLoader.setSqlResourcesDir("classpath*:demo");
			allowLoader.initialize(false, 0, null, false);
			SqlToyConfig config = allowLoader.getSqlToyConfig("shared_id");
			assertTrue(config.getSql().contains("select 1"), "允许覆盖时保持类加载器首个jar优先:" + config.getSql());
		} finally {
			Thread.currentThread().setContextClassLoader(origin);
		}
	}

	@Test
	public void combinedDirAndMappingDoNotDuplicatePhysicalResources(@TempDir Path tempDir) throws Exception {
		Path jar1 = tempDir.resolve("one.jar");
		Path jar2 = tempDir.resolve("two.jar");
		writeJar(jar1, "demo/dup.sql.xml", "jar1_only", "select 1");
		writeJar(jar2, "demo/dup.sql.xml", "jar2_only", "select 2");
		ClassLoader origin = Thread.currentThread().getContextClassLoader();
		try (URLClassLoader loader = new URLClassLoader(new URL[] { jar1.toUri().toURL(), jar2.toUri().toURL() },
				null)) {
			Thread.currentThread().setContextClassLoader(loader);
			List resources = SqlResourceScanner.getSqlResources("classpath*:demo",
					Arrays.asList("classpath*:demo/dup.sql.xml"));
			assertEquals(2, resources.size(), "目录扫描和精确mapping命中同一物理资源时不能重复加载:" + resources);
			ConcurrentHashMap<String, SqlToyConfig> cache = parse(resources);
			assertTrue(cache.containsKey("jar1_only"));
			assertTrue(cache.containsKey("jar2_only"));
		} finally {
			Thread.currentThread().setContextClassLoader(origin);
		}
	}

	@Test
	public void classesOverridesAllJarsAndReportsEachDuplicate(@TempDir Path tempDir) throws Exception {
		Path classesDir = Files.createDirectories(tempDir.resolve("classes"));
		writeXml(classesDir.resolve("demo/dup.sql.xml"), "shared_id", "select 0");
		Path jar1 = tempDir.resolve("one.jar");
		Path jar2 = tempDir.resolve("two.jar");
		writeJar(jar1, "demo/dup.sql.xml", "shared_id", "select 1");
		writeJar(jar2, "demo/dup.sql.xml", "shared_id", "select 2");
		ClassLoader origin = Thread.currentThread().getContextClassLoader();
		try (URLClassLoader loader = new URLClassLoader(
				new URL[] { classesDir.toUri().toURL(), jar1.toUri().toURL(), jar2.toUri().toURL() }, null)) {
			Thread.currentThread().setContextClassLoader(loader);
			List resources = SqlResourceScanner.getSqlResources("classpath*:demo", null);
			assertEquals(3, resources.size(), "classes和两个jar的同名entry都应保留:" + resources);
			ConcurrentHashMap<String, SqlToyConfig> cache = new ConcurrentHashMap<String, SqlToyConfig>();
			ConcurrentHashMap<String, Long> lastModified = new ConcurrentHashMap<String, Long>();
			int repeatCount = 0;
			for (Object resource : resources) {
				repeatCount += SqlXMLConfigParse.parseSingleFile(resource, lastModified, cache, "UTF-8", null, false, -1)
						.size();
			}
			assertEquals(2, repeatCount, "三份同名sqlId应产生两次重复提示");
			assertTrue(cache.get("shared_id").getSql().contains("select 0"), "classes最终覆盖所有jar");
		} finally {
			Thread.currentThread().setContextClassLoader(origin);
		}
	}

	@Test
	public void emptyRootFindsJarWithoutDirectoryEntries(@TempDir Path tempDir) throws Exception {
		Path jarPath = tempDir.resolve("no-dirs.jar");
		try (OutputStream out = Files.newOutputStream(jarPath); JarOutputStream jarOut = new JarOutputStream(out)) {
			jarOut.putNextEntry(new JarEntry("demo/nodirs.sql.xml"));
			jarOut.write(sqlXml("nodirs_id", "select 1").getBytes(StandardCharsets.UTF_8));
			jarOut.closeEntry();
		}
		ClassLoader origin = Thread.currentThread().getContextClassLoader();
		try (URLClassLoader loader = new URLClassLoader(new URL[] { jarPath.toUri().toURL() }, null)) {
			Thread.currentThread().setContextClassLoader(loader);
			List resources = SqlResourceScanner.getSqlResources("classpath*:**/*.sql.xml", null);
			assertEquals(1, resources.size(), "空根扫描不应依赖jar目录entry:" + resources);
			assertTrue(parse(resources).containsKey("nodirs_id"));
		} finally {
			Thread.currentThread().setContextClassLoader(origin);
		}
	}

	@Test
	public void emptyUrlChildWithSystemParentScansApplicationClassPath(@TempDir Path tempDir) throws Exception {
		Path jarPath = tempDir.resolve("system-parent-fixture.jar");
		writeJar(jarPath, "demo/fixture.sql.xml", "system_parent_fixture", "select 1");
		String oldClassPath = System.getProperty("java.class.path");
		ClassLoader origin = Thread.currentThread().getContextClassLoader();
		try {
			System.setProperty("java.class.path",
					oldClassPath + System.getProperty("path.separator") + jarPath.toAbsolutePath());
			Thread.currentThread()
					.setContextClassLoader(new URLClassLoader(new URL[0], ClassLoader.getSystemClassLoader()));
			List resources = SqlResourceScanner.getSqlResources("classpath*:**/*.sql.xml", null);
			assertTrue(containsResource(resources, "system-parent-fixture.jar"),
					"委派系统应用类加载器时应补java.class.path中的jar:" + resources);
		} finally {
			Thread.currentThread().setContextClassLoader(origin);
			System.setProperty("java.class.path", oldClassPath);
		}
	}

	@Test
	public void isolatedLoaderDoesNotScanGlobalClassPath(@TempDir Path tempDir) throws Exception {
		Path jarPath = tempDir.resolve("isolated-fixture.jar");
		writeJar(jarPath, "demo/fixture.sql.xml", "isolated_fixture", "select 1");
		String oldClassPath = System.getProperty("java.class.path");
		ClassLoader origin = Thread.currentThread().getContextClassLoader();
		try {
			System.setProperty("java.class.path",
					oldClassPath + System.getProperty("path.separator") + jarPath.toAbsolutePath());
			Thread.currentThread().setContextClassLoader(new URLClassLoader(new URL[0], null));
			List resources = SqlResourceScanner.getSqlResources("classpath*:**/*.sql.xml", null);
			assertTrue(!containsResource(resources, "isolated-fixture.jar"),
					"纯隔离加载器不应混入JVM全局classpath:" + resources);
		} finally {
			Thread.currentThread().setContextClassLoader(origin);
			System.setProperty("java.class.path", oldClassPath);
		}
	}

	@Test
	public void bootFatJarOuterRootNotDuplicatedWithNestedRoot() throws Exception {
		// update 2026-11-12 Boot fat-jar:LaunchedClassLoader提供嵌套根(此处模拟2.x/3.0的
		// jar:file:/app.jar!/BOOT-INF/classes!/形态),父链可达系统加载器时java.class.path中的
		// 外层app.jar不能再补为根,否则同一SQL以两种URL形态各命中一次,sqlId重复阻断启动
		// (不用@TempDir:Windows下URLClassLoader/JarFile缓存持有jar句柄,测试后临时目录删不掉)
		Path jarPath = Files.createTempFile("fat-app", ".jar");
		try (OutputStream out = Files.newOutputStream(jarPath); JarOutputStream jarOut = new JarOutputStream(out)) {
			// 标准jar handler要求"BOOT-INF/classes!/"entry真实存在才能解析嵌套根(真实Boot由专用handler提供)
			jarOut.putNextEntry(new JarEntry("BOOT-INF/classes!/"));
			jarOut.closeEntry();
			jarOut.putNextEntry(new JarEntry("BOOT-INF/classes/demo/fat.sql.xml"));
			jarOut.write(sqlXml("fat_jar_fixture", "select 1").getBytes(StandardCharsets.UTF_8));
			jarOut.closeEntry();
		}
		String oldClassPath = System.getProperty("java.class.path");
		ClassLoader origin = Thread.currentThread().getContextClassLoader();
		try {
			URL nestedRoot = new URL("jar:" + jarPath.toUri().toURL().toExternalForm() + "!/BOOT-INF/classes!/");
			System.setProperty("java.class.path",
					oldClassPath + System.getProperty("path.separator") + jarPath.toAbsolutePath());
			Thread.currentThread().setContextClassLoader(
					new URLClassLoader(new URL[] { nestedRoot }, ClassLoader.getSystemClassLoader()));
			List resources = SqlResourceScanner.getSqlResources("classpath*:**/*.sql.xml", null);
			int fatJarHits = 0;
			for (Object resource : resources) {
				if (String.valueOf(resource).contains(jarPath.getFileName().toString())) {
					fatJarHits++;
				}
			}
			assertEquals(1, fatJarHits, "fat-jar内同一SQL不能经嵌套根+外层根重复命中:" + resources);
		} finally {
			Thread.currentThread().setContextClassLoader(origin);
			System.setProperty("java.class.path", oldClassPath);
			try {
				Files.deleteIfExists(jarPath);
			} catch (Exception ignore) {
				// Windows下jar句柄可能被加载器缓存持有,删除失败不影响测试结论
			}
		}
	}

	@Test
	public void traditionalClasspathJarPrefixHasDirectoryBoundary(@TempDir Path tempDir) throws Exception {
		Path jarPath = tempDir.resolve("boundary.jar");
		writeJar(jarPath, new String[][] { { "com/app/sql/a.sql.xml", "boundary_inside", "select 1" },
				{ "com/app/sql2/b.sql.xml", "boundary_sibling", "select 2" },
				{ "com/app/sqlX.sql.xml", "boundary_file", "select 3" } });
		ClassLoader origin = Thread.currentThread().getContextClassLoader();
		try (URLClassLoader loader = new URLClassLoader(new URL[] { jarPath.toUri().toURL() }, null)) {
			Thread.currentThread().setContextClassLoader(loader);
			List resources = SqlResourceScanner.getSqlResources("classpath:com/app/sql", null);
			assertEquals(1, resources.size(), "传统目录前缀不能误扫兄弟目录:" + resources);
			ConcurrentHashMap<String, SqlToyConfig> cache = parse(resources);
			assertTrue(cache.containsKey("boundary_inside"));
			assertTrue(!cache.containsKey("boundary_sibling"));
			assertTrue(!cache.containsKey("boundary_file"));
		} finally {
			Thread.currentThread().setContextClassLoader(origin);
		}
	}

	private static boolean containsResource(List resources, String jarName) {
		for (Object resource : resources) {
			if (String.valueOf(resource).contains(jarName)) {
				return true;
			}
		}
		return false;
	}

	@Test
	public void coveredByNestedRootMatchesBothBootGenerations(@TempDir Path tempDir) throws Exception {
		File jar = tempDir.resolve("app.jar").toFile();
		Files.write(jar.toPath(), new byte[0]);
		String fileUrl = jar.toURI().toURL().toExternalForm();
		String abs = jar.getAbsolutePath().replace('\\', '/');
		Method method = SqlResourceScanner.class.getDeclaredMethod("coveredByNestedRoot", List.class, File.class);
		method.setAccessible(true);

		// Boot 2.x/3.0经典多叹号形态:jar:file:/path/app.jar!/BOOT-INF/classes!/
		assertTrue((Boolean) method.invoke(null, roots("jar:" + fileUrl + "!/BOOT-INF/classes!/"), jar),
				"经典classes内层根应判定外层jar已被覆盖");
		// Boot 3.2+嵌套形态(带前导斜杠):jar:nested:/path/app.jar/!BOOT-INF/classes/!/
		assertTrue((Boolean) method.invoke(null, roots("jar:nested:/" + abs + "/!BOOT-INF/classes/!/"), jar),
				"Boot3.2+带前导斜杠嵌套classes根应判定覆盖");
		// Boot 3.2+嵌套形态(Windows盘符无前导斜杠):jar:nested:C:/path/app.jar/!BOOT-INF/classes/!/
		assertTrue((Boolean) method.invoke(null, roots("jar:nested:" + abs + "/!BOOT-INF/classes/!/"), jar),
				"Boot3.2+无前导斜杠嵌套classes根应判定覆盖");
		// 仅暴露BOOT-INF/lib嵌套根:外层jar的BOOT-INF/classes资源未被承载,须保留外层根
		assertFalse((Boolean) method.invoke(null, roots("jar:nested:/" + abs + "/!BOOT-INF/lib/dep.jar!/"), jar),
				"仅lib嵌套根不能判定外层jar被覆盖");
		// 普通非嵌套jar根不参与覆盖判定
		assertFalse((Boolean) method.invoke(null, roots("jar:" + fileUrl + "!/"), jar),
				"非嵌套根不参与覆盖判定");
	}

	/**
	 * 真实Boot 3.2/4.x的nested URL用File.toURI().getRawPath()拼接,路径含空格/中文时为%xx编码;
	 * java.class.path给的是原始字符路径,覆盖判定必须同时匹配编码形态,否则外层jar会重复补根
	 */
	@Test
	public void coveredByNestedRootMatchesUrlEncodedPath(@TempDir Path tempDir) throws Exception {
		Method method = SqlResourceScanner.class.getDeclaredMethod("coveredByNestedRoot", List.class, File.class);
		method.setAccessible(true);
		// 空格路径(Windows常见:Program Files/My Documents)
		File jar = tempDir.resolve("app with space").resolve("app.jar").toFile();
		Files.createDirectories(jar.getParentFile().toPath());
		Files.write(jar.toPath(), new byte[0]);
		String nestedUrl = "jar:nested:" + jar.toURI().toURL().toExternalForm().substring("file:".length())
				+ "/!BOOT-INF/classes/!/";
		assertTrue(nestedUrl.contains("%20"), "用例前置:路径应被URL编码:" + nestedUrl);
		assertTrue((Boolean) method.invoke(null, roots(nestedUrl), jar), "URL编码(空格)路径也必须判定覆盖:" + nestedUrl);
		// 中文路径
		File cjkJar = tempDir.resolve("应用 目录").resolve("app.jar").toFile();
		Files.createDirectories(cjkJar.getParentFile().toPath());
		Files.write(cjkJar.toPath(), new byte[0]);
		String cjkUrl = "jar:nested:" + cjkJar.toURI().toURL().toExternalForm().substring("file:".length())
				+ "/!BOOT-INF/classes/!/";
		assertTrue((Boolean) method.invoke(null, roots(cjkUrl), cjkJar), "中文路径也必须判定覆盖:" + cjkUrl);
	}

	private static List<URL> roots(String spec) throws Exception {
		List<URL> list = new ArrayList<URL>();
		list.add(new URL(null, spec, new URLStreamHandler() {
			@Override
			protected URLConnection openConnection(URL u) {
				return null;
			}
		}));
		return list;
	}

	private static ConcurrentHashMap<String, SqlToyConfig> parse(List resources) throws Exception {
		ConcurrentHashMap<String, SqlToyConfig> cache = new ConcurrentHashMap<String, SqlToyConfig>();
		ConcurrentHashMap<String, Long> lastModified = new ConcurrentHashMap<String, Long>();
		for (Object resource : resources) {
			SqlXMLConfigParse.parseSingleFile(resource, lastModified, cache, "UTF-8", null, false, -1);
		}
		return cache;
	}

	private static void writeXml(Path path, String id, String sql) throws Exception {
		Files.createDirectories(path.getParent());
		Files.write(path, sqlXml(id, sql).getBytes(StandardCharsets.UTF_8));
	}

	private static String sqlXml(String id, String sql) {
		return "<?xml version=\"1.0\" encoding=\"utf-8\"?>\n<sqltoy>\n<sql id=\"" + id
				+ "\"><value><![CDATA[" + sql + "]]></value></sql>\n</sqltoy>\n";
	}

	private static void writeJar(Path jarPath, String entryName, String id, String sql) throws Exception {
		writeJar(jarPath, new String[][] { { entryName, id, sql } });
	}

	private static void writeJar(Path jarPath, String[][] entries) throws Exception {
		try (OutputStream out = Files.newOutputStream(jarPath); JarOutputStream jarOut = new JarOutputStream(out)) {
			java.util.Set<String> createdDirs = new java.util.HashSet<String>();
			for (String[] entry : entries) {
				String[] parts = entry[0].split("/");
				StringBuilder dir = new StringBuilder();
				for (int i = 0; i < parts.length - 1; i++) {
					dir.append(parts[i]).append('/');
					if (createdDirs.add(dir.toString())) {
						jarOut.putNextEntry(new JarEntry(dir.toString()));
						jarOut.closeEntry();
					}
				}
				jarOut.putNextEntry(new JarEntry(entry[0]));
				jarOut.write(sqlXml(entry[1], entry[2]).getBytes(StandardCharsets.UTF_8));
				jarOut.closeEntry();
			}
		}
	}
}
