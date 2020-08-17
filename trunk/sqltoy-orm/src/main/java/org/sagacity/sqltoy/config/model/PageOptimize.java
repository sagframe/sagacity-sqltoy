/**
 * 
 */
package org.sagacity.sqltoy.config.model;

import java.io.Serializable;

/**
 * @project sagacity-sqltoy
 * @description 分页优化配置
 * @author zhongxuchen@gmail.com
 * @version v1.0, Date:2020-8-4
 * @modify 2020-8-4,修改说明
 */
public class PageOptimize implements Serializable {

	/**
	 * 
	 */
	private static final long serialVersionUID = -4202934471963179375L;

	/**
	 * 1000个不同条件查询
	 */
	private int aliveMax = 1000;

	/**
	 * 1.5分钟
	 */
	private int aliveSeconds = 90;

	/**
	 * @return the aliveMax
	 */
	public int getAliveMax() {
		return aliveMax;
	}

	/**
	 * @param aliveMax the aliveMax to set
	 */
	public PageOptimize aliveMax(int aliveMax) {
		// 最大不超过10000
		if (aliveMax > 10000) {
			this.aliveMax = 10000;
		}
		// 最小100
		else if (aliveMax < 100) {
			this.aliveMax = 100;
		} else {
			this.aliveMax = aliveMax;
		}
		return this;
	}

	/**
	 * @return the aliveSeconds
	 */
	public int getAliveSeconds() {
		return aliveSeconds;
	}

	/**
	 * @param aliveSeconds the aliveSeconds to set
	 */
	public PageOptimize aliveSeconds(int aliveSeconds) {
		// 最小保持30秒
		if (aliveSeconds < 30) {
			this.aliveSeconds = 30;
		}
		// 不超过24小时
		else if (aliveSeconds > 3600 * 24) {
			this.aliveSeconds = 1800;
		} else {
			this.aliveSeconds = aliveSeconds;
		}
		return this;
	}

}
