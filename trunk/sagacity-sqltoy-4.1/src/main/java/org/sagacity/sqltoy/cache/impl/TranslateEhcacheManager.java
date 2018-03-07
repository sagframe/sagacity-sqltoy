/**
 * 
 */
package org.sagacity.sqltoy.cache.impl;

import java.util.HashMap;

import org.sagacity.sqltoy.cache.TranslateCacheManager;
import org.sagacity.sqltoy.config.model.CacheConfig;
import org.sagacity.sqltoy.utils.StringUtil;

import net.sf.ehcache.Cache;
import net.sf.ehcache.CacheManager;
import net.sf.ehcache.Element;

/**
 * @project sagacity-sqltoy4.0
 * @description 基于ehcache缓存实现translate 提取缓存数据和存放缓存
 * @author zhongxu <a href="mailto:zhongxuchen@gmail.com">联系作者</a>
 * @version id:TranslateEhcacheManager.java,Revision:v1.0,Date:2013-4-14
 */
@SuppressWarnings("unchecked")
public class TranslateEhcacheManager extends TranslateCacheManager {
	private CacheManager cacheManager;

	/**
	 * @param cacheManager
	 *            the cacheManager to set
	 */
	public void setCacheManager(Object cacheManager) {
		this.cacheManager = (CacheManager) cacheManager;
	}

	@Override
	public HashMap<String, Object[]> getCache(String cacheName, String cacheKey) {
		Cache cache = cacheManager.getCache(cacheName);
		if (cache == null)
			return null;
		Element cacheValue = cache.get(StringUtil.isNotBlank(cacheKey) ? cacheKey : cacheName);
		if (cacheValue != null)
			return (HashMap<String, Object[]>) cacheValue.getObjectValue();
		return null;
	}

	@Override
	public void put(CacheConfig cacheConfig, String cacheName, String cacheKey, HashMap<String, Object[]> cacheValue) {
		Cache cache = cacheManager.getCache(cacheName);
		// 缓存没有配置,自动创建缓存不建议使用
		if (cache == null) {
			// 创建缓存,update 2012-8-30 from ture,true to true,false
			cache = new Cache(cacheName, cacheConfig.getMaxElementsInMemory(), cacheConfig.isOverflowToDisk(),
					cacheConfig.isEternal(), cacheConfig.getExpireSeconds(), cacheConfig.getTimeToIdleSeconds());
			cacheManager.addCache(cache);
		}
		// 放入缓存
		cache.put(new net.sf.ehcache.Element(StringUtil.isNotBlank(cacheKey) ? cacheKey : cacheName, cacheValue));
	}

	/**
	 * @return the cacheManager
	 */
	public CacheManager getCacheManager() {
		return this.cacheManager;
	}

}
