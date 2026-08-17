package org.sagacity.sqltoy.plugins.sharding.impl;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.HashMap;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.sagacity.sqltoy.config.model.ShardingDBModel;
import org.sagacity.sqltoy.model.IgnoreCaseLinkedMap;

/**
 * 回归测试：分片值为负hash时仍必须路由到有效分表/数据源，而不是返回null静默回落基准表
 */
public class HashShardingStrategyTest {
	private HashShardingStrategy strategy = new HashShardingStrategy();

	private IgnoreCaseLinkedMap<String, Object> params(String key, Object value) {
		IgnoreCaseLinkedMap<String, Object> paramsMap = new IgnoreCaseLinkedMap<String, Object>();
		paramsMap.put(key, value);
		return paramsMap;
	}

	@BeforeEach
	public void setUp() {
		HashMap<String, String> tableMap = new HashMap<String, String>();
		for (int i = 0; i < 4; i++) {
			tableMap.put(String.valueOf(i), "t_" + i);
		}
		strategy.setTableMap(tableMap);
		HashMap<String, String> dbMap = new HashMap<String, String>();
		for (int i = 0; i < 3; i++) {
			dbMap.put(String.valueOf(i), "ds" + i);
		}
		strategy.setDataSourceMap(dbMap);
		strategy.initialize();
	}

	@Test
	public void positiveHashRoutesNormally() {
		assertEquals("t_2", strategy.getShardingTable(null, null, "t", "id", params("id", Integer.valueOf(6))));
		ShardingDBModel dbModel = strategy.getShardingDB(null, null, "t", "id", params("id", Integer.valueOf(7)));
		assertEquals("ds1", dbModel.getDataSourceName());
	}

	@Test
	public void negativeHashStillRoutesToValidShard() {
		Object shardingValue = Integer.valueOf(-5);
		assertTrue(shardingValue.hashCode() < 0);
		// floorMod(-5,4)=3;原代码-5%4=-1,取key:"-1"不存在返回null,数据静默回落基准表
		String table = strategy.getShardingTable(null, null, "t", "id", params("id", shardingValue));
		assertNotNull(table);
		assertEquals("t_3", table);
		// floorMod(-5,3)=1
		ShardingDBModel dbModel = strategy.getShardingDB(null, null, "t", "id", params("id", shardingValue));
		assertNotNull(dbModel.getDataSourceName());
		assertEquals("ds1", dbModel.getDataSourceName());
	}

	@Test
	public void emptyParamsMeansNoSharding() {
		assertNull(strategy.getShardingTable(null, null, "t", "id", new IgnoreCaseLinkedMap<String, Object>()));
		ShardingDBModel dbModel = strategy.getShardingDB(null, null, "t", "id",
				new IgnoreCaseLinkedMap<String, Object>());
		assertNull(dbModel.getDataSourceName());
	}
}
