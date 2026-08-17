package org.sagacity.sqltoy.utils;

import static org.junit.jupiter.api.Assertions.assertEquals;

import java.lang.reflect.Proxy;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

import org.junit.jupiter.api.Test;

/**
 * 回归测试：批量执行影响行数统计,Oracle等驱动executeBatch返回SUCCESS_NO_INFO(-2)
 * 表示语句成功但行数未知,框架内部单行语句链路(每条addBatch绑定一行)应按1计, 修复前按0计导致saveAll等返回值在Oracle上失真为0
 */
public class SqlUtilsExtBatchCountTest {

	private static Connection connectionReturning(int[] batchCounts) {
		PreparedStatement pst = (PreparedStatement) Proxy.newProxyInstance(
				SqlUtilsExtBatchCountTest.class.getClassLoader(), new Class[] { PreparedStatement.class },
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
		return (Connection) Proxy.newProxyInstance(SqlUtilsExtBatchCountTest.class.getClassLoader(),
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

	private static List<Object[]> threeRows() {
		List<Object[]> rows = new ArrayList<Object[]>();
		rows.add(new Object[] { 1, "a" });
		rows.add(new Object[] { 2, "b" });
		rows.add(new Object[] { 3, "c" });
		return rows;
	}

	private static Long execute(int[] batchCounts) throws Exception {
		return SqlUtilsExt.batchUpdateForPOJO(null, "insert into t values(?,?)", threeRows(), null, null, null, 100,
				null, connectionReturning(batchCounts), null);
	}

	@Test
	public void oracleStyleSuccessNoInfoCountedPerRow() throws Exception {
		// Oracle批量默认逐条返回-2(成功但行数未知),修复前统计为0
		assertEquals(3L, execute(new int[] { -2, -2, -2 }).longValue());
	}

	@Test
	public void mixedRealAndNoInfoCounts() throws Exception {
		// 真实行数与-2混合,按实际+1逐条累计
		assertEquals(4L, execute(new int[] { 1, -2, 2 }).longValue());
	}

	@Test
	public void executeFailedStillCountsZero() throws Exception {
		// -3(语句失败)保持按0计:0+5+5=10,不虚增
		assertEquals(10L, execute(new int[] { -3, 5, 5 }).longValue());
	}
}
