/**
 * 
 */
package org.sagacity.sqltoy.model;

/**
 * @project sagacity-sqltoy
 * @description 设置记录存在时如何处理，如:存在则修改、存在则忽视
 * @author zhongxuchen
 * @version v1.0,Date:2017年10月10日
 */
public enum SaveMode {
	// 默认为插入
	APPEND(0),
	// 修改处理
	UPDATE(1),

	// 忽视处理
	IGNORE(2);

	private final int mode;

	private SaveMode(int mode) {
		this.mode = mode;
	}

	public int getValue() {
		return this.mode;
	}

	@Override
	public String toString() {
		return Integer.toString(mode);
	}
}
