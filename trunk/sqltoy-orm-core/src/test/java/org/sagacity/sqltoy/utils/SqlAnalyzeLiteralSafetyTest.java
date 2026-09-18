package org.sagacity.sqltoy.utils;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.Test;
import org.sagacity.sqltoy.SqlToyConstants;
import org.sagacity.sqltoy.dialect.utils.DialectUtils;

/**
 * sql分析类函数的字面量安全验证:
 * 字面量内的from/union/order by、'('及')等字符不得参与关键词定位与括号配对,
 * 规避count sql改写取错from位置、union判定误报漏报等问题
 * (getSymMarkIndexExcludeKeyWords/hasUnion/clearDisturbSql入口统一做等长字面量掩码)
 */
public class SqlAnalyzeLiteralSafetyTest {

	// 与DialectUtils内私有常量一致的String正则
	private static final String SELECT_REGEX = "select\\s+";
	private static final String FROM_REGEX = "\\s+from[\\(\\s+]";

	/**
	 * 字面量内的" from ("不得被当成真实的from:
	 * select a , ' from (' from t1 真实from位于" from t1"处
	 */
	@Test
	public void literalFromNotTreatedAsRealFrom() {
		String sql = "select a , ' from (' from t1";
		int fromIndex = SqlUtil.getSymMarkIndexExcludeKeyWords(sql, SELECT_REGEX, FROM_REGEX, 0);
		assertEquals(sql.lastIndexOf(" from t1"), fromIndex, "应定位到真实from而非字面量内的from");
	}

	/**
	 * 注释中提到的select concat(a,'(') from场景:
	 * 字面量内的(不得参与括号配对,多层子查询下仍须定位到真实from
	 */
	@Test
	public void literalParenNotDisturbSymMark() {
		// 自愈场景:字面量'('后紧跟真实),旧实现结果碰巧正确,作为回归护栏
		String sql = "select concat(a,'(') , (select b from t2) y from t1";
		int fromIndex = SqlUtil.getSymMarkIndexExcludeKeyWords(sql, SELECT_REGEX, FROM_REGEX, 0);
		assertEquals(sql.lastIndexOf(" from t1"), fromIndex, "concat字面量'('场景应定位到真实from");

		// 深度错位场景:字面量'('与末尾字面量')'跨区间误配对,把真实from掩掉,
		// 旧实现回退返回第一个子查询的from(错位),新实现应定位到真实from
		sql = "select a , (select b from t2) , (select c from t3) y , '(' from t1 where z=')'";
		fromIndex = SqlUtil.getSymMarkIndexExcludeKeyWords(sql, SELECT_REGEX, FROM_REGEX, 0);
		assertEquals(sql.lastIndexOf(" from t1"), fromIndex, "字面量跨区间误配对场景应定位到真实from");
	}

	/**
	 * hasUnion:字面量内的union all不构成真实union;真实union不因字面量内的'('、')'被误删
	 */
	@Test
	public void hasUnionLiteralSafety() {
		assertFalse(SqlUtil.hasUnion("select a from t1 where remark=' union all '", false),
				"字面量内的union all不应判定为union查询");
		assertTrue(
				SqlUtil.hasUnion("select a from t1 where x='(' union all select b from t2 where y=')'", false),
				"真实union all不因字面量内的'('与')'跨区间误配对而漏判");
		assertTrue(
				SqlUtil.hasUnion(
						"select a from t1 where x='(' and y=(select max(z) from t2) union all select b from t3",
						false),
				"真实union all与字面量、子查询共存时应正确判定");
	}

	/**
	 * hasOrderByOrUnion(基于clearDisturbSql):字面量内的order by不参与判定
	 */
	@Test
	public void hasOrderByOrUnionLiteralSafety() {
		assertFalse(DialectUtils.hasOrderByOrUnion("select a from t where remark=' order by id'"),
				"字面量内的order by不应判定为存在排序");
		assertTrue(DialectUtils.hasOrderByOrUnion("select a from t order by id"), "真实order by应正确判定");
	}

	/**
	 * UNION_ALL_REGEX应能匹配真实的union all(\\all笔误导致默认正则永不匹配)
	 */
	@Test
	public void unionAllRegexMatchesRealSql() {
		assertTrue(StringUtil.matches(" select a from t1 union all select b from t2 ", SqlToyConstants.UNION_ALL_REGEX),
				"UNION_ALL_REGEX应能匹配小写union all");
		assertTrue(StringUtil.matches(" select a from t1 union all select b from t2 ".toUpperCase(),
				SqlToyConstants.UNION_ALL_REGEX), "UNION_ALL_REGEX应能匹配大写UNION ALL");
	}
}
