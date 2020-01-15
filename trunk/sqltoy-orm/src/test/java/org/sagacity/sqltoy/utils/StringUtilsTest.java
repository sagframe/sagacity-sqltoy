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
		String tmp = CommonUtils.readFileAsString("classpath:/showcase.txt", "UTF-8");
		String[] strs = StringUtil.splitExcludeSymMark(tmp, ",", SqlToyConstants.filters);
		for (String s : strs) {
			System.err.println("[" + s + "]");
		}
		// System.err.println(tmp);
		// String regex = "(^\")|([^\\\\]\")";
		// // String regex = "(^\\')|([^\\\\]\\')";
		// Pattern p = Pattern.compile(regex);
		// Matcher m = p.matcher(tmp);
		// while (m.find()) {
		// System.err.println(m.start());
		// System.err.println(m.group());
		// // System.err.println(m.group().substring(1));
		// // System.err.println(tmp.charAt(m.start() - 1));
		// }

	}
}
