package org.sagacity.sqltoy.model;

import java.io.Serializable;
import java.util.LinkedHashMap;

import javax.sql.DataSource;

/**
 * @description 提供给代码中进行查询使用，一般适用于接口服务内部逻辑处理以单表为主体(不用于页面展示)
 * @author renfei.chen <a href="mailto:zhongxuchen@hotmail.com">联系作者</a>
 * @version id:EntityQuery.java,Revision:v1.0,Date:2020-5-15
 */
public class EntityQuery implements Serializable {

	/**
	 * 
	 */
	private static final long serialVersionUID = 5223170071884950204L;

	public static EntityQuery create() {
		return new EntityQuery();
	}

	/**
	 * 条件语句
	 */
	private String where;

	/**
	 * 参数名称
	 */
	private String[] names;

	/**
	 * 参数值
	 */
	private Object[] values;

	private DataSource dataSource;

	/**
	 * 锁类型
	 */
	private LockMode lockMode;

	private LinkedHashMap<String, String> orderBy = new LinkedHashMap<String, String>();

	public EntityQuery where(String where) {
		this.where = where;
		return this;
	}

	public EntityQuery names(String... names) {
		this.names = names;
		return this;
	}

	public EntityQuery values(Object... values) {
		this.values = values;
		return this;
	}

	public EntityQuery orderBy(String field) {
		// 默认为升序
		orderBy.put(field, " ");
		return this;
	}

	public EntityQuery orderByDesc(String field) {
		orderBy.put(field, " desc ");
		return this;
	}

	public EntityQuery lock(LockMode lockMode) {
		this.lockMode = lockMode;
		return this;
	}

	public EntityQuery dataSource(DataSource dataSource) {
		this.dataSource = dataSource;
		return this;
	}

	/**
	 * @return the where
	 */
	public String getWhere() {
		return where;
	}

	/**
	 * @return the names
	 */
	public String[] getNames() {
		return names;
	}

	/**
	 * @return the values
	 */
	public Object[] getValues() {
		return values;
	}

	/**
	 * @return the dataSource
	 */
	public DataSource getDataSource() {
		return dataSource;
	}

	public LockMode getLockMode() {
		return lockMode;
	}

	public LinkedHashMap<String, String> getOrderBy() {
		return orderBy;
	}

}
