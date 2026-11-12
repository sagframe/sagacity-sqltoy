package org.sagacity.sqltoy.utils;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.lang.reflect.Proxy;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.SQLException;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

import org.junit.jupiter.api.Test;
import org.sagacity.sqltoy.callback.InsertRowCallbackHandler;
import org.sagacity.sqltoy.model.DBProfile;

/**
 * 回归测试：batchUpdateByJdbc失败时必须回滚(与executeBatchSql语义对齐)。修复前失败路径直接throw,
 * finally中恢复autoCommit(true)按JDBC规范会提交当前事务,已执行的批次被静默部分提交——调用方收到异常
 * 却已有数据落库;autoCommit=true分支则无需回滚(批次执行时即已提交)
 */
public class SqlUtilBatchUpdateByJdbcRollbackTest {

	private static final class Recorder {
		final List<String> calls = new ArrayList<String>();
		boolean initialAutoCommit = true;
		/** 第几次executeBatch抛异常,-1表示不抛 */
		int failOnExecuteBatchCall = -1;
		int executeBatchCount = 0;

		Connection connection() {
			return (Connection) Proxy.newProxyInstance(Recorder.class.getClassLoader(),
					new Class[] { Connection.class }, (proxy, method, args) -> {
						switch (method.getName()) {
						case "prepareStatement":
							return statement();
						case "getAutoCommit":
							return initialAutoCommit;
						case "setAutoCommit":
							calls.add("setAutoCommit:" + ((Boolean) args[0]).booleanValue());
							return null;
						case "rollback":
							calls.add("rollback");
							return null;
						case "commit":
							calls.add("commit");
							return null;
						}
						return defaultReturn(method.getReturnType());
					});
		}

		PreparedStatement statement() {
			return (PreparedStatement) Proxy.newProxyInstance(Recorder.class.getClassLoader(),
					new Class[] { PreparedStatement.class }, (proxy, method, args) -> {
						switch (method.getName()) {
						case "executeBatch":
							executeBatchCount++;
							if (executeBatchCount == failOnExecuteBatchCall) {
								throw new SQLException("mock batch failure");
							}
							return new int[0];
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

	/** 不做参数绑定,批量骨架由batchUpdateByJdbc承载,便于用代理连接验证事务动作 */
	private static final InsertRowCallbackHandler NOOP_HANDLER = (pst, index, rowData) -> {
	};

	private static List<Object> rows(int size) {
		List<Object> rowDatas = new ArrayList<Object>();
		for (int i = 0; i < size; i++) {
			rowDatas.add(new Object[] { "v" + i, i });
		}
		return rowDatas;
	}

	private static List<Object> fourRows() {
		return rows(4);
	}

	// update 2026-9-15 适配batchUpdateByJdbc末参dbType→DBProfile重构:
	// Recorder代理连接的metadata返回null不可走getDBProfile,按测试惯例构造最小档案
	private static DBProfile mysqlProfile() {
		return new DBProfile(null, "mysql", DataSourceUtils.DBType.MYSQL, null, 0, null, null, false);
	}

	@Test
	public void failureRollsBackThenRestoresAutoCommit() {
		Recorder rec = new Recorder();
		rec.failOnExecuteBatchCall = 2;
		SQLException ex = assertThrows(SQLException.class,
				() -> SqlUtil.batchUpdateByJdbc(null, "update t set a=? where id=?", fourRows(), 2, NOOP_HANDLER, null,
						Boolean.FALSE, rec.connection(), mysqlProfile()));
		assertEquals("mock batch failure", ex.getMessage());
		// 先切手动提交 -> 失败回滚 -> 最后才恢复autoCommit(此时事务已清空,恢复不会提交任何数据)
		assertEquals("setAutoCommit:false", rec.calls.get(0));
		assertTrue(rec.calls.contains("rollback"), "失败路径必须回滚");
		assertEquals("setAutoCommit:true", rec.calls.get(rec.calls.size() - 1));
		assertTrue(rec.calls.indexOf("rollback") < rec.calls.lastIndexOf("setAutoCommit:true"),
				"回滚必须发生在恢复autoCommit之前");
		assertFalse(rec.calls.contains("commit"), "不应存在显式commit");
	}

	@Test
	public void successRestoresAutoCommitWithoutRollback() throws Exception {
		Recorder rec = new Recorder();
		SqlUtil.batchUpdateByJdbc(null, "update t set a=? where id=?", fourRows(), 2, NOOP_HANDLER, null, Boolean.FALSE,
				rec.connection(), mysqlProfile());
		// 4行/batchSize=2 -> 循环内1次+尾部补齐1次
		assertEquals(2, rec.executeBatchCount);
		assertFalse(rec.calls.contains("rollback"), "成功路径不应回滚");
		assertEquals(Arrays.asList("setAutoCommit:false", "setAutoCommit:true"), rec.calls);
	}

	@Test
	public void switchedToAutoCommitFailureDoesNotRollback() {
		Recorder rec = new Recorder();
		// 连接原为手动提交,请求autoCommit=true:批次执行时即已提交,回滚无从撤销
		rec.initialAutoCommit = false;
		rec.failOnExecuteBatchCall = 2;
		assertThrows(SQLException.class,
				() -> SqlUtil.batchUpdateByJdbc(null, "update t set a=? where id=?", fourRows(), 2, NOOP_HANDLER, null,
						Boolean.TRUE, rec.connection(), mysqlProfile()));
		assertFalse(rec.calls.contains("rollback"), "autoCommit=true分支无需回滚");
		assertEquals("setAutoCommit:true", rec.calls.get(0));
		assertEquals("setAutoCommit:false", rec.calls.get(rec.calls.size() - 1));
	}

	@Test
	public void nullAutoCommitLeavesConnectionUntouched() {
		Recorder rec = new Recorder();
		rec.failOnExecuteBatchCall = 1;
		assertThrows(SQLException.class,
				() -> SqlUtil.batchUpdateByJdbc(null, "update t set a=? where id=?", fourRows(), 2, NOOP_HANDLER, null,
						null, rec.connection(), mysqlProfile()));
		// autoCommit为null表示保持连接原有提交方式:不得改动、也不得回滚调用方的事务
		assertTrue(rec.calls.isEmpty(), "未切换提交方式时不应有任何事务动作,实际:" + rec.calls);
	}
}
