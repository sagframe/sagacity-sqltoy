/**
 * 
 */
package org.sagacity.sqltoy.config.model;

import java.io.Serializable;

/**
 * @project sqltoy-orm
 * @description 翻译配置模型
 * @author renfei.chen <a href="mailto:zhongxuchen@hotmail.com">联系作者</a>
 * @version Revision:v1.0,Date:2013-4-8
 * @Modification Date:2013-4-8 {填写修改说明}
 */
public class TranslateCacheModel implements Serializable {
	/**
	 * 
	 */
	private static final long serialVersionUID = 4392516051742079330L;

	/**
	 * 对应的cacheName
	 */
	private String cacheName;

	/**
	 * sql语句或sqltoy中的sqlId
	 */
	private String sql;

	/**
	 * 数据库源
	 */
	private String dataSource;

	/**
	 * 自定义的ServiceBean
	 */
	private String service;

	/**
	 * service method
	 */
	private String serviceMethod;

	/**
	 * 转换成hash 的key，只有针对sql语句起作用
	 */
	private int keyIndex = 0;

	/**
	 * 缓存管理器(如ehcache和redis的实现)
	 */
	private String translateCacheManager;

	/**
	 * 缓存配置
	 */
	private CacheConfig cacheConfig;

	/**
	 * @return the cacheName
	 */
	public String getCacheName() {
		return cacheName;
	}

	/**
	 * @param cacheName
	 *            the cacheName to set
	 */
	public void setCacheName(String cacheName) {
		this.cacheName = cacheName;
	}

	/**
	 * @return the sql
	 */
	public String getSql() {
		return sql;
	}

	/**
	 * @param sql
	 *            the sql to set
	 */
	public void setSql(String sql) {
		this.sql = sql;
	}

	/**
	 * @return the service
	 */
	public String getService() {
		return service;
	}

	/**
	 * @param service
	 *            the service to set
	 */
	public void setService(String service) {
		this.service = service;
	}

	/**
	 * @return the serviceMethod
	 */
	public String getServiceMethod() {
		return serviceMethod;
	}

	/**
	 * @param serviceMethod
	 *            the serviceMethod to set
	 */
	public void setServiceMethod(String serviceMethod) {
		this.serviceMethod = serviceMethod;
	}

	/**
	 * @return the keyIndex
	 */
	public int getKeyIndex() {
		return keyIndex;
	}

	/**
	 * @param keyIndex
	 *            the keyIndex to set
	 */
	public void setKeyIndex(int keyIndex) {
		this.keyIndex = keyIndex;
	}

	/**
	 * @return the dataSource
	 */
	public String getDataSource() {
		return dataSource;
	}

	/**
	 * @param dataSource
	 *            the dataSource to set
	 */
	public void setDataSource(String dataSource) {
		this.dataSource = dataSource;
	}

	/**
	 * @return the translateCacheManager
	 */
	public String getTranslateCacheManager() {
		return translateCacheManager;
	}

	/**
	 * @param translateCacheManager
	 *            the translateCacheManager to set
	 */
	public void setTranslateCacheManager(String translateCacheManager) {
		this.translateCacheManager = translateCacheManager;
	}

	/**
	 * @return the cacheConfig
	 */
	public CacheConfig getCacheConfig() {
		return cacheConfig;
	}

	/**
	 * @param cacheConfig the cacheConfig to set
	 */
	public void setCacheConfig(CacheConfig cacheConfig) {
		this.cacheConfig = cacheConfig;
	}
}
