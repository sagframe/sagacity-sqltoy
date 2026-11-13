package org.sagacity.sqltoy.config;

import java.io.File;
import java.net.JarURLConnection;
import java.net.URI;
import java.net.URL;
import java.net.URLClassLoader;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Enumeration;
import java.util.LinkedHashSet;
import java.util.LinkedList;
import java.util.List;
import java.util.Locale;
import java.util.Queue;
import java.util.Set;
import java.util.Vector;
import java.util.concurrent.ConcurrentHashMap;
import java.util.jar.JarEntry;
import java.util.jar.JarFile;

import org.sagacity.sqltoy.utils.AntPathUtils;
import org.sagacity.sqltoy.utils.CollectionUtil;
import org.sagacity.sqltoy.utils.FileUtil;
import org.sagacity.sqltoy.utils.StringUtil;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * @project sagacity-sqltoy
 * @description 扫描classes目录以及jar包中的class文件；以及扫描sql.xml文件
 * @author zhongxuchen
 * @version v1.0,Date:2012-06-10
 * @modify Date:2017-10-28
 *         修改getResourceUrls方法,返回枚举数组,修复maven做单元测试时只检测testClass路径的问题
 * @modify Date:2019-09-23 剔除根据方言剔除非本方言sql文件的逻辑,实践证明这个功能价值很低
 * @modify Date:2020-03-13 调整sql加载策略,jar包中的优先加载,classes下面的加载顺序在jar后面,便于增量发版覆盖
 * @modify Date:2024-08-10 增加了文件路径存在空格等特殊符号的处理
 * @modify Date:2026-05-19 增加Ant风格路径匹配支持,支持classpath*:和**通配符
 * @modify Date:2026-11-11 jar内的sql资源保留物理URL(扫描结果不再只存逻辑路径),解析时按该URL开流,
 *         避免classes与jar同名时TCCL总是读到classes、jar独有sqlId静默缺失
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

	private static final boolean IS_WINDOWS = System.getProperty("os.name").toLowerCase(Locale.ROOT).contains("win");

	// 常见路径中的特殊字符
	private static final String[][] SPECIALCHARACTERS = new String[][] { { "%20", " " }, { "%25", "%" }, { "%23", "#" },
			{ "%5B", "[" }, { "%5D", "]" }, { "%2E", "." }, { "%2B", "+" }, { "%5C", "/" } };

	/**
	 * 获取sqltoy配置的sql文件
	 * 
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
				logger.warn(
						"no *.sql.xml files were loaded under resourceDir=[{}], please check the configuration according to the instructions below!\n"
								+ "resourceDir supports AntPath style path matching: 1)**:matches 0~n levels of path; 2)*:matches single level path; 3)?:matches a single character; 4)the *.sql.xml suffix can be omitted\n"
								+ "1)*.sql.xml suffix is appended by default: classpath:com/company/project is equivalent to classpath:com/company/project/**/*.sql.xml\n"
								+ "2)multiple paths (joined by comma): classpath:com/company/project1/**/sqlMapping,classpath:com/company/project2/modules/*/sqlMapping\n"
								+ "3)full path: classpath:com/company/project/modules\n"
								+ "4)multi-level matching: file:/root/project/**/sqlMapping\n"
								+ "5)single-level matching: classpath:com/company/project/*/sqlMapping\n"
								+ "6)single character matching: file:/root/project/?/sqlMapping",
						resourceDir);
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
			boolean isClasspathAll = realRes.toLowerCase(Locale.ROOT).startsWith(CLASSPATH_STAR);
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
					if (realRes.toLowerCase(Locale.ROOT).endsWith(SQLTOY_SQL_FILE_SUFFIX)) {
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
			if (pattern.toLowerCase(Locale.ROOT).startsWith(CLASSPATH_STAR)) {
				antPattern = pattern.substring(CLASSPATH_STAR.length());
			} else if (pattern.toLowerCase(Locale.ROOT).startsWith(CLASSPATH)) {
				antPattern = pattern.substring(CLASSPATH.length());
			}
			if (antPattern.startsWith("/")) {
				antPattern = antPattern.substring(1);
			}
			antPattern = antPattern.replace("\\", "/");
			// 自动补全后缀匹配规则
			if (!antPattern.toLowerCase(Locale.ROOT).endsWith(SQLTOY_SQL_FILE_SUFFIX)) {
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
			logger.debug("root path does not exist: {}", rootPath);
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
						if (file.getName().toLowerCase(Locale.ROOT).endsWith(SQLTOY_SQL_FILE_SUFFIX)
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
		return AntPathUtils.match(relativePath.replace('\\', '/'), patternParts);
	}

	/**
	 * 扫描classpath路径（支持Ant通配符）
	 */
	private static void scanClasspathWithPattern(List result, String rootPath, String[] patternParts,
			Set<String> notRepeatDirs, ClassLoader classLoader) throws Exception {
		// update 2026-11-11 空根classpath*:不能只靠getResources(""):普通URLClassLoader下它通常只返回
		// classes目录而不返回jar根,导致classpath*:**/*.sql.xml静默漏掉依赖jar中的sql资源
		Enumeration<URL> roots = rootPath.isEmpty() ? getClasspathRootUrls(classLoader)
				: getClasspathResourceUrls(rootPath, classLoader);
		while (roots.hasMoreElements()) {
			URL rootUrl = roots.nextElement();
			String protocol = rootUrl.getProtocol();
			if (JAR.equals(protocol)) {
				scanJarWithPattern(rootUrl, rootPath, patternParts, result, notRepeatDirs);
			} else if (RESOURCE.equals(protocol)) {
				String path = new URI(rootUrl.toString()).getPath();
				if (path != null && path.toLowerCase(Locale.ROOT).endsWith(SQLTOY_SQL_FILE_SUFFIX)
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
		// update 2026-11-11 嵌套jar(Spring Boot)的JarFile由加载器缓存共享,主动关闭会让后续类/资源读取
		// 报"zip file closed";普通jar关闭前禁用共享缓存,close安全释放句柄(Windows热部署不锁定jar)
		boolean nestedJar = isNestedJarUrl(jarUrl);
		if (!nestedJar) {
			conn.setUseCaches(false);
		}
		JarFile jarFile = conn.getJarFile();
		try {
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
				// update 2026-11-11 统一使用AntPathUtils,与实体扫描共用同一套**语义
				if (AntPathUtils.match(relativePath, patternParts)) {
					if (entryPath.toLowerCase(Locale.ROOT).endsWith(SQLTOY_SQL_FILE_SUFFIX)) {
						// JAR资源优先加载
						// update 2026-11-11 按物理URL去重:不同jar的同名entry都是独立资源,不能按逻辑路径只留首个
						URL entryUrl = buildJarEntryUrl(jarUrl, entryPath);
						if (CollectionUtil.notContainsAdd(notRepeatDirs, entryUrl.toExternalForm())) {
							result.add(0, entryUrl);
						}
					}
				}
			}
		} finally {
			if (!nestedJar) {
				jarFile.close();
			}
		}
	}

	/**
	 * @param url jar资源URL
	 * @return 是否为嵌套jar(Spring Boot 3.0/3.1多个"!/"或3.2+的nested:协议);嵌套jar的JarFile由加载器缓存持有
	 */
	static boolean isNestedJarUrl(URL url) {
		String external = url.toExternalForm();
		return external.contains("nested:") || external.indexOf("!/") != external.lastIndexOf("!/");
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
		if (realRes.toLowerCase(Locale.ROOT).startsWith(CLASSPATH_STAR)) {
			realRes = realRes.substring(11).trim();
			if (realRes.startsWith("/")) {
				realRes = realRes.substring(1);
			}
			startClasspath = true;
		} else if (realRes.toLowerCase(Locale.ROOT).startsWith(CLASSPATH)) {
			realRes = realRes.substring(10).trim();
			if (realRes.startsWith("/")) {
				realRes = realRes.substring(1);
			}
			startClasspath = true;
		}
		if (CollectionUtil.notContainsAdd(notRepeatDirs, realRes)) {
			Enumeration<URL> urls = startClasspath
					? (realRes.isEmpty() ? getClasspathRootUrls(classLoader) : getClasspathResourceUrls(realRes, classLoader))
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
						// update 2026-11-11 与Ant分支统一:嵌套jar(Spring Boot)的JarFile由加载器缓存共享,主动关闭会让后续读取
						// 报"zip file closed";普通jar关闭前禁用共享缓存,close安全释放句柄(Windows热部署不锁定jar)
						JarURLConnection jarConn = (JarURLConnection) url.openConnection();
						boolean nestedJar = isNestedJarUrl(url);
						if (!nestedJar) {
							jarConn.setUseCaches(false);
						}
						JarFile jar = jarConn.getJarFile();
						try {
							// update 2026-11-11 非空目录前缀必须带目录边界:startsWith("com/app/sql")会误扫
							// com/app/sql2、com/app/sqlX.sql.xml等兄弟路径,对齐Ant分支的root+"/"语义
							String jarPrefix = realRes;
							if (!jarPrefix.isEmpty() && !jarPrefix.endsWith("/")) {
								jarPrefix = jarPrefix + "/";
							}
							entries = jar.entries();
							while (entries.hasMoreElements()) {
								entry = entries.nextElement();
								sqlFile = entry.getName();
								if ((realRes.isEmpty() || sqlFile.startsWith(jarPrefix) || sqlFile.equals(realRes))
										&& sqlFile.toLowerCase(Locale.ROOT).endsWith(SQLTOY_SQL_FILE_SUFFIX)
										&& !entry.isDirectory()) {
									// update 2026-11-11 按物理URL去重:不同jar的同名entry都要保留
									URL entryUrl = buildJarEntryUrl(url, sqlFile);
									if (CollectionUtil.notContainsAdd(notRepeatDirs, entryUrl.toExternalForm())) {
										result.add(0, entryUrl);
									}
								}
							}
						} finally {
							if (!nestedJar) {
								jar.close();
							}
						}
					} else if (url.getProtocol().equals(RESOURCE)) {
						if (realRes.toLowerCase(Locale.ROOT).endsWith(SQLTOY_SQL_FILE_SUFFIX)
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
	 * 扫描解析指定的完整路径的sql.xml文件
	 * 
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
			if (realRes.toLowerCase(Locale.ROOT).endsWith(SQLTOY_SQL_FILE_SUFFIX)) {
				startClasspath = false;
				if (realRes.toLowerCase(Locale.ROOT).startsWith(CLASSPATH_STAR)) {
					realRes = realRes.substring(11).trim();
					if (realRes.startsWith("/")) {
						realRes = realRes.substring(1);
					}
					startClasspath = true;
				} else if (realRes.toLowerCase(Locale.ROOT).startsWith(CLASSPATH)) {
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
				if (normalizedRes.toLowerCase(Locale.ROOT).endsWith(SQLTOY_SQL_FILE_SUFFIX)) {
					// jar中的sql优先加载,从而确保直接放于classes目录下面的sql可以实现对之前的覆盖,便于项目增量发版管理
					// update 2026-11-11 按物理URL去重:不同jar的同名entry都要保留,相同sqlId仍由breakWhenSqlRepeat决定报错或覆盖
					URL entryUrl = buildJarEntryUrl(url, normalizedRes);
					if (CollectionUtil.notContainsAdd(notRepeatResources, entryUrl.toExternalForm())) {
						result.add(0, entryUrl);
					}
				}
			} else if (url.getProtocol().equals(RESOURCE)) {
				if (normalizedRes.toLowerCase(Locale.ROOT).endsWith(SQLTOY_SQL_FILE_SUFFIX)
						&& CollectionUtil.notContainsAdd(notRepeatResources, normalizedRes)) {
					result.add(normalizedRes);
				}
			} else {
				file = new File(url.toURI());
				String filePath = file.getAbsolutePath();
				if (file.getName().toLowerCase(Locale.ROOT).endsWith(SQLTOY_SQL_FILE_SUFFIX)
						&& CollectionUtil.notContainsAdd(notRepeatResources, filePath)) {
					result.add(file);
				}
			}
		}
	}

	/**
	 * 获取资源的URL
	 * 
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
	 * 空根classpath*:的类路径根发现:getResources("")在普通URLClassLoader下通常只返回classes目录,
	 * 不返回jar根;这里补充URLClassLoader层级中的jar/file根,避免空根Ant模式漏掉依赖jar中的sql资源
	 */
	private static Enumeration<URL> getClasspathRootUrls(ClassLoader classLoader) throws Exception {
		LinkedHashSet<String> rootKeys = new LinkedHashSet<String>();
		List<URL> roots = new ArrayList<URL>();
		addRootUrl(roots, rootKeys, classLoader.getResources(""));
		boolean hasUrlClassLoader = false;
		boolean reachesSystemClassLoader = false;
		ClassLoader systemClassLoader = ClassLoader.getSystemClassLoader();
		for (ClassLoader loader = classLoader; loader != null; loader = loader.getParent()) {
			if (loader instanceof URLClassLoader) {
				hasUrlClassLoader = true;
				for (URL url : ((URLClassLoader) loader).getURLs()) {
					URL root = toClasspathRootUrl(url);
					if (root != null) {
						addRootUrl(roots, rootKeys, root);
					}
				}
			}
			if (loader == systemClassLoader) {
				reachesSystemClassLoader = true;
			}
		}
		// update 2026-11-11 JDK9+应用类加载器不再是URLClassLoader(getResources("")也只返回目录):
		// 加载器层级无法枚举URL根、或委派到系统应用类加载器时,补充java.class.path;
		// 纯隔离加载器(未委派系统应用类加载器)不混入JVM全局classpath
		if (!hasUrlClassLoader || reachesSystemClassLoader) {
			addSystemClassPathRoots(roots, rootKeys);
		}
		return Collections.enumeration(roots);
	}

	/**
	 * 补充JVM启动参数中的classpath根(java.class.path):JDK9+的应用类加载器不是URLClassLoader,
	 * 仅靠getResources("")与URLClassLoader层级无法发现依赖jar
	 * 
	 * @param roots
	 * @param rootKeys
	 */
	private static void addSystemClassPathRoots(List<URL> roots, Set<String> rootKeys) {
		String classPath = System.getProperty("java.class.path");
		if (classPath == null || classPath.isEmpty()) {
			return;
		}
		for (String item : classPath.split(File.pathSeparator)) {
			String entry = item.trim();
			if (entry.isEmpty()) {
				continue;
			}
			// -cp "lib/*" 形态:展开目录下的jar/zip
			if (entry.endsWith("*")) {
				File dir = new File(entry.substring(0, entry.length() - 1));
				File[] children = dir.listFiles();
				if (children == null) {
					continue;
				}
				for (File child : children) {
					String name = child.getName().toLowerCase(Locale.ROOT);
					if ((name.endsWith(".jar") || name.endsWith(".zip")) && !coveredByNestedRoot(roots, child)) {
						addRootUrl(roots, rootKeys, toFileRootUrl(child));
					}
				}
				continue;
			}
			File entryFile = new File(entry);
			// update 2026-11-12 Boot fat-jar的外层jar已被嵌套根覆盖时不再补外层根(见coveredByNestedRoot)
			if (!entryFile.isDirectory() && coveredByNestedRoot(roots, entryFile)) {
				continue;
			}
			addRootUrl(roots, rootKeys, toFileRootUrl(entryFile));
		}
	}

	/**
	 * update 2026-11-12 判断jar是否已被嵌套jar根覆盖(Spring Boot fat-jar):Boot的LaunchedClassLoader
	 * 已提供jar:nested:/app.jar/!BOOT-INF/classes!/(3.2+)或jar:file:/app.jar!/BOOT-INF/classes!/
	 * (2.x/3.0-3.1)形态的内层根,若再从java.class.path补外层app.jar根,同一SQL会以两种URL形态各命中
	 * 一次(按URL字符串去重失效),breakWhenSqlRepeat默认true时判重复sqlId直接阻断启动
	 *
	 * @param roots   已收集的类路径根(含加载器层级提供的嵌套根)
	 * @param jarFile java.class.path中的jar文件
	 * @return true表示该jar的内容已经由嵌套根承载,不应再补外层根
	 */
	private static boolean coveredByNestedRoot(List<URL> roots, File jarFile) {
		String fileUrl;
		try {
			fileUrl = jarFile.toURI().toURL().toExternalForm();
		} catch (Exception e) {
			return false;
		}
		// update 2026-11-13 按jar绝对路径+嵌套分隔符"/!"比对,不依赖nested:后是否带前导斜杠
		// (Windows盘符下真实LaunchedClassLoader可能产生nested:/C:/或nested:C:/两种写法),
		// 原nestedPath字符串拼接对前导斜杠敏感会漏配,致外层jar重复补根
		String jarPath = jarFile.getAbsolutePath().replace('\\', '/');
		for (URL root : roots) {
			if (!isNestedJarUrl(root)) {
				continue;
			}
			String external = root.toExternalForm();
			// update 2026-11-12 评审R4:仅classes内层根才代表外层jar内容已被完整承载;
			// 若加载器只暴露BOOT-INF/lib/*嵌套根,外层根仍须保留以发现BOOT-INF/classes资源
			// update 2026-11-13 兼容两代classes内层根形态:Boot 2.x/3.0为"classes!/",
			// Boot 3.2+嵌套entry名须以/结尾,真实形态为"classes/!/",漏配会让外层jar重复补根、
			// 同一sqlId两种URL各命中一次,breakWhenSqlRepeat默认true时阻断启动(真实LaunchedClassLoader实证)
			if (!external.contains("classes!/") && !external.contains("classes/!/")) {
				continue;
			}
			// 经典形态jar:file:/path/app.jar!/BOOT-INF/classes!/ 或 嵌套形态jar:nested:[/]path/app.jar/!BOOT-INF/classes/!/
			if (external.contains(fileUrl + "!/") || external.contains(jarPath + "/!")) {
				return true;
			}
		}
		return false;
	}

	/**
	 * 文件路径转类路径根:目录保持file URL,jar/zip转为jar:file:...!/ 根
	 * 
	 * @param file
	 * @return
	 */
	private static URL toFileRootUrl(File file) {
		try {
			if (file == null || !file.exists()) {
				return null;
			}
			if (file.isDirectory()) {
				return file.toURI().toURL();
			}
			return new URL("jar:" + file.toURI().toURL().toExternalForm() + "!/");
		} catch (Exception e) {
			logger.debug("skip classpath root: {}", file, e);
			return null;
		}
	}

	private static void addRootUrl(List<URL> roots, Set<String> rootKeys, Enumeration<URL> urls) throws Exception {
		while (urls.hasMoreElements()) {
			addRootUrl(roots, rootKeys, urls.nextElement());
		}
	}

	private static void addRootUrl(List<URL> roots, Set<String> rootKeys, URL url) {
		if (url != null && rootKeys.add(url.toExternalForm())) {
			roots.add(url);
		}
	}

	private static URL toClasspathRootUrl(URL url) throws Exception {
		String protocol = url.getProtocol();
		if ("file".equals(protocol)) {
			File file = new File(url.toURI());
			if (!file.exists()) {
				return null;
			}
			if (file.isDirectory()) {
				return url;
			}
			return new URL("jar:" + url.toExternalForm() + "!/");
		}
		if (JAR.equals(protocol)) {
			String external = url.toExternalForm();
			int markIndex = external.lastIndexOf("!/");
			return new URL((markIndex > -1) ? external.substring(0, markIndex + 2) : external + "!/");
		}
		return url;
	}

	/**
	 * 递归获取文件夹下面的以sql.xml结尾的sql文件
	 * 
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
				if (curr.getName().toLowerCase(Locale.ROOT).endsWith(SQLTOY_SQL_FILE_SUFFIX)) {
					String path = curr.getAbsolutePath();
					if (CollectionUtil.notContainsAdd(notRepeatDirs, path)) {
						fileList.add(curr);
					}
				}
			}
		}
	}

	/**
	 * 由jar资源URL与entry名称构造精确的物理资源URL(jar:file:/x.jar!/entry), 兼容Spring
	 * Boot嵌套jar(jar:file:/app.jar!/BOOT-INF/classes!/entry)
	 * 
	 * @param jarUrl    扫描时命中的jar资源URL(含entry或目录)
	 * @param entryName jar内entry名称
	 * @return
	 * @throws Exception
	 */
	private static URL buildJarEntryUrl(URL jarUrl, String entryName) throws Exception {
		String url = jarUrl.toExternalForm();
		// 以最后一个"!/"为界截取到jar根(嵌套jar为内层jar根)
		int markIndex = url.lastIndexOf("!/");
		String root = (markIndex > -1) ? url.substring(0, markIndex + 2) : url + "!/";
		// update 2026-11-11 entry名按URL路径规则转义:含#时会被当作fragment截断,导致读取到错误entry
		return new URL(root.concat(encodeJarEntryName(entryName)));
	}

	/**
	 * jar entry名转URL路径片段:按URI路径规则转义(空格/%/#/?/非ASCII),JarURLConnection读取时会解码还原为真实entry名
	 * 
	 * @param entryName
	 * @return
	 */
	private static String encodeJarEntryName(String entryName) {
		try {
			// 多参URI构造器按"已解码路径"处理:转义非法字符且保留"/",并正确处理Unicode/代理对
			return new URI(null, null, entryName, null).getRawPath();
		} catch (Exception e) {
			logger.debug("failed to encode jar entry name: {}", entryName, e);
			return entryName;
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