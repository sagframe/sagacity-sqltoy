package org.sagacity.sqltoy.config.model;

public enum ResourceType {
	/** 本地文件系统（可监听变更） */
	FILE,
	/** JAR包内（不可监听） */
	JAR
}
