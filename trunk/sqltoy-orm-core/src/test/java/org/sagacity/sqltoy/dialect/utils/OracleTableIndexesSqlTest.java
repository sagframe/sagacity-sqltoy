package org.sagacity.sqltoy.dialect.utils;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.lang.reflect.Proxy;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.atomic.AtomicReference;

import org.junit.jupiter.api.Test;

/**
 * 回归测试：Oracle索引元数据查询的表名经?参数绑定,
 * 修复前两处直接拼接(TABLE_NAME='xxx'),含单引号的表名构成注入面
 */
public class OracleTableIndexesSqlTest {

	@Test
	@SuppressWarnings("unchecked")
	public void tableNameBoundAsParameterNotConcatenated() throws Exception {
		AtomicReference<String> capturedSql = new AtomicReference<String>();
		Map<Integer, String> boundParams = new HashMap<Integer, String>();
		PreparedStatement pst = (PreparedStatement) Proxy.newProxyInstance(getClass().getClassLoader(),
				new Class[] { PreparedStatement.class }, (proxy, method, args) -> {
					if ("setString".equals(method.getName())) {
						boundParams.put((Integer) args[0], (String) args[1]);
						return null;
					}
					if ("executeQuery".equals(method.getName())) {
						return Proxy.newProxyInstance(getClass().getClassLoader(), new Class[] { ResultSet.class },
								(rsProxy, rsMethod, rsArgs) -> {
									if ("next".equals(rsMethod.getName())) {
										return false;
									}
									return null;
								});
					}
					if (method.getReturnType() == boolean.class) {
						return false;
					}
					if (method.getReturnType() == int.class) {
						return 0;
					}
					return null;
				});
		Connection conn = (Connection) Proxy.newProxyInstance(getClass().getClassLoader(),
				new Class[] { Connection.class }, (proxy, method, args) -> {
					if ("prepareStatement".equals(method.getName())) {
						capturedSql.set((String) args[0]);
						return pst;
					}
					return null;
				});
		String maliciousTable = "T1' OR '1'='1";
		java.lang.reflect.Method method = DefaultDialectUtils.class.getDeclaredMethod("getOracleTableIndexes",
				String.class, String.class, String.class, Connection.class, Integer.class, String.class);
		method.setAccessible(true);
		method.invoke(null, null, null, maliciousTable, conn, null, null);
		// SQL中不应出现拼接的表名,而是两个?占位符
		String sql = capturedSql.get();
		assertTrue(sql.contains("TABLE_NAME =?") && sql.indexOf("?") != sql.lastIndexOf("?"),
				"应有两个?占位符,实际:" + sql);
		assertFalse(sql.contains(maliciousTable), "表名不应拼接进SQL,实际:" + sql);
		// 两个占位符均绑定大写化的表名
		assertEquals("T1' OR '1'='1", boundParams.get(1));
		assertEquals("T1' OR '1'='1", boundParams.get(2));
	}
}
