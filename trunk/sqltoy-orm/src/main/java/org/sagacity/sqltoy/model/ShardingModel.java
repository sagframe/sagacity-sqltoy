/**
 * 
 */
package org.sagacity.sqltoy.model;

import java.io.Serializable;

import javax.sql.DataSource;

/**
 * @project sagacity-sqltoy4.0
 * @description 单一分库分表模型
 * @author chenrenfei <a href="mailto:zhongxuchen@gmail.com">联系作者</a>
 * @version id:ShardingModel.java,Revision:v1.0,Date:2017年11月6日
 */
public class ShardingModel implements Serializable {
	/**
	 * 
	 */
	private static final long serialVersionUID = 4092467610948600457L;

	/**
	 * 数据源名称
	 */
	private String dataSourceName;

	/**
	 * 数据源名称
	 */
	private DataSource dataSource;

	/**
	 * 表名
	 */
	private String tableName;

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

	/**
	 * @return the tableName
	 */
	public String getTableName() {
		return tableName;
	}

	/**
	 * @param tableName the tableName to set
	 */
	public void setTableName(String tableName) {
		this.tableName = tableName;
	}

}
