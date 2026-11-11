package org.sagacity.sqltoy.config;

import java.io.File;
import java.lang.reflect.Modifier;
import java.net.JarURLConnection;
import java.net.URI;
import java.net.URL;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Enumeration;
import java.util.List;
import java.util.Locale;
import java.util.jar.JarEntry;
import java.util.jar.JarFile;
import java.util.stream.Stream;

import org.sagacity.sqltoy.config.annotation.Entity;
import org.sagacity.sqltoy.config.annotation.SqlToyEntity;
import org.sagacity.sqltoy.utils.AntPathUtils;
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
	private static final boolean IS_WINDOWS = System.getProperty("os.name").toLowerCase(Locale.ROOT).contains("win");

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
			logger.error(
					"exception occurred while scanning entity classes, pattern:{}! returns partial scan result, entities may be incomplete, please check!",
					packagePattern, e);
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
		// update 2026-11-11 目录与jar共用同一Ant匹配器:**匹配0层目录,
		// 原PathMatcher glob下"com.foo.**.entity"会漏掉"com.foo.entity.X"、"com.foo.**"会漏基包直属类
		String[] patternParts = buildClassPatternParts(relativePattern);
		// 基础包名前缀（用于拼接完整类名）
		String packagePrefix = basePackage.isEmpty() ? "" : basePackage + ".";
		try (Stream<Path> paths = Files.walk(root)) {
			// Files.walk的Stream持有目录句柄,必须关闭避免泄漏
			paths.filter(Files::isRegularFile).forEach(path -> {
				// 将Windows反斜杠路径转换为正斜杠，确保与模式兼容
				String relativePath = root.relativize(path).toString().replace('\\', '/');
				if (AntPathUtils.match(relativePath, patternParts)) {
					checkAndAdd(packagePrefix + pathToClass(root, path), result, classLoader);
				}
			});
		} catch (Exception e) {
			logger.debug("Skip directory: {}", root);
		}
	}

	/**
	 * 构建Ant匹配用的模式段:包路径按"."拆分后追加class文件匹配段,
	 * 追加"**"(匹配0~n层目录,含基包直属类),保证目录与jar两侧语义一致
	 *
	 * @param packagePattern 包模式(目录扫描传相对包模式,jar扫描传完整包模式)
	 * @return
	 */
	private static String[] buildClassPatternParts(String packagePattern) {
		List<String> parts = new ArrayList<String>();
		if (packagePattern != null && !packagePattern.isEmpty()) {
			for (String part : packagePattern.split("\\.")) {
				if (!part.isEmpty()) {
					parts.add(part);
				}
			}
		}
		parts.add("**");
		parts.add("*.class");
		return parts.toArray(new String[0]);
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
		String[] patternParts = buildClassPatternParts(pattern);
		String url = jarUrl.toExternalForm();
		// 多个"!"(Spring Boot可执行jar)或nested:协议为嵌套jar,其余为普通jar
		boolean nestedJar = url.contains("nested:") || url.indexOf("!/") != url.lastIndexOf("!/");
		// update 2026-11-11 嵌套jar必须经JarURLConnection.getJarFile()取内层jar:
		// 原实现用FileUtil.getJarPath在第一个"!"处截断,会把BOOT-INF/classes或内层jar错当成外层app.jar,
		// 导致entry路径与包模式不匹配、实体全量漏扫;普通jar仍按路径打开,避免依赖jar内目录entry是否存在
		JarFile jarFile = nestedJar ? ((JarURLConnection) jarUrl.openConnection()).getJarFile()
				: new JarFile(FileUtil.getJarPath(jarUrl));
		try {
			Enumeration<JarEntry> entries = jarFile.entries();
			JarEntry entry;
			String name;
			String className;
			while (entries.hasMoreElements()) {
				entry = entries.nextElement();
				name = entry.getName();
				if (name.endsWith(".class") && AntPathUtils.match(name, patternParts)) {
					className = name.replace("/", ".").replace(".class", "");
					checkAndAdd(className, result, classLoader);
				}
			}
		} finally {
			// 嵌套jar的JarFile由连接缓存持有,不主动关闭
			if (!nestedJar) {
				jarFile.close();
			}
		}
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
		if (className.toLowerCase(Locale.ROOT).endsWith(".class")) {
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