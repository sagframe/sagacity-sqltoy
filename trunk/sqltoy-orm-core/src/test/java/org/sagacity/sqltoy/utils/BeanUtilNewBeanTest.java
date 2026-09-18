package org.sagacity.sqltoy.utils;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.lang.reflect.Constructor;
import java.lang.reflect.Field;
import java.util.Map;

import org.junit.jupiter.api.Test;

/**
 * J3回归:结果集逐行映射的四处反射实例化(SqlUtil.reflectResultRowToVOClass、ResultUtils两个
 * 自定义Map子类分支、MapperUtils.reflectListToBean)统一走BeanUtil.newBean的无参构造器缓存。
 * 修复前均为逐行getDeclaredConstructor()(每行一次反射成员查找)
 */
public class BeanUtilNewBeanTest {

	public static class PublicVo {
		public PublicVo() {
		}
	}

	/** 非public构造器:修复前各调用点的裸getDeclaredConstructor().newInstance()会抛IllegalAccessException */
	public static class PrivateCtorVo {
		private PrivateCtorVo() {
		}
	}

	@SuppressWarnings("unchecked")
	private static Map<Class, Constructor> constructorCache() throws Exception {
		Field field = BeanUtil.class.getDeclaredField("noArgConstructors");
		field.setAccessible(true);
		return (Map<Class, Constructor>) field.get(null);
	}

	@Test
	public void createsBeanAndCachesConstructor() throws Exception {
		Object first = BeanUtil.newBean(PublicVo.class);
		Object second = BeanUtil.newBean(PublicVo.class);
		assertNotNull(first);
		assertNotNull(second);
		// 每次都是新实例
		assertTrue(first != second);
		assertEquals(PublicVo.class, first.getClass());
		// 构造器实例被缓存复用(反射成员查找只发生一次)
		Constructor cached = constructorCache().get(PublicVo.class);
		assertNotNull(cached, "构造器应已进入缓存");
		assertSame(cached, constructorCache().get(PublicVo.class));
	}

	@Test
	public void nonPublicConstructorIsAccessible() throws Exception {
		// setAccessible(true)兜底:非public构造器的VO同样可实例化
		assertNotNull(BeanUtil.newBean(PrivateCtorVo.class));
	}
}
