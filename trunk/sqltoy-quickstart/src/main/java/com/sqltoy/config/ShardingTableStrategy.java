/**
 * 
 */
package com.sqltoy.config;

import java.util.HashMap;

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
		DefaultShardingStrategy strategy = new DefaultShardingStrategy();
		//
		strategy.setDays("14");
		strategy.setDateParams("createTime,beginDate,bizDate,beginTime,bizTime");
		HashMap<String, String> tableMap = new HashMap<String, String>();
		// 写sql时可以直接用15d的表
		tableMap.put("SQLTOY_TRANS_INFO_15D", "SQLTOY_TRANS_INFO_HIS");
		strategy.setTableNamesMap(tableMap);
		return strategy;
	}
}
