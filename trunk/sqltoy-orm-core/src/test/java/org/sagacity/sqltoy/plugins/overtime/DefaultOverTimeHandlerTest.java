package org.sagacity.sqltoy.plugins.overtime;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;
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
	public void hugeTakeTimeSpreadKeepsDescendingOrder() {
		DefaultOverTimeHandler handler = new DefaultOverTimeHandler();
		// 差值超Integer.MAX:long差值截断int后符号翻转,旧比较器会把最慢的记录当成最慢的"反向"
		// 排到末尾(元素数控制在3以内走确定性逐对比较,不受TimSort对不一致比较器的容错影响)
		handler.log(new OverTimeSql("sql_huge", "select 1", Integer.MAX_VALUE + 10L, "trace"));
		handler.log(new OverTimeSql("sql_1", "select 1", 1L, "trace"));
		handler.log(new OverTimeSql("sql_2", "select 1", 2L, "trace"));
		List<OverTimeSql> slowest = handler.getSlowest(Integer.MAX_VALUE, true);
		assertEquals(3, slowest.size());
		assertEquals("sql_huge", slowest.get(0).getId());
		assertEquals("sql_2", slowest.get(1).getId());
		assertEquals("sql_1", slowest.get(2).getId());
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

	@Test
	public void noSqlIdQueueReturnsTrulySlowestInDescendingOrder() {
		DefaultOverTimeHandler handler = new DefaultOverTimeHandler();
		// 先放入最大耗时、再放入一串更小的:最小堆的数组层序会把100排在中间位置,
		// 修复前"取迭代的最后size个"会拿到 [90,80,100] 这类任意子集,而非真正最慢的 [100,90,80]
		handler.log(new OverTimeSql(null, "select 100", 100, "trace"));
		for (int i = 1; i <= 9; i++) {
			handler.log(new OverTimeSql(null, "select " + (i * 10), i * 10, "trace"));
		}
		List<OverTimeSql> slowest = handler.getSlowest(3, false);
		assertEquals(3, slowest.size());
		assertEquals(100L, slowest.get(0).getTakeTime());
		assertEquals(90L, slowest.get(1).getTakeTime());
		assertEquals(80L, slowest.get(2).getTakeTime());
	}

	/* ==================== log合并语义(同sqlId) ==================== */

	@Test
	public void mergeUpdatesAverageCountAndKeepsFirstLogTime() {
		DefaultOverTimeHandler handler = new DefaultOverTimeHandler();
		OverTimeSql first = new OverTimeSql("sql_a", "select 1", 100, "trace");
		handler.log(first);
		// 第二次耗时更大:替换为新记录,均值=加权平均(100*1+200)/2=150.000,次数累加,首次时间保留
		OverTimeSql second = new OverTimeSql("sql_a", "select 1", 200, "trace");
		handler.log(second);
		OverTimeSql merged = handler.getSlowest(1, true).get(0);
		assertEquals(200L, merged.getTakeTime(), "更大耗时者应胜出");
		assertEquals(2, merged.getOverTimeCount());
		assertEquals(0, merged.getAveTakeTime().compareTo(new java.math.BigDecimal("150.000")));
		assertEquals(first.getFirstLogTime(), merged.getFirstLogTime(), "首次超时时间应保留");
		// 第三次耗时不超过当前值:在原记录对象上就地更新,均值=(150*2+200)/3≈166.667
		OverTimeSql third = new OverTimeSql("sql_a", "select 1", 200, "trace");
		handler.log(third);
		OverTimeSql merged3 = handler.getSlowest(1, true).get(0);
		assertSame(merged, merged3, "不超过当前耗时应在原记录上就地更新");
		assertEquals(200L, merged3.getTakeTime(), "更小/相等耗时不得覆盖最大耗时");
		assertEquals(3, merged3.getOverTimeCount());
		assertEquals(0, merged3.getAveTakeTime().compareTo(new java.math.BigDecimal("166.667")));
	}

	@Test
	public void averageRoundsHalfUpAtThirdDecimal() {
		DefaultOverTimeHandler handler = new DefaultOverTimeHandler();
		handler.log(new OverTimeSql("sql_r", "select 1", 1, "trace"));
		handler.log(new OverTimeSql("sql_r", "select 1", 2, "trace"));
		handler.log(new OverTimeSql("sql_r", "select 1", 1, "trace"));
		// 第二次胜出:avg=(1+2)/2=1.5;再入1(不大于2):(1.5*2+1)/3=1.333(第三位HALF_UP)
		OverTimeSql merged = handler.getSlowest(1, true).get(0);
		assertEquals(0, merged.getAveTakeTime().compareTo(new java.math.BigDecimal("1.333")));
		assertEquals(3, merged.getOverTimeCount());
		assertEquals(2L, merged.getTakeTime());
	}

	@Test
	public void blankSqlIdRoutesToNoIdQueue() {
		DefaultOverTimeHandler handler = new DefaultOverTimeHandler();
		handler.log(new OverTimeSql("   ", "select 1", 500, "trace"));
		// 空白sqlId视同无sqlId:不进入map,进入队列
		assertEquals(0, handler.getSlowest(10, true).size());
		assertEquals(1, handler.getSlowest(10, false).size());
	}

	@Test
	public void capacityLimitKeepsSlowestNoSqlIdEntries() {
		DefaultOverTimeHandler handler = new DefaultOverTimeHandler();
		// 队列上限500:放入600条递增耗时,应挤掉最快的100条,保留最慢的500条
		for (long i = 1; i <= 600; i++) {
			handler.log(new OverTimeSql(null, "select " + i, i, "trace"));
		}
		List<OverTimeSql> all = handler.getSlowest(Integer.MAX_VALUE, false);
		assertEquals(500, all.size());
		assertEquals(600L, all.get(0).getTakeTime(), "最慢的应排最前");
		assertEquals(101L, all.get(499).getTakeTime(), "最快的100条应被淘汰");
	}

	@Test
	public void emptyHandlerAndSizeValidation() {
		DefaultOverTimeHandler handler = new DefaultOverTimeHandler();
		assertEquals(0, handler.getSlowest(5, true).size());
		assertEquals(0, handler.getSlowest(5, false).size());
		assertThrows(IllegalArgumentException.class, () -> handler.getSlowest(0, true));
		assertThrows(IllegalArgumentException.class, () -> handler.getSlowest(-1, false));
		// 两个池互不混入
		handler.log(new OverTimeSql("sql_x", "select 1", 100, "trace"));
		handler.log(new OverTimeSql(null, "select 2", 200, "trace"));
		assertEquals(1, handler.getSlowest(10, true).size());
		assertEquals(1, handler.getSlowest(10, false).size());
		assertEquals(100L, handler.getSlowest(10, true).get(0).getTakeTime());
		assertEquals(200L, handler.getSlowest(10, false).get(0).getTakeTime());
	}
}
