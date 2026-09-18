package org.sagacity.sqltoy.utils;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertSame;

import org.junit.jupiter.api.Test;
import org.sagacity.sqltoy.utils.DataSourceUtils.DBType;

/**
 * SqlUtil.escapeLikeValue 边缘场景测试:
 * like通配符转义矩阵、特殊字符、幂等性、方言无关性(统一\转义)
 */
public class SqlUtilEscapeLikeEdgeTest {

	@Test
	public void nullAndEmptyPassthrough() {
		assertNull(SqlUtil.escapeLikeValue(null, DBType.MYSQL, true));
		assertEquals("", SqlUtil.escapeLikeValue("", DBType.MYSQL, true));
	}

	@Test
	public void escapePercentWhenLiteral() {
		// escapePercent=true:%作为字面量被转义
		assertEquals("50\\%", SqlUtil.escapeLikeValue("50%", DBType.MYSQL, true));
	}

	@Test
	public void keepPercentAsWildcard() {
		// escapePercent=false:%保留为通配符
		assertEquals("50%", SqlUtil.escapeLikeValue("50%", DBType.MYSQL, false));
	}

	@Test
	public void escapeUnderscoreBothModes() {
		assertEquals("a\\_b", SqlUtil.escapeLikeValue("a_b", DBType.MYSQL, true));
		assertEquals("a\\_b", SqlUtil.escapeLikeValue("a_b", DBType.ORACLE, false));
	}

	@Test
	public void escapeBackslash() {
		// \本身被翻倍,配合ESCAPE '\'子句
		assertEquals("a\\\\b", SqlUtil.escapeLikeValue("a\\b", DBType.MYSQL, true));
	}

	@Test
	public void quoteNotEscaped() {
		// like转义只处理%、_、\,引号由参数绑定层负责
		assertEquals("a'b", SqlUtil.escapeLikeValue("a'b", DBType.MYSQL, true));
		assertEquals("a\"b", SqlUtil.escapeLikeValue("a\"b", DBType.ORACLE, false));
	}

	@Test
	public void idempotentDoubleEscape() {
		// 转义具备幂等性:对已转义结果再转义不发生变化(多次过滤链路安全)
		String value = "a%_b\\c";
		String once = SqlUtil.escapeLikeValue(value, DBType.MYSQL, true);
		String twice = SqlUtil.escapeLikeValue(once, DBType.MYSQL, true);
		assertEquals(once, twice);
		String onceFalse = SqlUtil.escapeLikeValue(value, DBType.MYSQL, false);
		String twiceFalse = SqlUtil.escapeLikeValue(onceFalse, DBType.MYSQL, false);
		assertEquals(onceFalse, twiceFalse);
	}

	@Test
	public void preEscapedPercentProtected() {
		// 已转义的\%不被二次破坏:\保留、%保持字面量语义
		assertEquals("100\\%", SqlUtil.escapeLikeValue("100\\%", DBType.MYSQL, true));
		assertEquals("100\\%", SqlUtil.escapeLikeValue("100\\%", DBType.MYSQL, false));
	}

	@Test
	public void dialectAgnosticUnifiedBackslash() {
		// dbType参数仅为向后兼容保留,所有数据库统一\转义
		String value = "a%_b\\c";
		assertEquals(SqlUtil.escapeLikeValue(value, DBType.MYSQL, true),
				SqlUtil.escapeLikeValue(value, DBType.ORACLE, true));
		assertEquals(SqlUtil.escapeLikeValue(value, DBType.ORACLE, true),
				SqlUtil.escapeLikeValue(value, DBType.CLICKHOUSE, true));
		assertEquals(SqlUtil.escapeLikeValue(value, DBType.ORACLE, false),
				SqlUtil.escapeLikeValue(value, DBType.TDENGINE, false));
	}

	@Test
	public void specialCharsCombined() {
		// 全特殊字符组合:%、_、\、'、"、中文
		String result = SqlUtil.escapeLikeValue("百%分_之\\a'b\"c", DBType.MYSQL, true);
		assertEquals("百\\%分\\_之\\\\a'b\"c", result);
	}
}
