package org.sagacity.sqltoy.integration.impl;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.lang.reflect.Proxy;
import java.sql.Connection;
import java.sql.SQLException;

import javax.sql.DataSource;

import org.junit.jupiter.api.Test;
import org.sagacity.sqltoy.exception.DataAccessException;

/**
 * 回归测试：连接获取失败必须抛出携带根因的异常(修复前吞掉SQLException返回null,
 * 后续jdbc操作以远离根因的NPE暴露);关闭失败仅记录日志不外抛
 */
public class SimpleConnectionFactoryTest {
	private final SimpleConnectionFactory factory = new SimpleConnectionFactory();

	private Object defaultAnswer(java.lang.reflect.Method method, Object... args) throws Throwable {
		return null;
	}

	@Test
	public void getConnectionFailureThrowsWithRootCause() {
		DataSource badDataSource = (DataSource) Proxy.newProxyInstance(getClass().getClassLoader(),
				new Class[] { DataSource.class }, (proxy, method, args) -> {
					if ("getConnection".equals(method.getName())) {
						throw new SQLException("pool exhausted");
					}
					return defaultAnswer(method, args);
				});
		DataAccessException ex = assertThrows(DataAccessException.class, () -> factory.getConnection(badDataSource));
		// 根因必须保留,修复前返回null连接
		assertTrue(ex.getCause() instanceof SQLException);
		assertTrue(ex.getCause().getMessage().contains("pool exhausted"));
	}

	@Test
	public void getConnectionReturnsDataSourceConnection() {
		Connection conn = (Connection) Proxy.newProxyInstance(getClass().getClassLoader(),
				new Class[] { Connection.class }, (proxy, method, args) -> defaultAnswer(method, args));
		DataSource dataSource = (DataSource) Proxy.newProxyInstance(getClass().getClassLoader(),
				new Class[] { DataSource.class },
				(proxy, method, args) -> "getConnection".equals(method.getName()) ? conn : defaultAnswer(method, args));
		assertSame(conn, factory.getConnection(dataSource));
	}

	@Test
	public void releaseConnectionCloseFailureNotThrown() {
		Connection badConn = (Connection) Proxy.newProxyInstance(getClass().getClassLoader(),
				new Class[] { Connection.class }, (proxy, method, args) -> {
					if ("close".equals(method.getName())) {
						throw new SQLException("close failed");
					}
					return defaultAnswer(method, args);
				});
		// close失败仅记录日志,不影响释放流程
		assertDoesNotThrow(() -> factory.releaseConnection(badConn, null));
	}
}
