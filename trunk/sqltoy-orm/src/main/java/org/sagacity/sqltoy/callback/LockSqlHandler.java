/**
 * 
 */
package org.sagacity.sqltoy.callback;

import org.sagacity.sqltoy.model.LockMode;

/**
 * @project sagacity-sqltoy
 * @description 根据数据库类型获得锁表sql
 * @author zhongxuchen
 * @version v1.0, Date:2021-3-17
 * @modify 2021-3-17,修改说明
*/
@FunctionalInterface
public interface LockSqlHandler {
	public String getLockSql(String sql, Integer dbType, LockMode lockMode);
}
