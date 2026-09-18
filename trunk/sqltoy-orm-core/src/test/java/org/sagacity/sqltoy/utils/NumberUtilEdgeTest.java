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
 * 回归测试：(a)分组单位算法(万/亿/兆/京按二进制位组合)最大支持到10^64(64位),
 * 65位起给出明确IllegalArgumentException而非数组越界,公共format入口优雅降级返回原值;
 * (b)英文金额以"."结尾按无小数处理, 不再new BigDecimal("")抛NumberFormatException
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
		// 组单位序号按二进制位组合,上限16组即10^64,65位起越界
		String overlong = repeat('1', 65);
		Method method = NumberUtil.class.getDeclaredMethod("numberToChina", String.class, boolean.class);
		method.setAccessible(true);
		// 反射调用异常被包装为InvocationTargetException,解包验证根因
		InvocationTargetException wrapped = assertThrows(InvocationTargetException.class,
				() -> method.invoke(null, overlong, false));
		Throwable cause = wrapped.getCause();
		assertEquals(IllegalArgumentException.class, cause.getClass(), "修复前为ArrayIndexOutOfBoundsException");
		assertTrue(cause.getMessage().contains("10^64"), "实际:" + cause.getMessage());
	}

	@Test
	public void boundary33DigitsStillWorks() {
		// 33位(含"京"单位)远在新算法64位上限内,行为不受防护影响
		String boundary = repeat('1', 33);
		String result = NumberUtil.format(boundary, Pattern.CAPITAL);
		assertTrue(result.startsWith("一京"), "实际:" + result);
	}

	@Test
	public void formatDegradesGracefullyOnOverlong() {
		String overlong = repeat('1', 65);
		// 公共入口整体捕获异常,返回原值(修复前日志中是难以排查的数组越界)
		assertEquals(overlong, NumberUtil.format(overlong, Pattern.CAPITAL));
		// 34位起涉及"京"(10^32)复合单位,新算法在10^64范围内可正常转换
		String inRange = repeat('1', 34);
		String result = NumberUtil.format(inRange, Pattern.CAPITAL);
		assertTrue(result.contains("京"), "实际:" + result);
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
