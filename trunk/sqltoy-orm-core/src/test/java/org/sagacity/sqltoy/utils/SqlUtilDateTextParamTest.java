package org.sagacity.sqltoy.utils;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.lang.reflect.Proxy;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.Types;

import org.junit.jupiter.api.Test;
import org.sagacity.sqltoy.model.DBProfile;
import org.sagacity.sqltoy.utils.DataSourceUtils.DBType;

/**
 * D5最小修复回归:DATE/TIME/TIMESTAMP字符串绑定解析失败时不再抛NPE,
 * 改为可读IllegalArgumentException;合法值仍正常调用对应setter。
 * 本测试只验证判空,不改变DateUtil宽松解析语义。
 */
public class SqlUtilDateTextParamTest {

	private static final Method SET_TEXT_PARAM;

	static {
		try {
			SET_TEXT_PARAM = SqlUtil.class.getDeclaredMethod("setTextParam", Connection.class, DBProfile.class,
					Integer.class, PreparedStatement.class, int.class, int.class, Object.class);
			SET_TEXT_PARAM.setAccessible(true);
		} catch (Exception e) {
			throw new ExceptionInInitializerError(e);
		}
	}

	@Test
	public void unparseableTextThrowsIllegalArgumentInsteadOfNpe() {
		for (int jdbcType : new int[] { Types.DATE, Types.TIMESTAMP, Types.TIME }) {
			IllegalArgumentException e = assertThrows(IllegalArgumentException.class,
					() -> invoke("abc", jdbcType, recorder().proxy));
			assertTrue(e.getMessage().contains("abc"), "message should contain value, got: " + e.getMessage());
			assertTrue(e.getMessage().contains(String.valueOf(jdbcType)),
					"message should contain jdbcType, got: " + e.getMessage());
		}
	}

	@Test
	public void emptyTextThrowsIllegalArgument() {
		assertThrows(IllegalArgumentException.class, () -> invoke("", Types.DATE, recorder().proxy));
		assertThrows(IllegalArgumentException.class, () -> invoke("   ", Types.TIMESTAMP, recorder().proxy));
	}

	@Test
	public void otherTextTypeUnaffected() throws Throwable {
		Recorder recorder = recorder();
		assertEquals(Boolean.TRUE, invoke("abc", Types.VARCHAR, recorder.proxy));
		assertEquals("setString", recorder.setter);
		assertEquals("abc", recorder.value);
	}

	@Test
	public void validDateBindsSqlDate() throws Throwable {
		Recorder recorder = recorder();
		assertEquals(Boolean.TRUE, invoke("2026-01-02", Types.DATE, recorder.proxy));
		assertEquals("setDate", recorder.setter);
		assertTrue(recorder.value instanceof java.sql.Date);
		assertEquals("2026-01-02", recorder.value.toString());
	}

	@Test
	public void validTimestampBindsTimestamp() throws Throwable {
		Recorder recorder = recorder();
		assertEquals(Boolean.TRUE, invoke("2026-01-02 03:04:05", Types.TIMESTAMP, recorder.proxy));
		assertEquals("setTimestamp", recorder.setter);
		assertTrue(recorder.value instanceof java.sql.Timestamp);
	}

	@Test
	public void validTimeBindsSqlTime() throws Throwable {
		Recorder recorder = recorder();
		assertEquals(Boolean.TRUE, invoke("09:30:00", Types.TIME, recorder.proxy));
		assertEquals("setTime", recorder.setter);
		assertTrue(recorder.value instanceof java.sql.Time);
		assertEquals("09:30:00", recorder.value.toString());
	}

	private static Object invoke(String value, int jdbcType, PreparedStatement pst) throws Throwable {
		try {
			return SET_TEXT_PARAM.invoke(null, null, null, Integer.valueOf(DBType.MYSQL), pst, jdbcType, 1, value);
		} catch (InvocationTargetException e) {
			throw e.getCause();
		}
	}

	private static Recorder recorder() {
		Recorder recorder = new Recorder();
		recorder.proxy = (PreparedStatement) Proxy.newProxyInstance(SqlUtilDateTextParamTest.class.getClassLoader(),
				new Class[] { PreparedStatement.class }, recorder);
		return recorder;
	}

	private static class Recorder implements java.lang.reflect.InvocationHandler {
		private PreparedStatement proxy;
		private String setter;
		private Object value;

		@Override
		public Object invoke(Object proxy, Method method, Object[] args) {
			String name = method.getName();
			if (name.startsWith("set")) {
				setter = name;
				value = (args == null || args.length < 2) ? null : args[1];
				return null;
			}
			if ("toString".equals(name)) {
				return "fakePst";
			}
			if ("hashCode".equals(name)) {
				return System.identityHashCode(proxy);
			}
			if ("equals".equals(name)) {
				return proxy == args[0];
			}
			return null;
		}
	}
}
