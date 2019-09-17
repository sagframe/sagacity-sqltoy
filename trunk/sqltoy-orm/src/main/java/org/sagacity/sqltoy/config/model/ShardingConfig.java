/**
 * 
 */
package org.sagacity.sqltoy.config.model;

import java.io.Serializable;

/**
 * @project sagacity-sqltoy
 * @description 对象分库分表配置
 * @author chenrenfei <a href="mailto:zhongxuchen@gmail.com">联系作者</a>
 * @version id:ShardingModel.java,Revision:v1.0,Date:2017年9月12日
 */
public class ShardingConfig implements Serializable {

	/**
	 * 
	 */
	private static final long serialVersionUID = 3194225565492327232L;

	/**
	 * 分库策略
	 */
	private ShardingStrategyConfig shardingDBStrategy;

	/**
	 * 分表策略
	 */
	private ShardingStrategyConfig shardingTableStrategy;

	/**
	 * 并行执行最大等待时长
	 */
	private int maxWaitSeconds;

	/**
	 * 最大并行线程数量
	 */
	private int maxConcurrents;

	/**
	 * 数据库操作异常做全局异常回滚
	 */
	private boolean globalRollback = true;

	/**
	 * @return the shardingDBStrategy
	 */
	public ShardingStrategyConfig getShardingDBStrategy() {
		return shardingDBStrategy;
	}

	/**
	 * @param shardingDBStrategy
	 *            the shardingDBStrategy to set
	 */
	public void setShardingDBStrategy(ShardingStrategyConfig shardingDBStrategy) {
		this.shardingDBStrategy = shardingDBStrategy;
	}

	/**
	 * @return the shardingTableStrategy
	 */
	public ShardingStrategyConfig getShardingTableStrategy() {
		return shardingTableStrategy;
	}

	/**
	 * @param shardingTableStrategy
	 *            the shardingTableStrategy to set
	 */
	public void setShardingTableStrategy(ShardingStrategyConfig shardingTableStrategy) {
		this.shardingTableStrategy = shardingTableStrategy;
	}

	/**
	 * @return the maxWaitSeconds
	 */
	public int getMaxWaitSeconds() {
		return maxWaitSeconds;
	}

	/**
	 * @param maxWaitSeconds
	 *            the maxWaitSeconds to set
	 */
	public void setMaxWaitSeconds(int maxWaitSeconds) {
		this.maxWaitSeconds = maxWaitSeconds;
	}

	/**
	 * @return the maxConcurrents
	 */
	public int getMaxConcurrents() {
		return maxConcurrents;
	}

	/**
	 * @param maxConcurrents
	 *            the maxConcurrents to set
	 */
	public void setMaxConcurrents(int maxConcurrents) {
		this.maxConcurrents = maxConcurrents;
	}

	/**
	 * @return the globalRollback
	 */
	public boolean isGlobalRollback() {
		return globalRollback;
	}

	/**
	 * @param globalRollback the globalRollback to set
	 */
	public void setGlobalRollback(boolean globalRollback) {
		this.globalRollback = globalRollback;
	}

}
