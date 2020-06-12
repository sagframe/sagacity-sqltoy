/**
 * 
 */
package org.sagacity.sqltoy.plugins.sharding.impl;

import java.util.HashMap;

import org.sagacity.sqltoy.SqlToyContext;
import org.sagacity.sqltoy.model.IgnoreCaseLinkedMap;
import org.sagacity.sqltoy.model.ShardingDBModel;
import org.sagacity.sqltoy.plugins.sharding.ShardingStrategy;

/**
 * @project sagacity-sqltoy4.0
 * @description hash取模形式的分库策略
 * @author chenrenfei <a href="mailto:zhongxuchen@gmail.com">联系作者</a>
 * @version id:HashShardingStrategy.java,Revision:v1.0,Date:2017年11月1日
 */
public class HashShardingStrategy implements ShardingStrategy {

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
	 * org.sagacity.sqltoy.plugin.ShardingStrategy#getShardingTable(org.sagacity.
	 * sqltoy.SqlToyContext, java.lang.Class, java.lang.String, java.lang.String,
	 * java.util.HashMap)
	 */
	@Override
	public String getShardingTable(SqlToyContext sqlToyContext, Class entityClass, String baseTableName,
			String decisionType, IgnoreCaseLinkedMap<String, Object> paramsMap) {
		if (tableMode < 1 || paramsMap == null || paramsMap.isEmpty())
			return null;
		// 单值hash取模
		Object shardingValue = paramsMap.values().iterator().next();
		int hashCode = shardingValue.hashCode();
		return tableMap.get(Integer.toString(hashCode % tableMode));
	}

	/*
	 * (non-Javadoc)
	 * 
	 * @see
	 * org.sagacity.sqltoy.plugin.ShardingStrategy#getShardingModel(org.sagacity.
	 * sqltoy.SqlToyContext, java.lang.String, java.lang.String, java.util.HashMap)
	 */
	@Override
	public ShardingDBModel getShardingDB(SqlToyContext sqlToyContext, Class entityClass, String tableOrSql,
			String strategyVar, IgnoreCaseLinkedMap<String, Object> paramsMap) {
		ShardingDBModel shardingModel = new ShardingDBModel();
		if (dataSourceMode < 1 || paramsMap == null || paramsMap.isEmpty())
			return shardingModel;
		// 单值hash取模
		Object shardingValue = paramsMap.values().iterator().next();
		int hashCode = shardingValue.hashCode();
		shardingModel.setDataSourceName(dataSourceMap.get(Integer.toString(hashCode % dataSourceMode)));
		return shardingModel;
	}

	/*
	 * (non-Javadoc)
	 * 
	 * @see org.sagacity.sqltoy.plugin.ShardingStrategy#initialize()
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
