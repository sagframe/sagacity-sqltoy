package org.sagacity.sqltoy.config;

import java.io.File;
import java.lang.reflect.Modifier;
import java.net.URI;
import java.net.URL;
import java.nio.file.FileSystems;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.PathMatcher;
import java.util.ArrayList;
import java.util.Enumeration;
import java.util.List;
import java.util.jar.JarEntry;
import java.util.jar.JarFile;
import java.util.stream.Stream;

import org.sagacity.sqltoy.config.annotation.Entity;
import org.sagacity.sqltoy.config.annotation.SqlToyEntity;
import org.sagacity.sqltoy.utils.FileUtil;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * 扫描指定的包路径，加载sqltoy的实体类
 * 
 * @date 2026-5-19
 */
public class EntityScanner {
	protected final static Logger logger = LoggerFactory.getLogger(EntityScanner.class);
	private static final boolean IS_WINDOWS = System.getProperty("os.name").toLowerCase().contains("win");

	/**
	 * 扫描sqltoy的POJO 类
	 * 
	 * @param packagePattern
	 * @param recursive
	 * @param charset
	 * @return
	 * @throws Exception
	 */
	public static List<Class<?>> scanEntityClasses(String packagePattern, boolean recursive, String charset)
			throws Exception {
		List<Class<?>> result = new ArrayList<>();
		if (packagePattern == null || packagePattern.isEmpty()) {
			return result;
		}
		packagePattern = packagePattern.trim();
		if (packagePattern.startsWith("/")) {
			packagePattern = packagePattern.substring(1);
		}
		if (packagePattern.endsWith("/")) {
			packagePattern = packagePattern.substring(0, packagePattern.length() - 1);
		}
		String basePackage = extractBasePackage(packagePattern);
		String basePath = basePackage.replace('.', '/');
		// 提取相对模式部分（去掉基础包路径前缀），用于匹配相对路径
		String relativePattern = packagePattern;
		if (!basePackage.isEmpty()) {
			relativePattern = packagePattern.substring(basePackage.length());
			if (relativePattern.startsWith(".")) {
				relativePattern = relativePattern.substring(1);
			}
		}
		try {
			ClassLoader classLoader = Thread.currentThread().getContextClassLoader();
			if (classLoader == null) {
				classLoader = ClassLoader.getSystemClassLoader();
			}
			Enumeration<URL> resources = classLoader.getResources(basePath);
			URL url;
			String protocol;
			while (resources.hasMoreElements()) {
				url = resources.nextElement();
				protocol = url.getProtocol();
				if ("file".equals(protocol)) {
					// 兼容路径中中文、空格、+ 号等场景
					String filePath = new URI(url.toString()).getPath();
					if (filePath != null) {
						if (IS_WINDOWS && filePath.startsWith("/")) {
							filePath = filePath.substring(1);
						}
						File dir = new File(filePath);
						if (dir.exists() && dir.isDirectory()) {
							scanDirectory(dir, basePackage, relativePattern, result, classLoader);
						}
					}
				} else if ("jar".equals(protocol)) {
					scanJar(url, packagePattern, result, charset, classLoader);
				}
			}
		} catch (Exception e) {
			logger.error("扫描实体类发生异常,模式:{}!当前返回部分扫描结果,实体可能不完整,请检查!", packagePattern, e);
		}
		return result;
	}

	/**
	 * 扫描文件路径(本地开发情况下编译到target classes下面)
	 * 
	 * @param dir
	 * @param basePackage
	 * @param relativePattern
	 * @param result
	 * @param classLoader
	 */
	private static void scanDirectory(File dir, String basePackage, String relativePattern, List<Class<?>> result,
			ClassLoader classLoader) {
		Path root = dir.toPath();
		// 构建匹配相对路径的 glob 模式
		// 只替换包分隔符的 .，保留通配符中的 . 不被替换
		StringBuilder globBuilder = new StringBuilder();
		for (String part : relativePattern.split("\\.", -1)) {
			if (globBuilder.length() > 0) {
				globBuilder.append("/");
			}
			globBuilder.append(part);
		}
		String globPattern = globBuilder.toString();
		if (!globPattern.isEmpty() && !globPattern.endsWith("/")) {
			globPattern = globPattern + "/";
		}
		// 使用 {*.class,**/*.class} 同时匹配当前目录和子目录中的class文件
		globPattern = globPattern + "{*.class,**/*.class}";
		PathMatcher matcher = FileSystems.getDefault().getPathMatcher("glob:" + globPattern);
		// 基础包名前缀（用于拼接完整类名）
		String packagePrefix = basePackage.isEmpty() ? "" : basePackage + ".";
		try (Stream<Path> paths = Files.walk(root)) {
			// Files.walk的Stream持有目录句柄,必须关闭避免泄漏
			paths.filter(Files::isRegularFile).forEach(path -> {
				try {
					Path relative = root.relativize(path);
					// 将Windows反斜杠路径转换为正斜杠，确保与glob模式兼容
					String relativePath = relative.toString().replace('\\', '/');
					Path normalizedRelative = FileSystems.getDefault().getPath(relativePath);
					if (matcher.matches(normalizedRelative)) {
						String className = packagePrefix + pathToClass(root, path);
						checkAndAdd(className, result, classLoader);
					}
				} catch (Exception e) {
					logger.debug("Skip unreadable file: {}", path);
				}
			});
		} catch (Exception e) {
			logger.debug("Skip directory: {}", root);
		}
	}

	/**
	 * 扫描jar包，打包部署场景、引用jar场景
	 * 
	 * @param jarUrl
	 * @param pattern
	 * @param result
	 * @param charset
	 * @param classLoader
	 * @throws Exception
	 */
	private static void scanJar(URL jarUrl, String pattern, List<Class<?>> result, String charset,
			ClassLoader classLoader) throws Exception {
		String jarPath = FileUtil.getJarPath(jarUrl);
		String antPattern = pattern.replace('.', '/') + "/**/*.class";
		try (JarFile jarFile = new JarFile(jarPath)) {
			Enumeration<JarEntry> entries = jarFile.entries();
			JarEntry entry;
			String name;
			String className;
			while (entries.hasMoreElements()) {
				entry = entries.nextElement();
				name = entry.getName();
				if (name.endsWith(".class") && matchJarPath(name, antPattern)) {
					className = name.replace("/", ".").replace(".class", "");
					checkAndAdd(className, result, classLoader);
				}
			}
		}
	}

	/**
	 * jar包路径匹配
	 * 
	 * @param path
	 * @param globPattern
	 * @return
	 */
	private static boolean matchJarPath(String path, String globPattern) {
		// glob模式转正则
		// ** 匹配零个或多个目录层级
		// 关键：/**/ 要匹配零个或多个完整路径段（包括斜杠），避免双斜杠问题
		// 使用唯一占位符保护正则中的 * 不被后续替换影响
		String regex = globPattern
				// 先用占位符保护 **
				.replace("**", "<<DS>>")
				// 处理带斜杠的 ** 模式 - 用唯一占位符保护正则中的 *
				.replace("/<<DS>>/", "<<PATH_SEG>>") // 匹配 /a, /a/b, 或空
				.replace("/<<DS>>", "<<PATH_END>>") // 末尾的 /** 匹配 /a, /a/b, 或空
				.replace("<<DS>>/", "<<PATH_START>>") // 开头的 **/ 匹配 a/, a/b/, 或空
				// 处理其他通配符
				.replace(".", "\\.").replace("*", "[^/]*").replace("?", ".")
				// 单独的 ** 匹配任意非斜杠字符（用于文件名）
				.replace("<<DS>>", "[^/]*")
				// 替换占位符为实际正则
				.replace("<<PATH_SEG>>", "(/[^/]*)*").replace("<<PATH_END>>", "(/[^/]*)*")
				.replace("<<PATH_START>>", "([^/]*/)*");
		return path.matches(regex);
	}

	/**
	 * 提取com/company/** /vo 包的base部分com/company
	 * 
	 * @param pattern
	 * @return
	 */
	private static String extractBasePackage(String pattern) {
		if (pattern == null || pattern.isEmpty()) {
			return "";
		}
		StringBuilder base = new StringBuilder();
		for (String part : pattern.split("\\.")) {
			if (part.contains("*") || part.contains("?")) {
				break;
			}
			if (base.length() > 0) {
				base.append(".");
			}
			base.append(part);
		}
		return base.toString();
	}

	private static String pathToClass(Path root, Path classFile) {
		String relative = root.relativize(classFile).toString();
		String className = relative.replace('\\', '.').replace('/', '.');
		if (className.toLowerCase().endsWith(".class")) {
			className = className.substring(0, className.length() - 6);
		}
		return className;
	}

	/**
	 * 盘点是否是sqltoy的实体类
	 * 
	 * @param entityClass
	 * @return
	 */
	public static boolean isSqlToyEntity(Class<?> entityClass) {
		if (entityClass.isAnnotationPresent(SqlToyEntity.class)) {
			return true;
		}
		if (entityClass.isAnnotationPresent(Entity.class) && !Modifier.isAbstract(entityClass.getModifiers())) {
			return true;
		}
		return false;
	}

	private static void checkAndAdd(String className, List<Class<?>> result, ClassLoader classLoader) {
		try {
			// 不做JVM级类名去重:EntityManager.parseEntityMeta以类名幂等去重,
			// 静态缓存会导致二次初始化(Spring上下文刷新/热部署/多上下文)扫描结果为空、实体元数据静默丢失,
			// 且类名先于Class.forName登记会让加载失败的类被永久跳过
			Class<?> clazz = Class.forName(className, false, classLoader);
			if (isSqlToyEntity(clazz)) {
				result.add(clazz);
				logger.debug("Found entity: {}", className);
			}
		} catch (Throwable e) {
			logger.debug("Skip class: {}", className, e);
		}
	}
}