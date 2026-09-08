package org.sagacity.sqltoy.dialect.utils;

import static org.junit.jupiter.api.Assertions.assertEquals;

import org.junit.jupiter.api.Test;

/**
 * DialectUtils.getParamsCount 边缘场景测试:
 * 分页/count语句的参数计数与字面量感知(与SqlConfigParseUtils的掩码修复同族)
 */
public class GetParamsCountEdgeTest {

	// ---------------- 常规场景(等价性基线,修复前后一致) ----------------

	@Test
	public void positionalCount() {
		assertEquals(2, DialectUtils.getParamsCount("select * from t where a=? and b=?"));
	}

	@Test
	public void namedCount() {
		assertEquals(2, DialectUtils.getParamsCount("select * from t where a=:p and b=:q"));
	}

	@Test
	public void plainLiteralNoParams() {
		// 字面量内不含?/:时计数为0
		assertEquals(0, DialectUtils.getParamsCount("select '有效' from t where a='M'"));
	}

	@Test
	public void blankSql() {
		assertEquals(0, DialectUtils.getParamsCount(""));
		assertEquals(0, DialectUtils.getParamsCount(null));
	}

	@Test
	public void mixedModeCountsQuestionMark() {
		// ?与:named混合时按?分支计数(:named不参与),修复前后行为一致
		assertEquals(1, DialectUtils.getParamsCount("select * from t where a=? and b=:p"));
	}

	// ---------------- 字面量感知 ----------------

	@Test
	public void literalQuestionMarkNotCounted() {
		// 修复前返回2(字面量内的?被计入),修复后正确返回1
		assertEquals(1, DialectUtils.getParamsCount("select '50?' from t where a=?"));
	}

	@Test
	public void doubledQuoteLiteralWithQuestionMarkNotCounted() {
		// ''转义字面量内的?不应计数
		assertEquals(1, DialectUtils.getParamsCount("select * from t where remark='it''s ok?' and a=?"));
	}

	@Test
	public void literalNamedParamNotCounted() {
		// 纯named模式:字面量内的':tag'(冒号前为空白,命中参数特征)不应被计入参数个数
		assertEquals(1, DialectUtils.getParamsCount("select '备注 :tag' from t where a=:p"));
	}

	@Test
	public void wordPrefixColonInLiteralNeverMatched() {
		// 行为记录:a:b形式的冒号前是字母,本就不命中SQL_NAMED_PATTERN,修复前后均为1
		assertEquals(1, DialectUtils.getParamsCount("select 'a:b' from t where a=:p"));
	}

	@Test
	public void backslashEscapeLiteralWithQuestionMark() {
		// mysql系:\'不终结字面量,字面量内的?不计数
		assertEquals(1, DialectUtils.getParamsCount("select * from t where remark='a\\'b?' and a=?", true));
	}

	@Test
	public void standardRuleBackslashTerminatesLiteral() {
		// 标准SQL(false):\为普通字符,'a\'在反斜杠后终结字面量,
		// b?'的?落在字面量外参与计数,随后的'重新开启字面量吞掉后续"and a=?"(该输入本身是mysql语法sql,
		// 按标准规则解析属垃圾进垃圾出,行为记录,实际计数为1)
		assertEquals(1, DialectUtils.getParamsCount("select * from t where remark='a\\'b?' and a=?", false));
	}
}
