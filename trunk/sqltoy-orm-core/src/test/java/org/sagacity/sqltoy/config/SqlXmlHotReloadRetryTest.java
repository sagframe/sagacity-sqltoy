package org.sagacity.sqltoy.config;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.File;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.ConcurrentHashMap;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.sagacity.sqltoy.config.model.SqlToyConfig;

/**
 * F8回归:sql.xml热更新必须"解析成功后才记录时间戳",且失败不得改动缓存。
 * 修复前parseXML/parseSingleFile先写时间戳再解析:解析失败时时间戳已前进,若文件系统时间戳精度为秒、
 * 开发者在同一tick内先存坏内容再存好内容,严格大于比较会判定"未变更",修复后的内容被永久吞掉;
 * 同时逐条cache.put会让失败的解析部分生效(同文件半新半旧)
 */
public class SqlXmlHotReloadRetryTest {

	private static final String FILE_NAME = "hot_reload.sql.xml";

	private File xmlFile;
	private ConcurrentHashMap<String, Long> lastModifyMap;
	private ConcurrentHashMap<String, SqlToyConfig> cache;
	private List<Object> files;
	private long baseModified;

	@BeforeEach
	public void setUp() throws Exception {
		File dir = Files.createTempDirectory("sqltoy_hot_reload").toFile();
		xmlFile = new File(dir, FILE_NAME);
		lastModifyMap = new ConcurrentHashMap<String, Long>();
		cache = new ConcurrentHashMap<String, SqlToyConfig>();
		files = new ArrayList<Object>();
		files.add(xmlFile);
		baseModified = System.currentTimeMillis() - 60000;
	}

	private void write(String content, long modified) throws Exception {
		writeTo(xmlFile, content, modified);
	}

	private static void writeTo(File file, String content, long modified) throws Exception {
		Files.write(file.toPath(), content.getBytes(StandardCharsets.UTF_8));
		file.setLastModified(modified);
	}

	private static String xml(String body) {
		return "<?xml version=\"1.0\" encoding=\"utf-8\"?>\n<sqltoy xmlns=\"http://www.sagframe.com/schema/sqltoy\">\n"
				+ body + "\n</sqltoy>\n";
	}

	private static String oneSql(String id, String sqlText) {
		return "  <sql id=\"" + id + "\">\n    <value>\n    <![CDATA[\n" + sqlText + "\n]]>\n    </value>\n  </sql>";
	}

	private void parse() throws Exception {
		SqlXMLConfigParse.parseXML(files, lastModifyMap, cache, "UTF-8", "mysql");
	}

	@Test
	public void validFileParsesAndRecordsTimestamp() throws Exception {
		write(xml(oneSql("hot_sql", "select id from t_hot_v1")), baseModified);
		parse();
		assertTrue(cache.get("hot_sql").getSql().contains("t_hot_v1"));
		assertEquals(Long.valueOf(baseModified), lastModifyMap.get(xmlFile.getAbsolutePath()));
	}

	@Test
	public void failedParseKeepsOldTimestampSoSameTickRetryWorks() throws Exception {
		write(xml(oneSql("hot_sql", "select id from t_hot_v1")), baseModified);
		parse();
		long sameTick = baseModified + 5000;
		// 同一时间戳刻度内先保存了非法xml
		write("<sqltoy><sql id=\"hot_sql\">", sameTick);
		assertThrows(Exception.class, () -> parse());
		// 时间戳不得前进(否则下一轮被判定为"未变更")
		assertEquals(Long.valueOf(baseModified), lastModifyMap.get(xmlFile.getAbsolutePath()));
		// 同一刻度内再保存正确内容:修复前会因时间戳未变而被跳过,新内容永不生效
		write(xml(oneSql("hot_sql", "select id from t_hot_v2")), sameTick);
		parse();
		assertTrue(cache.get("hot_sql").getSql().contains("t_hot_v2"));
		assertEquals(Long.valueOf(sameTick), lastModifyMap.get(xmlFile.getAbsolutePath()));
	}

	@Test
	public void failedParseLeavesCacheUntouched() throws Exception {
		write(xml(oneSql("hot_a", "select a1") + "\n" + oneSql("hot_b", "select b1")), baseModified);
		parse();
		assertTrue(cache.get("hot_a").getSql().contains("a1"));
		assertTrue(cache.get("hot_b").getSql().contains("b1"));
		// 第一个sql改为新内容(旧实现会立即cache.put),第二个sql内容为空在元素循环内触发解析失败
		write(xml(oneSql("hot_a", "select a2") + "\n  <sql id=\"hot_b\"></sql>"), baseModified + 5000);
		assertThrows(Exception.class, () -> parse());
		// 修复前:hot_a已被写入新内容(半新半旧);修复后:两者都必须保持旧内容
		assertTrue(cache.get("hot_a").getSql().contains("a1"), "解析失败不得改动缓存,实际:" + cache.get("hot_a").getSql());
		assertTrue(cache.get("hot_b").getSql().contains("b1"));
		assertEquals(Long.valueOf(baseModified), lastModifyMap.get(xmlFile.getAbsolutePath()));
	}

	@Test
	public void sameFileNameInDifferentDirectoriesIsTrackedIndependently() throws Exception {
		// 另一个目录下的同名文件:修复前两者共用一条时间戳登记(键为文件名)
		File otherDir = Files.createTempDirectory("sqltoy_hot_reload_other").toFile();
		File otherFile = new File(otherDir, FILE_NAME);
		files.add(otherFile);
		// 关键:A的mtime更新,修复前它写入的时间戳会成为B的比较基准,导致B被判为"未变更"而跳过解析
		writeTo(xmlFile, xml(oneSql("hot_a", "select a1")), baseModified + 9000);
		writeTo(otherFile, xml(oneSql("hot_b", "select b1")), baseModified);
		parse();
		assertTrue(cache.get("hot_a").getSql().contains("a1"));
		assertTrue(cache.get("hot_b").getSql().contains("b1"), "同名文件不得因另一目录文件的较新时间戳而被跳过解析");
		// 同名文件必须各占一条登记(键为绝对路径)
		assertEquals(2, lastModifyMap.size(), "同名文件应各占一条登记,实际:" + lastModifyMap.keySet());
		// 只改动其中一个:另一个不得被牵连重解析
		writeTo(otherFile, xml(oneSql("hot_b", "select b2")), baseModified + 15000);
		parse();
		assertTrue(cache.get("hot_b").getSql().contains("b2"));
		assertTrue(cache.get("hot_a").getSql().contains("a1"));
	}
}
