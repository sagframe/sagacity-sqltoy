package org.sagacity.sqltoy.config;

import java.io.File;
import java.net.JarURLConnection;
import java.net.URI;
import java.net.URL;
import java.util.ArrayList;
import java.util.Enumeration;
import java.util.LinkedList;
import java.util.List;
import java.util.Queue;
import java.util.Set;
import java.util.Vector;
import java.util.concurrent.ConcurrentHashMap;
import java.util.jar.JarEntry;
import java.util.jar.JarFile;

import org.sagacity.sqltoy.utils.CollectionUtil;
import org.sagacity.sqltoy.utils.FileUtil;
import org.sagacity.sqltoy.utils.StringUtil;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * @project sagacity-sqltoy
 * @description 扫描classes目录以及jar包中的class文件；以及扫描sql.xml文件
 * @author zhongxuchen
 * @version v1.0,Date:2012-6-10
 * @modify {Date:2017-10-28,修改getResourceUrls方法,返回枚举数组,修复maven做单元测试时只检测testClass路径的问题}
 * @modify {Date:2019-09-23,剔除根据方言剔除非本方言sql文件的逻辑,实践证明这个功能价值很低}
 * @modify {Date:2020-03-13,调整sql加载策略,jar包中的优先加载,classes下面的加载顺序在jar后面,便于增量发版覆盖}
 * @modify {Date:2024-08-10,增加了文件路径存在空格等特殊符号的处理}
 * @modify {Date:2026-05-19,增加Ant风格路径匹配支持,支持classpath*:和**通配符}
 */
@SuppressWarnings({ "unchecked", "rawtypes" })
public class SqlResourceScanner {
	/**
	 * 定义日志
	 */
	protected final static Logger logger = LoggerFactory.getLogger(SqlResourceScanner.class);

	/**
	 * 默认的sql定义文件后缀名,便于区分和查找加载
	 */
	private static final String SQLTOY_SQL_FILE_SUFFIX = ".sql.xml";

	private static final String CLASSPATH_STAR = "classpath*:";
	private static final String CLASSPATH = "classpath:";
	private static final String JAR = "jar";
	private static final String FILE_FLAG = "file:";
	private static final String RESOURCE = "resource";

	private static final boolean IS_WINDOWS = System.getProperty("os.name").toLowerCase().contains("win");

	// 常见路径中的特殊字符
	private static final String[][] SPECIALCHARACTERS = new String[][] { { "%20", " " }, { "%25", "%" }, { "%23", "#" },
			{ "%5B", "[" }, { "%5D", "]" }, { "%2E", "." }, { "%2B", "+" }, { "%5C", "/" } };

	/**
	 * @todo 获取sqltoy配置的sql文件
	 * @param resourceDir
	 * @param mappingResources
	 * @return
	 * @throws Exception
	 */
	public static List getSqlResources(String resourceDir, List<String> mappingResources) throws Exception {
		List result = new ArrayList();
		// 判断文件重复的集合
		Set<String> globalNotRepeatDirs = ConcurrentHashMap.newKeySet();
		ClassLoader classLoader = Thread.currentThread().getContextClassLoader();
		if (StringUtil.isNotBlank(resourceDir) && !resourceDir.equalsIgnoreCase("none") && !resourceDir.equals("\'\'")
				&& !resourceDir.equals("\"\"")) {
			// 规范路径中的名称
			scanSqlResources(result, clearIrregularChar(resourceDir), globalNotRepeatDirs, classLoader);
			if (result.isEmpty()) {
				logger.warn("扫描resourceDir=[" + resourceDir + "]路径未加载到*.sql.xml文件,请参照下面的说明检查配置!\n"
						+ "resourceDir配置支持AntPath模式的路径匹配:1)**:0~n级路径;2)*:单级路径;3)?:单个字符匹配;4)路径可写可不写*.sql.xml\n"
						+ "1)默认补充*.sql.xml结尾:classpath:com/company/project等效于classpath:com/company/project/**/*.sql.xml\n"
						+ "2)多路径(逗号拼接):classpath:com/company/project1/**/sqlMapping,classpath:com/company/project2/modules/*/sqlMapping\n"
						+ "3)完整路径:classpath:com/company/project/modules\n" + "4)多级匹配:file:/root/project/**/sqlMapping\n"
						+ "5)单级匹配:classpath:com/company/project/*/sqlMapping\n"
						+ "6)单字符匹配:file:/root/project/?/sqlMapping");
			}
		}
		// 完整路线的sql文件
		scanMappingResources(result, mappingResources, globalNotRepeatDirs, classLoader);
		return result;
	}

	/**
	 * 扫描SQL资源文件，支持Ant风格路径匹配。
	 * 
	 * @param result      结果列表
	 * @param resourceDir 资源目录路径（支持逗号分隔的多个路径）
	 * @throws Exception 扫描异常
	 */
	public static void scanSqlResources(List result, String resourceDir, Set<String> globalNotRepeatDirs,
			ClassLoader classLoader) throws Exception {
		String[] dirSet = resourceDir.split("\\,");
		// 线程安全去重集合
		Set<String> notRepeatDirs = ConcurrentHashMap.newKeySet();
		for (String dir : dirSet) {
			String realRes = dir.trim();
			if (realRes.isEmpty()) {
				continue;
			}
			// 剔除file协议头
			if (realRes.startsWith(FILE_FLAG)) {
				realRes = realRes.substring(5);
			}
			// Windows统一去除开头多余斜杠
			if (IS_WINDOWS && realRes.startsWith("/")) {
				realRes = realRes.substring(1);
			}
			boolean isClasspathAll = realRes.toLowerCase().startsWith(CLASSPATH_STAR);
			// 排除classpath*的干扰
			String tmpResPath = isClasspathAll ? realRes.substring(CLASSPATH_STAR.length()) : realRes;
			boolean hasWildcard = tmpResPath.contains("*") || tmpResPath.contains("?");
			// 规避路径重复
			if (CollectionUtil.notContainsAdd(notRepeatDirs, realRes)) {
				// 存在通配符号
				if (hasWildcard) {
					scanWithAntPattern(result, realRes, globalNotRepeatDirs, classLoader);
				} else {
					// 本身路径就是一个完整的sql文件路径,走scanMappingResources
					if (realRes.toLowerCase().endsWith(SQLTOY_SQL_FILE_SUFFIX)) {
						List<String> mappingFile = new ArrayList<>();
						mappingFile.add(realRes);
						scanMappingResources(result, mappingFile, globalNotRepeatDirs, classLoader);
					} else {
						// 无通配符号且是一个路径,走路径递归查找方式
						scanTraditionalPath(result, realRes, globalNotRepeatDirs, classLoader);
					}
				}
			}
		}
	}

	/**
	 * 使用Ant风格模式扫描资源。
	 *
	 * @param result  结果列表
	 * @param pattern Ant风格路径模式
	 */
	private static void scanWithAntPattern(List result, String pattern, Set<String> notRepeatDirs,
			ClassLoader classLoader) {
		try {
			boolean isFileSystemPath = FileUtil.isRootPath(pattern);
			String antPattern = pattern;
			if (pattern.toLowerCase().startsWith(CLASSPATH_STAR)) {
				antPattern = pattern.substring(CLASSPATH_STAR.length());
			} else if (pattern.toLowerCase().startsWith(CLASSPATH)) {
				antPattern = pattern.substring(CLASSPATH.length());
			}
			if (antPattern.startsWith("/")) {
				antPattern = antPattern.substring(1);
			}
			antPattern = antPattern.replace("\\", "/");
			// 自动补全后缀匹配规则
			if (!antPattern.toLowerCase().endsWith(SQLTOY_SQL_FILE_SUFFIX)) {
				if (!antPattern.endsWith("/")) {
					antPattern = antPattern + "/";
				}
				antPattern = antPattern + "**/*" + SQLTOY_SQL_FILE_SUFFIX;
			}
			String rootPath = extractRootPath(antPattern);
			String[] allParts = antPattern.split("/");
			// 计算根路径有多少段，模式部分从根路径段数之后开始
			String[] rootParts = rootPath.isEmpty() ? new String[0] : rootPath.split("/");
			String[] patternParts = new String[allParts.length - rootParts.length];
			System.arraycopy(allParts, rootParts.length, patternParts, 0, patternParts.length);
			if (isFileSystemPath) {
				scanFileSystemWithPattern(result, rootPath, patternParts, notRepeatDirs);
			} else {
				scanClasspathWithPattern(result, rootPath, patternParts, notRepeatDirs, classLoader);
			}
		} catch (Exception e) {
			logger.error("Ant pattern scan error:{}", pattern, e);
		}
	}

	/**
	 * 扫描文件系统路径（支持Ant通配符）。
	 */
	private static void scanFileSystemWithPattern(List result, String rootPath, String[] patternParts,
			Set<String> notRepeatDirs) {
		String normalizedRoot = rootPath;
		File rootDir = getFile(normalizedRoot);
		if (!rootDir.exists()) {
			logger.debug("Root path does not exist: " + rootPath);
			return;
		}
		Queue<File> dirQueue = new LinkedList<>();
		dirQueue.offer(rootDir);
		while (!dirQueue.isEmpty()) {
			File currentDir = dirQueue.poll();
			File[] files = currentDir.listFiles();
			if (files == null) {
				continue;
			}
			for (File file : files) {
				if (file.isDirectory()) {
					dirQueue.offer(file);
				} else {
					if (isFileMatchPattern(rootDir, file, patternParts)) {
						String filePath = file.getAbsolutePath();
						if (file.getName().toLowerCase().endsWith(SQLTOY_SQL_FILE_SUFFIX)
								&& CollectionUtil.notContainsAdd(notRepeatDirs, filePath)) {
							result.add(file);
						}
					}
				}
			}
		}
	}

	/**
	 * 检查文件是否匹配Ant模式
	 */
	private static boolean isFileMatchPattern(File rootDir, File file, String[] patternParts) {
		String rootPath = rootDir.getAbsolutePath();
		String filePath = file.getAbsolutePath();
		if (!filePath.startsWith(rootPath)) {
			return false;
		}
		String relativePath = filePath.substring(rootPath.length());
		if (relativePath.startsWith(File.separator)) {
			relativePath = relativePath.substring(1);
		}
		String[] pathParts = relativePath.split("\\\\|/");
		return matchAntRecursive(pathParts, 0, patternParts, 0);
	}

	/**
	 * 扫描classpath路径（支持Ant通配符）
	 */
	private static void scanClasspathWithPattern(List result, String rootPath, String[] patternParts,
			Set<String> notRepeatDirs, ClassLoader classLoader) throws Exception {
		Enumeration<URL> roots = getClasspathResourceUrls(rootPath, classLoader);
		while (roots.hasMoreElements()) {
			URL rootUrl = roots.nextElement();
			String protocol = rootUrl.getProtocol();
			if (JAR.equals(protocol)) {
				scanJarWithPattern(rootUrl, rootPath, patternParts, result, notRepeatDirs);
			} else if (RESOURCE.equals(protocol)) {
				String path = new URI(rootUrl.toString()).getPath();
				if (path != null && path.toLowerCase().endsWith(SQLTOY_SQL_FILE_SUFFIX)
						&& CollectionUtil.notContainsAdd(notRepeatDirs, path)) {
					result.add(0, path);
				}
			} else {
				String filePath = new URI(rootUrl.toString()).getPath();
				if (filePath != null) {
					if (IS_WINDOWS && filePath.startsWith("/")) {
						filePath = filePath.substring(1);
					}
					scanFileSystemWithPattern(result, filePath, patternParts, notRepeatDirs);
				}
			}
		}
	}

	/**
	 * 扫描JAR包（支持Ant通配符）。
	 */
	private static void scanJarWithPattern(URL jarUrl, String rootPath, String[] patternParts, List result,
			Set<String> notRepeatDirs) throws Exception {
		JarURLConnection conn = (JarURLConnection) jarUrl.openConnection();
		String normalizedRoot = rootPath;
		if (!normalizedRoot.isEmpty() && !normalizedRoot.endsWith("/")) {
			normalizedRoot = normalizedRoot + "/";
		}
		try (JarFile jarFile = conn.getJarFile()) {
			Enumeration<JarEntry> entries = jarFile.entries();
			while (entries.hasMoreElements()) {
				JarEntry entry = entries.nextElement();
				if (entry.isDirectory()) {
					continue;
				}
				String entryPath = entry.getName();
				// 先检查是否在rootPath目录下
				if (!normalizedRoot.isEmpty() && !entryPath.startsWith(normalizedRoot)) {
					continue;
				}
				// 提取相对路径进行匹配
				String relativePath = normalizedRoot.isEmpty() ? entryPath
						: entryPath.substring(normalizedRoot.length());
				if (matchAntPattern(relativePath, patternParts)) {
					if (entryPath.toLowerCase().endsWith(SQLTOY_SQL_FILE_SUFFIX)
							&& CollectionUtil.notContainsAdd(notRepeatDirs, entryPath)) {
						// JAR资源优先加载
						result.add(0, entryPath);
					}
				}
			}
		}
	}

	/**
	 * 检查路径是否匹配Ant模式。
	 */
	private static boolean matchAntPattern(String path, String[] patternParts) {
		String[] pathParts = path.split("/");
		return matchAntRecursive(pathParts, 0, patternParts, 0);
	}

	/**
	 * 递归匹配Ant模式。
	 */
	private static boolean matchAntRecursive(String[] pathParts, int pathIndex, String[] patternParts,
			int patternIndex) {
		// 模式和路径都匹配完毕
		if (patternIndex == patternParts.length && pathIndex == pathParts.length) {
			return true;
		}
		// 模式用完但路径还有
		if (patternIndex >= patternParts.length) {
			return false;
		}
		String pattern = patternParts[patternIndex];
		// ** 匹配任意层级
		if ("**".equals(pattern)) {
			// 尝试匹配0到多个路径段
			for (int i = pathIndex; i <= pathParts.length; i++) {
				if (matchAntRecursive(pathParts, i, patternParts, patternIndex + 1)) {
					return true;
				}
			}
			return false;
		}
		// 路径用完但模式还有（且不是**）
		if (pathIndex >= pathParts.length) {
			return false;
		}
		// 匹配当前段
		if (matchPattern(pathParts[pathIndex], pattern)) {
			return matchAntRecursive(pathParts, pathIndex + 1, patternParts, patternIndex + 1);
		}
		return false;
	}

	/**
	 * 单段通配符匹配（支持*和?）。
	 */
	private static boolean matchPattern(String text, String pattern) {
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
		// 动态规划或贪心匹配
		int t = 0, p = 0;
		int starIdx = -1;
		int matchIdx = 0;
		while (t < text.length()) {
			if (p < pattern.length() && pattern.charAt(p) == '*') {
				starIdx = p;
				matchIdx = t;
				p++;
			} else if (p < pattern.length() && (pattern.charAt(p) == '?' || pattern.charAt(p) == text.charAt(t))) {
				p++;
				t++;
			} else if (starIdx != -1) {
				p = starIdx + 1;
				matchIdx++;
				t = matchIdx;
			} else {
				return false;
			}
		}
		while (p < pattern.length() && pattern.charAt(p) == '*') {
			p++;
		}
		return p == pattern.length();
	}

	/**
	 * 从Ant模式中提取根路径（通配符之前的部分）。
	 */
	private static String extractRootPath(String antPattern) {
		String[] parts = antPattern.split("/");
		StringBuilder root = new StringBuilder();
		for (String part : parts) {
			// 遇到通配符则停止
			if (part.contains("*") || part.contains("?")) {
				break;
			}
			if (root.length() > 0) {
				root.append("/");
			}
			root.append(part);
		}
		String result = root.toString();
		// Windows路径恢复（如 /D/path -> D:/path），仅Windows系统下处理
		if (IS_WINDOWS && result.length() >= 3 && result.startsWith("/") && Character.isLetter(result.charAt(1))
				&& result.charAt(2) == '/') {
			result = result.substring(1);
		}
		return result.isEmpty() ? "" : result;
	}

	/**
	 * 传统方式扫描无通配符路径。
	 */
	private static void scanTraditionalPath(List result, String resourceDir, Set<String> notRepeatDirs,
			ClassLoader classLoader) throws Exception {
		String realRes = resourceDir;
		boolean startClasspath = false;
		if (realRes.toLowerCase().startsWith(CLASSPATH_STAR)) {
			realRes = realRes.substring(11).trim();
			if (realRes.startsWith("/")) {
				realRes = realRes.substring(1);
			}
			startClasspath = true;
		} else if (realRes.toLowerCase().startsWith(CLASSPATH)) {
			realRes = realRes.substring(10).trim();
			if (realRes.startsWith("/")) {
				realRes = realRes.substring(1);
			}
			startClasspath = true;
		}
		if (CollectionUtil.notContainsAdd(notRepeatDirs, realRes)) {
			Enumeration<URL> urls = startClasspath ? getClasspathResourceUrls(realRes, classLoader)
					: getResourceUrls(realRes, classLoader);
			if (null != urls) {
				URL url;
				Enumeration<JarEntry> entries;
				JarEntry entry;
				String sqlFile;
				while (urls.hasMoreElements()) {
					url = urls.nextElement();
					if (url.getProtocol().equals(JAR)) {
						if (!realRes.isEmpty() && realRes.startsWith("/")) {
							realRes = realRes.substring(1);
						}
						// try-with-resources关闭JarFile,避免文件句柄泄漏(Windows下会锁定jar阻碍热部署)
						try (JarFile jar = ((JarURLConnection) url.openConnection()).getJarFile()) {
							entries = jar.entries();
							while (entries.hasMoreElements()) {
								entry = entries.nextElement();
								sqlFile = entry.getName();
								if (sqlFile.startsWith(realRes)
										&& sqlFile.toLowerCase().endsWith(SQLTOY_SQL_FILE_SUFFIX)
										&& !entry.isDirectory()
										&& CollectionUtil.notContainsAdd(notRepeatDirs, sqlFile)) {
									result.add(0, sqlFile);
								}
							}
						}
					} else if (url.getProtocol().equals(RESOURCE)) {
						if (realRes.toLowerCase().endsWith(SQLTOY_SQL_FILE_SUFFIX)
								&& CollectionUtil.notContainsAdd(notRepeatDirs, realRes)) {
							result.add(realRes);
						}
					} else {
						String filePath = new URI(url.toString()).getPath();
						if (IS_WINDOWS && filePath.startsWith("/")) {
							filePath = filePath.substring(1);
						}
						getPathFiles(new File(filePath), result, notRepeatDirs);
					}
				}
			}
		}
	}

	/**
	 * @todo 扫描解析指定的完整路径的sql.xml文件
	 * @param result
	 * @param mappingResources
	 * @throws Exception
	 */
	private static void scanMappingResources(List result, List<String> mappingResources,
			Set<String> globalNotRepeatDirs, ClassLoader classLoader) throws Exception {
		if (mappingResources == null || mappingResources.isEmpty()) {
			return;
		}
		String realRes;
		Enumeration<URL> urls;
		// 内部避免文件重复
		Set<String> notRepeatMappingResources = ConcurrentHashMap.newKeySet();
		// 具体的完整路径指定的.sql.xml文件
		boolean startClasspath;
		for (int i = 0; i < mappingResources.size(); i++) {
			realRes = mappingResources.get(i).trim();
			// 必须是以.sql.xml结尾的文件
			if (realRes.toLowerCase().endsWith(SQLTOY_SQL_FILE_SUFFIX)) {
				startClasspath = false;
				if (realRes.toLowerCase().startsWith(CLASSPATH_STAR)) {
					realRes = realRes.substring(11).trim();
					if (realRes.startsWith("/")) {
						realRes = realRes.substring(1);
					}
					startClasspath = true;
				} else if (realRes.toLowerCase().startsWith(CLASSPATH)) {
					realRes = realRes.substring(10).trim();
					if (realRes.startsWith("/")) {
						realRes = realRes.substring(1);
					}
					startClasspath = true;
				}
				// update 2025-11-19 增加路径重复判断
				if (CollectionUtil.notContainsAdd(notRepeatMappingResources, realRes)) {
					urls = startClasspath ? getClasspathResourceUrls(realRes, classLoader)
							: getResourceUrls(realRes, classLoader);
					processMappingResourcesUrls(result, realRes, urls, globalNotRepeatDirs);
				}
			}
		}
	}

	/**
	 * 处理单个完整路径的sql文件
	 * 
	 * @param result
	 * @param realRes
	 * @param urls
	 * @param notRepeatResources 用于去重的路径集合
	 * @throws Exception
	 */
	private static void processMappingResourcesUrls(List result, String realRes, Enumeration<URL> urls,
			Set<String> notRepeatResources) throws Exception {
		if (urls == null) {
			return;
		}
		URL url;
		File file;
		String normalizedRes = realRes;
		if (!normalizedRes.isEmpty() && normalizedRes.startsWith("/")) {
			normalizedRes = normalizedRes.substring(1);
		}
		while (urls.hasMoreElements()) {
			url = urls.nextElement();
			if (url.getProtocol().equals(JAR)) {
				if (normalizedRes.toLowerCase().endsWith(SQLTOY_SQL_FILE_SUFFIX)
						&& CollectionUtil.notContainsAdd(notRepeatResources, normalizedRes)) {
					// jar中的sql优先加载,从而确保直接放于classes目录下面的sql可以实现对之前的覆盖,便于项目增量发版管理
					result.add(0, normalizedRes);
				}
			} else if (url.getProtocol().equals(RESOURCE)) {
				if (normalizedRes.toLowerCase().endsWith(SQLTOY_SQL_FILE_SUFFIX)
						&& CollectionUtil.notContainsAdd(notRepeatResources, normalizedRes)) {
					result.add(normalizedRes);
				}
			} else {
				file = new File(url.toURI());
				String filePath = file.getAbsolutePath();
				if (file.getName().toLowerCase().endsWith(SQLTOY_SQL_FILE_SUFFIX)
						&& CollectionUtil.notContainsAdd(notRepeatResources, filePath)) {
					result.add(file);
				}
			}
		}
	}

	/**
	 * @todo 获取资源的URL
	 * @param resourcePath
	 * @return
	 * @throws Exception
	 */
	public static Enumeration<URL> getResourceUrls(String resourcePath, ClassLoader classLoader) throws Exception {
		Enumeration<URL> urls = null;
		if (null == resourcePath) {
			return urls;
		}
		String resource = resourcePath;
		// 剔除不必要的干扰
		if (resource.startsWith(FILE_FLAG)) {
			resource = resource.substring(5);
		}
		// Windows路径处理
		if (IS_WINDOWS && resource.startsWith("/")) {
			resource = resource.substring(1);
		}
		File file = getFile(resource);
		if (file != null && file.exists()) {
			Vector<URL> v = new Vector<URL>();
			v.add(file.toURI().toURL());
			urls = v.elements();
		} else {
			if (!resource.isEmpty() && resource.startsWith("/")) {
				resource = resource.substring(1);
			}
			urls = classLoader.getResources(resource);
		}
		return urls;
	}

	/**
	 * 针对文件系统,避免文件resourcePath中存在# + 等特殊字符，进行容错处理
	 * 
	 * @param resourcePath
	 * @return
	 */
	private static File getFile(String resourcePath) {
		if (null == resourcePath) {
			return null;
		}
		String resource = resourcePath;
		File file = new File(resource);
		if (file.exists()) {
			return file;
		}
		// 文件不存在,但存在%20 空格、%25 百分号的转义符号(适度兼容，路径中不要搞极端特殊的符号)
		String fileResource = resource;
		boolean hasSpecChar = false;
		for (String[] item : SPECIALCHARACTERS) {
			if (fileResource.contains(item[0])) {
				hasSpecChar = true;
				fileResource = fileResource.replace(item[0], item[1]);
			}
		}
		// 存在特殊字符，重新实例化文件
		if (hasSpecChar) {
			file = new File(fileResource);
		}
		if (file.exists()) {
			return file;
		}
		// 文件依旧不存在，用decode转特殊符号
		file = new File(FileUtil.decodePath(resource));
		return file;
	}

	/**
	 * classpath:com/xxx/*.sql.xml
	 * 
	 * @param resource
	 * @return
	 * @throws Exception
	 */
	public static Enumeration<URL> getClasspathResourceUrls(String resource, ClassLoader classLoader) throws Exception {
		Enumeration<URL> urls = null;
		if (null == resource) {
			return urls;
		}
		if (!resource.isEmpty() && resource.startsWith("/")) {
			resource = resource.substring(1);
		}
		return classLoader.getResources(resource);
	}

	/**
	 * @todo 递归获取文件夹下面的以sql.xml结尾的sql文件
	 * @param parentFile
	 * @param fileList
	 * @param notRepeatDirs 用于去重的路径集合
	 */
	private static void getPathFiles(File parentFile, List fileList, Set<String> notRepeatDirs) {
		if (parentFile == null || !parentFile.exists()) {
			return;
		}
		Queue<File> queue = new LinkedList<>();
		queue.offer(parentFile);
		while (!queue.isEmpty()) {
			File curr = queue.poll();
			if (curr.isDirectory()) {
				File[] files = curr.listFiles();
				if (files != null) {
					for (File f : files) {
						queue.offer(f);
					}
				}
			} else {
				if (curr.getName().toLowerCase().endsWith(SQLTOY_SQL_FILE_SUFFIX)) {
					String path = curr.getAbsolutePath();
					if (CollectionUtil.notContainsAdd(notRepeatDirs, path)) {
						fileList.add(curr);
					}
				}
			}
		}
	}

	/**
	 * 替换全角字符,统一多路径分割符号为逗号
	 * 
	 * @param resourcesDir
	 * @return
	 */
	private static String clearIrregularChar(String resourcesDir) {
		if (resourcesDir == null || resourcesDir.isEmpty()) {
			return resourcesDir;
		}
		return resourcesDir.replaceAll("\\；", ",").replaceAll("\\，", ",").replaceAll("\\;", ",");
	}
}