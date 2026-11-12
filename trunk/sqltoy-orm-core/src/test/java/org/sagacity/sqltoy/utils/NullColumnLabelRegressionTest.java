package org.sagacity.sqltoy.utils;

import static org.junit.jupiter.api.Assertions.assertEquals;

import java.lang.reflect.InvocationHandler;
import java.lang.reflect.Method;
import java.lang.reflect.Proxy;
import java.sql.ResultSetMetaData;
import java.util.HashSet;

import org.junit.jupiter.api.Test;

/**
 * update 2026-11-12 回归:MySQL Connector/J对无别名的空串列(select '')的getColumnLabel返回null,
 * ResultUtils.resolveColumns/SqlUtil.getColumnLabelAndTypes旧实现直接toLowerCase会NPE(真库已复现),
 * 用代理元数据锁定null标签归一为空串的防御行为(不依赖具体数据库)。
 */
public class NullColumnLabelRegressionTest {

	private static ResultSetMetaData nullLabelMetaData() {
		InvocationHandler handler = new InvocationHandler() {
			@Override
			public Object invoke(Object proxy, Method method, Object[] args) throws Throwable {
				String name = method.getName();
				if ("getColumnCount".equals(name)) {
					return 2;
				}
				if ("getColumnLabel".equals(name) || "getColumnName".equals(name)) {
					return null;
				}
				if ("getColumnTypeName".equals(name)) {
					return "VARCHAR";
				}
				Class<?> returnType = method.getReturnType();
				if (returnType == int.class) {
					return 0;
				}
				if (returnType == boolean.class) {
					return false;
				}
				return null;
			}
		};
		return (ResultSetMetaData) Proxy.newProxyInstance(NullColumnLabelRegressionTest.class.getClassLoader(),
				new Class<?>[] { ResultSetMetaData.class }, handler);
	}

	@Test
	public void resolveColumnsToleratesNullLabel() throws Exception {
		Method method = ResultUtils.class.getDeclaredMethod("resolveColumns", ResultSetMetaData.class, int.class,
				int.class, String.class, boolean.class, java.util.Set.class, Integer.class);
		method.setAccessible(true);
		Object[] cols = (Object[]) method.invoke(null, nullLabelMetaData(), 0, 2, null, false, new HashSet<String>(),
				DataSourceUtils.DBType.MYSQL);
		assertEquals(2, cols.length);
		for (Object col : cols) {
			Object label = col.getClass().getMethod("getLabel").invoke(col);
			assertEquals("", label, "null列标签应归一为空串");
		}
	}

	@Test
	public void getColumnLabelAndTypesToleratesNullLabel() throws Exception {
		Method method = SqlUtil.class.getDeclaredMethod("getColumnLabelAndTypes", ResultSetMetaData.class);
		method.setAccessible(true);
		String[][] result = (String[][]) method.invoke(null, nullLabelMetaData());
		assertEquals("", result[0][0]);
		assertEquals("", result[0][1]);
		assertEquals("VARCHAR", result[1][0]);
	}
}
