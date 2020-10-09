package org.sagacity.sqltoy.utils;

import static org.junit.jupiter.api.Assertions.assertEquals;

import java.util.ArrayList;
import java.util.List;

import org.junit.jupiter.api.Test;
public class MacroIfLogicTest {

	@Test
	public void testInclude()
	{
		String sql="size(:statusAry) >=4";
		List params=new ArrayList();
		params.add(new Object[] {1,2});
		boolean result=MacroIfLogic.evalLogic(sql, params, 0, 1);
		assertEquals(result,true);
	}
	
	@Test
	public void testOr()
	{
		String sql=":status>='1' && :status<='3'";
		List params=new ArrayList();
		params.add(3);
		params.add(3);
		boolean result=MacroIfLogic.evalLogic(sql, params, 0, 2);
		assertEquals(result,true);
	}
}
