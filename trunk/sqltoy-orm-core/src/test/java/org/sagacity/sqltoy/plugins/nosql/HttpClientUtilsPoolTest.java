package org.sagacity.sqltoy.plugins.nosql;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;

import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.net.InetSocketAddress;
import java.nio.charset.StandardCharsets;
import java.util.HashMap;
import java.util.Locale;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicInteger;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.sagacity.sqltoy.SqlToyContext;
import org.sagacity.sqltoy.config.model.ElasticEndpoint;
import org.sagacity.sqltoy.config.model.NoSqlConfigModel;

import com.sun.net.httpserver.HttpExchange;
import com.sun.net.httpserver.HttpServer;

/**
 * elastic查询(统一rest5客户端)的端到端验证:连接复用、不发送Connection: close、
 * 池中连接被对端关闭后重试一次。以JDK内置HttpServer模拟elastic节点,零第三方依赖
 */
public class HttpClientUtilsPoolTest {

	private HttpServer server;

	private final Set<Integer> clientPorts = ConcurrentHashMap.newKeySet();

	/**
	 * 请求头Connection的取值(小写,无该头时记录null的替代串)
	 */
	private final Set<String> connectionHeaders = ConcurrentHashMap.newKeySet();

	private final AtomicInteger hits = new AtomicInteger();

	/**
	 * 1:首个请求不返回响应直接断开
	 */
	private final AtomicInteger closeFirstHit = new AtomicInteger();

	@BeforeEach
	public void startServer() throws IOException {
		server = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
		server.createContext("/", this::handle);
		server.start();
	}

	@AfterEach
	public void stopServer() {
		if (server != null) {
			server.stop(0);
		}
	}

	private void handle(HttpExchange exchange) throws IOException {
		// 消费请求体,保证连接可复用
		drain(exchange.getRequestBody());
		hits.incrementAndGet();
		clientPorts.add(exchange.getRemoteAddress().getPort());
		String connection = exchange.getRequestHeaders().getFirst("Connection");
		connectionHeaders.add(connection == null ? "" : connection.toLowerCase(Locale.ROOT));
		// 模拟池中keep-alive连接已被对端关闭:不返回响应直接断开
		if (closeFirstHit.compareAndSet(1, 2)) {
			exchange.close();
			return;
		}
		byte[] body = "{\"hits\":{\"total\":0}}".getBytes(StandardCharsets.UTF_8);
		exchange.getResponseHeaders().set("Content-Type", "application/json;charset=UTF-8");
		exchange.sendResponseHeaders(200, body.length);
		try (OutputStream out = exchange.getResponseBody()) {
			out.write(body);
		}
	}

	private void drain(InputStream in) throws IOException {
		byte[] buf = new byte[1024];
		while (in.read(buf) != -1) {
			// 读取请求体
		}
		in.close();
	}

	private ElasticEndpoint endpoint() {
		return new ElasticEndpoint("http://127.0.0.1:" + server.getAddress().getPort());
	}

	@Test
	public void pooledClientReusesConnection() throws Exception {
		ElasticEndpoint endpoint = endpoint();
		SqlToyContext context = new SqlToyContext();
		NoSqlConfigModel config = new NoSqlConfigModel();
		for (int i = 0; i < 3; i++) {
			assertNotNull(HttpClientUtils.doPost(context, config, endpoint, new HashMap<String, Object>()));
		}
		assertEquals(3, hits.get());
		assertEquals(1, clientPorts.size(), "连接池应复用同一条keep-alive连接,实际客户端端口:" + clientPorts);
		assertFalse(connectionHeaders.contains("close"), "rest5客户端不应再发送Connection: close:" + connectionHeaders);
		endpoint.close();
	}

	@Test
	public void staleConnectionRetriedOnce() throws Exception {
		closeFirstHit.set(1);
		ElasticEndpoint endpoint = endpoint();
		SqlToyContext context = new SqlToyContext();
		NoSqlConfigModel config = new NoSqlConfigModel();
		assertNotNull(HttpClientUtils.doPost(context, config, endpoint, new HashMap<String, Object>()));
		assertEquals(2, hits.get(), "首个请求连接被对端断开后应重试一次并成功");
		endpoint.close();
	}
}
