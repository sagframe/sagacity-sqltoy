package org.sagacity.sqltoy.plugins;

import static org.junit.jupiter.api.Assertions.assertEquals;

import java.util.Date;

import org.junit.jupiter.api.Test;
import org.sagacity.sqltoy.plugins.id.macro.impl.DateFormat;
import org.sagacity.sqltoy.utils.DateUtil;

/**
 * @df/@date主键宏的格式串处理:传入的格式串可能带单引号或双引号(@df(:createTime,'yyyyMMdd')),
 * 宏需剔除引号后再格式化;引号剔除由正则改为String.replace(纯字面量)后行为必须不变
 */
public class DateFormatMacroTest {

	private static final String DATE = "2026-09-14 10:20:30";

	@Test
	public void doubleQuotedFormat() {
		assertEquals("2026/09/14 10:20:30",
				execute(new String[] { DATE, "\"yyyy/MM/dd HH:mm:ss\"" }));
	}

	@Test
	public void singleQuotedFormat() {
		assertEquals("20260914102030", execute(new String[] { DATE, "'yyyyMMddHHmmss'" }));
	}

	@Test
	public void unquotedFormat() {
		assertEquals("2026-09-14", execute(new String[] { DATE, "yyyy-MM-dd" }));
	}

	@Test
	public void mixedAndInnerQuotesAreAllRemoved() {
		assertEquals("2026-09-14", execute(new String[] { DATE, "\"'yyyy-MM-dd'\"" }));
	}

	@Test
	public void blankFormatReturnsEmpty() {
		assertEquals("", execute(new String[] { DATE, "\"\"" }));
		assertEquals("", execute(new String[] { DATE, "'  '" }));
	}

	@Test
	public void nullFormatReturnsEmpty() {
		assertEquals("", execute(new String[] { DATE, "null" }));
		assertEquals("", execute(new String[] { DATE, "NULL" }));
	}

	@Test
	public void singleParamUsesCurrentDate() {
		String result = execute(new String[] { "yyyy" });
		assertEquals(4, result.length(), "实际:" + result);
		// 与当前年份一致(仅跨年毫秒窗口可能失败)
		assertEquals(DateUtil.getYear(new Date()), Integer.parseInt(result));
	}

	private static String execute(String[] params) {
		return new DateFormat().execute(params, null, null, null, null);
	}
}
