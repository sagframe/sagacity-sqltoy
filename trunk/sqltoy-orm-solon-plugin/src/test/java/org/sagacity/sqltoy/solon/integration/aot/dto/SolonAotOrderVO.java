package org.sagacity.sqltoy.solon.integration.aot.dto;

/**
 * AOT 测试夹具:普通 resultType VO,验证 Solon 侧 nativeReflectionPackages 包扫描
 */
public class SolonAotOrderVO {
	private String orderId;
	private String userName;

	public String getOrderId() {
		return orderId;
	}

	public void setOrderId(String orderId) {
		this.orderId = orderId;
	}

	public String getUserName() {
		return userName;
	}

	public void setUserName(String userName) {
		this.userName = userName;
	}
}
