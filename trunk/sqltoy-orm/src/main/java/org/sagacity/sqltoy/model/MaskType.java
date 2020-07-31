package org.sagacity.sqltoy.model;

/**
 * 
 * @author zhong
 *
 */
public enum MaskType {
	// 电话
	TEL("tel"),
	// 修改处理
	NAME("name"),
	// 邮箱
	EMAIL("name"),
	// 地址
	ADDRESS("address"),
	// 银行账号
	ACCOUNT_NO("bank-card"),
	// 身份证
	ID("id-card");

	private final String type;

	private MaskType(String type) {
		this.type = type;
	}

	public String getValue() {
		return this.type;
	}

	public String toString() {
		return type;
	}
}
