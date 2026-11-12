package org.sagacity.sqltoy.utils;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.ByteArrayInputStream;
import java.io.File;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

/**
 * FileUtil写入类方法改为返回boolean后的语义回归:写入失败必须返回false(原返回void时调用方
 * 无从得知数据未落盘),成功返回true;同时保持"不抛异常"的既有契约(见FileUtilEdgeTest)
 */
public class FileUtilWriteResultTest {

	@Test
	public void putBytesToFileReturnsTrue(@TempDir Path dir) {
		byte[] data = { 1, 2, 3, -1 };
		String file = dir.resolve("ok.bin").toString();
		assertTrue(FileUtil.putBytesToFile(data, file));
		assertTrue(java.util.Arrays.equals(data, FileUtil.readAsBytes(file)));
	}

	/**
	 * 目标路径的父级是一个已存在的文件:createFolder与FileOutputStream均失败,不得静默成功
	 */
	@Test
	public void putBytesToFileReturnsFalseWhenTargetUnwritable(@TempDir Path dir) throws Exception {
		Path blocker = dir.resolve("blocker.txt");
		Files.write(blocker, "x".getBytes(StandardCharsets.UTF_8));
		String target = blocker.resolve("child.bin").toString();
		assertFalse(FileUtil.putBytesToFile(new byte[] { 1 }, target));
		assertFalse(new File(target).exists());
	}

	@Test
	public void putInputStreamToFileReturnsTrue(@TempDir Path dir) throws Exception {
		String file = dir.resolve("nest/ok.txt").toString();
		assertTrue(FileUtil.putInputStreamToFile(new ByteArrayInputStream("hello".getBytes(StandardCharsets.UTF_8)), file));
		assertEquals("hello", FileUtil.readFileAsStr(new File(file), "UTF-8"));
	}

	@Test
	public void putInputStreamToFileReturnsFalseOnNullStream(@TempDir Path dir) {
		// 流为null:读取阶段抛NPE被捕获,方法不得抛异常且如实返回false
		assertFalse(FileUtil.putInputStreamToFile(null, dir.resolve("nullsrc.txt").toString()));
	}

	@Test
	public void putInputStreamToFileReturnsFalseWhenTargetUnwritable(@TempDir Path dir) throws Exception {
		Path blocker = dir.resolve("blocker2.txt");
		Files.write(blocker, "x".getBytes(StandardCharsets.UTF_8));
		assertFalse(FileUtil.putInputStreamToFile(new ByteArrayInputStream(new byte[] { 1 }),
				blocker.resolve("child.txt").toString()));
	}
}
