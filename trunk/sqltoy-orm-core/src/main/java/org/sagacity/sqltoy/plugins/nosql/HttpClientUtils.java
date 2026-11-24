package org.sagacity.sqltoy.plugins.nosql;

import java.io.IOException;
import java.nio.charset.Charset;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

import org.apache.hc.client5.http.auth.AuthScope;
import org.apache.hc.client5.http.auth.UsernamePasswordCredentials;
import org.apache.hc.client5.http.classic.methods.HttpPost;
import org.apache.hc.client5.http.config.ConnectionConfig;
import org.apache.hc.client5.http.config.RequestConfig;
import org.apache.hc.client5.http.entity.UrlEncodedFormEntity;
import org.apache.hc.client5.http.impl.auth.BasicCredentialsProvider;
import org.apache.hc.client5.http.impl.classic.CloseableHttpClient;
import org.apache.hc.client5.http.impl.classic.HttpClientBuilder;
import org.apache.hc.client5.http.impl.classic.HttpClients;
import org.apache.hc.client5.http.impl.io.PoolingHttpClientConnectionManager;
import org.apache.hc.client5.http.impl.io.PoolingHttpClientConnectionManagerBuilder;
import org.apache.hc.core5.http.ClassicHttpResponse;
import org.apache.hc.core5.http.ConnectionClosedException;
import org.apache.hc.core5.http.ContentType;
import org.apache.hc.core5.http.HttpEntity;
import org.apache.hc.core5.http.HttpException;
import org.apache.hc.core5.http.NameValuePair;
import org.apache.hc.core5.http.NoHttpResponseException;
import org.apache.hc.core5.http.io.HttpClientResponseHandler;
import org.apache.hc.core5.http.io.entity.EntityUtils;
import org.apache.hc.core5.http.io.entity.StringEntity;
import org.apache.hc.core5.http.message.BasicNameValuePair;
import org.apache.hc.core5.util.Timeout;
import org.sagacity.sqltoy.SqlToyContext;
import org.sagacity.sqltoy.config.model.ElasticEndpoint;
import org.sagacity.sqltoy.config.model.NoSqlConfigModel;
import org.sagacity.sqltoy.exception.DataAccessException;
import org.sagacity.sqltoy.plugins.json.JsonArray;
import org.sagacity.sqltoy.plugins.json.JsonObject;
import org.sagacity.sqltoy.plugins.json.JsonProviders;
import org.sagacity.sqltoy.utils.IOUtil;
import org.sagacity.sqltoy.utils.StringUtil;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import co.elastic.clients.transport.rest5_client.low_level.Request;
import co.elastic.clients.transport.rest5_client.low_level.Response;
import co.elastic.clients.transport.rest5_client.low_level.Rest5Client;

/**
 * @project sagacity-sqltoy
 * @description 提供基于http请求的工具类(elastic查询统一走rest5客户端:单地址/多地址、eql自定义url 一致,base
 *              path由pathPrefix承载,请求路径由buildRequestPath组织)
 * @author zhongxuchen
 * @version v1.0,Date:2018-01-07
 * @modified 2025-3-17 适配性优化，为切换httpclient5做准备
 * @modified 2026-9-12 升级httpclient至5.x,elastic rest
 *           client切换为官方基于httpclient5的rest5-client
 * @modified 2026-10-02 rest请求认证增加jwt token模式(配合HttpAuthConfig\JwtTokenManager),
 *           401时强制失效token并重试一次,兼容既有basic认证
 * @modified 2026-11-13 elastic查询统一走rest5客户端(单地址/多地址一致,直连httpclient路径移除);
 *           超时与字符集改为endpoint默认+语句级覆盖,池中连接被对端关闭时重试一次
 */
public class HttpClientUtils {
	/**
	 * 请求配置(RequestConfig.setConnectTimeout已废弃,连接超时由connectionConfig配置);
	 * 仅用于表单post(JWT/translate)的一次性客户端,elastic查询按endpoint/语句动态构建
	 */
	private final static RequestConfig requestConfig = RequestConfig.custom()
			.setConnectionRequestTimeout(Timeout.ofMilliseconds(NoSqlConfigModel.DEFAULT_REQUEST_TIMEOUT))
			.setResponseTimeout(Timeout.ofMilliseconds(NoSqlConfigModel.DEFAULT_SOCKET_TIMEOUT)).build();

	/**
	 * 连接配置(连接超时)
	 */
	private final static ConnectionConfig connectionConfig = ConnectionConfig.custom()
			.setConnectTimeout(Timeout.ofMilliseconds(NoSqlConfigModel.DEFAULT_CONNECT_TIMEOUT)).build();

	/**
	 * 定义全局日志
	 */
	protected final static Logger logger = LoggerFactory.getLogger(HttpClientUtils.class);

	private final static String CHARSET = "UTF-8";

	private final static String SEARCH = "_search";

	private final static String CONTENT_TYPE = "application/json";

	private final static String POST = "POST";

	private HttpClientUtils() {
	}

	public static String doPost(SqlToyContext sqltoyContext, final String url, String username, String password,
			String[] paramName, String[] paramValue) throws Exception {
		return doPost(sqltoyContext, url, HttpAuthConfig.basic(username, password), paramName, paramValue);
	}

	/**
	 * 执行post请求(支持basic与jwt两种认证模式)
	 * 
	 * @param sqltoyContext
	 * @param url           请求地址
	 * @param authConfig    认证配置:basic模式走凭据提供器,jwt模式携带Authorization请求头
	 * @param paramName     表单参数名称数组
	 * @param paramValue    表单参数值数组
	 * @return
	 * @throws Exception
	 */
	public static String doPost(SqlToyContext sqltoyContext, final String url, HttpAuthConfig authConfig,
			String[] paramName, String[] paramValue) throws Exception {
		HttpPost httpPost = new HttpPost(url);
		// 设置connection是否自动关闭
		httpPost.setHeader("Connection", "close");
		// jwt token认证模式:解析token头值(静态token或登录缓存token)及携带token的请求头名称
		final boolean jwtMode = (authConfig != null && authConfig.isJwt());
		final String tokenHeader = jwtMode ? JwtTokenManager.resolveTokenHeader(authConfig) : null;
		String authorization = jwtMode ? JwtTokenManager.resolveTokenValue(sqltoyContext, authConfig) : null;
		CloseableHttpClient client = null;
		try {
			if (!jwtMode && authConfig != null && StringUtil.isNotBlank(authConfig.getUsername())
					&& StringUtil.isNotBlank(authConfig.getPassword())) {
				// 凭据提供器(AuthScope在httpclient5.6中移除了ANY常量,null/-1即为任意host/port)
				BasicCredentialsProvider credsProvider = new BasicCredentialsProvider();
				credsProvider.setCredentials(new AuthScope(null, -1),
						// 认证用户名和密码
						new UsernamePasswordCredentials(authConfig.getUsername(),
								authConfig.getPassword().toCharArray()));
				client = createHttpClient(requestConfig, connectionConfig, credsProvider);
			} else {
				client = createHttpClient(requestConfig, connectionConfig, null);
			}
			if (paramValue != null && paramValue.length > 0) {
				List<NameValuePair> nvps = new ArrayList<NameValuePair>();
				// paramName与paramValue按位对应,长度不一致或name缺省时按较短者截取,避免数组越界
				int paramSize = (paramName == null) ? 0 : Math.min(paramName.length, paramValue.length);
				for (int i = 0; i < paramSize; i++) {
					// BasicNameValuePair遇null/空白name抛IllegalArgumentException,name缺省时跳过该项
					if (paramValue[i] != null && StringUtil.isNotBlank(paramName[i])) {
						nvps.add(new BasicNameValuePair(paramName[i], paramValue[i]));
					}
				}
				// 表单提交保持UrlEncodedFormEntity默认的application/x-www-form-urlencoded,
				// 不能覆盖成application/json,否则服务端无法按表单解析参数
				HttpEntity httpEntity = new UrlEncodedFormEntity(nvps, Charset.forName(CHARSET));
				httpPost.setEntity(httpEntity);
			}
			// jwt模式token被服务端拒绝(401)时强制失效重登并重试一次
			final int[] responseCode = new int[1];
			int maxAttempts = jwtMode ? 2 : 1;
			for (int attempt = 0; attempt < maxAttempts; attempt++) {
				if (attempt > 0) {
					JwtTokenManager.invalidate(authConfig);
					authorization = JwtTokenManager.resolveTokenValue(sqltoyContext, authConfig);
				}
				if (jwtMode) {
					httpPost.setHeader(tokenHeader, authorization);
				}
				String body = client.execute(httpPost, new HttpClientResponseHandler<String>() {
					@Override
					public String handleResponse(ClassicHttpResponse response) throws HttpException, IOException {
						responseCode[0] = response.getCode();
						// 返回结果
						HttpEntity reponseEntity = response.getEntity();
						if (reponseEntity != null) {
							return EntityUtils.toString(reponseEntity, CHARSET);
						}
						return null;
					}
				});
				// token过期被拒:失效缓存后重试(仅一次),再次401则交由调用方按返回内容处理
				if (jwtMode && responseCode[0] == 401 && attempt < maxAttempts - 1) {
					continue;
				}
				return body;
			}
			return null;
		} catch (Exception e) {
			throw e;
		} finally {
			IOUtil.closeQuietly(client);
		}
	}

	/**
	 * 执行post请求(按查询配置解释postValue的含义)
	 * <p>
	 * 三种模式:1)sqlMode且为原生sql(如_sql、_xpack/sql):postValue为SQL文本,包装为{"query":"..."};
	 * 2)sqlMode且非原生sql(elasticsearch-sql等插件路径):postValue为完整请求体文本,原样发送;
	 * 3)非sqlMode(eql):postValue为查询对象,整体序列化为json发送(传字符串会被二次转义,属误用)。
	 * 需要发送完整请求体(如原生sql游标分页的fetch_size+cursor)时应改用{@link #doPostBody}。
	 *
	 * @param sqltoyContext
	 * @param nosqlConfig
	 * @param esConfig
	 * @param postValue     sql模式为SQL文本(CharSequence),json模式为查询对象
	 * @return
	 * @throws Exception
	 */
	public static JsonObject doPost(SqlToyContext sqltoyContext, NoSqlConfigModel nosqlConfig, ElasticEndpoint esConfig,
			Object postValue) throws Exception {
		checkEndpoint(esConfig);
		// sql模式的入参是SQL文本:传Map等对象时toString()会得到{a=b}这类非json内容,服务端只能返回400,
		// 在发请求前给出明确错误(nativeSql包装前同样由此保证非空)
		if (nosqlConfig.isSqlMode() && !(postValue instanceof CharSequence)) {
			throw new IllegalArgumentException("elastic sql mode requires the SQL text as CharSequence, but got:["
					+ (postValue == null ? "null" : postValue.getClass().getName()) + "]");
		}
		// httpclient5的实体内容类型在构造时指定
		ContentType contentType = ContentType.create(CONTENT_TYPE, resolveCharset(nosqlConfig, esConfig));
		HttpEntity httpEntity = null;
		// sql 模式
		if (nosqlConfig.isSqlMode()) {
			// 6.3.x 版本支持xpack sql查询
			if (esConfig.isNativeSql()) {
				Map<String, String> map = new HashMap<String, String>();
				map.put("query", postValue.toString());
				httpEntity = new StringEntity(JsonProviders.get().toJSONString(map), contentType);
			} else {
				httpEntity = new StringEntity(postValue.toString(), contentType);
			}
		} else {
			httpEntity = new StringEntity(JsonProviders.get().toJSONString(postValue), contentType);
		}
		return doPostEntity(sqltoyContext, nosqlConfig, esConfig, httpEntity);
	}

	/**
	 * 发送完整请求体(原样json序列化,不按sql模式包装),用于es原生sql的游标分页等协议级请求
	 *
	 * @param sqltoyContext
	 * @param nosqlConfig
	 * @param esConfig
	 * @param bodyObject    完整请求体对象(如{"query":"...","fetch_size":100})
	 * @return
	 * @throws Exception
	 */
	public static JsonObject doPostBody(SqlToyContext sqltoyContext, NoSqlConfigModel nosqlConfig,
			ElasticEndpoint esConfig, Object bodyObject) throws Exception {
		checkEndpoint(esConfig);
		ContentType contentType = ContentType.create(CONTENT_TYPE, resolveCharset(nosqlConfig, esConfig));
		HttpEntity httpEntity = new StringEntity(JsonProviders.get().toJSONString(bodyObject), contentType);
		return doPostEntity(sqltoyContext, nosqlConfig, esConfig, httpEntity);
	}

	/**
	 * 发送请求体并解析响应(统一走rest5客户端:单地址/多地址、eql自定义url一致)
	 *
	 * @param sqltoyContext
	 * @param nosqlConfig
	 * @param esConfig
	 * @param httpEntity
	 * @return
	 * @throws Exception
	 */
	private static JsonObject doPostEntity(SqlToyContext sqltoyContext, NoSqlConfigModel nosqlConfig,
			ElasticEndpoint esConfig, HttpEntity httpEntity) throws Exception {
		// 语句级超时优先,endpoint配置兜底
		int requestTimeout = resolveRequestTimeout(nosqlConfig, esConfig);
		int connectTimeout = resolveConnectTimeout(nosqlConfig, esConfig);
		int socketTimeout = resolveSocketTimeout(nosqlConfig, esConfig);
		// 与endpoint默认超时不同的语句使用独立的rest5客户端(异步客户端超时只能在构建时确定)
		Rest5Client restClient = esConfig.getRestClient(requestTimeout, connectTimeout, socketTimeout);
		if (restClient == null) {
			throw new IllegalArgumentException(
					"elastic rest client initialize failed, please check elastic url config:[" + esConfig.getUrl()
							+ "]");
		}
		// 客户端已携带节点base地址与pathPrefix,请求只需相对路径
		String realUrl = buildRequestPath(esConfig, nosqlConfig);
		if (sqltoyContext.isDebug()) {
			logger.debug("esRestClient execution:URL=[{}],Path={}", esConfig.getUrl(), realUrl);
		}
		Request request = new Request(POST, realUrl);
		request.setEntity(httpEntity);
		Response response = performRequest(restClient, request);
		HttpEntity reponseEntity = response.getEntity();
		String result = (reponseEntity == null) ? null
				: EntityUtils.toString(reponseEntity, resolveCharset(nosqlConfig, esConfig));
		if (StringUtil.isBlank(result)) {
			return null;
		}
		if (sqltoyContext.isDebug()) {
			logger.debug("result={}", result);
		}
		return parseElasticResult(result);
	}

	/**
	 * 解析elastic查询结果,存在error时抛出携带错误信息的异常
	 * 
	 * @param result elastic返回的json字符串
	 * @return
	 */
	public static JsonObject parseElasticResult(String result) {
		// 将结果转换为JSON对象
		JsonObject json = JsonProviders.get().object(result);
		// 存在错误:error结构随版本/异常类型变化,root_cause可能缺失,error本身可能是字符串,
		// 防御式提取避免NPE掩盖真实错误信息
		if (json.containsKey("error")) {
			Object errorObj = json.get("error");
			String errorMessage;
			if (errorObj instanceof java.util.Map) {
				JsonObject errorJson = JsonObject.of(errorObj);
				JsonArray rootCause = errorJson.getJSONArray("root_cause");
				if (rootCause != null && !rootCause.isEmpty()) {
					errorMessage = JsonProviders.get().toJSONString(rootCause.get(0));
				} else if (errorJson.getString("reason") != null) {
					errorMessage = errorJson.getString("reason");
				} else {
					errorMessage = JsonProviders.get().toJSONString(errorJson);
				}
			} else {
				errorMessage = String.valueOf(errorObj);
			}
			logger.error("elastic query failed, error message:[{}]", errorMessage);
			throw new DataAccessException("ElasticSearch query failed, error message:" + errorMessage);
		}
		return json;
	}

	/**
	 * 组织rest5客户端的请求相对路径(节点地址与base path由客户端构建时的HttpHost/pathPrefix承载); 包级可见以便同包测试
	 *
	 * @param esConfig
	 * @param nosqlConfig
	 * @return 以"/"开头的请求路径
	 */
	static String buildRequestPath(ElasticEndpoint esConfig, NoSqlConfigModel nosqlConfig) {
		String sqlPath = StringUtil.isBlank(esConfig.getSqlPath()) ? "_sql" : esConfig.getSqlPath();
		String path = "";
		// elasticsearch6.3.x 通过xpack支持sql查询
		// 6.3 /_xpack/sql
		// 7.x /_sql
		// elasticsearch-sql7.4 /_sql
		// elasticsearch-sql7.5+ /_nlpcn/sql
		// elasticsearch-sql7.9.3 之后不再维护,启用_opendistro/_sql
		if (nosqlConfig.isSqlMode()) {
			return join(path, sqlPath);
		}
		if (StringUtil.isNotBlank(nosqlConfig.getIndex())) {
			// url本身已指向搜索端点(如http://host:9200/idx/_search)时pathPrefix已含index,
			// 跳过拼接避免/idx/idx/_search重复
			String pathPrefix = esConfig.getPathPrefix().toLowerCase(Locale.ROOT);
			if (!pathPrefix.endsWith("/" + nosqlConfig.getIndex().toLowerCase(Locale.ROOT))) {
				path = join(path, nosqlConfig.getIndex());
			}
		}
		// es6.x 支持，7开始废弃
		if (StringUtil.isNotBlank(nosqlConfig.getType())) {
			path = join(path, nosqlConfig.getType());
		}
		if (!path.toLowerCase(Locale.ROOT).endsWith(SEARCH)) {
			path = join(path, SEARCH);
		}
		return path;
	}

	/**
	 * 执行rest5请求:池化后的keep-alive连接可能已被服务端/LB单方面关闭,此类失败重建请求重试一次
	 * (查询类请求可安全重发,Request持有可重复写出的StringEntity)
	 */
	private static Response performRequest(Rest5Client restClient, Request request) throws IOException {
		try {
			return restClient.performRequest(request);
		} catch (IOException e) {
			if (!isStaleConnection(e)) {
				throw e;
			}
			// 属于keep-alive的正常失效(服务端/LB空闲回收),按debug记录避免刷日志
			logger.debug("elastic pooled connection was closed by peer, retry once: [{}]", e.getMessage());
			return restClient.performRequest(request);
		}
	}

	/**
	 * 拼接路径段:base以"/"结尾时不再重复补"/",base为空串时得到"/segment"
	 */
	private static String join(String base, String segment) {
		return base.concat(base.endsWith("/") ? "" : "/").concat(segment);
	}

	/**
	 * 校验es地址配置,地址为空(含仅分隔符)时给出明确提示(避免拼出非法uri)
	 */
	private static void checkEndpoint(ElasticEndpoint esConfig) {
		if (esConfig == null || esConfig.getUrlList().length == 0) {
			throw new IllegalArgumentException(
					"elastic server address is not configured, please check sqltoyContext elasticConfigs!");
		}
	}

	/**
	 * 解析字符集:语句级配置优先,endpoint(默认UTF-8)兜底;配置值非法时给出明确的配置错误。 包级可见以便同包测试
	 *
	 * @param nosqlConfig
	 * @param esConfig
	 * @return
	 */
	static Charset resolveCharset(NoSqlConfigModel nosqlConfig, ElasticEndpoint esConfig) {
		String charset = (nosqlConfig.isCharsetSet() && StringUtil.isNotBlank(nosqlConfig.getCharset()))
				? nosqlConfig.getCharset()
				: esConfig.getCharset();
		if (StringUtil.isBlank(charset)) {
			return Charset.forName(CHARSET);
		}
		try {
			return Charset.forName(charset);
		} catch (Exception e) {
			throw new IllegalArgumentException("illegal elastic charset:[" + charset + "]", e);
		}
	}

	/**
	 * 语句级配置优先,endpoint配置兜底
	 *
	 * @param nosqlConfig
	 * @param esConfig
	 * @return
	 */
	static int resolveRequestTimeout(NoSqlConfigModel nosqlConfig, ElasticEndpoint esConfig) {
		return nosqlConfig.hasRequestTimeout() ? nosqlConfig.getRequestTimeout() : esConfig.getRequestTimeout();
	}

	static int resolveConnectTimeout(NoSqlConfigModel nosqlConfig, ElasticEndpoint esConfig) {
		return nosqlConfig.hasConnectTimeout() ? nosqlConfig.getConnectTimeout() : esConfig.getConnectTimeout();
	}

	static int resolveSocketTimeout(NoSqlConfigModel nosqlConfig, ElasticEndpoint esConfig) {
		return nosqlConfig.hasSocketTimeout() ? nosqlConfig.getSocketTimeout() : esConfig.getSocketTimeout();
	}

	/**
	 * 判定是否"池中连接已被对端关闭"类异常:仅这类失败可安全重试。包级可见以便同包测试
	 *
	 * @param e
	 * @return
	 */
	static boolean isStaleConnection(IOException e) {
		return e instanceof NoHttpResponseException || e instanceof ConnectionClosedException;
	}

	// 连接管理器未设为shared,随client关闭时一并释放;表单post(JWT/translate)仍为一次性客户端
	private static CloseableHttpClient createHttpClient(RequestConfig requestConfig, ConnectionConfig connectionConfig,
			BasicCredentialsProvider credsProvider) {
		PoolingHttpClientConnectionManager connectionManager = PoolingHttpClientConnectionManagerBuilder.create()
				.setDefaultConnectionConfig(connectionConfig).build();
		HttpClientBuilder builder = HttpClients.custom().setConnectionManager(connectionManager)
				.setDefaultRequestConfig(requestConfig);
		if (credsProvider != null) {
			builder.setDefaultCredentialsProvider(credsProvider);
		}
		return builder.build();
	}
}
