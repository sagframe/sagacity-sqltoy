/**
 * 
 */
package org.sagacity.sqltoy.plugins.sharding.impl;

import java.util.HashMap;

import org.sagacity.sqltoy.SqlToyContext;
import org.sagacity.sqltoy.model.IgnoreCaseLinkedMap;
import org.sagacity.sqltoy.model.ShardingDBModel;
import org.sagacity.sqltoy.plugins.sharding.ShardingStrategy;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * @project sagacity-sqltoy
 * @description hash取模形式的分库策略
 * @author zhongxuchen
 * @version v1.0,Date:2017年11月1日
 */
public class HashShardingStrategy implements ShardingStrategy {
	private final static Logger logger = LoggerFactory.getLogger(HashShardingStrategy.class);
	private HashMap<String, String> dataSourceMap = new HashMap<String, String>();

	private HashMap<String, String> tableMap = new HashMap<String, String>();

	/**
	 * db取模数值
	 */
	private int dataSourceMode;

	/**
	 * table取模数组
	 */
	private int tableMode;

	/*
	 * (non-Javadoc)
	 * 
	 * @see
	 * org.sagacity.sqltoy.plugins.sharding.ShardingStrategy#getShardingTable(org.
	 * sagacity. sqltoy.SqlToyContext, java.lang.Class, java.lang.String,
	 * java.lang.String, java.util.HashMap)
	 */
	@Override
	public String getShardingTable(SqlToyContext sqlToyContext, Class entityClass, String baseTableName,
			String decisionType, IgnoreCaseLinkedMap<String, Object> paramsMap) {
		if (tableMode < 1 || paramsMap == null || paramsMap.isEmpty()) {
			return null;
		}
		// 单值hash取模
		Object shardingValue = paramsMap.values().iterator().next();
		int hashCode = shardingValue.hashCode();
		String modeKey = Integer.toString(hashCode % tableMode);
		logger.debug("分表取得modeKey:{},tableName:{}", modeKey, tableMap.get(modeKey));
		return tableMap.get(modeKey);
	}

	/*
	 * (non-Javadoc)
	 * 
	 * @see
	 * org.sagacity.sqltoy.plugins.sharding.ShardingStrategy#getShardingModel(org.
	 * sagacity. sqltoy.SqlToyContext, java.lang.String, java.lang.String,
	 * java.util.HashMap)
	 */
	@Override
	public ShardingDBModel getShardingDB(SqlToyContext sqlToyContext, Class entityClass, String tableOrSql,
			String strategyVar, IgnoreCaseLinkedMap<String, Object> paramsMap) {
		ShardingDBModel shardingModel = new ShardingDBModel();
		if (dataSourceMode < 1 || paramsMap == null || paramsMap.isEmpty()) {
			return shardingModel;
		}
		// 单值hash取模
		Object shardingValue = paramsMap.values().iterator().next();
		int hashCode = shardingValue.hashCode();
		String modeKey = Integer.toString(hashCode % dataSourceMode);
		shardingModel.setDataSourceName(dataSourceMap.get(modeKey));
		logger.debug("分库取得modeKey:{},dataSourceName:{}", modeKey, shardingModel.getDataSourceName());
		return shardingModel;
	}

	/*
	 * (non-Javadoc)
	 * 
	 * @see org.sagacity.sqltoy.plugins.sharding.ShardingStrategy#initialize()
	 */
	@Override
	public void initialize() {
		if (dataSourceMode == 0) {
			dataSourceMode = dataSourceMap.size();
		}
		if (tableMode == 0) {
			tableMode = tableMap.size();
		}
	}

	/**
	 * @return the dataSourceMap
	 */
	public HashMap<String, String> getDataSourceMap() {
		return dataSourceMap;
	}

	/**
	 * @param dataSourceMap the dataSourceMap to set
	 */
	public void setDataSourceMap(HashMap<String, String> dataSourceMap) {
		this.dataSourceMap = dataSourceMap;
	}

	/**
	 * @return the tableMap
	 */
	public HashMap<String, String> getTableMap() {
		return tableMap;
	}

	/**
	 * @param tableMap the tableMap to set
	 */
	public void setTableMap(HashMap<String, String> tableMap) {
		this.tableMap = tableMap;
	}

}
