package org.sagacity.sqltoy.integration.aot.entity;

import java.io.Serializable;

import org.sagacity.sqltoy.config.annotation.Column;
import org.sagacity.sqltoy.config.annotation.Entity;
import org.sagacity.sqltoy.config.annotation.Id;

/**
 * AOT 测试夹具:验证 @Id(strategy="ulid") 对应 ULIDGenerator 的反射注册
 */
@Entity(tableName = "sqltoy_aot_ulid")
public class AotTestUlidEntity implements Serializable {

	private static final long serialVersionUID = 1L;

	@Id(strategy = "ulid")
	private String id;

	@Column(name = "USER_NAME")
	private String userName;

	public String getId() {
		return id;
	}

	public void setId(String id) {
		this.id = id;
	}

	public String getUserName() {
		return userName;
	}

	public void setUserName(String userName) {
		this.userName = userName;
	}
}
