package org.sagacity.sqltoy.plugins.secure.impl;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

import java.security.KeyPair;
import java.security.KeyPairGenerator;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Base64;
import java.util.List;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;

import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.sagacity.sqltoy.plugins.secure.FieldsSecureProvider;

/**
 * 回归测试：Cipher并发安全、加解密字符集一致性、失败路径返回空串不抛异常
 */
public class FieldsRSASecureProviderTest {
	private static FieldsSecureProvider provider;

	@BeforeAll
	public static void setUp() throws Exception {
		KeyPairGenerator generator = KeyPairGenerator.getInstance("RSA");
		generator.initialize(2048);
		KeyPair keyPair = generator.generateKeyPair();
		String privateKeyStr = Base64.getEncoder().encodeToString(keyPair.getPrivate().getEncoded());
		String publicKeyStr = Base64.getEncoder().encodeToString(keyPair.getPublic().getEncoded());
		provider = new FieldsRSASecureProvider();
		provider.initialize(null, privateKeyStr, publicKeyStr);
	}

	@Test
	public void roundtripWithChineseCharset() {
		// 加密按CHARSET编码,解密也必须按CHARSET还原,否则非UTF-8默认字符集平台中文乱码
		String plain = "你好世界Hello123!@#密码";
		assertEquals(plain, provider.decrypt(provider.encrypt(plain)));
	}

	@Test
	public void concurrentEncryptDecrypt() throws Exception {
		int threads = 8;
		int loops = 100;
		ExecutorService pool = Executors.newFixedThreadPool(threads);
		AtomicInteger failures = new AtomicInteger();
		List<Future<?>> futures = new ArrayList<Future<?>>();
		for (int t = 0; t < threads; t++) {
			final int taskId = t;
			futures.add(pool.submit(() -> {
				for (int i = 0; i < loops; i++) {
					String plain = "机密数据-" + taskId + "-" + i + "-" + System.nanoTime();
					// 共享Cipher并发doFinal会抛异常或产生错乱密文导致还原失败
					if (!plain.equals(provider.decrypt(provider.encrypt(plain)))) {
						failures.incrementAndGet();
					}
				}
			}));
		}
		pool.shutdown();
		pool.awaitTermination(120, TimeUnit.SECONDS);
		for (Future<?> future : futures) {
			future.get();
		}
		assertEquals(0, failures.get());
	}

	@Test
	public void failureReturnsEmptyWithoutThrowing() {
		// 2048位RSA/PKCS1单块明文上限245字节,超长加密失败返回空串
		char[] chars = new char[300];
		Arrays.fill(chars, 'a');
		assertEquals("", provider.encrypt(new String(chars)));
		// 非法密文解密失败返回空串,不向查询链路抛异常
		assertEquals("", provider.decrypt("not-a-valid-cipher"));
	}

	@Test
	public void initializeRejectsBlankKeys() {
		assertThrows(IllegalArgumentException.class,
				() -> new FieldsRSASecureProvider().initialize("UTF-8", null, "abc"));
	}
}
