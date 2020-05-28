/**
 * @Copyright 2009 版权归陈仁飞，不要肆意侵权抄袭，如引用请注明出处保留作者信息。
 */
package org.sagacity.sqltoy.config;

import static java.lang.System.out;

import java.io.File;
import java.util.List;
import java.util.concurrent.ConcurrentHashMap;

import org.sagacity.sqltoy.SqlToyConstants;
import org.sagacity.sqltoy.config.model.ParamFilterModel;
import org.sagacity.sqltoy.config.model.SqlToyConfig;
import org.sagacity.sqltoy.config.model.SqlType;
import org.sagacity.sqltoy.dialect.utils.PageOptimizeUtils;
import org.sagacity.sqltoy.utils.StringUtil;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * @project sagacity-sqltoy
 * @description 解析sql配置文件，并放入缓存
 * @author chenrenfei <a href="mailto:zhongxuchen@hotmail.com">联系作者</a>
 * @version id:SqlScriptLoader.java,Revision:v1.0,Date:2009-12-13 下午03:27:53
 * @modify Date:2013-6-14 {修改了sql文件搜寻机制，兼容jar目录下面的查询}
 * @modify Date:2019-08-25 增加独立的文件变更检测程序用于重新加载sql
 * @modify Date:2019-09-15 增加代码中编写的sql缓存机制,避免每次动态解析从而提升性能
 * @modify Date:2020-04-22 增加System.out
 *         对sql文件加载的打印输出,避免有些开发在开发阶段不知道设置日志级别为debug从而看不到输出
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
	 * 文件最后修改时间
	 */
	private ConcurrentHashMap<String, Long> filesLastModifyMap = new ConcurrentHashMap<String, Long>();

	/**
	 * @TODO 初始化加载sql文件
	 * @param debug
	 * @param delayCheckSeconds
	 * @param scriptCheckIntervalSeconds
	 */
	public void initialize(boolean debug, int delayCheckSeconds, Integer scriptCheckIntervalSeconds) {
		if (initialized)
			return;
		initialized = true;
		boolean enabledDebug = logger.isDebugEnabled();
		if (enabledDebug) {
			logger.debug("开始加载sql配置文件..........................");
		} else {
			out.println("开始加载sql配置文件..........................");
		}
		try {
			// 检索所有匹配的sql.xml文件
			realSqlList = ScanEntityAndSqlResource.getSqlResources(sqlResourcesDir, sqlResources, dialect);
			if (realSqlList != null && !realSqlList.isEmpty()) {
				// 此处提供大量提示信息,避免开发者配置错误或未成功将资源文件编译到bin或classes下
				if (enabledDebug) {
					logger.debug("总计加载.sql.xml文件数量为:{}", realSqlList.size());
					logger.debug("如果.sql.xml文件不在下列清单中,很可能是文件没有在编译路径下(bin、classes等),请仔细检查!");
				} else {
					out.println("总计加载.sql.xml文件数量为:" + realSqlList.size());
					out.println("如果.sql.xml文件不在下列清单中,很可能是文件没有在编译路径下(bin、classes等),请仔细检查!");
				}
				Object sqlFile;
				for (int i = 0; i < realSqlList.size(); i++) {
					sqlFile = realSqlList.get(i);
					if (sqlFile instanceof File) {
						if (enabledDebug) {
							logger.debug("第:[" + i + "]个文件:" + ((File) sqlFile).getName());
						} else {
							out.println("第:[" + i + "]个文件:" + ((File) sqlFile).getName());
						}
					} else {
						if (enabledDebug) {
							logger.debug("第:[" + i + "]个文件:" + sqlFile.toString());
						} else {
							out.println("第:[" + i + "]个文件:" + sqlFile.toString());
						}
					}
				}
				for (int i = 0; i < realSqlList.size(); i++) {
					SqlXMLConfigParse.parseSingleFile(realSqlList.get(i), filesLastModifyMap, sqlCache, encoding,
							dialect, false);
				}
			} else {
				// 部分开发者经常会因为环境问题,未能将.sql.xml 文件编译到classes路径下，导致无法使用
				if (logger.isWarnEnabled()) {
					logger.warn(
							"没有检查到相应的.sql.xml文件,请检查sqltoyContext配置项sqlResourcesDir={}是否正确,或文件没有在编译路径下(bin、classes等)!",
							sqlResourcesDir);
				} else {
					out.println("未检测到以.sql.xml结尾的文件,请检查sqltoyContext配置项sqlResourcesDir=" + sqlResourcesDir
							+ "配置是否正确,或文件没有在编译路径下(bin、classes等)!");
				}
			}
		} catch (Exception e) {
			e.printStackTrace();
			logger.error("加载和解析xml过程发生异常!" + e.getMessage(), e);
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
				logger.warn("sleepSeconds={} 小于1秒或大于24小时，表示关闭sql文件变更检测!", sleepSeconds);
			}
		}
	}

	/**
	 * @todo 提供根据sql或sqlId获取sql配置模型
	 * @param sqlKey
	 * @param type
	 * @return
	 */
	public SqlToyConfig getSqlConfig(String sqlKey, SqlType type) {
		SqlToyConfig result = sqlCache.get(sqlKey);
		if (null == result) {
			result = codeSqlCache.get(sqlKey);
		}
		if (null != result) {
			return result;
		}
		// 判断是否是sqlId,非在xml中定义id的sql
		if (!SqlConfigParseUtils.isNamedQuery(sqlKey)) {
			result = SqlConfigParseUtils.parseSqlToyConfig(sqlKey, getDialect(), type);
			// 设置默认空白查询条件过滤filter,便于直接传递sql语句情况下查询条件的处理
			ParamFilterModel[] filters = new ParamFilterModel[1];
			filters[0] = new ParamFilterModel("blank", new String[] { "*" });
			result.setFilters(filters);
			// 限制数量的原因是存在部分代码中的sql会拼接条件参数值，导致不同的sql无限增加
			if (codeSqlCache.size() < SqlToyConstants.getMaxCodeSqlCount()) {
				codeSqlCache.put(sqlKey, result);
			}
		} else {
			// 这一步理论上不应该执行
			result = new SqlToyConfig(getDialect());
			result.setSql(sqlKey);
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
	 * @todo 直接构造SqlToyConfig 放入sqltoy 缓存
	 * @param sqlToyConfig
	 * @throws Exception
	 */
	public void putSqlToyConfig(SqlToyConfig sqlToyConfig) throws Exception {
		if (sqlToyConfig != null && StringUtil.isNotBlank(sqlToyConfig.getId())) {
			if (sqlCache.containsKey(sqlToyConfig.getId())) {
				logger.warn("发现重复的SQL语句:id={} 将被覆盖!", sqlToyConfig.getId());
				// 移除分页优化缓存
				PageOptimizeUtils.remove(sqlToyConfig.getId());
			}
			sqlCache.put(sqlToyConfig.getId(), sqlToyConfig);
		}
	}

	/**
	 * @param resourcesDir the resourcesDir to set
	 */
	public void setSqlResourcesDir(String sqlResourcesDir) {
		this.sqlResourcesDir = sqlResourcesDir;
	}

	/**
	 * @param mappingResources the mappingResources to set
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
