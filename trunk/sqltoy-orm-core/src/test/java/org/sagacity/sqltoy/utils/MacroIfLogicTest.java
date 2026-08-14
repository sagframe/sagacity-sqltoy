package org.sagacity.sqltoy.utils;

import static org.junit.jupiter.api.Assertions.assertEquals;

import java.util.ArrayList;
import java.util.List;

import org.junit.jupiter.api.Test;

public class MacroIfLogicTest {

	@Test
	public void testArySize() {
		String sql = "!size(:statusAry) >=4";
		List params = new ArrayList();
		params.add(new Object[] { 1, 2 });
		boolean result = MacroIfLogic.evalLogic(sql, params, 0, 1, 1);
		assertEquals(result, true);
	}

	@Test
	public void testInclude() {
		String sql = "!:statusAry include 4 ";
		List params = new ArrayList();
		params.add(new Object[] { 1, 2 });
		boolean result = MacroIfLogic.evalLogic(sql, params, 0, 1, 1);
		assertEquals(result, true);
	}

	@Test
	public void testAnd() {
		String sql = ":status>='1' && :status<='3'";
		List params = new ArrayList();
		params.add(3);
		params.add(3);
		boolean result = MacroIfLogic.evalLogic(sql, params, 0, 2, 1);
		assertEquals(result, true);
	}

	@Test
	public void testOr() {
		String sql = ":status!='1' || :status=='3'||:status=='2'";
		List params = new ArrayList();
		params.add(2);
		params.add(2);
		params.add(2);
		boolean result = MacroIfLogic.evalLogic(sql, params, 0, params.size(), 1);
		assertEquals(result, true);
	}

	// sqltoy @if() 逻辑兼容=和==场景
	@Test
	public void testEqual() {
		String sql = ":status='1' || :status == '2'";
		List params = new ArrayList();
		params.add(2);
		params.add(2);
		boolean result = MacroIfLogic.evalLogic(sql, params, 0, params.size(), 1);
		assertEquals(result, true);
	}

	@Test
	public void testBoolEqual() {
		String sql = ":status !=null && :status";
		List params = new ArrayList();
		params.add(true);
		params.add(true);
		boolean result = MacroIfLogic.evalLogic(sql, params, 0, params.size(), 1);
		assertEquals(result, true);
	}

	@Test
	public void testNotEqual() {
		String sql = ":status!='1' || :status<>'2'";
		List params = new ArrayList();
		params.add(2);
		params.add(2);
		boolean result = MacroIfLogic.evalLogic(sql, params, 0, params.size(), 1);
		assertEquals(result, true);
	}

	@Test
	public void testIn() {
		String sql = ":status in (1,2,4)";
		List params = new ArrayList();
		params.add(2);
		boolean result = MacroIfLogic.evalLogic(sql, params, 0, params.size(), 1);
		assertEquals(result, true);
	}

	@Test
	public void testInAllArgs() {
		String sql = ":status in (:statusList)";
		List params = new ArrayList();
		List item2List = new ArrayList();
		item2List.add(1);
		item2List.add(2);
		item2List.add(4);
		params.add(2);
		params.add(item2List);
		boolean result = MacroIfLogic.evalLogic(sql, params, 0, params.size(), 1);
		assertEquals(result, true);
	}

	@Test
	public void testMoreIn() {
		String sql = ":status in (1,2,4) || :type in (3,4,5)";
		List params = new ArrayList();
		params.add(5);
		params.add(4);
		boolean result = MacroIfLogic.evalLogic(sql, params, 0, params.size(), 1);
		assertEquals(result, true);
	}

	@Test
	public void testOut() {
		String sql = ":status out (1,2,4)";
		List params = new ArrayList();
		params.add(3);
		// params.add(3);
		boolean result = MacroIfLogic.evalLogic(sql, params, 0, params.size(), 1);
		assertEquals(result, true);
	}

	@Test
	public void testSplit() {
		String sql = ":status   '1,2,4'";
		String[] params = sql.split("\\s+");
		for (String str : params) {
			System.err.println("[" + str + "]");
		}
	}

	@Test
	public void testStartsWith() {
		String sql = ":status startswith 'a10'";
		List params = new ArrayList();
		params.add("a1011");
		boolean result = MacroIfLogic.evalLogic(sql, params, 0, params.size(), 1);
		assertEquals(result, true);
	}

	@Test
	public void testSize() {
		String sql = "size(:status)==2";
		List params = new ArrayList();
		params.add(new int[] { 1, 2 });
		boolean result = MacroIfLogic.evalLogic(sql, params, 0, params.size(), 1);
		assertEquals(result, true);
	}

	@Test
	public void testSize1() {
		String sql = "size(:status)==5";
		List params = new ArrayList();
		params.add("S0001");
		boolean result = MacroIfLogic.evalLogic(sql, params, 0, params.size(), 1);
		assertEquals(result, true);
	}

	@Test
	public void testTimeCompare() {
		String sql = ":bizDate>=:preBizDate-3600s";
		List params = new ArrayList();
		params.add(DateUtil.parseLocalDateTime("2026-6-1 18:20:00"));
		params.add(DateUtil.parseLocalDateTime("2026-6-1 19:20:00"));
		boolean result = MacroIfLogic.evalLogic(sql, params, 0, params.size(), 1);
		assertEquals(result, true);
	}

	@Test
	public void testNumCompare() {
		String sql = ":amt1>=(15600-:amt2)";
		List params = new ArrayList();
		params.add(700);
		params.add(15000);
		boolean result = MacroIfLogic.evalLogic(sql, params, 0, params.size(), 1);
		assertEquals(result, true);
	}

	@Test
	public void testEndsWith() {
		String sql = "@(:status) endswith '011'";
		List params = new ArrayList();
		params.add("a1011");
		boolean result = MacroIfLogic.evalLogic(sql, params, 0, params.size(), 2);
		assertEquals(result, true);
	}
}
