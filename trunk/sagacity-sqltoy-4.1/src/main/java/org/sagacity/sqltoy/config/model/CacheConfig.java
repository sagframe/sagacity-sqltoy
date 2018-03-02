/**
 * 
 */
package org.sagacity.sqltoy.config.model;

import java.io.Serializable;

/**
 * @project sagacity-sqltoy4.1
 * @description 缓存的配置
 * @author chenrenfei <a href="mailto:zhongxuchen@gmail.com">联系作者</a>
 * @version id:CacheConfig.java,Revision:v1.0,Date:2018年1月5日
 */
public class CacheConfig implements Serializable {
	/**
	 * 
	 */
	private static final long serialVersionUID = -7649883073721463222L;

	/**
	 * 过期时长:默认60分钟
	 */
	private long expireSeconds = 3600;

	/**
	 * 是否写磁盘
	 */
	private boolean overflowToDisk = true;

	/**
	 * 内存中存放的数量
	 */
	private int maxElementsInMemory = 1000;

	/**
	 * 是否永久不过期
	 */
	private boolean eternal = false;

	/**
	 * 空闲时间
	 */
	private int timeToIdleSeconds = 3600;

	/**
	 * @return the expireSeconds
	 */
	public long getExpireSeconds() {
		return expireSeconds;
	}

	/**
	 * @param expireSeconds
	 *            the expireSeconds to set
	 */
	public void setExpireSeconds(long expireSeconds) {
		this.expireSeconds = expireSeconds;
	}

	/**
	 * @return the overflowToDisk
	 */
	public boolean isOverflowToDisk() {
		return overflowToDisk;
	}

	/**
	 * @param overflowToDisk
	 *            the overflowToDisk to set
	 */
	public void setOverflowToDisk(boolean overflowToDisk) {
		this.overflowToDisk = overflowToDisk;
	}

	/**
	 * @return the maxElementsInMemory
	 */
	public int getMaxElementsInMemory() {
		return maxElementsInMemory;
	}

	/**
	 * @param maxElementsInMemory
	 *            the maxElementsInMemory to set
	 */
	public void setMaxElementsInMemory(int maxElementsInMemory) {
		this.maxElementsInMemory = maxElementsInMemory;
	}

	/**
	 * @return the eternal
	 */
	public boolean isEternal() {
		return eternal;
	}

	/**
	 * @param eternal
	 *            the eternal to set
	 */
	public void setEternal(boolean eternal) {
		this.eternal = eternal;
	}

	/**
	 * @return the timeToIdleSeconds
	 */
	public int getTimeToIdleSeconds() {
		return timeToIdleSeconds;
	}

	/**
	 * @param timeToIdleSeconds
	 *            the timeToIdleSeconds to set
	 */
	public void setTimeToIdleSeconds(int timeToIdleSeconds) {
		this.timeToIdleSeconds = timeToIdleSeconds;
	}

}
