/**
 * 
 */
package com.sqltoy.config;

import org.sagacity.sqltoy.plugins.sharding.ShardingStrategy;
import org.sagacity.sqltoy.plugins.sharding.impl.DefaultShardingStrategy;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * @project sqltoy-quickstart
 * @description 请在此说明类的功能
 * @author zhongxuchen
 * @version v1.0, Date:2020-7-22
 * @modify 2020-7-22,修改说明
 */
@Configuration
public class ShardingTableStrategy {

	@Bean("historyTableStrategy")
	public ShardingStrategy historyTableStrategy() {
		ShardingStrategy strategy = new DefaultShardingStrategy();
		return strategy;
	}
}
