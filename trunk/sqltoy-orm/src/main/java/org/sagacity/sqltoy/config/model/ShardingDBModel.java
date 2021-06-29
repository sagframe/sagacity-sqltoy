/**
 * 
 */
package org.sagacity.sqltoy.config.model;

import java.io.Serializable;

import javax.sql.DataSource;

/**
 * @project sagacity-sqltoy4.0
 * @description 分库模型
 * @author zhongxuchen
 * @version v1.0,Date:2017年11月3日
 */
public class ShardingDBModel implements Serializable {

	/**
	 * 
	 */
	private static final long serialVersionUID = -4119527303448356631L;

	/**
	 * 数据源名称
	 */
	private String dataSourceName;

	/**
	 * 数据源
	 */
	private DataSource dataSource;

	/**
	 * @return the dataSourceName
	 */
	public String getDataSourceName() {
		return dataSourceName;
	}

	/**
	 * @param dataSourceName the dataSourceName to set
	 */
	public void setDataSourceName(String dataSourceName) {
		this.dataSourceName = dataSourceName;
	}

	/**
	 * @return the dataSource
	 */
	public DataSource getDataSource() {
		return dataSource;
	}

	/**
	 * @param dataSource the dataSource to set
	 */
	public void setDataSource(DataSource dataSource) {
		this.dataSource = dataSource;
	}

}
