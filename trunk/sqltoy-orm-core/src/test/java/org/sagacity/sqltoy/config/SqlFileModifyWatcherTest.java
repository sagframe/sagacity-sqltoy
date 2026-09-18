package org.sagacity.sqltoy.config;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.ArrayList;
import java.util.concurrent.ConcurrentHashMap;

import org.junit.jupiter.api.Test;
import org.sagacity.sqltoy.config.model.SqlToyConfig;

/**
 * 回归测试：sql文件变更监视线程为daemon(destroy未调用时不阻止JVM退出); 中断后退出循环并恢复中断标志(上层线程池/关闭钩子可感知)
 */
public class SqlFileModifyWatcherTest {

	@Test
	public void watcherIsDaemonAndNamed() {
		SqlFileModifyWatcher watcher = new SqlFileModifyWatcher(new ConcurrentHashMap<String, SqlToyConfig>(),
				new ConcurrentHashMap<String, Long>(), new ArrayList<Object>(), "mysql", "UTF-8", 0, 1);
		assertTrue(watcher.isDaemon(), "监视线程应为daemon");
		assertTrue("sqltoy-sql-file-watcher".equals(watcher.getName()), "实际:" + watcher.getName());
	}

	@Test
	public void interruptedWatcherExitsAndRestoresFlag() throws Exception {
		final boolean[] flagRestored = new boolean[1];
		Thread runner = new Thread(() -> {
			SqlFileModifyWatcher watcher = new SqlFileModifyWatcher(new ConcurrentHashMap<String, SqlToyConfig>(),
					new ConcurrentHashMap<String, Long>(), new ArrayList<Object>(), "mysql", "UTF-8", 0, 3600);
			// 预置中断:run()中sleep被中断→恢复标志→退出;返回后线程的中断状态应可见
			Thread.currentThread().interrupt();
			long start = System.currentTimeMillis();
			watcher.run();
			flagRestored[0] = Thread.currentThread().isInterrupted();
			assertTrue(System.currentTimeMillis() - start < 10_000, "应快速退出而非睡满检测间隔");
		});
		runner.start();
		runner.join(15_000);
		assertFalse(runner.isAlive(), "watcher run()应已退出");
		assertTrue(flagRestored[0], "中断标志应被恢复供上层感知");
	}
}
