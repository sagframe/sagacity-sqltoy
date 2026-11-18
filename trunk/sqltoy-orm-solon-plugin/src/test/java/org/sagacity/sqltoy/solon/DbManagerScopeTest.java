package org.sagacity.sqltoy.solon;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertNotSame;
import static org.junit.jupiter.api.Assertions.assertSame;

import java.lang.reflect.Proxy;

import javax.sql.DataSource;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.sagacity.sqltoy.SqlToyContext;
import org.sagacity.sqltoy.dao.SqlToyLazyDao;

/**
 * 回归:DbManager是单SqlToyContext的JVM级门面,上下文切换时必须清空按DataSource缓存的DAO,
 * 否则新上下文会继续复用绑定旧上下文的实例(方言/翻译/加解密等配置全部错位)。
 * 单上下文场景行为不变
 */
public class DbManagerScopeTest {

	private static DataSource mockDataSource() {
		return (DataSource) Proxy.newProxyInstance(DbManagerScopeTest.class.getClassLoader(),
				new Class[] { DataSource.class }, (proxy, method, args) -> {
					// 作为ConcurrentHashMap的key需要正确的hashCode/equals语义
					if ("hashCode".equals(method.getName())) {
						return System.identityHashCode(proxy);
					}
					if ("equals".equals(method.getName())) {
						return proxy == args[0];
					}
					if ("toString".equals(method.getName())) {
						return "mockDataSource";
					}
					return null;
				});
	}

	@AfterEach
	public void clean() {
		DbManager.setContext(null);
	}

	@Test
	public void contextSwitchRebuildsCachedDao() {
		DataSource dataSource = mockDataSource();
		SqlToyContext contextA = new SqlToyContext();
		SqlToyContext contextB = new SqlToyContext();
		DbManager.setContext(contextA);
		SqlToyLazyDao daoA = DbManager.getDao(dataSource);
		// 同一上下文内保持缓存复用
		assertSame(daoA, DbManager.getDao(dataSource));
		// 上下文切换后不得复用绑定旧上下文的DAO
		assertDoesNotThrow(() -> DbManager.setContext(contextB));
		SqlToyLazyDao daoB = DbManager.getDao(dataSource);
		assertNotSame(daoA, daoB, "上下文切换后应重建DAO,不得继续复用旧上下文实例");
		// 新上下文下保持稳定
		assertSame(daoB, DbManager.getDao(dataSource));
	}
}
