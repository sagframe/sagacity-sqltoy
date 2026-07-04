/**
 * 
 */
package org.sagacity.sqltoy.model;

import java.io.Serializable;

import javax.sql.DataSource;

/**
 * @project sqltoy-orm
 * @description 唯一性验证查询模型
 * @author zhongxuchen
 * @version v1.0,Date:2015年3月16日
 */
public class UniqueExecutor implements Serializable {
	/**
	 * 
	 */
	private static final long serialVersionUID = 4655678022572718682L;

	/**
	 * 实体对象
	 */
	private Serializable entity;

	/**
	 * 整体判定唯一的字段
	 */
	private String[] uniqueFields;

	/**
	 * 特定数据库连接资源
	 */
	private DataSource dataSource;

	/**
	 * 上下文数据, 用于执行过程中的共享数据
	 */
	private Object contextData;

	private int timeout = -1;

	public UniqueExecutor entity(Serializable entity) {
		this.entity = entity;
		return this;
	}

	public UniqueExecutor(Serializable entity) {
		this.entity = entity;
	}

	public UniqueExecutor(Serializable entity, String[] uniqueFields) {
		this.entity = entity;
		this.uniqueFields = uniqueFields;
	}

	public UniqueExecutor dataSource(DataSource dataSource) {
		this.dataSource = dataSource;
		return this;
	}

	public UniqueExecutor queryTimeout(Integer timeout) {
		this.timeout = timeout;
		return this;
	}

	/**
	 * @return the entity
	 */
	public Serializable getEntity() {
		return entity;
	}

	/**
	 * @return the paramsName
	 */
	public String[] getUniqueFields() {
		return this.uniqueFields;
	}

	/**
	 * @return the dataSource
	 */
	public DataSource getDataSource() {
		return dataSource;
	}

	public int getTimeout() {
		return timeout;
	}
	
	public <T> T getContextData() {
		return (T) contextData;
	}

	public UniqueExecutor setContextData(Object contextData) {
		this.contextData = contextData;
		return this;
	}
}
