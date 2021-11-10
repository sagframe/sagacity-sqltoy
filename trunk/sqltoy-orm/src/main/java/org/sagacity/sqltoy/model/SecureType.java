package org.sagacity.sqltoy.model;

/**
 * @project sagacity-sqltoy
 * @description 字段安全处理(加密和脱敏)类型定义
 * @author zhongxuchen
 * @version v1.0,Date:2021-09-25
 */
public enum SecureType {
	// 加密
	ENCRYPT("encrypt"),
	// 电话
	TEL("tel"),
	// 姓名
	NAME("name"),
	// 邮箱
	EMAIL("email"),
	// 地址
	ADDRESS("address"),
	// 银行账号
	ACCOUNT_NO("bank-card"),
	// 身份证
	ID("id-card"),
	// 银行对公账号
	PUBLIC_ACCOUNT("public-account");

	private final String type;

	private SecureType(String type) {
		this.type = type;
	}

	public String getValue() {
		return this.type;
	}

	public String toString() {
		return type;
	}
}
