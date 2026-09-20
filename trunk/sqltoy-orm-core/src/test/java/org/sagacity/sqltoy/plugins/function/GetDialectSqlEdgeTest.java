package org.sagacity.sqltoy.plugins.function;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotEquals;

import java.util.Arrays;

import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

/**
 * getDialectSql裸函数名边界场景锁定:适配函数正则为 \W(函数名)\( 形态,要求函数名前存在
 * 非单词字符以保证token独立性;裸函数表达式(函数名位于串首,前面无任何字符)不参与转换,
 * 生产SQL(以select/insert等开头或函数前有空格/括号)不受影响。
 */
public class GetDialectSqlEdgeTest {

	@BeforeAll
	public static void init() {
		FunctionUtils.setFunctionConverts(Arrays.asList("default"));
	}

	@Test
	public void bareLeadingFunctionStaysUntouched() {
		// 函数名位于串首(前面无\W字符):正则不命中,原样返回
		String bare = FunctionUtils.getDialectSql("nvl(score, 0)", "mysql");
		assertEquals("nvl(score, 0)", bare, "串首裸函数名不满足\\W前置条件,原样返回");
	}

	@Test
	public void prefixedFunctionIsTransformed() {
		// 生产形态:函数名前有空格/select关键字,正常转换 nvl→ifnull(mysql)
		String prefixed = FunctionUtils.getDialectSql("select id, nvl(score, 0) as v from t", "mysql");
		assertNotEquals("select id, nvl(score, 0) as v from t", prefixed, "带前缀的nvl应被转换");
		System.out.println("[EDGE] prefixed=" + prefixed);

		String datediff = FunctionUtils.getDialectSql("select datediff(day,'2026-01-15',biz_date) from t",
				"mysql");
		System.out.println("[EDGE] datediff=" + datediff);
		assertNotEquals(datediff, "select datediff(day,'2026-01-15',biz_date) from t", "datediff应被转换");

		// 括号前缀同样满足\W:子查询场景
		String inParen = FunctionUtils.getDialectSql("(nvl(score, 0))", "mysql");
		System.out.println("[EDGE] inParen=" + inParen);
		assertNotEquals("(nvl(score, 0))", inParen, "括号前缀的nvl应被转换");
	}
}
