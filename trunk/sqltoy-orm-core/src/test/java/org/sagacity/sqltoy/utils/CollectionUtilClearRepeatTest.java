package org.sagacity.sqltoy.utils;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertSame;

import java.util.ArrayList;
import java.util.List;

import org.junit.jupiter.api.Test;

/**
 * @project sagacity-sqltoy
 * @description 多字段 in 条件的去重：入参是"每个参数一列"的数组集合。判重按各参数值的文本比较(既有语义)，
 *              修复后不再依赖拼接出的字符串做键，参数值本身含 "},{" 时也不会把不同组合误判成重复。
 * @author zhongxuchen
 */
public class CollectionUtilClearRepeatTest {
	private static List<Object[]> tuples(Object[]... columns) {
		List<Object[]> list = new ArrayList<>();
		for (Object[] column : columns) {
			list.add(column);
		}
		return list;
	}

	@Test
	public void valuesContainingDelimiterAreNotMerged() {
		// 按旧的 ,{v} 拼接方式，("a","b},{c") 与 ("a},{b","c") 都会拼成 ",{a},{b},{c}"，修复前会被误判为同一组合
		List<Object[]> result = CollectionUtil.clearRepeat(
				tuples(new Object[] { "a", "a},{b" }, new Object[] { "b},{c", "c" }));
		assertEquals(2, result.get(0).length, "两个不同的参数组合都应保留");
		assertArrayEquals(new Object[] { "a", "a},{b" }, result.get(0));
		assertArrayEquals(new Object[] { "b},{c", "c" }, result.get(1));
	}

	@Test
	public void duplicateTuplesKeepFirstOccurrenceOrder() {
		List<Object[]> result = CollectionUtil.clearRepeat(tuples(new Object[] { 1, 2, 1 },
				new Object[] { "x", "y", "x" }, new Object[] { "k1", "k2", "k1" }));
		assertArrayEquals(new Object[] { 1, 2 }, result.get(0), "整个组合重复时只保留首次出现");
		assertArrayEquals(new Object[] { "x", "y" }, result.get(1));
		assertArrayEquals(new Object[] { "k1", "k2" }, result.get(2), "同名参数的取值随第一次出现的组合一起保留");
	}

	@Test
	public void textEqualitySemanticsAreKept() {
		// 既有语义：按文本判重，整数 1 与字符串 "1" 视为同一个组合；null 与字符串 "null" 同理
		List<Object[]> result = CollectionUtil.clearRepeat(
				tuples(new Object[] { Integer.valueOf(1), "1" }, new Object[] { "x", "x" }));
		assertEquals(1, result.get(0).length, "文本相同的组合仍按既有语义合并");
		List<Object[]> nullResult = CollectionUtil.clearRepeat(
				tuples(new Object[] { null, "null" }, new Object[] { "k", "k" }));
		assertEquals(1, nullResult.get(0).length, "null 仍按字符串\"null\"参与比较");
	}

	@Test
	public void fewerThanTwoTuplesReturnedAsIs() {
		List<Object[]> single = tuples(new Object[] { "only" });
		assertSame(single, CollectionUtil.clearRepeat(single), "不足两个组合时原样返回(不做去重)");
		List<Object[]> empty = new ArrayList<>();
		assertSame(empty, CollectionUtil.clearRepeat(empty));
		assertSame(null, CollectionUtil.clearRepeat(null));
	}
}