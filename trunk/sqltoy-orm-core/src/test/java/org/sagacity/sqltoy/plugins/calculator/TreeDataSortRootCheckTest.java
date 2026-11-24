package org.sagacity.sqltoy.plugins.calculator;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.math.BigDecimal;
import java.sql.Timestamp;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Date;
import java.util.List;

import org.junit.jupiter.api.Test;

/**
 * @project sagacity-sqltoy
 * @description 树形排序根节点判定的诊断：根节点取"pid 不在任何 id 中"的行(文档示例里根节点的 pid 本身就是结果集之外的取值)，
 *              当 id 与 pid 的取值类型完全无法匹配时，所有行都会被当成根、逐层汇总不会发生，此时需要给出告警。
 *              本用例只校验诊断判定本身，不改变任何排序结果。
 * @author zhongxuchen
 */
public class TreeDataSortRootCheckTest {
	private static List<List<Object>> rows(Object[]... values) {
		List<List<Object>> rows = new ArrayList<>();
		for (Object[] value : values) {
			rows.add(new ArrayList<>(Arrays.asList(value)));
		}
		return rows;
	}

	@Test
	public void sameTypeIsCompatible() {
		assertFalse(TreeDataSort.hasIncompatibleIdPidTypes(rows(new Object[] { 1L, 0L }), 0, 1));
		assertFalse(TreeDataSort.hasIncompatibleIdPidTypes(
				rows(new Object[] { "a", "b" }, new Object[] { "b", null }), 0, 1));
	}

	@Test
	public void boxedNumberTypesAreIncompatible() {
		// Long 与 Integer 的 equals 永远不成立：树建立不起来
		assertTrue(TreeDataSort.hasIncompatibleIdPidTypes(
				rows(new Object[] { 1L, 0 }, new Object[] { 2L, 1 }), 0, 1));
		assertTrue(TreeDataSort.hasIncompatibleIdPidTypes(
				rows(new Object[] { new BigDecimal("1"), 0L }), 0, 1));
	}

	@Test
	public void assignableTypesAreCompatible() {
		// 时间戳作 id、日期作 pid：pid.equals(id) 可以成立，实际树能建立，不应报不兼容
		assertFalse(TreeDataSort.hasIncompatibleIdPidTypes(
				rows(new Object[] { new Timestamp(0L), new Date(0L) }), 0, 1));
	}

	@Test
	public void partiallyOverlappingTypesAreCompatible() {
		assertFalse(TreeDataSort.hasIncompatibleIdPidTypes(
				rows(new Object[] { 1L, 0 }, new Object[] { "a", 1L }), 0, 1), "存在同类型取值即可挂接");
	}

	@Test
	public void nullOnlyColumnsCanNotBeJudged() {
		assertFalse(TreeDataSort.hasIncompatibleIdPidTypes(rows(new Object[] { 1L, null }), 0, 1));
		assertFalse(TreeDataSort.hasIncompatibleIdPidTypes(rows(new Object[] { null, 1L }), 0, 1));
	}
}