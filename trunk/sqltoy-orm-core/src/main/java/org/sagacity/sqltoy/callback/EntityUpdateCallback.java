package org.sagacity.sqltoy.callback;

import java.io.Serializable;

/**
 * 面向实体对象的行更新回调，完全无ResultSet
 * 
 * @param <T> 业务DTO/实体
 */
@FunctionalInterface
public interface EntityUpdateCallback<T extends Serializable> {
	/**
	 * 仅操作实体get/set，底层自动同步ResultSet
	 * 
	 * @param entity   代理实体，get自动读库、set自动更新行
	 * @param rowIndex 当前行下标
	 * @throws Exception
	 */
	void update(T entity, int rowIndex) throws Exception;
}
