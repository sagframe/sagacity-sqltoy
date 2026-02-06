package org.sagacity.sqltoy.utils;

import java.lang.reflect.Field;
import java.lang.reflect.Modifier;
import java.util.HashMap;
import java.util.Map;

/**
 * @project sagacity-sqltoy
 * @description 枚举类型工具
 * @author zhongxuchen
 * @version v1.0,Date:2026-2-6
 */
public class EnumUtil {
	private static Map<Class, Boolean> withoutCustomFieldEnumMap = new HashMap<>();

	/**
	 * 终极版：判断枚举是否无开发者自定义属性（仅含枚举常量） 核心：仅判断「开发者手动定义的字段」，无视编译器任何隐式生成字段
	 * 
	 * @param enumClass 枚举Class
	 * @return true=无自定义属性，false=有/非枚举
	 */
	public static boolean isEnumWithoutCustomField(Class<?> enumClass) {
		if (withoutCustomFieldEnumMap.containsKey(enumClass)) {
			return withoutCustomFieldEnumMap.get(enumClass);
		}
		// 1. 非枚举直接返回false
		if (!enumClass.isEnum()) {
			withoutCustomFieldEnumMap.put(enumClass, false);
			return false;
		}
		// 2. 获取所有声明字段
		Field[] declaredFields = enumClass.getDeclaredFields();
		for (Field field : declaredFields) {
			// 过滤规则（满足任意一个则跳过，剩余的就是开发者自定义字段）：
			// ① 是枚举常量 ② 是编译器生成的字段（合成字段） ③ 是静态字段（除了枚举常量，编译器生成的都是静态）
			if (field.isEnumConstant() || field.isSynthetic() || Modifier.isStatic(field.getModifiers())) {
				continue;
			}
			withoutCustomFieldEnumMap.put(enumClass, false);
			// 走到这里，说明是开发者手动定义的「非静态成员字段」→ 有自定义属性
			return false;
		}
		withoutCustomFieldEnumMap.put(enumClass, true);
		// 无开发者自定义字段
		return true;
	}
}
