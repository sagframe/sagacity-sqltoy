/**
 * 
 */
package org.sagacity.sqltoy.utils;

import org.sagacity.sqltoy.SqlToyConstants;

/**
 * @author zhongxuchen
 *
 */
public class StringUtilsTest {
	public static void main(String[] args) {
		String source="#[testNum],'#,#0.00'";
		
		String[] result=StringUtil.splitExcludeSymMark(source, ",", SqlToyConstants.filters);
		for(String str:result)
		{
			System.err.println("["+str+"]");
		}
	}

}
