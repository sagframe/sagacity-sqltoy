/**
 * 
 */
package org.sagacity.sqltoy.config.model;

import java.io.Serializable;

/**
 * @project sagacity-sqltoy
 * @description 封装当前毫秒下的计数
 * @author zhongxuchen
 * @version v1.0,Date:2022-6-23
 */
public class CurrentTimeMaxValue implements Serializable {

	/**
	 * 
	 */
	private static final long serialVersionUID = -7476828694163555224L;

	/**
	 * 当前毫秒时间
	 */
	private long currentTime;

	/**
	 * 当前计数值
	 */
	private long value = 1;

	public CurrentTimeMaxValue(long currentTime, long value) {
		this.currentTime = currentTime;
		this.value = value;
	}

	public long getCurrentTime() {
		return currentTime;
	}

	public void setCurrentTime(long currentTime) {
		this.currentTime = currentTime;
	}

	public long getValue() {
		return value;
	}

	public void setValue(long value) {
		this.value = value;
	}

}
