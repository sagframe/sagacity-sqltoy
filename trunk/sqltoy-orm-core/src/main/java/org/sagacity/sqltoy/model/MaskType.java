package org.sagacity.sqltoy.model;

/**
 * @project sagacity-sqltoy
 * @description ParamFilters里面对查询结果值进行脱敏的类型
 * @author zhongxuchen
 * @version v1.0, Date:2020-10-21
 * @modify 2020-10-21,修改说明
 */
public enum MaskType {
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

	private MaskType(String type) {
		this.type = type;
	}

	public String getValue() {
		return this.type;
	}

	@Override
	public String toString() {
		return type;
	}
}
