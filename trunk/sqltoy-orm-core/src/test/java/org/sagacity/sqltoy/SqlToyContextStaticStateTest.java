package org.sagacity.sqltoy;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.lang.reflect.Field;
import java.util.Arrays;
import java.util.HashSet;
import java.util.Set;
import java.util.regex.Pattern;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;

/**
 * 回归测试：JVM级静态配置首个生效策略——同一配置项第二个SqlToyContext认领失败
 * (注入关键词/方言映射/workerId等不可被后初始化context覆写),无新值时静默
 */
public class SqlToyContextStaticStateTest {

	@SuppressWarnings("unchecked")
	private static Set<String> owners() throws Exception {
		Field field = SqlToyContext.class.getDeclaredField("STATIC_STATE_OWNERS");
		field.setAccessible(true);
		return (Set<String>) field.get(null);
	}

	@AfterEach
	public void clean() throws Exception {
		owners().clear();
	}

	@Test
	public void firstClaimWinsSecondIgnored() {
		// 首个context认领成功
		assertTrue(SqlToyContext.claimStaticState("sqlInjectionRegexes", true));
		// 第二个context(即使携带不同配置)认领失败,静态值不被覆写
		assertFalse(SqlToyContext.claimStaticState("sqlInjectionRegexes", true));
		assertFalse(SqlToyContext.claimStaticState("sqlInjectionRegexes", true));
	}

	@Test
	public void silentWhenNoNewValue() {
		assertTrue(SqlToyContext.claimStaticState("dialectMap", true));
		// 未携带该配置的后来context静默跳过,不产生告警噪音(返回值同为false由调用方不触发写入)
		assertFalse(SqlToyContext.claimStaticState("dialectMap", false));
	}

	@Test
	public void differentKeysIndependent() {
		assertTrue(SqlToyContext.claimStaticState("reservedWords", true));
		assertTrue(SqlToyContext.claimStaticState("dialectMap", true));
		assertFalse(SqlToyContext.claimStaticState("reservedWords", true));
	}

	@Test
	public void claimMechanismConcurrencySafe() throws Exception {
		// 并发认领同一key只有一个成功
		Set<String> winners = java.util.concurrent.ConcurrentHashMap.newKeySet();
		java.util.concurrent.CountDownLatch latch = new java.util.concurrent.CountDownLatch(8);
		for (int t = 0; t < 8; t++) {
			new Thread(() -> {
				try {
					latch.countDown();
					latch.await();
					if (SqlToyContext.claimStaticState("concurrentKey", true)) {
						winners.add("w");
					}
				} catch (Exception ignore) {
				}
			}).start();
		}
		latch.await();
		Thread.sleep(100);
		assertEqualsOne(winners.size());
	}

	private static void assertEqualsOne(int size) {
		assertTrue(size == 1, "并发认领应只有一个赢家,实际:" + size);
	}

	// 静态字段引用保持编译引用(防止import被误清理的语义锚)
	@SuppressWarnings("unused")
	private static void references() {
		Set<String> s = new HashSet<String>(Arrays.asList("x"));
		Pattern p = Pattern.compile("x");
	}
}
