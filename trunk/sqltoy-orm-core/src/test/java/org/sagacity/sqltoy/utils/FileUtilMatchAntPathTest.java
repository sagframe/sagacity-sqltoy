package org.sagacity.sqltoy.utils;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Comparator;
import java.util.List;
import java.util.stream.Collectors;

import org.junit.jupiter.api.Test;

/**
 * 回归测试：matchAntPath的Files.walk Stream必须关闭(修复前目录句柄泄漏,
 * Windows下锁定根目录导致无法删除);ant匹配行为不变
 */
public class FileUtilMatchAntPathTest {

	@Test
	public void antMatchClosesWalkStreamAndMatchesCorrectly() throws Exception {
		Path root = Files.createTempDirectory("sqltoy_ant");
		Path sub = Files.createDirectory(root.resolve("sub"));
		Files.write(root.resolve("a.txt"), "a".getBytes(StandardCharsets.UTF_8));
		Files.write(root.resolve("b.sql"), "b".getBytes(StandardCharsets.UTF_8));
		Files.write(sub.resolve("c.txt"), "c".getBytes(StandardCharsets.UTF_8));
		try {
			List<Path> matched = FileUtil.matchAntPath(root, "**/*.txt");
			assertEquals(2, matched.size());
			// 匹配后walk的目录句柄应已释放:根目录可删除(修复前Windows下句柄泄漏删除失败)
			List<Path> paths = Files.walk(root).sorted(Comparator.reverseOrder()).collect(Collectors.toList());
			for (Path path : paths) {
				Files.delete(path);
			}
			assertTrue(!Files.exists(root), "根目录应可删除,walk句柄未释放会锁定目录");
		} finally {
			if (Files.exists(root)) {
				Files.walk(root).sorted(Comparator.reverseOrder()).forEach(p -> {
					try {
						Files.delete(p);
					} catch (Exception ignore) {
					}
				});
			}
		}
	}
}
