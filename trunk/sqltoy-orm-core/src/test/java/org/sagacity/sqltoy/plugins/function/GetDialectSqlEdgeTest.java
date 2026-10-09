package org.sagacity.sqltoy.plugins.function;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotEquals;

import java.util.Arrays;

import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

/**
 * getDialectSql边界场景锁定:update 2026-10-9 串首裸函数行为翻转——整条sql即函数表达式
 * (如sql片段include的碎片形态)此前因\W前置条件恒不命中而漏转换,引擎入口空格哨兵补偿后
 * 正常参与转换;常规SQL以select/insert等开头,行为不变。
 */
public class GetDialectSqlEdgeTest {

	@BeforeAll
	public static void init() {
		FunctionUtils.setFunctionConverts(Arrays.asList("default"));
	}

	@Test
	public void bareLeadingFunctionIsTransformed() {
		// update 2026-10-9 串首裸函数名(前面无\W字符)经入口哨兵补偿后正常转换:nvl→ifnull(mysql)
		String bare = FunctionUtils.getDialectSql("nvl(score, 0)", "mysql");
		assertEquals("ifnull(score, 0)", bare, "串首裸函数名应参与转换");
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
