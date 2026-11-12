package org.sagacity.sqltoy.config;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.File;
import java.io.FileOutputStream;
import java.net.URL;
import java.net.URLClassLoader;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.concurrent.ConcurrentHashMap;
import java.util.jar.JarEntry;
import java.util.jar.JarOutputStream;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.sagacity.sqltoy.config.model.SqlToyConfig;

/**
 * update 2026-11-11 资源扫描规模门禁(风险驱动轮):
 * 空根classpath*发现会对类路径上全部jar做全量entry枚举——200个依赖jar的fat部署形态下
 * 扫描耗时不允许数量级退化(线性可接受),且200个jar内的sql资源全部可见不漏。
 */
public class SqlResourceScanScaleTest {

	private static final int JAR_COUNT = 200;

	@Test
	public void twoHundredJarsScanCompletesInLinearTime(@TempDir Path tempDir) throws Exception {
		Path libDir = Files.createDirectories(tempDir.resolve("libs"));
		for (int i = 0; i < JAR_COUNT; i++) {
			File jar = libDir.resolve("dep" + i + ".jar").toFile();
			try (JarOutputStream jarOut = new JarOutputStream(new FileOutputStream(jar))) {
				jarOut.putNextEntry(new JarEntry("sqltoy/"));
				jarOut.closeEntry();
				jarOut.putNextEntry(new JarEntry("sqltoy/dep" + i + ".sql.xml"));
				jarOut.write(("<?xml version=\"1.0\" encoding=\"utf-8\"?>\n<sqltoy>\n<sql id=\"scale_sql_" + i
						+ "\"><value><![CDATA[select " + i + "]]></value></sql>\n</sqltoy>\n")
						.getBytes(StandardCharsets.UTF_8));
				jarOut.closeEntry();
			}
		}
		// 200个jar各自作为类路径条目(真实fat部署形态:LaunchedClassLoader的getURLs即逐jar)
		URL[] jarUrls = new URL[JAR_COUNT];
		for (int i = 0; i < JAR_COUNT; i++) {
			jarUrls[i] = libDir.resolve("dep" + i + ".jar").toUri().toURL();
		}
		try (URLClassLoader loader = new URLClassLoader(jarUrls, ClassLoader.getSystemClassLoader())) {
			ClassLoader origin = Thread.currentThread().getContextClassLoader();
			Thread.currentThread().setContextClassLoader(loader);
			try {
				long start = System.nanoTime();
				List resources = SqlResourceScanner.getSqlResources("classpath*:**/*.sql.xml", null);
				long elapsedMs = (System.nanoTime() - start) / 1_000_000;
				// 200个jar全部可见(系统classpath的既有fixture经委派可见性可额外出现)
				assertTrue(resources.size() >= JAR_COUNT, "200个jar的sql资源应全部可见: " + resources.size());
				ConcurrentHashMap<String, SqlToyConfig> cache = new ConcurrentHashMap<String, SqlToyConfig>();
				ConcurrentHashMap<String, Long> lastModified = new ConcurrentHashMap<String, Long>();
				for (Object resource : resources) {
					SqlXMLConfigParse.parseSingleFile(resource, lastModified, cache, "UTF-8", null, false, -1);
				}
				for (int i = 0; i < JAR_COUNT; i++) {
					assertTrue(cache.containsKey("scale_sql_" + i), "第" + i + "个jar的sqlId应登记");
				}
				// 规模门禁:200 jar扫描应远低于10秒(现状毫秒级,防数量级退化)
				assertTrue(elapsedMs < 10_000, "200-jar扫描耗时:" + elapsedMs + "ms,超出门禁");
			} finally {
				Thread.currentThread().setContextClassLoader(origin);
			}
		}
	}
}
