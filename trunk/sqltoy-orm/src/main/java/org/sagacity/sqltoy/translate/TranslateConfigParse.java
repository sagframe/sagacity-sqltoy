/**
 * 
 */
package org.sagacity.sqltoy.translate;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;

import org.sagacity.sqltoy.SqlToyConstants;
import org.sagacity.sqltoy.SqlToyContext;
import org.sagacity.sqltoy.callback.XMLCallbackHandler;
import org.sagacity.sqltoy.config.SqlConfigParseUtils;
import org.sagacity.sqltoy.config.model.SqlToyConfig;
import org.sagacity.sqltoy.translate.model.CheckerConfigModel;
import org.sagacity.sqltoy.translate.model.DefaultConfig;
import org.sagacity.sqltoy.translate.model.TimeSection;
import org.sagacity.sqltoy.translate.model.TranslateConfigModel;
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
 * @project sagacity-sqltoy4.2
 * @description 用于解析缓存翻译的配置
 * @author chenrenfei <a href="mailto:zhongxuchen@gmail.com">联系作者</a>
 * @version id:TranslateConfigParse.java,Revision:v1.0,Date:2018年3月8日
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

	/**
	 * @todo 解析translate配置文件
	 * @param translateMap
	 * @param checker
	 * @param translateConfig
	 * @param charset
	 * @throws Exception
	 */
	public static DefaultConfig parseTranslateConfig(final SqlToyContext sqlToyContext,
			final HashMap<String, TranslateConfigModel> translateMap, final List<CheckerConfigModel> checker,
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
							if (translateType.equals("sql")) {
								if (StringUtil.isBlank(translateCacheModel.getSql())) {
									sqlNode = elt.getElementsByTagName("sql");
									if (sqlNode.getLength() > 0) {
										sql = StringUtil.trim(sqlNode.item(0).getTextContent());
									} else {
										sql = StringUtil.trim(elt.getTextContent());
									}
									sqlId = "sqltoy_translate_cache_00" + index;
									isShowSql = StringUtil.matches(sql, SqlToyConstants.NOT_PRINT_REGEX);
									SqlToyConfig sqlToyConfig = new SqlToyConfig(sqlId,
											StringUtil.clearMistyChars(SqlUtil.clearMark(sql), " "));
									sqlToyConfig.setShowSql(!isShowSql);
									sqlToyConfig.setParamsName(
											SqlConfigParseUtils.getSqlParamsName(sqlToyConfig.getSql(null), true));
									sqlToyContext.putSqlToyConfig(sqlToyConfig);
									translateCacheModel.setSql(sqlId);
									index++;
								}
							}
							// local 缓存 默认缓存不失效
							if (translateType.equals("local") && !elt.hasAttribute("keep-alive")) {
								translateCacheModel.setKeepAlive(-1);
							}
							translateMap.put(translateCacheModel.getCache(), translateCacheModel);
							logger.debug("已经加载缓存翻译:cache={},type={}", translateCacheModel.getCache(), translateType);
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
							// sql模式
							if (checherConfigModel.getType().equals("sql")) {
								if (StringUtil.isBlank(checherConfigModel.getSql())) {
									sqlId = (checherConfigModel.isIncrement() ? "sqltoy_translate_increment_check_00"
											: "sqltoy_translate_check_00") + index;
									sqlNode = elt.getElementsByTagName("sql");
									if (sqlNode.getLength() > 0) {
										sql = StringUtil.trim(sqlNode.item(0).getTextContent());
									} else {
										sql = StringUtil.trim(elt.getTextContent());
									}
									isShowSql = StringUtil.matches(sql, SqlToyConstants.NOT_PRINT_REGEX);
									SqlToyConfig sqlToyConfig = new SqlToyConfig(sqlId,
											StringUtil.clearMistyChars(SqlUtil.clearMark(sql), " "));
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
							String frequency = StringUtil.clearMistyChars(checherConfigModel.getCheckFrequency(), "");
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
							logger.debug("已经加载缓存更新检测器:type={}", translateType);
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
}
