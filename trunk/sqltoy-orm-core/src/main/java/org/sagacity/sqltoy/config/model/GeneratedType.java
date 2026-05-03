package org.sagacity.sqltoy.config.model;

/**
 * 数据库表计算列
 */
public enum GeneratedType {
	// 普通列
	DEFAULT(0),
	// 虚拟列
	VIRTUAL(1),
	// 存储列
	STORED(2);

	private final int value;

	private GeneratedType(int value) {
		this.value = value;
	}

	public int getValue() {
		return this.value;
	}

	@Override
	public String toString() {
		return Integer.toString(this.value);
	}
}
