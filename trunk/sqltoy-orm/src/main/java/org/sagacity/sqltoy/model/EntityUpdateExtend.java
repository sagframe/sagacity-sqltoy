/**
 * 
 */
package org.sagacity.sqltoy.model;

import java.io.Serializable;
import java.util.LinkedHashMap;

import javax.sql.DataSource;

/**
 * @author zhong
 *
 */
public class EntityUpdateExtend implements Serializable {

	/**
	 * 
	 */
	private static final long serialVersionUID = -7224627356139617128L;

	/**
	 * 条件语句
	 */
	public String where;

	/**
	 * 条件参数值
	 */
	public Object[] values;

	/**
	 * 数据源
	 */
	public DataSource dataSource;

	/**
	 * update 的字段名称和对应的值
	 */
	public LinkedHashMap<String, Object> updateValues = new LinkedHashMap<String, Object>();

}
