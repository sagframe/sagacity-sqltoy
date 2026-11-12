package org.sagacity.sqltoy.plugins.function;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.Arrays;

import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

/**
 * split_part 负索引(第三参为负=从右数)在 PG 系内的方言差异:
 * 负索引是PG14新增特性,KingbaseES V9R1真库实测 [[split_part('a,b,c,d',',',-1)]] 报
 * "field position must be greater than zero";POSTGRESQL14 承接 majorVersion&lt;15(含PG12/13)同理。
 * 处理口径:PG15+(postgresql)原生透传;其余PG系内核用正向 split_part+段数守卫等价模拟;
 * 正整数字面量(最常见用法)保持原样输出,不改变既有SQL。
 */
public class SubstringIndexSplitPartTest {

	@BeforeAll
	public static void init() {
		FunctionUtils.setFunctionConverts(Arrays.asList("default"));
	}

	private static String c(String sql, String dialect) {
		return FunctionUtils.getDialectSql(sql, dialect);
	}

	@Test
	public void kingbasePositiveLiteralStaysNative() {
		// 常见正向用法:SQL原样保留(pass-through)
		String sql = "select split_part(name, ',', 2) from t";
		assertEquals(sql, c(sql, "kingbase"));
		assertEquals(sql, c(sql, "postgresql14"));
		assertEquals(sql, c(sql, "opengauss"));
	}

	@Test
	public void kingbaseNegativeLiteralIsEmulated() {
		String out = c("select split_part('a,b,c,d', ',', -1) from t", "kingbase");
		assertTrue(out.contains("split_part('a,b,c,d',',',"), "仍以正向split_part为基元:" + out);
		assertTrue(out.contains("(0 - (-1))"), "负索引应换算为正索引并带段数守卫:" + out);
		assertTrue(out.contains(">((length("), "应使用段数表达式做越界守卫:" + out);
		assertTrue(out.contains("then ''"), "负索引越界应返回空串(PG14语义):" + out);
		// 不再出现原生负索引形态
		assertFalse(out.contains("', '-1')"), out);
	}

	@Test
	public void kingbaseZeroLiteralReturnsEmptyString() {
		String out = c("select split_part('a,b,c', ',', 0) from t", "kingbase");
		assertTrue(out.contains("''"), out);
		assertFalse(out.contains("split_part"), "n=0属病态输入,统一返回'':" + out);
	}

	@Test
	public void dynamicIndexIsEmulatedWithCase() {
		String out = c("select split_part(name, ',', idx) from t", "kingbase");
		assertTrue(out.contains("case when idx=0 then ''"), out);
		assertTrue(out.contains("when idx>0 then split_part(name,',',idx)"), out);
		assertTrue(out.contains("(0 - (idx))"), out);
	}

	@Test
	public void postgres15KeepsNativeNegativeIndex() {
		// PG15+支持负索引,保持原样
		String sql = "select split_part(name, ',', -1) from t";
		assertEquals(sql, c(sql, "postgresql"));
	}

	@Test
	public void otherPgKernelsEmulateNegativeIndex() {
		for (String dialect : new String[] { "postgresql14", "opengauss", "gaussdb", "mogdb", "vastbase", "stardb",
				"highgo", "kingbase" }) {
			String out = c("select split_part(name, ',', -2) from t", dialect);
			assertTrue(out.contains("(0 - (-2))"), dialect + " 应模拟负索引:" + out);
		}
	}

	@Test
	public void mysqlFamilyNegativeIndexUnchanged() {
		// MySQL系走substring_index组合,不应受本次改动影响
		String out = c("select split_part(name, ',', -1) from t", "mysql");
		assertTrue(out.contains("substring_index"), out);
	}
}
