package org.sagacity.sqltoy.plugins.function;

import static org.junit.jupiter.api.Assertions.assertEquals;

import java.util.Arrays;

import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

/**
 * 回归锁定:函数转换引擎的两类缺陷修复——
 * 1、同名(含nvl/ifnull/isnull别名互嵌)函数嵌套位于参数首位时内层漏转换(响亮报错型);
 * 2、大写oracle惯用格式模型'YYYY-MM-DD HH24:MI:SS'的YYYY/YY/DD/MI/SS token在
 * mysql/clickhouse/sqlite目标分支残留字面量(静默错值型)
 */
public class FunctionAuditProbeTest {

	@BeforeAll
	public static void init() {
		FunctionUtils.setFunctionConverts(Arrays.asList("default"));
	}

	@Test
	public void nestedSameFunctionAtParamsStart() {
		// 紧凑嵌套(内层紧贴外层左括号):内层必须完成转换
		String compact = FunctionUtils.getDialectSql("select nvl(nvl(score,0),1) from t", "postgresql");
		assertEquals("select coalesce(coalesce(score,0),1) from t", compact, "紧凑嵌套的内层nvl应被转换");

		// 别名互嵌:内层ifnull同样由Nvl转换器处理
		String alias = FunctionUtils.getDialectSql("select nvl(ifnull(score,0),1) from t", "postgresql");
		assertEquals("select coalesce(coalesce(score,0),1) from t", alias, "内层ifnull应被转换");

		// 三层嵌套逐层展开
		String triple = FunctionUtils.getDialectSql("select nvl(nvl(nvl(a,0),b),c) from t", "postgresql");
		assertEquals("select coalesce(coalesce(coalesce(a,0),b),c) from t", triple, "三层嵌套应逐层转换");

		// 内层位于第二参数(逗号前缀):既有行为不回归
		String tail = FunctionUtils.getDialectSql("select nvl(1,nvl(score,0)) from t", "postgresql");
		assertEquals("select coalesce(1,coalesce(score,0)) from t", tail, "第二参数嵌套行为保持");
	}

	@Test
	public void uppercaseOracleFormatModel() {
		// oracle惯用大写模型在%token目标分支的完整映射
		String sql = "select to_char(create_time,'YYYY-MM-DD HH24:MI:SS') from t";
		assertEquals("select date_format(create_time,'%Y-%m-%d %H:%i:%s') from t",
				FunctionUtils.getDialectSql(sql, "mysql"), "mysql目标应无字面token残留");
		assertEquals("select strftime('%Y-%m-%d %H:%M:%S',datetime(create_time/1000,'unixepoch','localtime')) from t",
				FunctionUtils.getDialectSql(sql, "sqlite"), "sqlite目标应无字面token残留");
		assertEquals("select date_format(toDateTime(create_time),'%Y-%m-%d %H:%i:%s') from t",
				FunctionUtils.getDialectSql(sql, "clickhouse"), "clickhouse目标应无字面token残留");
		// 已修复的sqlserver分支保持正确
		assertEquals("select FORMAT(create_time,'yyyy-MM-dd HH:mm:ss') from t",
				FunctionUtils.getDialectSql(sql, "sqlserver"), "sqlserver目标保持正确");

		// to_date的大写oracle模型(STR_TO_DATE格式)
		assertEquals("select STR_TO_DATE(create_time,'%Y-%m-%d %H:%i:%s') from t",
				FunctionUtils.getDialectSql("select to_date(create_time,'YYYY-MM-DD HH24:MI:SS') from t", "mysql"),
				"to_date大写模型应完整映射");

		// date_format源含大写oracle形态(mysql分支)
		assertEquals("select date_format(create_time,'%Y-%m-%d') from t",
				FunctionUtils.getDialectSql("select date_format(create_time,'YYYY-MM-DD') from t", "mysql"),
				"date_format大写oracle形态应完整映射");

		// 小写模型既有行为不回归
		assertEquals("select date_format(create_time,'%Y-%m-%d %H:%i:%s') from t",
				FunctionUtils.getDialectSql("select to_char(create_time,'yyyy-MM-dd hh24:mi:ss') from t", "mysql"),
				"小写模型行为保持");
	}
}
