package org.sagacity.sqltoy.translate;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.junit.jupiter.api.Assertions.fail;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.atomic.AtomicInteger;

import org.junit.jupiter.api.Test;
import org.sagacity.sqltoy.SqlToyContext;
import org.sagacity.sqltoy.model.IgnoreKeyCaseMap;
import org.sagacity.sqltoy.translate.model.CheckerConfigModel;
import org.sagacity.sqltoy.translate.model.DefaultConfig;
import org.sagacity.sqltoy.translate.model.TranslateConfigModel;

/**
 * 回归:缓存更新检测器的"重复定义"判定必须严格属于单次解析会话。
 * 修复前去重集合是静态共享的,且每次解析入口clear:同一JVM内两个SqlToyContext并发解析translate配置时,
 * 一方clear会抹掉另一方刚登记的检测器,导致同一份合法配置偶发抛"duplicate update checker"启动失败
 * (也可能反向漏检同文件内的真正重复定义)
 */
public class TranslateConfigSessionTest {

	private static final String SESSION_CONFIG = "classpath:translate/session-checker-translate.xml";

	private static final String DUPLICATE_CONFIG = "classpath:translate/duplicate-checker-translate.xml";

	private static DefaultConfig parse(String config) throws Exception {
		return TranslateConfigParse.parseTranslateConfig(new SqlToyContext(),
				new IgnoreKeyCaseMap<String, TranslateConfigModel>(), new CopyOnWriteArrayList<CheckerConfigModel>(),
				config, false, "UTF-8");
	}

	@Test
	public void duplicateCheckerInSameConfigDetected() {
		RuntimeException ex = assertThrows(RuntimeException.class, () -> parse(DUPLICATE_CONFIG));
		assertTrue(ex.getMessage().contains("duplicate update checker"), "实际:" + ex.getMessage());
	}

	@Test
	public void twoSequentialSessionsWithSameCheckerDoNotConflict() throws Exception {
		DefaultConfig first = parse(SESSION_CONFIG);
		DefaultConfig second = parse(SESSION_CONFIG);
		// 两个上下文先后解析同一份配置:各自的会话去重互不影响
		assertTrue(first.isUseCache());
		assertTrue(second.isUseCache());
	}

	@Test
	public void concurrentSessionsWithSameCheckerDoNotInterfere() throws Exception {
		final int threads = 2;
		final int iterations = 80;
		final CountDownLatch startGate = new CountDownLatch(1);
		final AtomicInteger done = new AtomicInteger();
		final List<Throwable> failures = new CopyOnWriteArrayList<Throwable>();
		List<Thread> threadList = new ArrayList<Thread>();
		for (int t = 0; t < threads; t++) {
			Thread thread = new Thread(() -> {
				try {
					startGate.await();
					for (int i = 0; i < iterations; i++) {
						parse(SESSION_CONFIG);
						done.incrementAndGet();
					}
				} catch (Throwable e) {
					failures.add(e);
				}
			});
			thread.start();
			threadList.add(thread);
		}
		startGate.countDown();
		for (Thread thread : threadList) {
			thread.join(60_000);
		}
		if (!failures.isEmpty()) {
			Throwable first = failures.get(0);
			fail("并发解析两个独立会话时不得互相干扰,失败:[" + failures.size() + "]次,首个异常:" + first.getMessage());
		}
		assertEquals(threads * iterations, done.get());
	}
}
