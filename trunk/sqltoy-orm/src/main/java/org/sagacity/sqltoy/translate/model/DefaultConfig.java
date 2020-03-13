/**
 * 
 */
package org.sagacity.sqltoy.translate.model;

import java.io.Serializable;

/**
 * @project sagacity-sqltoy4.2
 * @description 默认本地缓存配置参数
 * @author chenrenfei <a href="mailto:zhongxuchen@gmail.com">联系作者</a>
 * @version id:DefaultConfig.java,Revision:v1.0,Date:2018年3月11日
 */
public class DefaultConfig implements Serializable {

	/**
	 * 
	 */
	private static final long serialVersionUID = 7793069400942116219L;

	/**
	 * 默认缓存超限后存放磁盘路径
	 */
	private String diskStorePath;

	/**
	 * 集群的节点时间差异(秒)，默认偏离1秒
	 */
	private int deviationSeconds = -1;

	/**
	 * 内存中存放的数量(条)
	 */
	private Integer defaultHeap = 10000;

	/**
	 * 单位MB
	 */
	private Integer defaultOffHeap = 0;

	/**
	 * 单位MB
	 */
	private Integer defaultDiskSize = 0;

	/**
	 * 默认缓存有效时长(秒)
	 */
	private Integer defaultKeepAlive = 3600;

	/**
	 * @return the diskStorePath
	 */
	public String getDiskStorePath() {
		return diskStorePath;
	}

	/**
	 * @param diskStorePath
	 *            the diskStorePath to set
	 */
	public void setDiskStorePath(String diskStorePath) {
		this.diskStorePath = diskStorePath;
	}

	public int getDeviationSeconds() {
		return deviationSeconds;
	}

	public void setDeviationSeconds(int deviationSeconds) {
		this.deviationSeconds = deviationSeconds;
	}

	/**
	 * @return the defaultHeap
	 */
	public Integer getDefaultHeap() {
		return defaultHeap;
	}

	/**
	 * @param defaultHeap
	 *            the defaultHeap to set
	 */
	public void setDefaultHeap(Integer defaultHeap) {
		this.defaultHeap = defaultHeap;
	}

	/**
	 * @return the defaultOffHeap
	 */
	public Integer getDefaultOffHeap() {
		return defaultOffHeap;
	}

	/**
	 * @param defaultOffHeap
	 *            the defaultOffHeap to set
	 */
	public void setDefaultOffHeap(Integer defaultOffHeap) {
		this.defaultOffHeap = defaultOffHeap;
	}

	/**
	 * @return the dafaultDiskSize
	 */
	public Integer getDefaultDiskSize() {
		return defaultDiskSize;
	}

	/**
	 * @param dafaultDiskSize
	 *            the dafaultDiskSize to set
	 */
	public void setDefaultDiskSize(Integer defaultDiskSize) {
		this.defaultDiskSize = defaultDiskSize;
	}

	/**
	 * @return the defaultKeepAlive
	 */
	public Integer getDefaultKeepAlive() {
		return defaultKeepAlive;
	}

	/**
	 * @param defaultKeepAlive
	 *            the defaultKeepAlive to set
	 */
	public void setDefaultKeepAlive(Integer defaultKeepAlive) {
		this.defaultKeepAlive = defaultKeepAlive;
	}

}
