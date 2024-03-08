/**
 * 
 */
package org.sagacity.sqltoy.config.model;

import java.io.Serializable;

/**
 * @project sagacity-sqltoy
 * @description 分库分表策略配置
 * @author zhongxuchen
 * @version v1.0,Date:2017年11月5日
 */
public class ShardingStrategyConfig implements Serializable {

	/**
	 * 
	 */
	private static final long serialVersionUID = -1424263873061119413L;

	public ShardingStrategyConfig(int type) {
		this.type = type;
	}

	/**
	 * 是分库还是分表:0、分库；1、分表 目前属于冗余属性
	 */
	private int type = 0;

	/**
	 * 表名
	 */
	private String[] tables;

	/**
	 * 策略名称
	 */
	private String strategy;

	/**
	 * 字段
	 */
	private String[] fields;

	/**
	 * 字段的别名
	 */
	private String[] aliasNames;

	/**
	 * 决策类别，扩展预留属性，方便一个策略中提供多种sharding策略，从而可以选择指定策略
	 */
	private String decisionType;

	/**
	 * @return the fields
	 */
	public String[] getFields() {
		return fields;
	}

	/**
	 * @param fields the fields to set
	 */
	public void setFields(String[] fields) {
		this.fields = fields;
	}

	/**
	 * @return the aliasNames
	 */
	public String[] getAliasNames() {
		return aliasNames;
	}

	/**
	 * @param aliasNames the aliasNames to set
	 */
	public void setAliasNames(String[] aliasNames) {
		this.aliasNames = aliasNames;
	}

	/**
	 * @return the decisionType
	 */
	public String getDecisionType() {
		return decisionType;
	}

	/**
	 * @param decisionType the decisionType to set
	 */
	public void setDecisionType(String decisionType) {
		this.decisionType = decisionType;
	}

	public String getStrategy() {
		return strategy;
	}

	public void setStrategy(String strategy) {
		this.strategy = strategy;
	}

	public int getType() {
		return type;
	}

	public void setType(int type) {
		this.type = type;
	}

	public String[] getTables() {
		return tables;
	}

	public void setTables(String[] tables) {
		this.tables = tables;
	}

}
