package org.sagacity.sqltoy.config;

import static java.lang.System.out;

import java.io.ByteArrayInputStream;
import java.io.File;
import java.io.FileInputStream;
import java.io.InputStream;
import java.math.RoundingMode;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.concurrent.ConcurrentHashMap;
import java.util.regex.Pattern;

import javax.xml.parsers.DocumentBuilder;
import javax.xml.parsers.DocumentBuilderFactory;

import org.sagacity.sqltoy.SqlToyConstants;
import org.sagacity.sqltoy.config.model.CacheFilterModel;
import org.sagacity.sqltoy.config.model.ColsChainRelativeModel;
import org.sagacity.sqltoy.config.model.FormatModel;
import org.sagacity.sqltoy.config.model.LinkModel;
import org.sagacity.sqltoy.config.model.NoSqlConfigModel;
import org.sagacity.sqltoy.config.model.PageOptimize;
import org.sagacity.sqltoy.config.model.ParamFilterModel;
import org.sagacity.sqltoy.config.model.PivotModel;
import org.sagacity.sqltoy.config.model.ReverseModel;
import org.sagacity.sqltoy.config.model.RowsChainRelativeModel;
import org.sagacity.sqltoy.config.model.SecureMask;
import org.sagacity.sqltoy.config.model.ShardingStrategyConfig;
import org.sagacity.sqltoy.config.model.SqlToyConfig;
import org.sagacity.sqltoy.config.model.SqlType;
import org.sagacity.sqltoy.config.model.SummaryGroupMeta;
import org.sagacity.sqltoy.config.model.SummaryModel;
import org.sagacity.sqltoy.config.model.Translate;
import org.sagacity.sqltoy.config.model.UnpivotModel;
import org.sagacity.sqltoy.dialect.utils.PageOptimizeUtils;
import org.sagacity.sqltoy.model.IgnoreCaseSet;
import org.sagacity.sqltoy.model.TimeUnit;
import org.sagacity.sqltoy.plugins.function.FunctionUtils;
import org.sagacity.sqltoy.utils.BeanUtil;
import org.sagacity.sqltoy.utils.DataSourceUtils;
import org.sagacity.sqltoy.utils.ReservedWordsUtil;
import org.sagacity.sqltoy.utils.SqlUtil;
import org.sagacity.sqltoy.utils.StringUtil;
import org.sagacity.sqltoy.utils.XMLUtil;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.w3c.dom.Document;
import org.w3c.dom.Element;
import org.w3c.dom.Node;
import org.w3c.dom.NodeList;

/**
 * @project sagacity-sqltoy
 * @description 解析sql配置文件，不要纠结于xml解析的方式,后期已经部分使用xmlutil,但显式的解析更加清晰
 * @author zhongxuchen
 * @version v1.0,Date:2009-12-14
 * @modify Date:2011-8-30 {增加sql文件设置数据库类别功能，优化解决跨数据库sql文件的配置方式}
 * @modify Date:2018-1-1 {增加对es和mongo的查询配置解析支持}
 * @modify Date:2019-1-15 {增加cache-arg 和 to-in-arg 过滤器}
 * @modify Date:2020-3-27 {增加rows-chain-relative 和 cols-chain-relative
 *         环比计算功能,并优化unpivot解析改用XMLUtil类}
 * @modify Date:2020-7-2 {支持外部集成命名空间前缀适配解析,如报表集成定义了前缀s:filters等}
 */
@SuppressWarnings({ "rawtypes", "unchecked" })
public class SqlXMLConfigParse {
	/**
	 * 定义全局日志
	 */
	protected final static Logger logger = LoggerFactory.getLogger(SqlXMLConfigParse.class);

	/**
	 * es判断是否有聚合的表达式
	 */
	private final static Pattern ES_AGGS_PATTERN = Pattern
			.compile("(?i)\\W(\"|\')(aggregations|aggs)(\"|\')\\s*\\:\\s*\\{");

	// 判断mongo是否存在聚合
	private final static Pattern MONGO_AGGS_PATTERN = Pattern.compile("(?i)\\$group\\s*\\:");

	private final static Pattern GROUP_BY_PATTERN = Pattern.compile("(?i)\\Wgroup\\s+by\\W");

	private static DocumentBuilderFactory domFactory = DocumentBuilderFactory.newInstance();

	public static HashMap<String, String> filters = new HashMap<String, String>() {
		/**
		 * 
		 */
		private static final long serialVersionUID = 1636155921862321269L;
		{
			put("[", "]");
			put("{", "}");
		}
	};

	/**
	 * @todo 判断文件 是否被修改，修改了则重新解析文件重置缓存
	 * @param xmlFiles
	 * @param filesLastModifyMap
	 * @param cache
	 * @param encoding
	 * @param dialect
	 * @throws Exception
	 */
	public static void parseXML(List xmlFiles, ConcurrentHashMap<String, Long> filesLastModifyMap,
			ConcurrentHashMap<String, SqlToyConfig> cache, String encoding, String dialect) throws Exception {
		if (xmlFiles == null || xmlFiles.isEmpty()) {
			return;
		}
		File sqlFile;
		String fileName;
		Object resource;
		boolean isDebug = logger.isDebugEnabled();
		Long lastModified;
		Long preModified;
		for (int i = 0; i < xmlFiles.size(); i++) {
			resource = xmlFiles.get(i);
			if (resource instanceof File) {
				sqlFile = (File) resource;
				fileName = sqlFile.getName();
				synchronized (fileName) {
					lastModified = Long.valueOf(sqlFile.lastModified());
					// 调试模式，判断文件的最后修改时间，决定是否重新加载sql
					preModified = filesLastModifyMap.get(fileName);
					// 最后修改时间比上次修改时间大，重新加载sql文件
					if (preModified == null || lastModified.longValue() > preModified.longValue()) {
						filesLastModifyMap.put(fileName, lastModified);
						if (isDebug) {
							logger.debug("sql文件:{}已经被修改,进行重新解析!", fileName);
						} else {
							out.println("sql文件:" + fileName + " 已经被修改,进行重新解析!");
						}
						parseSingleFile(sqlFile, filesLastModifyMap, cache, encoding, dialect, true, -1);
					}
				}
			}
		}
	}

	/**
	 * @todo <b>解析单个sql对应的xml文件</b>
	 * @param xmlFile
	 * @param filesLastModifyMap
	 * @param cache
	 * @param encoding
	 * @param dialect
	 * @param isReload
	 * @param index
	 * @return
	 * @throws Exception
	 */
	public static List<String> parseSingleFile(Object xmlFile, ConcurrentHashMap<String, Long> filesLastModifyMap,
			ConcurrentHashMap<String, SqlToyConfig> cache, String encoding, String dialect, boolean isReload, int index)
			throws Exception {
		InputStream fileIS = null;
		List<String> repeatSql = new ArrayList<String>();
		try {
			boolean isDebug = logger.isDebugEnabled();
			String sqlFile;
			if (xmlFile instanceof File) {
				File file = (File) xmlFile;
				sqlFile = file.getName();
				filesLastModifyMap.put(sqlFile, Long.valueOf(file.lastModified()));
				fileIS = new FileInputStream(file);
			} else {
				sqlFile = (String) xmlFile;
				fileIS = getResourceAsStream(sqlFile);
			}
			String logStr = "正在解析".concat((index != -1) ? "第:[" + index + "]个" : "").concat("sql文件:").concat(sqlFile);
			if (isDebug) {
				logger.debug(logStr);
			} else {
				out.println(logStr);
			}
			if (fileIS != null) {
				domFactory.setFeature(SqlToyConstants.XML_FETURE, false);
				DocumentBuilder domBuilder = domFactory.newDocumentBuilder();
				Document doc = domBuilder.parse(fileIS);
				NodeList sqlElts = doc.getDocumentElement().getChildNodes();
				if (sqlElts == null || sqlElts.getLength() == 0) {
					return repeatSql;
				}
				// 解析单个sql
				SqlToyConfig sqlToyConfig;
				Element sqlElt;
				Node obj;
				for (int i = 0; i < sqlElts.getLength(); i++) {
					obj = sqlElts.item(i);
					if (obj.getNodeType() == Node.ELEMENT_NODE) {
						sqlElt = (Element) obj;
						sqlToyConfig = parseSingleSql(sqlElt, dialect);
						if (sqlToyConfig != null) {
							// 去除sql中的注释语句并放入缓存
							if (cache.containsKey(sqlToyConfig.getId())) {
								repeatSql.add(StringUtil.fillArgs("sql文件:{} 中发现重复的SQL语句id={} 已经被覆盖!", sqlFile,
										sqlToyConfig.getId()));
								// 移除分页优化缓存
								if (isReload) {
									PageOptimizeUtils.remove(sqlToyConfig.getId());
								}
							}
							cache.put(sqlToyConfig.getId(), sqlToyConfig);
						}
					}
				}
			}
		} catch (Exception e) {
			e.printStackTrace();
			logger.error(
					"解析xml中对应的sql失败,对应文件={},正确的配置为<sql|mql|eql id=\"\"><![CDATA[]]></sql|mql|eql>或<sql|mql|eql id=\"\"><desc></desc><value><![CDATA[]]></value></sql|mql|eql>",
					xmlFile, e);
			throw e;
		} finally {
			if (fileIS != null) {
				fileIS.close();
			}
		}
		return repeatSql;
	}

	/**
	 * @todo 解析单个sql片段
	 * @param sqlSegment
	 * @param encoding
	 * @param dialect
	 * @return
	 * @throws Exception
	 */
	public static SqlToyConfig parseSagment(Object sqlSegment, String encoding, String dialect) throws Exception {
		Element elt = null;
		if (sqlSegment instanceof String) {
			Document doc = domFactory.newDocumentBuilder().parse(
					new ByteArrayInputStream(((String) sqlSegment).getBytes(encoding == null ? "UTF-8" : encoding)));
			elt = doc.getDocumentElement();
		} else if (sqlSegment instanceof Element) {
			elt = (Element) sqlSegment;
		}
		if (elt == null) {
			logger.error("sqlSegment type must is String or org.w3c.dom.Element!");
			throw new IllegalArgumentException("sqlSegment type must is String or org.w3c.dom.Element!");
		}
		return parseSingleSql(elt, dialect);
	}

	/**
	 * @TODO 获取sql xml element的namespace前缀
	 * @param sqlElement
	 * @return
	 */
	private static String getElementPrefixName(Element sqlElement) {
		NodeList nodeList = sqlElement.getChildNodes();
		Node node;
		String nodeName = null;
		int index;
		for (int i = 0; i < nodeList.getLength(); i++) {
			node = nodeList.item(i);
			if (node.getNodeType() == Node.ELEMENT_NODE) {
				nodeName = node.getNodeName();
				index = nodeName.indexOf(":");
				if (index > 0) {
					nodeName = nodeName.substring(0, index);
				} else {
					nodeName = null;
				}
				break;
			}
		}
		return nodeName;
	}

	/**
	 * @todo 解析单个sql element元素,update 2020-7-2 支持外部集成命名空间前缀适配
	 * @param sqlElt
	 * @param dialect
	 * @return
	 * @throws Exception
	 */
	public static SqlToyConfig parseSingleSql(Element sqlElt, String dialect) throws Exception {
		String realDialect = dialect;
		String nodeName = sqlElt.getNodeName().toLowerCase();
		// 剔除前缀
		int prefixIndex = nodeName.indexOf(":");
		if (prefixIndex > 0) {
			nodeName = nodeName.substring(prefixIndex + 1);
		}
		// 目前只支持传统sql、elastic、mongo三种类型的语法
		if (!nodeName.equals("sql") && !nodeName.equals("eql") && !nodeName.equals("mql")) {
			return null;
		}
		String id = sqlElt.getAttribute("id");
		if (id == null) {
			throw new RuntimeException("请检查sql配置,没有给定sql对应的 id值!");
		}
		// 获取元素的namespace前缀
		String localName = getElementPrefixName(sqlElt);
		String local = StringUtil.isBlank(localName) ? "" : localName.concat(":");
		// 判断是否xml为精简模式即只有<sql id=""><![CDATA[]]></sql>模式
		NodeList nodeList = sqlElt.getElementsByTagName(local.concat("value"));
		String sqlContent = null;
		if (nodeList.getLength() > 0) {
			sqlContent = StringUtil.trim(nodeList.item(0).getTextContent());
		} else {
			sqlContent = StringUtil.trim(sqlElt.getTextContent());
		}
		if (StringUtil.isBlank(sqlContent)) {
			throw new RuntimeException("请检查sql-id='" + id + "' 的配置,没有正确填写sql内容!");
		}
		nodeList = sqlElt.getElementsByTagName(local.concat("count-sql"));
		String countSql = null;
		if (nodeList.getLength() > 0) {
			countSql = StringUtil.trim(nodeList.item(0).getTextContent());
		}
		// 替换全角空格
		sqlContent = sqlContent.replaceAll("\u3000", " ");
		if (countSql != null) {
			countSql = countSql.replaceAll("\u3000", " ");
		}
		SqlType type = sqlElt.hasAttribute("type") ? SqlType.getSqlType(sqlElt.getAttribute("type")) : SqlType.search;
		// 是否nosql模式
		boolean isNoSql = false;
		if (nodeName.equals("mql") || nodeName.equals("eql")) {
			if (nodeName.equals("mql")) {
				realDialect = DataSourceUtils.Dialect.MONGO;
			} else if (nodeName.equals("eql")) {
				realDialect = DataSourceUtils.Dialect.ES;
			}
			isNoSql = true;
		}
		SqlToyConfig sqlToyConfig = SqlConfigParseUtils.parseSqlToyConfig(sqlContent, realDialect, type);
		// debug 控制台输出sql执行日志
		if (sqlElt.hasAttribute("debug")) {
			sqlToyConfig.setShowSql(Boolean.valueOf(sqlElt.getAttribute("debug")));
		}
		sqlToyConfig.setId(id);
		sqlToyConfig.setSqlType(type);
		// 为sql提供特定数据库的扩展
		if (sqlElt.hasAttribute("dataSource")) {
			sqlToyConfig.setDataSource(sqlElt.getAttribute("dataSource"));
		} else if (sqlElt.hasAttribute("datasource")) {
			sqlToyConfig.setDataSource(sqlElt.getAttribute("datasource"));
		}
		if (countSql != null) {
			// 清理sql中的一些注释、以及特殊的符号
			countSql = SqlUtil.clearMistyChars(SqlUtil.clearMark(countSql), " ").concat(" ");
			countSql = FunctionUtils.getDialectSql(countSql, dialect);
			countSql = ReservedWordsUtil.convertSql(countSql, DataSourceUtils.getDBType(dialect));
			sqlToyConfig.setCountSql(countSql);
		}
		// 是否是单纯的union all分页(在取count记录数时,将union all 每部分的查询from前面的全部替换成
		// select 1 from,减少不必要的执行运算，提升效率)
		if (sqlElt.hasAttribute("union-all-count")) {
			sqlToyConfig.setUnionAllCount(Boolean.parseBoolean(sqlElt.getAttribute("union-all-count")));
		}
		// 解析sql对应dataSource的sharding配置
		parseShardingDataSource(sqlToyConfig, sqlElt.getElementsByTagName(local.concat("sharding-datasource")));

		// 解析sql对应的table的sharding配置
		parseShardingTables(sqlToyConfig, sqlElt.getElementsByTagName(local.concat("sharding-table")));
		// 解析格式化
		parseFormat(sqlToyConfig, sqlElt.getElementsByTagName(local.concat("date-format")),
				sqlElt.getElementsByTagName(local.concat("number-format")));
		// 参数值为空白是否当中null处理,默认为-1
		int blankToNull = -1;
		if (sqlElt.hasAttribute("blank-to-null")) {
			blankToNull = (Boolean.parseBoolean(sqlElt.getAttribute("blank-to-null"))) ? 1 : 0;
		}
		// 参数加工过滤器
		nodeList = sqlElt.getElementsByTagName(local.concat("filters"));
		// 解析参数过滤器
		if (nodeList.getLength() > 0) {
			parseFilters(sqlToyConfig, nodeList.item(0).getChildNodes(), blankToNull, local);
		} else {
			parseFilters(sqlToyConfig, null, blankToNull, local);
		}

		// 解析分页优化器
		// <page-optimize parallel="true" alive-max="100" alive-seconds="90"
		// parallel-maxwait-seconds="600"/>
		nodeList = sqlElt.getElementsByTagName(local.concat("page-optimize"));
		if (nodeList.getLength() > 0) {
			PageOptimize optimize = new PageOptimize();
			Element pageOptimize = (Element) nodeList.item(0);
			// 保留不同条件的count缓存记录量
			if (pageOptimize.hasAttribute("alive-max")) {
				optimize.aliveMax(Integer.parseInt(pageOptimize.getAttribute("alive-max")));
			}
			// 不同sql条件分页记录数量保存有效时长(默认90秒)
			if (pageOptimize.hasAttribute("alive-seconds")) {
				optimize.aliveSeconds(Integer.parseInt(pageOptimize.getAttribute("alive-seconds")));
			}
			// 是否支持并行查询
			if (pageOptimize.hasAttribute("parallel")) {
				optimize.parallel(Boolean.parseBoolean(pageOptimize.getAttribute("parallel")));
			}
			// 最大并行等待时长(秒)
			if (pageOptimize.hasAttribute("parallel-maxwait-seconds")) {
				optimize.parallelMaxWaitSeconds(Long.parseLong(pageOptimize.getAttribute("parallel-maxwait-seconds")));
			}
			sqlToyConfig.setPageOptimize(optimize);
		}

		// 解析翻译器
		parseTranslate(sqlToyConfig, sqlElt.getElementsByTagName(local.concat("translate")));
		// 解析link
		parseLink(sqlToyConfig, sqlElt.getElementsByTagName(local.concat("link")), local);
		// 解析对结果的运算
		parseCalculator(sqlToyConfig, sqlElt, local);
		// 解析解密字段
		parseSecureDecrypt(sqlToyConfig, sqlElt.getElementsByTagName(local.concat("secure-decrypt")));
		// 解析安全脱敏配置
		parseSecureMask(sqlToyConfig, sqlElt.getElementsByTagName(local.concat("secure-mask")));
		// mongo/elastic查询语法
		if (isNoSql) {
			parseNoSql(sqlToyConfig, sqlElt, local);
		}
		return sqlToyConfig;
	}

	/**
	 * @todo 解析nosql的相关配置
	 * @param sqlToyConfig
	 * @param sqlElt
	 * @param local
	 */
	private static void parseNoSql(SqlToyConfig sqlToyConfig, Element sqlElt, String local) {
		NoSqlConfigModel noSqlConfig = new NoSqlConfigModel();
		NodeList nodeList;
		// mongo 的collection
		if (sqlElt.hasAttribute("collection")) {
			noSqlConfig.setCollection(sqlElt.getAttribute("collection"));
		}

		// url应该是一个变量如:${es_url}
		if (sqlElt.hasAttribute("url")) {
			noSqlConfig.setEndpoint(SqlToyConstants.replaceParams(sqlElt.getAttribute("url")));
		} else if (sqlElt.hasAttribute("end-point")) {
			noSqlConfig.setEndpoint(SqlToyConstants.replaceParams(sqlElt.getAttribute("end-point")));
		} else if (sqlElt.hasAttribute("endpoint")) {
			noSqlConfig.setEndpoint(SqlToyConstants.replaceParams(sqlElt.getAttribute("endpoint")));
		}
		// 索引
		if (sqlElt.hasAttribute("index")) {
			noSqlConfig.setIndex(sqlElt.getAttribute("index"));
		}
		// es 索引类型
		if (sqlElt.hasAttribute("type")) {
			noSqlConfig.setType(sqlElt.getAttribute("type"));
		}
		// 请求超时时间(单位毫秒)
		if (sqlElt.hasAttribute("request-timeout")) {
			noSqlConfig.setRequestTimeout(Integer.parseInt(sqlElt.getAttribute("request-timeout")));
		}
		// 连接超时时间(单位毫秒)
		if (sqlElt.hasAttribute("connection-timeout")) {
			noSqlConfig.setConnectTimeout(Integer.parseInt(sqlElt.getAttribute("connection-timeout")));
		}

		// 整个请求超时时长(毫秒)
		if (sqlElt.hasAttribute("socket-timeout")) {
			noSqlConfig.setSocketTimeout(Integer.parseInt(sqlElt.getAttribute("socket-timeout")));
		}

		// url请求字符集类型
		if (sqlElt.hasAttribute("charset")) {
			noSqlConfig.setCharset(sqlElt.getAttribute("charset"));
		}
		// fields
		if (sqlElt.hasAttribute("fields")) {
			if (StringUtil.isNotBlank(sqlElt.getAttribute("fields"))) {
				noSqlConfig.setFields(splitFields(sqlElt.getAttribute("fields")));
			}
		} else {
			nodeList = sqlElt.getElementsByTagName(local.concat("fields"));
			if (nodeList.getLength() > 0) {
				noSqlConfig.setFields(splitFields(nodeList.item(0).getTextContent()));
			}
		}

		// valueRoot
		if (sqlElt.hasAttribute("value-root")) {
			noSqlConfig.setValueRoot(trimParams(sqlElt.getAttribute("value-root").split("\\,")));
		} else if (sqlElt.hasAttribute("value-path")) {
			noSqlConfig.setValueRoot(trimParams(sqlElt.getAttribute("value-path").split("\\,")));
		}
		String nodeName = sqlElt.getNodeName().toLowerCase();
		// 是否有聚合查询
		if (nodeName.equals("eql")) {
			if (sqlElt.hasAttribute("aggregate")) {
				noSqlConfig.setHasAggs(Boolean.parseBoolean(sqlElt.getAttribute("aggregate")));
			} else if (sqlElt.hasAttribute("is-aggregate")) {
				noSqlConfig.setHasAggs(Boolean.parseBoolean(sqlElt.getAttribute("is-aggregate")));
			} else {
				noSqlConfig.setHasAggs(StringUtil.matches(sqlToyConfig.getSql(null), ES_AGGS_PATTERN));
			}
			// 判断查询语句的模式是否sql模式
			if (StringUtil.matches(sqlToyConfig.getSql(null), "(?i)\\s*select\\s*")
					&& sqlToyConfig.getSql(null).toLowerCase().indexOf("from") > 0) {
				noSqlConfig.setSqlMode(true);
				// sql模式下存在group by 则判定为聚合查询
				if (StringUtil.matches(sqlToyConfig.getSql(null), GROUP_BY_PATTERN)) {
					noSqlConfig.setHasAggs(true);
				}
			}
		} else if (nodeName.equals("mql")) {
			if (sqlElt.hasAttribute("aggregate")) {
				noSqlConfig.setHasAggs(Boolean.parseBoolean(sqlElt.getAttribute("aggregate")));
			} else if (sqlElt.hasAttribute("is-aggregate")) {
				noSqlConfig.setHasAggs(Boolean.parseBoolean(sqlElt.getAttribute("is-aggregate")));
			} else {
				noSqlConfig.setHasAggs(StringUtil.matches(sqlToyConfig.getSql(null), MONGO_AGGS_PATTERN));
			}
		}

		sqlToyConfig.setNoSqlConfigModel(noSqlConfig);
		// nosql参数解析模式不同于sql
		if (!noSqlConfig.isSqlMode()) {
			sqlToyConfig.setParamsName(SqlConfigParseUtils.getNoSqlParamsName(sqlToyConfig.getSql(null), true));
		}
	}

	/**
	 * @TODO 解析解密字段
	 * @param sqlToyConfig
	 * @param decryptElts
	 */
	public static void parseSecureDecrypt(SqlToyConfig sqlToyConfig, NodeList decryptElts) {
		if (decryptElts == null || decryptElts.getLength() == 0) {
			return;
		}
		Element decryptElt = (Element) decryptElts.item(0);
		if (decryptElt.hasAttribute("columns")) {
			String[] columns = trimParams(decryptElt.getAttribute("columns").toLowerCase().split("\\,"));
			IgnoreCaseSet decryptColumns = new IgnoreCaseSet();
			for (String col : columns) {
				decryptColumns.add(col);
			}
			sqlToyConfig.setDecryptColumns(decryptColumns);
		}
	}

	/**
	 * @todo 解析安全脱敏配置
	 * @param sqlToyConfig
	 * @param maskElts
	 */
	public static void parseSecureMask(SqlToyConfig sqlToyConfig, NodeList maskElts) {
		if (maskElts == null || maskElts.getLength() == 0) {
			return;
		}
		// <secure-mask columns="" type="name" head-size="" tail-size=""
		// mask-code="*****" mask-rate="50%"/>
		List<SecureMask> secureMasks = new ArrayList<SecureMask>();
		String tmp;
		Element elt;
		for (int i = 0; i < maskElts.getLength(); i++) {
			elt = (Element) maskElts.item(i);
			tmp = getAttrValue(elt, "columns");
			// 兼容老版本
			if (tmp == null) {
				tmp = getAttrValue(elt, "column");
			}
			String[] columns = trimParams(tmp.toLowerCase().split("\\,"));
			String type = getAttrValue(elt, "type").toLowerCase();
			String maskCode = getAttrValue(elt, "mask-code");
			String headSize = getAttrValue(elt, "head-size");
			String tailSize = getAttrValue(elt, "tail-size");
			String maskRate = getAttrValue(elt, "mask-rate");
			if (maskRate == null) {
				maskRate = getAttrValue(elt, "mask-percent");
			}
			// 剔除百分号
			if (maskRate != null) {
				maskRate = maskRate.replace("%", "").trim();
			}
			for (String col : columns) {
				SecureMask secureMask = new SecureMask();
				secureMask.setColumn(col);
				secureMask.setType(type);
				secureMask.setMaskCode(maskCode);
				if (secureMask.getMaskCode() == null) {
					if (secureMask.getType().equals("id-card") || secureMask.getType().equals("bank-card")
							|| secureMask.getType().equals("email") || secureMask.getType().equals("address")
							|| secureMask.getType().equals("address")) {
						secureMask.setMaskCode("******");
					} else if (secureMask.getType().equals("name")) {
						secureMask.setMaskCode("**");
					} else {
						secureMask.setMaskCode("****");
					}
				}
				if (StringUtil.isNotBlank(headSize)) {
					secureMask.setHeadSize(Integer.parseInt(headSize));
				}
				if (StringUtil.isNotBlank(tailSize)) {
					secureMask.setTailSize(Integer.parseInt(tailSize));
				}
				if (StringUtil.isNotBlank(maskRate)) {
					// 小数
					if (Double.parseDouble(maskRate) < 1) {
						secureMask.setMaskRate(Double.valueOf(Double.parseDouble(maskRate) * 100).intValue());
					} else {
						secureMask.setMaskRate(Double.valueOf(maskRate).intValue());
					}
				}
				secureMasks.add(secureMask);
			}
		}
		sqlToyConfig.setSecureMasks(secureMasks);
	}

	/**
	 * @TODO 获取xml元素的属性值
	 * @param elt
	 * @param attrName
	 * @return
	 */
	private static String getAttrValue(Element elt, String attrName) {
		if (elt.hasAttribute(attrName)) {
			return elt.getAttribute(attrName);
		}
		return null;
	}

	/**
	 * @todo 解析dataSource的sharding
	 * @param sqlToyConfig
	 * @param shardingDBNode
	 */
	private static void parseShardingDataSource(SqlToyConfig sqlToyConfig, NodeList shardingDBNode) {
		if (shardingDBNode == null || shardingDBNode.getLength() == 0) {
			return;
		}
		Element shardingDataSource = (Element) shardingDBNode.item(0);
		ShardingStrategyConfig shardingConfig = new ShardingStrategyConfig(0);
		// 策略辨别值
		if (shardingDataSource.hasAttribute("strategy-value")) {
			shardingConfig.setDecisionType(shardingDataSource.getAttribute("strategy-value"));
		} else if (shardingDataSource.hasAttribute("strategy-type")) {
			shardingConfig.setDecisionType(shardingDataSource.getAttribute("strategy-type"));
		} else if (shardingDataSource.hasAttribute("decision-type")) {
			shardingConfig.setDecisionType(shardingDataSource.getAttribute("decision-type"));
		}
		// 全部参数
		List<String> params = new ArrayList<String>();
		if (shardingDataSource.hasAttribute("params")) {
			String[] fields = shardingDataSource.getAttribute("params").replace(";", ",").toLowerCase().split("\\,");
			int size = fields.length;
			String[] paramsAlias = new String[size];
			String[] paramName;
			for (int i = 0; i < size; i++) {
				paramName = fields[i].split("\\:");
				fields[i] = paramName[0].trim();
				if (!params.contains(fields[i])) {
					params.add(fields[i]);
				}
				paramsAlias[i] = paramName[paramName.length - 1].trim();
			}
			shardingConfig.setFields(fields);
			shardingConfig.setAliasNames(paramsAlias);
		}
		if (!params.isEmpty()) {
			String[] paramAry = new String[params.size()];
			params.toArray(paramAry);
			sqlToyConfig.setDbShardingParams(paramAry);
		}
		shardingConfig.setStrategy(shardingDataSource.getAttribute("strategy"));
		sqlToyConfig.setDataSourceSharding(shardingConfig);
	}

	/**
	 * @todo 解析table的sharding
	 * @param sqlToyConfig
	 * @param shardingTables
	 */
	private static void parseShardingTables(SqlToyConfig sqlToyConfig, NodeList shardingTables) {
		if (shardingTables == null || shardingTables.getLength() == 0) {
			return;
		}
		List<ShardingStrategyConfig> tablesShardings = new ArrayList();
		String[] paramName;
		String[] paramsAlias;
		int size;
		Element elt;
		// 全部参数
		List<String> params = new ArrayList<String>();
		for (int i = 0; i < shardingTables.getLength(); i++) {
			elt = (Element) shardingTables.item(i);
			if (elt.hasAttribute("tables") && elt.hasAttribute("strategy")) {
				ShardingStrategyConfig shardingModel = new ShardingStrategyConfig(1);
				shardingModel.setTables(trimParams(elt.getAttribute("tables").split("\\,")));
				String[] fields;
				if (elt.hasAttribute("params")) {
					fields = elt.getAttribute("params").replace(";", ",").toLowerCase().split("\\,");
					// params="a:a1,b:b1";params为{a:a1, b:b1}
					size = fields.length;
					paramsAlias = new String[size];
					for (int j = 0; j < size; j++) {
						paramName = fields[j].split("\\:");
						// 重置params数组值
						fields[j] = paramName[0].trim();
						if (!params.contains(fields[j])) {
							params.add(fields[j]);
						}
						paramsAlias[j] = paramName[paramName.length - 1].trim();
					}
					shardingModel.setFields(fields);
					shardingModel.setAliasNames(paramsAlias);
				}
				if (elt.hasAttribute("strategy-value")) {
					shardingModel.setDecisionType(elt.getAttribute("strategy-value"));
				} else if (elt.hasAttribute("strategy-type")) {
					shardingModel.setDecisionType(elt.getAttribute("strategy-type"));
				} else if (elt.hasAttribute("decision-type")) {
					shardingModel.setDecisionType(elt.getAttribute("decision-type"));
				}
				shardingModel.setStrategy(elt.getAttribute("strategy"));
				tablesShardings.add(shardingModel);
			}
		}
		if (!params.isEmpty()) {
			String[] paramAry = new String[params.size()];
			params.toArray(paramAry);
			sqlToyConfig.setTableShardingParams(paramAry);
		}
		sqlToyConfig.setTableShardings(tablesShardings);
	}

	/**
	 * @todo 解析3.0版本 filters xml元素
	 * @param sqlToyConfig
	 * @param filterSet
	 * @param blankToNull
	 * @param local        命名空间前缀
	 */
	private static void parseFilters(SqlToyConfig sqlToyConfig, NodeList filterSet, int blankToNull, String local) {
		List<ParamFilterModel> filterModels = new ArrayList<ParamFilterModel>();
		// 1:强制将空白当做null;0:强制对空白不作为null处理;-1:默认值,用户不配置blank过滤器则视同为1,配置了则视同为0
		if (blankToNull == 1) {
			filterModels.add(new ParamFilterModel("blank", new String[] { "*" }));
		}
		boolean hasBlank = false;
		if (filterSet != null && filterSet.getLength() > 0) {
			String filterType;
			boolean blank = false;
			Element filter;
			int prefixIndex;
			for (int i = 0; i < filterSet.getLength(); i++) {
				if (filterSet.item(i).getNodeType() == Node.ELEMENT_NODE) {
					filter = (Element) filterSet.item(i);
					blank = false;
					// 剔除xml命名空间的前缀部分
					filterType = filter.getNodeName();
					prefixIndex = filterType.indexOf(":");
					if (prefixIndex > 0) {
						filterType = filterType.substring(prefixIndex + 1);
					}
					// 当开发者配置了blank过滤器时，则表示关闭默认将全部空白当做null处理的逻辑
					if (filterType.equals("blank")) {
						hasBlank = true;
						blank = true;
					}
					// [非强制且是blank ] 或者 [ 非blank]
					if ((blank && blankToNull != 1) || !blank) {
						ParamFilterModel filterModel = new ParamFilterModel();
						// 统一过滤的类别,避免不同版本和命名差异
						if (filterType.equals("equals") || filterType.equals("any") || filterType.equals("in")) {
							filterType = "eq";
						} else if (filterType.equals("moreThan") || filterType.equals("more")) {
							filterType = "gt";
						} else if (filterType.equals("moreEquals") || filterType.equals("more-equals")) {
							filterType = "gte";
						} else if (filterType.equals("lessThan") || filterType.equals("less")) {
							filterType = "lt";
						} else if (filterType.equals("lessEquals") || filterType.equals("less-equals")) {
							filterType = "lte";
						} else if (filterType.equals("not-any")) {
							filterType = "neq";
						} else if (filterType.equals("dateFormat")) {
							filterType = "date-format";
						}
						filterModel.setFilterType(filterType);
						parseFilterElt(sqlToyConfig, filterModel, filter, local);
						filterModels.add(filterModel);
					}
				}
			}
		}
		// 当没有特定配置时，默认将所有空白当做null处理
		if (!hasBlank && blankToNull == -1) {
			filterModels.add(0, new ParamFilterModel("blank", new String[] { "*" }));
		}
		if (filterModels.isEmpty()) {
			return;
		}
		sqlToyConfig.addFilters(filterModels);
	}

	/**
	 * @todo 解析filter
	 * @param sqlToyConfig
	 * @param filterModel
	 * @param filter
	 * @param local
	 */
	private static void parseFilterElt(SqlToyConfig sqlToyConfig, ParamFilterModel filterModel, Element filter,
			String local) {
		// 没有设置参数名称，则表示全部参数用*表示
		if (!filter.hasAttribute("params")) {
			filterModel.setParams(new String[] { "*" });
		} else {
			filterModel.setParams(trimParams(filter.getAttribute("params").toLowerCase().split("\\,")));
		}
		// equals\any\not-any等类型
		if (filter.hasAttribute("value")) {
			filterModel.setValues(
					StringUtil.splitExcludeSymMark(filter.getAttribute("value"), ",", SqlToyConstants.filters));
		} else if (filter.hasAttribute("start-value") && filter.hasAttribute("end-value")) {
			filterModel
					.setValues(new String[] { filter.getAttribute("start-value"), filter.getAttribute("end-value") });
		}

		// 解析to-date 的加减操作
		if (filter.hasAttribute("increment-time")) {
			filterModel.setIncrementTime(Double.valueOf(filter.getAttribute("increment-time")));
		} // 兼容老版本
		else if (filter.hasAttribute("increment-days")) {
			filterModel.setIncrementTime(Double.valueOf(filter.getAttribute("increment-days")));
		}
		if (filter.hasAttribute("increment-unit")) {
			String timeUnit = filter.getAttribute("increment-unit").toUpperCase();
			if (timeUnit.equals("DAYS") || timeUnit.equals("DAY")) {
				filterModel.setTimeUnit(TimeUnit.DAYS);
			} else if (timeUnit.equals("HOURS") || timeUnit.equals("HOUR")) {
				filterModel.setTimeUnit(TimeUnit.HOURS);
			} else if (timeUnit.equals("MINUTES") || timeUnit.equals("MINUTE")) {
				filterModel.setTimeUnit(TimeUnit.MINUTES);
			} else if (timeUnit.equals("SECONDS") || timeUnit.equals("SECOND")) {
				filterModel.setTimeUnit(TimeUnit.SECONDS);
			} else if (timeUnit.equals("MILLISECONDS") || timeUnit.equals("MILLISECOND")) {
				filterModel.setTimeUnit(TimeUnit.MILLISECONDS);
			} else if (timeUnit.equals("MONTHS") || timeUnit.equals("MONTH")) {
				filterModel.setTimeUnit(TimeUnit.MONTHS);
			} else if (timeUnit.equals("YEARS") || timeUnit.equals("YEAR")) {
				filterModel.setTimeUnit(TimeUnit.YEARS);
			}
		}

		// to-date filter
		if (filter.hasAttribute("format")) {
			String fmt = filter.getAttribute("format");
			// 规整toDate的格式
			if (fmt.equalsIgnoreCase("first_day") || fmt.equalsIgnoreCase("first_month_day")) {
				filterModel.setFormat("FIRST_OF_MONTH");
			} else if (fmt.equalsIgnoreCase("last_day") || fmt.equalsIgnoreCase("last_month_day")) {
				filterModel.setFormat("LAST_OF_MONTH");
			} else if (fmt.equalsIgnoreCase("first_year_day")) {
				filterModel.setFormat("FIRST_OF_YEAR");
			} else if (fmt.equalsIgnoreCase("last_year_day")) {
				filterModel.setFormat("LAST_OF_YEAR");
			} else if (fmt.equalsIgnoreCase("first_week_day")) {
				filterModel.setFormat("FIRST_OF_WEEK");
			} else if (fmt.equalsIgnoreCase("last_week_day")) {
				filterModel.setFormat("LAST_OF_WEEK");
			} else {
				filterModel.setFormat(fmt);
			}
		}
		// to-date 中设置type类型
		if (filter.hasAttribute("type")) {
			filterModel.setType(filter.getAttribute("type").toLowerCase());
		}
		// regex(replace filter)
		if (filter.hasAttribute("regex")) {
			filterModel.setRegex(filter.getAttribute("regex"));
		}

		// 用于replace 转换器,设置是否是替换首个匹配的字符
		if (filter.hasAttribute("is-first")) {
			filterModel.setFirst(Boolean.parseBoolean(filter.getAttribute("is-first")));
		}
		// 用于to-in-arg
		if (filter.hasAttribute("single-quote")) {
			filterModel.setSingleQuote(Boolean.parseBoolean(filter.getAttribute("single-quote")));
		}
		// 分割符号
		if (filter.hasAttribute("split-sign")) {
			filterModel.setSplit(filter.getAttribute("split-sign"));
		}

		// 互斥型和决定性(primary)filter的参数
		if (filter.hasAttribute("excludes")) {
			String[] excludeParams = filter.getAttribute("excludes").toLowerCase().split("\\,");
			for (String excludeParam : excludeParams) {
				filterModel.addExclude(excludeParam.trim());
			}
		}

		// exclusive 和primary filter、cache-arg 专用参数
		if (filter.hasAttribute("param")) {
			filterModel.setParam(filter.getAttribute("param").toLowerCase());
		}
		// <cache-arg cache-name="" cache-type="" param="" cache-mapping-indexes=""
		// data-type="" alias-name=""/>
		if (filter.hasAttribute("cache-name")) {
			sqlToyConfig.addCacheArgParam(filterModel.getParam());
			filterModel.setCacheName(filter.getAttribute("cache-name"));
			if (filter.hasAttribute("cache-type")) {
				filterModel.setCacheType(filter.getAttribute("cache-type"));
			}
			if (filter.hasAttribute("cache-key-index")) {
				filterModel.setCacheKeyIndex(Integer.parseInt(filter.getAttribute("cache-key-index")));
			}
			if (filter.hasAttribute("cache-mapping-max")) {
				filterModel.setCacheMappingMax(Integer.parseInt(filter.getAttribute("cache-mapping-max")));
				// sql in a参数量不能超过1000
				if (filterModel.getCacheMappingMax() > SqlToyConstants.SQL_IN_MAX) {
					filterModel.setCacheMappingMax(SqlToyConstants.SQL_IN_MAX);
				}
			}
			if (filter.hasAttribute("cache-mapping-indexes")) {
				String[] cacheIndexes = trimParams(filter.getAttribute("cache-mapping-indexes").split("\\,"));
				int[] mappingIndexes = new int[cacheIndexes.length];
				for (int i = 0; i < cacheIndexes.length; i++) {
					mappingIndexes[i] = Integer.parseInt(cacheIndexes[i]);
				}
				filterModel.setCacheMappingIndexes(mappingIndexes);
			}
			if (filter.hasAttribute("alias-name")) {
				filterModel.setAliasName(filter.getAttribute("alias-name").toLowerCase());
				sqlToyConfig.addCacheArgParam(filterModel.getAliasName());
			}
			// 缓存过滤未匹配上赋予的默认值
			if (filter.hasAttribute("cache-not-matched-value")) {
				filterModel.setCacheNotMatchedValue(filter.getAttribute("cache-not-matched-value"));
			}
			// 针对缓存的二级过滤,比如员工信息的缓存,过滤机构是当前人授权的
			NodeList nodeList = filter.getElementsByTagName(local.concat("filter"));
			if (nodeList.getLength() > 0) {
				CacheFilterModel[] cacheFilterModels = new CacheFilterModel[nodeList.getLength()];
				Element cacheFilter;
				for (int i = 0; i < nodeList.getLength(); i++) {
					cacheFilter = (Element) nodeList.item(i);
					CacheFilterModel cacheFilterModel = new CacheFilterModel();
					// 对比列
					cacheFilterModel.setCacheIndex(Integer.parseInt(cacheFilter.getAttribute("cache-index")));
					// 对比条件参数
					cacheFilterModel.setCompareParam(cacheFilter.getAttribute("compare-param").toLowerCase());
					if (cacheFilter.hasAttribute("compare-type")) {
						cacheFilterModel.setCompareType(cacheFilter.getAttribute("compare-type").toLowerCase());
					}
					cacheFilterModels[i] = cacheFilterModel;
				}
				filterModel.setCacheFilters(cacheFilterModels);
			}
		}
		// exclusive 排他性filter 当条件成立时需要修改的参数(即排斥的参数)
		if (filter.hasAttribute("set-params")) {
			filterModel.setUpdateParams(trimParams(filter.getAttribute("set-params").toLowerCase().split("\\,")));
		} else if (filter.hasAttribute("exclusive-params")) {
			filterModel.setUpdateParams(trimParams(filter.getAttribute("exclusive-params").toLowerCase().split("\\,")));
		}
		// exclusive 排他性filter 对排斥的参数设置的值(默认置为null)
		if (filter.hasAttribute("set-value")) {
			filterModel.setUpdateValue(filter.getAttribute("set-value"));
		}
		// add 2022-2-10 for clone filter
		if (filter.hasAttribute("as-param")) {
			filterModel.setUpdateParams(new String[] { filter.getAttribute("as-param") });
		}
		// exclusive 排他性filter 条件成立的对比方式
		if (filter.hasAttribute("compare-type")) {
			String compareType = filter.getAttribute("compare-type");
			if (compareType.equals("eq") || compareType.equals("==") || compareType.equals("equals")
					|| compareType.equals("=")) {
				filterModel.setCompareType("==");
			} else if (compareType.equals("neq") || compareType.equals("<>") || compareType.equals("!=")
					|| compareType.equals("ne")) {
				filterModel.setCompareType("<>");
			} else if (compareType.equals(">") || compareType.equals("gt") || compareType.equals("more")) {
				filterModel.setCompareType(">");
			} else if (compareType.equals(">=") || compareType.equals("gte") || compareType.equals("ge")) {
				filterModel.setCompareType(">=");
			} else if (compareType.equals("<") || compareType.equals("lt") || compareType.equals("less")) {
				filterModel.setCompareType("<");
			} else if (compareType.equals("<=") || compareType.equals("lte") || compareType.equals("le")) {
				filterModel.setCompareType("<=");
			} else if (compareType.equals("between")) {
				filterModel.setCompareType("between");
			} else if (compareType.equals("any") || compareType.equals("in")) {
				filterModel.setCompareType("any");
			}
		}
		// exclusive 排他性filter 条件成立的对比值
		if (filter.hasAttribute("compare-values")) {
			String compareValue = filter.getAttribute("compare-values");
			if (compareValue.indexOf(";") != -1) {
				filterModel.setCompareValues(trimParams(compareValue.split("\\;")));
			} else {
				filterModel.setCompareValues(trimParams(compareValue.split("\\,")));
			}
		}

		// 数据类型
		if (filter.hasAttribute("data-type")) {
			filterModel.setDataType(filter.getAttribute("data-type").toLowerCase());
		}
	}

	/**
	 * @todo 解析翻译器
	 * @param sqlToyConfig
	 * @param translates
	 */
	public static void parseTranslate(SqlToyConfig sqlToyConfig, NodeList translates) {
		if (translates == null || translates.getLength() == 0) {
			return;
		}
		// 翻译器
		HashMap<String, Translate> translateMap = new HashMap<String, Translate>();
		String cacheType;
		String cacheName;
		String[] columns;
		Integer[] cacheIndexs;
		String[] cacheIndexStr;
		String uncachedTemplate;
		// 为mongo和elastic模式提供备用
		String[] aliasNames;
		// 分隔表达式
		String splitRegex = null;
		// 对应split重新连接的字符
		String linkSign = ",";
		boolean hasLink = false;
		Element translate;
		for (int k = 0; k < translates.getLength(); k++) {
			translate = (Element) translates.item(k);
			hasLink = false;
			cacheName = translate.getAttribute("cache");
			// 具体的缓存子分类，如数据字典类别
			if (translate.hasAttribute("cache-type")) {
				cacheType = translate.getAttribute("cache-type");
			} else {
				cacheType = null;
			}
			// 已经小写
			columns = trimParams(translate.getAttribute("columns").toLowerCase().split("\\,"));
			aliasNames = null;
			uncachedTemplate = null;
			if (translate.hasAttribute("undefine-template")) {
				uncachedTemplate = translate.getAttribute("undefine-template");
			} else if (translate.hasAttribute("uncached-template")) {
				uncachedTemplate = translate.getAttribute("uncached-template");
			} else if (translate.hasAttribute("uncached")) {
				uncachedTemplate = translate.getAttribute("uncached");
			}
			if (translate.hasAttribute("split-regex")) {
				splitRegex = translate.getAttribute("split-regex");
				if (translate.hasAttribute("link-sign")) {
					linkSign = translate.getAttribute("link-sign");
					hasLink = true;
				}
				// 正则转化
				if (splitRegex.equals(",") || splitRegex.equals("，")) {
					splitRegex = "\\,";
				} else if (splitRegex.equals(";") || splitRegex.equals("；")) {
					splitRegex = "\\;";
					if (!hasLink) {
						linkSign = ";";
					}
				} else if (splitRegex.equals("、")) {
					splitRegex = "\\、";
				} else if (splitRegex.equals("->")) {
					splitRegex = "\\-\\>";
					if (!hasLink) {
						linkSign = "->";
					}
				}
			}
			// 使用alias时只能针对单列处理
			if (translate.hasAttribute("alias-name")) {
				aliasNames = trimParams(translate.getAttribute("alias-name").toLowerCase().split("\\,"));
			} else if (translate.hasAttribute("original-columns")) {
				aliasNames = trimParams(translate.getAttribute("original-columns").toLowerCase().split("\\,"));
			}

			// 翻译key对应value的在缓存数组中对应的列
			cacheIndexs = null;
			if (translate.hasAttribute("cache-indexs")) {
				cacheIndexStr = trimParams(translate.getAttribute("cache-indexs").split("\\,"));
				cacheIndexs = new Integer[cacheIndexStr.length];
				for (int i = 0; i < cacheIndexStr.length; i++) {
					cacheIndexs[i] = Integer.parseInt(cacheIndexStr[i]);
				}
			} // 兼容参数命名
			else if (translate.hasAttribute("cache-indexes")) {
				cacheIndexStr = trimParams(translate.getAttribute("cache-indexes").split("\\,"));
				cacheIndexs = new Integer[cacheIndexStr.length];
				for (int i = 0; i < cacheIndexStr.length; i++) {
					cacheIndexs[i] = Integer.parseInt(cacheIndexStr[i]);
				}
			}
			if (cacheIndexs == null || cacheIndexs.length == columns.length) {
				for (int i = 0; i < columns.length; i++) {
					Translate translateModel = new Translate(cacheName);
					// 小写
					translateModel.setColumn(columns[i]);
					translateModel.setAlias(aliasNames == null ? columns[i] : aliasNames[i]);
					translateModel.setCacheType(cacheType);
					translateModel.setSplitRegex(splitRegex);
					translateModel.setLinkSign(linkSign);
					if (uncachedTemplate != null) {
						// 统一未匹配中的通配符号为${value}
						translateModel.setUncached(
								uncachedTemplate.replaceAll("(?i)\\$?\\{\\s*key\\s*\\}", "\\$\\{value\\}"));
					}
					if (cacheIndexs != null) {
						if (i < cacheIndexs.length - 1) {
							translateModel.setIndex(cacheIndexs[i]);
						} else {
							translateModel.setIndex(cacheIndexs[cacheIndexs.length - 1]);
						}
					}
					// column 已经小写
					translateMap.put(translateModel.getExtend().column, translateModel);
				}
			} else if (cacheIndexs != null && cacheIndexs.length != columns.length) {
				logger.warn("sqlId:{} 对应的cache translate columns suggest config with cache-indexs!",
						sqlToyConfig.getId());
			}
		}
		sqlToyConfig.setTranslateMap(translateMap);
	}

	/**
	 * @todo 解析Link 查询
	 * @param sqlToyConfig
	 * @param linkNode
	 * @param local
	 */
	private static void parseLink(SqlToyConfig sqlToyConfig, NodeList linkNode, String local) {
		if (linkNode == null || linkNode.getLength() == 0) {
			return;
		}
		Element link = (Element) linkNode.item(0);
		LinkModel linkModel = new LinkModel();
		// update 2020-09-07 增加支持多列场景(兼容旧的模式)
		if (link.hasAttribute("column")) {
			linkModel.setColumns(trimParams(link.getAttribute("column").split("\\,")));
		} else if (link.hasAttribute("columns")) {
			linkModel.setColumns(trimParams(link.getAttribute("columns").split("\\,")));
		}
		// update 2021-10-15 支持多列
		if (link.hasAttribute("id-columns")) {
			linkModel.setIdColumns(trimParams(link.getAttribute("id-columns").split("\\,")));
		} else if (link.hasAttribute("id-column")) {
			linkModel.setIdColumns(trimParams(link.getAttribute("id-column").split("\\,")));
		}
		if (link.hasAttribute("sign")) {
			linkModel.setSign(link.getAttribute("sign"));
		}
		if (link.hasAttribute("distinct")) {
			linkModel.setDistinct(Boolean.parseBoolean(link.getAttribute("distinct")));
		}
		NodeList nodeList = link.getElementsByTagName(local.concat("decorate"));
		if (nodeList.getLength() > 0) {
			Element decorateElt = (Element) nodeList.item(0);
			if (decorateElt.hasAttribute("align")) {
				linkModel.setDecorateAlign(decorateElt.getAttribute("align").toLowerCase());
			}
			linkModel.setDecorateAppendChar(decorateElt.getAttribute("char"));
			linkModel.setDecorateSize(Integer.parseInt(decorateElt.getAttribute("size")));
		}
		sqlToyConfig.setLinkModel(linkModel);
	}

	/**
	 * @todo 解析对结果字段类型为日期、数字格式化处理配置
	 * @param sqlToyConfig
	 * @param dfElts
	 * @param nfElts
	 */
	private static void parseFormat(SqlToyConfig sqlToyConfig, NodeList dfElts, NodeList nfElts) {
		List<FormatModel> formatModels = new ArrayList<FormatModel>();
		if (dfElts != null && dfElts.getLength() > 0) {
			Element df;
			for (int i = 0; i < dfElts.getLength(); i++) {
				df = (Element) dfElts.item(i);
				String[] columns = trimParams(df.getAttribute("columns").toLowerCase().split("\\,"));
				String format = df.hasAttribute("format") ? df.getAttribute("format") : "yyyy-MM-dd";
				String locale = df.hasAttribute("locale") ? df.getAttribute("locale") : null;
				for (String col : columns) {
					FormatModel formatModel = new FormatModel();
					formatModel.setColumn(col);
					formatModel.setType(1);
					formatModel.setFormat(format);
					formatModel.setLocale(locale);
					formatModels.add(formatModel);
				}
			}
		}
		if (nfElts != null && nfElts.getLength() > 0) {
			Element nf;
			for (int i = 0; i < nfElts.getLength(); i++) {
				nf = (Element) nfElts.item(i);
				String[] columns = trimParams(nf.getAttribute("columns").toLowerCase().split("\\,"));
				String format = nf.hasAttribute("format") ? nf.getAttribute("format") : "capital";
				String roundStr = nf.hasAttribute("roundingMode") ? nf.getAttribute("roundingMode").toUpperCase()
						: null;
				String locale = nf.hasAttribute("locale") ? nf.getAttribute("locale") : null;
				RoundingMode roundMode = null;
				if (roundStr != null) {
					if (roundStr.equals("HALF_UP")) {
						roundMode = RoundingMode.HALF_UP;
					} else if (roundStr.equals("HALF_DOWN")) {
						roundMode = RoundingMode.HALF_DOWN;
					} else if (roundStr.equals("ROUND_DOWN")) {
						roundMode = RoundingMode.DOWN;
					} else if (roundStr.equals("ROUND_UP")) {
						roundMode = RoundingMode.UP;
					}
				}
				for (String col : columns) {
					FormatModel formatModel = new FormatModel();
					formatModel.setColumn(col);
					formatModel.setRoundingMode(roundMode);
					formatModel.setType(2);
					formatModel.setFormat(format);
					formatModel.setLocale(locale);
					formatModels.add(formatModel);
				}
			}
		}
		sqlToyConfig.setFormatModels(formatModels);
	}

	/**
	 * @todo 解析对sqltoy查询结果的计算处理逻辑定义(包含:旋转、汇总等)
	 * @param sqlToyConfig
	 * @param sqlElt
	 * @param local
	 * @throws Exception
	 */
	private static void parseCalculator(SqlToyConfig sqlToyConfig, Element sqlElt, String local) throws Exception {
		NodeList elements = sqlElt.getChildNodes();
		Element elt;
		String eltName;
		List resultProcessor = new ArrayList();
		for (int i = 0; i < elements.getLength(); i++) {
			if (elements.item(i).getNodeType() == Node.ELEMENT_NODE) {
				elt = (Element) elements.item(i);
				eltName = elt.getNodeName();
				// 旋转(只能进行一次旋转)
				if (eltName.equals(local.concat("pivot"))) {
					PivotModel pivotModel = new PivotModel();
					if (elt.hasAttribute("group-columns")) {
						pivotModel
								.setGroupCols(trimParams(elt.getAttribute("group-columns").toLowerCase().split("\\,")));
					}
					if (elt.hasAttribute("category-columns")) {
						pivotModel.setCategoryCols(
								trimParams(elt.getAttribute("category-columns").toLowerCase().split("\\,")));
					}
					if (elt.hasAttribute("category-sql")) {
						pivotModel.setCategorySql(elt.getAttribute("category-sql"));
					}
					String[] startEndCols = new String[2];
					startEndCols[0] = elt.getAttribute("start-column").toLowerCase();
					if (elt.hasAttribute("end-column")) {
						startEndCols[1] = elt.getAttribute("end-column").toLowerCase();
					} else {
						startEndCols[1] = startEndCols[0];
					}
					if (elt.hasAttribute("default-value")) {
						String defaultValue = elt.getAttribute("default-value");
						if (elt.hasAttribute("default-type")) {
							String defaultType = elt.getAttribute("default-type").toLowerCase();
							try {
								pivotModel.setDefaultValue(BeanUtil.convertType(defaultValue, defaultType));
							} catch (Exception e) {
								e.printStackTrace();
							}
						} else {
							pivotModel.setDefaultValue(defaultValue);
						}
					}
					pivotModel.setStartEndCols(startEndCols);
					resultProcessor.add(pivotModel);
				} // 列转行
				else if (eltName.equals(local.concat("unpivot"))) {
					UnpivotModel unpivotModel = new UnpivotModel();
					XMLUtil.setAttributes(elt, unpivotModel);
					if (unpivotModel.getColumnsToRows().length > 1) {
						// 2022-5-9 支持多组旋转(统一分组符号)
						String cols = elt.getAttribute("columns-to-rows").trim().replace("[", "{").replace("]", "}");
						unpivotModel.setColumnsToRows(StringUtil.splitExcludeSymMark(cols, ",", filters));
						if (cols.startsWith("{") && cols.endsWith("}")) {
							unpivotModel.setGroupSize(unpivotModel.getColumnsToRows().length);
							//替换掉分组符号
							String[] colsToRows = unpivotModel.getColumnsToRows();
							for (int t = 0; t < unpivotModel.getGroupSize(); t++) {
								colsToRows[t] = colsToRows[t].replace("{", "").replace("}", "").trim();
							}
							unpivotModel.setColumnsToRows(colsToRows);
						}
						resultProcessor.add(unpivotModel);
					}
				}
				// 汇总合计
				else if (eltName.equals(local.concat("summary"))) {
					SummaryModel summaryModel = new SummaryModel();
					// 是否逆向汇总
					if (elt.hasAttribute("reverse")) {
						summaryModel.setReverse(Boolean.parseBoolean(elt.getAttribute("reverse")));
					}
					// 汇总合计涉及的列
					if (elt.hasAttribute("sum-columns")) {
						summaryModel.setSummaryCols(elt.getAttribute("sum-columns").toLowerCase());
					} else if (elt.hasAttribute("columns")) {
						summaryModel.setSummaryCols(elt.getAttribute("columns").toLowerCase());
					}
					// 计算平均值的列
					if (elt.hasAttribute("average-columns")) {
						summaryModel.setAverageCols(elt.getAttribute("average-columns").toLowerCase());
					}
					// 保留小数点位数(2022-2-23 扩展成数组，便于给不同平均值列设置不同的小数位)
					if (elt.hasAttribute("average-radix-sizes")) {
						summaryModel
								.setRadixSize(trimParamsToInt(elt.getAttribute("average-radix-sizes").split("\\,")));
					} else if (elt.hasAttribute("radix-size")) {
						summaryModel.setRadixSize(trimParamsToInt(elt.getAttribute("radix-size").split("\\,")));
					}
					if (elt.hasAttribute("average-rounding-modes")) {
						String[] roundingModeAry = trimParams(
								elt.getAttribute("average-rounding-modes").toUpperCase().split("\\,"));
						RoundingMode[] roudingModes = new RoundingMode[roundingModeAry.length];
						String roundingMode;
						RoundingMode roundMode = null;
						for (int k = 0; k < roundingModeAry.length; k++) {
							roundingMode = roundingModeAry[k];
							if (roundingMode.equals("HALF_UP")) {
								roundMode = RoundingMode.HALF_UP;
							} else if (roundingMode.equals("HALF_DOWN")) {
								roundMode = RoundingMode.HALF_DOWN;
							} else if (roundingMode.equals("ROUND_DOWN")) {
								roundMode = RoundingMode.DOWN;
							} else if (roundingMode.equals("ROUND_UP")) {
								roundMode = RoundingMode.UP;
							} else {
								roundMode = RoundingMode.HALF_UP;
							}
							roudingModes[k] = roundMode;
						}
						summaryModel.setRoundingModes(roudingModes);
					}
					// 汇总所在位置
					if (elt.hasAttribute("sum-site")) {
						summaryModel.setSumSite(elt.getAttribute("sum-site"));
					}
					// sum和average值左右拼接时的连接字符串
					if (elt.hasAttribute("link-sign")) {
						summaryModel.setLinkSign(elt.getAttribute("link-sign"));
					}
					// 求平均时是否过滤掉null的记录
					if (elt.hasAttribute("average-skip-null")) {
						summaryModel.setAverageSkipNull(Boolean.parseBoolean(elt.getAttribute("average-skip-null")));
					}
					// 单行数据是否需要进行分组汇总计算
					if (elt.hasAttribute("skip-single-row")) {
						summaryModel.setSkipSingleRow(Boolean.parseBoolean(elt.getAttribute("skip-single-row")));
					}
					NodeList nodeList = elt.getElementsByTagName(local.concat("global"));
					List<SummaryGroupMeta> groupMetaList = new ArrayList<SummaryGroupMeta>();
					// 全局汇总
					if (nodeList.getLength() > 0) {
						SummaryGroupMeta globalMeta = new SummaryGroupMeta();
						Element globalSummary = (Element) nodeList.item(0);
						if (globalSummary.hasAttribute("label-column")) {
							globalMeta.setLabelColumn(globalSummary.getAttribute("label-column").toLowerCase());
						}
						if (globalSummary.hasAttribute("average-label")) {
							globalMeta.setAverageTitle(globalSummary.getAttribute("average-label"));
						}
						// 汇总分组列
						if (globalSummary.hasAttribute("group-column")) {
							globalMeta.setGroupColumn(globalSummary.getAttribute("group-column").toLowerCase());
						}
						if (globalSummary.hasAttribute("sum-label")) {
							globalMeta.setSumTitle(globalSummary.getAttribute("sum-label"));
						}
						if (globalSummary.hasAttribute("reverse")) {
							globalMeta.setGlobalReverse(Boolean.parseBoolean(globalSummary.getAttribute("reverse")));
						}
						if (summaryModel.isReverse()) {
							globalMeta.setGlobalReverse(false);
						}
						groupMetaList.add(globalMeta);
					}
					// 分组汇总
					nodeList = elt.getElementsByTagName(local.concat("group"));
					if (nodeList.getLength() > 0) {
						Element groupElt;
						for (int j = 0; j < nodeList.getLength(); j++) {
							groupElt = (Element) nodeList.item(j);
							SummaryGroupMeta groupMeta = new SummaryGroupMeta();
							groupMeta.setGroupColumn(groupElt.getAttribute("group-column").toLowerCase());
							if (groupElt.hasAttribute("average-label")) {
								groupMeta.setAverageTitle(groupElt.getAttribute("average-label"));
							}
							if (groupElt.hasAttribute("sum-label")) {
								groupMeta.setSumTitle(groupElt.getAttribute("sum-label"));
							}
							if (groupElt.hasAttribute("label-column")) {
								groupMeta.setLabelColumn(groupElt.getAttribute("label-column"));
							}
							groupMetaList.add(groupMeta);
						}
					}
					if (!groupMetaList.isEmpty()) {
						SummaryGroupMeta[] groupMetas = new SummaryGroupMeta[groupMetaList.size()];
						groupMetaList.toArray(groupMetas);
						summaryModel.setGroupMeta(groupMetas);
					}
					resultProcessor.add(summaryModel);
				} // 列与列进行比较
				else if (eltName.equals(local.concat("cols-chain-relative"))) {
					ColsChainRelativeModel colsRelativeModel = new ColsChainRelativeModel();
					XMLUtil.setAttributes(elt, colsRelativeModel);
					resultProcessor.add(colsRelativeModel);
				} // 行与行进行比较
				else if (eltName.equals(local.concat("rows-chain-relative"))) {
					RowsChainRelativeModel rowsRelativeModel = new RowsChainRelativeModel();
					XMLUtil.setAttributes(elt, rowsRelativeModel);
					resultProcessor.add(rowsRelativeModel);
				} // 集合数据顺序颠倒
				else if (eltName.equals(local.concat("reverse"))) {
					ReverseModel reverseModel = new ReverseModel();
					XMLUtil.setAttributes(elt, reverseModel);
					resultProcessor.add(reverseModel);
				}
			}
		}
		// 加入sqlToyConfig
		sqlToyConfig.setResultProcessor(resultProcessor);
	}

	/**
	 * @todo 获取Resource
	 * @param reasource
	 * @return
	 */
	private static InputStream getResourceAsStream(String reasource) {
		return Thread.currentThread().getContextClassLoader()
				.getResourceAsStream((reasource.charAt(0) == '/') ? reasource.substring(1) : reasource);
	}

	/**
	 * @todo 对split之后参数名称进行trim
	 * @param paramNames
	 * @return
	 */
	private static String[] trimParams(String[] paramNames) {
		if (paramNames == null || paramNames.length == 0) {
			return paramNames;
		}
		String[] realParamNames = new String[paramNames.length];
		for (int i = 0; i < paramNames.length; i++) {
			realParamNames[i] = (paramNames[i] == null) ? null : paramNames[i].trim();
		}
		return realParamNames;
	}

	private static Integer[] trimParamsToInt(String[] paramNames) {
		if (paramNames == null || paramNames.length == 0) {
			return null;
		}
		Integer[] realParamNames = new Integer[paramNames.length];
		for (int i = 0; i < paramNames.length; i++) {
			realParamNames[i] = (paramNames[i] == null || paramNames[i].trim().equals("")) ? null
					: Integer.parseInt(paramNames[i].trim());
		}
		return realParamNames;
	}

	/**
	 * @TODO 切割nosql 定义的fields,让其符合预期格式,格式为id[col1,col2:aliasName],col3,col4
	 *       将其按逗号分隔成 id.col1,id.cols2:aliasName,col3,col4
	 * @param fields
	 * @return
	 */
	private static String[] splitFields(String fields) {
		if (StringUtil.isBlank(fields)) {
			return null;
		}
		List<String> fieldSet = new ArrayList<String>();
		String[] strs = StringUtil.splitExcludeSymMark(fields, ",", filters);
		String pre;
		String[] params;
		for (String str : strs) {
			if (str.contains("[") && str.contains("]")) {
				pre = str.substring(0, str.indexOf("[")).trim();
				params = str.substring(str.indexOf("[") + 1, str.indexOf("]")).split("\\,");
				for (String param : params) {
					fieldSet.add(pre.concat(".").concat(param.trim()));
				}
			} else {
				fieldSet.add(str.trim());
			}
		}
		String[] result = new String[fieldSet.size()];
		fieldSet.toArray(result);
		return result;
	}
}
