package org.sagacity.sqltoy.integration.aot.entity;

/**
 * AOT 测试夹具:非 @Entity 的普通 resultType DTO,验证 nativeReflectionClasses 人工登记
 */
public class AotTestDto {
	private String userName;

	public String getUserName() {
		return userName;
	}

	public void setUserName(String userName) {
		this.userName = userName;
	}
}
