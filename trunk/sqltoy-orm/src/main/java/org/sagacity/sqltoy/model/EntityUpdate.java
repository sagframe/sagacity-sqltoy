/**
 * 
 */
package org.sagacity.sqltoy.model;

import java.io.Serializable;
import java.util.LinkedHashMap;

import javax.sql.DataSource;

/**
 * @description 提供给代码中组织sql进行数据库update操作
 * @author renfei.chen <a href="mailto:zhongxuchen@hotmail.com">联系作者</a>
 * @version id:EntityUpdate.java,Revision:v1.0,Date:2020-5-15
 */
public class EntityUpdate implements Serializable {

	/**
	 * 
	 */
	private static final long serialVersionUID = -6476698994760985087L;

	public static EntityUpdate create() {
		return new EntityUpdate();
	}

	/**
	 * 条件语句
	 */
	private String where;

	/**
	 * 参数值
	 */
	private Object[] values;

	private DataSource dataSource;

	private LinkedHashMap<String, Object> updateValues = new LinkedHashMap<String, Object>();

	public EntityUpdate set(String param, Object value) {
		updateValues.put(param, value);
		return this;
	}

	public EntityUpdate where(String where) {
		this.where = where;
		return this;
	}

	public EntityUpdate values(Object... values) {
		this.values = values;
		return this;
	}

	public EntityUpdate dataSource(DataSource dataSource) {
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

	/**
	 * @return the updateValues
	 */
	public LinkedHashMap<String, Object> getUpdateValues() {
		return updateValues;
	}

}
