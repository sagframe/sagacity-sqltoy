package org.sagacity.sqltoy.plugins.function;

import static org.junit.jupiter.api.Assertions.assertEquals;

import org.junit.jupiter.api.Test;

/**
 * toMySqlFormat 的 mm 上下文语义锁定(2026-11-11 v3,lastDirective 跨字面分隔符):
 * 大写MM恒为月份(java月份也是MM);小写mm在"最近的directive为%H"(HH/HH24/hh24,java 24小时上下文)
 * 时为分钟——字面分隔符(冒号/连字符/空格/中文等)不切断该上下文(HHmmss/HHmm/HH:mm/HH-mm/HH mm/HH时mm分),
 * 但任何其它directive(日期/年份/小写hh等)会切换上下文;其余(Oracle的HHMM/hhmm大小写不敏感=小时+月份、
 * 串首mm、日期后mm)为月份;%风格串原样返回;宏求值层(java DateTimeFormatter)不经此层不受影响
 */
public class ToMySqlFormatContextTest {

	private static String fmt(String formatArg) {
		return FunctionUtils.toMySqlFormat(formatArg);
	}

	@Test
	public void mmAfterHourIsMinute() {
		assertEquals("'%H%i%s'", fmt("'HHmmss'"), "HHmmss的mm紧邻HH,按java时间上下文映射分钟");
		assertEquals("'%H%i'", fmt("'HHmm'"), "HHmm的mm紧邻HH,映射分钟");
		assertEquals("'%H%i'", fmt("'hh24mm'"), "hh24后的mm紧邻%H,映射分钟");
	}

	@Test
	public void hhmmOracleStyleIsMonth() {
		// Oracle格式token大小写不敏感:HHMM/hhmm=小时+月份(2026-11-10 v2收紧,v1曾误判成小时+分钟)
		assertEquals("'%H%m'", fmt("'HHMM'"), "Oracle的HHMM=小时+月份");
		assertEquals("'%H%m%s'", fmt("'HHMMss'"), "Oracle的HHMMss=小时+月份+秒");
		assertEquals("'%h%m%s'", fmt("'hhmmss'"), "小写hh后的mm按Oracle解释为月份(java 12小时+分钟形态牺牲,正写hh24mi/HHmm)");
	}

	@Test
	public void mmOtherwiseIsMonth() {
		assertEquals("'%Y%m%d'", fmt("'yyyymmdd'"), "日期上下文的mm映射月份");
		assertEquals("'%m'", fmt("'mm'"), "单独mm(月份分组意图)映射月份");
		assertEquals("'%d%m'", fmt("'ddmm'"), "dd后的mm映射月份");
		assertEquals("'%Y%m'", fmt("'yyyyMM'"), "大写MM映射月份");
	}

	@Test
	public void mixedCaseMonthStillMapped() {
		// 历史问题:大小写分治只认精确MM/mm,混合大小写落到逐字符输出,'%YMm%d'会让MySQL把Mm当字面量输出
		assertEquals("'%Y%m%d'", fmt("'yyyyMmdd'"), "混合大小写Mm仍须映射月份,不得原样输出");
		assertEquals("'%Y-%m-%d'", fmt("'yyyy-Mm-dd'"), "带分隔符的混合大小写Mm同样映射月份");
		assertEquals("'%Y-%m-%d'", fmt("'yyyy-mM-dd'"), "mM与Mm同义");
	}

	@Test
	public void colonJavaFormsKept() {
		assertEquals("'%H:%i:%s'", fmt("'HH:mm:ss'"), "冒号java时间形态分钟既有行为不变");
		assertEquals("'%h:%i'", fmt("'hh:mm'"), "冒号hh:mm分钟既有行为不变");
	}

	@Test
	public void percentStylePassedThrough() {
		assertEquals("'%Y-%m-%d'", fmt("'%Y-%m-%d'"), "%风格串原样返回(DateFormat分支幂等兜底)");
	}

	@Test
	public void nonLiteralPassedThrough() {
		assertEquals(":fmt", fmt(":fmt"), "占位符原样返回");
	}

	@Test
	public void nonColonSeparatorAfterHourIsMinute() {
		// 非冒号分隔符不切断24小时上下文:HH后的mm仍是分钟(真库实测曾误判月份)
		assertEquals("'%H时%i分%s秒'", fmt("'HH时mm分ss秒'"), "中文分隔符HH时mm分ss秒的mm映射分钟");
		assertEquals("'%H-%i'", fmt("'HH-mm'"), "连字符分隔HH-mm的mm映射分钟");
		assertEquals("'%H %i'", fmt("'HH mm'"), "空格分隔HH mm的mm映射分钟");
		assertEquals("'%H-%i-%s'", fmt("'HH-mm-ss'"), "多段连字符时间形态mm映射分钟");
	}

	@Test
	public void dateContextWithSeparatorStillMonth() {
		// 对照:日期上下文即使带分隔符,mm仍是月份(修复不得过度泛化到非24小时上下文)
		assertEquals("'%Y-%m-%d'", fmt("'yyyy-mm-dd'"), "日期yyyy-mm-dd的mm映射月份");
		assertEquals("'%d-%m'", fmt("'dd-mm'"), "dd-mm的mm映射月份");
	}
}
