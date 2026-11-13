package org.sagacity.sqltoy.dialect.utils;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.Test;
import org.sagacity.sqltoy.utils.SqlUtil;
import org.sagacity.sqltoy.utils.StringUtil;

/**
 * update 2026-11-12 DialectUtils.SET_OPERATION_PATTERN与SqlUtil.SET_OPERATION_PATTERN口径对齐锁定:
 * 该模式用于count改写守卫(fast-path判定)与top/random派生表包裹判定,minus(a,b)等函数形态
 * (ClickHouse算术函数)不得命中,否则count/top/random链路误包派生表(旧版CH子查询重复列名报
 * Duplicate column name,新版CH仅多包一层)。except(select ...)等集合运算紧贴括号写法仍须命中。
 */
public class DialectUtilsSetOperationPatternTest {

	private static boolean matches(String sql) {
		return StringUtil.matches(sql, DialectUtils.SET_OPERATION_PATTERN);
	}

	@Test
	public void functionFormNotMatched() {
		assertFalse(matches("select id from t where minus(a,b)>0"), "minus()函数");
		assertFalse(matches("select minus(a,b) d from t"), "select列中的minus()");
		assertFalse(matches("select id from t where t.minus(a,b)>0"), "限定名minus()");
		assertFalse(matches("select id from t where minus (a,b)>0"), "minus与括号间有空格");
		assertFalse(matches("select id from t where except(a,b)>0"), "except函数形态");
		assertFalse(matches("select id from t where intersect(a,b)>0"), "intersect函数形态");
	}

	@Test
	public void setOperationFormsStillMatched() {
		assertTrue(matches("select id from a minus select id from b"), "裸minus");
		assertTrue(matches("select id from a except select id from b"), "裸except");
		assertTrue(matches("select id from a intersect select id from b"), "裸intersect");
		assertTrue(matches("select id from a minus(select id from b)"), "minus紧贴括号+select");
		assertTrue(matches("select id from a minus (select id from b)"), "minus空格括号+select");
		assertTrue(matches("select id from a except(select id from b)"), "except紧贴括号+select");
		assertTrue(matches("select id from a intersect(select id from b)"), "intersect紧贴括号+select");
		assertTrue(matches("select id from a MINUS SELECT id from b"), "大写");
		assertTrue(matches("select id from a minus"), "关键字落串尾($分支)");
	}

	@Test
	public void unionHandledBySeparatePattern() {
		// union不在本模式(由UNION_PATTERN单独判定),口径与既有设计一致
		assertFalse(matches("select id from a union select id from b"), "union不归本模式");
		assertTrue(StringUtil.matches("select id from a union select id from b", DialectUtils.UNION_PATTERN));
	}

	@Test
	public void alignedWithSqlUtilOnFunctionForms() {
		// 与SqlUtil.hasSetOperation在函数形态/集合写法上的口径一致性(调用点均先掩码字面量,
		// 此处用不含字面量的SQL矩阵锁定两个模式的判定一致)
		String[] functionForms = { "select id from t where minus(a,b)>0", "select minus(a,b) d from t",
				"select id from t where minus (a,b)>0" };
		for (String sql : functionForms) {
			assertFalse(matches(sql), "DialectUtils函数形态:" + sql);
			assertFalse(SqlUtil.hasSetOperation(sql, true), "SqlUtil函数形态:" + sql);
		}
		String[] setForms = { "select id from a minus select id from b", "select id from a except(select id from b)",
				"select id from a intersect (select id from b)" };
		for (String sql : setForms) {
			assertTrue(matches(sql), "DialectUtils集合写法:" + sql);
			assertTrue(SqlUtil.hasSetOperation(sql, true), "SqlUtil集合写法:" + sql);
		}
	}
}