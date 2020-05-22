/**
 * 
 */
package org.sagacity.sqltoy.utils;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;

import org.junit.jupiter.api.Test;
import org.sagacity.sqltoy.SqlToyConstants;

/**
 * @author zhongxuchen
 *
 */
public class StringUtilsTest {

	@Test
	public void testSplitExcludeSymMark1() {
		String source = "#[testNum],'#,#0.00'";
		String[] result = StringUtil.splitExcludeSymMark(source, ",", SqlToyConstants.filters);
		assertArrayEquals(result, new String[] { "#[testNum]", "'#,#0.00'" });
		source = ",'#,#0.00'";
		result = StringUtil.splitExcludeSymMark(source, ",", SqlToyConstants.filters);
		assertArrayEquals(result, new String[] { "", "'#,#0.00'" });

		source = "'\\'', t.`ORGAN_ID`, '\\''";
		result = StringUtil.splitExcludeSymMark(source, ",", SqlToyConstants.filters);
		assertArrayEquals(result, new String[] { "'\\''", " t.`ORGAN_ID`", " '\\''" });

		source = "orderNo,<td align=\"center\" rowspan=\"#[group('orderNo,').size()]\">,@dict(EC_PAY_TYPE,#[payType])</td>";
		result = StringUtil.splitExcludeSymMark(source, ",", SqlToyConstants.filters);
		assertArrayEquals(result,
				new String[] { "orderNo", "<td align=\"center\" rowspan=\"#[group('orderNo,').size()]\">",
						"@dict(EC_PAY_TYPE,#[payType])</td>" });
		source = "reportId=\"RPT_DEMO_005\",chart-index=\"1\",style=\"width:49%;height:350px;display:inline-block;\"";
		result = StringUtil.splitExcludeSymMark(source, ",", SqlToyConstants.filters);
		assertArrayEquals(result, new String[] { "reportId=\"RPT_DEMO_005\"", "chart-index=\"1\"",
				"style=\"width:49%;height:350px;display:inline-block;\"" });
		source = "a,\"\"\",\",a";
		result = StringUtil.splitExcludeSymMark(source, ",", SqlToyConstants.filters);
		for (String s : result) {
			System.err.println("[" + s.trim() + "]");
		}
		assertArrayEquals(result, new String[] { "a", "\"\"\",\"", "a" });
	}

}
