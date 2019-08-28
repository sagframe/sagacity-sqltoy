/**
 * @Copyright 2009 版权归陈仁飞，不要肆意侵权抄袭，如引用请注明出处保留作者信息。
 */
package org.sagacity.sqltoy.translate;

import java.sql.Connection;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Timer;

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.sagacity.sqltoy.SqlToyContext;
import org.sagacity.sqltoy.config.model.SqlTranslate;
import org.sagacity.sqltoy.translate.cache.TranslateCacheManager;
import org.sagacity.sqltoy.translate.cache.impl.TranslateEhcacheManager;
import org.sagacity.sqltoy.translate.model.CheckerConfigModel;
import org.sagacity.sqltoy.translate.model.DefaultConfig;
import org.sagacity.sqltoy.translate.model.TranslateConfigModel;
import org.sagacity.sqltoy.utils.StringUtil;

/**
 * @project sqltoy-orm
 * @description sqltoy 缓存翻译器(通过缓存存储常用数据，如数据字典、机构、员工等，从而在数据库查询时可以避免 关联查询)
 * @author chenrenfei <a href="mailto:zhongxuchen@gmail.com">联系作者</a>
 * @version id:TranslateManager.java,Revision:v1.0,Date:2013年4月8日
 * @Modification {Date:2017-12-8,提取缓存时增加分库策略判断,如果存在分库策略dataSource则按照分库逻辑提取}
 * @Modification {Date:2018-1-5,增加redis缓存翻译的管理和支持}
 */
public class TranslateManager {
	/**
	 * 定义全局日志
	 */
	protected final static Logger logger = LogManager.getLogger(TranslateManager.class);

	/**
	 * 翻译缓存管理器，默认提供基于ehcache的实现，用户可以另行定义
	 */
	private TranslateCacheManager translateCacheManager;

	/**
	 * 字符集
	 */
	private String charset = "UTF-8";

	/**
	 * 翻译配置解析后的模型
	 */
	private HashMap<String, TranslateConfigModel> translateMap = new HashMap<String, TranslateConfigModel>();

	/**
	 * 更新检测器
	 */
	private List<CheckerConfigModel> updateCheckers = new ArrayList<CheckerConfigModel>();

	/**
	 * 是否初始化过
	 */
	private boolean initialized = false;

	/**
	 * 翻译器配置文件,默认配置文件放于classpath下面，名称为sqltoy-translate.xml
	 */
	private String translateConfig = "classpath:sqltoy-translate.xml";

	/**
	 * 定时器
	 */
	private Timer timer;

	/**
	 * @param translateConfig
	 *            the translateConfig to set
	 */
	public void setTranslateConfig(String translateConfig) {
		this.translateConfig = translateConfig;
	}

	public synchronized void initialize(SqlToyContext sqlToyContext) throws Exception {
		if (initialized)
			return;
		logger.debug("开始加载sqltoy的translate缓存翻译配置文件..........................");
		try {
			// 加载和解析缓存翻译的配置
			DefaultConfig defaultConfig = TranslateConfigParse.parseTranslateConfig(sqlToyContext, translateMap,
					updateCheckers, translateConfig, charset);
			// 配置了缓存翻译
			if (defaultConfig != null) {
				if (translateCacheManager == null)
					translateCacheManager = new TranslateEhcacheManager();
				if (!StringUtil.isBlank(defaultConfig.getDiskStorePath()))
					((TranslateEhcacheManager) translateCacheManager)
							.setDiskStorePath(defaultConfig.getDiskStorePath());
				translateCacheManager.init();
				initialized = true;
				if (timer == null)
					timer = new Timer();
				/**
				 * 每隔1秒执行一次检查(检查各个任务时间间隔是否到达设定的区间,并不意味着一秒执行数据库或调用接口) 正常情况下,这种检查都是高效率的空转不影响性能
				 */
				timer.schedule(new CacheCheckTimer(sqlToyContext, translateCacheManager, updateCheckers), 20000, 1000);
			}
		} catch (Exception e) {
			e.printStackTrace();
			logger.error("加载和解析xml过程发生异常!{}", e.getMessage(), e);
			throw e;
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
			HashMap<String, SqlTranslate> translates) {
		HashMap<String, HashMap<String, Object[]>> result = new HashMap<String, HashMap<String, Object[]>>();
		SqlTranslate translate;
		HashMap<String, Object[]> cache;
		TranslateConfigModel cacheModel;
		for (Map.Entry<String, SqlTranslate> entry : translates.entrySet()) {
			translate = entry.getValue();
			if (translateMap.containsKey(translate.getCache())) {
				cacheModel = translateMap.get(translate.getCache());
				cache = getCacheData(sqlToyContext, cacheModel, translate.getDictType());
				if (cache != null)
					result.put(translate.getColumn(), cache);
				else {
					result.put(translate.getColumn(), new HashMap<String, Object[]>());
					logger.warn("sqltoy translate:cacheName={},cache-type={},column={}配置不正确,未获取对应cache数据!",
							cacheModel.getCache(), translate.getDictType(), translate.getColumn());
				}
			} else {
				logger.error("cacheName:{} 没有配置,请检查sqltoy-translate.xml文件!", translate.getCache());
			}
		}
		return result;
	}

	/**
	 * @todo 根据sqltoy sql.xml中的翻译设置获取对应的缓存
	 * @param sqlToyContext
	 * @param cacheModel
	 * @param cacheType
	 *            一般为null,不为空时一般用于数据字典等同于dictType
	 * @return
	 * @throws Exception
	 */
	private HashMap<String, Object[]> getCacheData(final SqlToyContext sqlToyContext, TranslateConfigModel cacheModel,
			String cacheType) {
		HashMap<String, Object[]> result = translateCacheManager.getCache(cacheModel.getCache(), cacheType);
		if (result == null || result.isEmpty()) {
			result = TranslateFactory.getCacheData(sqlToyContext, cacheModel, cacheType);
			// 放入缓存
			if (result != null && !result.isEmpty()) {
				translateCacheManager.put(cacheModel, cacheModel.getCache(), cacheType, result);
			}
		}
		return result;
	}

	/**
	 * @todo 提供对外的访问
	 * @param sqlToyContext
	 * @param cacheName
	 * @param cacheType
	 * @return
	 * @throws Exception
	 */
	public HashMap<String, Object[]> getCacheData(final SqlToyContext sqlToyContext, String cacheName,
			String cacheType) {
		TranslateConfigModel cacheModel = translateMap.get(cacheName);
		if (cacheModel == null) {
			logger.error("cacheName:{} 没有配置,请检查sqltoy-translate.xml文件!", cacheName);
			return null;
		}
		return getCacheData(sqlToyContext, cacheModel, cacheType);
	}

	/**
	 * @param charset
	 *            the charset to set
	 */
	public void setCharset(String charset) {
		this.charset = charset;
	}

	/**
	 * @param translateCacheManager
	 *            the translateCacheManager to set
	 */
	public void setTranslateCacheManager(TranslateCacheManager translateCacheManager) {
		this.translateCacheManager = translateCacheManager;
	}

	/**
	 * @return the translateCacheManager
	 */
	public TranslateCacheManager getTranslateCacheManager() {
		return translateCacheManager;
	}

	public void close() {
		if (translateCacheManager != null)
			translateCacheManager.destroy();
		if (timer != null)
			timer.cancel();
	}
}
