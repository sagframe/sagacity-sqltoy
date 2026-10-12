package org.sagacity.sqltoy.utils;

import static org.junit.jupiter.api.Assertions.assertEquals;

import java.lang.reflect.Method;

import org.junit.jupiter.api.Test;
import org.sagacity.sqltoy.utils.DataSourceUtils.DBType;

/**
 * update 2026-10-12 SqlUtil.addDateFunction(日志SQL日期参数包裹函数)分派锁定:
 * HIGHGO归PG系、GBASE8S的DATETIME cast族(FRACTION(5)上限+超限截断)、H2的CAST族
 * (2.x无TO_DATE)、SQLITE的date/datetime/time修饰函数——形态真库探针定界,
 * 本测试锁渲染形态防漂移(方法为private,经反射调用;preSql传比较条件片段)
 */
public class AddDateFunctionFormTest {

	private static String wrap(int dbType, int type, String dateStr) throws Exception {
		Method m = SqlUtil.class.getDeclaredMethod("addDateFunction", String.class, String.class, int.class,
				int.class);
		m.setAccessible(true);
		return (String) m.invoke(null, dateStr, " create_time >= ", type, dbType);
	}

	@Test
	public void highgoFollowsPgFamily() throws Exception {
		assertEquals("TO_DATE('2026-01-15','YYYY-MM-DD')", wrap(DBType.HIGHGO, 1, "'2026-01-15'"));
		assertEquals("TO_TIMESTAMP('2026-01-15 10:30:00','YYYY-MM-DD HH24:MI:SS')",
				wrap(DBType.HIGHGO, 2, "'2026-01-15 10:30:00'"));
		assertEquals("'10:30:00'::TIME", wrap(DBType.HIGHGO, 4, "'10:30:00'"));
	}

	@Test
	public void gbase8sDatetimeCastFamily() throws Exception {
		assertEquals("CAST('2026-01-15' AS DATE)", wrap(DBType.GBASE8S, 1, "'2026-01-15'"));
		assertEquals("CAST('2026-01-15 10:30:00' AS DATETIME YEAR TO SECOND)",
				wrap(DBType.GBASE8S, 2, "'2026-01-15 10:30:00'"));
		assertEquals("CAST('2026-01-15 10:30:00.123' AS DATETIME YEAR TO FRACTION(5))",
				wrap(DBType.GBASE8S, 3, "'2026-01-15 10:30:00.123'"));
		// 超5位小数截断(FRACTION上限5,超出报Extra characters)
		assertEquals("CAST('2026-01-15 10:30:00.12345' AS DATETIME YEAR TO FRACTION(5))",
				wrap(DBType.GBASE8S, 3, "'2026-01-15 10:30:00.123456'"));
		// 无小数回YEAR TO SECOND
		assertEquals("CAST('2026-01-15 10:30:00' AS DATETIME YEAR TO SECOND)",
				wrap(DBType.GBASE8S, 3, "'2026-01-15 10:30:00'"));
		assertEquals("CAST('10:30:00' AS DATETIME HOUR TO SECOND)", wrap(DBType.GBASE8S, 4, "'10:30:00'"));
		assertEquals("CAST('10:30:00.12' AS DATETIME HOUR TO FRACTION(5))",
				wrap(DBType.GBASE8S, 5, "'10:30:00.12'"));
	}

	@Test
	public void h2AndSqliteCovered() throws Exception {
		assertEquals("CAST('2026-01-15' AS DATE)", wrap(DBType.H2, 1, "'2026-01-15'"));
		assertEquals("CAST('2026-01-15 10:30:00' AS TIMESTAMP)", wrap(DBType.H2, 2, "'2026-01-15 10:30:00'"));
		assertEquals("CAST('10:30:00' AS TIME)", wrap(DBType.H2, 4, "'10:30:00'"));
		assertEquals("date('2026-01-15')", wrap(DBType.SQLITE, 1, "'2026-01-15'"));
		assertEquals("datetime('2026-01-15 10:30:00')", wrap(DBType.SQLITE, 2, "'2026-01-15 10:30:00'"));
		assertEquals("time('10:30:00')", wrap(DBType.SQLITE, 4, "'10:30:00'"));
	}

	@Test
	public void nonComparePositionNoWrap() throws Exception {
		// 前置片段非比较条件(如select列表)不包裹
		Method m = SqlUtil.class.getDeclaredMethod("addDateFunction", String.class, String.class, int.class,
				int.class);
		m.setAccessible(true);
		assertEquals("'2026-01-15'",
				m.invoke(null, "'2026-01-15'", " select ", 1, DBType.MYSQL));
	}
}
