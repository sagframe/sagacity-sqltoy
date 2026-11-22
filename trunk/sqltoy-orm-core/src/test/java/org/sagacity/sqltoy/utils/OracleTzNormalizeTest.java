package org.sagacity.sqltoy.utils;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;

import java.lang.reflect.Method;

import org.junit.jupiter.api.Test;

/**
 * update 2026-11-22 oracle 时区类型对象归一的方法解析验证:
 * TIMESTAMPTZ 有无参 offsetDateTimeValue(),可归一为 OffsetDateTime;
 * TIMESTAMPLTZ 的转换入口均需 Connection(真库实测:无参 stringValue/timestampValue 报
 * "Conversion failed"、toJdbc 返回自身),故不纳入归一——Map/VO 仍是驱动对象,需要可读值时
 * 走 SQL 端 to_char 或 rs.getTimestamp。本测试锁定该边界,防止误以为 LTZ 已归一。
 *
 * <p>注:ojdbc11 在 orm-core 为 provided 作用域,测试类路径可见,但无法构造无连接的真实对象,
 * 故只验证方法解析结果(可构造环境下由 verify 工程真库用例覆盖取值)。
 *
 * @author zsy 2026-11-22
 */
public class OracleTzNormalizeTest {

	@Test
	public void timestamptzResolvesOffsetDateTimeValue() throws Exception {
		Class<?> clazz = Class.forName("oracle.sql.TIMESTAMPTZ");
		Method method = ResultUtils.resolveOracleTzMethod(clazz);
		assertNotNull(method, "TIMESTAMPTZ 应解析出无参转换方法");
		assertEquals("offsetDateTimeValue", method.getName(), "TIMESTAMPTZ 用无参 offsetDateTimeValue");
	}

	@Test
	public void timestampltzHasNoConnectionFreeConverter() throws Exception {
		Class<?> clazz = Class.forName("oracle.sql.TIMESTAMPLTZ");
		// 只有 offsetDateTimeValue(Connection);无参 offsetDateTimeValue 不存在
		boolean hasNoArg = true;
		try {
			clazz.getMethod("offsetDateTimeValue");
		} catch (NoSuchMethodException e) {
			hasNoArg = false;
		}
		org.junit.jupiter.api.Assertions.assertFalse(hasNoArg,
				"若 ojdbc 为 TIMESTAMPLTZ 增加无参 offsetDateTimeValue,可移除边界并纳入归一");
		assertNull(ResultUtils.resolveOracleTzMethod(clazz), "TIMESTAMPLTZ 不纳入归一(需会话上下文)");
	}

	@Test
	public void unknownClassReturnsNull() {
		assertNull(ResultUtils.resolveOracleTzMethod(null));
		assertNull(ResultUtils.resolveOracleTzMethod(String.class));
	}
}
