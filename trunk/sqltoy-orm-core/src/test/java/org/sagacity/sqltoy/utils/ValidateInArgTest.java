package org.sagacity.sqltoy.utils;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.Test;

/**
 * 回归测试：in参数内联校验采用结构不变式——引号包裹项必须恰好一对引号且无反斜杠, 否则回退参数化。覆盖三类真实绕过(修复前均通过): 1.'a' or
 * sleep(5)--'(3引号,关键词后无空白导致检测失效,sleep在MySQL下真实执行) 2.'a\' or
 * sleep(5)--'(反斜杠转义收尾引号,字面量提前闭合) 3.'' +(select...) +''(多引号拼接)
 * 行为变化:'it''s'标准双写转义值不再内联(回退参数化,方向安全)
 */
public class ValidateInArgTest {

	@Test
	public void quoteConcatBypassBlocked() {
		// 修复前true:关键词正则要求关键词后跟空白,sleep(5)--的)后是-不匹配,3引号未被拦截
		assertFalse(SqlUtil.validateInArg("'a' or sleep(5)--'"));
		assertFalse(SqlUtil.validateInArg("'a' or 'b'='c'"));
		assertFalse(SqlUtil.validateInArg("''+(select field from table)+''"));
		assertFalse(SqlUtil.validateInArg("\"a\" or \"b\"=\"c\""));
	}

	@Test
	public void backslashEscapeBypassBlocked() {
		// 修复前true:MySQL等方言 \' 转义收尾引号,or sleep(5)-- 泄漏为可执行sql
		assertFalse(SqlUtil.validateInArg("'a\\' or sleep(5)--'"));
		assertFalse(SqlUtil.validateInArg("\"a\\\" or sleep(5)--\""));
		assertFalse(SqlUtil.validateInArg("'a\\','b\\'"));
	}

	@Test
	public void auditClaimedPayloadStillRejected() {
		// 以)结尾进不了引号分支,数字分支拒绝
		assertFalse(SqlUtil.validateInArg("'ok' or sleep(5)"));
	}

	@Test
	public void normalValuesStillInlined() {
		assertTrue(SqlUtil.validateInArg("'a','b'"));
		assertTrue(SqlUtil.validateInArg("1,2,3"));
		assertTrue(SqlUtil.validateInArg("'abc'"));
		assertTrue(SqlUtil.validateInArg("'a b c'"));
		assertTrue(SqlUtil.validateInArg("\"a\",\"b\""));
		// 单数字不内联走参数化;含转义引号的值回退参数化(行为变化,方向安全)
		assertFalse(SqlUtil.validateInArg("123"));
		assertFalse(SqlUtil.validateInArg("'it''s'"));
	}
}
