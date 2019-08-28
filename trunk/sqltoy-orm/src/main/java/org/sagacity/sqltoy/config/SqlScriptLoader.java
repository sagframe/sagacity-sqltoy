/**
 * @Copyright 2009 版权归陈仁飞，不要肆意侵权抄袭，如引用请注明出处保留作者信息。
 */
package org.sagacity.sqltoy.config;

import java.io.File;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.ConcurrentHashMap;

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.sagacity.sqltoy.config.model.SqlToyConfig;
import org.sagacity.sqltoy.plugin.IFunction;
import org.sagacity.sqltoy.utils.StringUtil;

/**
 * @project sagacity-sqltoy
 * @description 解析sql配置文件，并放入缓存
 * @author chenrenfei <a href="mailto:zhongxuchen@hotmail.com">联系作者</a>
 * @version id:SqlScriptLoader.java,Revision:v1.0,Date:2009-12-13 下午03:27:53
 * @Modification Date:2013-6-14 {修改了sql文件搜寻机制，兼容jar目录下面的查询}
 * @Modification Date:2019-08-25 增加独立的文件变更检测程序用于重新加载sql
 */
@SuppressWarnings({ "rawtypes", "unchecked" })
public class SqlScriptLoader {
	/**
	 * 定义全局日志
	 */
	private final static Logger logger = LogManager.getLogger(SqlScriptLoader.class);

	// 设置默认的缓存
	private ConcurrentHashMap<String, SqlToyConfig> sqlCache = new ConcurrentHashMap<String, SqlToyConfig>(256);

	// 提供默认函数配置
	private final static String[] functions = { "org.sagacity.sqltoy.plugin.function.SubStr",
			"org.sagacity.sqltoy.plugin.function.Trim", "org.sagacity.sqltoy.plugin.function.Instr",
			"org.sagacity.sqltoy.plugin.function.Concat", "org.sagacity.sqltoy.plugin.function.Nvl" };

	/**
	 * sql资源配置路径
	 */
	private String sqlResourcesDir = "classpath:/sqlResources/";

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
	 * sql中的函数转换器
	 */
	private List<IFunction> functionConverts;

	/**
	 * 实际sql配置文件集合
	 */
	private List realSqlList;

	/**
	 * 是否初始化过
	 */
	private boolean initialized = false;

	private SqlFileModifyWatcher watcher;

	/**
	 * 最大检测间隔时长(秒)
	 */
	private int maxWait = 3600 * 24;

	/**
	 * 初始化加载sql文件
	 * 
	 * @param debug
	 */
	public void initialize(boolean debug, int sleepSeconds) {
		if (initialized)
			return;
		initialized = true;
		logger.debug("开始加载sql配置文件..........................");
		try {
			// 设置sql函数转换器
			SqlXMLConfigParse.setFunctionConverts(functionConverts);
			// 检索所有匹配的sql.xml文件
			realSqlList = ScanEntityAndSqlResource.getSqlResources(sqlResourcesDir, sqlResources, dialect);
			if (realSqlList != null && !realSqlList.isEmpty()) {
				// 此处提供大量提升信息,避免开发者配置错误或未成功将资源文件编译到bin或classes下
				logger.debug("总计加载.sql.xml文件数量为:" + realSqlList.size());
				logger.debug("如果.sql.xml文件不在下列清单中,很可能是文件没有在编译路径下(bin、classes等),请仔细检查!");
				Object sqlFile;
				for (int i = 0; i < realSqlList.size(); i++) {
					sqlFile = realSqlList.get(i);
					if (sqlFile instanceof File)
						logger.debug("第:[" + i + "]个文件:" + ((File) sqlFile).getName());
					else
						logger.debug("第:[" + i + "]个文件:" + sqlFile.toString());
				}
				for (int i = 0; i < realSqlList.size(); i++) {
					SqlXMLConfigParse.parseSingleFile(realSqlList.get(i), sqlCache, encoding, dialect, false);
				}
			} else {
				logger.warn("没有检查到相应的.sql.xml文件,请检查sqltoyContext配置项sqlResourcesDir={}是否正确,或文件没有在编译路径下(bin、classes等)!",
						sqlResourcesDir);
			}
		} catch (Exception e) {
			e.printStackTrace();
			logger.error("加载和解析xml过程发生异常!" + e.getMessage(), e);
		}

		// update 2019-08-25 增加独立的文件变更检测程序用于重新加载sql
		if (sleepSeconds > 0 && sleepSeconds < maxWait) {
			watcher = new SqlFileModifyWatcher(sqlCache, realSqlList, dialect, encoding, sleepSeconds, debug);
			watcher.start();
		}
	}

	/*
	 * (non-Javadoc)
	 * 
	 * @see org.sagacity.sqltoy.plugin.SqlConfigPlugin#getSql(java.lang.String)
	 */
	public SqlToyConfig getSqlConfig(String sqlKey) {
		return (SqlToyConfig) sqlCache.get(sqlKey);
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
	 * @todo 直接构造SqlToyModel 放入sqltoy 缓存
	 * @param sqlToyConfig
	 * @throws Exception
	 */
	public void putSqlToyConfig(SqlToyConfig sqlToyConfig) throws Exception {
		if (sqlToyConfig != null && StringUtil.isNotBlank(sqlToyConfig.getId())) {
			if (sqlCache.get(sqlToyConfig.getId()) != null)
				logger.warn("发现重复的SQL语句:id={} 将被覆盖!", sqlToyConfig.getId());
			sqlCache.put(sqlToyConfig.getId(), sqlToyConfig);
		}
	}

	/**
	 * @param resourcesDir
	 *            the resourcesDir to set
	 */
	public void setSqlResourcesDir(String sqlResourcesDir) {
		this.sqlResourcesDir = sqlResourcesDir;
	}

	/**
	 * @param mappingResources
	 *            the mappingResources to set
	 */
	public void setSqlResources(List sqlResources) {
		this.sqlResources = sqlResources;
	}

	/**
	 * @param encoding
	 *            the encoding to set
	 */
	public void setEncoding(String encoding) {
		this.encoding = encoding;
	}

	/**
	 * @param functionConverts
	 *            the functionConverts to set
	 */
	public void setFunctionConverts(List functionConverts) {
		List<IFunction> converts = new ArrayList<IFunction>();
		try {
			if (functionConverts != null && !functionConverts.isEmpty()) {
				String functionName = null;
				for (int i = 0; i < functionConverts.size(); i++) {
					functionName = functionConverts.get(i).toString().trim();
					// sql函数包名变更,修正调整后的包路径,保持兼容
					functionName = functionName.replace("org.sagacity.sqltoy.config.function.impl",
							"org.sagacity.sqltoy.plugin.function");
					// 只是不包含包名的类名称
					if (functionName.indexOf(".") == -1) {
						converts.add((IFunction) (Class
								.forName("org.sagacity.sqltoy.config.function.impl.".concat(functionName))
								.getDeclaredConstructor().newInstance()));
					} else {
						converts.add((IFunction) (Class.forName(functionName).getDeclaredConstructor().newInstance()));
					}
				}
			} // 为null时启用默认配置
			else {
				for (String convert : functions) {
					converts.add((IFunction) (Class.forName(convert).getDeclaredConstructor().newInstance()));
				}
			}
		} catch (Exception e) {
			e.printStackTrace();
		}
		this.functionConverts = converts;
	}

	public List<IFunction> getFunctionConverts() {
		return this.functionConverts;
	}

	/**
	 * @param dialect
	 *            the dialect to set
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
