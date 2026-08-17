package org.sagacity.sqltoy.plugins.overtime;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.List;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.atomic.AtomicInteger;

import org.junit.jupiter.api.Test;
import org.sagacity.sqltoy.model.OverTimeSql;

/**
 * 回归测试：慢sql插件(a)getSlowest请求size条必须返回size条(修复前差一错误);
 * (b)并发log对同一sqlId的计数累加不允许丢失更新,对无sqlId队列的并发offer不允许破坏结构
 */
public class DefaultOverTimeHandlerTest {

	@Test
	public void getSlowestReturnsRequestedSize() {
		DefaultOverTimeHandler handler = new DefaultOverTimeHandler();
		handler.log(new OverTimeSql("sql_a", "select 1", 100, "trace"));
		handler.log(new OverTimeSql("sql_b", "select 2", 300, "trace"));
		handler.log(new OverTimeSql("sql_c", "select 3", 200, "trace"));
		List<OverTimeSql> slowest = handler.getSlowest(2, true);
		// 修复前subList(0,size-1)差一,请求2条只返回1条,getSlowest(1)返回空
		assertEquals(2, slowest.size());
		assertEquals("sql_b", slowest.get(0).getId());
		assertEquals("sql_c", slowest.get(1).getId());
	}

	@Test
	public void concurrentLogSameSqlIdNoLostUpdate() throws Exception {
		DefaultOverTimeHandler handler = new DefaultOverTimeHandler();
		int threads = 8;
		int loops = 1000;
		CountDownLatch latch = new CountDownLatch(threads);
		AtomicInteger errors = new AtomicInteger();
		for (int t = 0; t < threads; t++) {
			final int taskId = t;
			new Thread(() -> {
				try {
					for (int i = 0; i < loops; i++) {
						handler.log(new OverTimeSql("sql_a", "select 1", (taskId * loops + i) % 500, "trace"));
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
		// overTimeCount初始为1,总计8000次log后必须恰好等于8000,任何丢失更新都会使计数偏小
		OverTimeSql merged = handler.getSlowest(1, true).get(0);
		assertEquals(threads * loops, merged.getOverTimeCount(), "并发log同一sqlId存在丢失更新");
	}

	@Test
	public void concurrentNoSqlIdLogsKeepQueueConsistent() throws Exception {
		DefaultOverTimeHandler handler = new DefaultOverTimeHandler();
		int threads = 8;
		int loops = 1000;
		CountDownLatch latch = new CountDownLatch(threads);
		AtomicInteger errors = new AtomicInteger();
		for (int t = 0; t < threads; t++) {
			final int taskId = t;
			new Thread(() -> {
				try {
					for (int i = 0; i < loops; i++) {
						handler.log(new OverTimeSql(null, "select 1", (taskId * loops + i) % 500, "trace"));
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
		// 队列容量上限500,并发offer不允许破坏堆结构导致异常或超限
		List<OverTimeSql> all = handler.getSlowest(Integer.MAX_VALUE, false);
		assertTrue(all.size() <= 500);
	}
}
