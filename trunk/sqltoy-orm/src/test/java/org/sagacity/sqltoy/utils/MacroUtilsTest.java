/**
 * 
 */
package org.sagacity.sqltoy.utils;

import org.sagacity.sqltoy.model.IgnoreKeyCaseMap;
import org.sagacity.sqltoy.plugins.id.macro.MacroUtils;

/**
 * @author zhong
 *
 */
public class MacroUtilsTest {
	public static void main(String[] args) {
		String macroStr = "@substr(${corpId},0,2)-SE@substr(@day(yyMMdd),0,4)";
		IgnoreKeyCaseMap keyValues = new IgnoreKeyCaseMap();
		keyValues.put("corpId", "HX02");
		keyValues.put("bizDate", DateUtil.parseString("2020-02-12"));
		System.err.println(MacroUtils.replaceMacros(macroStr, keyValues));
	}
}
