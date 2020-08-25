package org.sagacity.sqltoy.utils;

import static org.junit.jupiter.api.Assertions.assertEquals;

import java.util.ArrayList;
import java.util.List;

import org.junit.jupiter.api.Test;
public class MacroIfLogicTest {

	@Test
	public void testInclude()
	{
		String sql=":statusAry include 1";
		List params=new ArrayList();
		params.add(new Object[] {1,2});
		boolean result=MacroIfLogic.evalLogic(sql, params, 0, 1);
		assertEquals(result,true);
	}
}
