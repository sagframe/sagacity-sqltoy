package org.sagacity.sqltoy.plugins.secure.impl;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

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
import org.sagacity.sqltoy.exception.DataAccessException;
import org.sagacity.sqltoy.plugins.secure.FieldsSecureProvider;

/**
 * 回归测试：Cipher并发安全、加解密字符集一致性、失败路径必须抛异常
 * (修复前失败返回""——加密失败的空串被直接落库使原文永久丢失,解密失败的空串与"字段值为空"无法区分)
 */
public class FieldsRSASecureProviderTest {
	/** 2048位RSA(PKCS1 padding)单块明文上限=256-11 */
	private static final int MAX_PLAIN_BYTES = 256 - 11;

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

	private static String plainText(int length) {
		char[] chars = new char[length];
		Arrays.fill(chars, 'a');
		return new String(chars);
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
	public void plainTextAtKeyLimitStillEncrypts() {
		// 边界内侧:恰好达到上限必须正常加密,与下方越界用例构成边界对
		assertEquals(MAX_PLAIN_BYTES, plainText(MAX_PLAIN_BYTES).length());
		assertNotEquals("", provider.encrypt(plainText(MAX_PLAIN_BYTES)));
	}

	@Test
	public void overLongPlainTextThrowsInsteadOfReturningEmpty() {
		// 修复前:超长明文加密失败返回"",空串被直接落库使原文永久丢失且调用方无感
		DataAccessException ex = assertThrows(DataAccessException.class,
				() -> provider.encrypt(plainText(MAX_PLAIN_BYTES + 1)));
		// 异常需给出密钥明文上限,便于定位是密钥位数过小还是字段过长
		assertTrue(ex.getMessage().contains(String.valueOf(MAX_PLAIN_BYTES)), ex.getMessage());
	}

	@Test
	public void corruptCipherTextThrowsInsteadOfReturningEmpty() {
		// 修复前:非法密文解密失败返回"",查询结果静默变空
		assertThrows(DataAccessException.class, () -> provider.decrypt("not-a-valid-cipher"));
		// 历史遗留的未加密明文:合法base64但不是密文,长度不符同样必须抛异常
		assertThrows(DataAccessException.class, () -> provider.decrypt("Y2hlbnJlbmZlaQ=="));
	}

	@Test
	public void initializeRejectsBlankKeys() {
		assertThrows(IllegalArgumentException.class,
				() -> new FieldsRSASecureProvider().initialize("UTF-8", null, "abc"));
	}
}
