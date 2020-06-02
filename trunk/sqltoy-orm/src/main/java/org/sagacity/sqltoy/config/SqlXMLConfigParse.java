/**
 * @Copyright 2009 版权归陈仁飞，不要肆意侵权抄袭，如引用请注明出处保留作者信息。
 */
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
import org.sagacity.sqltoy.config.model.GroupMeta;
import org.sagacity.sqltoy.config.model.LinkModel;
import org.sagacity.sqltoy.config.model.NoSqlConfigModel;
import org.sagacity.sqltoy.config.model.ParamFilterModel;
import org.sagacity.sqltoy.config.model.PivotModel;
import org.sagacity.sqltoy.config.model.QueryShardingModel;
import org.sagacity.sqltoy.config.model.ReverseModel;
import org.sagacity.sqltoy.config.model.RowsChainRelativeModel;
import org.sagacity.sqltoy.config.model.SecureMask;
import org.sagacity.sqltoy.config.model.SqlToyConfig;
import org.sagacity.sqltoy.config.model.SqlTranslate;
import org.sagacity.sqltoy.config.model.SqlType;
import org.sagacity.sqltoy.config.model.SummaryModel;
import org.sagacity.sqltoy.config.model.UnpivotModel;
import org.sagacity.sqltoy.dialect.utils.PageOptimizeUtils;
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
 * @author chenrenfei <a href="mailto:zhongxuchen@hotmail.com">联系作者</a>
 * @version id:SqlXMLConfigParse.java,Revision:v1.0,Date:2009-12-14 上午12:07:03
 * @modify Date:2011-8-30 {增加sql文件设置数据库类别功能，优化解决跨数据库sql文件的配置方式}
 * @modify Date:2018-1-1 {增加对es和mongo的查询配置解析支持}
 * @modify Date:2019-1-15 {增加cache-arg 和 to-in-arg 过滤器}
 * @modify Date:2020-3-27 {增加rows-chain-relative 和 cols-chain-relative
 *         环比计算功能,并优化unpivot解析改用XMLUtil类}
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
		if (xmlFiles == null || xmlFiles.isEmpty())
			return;
		File sqlFile;
		String fileName;
		Object resource;
		boolean isDebug = logger.isDebugEnabled();
		for (int i = 0; i < xmlFiles.size(); i++) {
			resource = xmlFiles.get(i);
			if (resource instanceof File) {
				sqlFile = (File) resource;
				fileName = sqlFile.getName();
				synchronized (fileName) {
					Long lastModified = Long.valueOf(sqlFile.lastModified());
					// 调试模式，判断文件的最后修改时间，决定是否重新加载sql
					Long preModified = filesLastModifyMap.get(fileName);
					// 最后修改时间比上次修改时间大，重新加载sql文件
					if (preModified == null || lastModified.longValue() > preModified.longValue()) {
						filesLastModifyMap.put(fileName, lastModified);
						if (isDebug) {
							logger.debug("sql文件:{}已经被修改,进行重新解析!", fileName);
						} else {
							out.println("sql文件:" + fileName + " 已经被修改,进行重新解析!");
						}
						parseSingleFile(sqlFile, filesLastModifyMap, cache, encoding, dialect, true);
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
	 * @throws Exception
	 */
	public static void parseSingleFile(Object xmlFile, ConcurrentHashMap<String, Long> filesLastModifyMap,
			ConcurrentHashMap<String, SqlToyConfig> cache, String encoding, String dialect, boolean isReload)
			throws Exception {
		InputStream fileIS = null;
		try {
			boolean isDebug = logger.isDebugEnabled();
			if (xmlFile instanceof File) {
				File file = (File) xmlFile;
				filesLastModifyMap.put(file.getName(), Long.valueOf(file.lastModified()));
				fileIS = new FileInputStream(file);
				if (isDebug) {
					logger.debug("正在解析sql文件,对应文件={}", file.getName());
				} else {
					out.println("正在解析sql文件,对应文件=" + file.getName());
				}
			} else {
				fileIS = getResourceAsStream((String) xmlFile);
				if (isDebug) {
					logger.debug("正在解析sql文件,对应文件={}", (String) xmlFile);
				} else {
					out.println("正在解析sql文件,对应文件=" + (String) xmlFile);
				}
			}
			if (fileIS != null) {
				domFactory.setFeature(SqlToyConstants.XML_FETURE, false);
				DocumentBuilder domBuilder = domFactory.newDocumentBuilder();
				Document doc = domBuilder.parse(fileIS);
				NodeList sqlElts = doc.getDocumentElement().getChildNodes();
				if (sqlElts == null || sqlElts.getLength() == 0)
					return;
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
							if (cache.containsKey(sqlToyConfig.getId()) && !isReload) {
								logger.warn("发现重复的SQL语句,id={},将被覆盖!", sqlToyConfig.getId());
								// 移除分页优化缓存
								PageOptimizeUtils.remove(sqlToyConfig.getId());
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
		SqlToyConfig sqlToyConfig = null;
		if (sqlSegment instanceof String) {
			Document doc = domFactory.newDocumentBuilder().parse(
					new ByteArrayInputStream(((String) sqlSegment).getBytes(encoding == null ? "UTF-8" : encoding)));
			sqlToyConfig = parseSingleSql(doc.getDocumentElement(), dialect);
		} else if (sqlSegment instanceof Element) {
			sqlToyConfig = parseSingleSql((Element) sqlSegment, dialect);
		}
		return sqlToyConfig;
	}

	/**
	 * @todo 解析单个sql element元素
	 * @param sqlElt
	 * @param dialect
	 * @return
	 * @throws Exception
	 */
	public static SqlToyConfig parseSingleSql(Element sqlElt, String dialect) throws Exception {
		String realDialect = dialect;
		String nodeName = sqlElt.getNodeName().toLowerCase();
		// 目前只支持传统sql、elastic、mongo三种类型的语法
		if (!nodeName.equals("sql") && !nodeName.equals("eql") && !nodeName.equals("mql"))
			return null;
		String id = sqlElt.getAttribute("id");
		if (id == null) {
			throw new RuntimeException("请检查sql配置,没有给定sql对应的 id值!");
		}

		// 判断是否xml为精简模式即只有<sql id=""><![CDATA[]]></sql>模式
		NodeList nodeList = sqlElt.getElementsByTagName("value");
		String sqlContent = null;
		if (nodeList.getLength() > 0) {
			sqlContent = StringUtil.trim(nodeList.item(0).getTextContent());
		} else {
			sqlContent = StringUtil.trim(sqlElt.getTextContent());
		}
		if (StringUtil.isBlank(sqlContent)) {
			throw new RuntimeException("请检查sql-id='" + id + "' 的配置,没有正确填写sql内容!");
		}
		nodeList = sqlElt.getElementsByTagName("count-sql");
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
			countSql = StringUtil.clearMistyChars(SqlUtil.clearMark(countSql), " ").concat(" ");
			countSql = FunctionUtils.getDialectSql(countSql, dialect);
			countSql = ReservedWordsUtil.convertSql(countSql, DataSourceUtils.getDBType(dialect));
			sqlToyConfig.setCountSql(countSql);
		}
		/**
		 * 是否是单纯的union all分页(在取count记录数时,将union all 每部分的查询from前面的全部替换成 select 1
		 * from,减少不必要的执行运算，提升效率)
		 */
		if (sqlElt.hasAttribute("union-all-count")) {
			sqlToyConfig.setUnionAllCount(Boolean.parseBoolean(sqlElt.getAttribute("union-all-count")));
		}
		// 解析sql对应dataSource的sharding配置
		parseShardingDataSource(sqlToyConfig, sqlElt.getElementsByTagName("sharding-datasource"));

		// 解析sql对应的table的sharding配置
		parseShardingTables(sqlToyConfig, sqlElt.getElementsByTagName("sharding-table"));
		// 解析格式化
		parseFormat(sqlToyConfig, sqlElt.getElementsByTagName("date-format"),
				sqlElt.getElementsByTagName("number-format"));
		// 参数值为空白是否当中null处理,默认为-1
		int blankToNull = -1;
		if (sqlElt.hasAttribute("blank-to-null")) {
			blankToNull = (Boolean.parseBoolean(sqlElt.getAttribute("blank-to-null"))) ? 1 : 0;
		}
		nodeList = sqlElt.getElementsByTagName("filters");
		// 解析参数过滤器
		if (nodeList.getLength() > 0) {
			parseFilters(sqlToyConfig, nodeList.item(0).getChildNodes(), blankToNull);
		} else {
			parseFilters(sqlToyConfig, null, blankToNull);
		}

		// 解析分页优化器
		// <page-optimize alive-max="100" alive-seconds="90"/>
		nodeList = sqlElt.getElementsByTagName("page-optimize");
		if (nodeList.getLength() > 0) {
			Element pageOptimize = (Element) nodeList.item(0);
			sqlToyConfig.setPageOptimize(true);
			if (pageOptimize.hasAttribute("alive-max")) {
				sqlToyConfig.setPageAliveMax(Integer.parseInt(pageOptimize.getAttribute("alive-max")));
			}
			// 不同sql条件分页记录数量保存有效时长(默认90秒)
			if (pageOptimize.hasAttribute("alive-seconds")) {
				sqlToyConfig.setPageAliveSeconds(Integer.parseInt(pageOptimize.getAttribute("alive-seconds")));
			}
		}

		// 解析翻译器
		parseTranslate(sqlToyConfig, sqlElt.getElementsByTagName("translate"));
		// 解析link
		parseLink(sqlToyConfig, sqlElt.getElementsByTagName("link"));
		// 解析对结果的运算
		parseCalculator(sqlToyConfig, sqlElt);

		// 解析安全脱敏配置
		parseSecureMask(sqlToyConfig, sqlElt.getElementsByTagName("secure-mask"));
		// mongo/elastic查询语法
		if (isNoSql) {
			parseNoSql(sqlToyConfig, sqlElt);
		}
		return sqlToyConfig;
	}

	/**
	 * @todo 解析nosql的相关配置
	 * @param sqlToyConfig
	 * @param sqlElt
	 */
	private static void parseNoSql(SqlToyConfig sqlToyConfig, Element sqlElt) {
		NoSqlConfigModel noSqlConfig = new NoSqlConfigModel();
		NodeList nodeList;
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
			nodeList = sqlElt.getElementsByTagName("fields");
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
		// 是否有聚合查询
		if (sqlElt.getNodeName().equalsIgnoreCase("eql")) {
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
		} else if (sqlElt.getNodeName().equalsIgnoreCase("mql")) {
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
	 * @todo 解析安全脱敏配置
	 * @param sqlToyConfig
	 * @param maskElts
	 */
	public static void parseSecureMask(SqlToyConfig sqlToyConfig, NodeList maskElts) {
		if (maskElts != null && maskElts.getLength() > 0) {
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
				String[] columns = tmp.toLowerCase().split("\\,");
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
			SecureMask[] masks = new SecureMask[secureMasks.size()];
			secureMasks.toArray(masks);
			sqlToyConfig.setSecureMasks(masks);
		}
	}

	private static String getAttrValue(Element elt, String attrName) {
		if (elt.hasAttribute(attrName))
			return elt.getAttribute(attrName);
		return null;
	}

	/**
	 * @todo 解析dataSource的sharding
	 * @param sqlToyConfig
	 * @param shardingDataSource
	 */
	private static void parseShardingDataSource(SqlToyConfig sqlToyConfig, NodeList shardingDBNode) {
		if (shardingDBNode == null || shardingDBNode.getLength() == 0)
			return;
		Element shardingDataSource = (Element) shardingDBNode.item(0);
		// 策略辨别值
		if (shardingDataSource.hasAttribute("strategy-value")) {
			sqlToyConfig.setDataSourceShardingStrategyValue(shardingDataSource.getAttribute("strategy-value"));
		}
		if (shardingDataSource.hasAttribute("params")) {
			sqlToyConfig.setDataSourceShardingParams(
					shardingDataSource.getAttribute("params").replace(";", ",").toLowerCase().split("\\,"));
			int size = sqlToyConfig.getDataSourceShardingParams().length;
			String[] paramsAlias = new String[size];
			String[] paramName;
			for (int i = 0; i < size; i++) {
				paramName = sqlToyConfig.getDataSourceShardingParams()[i].split("\\:");
				sqlToyConfig.getDataSourceShardingParams()[i] = paramName[0].trim();
				paramsAlias[i] = paramName[paramName.length - 1].trim();
			}
			sqlToyConfig.setDataSourceShardingParamsAlias(paramsAlias);
		}
		sqlToyConfig.setDataSourceShardingStragety(shardingDataSource.getAttribute("strategy"));
	}

	/**
	 * @todo 解析table的sharding
	 * @param sqlToyConfig
	 * @param shardingTables
	 */
	private static void parseShardingTables(SqlToyConfig sqlToyConfig, NodeList shardingTables) {
		if (shardingTables == null || shardingTables.getLength() == 0)
			return;
		List<QueryShardingModel> tablesShardings = new ArrayList();
		String[] paramName;
		String[] paramsAlias;
		int size;
		List<String> params = new ArrayList();
		Element elt;
		for (int i = 0; i < shardingTables.getLength(); i++) {
			elt = (Element) shardingTables.item(i);
			if (elt.hasAttribute("tables") && elt.hasAttribute("strategy")) {
				QueryShardingModel shardingModel = new QueryShardingModel();
				shardingModel.setTables(elt.getAttribute("tables").split(","));
				if (elt.hasAttribute("params")) {
					// params="a:a1,b:b1";params为{a:a1, b:b1}
					shardingModel.setParams(elt.getAttribute("params").replace(";", ",").toLowerCase().split("\\,"));
					size = shardingModel.getParams().length;
					paramsAlias = new String[size];
					for (int j = 0; j < size; j++) {
						paramName = shardingModel.getParams()[j].split("\\:");
						// 重置params数组值
						shardingModel.getParams()[j] = paramName[0].trim();
						params.add(shardingModel.getParams()[j]);
						paramsAlias[j] = paramName[paramName.length - 1].trim();
					}
					shardingModel.setParamsAlias(paramsAlias);
				}
				if (elt.hasAttribute("strategy-value")) {
					shardingModel.setStrategyValue(elt.getAttribute("strategy-value"));
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
		sqlToyConfig.setTablesShardings(tablesShardings);
	}

	/**
	 * @todo 解析3.0版本 filters xml元素
	 * @param sqlToyConfig
	 * @param filterSet
	 * @param blankToNull
	 */
	public static void parseFilters(SqlToyConfig sqlToyConfig, NodeList filterSet, int blankToNull) {
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
			for (int i = 0; i < filterSet.getLength(); i++) {
				if (filterSet.item(i).getNodeType() == Node.ELEMENT_NODE) {
					filter = (Element) filterSet.item(i);
					blank = false;
					filterType = filter.getNodeName();
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
						parseFilterElt(sqlToyConfig, filterModel, filter);
						filterModels.add(filterModel);
					}
				}
			}
		}
		// 当没有特定配置时，默认将所有空白当做null处理
		if (!hasBlank && blankToNull == -1) {
			filterModels.add(0, new ParamFilterModel("blank", new String[] { "*" }));
		}
		if (filterModels.isEmpty())
			return;
		ParamFilterModel[] result = new ParamFilterModel[filterModels.size()];
		filterModels.toArray(result);
		sqlToyConfig.setFilters(result);
	}

	/**
	 * @todo 解析filter
	 * @param sqlToyConfig
	 * @param filterModel
	 * @param filter
	 */
	private static void parseFilterElt(SqlToyConfig sqlToyConfig, ParamFilterModel filterModel, Element filter) {
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
		if (filter.hasAttribute("increment-days")) {
			filterModel.setIncrementDays(Double.valueOf(filter.getAttribute("increment-days")));
		}
		// to-date filter
		if (filter.hasAttribute("format")) {
			filterModel.setFormat(filter.getAttribute("format"));
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
			HashMap<String, String> excludeMaps = new HashMap<String, String>();
			for (String excludeParam : excludeParams) {
				excludeMaps.put(excludeParam.trim(), "1");
			}
			filterModel.setExcludesMap(excludeMaps);
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
			NodeList nodeList = filter.getElementsByTagName("filter");
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
		if (translates == null || translates.getLength() == 0)
			return;
		// 翻译器
		HashMap<String, SqlTranslate> translateMap = new HashMap<String, SqlTranslate>();
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
					SqlTranslate translateModel = new SqlTranslate();
					translateModel.setCache(cacheName);
					translateModel.setColumn(columns[i]);
					translateModel.setAlias(aliasNames == null ? columns[i] : aliasNames[i]);
					translateModel.setDictType(cacheType);
					translateModel.setSplitRegex(splitRegex);
					translateModel.setLinkSign(linkSign);
					if (uncachedTemplate != null) {
						if (uncachedTemplate.trim().equals("")) {
							translateModel.setUncached(null);
						} else {
							translateModel.setUncached(uncachedTemplate);
						}
					}
					if (cacheIndexs != null) {
						if (i < cacheIndexs.length - 1) {
							translateModel.setIndex(cacheIndexs[i]);
						} else {
							translateModel.setIndex(cacheIndexs[cacheIndexs.length - 1]);
						}
					}
					translateMap.put(translateModel.getColumn(), translateModel);
				}
			} else if (cacheIndexs != null && cacheIndexs.length != columns.length) {
				logger.warn("sqlId:{} 对应的cache translate columns must mapped with cache-indexs!", sqlToyConfig.getId());
			}
		}
		sqlToyConfig.setTranslateMap(translateMap);
	}

	/**
	 * @todo 解析Link 查询
	 * @param sqlToyConfig
	 * @param link
	 */
	private static void parseLink(SqlToyConfig sqlToyConfig, NodeList linkNode) {
		if (linkNode == null || linkNode.getLength() == 0)
			return;
		Element link = (Element) linkNode.item(0);
		LinkModel linkModel = new LinkModel();
		linkModel.setColumn(link.getAttribute("column"));
		if (link.hasAttribute("id-column")) {
			linkModel.setIdColumn(link.getAttribute("id-column"));
		}
		if (link.hasAttribute("sign")) {
			linkModel.setSign(link.getAttribute("sign"));
		}
		NodeList nodeList = link.getElementsByTagName("decorate");
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
				String[] columns = df.getAttribute("columns").toLowerCase().split("\\,");
				String format = df.hasAttribute("format") ? df.getAttribute("format") : "yyyy-MM-dd";
				for (String col : columns) {
					FormatModel formatModel = new FormatModel();
					formatModel.setColumn(col);
					formatModel.setType(1);
					formatModel.setFormat(format);
					formatModels.add(formatModel);
				}
			}
		}
		if (nfElts != null && nfElts.getLength() > 0) {
			Element nf;
			for (int i = 0; i < nfElts.getLength(); i++) {
				nf = (Element) nfElts.item(i);
				String[] columns = nf.getAttribute("columns").toLowerCase().split("\\,");
				String format = nf.hasAttribute("format") ? nf.getAttribute("format") : "capital";
				String roundStr = nf.hasAttribute("roundingMode") ? nf.getAttribute("roundingMode").toUpperCase()
						: null;
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
					formatModels.add(formatModel);
				}
			}
		}
		if (!formatModels.isEmpty()) {
			FormatModel[] formats = new FormatModel[formatModels.size()];
			formatModels.toArray(formats);
			sqlToyConfig.setFormatModels(formats);
		}
	}

	/**
	 * @todo 解析对sqltoy查询结果的计算处理逻辑定义(包含:旋转、汇总等)
	 * @param sqlToyConfig
	 * @param sqlElt
	 */
	private static void parseCalculator(SqlToyConfig sqlToyConfig, Element sqlElt) throws Exception {
		NodeList elements = sqlElt.getChildNodes();
		Element elt;
		String eltName;
		List resultProcessor = new ArrayList();
		for (int i = 0; i < elements.getLength(); i++) {
			if (elements.item(i).getNodeType() == Node.ELEMENT_NODE) {
				elt = (Element) elements.item(i);
				eltName = elt.getNodeName();
				// 旋转(只能进行一次旋转)
				if (eltName.equals("pivot")) {
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
					String[] pivotCols = new String[2];
					pivotCols[0] = elt.getAttribute("start-column").toLowerCase();
					if (elt.hasAttribute("end-column")) {
						pivotCols[1] = elt.getAttribute("end-column").toLowerCase();
					} else {
						pivotCols[1] = pivotCols[0];
					}
					if (elt.hasAttribute("default-value")) {
						String defaultValue = elt.getAttribute("default-value");
						if (elt.hasAttribute("default-type")) {
							String defaultType = elt.getAttribute("default-type");
							try {
								pivotModel.setDefaultValue(BeanUtil.convertType(defaultValue, defaultType));
							} catch (Exception e) {
								e.printStackTrace();
							}
						} else {
							pivotModel.setDefaultValue(defaultValue);
						}
					}
					pivotModel.setPivotCols(pivotCols);
					resultProcessor.add(pivotModel);
				} // 列转行
				else if (eltName.equals("unpivot")) {
					UnpivotModel unpivotModel = new UnpivotModel();
					XMLUtil.setAttributes(elt, unpivotModel);
					if (unpivotModel.getColumnsToRows().length > 1) {
						resultProcessor.add(unpivotModel);
					}
				}
				// 汇总合计
				else if (eltName.equals("summary")) {
					SummaryModel summaryModel = new SummaryModel();
					// 是否逆向汇总
					if (elt.hasAttribute("reverse")) {
						summaryModel.setReverse(Boolean.parseBoolean(elt.getAttribute("reverse")));
						summaryModel.setGlobalReverse(summaryModel.isReverse());
					}
					// 汇总合计涉及的列
					if (elt.hasAttribute("columns")) {
						summaryModel.setSummaryCols(elt.getAttribute("columns").toLowerCase());
					}
					// 保留小数点位数
					if (elt.hasAttribute("radix-size")) {
						summaryModel.setRadixSize(Integer.parseInt(elt.getAttribute("radix-size")));
					} else {
						summaryModel.setRadixSize(-1);
					}
					// 汇总所在位置
					if (elt.hasAttribute("sum-site")) {
						summaryModel.setSumSite(elt.getAttribute("sum-site"));
					}
					// sum和average值左右拼接时的连接字符串
					if (elt.hasAttribute("link-sign")) {
						summaryModel.setLinkSign(elt.getAttribute("link-sign"));
					}
					NodeList nodeList = elt.getElementsByTagName("global");
					// 全局汇总
					if (nodeList.getLength() > 0) {
						Element globalSummary = (Element) nodeList.item(0);
						if (globalSummary.hasAttribute("label-column")) {
							summaryModel.setGlobalLabelColumn(globalSummary.getAttribute("label-column").toLowerCase());
						}
						if (globalSummary.hasAttribute("average-label")) {
							summaryModel.setGlobalAverageTitle(globalSummary.getAttribute("average-label"));
						}
						// 汇总分组列
						if (globalSummary.hasAttribute("group-column")) {
							summaryModel.setGroupColumn(globalSummary.getAttribute("group-column").toLowerCase());
						}
						// 全局汇总合计是否逆向
						if (globalSummary.hasAttribute("reverse")) {
							summaryModel.setGlobalReverse(Boolean.parseBoolean(globalSummary.getAttribute("reverse")));
						}
						if (globalSummary.hasAttribute("sum-label")) {
							summaryModel.setGlobalSumTitle(globalSummary.getAttribute("sum-label"));
						}
					}
					// 分组汇总
					nodeList = elt.getElementsByTagName("group");
					if (nodeList.getLength() > 0) {
						GroupMeta[] groupMetas = new GroupMeta[nodeList.getLength()];
						Element groupElt;
						for (int j = 0; j < nodeList.getLength(); j++) {
							groupElt = (Element) nodeList.item(j);
							GroupMeta groupMeta = new GroupMeta();
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
							groupMetas[j] = groupMeta;
						}
						summaryModel.setGroupMeta(groupMetas);
					}
					resultProcessor.add(summaryModel);
				} // 列与列进行比较
				else if (eltName.equals("cols-chain-relative")) {
					ColsChainRelativeModel colsRelativeModel = new ColsChainRelativeModel();
					XMLUtil.setAttributes(elt, colsRelativeModel);
					resultProcessor.add(colsRelativeModel);
				} // 行与行进行比较
				else if (eltName.equals("rows-chain-relative")) {
					RowsChainRelativeModel rowsRelativeModel = new RowsChainRelativeModel();
					XMLUtil.setAttributes(elt, rowsRelativeModel);
					resultProcessor.add(rowsRelativeModel);
				} // 集合数据顺序颠倒
				else if (eltName.equals("reverse")) {
					ReverseModel reverseModel = new ReverseModel();
					XMLUtil.setAttributes(elt, reverseModel);
					resultProcessor.add(reverseModel);
				}
			}
		}

		// 加入sqlToyConfig
		if (!resultProcessor.isEmpty()) {
			sqlToyConfig.setResultProcessor(resultProcessor);
		}
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

	/**
	 * @TODO 切割nosql 定义的fields,让其符合预期格式,格式为id[col1,col2:aliasName],col3,col4 将其按逗号分隔成
	 * id.col1,id.cols2:aliasName,col3,col4
	 * @param fields
	 * @return
	 */
	private static String[] splitFields(String fields) {
		if (StringUtil.isBlank(fields))
			return null;
		List<String> fieldSet = new ArrayList<String>();
		String[] strs = StringUtil.splitExcludeSymMark(fields, ",", filters);
		String pre;
		String[] params;
		for (String str : strs) {
			if (str.contains("[") && str.contains("]")) {
				pre = str.substring(0, str.indexOf("[")).trim();
				params = str.substring(str.indexOf("[") + 1, str.indexOf("]")).split(",");
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

//	public static void main(String[] args) {
//		String fields;
//		fields = "id[code,sexType:sexTypeName],name";
//		fields = "id[code,sexType],name";
//		fields = "id.code,sexType,name";
//		String[] result = splitFields(fields);
//		for (String str : result) {
//			System.err.println("[" + str + "]");
//		}
//	}
}
