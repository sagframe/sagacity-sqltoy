/**
 * 
 */
package org.sagacity.sqltoy.translate.cache.impl;

import java.util.HashMap;

import org.sagacity.sqltoy.translate.cache.TranslateCacheManager;
import org.sagacity.sqltoy.translate.model.TranslateConfigModel;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

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
	// CaffeineCacheManager cacheManager = new CaffeineCacheManager();

	// protected static Cache<String, DataObject> cache;

	@Override
	public HashMap<String, Object[]> getCache(String cacheName, String cacheType) {
		// Caffeine.from(cacheName)
		return null;
	}

	@Override
	public void put(TranslateConfigModel cacheModel, String cacheName, String cacheType,
			HashMap<String, Object[]> cacheValue) {

	}

	@Override
	public void clear(String cacheName, String cacheType) {

	}

	@Override
	public boolean init() {
//		if (cacheManager != null) {
//			return true;
//		}
//		cacheManager = new CaffeineCacheManager();
		return false;
	}

	@Override
	public void destroy() {

	}

}
