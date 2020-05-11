package org.sagacity.sqltoy.link;

import java.io.Serializable;

public class EntityQuery<T> implements Serializable {
	
	/**
	 * 
	 */
	private static final long serialVersionUID = 5223170071884950204L;
	
	private Class<T> resultType;
	
	//private List<>

	public EntityQuery(Class<T> entityClass) {
		this.resultType = entityClass;
	}

}
