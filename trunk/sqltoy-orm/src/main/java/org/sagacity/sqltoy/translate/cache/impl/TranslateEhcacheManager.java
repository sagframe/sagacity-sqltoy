/**
 * 
 */
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
 * @project sagacity-sqltoy4.0
 * @description 基于ehcache缓存实现translate 提取缓存数据和存放缓存
 * @author zhongxu <a href="mailto:zhongxuchen@gmail.com">联系作者</a>
 * @version id:TranslateEhcacheManager.java,Revision:v1.0,Date:2013-4-14
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
	 * @param diskStorePath
	 *            the diskStorePath to set
	 */
	public void setDiskStorePath(String diskStorePath) {
		this.diskStorePath = diskStorePath;
	}

	protected static CacheManager cacheManager;

	@Override
	public HashMap<String, Object[]> getCache(String cacheName, String cacheKey) {
		if (cacheManager == null)
			return null;
		Cache<String, HashMap> cache = cacheManager.getCache(cacheName, String.class, HashMap.class);
		if (cache == null)
			return null;
		Object cacheValue = cache.get(StringUtil.isNotBlank(cacheKey) ? cacheKey : cacheName);
		if (cacheValue != null) {
			return (HashMap<String, Object[]>) cacheValue;
		}
		return null;
	}

	@Override
	public void put(TranslateConfigModel cacheConfig, String cacheName, String cacheKey,
			HashMap<String, Object[]> cacheValue) {
		if (cacheManager == null)
			return;
		synchronized (cacheName) {
			Cache<String, HashMap> cache = cacheManager.getCache(cacheName, String.class, HashMap.class);
			// 缓存没有配置,自动创建缓存(不建议使用)
			if (cache == null) {
				ResourcePoolsBuilder resBuilder = ResourcePoolsBuilder.newResourcePoolsBuilder();
				// 堆内内存大小(20000条)
				resBuilder = resBuilder.heap((cacheConfig.getHeap() < 1) ? 1000 : cacheConfig.getHeap(),
						EntryUnit.ENTRIES);
				if (cacheConfig.getOffHeap() > 0) {
					resBuilder = resBuilder.offheap(cacheConfig.getOffHeap(), MemoryUnit.MB);
				}
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
	public void clear(String cacheName, String cacheKey) {
		synchronized (cacheName) {
			if (cacheManager == null)
				return;
			Cache<String, HashMap> cache = cacheManager.getCache(cacheName, String.class, HashMap.class);
			// 缓存没有配置,自动创建缓存不建议使用
			if (cache != null) {
				if (StringUtil.isBlank(cacheKey)) {
					cache.clear();
				} else {
					cache.remove(cacheKey);
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
		if (cacheManager != null)
			return true;
		// 未定义持久化文件,则由ehcache自行默认创建
		if (StringUtil.isBlank(diskStorePath)) {
			cacheManager = CacheManagerBuilder.newCacheManagerBuilder().build(true);
			return true;
		} else {
			// 解决一些场景下,主程序已经关闭但缓存文件仍然被占用,重新开辟一个缓存文件
			try {
				cacheManager = CacheManagerBuilder.newCacheManagerBuilder()
						.with(CacheManagerBuilder.persistence(diskStorePath)).build(true);
				return true;
			} catch (Exception e) {
				logger.error("cache file:{} is locked,create cacheManager failure,please stop running progress!",
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
					logger.error("cann't create cacheManager with file:{},you cann't use cacheTranslate!",
							realCacheFile);
				}
			}
			return false;
		}
	}

	/*
	 * (non-Javadoc)
	 * 
	 * @see org.sagacity.sqltoy.translate.cache.TranslateCacheManager#destroy()
	 */
	@Override
	public void destroy() {
		if (cacheManager != null) {
			cacheManager.close();
			cacheManager = null;
		}
	}

}
