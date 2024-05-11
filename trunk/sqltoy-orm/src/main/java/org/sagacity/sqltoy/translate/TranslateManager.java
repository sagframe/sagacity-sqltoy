package org.sagacity.sqltoy.translate;

import java.util.Collection;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Iterator;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.CopyOnWriteArrayList;

import org.sagacity.sqltoy.SqlExecuteStat;
import org.sagacity.sqltoy.SqlToyContext;
import org.sagacity.sqltoy.config.model.SqlExecuteTrace;
import org.sagacity.sqltoy.config.model.Translate;
import org.sagacity.sqltoy.model.IgnoreKeyCaseMap;
import org.sagacity.sqltoy.model.inner.TranslateExtend;
import org.sagacity.sqltoy.translate.cache.TranslateCacheManager;
import org.sagacity.sqltoy.translate.cache.impl.TranslateEhcacheManager;
import org.sagacity.sqltoy.translate.model.CheckerConfigModel;
import org.sagacity.sqltoy.translate.model.DefaultConfig;
import org.sagacity.sqltoy.translate.model.TranslateConfigModel;
import org.sagacity.sqltoy.utils.StringUtil;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * @project sqltoy-orm
 * @description sqltoy 缓存翻译器(通过缓存存储常用数据，如数据字典、机构、员工等，从而在数据库查询时可以避免 关联查询)
 * @author zhongxuchen
 * @version v1.0,Date:2013年4月8日
 * @modify {Date:2018-1-5,增强缓存更新检测机制}
 * @modify {Date:2022-06-11,支持多个缓存翻译定义文件}
 */
public class TranslateManager {
	/**
	 * 定义全局日志
	 */
	protected final static Logger logger = LoggerFactory.getLogger(TranslateManager.class);

	/**
	 * 翻译缓存管理器，默认提供基于ehcache的实现，用户可以另行定义
	 */
	private TranslateCacheManager translateCacheManager;

	/**
	 * 字符集
	 */
	private String charset = "UTF-8";

	/**
	 * 翻译配置解析后的模型(update 2021-11-15 HashMap转为IgnoreKeyCaseMap)
	 */
	private IgnoreKeyCaseMap<String, TranslateConfigModel> translateMap = new IgnoreKeyCaseMap<String, TranslateConfigModel>();

	/**
	 * 更新检测器
	 */
	private CopyOnWriteArrayList<CheckerConfigModel> updateCheckers = new CopyOnWriteArrayList<CheckerConfigModel>();

	/**
	 * 是否初始化过
	 */
	private boolean initialized = false;

	/**
	 * 翻译器配置文件,默认配置文件放于classpath下面，名称为sqltoy-translate.xml
	 */
	private String translateConfig = null;

	/**
	 * 默认配置支持单个文件和具体路径下的多个文件
	 */
	public final static String defaultTranslateConfig = "classpath:sqltoy-translate.xml;classpath:translates";

	/**
	 * 缓存更新检测程序(后台线程)
	 */
	private CacheUpdateWatcher cacheUpdateWatcher;

	private SqlToyContext sqlToyContext;

	/**
	 * @param translateConfig the translateConfig to set
	 */
	public void setTranslateConfig(String translateConfig) {
		this.translateConfig = translateConfig;
	}

	/**
	 * @TODO 初始化缓存管理器
	 * @param sqlToyContext
	 * @param cacheManager           自定义的缓存管理器(一般为null)
	 * @param delayCheckCacheSeconds 延时多久进行更新检测
	 * @throws Exception
	 */
	public synchronized void initialize(SqlToyContext sqlToyContext, TranslateCacheManager cacheManager,
			int delayCheckCacheSeconds) throws Exception {
		// 防止被多次调用
		if (initialized) {
			return;
		}
		try {
			this.sqlToyContext = sqlToyContext;
			initialized = true;
			String realTranslateConfig = (translateConfig == null) ? defaultTranslateConfig : translateConfig;
			logger.debug("开始加载sqltoy的translate缓存翻译配置文件:{}", realTranslateConfig);
			// 加载和解析缓存翻译的配置
			DefaultConfig defaultConfig = TranslateConfigParse.parseTranslateConfig(sqlToyContext, translateMap,
					updateCheckers, realTranslateConfig, (translateConfig == null), charset);
			// 配置了缓存翻译
			if (defaultConfig.isUseCache()) {
				// 可以自定义缓存管理器,默认为ehcache实现
				if (cacheManager == null) {
					translateCacheManager = new TranslateEhcacheManager();
				} else {
					translateCacheManager = cacheManager;
				}
				// 设置默认存储路径
				if (!StringUtil.isBlank(defaultConfig.getDiskStorePath())
						&& translateCacheManager instanceof TranslateEhcacheManager) {
					((TranslateEhcacheManager) translateCacheManager)
							.setDiskStorePath(defaultConfig.getDiskStorePath());
				}
				// 设置装入具体缓存配置
				translateCacheManager.setTranslateMap(translateMap);
				boolean initSuccess = translateCacheManager.init();
				// 每隔1秒执行一次检查(检查各个任务时间间隔是否到达设定的区间,并不意味着一秒执行数据库或调用接口) 正常情况下,
				// 这种检查都是高效率的空转不影响性能
				if (initSuccess) {
					cacheUpdateWatcher = new CacheUpdateWatcher(sqlToyContext, translateCacheManager, translateMap,
							updateCheckers, delayCheckCacheSeconds, defaultConfig.getDeviationSeconds());
					cacheUpdateWatcher.start();
					logger.debug("sqltoy的translate共:{} 个缓存配置加载完成,并且启动:{} 个缓存更新检测!", translateMap.size(),
							updateCheckers.size());
				} else {
					logger.debug("sqltoy的translate共:{} 个缓存配置加载完成,您没有配置缓存更新检测机制或没有配置缓存,将不做缓存更新检测!", translateMap.size());
				}
			} else {
				logger.warn(
						"translateConfig={} 未找到实际定义文件,请正确定义[以.trans.xml|-translate.xml|-translates.xml结尾],如不使用缓存翻译可忽视此提示!",
						realTranslateConfig);
			}
		} catch (Exception e) {
			logger.error("加载sqltoy的translate缓存翻译过程发生异常!{}", e.getMessage(), e);
			throw e;
		}
	}

	/**
	 * @todo 根据sqltoy sql.xml中的翻译设置获取对应的缓存(多个translate对应的多个缓存结果)
	 * @param translates
	 * @return
	 */
	public HashMap<String, HashMap<String, Object[]>> getTranslates(HashMap<String, Translate> translates) {
		// 获得当前线程中的sql执行日志，后续缓存获取会覆盖掉日志
		SqlExecuteTrace sqlTrace = SqlExecuteStat.get();
		HashMap<String, HashMap<String, Object[]>> result = new HashMap<String, HashMap<String, Object[]>>();
		HashMap<String, Object[]> cache;
		TranslateConfigModel cacheModel;
		TranslateExtend extend;
		int cacheEltLength;
		for (Map.Entry<String, Translate> entry : translates.entrySet()) {
			extend = entry.getValue().getExtend();
			if (translateMap.containsKey(extend.cache)) {
				cacheModel = translateMap.get(extend.cache);
				cache = getCacheData(cacheModel, extend.cacheType);
				if (cache != null) {
					// update 2022-1-4 增加缓存使用时cache-index 合法性校验
					if (cache.size() > 0) {
						cacheEltLength = cache.values().iterator().next().length;
						if (extend.index >= cacheEltLength) {
							throw new IllegalArgumentException("缓存取值数组越界:cacheName:" + extend.cache + ", column:"
									+ extend.column + ",cache-indexs:(" + extend.index + ">=" + cacheEltLength
									+ ")[缓存内容数组长度],请检查cache-indexs值确保跟缓存数据具体列保持一致!");
						}
					}
					result.put(extend.column, cache);
				} else {
					result.put(extend.column, new HashMap<String, Object[]>());
					if (logger.isWarnEnabled()) {
						logger.warn("sqltoy translate:cacheName={},cache-type={},column={}配置不正确,未获取对应cache数据!",
								cacheModel.getCache(), extend.cacheType, extend.column);
					} else {
						System.err.println("sqltoy translate:cacheName=" + cacheModel.getCache() + ",cache-type="
								+ extend.cacheType + ",column=" + extend.column + " 配置不正确,未获取对应cache数据!");
					}
				}
			} else {
				logger.error("cacheName:{} 没有配置,请检查缓存配置文件!", extend.cache);
			}
		}
		// 将调用获取缓存之前的日志放回线程中
		if (sqlTrace != null) {
			SqlExecuteStat.set(sqlTrace);
		}
		return result;
	}

	/**
	 * @todo 根据sqltoy sql.xml中的翻译设置获取对应的缓存
	 * @param cacheModel
	 * @param cacheType  一般为null,不为空时一般用于数据字典等同于dictType
	 * @return
	 */
	private HashMap<String, Object[]> getCacheData(TranslateConfigModel cacheModel, String cacheType) {
		// 从缓存中提取数据
		HashMap<String, Object[]> result = translateCacheManager.getCache(cacheModel.getCache(), cacheType);
		// 数据为空则执行调用逻辑提取数据放入缓存，否则直接返回
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
	 * @todo 提供对外的访问(如要做增量更新可以对这里的数据进行修改即可达到缓存的更新作用)
	 * @param cacheName
	 * @param cacheType (一般为null,不为空时一般用于数据字典等同于dictType)
	 * @return
	 */
	public HashMap<String, Object[]> getCacheData(String cacheName, String cacheType) {
		TranslateConfigModel cacheModel = translateMap.get(cacheName);
		if (cacheModel == null) {
			logger.error("cacheName:{} 没有配置,请检查缓存配置文件!", cacheName);
			return null;
		}
		// 获得当前线程中的sql执行日志，后续缓存获取会覆盖掉日志
		SqlExecuteTrace sqlTrace = SqlExecuteStat.get();
		HashMap<String, Object[]> result = getCacheData(cacheModel, cacheType);
		// 将调用获取缓存之前的日志放回线程中
		if (sqlTrace != null) {
			SqlExecuteStat.set(sqlTrace);
		}
		return result;
	}

	/**
	 * @todo 更新单个缓存的整体数据
	 * @param cacheName
	 * @param cacheType  (默认为null，针对诸如数据字典类型的，对应字典类型)
	 * @param cacheValue
	 */
	public void putCacheData(String cacheName, String cacheType, HashMap<String, Object[]> cacheValue) {
		if (translateCacheManager != null) {
			TranslateConfigModel cacheModel = translateMap.get(cacheName);
			if (cacheModel == null) {
				logger.error("cacheName:{} 没有配置,请检查缓存配置文件!", cacheName);
				return;
			}
			translateCacheManager.put(cacheModel, cacheModel.getCache(), cacheType, cacheValue);
		} else {
			logger.error("因没有定义缓存翻译的配置文件(可不定义具体缓存)，则没有启用缓存翻译,无法设置缓存数据!");
		}
	}

	/**
	 * @todo 清空缓存
	 * @param cacheName
	 * @param cacheType (默认为null，针对诸如数据字典类型的，对应字典类型)
	 */
	public void clear(String cacheName, String cacheType) {
		if (translateCacheManager != null) {
			TranslateConfigModel cacheModel = translateMap.get(cacheName);
			if (cacheModel != null) {
				translateCacheManager.clear(cacheModel.getCache(), cacheType);
			}
		}
	}

	/**
	 * @todo 判断cache是否存在
	 * @param cacheName
	 * @return
	 */
	public boolean existCache(String cacheName) {
		return translateMap.containsKey(cacheName);
	}

	public TranslateConfigModel getCacheConfig(String cacheName) {
		return translateMap.get(cacheName);
	}

	/**
	 * @TODO 动态增加缓存配置(只允许增加和覆盖,不允许删除)
	 * @param translateConfigModel
	 */
	public void putCache(TranslateConfigModel translateConfigModel) {
		if (translateConfigModel == null) {
			return;
		}
		if (translateCacheManager == null) {
			logger.error("因没有定义缓存翻译的配置文件(可不定义具体缓存)，则没有启用缓存翻译,无法动态增加缓存!");
		} else {
			translateMap.put(translateConfigModel.getCache(), translateConfigModel);
		}
	}

	/**
	 * @TODO 移除某个缓存翻译配置
	 * @param cacheName
	 */
	public void removeCache(String cacheName) {
		TranslateConfigModel cacheModel = translateMap.get(cacheName);
		if (cacheModel == null) {
			logger.error("cacheName:{} 没有配置,请检查缓存配置文件!", cacheName);
			return;
		}
		translateMap.remove(cacheName);
		if (translateCacheManager != null) {
			// 清除缓存数据
			translateCacheManager.clear(cacheModel.getCache(), null);
		}
		// 移除对应缓存更新检测
		CheckerConfigModel checker;
		for (int i = 0; i < updateCheckers.size(); i++) {
			checker = updateCheckers.get(i);
			if (checker.getCache().equalsIgnoreCase(cacheName)) {
				updateCheckers.remove(i);
				break;
			}
		}
	}

	/**
	 * @TODO 移除某个缓存更新检测器
	 * @param checkerConfigModel
	 */
	public void removeCacheUpdater(CheckerConfigModel checkerConfigModel) {
		if (checkerConfigModel == null) {
			return;
		}
		if (StringUtil.isNotBlank(checkerConfigModel.getCache())) {
			if (!translateMap.containsKey(checkerConfigModel.getCache())) {
				logger.error("cacheName:{} 不存在无需做移除,请检查缓存配置文件!", checkerConfigModel.getCache());
				return;
			}
		}
		CheckerConfigModel checker;
		for (int i = 0; i < updateCheckers.size(); i++) {
			checker = updateCheckers.get(i);
			if (checker.getId().equalsIgnoreCase(checkerConfigModel.getId())) {
				updateCheckers.remove(i);
				break;
			}
		}
	}

	/**
	 * @TODO 动态增加或者更新缓存变更检测器
	 * @param checkerConfigModel
	 */
	public void putCacheUpdater(CheckerConfigModel checkerConfigModel) {
		if (checkerConfigModel == null) {
			return;
		}
		// 具体缓存的更新，验证缓存是否存在
		if (StringUtil.isNotBlank(checkerConfigModel.getCache())) {
			if (!translateMap.containsKey(checkerConfigModel.getCache())) {
				logger.error("cacheName:{} 没有配置,请检查缓存配置文件!", checkerConfigModel.getCache());
				return;
			}
		} // 增量模式，必须针对具体的cacheName
		else if (checkerConfigModel.isIncrement()) {
			logger.error("缓存增量更新检测必须要指定具体的缓存名称:checkerConfigModel.setCache(cacheName)!");
			return;
		}
		// 验证sql\service\rest三种形态必须有一种
		if (StringUtil.isBlank(checkerConfigModel.getSql())
				&& (StringUtil.isBlank(checkerConfigModel.getService())
						|| StringUtil.isBlank(checkerConfigModel.getMethod()))
				&& StringUtil.isBlank(checkerConfigModel.getUrl())) {
			logger.error("缓存更新检测必须设定:sql、[service|method]、url(rest) 三种类型中的一种!");
			return;
		}
		// 先移除之前同名的
		CheckerConfigModel checker;
		for (int i = 0; i < updateCheckers.size(); i++) {
			checker = updateCheckers.get(i);
			if (checker.getId().equalsIgnoreCase(checkerConfigModel.getId())) {
				updateCheckers.remove(i);
				break;
			}
		}
		updateCheckers.add(checkerConfigModel);
	}

	/**
	 * @todo 获取所有缓存的名称
	 * @return
	 */
	public Set<String> getCacheNames() {
		Set<String> cacheNames = new HashSet<String>();
		if (!translateMap.isEmpty()) {
			Iterator<TranslateConfigModel> iter = translateMap.values().iterator();
			while (iter.hasNext()) {
				cacheNames.add(iter.next().getCache());
			}
		}
		return cacheNames;
	}

	/**
	 * @TODO 获取全部的缓存翻译配置信息
	 * @return
	 */
	public Collection<TranslateConfigModel> getAllTranslates() {
		return translateMap.values();
	}

	/**
	 * @param charset the charset to set
	 */
	public void setCharset(String charset) {
		this.charset = charset;
	}

	/**
	 * @return the translateCacheManager
	 */
	public TranslateCacheManager getTranslateCacheManager() {
		return translateCacheManager;
	}

	public void destroy() {
		try {
			if (translateCacheManager != null) {
				translateCacheManager.destroy();
			}
			if (cacheUpdateWatcher != null && !cacheUpdateWatcher.isInterrupted()) {
				cacheUpdateWatcher.interrupt();
			}
		} catch (Exception e) {

		}
	}
}
