/**
 * 
 */
package org.sagacity.sqltoy.translate.model;

import java.io.Serializable;

/**
 * @project sagacity-sqltoy4.2
 * @description 缓存刷新结果模型
 * @author chenrenfei <a href="mailto:zhongxuchen@gmail.com">联系作者</a>
 * @version id:CacheCheckResult.java,Revision:v1.0,Date:2018年3月8日
 */
public class CacheCheckResult implements Serializable {

	/**
	 * 
	 */
	private static final long serialVersionUID = 5068540349332451092L;

	/**
	 * 缓存名称
	 */
	private String cacheName;

	/**
	 * 类别
	 */
	private String cacheType;

	/**
	 * 缓存内容
	 */
	private Object[] item;

	/**
	 * @return the cacheName
	 */
	public String getCacheName() {
		return cacheName;
	}

	/**
	 * @param cacheName the cacheName to set
	 */
	public void setCacheName(String cacheName) {
		this.cacheName = cacheName;
	}

	/**
	 * @return the cacheType
	 */
	public String getCacheType() {
		return cacheType;
	}

	/**
	 * @param cacheType the cacheType to set
	 */
	public void setCacheType(String cacheType) {
		this.cacheType = cacheType;
	}

	public Object[] getItem() {
		return item;
	}

	public void setItem(Object[] item) {
		this.item = item;
	}

}
