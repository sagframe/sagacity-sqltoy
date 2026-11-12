package org.sagacity.sqltoy.plugins.function.impl;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;

import org.junit.jupiter.api.Test;
import org.sagacity.sqltoy.utils.DataSourceUtils.DBType;

/**
 * update 2026-10-22 Trim修饰符形态判定的字面量感知回归。原实现modifierForm/fromIdx用
 * contains(" from ")/indexOf在原串上判定,单参trim(' from ')等字面量内含" from "文本被
 * 误判为修饰符形态,sqlite/starrocks分支拆参产物语义错值(如trim("'","'")变成剔除引号
 * 字符而非去空白);现判定在字面量掩码串上进行(等长同位),字面量内文本不参与判定。
 * 直接调wrap()避免污染FunctionUtils.functionConverts全局静态态
 */
public class TrimLiteralAwareTest {

	private final Trim trim = new Trim();

	private String wrap(int dialect, String arg) {
		return trim.wrap(dialect, "trim", true, arg);
	}

	// ---------- 字面量内" from "不再误判(修复点) ----------

	@Test
	public void literalContainingFromNotMisparsedOnSqlite() {
		// 原实现产物trim("'","'")(剔除引号字符,静默错值);现按单参普通trim透传
		assertNull(wrap(DBType.SQLITE, "' from '"), "字面量内' from '不应判为修饰符形态");
		assertNull(wrap(DBType.SQLITE, "'a from b'"), "字面量内a from b不应判为修饰符形态");
	}

	@Test
	public void literalContainingFromNotMisparsedOnStarRocks() {
		assertNull(wrap(DBType.STARROCKS, "' from '"));
		assertNull(wrap(DBType.STARROCKS, "'a from b'"));
	}

	@Test
	public void literalContainingFromNotMisparsedOnOtherDialects() {
		// 其余分支原本恰好IGNORE透传,锁定不回归
		assertNull(wrap(DBType.MYSQL, "' from '"));
		assertNull(wrap(DBType.ORACLE, "'a from b'"));
		// H2单参归一为both空格形态(包裹原字面量,语义不变)
		assertEquals("trim(both ' ' from ' from ')", wrap(DBType.H2, "' from '"));
		// sqlserver单参转rtrim(ltrim(x)),语义不变
		assertEquals("rtrim(ltrim(' from '))", wrap(DBType.SQLSERVER, "' from '"));
	}

	@Test
	public void pathologicalLiteralBeforeRealFrom() {
		// 真修饰符+字面量内也含" from ":fromIdx在掩码串上定位,命中真实的from而非字面量内的
		assertEquals("trim(col,' from ')", wrap(DBType.SQLITE, "both ' from ' from col"));
		// 原实现fromIdx命中字面量内位置(拆出chars="'",产物错值),现正确保留完整字面量为剔除集
	}

	// ---------- maskQuoted能力:引号标识符/嵌套函数/转义 ----------

	@Test
	public void doubledQuoteLiteralContainingFrom() {
		// ''成对转义的字面量内含" from ":整个字面量被遮蔽,真实from正确定位,字面量完整保留
		assertEquals("trim(col,'a'' from b')", wrap(DBType.SQLITE, "'a'' from b' from col"));
	}

	@Test
	public void nestedFunctionLiteralFromPreserved() {
		// 嵌套函数参数中的' from '字面量被遮蔽,真实的from定位正确,嵌套表达式完整保留
		assertEquals("trim(col,concat(' from ', x))", wrap(DBType.SQLITE, "concat(' from ', x) from col"));
	}

	@Test
	public void quotedIdentifierContainingFrom() {
		// 双引号标识符内的from不参与判定,真实from定位正确
		assertEquals("trim(col,\"a from b\")", wrap(DBType.SQLITE, "\"a from b\" from col"));
	}

	@Test
	public void subqueryNewlineFromNotMisdetected() {
		// 参数内子查询换行后的from(非修饰符from)不被" from "误命中(\n不是空格且掩码不触碰它),
		// 真实修饰符from正确定位;注释场景无需测试——解析期clearMark已在函数转换前剔除
		assertEquals("trim(col,(select 1\nfrom t))", wrap(DBType.SQLITE, "(select 1\nfrom t) from col"));
	}

	// ---------- 真实修饰符形态不回归 ----------

	@Test
	public void realModifierFormsStillConvert() {
		// sqlite/starrocks:二参剔除字符集形态
		assertEquals("trim(col,'x')", wrap(DBType.SQLITE, "'x' from col"));
		assertEquals("ltrim(col,'x')", wrap(DBType.SQLITE, "leading 'x' from col"));
		assertEquals("rtrim(col,'x')", wrap(DBType.SQLITE, "trailing 'x' from col"));
		// 标准组(省略字符的from形态转单参/单向)
		assertEquals("trim(col)", wrap(DBType.MYSQL, "both from col"));
		assertEquals("ltrim(col)", wrap(DBType.MYSQL, "leading from col"));
		assertEquals("rtrim(col)", wrap(DBType.MYSQL, "trailing from col"));
		assertEquals("trim(col)", wrap(DBType.MYSQL, "from col"));
		// 带字符的标准组形态:mysql/oracle原生透传
		assertNull(wrap(DBType.MYSQL, "'x' from col"));
		assertNull(wrap(DBType.ORACLE, "both 'x' from col"));
		// sqlserver:非空格剔除字符无法表达,响亮保留
		assertNull(wrap(DBType.SQLSERVER, "'x' from col"));
		assertEquals("ltrim(col)", wrap(DBType.SQLSERVER, "leading from col"));
		assertEquals("rtrim(ltrim(col))", wrap(DBType.SQLSERVER, "both from col"));
		// H2:from形态显式归一为both空格
		assertEquals("trim(both ' ' from col)", wrap(DBType.H2, "from col"));
		assertEquals("trim(both ' ' from col)", wrap(DBType.H2, "col"));
	}
}
