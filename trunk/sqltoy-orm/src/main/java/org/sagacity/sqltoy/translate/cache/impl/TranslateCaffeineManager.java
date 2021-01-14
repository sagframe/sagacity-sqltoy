/**
 * 
 */
package org.sagacity.sqltoy.translate.cache.impl;

import java.util.HashMap;

import org.sagacity.sqltoy.translate.cache.TranslateCacheManager;
import org.sagacity.sqltoy.translate.model.TranslateConfigModel;

/**
 * @project sagacity-sqltoy
 * @description 提供基于Caffeine缓存实现
 * @author zhongxuchen
 * @version v1.0, Date:2021-1-14
 * @modify 2021-1-14,修改说明
 */
public class TranslateCaffeineManager extends TranslateCacheManager {

	@Override
	public HashMap<String, Object[]> getCache(String cacheName, String cacheType) {
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
		return false;
	}

	@Override
	public void destroy() {

	}

}
