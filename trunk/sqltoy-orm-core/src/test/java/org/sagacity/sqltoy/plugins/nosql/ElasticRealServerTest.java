package org.sagacity.sqltoy.plugins.nosql;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.net.HttpURLConnection;
import java.net.URL;
import java.nio.charset.StandardCharsets;
import java.util.Base64;
import java.util.Properties;

import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.Assumptions;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.sagacity.sqltoy.SqlToyContext;
import org.sagacity.sqltoy.config.model.ElasticEndpoint;
import org.sagacity.sqltoy.config.model.NoSqlConfigModel;
import org.sagacity.sqltoy.exception.DataAccessException;
import org.sagacity.sqltoy.plugins.json.JsonArray;
import org.sagacity.sqltoy.plugins.json.JsonObject;
import org.sagacity.sqltoy.plugins.json.JsonProviders;
import org.sagacity.sqltoy.support.RealDbProbeSupport;

/**
 * elasticsearch真实服务验证(全量rest客户端收口后的连接复用/超时覆盖/base path/游标分页/错误响应)。
 * 容器由docker提供,探针取target/elastic-probe.properties(url/username/password),缺省http://127.0.0.1:9200;
 * 服务不可达时整类跳过,不把"无容器"误报为失败。
 * (jre8同步适配:管理端请求由java.net.http改为HttpURLConnection,本侧rest客户端为
 * httpclient4+elasticsearch-rest-client形态)
 */
public class ElasticRealServerTest {

	private static final String DEFAULT_URL = "http://127.0.0.1:9200";

	private static final String INDEX = "sqltoy_http_probe";

	private static String baseUrl;

	private static String username;

	private static String password;

	private static String authHeader;

	private static boolean serverUp;

	private static SqlToyContext context;

	/**
	 * 管理端响应(状态码+响应体)
	 */
	private static class AdminResponse {
		final int statusCode;
		final String body;

		AdminResponse(int statusCode, String body) {
			this.statusCode = statusCode;
			this.body = body;
		}
	}

	@BeforeAll
	public static void init() throws Exception {
		Properties probe = RealDbProbeSupport.load("elastic");
		baseUrl = (probe == null || probe.getProperty("url") == null) ? DEFAULT_URL : probe.getProperty("url").trim();
		username = probe == null ? null : probe.getProperty("username");
		password = probe == null ? null : probe.getProperty("password");
		Assumptions.assumeTrue(RealDbProbeSupport.isReachable(baseUrl),
				"elasticsearch not reachable, skipped: " + baseUrl);
		if (username != null && password != null) {
			authHeader = "Basic "
					+ Base64.getEncoder().encodeToString((username + ":" + password).getBytes(StandardCharsets.UTF_8));
		}
		serverUp = true;
		// TCP可达不代表集群已就绪:等yellow,最多30秒
		boolean ready = false;
		for (int i = 0; i < 60 && !ready; i++) {
			ready = send("GET", "/_cluster/health?wait_for_status=yellow&timeout=1s", null, null).statusCode == 200;
			if (!ready) {
				Thread.sleep(500);
			}
		}
		Assumptions.assumeTrue(ready, "elasticsearch cluster not ready, skipped: " + baseUrl);
		context = new SqlToyContext();
		// 重建探针索引
		send("DELETE", "/" + INDEX, null, null);
		int createCode = send("PUT", "/" + INDEX,
				"{\"settings\":{\"number_of_shards\":1,\"number_of_replicas\":0},"
						+ "\"mappings\":{\"properties\":{\"name\":{\"type\":\"keyword\"},"
						+ "\"dept\":{\"type\":\"keyword\"},\"age\":{\"type\":\"integer\"}}}}",
				"application/json").statusCode;
		Assumptions.assumeTrue(createCode == 200, "create index failed, status=" + createCode);
		String bulk = "{\"index\":{\"_index\":\"" + INDEX + "\",\"_id\":\"1\"}}\n"
				+ "{\"name\":\"张三\",\"dept\":\"研发\",\"age\":28}\n" + "{\"index\":{\"_index\":\"" + INDEX
				+ "\",\"_id\":\"2\"}}\n" + "{\"name\":\"李四\",\"dept\":\"销售\",\"age\":35}\n"
				+ "{\"index\":{\"_index\":\"" + INDEX + "\",\"_id\":\"3\"}}\n"
				+ "{\"name\":\"王五\",\"dept\":\"研发\",\"age\":42}\n";
		AdminResponse bulkResp = send("POST", "/_bulk", bulk, "application/x-ndjson");
		assertEquals(200, bulkResp.statusCode, "bulk失败:" + bulkResp.body);
		assertFalse(bulkResp.body.contains("\"errors\":true"), "bulk存在失败项:" + bulkResp.body);
		assertEquals(200, send("POST", "/" + INDEX + "/_refresh", null, null).statusCode);
	}

	@AfterAll
	public static void clean() throws Exception {
		if (serverUp) {
			send("DELETE", "/" + INDEX, null, null);
			serverUp = false;
		}
		// 验证destroy能释放自定义url端点持有的rest客户端
		if (context != null) {
			context.destroy();
			context = null;
		}
	}

	private static AdminResponse send(String method, String path, String body, String contentType)
			throws IOException {
		HttpURLConnection conn = (HttpURLConnection) new URL(baseUrl + path).openConnection();
		conn.setRequestMethod(method);
		conn.setConnectTimeout(3000);
		conn.setReadTimeout(10000);
		if (authHeader != null) {
			conn.setRequestProperty("Authorization", authHeader);
		}
		if (body != null) {
			conn.setDoOutput(true);
			conn.setRequestProperty("Content-Type", contentType == null ? "application/json" : contentType);
			byte[] data = body.getBytes(StandardCharsets.UTF_8);
			conn.setFixedLengthStreamingMode(data.length);
			OutputStream out = conn.getOutputStream();
			try {
				out.write(data);
			} finally {
				out.close();
			}
		}
		int statusCode = conn.getResponseCode();
		InputStream in = (statusCode >= 400) ? conn.getErrorStream() : conn.getInputStream();
		String responseBody = "";
		if (in != null) {
			try {
				ByteArrayOutputStream bos = new ByteArrayOutputStream();
				byte[] buf = new byte[4096];
				int len;
				while ((len = in.read(buf)) != -1) {
					bos.write(buf, 0, len);
				}
				responseBody = new String(bos.toByteArray(), StandardCharsets.UTF_8);
			} finally {
				in.close();
			}
		}
		conn.disconnect();
		return new AdminResponse(statusCode, responseBody);
	}

	private static ElasticEndpoint endpoint() {
		ElasticEndpoint endpoint = new ElasticEndpoint(baseUrl);
		if (username != null && password != null) {
			endpoint.setUsername(username);
			endpoint.setPassword(password);
		}
		return endpoint;
	}

	private static ElasticEndpoint multiEndpoint() {
		return multiEndpoint(null);
	}

	private static ElasticEndpoint multiEndpoint(Integer socketTimeout) {
		ElasticEndpoint endpoint = new ElasticEndpoint(baseUrl + "," + baseUrl);
		if (username != null && password != null) {
			endpoint.setUsername(username);
			endpoint.setPassword(password);
		}
		if (socketTimeout != null) {
			endpoint.setSocketTimeout(socketTimeout);
		}
		endpoint.initRestClient();
		assertNotNull(endpoint.getRestClient(), "多地址应构建rest客户端");
		return endpoint;
	}

	private static NoSqlConfigModel eqlConfig() {
		NoSqlConfigModel config = new NoSqlConfigModel();
		config.setIndex(INDEX);
		return config;
	}

	private static JsonObject matchAll() {
		return JsonProviders.get().object("{\"query\":{\"match_all\":{}},\"size\":10}");
	}

	/**
	 * 约300ms的慢查询(painless忙等):用于稳定验证"响应超时"是否按配置生效
	 */
	private static JsonObject slowQuery() {
		return slowQuery(300);
	}

	/**
	 * 指定忙等时长的慢查询;让查询明显超过超时阈值,稳定验证按配置超时
	 */
	private static JsonObject slowQuery(int millis) {
		return JsonProviders.get().object("{\"query\":{\"match_all\":{}},\"script_fields\":"
				+ "{\"slow\":{\"script\":{\"source\":\"long t=System.currentTimeMillis(); "
				+ "while(System.currentTimeMillis()-t<" + millis + "){} return 1;\"}}}}");
	}

	private static int totalHits(JsonObject result) {
		Object total = result.getJSONObject("hits").get("total");
		if (total instanceof java.util.Map) {
			return ((Number) ((java.util.Map<?, ?>) total).get("value")).intValue();
		}
		return ((Number) total).intValue();
	}

	private static long totalOpened() throws Exception {
		AdminResponse resp = send("GET", "/_nodes/stats/http?filter_path=nodes.*.http.total_opened", null, null);
		JsonObject nodes = JsonProviders.get().object(resp.body).getJSONObject("nodes");
		long total = 0;
		for (Object node : nodes.unwrap().values()) {
			total += JsonObject.of(node).getJSONObject("http").getLongValue("total_opened");
		}
		return total;
	}

	private static boolean isTimeout(Throwable e) {
		Throwable current = e;
		while (current != null) {
			if (current instanceof java.net.SocketTimeoutException
					|| current.getClass().getName().toLowerCase().contains("timeout")) {
				return true;
			}
			if (current.getMessage() != null && current.getMessage().toLowerCase().contains("timed out")) {
				return true;
			}
			current = current.getCause();
		}
		return false;
	}

	@Test
	public void singleAddressEqlQuery() throws Exception {
		ElasticEndpoint endpoint = endpoint();
		try {
			JsonObject result = HttpClientUtils.doPost(context, eqlConfig(), endpoint, matchAll());
			assertEquals(3, totalHits(result));
			assertEquals(3, result.getJSONObject("hits").getJSONArray("hits").size());
		} finally {
			endpoint.close();
		}
	}

	@Test
	public void singleAddressSqlQueryWrappedAsQuery() throws Exception {
		ElasticEndpoint endpoint = endpoint();
		NoSqlConfigModel config = eqlConfig();
		config.setSqlMode(true);
		try {
			JsonObject result = HttpClientUtils.doPost(context, config, endpoint,
					"select name, age from " + INDEX + " order by age");
			JsonArray rows = result.getJSONArray("rows");
			assertNotNull(rows, "原生sql应返回rows:" + result.toJSONString());
			assertEquals(3, rows.size());
			assertEquals("张三", rows.getJSONArray(0).getString(0));
			assertEquals(28, ((Number) rows.getJSONArray(0).get(1)).intValue());
		} finally {
			endpoint.close();
		}
	}

	@Test
	public void singleAddressNativeSqlCursorPaging() throws Exception {
		ElasticEndpoint endpoint = endpoint();
		NoSqlConfigModel config = eqlConfig();
		config.setSqlMode(true);
		try {
			JsonObject first = new JsonObject();
			first.put("query", "select name, age from " + INDEX + " order by age");
			first.put("fetch_size", 1);
			JsonObject page1 = HttpClientUtils.doPostBody(context, config, endpoint, first);
			assertEquals(1, page1.getJSONArray("rows").size());
			String cursor = page1.getString("cursor");
			assertNotNull(cursor, "fetch_size=1时应返回cursor:" + page1.toJSONString());
			JsonObject next = new JsonObject();
			next.put("cursor", cursor);
			next.put("fetch_size", 1);
			JsonObject page2 = HttpClientUtils.doPostBody(context, config, endpoint, next);
			assertEquals(1, page2.getJSONArray("rows").size());
			assertEquals("李四", page2.getJSONArray("rows").getJSONArray(0).getString(0));
		} finally {
			endpoint.close();
		}
	}

	@Test
	public void singleAddressPoolReusesConnection() throws Exception {
		// 预热admin统计自身的keep-alive连接
		totalOpened();
		long before = totalOpened();
		ElasticEndpoint endpoint = endpoint();
		try {
			for (int i = 0; i < 5; i++) {
				assertEquals(3, totalHits(HttpClientUtils.doPost(context, eqlConfig(), endpoint, matchAll())));
			}
		} finally {
			endpoint.close();
		}
		long after = totalOpened();
		assertEquals(1, after - before, "5次查询应只新增1条连接(rest连接池复用),before=" + before + ",after=" + after);
	}

	@Test
	public void endpointTimeoutIsOverriddenByStatement() throws Exception {
		ElasticEndpoint endpoint = endpoint();
		endpoint.setSocketTimeout(100);
		NoSqlConfigModel config = eqlConfig();
		try {
			Exception e = assertThrows(Exception.class,
					() -> HttpClientUtils.doPost(context, config, endpoint, slowQuery(2500)));
			assertTrue(isTimeout(e), "endpoint socketTimeout=100ms应超时,实际:" + e);
			// 语句级覆盖为60秒后应成功
			config.setSocketTimeout(60000);
			assertEquals(3, totalHits(HttpClientUtils.doPost(context, config, endpoint, slowQuery(2500))));
		} finally {
			endpoint.close();
		}
	}

	@Test
	public void configuredUrlWithSqlSuffix() throws Exception {
		// url本身指向/_sql:pathPrefix应去掉该尾段,避免/_sql/_sql
		ElasticEndpoint endpoint = new ElasticEndpoint(baseUrl + "/_sql");
		NoSqlConfigModel config = eqlConfig();
		config.setSqlMode(true);
		try {
			JsonObject result = HttpClientUtils.doPost(context, config, endpoint,
					"select name from " + INDEX + " order by age");
			assertEquals(3, result.getJSONArray("rows").size());
		} finally {
			endpoint.close();
		}
	}

	@Test
	public void configuredUrlWithSearchSuffix() throws Exception {
		// url本身指向/{index}/_search:pathPrefix应为/{index},请求路径/_search
		ElasticEndpoint endpoint = new ElasticEndpoint(baseUrl + "/" + INDEX + "/_search");
		try {
			JsonObject result = HttpClientUtils.doPost(context, new NoSqlConfigModel(), endpoint, matchAll());
			assertEquals(3, totalHits(result));
		} finally {
			endpoint.close();
		}
	}

	@Test
	public void rest5QueryWorks() throws Exception {
		ElasticEndpoint endpoint = multiEndpoint();
		try {
			JsonObject result = HttpClientUtils.doPost(context, eqlConfig(), endpoint, matchAll());
			assertEquals(3, totalHits(result));
		} finally {
			endpoint.close();
		}
	}

	@Test
	public void rest5StatementTimeoutApplied() throws Exception {
		ElasticEndpoint endpoint = multiEndpoint();
		NoSqlConfigModel config = eqlConfig();
		config.setSocketTimeout(100);
		try {
			Exception e = assertThrows(Exception.class,
					() -> HttpClientUtils.doPost(context, config, endpoint, slowQuery(2500)));
			assertTrue(isTimeout(e), "语句socketTimeout=100ms应使用独立rest客户端并超时,实际:" + e);
			config.setSocketTimeout(60000);
			assertEquals(3, totalHits(HttpClientUtils.doPost(context, config, endpoint, slowQuery(2500))));
		} finally {
			endpoint.close();
		}
	}

	@Test
	public void rest5EndpointTimeoutApplied() throws Exception {
		ElasticEndpoint endpoint = multiEndpoint(100);
		try {
			Exception e = assertThrows(Exception.class,
					() -> HttpClientUtils.doPost(context, eqlConfig(), endpoint, slowQuery(2500)));
			assertTrue(isTimeout(e), "rest客户端按endpoint socketTimeout=100ms应超时,实际:" + e);
		} finally {
			endpoint.close();
		}
	}

	@Test
	public void rest5CursorPaging() throws Exception {
		ElasticEndpoint endpoint = multiEndpoint();
		NoSqlConfigModel config = eqlConfig();
		config.setSqlMode(true);
		try {
			JsonObject first = new JsonObject();
			first.put("query", "select name, age from " + INDEX + " order by age");
			first.put("fetch_size", 1);
			JsonObject page1 = HttpClientUtils.doPostBody(context, config, endpoint, first);
			assertEquals(1, page1.getJSONArray("rows").size());
			assertNotNull(page1.getString("cursor"));
		} finally {
			endpoint.close();
		}
	}

	@Test
	public void errorResponseRaisesDataAccessException() {
		ElasticEndpoint endpoint = endpoint();
		NoSqlConfigModel config = new NoSqlConfigModel();
		config.setIndex("sqltoy_no_such_index_xyz");
		try {
			DataAccessException e = assertThrows(DataAccessException.class,
					() -> HttpClientUtils.doPost(context, config, endpoint, matchAll()));
			assertTrue(e.getMessage().contains("sqltoy_no_such_index_xyz"), "错误信息应含索引名:" + e.getMessage());
		} finally {
			endpoint.close();
		}
	}

	@Test
	public void customUrlEndpointIsReusedAndWorks() throws Exception {
		ElasticEndpoint first = context.getElasticEndpoint(baseUrl);
		ElasticEndpoint second = context.getElasticEndpoint(baseUrl.toUpperCase(java.util.Locale.ROOT));
		assertEquals(first, second, "同一url应复用端点");
		assertEquals(3, totalHits(HttpClientUtils.doPost(context, eqlConfig(), first, matchAll())));
	}
}
