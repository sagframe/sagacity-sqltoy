package org.sagacity.sqltoy.utils;

import static org.junit.jupiter.api.Assertions.assertEquals;

import java.lang.reflect.Proxy;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.util.ArrayList;
import java.util.Collection;
import java.util.List;

import org.junit.jupiter.api.Test;

/**
 * 回归测试：公共API batchUpdateByJdbc的影响行数统计,Oracle等驱动executeBatch返回
 * SUCCESS_NO_INFO(-2)按1行计(方法按行执行,每次addBatch绑定一行),修复前按0计导致统计失真
 */
public class SqlUtilBatchUpdateJdbcCountTest {

	private static Connection connectionReturning(int[] batchCounts) {
		PreparedStatement pst = (PreparedStatement) Proxy.newProxyInstance(
				SqlUtilBatchUpdateJdbcCountTest.class.getClassLoader(), new Class[] { PreparedStatement.class },
				(proxy, method, args) -> {
					if ("executeBatch".equals(method.getName())) {
						return batchCounts;
					}
					if (method.getReturnType() == boolean.class) {
						return false;
					}
					if (method.getReturnType() == int.class) {
						return 0;
					}
					if (method.getReturnType() == long.class) {
						return 0L;
					}
					return null;
				});
		return (Connection) Proxy.newProxyInstance(SqlUtilBatchUpdateJdbcCountTest.class.getClassLoader(),
				new Class[] { Connection.class }, (proxy, method, args) -> {
					if ("prepareStatement".equals(method.getName())) {
						return pst;
					}
					if ("getAutoCommit".equals(method.getName())) {
						return true;
					}
					if (method.getReturnType() == boolean.class) {
						return false;
					}
					if (method.getReturnType() == int.class) {
						return 0;
					}
					return null;
				});
	}

	private static Collection threeRows() {
		List<Object[]> rows = new ArrayList<Object[]>();
		rows.add(new Object[] { 1, "a" });
		rows.add(new Object[] { 2, "b" });
		rows.add(new Object[] { 3, "c" });
		return rows;
	}

	private static Long execute(int[] batchCounts) throws Exception {
		return SqlUtil.batchUpdateByJdbc(null, "insert into t values(?,?)", threeRows(), 100, null, null, null,
				connectionReturning(batchCounts), null);
	}

	@Test
	public void oracleStyleSuccessNoInfoCountedPerRow() throws Exception {
		// 修复前统计为0
		assertEquals(3L, execute(new int[] { -2, -2, -2 }).longValue());
	}

	@Test
	public void mixedRealAndNoInfoCounts() throws Exception {
		// 1+(-2按1)+2=4,修复前为3
		assertEquals(4L, execute(new int[] { 1, -2, 2 }).longValue());
	}

	@Test
	public void executeFailedStillCountsZero() throws Exception {
		// -3按0计:0+5+5=10
		assertEquals(10L, execute(new int[] { -3, 5, 5 }).longValue());
	}
}
