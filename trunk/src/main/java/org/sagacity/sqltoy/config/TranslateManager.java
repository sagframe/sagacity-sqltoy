/**
 * @Copyright 2009 版权归陈仁飞，不要肆意侵权抄袭，如引用请注明出处保留作者信息。
 */
package org.sagacity.sqltoy.config;

import java.io.InputStream;
import java.io.InputStreamReader;
import java.sql.Connection;
import java.util.HashMap;
import java.util.Iterator;
import java.util.List;
import java.util.Map;

import javax.sql.DataSource;

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.dom4j.Document;
import org.dom4j.DocumentException;
import org.dom4j.Element;
import org.dom4j.io.SAXReader;
import org.sagacity.sqltoy.SqlToyConstants;
import org.sagacity.sqltoy.SqlToyContext;
import org.sagacity.sqltoy.cache.TranslateCacheManager;
import org.sagacity.sqltoy.cache.impl.TranslateEhcacheManager;
import org.sagacity.sqltoy.callback.DataSourceCallbackHandler;
import org.sagacity.sqltoy.config.model.CacheConfig;
import org.sagacity.sqltoy.config.model.SqlToyConfig;
import org.sagacity.sqltoy.config.model.SqlType;
import org.sagacity.sqltoy.config.model.TranslateCacheModel;
import org.sagacity.sqltoy.config.model.TranslateModel;
import org.sagacity.sqltoy.dialect.utils.DialectUtils;
import org.sagacity.sqltoy.executor.QueryExecutor;
import org.sagacity.sqltoy.utils.CommonUtils;
import org.sagacity.sqltoy.utils.DataSourceUtils;
import org.sagacity.sqltoy.utils.ShardingUtils;
import org.sagacity.sqltoy.utils.StringUtil;

/**
 * @project sqltoy-orm
 * @description sqltoy 缓存翻译器(通过缓存存储常用数据，如数据字典、机构、员工等，从而在数据库查询时可以避免 关联查询)
 * @author chenrenfei <a href="mailto:zhongxuchen@gmail.com">联系作者</a>
 * @version id:TranslateManager.java,Revision:v1.0,Date:2013年4月8日
 * @Modification {Date:2017-12-8,提取缓存时增加分库策略判断,如果存在分库策略dataSource则按照分库逻辑提取}
 * @Modification {Date:2018-1-5,增加redis缓存翻译的管理和支持}
 */
@SuppressWarnings({ "rawtypes", "unchecked" })
public class TranslateManager {
	/**
	 * 定义全局日志
	 */
	protected final static Logger logger = LogManager.getLogger(TranslateManager.class);

	/**
	 * 多种缓存翻译混合模式
	 */
	private HashMap<String, TranslateCacheManager> translateCacheManagers = new HashMap<String, TranslateCacheManager>();

	/**
	 * 翻译缓存管理器，默认提供基于ehcache的实现，用户可以另行定义
	 */
	private TranslateCacheManager translateCacheManager;

	/**
	 * 翻译配置解析后的模型
	 */
	private HashMap<String, TranslateCacheModel> translateMap = new HashMap<String, TranslateCacheModel>();

	/**
	 * 是否初始化过
	 */
	private boolean initialized = false;

	/**
	 * 翻译器配置文件,默认配置文件放于classpath下面，名称为sqltoy-translate.xml
	 */
	private String translateConfig = "classpath:sqltoy-translate.xml";

	/**
	 * 默认数据库
	 */
	private String defaultDataSource = "dataSource";

	/**
	 * 缓存管理器
	 */
	private Object cacheMananger;

	/**
	 * @param translateConfig
	 *            the translateConfig to set
	 */
	public void setTranslateConfig(String translateConfig) {
		this.translateConfig = translateConfig;
	}

	/**
	 * @param translateCacheManager
	 *            the translateCacheManager to set
	 */
	public void setTranslateCacheManager(TranslateCacheManager translateCacheManager) {
		this.translateCacheManager = translateCacheManager;
	}

	public void initialize() {
		if (initialized)
			return;
		initialized = true;
		// 提供默认基于ehcache缓存的实现
		if ((translateCacheManagers == null || translateCacheManagers.isEmpty()) && translateCacheManager == null) {
			translateCacheManager = new TranslateEhcacheManager();
		}
		if (translateCacheManager != null && (translateCacheManager instanceof TranslateEhcacheManager)) {
			if (translateCacheManager.getCacheManager() == null)
				translateCacheManager.setCacheManager(this.cacheMananger);
		}

		logger.debug("开始加载sqltoy的translate缓存翻译配置文件..........................");
		try {
			// 加载和解析缓存翻译的配置
			parseTranslate(translateConfig);
		} catch (Exception e) {
			e.printStackTrace();
			logger.error("加载和解析xml过程发生异常!{}", e.getMessage(), e);
		}
	}

	/**
	 * @todo 解析缓存翻译的配置文件
	 * @param configFile
	 * @throws Exception
	 */
	public void parseTranslate(String configFile) throws Exception {
		if (StringUtil.isBlank(configFile))
			return;
		InputStream fileIS = null;
		InputStreamReader ir = null;
		try {
			fileIS = CommonUtils.getFileInputStream(configFile);
			if (fileIS != null) {
				SAXReader saxReader = new SAXReader();
				saxReader.setFeature("http://apache.org/xml/features/nonvalidating/load-external-dtd", false);
				ir = new InputStreamReader(fileIS);
				Document doc = saxReader.read(ir);
				Element root = doc.getRootElement();
				List translates = root.elements("translate");
				if (translates == null || translates.isEmpty())
					return;
				Element translate;
				for (Iterator iter = translates.iterator(); iter.hasNext();) {
					translate = (Element) iter.next();
					TranslateCacheModel translateCacheModel = new TranslateCacheModel();
					// 对应缓存
					translateCacheModel.setCacheName(translate.attributeValue("cache"));
					// key对应的数据列
					if (translate.attribute("key-index") != null) {
						translateCacheModel.setKeyIndex(Integer.parseInt(translate.attributeValue("key-index")));
					}

					// 查询数据的service
					if (translate.attribute("service") != null) {
						translateCacheModel.setService(translate.attributeValue("service"));
					}

					// 查询数据的service对应的方法
					if (translate.attribute("method") != null) {
						translateCacheModel.setServiceMethod(translate.attributeValue("method"));
					}

					// 设置缓存管理器
					if (translate.attribute("cache-manager") != null) {
						translateCacheModel.setTranslateCacheManager(translate.attributeValue("cache-manager"));
					} else if (translate.attribute("manager") != null) {
						translateCacheModel.setTranslateCacheManager(translate.attributeValue("manager"));
					}

					// 过期时长
					CacheConfig cacheConfig = new CacheConfig();
					cacheConfig.setExpireSeconds(SqlToyConstants.getCacheExpireSeconds());
					if (translate.attribute("expire-seconds") != null) {
						cacheConfig.setExpireSeconds(Long.parseLong(translate.attributeValue("expire-seconds")));
					} else if (translate.attribute("keep-alive") != null) {
						cacheConfig.setExpireSeconds(Long.parseLong(translate.attributeValue("keep-alive")));
					}

					// sql对应的dataSource
					if (translate.attribute("datasource") != null)
						translateCacheModel.setDataSource(translate.attributeValue("datasource"));
					else if (translate.attribute("dataSource") != null)
						translateCacheModel.setDataSource(translate.attributeValue("dataSource"));
					// 基于简单的sql查询对应的sql
					if (translate.attribute("sql") != null) {
						translateCacheModel.setSql(translate.attributeValue("sql"));
					} else if (StringUtil.isNotBlank(translate.getTextTrim())) {
						translateCacheModel.setSql(translate.getText());
					} else if (translate.element("sql") != null) {
						translateCacheModel.setSql(translate.element("sql").getText());
					}
					translateMap.put(translateCacheModel.getCacheName(), translateCacheModel);
				}
			}
		} catch (DocumentException de) {
			de.printStackTrace();
			logger.error("读取translate对应的xml文件失败,对应文件={}", configFile, de);
			throw de;
		} catch (Exception e) {
			e.printStackTrace();
			throw e;
		} finally {
			if (ir != null)
				ir.close();
			if (fileIS != null)
				fileIS.close();
		}
	}

	/**
	 * @todo 根据sqltoy sql.xml中的翻译设置获取对应的缓存(多个translate对应的多个缓存结果)
	 * @param sqlToyContext
	 * @param conn
	 * @param translates
	 * @return
	 * @throws Exception
	 */
	public HashMap<String, HashMap<String, Object[]>> getTranslates(SqlToyContext sqlToyContext, Connection conn,
			HashMap<String, TranslateModel> translates) throws Exception {
		HashMap<String, HashMap<String, Object[]>> result = new HashMap<String, HashMap<String, Object[]>>();
		TranslateModel translate;
		String cacheName;
		HashMap<String, Object[]> cache;
		TranslateCacheModel cacheModel;
		for (Map.Entry<String, TranslateModel> entry : translates.entrySet()) {
			translate = entry.getValue();
			if (translateMap.containsKey(translate.getCache())) {
				cacheModel = translateMap.get(translate.getCache());
				cacheName = cacheModel.getCacheName();
				cache = getCache(sqlToyContext, conn, cacheName, translate.getDictType());
				if (cache != null)
					result.put(translate.getColumn(), cache);
				else {
					result.put(translate.getColumn(), new HashMap<String, Object[]>());
					logger.warn("sqltoy translate:cacheName={},cache-type={},column={}配置不正确,未获取对应cache数据!", cacheName,
							translate.getDictType(), translate.getColumn());
				}
			}
		}
		return result;
	}

	/**
	 * @todo 多种缓存翻译同时存在的时候通过key获取实际的缓存翻译管理器,同时兼容老的模式
	 * @param cacheManager
	 * @return
	 */
	private TranslateCacheManager getTranslateCacheManager(String cacheManager) {
		// key为null
		if (StringUtil.isBlank(cacheManager)) {
			// 返回默认缓存翻译管理器
			if (translateCacheManager != null)
				return translateCacheManager;
			// map中只有唯一的缓存翻译器
			else if (translateCacheManagers.size() == 1) {
				return translateCacheManagers.values().iterator().next();
			}
		}
		if (translateCacheManagers.containsKey(cacheManager))
			return translateCacheManagers.get(cacheManager);
		else
			return translateCacheManager;
	}

	/**
	 * @todo 根据sqltoy sql.xml中的翻译设置获取对应的缓存
	 * @param sqlToyContext
	 * @param conn
	 * @param cacheName
	 * @param cacheType
	 *            一般为null,不为空时一般用于数据字典等同于dictType
	 * @return
	 * @throws Exception
	 */
	public HashMap<String, Object[]> getCache(final SqlToyContext sqlToyContext, Connection conn, String cacheName,
			String cacheType) throws Exception {
		// 获取缓存翻译的管理器
		TranslateCacheModel cacheModel = translateMap.get(cacheName);
		if (cacheModel == null) {
			// logger.warn("sqltoy translate cache:{} 未定义,缓存翻译未生效!",
			// cacheName);
			return null;
		}
		TranslateCacheManager manager = getTranslateCacheManager(cacheModel.getTranslateCacheManager());
		HashMap<String, Object[]> result = manager.getCache(cacheName, cacheType);
		if (result == null || result.isEmpty()) {
			final Object[] args = StringUtil.isBlank(cacheType) ? null : new Object[] { cacheType };
			/*
			 * update 2016-4-21 by chenrenfei 针对缓存翻译功能可以根据sql id 配置的dataSource进行数据查询
			 * 以便于多数据库查询，缓存数据在一个数据库中，而其他查询数据在其他数据库的情况
			 */
			// sql 查询模式
			if (StringUtil.isNotBlank(cacheModel.getSql())) {
				final SqlToyConfig sqlToyConfig = sqlToyContext.getSqlToyConfig(cacheModel.getSql(), SqlType.search);
				String dataSourceName = cacheModel.getDataSource();
				if (dataSourceName == null)
					dataSourceName = sqlToyConfig.getDataSource();
				// 设置默认数据库
				if (null == conn && StringUtil.isBlank(dataSourceName))
					dataSourceName = getDefaultDataSource();
				List cacheResult = null;
				// 缓存sql来源于不同数据库
				if (sqlToyConfig.getDataSourceShardingStragety() != null
						|| StringUtil.isNotBlank(dataSourceName)) {
					DataSource dataBase = null;
					if (StringUtil.isNotBlank(dataSourceName))
						dataBase = sqlToyContext.getDataSource(dataSourceName);
					else {
						// 考虑存在分库策略，update 2017-12-8
						dataBase = ShardingUtils.getShardingDataSource(sqlToyContext, sqlToyConfig,
								new QueryExecutor(cacheModel.getSql(), null, args), null);
					}
					cacheResult = (List) DataSourceUtils.processDataSource(sqlToyContext, dataBase,
							new DataSourceCallbackHandler() {
								@Override
								public void doConnection(Connection conn, Integer dbType, String dialect)
										throws Exception {
									this.setResult(DialectUtils.findBySql(sqlToyContext, sqlToyConfig,
											sqlToyContext.convertFunctions(sqlToyConfig.getSql(), dialect), args, null,
											conn, 0, -1, -1).getRows());
								}
							});
				} else {
					cacheResult = DialectUtils.findBySql(sqlToyContext, sqlToyConfig,
							sqlToyContext.convertFunctions(sqlToyConfig.getSql(),
									DataSourceUtils.getDialect(DataSourceUtils.getDbType(conn))),
							args, null, conn, 0, -1, -1).getRows();
				}
				result = new HashMap<String, Object[]>();
				int cacheIndex = cacheModel.getKeyIndex();
				List row;
				for (int i = 0, n = cacheResult.size(); i < n; i++) {
					row = (List) cacheResult.get(i);
					Object[] rowAry = new Object[row.size()];
					for (int j = 0, t = rowAry.length; j < t; j++) {
						rowAry[j] = row.get(j);
					}
					result.put(rowAry[cacheIndex].toString(), rowAry);
				}
			} else {
				// 通过spring 调用具体的bean 方法获取数据，必须返回的是HashMap结果
				result = (HashMap<String, Object[]>) sqlToyContext.getServiceData(cacheModel.getService(),
						cacheModel.getServiceMethod(), args);
			}
			// 放入缓存
			if (result != null && !result.isEmpty()) {
				manager.put(cacheModel.getCacheConfig(), cacheName, cacheType, result);
			}
		}
		return result;
	}

	/**
	 * @param cacheManager
	 *            the cacheManager to set
	 */
	public void setCacheManager(Object cacheManager) {
		this.cacheMananger = cacheManager;
	}

	/**
	 * @return the defaultDataSource
	 */
	public String getDefaultDataSource() {
		return defaultDataSource;
	}

	/**
	 * @param defaultDataSource
	 *            the defaultDataSource to set
	 */
	public void setDefaultDataSource(String defaultDataSource) {
		this.defaultDataSource = defaultDataSource;
	}

	/**
	 * @param translateCacheManagers
	 *            the translateCacheManagers to set
	 */
	public void setTranslateCacheManagers(HashMap<String, TranslateCacheManager> translateCacheManagers) {
		this.translateCacheManagers = translateCacheManagers;
	}

}
