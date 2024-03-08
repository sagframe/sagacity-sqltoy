package org.sagacity.sqltoy.callback;

/**
 * @project sagacity-sqltoy
 * @description 用于EntityQuery 查询字段使用(意义不大)
 * @author zhongxuchen
 * @version v1.0, Date:2020-10-15
 * @modify 2020-10-15,修改说明
 */
public abstract class SelectFields {
	/**
	 * @TODO 获取最终查询字段
	 * @return
	 */
	public abstract String[] getSelectFields();
}
