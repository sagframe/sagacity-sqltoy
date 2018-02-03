/**
 * 
 */
package org.sagacity.sqltoy.config.model;

import java.io.Serializable;

/**
 * @project sqltoy-orm
 * @description table sharding 配置模型
 * @author chenrenfei <a href="mailto:zhongxuchen@gmail.com">联系作者</a>
 * @version id:QueryShardingModel.java,Revision:v1.0,Date:2015年4月14日
 */
public class QueryShardingModel implements Serializable {

	/**
	 * 
	 */
	private static final long serialVersionUID = 3194225565492327232L;

	/**
	 * 表名称
	 */
	private String[] tables;

	/**
	 * sharding策略
	 */
	private String strategy;
	
	/**
	 * 策略辨别值
	 */
	private String strategyValue;
	
	/**
	 * 参数
	 */
	private String[] params;
	
	/**
	 * 参数别名
	 */
	private String[] paramsAlias;

	/**
	 * @return the tables
	 */
	public String[] getTables() {
		return tables;
	}

	/**
	 * @param tables the tables to set
	 */
	public void setTables(String[] tables) {
		this.tables = tables;
	}

	/**
	 * @return the strategy
	 */
	public String getStrategy() {
		return strategy;
	}

	/**
	 * @param strategy the strategy to set
	 */
	public void setStrategy(String strategy) {
		this.strategy = strategy;
	}

	/**
	 * @return the params
	 */
	public String[] getParams() {
		return params;
	}

	/**
	 * @param params the params to set
	 */
	public void setParams(String[] params) {
		this.params = params;
	}

	/**
	 * @return the paramsAlias
	 */
	public String[] getParamsAlias() {
		return paramsAlias;
	}

	/**
	 * @param paramsAlias the paramsAlias to set
	 */
	public void setParamsAlias(String[] paramsAlias) {
		this.paramsAlias = paramsAlias;
	}

	public String getStrategyValue() {
		return strategyValue;
	}

	public void setStrategyValue(String strategyValue) {
		this.strategyValue = strategyValue;
	}
	
	
}
