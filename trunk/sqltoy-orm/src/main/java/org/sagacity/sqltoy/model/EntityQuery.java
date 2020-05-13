package org.sagacity.sqltoy.model;

/**
 * 
 * @author zhongxuchen
 * @param <T>
 */
public class EntityQuery<T> implements Query<T> {

	/**
	 * 
	 */
	// private static final long serialVersionUID = 5223170071884950204L;

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

	public EntityQuery where(String where) {
		this.where = where;
		return this;
	}

	public EntityQuery values(String... names) {
		this.names = names;
		return this;
	}

	public EntityQuery values(Object... values) {
		this.values = values;
		return this;
	}

}
