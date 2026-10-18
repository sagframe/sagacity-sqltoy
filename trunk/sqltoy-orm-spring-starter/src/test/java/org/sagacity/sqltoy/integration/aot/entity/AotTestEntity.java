package org.sagacity.sqltoy.integration.aot.entity;

import java.io.Serializable;

import org.sagacity.sqltoy.config.annotation.Column;
import org.sagacity.sqltoy.config.annotation.Entity;
import org.sagacity.sqltoy.config.annotation.Id;

/**
 * AOT 实体扫描的测试夹具:独立顶层 @Entity 类,供 EntityScanner.scanEntityClasses 按包扫描发现
 * (SqltoyAotHintsTest 以 spring.sqltoy.packagesToScan 指向本包)
 */
@Entity(tableName = "sqltoy_aot_test")
public class AotTestEntity implements Serializable {

	private static final long serialVersionUID = 1L;

	@Id
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
