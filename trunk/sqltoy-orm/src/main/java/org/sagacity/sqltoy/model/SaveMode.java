/**
 * 
 */
package org.sagacity.sqltoy.model;

/**
 * @project sagacity-sqltoy
 * @description 记录存在是如何处理
 * @author chenrenfei <a href="mailto:zhongxuchen@gmail.com">联系作者</a>
 * @version id:SaveMode.java,Revision:v1.0,Date:2017年10月10日
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

	public String toString() {
		return Integer.toString(mode);
	}
}
