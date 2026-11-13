package org.sagacity.sqltoy.utils;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.File;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Comparator;
import java.util.List;

import org.junit.jupiter.api.Test;

/**
 * 回归测试：copyFolder每个文件的流独立关闭(不再循环覆盖泄漏句柄)，
 * 目录内容(含子目录)完整复制；复制完成后源和目标文件可删除(Windows下泄漏的流会锁定文件导致删除失败)
 */
public class FileUtilCopyFolderTest {

	@Test
	public void copyFolderClosesEveryStreamAndCopiesAll() throws Exception {
		Path srcRoot = Files.createTempDirectory("sqltoy_copy_src");
		Path destRoot = Files.createTempDirectory("sqltoy_copy_dest");
		try {
			// 3个顶层文件+1个子目录文件,超过旧代码finally只关最后一对的场景
			Files.write(srcRoot.resolve("a.txt"), "content-a".getBytes(StandardCharsets.UTF_8));
			Files.write(srcRoot.resolve("b.txt"), "content-b".getBytes(StandardCharsets.UTF_8));
			Files.write(srcRoot.resolve("c.txt"), "content-c".getBytes(StandardCharsets.UTF_8));
			Path subDir = Files.createDirectory(srcRoot.resolve("sub"));
			Files.write(subDir.resolve("d.txt"), "content-d".getBytes(StandardCharsets.UTF_8));

			FileUtil.copyFolder(srcRoot.toString(), destRoot.toString());

			assertEquals("content-a", new String(Files.readAllBytes(destRoot.resolve("a.txt"))));
			assertEquals("content-b", new String(Files.readAllBytes(destRoot.resolve("b.txt"))));
			assertEquals("content-c", new String(Files.readAllBytes(destRoot.resolve("c.txt"))));
			assertEquals("content-d", new String(Files.readAllBytes(destRoot.resolve("sub").resolve("d.txt"))));

			// 删除目标树:Windows下若有输出流泄漏未关闭,文件被锁定删除会失败
			deleteTree(destRoot);
			assertTrue(!Files.exists(destRoot.resolve("a.txt")));
			// 源文件的输入流同样不能泄漏
			deleteTree(srcRoot);
			assertTrue(!Files.exists(srcRoot.resolve("a.txt")));
		} finally {
			deleteTreeQuietly(srcRoot);
			deleteTreeQuietly(destRoot);
		}
	}

	@Test
	public void getPathFilesStillCollectsFilteredFiles() throws Exception {
		Path root = Files.createTempDirectory("sqltoy_path_files");
		try {
			Files.write(root.resolve("x.sql"), "select 1".getBytes(StandardCharsets.UTF_8));
			Files.write(root.resolve("y.txt"), "text".getBytes(StandardCharsets.UTF_8));
			List<File> files = FileUtil.getPathFiles(root.toString(), new String[] { "sql" });
			assertEquals(1, files.size());
			assertEquals("x.sql", files.get(0).getName());
		} finally {
			deleteTreeQuietly(root);
		}
	}

	private static void deleteTree(Path root) throws Exception {
		List<Path> paths = Files.walk(root).sorted(Comparator.reverseOrder())
				.collect(java.util.stream.Collectors.toList());
		for (Path path : paths) {
			Files.delete(path);
		}
	}

	private static void deleteTreeQuietly(Path root) {
		try {
			if (Files.exists(root)) {
				deleteTree(root);
			}
		} catch (Exception ignore) {
		}
	}
}
