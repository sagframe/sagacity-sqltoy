/**
 * 
 */
package org.sagacity.sqltoy.cache.impl;

import java.util.HashMap;
import java.util.concurrent.TimeUnit;

import org.sagacity.sqltoy.cache.TranslateCacheManager;
import org.sagacity.sqltoy.config.model.CacheConfig;
import org.sagacity.sqltoy.utils.StringUtil;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.data.redis.core.RedisTemplate;

/**
 * @project sagacity-sqltoy4.1
 * @description 基于redis的缓存翻译管理实现
 * @author chenrenfei <a href="mailto:zhongxuchen@gmail.com">联系作者</a>
 * @version id:TranslateRedisManager.java,Revision:v1.0,Date:2018年1月3日
 */
@SuppressWarnings({ "rawtypes", "unchecked" })
public class TranslateRedisManager extends TranslateCacheManager {
	/**
	 * 基于spring-data的redis管理器
	 */
	private Object cacheManager;

	/**
	 * spring redisTemplate
	 */
	private RedisTemplate redisTemplate;

	/**
	 * 是否复合cacheKey
	 */
	private boolean unionKey = true;

	/**
	 * 复合key的链接字符
	 */
	private String unionSign = "_";

	/*
	 * (non-Javadoc)
	 * 
	 * @see
	 * org.sagacity.sqltoy.cache.TranslateCacheManager#getCache(java.lang.String,
	 * java.lang.String)
	 */
	@Override
	public HashMap<String, Object[]> getCache(String cacheName, String cacheKey) {
		if (StringUtil.isBlank(cacheKey))
			return (HashMap<String, Object[]>) redisTemplate.opsForValue().get(cacheName);
		else {
			if (unionKey)
				return (HashMap<String, Object[]>) redisTemplate.opsForValue()
						.get(cacheName.concat(unionSign).concat(cacheKey));
			else
				return (HashMap<String, Object[]>) redisTemplate.opsForHash().get(cacheName, cacheKey);
		}
	}

	/*
	 * (non-Javadoc)
	 * 
	 * @see org.sagacity.sqltoy.cache.TranslateCacheManager#put(java.lang.String,
	 * java.lang.String, java.util.HashMap)
	 */
	@Override
	public void put(CacheConfig cacheConfig, String cacheName, String cacheKey, HashMap<String, Object[]> cacheValue) {
		// 单key模式
		if (StringUtil.isBlank(cacheKey)) {
			redisTemplate.opsForValue().set(cacheName, cacheValue, cacheConfig.getExpireSeconds(), TimeUnit.SECONDS);
		} else {
			// 组合key
			if (unionKey) {
				redisTemplate.opsForValue().set(cacheName.concat(unionSign).concat(cacheKey), cacheValue,
						cacheConfig.getExpireSeconds(), TimeUnit.SECONDS);
			} else {
				// 判断是否存在
				if (!redisTemplate.hasKey(cacheName)) {
					redisTemplate.opsForHash().put(cacheName, cacheKey, cacheValue);
					redisTemplate.expire(cacheName, cacheConfig.getExpireSeconds(), TimeUnit.SECONDS);
				} else
					redisTemplate.opsForHash().put(cacheName, cacheKey, cacheValue);
			}
		}
	}

	/*
	 * (non-Javadoc)
	 * 
	 * @see org.sagacity.sqltoy.cache.TranslateCacheManager#getCacheManager()
	 */
	@Override
	public Object getCacheManager() {
		return this.cacheManager;
	}

	/**
	 * @param redisTemplate
	 *            the redisTemplate to set
	 */
	@Autowired(required = false)
	@Qualifier(value = "redisTemplate")
	public void setRedisTemplate(RedisTemplate redisTemplate) {
		this.redisTemplate = redisTemplate;
	}

	/**
	 * @param cacheManager
	 *            the cacheManager to set
	 */
	@Override
	public void setCacheManager(Object cacheManager) {
		this.cacheManager = cacheManager;
	}

	/**
	 * @return the unionKey
	 */
	public boolean isUnionKey() {
		return unionKey;
	}

	/**
	 * @param unionKey
	 *            the unionKey to set
	 */
	public void setUnionKey(boolean unionKey) {
		this.unionKey = unionKey;
	}

	/**
	 * @param unionSign
	 *            the unionSign to set
	 */
	public void setUnionSign(String unionSign) {
		this.unionSign = unionSign;
	}

}
