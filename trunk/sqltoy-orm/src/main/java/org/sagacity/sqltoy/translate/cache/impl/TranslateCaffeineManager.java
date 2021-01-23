/**
 * 
 */
package org.sagacity.sqltoy.translate.cache.impl;

import java.time.Duration;
import java.util.HashMap;

import org.sagacity.sqltoy.translate.cache.TranslateCacheManager;
import org.sagacity.sqltoy.translate.model.TranslateConfigModel;
import org.sagacity.sqltoy.utils.StringUtil;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.cache.Cache;
import org.springframework.cache.caffeine.CaffeineCacheManager;

import com.github.benmanes.caffeine.cache.Caffeine;

/**
 * @project sagacity-sqltoy
 * @description 提供基于Caffeine缓存实现
 * @author zhongxuchen
 * @version v1.0, Date:2021-1-14
 * @modify 2021-1-14,修改说明
 */
public class TranslateCaffeineManager extends TranslateCacheManager {
	/**
	 * 定义全局日志
	 */
	protected final static Logger logger = LoggerFactory.getLogger(TranslateCaffeineManager.class);

	protected static CaffeineCacheManager cacheManager;

	@Override
	public HashMap<String, Object[]> getCache(String cacheName, String cacheType) {
		if (cacheManager == null) {
			return null;
		}
		Cache cache = cacheManager.getCache(cacheName);
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
	public void put(TranslateConfigModel cacheConfig, String cacheName, String cacheType,
			HashMap<String, Object[]> cacheValue) {
		if (cacheManager == null) {
			return;
		}
		synchronized (cacheName) {
			Cache cache = cacheManager.getCache(cacheName);
			// 缓存没有配置,自动创建缓存
			if (cache == null) {
				Caffeine caffeine = Caffeine.newBuilder()
						.maximumSize((cacheConfig.getHeap() < 1) ? 1000 : cacheConfig.getHeap());
				if (cacheConfig.getKeepAlive() > 0) {
					caffeine.expireAfterWrite(Duration.ofSeconds(cacheConfig.getKeepAlive()));
				}
				cacheManager.setCaffeine(caffeine);
				cacheManager.registerCustomCache(cacheName, caffeine.build());
			}
			// 清除缓存(一般不会执行,即缓存值被设置为null表示清除缓存)
			if (cacheValue == null) {
				if (StringUtil.isBlank(cacheType)) {
					cache.clear();
				} else {
					cache.evict(cacheType);
				}
			}
			// 更新缓存
			else {
				cache.put(StringUtil.isBlank(cacheType) ? cacheName : cacheType, cacheValue);
			}
		}
	}

	/**
	 * 这里cacheKey
	 */
	@Override
	public void clear(String cacheName, String cacheType) {
		if (cacheManager == null) {
			return;
		}
		synchronized (cacheName) {
			Cache cache = cacheManager.getCache(cacheName);
			// 缓存没有配置,自动创建缓存不建议使用
			if (cache != null) {
				if (StringUtil.isBlank(cacheType)) {
					cache.clear();
				} else {
					cache.evict(cacheType);
				}
			}
		}

	}

	@Override
	public boolean init() {
		if (cacheManager != null) {
			return true;
		}
		cacheManager = new CaffeineCacheManager();
		return true;
	}

	@Override
	public void destroy() {
		cacheManager = null;
	}

}
