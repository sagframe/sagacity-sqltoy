package org.sagacity.sqltoy.config;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.File;
import java.io.FileOutputStream;
import java.lang.ref.WeakReference;
import java.net.URL;
import java.net.URLClassLoader;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.concurrent.ConcurrentHashMap;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.sagacity.sqltoy.config.model.SqlToyConfig;
import org.sagacity.sqltoy.utils.BeanUtil;

/**
 * update 2026-11-11 热重载与类卸载长稳测试(必经路径,风险驱动轮):
 * <ol>
 * <li>SqlXMLConfigParse的mtime热重载判定:未变更不重解析、变更后重解析、
 * 解析失败不推进时间戳(2026-9-14修复点:失败后watcher下一轮可重试)、
 * 不同目录同名文件以绝对路径为键不互相覆盖(2026-9-14修复点)</li>
 * <li>类加载/卸载100轮循环:同一字节码经100个独立加载器各建Class实例并登记
 * classFieldTypeCache(ClassValue),全部释放后GC必须全部回收——单轮判别用例
 * (BeanUtilClassFieldMapCacheTest)只能证明"能卸载",循环证明"无慢泄漏"</li>
 * </ol>
 */
public class SqlXmlHotReloadStabilityTest {

	// ==================== mtime 热重载 ====================

	@Test
	public void unchangedFileIsNotReparsed(@TempDir Path tempDir) throws Exception {
		File file = writeSqlXml(tempDir.resolve("a.sql.xml"), "hot_id", "select 1");
		ConcurrentHashMap<String, Long> lastModified = new ConcurrentHashMap<String, Long>();
		ConcurrentHashMap<String, SqlToyConfig> cache = new ConcurrentHashMap<String, SqlToyConfig>();
		SqlXMLConfigParse.parseSingleFile(file, lastModified, cache, "UTF-8", null, true, -1);
		assertEquals("select 1", cache.get("hot_id").getSql(null).trim());
		// 篡改缓存模拟"若重解析会被覆盖"的观测哨兵
		cache.get("hot_id").setSql("CORRUPTED");
		// 未变更:parseXML跳过重解析,哨兵保持
		SqlXMLConfigParse.parseXML(Arrays.asList(new Object[] { file }), lastModified, cache, "UTF-8", null);
		assertEquals("CORRUPTED", cache.get("hot_id").getSql(null), "未变更的文件不得重解析");
	}

	@Test
	public void changedFileIsReparsed(@TempDir Path tempDir) throws Exception {
		File file = writeSqlXml(tempDir.resolve("b.sql.xml"), "hot_id2", "select 1");
		ConcurrentHashMap<String, Long> lastModified = new ConcurrentHashMap<String, Long>();
		ConcurrentHashMap<String, SqlToyConfig> cache = new ConcurrentHashMap<String, SqlToyConfig>();
		SqlXMLConfigParse.parseSingleFile(file, lastModified, cache, "UTF-8", null, true, -1);
		// 内容变更+mtime推进
		writeSqlXml(file.toPath(), "hot_id2", "select 2");
		file.setLastModified(file.lastModified() + 2000);
		SqlXMLConfigParse.parseXML(Arrays.asList(new Object[] { file }), lastModified, cache, "UTF-8", null);
		assertEquals("select 2", cache.get("hot_id2").getSql(null).trim(), "变更后的文件必须重解析");
	}

	@Test
	public void parseFailureDoesNotAdvanceTimestamp(@TempDir Path tempDir) throws Exception {
		Path dir = Files.createDirectories(tempDir.resolve("fail"));
		File bad = dir.resolve("bad.sql.xml").toFile();
		Files.write(bad.toPath(), "not-a-valid-xml<<".getBytes(StandardCharsets.UTF_8));
		ConcurrentHashMap<String, Long> lastModified = new ConcurrentHashMap<String, Long>();
		ConcurrentHashMap<String, SqlToyConfig> cache = new ConcurrentHashMap<String, SqlToyConfig>();
		boolean failed = false;
		try {
			SqlXMLConfigParse.parseSingleFile(bad, lastModified, cache, "UTF-8", null, true, -1);
		} catch (Exception e) {
			failed = true;
		}
		assertTrue(failed, "非法xml应解析失败");
		assertNull(lastModified.get(bad.getAbsolutePath()), "解析失败不得推进时间戳(推进后watcher不再重试)");
	}

	@Test
	public void sameNameDifferentDirsUseAbsolutePathKey(@TempDir Path tempDir) throws Exception {
		Path dir1 = Files.createDirectories(tempDir.resolve("d1"));
		Path dir2 = Files.createDirectories(tempDir.resolve("d2"));
		File f1 = writeSqlXml(dir1.resolve("same.sql.xml"), "same_id", "select 1");
		File f2 = writeSqlXml(dir2.resolve("same.sql.xml"), "same_id", "select 2");
		ConcurrentHashMap<String, Long> lastModified = new ConcurrentHashMap<String, Long>();
		ConcurrentHashMap<String, SqlToyConfig> cache = new ConcurrentHashMap<String, SqlToyConfig>();
		SqlXMLConfigParse.parseSingleFile(f1, lastModified, cache, "UTF-8", null, true, -1);
		SqlXMLConfigParse.parseSingleFile(f2, lastModified, cache, "UTF-8", null, true, -1);
		// 两个同名文件的时间戳各自独立(若以basename为键会互相覆盖比较基准)
		assertNotNull(lastModified.get(f1.getAbsolutePath()));
		assertNotNull(lastModified.get(f2.getAbsolutePath()));
		// 后解析者胜出(同名sqlId覆盖),但f1的时间戳基准不受f2影响:
		// 推进f1的mtime后parseXML应重解析f1并把sql改回select 1
		f1.setLastModified(f1.lastModified() + 2000);
		writeSqlXml(f1.toPath(), "same_id", "select 1-again");
		SqlXMLConfigParse.parseXML(Arrays.asList(new Object[] { f1, f2 }), lastModified, cache, "UTF-8", null);
		assertEquals("select 1-again", cache.get("same_id").getSql(null).trim(), "f1变更须生效(键不被同名文件覆盖)");
	}

	// ==================== 100轮类加载/卸载循环 ====================

	@Test
	public void hundredClassLoaderCyclesAllCollect() throws Exception {
		Path dir = Files.createTempDirectory("cycle-classes");
		Path src = dir.resolve("CycleVO.java");
		Files.write(src, ("import org.sagacity.sqltoy.config.annotation.Column;"
				+ "import org.sagacity.sqltoy.model.JdbcTypes;"
				+ "public class CycleVO {"
				+ "@Column(name = \"c_col\", type = JdbcTypes.JSON) private String cCol;"
				+ "}").getBytes(StandardCharsets.UTF_8));
		javax.tools.JavaCompiler compiler = javax.tools.ToolProvider.getSystemJavaCompiler();
		assertEquals(0, compiler.run(null, null, null, "-encoding", "UTF-8",
				"-cp", System.getProperty("java.class.path"), "-d", dir.toString(), src.toString()), "fixture编译失败");
		List<WeakReference<Class<?>>> weakClasses = new ArrayList<WeakReference<Class<?>>>();
		List<URLClassLoader> loaders = new ArrayList<URLClassLoader>();
		try {
			for (int i = 0; i < 100; i++) {
				URLClassLoader loader = new URLClassLoader(new URL[] { dir.toUri().toURL() },
						getClass().getClassLoader());
				loaders.add(loader);
				Class<?> clazz = Class.forName("CycleVO", false, loader);
				weakClasses.add(new WeakReference<Class<?>>(clazz));
				// 登记进ClassValue缓存(每轮一个独立的Class实例)
				assertFalse(BeanUtil.getClassFieldMap(clazz, new String[] { "cCol" }).isEmpty(),
						"第" + i + "轮字段映射应生效");
			}
			assertEquals(100, weakClasses.size());
		} finally {
			// 释放全部加载器强引用
			for (URLClassLoader loader : loaders) {
				loader.close();
			}
			loaders.clear();
			loaders = null;
		}
		// GC后100个Class实例必须全部回收(ClassValue不构成强引用)
		for (int round = 0; round < 40; round++) {
			System.gc();
			Thread.sleep(50);
			int alive = 0;
			for (WeakReference<Class<?>> weak : weakClasses) {
				if (weak.get() != null) {
					alive++;
				}
			}
			if (alive == 0) {
				break;
			}
		}
		int alive = 0;
		for (WeakReference<Class<?>> weak : weakClasses) {
			if (weak.get() != null) {
				alive++;
			}
		}
		assertEquals(0, alive, "100轮类加载卸载循环后不得有Class滞留(实际存活:" + alive + ")——ClassValue慢泄漏信号");
	}

	private static File writeSqlXml(Path path, String id, String sql) throws Exception {
		Files.createDirectories(path.getParent());
		File file = path.toFile();
		try (FileOutputStream out = new FileOutputStream(file)) {
			out.write(("<?xml version=\"1.0\" encoding=\"utf-8\"?>\n<sqltoy>\n<sql id=\"" + id
					+ "\"><value><![CDATA[" + sql + "]]></value></sql>\n</sqltoy>\n").getBytes(StandardCharsets.UTF_8));
		}
		return file;
	}
}
