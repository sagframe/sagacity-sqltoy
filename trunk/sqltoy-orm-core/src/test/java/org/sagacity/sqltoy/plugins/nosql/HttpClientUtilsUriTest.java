package org.sagacity.sqltoy.plugins.nosql;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.nio.charset.Charset;
import java.util.HashMap;
import java.util.Map;

import org.apache.http.ConnectionClosedException;
import org.apache.http.NoHttpResponseException;
import org.junit.jupiter.api.Test;
import org.sagacity.sqltoy.config.model.ElasticEndpoint;
import org.sagacity.sqltoy.config.model.NoSqlConfigModel;

/**
 * rest请求路径/base path解析、超时与字符集优先级、入参契约的回归。
 * 全量收口后单/多地址统一走rest客户端,此处只验证相对路径与pathPrefix的拆分规则
 * (jre8同步适配:stale判定的异常类型为httpclient4包名)
 */
public class HttpClientUtilsUriTest {

	private static NoSqlConfigModel sqlMode() {
		NoSqlConfigModel config = new NoSqlConfigModel();
		config.setSqlMode(true);
		return config;
	}

	private static NoSqlConfigModel jsonMode(String index, String type) {
		NoSqlConfigModel config = new NoSqlConfigModel();
		config.setIndex(index);
		config.setType(type);
		return config;
	}

	@Test
	public void sqlRequestPath() {
		assertEquals("/_sql", HttpClientUtils.buildRequestPath(new ElasticEndpoint("http://127.0.0.1:9200"), sqlMode()));
	}

	@Test
	public void sqlRequestPathCustomSqlPath() {
		// 插件路径场景:按配置的sqlPath组织(构造器会去掉前导"/")
		assertEquals("/_nlpcn/sql",
				HttpClientUtils.buildRequestPath(new ElasticEndpoint("http://127.0.0.1:9200", "/_nlpcn/sql"), sqlMode()));
	}

	@Test
	public void jsonRequestPathWithIndexAndType() {
		assertEquals("/idx/doc/_search",
				HttpClientUtils.buildRequestPath(new ElasticEndpoint("http://127.0.0.1:9200"), jsonMode("idx", "doc")));
	}

	@Test
	public void jsonRequestPathWithoutIndex() {
		assertEquals("/_search",
				HttpClientUtils.buildRequestPath(new ElasticEndpoint("http://127.0.0.1:9200"), jsonMode(null, null)));
	}

	@Test
	public void requestPathIgnoresConfiguredUrlPath() {
		// base path由pathPrefix承载,请求路径与配置url无关
		assertEquals("/_sql",
				HttpClientUtils.buildRequestPath(new ElasticEndpoint("http://127.0.0.1:9200/es"), sqlMode()));
	}

	@Test
	public void pathPrefixFromUrl() {
		assertEquals("", new ElasticEndpoint("http://127.0.0.1:9200").getPathPrefix());
		assertEquals("", new ElasticEndpoint("http://127.0.0.1:9200/").getPathPrefix());
		assertEquals("/es", new ElasticEndpoint("http://127.0.0.1:9200/es").getPathPrefix());
		assertEquals("/es", new ElasticEndpoint("http://127.0.0.1:9200/es/").getPathPrefix());
	}

	@Test
	public void pathPrefixStripsEndpointSuffix() {
		// url本身已指向sql/搜索端点时去掉尾段,避免与请求路径重复拼接
		assertEquals("", new ElasticEndpoint("http://127.0.0.1:9200/_sql").getPathPrefix());
		assertEquals("/es", new ElasticEndpoint("http://127.0.0.1:9200/es/_sql").getPathPrefix());
		assertEquals("/idx", new ElasticEndpoint("http://127.0.0.1:9200/idx/_search").getPathPrefix());
		assertEquals("", new ElasticEndpoint("http://127.0.0.1:9200/_xpack/sql", "_xpack/sql").getPathPrefix());
	}

	@Test
	public void pathPrefixUsesFirstNode() {
		// 多地址取首个节点的base path(全角分隔符与空白由getUrlList归一化)
		assertEquals("/es", new ElasticEndpoint("http://a:9200/es ；,http://b:9200/es").getPathPrefix());
	}

	@Test
	public void urlListNormalizesSeparatorsAndBlanks() {
		ElasticEndpoint endpoint = new ElasticEndpoint(" http://a:9200 ；,http://b:9200, ");
		assertArrayEquals(new String[] { "http://a:9200", "http://b:9200" }, endpoint.getUrlList());
		// 未配置地址时返回空数组,由checkEndpoint给出明确提示
		assertArrayEquals(new String[0], new ElasticEndpoint((String) null).getUrlList());
	}

	@Test
	public void charsetPrefersStatementThenEndpoint() {
		ElasticEndpoint endpoint = new ElasticEndpoint("http://127.0.0.1:9200");
		NoSqlConfigModel statement = jsonMode(null, null);
		// 语句未配置:取endpoint(默认UTF-8)
		assertEquals(Charset.forName("UTF-8"), HttpClientUtils.resolveCharset(statement, endpoint));
		// endpoint配置后作为默认值生效
		endpoint.setCharset("ISO-8859-1");
		assertEquals(Charset.forName("ISO-8859-1"), HttpClientUtils.resolveCharset(statement, endpoint));
		// 语句配置覆盖endpoint
		statement.setCharset("US-ASCII");
		assertEquals(Charset.forName("US-ASCII"), HttpClientUtils.resolveCharset(statement, endpoint));
		// 语句配置为空值时仍回退endpoint
		statement.setCharset(" ");
		assertEquals(Charset.forName("ISO-8859-1"), HttpClientUtils.resolveCharset(statement, endpoint));
	}

	@Test
	public void charsetDefaultsAreUnset() {
		NoSqlConfigModel config = new NoSqlConfigModel();
		assertFalse(config.isCharsetSet());
		assertEquals(NoSqlConfigModel.DEFAULT_CHARSET, config.getCharset());
		assertFalse(config.hasRequestTimeout());
		assertEquals(NoSqlConfigModel.DEFAULT_REQUEST_TIMEOUT, config.getRequestTimeout());
		assertFalse(config.hasConnectTimeout());
		assertEquals(NoSqlConfigModel.DEFAULT_CONNECT_TIMEOUT, config.getConnectTimeout());
		assertFalse(config.hasSocketTimeout());
		assertEquals(NoSqlConfigModel.DEFAULT_SOCKET_TIMEOUT, config.getSocketTimeout());
	}

	@Test
	public void illegalCharsetGivesClearError() {
		NoSqlConfigModel statement = jsonMode(null, null);
		statement.setCharset("not_a_charset");
		IllegalArgumentException e = assertThrows(IllegalArgumentException.class,
				() -> HttpClientUtils.resolveCharset(statement, new ElasticEndpoint("http://127.0.0.1:9200")));
		assertTrue(e.getMessage().contains("not_a_charset"));
	}

	@Test
	public void timeoutResolutionPrefersStatementThenEndpoint() {
		ElasticEndpoint endpoint = new ElasticEndpoint("http://127.0.0.1:9200");
		endpoint.setRequestTimeout(1111);
		endpoint.setConnectTimeout(2222);
		endpoint.setSocketTimeout(3333);
		NoSqlConfigModel statement = jsonMode(null, null);
		// 语句未配置:取endpoint
		assertEquals(1111, HttpClientUtils.resolveRequestTimeout(statement, endpoint));
		assertEquals(2222, HttpClientUtils.resolveConnectTimeout(statement, endpoint));
		assertEquals(3333, HttpClientUtils.resolveSocketTimeout(statement, endpoint));
		// 语句配置覆盖endpoint
		statement.setRequestTimeout(4444);
		statement.setConnectTimeout(5555);
		statement.setSocketTimeout(6666);
		assertEquals(4444, HttpClientUtils.resolveRequestTimeout(statement, endpoint));
		assertEquals(5555, HttpClientUtils.resolveConnectTimeout(statement, endpoint));
		assertEquals(6666, HttpClientUtils.resolveSocketTimeout(statement, endpoint));
	}

	@Test
	public void staleConnectionClassification() {
		assertTrue(HttpClientUtils.isStaleConnection(new NoHttpResponseException("closed")));
		assertTrue(HttpClientUtils.isStaleConnection(new ConnectionClosedException("closed")));
		assertFalse(HttpClientUtils.isStaleConnection(new java.net.SocketTimeoutException("timeout")));
	}

	@Test
	public void sqlModeRejectsNonTextValue() {
		// sql模式传Map时toString()会得到{a=b}这类非json内容,须在发请求前失败
		Map<String, String> body = new HashMap<String, String>();
		body.put("query", "select 1");
		IllegalArgumentException e = assertThrows(IllegalArgumentException.class,
				() -> HttpClientUtils.doPost(null, sqlMode(), new ElasticEndpoint("http://127.0.0.1:9200"), body));
		assertTrue(e.getMessage().contains("CharSequence"));
	}

	@Test
	public void missingEndpointGivesClearError() {
		assertThrows(IllegalArgumentException.class,
				() -> HttpClientUtils.doPost(null, jsonMode(null, null), new ElasticEndpoint((String) null), "{}"));
	}
}
