package org.sagacity.sqltoy.model;

import java.io.Serializable;

import javax.sql.DataSource;

/**
 * 
 * @author zhongxuchen
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

}
