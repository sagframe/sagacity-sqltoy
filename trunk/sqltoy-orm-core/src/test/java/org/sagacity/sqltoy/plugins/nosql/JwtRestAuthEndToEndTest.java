package org.sagacity.sqltoy.plugins.nosql;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;

import java.io.IOException;
import java.io.OutputStream;
import java.net.InetSocketAddress;
import java.net.URLDecoder;
import java.nio.charset.StandardCharsets;
import java.sql.Timestamp;
import java.util.Base64;
import java.util.List;
import java.util.concurrent.atomic.AtomicInteger;

import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.sagacity.sqltoy.translate.TranslateFactory;
import org.sagacity.sqltoy.translate.model.CacheCheckResult;
import org.sagacity.sqltoy.translate.model.CheckerConfigModel;

import com.sun.net.httpserver.HttpExchange;
import com.sun.net.httpserver.HttpServer;

/**
 * jwt rest认证端到端测试:以JDK内置HttpServer模拟认证服务端,零第三方依赖验证完整链路——
 * 1、token-url登录获取token并缓存,请求携带Authorization头,TranslateFactory.doCheck解析检测结果;
 * 2、token过期(401)自动失效重登并重试成功;
 * 3、token-secure-key自签名+自定义头(Sag-Auth-Token)+无前缀,服务端以共享密钥真实验签
 */
public class JwtRestAuthEndToEndTest {

	private HttpServer server;

	/**
	 * 登录接口命中次数(用于验证token缓存与401重登)
	 */
	private AtomicInteger loginHits = new AtomicInteger();

	@BeforeEach
	public void startServer() throws Exception {
		// port=0由系统自动分配,每个用例独立server天然隔离token缓存
		server = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
		server.setExecutor(null);
		server.start();
	}

	@AfterEach
	public void stopServer() {
		if (server != null) {
			server.stop(0);
		}
	}

	private String url(String path) {
		return "http://127.0.0.1:" + server.getAddress().getPort() + path;
	}

	private static void respond(HttpExchange exchange, int status, String body) throws IOException {
		byte[] data = body.getBytes(StandardCharsets.UTF_8);
		exchange.getResponseHeaders().set("Content-Type", "application/json;charset=UTF-8");
		exchange.sendResponseHeaders(status, data.length);
		try (OutputStream out = exchange.getResponseBody()) {
			out.write(data);
		}
		exchange.close();
	}

	private static String formBody(HttpExchange exchange) throws IOException {
		return URLDecoder.decode(new String(exchange.getRequestBody().readAllBytes(), StandardCharsets.UTF_8), "UTF-8");
	}

	@Test
	public void tokenUrlFetchCacheAndDoCheck() throws Exception {
		// 登录端点:校验表单账密,签发token
		server.createContext("/login", exchange -> {
			loginHits.incrementAndGet();
			String form = formBody(exchange);
			if (!form.contains("username=admin") || !form.contains("password=pwd")) {
				respond(exchange, 401, "{\"message\":\"bad credentials\"}");
				return;
			}
			respond(exchange, 200, "{\"access_token\":\"e2e-token\",\"expires_in\":600}");
		});
		// 检测端点:校验Bearer头,返回CacheCheckResult数组
		server.createContext("/check", exchange -> {
			String authorization = exchange.getRequestHeaders().getFirst("Authorization");
			if (!"Bearer e2e-token".equals(authorization)) {
				respond(exchange, 401, "{\"message\":\"unauthorized\"}");
				return;
			}
			respond(exchange, 200, "[{\"cacheName\":\"dictCache\",\"cacheType\":\"t1\"}]");
		});
		CheckerConfigModel checker = new CheckerConfigModel();
		checker.setUrl(url("/check")).setAuthType("jwt").setTokenUrl(url("/login")).setUsername("admin")
				.setPassword("pwd").setIncrement(false);
		// 走TranslateFactory.doCheck完整链路:登录获取token->携带Authorization头->解析检测结果
		List<CacheCheckResult> result = TranslateFactory.doCheck(null, checker,
				new Timestamp(System.currentTimeMillis() - 60000));
		assertNotNull(result, "doCheck应返回检测结果");
		assertEquals(1, result.size());
		assertEquals("dictCache", result.get(0).getCacheName());
		// 第二次检测:token缓存复用,登录接口不再被调用
		TranslateFactory.doCheck(null, checker, new Timestamp(System.currentTimeMillis() - 60000));
		assertEquals(1, loginHits.get(), "token有效期内应复用缓存,登录接口仅命中一次");
	}

	@Test
	public void unauthorizedRefetchTokenAndRetry() throws Exception {
		// 登录端点:首次签发即将过期的stale-token,重登后签发fresh-token
		server.createContext("/login", exchange -> {
			int hits = loginHits.incrementAndGet();
			respond(exchange, 200, hits == 1 ? "{\"access_token\":\"stale-token\"}"
					: "{\"access_token\":\"fresh-token\"}");
		});
		// 检测端点:仅接受fresh-token,其余返回401
		server.createContext("/check", exchange -> {
			String authorization = exchange.getRequestHeaders().getFirst("Authorization");
			if (!"Bearer fresh-token".equals(authorization)) {
				respond(exchange, 401, "{\"message\":\"token expired\"}");
				return;
			}
			respond(exchange, 200, "[{\"cacheName\":\"dictCache\",\"cacheType\":\"t2\"}]");
		});
		HttpAuthConfig authConfig = new HttpAuthConfig().authType("jwt").username("admin").password("pwd")
				.tokenUrl(url("/login"));
		String body = HttpClientUtils.doPost(null, url("/check"), authConfig, new String[] { "lastUpdateTime" },
				new String[] { "2026-10-02 00:00:00.000" });
		// 首次携带stale-token被401拒绝->强制失效->重登fresh-token->重试成功
		assertEquals("[{\"cacheName\":\"dictCache\",\"cacheType\":\"t2\"}]", body);
		assertEquals(2, loginHits.get(), "401应触发token失效重登(登录接口命中两次)");
	}

	@Test
	public void selfSignCustomHeaderVerifiedByServer() throws Exception {
		// 检测端点:以共享密钥对Sag-Auth-Token头中的自签jwt真实验签(等价服务端行为)
		server.createContext("/check", exchange -> {
			String token = exchange.getRequestHeaders().getFirst("Sag-Auth-Token");
			String[] parts = (token == null) ? new String[0] : token.split("\\.");
			boolean valid = parts.length == 3;
			if (valid) {
				try {
					Mac mac = Mac.getInstance("HmacSHA256");
					mac.init(new SecretKeySpec("e2e-secret".getBytes(StandardCharsets.UTF_8), "HmacSHA256"));
					String expectSign = Base64.getUrlEncoder().withoutPadding().encodeToString(
							mac.doFinal((parts[0] + "." + parts[1]).getBytes(StandardCharsets.UTF_8)));
					valid = expectSign.equals(parts[2])
							&& new String(Base64.getUrlDecoder().decode(parts[1]), StandardCharsets.UTF_8)
									.contains("\"sub\":\"sqltoy\"");
				} catch (Exception ignore) {
					valid = false;
				}
			}
			if (!valid) {
				respond(exchange, 401, "{\"message\":\"sign invalid\"}");
				return;
			}
			respond(exchange, 200, "[{\"cacheName\":\"dictCache\",\"cacheType\":\"t3\"}]");
		});
		// 自签名+自定义头+无前缀:服务端收到的是裸jwt
		HttpAuthConfig authConfig = new HttpAuthConfig().authType("jwt").username("sqltoy").tokenPrefix("none")
				.tokenSecureKey("e2e-secret").tokenHeader("Sag-Auth-Token");
		String body = HttpClientUtils.doPost(null, url("/check"), authConfig, new String[] { "cacheType" },
				new String[] { "t3" });
		assertEquals("[{\"cacheName\":\"dictCache\",\"cacheType\":\"t3\"}]", body);
	}
}
