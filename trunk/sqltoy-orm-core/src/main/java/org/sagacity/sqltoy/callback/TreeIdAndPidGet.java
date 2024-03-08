package org.sagacity.sqltoy.callback;

/**
 * @project sagacity-sqltoy
 * @description 树形结构数据处理的反调接口，用于树结构集合排序，形成层级关系
 * @author zhongxuchen
 * @version v1.0,Date:2010-9-13
 */
@FunctionalInterface
public interface TreeIdAndPidGet<T> {

	/**
	 * @TODO 获取id和pid值
	 * @param obj 要拆解的对象
	 * @return 返回id和父id的长度为2的数组
	 */
	public Object[] getIdAndPid(T obj);
}
