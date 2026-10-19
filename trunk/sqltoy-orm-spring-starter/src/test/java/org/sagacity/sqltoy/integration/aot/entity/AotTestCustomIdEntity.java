package org.sagacity.sqltoy.integration.aot.entity;

import java.io.Serializable;

import org.sagacity.sqltoy.config.annotation.Column;
import org.sagacity.sqltoy.config.annotation.Entity;
import org.sagacity.sqltoy.config.annotation.Id;

/**
 * AOT 测试夹具:验证 @Id(generator=自定义类) 会被 AOT 注册反射元数据
 */
@Entity(tableName = "sqltoy_aot_custom_id")
public class AotTestCustomIdEntity implements Serializable {

	private static final long serialVersionUID = 1L;

	@Id(generator = "org.sagacity.sqltoy.integration.aot.entity.AotTestCustomGenerator")
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
