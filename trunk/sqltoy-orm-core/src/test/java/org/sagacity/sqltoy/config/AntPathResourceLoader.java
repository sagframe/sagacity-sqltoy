package org.sagacity.sqltoy.config;

import java.io.File;
import java.io.IOException;
import java.net.JarURLConnection;
import java.net.URL;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.Comparator;
import java.util.Enumeration;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.jar.JarEntry;
import java.util.jar.JarFile;
import java.util.stream.Collectors;

import org.sagacity.sqltoy.config.model.Resource;
import org.sagacity.sqltoy.config.model.ResourceType;
import org.sagacity.sqltoy.utils.FileUtil;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Ant风格路径资源加载器，用于扫描classpath下的资源文件。
 * 
 * <p>
 * 支持特性：
 * <ul>
 * <li>支持classpath和classpath*前缀，classpath*会扫描所有JAR包</li>
 * <li>支持Ant风格通配符：* (匹配任意字符)、? (匹配单个字符)、** (匹配多级目录)</li>
 * <li>区分本地文件系统资源和JAR包内资源，便于对本地资源进行文件变更监听</li>
 * <li>支持缓存优化，避免重复解析路径模式</li>
 * <li>支持文件系统绝对路径，如：D:\sqlMapping\** \*.sql.xml 或 /home/user/sqls/**
 * /*.sql.xml</li>
 * <li>支持file:前缀路径，如：file:D:/sqlMapping/** /*.sql.xml</li>
 * <li>自动处理URL编码的路径（空白被转义为%20等场景）</li>
 * </ul>
 * 
 * <p>
 * 使用示例：
 * 
 * <pre>{@code
 * // 加载所有匹配的资源
 * List<Resource> resources = AntPathResourceLoader.load("classpath*:com/example/** /*.sql.xml");
 * 
 * // 按来源分组加载
 * LoadResult result = AntPathResourceLoader.loadWithResult("classpath:config/** /*.xml");
 * List<Resource> fileResources = result.getFileResources(); // 本地文件
 * List<Resource> jarResources = result.getJarResources(); // JAR包内资源
 * 
 * // 加载文件系统绝对路径
 * List<Resource> fsResources = AntPathResourceLoader.load("D:/sqlMapping/** /*.sql.xml");
 * List<Resource> fsResources2 = AntPathResourceLoader.load("file:/home/user/sqls/** /*.sql.xml");
 * }</pre>
 * 
 * @author sagacity
 * @see Resource
 * @see LoadResult
 */
public class AntPathResourceLoader {

	/** 默认文件后缀，用于sqltoy的SQL XML文件 */
	private static final String DEFAULT_FILE_SUFFIX = "*.sql.xml";

	/** 路径模式缓存，避免重复解析相同的Ant路径模式 */
	private static final ConcurrentHashMap<String, String[]> PATTERN_CACHE = new ConcurrentHashMap<>();

	/** 缓存的classpath根路径列表 */
	private static volatile List<URL> cachedRoots = null;

	// 常见路径中的特殊字符
	private static final String[][] SPECIALCHARACTERS = new String[][] { { "%20", " " }, { "%25", "%" }, { "%23", "#" },
			{ "%5B", "[" }, { "%5D", "]" }, { "%2E", "." } };

	protected final static Logger logger = LoggerFactory.getLogger(ScanEntityAndSqlResource.class);

	/**
	 * 扫描结果容器，按资源来源分组存储扫描结果。
	 * 
	 * <p>
	 * 提供便捷方法获取本地文件资源、JAR包资源或合并后的所有资源。
	 */
	public static class LoadResult {
		/** 本地文件系统资源列表 */
		private final List<Resource> fileResources;
		/** JAR包内资源列表 */
		private final List<Resource> jarResources;

		/**
		 * 构造扫描结果。
		 * 
		 * @param fileResources 本地文件系统资源列表
		 * @param jarResources  JAR包内资源列表
		 */
		public LoadResult(List<Resource> fileResources, List<Resource> jarResources) {
			this.fileResources = fileResources;
			this.jarResources = jarResources;
		}

		/**
		 * 获取本地文件系统资源列表。
		 * 
		 * @return 本地文件资源列表，可监听文件变更
		 */
		public List<Resource> getFileResources() {
			return fileResources;
		}

		/**
		 * 获取JAR包内资源列表。
		 * 
		 * @return JAR包内资源列表
		 */
		public List<Resource> getJarResources() {
			return jarResources;
		}

		/**
		 * 获取所有资源，合并本地文件和JAR包资源后按路径排序。
		 * 
		 * @return 合并排序后的所有资源列表
		 */
		public List<Resource> getAllResources() {
			List<Resource> all = new ArrayList<>(fileResources.size() + jarResources.size());
			all.addAll(fileResources);
			all.addAll(jarResources);
			all.sort(Comparator.comparing(Resource::getPath));
			return all;
		}

		/**
		 * 判断是否存在本地文件资源。
		 * 
		 * @return 存在本地文件资源返回true
		 */
		public boolean hasFileResources() {
			return !fileResources.isEmpty();
		}

		/**
		 * 判断是否存在JAR包资源。
		 * 
		 * @return 存在JAR包资源返回true
		 */
		public boolean hasJarResources() {
			return !jarResources.isEmpty();
		}
	}

	/**
	 * 使用默认文件后缀加载资源。
	 * 
	 * @param patterns Ant风格路径模式数组
	 * @return 匹配的所有资源列表
	 */
	public static List<Resource> load(String... patterns) {
		return load(DEFAULT_FILE_SUFFIX, patterns);
	}

	/**
	 * 加载指定后缀的资源文件。
	 * 
	 * @param fileSuffix 文件后缀，如 "*.sql.xml"
	 * @param patterns   Ant风格路径模式数组
	 * @return 匹配的所有资源列表
	 */
	public static List<Resource> load(String fileSuffix, String... patterns) {
		return loadWithResult(fileSuffix, patterns).getAllResources();
	}

	/**
	 * 使用默认文件后缀加载资源并按来源分组返回。
	 * 
	 * @param patterns Ant风格路径模式数组
	 * @return 按来源分组的扫描结果
	 */
	public static LoadResult loadWithResult(String... patterns) {
		return loadWithResult(DEFAULT_FILE_SUFFIX, patterns);
	}

	/**
	 * 加载资源并按来源分组返回。
	 * 
	 * <p>
	 * 支持路径前缀：
	 * <ul>
	 * <li>classpath: 仅扫描第一个匹配的classpath根目录</li>
	 * <li>classpath*: 扫描所有classpath根目录（包括所有JAR包）</li>
	 * </ul>
	 * 
	 * @param fileSuffix 文件后缀，如 "*.sql.xml"
	 * @param patterns   Ant风格路径模式数组
	 * @return 按来源分组的扫描结果
	 */
	public static LoadResult loadWithResult(String fileSuffix, String... patterns) {
		if (patterns == null || patterns.length == 0) {
			return new LoadResult(Collections.emptyList(), Collections.emptyList());
		}

		Map<String, Resource> fileMap = new LinkedHashMap<>();
		Map<String, Resource> jarMap = new LinkedHashMap<>();
		ClassLoader loader = Thread.currentThread().getContextClassLoader();
		List<URL> roots = getRoots(loader);

		for (String pattern : patterns) {
			if (pattern == null || pattern.isBlank())
				continue;

			// 检查是否为文件系统绝对路径
			if (FileUtil.isRootPath(pattern)) {
				List<Resource> found = scanFileSystemPath(pattern, fileSuffix);
				for (Resource r : found) {
					fileMap.putIfAbsent(r.getPath(), r);
				}
				continue;
			}

			boolean scanAllJars = pattern.startsWith("classpath*:");
			String antPattern = pattern;

			if (pattern.startsWith("classpath*:")) {
				antPattern = pattern.substring("classpath*:".length());
			} else if (pattern.startsWith("classpath:")) {
				antPattern = pattern.substring("classpath:".length());
			}

			if (!antPattern.endsWith(fileSuffix)) {
				if (!antPattern.endsWith("/")) {
					antPattern = antPattern + "/";
				}
				antPattern = antPattern + "**/" + fileSuffix;
			}

			String[] patternParts = PATTERN_CACHE.computeIfAbsent(antPattern, p -> p.replaceFirst("^/", "").split("/"));

			try {
				for (URL root : roots) {
					String protocol = root.getProtocol();

					if ("file".equals(protocol)) {
						List<Resource> found = scanFile(root, antPattern, patternParts);
						for (Resource r : found) {
							fileMap.putIfAbsent(r.getPath(), r);
						}
					} else if ("jar".equals(protocol)) {
						List<Resource> found = scanJar(root, antPattern, patternParts);
						for (Resource r : found) {
							jarMap.putIfAbsent(r.getPath(), r);
						}
					} else {
						// 兼容 resource、bundle、vfs 等其他协议
						List<Resource> found = scanGenericUrl(root, antPattern, patternParts);
						for (Resource r : found) {
							fileMap.putIfAbsent(r.getPath(), r);
						}
					}

					// classpath: 模式下，如果本地文件已找到则不再扫描JAR
					if (!scanAllJars && !fileMap.isEmpty()) {
						break;
					}
				}
			} catch (Exception e) {
				e.printStackTrace();
				logger.error("Error scanning pattern: " + pattern);
			}
		}

		List<Resource> fileResources = fileMap.values().stream().sorted(Comparator.comparing(Resource::getPath))
				.collect(Collectors.toList());
		List<Resource> jarResources = jarMap.values().stream().sorted(Comparator.comparing(Resource::getPath))
				.collect(Collectors.toList());

		return new LoadResult(fileResources, jarResources);
	}

	/**
	 * 仅加载本地文件系统资源，用于文件监听场景。
	 * 
	 * @param patterns Ant风格路径模式数组
	 * @return 本地文件资源列表
	 */
	public static List<Resource> loadLocalFiles(String... patterns) {
		return loadLocalFiles(DEFAULT_FILE_SUFFIX, patterns);
	}

	/**
	 * 仅加载本地文件系统资源，用于文件监听场景。
	 * 
	 * @param fileSuffix 文件后缀，如 "*.sql.xml"
	 * @param patterns   Ant风格路径模式数组
	 * @return 本地文件资源列表
	 */
	public static List<Resource> loadLocalFiles(String fileSuffix, String... patterns) {
		return loadWithResult(fileSuffix, patterns).getFileResources();
	}

	/**
	 * 获取classpath根路径列表，使用双重检查锁定的懒加载方式。
	 * 
	 * @param loader 类加载器
	 * @return classpath根URL列表
	 */
	private static List<URL> getRoots(ClassLoader loader) {
		if (cachedRoots != null) {
			return cachedRoots;
		}
		synchronized (AntPathResourceLoader.class) {
			if (cachedRoots != null) {
				return cachedRoots;
			}
			List<URL> roots = new ArrayList<>();
			try {
				Enumeration<URL> urls = loader.getResources("");
				while (urls.hasMoreElements()) {
					roots.add(urls.nextElement());
				}
			} catch (IOException e) {
				e.printStackTrace();
				logger.error("Failed to get classpath roots", e);
			}
			cachedRoots = roots;
			return roots;
		}
	}

	/**
	 * 清除缓存，用于重新扫描资源。
	 */
	public static void clearCache() {
		cachedRoots = null;
		PATTERN_CACHE.clear();
	}

	/**
	 * 扫描本地文件系统目录。
	 * 
	 * @param rootUrl      根目录URL
	 * @param pattern      Ant路径模式
	 * @param patternParts 预解析的路径模式分段
	 * @return 匹配的资源列表
	 */
	private static List<Resource> scanFile(URL rootUrl, String pattern, String[] patternParts) {
		List<Resource> list = new ArrayList<>();
		try {
			Path root = Paths.get(rootUrl.toURI());

			try (var stream = Files.walk(root, Integer.MAX_VALUE)) {
				stream.filter(Files::isRegularFile).forEach(p -> {
					try {
						if (Files.size(p) <= 0) {
							return;
						}
					} catch (IOException e) {
						return;
					}

					String relPath = root.relativize(p).toString().replace("\\", "/");
					if (matchCached(relPath, patternParts)) {
						try {
							list.add(new Resource(relPath, p.toUri().toURL(), ResourceType.FILE, p));
						} catch (Exception e) {
							logger.error("Failed to create resource for: " + p, e);
						}
					}
				});
			}
		} catch (Exception e) {
			logger.error("Error scanning file system: " + rootUrl, e);
		}
		return list;
	}

	/**
	 * 扫描JAR包内资源。
	 * 
	 * @param jarUrl       JAR包URL
	 * @param pattern      Ant路径模式
	 * @param patternParts 预解析的路径模式分段
	 * @return 匹配的资源列表
	 */
	private static List<Resource> scanJar(URL jarUrl, String pattern, String[] patternParts) {
		List<Resource> list = new ArrayList<>();
		try {
			JarURLConnection conn = (JarURLConnection) jarUrl.openConnection();
			try (JarFile jarFile = conn.getJarFile()) {
				String jarFilePath = conn.getJarFileURL().getFile();

				Enumeration<JarEntry> entries = jarFile.entries();
				while (entries.hasMoreElements()) {
					JarEntry entry = entries.nextElement();
					if (entry.isDirectory())
						continue;

					String path = entry.getName();
					if (matchCached(path, patternParts)) {
						try {
							String jarEntryUrl = "jar:file:" + jarFilePath + "!/" + path;
							list.add(new Resource(path, new URL(jarEntryUrl), ResourceType.JAR));
						} catch (Exception e) {
							logger.error("Failed to create resource for jar entry: " + path, e);
						}
					}
				}
			}
		} catch (Exception e) {
			logger.error("Error scanning jar: " + jarUrl, e);
		}
		return list;
	}

	/**
	 * 扫描通用URL资源（兼容resource、bundle、vfs等多种协议）。
	 * 
	 * <p>
	 * 支持协议：
	 * <ul>
	 * <li>resource - WebLogic/WebSphere等应用服务器</li>
	 * <li>bundle - OSGi环境（Equinox、Felix）</li>
	 * <li>vfs - JBoss/WildFly虚拟文件系统</li>
	 * <li>其他未知协议 - 尝试通过URL连接获取内容</li>
	 * </ul>
	 * 
	 * @param url          资源URL
	 * @param pattern      Ant路径模式
	 * @param patternParts 预解析的路径模式分段
	 * @return 匹配的资源列表
	 */
	private static List<Resource> scanGenericUrl(URL url, String pattern, String[] patternParts) {
		List<Resource> list = new ArrayList<>();
		try {
			String protocol = url.getProtocol();
			String path = url.getPath();

			// 处理 vfs 协议（JBoss/WildFly）
			if ("vfs".equals(protocol)) {
				Object content = url.openConnection().getContent();
				if (content != null) {
					// 尝试通过反射获取虚拟文件
					try {
						Class<?> vfsFileClass = Class.forName("org.jboss.vfs.VirtualFile");
						if (vfsFileClass.isInstance(content)) {
							java.lang.reflect.Method getPhysicalFile = vfsFileClass.getMethod("getPhysicalFile");
							File physicalFile = (File) getPhysicalFile.invoke(content);
							if (physicalFile != null && physicalFile.exists()) {
								if (physicalFile.isDirectory()) {
									scanFileRecursive(physicalFile, physicalFile, patternParts, list);
								} else if (matchCached(path, patternParts)) {
									list.add(new Resource(path, url, ResourceType.FILE, physicalFile.toPath()));
								}
							}
						}
					} catch (ClassNotFoundException | NoSuchMethodException e) {
						// VFS类不可用，尝试其他方式
						if (matchCached(path, patternParts)) {
							list.add(new Resource(path, url, ResourceType.FILE));
						}
					}
				}
			}
			// 处理 bundle 协议（OSGi）
			else if ("bundle".equals(protocol)) {
				scanBundle(url, patternParts, list);
			}
			// 处理 resource 及其他协议
			else {
				if (matchCached(path, patternParts)) {
					// 尝试判断是文件还是JAR资源
					ResourceType type = ResourceType.FILE;
					try {
						String externalForm = url.toExternalForm();
						if (externalForm.contains(".jar!") || externalForm.contains(".zip!")) {
							type = ResourceType.JAR;
						}
					} catch (Exception ignored) {
					}
					list.add(new Resource(path, url, type));
				}
			}
		} catch (Exception e) {
			logger.debug("Error scanning generic url: " + url + ", error: " + e.getMessage());
		}
		return list;
	}

	/**
	 * 扫描OSGi bundle内的资源。
	 * 
	 * <p>
	 * 通过反射调用OSGi Bundle API遍历bundle内的资源，支持Equinox和Felix等OSGi容器。
	 * 
	 * @param bundleUrl    bundle协议URL
	 * @param patternParts 路径模式分段
	 * @param result       结果列表
	 */
	private static void scanBundle(URL bundleUrl, String[] patternParts, List<Resource> result) {
		try {
			String path = bundleUrl.getPath();
			// bundle URL格式: bundle://bundleId/path 或 bundleentry://bundleId/path
			// 提取基础路径用于遍历
			String basePath = path;
			if (basePath.startsWith("/")) {
				basePath = basePath.substring(1);
			}
			// 获取路径的根目录
			int slashIdx = basePath.indexOf('/');
			if (slashIdx > 0) {
				basePath = basePath.substring(0, slashIdx + 1);
			}

			// 尝试通过反射获取Bundle对象并遍历资源
			try {
				// 方式1: 通过BundleWiring获取资源（OSGi R4.2+）
				Class<?> bundleWiringClass = Class.forName("org.osgi.framework.wiring.BundleWiring");
				Class<?> bundleClass = Class.forName("org.osgi.framework.Bundle");

				// 尝试从URL获取Bundle引用
				Object bundle = getBundleFromUrl(bundleUrl);
				if (bundle != null) {
					// 调用 bundle.adapt(BundleWiring.class)
					java.lang.reflect.Method adaptMethod = bundleClass.getMethod("adapt", Class.class);
					Object bundleWiring = adaptMethod.invoke(bundle, bundleWiringClass);

					if (bundleWiring != null) {
						// 调用 bundleWiring.listResources(path, pattern, options)
						java.lang.reflect.Method listResourcesMethod = bundleWiringClass.getMethod("listResources",
								String.class, String.class, int.class);
						// LISTRESOURCES_RECURSE = 2, LISTRESOURCES_LOCAL = 1
						@SuppressWarnings("unchecked")
						Collection<String> resources = (Collection<String>) listResourcesMethod.invoke(bundleWiring,
								basePath, "*", 2);

						if (resources != null) {
							for (String resourcePath : resources) {
								if (matchCached(resourcePath, patternParts)) {
									try {
										URL resourceUrl = bundle.getClass().getMethod("getResource", String.class)
												.invoke(bundle, resourcePath) instanceof URL
														? (URL) bundle.getClass().getMethod("getResource", String.class)
																.invoke(bundle, resourcePath)
														: null;
										if (resourceUrl != null) {
											result.add(new Resource(resourcePath, resourceUrl, ResourceType.JAR));
										}
									} catch (Exception e) {
										// 忽略单个资源错误
									}
								}
							}
						}
						return;
					}
				}
			} catch (ClassNotFoundException e) {
				// OSGi API不可用，尝试其他方式
			}

			// 方式2: 通过Bundle.findEntries遍历（兼容旧版OSGi）
			try {
				Class<?> bundleClass = Class.forName("org.osgi.framework.Bundle");
				Object bundle = getBundleFromUrl(bundleUrl);
				if (bundle != null) {
					// 调用 bundle.findEntries(path, pattern, recurse)
					java.lang.reflect.Method findEntriesMethod = bundleClass.getMethod("findEntries", String.class,
							String.class, boolean.class);
					@SuppressWarnings("unchecked")
					Enumeration<URL> entries = (Enumeration<URL>) findEntriesMethod.invoke(bundle,
							basePath.startsWith("/") ? basePath.substring(1) : basePath, "*", true);

					if (entries != null) {
						while (entries.hasMoreElements()) {
							URL entryUrl = entries.nextElement();
							String entryPath = entryUrl.getPath();
							if (matchCached(entryPath, patternParts)) {
								result.add(new Resource(entryPath, entryUrl, ResourceType.JAR));
							}
						}
					}
					return;
				}
			} catch (ClassNotFoundException e) {
				// OSGi API不可用
			}

			// 方式3: 回退到简单匹配（非OSGi环境或无法获取Bundle）
			if (matchCached(path, patternParts)) {
				result.add(new Resource(path, bundleUrl, ResourceType.JAR));
			}
		} catch (Exception e) {
			logger.debug("Error scanning bundle: " + bundleUrl + ", error: " + e.getMessage());
		}
	}

	/**
	 * 尝试从bundle URL获取Bundle对象。
	 * 
	 * @param bundleUrl bundle协议URL
	 * @return Bundle对象，或null
	 */
	private static Object getBundleFromUrl(URL bundleUrl) {
		try {
			// 尝试通过FrameworkUtil获取Bundle（需要当前类所在的bundle）
			Class<?> frameworkUtilClass = Class.forName("org.osgi.framework.FrameworkUtil");
			Class<?> bundleClass = Class.forName("org.osgi.framework.Bundle");
			Class<?> bundleContextClass = Class.forName("org.osgi.framework.BundleContext");

			// FrameworkUtil.getBundle(Class)
			java.lang.reflect.Method getBundleMethod = frameworkUtilClass.getMethod("getBundle", Class.class);
			Object currentBundle = getBundleMethod.invoke(null, AntPathResourceLoader.class);

			if (currentBundle != null) {
				// 获取BundleContext
				java.lang.reflect.Method getBundleContextMethod = bundleClass.getMethod("getBundleContext");
				Object bundleContext = getBundleContextMethod.invoke(currentBundle);

				if (bundleContext != null) {
					// 从URL解析bundle ID
					String urlStr = bundleUrl.toExternalForm();
					// bundle://bundleId/path 或 bundleentry://bundleId/path
					java.util.regex.Pattern pattern = java.util.regex.Pattern.compile("bundle(?:entry)?://(\\d+)");
					java.util.regex.Matcher matcher = pattern.matcher(urlStr);
					if (matcher.find()) {
						long bundleId = Long.parseLong(matcher.group(1));
						// 调用 bundleContext.getBundle(bundleId)
						java.lang.reflect.Method getBundleByIdMethod = bundleContextClass.getMethod("getBundle",
								long.class);
						return getBundleByIdMethod.invoke(bundleContext, bundleId);
					}

					// 如果无法解析bundle ID，返回当前bundle
					return currentBundle;
				}
			}
		} catch (Exception e) {
			// 忽略，返回null
		}
		return null;
	}

	/**
	 * 递归扫描目录下的文件。
	 * 
	 * @param rootDir      根目录
	 * @param currentDir   当前目录
	 * @param patternParts 路径模式分段
	 * @param result       结果列表
	 */
	private static void scanFileRecursive(File rootDir, File currentDir, String[] patternParts, List<Resource> result) {
		File[] files = currentDir.listFiles();
		if (files == null) {
			return;
		}
		for (File file : files) {
			if (file.isDirectory()) {
				scanFileRecursive(rootDir, file, patternParts, result);
			} else {
				String relPath = rootDir.toPath().relativize(file.toPath()).toString().replace("\\", "/");
				if (matchCached(relPath, patternParts)) {
					try {
						result.add(new Resource(relPath, file.toURI().toURL(), ResourceType.FILE, file.toPath()));
					} catch (Exception e) {
						logger.debug("Failed to create resource for: " + file);
					}
				}
			}
		}
	}

	/**
	 * 扫描文件系统绝对路径。
	 * 
	 * <p>
	 * 支持Ant风格通配符，自动处理URL编码的路径。
	 * 
	 * @param pattern    文件系统路径模式（可包含通配符）
	 * @param fileSuffix 文件后缀
	 * @return 匹配的资源列表
	 */
	private static List<Resource> scanFileSystemPath(String pattern, String fileSuffix) {
		List<Resource> list = new ArrayList<>();

		// 处理file:前缀
		String path = pattern;
		if (path.startsWith("file:")) {
			path = path.substring("file:".length());
		}

		// 解码URL编码的路径（处理空白被转义的情况）
		// path = FileUtil.decodePath(path.trim());

		// 统一使用正斜杠处理
		path = path.replace("\\", "/");

		// 确保路径以/开头（Windows路径如 D:/path 转换为 /D/path 便于统一处理）
		if (path.length() >= 2 && Character.isLetter(path.charAt(0)) && path.charAt(1) == ':') {
			path = "/" + path;
		}

		// 如果路径不包含通配符，补充默认后缀模式
		String antPattern = path;
		if (!antPattern.endsWith(fileSuffix)) {
			if (!antPattern.endsWith("/")) {
				antPattern = antPattern + "/";
			}
			antPattern = antPattern + "**/" + fileSuffix;
		}

		// 找到根目录（通配符之前的部分）
		String rootPath = clearUnSpecChar(extractRootPath(antPattern), true);
		// rootPath = clearUnSpecChar(rootPath,true);
		String[] patternParts = PATTERN_CACHE.computeIfAbsent(antPattern, p -> p.replaceFirst("^/", "").split("/"));

		try {
			Path root = Paths.get(rootPath);
			if (!Files.exists(root)) {
				logger.debug("Root path does not exist: " + rootPath);
				return list;
			}

			try (var stream = Files.walk(root, Integer.MAX_VALUE)) {
				stream.filter(Files::isRegularFile).forEach(p -> {
					try {
						if (Files.size(p) <= 0) {
							return;
						}
					} catch (IOException e) {
						return;
					}

					String relPath = root.relativize(p).toString().replace("\\", "/");
					String fullPath = rootPath.replace("\\", "/") + "/" + relPath;
					// 移除开头的/以便匹配
					fullPath = fullPath.replaceFirst("^/", "");

					if (matchCached(fullPath, patternParts)) {
						try {
							String resourcePath = relPath;
							list.add(new Resource(resourcePath, p.toUri().toURL(), ResourceType.FILE, p));
						} catch (Exception e) {
							e.printStackTrace();
							logger.error("Failed to create resource for: " + p, e);
						}
					}
				});
			}
		} catch (Exception e) {
			e.printStackTrace();
			logger.error("Error scanning file system path: " + pattern, e);
		}

		return list;
	}

	private static String clearUnSpecChar(String path, boolean isAbsoluteFile) {
		if (isAbsoluteFile) {
			File file = new File(path);
			if (file.exists()) {
				return path;
			} else {
				boolean hasSpecChar = false;
				String fileResource = path;
				for (String[] item : SPECIALCHARACTERS) {
					if (fileResource.contains(item[0])) {
						hasSpecChar = true;
						fileResource = fileResource.replace(item[0], item[1]);
					}
				}
				// 存在特殊字符，重新实例化文件
				if (hasSpecChar) {
					file = new File(fileResource);
					if (file.exists()) {
						return fileResource;
					}
				}
				// 文件依旧不存在
				if (fileResource.contains("%")) {
					fileResource = FileUtil.decodePath(fileResource);
					file = new File(fileResource);
					if (file.exists()) {
						return fileResource;
					}
				}
			}
		}
		return path;
	}

	/**
	 * 从Ant路径模式中提取根目录（通配符之前的部分）。
	 * 
	 * @param antPattern Ant风格路径模式
	 * @return 根目录路径
	 */
	private static String extractRootPath(String antPattern) {
		String[] parts = antPattern.replaceFirst("^/", "").split("/");
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
		// Windows路径恢复（如 /D/path -> D:/path）
		if (result.length() >= 3 && result.startsWith("/") && Character.isLetter(result.charAt(1))
				&& result.charAt(2) == '/') {
			result = result.substring(1);
		}

		return result.isEmpty() ? "/" : result;
	}

	/**
	 * 使用缓存的模式分段匹配路径。
	 * 
	 * @param path         待匹配的路径
	 * @param patternParts 预解析的路径模式分段
	 * @return 匹配成功返回true
	 */
	private static boolean matchCached(String path, String[] patternParts) {
		String[] pathParts = path.replaceFirst("^/", "").split("/");
		return match(pathParts, 0, patternParts, 0);
	}

	/**
	 * 匹配路径与Ant模式。
	 * 
	 * @param path    待匹配的路径
	 * @param pattern Ant风格路径模式
	 * @return 匹配成功返回true
	 */
	public static boolean match(String path, String pattern) {
		String[] pParts = pattern.replaceFirst("^/", "").split("/");
		String[] fParts = path.replaceFirst("^/", "").split("/");
		return match(fParts, 0, pParts, 0);
	}

	/**
	 * 递归匹配路径分段与模式分段。
	 * 
	 * <p>
	 * 支持Ant通配符：
	 * <ul>
	 * <li>* - 匹配任意字符</li>
	 * <li>? - 匹配单个字符</li>
	 * <li>** - 匹配多级目录</li>
	 * </ul>
	 * 
	 * @param f  路径分段数组
	 * @param fi 当前路径分段索引
	 * @param p  模式分段数组
	 * @param pi 当前模式分段索引
	 * @return 匹配成功返回true
	 */
	private static boolean match(String[] f, int fi, String[] p, int pi) {
		if (pi == p.length && fi == f.length)
			return true;
		if (pi >= p.length)
			return false;

		String part = p[pi];
		if ("**".equals(part)) {
			for (int i = fi; i <= f.length; i++) {
				if (match(f, i, p, pi + 1))
					return true;
			}
			return false;
		}

		if (fi >= f.length)
			return false;
		return wildcardMatch(f[fi], part) && match(f, fi + 1, p, pi + 1);
	}

	/**
	 * 单段通配符匹配，支持 * 和 ? 通配符。
	 * 
	 * @param text     待匹配文本
	 * @param wildcard 通配符模式
	 * @return 匹配成功返回true
	 */
	private static boolean wildcardMatch(String text, String wildcard) {
		if (wildcard.isEmpty())
			return text.isEmpty();
		if ("*".equals(wildcard))
			return true;
		if (!wildcard.contains("*") && !wildcard.contains("?"))
			return text.equals(wildcard);

		int t = 0, w = 0;
		int star = -1;
		int matchPos = 0;

		while (t < text.length()) {
			if (w < wildcard.length() && wildcard.charAt(w) == '*') {
				star = w++;
				matchPos = t;
			} else if (w < wildcard.length() && (wildcard.charAt(w) == '?' || wildcard.charAt(w) == text.charAt(t))) {
				w++;
				t++;
			} else if (star != -1) {
				w = star + 1;
				matchPos++;
				t = matchPos;
			} else {
				return false;
			}
		}

		while (w < wildcard.length() && wildcard.charAt(w) == '*')
			w++;

		while (w < wildcard.length() && wildcard.charAt(w) == '?') {
			w++;
			t++;
		}

		return w == wildcard.length() && t == text.length();
	}

	/**
	 * 测试入口，演示资源加载功能。
	 * 
	 * @param args 命令行参数
	 */
	public static void main(String[] args) {
		String[] resourceDirs = { "classpath*:com/example/**/*.sql.xml" };

		LoadResult result = loadWithResult(resourceDirs);

		System.out.println("=== 本地文件资源（可监听变更） ===");
		System.out.println("共 " + result.getFileResources().size() + " 个");
		result.getFileResources().forEach(r -> {
			System.out.println("  " + r.getPath() + " [lastModified=" + r.getLastModified() + "]");
		});

		System.out.println("\n=== JAR包内资源 ===");
		System.out.println("共 " + result.getJarResources().size() + " 个");
		result.getJarResources().forEach(r -> System.out.println("  " + r.getPath()));

		System.out.println("\n=== 所有资源 ===");
		System.out.println("共 " + result.getAllResources().size() + " 个");
	}
}
