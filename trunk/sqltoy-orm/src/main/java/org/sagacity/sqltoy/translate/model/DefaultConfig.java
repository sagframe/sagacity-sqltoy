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
	 * 集群的节点时间差异(秒)，默认偏离1秒,xml配置时无需设置为负数,这里是解析后的结果
	 */
	private int deviationSeconds = -1;

	/**
	 * 内存中存放的数量(条)
	 */
	private int defaultHeap = 10000;

	/**
	 * 单位MB
	 */
	private int defaultOffHeap = 0;

	/**
	 * 单位MB
	 */
	private int defaultDiskSize = 0;

	/**
	 * 默认缓存有效时长(秒)
	 */
	private int defaultKeepAlive = 3600;

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
	public int getDefaultHeap() {
		return defaultHeap;
	}

	/**
	 * @param defaultHeap
	 *            the defaultHeap to set
	 */
	public void setDefaultHeap(int defaultHeap) {
		this.defaultHeap = defaultHeap;
	}

	/**
	 * @return the defaultOffHeap
	 */
	public int getDefaultOffHeap() {
		return defaultOffHeap;
	}

	/**
	 * @param defaultOffHeap
	 *            the defaultOffHeap to set
	 */
	public void setDefaultOffHeap(int defaultOffHeap) {
		this.defaultOffHeap = defaultOffHeap;
	}

	/**
	 * @return the dafaultDiskSize
	 */
	public int getDefaultDiskSize() {
		return defaultDiskSize;
	}

	/**
	 * @param dafaultDiskSize
	 *            the dafaultDiskSize to set
	 */
	public void setDefaultDiskSize(int defaultDiskSize) {
		this.defaultDiskSize = defaultDiskSize;
	}

	/**
	 * @return the defaultKeepAlive
	 */
	public int getDefaultKeepAlive() {
		return defaultKeepAlive;
	}

	/**
	 * @param defaultKeepAlive
	 *            the defaultKeepAlive to set
	 */
	public void setDefaultKeepAlive(int defaultKeepAlive) {
		this.defaultKeepAlive = defaultKeepAlive;
	}

}
