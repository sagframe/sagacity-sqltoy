package org.sagacity.sqltoy.utils;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.lang.reflect.Proxy;
import java.sql.CallableStatement;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.ArrayList;
import java.util.List;

import org.junit.jupiter.api.Test;
import org.sagacity.sqltoy.callback.CallableStatementResultHandler;
import org.sagacity.sqltoy.callback.PreparedStatementResultHandler;

/**
 * B1回归:preparedStatementProcess/callableStatementProcess中rs与pst的关闭必须相互独立。
 * 修复前两者合并在同一个try,rs.close()抛SQLException会跳过pst.close(),statement未释放直至连接归还
 * (同文件getSequenceValue、DialectUtils的存储过程分支早已是独立关闭范式)
 */
public class SqlUtilPreparedStatementProcessTest {

	private static final class Recorder {
		final List<String> calls = new ArrayList<String>();
		boolean failRsClose = false;
		boolean failPstClose = false;

		ResultSet resultSet() {
			return (ResultSet) Proxy.newProxyInstance(Recorder.class.getClassLoader(), new Class[] { ResultSet.class },
					(proxy, method, args) -> {
						if ("close".equals(method.getName())) {
							calls.add("rs.close");
							if (failRsClose) {
								throw new SQLException("mock rs close failure");
							}
						}
						return defaultReturn(method.getReturnType());
					});
		}

		PreparedStatement statement() {
			return (PreparedStatement) Proxy.newProxyInstance(Recorder.class.getClassLoader(),
					new Class[] { PreparedStatement.class }, (proxy, method, args) -> {
						if ("close".equals(method.getName())) {
							calls.add("pst.close");
							if (failPstClose) {
								throw new SQLException("mock pst close failure");
							}
						}
						return defaultReturn(method.getReturnType());
					});
		}

		CallableStatement callableStatement() {
			return (CallableStatement) Proxy.newProxyInstance(Recorder.class.getClassLoader(),
					new Class[] { CallableStatement.class }, (proxy, method, args) -> {
						if ("close".equals(method.getName())) {
							calls.add("pst.close");
							if (failPstClose) {
								throw new SQLException("mock pst close failure");
							}
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

	private static PreparedStatementResultHandler noopHandler() {
		return new PreparedStatementResultHandler() {
			@Override
			public void execute(Object rowData, PreparedStatement pst, ResultSet rs) {
			}
		};
	}

	@Test
	public void resultSetCloseFailureDoesNotSkipStatementClose() throws Exception {
		Recorder rec = new Recorder();
		rec.failRsClose = true;
		// 修复前:rs.close()抛错被合并的catch吞掉,pst.close()此行不再执行
		SqlUtil.preparedStatementProcess(null, rec.statement(), rec.resultSet(), noopHandler());
		assertTrue(rec.calls.contains("rs.close"), "应尝试关闭rs,实际:" + rec.calls);
		assertTrue(rec.calls.contains("pst.close"), "rs.close()抛错不得跳过pst.close(),实际:" + rec.calls);
	}

	@Test
	public void closeFailuresDoNotMaskHandlerException() {
		Recorder rec = new Recorder();
		rec.failRsClose = true;
		rec.failPstClose = true;
		Exception ex = assertThrows(Exception.class, () -> SqlUtil.preparedStatementProcess(null, rec.statement(),
				rec.resultSet(), new PreparedStatementResultHandler() {
					@Override
					public void execute(Object rowData, PreparedStatement pst, ResultSet rs) throws Exception {
						throw new IllegalStateException("mock handler failure");
					}
				}));
		// 关闭异常各自被吞掉并记录日志,不得替换业务异常
		assertEquals("mock handler failure", ex.getMessage());
		assertTrue(rec.calls.contains("rs.close") && rec.calls.contains("pst.close"),
				"两个资源都应尝试关闭,实际:" + rec.calls);
	}

	@Test
	public void callableResultSetCloseFailureDoesNotSkipStatementClose() throws Exception {
		Recorder rec = new Recorder();
		rec.failRsClose = true;
		SqlUtil.callableStatementProcess(null, rec.callableStatement(), rec.resultSet(),
				new CallableStatementResultHandler() {
					@Override
					public void execute(Object rowData, CallableStatement pst, ResultSet rs) {
					}
				});
		assertTrue(rec.calls.contains("rs.close"), "应尝试关闭rs,实际:" + rec.calls);
		assertTrue(rec.calls.contains("pst.close"), "rs.close()抛错不得跳过callStat.close(),实际:" + rec.calls);
	}
}
