package org.sagacity.sqltoy.utils;

import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNotSame;
import static org.junit.jupiter.api.Assertions.assertSame;

import java.io.InputStream;
import java.sql.Types;

import org.junit.jupiter.api.Test;
import org.sagacity.sqltoy.config.model.DataType;

/**
 * 回归:字符串转枚举时必须优先使用线程上下文类加载器,且类名缓存要校验装载器归属。
 * 修复前固定用框架类加载器Class.forName并按类名无条件缓存:热部署/多classloader下同名枚举类
 * 重新加载后会继续返回旧Class(枚举转换错位),新加载器的定义永远不被使用
 */
public class BeanUtilEnumClassReloadTest {

	private static final String ENUM_CLASS_NAME = "org.sagacity.sqltoy.utils.ReloadTestEnum";

	/** 以platform类加载器为父的独立加载器:父加载器看不到测试类,保证每个实例各自定义一份同名枚举类 */
	private static final class IsolatedEnumLoader extends ClassLoader {
		private final byte[] bytes;

		private IsolatedEnumLoader(byte[] bytes) {
			// (jre8同步适配:platform类加载器为JDK9+,改用系统加载器的父加载器——
			// 同样看不到测试classpath,保证每个实例各自定义一份同名枚举类)
			super(ClassLoader.getSystemClassLoader().getParent());
			this.bytes = bytes;
		}

		private Class<?> define() {
			return defineClass(ENUM_CLASS_NAME, bytes, 0, bytes.length);
		}
	}

	private static IsolatedEnumLoader newLoader() throws Exception {
		String resource = ENUM_CLASS_NAME.replace('.', '/') + ".class";
		try (InputStream is = BeanUtilEnumClassReloadTest.class.getClassLoader().getResourceAsStream(resource)) {
			assertNotNull(is, "找不到测试枚举class资源:" + resource);
			// (jre8同步适配:InputStream.readAllBytes为JDK9+,等价循环读取)
			java.io.ByteArrayOutputStream buffer = new java.io.ByteArrayOutputStream();
			byte[] chunk = new byte[8192];
			int read;
			while ((read = is.read(chunk)) != -1) {
				buffer.write(chunk, 0, read);
			}
			return new IsolatedEnumLoader(buffer.toByteArray());
		}
	}

	private static Object convert(String value, ClassLoader loader) throws Exception {
		ClassLoader origin = Thread.currentThread().getContextClassLoader();
		try {
			Thread.currentThread().setContextClassLoader(loader);
			return BeanUtil.convertType(value, Types.VARCHAR, DataType.enumType, ENUM_CLASS_NAME);
		} finally {
			Thread.currentThread().setContextClassLoader(origin);
		}
	}

	@Test
	public void reloadedEnumMustBeResolvedByContextClassLoader() throws Exception {
		IsolatedEnumLoader firstLoader = newLoader();
		IsolatedEnumLoader secondLoader = newLoader();
		Class<?> firstEnum = firstLoader.define();
		Class<?> secondEnum = secondLoader.define();
		assertNotSame(firstEnum, secondEnum);
		// 修复前:Class.forName固定用框架类加载器,拿到的是测试classpath上的父版本,而非TCCL中的新版本
		Object firstResult = convert("A", firstLoader);
		assertSame(firstLoader, firstResult.getClass().getClassLoader(),
				"应按线程上下文类加载器解析枚举类,实际:" + firstResult.getClass().getClassLoader());
		Object secondResult = convert("A", secondLoader);
		assertSame(secondLoader, secondResult.getClass().getClassLoader(),
				"同名枚举类重新加载后必须使用新加载器的Class,实际:" + secondResult.getClass().getClassLoader());
		// 同一装载器重复转换应命中缓存并返回同一Class
		Object again = convert("A", firstLoader);
		assertSame(firstResult.getClass(), again.getClass());
	}
}
