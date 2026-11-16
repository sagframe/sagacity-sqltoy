package org.sagacity.sqltoy.solon.integration;

import java.util.concurrent.Executor;
import java.util.concurrent.ThreadFactory;
import java.util.concurrent.ThreadPoolExecutor;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;

import com.alibaba.ttl.TtlRunnable;

/**
 * @project sagacity-sqltoy
 * @description solon插件按taskExecutor配置参数构建的默认线程池(对齐spring starter在targetPoolName为空时
 *              的构建行为):此前solon侧仅targetPoolName生效,threadNamePrefix/corePoolSize等参数配置无效。
 *              <li>类型即所有权标记:Plugin.stop()据此仅关闭插件自建的池,
 *              targetPoolName指向的外部线程池bean不受影响</li>
 *              <li>execute经TtlRunnable包装提交,与spring starter的TtlExecutors包装等价,
 *              保障并行查询的TransmittableThreadLocal上下文传播</li>
 *              <li>shutdown语义对齐spring的ThreadPoolTaskExecutor:
 *              waitForTasksToCompleteOnShutdown=true走shutdown()(不中断执行中任务),
 *              false走shutdownNow();awaitTerminationSeconds>0时等待指定秒数</li>
 * @author zhongxuchen
 * @version v1.0,Date:2026-11-16
 */
public class SolonTaskExecutor implements Executor {

	private final ThreadPoolExecutor delegate;

	private final boolean waitForTasksToCompleteOnShutdown;

	private final int awaitTerminationSeconds;

	SolonTaskExecutor(ThreadPoolExecutor delegate, boolean waitForTasksToCompleteOnShutdown,
			int awaitTerminationSeconds) {
		this.delegate = delegate;
		this.waitForTasksToCompleteOnShutdown = waitForTasksToCompleteOnShutdown;
		this.awaitTerminationSeconds = awaitTerminationSeconds;
	}

	/**
	 * 委托执行,任务经TtlRunnable包装(TransmittableThreadLocal上下文跨线程传播)
	 */
	@Override
	public void execute(Runnable command) {
		delegate.execute(TtlRunnable.get(command));
	}

	/**
	 * 插件stop时统一调用:关闭底层线程池,语义对齐spring的ThreadPoolTaskExecutor销毁行为
	 */
	public void shutdown() {
		if (waitForTasksToCompleteOnShutdown) {
			delegate.shutdown();
		} else {
			delegate.shutdownNow();
		}
		if (awaitTerminationSeconds > 0) {
			try {
				delegate.awaitTermination(awaitTerminationSeconds, TimeUnit.SECONDS);
			} catch (InterruptedException e) {
				// 等待被中断:立即返回并恢复中断标记,由调用方决定后续处理
				Thread.currentThread().interrupt();
			}
		}
	}

	/**
	 * 线程命名工厂:线程名带序号便于排查(等价spring的CustomizableThreadFactory);
	 * 非守护线程——任务不被JVM退出截断,关闭由SolonTaskExecutor.shutdown在插件stop时统一处理
	 */
	static class NamedThreadFactory implements ThreadFactory {
		private final String prefix;
		private final AtomicInteger seq = new AtomicInteger(1);

		NamedThreadFactory(String prefix) {
			this.prefix = prefix;
		}

		@Override
		public Thread newThread(Runnable r) {
			return new Thread(r, prefix + "-" + seq.getAndIncrement());
		}
	}
}
