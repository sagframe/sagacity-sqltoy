package org.sagacity.sqltoy.integration.aot.dto;

/**
 * AOT 测试夹具:普通 resultType VO,验证 nativeReflectionPackages 包扫描
 */
public class AotTestOrderVO {
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
