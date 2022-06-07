/**
 * 
 */
package org.sagacity.sqltoy.plugins;

import java.util.HashMap;
import java.util.Map;

import org.junit.jupiter.api.Test;
import org.sagacity.sqltoy.config.SqlConfigParseUtils;
import org.sagacity.sqltoy.model.IgnoreKeyCaseMap;
import org.sagacity.sqltoy.plugins.id.macro.AbstractMacro;
import org.sagacity.sqltoy.plugins.id.macro.MacroUtils;
import org.sagacity.sqltoy.plugins.id.macro.impl.SqlLoop;

/**
 * @project sagacity-sqltoy
 * @description 请在此说明类的功能
 * @author zhong
 * @version v1.0, Date:2020-9-23
 * @modify 2020-9-23,修改说明
 */
public class MacroUtilsTest {
	@Test
	public void testReplaceMacros() {
		Map<String, AbstractMacro> macros = new HashMap<String, AbstractMacro>();

		macros.put("@loop", new SqlLoop());
		String sql = "select * from table where 1=1 @loop(:startDates,'(bizDate between :startDates[i] and :endDates[i])',or)";
		String[] paramsNamed = { "startDates", "endDates" };
		String[][] paramsValue = { { "2020-10-01", "2020-11-01", "2020-12-01" },
				{ "2020-10-30", "2020-11-30", "2020-12-30" } };
		IgnoreKeyCaseMap<String, Object> keyValues = new IgnoreKeyCaseMap<String, Object>();
		for (int i = 0; i < paramsNamed.length; i++) {
			keyValues.put(paramsNamed[i], paramsValue[i]);
		}

		String result = MacroUtils.replaceMacros(sql, keyValues, false, macros);
		System.err.println(result);
	}

	@Test
	public void testReplaceMacros1() {
		Map<String, AbstractMacro> macros = new HashMap<String, AbstractMacro>();

		macros.put("@loop", new SqlLoop());
		String sql = "select * from table where 1=1 @loop(:startDates,' (bizDate between str_to_date(':startDates[i]','%Y-%m-%d') and str_to_date(':endDates[i]','%Y-%m-%d')','or')";
		String[] paramsNamed = { "startDates", "endDates" };
		String[][] paramsValue = { { "2020-10-01", "2020-11-01", "2020-12-01" },
				{ "2020-10-30", "2020-11-30", "2020-12-30" } };
		IgnoreKeyCaseMap<String, Object> keyValues = new IgnoreKeyCaseMap<String, Object>();
		for (int i = 0; i < paramsNamed.length; i++) {
			keyValues.put(paramsNamed[i], paramsValue[i]);
		}

		String result = MacroUtils.replaceMacros(sql, keyValues, false, macros);
		System.err.println(result);
	}

	@Test
	public void testReplaceMacros2() {
		Map<String, AbstractMacro> macros = new HashMap<String, AbstractMacro>();

		macros.put("@loop", new SqlLoop(true));
		macros.put("@loop-full", new SqlLoop(false));
		String sql = "select * from table where 1=1 @loop(:startDates,' (bizDate between str_to_date(':startDates[i]','%Y-%m-%d') and str_to_date(':endDates[i]','%Y-%m-%d'))',or)"
				+ " (@loop-full(:startDates,' (bizDate between str_to_date(':startDates[i]','%Y-%m-%d') and str_to_date(':endDates[i]','%Y-%m-%d'))',or))";
		String[] paramsNamed = { "startDates", "endDates" };
		String[][] paramsValue = { { "2020-10-01", null },
				{ "2020-10-30", null } };
		IgnoreKeyCaseMap<String, Object> keyValues = new IgnoreKeyCaseMap<String, Object>();
		for (int i = 0; i < paramsNamed.length; i++) {
			keyValues.put(paramsNamed[i], paramsValue[i]);
		}

		String result = MacroUtils.replaceMacros(sql, keyValues, false, macros);
		System.err.println(result);
	}
	
	@Test
	public void testGetNames() {
		String sql = "select * from table where 1=1 @loop(:startDates,'or (bizDate between :startDates[i] and :endDates[i])')";
		String[] args = SqlConfigParseUtils.getSqlParamsName(sql, false);
		for (String str : args) {
			System.err.println(str);
		}
	}

}
