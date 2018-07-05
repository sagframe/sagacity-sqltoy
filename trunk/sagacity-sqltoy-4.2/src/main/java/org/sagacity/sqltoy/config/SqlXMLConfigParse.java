/**
 * @Copyright 2009 版权归陈仁飞，不要肆意侵权抄袭，如引用请注明出处保留作者信息。
 */
package org.sagacity.sqltoy.config;

import java.io.ByteArrayInputStream;
import java.io.File;
import java.io.FileInputStream;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.Iterator;
import java.util.List;
import java.util.concurrent.ConcurrentHashMap;
import java.util.regex.Pattern;

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.dom4j.Document;
import org.dom4j.DocumentException;
import org.dom4j.Element;
import org.dom4j.io.SAXReader;
import org.sagacity.sqltoy.SqlToyConstants;
import org.sagacity.sqltoy.config.model.FormatModel;
import org.sagacity.sqltoy.config.model.GroupMeta;
import org.sagacity.sqltoy.config.model.LinkModel;
import org.sagacity.sqltoy.config.model.NoSqlConfigModel;
import org.sagacity.sqltoy.config.model.ParamFilterModel;
import org.sagacity.sqltoy.config.model.PivotModel;
import org.sagacity.sqltoy.config.model.QueryShardingModel;
import org.sagacity.sqltoy.config.model.SecureMask;
import org.sagacity.sqltoy.config.model.SqlToyConfig;
import org.sagacity.sqltoy.config.model.SqlTranslate;
import org.sagacity.sqltoy.config.model.SqlType;
import org.sagacity.sqltoy.config.model.SummaryModel;
import org.sagacity.sqltoy.config.model.UnpivotModel;
import org.sagacity.sqltoy.plugin.IFunction;
import org.sagacity.sqltoy.utils.BeanUtil;
import org.sagacity.sqltoy.utils.DataSourceUtils;
import org.sagacity.sqltoy.utils.SqlUtil;
import org.sagacity.sqltoy.utils.StringUtil;

/**
 * @project sqltoy-orm
 * @description 解析sql配置文件
 * @author chenrenfei <a href="mailto:zhongxuchen@hotmail.com">联系作者</a>
 * @version id:SqlXMLConfigParse.java,Revision:v1.0,Date:2009-12-14 上午12:07:03
 * @Modification Date:2011-8-30 {增加sql文件设置数据库类别功能，优化解决跨数据库sql文件的配置方式}
 * @Modification Date:2018-1-1 {增加对es和mongo的查询配置解析支持}
 */
@SuppressWarnings({ "rawtypes", "unchecked" })
public class SqlXMLConfigParse {
	/**
	 * 定义全局日志
	 */
	protected final static Logger logger = LogManager.getLogger(SqlXMLConfigParse.class);

	/**
	 * 保存文件最后修改时间的Map
	 */
	private static HashMap filesLastModifyMap = new HashMap();

	/**
	 * 数据库不同方言的函数转换器
	 */
	private static List<IFunction> functionConverts;

	/**
	 * es判断是否有聚合的表达式
	 */
	private final static Pattern ES_AGGS_PATTERN = Pattern
			.compile("(?i)\\W(\"|\')(aggregations|aggs)(\"|\')\\s*\\:\\s*\\{");

	private final static Pattern MONGO_AGGS_PATTERN = Pattern.compile("(?i)\\\\$group\\\\s*\\\\:");

	private static final Pattern GROUP_BY_PATTERN = Pattern.compile("(?i)\\Wgroup\\s+by\\W");

	/**
	 * @param functionConverts
	 *            the functionConverts to set
	 */
	public static void setFunctionConverts(List<IFunction> functionConverts) {
		SqlXMLConfigParse.functionConverts = functionConverts;
	}

	/**
	 * @todo 判断文件 是否被修改，修改了则重新解析文件重置缓存
	 * @param xmlFiles
	 * @param cache
	 * @param encoding
	 * @param dialect
	 * @throws Exception
	 */
	public static void parseXML(List xmlFiles, ConcurrentHashMap<String, SqlToyConfig> cache, String encoding,
			String dialect) throws Exception {
		if (xmlFiles == null || xmlFiles.isEmpty())
			return;
		File sqlFile;
		String fileName;
		Object resource;
		for (int i = 0; i < xmlFiles.size(); i++) {
			resource = xmlFiles.get(i);
			if (resource instanceof File) {
				sqlFile = (File) resource;
				fileName = sqlFile.getName();
				Long lastModified = new Long(sqlFile.lastModified());
				// 调试模式，判断文件的最后修改时间，决定是否重新加载sql
				Long preModified = (Long) filesLastModifyMap.get(fileName);
				// 最后修改时间比上次修改时间大，重新加载sql文件
				if (preModified == null || lastModified.longValue() > preModified.longValue()) {
					filesLastModifyMap.put(fileName, lastModified);
					logger.debug("sql文件:{}已经被修改,进行重新解析!", fileName);
					parseSingleFile(sqlFile, cache, encoding, dialect);
				}
			}
		}
	}

	/**
	 * @todo <b>解析单个sql对应的xml文件</b>
	 * @param xmlFile
	 * @param cache
	 * @param encoding
	 * @param dialect
	 * @throws Exception
	 */
	public static void parseSingleFile(Object xmlFile, ConcurrentHashMap<String, SqlToyConfig> cache, String encoding,
			String dialect) throws Exception {
		InputStream fileIS = null;
		InputStreamReader ir = null;
		try {
			if (xmlFile instanceof File) {
				File file = (File) xmlFile;
				filesLastModifyMap.put(file.getName(), new Long(file.lastModified()));
				fileIS = new FileInputStream(file);
				if (logger.isDebugEnabled())
					logger.debug("正在解析sql文件,对应文件={}", file.getName());
			} else {
				fileIS = getResourceAsStream((String) xmlFile);
				if (logger.isDebugEnabled())
					logger.debug("正在解析sql文件,对应文件={}", (String) xmlFile);
			}
			if (fileIS != null) {
				if (encoding != null)
					ir = new InputStreamReader(fileIS, encoding);
				else
					ir = new InputStreamReader(fileIS);
				SAXReader saxReader = new SAXReader();
				saxReader.setFeature(SqlToyConstants.XML_FETURE, false);
				if (StringUtil.isNotBlank(encoding))
					saxReader.setEncoding(encoding);
				Document doc = saxReader.read(ir);
				List<Element> sqlElts = doc.getRootElement().elements();
				if (sqlElts == null || sqlElts.isEmpty())
					return;
				// 解析单个sql
				SqlToyConfig sqlToyConfig;
				for (Iterator<Element> iter = sqlElts.iterator(); iter.hasNext();) {
					sqlToyConfig = parseSingleSql(iter.next(), dialect);
					if (sqlToyConfig != null) {
						// 去除sql中的注释语句并放入缓存
						if (cache.get(sqlToyConfig.getId()) != null)
							logger.warn("发现重复的SQL语句,id={},将被覆盖!", sqlToyConfig.getId());
						cache.put(sqlToyConfig.getId(), sqlToyConfig);
					}
				}
			}
		} catch (DocumentException de) {
			de.printStackTrace();
			logger.error("读取sql对应的xml文件失败,对应文件={}", xmlFile, de);
			throw de;
		} catch (Exception e) {
			e.printStackTrace();
			logger.error(
					"解析xml中对应的sql失败,对应文件={},正确的配置为<sql|mql|eql id=\"\"><![CDATA[]]></sql|mql|eql>或<sql|mql|eql id=\"\"><desc></desc><value><![CDATA[]]></value></sql|mql|eql>",
					xmlFile, e);
			throw e;
		} finally {
			if (ir != null)
				ir.close();
			if (fileIS != null)
				fileIS.close();
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
			SAXReader saxReader = new SAXReader();
			if (StringUtil.isNotBlank(encoding))
				saxReader.setEncoding(encoding);
			Document doc = saxReader.read(new ByteArrayInputStream(((String) sqlSegment).getBytes(encoding)));
			sqlToyConfig = parseSingleSql(doc.getRootElement(), dialect);
		} else if (sqlSegment instanceof Element)
			sqlToyConfig = parseSingleSql((Element) sqlSegment, dialect);
		return sqlToyConfig;
	}

	/**
	 * @todo 解析单个sql element元素
	 * @param sqlElt
	 * @param dialect
	 * @return
	 * @throws Exception
	 */
	private static SqlToyConfig parseSingleSql(Element sqlElt, String dialect) throws Exception {
		String realDialect = dialect;
		String nodeName = sqlElt.getName().toLowerCase();
		// 目前只支持传统sql、elastic、mongo三种类型的语法
		if (!nodeName.equals("sql") && !nodeName.equals("eql") && !nodeName.equals("mql"))
			return null;
		// 判断是否xml为精简模式即只有<sql id=""><![CDATA[]]></sql>模式
		String sqlContent = (sqlElt.isTextOnly()) ? sqlElt.getText() : sqlElt.elementText("value");
		String countSql = (sqlElt.element("count-sql") == null) ? null : sqlElt.elementText("count-sql");

		if (null == sqlContent || sqlContent.trim().equals(""))
			throw new Exception("请检查sql配置,没有正确填写sql内容!");

		String id = sqlElt.attributeValue("id");
		if (id == null)
			throw new Exception("请检查sql配置,没有给定sql id!");

		// 替换全角空格
		if (sqlContent != null) {
			sqlContent = sqlContent.replaceAll("\u3000", " ");
		}
		if (countSql != null) {
			countSql = countSql.replaceAll("\u3000", " ");
		}
		SqlType type = (sqlElt.attribute("type") == null) ? SqlType.search
				: SqlType.getSqlType(sqlElt.attributeValue("type"));
		// 是否nosql模式
		boolean isNoSql = false;
		if (nodeName.equals("mql") || nodeName.equals("eql")) {
			if (nodeName.equals("mql"))
				realDialect = DataSourceUtils.Dialect.MONGO;
			else if (nodeName.equals("eql"))
				realDialect = DataSourceUtils.Dialect.ES;
			isNoSql = true;
		}
		SqlToyConfig sqlToyConfig = SqlConfigParseUtils.parseSqlToyConfig(sqlContent, realDialect, type,
				functionConverts);
		sqlToyConfig.setId(id);
		sqlToyConfig.setSqlType(type);
		// 为sql提供特定数据库的扩展
		if (sqlElt.attribute("dataSource") != null)
			sqlToyConfig.setDataSource(sqlElt.attributeValue("dataSource"));
		else if (sqlElt.attribute("datasource") != null)
			sqlToyConfig.setDataSource(sqlElt.attributeValue("datasource"));
		if (countSql != null) {
			// 清理sql中的一些注释、以及特殊的符号
			countSql = StringUtil.clearMistyChars(SqlUtil.clearMark(countSql), " ").concat(" ");
			countSql = SqlConfigParseUtils.convertFunctions(functionConverts, dialect, countSql);
			sqlToyConfig.setCountSql(countSql);
		}
		/**
		 * 是否是单纯的union all分页(在取count记录数时,将union all 每部分的查询from前面的全部替换成 select 1
		 * from,减少不必要的执行运算，提升效率)
		 */
		if (sqlElt.attribute("union-all-count") != null)
			sqlToyConfig.setUnionAllCount(Boolean.parseBoolean(sqlElt.attributeValue("union-all-count")));
		// 解析sql对应dataSource的sharding配置
		parseShardingDataSource(sqlToyConfig, sqlElt.element("sharding-datasource"));

		// 解析sql对应的table的sharding配置
		parseShardingTables(sqlToyConfig, sqlElt.elements("sharding-table"));
		// 解析格式化
		parseFormat(sqlToyConfig, sqlElt.elements("dateFormat"), sqlElt.elements("numberFormat"));
		// 参数值为空白是否当中null处理,默认为-1
		int blankToNull = -1;
		if (sqlElt.attribute("blank-to-null") != null)
			blankToNull = (Boolean.parseBoolean(sqlElt.attributeValue("blank-to-null"))) ? 1 : 0;
		// 解析参数过滤器
		if (sqlElt.element("filters") != null)
			sqlToyConfig.setFilters(parseFilters(sqlElt.element("filters").elements(), blankToNull));
		else
			sqlToyConfig.setFilters(parseFilters(null, blankToNull));

		// 解析分页优化器
		// <page-optimize alive-max="100" alive-seconds="600"/>
		Element pageOptimize = sqlElt.element("page-optimize");
		if (pageOptimize != null) {
			sqlToyConfig.setPageOptimize(true);
			if (pageOptimize.attribute("alive-max") != null)
				sqlToyConfig.setPageAliveMax(Integer.parseInt(pageOptimize.attributeValue("alive-max")));

			if (pageOptimize.attribute("alive-seconds") != null)
				sqlToyConfig.setPageAliveSeconds(Integer.parseInt(pageOptimize.attributeValue("alive-seconds")));
		}

		// 解析翻译器
		sqlToyConfig.setTranslateMap(parseTranslate(sqlElt.elements("translate")));
		// 解析link
		parseLink(sqlToyConfig, sqlElt.element("link"));
		// 解析对结果的运算
		parseCalculator(sqlToyConfig, sqlElt);

		// 解析安全脱敏配置
		parseSecureMask(sqlToyConfig, sqlElt.elements("secure-mask"));
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
		if (sqlElt.attribute("collection") != null)
			noSqlConfig.setCollection(sqlElt.attributeValue("collection"));
		if (sqlElt.attribute("mongo-factory") != null)
			noSqlConfig.setMongoFactory(sqlElt.attributeValue("mongo-factory"));
		// url应该是一个变量如:${es_url}
		if (sqlElt.attribute("url") != null)
			noSqlConfig.setEndpoint(SqlToyConstants.replaceParams(sqlElt.attributeValue("url")));
		else if (sqlElt.attribute("end-point") != null)
			noSqlConfig.setEndpoint(SqlToyConstants.replaceParams(sqlElt.attributeValue("end-point")));
		else if (sqlElt.attribute("endpoint") != null)
			noSqlConfig.setEndpoint(SqlToyConstants.replaceParams(sqlElt.attributeValue("endpoint")));
		// 索引
		if (sqlElt.attribute("index") != null)
			noSqlConfig.setIndex(sqlElt.attributeValue("index"));

		if (sqlElt.attribute("type") != null)
			noSqlConfig.setType(sqlElt.attributeValue("type"));
		// 请求超时时间(单位毫秒)
		if (sqlElt.attribute("request-timeout") != null)
			noSqlConfig.setRequestTimeout(Integer.parseInt(sqlElt.attributeValue("request-timeout")));
		// 连接超时时间(单位毫秒)
		if (sqlElt.attribute("connection-timeout") != null)
			noSqlConfig.setConnectTimeout(Integer.parseInt(sqlElt.attributeValue("connection-timeout")));

		// 整个请求超时时长(毫秒)
		if (sqlElt.attribute("socket-timeout") != null)
			noSqlConfig.setSocketTimeout(Integer.parseInt(sqlElt.attributeValue("socket-timeout")));

		// url请求字符集类型
		if (sqlElt.attribute("charset") != null)
			noSqlConfig.setCharset(sqlElt.attributeValue("charset"));
		// fields
		if (sqlElt.attribute("fields") != null) {
			if (StringUtil.isNotBlank(sqlElt.attributeValue("fields")))
				noSqlConfig.setFields(trimParams(sqlElt.attributeValue("fields").split("\\,")));
		} else if (sqlElt.element("fields") != null)
			noSqlConfig.setFields(trimParams(sqlElt.elementTextTrim("fields").split("\\,")));

		// valueRoot
		if (sqlElt.attribute("value-root") != null)
			noSqlConfig.setValueRoot(trimParams(sqlElt.attributeValue("value-root").split("\\,")));
		else if (sqlElt.attribute("value-path") != null)
			noSqlConfig.setValueRoot(trimParams(sqlElt.attributeValue("value-path").split("\\,")));
		// 是否有聚合查询
		if (sqlElt.getName().equalsIgnoreCase("eql")) {
			if (sqlElt.attribute("aggregate") != null) {
				noSqlConfig.setHasAggs(Boolean.parseBoolean(sqlElt.attributeValue("aggregate")));
			} else {
				noSqlConfig.setHasAggs(StringUtil.matches(sqlToyConfig.getSql(), ES_AGGS_PATTERN));
			}
			// 判断查询语句的模式是否sql模式
			if (StringUtil.matches(sqlToyConfig.getSql(), "(?i)\\s*select\\s*")
					&& sqlToyConfig.getSql().toLowerCase().indexOf("from") > 0) {
				noSqlConfig.setSqlMode(true);
				// sql模式下存在group by 则判定为聚合查询
				if (StringUtil.matches(sqlToyConfig.getSql(), GROUP_BY_PATTERN))
					noSqlConfig.setHasAggs(true);
			}
		} else if (sqlElt.getName().equalsIgnoreCase("mql")) {
			if (sqlElt.attribute("aggregate") != null) {
				noSqlConfig.setHasAggs(Boolean.parseBoolean(sqlElt.attributeValue("aggregate")));
			} else
				noSqlConfig.setHasAggs(StringUtil.matches(sqlToyConfig.getSql(), MONGO_AGGS_PATTERN));
		}

		sqlToyConfig.setNoSqlConfigModel(noSqlConfig);
		// nosql参数解析模式不同于sql
		if (!noSqlConfig.isSqlMode())
			sqlToyConfig.setParamsName(SqlConfigParseUtils.getNoSqlParamsName(sqlToyConfig.getSql(), true));
	}

	/**
	 * @todo 解析安全脱敏配置
	 * @param sqlToyConfig
	 * @param maskElts
	 */
	public static void parseSecureMask(SqlToyConfig sqlToyConfig, List<Element> maskElts) {
		if (maskElts != null && !maskElts.isEmpty()) {
			// <secure-mask columns="" type="name" head-size="" tail-size=""
			// mask-code="*****" mask-rate="50%"/>
			List<SecureMask> secureMasks = new ArrayList<SecureMask>();
			String tmp;
			for (Element elt : maskElts) {
				tmp = getAttrValue(elt, "columns");
				// 兼容老版本
				if (tmp == null)
					tmp = getAttrValue(elt, "column");
				String[] columns = tmp.toLowerCase().split("\\,");
				String type = getAttrValue(elt, "type").toLowerCase();
				String maskCode = getAttrValue(elt, "mask-code");
				String headSize = getAttrValue(elt, "head-size");
				String tailSize = getAttrValue(elt, "tail-size");
				String maskRate = getAttrValue(elt, "mask-rate");
				if (maskRate == null)
					maskRate = getAttrValue(elt, "mask-percent");
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
						} else
							secureMask.setMaskCode("****");
					}
					if (StringUtil.isNotBlank(headSize))
						secureMask.setHeadSize(Integer.parseInt(headSize));
					if (StringUtil.isNotBlank(tailSize))
						secureMask.setTailSize(Integer.parseInt(tailSize));
					if (StringUtil.isNotBlank(maskRate))
						secureMask.setMaskRate(Integer.parseInt(maskRate.replace("%", "")));
					secureMasks.add(secureMask);
				}
			}
			sqlToyConfig.setSecureMasks((SecureMask[]) secureMasks.toArray());
		}
	}

	private static String getAttrValue(Element elt, String attrName) {
		if (elt.attribute(attrName) != null)
			return elt.attributeValue(attrName);
		return null;
	}

	/**
	 * @todo 解析dataSource的sharding
	 * @param sqlToyConfig
	 * @param shardingDataSource
	 */
	private static void parseShardingDataSource(SqlToyConfig sqlToyConfig, Element shardingDataSource) {
		if (shardingDataSource == null)
			return;
		// 策略辨别值
		if (shardingDataSource.attribute("strategy-value") != null) {
			sqlToyConfig.setDataSourceShardingStrategyValue(shardingDataSource.attributeValue("strategy-value"));
		}
		if (shardingDataSource.attribute("params") != null) {
			sqlToyConfig.setDataSourceShardingParams(
					shardingDataSource.attributeValue("params").replace(";", ",").toLowerCase().split("\\,"));
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
		sqlToyConfig.setDataSourceShardingStragety(shardingDataSource.attributeValue("strategy"));
	}

	/**
	 * @todo 解析table的sharding
	 * @param sqlToyModel
	 * @param shardingTables
	 */
	private static void parseShardingTables(SqlToyConfig sqlToyConfig, List<Element> shardingTables) {
		if (shardingTables == null || shardingTables.isEmpty())
			return;
		List<QueryShardingModel> tablesShardings = new ArrayList();
		String[] paramName;
		String[] paramsAlias;
		int size;
		List<String> params = new ArrayList();
		for (Element elt : shardingTables) {
			if (elt.attribute("tables") != null && elt.attribute("strategy") != null) {
				QueryShardingModel shardingModel = new QueryShardingModel();
				shardingModel.setTables(elt.attributeValue("tables").split(","));
				if (elt.attribute("params") != null) {
					// params="a:a1,b:b1";params为{a:a1, b:b1}
					shardingModel.setParams(elt.attributeValue("params").replace(";", ",").toLowerCase().split("\\,"));
					size = shardingModel.getParams().length;
					paramsAlias = new String[size];
					for (int i = 0; i < size; i++) {
						paramName = shardingModel.getParams()[i].split("\\:");
						// 重置params数组值
						shardingModel.getParams()[i] = paramName[0].trim();
						params.add(shardingModel.getParams()[i]);
						paramsAlias[i] = paramName[paramName.length - 1].trim();
					}
					shardingModel.setParamsAlias(paramsAlias);
				}
				if (elt.attribute("strategy-value") != null)
					shardingModel.setStrategyValue(elt.attributeValue("strategy-value"));
				shardingModel.setStrategy(elt.attributeValue("strategy"));
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
	 * @param filterSet
	 * @param blankToNull
	 * @return
	 */
	public static ParamFilterModel[] parseFilters(List<Element> filterSet, int blankToNull) {
		List<ParamFilterModel> filterModels = new ArrayList<ParamFilterModel>();
		// 1:强制将空白当做null;0:强制对空白不作为null处理;-1:默认值,用户不配置blank过滤器则视同为1,配置了则视同为0
		if (blankToNull == 1)
			filterModels.add(new ParamFilterModel("blank", new String[] { "*" }));
		boolean hasBlank = false;
		if (filterSet != null && !filterSet.isEmpty()) {
			String filterType;
			boolean blank = false;
			for (Element filter : filterSet) {
				blank = false;
				filterType = filter.getName();
				// 当开发者配置了blank过滤器时，则表示关闭默认将全部空白当做null处理的逻辑
				if (filterType.equals("blank")) {
					hasBlank = true;
					blank = true;
				}
				// [非强制且是blank ] 或者 [ 非blank]
				if ((blank && blankToNull != 1) || !blank) {
					ParamFilterModel filterModel = new ParamFilterModel();
					// 统一过滤的类别,避免不同版本和命名差异
					if (filterType.equals("equals") || filterType.equals("any") || filterType.equals("in"))
						filterType = "eq";
					else if (filterType.equals("moreThan") || filterType.equals("more"))
						filterType = "gt";
					else if (filterType.equals("moreEquals") || filterType.equals("more-equals"))
						filterType = "gte";
					else if (filterType.equals("lessThan") || filterType.equals("less"))
						filterType = "lt";
					else if (filterType.equals("lessEquals") || filterType.equals("less-equals"))
						filterType = "lte";
					else if (filterType.equals("not-any"))
						filterType = "neq";
					else if (filterType.equals("dateFormat"))
						filterType = "date-format";
					filterModel.setFilterType(filterType);
					parseFilterElt(filterModel, filter);
					filterModels.add(filterModel);
				}
			}
		}
		// 当没有特定配置时，默认将所有空白当做null处理
		if (!hasBlank && blankToNull == -1)
			filterModels.add(0, new ParamFilterModel("blank", new String[] { "*" }));
		if (filterModels.isEmpty())
			return null;
		ParamFilterModel[] result = new ParamFilterModel[filterModels.size()];
		filterModels.toArray(result);
		return result;
	}

	/**
	 * @todo 解析filter
	 * @param filterModel
	 * @param filter
	 */
	private static void parseFilterElt(ParamFilterModel filterModel, Element filter) {
		// 没有设置参数名称，则表示全部参数用*表示
		if (filter.attribute("params") == null) {
			filterModel.setParams(new String[] { "*" });
		} else
			filterModel.setParams(trimParams(filter.attributeValue("params").toLowerCase().split("\\,")));
		// equals\any\not-any等类型
		if (filter.attribute("value") != null) {
			filterModel.setValues(
					StringUtil.splitExcludeSymMark(filter.attributeValue("value"), ",", SqlToyConstants.filters));
		} else if (filter.attribute("start-value") != null && filter.attribute("end-value") != null)
			filterModel.setValues(
					new String[] { filter.attributeValue("start-value"), filter.attributeValue("end-value") });
		if (filter.attribute("increment-days") != null)
			filterModel.setIncrementDays(Double.valueOf(filter.attributeValue("increment-days")));

		// to-date filter
		if (filter.attribute("format") != null)
			filterModel.setFormat(filter.attributeValue("format"));
		// regex(replace filter)
		if (filter.attribute("regex") != null)
			filterModel.setRegex(filter.attributeValue("regex"));

		// 用于replace 转换器,设置是否是替换首个匹配的字符
		if (filter.attribute("is-first") != null)
			filterModel.setFirst(Boolean.parseBoolean(filter.attributeValue("is-first")));

		// 分割符号
		if (filter.attribute("split-sign") != null)
			filterModel.setSplit(filter.attributeValue("split-sign"));

		// 互斥型和决定性(primary)filter的参数
		if (filter.attribute("excludes") != null) {
			String[] excludeParams = filter.attributeValue("excludes").toLowerCase().split("\\,");
			HashMap<String, String> excludeMaps = new HashMap<String, String>();
			for (String excludeParam : excludeParams)
				excludeMaps.put(excludeParam.trim(), "1");
			filterModel.setExcludesMap(excludeMaps);
		}

		// exclusive 和primary filter 专用参数
		if (filter.attribute("param") != null)
			filterModel.setParam(filter.attributeValue("param").toLowerCase());

		// exclusive 排他性filter 当条件成立时需要修改的参数(即排斥的参数)
		if (filter.attribute("set-params") != null)
			filterModel.setUpdateParams(trimParams(filter.attributeValue("set-params").toLowerCase().split("\\,")));
		else if (filter.attribute("exclusive-params") != null)
			filterModel.setUpdateParams(trimParams(filter.attributeValue("exclusive-params").toLowerCase().split("\\,")));

		// exclusive 排他性filter 对排斥的参数设置的值(默认置为null)
		if (filter.attribute("set-value") != null)
			filterModel.setUpdateValue(filter.attributeValue("set-value"));

		// exclusive 排他性filter 条件成立的对比方式
		if (filter.attribute("compare-type") != null) {
			String compareType = filter.attributeValue("compare-type");
			if (compareType.equals("eq") || compareType.equals("==") || compareType.equals("equals")
					|| compareType.equals("="))
				filterModel.setCompareType("==");
			else if (compareType.equals("neq") || compareType.equals("<>") || compareType.equals("!=")
					|| compareType.equals("ne"))
				filterModel.setCompareType("<>");
			else if (compareType.equals(">") || compareType.equals("gt") || compareType.equals("more"))
				filterModel.setCompareType(">");
			else if (compareType.equals(">=") || compareType.equals("gte") || compareType.equals("ge"))
				filterModel.setCompareType(">=");
			else if (compareType.equals("<") || compareType.equals("lt") || compareType.equals("less"))
				filterModel.setCompareType("<");
			else if (compareType.equals("<=") || compareType.equals("lte") || compareType.equals("le"))
				filterModel.setCompareType("<=");
			else if (compareType.equals("between"))
				filterModel.setCompareType("between");
			else if (compareType.equals("any") || compareType.equals("in"))
				filterModel.setCompareType("any");
		}
		// exclusive 排他性filter 条件成立的对比值
		if (filter.attribute("compare-values") != null) {
			String compareValue = filter.attributeValue("compare-values");
			if (compareValue.indexOf(";") != -1)
				filterModel.setCompareValues(compareValue.split("\\;"));
			else
				filterModel.setCompareValues(compareValue.split("\\,"));
		}

		// 数据类型
		if (filter.attribute("data-type") != null)
			filterModel.setDataType(filter.attributeValue("data-type").toLowerCase());
	}

	/**
	 * @todo 解析翻译器
	 * @param translates
	 * @return
	 */
	public static HashMap<String, SqlTranslate> parseTranslate(List<Element> translates) {
		if (translates != null && !translates.isEmpty()) {
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
			for (Element translate : translates) {
				hasLink = false;
				cacheName = translate.attributeValue("cache");
				// 具体的缓存子分类，如数据字典类别
				if (translate.attribute("cache-type") != null)
					cacheType = translate.attributeValue("cache-type");
				else
					cacheType = null;
				columns = trimParams(translate.attributeValue("columns").toLowerCase().split("\\,"));
				aliasNames = null;
				uncachedTemplate = null;
				if (translate.attribute("undefine-template") != null)
					uncachedTemplate = translate.attributeValue("undefine-template");
				else if (translate.attribute("uncached-template") != null)
					uncachedTemplate = translate.attributeValue("uncached-template");
				else if (translate.attribute("uncached") != null)
					uncachedTemplate = translate.attributeValue("uncached");

				if (translate.attribute("split-regex") != null) {
					splitRegex = translate.attributeValue("split-regex");
					if (translate.attribute("link-sign") != null) {
						linkSign = translate.attributeValue("link-sign");
						hasLink = true;
					}
					// 正则转化
					if (splitRegex.equals(",") || splitRegex.equals("，")) {
						splitRegex = "\\,";
					} else if (splitRegex.equals(";") || splitRegex.equals("；")) {
						splitRegex = "\\;";
						if (!hasLink)
							linkSign = ";";
					} else if (splitRegex.equals("、")) {
						splitRegex = "\\、";
					} else if (splitRegex.equals("->")) {
						splitRegex = "\\-\\>";
						if (!hasLink)
							linkSign = "->";
					}
				}
				// 使用alias时只能针对单列处理
				if (translate.attribute("alias-name") != null)
					aliasNames = trimParams(translate.attributeValue("alias-name").toLowerCase().split("\\,"));
				// 翻译key对应value的在缓存数组中对应的列
				cacheIndexs = null;
				if (translate.attribute("cache-indexs") != null) {
					cacheIndexStr = trimParams(translate.attributeValue("cache-indexs").split("\\,"));
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
							if (uncachedTemplate.trim().equals(""))
								translateModel.setUncached(null);
							else
								translateModel.setUncached(uncachedTemplate);
						}
						if (cacheIndexs != null) {
							if (i < cacheIndexs.length - 1)
								translateModel.setIndex(cacheIndexs[i]);
							else
								translateModel.setIndex(cacheIndexs[cacheIndexs.length - 1]);
						}
						translateMap.put(translateModel.getColumn(), translateModel);
					}
				} else
					logger.warn("cache translate columns must mapped with cache-indexs!");
			}
			return translateMap;
		}
		return null;
	}

	/**
	 * @todo 解析Link 查询
	 * @param sqlToyConfig
	 * @param link
	 */
	private static void parseLink(SqlToyConfig sqlToyConfig, Element link) {
		if (link == null)
			return;
		LinkModel linkModel = new LinkModel();
		linkModel.setColumn(link.attributeValue("column"));
		if (link.attribute("id-column") != null)
			linkModel.setIdColumn(link.attributeValue("id-column"));
		if (link.attribute("sign") != null)
			linkModel.setSign(link.attributeValue("sign"));
		if (link.element("decorate") != null) {
			Element decorateElt = link.element("decorate");
			if (decorateElt.attribute("align") != null)
				linkModel.setDecorateAlign(decorateElt.attributeValue("align").toLowerCase());
			linkModel.setDecorateAppendChar(decorateElt.attributeValue("char"));
			linkModel.setDecorateSize(Integer.parseInt(decorateElt.attributeValue("size")));
		}
		sqlToyConfig.setLinkModel(linkModel);
	}

	/**
	 * @todo 解析列格式化
	 * @param sqlToyConfig
	 * @param dfElts
	 * @param nfElts
	 */
	private static void parseFormat(SqlToyConfig sqlToyConfig, List<Element> dfElts, List<Element> nfElts) {
		List<FormatModel> formatModels = new ArrayList<FormatModel>();
		if (dfElts != null && !dfElts.isEmpty()) {
			for (Element df : dfElts) {
				String[] columns = df.attributeValue("columns").toLowerCase().split("\\,");
				String format = (df.attribute("format") == null) ? "yyyy-MM-dd" : df.attributeValue("format");
				for (String col : columns) {
					FormatModel formatModel = new FormatModel();
					formatModel.setColumn(col);
					formatModel.setType(1);
					formatModel.setFormat(format);
					formatModels.add(formatModel);
				}
			}
		}
		if (nfElts != null && !nfElts.isEmpty()) {
			for (Element nf : nfElts) {
				String[] columns = nf.attributeValue("columns").toLowerCase().split("\\,");
				String format = (nf.attribute("format") == null) ? "capital" : nf.attributeValue("format");
				for (String col : columns) {
					FormatModel formatModel = new FormatModel();
					formatModel.setColumn(col);
					formatModel.setType(2);
					formatModel.setFormat(format);
					formatModels.add(formatModel);
				}
			}
		}
		if (!formatModels.isEmpty())
			sqlToyConfig.setFormatModels((FormatModel[]) formatModels.toArray());
	}

	/**
	 * @todo 解析对sqltoy查询结果的处理逻辑定义
	 * @param sqlToyConfig
	 * @param sqlElt
	 */
	private static void parseCalculator(SqlToyConfig sqlToyConfig, Element sqlElt) {
		List elements = sqlElt.elements();
		Element elt;
		String eltName;
		List resultProcessor = new ArrayList();
		for (int i = 0; i < elements.size(); i++) {
			elt = (Element) elements.get(i);
			eltName = elt.getName();
			// 旋转(只能进行一次旋转)
			if (eltName.equals("pivot")) {
				PivotModel pivotModel = new PivotModel();
				if (elt.attribute("group-columns") != null)
					pivotModel.setGroupCols(trimParams(elt.attributeValue("group-columns").toLowerCase().split("\\,")));
				if (elt.attribute("category-columns") != null)
					pivotModel.setCategoryCols(
							trimParams(elt.attributeValue("category-columns").toLowerCase().split("\\,")));
				if (elt.attribute("category-sql") != null)
					pivotModel.setCategorySql(elt.attributeValue("category-sql"));
				String[] pivotCols = new String[2];
				pivotCols[0] = elt.attributeValue("start-column").toLowerCase();
				if (elt.attribute("end-column") != null)
					pivotCols[1] = elt.attributeValue("end-column").toLowerCase();
				else
					pivotCols[1] = pivotCols[0];
				if (elt.attribute("default-value") != null) {
					String defaultValue = elt.attributeValue("default-value");
					if (elt.attribute("default-type") != null) {
						String defaultType = elt.attributeValue("default-type");
						try {
							pivotModel.setDefaultValue(BeanUtil.convertType(defaultValue, defaultType));
						} catch (Exception e) {

						}
					} else
						pivotModel.setDefaultValue(defaultValue);
				}
				pivotModel.setPivotCols(pivotCols);
				resultProcessor.add(pivotModel);
			} // 列转行
			else if (eltName.equals("unpivot")) {
				if (elt.attribute("columns") != null && elt.attribute("values-as-column") != null) {
					UnpivotModel unpivotModel = new UnpivotModel();
					String[] columns = elt.attributeValue("columns").split("\\,");
					String[] realCols = new String[columns.length];
					String[] colsAlias = new String[columns.length];
					int index = 0;
					String[] temp;
					for (String column : columns) {
						temp = column.split(":");
						realCols[index] = temp[0].trim().toLowerCase();
						colsAlias[index] = temp[temp.length - 1];
						index++;
					}
					unpivotModel.setColumns(realCols);
					unpivotModel.setColsAlias(colsAlias);
					// 多列变成行时转成的列名称
					unpivotModel.setAsColumn(elt.attributeValue("values-as-column"));
					// 变成行的列标题作为的新列名称
					if (elt.attribute("labels-as-column") != null)
						unpivotModel.setLabelsColumn(elt.attributeValue("labels-as-column"));
					// 必须要有2个或以上列
					if (index > 1)
						resultProcessor.add(unpivotModel);
				}
			}
			// 汇总合计
			else if (eltName.equals("summary")) {
				SummaryModel summaryModel = new SummaryModel();
				// 是否逆向汇总
				if (elt.attribute("reverse") != null) {
					summaryModel.setReverse(Boolean.parseBoolean(elt.attributeValue("reverse")));
					summaryModel.setGlobalReverse(summaryModel.isReverse());
				}
				// 汇总合计涉及的列
				if (elt.attribute("columns") != null)
					summaryModel.setSummaryCols(elt.attributeValue("columns").toLowerCase());
				// 保留小数点位数
				if (elt.attribute("radix-size") != null)
					summaryModel.setRadixSize(Integer.parseInt(elt.attributeValue("radix-size")));
				else
					summaryModel.setRadixSize(-1);
				// 汇总所在位置
				if (elt.attribute("sum-site") != null)
					summaryModel.setSumSite(elt.attributeValue("sum-site"));
				// sum和average值左右拼接时的连接字符串
				if (elt.attribute("link-sign") != null)
					summaryModel.setLinkSign(elt.attributeValue("link-sign"));

				// 全局汇总
				Element globalSummary = elt.element("global");
				if (globalSummary != null) {
					if (globalSummary.attribute("label-column") != null)
						summaryModel.setGlobalLabelColumn(globalSummary.attributeValue("label-column").toLowerCase());
					if (globalSummary.attribute("average-label") != null)
						summaryModel.setGlobalAverageTitle(globalSummary.attributeValue("average-label"));
					// 汇总分组列
					if (globalSummary.attribute("group-column") != null)
						summaryModel.setGroupColumn(globalSummary.attributeValue("group-column").toLowerCase());
					// 全局汇总合计是否逆向
					if (globalSummary.attribute("reverse") != null)
						summaryModel.setGlobalReverse(Boolean.parseBoolean(globalSummary.attributeValue("reverse")));
					if (globalSummary.attribute("sum-label") != null)
						summaryModel.setGlobalSumTitle(globalSummary.attributeValue("sum-label"));
				}
				// 分组汇总
				List<Element> groupElts = elt.elements("group");
				if (groupElts != null && !groupElts.isEmpty()) {
					GroupMeta[] groupMetas = new GroupMeta[groupElts.size()];
					int index = 0;
					for (Element groupElt : groupElts) {
						GroupMeta groupMeta = new GroupMeta();
						groupMeta.setGroupColumn(groupElt.attributeValue("group-column").toLowerCase());
						if (groupElt.attribute("average-label") != null)
							groupMeta.setAverageTitle(groupElt.attributeValue("average-label"));
						if (groupElt.attribute("sum-label") != null)
							groupMeta.setSumTitle(groupElt.attributeValue("sum-label"));
						if (groupElt.attribute("label-column") != null)
							groupMeta.setLabelColumn(groupElt.attributeValue("label-column"));
						groupMetas[index] = groupMeta;
						index++;
					}
					summaryModel.setGroupMeta(groupMetas);
				}
				resultProcessor.add(summaryModel);
			}
		}

		// 加入sqltoyModel
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
	 * 对split之后参数名称进行trim
	 * 
	 * @param paramNames
	 * @return
	 */
	private static String[] trimParams(String[] paramNames) {
		if (paramNames == null || paramNames.length == 0)
			return paramNames;
		String[] realParamNames = new String[paramNames.length];
		for (int i = 0; i < paramNames.length; i++)
			realParamNames[i] = (paramNames[i] == null) ? null : paramNames[i].trim();
		return realParamNames;
	}
}
