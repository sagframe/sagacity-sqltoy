package org.sagacity.sqltoy.model;

/**
 * @project sagacity-sqltoy
 * @description 定义pojo注解上的安全处理类型，这里混合了加密和脱敏处理的几种类型
 * @author zhongxuchen
 */
public enum SecureType {
	// 加密
	ENCRYPT("encrypt"),
	// 电话
	TEL("tel"),
	// 修改处理
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
