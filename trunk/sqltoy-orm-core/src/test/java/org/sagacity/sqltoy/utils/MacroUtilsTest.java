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
		String macroStr = "@substr(${corpId},0,2)-@case(${periodType},L,@case(${longType},2,K,C),)@case(${tradeType},I,N,O,W,Z)@case(${orderType},P,C,S,X,B)@day(yyMMdd)";
		IgnoreKeyCaseMap keyValues = new IgnoreKeyCaseMap();
		keyValues.put("corpId", "HX02");
		keyValues.put("periodType", "S");
		keyValues.put("tradeType", "I");
		keyValues.put("orderType", "P");
		System.err.println(MacroUtils.replaceMacros(macroStr, keyValues));
	}
}
