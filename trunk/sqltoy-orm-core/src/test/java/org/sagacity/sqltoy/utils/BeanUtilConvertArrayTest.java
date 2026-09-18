package org.sagacity.sqltoy.utils;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertSame;

import java.math.BigDecimal;

import org.junit.jupiter.api.Test;

/**
 * 回归测试：convertArray用反射Array统一处理原始类型数组和对象数组,
 * 修复前(Object[])强转在所有分支之前,int[]等原始数组输入必然ClassCastException
 */
public class BeanUtilConvertArrayTest {

	@Test
	public void primitiveInputNoLongerThrowsCce() {
		// 修复前:此处直接ClassCastException,后面的int[]分支永不可达
		assertArrayEquals(new Integer[] { 1, 2, 3 },
				(Integer[]) BeanUtil.convertArray(new int[] { 1, 2, 3 }, "java.lang.Integer[]"));
		assertArrayEquals(new Long[] { 5L, 6L },
				(Long[]) BeanUtil.convertArray(new long[] { 5L, 6L }, "java.lang.Long[]"));
		assertArrayEquals(new String[] { "7", "8" },
				(String[]) BeanUtil.convertArray(new int[] { 7, 8 }, "java.lang.String[]"));
	}

	@Test
	public void boxedInputToPrimitiveTarget() {
		assertArrayEquals(new int[] { 1, 2 }, (int[]) BeanUtil.convertArray(new Integer[] { 1, 2 }, "int[]"));
		assertArrayEquals(new long[] { 3, 4 }, (long[]) BeanUtil.convertArray(new Long[] { 3L, 4L }, "long[]"));
	}

	@Test
	public void boxedInputWithNullToPrimitiveTarget() {
		// null元素跳过,原始类型数组保持默认值0(与原实现语义一致)
		assertArrayEquals(new int[] { 1, 0 }, (int[]) BeanUtil.convertArray(new Integer[] { 1, null }, "int[]"));
	}

	@Test
	public void objectArrayToTypedArray() {
		assertArrayEquals(new String[] { "1", "a" },
				(String[]) BeanUtil.convertArray(new Object[] { 1, "a" }, "java.lang.String[]"));
		assertArrayEquals(new BigDecimal[] { new BigDecimal("1.5"), new BigDecimal("2.5") },
				(BigDecimal[]) BeanUtil.convertArray(new String[] { "1.5", "2.5" }, "java.math.BigDecimal[]"));
	}

	@Test
	public void sameTypeReturnsOriginalInstance() {
		String[] input = new String[] { "x" };
		assertSame(input, BeanUtil.convertArray(input, "java.lang.String[]"));
		int[] ints = new int[] { 1 };
		assertSame(ints, BeanUtil.convertArray(ints, "int[]"));
	}

	@Test
	public void unsupportedTargetTypeReturnsOriginal() {
		String[] input = new String[] { "x" };
		assertSame(input, BeanUtil.convertArray(input, "java.time.LocalDate[]"));
	}
}
