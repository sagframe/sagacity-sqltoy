package org.sagacity.sqltoy.config;

import java.io.File;
import java.io.FileFilter;
import java.io.IOException;
import java.lang.reflect.Modifier;
import java.net.JarURLConnection;
import java.net.URL;
import java.net.URLDecoder;
import java.util.ArrayList;
import java.util.Enumeration;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;
import java.util.Vector;
import java.util.jar.JarEntry;
import java.util.jar.JarFile;

import org.sagacity.sqltoy.config.annotation.Entity;
import org.sagacity.sqltoy.config.annotation.SqlToyEntity;
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
 */
@SuppressWarnings({ "unchecked", "rawtypes" })
public class ScanEntityAndSqlResource {
	/**
	 * 定义日志
	 */
	protected final static Logger logger = LoggerFactory.getLogger(ScanEntityAndSqlResource.class);

	/**
	 * 默认的sql定义文件后缀名,便于区分和查找加载
	 */
	private static final String SQLTOY_SQL_FILE_SUFFIX = ".sql.xml";

	private static final String CLASSPATH = "classpath:";
	private static final String JAR = "jar";

	/**
	 * @todo 从指定包package中获取所有的sqltoy实体对象(意义已经不大,entity类目前已经改为在使用时加载的解析模式)
	 * @param pack
	 * @param recursive
	 * @param charset
	 * @return
	 */
	@Deprecated
	public static Set<Class<?>> getPackageEntities(String pack, boolean recursive, String charset) {
		// class类的集合
		Set<Class<?>> entities = new LinkedHashSet<Class<?>>();
		// 获取包的名字 并进行替换
		String packageName = pack.trim();
		// 剔除第一个字符为目录的符合,并统一packName为xxx.xxx 格式
		if (packageName.length() > 0 && packageName.charAt(0) == '/') {
			packageName = packageName.substring(1);
		}
		if (packageName.endsWith("/")) {
			packageName = packageName.substring(0, packageName.length() - 1);
		}
		packageName = packageName.replace("/", ".");
		String packageDirName = packageName.replace('.', '/');
		// 定义一个枚举的集合,循环向下级目录检索entity类
		Enumeration<URL> dirs;
		try {
			dirs = Thread.currentThread().getContextClassLoader().getResources(packageDirName);
			// 循环迭代下去
			URL url;
			String protocol;
			while (dirs.hasMoreElements()) {
				// 获取下一个元素
				url = dirs.nextElement();
				// 得到协议的名称
				protocol = url.getProtocol();
				// 如果是以文件的形式保存在服务器上
				if ("file".equals(protocol)) {
					// 获取包的物理路径
					String filePath = URLDecoder.decode(url.getFile(), (charset == null) ? "UTF-8" : charset);
					// 以文件的方式扫描整个包下的文件 并添加到集合中
					addEntitiesInPackage(packageName, filePath, recursive, entities);
				} else if ("jar".equals(protocol)) {
					// 如果是jar包文件
					logger.debug("jar类型的扫描,加载POJO实体对象");
					// 定义一个JarFile
					JarFile jar;
					try {
						// 获取jar
						jar = ((JarURLConnection) url.openConnection()).getJarFile();
						// 从此jar包 得到一个枚举类
						Enumeration<JarEntry> entries = jar.entries();
						// 同样的进行循环迭代
						JarEntry entry;
						String name;
						String loadClass;
						while (entries.hasMoreElements()) {
							// 获取jar里的一个实体 可以是目录 和一些jar包里的其他文件 如META-INF等文件
							entry = entries.nextElement();
							name = entry.getName();
							// 如果前半部分和定义的包名相同
							if (name.startsWith(packageDirName) && name.endsWith(".class") && !entry.isDirectory()) {
								// 去掉后面的".class" 获取真正的类名
								loadClass = name.substring(0, name.length() - 6).replace("/", ".");
								try {
									// 添加到classes
									Class entityClass = Thread.currentThread().getContextClassLoader()
											.loadClass(loadClass);
									// 判定是否是sqltoy实体对象
									if (isSqlToyEntity(entityClass)) {
										entities.add(entityClass);
									}
								} catch (ClassNotFoundException e) {
									e.printStackTrace();
								}
							}
						}
					} catch (IOException e) {
						e.printStackTrace();
					}
				}
			}
		} catch (IOException e) {
			e.printStackTrace();
		}
		return entities;
	}

	/**
	 * @todo 以文件的形式来获取包下的所有Class(意义已经不大,entity类目前已经改为使用时加载解析模式)
	 * @param packageName
	 * @param packagePath
	 * @param recursive
	 * @param entities
	 */
	@Deprecated
	public static void addEntitiesInPackage(String packageName, String packagePath, final boolean recursive,
			Set<Class<?>> entities) {
		// 获取此包的目录 建立一个File
		File dir = new File(packagePath);
		// 如果不存在或者 也不是目录就直接返回
		if (!dir.exists() || !dir.isDirectory()) {
			return;
		}
		// 如果存在 就获取包下的所有文件 包括目录
		File[] dirfiles = dir.listFiles(new FileFilter() {
			// 自定义过滤规则 如果可以循环(包含子目录) 或则是以.class结尾的文件(编译好的java类文件)
			public boolean accept(File file) {
				return (recursive && file.isDirectory()) || (file.getName().endsWith(".class"));
			}
		});
		// 循环所有文件
		String className;
		for (File file : dirfiles) {
			// 如果是目录 则继续递归扫描
			if (file.isDirectory()) {
				addEntitiesInPackage(packageName.concat(".").concat(file.getName()), file.getAbsolutePath(), recursive,
						entities);
			} else {
				className = file.getName();
				// 如果是java类文件 去掉后面的.class 只留下类名
				className = className.substring(0, className.length() - 6);
				try {
					// 加载class 并判定是否为sqltoy的实体类
					Class entityClass = Thread.currentThread().getContextClassLoader()
							.loadClass(packageName.concat(".").concat(className));
					if (isSqlToyEntity(entityClass)) {
						entities.add(entityClass);
					}
				} catch (ClassNotFoundException e) {
					e.printStackTrace();
				}
			}
		}
	}

	/**
	 * @todo 判定是否sqltoy的实体对象
	 * @param entityClass
	 * @return
	 */
	public static boolean isSqlToyEntity(Class entityClass) {
		if (entityClass.isAnnotationPresent(SqlToyEntity.class)) {
			return true;
		}
		if (entityClass.isAnnotationPresent(Entity.class) && !Modifier.isAbstract(entityClass.getModifiers())) {
			return true;
		}
		return false;
	}

	/**
	 * @todo 获取sqltoy配置的sql文件
	 * @param resourceDir
	 * @param mappingResources
	 * @return
	 * @throws Exception
	 */
	public static List getSqlResources(String resourceDir, List<String> mappingResources) throws Exception {
		List result = new ArrayList();
		String realRes;
		Enumeration<URL> urls;
		URL url;
		File file;
		boolean startClasspath = false;
		if (StringUtil.isNotBlank(resourceDir)) {
			// 统一全角半角，用逗号分隔
			String[] dirSet = resourceDir.replaceAll("\\；", ",").replaceAll("\\，", ",").replaceAll("\\;", ",")
					.split("\\,");
			JarFile jar;
			Enumeration<JarEntry> entries;
			JarEntry entry;
			String sqlFile;
			for (String dir : dirSet) {
				realRes = dir.trim();
				startClasspath = false;
				if (realRes.toLowerCase().startsWith(CLASSPATH)) {
					realRes = realRes.substring(10).trim();
					startClasspath = true;
				}
				urls = getResourceUrls(realRes, startClasspath);
				if (urls != null) {
					while (urls.hasMoreElements()) {
						url = urls.nextElement();
						if (url.getProtocol().equals(JAR)) {
							if (realRes.length() > 0 && realRes.charAt(0) == '/') {
								realRes = realRes.substring(1);
							}
							jar = ((JarURLConnection) url.openConnection()).getJarFile();
							entries = jar.entries();
							while (entries.hasMoreElements()) {
								// 获取jar里的一个实体 可以是目录 和一些jar包里的其他文件 如META-INF等文件
								entry = entries.nextElement();
								sqlFile = entry.getName();
								if (sqlFile.startsWith(realRes)
										&& sqlFile.toLowerCase().endsWith(SQLTOY_SQL_FILE_SUFFIX)
										&& !entry.isDirectory()) {
									// update 2020-03-13 调整sql加载策略
									// jar中的sql顺序放在前面,从而便于classes里面的sql可以在后面加载可以覆盖前面的，便于项目做增量更新
									result.add(0, sqlFile);
								}
							}
						} else {
							getPathFiles(new File(url.toURI()), result);
						}
					}
				}
			}
		}
		if (mappingResources != null && !mappingResources.isEmpty()) {
			for (int i = 0; i < mappingResources.size(); i++) {
				realRes = mappingResources.get(i).trim();
				// 必须是以.sql.xml结尾的文件
				if (realRes.toLowerCase().endsWith(SQLTOY_SQL_FILE_SUFFIX)) {
					startClasspath = false;
					if (realRes.toLowerCase().startsWith(CLASSPATH)) {
						realRes = realRes.substring(10).trim();
						startClasspath = true;
					}
					urls = getResourceUrls(realRes, startClasspath);
					if (null != urls) {
						while (urls.hasMoreElements()) {
							url = urls.nextElement();
							if (realRes.length() > 0 && realRes.charAt(0) == '/') {
								realRes = realRes.substring(1);
							}

							if (url.getProtocol().equals(JAR)) {
								if (!result.contains(realRes)) {
									// jar中的sql优先加载,从而确保直接放于classes目录下面的sql可以实现对之前的覆盖,便于项目增量发版管理
									result.add(0, realRes);
								}
							} else {
								file = new File(url.toURI());
								if (!result.contains(file)) {
									result.add(file);
								}
							}
						}
					}
				}
			}
		}
		return result;
	}

	/**
	 * @todo 获取资源的URL
	 * @param resource
	 * @param startClasspath
	 * @return
	 * @throws Exception
	 * @modify update 2017-10-28 从单URL变成URL枚举数组
	 */
	public static Enumeration<URL> getResourceUrls(String resource, boolean startClasspath) throws Exception {
		Enumeration<URL> urls = null;
		if (null == resource) {
			return urls;
		}
		if (!startClasspath) {
			File file = new File(resource);
			if (file.exists()) {
				Vector<URL> v = new Vector<URL>();
				v.add(file.toURI().toURL());
				urls = v.elements();
			} else {
				if (resource.length() > 0 && resource.charAt(0) == '/') {
					resource = resource.substring(1);
				}
				urls = Thread.currentThread().getContextClassLoader().getResources(resource);
			}
		} else {
			if (resource.length() > 0 && resource.charAt(0) == '/') {
				resource = resource.substring(1);
			}
			urls = Thread.currentThread().getContextClassLoader().getResources(resource);
		}
		return urls;
	}

	/**
	 * @todo 递归获取文件夹下面的以sql.xml结尾的sql文件
	 * @param parentFile
	 * @param fileList
	 */
	private static void getPathFiles(File parentFile, List fileList) {
		if (null == parentFile) {
			return;
		}
		String fileName = parentFile.getName();
		// 路径，取路径下文件
		if (parentFile.isDirectory()) {
			File[] files = parentFile.listFiles();
			File file;
			for (int loop = 0; loop < files.length; loop++) {
				file = files[loop];
				fileName = file.getName();
				if (!file.isDirectory() && fileName.toLowerCase().endsWith(SQLTOY_SQL_FILE_SUFFIX)) {
					fileList.add(file);
				} else {
					getPathFiles(files[loop], fileList);
				}
			}
		} // 文件
		else if (fileName.toLowerCase().endsWith(SQLTOY_SQL_FILE_SUFFIX)) {
			fileList.add(parentFile);
		}
	}
}
