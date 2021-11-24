package org.sagacity.sqltoy.translate;

import java.lang.reflect.Array;
import java.lang.reflect.Field;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import org.sagacity.sqltoy.SqlToyConstants;
import org.sagacity.sqltoy.SqlToyContext;
import org.sagacity.sqltoy.callback.XMLCallbackHandler;
import org.sagacity.sqltoy.config.SqlConfigParseUtils;
import org.sagacity.sqltoy.config.model.SqlToyConfig;
import org.sagacity.sqltoy.config.model.Translate;
import org.sagacity.sqltoy.model.IgnoreKeyCaseMap;
import org.sagacity.sqltoy.translate.model.CheckerConfigModel;
import org.sagacity.sqltoy.translate.model.DefaultConfig;
import org.sagacity.sqltoy.translate.model.TimeSection;
import org.sagacity.sqltoy.translate.model.TranslateConfigModel;
import org.sagacity.sqltoy.utils.BeanUtil;
import org.sagacity.sqltoy.utils.FileUtil;
import org.sagacity.sqltoy.utils.NumberUtil;
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
 */
public class TranslateConfigParse {
	/**
	 * 定义全局日志
	 */
	protected final static Logger logger = LoggerFactory.getLogger(TranslateConfigParse.class);

	private final static String TRANSLATE_SUFFIX = "-translate";
	private final static String CHECKER_SUFFIX = "-checker";
	private final static String[] TRANSLATE_TYPES = new String[] { "sql", "service", "rest", "local" };
	private final static String[] TRANSLATE_CHECKER_TYPES = new String[] { "sql-increment", "sql", "service-increment",
			"service", "rest", "rest-increment" };

	// 存放类包含缓存翻译注解配置
	private final static HashMap<String, HashMap<String, Translate>> classTranslateConfigMap = new HashMap<String, HashMap<String, Translate>>();

	/**
	 * @todo 解析translate配置文件
	 * @param sqlToyContext
	 * @param translateMap    最终缓存配置，构建一个空map，在解析过程中填充
	 * @param checker         更新检测配置
	 * @param translateConfig 缓存配置文件
	 * @param charset
	 * @return
	 * @throws Exception
	 */
	public static DefaultConfig parseTranslateConfig(final SqlToyContext sqlToyContext,
			final IgnoreKeyCaseMap<String, TranslateConfigModel> translateMap, final List<CheckerConfigModel> checker,
			String translateConfig, String charset) throws Exception {
		// 判断缓存翻译的配置文件是否存在
		if (FileUtil.getFileInputStream(translateConfig) == null) {
			logger.warn("缓存翻译配置文件:{}无法加载,请检查配路径正确性,如不使用缓存翻译可忽略此提示!", translateConfig);
			return null;
		}
		return (DefaultConfig) XMLUtil.readXML(translateConfig, charset, false, new XMLCallbackHandler() {
			@Override
			public Object process(Document doc, Element root) throws Exception {
				DefaultConfig defaultConfig = new DefaultConfig();
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
							if (translateType.equals("sql")) {
								if (StringUtil.isBlank(translateCacheModel.getSql())) {
									sqlNode = elt.getElementsByTagName("sql");
									if (sqlNode.getLength() > 0) {
										sql = StringUtil.trim(sqlNode.item(0).getTextContent());
									} else {
										sql = StringUtil.trim(elt.getTextContent());
									}
									sqlId = "s_trans_cache_0" + index;
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
							// local模式缓存 默认缓存不失效，表示缓存由开发者在应用程序中自行控制，sqltoy只做初始化构建(如ehcache创建一个缓存实例，但不加载数据)
							// local模式是避免一些额外争议的产物，有部分开发者坚持缓存要应用自己管理
							if (translateType.equals("local") && !elt.hasAttribute("keep-alive")) {
								translateCacheModel.setKeepAlive(-1);
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
							}
							// sql模式且非sqlId模式定义
							if (checherConfigModel.getType().equals("sql")) {
								if (StringUtil.isBlank(checherConfigModel.getSql())) {
									sqlId = (checherConfigModel.isIncrement() ? "s_trans_merge_chk_0" : "s_trans_chk_0")
											+ index;
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
														+ sqlToyConfig.getParamsName().length + " 个不同条件参数名!");
									}
									sqlToyContext.putSqlToyConfig(sqlToyConfig);
									checherConfigModel.setSql(sqlId);
									index++;
								}
							}
							// 剔除tab\回车等特殊字符
							String frequency = SqlUtil.clearMistyChars(checherConfigModel.getCheckFrequency(), "");
							List<TimeSection> timeSections = new ArrayList<TimeSection>();
							// frequency的格式 frequency="0..12?15,12..18:30?10,18:30..24?60"
							if (StringUtil.isNotBlank(frequency)) {
								// 统一格式,去除全角字符,去除空白
								frequency = StringUtil.toDBC(frequency).replaceAll("\\;", ",").trim();
								// 0~24点 统一的检测频率
								// 可以是单个频率值,表示0到24小时采用统一的频率
								if (NumberUtil.isInteger(frequency)) {
									TimeSection section = new TimeSection();
									section.setStart(0);
									section.setEnd(2400);
									section.setIntervalSeconds(Integer.parseInt(frequency));
									timeSections.add(section);
								} else {
									// 归整分割符号统一为逗号,将时间格式由HH:mm 转为HHmm格式
									String[] sectionsStr = frequency.split("\\,");
									for (int j = 0; j < sectionsStr.length; j++) {
										TimeSection section = new TimeSection();
										// 问号切割获取时间区间和时间间隔
										String[] sectionPhase = sectionsStr[j].split("\\?");
										// 获取开始和结束时间点
										String[] startEnd = sectionPhase[0].split("\\.{2}");
										section.setIntervalSeconds(Integer.parseInt(sectionPhase[1].trim()));
										section.setStart(getHourMinute(startEnd[0].trim()));
										section.setEnd(getHourMinute(startEnd[1].trim()));
										timeSections.add(section);
									}
								}
							}
							checherConfigModel.setTimeSections(timeSections);
							checker.add(checherConfigModel);
							logger.debug("已经加载针对缓存:{} 更新的检测器,type={}", checherConfigModel.getCache(), translateType);
						}
					}
				}
				return defaultConfig;
			}
		});

	}

	/**
	 * @todo 统一组成:1236[HHmm]格式
	 * @param hourMinuteStr
	 * @return
	 */
	private static int getHourMinute(String hourMinuteStr) {
		// 320(3点20分)
		if (NumberUtil.isInteger(hourMinuteStr) && hourMinuteStr.length() > 2) {
			return Integer.parseInt(hourMinuteStr);
		}
		String tmp = hourMinuteStr.replaceAll("\\.", ":");
		String[] hourMin = tmp.split("\\:");
		return Integer.parseInt(hourMin[0]) * 100 + ((hourMin.length > 1) ? Integer.parseInt(hourMin[1]) : 0);
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
		Field[] fields;
		while (classVar != null && !classVar.equals(Object.class)) {
			fields = classVar.getDeclaredFields();
			for (Field field : fields) {
				translate = field.getAnnotation(org.sagacity.sqltoy.config.annotation.Translate.class);
				// 以子类注解为优先
				if (translate != null && !translateConfig.containsKey(field.getName())) {
					Translate trans = new Translate(translate.cacheName());
					trans.setIndex(translate.cacheIndex());
					if (StringUtil.isNotBlank(translate.cacheType())) {
						trans.setCacheType(translate.cacheType());
					}
					trans.setKeyColumn(translate.keyField());
					trans.setColumn(field.getName());
					trans.setAlias(field.getName());
					if (StringUtil.isNotBlank(translate.split())) {
						trans.setSplitRegex(translate.split());
					}
					// 默认是,逗号
					if (StringUtil.isNotBlank(translate.join())) {
						trans.setLinkSign(translate.join());
					}
					if (translate.uncached() != null && !translate.uncached().equals("")) {
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
}
