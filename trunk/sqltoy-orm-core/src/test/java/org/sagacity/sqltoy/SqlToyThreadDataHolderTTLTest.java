package org.sagacity.sqltoy;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;

import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;

import org.junit.jupiter.api.Test;
import org.sagacity.sqltoy.model.DBProfile;
import org.sagacity.sqltoy.utils.DataSourceUtils;

import com.alibaba.ttl.threadpool.TtlExecutors;

/**
 * update 2026-11-11 方言上下文(TransmittableThreadLocal)的线程池传播与污染测试(必经路径,风险驱动轮):
 * DBProfile在连接获取时绑定线程、finally清除(DataSourceUtils.processDataSource)——
 * 线程池场景下三个契约:
 * <ol>
 * <li>TTL装饰的线程池:提交时快照传播,任务内可见;提交侧清除后后续任务无残留</li>
 * <li>普通线程池(未装饰):池线程预热后主线程的设置不得泄漏进任务(无幻影继承)</li>
 * <li>任务内set+finally clear的纪律:下一个任务不得读到上一个任务的档案(无跨任务污染)</li>
 * </ol>
 */
public class SqlToyThreadDataHolderTTLTest {

	private static DBProfile profileOf(int dbType) {
		return new DBProfile("jdbc:test://localhost/db", "test", dbType, "TestDB", 1, null, null, false);
	}

	@Test
	public void ttlInstrumentedPoolTransmitsAndLeavesNoResidue() throws Exception {
		ExecutorService pool = TtlExecutors.getTtlExecutorService(Executors.newFixedThreadPool(2));
		try {
			// 提交侧设置:任务内可见(TTL快照传播)
			SqlToyThreadDataHolder.setDBProfile(profileOf(DataSourceUtils.DBType.MYSQL));
			try {
				Integer seen = pool.submit(() -> SqlToyThreadDataHolder.getActuallyDBType()).get(10, TimeUnit.SECONDS);
				assertEquals(Integer.valueOf(DataSourceUtils.DBType.MYSQL), seen, "TTL池应传播提交侧的方言档案");
			} finally {
				SqlToyThreadDataHolder.clearDBProfile();
			}
			// 清除后:同池后续任务不得读到残留
			Integer residue = pool.submit(() -> SqlToyThreadDataHolder.getActuallyDBType()).get(10, TimeUnit.SECONDS);
			assertNull(residue, "提交侧清除后池内不得有残留档案");
		} finally {
			pool.shutdownNow();
		}
	}

	@Test
	public void plainPoolDoesNotPhantomInheritLateSets() throws Exception {
		ExecutorService pool = Executors.newFixedThreadPool(2);
		try {
			// 预热池线程(创建先于主线程的设置,规避InheritableThreadLocal的创建期继承)
			pool.submit(() -> null).get(10, TimeUnit.SECONDS);
			pool.submit(() -> null).get(10, TimeUnit.SECONDS);
			// 主线程设置方言档案
			SqlToyThreadDataHolder.setDBProfile(profileOf(DataSourceUtils.DBType.ORACLE));
			try {
				Integer seen = pool.submit(() -> SqlToyThreadDataHolder.getActuallyDBType()).get(10, TimeUnit.SECONDS);
				assertNull(seen, "未装饰的普通池不得幻影继承主线程晚到的档案(见到即跨线程污染): " + seen);
			} finally {
				SqlToyThreadDataHolder.clearDBProfile();
			}
		} finally {
			pool.shutdownNow();
		}
	}

	@Test
	public void taskScopedSetClearLeavesNoCrossTaskPollution() throws Exception {
		ExecutorService pool = Executors.newFixedThreadPool(1);
		try {
			// 模拟processDataSource纪律:任务内set,finally clear
			pool.submit(() -> {
				try {
					SqlToyThreadDataHolder.setDBProfile(profileOf(DataSourceUtils.DBType.DB2));
					return SqlToyThreadDataHolder.getActuallyDBType();
				} finally {
					SqlToyThreadDataHolder.clearDBProfile();
				}
			}).get(10, TimeUnit.SECONDS);
			// 同一池线程的下一个任务:不得读到上一任务的DB2档案
			Integer next = pool.submit(() -> SqlToyThreadDataHolder.getActuallyDBType()).get(10, TimeUnit.SECONDS);
			assertNull(next, "任务级set+clear后不得跨任务污染: " + next);
		} finally {
			pool.shutdownNow();
		}
	}

	@Test
	public void clearAllResetsEverything() {
		SqlToyThreadDataHolder.setLanguage("zh");
		SqlToyThreadDataHolder.setDBProfile(profileOf(DataSourceUtils.DBType.MYSQL));
		SqlToyThreadDataHolder.clearAll();
		assertNull(SqlToyThreadDataHolder.getLanguage());
		assertNull(SqlToyThreadDataHolder.getActuallyDBType());
	}
}
