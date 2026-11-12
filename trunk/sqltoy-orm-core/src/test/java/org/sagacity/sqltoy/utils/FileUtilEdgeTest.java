package org.sagacity.sqltoy.utils;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.ByteArrayInputStream;
import java.io.File;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

/**
 * FileUtil 边缘场景测试
 */
public class FileUtilEdgeTest {

	@TempDir
	Path tmpDir;

	@Test
	public void formatPath() {
		assertEquals("a" + File.separator + "b" + File.separator + "c", FileUtil.formatPath("a\\b/c"));
		// 连续分隔符收敛为单个
		assertEquals("a" + File.separator + "b", FileUtil.formatPath("a\\\\//b"));
		assertNull(FileUtil.formatPath(null));
		assertEquals("", FileUtil.formatPath(""));
	}

	@Test
	public void isRootPath() {
		assertTrue(FileUtil.isRootPath("D:\\a"));
		assertTrue(FileUtil.isRootPath("d:/a"));
		assertTrue(FileUtil.isRootPath("/usr/local"));
		assertTrue(FileUtil.isRootPath("file:/home/a"));
		assertFalse(FileUtil.isRootPath("relative/path"));
		assertFalse(FileUtil.isRootPath(""));
		assertFalse(FileUtil.isRootPath(null));
		assertFalse(FileUtil.isRootPath("  "));
	}

	@Test
	public void linkPath() {
		assertEquals("a" + File.separator + "b", FileUtil.linkPath("a", "b"));
		// topPath已带尾部分隔符
		assertEquals("a" + File.separator + "b", FileUtil.linkPath("a/", "b"));
		assertEquals("a" + File.separator + "b", FileUtil.linkPath("a\\", "b"));
		// lowPath是绝对路径时直接返回
		assertEquals("D:\\x", FileUtil.linkPath("a", "D:\\x"));
		// 前导"/"按Unix根路径处理,Windows上也原样返回(isRootPath的设计行为)
		assertEquals("/usr", FileUtil.linkPath("a", "/usr"));
		// lowPath带前导"\\"分隔符被去除拼接
		assertEquals("a" + File.separator + "b", FileUtil.linkPath("a", "\\b"));
		// 两者均为空
		assertEquals("", FileUtil.linkPath("", ""));
	}

	@Test
	public void getParentPath() {
		// 原样返回输入中使用的分隔符(不做平台归一)
		assertEquals("a/b", FileUtil.getParentPath("a/b/c.txt"));
		assertEquals("a", FileUtil.getParentPath("a\\b.txt"));
		assertNull(FileUtil.getParentPath("abc.txt"));
	}

	/**
	 * 回归测试:getParentPath对null入参容错返回null
	 */
	@Test
	public void getParentPathNull() {
		assertNull(FileUtil.getParentPath(null));
	}

	@Test
	public void putStrAndReadRoundTrip(@TempDir Path dir) throws Exception {
		String file = dir.resolve("sub/nested.txt").toString();
		String content = "第一行\n第二行English123";
		FileUtil.putStrToFile(content, file, "UTF-8");
		// File重载走readAsBytes,字节精确
		assertEquals(content, FileUtil.readFileAsStr(new File(file), "UTF-8"));
		// Object重载经readLine重建,换行被规范化为平台分隔符(非字节精确,设计行为)
		String sep = System.lineSeparator();
		String normalized = content.replace("\n", sep);
		assertEquals(normalized, FileUtil.readFileAsStr((Object) file, "UTF-8"));
		// charset为空默认UTF-8
		assertEquals(content, FileUtil.readFileAsStr(new File(file), ""));
	}

	@Test
	public void readAsBytesNullAndMissing() {
		assertNull(FileUtil.readAsBytes(null));
		assertNull(FileUtil.readAsBytes("no/such/file_not_exist.txt"));
	}

	@Test
	public void putInputStreamToFileNestedDir(@TempDir Path dir) throws Exception {
		String file = dir.resolve("x/y/z.txt").toString();
		FileUtil.putInputStreamToFile(new ByteArrayInputStream("hello".getBytes(StandardCharsets.UTF_8)), file);
		assertEquals("hello", FileUtil.readFileAsStr(new File(file), "UTF-8"));
	}

	@Test
	public void putInputStreamToFileNullStream() throws Exception {
		// 流为null:不应创建出损坏文件之外的异常
		FileUtil.putInputStreamToFile(null, tmpDir.resolve("nullsrc.txt").toString());
		assertFalse(tmpDir.resolve("nullsrc.txt").toFile().exists() && FileUtil.readFileAsStr(tmpDir.resolve("nullsrc.txt").toFile(), "UTF-8") == null);
	}

	@Test
	public void copyFileMissingSource(@TempDir Path dir) {
		assertFalse(FileUtil.copyFile(dir.resolve("no_exist.bin").toString(), dir.resolve("target.bin").toString()));
	}

	@Test
	public void copyFileRoundTrip(@TempDir Path dir) throws Exception {
		Path src = dir.resolve("src.bin");
		Files.write(src, "copy content 中文".getBytes(StandardCharsets.UTF_8));
		String target = dir.resolve("deep/dir/target.bin").toString();
		assertTrue(FileUtil.copyFile(src.toString(), target));
		assertEquals("copy content 中文", FileUtil.readFileAsStr(new File(target), "UTF-8"));
	}

	@Test
	public void putBytesToFile(@TempDir Path dir) {
		byte[] data = { 1, 2, 3, -1 };
		String file = dir.resolve("bytes.bin").toString();
		FileUtil.putBytesToFile(data, file);
		assertTrue(java.util.Arrays.equals(data, FileUtil.readAsBytes(file)));
	}

	@Test
	public void getPathFilesWithFilters(@TempDir Path dir) throws Exception {
		Files.write(dir.resolve("a.sql"), "1".getBytes());
		Files.write(dir.resolve("b.txt"), "1".getBytes());
		Files.createDirectories(dir.resolve("sub"));
		Files.write(dir.resolve("sub/c.sql"), "1".getBytes());
		List fileList = FileUtil.getPathFiles(dir.toString(), new String[] { ".sql" });
		assertEquals(2, fileList.size());
		// filters为null返回全部文件
		List all = FileUtil.getPathFiles(dir.toString(), null);
		assertEquals(3, all.size());
		// 目录不存在返回空列表不抛异常
		assertTrue(FileUtil.getPathFiles("no_such_dir_path", null).isEmpty());
		assertNull(FileUtil.getPathFiles(null, null));
	}

	@Test
	public void matchAntPath(@TempDir Path dir) throws Exception {
		Files.write(dir.resolve("A.java"), "1".getBytes());
		Files.createDirectories(dir.resolve("org/sub"));
		Files.write(dir.resolve("org/sub/B.java"), "1".getBytes());
		Files.write(dir.resolve("org/sub/C.txt"), "1".getBytes());
		List<Path> matched = FileUtil.matchAntPath(dir, "**" + File.separator + "*.java");
		// 兼容Unix分隔符写法
		List<Path> matched2 = FileUtil.matchAntPath(dir, "**/*.java");
		int total = Math.max(matched.size(), matched2.size());
		assertEquals(2, total, "glob应匹配到2个java文件");
	}

	@Test
	public void createAndDelFile(@TempDir Path dir) throws Exception {
		String file = dir.resolve("created.txt").toString();
		FileUtil.createFile(file, null);
		assertTrue(new File(file).exists());
		// 写内容会覆盖
		FileUtil.createFile(file, "hello");
		assertEquals("hello", FileUtil.readFileAsStr(new File(file), "UTF-8").trim());
		assertTrue(FileUtil.delFile(file));
		// 删除不存在的文件返回false
		assertFalse(FileUtil.delFile(file));
	}

	/**
	 * 回归测试:delFile如实返回File.delete()的结果,删除失败(目录非空、文件被锁定)返回false
	 */
	@Test
	public void delFileReturnWhenDeleteFailed(@TempDir Path dir) throws Exception {
		Path subDir = dir.resolve("nondirty");
		Files.createDirectories(subDir);
		Files.write(subDir.resolve("inner.txt"), "1".getBytes());
		boolean result = FileUtil.delFile(subDir.toString());
		assertTrue(subDir.toFile().exists(), "非空目录不应被删除");
		assertFalse(result, "删除失败时应返回false");
	}

	/**
	 * 回归测试:rename如实返回renameTo结果,失败(如Windows下目标文件已存在)返回0
	 */
	@Test
	public void renameReturnWhenTargetExists(@TempDir Path dir) throws Exception {
		Path src = dir.resolve("src.txt");
		Files.write(src, "s".getBytes());
		Path target = dir.resolve("target.txt");
		Files.write(target, "t".getBytes());
		// Windows下目标文件已存在时renameTo失败
		int result = FileUtil.rename(src.toString(), target.toString());
		assertTrue(result != 1, "rename失败时不应返回1,实际返回:" + result);
		// 目标不存在时正常重命名返回1
		Path target2 = dir.resolve("target2.txt");
		assertEquals(1, FileUtil.rename(src.toString(), target2.toString()));
		assertFalse(src.toFile().exists());
		assertTrue(target2.toFile().exists());
	}

	@Test
	public void renameMissingFile() {
		assertEquals(-1, FileUtil.rename("no_such_file.txt", "x.txt"));
	}

	/**
	 * 回归测试:空文件的摘要为其内容对应的MD5值,而非空字符串
	 */
	@Test
	public void fileMessageDigestEmptyFile() throws Exception {
		Path empty = tmpDir.resolve("empty.txt");
		Files.write(empty, new byte[0]);
		assertEquals("d41d8cd98f00b204e9800998ecf8427e", FileUtil.getFileMessageDigest(empty.toString(), "MD5"));
	}

	@Test
	public void fileMessageDigest() throws Exception {
		Path file = dir("abc.txt");
		Files.write(file, "abc".getBytes(StandardCharsets.UTF_8));
		// MD5("abc")标准值
		assertEquals("900150983cd24fb0d6963f7d28e17f72", FileUtil.getFileMessageDigest(file.toString(), "MD5"));
		// 非法算法返回空串不抛异常
		assertEquals("", FileUtil.getFileMessageDigest(file.toString(), "NO_SUCH_ALGO"));
	}

	private Path dir(String name) {
		return tmpDir.resolve(name);
	}

	@Test
	public void existFile() throws Exception {
		assertFalse(FileUtil.existFile(null));
		assertFalse(FileUtil.existFile("no_such_file_anywhere.xyz"));
		Path file = tmpDir.resolve("exist.txt");
		Files.write(file, "1".getBytes());
		assertTrue(FileUtil.existFile(file.toString()));
		assertTrue(FileUtil.existFile(file.toFile()));
		// classpath:下的测试类路径资源(本测试类自身)
		assertTrue(FileUtil.existFile("classpath:org/sagacity/sqltoy/utils/FileUtilEdgeTest.class"));
	}

	@Test
	public void getFileInputStreamClasspath() throws Exception {
		InputStream is = FileUtil.getFileInputStream("classpath:org/sagacity/sqltoy/utils/FileUtilEdgeTest.class");
		assertTrue(is != null && is.available() > 0);
		// 带前导斜杠
		InputStream is2 = FileUtil.getFileInputStream("classpath:/org/sagacity/sqltoy/utils/FileUtilEdgeTest.class");
		assertTrue(is2 != null && is2.available() > 0);
		assertNull(FileUtil.getFileInputStream(null));
		assertNull(FileUtil.getFileInputStream("classpath:no_such_resource.xyz"));
	}

	@Test
	public void getFile() {
		assertEquals("D:\\a\\b", FileUtil.getFile("D:\\a\\b").getPath().replace("/", "\\"));
		// file:前缀
		assertTrue(FileUtil.getFile("file:D:/x").getPath().endsWith("D:" + File.separator + "x")
				|| FileUtil.getFile("file:D:/x").getPath().endsWith("D:/x"));
		assertNull(FileUtil.getFile(null));
		// 相对路径trim
		assertEquals("a.txt", FileUtil.getFile("  a.txt ").getPath());
	}

	@Test
	public void isPackage() {
		assertTrue(FileUtil.isPackage("classpath:org/sagacity/sqltoy"));
		assertTrue(FileUtil.isPackage("classpath*:org/sagacity/sqltoy"));
		assertFalse(FileUtil.isPackage("D:\\data\\dir"));
		// 不存在的相对路径按package处理
		assertTrue(FileUtil.isPackage("no/such/dir/path"));
	}

	@Test
	public void appendFile(@TempDir Path dir) throws Exception {
		// 三种追加方式均按UTF-8写入,中文可无损往返
		String file = dir.resolve("append.txt").toString();
		FileUtil.appendFileByStream(file, "a中");
		FileUtil.appendFileByStream(file, "b");
		assertEquals("a中b", FileUtil.readFileAsStr(new File(file), "UTF-8"));

		String file2 = dir.resolve("append2.txt").toString();
		FileUtil.appendFileByWriter(file2, "x中");
		FileUtil.appendFileByWriter(file2, "y");
		assertEquals("x中y", FileUtil.readFileAsStr(new File(file2), "UTF-8"));

		String file3 = dir.resolve("append3.txt").toString();
		// RandomAccess方式按UTF-8写入,中文不丢高位字节
		FileUtil.appendFileByRandomAccess(file3, "中a");
		FileUtil.appendFileByRandomAccess(file3, "文");
		assertEquals("中a文", FileUtil.readFileAsStr(new File(file3), "UTF-8"));

		// File对象重载
		String file4 = dir.resolve("append4.txt").toString();
		FileUtil.appendFileByStream(new File(file4), "z");
		assertEquals("z", FileUtil.readFileAsStr(new File(file4), "UTF-8"));
	}

	@Test
	public void copyAndMoveFolder(@TempDir Path dir) throws Exception {
		Path srcDir = dir.resolve("srcdir");
		Files.createDirectories(srcDir.resolve("child"));
		Files.write(srcDir.resolve("f1.txt"), "f1".getBytes(StandardCharsets.UTF_8));
		Files.write(srcDir.resolve("child/f2.txt"), "f2".getBytes(StandardCharsets.UTF_8));
		String target = dir.resolve("targetdir").toString();
		FileUtil.copyFolder(srcDir.toString(), target);
		assertEquals("f1", FileUtil.readFileAsStr(new File(target, "f1.txt"), "UTF-8"));
		assertEquals("f2", FileUtil.readFileAsStr(new File(target, "child/f2.txt"), "UTF-8"));

		String moved = dir.resolve("moveddir").toString();
		FileUtil.moveFolder(target, moved);
		assertFalse(new File(target).exists());
		assertEquals("f1", FileUtil.readFileAsStr(new File(moved, "f1.txt"), "UTF-8"));

		// moveFile保留原文件模式
		String mf = dir.resolve("moveSrc.txt").toString();
		Files.write(dir.resolve("moveSrc.txt"), "mv".getBytes());
		FileUtil.moveFile(mf, dir.resolve("moveDst.txt").toString(), false);
		assertTrue(new File(mf).exists());
		assertEquals("mv", FileUtil.readFileAsStr(new File(dir.resolve("moveDst.txt").toString()), "UTF-8"));
	}

	@Test
	public void delFolderAndAllFile(@TempDir Path dir) throws Exception {
		Path root = dir.resolve("delroot");
		Files.createDirectories(root.resolve("sub"));
		Files.write(root.resolve("a.txt"), "a".getBytes());
		Files.write(root.resolve("sub/b.txt"), "b".getBytes());
		FileUtil.delFolder(root.toString());
		assertFalse(root.toFile().exists());
		// 删除不存在的目录不抛异常
		FileUtil.delFolder(root.toString());
		assertFalse(FileUtil.delAllFile(root.toString()));
	}

	@Test
	public void deleteMatchedFile(@TempDir Path dir) throws Exception {
		Files.write(dir.resolve("del1.log"), "1".getBytes());
		Files.write(dir.resolve("keep.txt"), "1".getBytes());
		assertTrue(FileUtil.deleteMatchedFile(dir.toString(), new String[] { ".log" }));
		assertFalse(dir.resolve("del1.log").toFile().exists());
		assertTrue(dir.resolve("keep.txt").toFile().exists());
	}

	@Test
	public void decodePath() {
		assertEquals("a b/c", FileUtil.decodePath("a%20b/c"));
		// 中文直接透传
		assertEquals("中文", FileUtil.decodePath("中文"));
		assertEquals("", FileUtil.decodePath(""));
		assertNull(FileUtil.decodePath(null));
	}

	@Test
	public void skipPath() {
		// 以basePath为基准跳转:./表示当前目录
		String base = "D:" + File.separator + "a" + File.separator + "b" + File.separator + "c";
		assertEquals("D:" + File.separator + "a" + File.separator + "b" + File.separator + "c"
				+ File.separator + "file.sql", FileUtil.formatPath(FileUtil.skipPath(base, "./file.sql")));
		// ..表示上级目录
		assertEquals("D:" + File.separator + "a" + File.separator + "b" + File.separator + "file.sql",
				FileUtil.formatPath(FileUtil.skipPath(base, "../file.sql")));
	}

	@Test
	public void getJarPath() throws Exception {
		assertEquals("D:" + File.separator + "app" + File.separator + "x.jar",
				FileUtil.formatPath(FileUtil.getJarPath(new java.net.URL("file:/D:/app/x.jar"))));
		// 含感叹号截取jar部分
		String jar = FileUtil.getJarPath(new java.net.URL("file:/D:/app/x.jar!/org/A.class"));
		assertTrue(jar.replace("/", "\\").endsWith("x.jar"));
		// 含空格的路径解码
		String jar2 = FileUtil.getJarPath(new java.net.URL("file:/D:/my%20app/x.jar"));
		assertTrue(jar2.contains("my app"), "应解码为my app,实际:" + jar2);
	}

	@Test
	public void readLineAsStr(@TempDir Path dir) throws Exception {
		Path file = dir.resolve("lines.txt");
		Files.write(file, "l1\r\nl2".getBytes(StandardCharsets.UTF_8));
		String sep = System.lineSeparator();
		assertEquals("l1" + sep + "l2", FileUtil.readLineAsStr(file.toFile(), "UTF-8"));
		// 文件不存在返回null
		assertNull(FileUtil.readLineAsStr(dir.resolve("nope.txt").toFile(), "UTF-8"));
		assertNull(FileUtil.readLineAsStr(null, "UTF-8"));
	}

	@Test
	public void putFileInOutStreamNull(@TempDir Path dir) throws Exception {
		// null参数抛IllegalArgumentException
		try {
			FileUtil.putFileInOutStream(null, "x");
			org.junit.jupiter.api.Assertions.fail("应抛出IllegalArgumentException");
		} catch (IllegalArgumentException e) {
			// expected
		}
		java.io.OutputStream out = new java.io.ByteArrayOutputStream();
		try {
			FileUtil.putFileInOutStream(out, 12345);
			org.junit.jupiter.api.Assertions.fail("非法类型应抛出IllegalArgumentException");
		} catch (IllegalArgumentException e) {
			// expected
		}
	}
}
