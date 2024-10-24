package org.sagacity.sqltoy.config;

import static java.lang.System.out;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

import org.sagacity.sqltoy.SqlToyConstants;
import org.sagacity.sqltoy.config.model.ParamFilterModel;
import org.sagacity.sqltoy.config.model.SqlToyConfig;
import org.sagacity.sqltoy.config.model.SqlType;
import org.sagacity.sqltoy.dialect.utils.PageOptimizeUtils;
import org.sagacity.sqltoy.exception.DataAccessException;
import org.sagacity.sqltoy.plugins.function.FunctionUtils;
import org.sagacity.sqltoy.plugins.id.macro.AbstractMacro;
import org.sagacity.sqltoy.plugins.id.macro.MacroUtils;
import org.sagacity.sqltoy.plugins.id.macro.impl.Include;
import org.sagacity.sqltoy.utils.DataSourceUtils;
import org.sagacity.sqltoy.utils.DataSourceUtils.Dialect;
import org.sagacity.sqltoy.utils.ReservedWordsUtil;
import org.sagacity.sqltoy.utils.StringUtil;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * @project sagacity-sqltoy
 * @description 解析sql配置文件，并放入缓存
 * @author zhongxuchen
 * @version v1.0,Date:2009-12-13
 * @modify Date:2013-6-14 {修改了sql文件搜寻机制，兼容jar目录下面的查询}
 * @modify Date:2019-08-25 增加独立的文件变更检测程序用于重新加载sql
 * @modify Date:2019-09-15 增加代码中编写的sql缓存机制,避免每次动态解析从而提升性能
 * @modify Date:2020-04-22 增加System.out
 *         对sql文件加载的打印输出,避免有些开发在开发阶段不知道设置日志级别为debug从而看不到输出
 * @modify Date:2023-8-19 增加了@include(:scriptName) 模式
 * @modify Date:2024-5-13 @include(id="sqlId")
 *         增强兼容sqlId根据dialect方言找sqlId_dialect模式
 * @modify Date:2024-09-19 增加@fast(@include("sqlId")) 场景
 */
@SuppressWarnings({ "rawtypes", "unchecked" })
public class SqlScriptLoader {
	/**
	 * 定义全局日志
	 */
	private final static Logger logger = LoggerFactory.getLogger(SqlScriptLoader.class);

	// 设置默认的缓存
	private ConcurrentHashMap<String, SqlToyConfig> sqlCache = new ConcurrentHashMap<String, SqlToyConfig>(256);

	// 代码中编写的sql语句缓存
	private ConcurrentHashMap<String, SqlToyConfig> codeSqlCache = new ConcurrentHashMap<String, SqlToyConfig>(128);

	/**
	 * sql资源配置路径
	 */
	private String sqlResourcesDir;

	/**
	 * sql资源文件明细
	 */
	private List sqlResources;

	/**
	 * 数据库类型
	 */
	private String dialect;

	/**
	 * xml解析格式
	 */
	private String encoding = "UTF-8";

	/**
	 * 实际sql配置文件集合
	 */
	private List realSqlList;

	/**
	 * 是否初始化过
	 */
	private boolean initialized = false;

	/**
	 * sql文件变更监测器
	 */
	private SqlFileModifyWatcher watcher;

	/**
	 * 最大检测间隔时长(秒)
	 */
	private int maxWait = 3600 * 24;

	/**
	 * 是否将sqlResourcesDir下的sql文件解析成具体的文件resourceList
	 */
	private static boolean SQLRESOURCESDIRTOLIST = false;

	/**
	 * 文件最后修改时间
	 */
	private ConcurrentHashMap<String, Long> filesLastModifyMap = new ConcurrentHashMap<String, Long>();

	private static Map<String, AbstractMacro> macros = new HashMap<String, AbstractMacro>();

	static {
		macros.put("@include", new Include());
	}

	/**
	 * 增加路径验证提示,最易配错导致无法加载sql文件
	 * 
	 * @param sqlResourcesDir
	 */
	public static void checkSqlResourcesDir(String sqlResourcesDir) {
		if (SQLRESOURCESDIRTOLIST) {
			return;
		}
		if (StringUtil.isNotBlank(sqlResourcesDir)
				&& (sqlResourcesDir.toLowerCase().contains(".sql.xml") || sqlResourcesDir.contains("*"))) {
			throw new IllegalArgumentException("\n您的配置:spring.sqltoy.sqlResourcesDir=" + sqlResourcesDir + " 不正确!\n"
					+ "/*----正确格式只接受单个或逗号分隔的多个路径模式且不能有*和**以及*.sql.xml等配符(会自动递归往下钻取!)----*/\n"
					+ "/*- 1、单路径模式:spring.sqltoy.sqlResourcesDir=classpath:com/sagacity/crm\n"
					+ "/*- 2、多路径模式:spring.sqltoy.sqlResourcesDir=classpath:com/sagacity/crm,classpath:com/sagacity/hr\n"
					+ "/*- 3、绝对路径模式:spring.sqltoy.sqlResourcesDir=/home/web/project/sql\n"
					+ "/*-----------错误范例(请看仔细:不能有*、**和*.sql.xml)----------------------*/\n"
					+ "/*-1、classpath:*/com/yourproject/yourpackage/**/*.sql.xml\n"
					+ "/*-2、classpath*:/com/yourproject/yourpackage/**/**.sql.xml\n"
					+ "/*-----------------------------------------------------------------------*/");
		}
	}

	public static void setResourcesDirToList(boolean sqlResourcesDirToList) {
		SQLRESOURCESDIRTOLIST = sqlResourcesDirToList;
	}

	/**
	 * @TODO 初始化加载sql文件
	 * @param debug
	 * @param delayCheckSeconds
	 * @param scriptCheckIntervalSeconds 属性:spring.sqltoy.scriptCheckIntervalSeconds
	 *                                   sql文件更新检测时间间隔
	 * @param breakWhenSqlRepeat
	 * @throws Exception
	 */
	public void initialize(boolean debug, int delayCheckSeconds, Integer scriptCheckIntervalSeconds,
			boolean breakWhenSqlRepeat) throws Exception {
		// 增加路径验证提示,最易配错导致无法加载sql文件
		checkSqlResourcesDir(sqlResourcesDir);
		if (initialized) {
			return;
		}
		initialized = true;
		boolean enabledDebug = logger.isDebugEnabled();
		try {
			// 检索所有匹配的sql.xml文件
			realSqlList = ScanEntityAndSqlResource.getSqlResources(SQLRESOURCESDIRTOLIST ? null : sqlResourcesDir,
					sqlResources);
			if (realSqlList != null && !realSqlList.isEmpty()) {
				// 此处提供大量提示信息,避免开发者配置错误或未将资源文件编译到bin或classes下
				if (enabledDebug) {
					logger.debug("总计将加载.sql.xml文件数量为:{}", realSqlList.size());
					logger.debug("如果.sql.xml文件不在下列清单中,很可能是文件没有在编译路径下(bin、classes等),请仔细检查!");
				} else {
					out.println("总计将加载.sql.xml文件数量为:" + realSqlList.size());
					out.println("如果.sql.xml文件不在下列清单中,很可能是文件没有在编译路径下(bin、classes等),请仔细检查!");
				}
				List<String> repeatSql = new ArrayList<String>();
				for (int i = 0; i < realSqlList.size(); i++) {
					repeatSql.addAll(SqlXMLConfigParse.parseSingleFile(realSqlList.get(i), filesLastModifyMap, sqlCache,
							encoding, dialect, false, i));
				}
				int repeatSqlSize = repeatSql.size();
				if (repeatSqlSize > 0) {
					StringBuilder repeatSqlIds = new StringBuilder();
					repeatSqlIds.append("\n/*----------- 总计发现:" + repeatSqlSize + " 个重复的sqlId,请检查处理---------------\n");
					if (breakWhenSqlRepeat) {
						repeatSqlIds.append("/*--提示:设置 spring.sqltoy.breakWhenSqlRepeat=false 可允许sqlId重复并覆盖!-------\n");
					}
					for (String repeat : repeatSql) {
						repeatSqlIds.append("/*--").append(repeat).append("\n");
					}
					if (breakWhenSqlRepeat) {
						logger.error(repeatSqlIds.toString());
					} else {
						logger.warn(repeatSqlIds.toString());
					}
					if (breakWhenSqlRepeat) {
						throw new Exception(repeatSqlIds.toString());
					}
				}
			} else {
				// 部分开发者经常会因为环境问题,未能将.sql.xml 文件编译到classes路径下，导致无法使用
				if (enabledDebug) {
					logger.debug("总计加载*.sql.xml文件数量为:0 !");
					logger.debug("请检查配置项sqlResourcesDir={}是否正确(如:字母拼写),或文件没有在编译路径下(bin、classes等)!", sqlResourcesDir);
				} else {
					out.println("总计加载*.sql.xml文件数量为:0 !");
					out.println(
							"请检查配置项sqlResourcesDir=[" + sqlResourcesDir + "]是否正确(如:字母拼写),或文件没有在编译路径下(bin、classes等)!");
				}
			}
		} catch (Exception e) {
			logger.error("加载和解析以sql.xml结尾的文件过程发生异常!" + e.getMessage(), e);
			throw e;
		}
		// 存在sql文件，启动文件变更检测便于重新加载sql
		if (realSqlList != null && !realSqlList.isEmpty()) {
			int sleepSeconds = 0;
			if (scriptCheckIntervalSeconds == null) {
				// debug模式下,sql文件每隔2秒检测
				if (debug) {
					sleepSeconds = 2;
				} else {
					sleepSeconds = 15;
				}
			} else {
				sleepSeconds = scriptCheckIntervalSeconds.intValue();
			}
			// update 2019-08-25 增加独立的文件变更检测程序用于重新加载sql
			if (sleepSeconds > 0 && sleepSeconds <= maxWait) {
				if (enabledDebug) {
					logger.debug("已经开启sql文件变更检测，会自动间隔:{}秒检测一次,发生变更会自动重新载入!", sleepSeconds);
				} else {
					out.println("已经开启sql文件变更检测，会自动间隔:" + sleepSeconds + "秒检测一次,发生变更会自动重新载入!");
				}
				watcher = new SqlFileModifyWatcher(sqlCache, filesLastModifyMap, realSqlList, dialect, encoding,
						delayCheckSeconds, sleepSeconds);
				watcher.start();
			} else {
				logger.warn("sql文件更新检测:sleepSeconds={} 小于1秒或大于24小时，表示关闭sql文件变更检测!", sleepSeconds);
			}
		}
	}

	/**
	 * @todo 提供根据sql或sqlId获取sql配置模型
	 * @param sqlKey
	 * @param sqlType
	 * @param dialect
	 * @param paramValues
	 * @param blankToNull
	 * @return
	 */
	public SqlToyConfig getSqlConfig(String sqlKey, SqlType sqlType, String dialect, Object paramValues,
			boolean blankToNull) {
		if (StringUtil.isBlank(sqlKey)) {
			throw new IllegalArgumentException("sql or sqlId is null!");
		}

		SqlToyConfig result = null;
		String realDialect = (dialect == null) ? "" : dialect.toLowerCase();
		// sqlId形式
		if (SqlConfigParseUtils.isNamedQuery(sqlKey)) {
			if (!"".equals(realDialect)) {
				// sqlId_dialect
				result = sqlCache.get(sqlKey.concat("_").concat(realDialect));
				// dialect_sqlId
				if (result == null) {
					result = sqlCache.get(realDialect.concat("_").concat(sqlKey));
				}
				// 兼容一下sqlserver的命名
				if (result == null && realDialect.equals(Dialect.SQLSERVER)) {
					result = sqlCache.get(sqlKey.concat("_mssql"));
					if (result == null) {
						result = sqlCache.get("mssql_".concat(sqlKey));
					}
				} // 兼容一下postgres的命名
				if (result == null && realDialect.equals(Dialect.POSTGRESQL)) {
					result = sqlCache.get(sqlKey.concat("_postgres"));
					if (result == null) {
						result = sqlCache.get("postgres_".concat(sqlKey));
					}
				}
			}
			if (result == null) {
				result = sqlCache.get(sqlKey);
				if (result == null) {
					throw new DataAccessException("\n发生错误:sqlId=[" + sqlKey + "]无对应的sql配置,请检查对应的sql.xml文件是否被正确加载!\n"
							+ "/*----------------------错误可能的原因如下---------------------*/\n"
							+ "/* 1、检查: spring.sqltoy.sqlResourcesDir=[" + sqlResourcesDir
							+ "]配置(如:字母拼写),会导致sql文件没有被加载;\n"
							+ "/* 2、sql.xml文件没有被编译到classes目录下面;请检查maven的编译配置                        \n"
							+ "/* 3、sqlId对应的文件内部错误!版本合并或书写错误会导致单个文件解析错误                          \n"
							+ "/* ------------------------------------------------------------*/");
				}
			}
			// 存在@include("sqlId") 重新组织sql
			if (result != null && result.isHasIncludeSql()) {
				boolean isParamInclude = StringUtil.matches(result.getSql(), SqlToyConstants.INCLUDE_PARAM_PATTERN);
				// 复制一份，避免直接修改sql缓存中的模型
				if (isParamInclude) {
					result = result.clone();
					result.clearDialectSql();
				}
				// 替换include的实际sql
				String sql = result.getSql();
				// update 2024-09-19 增加@fast(@include("sqlId")) 场景，result.getSql()
				// 已经剔除了@fast,导致再次解析时已经无法判断是@fast语句，所以需要拼接上@fast还原原本的sql
				// SqlToyConfig.setSql(fastPreSql.concat(" (").concat(fastSql).concat(")").concat(fastTailSql)),剔除了@fast
				if (result.isHasFast()) {
					sql = result.getFastPreSql(null).concat(" @fast(").concat(result.getFastSql(null)).concat(") ")
							.concat(result.getFastTailSql(null));
				}
				sql = MacroUtils.replaceMacros(sql, (Map) sqlCache, paramValues, false, macros, dialect);
				// 重新解析sql内容
				SqlToyConfig tmpConfig = SqlConfigParseUtils.parseSqlToyConfig(sql, realDialect, sqlType);
				result.setHasUnion(tmpConfig.isHasUnion());
				result.setHasWith(tmpConfig.isHasWith());
				result.setHasFast(tmpConfig.isHasFast());
				result.setFastSql(tmpConfig.getFastSql(null));
				result.setFastPreSql(tmpConfig.getFastPreSql(null));
				result.setFastTailSql(tmpConfig.getFastTailSql(null));
				result.setFastWithSql(tmpConfig.getFastWithSql(null));
				result.setSql(tmpConfig.getSql());
				result.setParamsName(tmpConfig.getParamsName());

				String countSql = result.getCountSql(null);
				if (countSql != null && StringUtil.matches(countSql, SqlToyConstants.INCLUDE_PATTERN)) {
					countSql = MacroUtils.replaceMacros(countSql, (Map) sqlCache, paramValues, false, macros, dialect);
					countSql = FunctionUtils.getDialectSql(countSql, realDialect);
					countSql = ReservedWordsUtil.convertSql(countSql, DataSourceUtils.getDBType(realDialect));
					result.setCountSql(countSql);
				}
				// 2023-8-19 增加了@include(:scriptName) 模式，每次都是动态的
				// 完全是@include(sqlId)模式，下次无需再进行sql拼装，提升性能
				if (!isParamInclude) {
					result.setHasIncludeSql(false);
				}
			}
		} else {
			result = codeSqlCache.get(sqlKey);
			if (result == null) {
				boolean hasInclude = StringUtil.matches(sqlKey, SqlToyConstants.INCLUDE_PATTERN);
				boolean isParamInclude = false;
				// 替换include的实际sql
				if (hasInclude) {
					isParamInclude = StringUtil.matches(sqlKey, SqlToyConstants.INCLUDE_PARAM_PATTERN);
					String sql = MacroUtils.replaceMacros(sqlKey, (Map) sqlCache, paramValues, false, macros, dialect);
					result = SqlConfigParseUtils.parseSqlToyConfig(sql, realDialect, sqlType);
				} else {
					result = SqlConfigParseUtils.parseSqlToyConfig(sqlKey, realDialect, sqlType);
				}
				// 设置默认空白查询条件过滤filter,便于直接传递sql语句情况下查询条件的处理
				if (blankToNull) {
					result.addFilter(new ParamFilterModel("blank", new String[] { "*" }));
				}
				// 限制数量的原因是存在部分代码中的sql会拼接条件参数值，导致不同的sql无限增加
				// 非@include(:paramName)模式才可以缓存
				if (!isParamInclude && codeSqlCache.size() < SqlToyConstants.getMaxCodeSqlCount()) {
					codeSqlCache.put(sqlKey, result);
				}
			}
		}
		return result;
	}

	/**
	 * @todo 加入sql 片段解析产生对应的sqlToyConfig 放入缓存
	 * @param sqlSegment
	 * @return
	 * @throws Exception
	 */
	public SqlToyConfig parseSqlSagment(Object sqlSegment) throws Exception {
		return SqlXMLConfigParse.parseSagment(sqlSegment, this.encoding, this.dialect);
	}

	/**
	 * @TODO 开放sql文件由开发者放入sqltoy统一解析管理
	 * @param sqlFile
	 * @throws Exception
	 */
	public void parseSqlFile(Object sqlFile) throws Exception {
		SqlXMLConfigParse.parseSingleFile(sqlFile, filesLastModifyMap, sqlCache, encoding, dialect, true, -1);
	}

	/**
	 * @todo 直接构造SqlToyConfig 放入sqltoy 缓存
	 * @param sqlToyConfig
	 * @throws Exception
	 */
	public void putSqlToyConfig(SqlToyConfig sqlToyConfig) throws Exception {
		if (sqlToyConfig == null || StringUtil.isBlank(sqlToyConfig.getId())) {
			logger.warn("sqlToyConfig is null 或者 id 为null!");
			return;
		}
		// 判断是否已经存在，存在则清理一下分页优化的缓存
		if (sqlCache.containsKey(sqlToyConfig.getId())) {
			logger.warn("发现重复的SQL语句:id={} 将被覆盖!", sqlToyConfig.getId());
			// 移除分页优化缓存
			PageOptimizeUtils.remove(sqlToyConfig.getId());
		}
		sqlCache.put(sqlToyConfig.getId(), sqlToyConfig);
	}

	/**
	 * @param sqlResourcesDir the resourcesDir to set
	 */
	public void setSqlResourcesDir(String sqlResourcesDir) {
		this.sqlResourcesDir = sqlResourcesDir;
	}

	/**
	 * @param sqlResources the mappingResources to set
	 */
	public void setSqlResources(List sqlResources) {
		this.sqlResources = sqlResources;
	}

	/**
	 * @param encoding the encoding to set
	 */
	public void setEncoding(String encoding) {
		this.encoding = encoding;
	}

	/**
	 * @param dialect the dialect to set
	 */
	public void setDialect(String dialect) {
		this.dialect = dialect;
	}

	/**
	 * @return the dialect
	 */
	public String getDialect() {
		return dialect;
	}

	/**
	 * 进程销毁
	 */
	public void destroy() {
		try {
			if (watcher != null && !watcher.isInterrupted()) {
				watcher.interrupt();
			}
		} catch (Exception e) {

		}
	}
}
