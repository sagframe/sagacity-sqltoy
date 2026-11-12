package org.sagacity.sqltoy.config;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.junit.jupiter.api.Assumptions.assumeTrue;

import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.net.URL;
import java.net.URLClassLoader;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Enumeration;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.concurrent.TimeUnit;
import java.util.jar.JarEntry;
import java.util.jar.JarOutputStream;

import javax.tools.JavaCompiler;
import javax.tools.ToolProvider;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

/**
 * update 2026-11-11 实体扫描的Spring Boot fat jar多形态集成测试(项目必经路径,严格门禁):
 * <p>
 * 用真实boot launcher以子进程运行组装的fat jar,验证 {@link EntityScanner#scanEntityClasses}
 * 在各方言形态下的发现能力——这是对"扫描结果只依赖ClassLoader.getResources返回的URL协议形态"
 * 的端到端实证(而非仅对自造URL的单元断言):
 * <ul>
 * <li>Boot 3.1.2(经典launcher):资源URL为多叹号 {@code jar:file:/app.jar!/BOOT-INF/classes!/pkg/} 形态,
 * 须经boot classic Handler的JarURLConnection取内层jar视图</li>
 * <li>Boot 4.1.2(现行launcher):资源URL为 {@code nested:} 协议形态,分派层必须识别该协议并
 * 经boot loader的NestedJarFile取内层视图(裸NestedUrlConnection并非JarURLConnection)</li>
 * <li>双来源:BOOT-INF/classes(应用自身实体)与BOOT-INF/lib/dep.jar(依赖内实体)都必须可见</li>
 * <li>** 语义:com.app.** 须匹配基包直属类(零层),com.**.sub 须匹配中间层通配——跨classes与lib两来源</li>
 * </ul>
 * fixture实体经javax.tools现场编译,orm-core以target/classes拷入BOOT-INF/classes;
 * loader jar由pom的dependency:copy落至target/boot-loaders(缺失时整类跳过,不阻断普通单测环境)。
 */
public class EntityScannerFatJarTest {

	private static final String APP_ENTITY_SRC = "package com.app;\n"
			+ "import org.sagacity.sqltoy.config.annotation.Entity;\n"
			+ "@Entity(tableName = \"app_entity\")\n"
			+ "public class AppEntity {\n"
			+ "}\n";

	private static final String SUB_ENTITY_SRC = "package com.app.sub;\n"
			+ "import org.sagacity.sqltoy.config.annotation.Entity;\n"
			+ "@Entity(tableName = \"app_sub_entity\")\n"
			+ "public class SubEntity {\n"
			+ "}\n";

	private static final String OTHER_ENTITY_SRC = "package com.other;\n"
			+ "import org.sagacity.sqltoy.config.annotation.Entity;\n"
			+ "@Entity(tableName = \"other_entity\")\n"
			+ "public class OtherEntity {\n"
			+ "}\n";

	private static final String LIB_ENTITY_SRC = "package com.lib;\n"
			+ "import org.sagacity.sqltoy.config.annotation.Entity;\n"
			+ "@Entity(tableName = \"lib_entity\")\n"
			+ "public class LibEntity {\n"
			+ "}\n";

	private static final String LIB_DEEP_ENTITY_SRC = "package com.lib.deep;\n"
			+ "import org.sagacity.sqltoy.config.annotation.Entity;\n"
			+ "@Entity(tableName = \"deep_lib_entity\")\n"
			+ "public class DeepLibEntity {\n"
			+ "}\n";

	/** 子进程入口:按参数扫描实体并打印,同时打印getResources的URL形态供形态断言 */
	private static final String RUNNER_SRC = "package com.app.launch;\n"
			+ "public class ScanRunner {\n"
			+ "  public static void main(String[] args) throws Exception {\n"
			+ "    if (\"sql\".equals(args[0])) {\n"
			+ "      java.util.List resources = org.sagacity.sqltoy.config.SqlResourceScanner\n"
			+ "          .getSqlResources(\"classpath*:**/*.sql.xml\", null);\n"
			+ "      java.util.concurrent.ConcurrentHashMap<String, Long> lastModified\n"
			+ "          = new java.util.concurrent.ConcurrentHashMap<String, Long>();\n"
			+ "      java.util.concurrent.ConcurrentHashMap<String, org.sagacity.sqltoy.config.model.SqlToyConfig> cache\n"
			+ "          = new java.util.concurrent.ConcurrentHashMap<String, org.sagacity.sqltoy.config.model.SqlToyConfig>();\n"
			+ "      for (Object resource : resources) {\n"
			+ "        org.sagacity.sqltoy.config.SqlXMLConfigParse.parseSingleFile(resource, lastModified, cache,\n"
			+ "            \"UTF-8\", null, false, -1);\n"
			+ "      }\n"
			+ "      for (String id : cache.keySet()) {\n"
			+ "        System.out.println(\"SQLID:\" + id);\n"
			+ "      }\n"
			+ "      System.out.println(\"SQLTOTAL:\" + cache.size());\n"
			+ "    } else {\n"
			+ "      java.util.List<Class<?>> list = org.sagacity.sqltoy.config.EntityScanner\n"
			+ "          .scanEntityClasses(args[0], Boolean.parseBoolean(args[1]), null);\n"
			+ "      for (Class<?> c : list) {\n"
			+ "        System.out.println(\"FOUND:\" + c.getName());\n"
			+ "      }\n"
			+ "      System.out.println(\"TOTAL:\" + list.size());\n"
			+ "    }\n"
			+ "    java.util.Enumeration<java.net.URL> urls = Thread.currentThread().getContextClassLoader()\n"
			+ "        .getResources(args[2]);\n"
			+ "    while (urls.hasMoreElements()) {\n"
			+ "      System.out.println(\"RESURL:\" + urls.nextElement().toExternalForm());\n"
			+ "    }\n"
			+ "    System.out.println(\"END\");\n"
			+ "  }\n"
			+ "}\n";

	/** Boot 3.1.2:经典多叹号jar: URL形态(3.0/3.1及2.x) */
	@Test
	public void classicFatJarFindsClassesAndLibEntities(@TempDir Path tempDir) throws Exception {
		File fatJar = buildFatJar(tempDir, "3.1.2", "org.springframework.boot.loader.JarLauncher");
		RunResult result = runFatJar(fatJar, "com.app", "com/app");
		assertTrue(result.urls.stream().anyMatch(u -> u.startsWith("jar:") && u.contains("!/BOOT-INF/")),
				"3.1.2 launcher应产生多叹号jar: URL形态: " + result.urls);
		assertEntitiesFound(result, "com.app.AppEntity", "com.app.sub.SubEntity");
		// 依赖jar中的实体
		assertEntitiesFound(runFatJar(fatJar, "com.lib", "com/lib"), "com.lib.LibEntity", "com.lib.deep.DeepLibEntity");
		// **零层语义:com.lib.** 须匹配基包直属LibEntity(旧glob实现会漏)
		assertEntitiesFound(runFatJar(fatJar, "com.lib.**", "com/lib"), "com.lib.LibEntity", "com.lib.deep.DeepLibEntity");
	}

	/** Boot 3.2.12:首个nested世代launcher,须识别其资源URL形态(实证3.2.x是否为裸nested:协议) */
	@Test
	public void firstNestedGenerationFatJarFindsEntities(@TempDir Path tempDir) throws Exception {
		File fatJar = buildFatJar(tempDir, "3.2.12", "org.springframework.boot.loader.launch.JarLauncher");
		RunResult result = runFatJar(fatJar, "com.app", "com/app");
		assertTrue(result.urls.stream().anyMatch(u -> u.startsWith("jar:nested:")),
				"3.2.12 launcher应产生jar:nested: URL形态: " + result.urls);
		assertEntitiesFound(result, "com.app.AppEntity", "com.app.sub.SubEntity");
		assertEntitiesFound(runFatJar(fatJar, "com.lib", "com/lib"), "com.lib.LibEntity", "com.lib.deep.DeepLibEntity");
	}

	/** Boot 4.1.2:现行launcher,资源URL为nested:协议形态 */
	@Test
	public void nestedFatJarFindsClassesAndLibEntities(@TempDir Path tempDir) throws Exception {
		File fatJar = buildFatJar(tempDir, "4.1.2", "org.springframework.boot.loader.launch.JarLauncher");
		RunResult result = runFatJar(fatJar, "com.app", "com/app");
		// 实测4.x的LaunchedClassLoader资源URL为jar:nested:形态(协议jar+嵌套root,boot的JarUrlConnection承接);
		// 3.2.x世代为裸nested:协议(Boot 3.2.0引入),两者都必须被扫描层识别
		assertTrue(result.urls.stream().anyMatch(u -> u.startsWith("nested:") || u.startsWith("jar:nested:")),
				"4.1.2 launcher应产生nested系URL形态: " + result.urls);
		assertEntitiesFound(result, "com.app.AppEntity", "com.app.sub.SubEntity");
		assertEntitiesFound(runFatJar(fatJar, "com.lib", "com/lib"), "com.lib.LibEntity", "com.lib.deep.DeepLibEntity");
		assertEntitiesFound(runFatJar(fatJar, "com.lib.**", "com/lib"), "com.lib.LibEntity", "com.lib.deep.DeepLibEntity");
	}

	/** **中间层通配:com.**.sub 须命中com.app.sub(跨classes来源),不误收com.other */
	@Test
	public void midGlobstarPatternAcrossSources(@TempDir Path tempDir) throws Exception {
		File fatJar = buildFatJar(tempDir, "4.1.2", "org.springframework.boot.loader.launch.JarLauncher");
		RunResult result = runFatJar(fatJar, "com.**.sub", "com/app");
		assertEntitiesFound(result, "com.app.sub.SubEntity");
		assertTrue(!result.found.contains("com.other.OtherEntity"), "com.**.sub不得命中com.other: " + result.found);
	}

	/** 炸开目录形态(本地开发常态):file: URL;**零层语义+非递归边界 */
	@Test
	public void explodedDirectoryForm(@TempDir Path tempDir) throws Exception {
		Path classesDir = Files.createDirectories(tempDir.resolve("exploded"));
		compileSources(tempDir, classesDir.toFile(),
				"AppEntity", APP_ENTITY_SRC,
				"SubEntity", SUB_ENTITY_SRC,
				"LibEntity", LIB_ENTITY_SRC,
				"DeepLibEntity", LIB_DEEP_ENTITY_SRC);
		try (URLClassLoader loader = new URLClassLoader(new URL[] { classesDir.toUri().toURL() },
				EntityScannerFatJarTest.class.getClassLoader())) {
			swapContextClassLoader(loader);
			try {
				Set<String> found = scanAsSet("com.app", true);
				assertTrue(found.contains("com.app.AppEntity") && found.contains("com.app.sub.SubEntity"),
						"炸开目录应发现直属与子包实体: " + found);
				// **零层:com.lib.** 须匹配基包直属LibEntity(旧glob语义会漏,本轮修复点)
				Set<String> star = scanAsSet("com.lib.**", true);
				assertTrue(star.contains("com.lib.LibEntity") && star.contains("com.lib.deep.DeepLibEntity"),
						"com.lib.**须匹配基包直属与深层: " + star);
				// 非递归边界:只取直属,不深入子包
				Set<String> flat = scanAsSet("com.lib", false);
				assertTrue(flat.contains("com.lib.LibEntity") && !flat.contains("com.lib.deep.DeepLibEntity"),
						"非递归只取直属: " + flat);
			} finally {
				swapContextClassLoader(null);
			}
		}
	}

	/** 普通依赖jar形态(打包引用场景):单叹号jar: URL(JDK原生handler);**零层语义同样生效 */
	@Test
	public void plainDependencyJarForm(@TempDir Path tempDir) throws Exception {
		Path classesDir = Files.createDirectories(tempDir.resolve("jarclasses"));
		compileSources(tempDir, classesDir.toFile(),
				"AppEntity", APP_ENTITY_SRC,
				"SubEntity", SUB_ENTITY_SRC,
				"LibEntity", LIB_ENTITY_SRC);
		File plainJar = tempDir.resolve("plain.jar").toFile();
		try (JarOutputStream jarOut = new JarOutputStream(new FileOutputStream(plainJar))) {
			// addDirectory递归写目录entry,无需手工补
			addDirectory(jarOut, classesDir.toFile(), "");
		}
		try (URLClassLoader loader = new URLClassLoader(new URL[] { plainJar.toURI().toURL() },
				EntityScannerFatJarTest.class.getClassLoader())) {
			swapContextClassLoader(loader);
			try {
				Set<String> found = scanAsSet("com.app", true);
				assertTrue(found.contains("com.app.AppEntity") && found.contains("com.app.sub.SubEntity"),
						"普通jar应发现实体: " + found);
				Set<String> star = scanAsSet("com.lib.**", true);
				assertTrue(star.contains("com.lib.LibEntity"), "jar内com.lib.**须匹配基包直属: " + star);
			} finally {
				swapContextClassLoader(null);
			}
		}
	}

	private static Set<String> scanAsSet(String pattern, boolean recursive) throws Exception {
		Set<String> names = new HashSet<String>();
		for (Class<?> clazz : EntityScanner.scanEntityClasses(pattern, recursive, null)) {
			names.add(clazz.getName());
		}
		return names;
	}

	private static void swapContextClassLoader(ClassLoader loader) {
		if (loader != null) {
			Thread.currentThread().setContextClassLoader(loader);
		} else {
			Thread.currentThread().setContextClassLoader(EntityScannerFatJarTest.class.getClassLoader());
		}
	}

	/**
	 * 空根classpath*全匹配sql.xml在真实launched fat jar内的端到端:
	 * LaunchedClassLoader(extends URLClassLoader)的getURLs经toClasspathRootUrl转根URL,
	 * BOOT-INF/classes与BOOT-INF/lib/dep.jar两侧的sql xml都必须可见——sql资源加载必经路径的最深组合
	 */
	@Test
	public void nestedFatJarEmptyRootClasspathStarLoadsSqlFromBothSources(@TempDir Path tempDir) throws Exception {
		File fatJar = buildFatJar(tempDir, "4.1.2", "org.springframework.boot.loader.launch.JarLauncher");
		ProcessBuilder pb = new ProcessBuilder(System.getProperty("java.home") + File.separator + "bin"
				+ File.separator + "java", "-jar", fatJar.getAbsolutePath(), "sql", "-", "sqltoy");
		pb.redirectErrorStream(true);
		Process process = pb.start();
		ByteArrayOutputStream out = new ByteArrayOutputStream();
		try (InputStream in = process.getInputStream()) {
			byte[] buffer = new byte[4096];
			int n;
			while ((n = in.read(buffer)) >= 0) {
				out.write(buffer, 0, n);
			}
		}
		assertTrue(process.waitFor(90, TimeUnit.SECONDS), "子进程超时");
		String raw = out.toString("UTF-8");
		assertEquals(0, process.exitValue(), "空根classpath*扫描子进程失败:\n" + raw);
		assertTrue(raw.contains("SQLID:fatjar_app_sql"), "BOOT-INF/classes侧sql.xml应被发现:\n" + raw);
		assertTrue(raw.contains("SQLID:fatjar_lib_sql"), "BOOT-INF/lib/dep.jar侧sql.xml应被发现:\n" + raw);
	}

	// ==================== fixture组装与子进程 ====================

	private static final class RunResult {
		final Set<String> found = new HashSet<String>();
		final List<String> urls = new ArrayList<String>();
		final String raw;
		RunResult(String raw) {
			this.raw = raw;
			for (String line : raw.split("\\R")) {
				if (line.startsWith("FOUND:")) {
					found.add(line.substring("FOUND:".length()));
				} else if (line.startsWith("RESURL:")) {
					urls.add(line.substring("RESURL:".length()));
				}
			}
		}
	}

	private static void assertEntitiesFound(RunResult result, String... expected) {
		for (String name : expected) {
			assertTrue(result.found.contains(name), "应发现实体 " + name + ",实际: " + result.found);
		}
	}

	private static RunResult runFatJar(File fatJar, String pattern, String resourcePath) throws Exception {
		ProcessBuilder pb = new ProcessBuilder(System.getProperty("java.home") + File.separator + "bin"
				+ File.separator + "java", "-jar", fatJar.getAbsolutePath(), pattern, "true", resourcePath);
		pb.redirectErrorStream(true);
		Process process = pb.start();
		ByteArrayOutputStream out = new ByteArrayOutputStream();
		try (InputStream in = process.getInputStream()) {
			byte[] buffer = new byte[4096];
			int n;
			while ((n = in.read(buffer)) >= 0) {
				out.write(buffer, 0, n);
			}
		}
		assertTrue(process.waitFor(90, TimeUnit.SECONDS), "子进程超时");
		assertEquals(0, process.exitValue(), "子进程退出码非0:\n" + out.toString("UTF-8")
				+ "\n=== FAT JAR条目:\n" + listJarEntries(fatJar));
		String raw = out.toString("UTF-8");
		assertTrue(raw.contains("END"), "子进程未完成输出:\n" + raw);
		return new RunResult(raw);
	}

	/**
	 * 组装可执行fat jar:loader类在jar根 + BOOT-INF/classes(orm-core的classes+fixture) +
	 * BOOT-INF/lib(slf4j-api + 含实体的dep.jar),MANIFEST含Main-Class与Start-Class
	 */
	private static File buildFatJar(Path tempDir, String loaderVersion, String launcherClass) throws Exception {
		File loaderJar = new File("target/boot-loaders/spring-boot-loader-" + loaderVersion + ".jar");
		assumeTrue(loaderJar.exists(), "boot loader " + loaderVersion + " 未拷贝(离线环境),跳过fat jar集成测试");
		Path work = Files.createDirectories(tempDir.resolve("build-" + loaderVersion));
		Path classesDir = Files.createDirectories(work.resolve("classes"));
		// ① orm-core的classes(注解/EntityScanner本体)
		copyTree(new File("target/classes"), classesDir.toFile());
		// ② classes侧的sql xml(空根classpath*扫描fixture)
		Path sqlDir = Files.createDirectories(classesDir.resolve("sqltoy"));
		Files.write(sqlDir.resolve("app-sql.sql.xml"),
				sqlXmlOf("fatjar_app_sql", "select 1 as app").getBytes(StandardCharsets.UTF_8));
		// ③ 现场编译fixture实体与runner(注解经orm-core classes解析)
		compileSources(work, classesDir.toFile(),
				"AppEntity", APP_ENTITY_SRC,
				"SubEntity", SUB_ENTITY_SRC,
				"OtherEntity", OTHER_ENTITY_SRC,
				"LibEntity", LIB_ENTITY_SRC,
				"DeepLibEntity", LIB_DEEP_ENTITY_SRC,
				"ScanRunner", RUNNER_SRC);
		// ③ 依赖jar:slf4j-api + transmittable-thread-local(经test classpath定位) + dep.jar(lib实体)
		File slf4jJar = locateClasspathJar("org/slf4j/Logger.class");
		assertNotNull(slf4jJar, "slf4j-api jar未在classpath上定位到");
		File ttlJar = locateClasspathJar("com/alibaba/ttl/TransmittableThreadLocal.class");
		assertNotNull(ttlJar, "transmittable-thread-local jar未在classpath上定位到");
		Path libDir = Files.createDirectories(work.resolve("lib"));
		Files.copy(slf4jJar.toPath(), libDir.resolve(slf4jJar.getName()));
		Files.copy(ttlJar.toPath(), libDir.resolve(ttlJar.getName()));
		buildDepJar(libDir.resolve("dep.jar").toFile());
		// ④ 组装fat jar
		File fatJar = work.resolve("app-" + loaderVersion + ".jar").toFile();
		try (JarOutputStream jarOut = new JarOutputStream(new FileOutputStream(fatJar), manifest(launcherClass))) {
			// loader类放jar根(跳过其MANIFEST,沿用本次创建的)
			copyJarEntries(loaderJar, jarOut, "BOOT-INF");
			// BOOT-INF/classes/根目录entry必须存在:launcher按该entry枚举类路径归档,缺失则classes整体不可见
			jarOut.putNextEntry(new JarEntry("BOOT-INF/classes/"));
			jarOut.closeEntry();
			addDirectory(jarOut, classesDir.toFile(), "BOOT-INF/classes");
			// BOOT-INF/lib的嵌套jar必须STORED(不压缩):boot launcher以内嵌zip偏移直读,压缩形态报Unable to open nested jar file
			addStoredFile(jarOut, libDir.resolve("dep.jar").toFile(), "BOOT-INF/lib/dep.jar");
			addStoredFile(jarOut, libDir.resolve(slf4jJar.getName()).toFile(), "BOOT-INF/lib/" + slf4jJar.getName());
			addStoredFile(jarOut, libDir.resolve(ttlJar.getName()).toFile(), "BOOT-INF/lib/" + ttlJar.getName());
		}
		return fatJar;
	}

	private static String listJarEntries(File jar) {
		StringBuilder sb = new StringBuilder();
		try (java.util.jar.JarFile jarFile = new java.util.jar.JarFile(jar)) {
			Enumeration<JarEntry> entries = jarFile.entries();
			while (entries.hasMoreElements()) {
				sb.append(entries.nextElement().getName()).append('\n');
			}
		} catch (Exception e) {
			sb.append("list failed: ").append(e);
		}
		return sb.toString();
	}

	private static java.util.jar.Manifest manifest(String launcherClass) {
		java.util.jar.Manifest manifest = new java.util.jar.Manifest();
		manifest.getMainAttributes().putValue("Manifest-Version", "1.0");
		manifest.getMainAttributes().putValue("Main-Class", launcherClass);
		manifest.getMainAttributes().putValue("Start-Class", "com.app.launch.ScanRunner");
		return manifest;
	}

	private static void compileSources(Path work, File classesDir, String... nameAndSourcePairs) throws IOException {
		JavaCompiler compiler = ToolProvider.getSystemJavaCompiler();
		assertNotNull(compiler, "JDK编译器不可用");
		List<String> args = new ArrayList<String>();
		for (int i = 0; i < nameAndSourcePairs.length; i += 2) {
			// public类必须落在同名java文件里
			Path src = work.resolve(nameAndSourcePairs[i] + ".java");
			Files.write(src, nameAndSourcePairs[i + 1].getBytes(StandardCharsets.UTF_8));
			args.add(src.toString());
		}
		args.addAll(Arrays.asList("-encoding", "UTF-8", "-d", classesDir.getAbsolutePath()));
		ByteArrayOutputStream err = new ByteArrayOutputStream();
		int rc = compiler.run(null, null, err, args.toArray(new String[0]));
		assertEquals(0, rc, "fixture编译失败:\n" + err.toString("UTF-8"));
	}

	/** BOOT-INF/lib/dep.jar:含com/lib实体(带目录entry) */
	/** BOOT-INF/lib/dep.jar:含com/lib实体(带目录entry)与lib侧sql xml */
	private static void buildDepJar(File depJar) throws IOException {
		Path tmp = Files.createTempDirectory("depjar-classes");
		File classesDir = tmp.toFile();
		compileSources(tmp, classesDir,
				"LibEntity", LIB_ENTITY_SRC,
				"DeepLibEntity", LIB_DEEP_ENTITY_SRC);
		Path sqlDir = Files.createDirectories(tmp.resolve("sqltoy"));
		Files.write(sqlDir.resolve("lib-sql.sql.xml"),
				sqlXmlOf("fatjar_lib_sql", "select 2 as lib").getBytes(StandardCharsets.UTF_8));
		try (JarOutputStream jarOut = new JarOutputStream(new FileOutputStream(depJar))) {
			addDirectory(jarOut, classesDir, "");
		}
	}

	private static String sqlXmlOf(String id, String sql) {
		return "<?xml version=\"1.0\" encoding=\"utf-8\"?>\n<sqltoy>\n<sql id=\"" + id + "\"><value><![CDATA["
				+ sql + "]]></value></sql>\n</sqltoy>\n";
	}

	private static void addDirectory(JarOutputStream jarOut, File dir, String prefix) throws IOException {
		File[] children = dir.listFiles();
		if (children == null) {
			return;
		}
		for (File child : children) {
			String entryName = prefix.isEmpty() ? child.getName() : prefix + "/" + child.getName();
			if (child.isDirectory()) {
				jarOut.putNextEntry(new JarEntry(entryName + "/"));
				jarOut.closeEntry();
				addDirectory(jarOut, child, entryName);
			} else {
				addFile(jarOut, child, entryName);
			}
		}
	}

	/** STORED形态写入(boot launcher要求BOOT-INF/lib下的jar不压缩,需预置size与CRC) */
	private static void addStoredFile(JarOutputStream jarOut, File file, String entryName) throws IOException {
		JarEntry entry = new JarEntry(entryName);
		entry.setMethod(JarEntry.STORED);
		byte[] data = Files.readAllBytes(file.toPath());
		entry.setSize(data.length);
		entry.setTime(file.lastModified());
		java.util.zip.CRC32 crc = new java.util.zip.CRC32();
		crc.update(data);
		entry.setCrc(crc.getValue());
		jarOut.putNextEntry(entry);
		jarOut.write(data);
		jarOut.closeEntry();
	}

	private static void addFile(JarOutputStream jarOut, File file, String entryName) throws IOException {
		jarOut.putNextEntry(new JarEntry(entryName));
		Files.copy(file.toPath(), jarOut);
		jarOut.closeEntry();
	}

	/** 拷贝jar内全部entry(可排除某前缀,如loader jar中的BOOT-INF不存在但保留扩展位) */
	private static void copyJarEntries(File sourceJar, JarOutputStream jarOut, String excludePrefix)
			throws IOException {
		try (java.util.jar.JarFile jarFile = new java.util.jar.JarFile(sourceJar)) {
			Enumeration<JarEntry> entries = jarFile.entries();
			while (entries.hasMoreElements()) {
				JarEntry entry = entries.nextElement();
				if (entry.isDirectory() || entry.getName().startsWith("META-INF/MANIFEST")
						|| entry.getName().startsWith(excludePrefix)) {
					continue;
				}
				JarEntry newEntry = new JarEntry(entry.getName());
				jarOut.putNextEntry(newEntry);
				try (InputStream in = jarFile.getInputStream(entry)) {
					byte[] buffer = new byte[4096];
					int n;
					while ((n = in.read(buffer)) >= 0) {
						jarOut.write(buffer, 0, n);
					}
				}
				jarOut.closeEntry();
			}
		}
	}

	private static void copyTree(File from, File to) throws IOException {
		File[] children = from.listFiles();
		if (children == null) {
			return;
		}
		for (File child : children) {
			File target = new File(to, child.getName());
			if (child.isDirectory()) {
				target.mkdirs();
				copyTree(child, target);
			} else {
				Files.copy(child.toPath(), target.toPath());
			}
		}
	}

	/** 经classpath资源定位所在jar文件(test classpath上的依赖jar) */
	private static File locateClasspathJar(String resourcePath) throws Exception {
		URL url = EntityScannerFatJarTest.class.getClassLoader().getResource(resourcePath);
		if (url == null || !"jar".equals(url.getProtocol())) {
			return null;
		}
		String external = url.toExternalForm();
		String jarPath = external.substring("jar:file:".length(), external.indexOf("!/"));
		return new File(new java.net.URI("file://" + jarPath).getPath());
	}
}
