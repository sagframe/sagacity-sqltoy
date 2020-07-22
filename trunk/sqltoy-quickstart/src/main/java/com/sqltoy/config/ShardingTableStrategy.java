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
		// 分14天表和历史表,也可以1,15,90 表示多个间隔
		strategy.setDays("14");
		strategy.setDateParams("createTime,beginDate,bizDate,beginTime,bizTime");
		HashMap<String, String> tableMap = new HashMap<String, String>();
		// 写sql时可以直接用15d的表
		//value可用逗号分隔,跟days对应 ,SQLTOY_TRANS_INFO_90,SQLTOY_TRANS_INFO_HIS
		tableMap.put("SQLTOY_TRANS_INFO_15D", "SQLTOY_TRANS_INFO_HIS");
		strategy.setTableNamesMap(tableMap);
		return strategy;
	}
}
