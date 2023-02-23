package org.sagacity.sqltoy.translate;

import java.io.File;
import java.lang.reflect.Array;
import java.lang.reflect.Field;
import java.net.JarURLConnection;
import java.net.URL;
import java.util.ArrayList;
import java.util.Enumeration;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.jar.JarEntry;
import java.util.jar.JarFile;

import org.sagacity.sqltoy.SqlToyConstants;
import org.sagacity.sqltoy.SqlToyContext;
import org.sagacity.sqltoy.callback.XMLCallbackHandler;
import org.sagacity.sqltoy.config.ScanEntityAndSqlResource;
import org.sagacity.sqltoy.config.SqlConfigParseUtils;
import org.sagacity.sqltoy.config.model.SqlToyConfig;
import org.sagacity.sqltoy.config.model.Translate;
import org.sagacity.sqltoy.model.IgnoreKeyCaseMap;
import org.sagacity.sqltoy.translate.model.CheckerConfigModel;
import org.sagacity.sqltoy.translate.model.DefaultConfig;
import org.sagacity.sqltoy.translate.model.TranslateConfigModel;
import org.sagacity.sqltoy.utils.BeanUtil;
import org.sagacity.sqltoy.utils.FileUtil;
import org.sagacity.sqltoy.utils.SqlUtil;
import org.sagacity.sqltoy.utils.StringUtil;
import org.sagacity.sqltoy.utils.XMLUtil;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.w3c.dom.Document;
import org.w3c.dom.Element;
import org.w3c.dom.NodeList;

/**
 * @project sagacity-sqltoy
 * @description 用于解析缓存翻译的配置
 * @author zhongxuchen
 * @version v1.0,Date:2018年3月8日
 * @modify {Date:2022-06-11,支持多个缓存翻译定义文件}
 * @modify {Date:2022-10-05,支持i18n多语言翻译，由fightForYou反馈}
 */
public class TranslateConfigParse {
	/**
	 * 定义全局日志
	 */
	protected final static Logger logger = LoggerFactory.getLogger(TranslateConfigParse.class);

	private static final String CLASSPATH = "classpath:";
	private static final String JAR = "jar";

	private final static String TRANSLATE_SUFFIX = "-translate";
	private final static String CHECKER_SUFFIX = "-checker";
	private final static String[] TRANSLATE_TYPES = new String[] { "sql", "service", "rest", "local" };
	private final static String[] TRANSLATE_CHECKER_TYPES = new String[] { "sql-increment", "sql", "service-increment",
			"service", "rest", "rest-increment" };

	// 存放类包含缓存翻译注解配置
	private final static HashMap<String, HashMap<String, Translate>> classTranslateConfigMap = new HashMap<String, HashMap<String, Translate>>();

	// 缓存更新检测器,用于辨别是否重复定义
	private final static HashSet<String> cacheCheckers = new HashSet<String>();

	/**
	 * @todo 解析translate配置文件
	 * @param sqlToyContext
	 * @param translateMap    最终缓存配置，构建一个空map，在解析过程中填充
	 * @param checker         更新检测配置
	 * @param translateConfig 缓存配置文件
	 * @param isDefault       是否使用了默认配置,默认配置可跳过不存在的文件
	 * @param charset
	 * @return
	 * @throws Exception
	 */
	public static DefaultConfig parseTranslateConfig(final SqlToyContext sqlToyContext,
			IgnoreKeyCaseMap<String, TranslateConfigModel> translateMap,
			CopyOnWriteArrayList<CheckerConfigModel> checker, String translateConfig, boolean isDefault, String charset)
			throws Exception {
		// 获取全部缓存翻译定义文件
		List translateFiles = getTranslateFiles(translateConfig);
		Object translateFile;
		DefaultConfig result = new DefaultConfig();
		DefaultConfig defaultConfig = null;
		String translateFlieStr;
		boolean fileExist;
		Set<String> fileSet = new HashSet<String>();
		int index = 0;
		for (int i = 0; i < translateFiles.size(); i++) {
			translateFile = translateFiles.get(i);
			if (translateFile instanceof File) {
				translateFlieStr = ((File) translateFile).getPath();
			} else {
				translateFlieStr = translateFile.toString();
			}
			// 避免重复解析
			if (!fileSet.contains(translateFlieStr)) {
				fileExist = true;
				if (FileUtil.getFileInputStream(translateFile) == null) {
					fileExist = false;
				}
				// 判断缓存翻译的配置文件是否存在
				if (!isDefault && !fileExist) {
					logger.warn("缓存翻译配置文件:{}无法加载,请检查配路径正确性,如不使用缓存翻译可忽略此提示!", translateFlieStr);
					translateMap.clear();
					return result;
				}
				// 文件存在
				if (fileExist) {
					logger.debug("开始解析缓存配置文件:{}", translateFlieStr);
					// 解析单个缓存翻译定义文件
					defaultConfig = parseTranslate(sqlToyContext, translateMap, checker, index + 1, translateFile,
							charset);
					// 第一个作为默认全局配置
					if (index == 0) {
						result = defaultConfig;
					} else {
						// 集群各个节点检测时间容差,一般在1~60秒内，默认1秒
						if (result.getDeviationSeconds() == -1 && defaultConfig.getDeviationSeconds() != -1) {
							result.setDeviationSeconds(defaultConfig.getDeviationSeconds());
						}
						// 针对ehcache 额外的缓存磁盘存储路径(一般不需要定义)
						if (StringUtil.isBlank(result.getDiskStorePath())
								&& StringUtil.isNotBlank(defaultConfig.getDiskStorePath())) {
							result.setDiskStorePath(defaultConfig.getDiskStorePath());
						}
					}
					index++;
				}
				fileSet.add(translateFlieStr);
			}
		}
		return result;
	}

	/**
	 * @TODO 解析单个缓存配置文件
	 * @param sqlToyContext
	 * @param translateMap
	 * @param checker
	 * @param fileIndex       第几个缓存翻译配置文件,避免sqlId重复
	 * @param translateConfig
	 * @param charset
	 * @return
	 * @throws Exception
	 */
	private static DefaultConfig parseTranslate(final SqlToyContext sqlToyContext,
			IgnoreKeyCaseMap<String, TranslateConfigModel> translateMap,
			CopyOnWriteArrayList<CheckerConfigModel> checker, final int fileIndex, Object translateConfig,
			String charset) throws Exception {
		return (DefaultConfig) XMLUtil.readXML(translateConfig, charset, false, new XMLCallbackHandler() {
			@Override
			public Object process(Document doc, Element root) throws Exception {
				// 缓存翻译配置文件
				String translateFileStr = ((translateConfig instanceof File) ? ((File) translateConfig).getName()
						: translateConfig.toString());
				DefaultConfig defaultConfig = new DefaultConfig();
				// 存在缓存翻译的配置文件，后续以此作为使用缓存的依据
				defaultConfig.setUseCache(true);
				NodeList nodeList = root.getElementsByTagName("cache-translates");
				if (nodeList.getLength() == 0) {
					return defaultConfig;
				}
				// 解析缓存翻译配置
				Element node = (Element) nodeList.item(0);
				XMLUtil.setAttributes(node, defaultConfig);
				NodeList elts;
				Element elt;
				NodeList sqlNode;
				String sql;
				String sqlId;
				// 执行sql时是否显示debug信息
				boolean isShowSql;
				int index = 1;
				for (String translateType : TRANSLATE_TYPES) {
					elts = node.getElementsByTagName(translateType.concat(TRANSLATE_SUFFIX));
					if (elts.getLength() > 0) {
						for (int i = 0; i < elts.getLength(); i++) {
							elt = (Element) elts.item(i);
							TranslateConfigModel translateCacheModel = new TranslateConfigModel();
							// 设置默认值
							translateCacheModel.setHeap(defaultConfig.getDefaultHeap());
							translateCacheModel.setOffHeap(defaultConfig.getDefaultOffHeap());
							translateCacheModel.setDiskSize(defaultConfig.getDefaultDiskSize());
							translateCacheModel.setKeepAlive(defaultConfig.getDefaultKeepAlive());
							XMLUtil.setAttributes(elt, translateCacheModel);
							translateCacheModel.setType(translateType);
							// 非sqlId模式定义
							if ("sql".equals(translateType)) {
								if (StringUtil.isBlank(translateCacheModel.getSql())) {
									sqlNode = elt.getElementsByTagName("sql");
									if (sqlNode.getLength() > 0) {
										sql = StringUtil.trim(sqlNode.item(0).getTextContent());
									} else {
										sql = StringUtil.trim(elt.getTextContent());
									}
									sqlId = "s_trans_cache_" + fileIndex + "_0" + index;
									isShowSql = StringUtil.matches(sql, SqlToyConstants.NOT_PRINT_REGEX);
									SqlToyConfig sqlToyConfig = new SqlToyConfig(sqlId,
											SqlUtil.clearMistyChars(SqlUtil.clearMark(sql), " "));
									sqlToyConfig.setShowSql(!isShowSql);
									sqlToyConfig.setParamsName(
											SqlConfigParseUtils.getSqlParamsName(sqlToyConfig.getSql(null), true));
									sqlToyContext.putSqlToyConfig(sqlToyConfig);
									translateCacheModel.setSql(sqlId);
									index++;
								}
							}
							// 解析i18n国际化配置
							if (elt.hasAttribute("i18n")) {
								String[] i18nAry = elt.getAttribute("i18n").replace(";", ",").split("\\,");
								String[] localIndex;
								for (String i18n : i18nAry) {
									localIndex = i18n.split("\\:");
									translateCacheModel.putI18n(localIndex[0].trim(),
											Integer.parseInt(localIndex[1].trim()));
								}
							}
							// local模式缓存 默认缓存不失效，表示缓存由开发者在应用程序中自行控制，sqltoy只做初始化构建(如ehcache创建一个缓存实例，但不加载数据)
							// local模式是避免一些额外争议的产物，有部分开发者坚持缓存要应用自己管理
							if ("local".equals(translateType) && !elt.hasAttribute("keep-alive")) {
								translateCacheModel.setKeepAlive(-1);
							}
							if (translateMap.containsKey(translateCacheModel.getCache())) {
								throw new RuntimeException("缓存翻译配置中缓存:[" + translateCacheModel.getCache()
										+ "] 的定义已经存在!请检查配置文件:" + translateFileStr);
							}
							translateMap.put(translateCacheModel.getCache(), translateCacheModel);
							logger.debug("已经加载缓存翻译:cache={},type={}",
									(translateCacheModel.getCache() == null) ? "[非增量]" : translateCacheModel.getCache(),
									translateType);
						}
					}
				}
				nodeList = root.getElementsByTagName("cache-update-checkers");
				// 解析更新检测器
				if (nodeList.getLength() == 0) {
					return defaultConfig;
				}
				node = (Element) nodeList.item(0);
				// 集群节点时间偏差(秒)
				if (node.hasAttribute("cluster-time-deviation")) {
					defaultConfig.setDeviationSeconds(Integer.parseInt(node.getAttribute("cluster-time-deviation")));
					if (Math.abs(defaultConfig.getDeviationSeconds()) > 60) {
						logger.debug("您设置的集群节点时间差异参数cluster-time-deviation={} 秒>60秒,将设置为60秒!",
								defaultConfig.getDeviationSeconds());
						defaultConfig.setDeviationSeconds(-60);
					} else {
						defaultConfig.setDeviationSeconds(0 - Math.abs(defaultConfig.getDeviationSeconds()));
					}
				}
				String nodeType;
				index = 1;
				// 缓存更新检测
				for (String translateType : TRANSLATE_CHECKER_TYPES) {
					nodeType = translateType.concat(CHECKER_SUFFIX);
					elts = node.getElementsByTagName(nodeType);
					if (elts.getLength() > 0) {
						for (int i = 0; i < elts.getLength(); i++) {
							elt = (Element) elts.item(i);
							CheckerConfigModel checherConfigModel = new CheckerConfigModel();
							XMLUtil.setAttributes(elt, checherConfigModel);
							// 数据交互类型
							checherConfigModel.setType(translateType.replace("-increment", ""));
							// 增量方式更新
							if (translateType.endsWith("-increment")) {
								checherConfigModel.setIncrement(true);
								// 增量方式必须要指定缓存名称
								if (StringUtil.isBlank(checherConfigModel.getCache())) {
									logger.error("translate update checker:{}  must config with cache=\"xxx\"!",
											nodeType);
									throw new IllegalArgumentException(nodeType + " must config with cache=\"xxx\"");
								}
							} else {
								checherConfigModel.setIncrement(false);
							}
							// sql模式且非sqlId模式定义
							if ("sql".equals(checherConfigModel.getType())) {
								if (StringUtil.isBlank(checherConfigModel.getSql())) {
									sqlId = (checherConfigModel.isIncrement() ? "s_trans_merge_chk_" : "s_trans_chk_")
											+ fileIndex + "_0" + index;
									sqlNode = elt.getElementsByTagName("sql");
									if (sqlNode.getLength() > 0) {
										sql = StringUtil.trim(sqlNode.item(0).getTextContent());
									} else {
										sql = StringUtil.trim(elt.getTextContent());
									}
									isShowSql = StringUtil.matches(sql, SqlToyConstants.NOT_PRINT_REGEX);
									SqlToyConfig sqlToyConfig = new SqlToyConfig(sqlId,
											SqlUtil.clearMistyChars(SqlUtil.clearMark(sql), " "));
									sqlToyConfig.setShowSql(!isShowSql);
									sqlToyConfig.setParamsName(
											SqlConfigParseUtils.getSqlParamsName(sqlToyConfig.getSql(null), true));
									// 增加条件参数检查,避免开发者手误然后找不到原因!有出现:lastUpdateTime 和 lastUpdateTimee 的找半天发现不了问题的!
									if (sqlToyConfig.getParamsName() != null
											&& sqlToyConfig.getParamsName().length > 1) {
										throw new IllegalArgumentException(
												"请检查缓存更新检测sql语句中的参数名称,所有参数名称要保持一致为lastUpdateTime!当前有:"
														+ sqlToyConfig.getParamsName().length + " 个不同条件参数名!请检查配置文件:"
														+ translateFileStr);
									}
									sqlToyContext.putSqlToyConfig(sqlToyConfig);
									checherConfigModel.setSql(sqlId);
									index++;
								}
							}
							checker.add(checherConfigModel);
							if (StringUtil.isNotBlank(checherConfigModel.getCache())) {
								if (cacheCheckers.contains(checherConfigModel.getCache())) {
									throw new RuntimeException("缓存翻译配置针对缓存:[" + checherConfigModel.getCache()
											+ "]的更新检测器已经存在!请检查文件:" + translateFileStr);
								} else {
									cacheCheckers.add(checherConfigModel.getCache());
								}
							}
							logger.debug("已经加载针对缓存:{} 更新的检测器,type={}", checherConfigModel.getCache(), translateType);
						}
					}
				}
				return defaultConfig;
			}
		});
	}

	/**
	 * @TODO 获取DTO或POJO中的@translate注解
	 * @param classType
	 * @return
	 */
	public static HashMap<String, Translate> getClassTranslates(Class classType) {
		if (classType == null || classType.equals(Map.class) || classType.equals(HashMap.class)
				|| classType.equals(List.class) || classType.equals(ArrayList.class) || classType.equals(Array.class)
				|| BeanUtil.isBaseDataType(classType)) {
			return null;
		}
		String className = classType.getName();
		// 利用Map对类中的缓存翻译配置进行缓存，规避每次都解析
		if (classTranslateConfigMap.containsKey(className)) {
			return classTranslateConfigMap.get(className);
		}
		HashMap<String, Translate> translateConfig = new HashMap<String, Translate>();
		org.sagacity.sqltoy.config.annotation.Translate translate;
		Class classVar = classType;
		while (classVar != null && !classVar.equals(Object.class)) {
			for (Field field : classVar.getDeclaredFields()) {
				translate = field.getAnnotation(org.sagacity.sqltoy.config.annotation.Translate.class);
				// 以子类注解为优先
				if (translate != null && !translateConfig.containsKey(field.getName())) {
					Translate trans = new Translate(translate.cacheName());
					trans.setIndex(translate.cacheIndex());
					if (StringUtil.isNotBlank(translate.cacheType())) {
						trans.setCacheType(translate.cacheType());
					}
					trans.setKeyColumn(translate.keyField());
					// 内部转了小写
					trans.setColumn(field.getName());
					trans.setAlias(field.getName());
					if (StringUtil.isNotBlank(translate.split())) {
						trans.setSplitRegex(translate.split());
					}
					// 默认是,逗号
					if (StringUtil.isNotBlank(translate.join())) {
						trans.setLinkSign(translate.join());
					}
					if (translate.uncached() != null && !"".equals(translate.uncached())) {
						trans.setUncached(translate.uncached().trim());
					}
					translateConfig.put(field.getName(), trans);
				}
			}
			// 向父类递归
			classVar = classVar.getSuperclass();
		}
		classTranslateConfigMap.put(className, translateConfig);
		return translateConfig;
	}

	/**
	 * @TODO 获取缓存配置文件集合
	 * @param translateConfig
	 * @return
	 * @throws Exception
	 */
	public static List getTranslateFiles(String translateConfig) throws Exception {
		List result = new ArrayList();
		if (StringUtil.isBlank(translateConfig)) {
			return result;
		}
		// 多个配置，支持classpath:sqltoy-translate.xml;translates 具体文件和路径两种方式
		String[] translateCfgs = translateConfig.replaceAll("\\,", ";").replaceAll("\\，", ";").replaceAll("\\；", ";")
				.split("\\;");
		String realRes;
		boolean startClasspath;
		Enumeration<URL> urls;
		URL url;
		JarFile jar;
		Enumeration<JarEntry> entries;
		JarEntry entry;
		String transConfigFile;
		File transFile;
		for (String translate : translateCfgs) {
			realRes = translate.trim();
			startClasspath = false;
			if (realRes.toLowerCase().startsWith(CLASSPATH)) {
				realRes = realRes.substring(10).trim();
				startClasspath = true;
			}
			urls = ScanEntityAndSqlResource.getResourceUrls(realRes, startClasspath);
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
							transConfigFile = entry.getName();
							if (transConfigFile.startsWith(realRes) && isTranslateConfig(transConfigFile)
									&& !entry.isDirectory()) {
								result.add(transConfigFile);
							}
						}
					} else {
						transFile = new File(url.toURI());
						String fileName = transFile.getName();
						// 取路径下的文件
						if (transFile.isDirectory()) {
							File[] files = transFile.listFiles();
							File file;
							for (int loop = 0; loop < files.length; loop++) {
								file = files[loop];
								fileName = file.getName();
								if (!file.isDirectory() && isTranslateConfig(fileName)) {
									result.add(file);
								}
							}
						} else if (isTranslateConfig(fileName)) {
							result.add(transFile);
						}
					}
				}
			}
		}
		return result;
	}

	/**
	 * @TODO 判断文件是否是缓存配置的xml文件，原则上建议.trans.xml格式命名
	 * @param fileName
	 * @return
	 */
	private static boolean isTranslateConfig(String fileName) {
		String lowFile = fileName.toLowerCase();
		if (lowFile.endsWith("-translate.xml") || lowFile.endsWith("-translates.xml") || lowFile.endsWith(".trans.xml")
				|| lowFile.endsWith(".translate.xml") || lowFile.endsWith(".translates.xml")) {
			return true;
		}
		return false;
	}
}
