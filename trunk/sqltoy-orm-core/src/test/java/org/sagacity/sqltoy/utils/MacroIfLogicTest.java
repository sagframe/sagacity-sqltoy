package org.sagacity.sqltoy.utils;

import static org.junit.jupiter.api.Assertions.assertEquals;

import java.util.ArrayList;
import java.util.List;

import org.junit.jupiter.api.Test;

public class MacroIfLogicTest {

	@Test
	public void testInclude() {
		String sql = "size(:statusAry) >=4";
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
		String sql = ":status='1' || :status=='2'";
		List params = new ArrayList();
		params.add(2);
		params.add(2);
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
		String sql = ":status in '1,2,4'";
		List params = new ArrayList();
		params.add(2);
		boolean result = MacroIfLogic.evalLogic(sql, params, 0, params.size(), 1);
		assertEquals(result, true);
	}

	@Test
	public void testOut() {
		String sql = ":status out '1,2,4'";
		List params = new ArrayList();
		// params.add(2);
		params.add(3);
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
	public void testEndsWith() {
		String sql = "@(:status) endswith '011'";
		List params = new ArrayList();
		params.add("a1011");
		boolean result = MacroIfLogic.evalLogic(sql, params, 0, params.size(), 2);
		assertEquals(result, true);
	}
}
