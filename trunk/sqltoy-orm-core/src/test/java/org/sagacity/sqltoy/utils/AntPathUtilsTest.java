package org.sagacity.sqltoy.utils;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.Random;

import org.junit.jupiter.api.Test;

/**
 * AntPathUtils 深度验证:
 * <li>与EntityScanner原jar匹配正则做差分(相同输入必须相同结果)</li>
 * <li>** 零层、段内通配、大小写无关路径等边界</li>
 * <li>动态SQL/实体扫描共用的分段语义</li>
 * </ul>
 */
public class AntPathUtilsTest {

	@Test
	public void zeroLevelStarStarMatches() {
		assertTrue(AntPathUtils.match("com/foo/X.class", "com/foo/**/*.class"), "**应匹配0层");
		assertTrue(AntPathUtils.match("com/foo/a/X.class", "com/foo/**/*.class"));
		assertTrue(AntPathUtils.match("com/foo/a/b/X.class", "com/foo/**/*.class"));
		assertTrue(AntPathUtils.match("entity/X.class", "**/entity/*.class"));
		assertTrue(AntPathUtils.match("a/entity/X.class", "**/entity/*.class"));
		assertTrue(AntPathUtils.match("X.class", "**/*.class"));
		assertFalse(AntPathUtils.match("entity/X.class", "**/other/*.class"));
	}

	@Test
	public void segmentWildcardsDoNotCrossDirectories() {
		assertTrue(AntPathUtils.match("com/foo/X.class", "com/*/*.class"));
		assertFalse(AntPathUtils.match("com/foo/bar/X.class", "com/*/*.class"), "*不应跨目录");
		assertTrue(AntPathUtils.match("com/foo1/X.class", "com/foo?/*.class"));
		assertFalse(AntPathUtils.match("com/foo12/X.class", "com/foo?/*.class"), "?只匹配单字符");
	}

	@Test
	public void caseSensitivePathSegments() {
		assertFalse(AntPathUtils.match("com/Foo/X.class", "com/foo/*.class"));
		assertTrue(AntPathUtils.match("com/Foo/X.class", "com/Foo/*.class"));
	}

	@Test
	public void differentialAgainstLegacyJarRegex() {
		String[] packageParts = { "com", "foo", "**", "*", "entity", "x" };
		String[] pathParts = { "com", "foo", "a", "b", "entity", "x", "X.class", "Other.class" };
		Random random = new Random(20261116L);
		int tightened = 0;
		for (int i = 0; i < 5000; i++) {
			StringBuilder packagePattern = new StringBuilder();
			int partSize = 1 + random.nextInt(4);
			for (int p = 0; p < partSize; p++) {
				if (p > 0) {
					packagePattern.append('.');
				}
				packagePattern.append(packageParts[random.nextInt(packageParts.length)]);
			}
			boolean recursive = random.nextBoolean();
			String fullPattern = packagePattern.toString().replace('.', '/') + (recursive ? "/**/*.class" : "/*.class");
			String[] patternParts = fullPattern.split("/");
			StringBuilder path = new StringBuilder();
			int pathSize = 1 + random.nextInt(5);
			for (int p = 0; p < pathSize; p++) {
				if (p > 0) {
					path.append('/');
				}
				path.append(pathParts[random.nextInt(pathParts.length)]);
			}
			boolean legacy = legacyMatchJarPath(path.toString(), fullPattern);
			boolean actual = AntPathUtils.match(path.toString(), patternParts);
			// 新匹配器只允许比旧正则更严格(旧正则对"*段紧邻/**/"存在过度匹配),绝不能更宽松
			assertTrue(!actual || legacy, "新匹配器出现了旧实现不支持的匹配,模式:" + fullPattern + " 路径:" + path);
			if (legacy && !actual) {
				tightened++;
				assertTrue(fullPattern.contains("*/**/"),
						"仅允许'*段紧跟/**/'这一旧正则缺陷场景被收紧,模式:" + fullPattern + " 路径:" + path);
			}
		}
		assertTrue(tightened < 500, "收紧场景数量异常:" + tightened);
	}

	/**
	 * 真实包模式(实体扫描实际输入的形态)必须与旧实现完全一致
	 */
	@Test
	public void realisticPatternsMatchLegacyExactly() {
		String[] realisticPatterns = { "com/foo/**/*.class", "com/foo/**/entity/**/*.class",
				"com/foo/*/entity/**/*.class", "com/foo/entity/**/*.class", "com/foo/*.class",
				"com/foo/**/entity/*.class" };
		String[] paths = { "com/foo/X.class", "com/foo/entity/X.class", "com/foo/a/entity/X.class",
				"com/foo/a/b/entity/X.class", "com/foo/a/X.class", "com/foo/a/b/X.class", "com/foo/entity/a/X.class" };
		for (String pattern : realisticPatterns) {
			for (String path : paths) {
				assertEquals(legacyMatchJarPath(path, pattern), AntPathUtils.match(path, pattern.split("/")),
						"模式:" + pattern + " 路径:" + path);
			}
		}
	}

	/**
	 * 已知且有意的收紧:旧jar正则把"单星号段紧跟双星号段"当成可空层级,
	 * 目录扫描(PathMatcher)并不这样认为;统一后按Ant语义处理,与目录侧一致
	 */
	@Test
	public void starSegmentBeforeGlobstarFollowsAntSemantics() {
		assertFalse(AntPathUtils.match("com/foo/X.class", "com/foo/*/**/*.class"));
		assertTrue(AntPathUtils.match("com/foo/a/X.class", "com/foo/*/**/*.class"));
		assertTrue(AntPathUtils.match("com/foo/a/b/X.class", "com/foo/*/**/*.class"));
	}

	/**
	 * 复刻EntityScanner改造前的jar匹配正则,作为差分参照
	 */
	private static boolean legacyMatchJarPath(String path, String globPattern) {
		String regex = globPattern.replace("**", "<<DS>>").replace("/<<DS>>/", "<<PATH_SEG>>")
				.replace("/<<DS>>", "<<PATH_END>>").replace("<<DS>>/", "<<PATH_START>>").replace(".", "\\.")
				.replace("*", "[^/]*").replace("?", ".").replace("<<DS>>", "[^/]*")
				.replace("<<PATH_SEG>>", "(/[^/]*)*").replace("<<PATH_END>>", "(/[^/]*)*")
				.replace("<<PATH_START>>", "([^/]*/)*");
		return path.matches(regex);
	}
}
