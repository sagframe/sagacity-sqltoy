/**
 * 
 */
package org.sagacity.sqltoy.config.model;

/**
 * @project sagacity-sqltoy
 * @description 细化sql类型
 * @author zhongxuchen
 * @version v1.0, Date:2022年9月9日
 * @modify 2022年9月9日,修改说明
 */
public enum OperateType {
	// 普通sql查询
	search(1),
	// 分页查询
	page(2),
	// 取top记录
	top(3),
	// 取随机记录
	random(4),
	// count查询
	count(5),
	// POJO load
	load(6),
	// pojo loadAll
	loadAll(7),
	// 唯一性查询
	unique(8),
	// 锁查询且更新
	fetchUpdate(9),
	// 修改、删除类sql执行
	execute(10),
	// POJO 更新、批量更新、删除、批量删除、保存或修改
	update(11), updateAll(12), delete(13), deleteAll(14), saveOrUpdate(15),
	// 单表对象化操作(findEntity/deleteByQuery/updateByQuery)
	singleTable(16),
	// 单POJO保存
	insert(17),
	// 批量POOJO保存
	insertAll(18);

	private final int optType;

	private OperateType(int optType) {
		this.optType = optType;
	}

	public int getValue() {
		return optType;
	}
}
