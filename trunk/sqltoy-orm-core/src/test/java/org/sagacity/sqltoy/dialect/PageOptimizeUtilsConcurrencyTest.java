package org.sagacity.sqltoy.dialect;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicReference;

import javax.sql.DataSource;

import org.h2.jdbcx.JdbcDataSource;
import org.junit.jupiter.api.Test;
import org.sagacity.sqltoy.SqlToyContext;
import org.sagacity.sqltoy.config.model.OperateType;
import org.sagacity.sqltoy.config.model.PageOptimize;
import org.sagacity.sqltoy.config.model.SqlToyConfig;
import org.sagacity.sqltoy.model.QueryExecutor;
import org.sagacity.sqltoy.plugins.IUnifyFieldsHandler;

/**
 * 分页count缓存命名空间的并发与隔离深度验证:租户×数据源组合互不串用、并发登记/读取、
 * 并发清理与登记、过期失效、新旧重载方法命名空间隔离
 */
public class PageOptimizeUtilsConcurrencyTest {

	private static final String SQL_ID = "sqltoy_page_optimize_concurrency_test";

	private static class TenantHandler implements IUnifyFieldsHandler {
		private final String tenantId;

		private TenantHandler(String tenantId) {
			this.tenantId = tenantId;
		}

		@Override
		public String getUserTenantId() {
			return tenantId;
		}
	}

	private static SqlToyContext context(String tenantId) {
		SqlToyContext context = new SqlToyContext();
		if (tenantId != null) {
			context.setUnifyFieldsHandler(new TenantHandler(tenantId));
		}
		return context;
	}

	private static SqlToyConfig config() {
		return new SqlToyConfig(SQL_ID, "select * from t_demo");
	}

	private static PageOptimize pageOptimize() {
		return new PageOptimize().aliveMax(100).aliveSeconds(60);
	}

	private static QueryExecutor queryExecutor() {
		QueryExecutor queryExecutor = new QueryExecutor("select * from t_demo");
		queryExecutor.getInnerModel().entityClass = Object.class;
		return queryExecutor;
	}

	private static DataSource newDataSource() {
		return new JdbcDataSource();
	}

	@Test
	public void concurrentTenantDataSourceMatrixDoesNotCrossTalk() throws Exception {
		int threads = 16;
		CountDownLatch start = new CountDownLatch(1);
		CountDownLatch done = new CountDownLatch(threads);
		AtomicReference<Throwable> failure = new AtomicReference<Throwable>();
		PageOptimizeUtils.remove(SQL_ID);
		for (int t = 0; t < threads; t++) {
			final int threadNo = t;
			new Thread(() -> {
				try {
					SqlToyContext context = context("T" + threadNo);
					DataSource dataSource = newDataSource();
					SqlToyConfig config = config();
					PageOptimize pageOptimize = pageOptimize();
					String key = PageOptimizeUtils.generateOptimizeKey(context, config, queryExecutor(),
							pageOptimize);
					long expected = 1000L + threadNo;
					start.await();
					PageOptimizeUtils.registPageTotalCount(config, pageOptimize, key, expected, "h2", context,
							dataSource);
					Thread.sleep(5);
					Long actual = PageOptimizeUtils.getPageTotalCount(config, pageOptimize, key, "h2", context,
							dataSource);
					if (actual == null || actual.longValue() != expected) {
						throw new IllegalStateException(
								"线程" + threadNo + "命中错误count:" + actual + ",期望:" + expected);
					}
				} catch (Throwable e) {
					failure.compareAndSet(null, e);
				} finally {
					done.countDown();
				}
			}).start();
		}
		start.countDown();
		assertTrue(done.await(60, TimeUnit.SECONDS), "并发用例超时");
		PageOptimizeUtils.remove(SQL_ID);
		if (failure.get() != null) {
			throw new AssertionError("并发隔离失败", failure.get());
		}
	}

	@Test
	public void concurrentRegistOnSameNamespaceNeverThrows() throws Exception {
		SqlToyContext context = context("T-shared");
		DataSource dataSource = newDataSource();
		SqlToyConfig config = config();
		PageOptimize pageOptimize = pageOptimize();
		String key = PageOptimizeUtils.generateOptimizeKey(context, config, queryExecutor(), pageOptimize);
		PageOptimizeUtils.remove(SQL_ID);
		int threads = 8;
		CountDownLatch start = new CountDownLatch(1);
		CountDownLatch done = new CountDownLatch(threads);
		AtomicReference<Throwable> failure = new AtomicReference<Throwable>();
		for (int t = 0; t < threads; t++) {
			final long value = t;
			new Thread(() -> {
				try {
					start.await();
					for (int i = 0; i < 2000; i++) {
						PageOptimizeUtils.registPageTotalCount(config, pageOptimize, key, value, "h2", context,
								dataSource);
						PageOptimizeUtils.getPageTotalCount(config, pageOptimize, key, "h2", context, dataSource);
					}
				} catch (Throwable e) {
					failure.compareAndSet(null, e);
				} finally {
					done.countDown();
				}
			}).start();
		}
		start.countDown();
		assertTrue(done.await(60, TimeUnit.SECONDS), "并发登记用例超时");
		if (failure.get() != null) {
			throw new AssertionError("并发登记异常", failure.get());
		}
		Long finalValue = PageOptimizeUtils.getPageTotalCount(config, pageOptimize, key, "h2", context, dataSource);
		assertTrue(finalValue != null && finalValue >= 0 && finalValue < threads);
		PageOptimizeUtils.remove(SQL_ID);
	}

	@Test
	public void concurrentRemoveAndRegistNeverThrows() throws Exception {
		SqlToyContext context = context("T-remove");
		DataSource dataSource = newDataSource();
		SqlToyConfig config = config();
		PageOptimize pageOptimize = pageOptimize();
		String key = PageOptimizeUtils.generateOptimizeKey(context, config, queryExecutor(), pageOptimize);
		PageOptimizeUtils.remove(SQL_ID);
		CountDownLatch start = new CountDownLatch(1);
		CountDownLatch done = new CountDownLatch(2);
		AtomicReference<Throwable> failure = new AtomicReference<Throwable>();
		new Thread(() -> {
			try {
				start.await();
				for (int i = 0; i < 3000; i++) {
					PageOptimizeUtils.registPageTotalCount(config, pageOptimize, key, (long) i, "h2", context,
							dataSource);
				}
			} catch (Throwable e) {
				failure.compareAndSet(null, e);
			} finally {
				done.countDown();
			}
		}).start();
		new Thread(() -> {
			try {
				start.await();
				for (int i = 0; i < 3000; i++) {
					PageOptimizeUtils.remove(SQL_ID);
				}
			} catch (Throwable e) {
				failure.compareAndSet(null, e);
			} finally {
				done.countDown();
			}
		}).start();
		start.countDown();
		assertTrue(done.await(60, TimeUnit.SECONDS), "并发清理用例超时");
		PageOptimizeUtils.remove(SQL_ID);
		if (failure.get() != null) {
			throw new AssertionError("并发清理异常", failure.get());
		}
	}

	@Test
	public void expiredCountIsDroppedOnRead() throws Exception {
		SqlToyContext context = context("T-expire");
		DataSource dataSource = newDataSource();
		SqlToyConfig config = config();
		// aliveSeconds的setter最小钳制到30秒,这里直接改字段以缩短用例耗时(仅测试用)
		PageOptimize pageOptimize = new PageOptimize().aliveMax(10);
		java.lang.reflect.Field aliveField = PageOptimize.class.getDeclaredField("aliveSeconds");
		aliveField.setAccessible(true);
		aliveField.setInt(pageOptimize, 1);
		String key = PageOptimizeUtils.generateOptimizeKey(context, config, queryExecutor(), pageOptimize);
		PageOptimizeUtils.remove(SQL_ID);
		PageOptimizeUtils.registPageTotalCount(config, pageOptimize, key, 9L, "h2", context, dataSource);
		assertEquals(Long.valueOf(9L),
				PageOptimizeUtils.getPageTotalCount(config, pageOptimize, key, "h2", context, dataSource));
		Thread.sleep(1100);
		assertNull(PageOptimizeUtils.getPageTotalCount(config, pageOptimize, key, "h2", context, dataSource),
				"超过aliveSeconds的记录应失效");
		PageOptimizeUtils.remove(SQL_ID);
	}

	@Test
	public void legacyOverloadsUseSeparateNamespace() throws Exception {
		SqlToyContext context = context(null);
		DataSource dataSource = newDataSource();
		SqlToyConfig config = config();
		PageOptimize pageOptimize = pageOptimize();
		PageOptimizeUtils.remove(SQL_ID);
		// 旧重载(无context/数据源)与显式默认数据源的命名空间必须隔离,避免旧调用方误命中新缓存
		PageOptimizeUtils.registPageTotalCount(config, pageOptimize, "k", 1L, "h2");
		assertNull(PageOptimizeUtils.getPageTotalCount(config, pageOptimize, "k", "h2", context, dataSource));
		PageOptimizeUtils.registPageTotalCount(config, pageOptimize, "k", 2L, "h2", context, dataSource);
		assertEquals(Long.valueOf(1L), PageOptimizeUtils.getPageTotalCount(config, pageOptimize, "k", "h2"));
		assertEquals(Long.valueOf(2L),
				PageOptimizeUtils.getPageTotalCount(config, pageOptimize, "k", "h2", context, dataSource));
		PageOptimizeUtils.remove(SQL_ID);
		assertNull(PageOptimizeUtils.getPageTotalCount(config, pageOptimize, "k", "h2"));
	}
}
