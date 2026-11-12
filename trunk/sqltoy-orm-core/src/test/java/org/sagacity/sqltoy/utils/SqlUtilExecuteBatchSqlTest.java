package org.sagacity.sqltoy.utils;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.lang.reflect.Proxy;
import java.sql.Connection;
import java.sql.SQLException;
import java.sql.Statement;
import java.util.ArrayList;
import java.util.List;

import org.junit.jupiter.api.Test;

/**
 * 回归测试：executeBatchSql失败时回滚并恢复autoCommit、close异常不吞掉真实异常
 * 修复前:失败路径直接throw,已执行的批次既不commit也不rollback,autoCommit改动泄漏给连接后续使用者
 */
public class SqlUtilExecuteBatchSqlTest {

	private static final class Recorder {
		final List<String> calls = new ArrayList<String>();
		int addBatchCount = 0;
		int executeBatchCount = 0;
		boolean failBatch = false;
		boolean failClose = false;

		Connection connection() {
			return (Connection) Proxy.newProxyInstance(Recorder.class.getClassLoader(),
					new Class[] { Connection.class }, (proxy, method, args) -> {
						switch (method.getName()) {
						case "createStatement":
							return statement();
						case "getAutoCommit":
							return true;
						case "setAutoCommit":
							calls.add("setAutoCommit:" + ((Boolean) args[0]).booleanValue());
							return null;
						case "rollback":
							calls.add("rollback");
							return null;
						}
						return defaultReturn(method.getReturnType());
					});
		}

		Statement statement() {
			return (Statement) Proxy.newProxyInstance(Recorder.class.getClassLoader(), new Class[] { Statement.class },
					(proxy, method, args) -> {
						switch (method.getName()) {
						case "addBatch":
							addBatchCount++;
							return null;
						case "executeBatch":
							executeBatchCount++;
							if (failBatch) {
								throw new SQLException("mock batch failure");
							}
							return new int[0];
						case "close":
							if (failClose) {
								throw new SQLException("mock close failure");
							}
							return null;
						}
						return defaultReturn(method.getReturnType());
					});
		}

		private static Object defaultReturn(Class<?> type) {
			if (type == boolean.class) {
				return false;
			}
			if (type == int.class) {
				return 0;
			}
			if (type == long.class) {
				return 0L;
			}
			return null;
		}
	}

	private static String script() {
		return "create table t(id int);insert into t values(1)";
	}

	@Test
	public void failureRollsBackAndRestoresAutoCommit() {
		Recorder rec = new Recorder();
		rec.failBatch = true;
		SQLException ex = assertThrows(SQLException.class,
				() -> SqlUtil.executeBatchSql(rec.connection(), script(), 100, false));
		assertEquals("mock batch failure", ex.getMessage());
		// 修复前:既不rollback也不恢复autoCommit,事务悬挂
		assertTrue(rec.calls.contains("rollback"));
		assertEquals("setAutoCommit:false", rec.calls.get(0));
		assertEquals("setAutoCommit:true", rec.calls.get(rec.calls.size() - 1));
	}

	@Test
	public void successRestoresAutoCommitWithoutRollback() throws Exception {
		Recorder rec = new Recorder();
		SqlUtil.executeBatchSql(rec.connection(), script(), 100, false);
		assertFalse(rec.calls.contains("rollback"));
		assertEquals(2, rec.addBatchCount);
		assertEquals(1, rec.executeBatchCount);
		assertEquals("setAutoCommit:false", rec.calls.get(0));
		assertEquals("setAutoCommit:true", rec.calls.get(1));
	}

	@Test
	public void closeFailureDoesNotMaskBatchException() {
		Recorder rec = new Recorder();
		rec.failBatch = true;
		rec.failClose = true;
		// 修复前:finally中close抛出的异常会替换掉真正的batch异常
		SQLException ex = assertThrows(SQLException.class,
				() -> SqlUtil.executeBatchSql(rec.connection(), script(), 100, false));
		assertEquals("mock batch failure", ex.getMessage());
		// close异常不能跳过autoCommit恢复
		assertEquals("setAutoCommit:true", rec.calls.get(rec.calls.size() - 1));
	}
}
