/**
 * 
 */
package org.sagacity.sqltoy.config.model;

import java.io.Serializable;

/**
 * @project sagacity-sqltoy
 * @description 分页优化配置
 * @author zhongxuchen
 * @version v1.0, Date:2020-8-4
 * @modify 2020-8-4,修改说明
 */
public class PageOptimize implements Serializable {

	/**
	 * 
	 */
	private static final long serialVersionUID = -4202934471963179375L;

	/**
	 * 开启并行查询
	 */
	private boolean parallel = false;

	/**
	 * 1000个不同条件查询
	 */
	private int aliveMax = 1000;

	/**
	 * 1.5分钟
	 */
	private int aliveSeconds = 90;

	/**
	 * 默认值为1800秒
	 */
	private long parallelMaxWaitSeconds = 1800;

	/**
	 * 是否跳过count为0的缓存
	 */
	private boolean skipZeroCount = false;

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

	public boolean isParallel() {
		return parallel;
	}

	/**
	 * @TODO 设置并行，即同时查询count和单页记录
	 * @param parallel
	 * @return
	 */
	public PageOptimize parallel(boolean parallel) {
		this.parallel = parallel;
		return this;
	}

	public PageOptimize skipZeroCount(boolean skipZeroCount) {
		this.skipZeroCount = skipZeroCount;
		return this;
	}

	/**
	 * @return the skipZeroCount
	 */
	public boolean isSkipZeroCount() {
		return skipZeroCount;
	}

	/**
	 * @param aliveSeconds the aliveSeconds to set
	 */
	public PageOptimize aliveSeconds(int aliveSeconds) {
		// 最小保持30秒(小于等于1表示关闭缓存分页优化)
		if (aliveSeconds < 30 && aliveSeconds > 0) {
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

	public long getParallelMaxWaitSeconds() {
		return parallelMaxWaitSeconds;
	}

	/**
	 * @TODO 设置并行最大等待时长(可以不用设置)
	 * @param parallelMaxWaitSeconds
	 * @return
	 */
	public PageOptimize parallelMaxWaitSeconds(long parallelMaxWaitSeconds) {
		this.parallelMaxWaitSeconds = parallelMaxWaitSeconds;
		return this;
	}

}
