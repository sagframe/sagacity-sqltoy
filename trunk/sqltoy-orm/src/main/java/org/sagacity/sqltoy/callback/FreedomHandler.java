/**
 * 
 */
package org.sagacity.sqltoy.callback;

/**
 * @project sagacity-sqltoy
 * @description 提供给Service层做反调实现事务包裹
 * @author zhongxuchen
 * @version v1.0, Date:2020-8-6
 * @modify 2020-8-6,修改说明
 */
@Deprecated
public abstract class FreedomHandler {
	public abstract Object process(Object values);
}
