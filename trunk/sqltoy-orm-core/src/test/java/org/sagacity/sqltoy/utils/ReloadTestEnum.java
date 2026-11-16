package org.sagacity.sqltoy.utils;

/**
 * 供 BeanUtil 枚举解析的热部署回归测试使用(会被两个独立classloader各自加载出同名Class)
 */
public enum ReloadTestEnum {
	A, B
}
