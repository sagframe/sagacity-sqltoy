package org.sagacity.sqltoy.link;

import java.io.Serializable;

public class EntityQuery<T> implements Serializable {

	/**
	 * 
	 */
	private static final long serialVersionUID = 5223170071884950204L;

	private Class<T> resultType;

	private String where;

	private String[] names;

	private Object[] values;

	// private List<>

	public EntityQuery(Class<T> entityClass) {
		this.resultType = entityClass;
	}

	public EntityQuery where(String where) {
		this.where = where;
		return this;
	}

	public EntityQuery values(Object... values) {
		this.values = values;
		return this;
	}

}
