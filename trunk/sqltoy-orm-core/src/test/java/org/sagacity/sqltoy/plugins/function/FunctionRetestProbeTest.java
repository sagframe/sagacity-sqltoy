package org.sagacity.sqltoy.plugins.function;

import static org.junit.jupiter.api.Assertions.assertEquals;

import java.util.Arrays;

import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

/**
 * 回归锁定:复测确认的四个缺陷修复——
 * D2:串首裸函数(整条sql即函数表达式)永不转换(引擎入口哨兵补偿);
 * D3:group_concat的separator关键字紧贴参数首位时被当拼接列(matcher定位+哨兵探测);
 * D4:java风格格式串'HH:mm:ss'到pg系/sqlite原样透传或token残留(时间token归一);
 * D5:日期格式含字面数字('yyyy-MM-dd 00:00:00')被误判数值模型转CAST DECIMAL(模型判别精化)
 */
public class FunctionRetestProbeTest {

	@BeforeAll
	public static void init() {
		FunctionUtils.setFunctionConverts(Arrays.asList("default"));
	}

	@Test
	public void d2BareLeadingFunctionConverts() {
		// 整串即裸函数:入口哨兵补偿\W前缀缺失,正常转换
		assertEquals("nvl(a,b)", FunctionUtils.getDialectSql("ifnull(a,b)", "oracle"), "串首裸ifnull应转nvl");
	}

	@Test
	public void d3SeparatorAtSegmentStart() {
		// separator紧贴参数首位:不再被当拼接列,正确提取为分隔符
		assertEquals("select  listagg(a,'-') within group (order by null)  from t",
				FunctionUtils.getDialectSql("select group_concat(a,separator '-') from t", "oracle"),
				"separator应提取为listagg分隔符");
		// 常规"expr separator sep"形态既有行为保持
		assertEquals("select  listagg(a,'-') within group (order by null)  from t",
				FunctionUtils.getDialectSql("select group_concat(a separator '-') from t", "oracle"),
				"常规separator形态行为保持");
	}

	@Test
	public void d4JavaStyleTimeTokens() {
		// java的HH=24小时/mm=分钟,到pg系归一为oracle模型(hh24/mi),不再原样透传错值
		assertEquals("select to_char(t,'yyyy-MM-dd hh24:mi:ss') from t",
				FunctionUtils.getDialectSql("select date_format(t,'yyyy-MM-dd HH:mm:ss') from t", "postgresql"),
				"date_format的java风格时间token应归一");
		assertEquals("select to_char(t,'yyyy-MM-dd hh24:mi:ss') from t",
				FunctionUtils.getDialectSql("select to_char(t,'yyyy-MM-dd HH:mm:ss') from t", "postgresql"),
				"to_char的java风格时间token应归一");
		// sqlite:归一后HH:mm:ss完整映射为%H:%M:%S(24小时)
		assertEquals("select strftime('%Y-%m-%d %H:%M:%S',datetime(t/1000,'unixepoch','localtime')) from t",
				FunctionUtils.getDialectSql("select to_char(t,'yyyy-MM-dd HH:mm:ss') from t", "sqlite"),
				"sqlite的java风格时间token应完整映射");
		// oracle原生形态(含裸mm=月)不受归一影响
		assertEquals("select to_char(t,'yyyy-mm-dd hh24:mi:ss') from t",
				FunctionUtils.getDialectSql("select to_char(t,'yyyy-mm-dd hh24:mi:ss') from t", "postgresql"),
				"oracle原生模型保持透传");
	}

	@Test
	public void d5NumericModelDiscrimination() {
		// 日期格式含字面数字:不再误判数值模型(日期token正常转换,00:00:00字面保留)
		assertEquals("select date_format(t,'%Y-%m-%d 00:00:00') from t",
				FunctionUtils.getDialectSql("select to_char(t,'yyyy-MM-dd 00:00:00') from t", "mysql"),
				"含00:00:00的日期格式应走日期分支");
		// 真数值模型保持CAST行为
		assertEquals("select CAST(t AS DECIMAL(20,2)) from t",
				FunctionUtils.getDialectSql("select to_char(t,'999.99') from t", "mysql"),
				"纯数值模型保持CAST");
		// 数值模型MI后缀('999MI')不得因mi误判为日期
		assertEquals("select CAST(t AS DECIMAL(20,0)) from t",
				FunctionUtils.getDialectSql("select to_char(t,'999MI') from t", "mysql"),
				"数值MI后缀不得误判为日期模型");
	}

	@Test
	public void negativeStartSubstrEmptyStringContract() {
		// update 2026-10-9 负起点|n|超串长对齐mysql返回空串:两参RIGHT/三参substring均包CASE守卫
		// pg系两参(length函数形态)
		assertEquals("select case when length(name)>=2 then RIGHT(name,2) else '' end from t",
				FunctionUtils.getDialectSql("select substr(name,-2) from t", "postgresql"),
				"pg两参负起点应为守卫RIGHT形态");
		// pg系三参:短串分支返回空串,否则起点=串长-n+1
		assertEquals("select case when length(name)<5 then '' else substring(name,length(name)-5+1,3) end from t",
				FunctionUtils.getDialectSql("select substr(name,-5,3) from t", "postgresql"),
				"pg三参负起点短串分支应为空串");
		// sqlserver(len函数形态)
		assertEquals("select case when len(name)>=2 then RIGHT(name,2) else '' end from t",
				FunctionUtils.getDialectSql("select substr(name,-2) from t", "sqlserver"),
				"sqlserver两参负起点应为len守卫形态");
		// db2/hana同契约(db2产物中的length会被Length转换器按db2字符语义二次转为char_length)
		assertEquals("select case when char_length(name)>=2 then RIGHT(name,2) else '' end from t",
				FunctionUtils.getDialectSql("select substr(name,-2) from t", "db2"), "db2两参负起点应为守卫形态");
		assertEquals("select case when length(name)>=2 then RIGHT(name,2) else '' end from t",
				FunctionUtils.getDialectSql("select substr(name,-2) from t", "hana"), "hana两参负起点应为守卫形态");
		assertEquals("select case when length(name)<5 then '' else substring(name,length(name)-5+1,3) end from t",
				FunctionUtils.getDialectSql("select substr(name,-5,3) from t", "hana"),
				"hana三参负起点短串分支应为空串");
		// 非负起点行为保持
		assertEquals("select substring(name,2) from t",
				FunctionUtils.getDialectSql("select substr(name,2) from t", "postgresql"),
				"非负起点保持substring换名");
	}
}
