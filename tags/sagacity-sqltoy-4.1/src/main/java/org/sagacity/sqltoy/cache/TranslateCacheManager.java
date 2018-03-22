/**
 * 
 */
package org.sagacity.sqltoy.cache;

import java.util.HashMap;

import org.sagacity.sqltoy.config.model.CacheConfig;

/**
 * @project sagacity-sqltoy4.0
 * @description translate 翻译缓存管理接口定义，为基于其他缓存框架的实现提供接口规范
 * @author chenrenfei <a href="mailto:zhongxuchen@gmail.com">联系作者</a>
 * @version id:TranslateCacheManager.java,Revision:v1.0,Date:2013-4-14
 */
public abstract class TranslateCacheManager {
	private String name;

	/**
	 * @return the name
	 */
	public String getName() {
		return name;
	}

	/**
	 * @param name
	 *            the name to set
	 */
	public void setName(String name) {
		this.name = name;
	}

	/**
	 * @todo 从缓存中获取翻译的hashMap 集合数据
	 * @param cacheName
	 * @param elementId
	 * @return
	 */
	public abstract HashMap<String, Object[]> getCache(String cacheName, String elementId);

	/**
	 * @todo 将数据放入缓存
	 * @param cacheConfig
	 * @param cacheName
	 * @param elementId
	 * @param cacheValue
	 */
	public abstract void put(CacheConfig cacheConfig, String cacheName, String elementId,
			HashMap<String, Object[]> cacheValue);

	/**
	 * @todo 设置缓存管理器
	 * @param cacheManager
	 */
	public abstract void setCacheManager(Object cacheManager);

	/**
	 * @todo 获取缓存管理器
	 * @return
	 */
	public abstract Object getCacheManager();
}
