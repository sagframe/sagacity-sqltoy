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
	search(1),

	page(2),

	top(3),

	random(4), count(5), load(6), unique(7), fetchUpdate(8), execute(9);

	private final int optType;

	private OperateType(int optType) {
		this.optType = optType;
	}

	public int getValue() {
		return optType;
	}
}
