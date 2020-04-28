/**
 * 
 */
package org.sagacity.sqltoy.utils;

import org.sagacity.sqltoy.SqlToyConstants;

/**
 * @author zhong
 *
 */
public class StringUtilsTest {
	public static void main(String[] args) {
		String tmp = CommonUtils.readFileAsString("classpath:scripts/function.txt", "UTF-8");
		String[] strs = StringUtil.splitExcludeSymMark(tmp, ",", SqlToyConstants.filters);
		for (String s : strs) {
			System.err.println("[" + s.trim() + "]");
		}

	}
}
