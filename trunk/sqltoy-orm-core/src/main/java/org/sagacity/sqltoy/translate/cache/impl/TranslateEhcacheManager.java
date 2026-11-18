package org.sagacity.sqltoy.translate.cache.impl;

import java.time.Duration;
import java.util.HashMap;

import org.ehcache.Cache;
import org.ehcache.CacheManager;
import org.ehcache.config.builders.CacheConfigurationBuilder;
import org.ehcache.config.builders.CacheManagerBuilder;
import org.ehcache.config.builders.ExpiryPolicyBuilder;
import org.ehcache.config.builders.ResourcePoolsBuilder;
import org.ehcache.config.units.EntryUnit;
import org.ehcache.config.units.MemoryUnit;
import org.sagacity.sqltoy.translate.cache.TranslateCacheManager;
import org.sagacity.sqltoy.translate.model.TranslateConfigModel;
import org.sagacity.sqltoy.utils.IdUtil;
import org.sagacity.sqltoy.utils.StringUtil;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * @project sagacity-sqltoy
 * @description 基于ehcache缓存实现translate 提取缓存数据和存放缓存
 * @author zhongxuchen
 * @version v1.0,Date:2013-04-14
 */
@SuppressWarnings("unchecked")
public class TranslateEhcacheManager extends TranslateCacheManager {
	/**
	 * 定义全局日志
	 */
	protected final static Logger logger = LoggerFactory.getLogger(TranslateEhcacheManager.class);

	/**
	 * 缓存大小超出范围后存储磁盘路径(默认不存储到文件)
	 */
	private String diskStorePath = null;

	/**
	 * @param diskStorePath the diskStorePath to set
	 */
	public void setDiskStorePath(String diskStorePath) {
		this.diskStorePath = diskStorePath;
	}

	// update 2026-11-15 原为static:JVM内多个SqlToyContext共用同一个ehcache CacheManager,
	// 后初始化context的diskStorePath等配置被静默忽略,且任一context的destroy会关闭共享manager
	// 导致其它存活context的翻译缓存整体失效(读取返回null后退化为每次实时查库);
	// 改为实例字段,缓存管理器随SqlToyContext生命周期隔离
	protected CacheManager cacheManager;

	@Override
	public HashMap<String, Object[]> getCache(String cacheName, String cacheType) {
		if (cacheManager == null) {
			return null;
		}
		Cache<String, HashMap> cache = cacheManager.getCache(cacheName, String.class, HashMap.class);
		if (cache == null) {
			return null;
		}
		Object cacheValue = cache.get(StringUtil.isNotBlank(cacheType) ? cacheType : cacheName);
		if (cacheValue != null) {
			return (HashMap<String, Object[]>) cacheValue;
		}
		return null;
	}

	@Override
	public boolean hasCache(String cacheName) {
		// init未执行或destroy后cacheManager为null,与getCache/put/clear的判空防护对称
		if (cacheManager == null) {
			return false;
		}
		Cache<String, HashMap> cache = cacheManager.getCache(cacheName, String.class, HashMap.class);
		if (null == cache) {
			return false;
		}
		return true;
	}

	@Override
	public void put(TranslateConfigModel cacheConfig, String cacheName, String cacheKey,
			HashMap<String, Object[]> cacheValue) {
		if (cacheManager == null) {
			return;
		}
		// update 2026-11-15 原synchronized(cacheName.intern())是JVM级锁,且缓存名会常驻字符串常量池;
		// manager已按context实例化,创建缓存属低频操作,实例锁即可保证同实例内不重复创建
		synchronized (this) {
			Cache<String, HashMap> cache = cacheManager.getCache(cacheName, String.class, HashMap.class);
			// 缓存没有配置,自动创建缓存(不建议使用)
			if (cache == null) {
				ResourcePoolsBuilder resBuilder = ResourcePoolsBuilder.newResourcePoolsBuilder();
				// 堆内内存大小(默认10000条)
				resBuilder = resBuilder.heap((cacheConfig.getHeap() < 1) ? 1000 : cacheConfig.getHeap(),
						EntryUnit.ENTRIES);
				// offHeap 堆外内存
				if (cacheConfig.getOffHeap() > 0) {
					resBuilder = resBuilder.offheap(cacheConfig.getOffHeap(), MemoryUnit.MB);
				}
				// disk
				if (cacheConfig.getDiskSize() > 0) {
					resBuilder = resBuilder.disk(cacheConfig.getDiskSize(), MemoryUnit.MB, true);
				}
				cache = cacheManager.createCache(cacheName,
						CacheConfigurationBuilder.newCacheConfigurationBuilder(String.class, HashMap.class, resBuilder)
								.withExpiry(cacheConfig.getKeepAlive() > 0
										? ExpiryPolicyBuilder
												.timeToLiveExpiration(Duration.ofSeconds(cacheConfig.getKeepAlive()))
										: ExpiryPolicyBuilder.noExpiration())
								.build());
			}
			// 清除缓存(一般不会执行,即缓存值被设置为null表示清除缓存)
			if (cacheValue == null) {
				if (StringUtil.isBlank(cacheKey)) {
					cache.clear();
				} else {
					cache.remove(cacheKey);
				}
			}
			// 更新缓存
			else {
				cache.put(StringUtil.isBlank(cacheKey) ? cacheName : cacheKey, cacheValue);
			}
		}
	}

	/*
	 * (non-Javadoc)
	 * 
	 * @see
	 * org.sagacity.sqltoy.translate.cache.TranslateCacheManager#clear(java.lang.
	 * String, java.lang.String)
	 */
	@Override
	public void clear(String cacheName, String cacheType) {
		if (cacheManager == null) {
			return;
		}
		synchronized (this) {
			Cache<String, HashMap> cache = cacheManager.getCache(cacheName, String.class, HashMap.class);
			if (cache != null) {
				if (StringUtil.isBlank(cacheType)) {
					cache.clear();
				} else {
					cache.remove(cacheType);
				}
			}
		}
	}

	/*
	 * (non-Javadoc)
	 * 
	 * @see org.sagacity.sqltoy.translate.cache.TranslateCacheManager#init()
	 */
	@Override
	public boolean init() {
		// update 2026-11-15 每个SqlToyContext持有独立的CacheManager(原static共享):init只创建本实例,
		// 未配置持久化路径时各自开辟内存级管理,配置了持久化路径时各自独立open
		if (cacheManager != null) {
			return true;
		}
		logger.debug("started ehcache cache manager--------------------------------------");
		// 未定义持久化文件,则由ehcache自行默认创建
		if (StringUtil.isBlank(diskStorePath)) {
			cacheManager = CacheManagerBuilder.newCacheManagerBuilder().build(true);
			return true;
		}
		// 解决一些场景下,主程序已经关闭但缓存文件仍然被占用,重新开辟一个缓存文件
		try {
			cacheManager = CacheManagerBuilder.newCacheManagerBuilder()
					.with(CacheManagerBuilder.persistence(diskStorePath)).build(true);
			return true;
		} catch (Exception e) {
			// update 2026-11-15 manager按context实例化后,同JVM多context共用diskStorePath会在此冲突,
			// 属可预期的回退分支(下方会改用唯一文件名),降为warn避免误导为启动失败
			logger.warn(
					"cache file:{} is locked(possibly used by another program or another SqlToyContext in the same JVM), will create a new unique cache file instead!",
					diskStorePath);
		}
		if (cacheManager == null) {
			// 缓存文件被锁,重新定义一个不重复的文件名称
			String realCacheFile = diskStorePath.concat(IdUtil.getShortNanoTimeId(null).toPlainString());
			try {
				logger.warn("sqltoy ehcacheManager create cache file:{}", realCacheFile);
				cacheManager = CacheManagerBuilder.newCacheManagerBuilder()
						.with(CacheManagerBuilder.persistence(realCacheFile)).build(true);
				return true;
			} catch (Exception e) {
				logger.error("cann't create cacheManager with file:{},you cann't use cacheTranslate!", realCacheFile);
			}
		}
		return false;
	}

	/*
	 * (non-Javadoc)
	 * 
	 * @see org.sagacity.sqltoy.translate.cache.TranslateCacheManager#destroy()
	 */
	@Override
	public void destroy() {
		// update 2026-11-15 只关闭本实例持有的CacheManager:一个SqlToyContext销毁不得影响同JVM其它存活上下文
		if (cacheManager != null) {
			cacheManager.close();
			cacheManager = null;
		}
	}
}
