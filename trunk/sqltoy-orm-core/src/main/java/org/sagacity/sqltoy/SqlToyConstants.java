package org.sagacity.sqltoy;

import java.io.IOException;
import java.io.InputStream;
import java.time.ZoneId;
import java.util.HashMap;
import java.util.Iterator;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Properties;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import org.sagacity.sqltoy.model.IgnoreKeyCaseMap;
import org.sagacity.sqltoy.utils.FileUtil;
import org.sagacity.sqltoy.utils.IdUtil;
import org.sagacity.sqltoy.utils.StringUtil;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * @project sagacity-sqltoy
 * @description sqlToy的基础常量参数定义
 * @author zhongxuchen
 * @version v1.0,Date:2014年12月26日
 */
public class SqlToyConstants {

	/**
	 * 定义日志
	 */
	protected final static Logger logger = LoggerFactory.getLogger(SqlToyConstants.class);

	/**
	 * 符号对,用来提取字符串中对称字符的过滤,如:{ name(){} }，第一个{对称的符合}是最后一位
	 */
	public static HashMap<String, String> filters = new HashMap<String, String>() {
		/**
		 * 
		 */
		private static final long serialVersionUID = 1636155921862321269L;
		{
			put("(", ")");
			put("'", "'");
			put("\"", "\"");
			put("[", "]");
			put("{", "}");
		}
	};

	// 目前还不支持此功能的提醒
	public static String UN_SUPPORT_MESSAGE = "This feature is currently not supported!";

	public static final String DEFAULT_NULL = "_SQLTOY_NULL_FLAG";

	public static String UN_MATCH_DIALECT_MESSAGE = "Failed to correctly match the corresponding database dialect!";

	/**
	 * 判断sql中是否存在union all的表达式
	 */
	public static String UNION_ALL_REGEX = "\\W+union\\s+\\all\\W+";

	/**
	 * 判断sql中是否存在union的表达式
	 */
	public static String UNION_REGEX = "\\W+union\\W+";

	/**
	 * 当sql中是参数条件是?时转换后对应的别名模式:sagParamIndexName+index,如sagParamIndexName0、sagParamIndexName1
	 * 统一成别名模式的好处在于解决诸如分页、取随机记录等封装处理的统一性问题
	 */
	public static String DEFAULT_PARAM_NAME = "sagParamIndexName";

	/**
	 * 随机记录数量参数名称
	 */
	public final static String RANDOM_NAMED = "sagRandomSize";

	/**
	 * 分页开始记录参数Named
	 */
	public final static String PAGE_FIRST_PARAM_NAME = "pageFirstParamName";

	/**
	 * 分页截止记录参数Named
	 */
	public final static String PAGE_LAST_PARAM_NAME = "pageLastParamName";

	/**
	 * 临时表占位符号
	 */
	public final static String TEMPLATE_TABLE_HOLDER = "@templateTable";

	/**
	 * 存放sqltoy的系统参数
	 */
	private static Map<String, String> sqlToyProps = new HashMap<String, String>();

	/**
	 * sqltoy 默认的配置文件
	 */
	private final static String DEFAULT_CONFIG = "org/sagacity/sqltoy/sqltoy-default.properties";

	public final static String XML_FETURE = "http://apache.org/xml/features/nonvalidating/load-external-dtd";

	/**
	 * 服务器节点ID
	 */
	public static int WORKER_ID = 0;

	/**
	 * 数据中心ID
	 */
	public static int DATA_CENTER_ID = 0;

	/**
	 * 为22位或26位主键提供的主机Id
	 */
	public static String SERVER_ID;

	public static String keywordSign = "'";

	/**
	 * sql in 里面参数最大值
	 */
	public static int SQL_IN_MAX = 999;

	/**
	 * 并行默认最大等待时长
	 */
	public static int PARALLEL_MAXWAIT_SECONDS = 1800;

	public static int FETCH_SIZE = -1;

	/**
	 * 默认一页数据条数
	 */
	public static int DEFAULT_PAGE_SIZE = 10;

	/**
	 * 变更操作型sql空白默认转为null
	 */
	public static boolean executeSqlBlankToNull = true;

	/**
	 * 分页中间表名称
	 */
	public static String INTERMEDIATE_TABLE = "SAG_INTERMEDIATE_TABLE";
	public static String INTERMEDIATE_TABLE1 = "SAG_INTERMEDIATE_TABLE1";

	/**
	 * 字符串中内嵌参数的匹配模式
	 */
	public final static Pattern paramPattern = Pattern.compile(
			"\\$\\{\\s*[0-9a-zA-Z\u4e00-\u9fa5]+((\\.|\\_)[0-9a-zA-Z\u4e00-\u9fa5]+)*(\\[\\d*(\\,)?\\d*\\])?\\s*\\}");

	// update 2020-9-16 将\\W 替换为[^A-Za-z0-9_:] 增加排除: 适应::jsonb 这种模式场景
	// update 2021-10-13 增加参数名称为中文场景(应对一些极为不规范的项目场景)
	// update 2023-8-17 增加支持:itemSet[0].paramName 模式(之前只支持:itemSet[0])
	// update 2023-12-19 替换为 [^A-Za-z0-9:],将下划线放开
	public final static Pattern SQL_NAMED_PATTERN = Pattern.compile(
			"[^A-Za-z0-9:\u4e00-\u9fa5]\\:\\s*[a-zA-Z\u4e00-\u9fa5][a-zA-Z0-9_\u4e00-\u9fa5]*(\\.[\\w\u4e00-\u9fa5]+)*(\\[\\d+\\](\\.[a-zA-Z0-9_\u4e00-\u9fa5]+)*)?\\s?");
	public final static Pattern NOSQL_NAMED_PATTERN = Pattern.compile(
			"(?i)\\@(param|blank|value)?\\(\\s*\\:\\s*[a-zA-Z\u4e00-\u9fa5][a-zA-Z0-9_\u4e00-\u9fa5]*(\\.[\\w\u4e00-\u9fa5]+)*(\\[\\d+\\](\\.[a-zA-Z0-9_\u4e00-\u9fa5]+)*)?\\s*\\)");

	// mysql8 支持 with recursive cte as
	// postgresql12 支持materialized 物化
	// with aliasTable as materialized ()
	// with aliasTable as not materialized ()
	public final static Pattern withPattern = Pattern.compile(
			"(?i)\\Wwith\\s+([a-z]+\\s+)?[a-z|0-9|\\_]+\\s*(\\([a-z|0-9|\\_|\\s|\\,]+\\))?\\s+as\\s*(\\s+[a-z|\\_]+){0,2}\\s*\\(");

	// with 下面多个as
	public final static Pattern otherWithPattern = Pattern.compile(
			"(?i)\\s*\\,\\s*([a-z]+\\s+)?[a-z|0-9|\\_]+\\s*(\\([a-z|0-9|\\_|\\s|\\,]+\\))?\\s+as\\s*(\\s+[a-z|\\_]+){0,2}\\s*\\(");

	// 以空白结尾
	public final static Pattern BLANK_END = Pattern.compile("\\s$");

	// 以and 或 or结尾
	public final static Pattern AND_OR_END = Pattern.compile("(?i)\\W(and|or)\\s*$");

	/**
	 * 不输出sql的表达式
	 */
	public final static Pattern NOT_PRINT_REGEX = Pattern.compile("(?i)\\#not\\_(print|debug)\\#");
	public final static Pattern DO_PRINT_REGEX = Pattern.compile("(?i)\\#(print|debug)\\#");

	/**
	 * 忽视空记录
	 */
	public final static Pattern IGNORE_EMPTY_REGEX = Pattern.compile("(?i)\\#ignore_all_null_set\\#");

	/**
	 * 判断sql中是否存在@include(sqlId)的表达式
	 */
	public final static Pattern INCLUDE_PATTERN = Pattern.compile("(?i)\\@include\\([\\w\\W]*\\)");
	// @include(:sqlScriptParamName) 模式(2023-08-19)
	public final static Pattern INCLUDE_PARAM_PATTERN = Pattern
			.compile("(?i)\\@include\\(\\s*\\:\\s*[a-zA-Z\u4e00-\u9fa5][a-zA-Z0-9_\u4e00-\u9fa5]*[\\w\\W]*\\)");
	// 标记分页或取随机记录原始sql的标记，便于sql interceptor加工处理快速定位
	public final static String MARK_ORIGINAL_START = " /*-- sqltoy_original_mark_start --*/ ";
	public final static String MARK_ORIGINAL_END = " /*-- sqltoy_original_mark_end --*/ ";

	public final static String MERGE_ALIAS_ON = ") tv on (";
	public final static String MERGE_ALIAS_ON_REGEX = "\\)\\s+tv\\s+on\\s+\\(";
	public final static String MERGE_UPDATE = " when matched then update set ";
	public final static String MERGE_INSERT = " when not matched then insert ";

	/**
	 * sqltoy的框架包路径
	 */
	public final static String SQLTOY_PACKAGE = "org.sagacity.sqltoy";

	public static String localDateTimeFormat;

	public static String localTimeFormat;

	// 单记录保存采用identity、sequence主键策略，并返回主键值时，字段名称大小写处理(lower/upper)
	public static IgnoreKeyCaseMap<String, String> dialectReturnPrimaryColumnCase = new IgnoreKeyCaseMap<String, String>();

	/**
	 * @todo 解析模板中的参数
	 * @param template
	 * @return
	 */
	private static LinkedHashMap<String, String> parseParams(String template) {
		LinkedHashMap<String, String> paramsMap = new LinkedHashMap<String, String>();
		Matcher m = paramPattern.matcher(template);
		String group;
		while (m.find()) {
			group = m.group();
			// key as ${name} value:name
			paramsMap.put(group, group.substring(2, group.length() - 1).trim());
		}
		return paramsMap;
	}

	/**
	 * @todo 获取常量值
	 * @param key
	 * @return
	 */
	public static String getKeyValue(String key) {
		String result = sqlToyProps.get(key);
		if (result == null) {
			result = System.getProperty(key);
		}
		return result;
	}

	/**
	 * 关闭数据库多字段in支持
	 * 
	 * @return
	 */
	public static boolean closeMultiFieldIn() {
		String result = getKeyValue("sqltoy.close.multiFieldIn");
		if (result != null && result.equalsIgnoreCase("true")) {
			return true;
		}
		return false;
	}

	/**
	 * 获得默认时区
	 * @return
	 */
	public static ZoneId getZoneId() {
		String zoneStr = getKeyValue("sqltoy.default_zone");
		if (zoneStr == null) {
			return ZoneId.systemDefault();
		} else {
			return ZoneId.of(zoneStr);
		}
	}

	/**
	 * @todo 获取常量值
	 * @param key
	 * @param defaultValue
	 * @return
	 */
	public static String getKeyValue(String key, String defaultValue) {
		String result = sqlToyProps.get(key);
		if (result == null) {
			result = System.getProperty(key);
		}
		if (StringUtil.isNotBlank(result)) {
			return result;
		}
		return defaultValue;
	}

	/**
	 * @todo db2 是否为查询语句自动补充with ur进行脏读
	 * @return
	 */
	public static boolean db2WithUR() {
		return Boolean.parseBoolean(getKeyValue("sqltoy.db2.search.with.ur", "true"));
	}

	/**
	 * @todo 获取记录提取的警告阀值
	 * @return
	 */
	public static int getWarnThresholds() {
		// 默认值为25000
		return Integer.parseInt(getKeyValue("sqltoy.fetch.result.warn.thresholds", "25000"));
	}

	/**
	 * oracle.sql.TIMESTAMP是否默认要转为java.sql.Timestamp类型
	 * 
	 * @return
	 */
	public static boolean convertOracleTimestamp() {
		return Boolean.parseBoolean(getKeyValue("sqltoy.convert.oracle.timestamp", "true"));
	}

	/**
	 * @todo 获取项目中在代码中编写的sql数量，超过此阈值不纳入缓存
	 * @return
	 */
	public static int getMaxCodeSqlCount() {
		// 默认值为2500
		return Integer.parseInt(getKeyValue("sqltoy.max.code.sql.count", "2500"));
	}

	/**
	 * @todo 获取记录提取的最大阀值
	 * @return
	 */
	public static Long getMaxThresholds() {
		// 无限大
		return Long.parseLong(getKeyValue("sqltoy.fetch.result.max.thresholds", "999999999999"));
	}

	/**
	 * @todo oracle分页是否忽视排序导致错乱的问题
	 * @return
	 */
	public static boolean oraclePageIgnoreOrder() {
		return Boolean.parseBoolean(getKeyValue("sqltoy.oracle.page.ignore.order", "false"));
	}

	/**
	 * @todo 是否显示数据库信息
	 * @return
	 */
	public static boolean showDatasourceInfo() {
		return Boolean.parseBoolean(getKeyValue("sqltoy.show.datasource.info", "false"));
	}

	/**
	 * @todo 获取文件中的常量元素
	 * @param propertiesFile
	 */
	private static void loadPropertyFile(String propertiesFile) {
		// 加载指定的额外参数，或提供开发者修改默认参数
		if (StringUtil.isBlank(propertiesFile)) {
			return;
		}
		InputStream fis = null;
		try {
			Properties props = new Properties();
			fis = FileUtil.getFileInputStream(propertiesFile);
			if (fis != null) {
				props.load(fis);
				sqlToyProps.putAll((Map) props);
				fis.close();
			}
		} catch (IOException e) {
			e.printStackTrace();
		} finally {
			try {
				if (fis != null) {
					fis.close();
				}
			} catch (Exception e) {
				e.printStackTrace();
			}
		}
	}

	/**
	 * @todo 加载数据库方言的参数
	 * @param keyValues
	 */
	public static void loadProperties(Map<String, String> keyValues) {
		// 加载默认参数
		loadPropertyFile(DEFAULT_CONFIG);
		if (keyValues != null && !keyValues.isEmpty()) {
			sqlToyProps.putAll(keyValues);
		}
	}

	/**
	 * @todo 用户可以根据实际数据库方言，通过常量参数转换默认值(如db2 当前时间戳，可以是current
	 *       timestamp,而在oracle中必须是current_timestamp)
	 * @param dbType
	 * @param defaultValue
	 * @return
	 */
	public static String getDefaultValue(Integer dbType, String defaultValue) {
		String realDefault = getKeyValue(defaultValue);
		if (realDefault == null) {
			if ("CURRENT TIMESTAMP".equals(defaultValue.toUpperCase())) {
				return "CURRENT_TIMESTAMP";
			}
			return defaultValue;
		}
		return realDefault;
	}

	/**
	 * @todo 替换模板中${paramName}变量参数,目前仅用于nosql部分的解析
	 * @param template
	 * @return
	 */
	public static String replaceParams(String template) {
		if (StringUtil.isBlank(template)) {
			return template;
		}
		LinkedHashMap<String, String> paramsMap = parseParams(template);
		String result = template;
		if (paramsMap.size() > 0) {
			Map.Entry<String, String> entry;
			String value;
			for (Iterator<Map.Entry<String, String>> iter = paramsMap.entrySet().iterator(); iter.hasNext();) {
				entry = iter.next();
				value = getKeyValue(entry.getValue());
				if (value != null) {
					result = result.replace(entry.getKey(), value);
				}
			}
		}
		return result;
	}

	/**
	 * @todo 获取loadAll单个批次最大的记录数量,主要是防止sql in ()参数超过1000导致错误
	 * @return
	 */
	public static int getLoadAllBatchSize() {
		// 默认值为1000
		return Integer.parseInt(getKeyValue("sqltoy.loadAll.batchsize", "1000"));
	}

	/**
	 * @TODO 是否打开sql签名
	 * @return
	 */
	public static boolean openSqlSign() {
		return Boolean.parseBoolean(getKeyValue("sqltoy.open.sqlsign", "true"));
	}

	/**
	 * @TODO 针对主键策略提前设置或计算雪花算法的worker_id,dataCenterId以及22位和26位主键对应的应用id
	 * @param workerId
	 * @param dataCenterId
	 * @param serverId
	 */
	public static void setWorkerAndDataCenterId(Integer workerId, Integer dataCenterId, Integer serverId) {
		try {
			String serverIdentity = IdUtil.getLastIp(2);
			int id = Integer.parseInt(serverIdentity == null ? "0" : serverIdentity);
			String keyValue;
			if (workerId == null) {
				keyValue = getKeyValue("sqltoy.snowflake.workerId");
				if (keyValue != null) {
					workerId = Integer.parseInt(keyValue);
				}
			}
			if (dataCenterId == null) {
				keyValue = getKeyValue("sqltoy.snowflake.dataCenterId");
				if (keyValue != null) {
					dataCenterId = Integer.parseInt(keyValue);
				}
			}
			if (workerId != null && (workerId.intValue() > 0 && workerId.intValue() < 32)) {
				WORKER_ID = workerId.intValue();
			} else {
				if (id > 31) {
					// 个位作为workerId
					WORKER_ID = id % 10;
				} else {
					WORKER_ID = id;
				}
			}
			if (dataCenterId != null && dataCenterId.intValue() > 0 && dataCenterId.intValue() < 32) {
				DATA_CENTER_ID = dataCenterId.intValue();
			} else {
				if (id > 31) {
					// 十位数作为dataCenterId
					DATA_CENTER_ID = id / 10;
				} else {
					DATA_CENTER_ID = id;
				}
			}
			// 22位或26位主键对应的serverId
			String serverNode = (serverId == null) ? getKeyValue("sqltoy.server.id") : ("" + serverId);
			if (serverNode != null) {
				serverNode = StringUtil.addLeftZero2Len(serverNode, 3);
				if (serverNode.length() > 3) {
					serverNode = serverNode.substring(serverNode.length() - 3);
				}
				SERVER_ID = serverNode;
			}
		} catch (Exception e) {
			e.printStackTrace();
			logger.error("设置workerId和dataCenterId发生错误:{}", e.getMessage());
		}
	}

	/**
	 * 部分数据库表名和字段名取小写或大写，有些数据库大小写不敏感
	 * 
	 * @param tableOrColumnName
	 * @param dialect
	 * @return
	 */
	public static String getDialectLowcaseStrategyName(String tableOrColumnName, String dialect) {
		if (tableOrColumnName == null) {
			return null;
		}
		// 规避版本影响
		String realDialect = dialect.toLowerCase();
		if (realDialect.startsWith("postgresql")) {
			realDialect = "postgresql";
		} else if (realDialect.startsWith("oracle")) {
			realDialect = "oracle";
		} else if (realDialect.startsWith("mysql")) {
			realDialect = "mysql";
		}
		String result = getKeyValue("sqltoy.table_names.strategy.".concat(realDialect));
		if (result == null) {
			return tableOrColumnName;
		}
		String lowResult = result.toLowerCase();
		if (lowResult.startsWith("lower")) {
			return tableOrColumnName.toLowerCase();
		} else if (lowResult.startsWith("upper")) {
			return tableOrColumnName.toUpperCase();
		}
		return tableOrColumnName;
	}
}
