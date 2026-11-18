package org.sagacity.sqltoy.plugins.nosql;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.StringReader;
import java.lang.reflect.Field;
import java.nio.charset.StandardCharsets;
import java.security.KeyPair;
import java.security.KeyPairGenerator;
import java.security.Signature;
import java.util.Base64;
import java.util.Map;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;

import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;
import javax.xml.parsers.DocumentBuilderFactory;

import org.junit.jupiter.api.Test;
import org.sagacity.sqltoy.translate.model.CheckerConfigModel;
import org.sagacity.sqltoy.utils.XMLUtil;
import org.w3c.dom.Element;
import org.xml.sax.InputSource;

import com.alibaba.fastjson2.JSONObject;

/**
 * jwt token认证单元测试:登录响应解析(token提取\有效期识别\点路径\异常提示)、
 * token头值组装(静态token\缺省前缀\自定义前缀\配置缺失校验)、
 * token-secure-key自签名(结构\claims\签名可验证\缓存复用\算法可选)及xml属性绑定
 */
public class JwtTokenManagerTest {

	@Test
	public void parseTokenResponseDefaultKeyAndExpires() {
		JwtTokenManager.TokenEntry entry = JwtTokenManager.parseTokenResponse(
				"{\"access_token\":\"token-1\",\"expires_in\":3600}", null, 1800);
		assertEquals("token-1", entry.token);
		long expect = System.currentTimeMillis() + 3600 * 1000L;
		// 解析耗时容忍1秒误差
		assertTrue(Math.abs(entry.expireAtMillis - expect) < 1000, "expires_in应作为有效期");
	}

	@Test
	public void parseTokenResponseDotPathWithCustomPath() {
		// code/message包裹结构,token藏在data.token,有效期字段与token同级
		JwtTokenManager.TokenEntry entry = JwtTokenManager
				.parseTokenResponse("{\"code\":200,\"data\":{\"token\":\"token-2\",\"expires\":7200}}", "data.token",
						1800);
		assertEquals("token-2", entry.token);
		long expect = System.currentTimeMillis() + 7200 * 1000L;
		assertTrue(Math.abs(entry.expireAtMillis - expect) < 1000, "data同级expires应作为有效期");
	}

	@Test
	public void parseTokenResponseExpireFromStringAndAbnormal() {
		// 数字字符串有效期合法
		JwtTokenManager.TokenEntry entry = JwtTokenManager
				.parseTokenResponse("{\"access_token\":\"t\",\"expires_in\":\"600\"}", null, 1800);
		long expect = System.currentTimeMillis() + 600 * 1000L;
		assertTrue(Math.abs(entry.expireAtMillis - expect) < 1000, "数字字符串有效期应被识别");
		// 超过30天上限的异常值回落到缺省有效期
		entry = JwtTokenManager.parseTokenResponse("{\"access_token\":\"t\",\"expires_in\":999999999}", null, 1800);
		expect = System.currentTimeMillis() + 1800 * 1000L;
		assertTrue(Math.abs(entry.expireAtMillis - expect) < 1000, "异常超大有效期应回落缺省值");
	}

	@Test
	public void parseTokenResponseMissingTokenThrows() {
		assertThrows(IllegalArgumentException.class, () -> JwtTokenManager
				.parseTokenResponse("{\"code\":500,\"message\":\"login failed\"}", null, 1800));
		assertThrows(Exception.class,
				() -> JwtTokenManager.parseTokenResponse("{\"data\":{\"foo\":1}}", "data.token", 1800));
	}

	@Test
	public void resolveTokenValueWithStaticToken() throws Exception {
		// 静态token:缺省Bearer前缀
		HttpAuthConfig authConfig = new HttpAuthConfig().authType("jwt").authorization("abc-token");
		assertTrue(authConfig.isJwt());
		assertEquals("Bearer abc-token", JwtTokenManager.resolveTokenValue(null, authConfig));
		// 自定义前缀
		authConfig = new HttpAuthConfig().authType("JWT").tokenPrefix("Token").authorization("abc-token");
		assertEquals("Token abc-token", JwtTokenManager.resolveTokenValue(null, authConfig));
	}

	@Test
	public void resolveCustomTokenHeaderAndNonePrefix() throws Exception {
		// 自定义携带token的请求头名称:缺省Authorization
		HttpAuthConfig authConfig = new HttpAuthConfig().authType("jwt").authorization("abc-token");
		assertEquals("Authorization", JwtTokenManager.resolveTokenHeader(authConfig));
		// token-prefix=none:头值仅放token本身(适配自定义头+裸token的网关)
		authConfig = new HttpAuthConfig().authType("jwt").tokenPrefix("none").authorization("abc-token")
				.tokenHeader("Sag-Auth-Token");
		assertEquals("Sag-Auth-Token", JwtTokenManager.resolveTokenHeader(authConfig));
		assertEquals("abc-token", JwtTokenManager.resolveTokenValue(null, authConfig));
	}

	@Test
	public void resolveTokenValueMissingTokenSourceThrows() {
		// jwt模式token-url、token-secure-key、authorization均未配置,给出明确错误
		HttpAuthConfig authConfig = new HttpAuthConfig().authType("jwt").username("u");
		assertThrows(IllegalArgumentException.class, () -> JwtTokenManager.resolveTokenValue(null, authConfig));
	}

	@Test
	public void selfSignWithSecureKeyVerifiable() throws Exception {
		// 自签名模式:本地生成HS256 jwt,无需登录接口
		HttpAuthConfig authConfig = new HttpAuthConfig().authType("jwt").username("sqltoy").tokenExpire(600)
				.tokenSecureKey("my-secret");
		String token = JwtTokenManager.resolveTokenValue(null, authConfig).substring("Bearer ".length());
		// jwt三段式:header.payload.signature
		String[] parts = token.split("\\.");
		assertEquals(3, parts.length, "自签token应为三段式结构");
		// header
		String header = new String(Base64.getUrlDecoder().decode(parts[0]), StandardCharsets.UTF_8);
		assertEquals("{\"alg\":\"HS256\",\"typ\":\"JWT\"}", header);
		// payload claims:sub=username,exp-iat=有效期
		JSONObject claims = JSONObject
				.parseObject(new String(Base64.getUrlDecoder().decode(parts[1]), StandardCharsets.UTF_8));
		assertEquals("sqltoy", claims.getString("sub"));
		assertEquals(600L, claims.getLong("exp") - claims.getLong("iat"));
		// 签名可验证:以同一密钥重算HMAC-SHA256应与签名段一致(服务端验签等价)
		String expectSign = recomputeHmac("HmacSHA256", "my-secret", parts[0] + "." + parts[1]);
		assertEquals(expectSign, parts[2], "自签token签名应可通过同密钥验签");
		// 有效期内缓存复用:两次获取返回同一token
		assertEquals(token, JwtTokenManager.resolveTokenValue(null, authConfig).substring("Bearer ".length()),
				"有效期内应复用缓存的token");
	}

	private static String recomputeHmac(String macAlgorithm, String secret, String signingInput) throws Exception {
		Mac mac = Mac.getInstance(macAlgorithm);
		mac.init(new SecretKeySpec(secret.getBytes(StandardCharsets.UTF_8), macAlgorithm));
		return Base64.getUrlEncoder().withoutPadding()
				.encodeToString(mac.doFinal(signingInput.getBytes(StandardCharsets.UTF_8)));
	}

	@Test
	public void unsupportedSignAlgorithmThrows() {
		// 未支持的算法给出明确错误并列出支持清单
		HttpAuthConfig authConfig = new HttpAuthConfig().authType("jwt").tokenSecureKey("k").signAlgorithm("ES256");
		assertThrows(IllegalArgumentException.class, () -> JwtTokenManager.resolveTokenValue(null, authConfig));
	}

	@Test
	public void selfSignWithSelectableHsAlgorithm() throws Exception {
		// HS384:头部alg声明正确,签名可通过同算法独立重算验签
		HttpAuthConfig authConfig = new HttpAuthConfig().authType("jwt").username("sqltoy").tokenExpire(600)
				.tokenSecureKey("my-secret-384").signAlgorithm("hs384");
		String token = JwtTokenManager.resolveTokenValue(null, authConfig).substring("Bearer ".length());
		String[] parts = token.split("\\.");
		assertEquals(3, parts.length);
		assertEquals("{\"alg\":\"HS384\",\"typ\":\"JWT\"}",
				new String(Base64.getUrlDecoder().decode(parts[0]), StandardCharsets.UTF_8));
		assertEquals(recomputeHmac("HmacSHA384", "my-secret-384", parts[0] + "." + parts[1]), parts[2],
				"HS384签名应可通过同密钥独立重算验证");
	}

	@Test
	public void selfSignWithRs256VerifiedByPublicKey() throws Exception {
		// 运行期生成RSA密钥对:私钥(PEM格式)参与自签,公钥独立验签(等价服务端验签)
		KeyPairGenerator keyGen = KeyPairGenerator.getInstance("RSA");
		keyGen.initialize(2048);
		KeyPair keyPair = keyGen.generateKeyPair();
		String pem = "-----BEGIN PRIVATE KEY-----\n"
				.concat(Base64.getMimeEncoder().encodeToString(keyPair.getPrivate().getEncoded()))
				.concat("\n-----END PRIVATE KEY-----");
		HttpAuthConfig authConfig = new HttpAuthConfig().authType("jwt").username("sqltoy").tokenExpire(600)
				.tokenSecureKey(pem).signAlgorithm("RS256");
		String token = JwtTokenManager.resolveTokenValue(null, authConfig).substring("Bearer ".length());
		String[] parts = token.split("\\.");
		assertEquals("{\"alg\":\"RS256\",\"typ\":\"JWT\"}",
				new String(Base64.getUrlDecoder().decode(parts[0]), StandardCharsets.UTF_8));
		Signature verifier = Signature.getInstance("SHA256withRSA");
		verifier.initVerify(keyPair.getPublic());
		verifier.update((parts[0] + "." + parts[1]).getBytes(StandardCharsets.UTF_8));
		assertTrue(verifier.verify(Base64.getUrlDecoder().decode(parts[2])), "RS256签名应可通过公钥验签");
		// 裸base64密钥体(无PEM头尾)同样可解析
		assertTrue(JwtTokenManager.parsePkcs8PrivateKey(
				Base64.getEncoder().encodeToString(keyPair.getPrivate().getEncoded())) != null);
	}

	@Test
	public void xmlAttributesBindToCheckerConfig() throws Exception {
		// 验证xsd新增属性经XMLUtil.setAttributes(kebab-case转驼峰)正确绑定到检测器模型
		String xml = "<rest-checker url=\"http://a/b\" auth-type=\"jwt\" token-url=\"http://a/login\""
				+ " token-path=\"Sag-Auth-Token\" token-prefix=\"Token\" token-expire=\"600\" token-secure-key=\"sk-123\""
				+ " token-header=\"Sag-Auth-Token\" sign-algorithm=\"RS256\" authorization=\"static-token\"/>";
		Element elt = DocumentBuilderFactory.newInstance().newDocumentBuilder()
				.parse(new InputSource(new StringReader(xml))).getDocumentElement();
		CheckerConfigModel model = new CheckerConfigModel();
		XMLUtil.setAttributes(elt, model);
		assertEquals("http://a/b", model.getUrl());
		assertEquals("jwt", model.getAuthType());
		assertEquals("http://a/login", model.getTokenUrl());
		assertEquals("Sag-Auth-Token", model.getTokenPath());
		assertEquals("Token", model.getTokenPrefix());
		assertEquals(600, model.getTokenExpire());
		assertEquals("sk-123", model.getTokenSecureKey());
		assertEquals("Sag-Auth-Token", model.getTokenHeader());
		assertEquals("RS256", model.getSignAlgorithm());
		assertEquals("static-token", model.getAuthorization());
		HttpAuthConfig authConfig = new HttpAuthConfig().authType(model.getAuthType()).username(model.getUsername())
				.password(model.getPassword()).tokenUrl(model.getTokenUrl()).tokenPath(model.getTokenPath())
				.tokenPrefix(model.getTokenPrefix()).tokenHeader(model.getTokenHeader())
				.tokenExpire(model.getTokenExpire()).tokenSecureKey(model.getTokenSecureKey())
				.signAlgorithm(model.getSignAlgorithm()).authorization(model.getAuthorization());
		assertTrue(authConfig.isJwt());
		assertEquals("RS256", JwtTokenManager.resolveSignAlgorithm(authConfig));
	}

	@SuppressWarnings("unchecked")
	private static Map<String, JwtTokenManager.TokenEntry> tokenCache() throws Exception {
		Field field = JwtTokenManager.class.getDeclaredField("TOKEN_CACHE");
		field.setAccessible(true);
		return (Map<String, JwtTokenManager.TokenEntry>) field.get(null);
	}

	@Test
	public void expiredTokenEntriesArePurgedOnRefresh() throws Exception {
		Map<String, JwtTokenManager.TokenEntry> cache = tokenCache();
		cache.clear();
		long now = System.currentTimeMillis();
		cache.put("expired-key", new JwtTokenManager.TokenEntry("expired-token", now - 1000L));
		cache.put("live-key", new JwtTokenManager.TokenEntry("live-token", now + 3600000L));
		// 自签名模式下新key的写入应触发过期清理
		HttpAuthConfig authConfig = new HttpAuthConfig().authType("jwt").username("purge-user").tokenExpire(600)
				.tokenSecureKey("purge-secret");
		JwtTokenManager.resolveTokenValue(null, authConfig);
		assertFalse(cache.containsKey("expired-key"), "过期token条目应在下次写入时清理");
		assertTrue(cache.containsKey("live-key"), "未过期token条目应保留");
		cache.clear();
	}

	@Test
	public void tokenCacheIsBounded() throws Exception {
		Map<String, JwtTokenManager.TokenEntry> cache = tokenCache();
		cache.clear();
		long farFuture = System.currentTimeMillis() + 3600000L;
		for (int i = 0; i < 2200; i++) {
			cache.put("bulk-" + i, new JwtTokenManager.TokenEntry("bulk-token-" + i, farFuture));
		}
		HttpAuthConfig authConfig = new HttpAuthConfig().authType("jwt").username("bounded-user").tokenExpire(600)
				.tokenSecureKey("bounded-secret");
		JwtTokenManager.resolveTokenValue(null, authConfig);
		assertTrue(cache.size() <= 2000, "token缓存应有容量上限(2000),实际:" + cache.size());
		cache.clear();
	}

	@Test
	public void concurrentRefreshesStayBoundedAndValid() throws Exception {
		Map<String, JwtTokenManager.TokenEntry> cache = tokenCache();
		cache.clear();
		int threads = 8;
		int perThread = 300;
		CountDownLatch startGate = new CountDownLatch(1);
		CountDownLatch done = new CountDownLatch(threads);
		AtomicInteger failures = new AtomicInteger();
		for (int t = 0; t < threads; t++) {
			final int threadId = t;
			Thread thread = new Thread(() -> {
				try {
					startGate.await();
					for (int i = 0; i < perThread; i++) {
						HttpAuthConfig authConfig = new HttpAuthConfig().authType("jwt")
								.username("user-" + threadId + "-" + i).tokenExpire(600)
								.tokenSecureKey("secret-" + threadId);
						String value = JwtTokenManager.resolveTokenValue(null, authConfig);
						if (value == null || !value.startsWith("Bearer ")) {
							failures.incrementAndGet();
						}
					}
				} catch (Throwable e) {
					failures.incrementAndGet();
				} finally {
					done.countDown();
				}
			});
			thread.start();
		}
		startGate.countDown();
		done.await(120, TimeUnit.SECONDS);
		assertEquals(0, failures.get(), "并发刷新token不得出现异常/空token");
		assertTrue(cache.size() <= 2000, "并发刷新后token缓存仍须有界(2000),实际:" + cache.size());
		cache.clear();
	}

	@Test
	public void refreshAheadWindowReplacesCachedEntry() throws Exception {
		// update 2026-11-15 回归:提前刷新窗口内(未到期)的条目刷新后必须被新token覆盖。
		// putBounded为putIfAbsent语义,若不先移除窗口内旧条目,新token入不了缓存——
		// 窗口期内每次调用都重新获取token(原TOKEN_CACHE.put为覆盖语义)
		HttpAuthConfig authConfig = new HttpAuthConfig().authType("jwt").username("refresh-window-user")
				.tokenExpire(600).tokenSecureKey("my-secret");
		Field cacheField = JwtTokenManager.class.getDeclaredField("TOKEN_CACHE");
		cacheField.setAccessible(true);
		@SuppressWarnings("unchecked")
		java.util.concurrent.ConcurrentHashMap<String, JwtTokenManager.TokenEntry> cache = (java.util.concurrent.ConcurrentHashMap<String, JwtTokenManager.TokenEntry>) cacheField
				.get(null);
		try {
			// 静态缓存可能被同套件其它用例/后台线程留有残留:先清空保证基线
			cache.clear();
			// 首次调用建立缓存(不断言全局size:TOKEN_CACHE是JVM级共享缓存,可能被其它线程写入)
			String firstValue = JwtTokenManager.resolveTokenValue(null, authConfig);
			String firstToken = firstValue.substring("Bearer ".length());
			String key = null;
			for (Map.Entry<String, JwtTokenManager.TokenEntry> entry : cache.entrySet()) {
				if (firstToken.equals(entry.getValue().token)) {
					key = entry.getKey();
					break;
				}
			}
			assertNotNull(key, "首次调用应建立本token的缓存条目");
			JwtTokenManager.TokenEntry old = cache.get(key);
			// 把有效期移入提前刷新窗口:now+30s(窗口60s,尚未到期,purge不会清理)
			cache.put(key, new JwtTokenManager.TokenEntry(old.token, System.currentTimeMillis() + 30_000L));
			// 触发刷新:新token应覆盖窗口内旧条目
			JwtTokenManager.resolveTokenValue(null, authConfig);
			JwtTokenManager.TokenEntry after = cache.get(key);
			assertTrue(after.expireAtMillis > System.currentTimeMillis() + 300_000L,
					"刷新后缓存必须被新条目覆盖(tokenExpire=600s),实际剩余:"
							+ (after.expireAtMillis - System.currentTimeMillis()) / 1000 + "s");
			// 二次调用命中新条目缓存,不再重新获取(token不变)
			assertEquals("Bearer " + after.token, JwtTokenManager.resolveTokenValue(null, authConfig),
					"刷新后应复用新缓存token");
		} finally {
			cache.clear();
		}
	}
}
