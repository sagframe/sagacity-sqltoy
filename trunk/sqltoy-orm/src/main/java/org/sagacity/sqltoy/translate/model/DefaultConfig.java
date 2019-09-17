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
	private String diskStorePath = "./translateCaches";

	/**
	 * @return the diskStorePath
	 */
	public String getDiskStorePath() {
		return diskStorePath;
	}

	/**
	 * @param diskStorePath the diskStorePath to set
	 */
	public void setDiskStorePath(String diskStorePath) {
		this.diskStorePath = diskStorePath;
	}
	
	
}
