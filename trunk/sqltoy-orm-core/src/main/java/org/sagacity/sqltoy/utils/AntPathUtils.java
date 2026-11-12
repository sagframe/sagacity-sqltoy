package org.sagacity.sqltoy.utils;

/**
 * @project sagacity-sqltoy
 * @description Ant风格路径匹配工具,供sql资源扫描(目录/jar)与实体扫描共用同一套语义
 *              <li>**: 匹配0~n层目录(即 com/foo/**&#47;*.class 也匹配
 *              com/foo/X.class)</li>
 *              <li>*: 匹配单层目录/文件名内的0~n个字符</li>
 *              <li>?: 匹配单层目录/文件名内的单个字符</li>
 * @author zhongxuchen
 * @version v1.0,Date:2026-11-11
 */
public class AntPathUtils {

	private AntPathUtils() {
	}

	/**
	 * 路径与分段后的Ant模式匹配
	 * 
	 * @param path         以"/"分隔的路径(jar entry名称或相对路径)
	 * @param patternParts 以"/"拆分后的模式段
	 * @return
	 */
	public static boolean match(String path, String[] patternParts) {
		if (path == null || patternParts == null) {
			return false;
		}
		return match(path.split("/"), 0, patternParts, 0);
	}

	/**
	 * 路径与完整Ant模式匹配
	 * 
	 * @param path
	 * @param antPattern
	 * @return
	 */
	public static boolean match(String path, String antPattern) {
		if (path == null || antPattern == null) {
			return false;
		}
		return match(path, antPattern.split("/"));
	}

	private static boolean match(String[] pathParts, int pathIndex, String[] patternParts, int patternIndex) {
		// 模式和路径都匹配完毕
		if (patternIndex == patternParts.length && pathIndex == pathParts.length) {
			return true;
		}
		// 模式用完但路径还有
		if (patternIndex >= patternParts.length) {
			return false;
		}
		String pattern = patternParts[patternIndex];
		// ** 匹配0到多个路径段
		if ("**".equals(pattern)) {
			for (int i = pathIndex; i <= pathParts.length; i++) {
				if (match(pathParts, i, patternParts, patternIndex + 1)) {
					return true;
				}
			}
			return false;
		}
		// 路径用完但模式还有(且不是**)
		if (pathIndex >= pathParts.length) {
			return false;
		}
		// 匹配当前段
		if (matchSegment(pathParts[pathIndex], pattern)) {
			return match(pathParts, pathIndex + 1, patternParts, patternIndex + 1);
		}
		return false;
	}

	/**
	 * 单段通配符匹配(支持*和?)
	 */
	private static boolean matchSegment(String text, String pattern) {
		if (pattern == null || text == null) {
			return false;
		}
		if (pattern.isEmpty()) {
			return text.isEmpty();
		}
		if ("*".equals(pattern)) {
			return true;
		}
		if (!pattern.contains("*") && !pattern.contains("?")) {
			return text.equals(pattern);
		}
		// 贪心+回溯匹配
		int textIndex = 0, patternIndex = 0;
		int starIndex = -1;
		int matchIndex = 0;
		while (textIndex < text.length()) {
			if (patternIndex < pattern.length() && pattern.charAt(patternIndex) == '*') {
				starIndex = patternIndex;
				matchIndex = textIndex;
				patternIndex++;
			} else if (patternIndex < pattern.length() && (pattern.charAt(patternIndex) == '?'
					|| pattern.charAt(patternIndex) == text.charAt(textIndex))) {
				patternIndex++;
				textIndex++;
			} else if (starIndex != -1) {
				patternIndex = starIndex + 1;
				matchIndex++;
				textIndex = matchIndex;
			} else {
				return false;
			}
		}
		while (patternIndex < pattern.length() && pattern.charAt(patternIndex) == '*') {
			patternIndex++;
		}
		return patternIndex == pattern.length();
	}
}
