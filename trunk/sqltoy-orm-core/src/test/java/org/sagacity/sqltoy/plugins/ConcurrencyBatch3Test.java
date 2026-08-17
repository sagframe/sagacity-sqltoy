package org.sagacity.sqltoy.plugins;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.lang.reflect.Proxy;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.atomic.AtomicInteger;

import javax.sql.DataSource;

import org.junit.jupiter.api.Test;
import org.sagacity.sqltoy.integration.AppContext;
import org.sagacity.sqltoy.integration.ConnectionFactory;
import org.sagacity.sqltoy.plugins.function.FunctionUtils;
import org.sagacity.sqltoy.plugins.id.impl.SnowflakeIdGenerator;
import org.sagacity.sqltoy.plugins.sharding.IdleConnectionMonitor;

/**
 * 回归测试批次：(47)函数转换器部分列表不覆盖全局+volatile;
 * (48)雪花idWorker双检锁并发安全;(49)IdleConnectionMonitor每轮独立资源,异常轮不双重归还连接
 */
public class ConcurrencyBatch3Test {

	// ============ 47: FunctionUtils ============

	@Test
	public void failedFunctionLoadKeepsPreviousConverters() {
		FunctionUtils.setFunctionConverts(Arrays.asList("default"));
		String before = FunctionUtils.getDialectSql("select isnull(a,b) from t", "mysql");
		assertTrue(before.contains("ifnull"), "默认转换器应生效,实际:" + before);
		// 加载失败的自定义函数类:修复前converts为空/部分列表仍覆盖全局,转换静默失效
		FunctionUtils.setFunctionConverts(Arrays.asList("default", "org.not.exists.BadFunction"));
		String after = FunctionUtils.getDialectSql("select isnull(a,b) from t", "mysql");
		assertTrue(after.contains("ifnull"), "失败后应保留原有完整转换器,实际:" + after);
		// 恢复默认,避免影响其他测试
		FunctionUtils.setFunctionConverts(Arrays.asList("default"));
	}

	// ============ 48: SnowflakeIdGenerator ============

	@Test
	public void concurrentGetIdAllUnique() throws Exception {
		SnowflakeIdGenerator generator = new SnowflakeIdGenerator();
		int threads = 8;
		int loops = 5000;
		Set<Long> ids = ConcurrentHashMap.newKeySet();
		AtomicInteger errors = new AtomicInteger();
		CountDownLatch latch = new CountDownLatch(threads);
		for (int t = 0; t < threads; t++) {
			new Thread(() -> {
				try {
					for (int i = 0; i < loops; i++) {
						ids.add(((Number) generator.getId("batch3_table", null, null, null, null, "Long", 0, 0))
								.longValue());
					}
				} catch (Throwable e) {
					errors.incrementAndGet();
				} finally {
					latch.countDown();
				}
			}).start();
		}
		latch.await();
		assertEquals(0, errors.get());
		assertEquals(threads * loops, ids.size(), "并发getID应全部唯一");
	}

	// ============ 49: IdleConnectionMonitor ============

	@Test
	public void exceptionRoundDoesNotDoubleReleaseConnection() throws Exception {
		Connection releasedConn = (Connection) Proxy.newProxyInstance(getClass().getClassLoader(),
				new Class[] { Connection.class }, (proxy, method, args) -> {
					if ("getMetaData".equals(method.getName())) {
						return Proxy.newProxyInstance(getClass().getClassLoader(),
								new Class[] { java.sql.DatabaseMetaData.class }, (metaProxy, metaMethod, metaArgs) -> {
									if ("getDatabaseProductName".equals(metaMethod.getName())) {
										return "MySQL";
									}
									if ("getDatabaseMajorVersion".equals(metaMethod.getName())) {
										return 8;
									}
									return null;
								});
					}
					if ("prepareStatement".equals(method.getName())) {
						return Proxy.newProxyInstance(getClass().getClassLoader(),
								new Class[] { java.sql.PreparedStatement.class }, (pstProxy, pstMethod, pstArgs) -> {
									if ("executeQuery".equals(pstMethod.getName())) {
										return Proxy.newProxyInstance(getClass().getClassLoader(),
												new Class[] { java.sql.ResultSet.class },
												(rsProxy, rsMethod, rsArgs) -> null);
									}
									if (pstMethod.getReturnType() == boolean.class) {
										return false;
									}
									if (pstMethod.getReturnType() == int.class) {
										return 0;
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
		DataSource ds1 = (DataSource) Proxy.newProxyInstance(getClass().getClassLoader(),
				new Class[] { DataSource.class }, (proxy, method, args) -> null);
		AtomicInteger releaseCount = new AtomicInteger();
		List<String> releaseLog = Collections.synchronizedList(new ArrayList<String>());
		ConnectionFactory connFactory = new ConnectionFactory() {
			@Override
			public Connection getConnection(DataSource dataSource) {
				return releasedConn;
			}

			@Override
			public void releaseConnection(Connection conn, DataSource dataSource) {
				releaseCount.incrementAndGet();
				releaseLog.add("release#" + releaseCount.get());
			}
		};
		// 第1个数据源正常,第2个getBean抛异常(修复前:残留变量导致第1轮连接被双重归还)
		AppContext appContext = (AppContext) Proxy.newProxyInstance(getClass().getClassLoader(),
				new Class[] { AppContext.class }, (proxy, method, args) -> {
					if ("getBean".equals(method.getName())) {
						if ("ds1".equals(args[0])) {
							return ds1;
						}
						throw new RuntimeException("bean not found");
					}
					return null;
				});
		int[] weights = new int[2];
		IdleConnectionMonitor monitor = new IdleConnectionMonitor(appContext, connFactory,
				new Object[][] { { "ds1", 5 }, { "ds2", 5 } }, weights, 0, 3600);
		assertTrue(monitor.isDaemon(), "监测线程应为daemon");
		// 预置中断标志:首轮检测完成后run()检测到中断退出,避免长眠
		Thread.currentThread().interrupt();
		monitor.run();
		Thread.interrupted(); // 清除标志
		assertEquals(1, releaseCount.get(), "连接归还次数,实际:" + releaseLog);
		assertEquals(5, weights[0]);
		assertEquals(0, weights[1]);
	}

	@SuppressWarnings("unused")
	private static void keepImports(PreparedStatement p) {
	}
}
