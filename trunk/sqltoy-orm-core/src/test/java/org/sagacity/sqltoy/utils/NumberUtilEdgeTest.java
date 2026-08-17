package org.sagacity.sqltoy.utils;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;

import org.junit.jupiter.api.Test;
import org.sagacity.sqltoy.utils.NumberUtil.Pattern;

/**
 * 回归测试：(a)UOM单位数组32项,最大支持33位数字,34位起给出明确IllegalArgumentException
 * 而非数组越界,公共format入口优雅降级返回原值;(b)英文金额以"."结尾按无小数处理, 不再new
 * BigDecimal("")抛NumberFormatException
 */
public class NumberUtilEdgeTest {

	private static String repeat(char c, int count) {
		StringBuilder sb = new StringBuilder(count);
		for (int i = 0; i < count; i++) {
			sb.append(c);
		}
		return sb.toString();
	}

	@Test
	public void overlongNumberGivesClearErrorNotArrayBounds() throws Exception {
		// 32项UOM数组可覆盖33位(最高单位"京"),34位起越界
		String overlong = repeat('1', 34);
		Method method = NumberUtil.class.getDeclaredMethod("numberToChina", String.class, boolean.class);
		method.setAccessible(true);
		// 反射调用异常被包装为InvocationTargetException,解包验证根因
		InvocationTargetException wrapped = assertThrows(InvocationTargetException.class,
				() -> method.invoke(null, overlong, false));
		Throwable cause = wrapped.getCause();
		assertEquals(IllegalArgumentException.class, cause.getClass(), "修复前为ArrayIndexOutOfBoundsException");
		assertTrue(cause.getMessage().contains("中文单位覆盖范围"), "实际:" + cause.getMessage());
	}

	@Test
	public void boundary33DigitsStillWorks() {
		// 33位(含"京"单位)是支持上限,行为不受防护影响
		String boundary = repeat('1', 33);
		String result = NumberUtil.format(boundary, Pattern.CAPITAL);
		assertTrue(result.startsWith("一京"), "实际:" + result);
	}

	@Test
	public void formatDegradesGracefullyOnOverlong() {
		String overlong = repeat('1', 34);
		// 公共入口整体捕获异常,返回原值(修复前日志中是难以排查的数组越界)
		assertEquals(overlong, NumberUtil.format(overlong, Pattern.CAPITAL));
	}

	@Test
	public void trailingDotEnglishMoneyTreatedAsInteger() {
		// 修复前:new BigDecimal("")抛NumberFormatException
		String result = assertDoesNotThrow(() -> NumberUtil.convertToEnglishMoney("123."));
		assertTrue(result.contains("ONE HUNDRED AND TWENTY-THREE"), "实际:" + result);
		assertTrue(!result.contains("CENTS"));
	}

	@Test
	public void normalEnglishMoneyUnchanged() {
		String result = NumberUtil.convertToEnglishMoney("123.45");
		assertTrue(result.contains("ONE HUNDRED AND TWENTY-THREE"), "实际:" + result);
		assertTrue(result.contains("AND CENTS FORTY-FIVE"), "实际:" + result);
	}
}
