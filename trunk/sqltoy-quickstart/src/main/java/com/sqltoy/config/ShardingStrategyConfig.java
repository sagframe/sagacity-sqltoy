/**
 * 
 */
package com.sqltoy.config;

import java.util.HashMap;

import org.sagacity.sqltoy.plugins.sharding.ShardingStrategy;
import org.sagacity.sqltoy.plugins.sharding.impl.DefaultShardingStrategy;
import org.sagacity.sqltoy.plugins.sharding.impl.HashShardingStrategy;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * @project sqltoy-quickstart
 * @description 演示一个分表的策略(分库的类似)
 * @author zhongxuchen
 * @version v1.0, Date:2020-7-22
 * @modify 2020-7-22,修改说明
 */
@Configuration
public class ShardingStrategyConfig {

	/**
	 * @TODO 演示实时表和历史表分表效果
	 * @return
	 */
	@Bean("realHisTable")
	public ShardingStrategy realHisTable() {
		DefaultShardingStrategy strategy = new DefaultShardingStrategy();
		// 分14天表和历史表,也可以1,15,90 表示多个间隔
		strategy.setDays("14");
		strategy.setDateParams("createTime,beginDate,bizDate,beginTime,bizTime");
		HashMap<String, String> tableMap = new HashMap<String, String>();
		// 写sql时可以直接用15d的表
		// value可用逗号分隔,跟days对应 ,SQLTOY_TRANS_INFO_90,SQLTOY_TRANS_INFO_HIS
		tableMap.put("SQLTOY_TRANS_INFO_15D", "SQLTOY_TRANS_INFO_HIS");
		strategy.setTableNamesMap(tableMap);
		return strategy;
	}

	/**
	 * @TODO 按权重进行分库
	 * @return
	 */
	@Bean("weightDataSource")
	public ShardingStrategy weightDataSource() {
		DefaultShardingStrategy strategy = new DefaultShardingStrategy();

		return strategy;
	}

	/**
	 * @TODO 通过hash取模方式分库
	 * @return
	 */
	@Bean("hashDataSource")
	public ShardingStrategy hashDataSource() {
		HashShardingStrategy strategy = new HashShardingStrategy();

		return strategy;
	}
}
