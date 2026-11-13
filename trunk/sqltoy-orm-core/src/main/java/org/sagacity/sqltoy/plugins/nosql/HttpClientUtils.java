package org.sagacity.sqltoy.plugins.nosql;

import java.io.IOException;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

import org.apache.http.HttpEntity;
import org.apache.http.HttpResponse;
import org.apache.http.NameValuePair;
import org.apache.http.auth.AuthScope;
import org.apache.http.auth.UsernamePasswordCredentials;
import org.apache.http.client.ClientProtocolException;
import org.apache.http.client.CredentialsProvider;
import org.apache.http.client.ResponseHandler;
import org.apache.http.client.config.RequestConfig;
import org.apache.http.client.entity.UrlEncodedFormEntity;
import org.apache.http.client.methods.HttpPost;
import org.apache.http.entity.StringEntity;
import org.apache.http.impl.client.BasicCredentialsProvider;
import org.apache.http.impl.client.CloseableHttpClient;
import org.apache.http.impl.client.HttpClients;
import org.apache.http.message.BasicNameValuePair;
import org.apache.http.util.EntityUtils;
import org.elasticsearch.client.Request;
import org.elasticsearch.client.Response;
import org.elasticsearch.client.RestClient;
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

/**
 * @project sagacity-sqltoy
 * @description 提供基于http请求的工具类
 * @author zhongxuchen
 * @version v1.0,Date:2018-01-07
 * @modified 2025-3-17 适配性优化，为切换httpclient5做准备
 * @modified 2026-11-12 jre8全量同步合并:ES提交路径保留httpclient4+elasticsearch-rest-client
 *             (rest5-client为JDK17生态,jre8不可用);rest翻译/JWT认证的通用提交方法
 *             移植自6.0的httpclient5实现(两代httpclient包名不同,通过FQN共存)
 * @modified 2026-11-13 ES栈JSON切换为JsonProvider抽象(与6.0对齐,fastjson2/jackson2可选,
 *             不再硬绑fastjson2);rest路径改相对路径wrapPath(修复多地址url拼接非法uri)
 * @modified 2026-10-02 rest请求认证增加jwt token模式(配合HttpAuthConfig\JwtTokenManager),
 *             401时强制失效token并重试一次,兼容既有basic认证
 */
public class HttpClientUtils {
	/**
	 * 请求配置
	 */
	private final static RequestConfig requestConfig = RequestConfig.custom().setConnectionRequestTimeout(30000)
			.setConnectTimeout(10000).setSocketTimeout(180000).build();

	/**
	 * rest通用请求的httpclient5配置(RequestConfig.setConnectTimeout已废弃,连接超时由connectionConfig配置)
	 */
	private final static org.apache.hc.client5.http.config.RequestConfig restRequestConfig = org.apache.hc.client5.http.config.RequestConfig
			.custom().setConnectionRequestTimeout(org.apache.hc.core5.util.Timeout.ofMilliseconds(30000))
			.setResponseTimeout(org.apache.hc.core5.util.Timeout.ofMilliseconds(180000)).build();

	/**
	 * rest通用请求的httpclient5连接配置(连接超时)
	 */
	private final static org.apache.hc.client5.http.config.ConnectionConfig restConnectionConfig = org.apache.hc.client5.http.config.ConnectionConfig
			.custom().setConnectTimeout(org.apache.hc.core5.util.Timeout.ofMilliseconds(10000)).build();

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
	 * 执行post请求(支持basic与jwt两种认证模式,httpclient5实现)
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
		org.apache.hc.client5.http.classic.methods.HttpPost httpPost = new org.apache.hc.client5.http.classic.methods.HttpPost(
				url);
		// 设置connection是否自动关闭
		httpPost.setHeader("Connection", "close");
		// jwt token认证模式:解析token头值(静态token或登录缓存token)及携带token的请求头名称
		final boolean jwtMode = (authConfig != null && authConfig.isJwt());
		final String tokenHeader = jwtMode ? JwtTokenManager.resolveTokenHeader(authConfig) : null;
		String authorization = jwtMode ? JwtTokenManager.resolveTokenValue(sqltoyContext, authConfig) : null;
		org.apache.hc.client5.http.impl.classic.CloseableHttpClient client = null;
		try {
			if (!jwtMode && authConfig != null && StringUtil.isNotBlank(authConfig.getUsername())
					&& StringUtil.isNotBlank(authConfig.getPassword())) {
				// 凭据提供器(AuthScope在httpclient5.6中移除了ANY常量,null/-1即为任意host/port)
				org.apache.hc.client5.http.impl.auth.BasicCredentialsProvider credsProvider = new org.apache.hc.client5.http.impl.auth.BasicCredentialsProvider();
				credsProvider.setCredentials(new org.apache.hc.client5.http.auth.AuthScope(null, -1),
						// 认证用户名和密码
						new org.apache.hc.client5.http.auth.UsernamePasswordCredentials(authConfig.getUsername(),
								authConfig.getPassword().toCharArray()));
				client = createRestHttpClient(restRequestConfig, restConnectionConfig, credsProvider);
			} else {
				client = createRestHttpClient(restRequestConfig, restConnectionConfig, null);
			}
			if (paramValue != null && paramValue.length > 0) {
				List<org.apache.hc.core5.http.NameValuePair> nvps = new ArrayList<org.apache.hc.core5.http.NameValuePair>();
				// paramName与paramValue按位对应,长度不一致或name缺省时按较短者截取,避免数组越界
				int paramSize = (paramName == null) ? 0 : Math.min(paramName.length, paramValue.length);
				for (int i = 0; i < paramSize; i++) {
					// BasicNameValuePair遇null/空白name抛IllegalArgumentException,name缺省时跳过该项
					if (paramValue[i] != null && StringUtil.isNotBlank(paramName[i])) {
						nvps.add(new org.apache.hc.core5.http.message.BasicNameValuePair(paramName[i], paramValue[i]));
					}
				}
				// 表单提交保持UrlEncodedFormEntity默认的application/x-www-form-urlencoded,
				// 不能覆盖成application/json,否则服务端无法按表单解析参数
				org.apache.hc.core5.http.HttpEntity httpEntity = new org.apache.hc.client5.http.entity.UrlEncodedFormEntity(
						nvps, java.nio.charset.Charset.forName(CHARSET));
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
				String body = client.execute(httpPost,
						new org.apache.hc.core5.http.io.HttpClientResponseHandler<String>() {
							@Override
							public String handleResponse(org.apache.hc.core5.http.ClassicHttpResponse response)
									throws org.apache.hc.core5.http.HttpException, IOException {
								responseCode[0] = response.getCode();
								// 返回结果
								org.apache.hc.core5.http.HttpEntity reponseEntity = response.getEntity();
								if (reponseEntity != null) {
									return org.apache.hc.core5.http.io.entity.EntityUtils
											.toString(reponseEntity, CHARSET);
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

	// 连接管理器未设为shared,随client关闭时一并释放
	private static org.apache.hc.client5.http.impl.classic.CloseableHttpClient createRestHttpClient(
			org.apache.hc.client5.http.config.RequestConfig requestConfig,
			org.apache.hc.client5.http.config.ConnectionConfig connectionConfig,
			org.apache.hc.client5.http.impl.auth.BasicCredentialsProvider credsProvider) {
		org.apache.hc.client5.http.impl.io.PoolingHttpClientConnectionManager connectionManager = org.apache.hc.client5.http.impl.io.PoolingHttpClientConnectionManagerBuilder
				.create().setDefaultConnectionConfig(connectionConfig).build();
		org.apache.hc.client5.http.impl.classic.HttpClientBuilder builder = org.apache.hc.client5.http.impl.classic.HttpClients
				.custom().setConnectionManager(connectionManager).setDefaultRequestConfig(requestConfig);
		if (credsProvider != null) {
			builder.setDefaultCredentialsProvider(credsProvider);
		}
		return builder.build();
	}

	/**
	 * 执行post请求
	 *
	 * @param sqltoyContext
	 * @param nosqlConfig
	 * @param esConfig
	 * @param postValue
	 * @return
	 * @throws Exception
	 */
	public static JsonObject doPost(SqlToyContext sqltoyContext, NoSqlConfigModel nosqlConfig, ElasticEndpoint esConfig,
			Object postValue) throws Exception {
		if (esConfig.getUrl() == null) {
			throw new IllegalArgumentException(
					"elastic server address is not configured, please check sqltoyContext elasticConfigs!");
		}
		String charset = (nosqlConfig.getCharset() == null) ? CHARSET : nosqlConfig.getCharset();
		HttpEntity httpEntity = null;
		// sql 模式
		if (nosqlConfig.isSqlMode()) {
			// 6.3.x 版本支持xpack sql查询
			if (esConfig.isNativeSql()) {
				Map<String, String> map = new HashMap<String, String>();
				map.put("query", postValue.toString());
				httpEntity = new StringEntity(JsonProviders.get().toJSONString(map), charset);
			} else {
				httpEntity = new StringEntity(postValue.toString(), charset);
			}
		} else {
			httpEntity = new StringEntity(JsonProviders.get().toJSONString(postValue), charset);
		}
		((StringEntity) httpEntity).setContentEncoding(charset);
		((StringEntity) httpEntity).setContentType(CONTENT_TYPE);
		return doPostEntity(sqltoyContext, nosqlConfig, esConfig, httpEntity);
	}

	/**
	 * 发送整体序列化的json请求体(不做sql模式包装,面向ES SQL的fetch_size+cursor游标分页等
	 * 需要自定义请求体结构的场景)
	 *
	 * @param sqltoyContext
	 * @param nosqlConfig
	 * @param esConfig
	 * @param bodyObject    请求体对象(将整体序列化为json发送,不做sql模式包装)
	 * @return
	 * @throws Exception
	 */
	public static JsonObject doPostBody(SqlToyContext sqltoyContext, NoSqlConfigModel nosqlConfig,
			ElasticEndpoint esConfig, Object bodyObject) throws Exception {
		if (esConfig.getUrl() == null) {
			throw new IllegalArgumentException(
					"elastic server address is not configured, please check sqltoyContext elasticConfigs!");
		}
		String charset = (nosqlConfig.getCharset() == null) ? CHARSET : nosqlConfig.getCharset();
		HttpEntity httpEntity = new StringEntity(JsonProviders.get().toJSONString(bodyObject), charset);
		((StringEntity) httpEntity).setContentEncoding(charset);
		((StringEntity) httpEntity).setContentType(CONTENT_TYPE);
		return doPostEntity(sqltoyContext, nosqlConfig, esConfig, httpEntity);
	}

	/**
	 * 发送请求体并解析响应(统一elastic rest client与直连httpclient两条路径)
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
		String realUrl;
		// 返回结果
		String result = null;
		// 使用elastic rest client(默认)
		if (esConfig.getRestClient() != null) {
			// 多节点客户端自带base地址,请求只需相对路径(修复多地址url直接拼接产生非法uri的问题,
			// 与6.0的rest5-client路径同款修法)
			realUrl = wrapPath(esConfig, nosqlConfig);
			if (sqltoyContext.isDebug()) {
				logger.debug("esRestClient execution:URL=[{}],Path={}", esConfig.getUrl(), realUrl);
			}
			// 默认采用post请求
			RestClient restClient = esConfig.getRestClient();
			Request request = new Request(POST, realUrl);
			request.setEntity(httpEntity);
			Response response = restClient.performRequest(request);
			HttpEntity reponseEntity = response.getEntity();
			if (reponseEntity != null) {
				result = EntityUtils.toString(reponseEntity, nosqlConfig.getCharset());
			}
		} // 组织httpclient模式调用(此种模式不推荐使用)
		else {
			realUrl = wrapUrl(esConfig, nosqlConfig);
			HttpPost httpPost = new HttpPost(realUrl);
			if (sqltoyContext.isDebug()) {
				logger.debug("httpClient execution URL=[{}]", realUrl);
			}
			httpPost.setEntity(httpEntity);
			// 设置connection是否自动关闭
			httpPost.setHeader("Connection", "close");
			CloseableHttpClient client = null;
			try {
				if (StringUtil.isNotBlank(esConfig.getUsername()) && StringUtil.isNotBlank(esConfig.getPassword())) {
					// 凭据提供器
					CredentialsProvider credsProvider = new BasicCredentialsProvider();
					credsProvider.setCredentials(AuthScope.ANY,
							// 认证用户名和密码
							new UsernamePasswordCredentials(esConfig.getUsername(), esConfig.getPassword()));
					client = HttpClients.custom().setDefaultRequestConfig(getRequestConfig(nosqlConfig))
							.setDefaultCredentialsProvider(credsProvider).build();
				} else {
					client = HttpClients.custom().setDefaultRequestConfig(getRequestConfig(nosqlConfig)).build();
				}
				result = client.execute(httpPost, new ResponseHandler<String>() {
					@Override
					public String handleResponse(HttpResponse response) throws ClientProtocolException, IOException {
						HttpEntity reponseEntity = response.getEntity();
						if (reponseEntity != null) {
							return EntityUtils.toString(reponseEntity, nosqlConfig.getCharset());
						}
						return null;
					}
				});
			} catch (Exception e) {
				throw e;
			} finally {
				IOUtil.closeQuietly(client);
			}
		}
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
	 * 重新组织url
	 *
	 * @param esConfig
	 * @param nosqlConfig
	 * @return
	 */
	private static String wrapUrl(ElasticEndpoint esConfig, NoSqlConfigModel nosqlConfig) {
		String url = esConfig.getUrl();
		String sqlPath = esConfig.getSqlPath();
		if (StringUtil.isBlank(sqlPath)) {
			sqlPath = "_sql";
		}
		// elasticsearch6.3.x 通过xpack支持sql查询
		// 6.3 /_xpack/sql
		// 7.x /_sql
		// elasticsearch-sql7.4 /_sql
		// elasticsearch-sql7.5+ /_nlpcn/sql
		// elasticsearch-sql7.9.3 之后不再维护,启用_opendistro/_sql
		if (nosqlConfig.isSqlMode()) {
			if (!url.toLowerCase(Locale.ROOT).contains(sqlPath)) {
				url = url.concat(url.endsWith("/") ? "" : "/").concat(sqlPath);
			}
		} else {
			if (StringUtil.isNotBlank(nosqlConfig.getIndex())) {
				url = url.concat(url.endsWith("/") ? "" : "/").concat(nosqlConfig.getIndex());
			}
			// es6.x 支持，7开始废弃
			if (StringUtil.isNotBlank(nosqlConfig.getType())) {
				url = url.concat(url.endsWith("/") ? "" : "/").concat(nosqlConfig.getType());
			}
			if (!url.toLowerCase(Locale.ROOT).endsWith(SEARCH)) {
				url = url.concat(url.endsWith("/") ? "" : "/").concat(SEARCH);
			}
		}
		return url;
	}

	/**
	 * 组织相对查询路径(rest-client模式:客户端自带多节点base地址,请求只需路径,
	 * 避免多地址url直接拼接产生非法uri)
	 *
	 * @param esConfig
	 * @param nosqlConfig
	 * @return
	 */
	private static String wrapPath(ElasticEndpoint esConfig, NoSqlConfigModel nosqlConfig) {
		String path = "";
		String sqlPath = esConfig.getSqlPath();
		if (StringUtil.isBlank(sqlPath)) {
			sqlPath = "_sql";
		}
		if (nosqlConfig.isSqlMode()) {
			path = path.concat("/").concat(sqlPath);
		} else {
			if (StringUtil.isNotBlank(nosqlConfig.getIndex())) {
				path = path.concat("/").concat(nosqlConfig.getIndex());
			}
			// es6.x 支持，7开始废弃
			if (StringUtil.isNotBlank(nosqlConfig.getType())) {
				path = path.concat("/").concat(nosqlConfig.getType());
			}
			if (!path.toLowerCase(Locale.ROOT).endsWith(SEARCH)) {
				path = path.concat("/").concat(SEARCH);
			}
		}
		return path;
	}

	private static RequestConfig getRequestConfig(NoSqlConfigModel nosqlConfig) {
		if (nosqlConfig != null && (nosqlConfig.getRequestTimeout() != 30000 || nosqlConfig.getConnectTimeout() != 10000
				|| nosqlConfig.getSocketTimeout() != 180000)) {
			return RequestConfig.custom().setConnectTimeout(nosqlConfig.getConnectTimeout())
					.setConnectionRequestTimeout(nosqlConfig.getRequestTimeout())
					.setSocketTimeout(nosqlConfig.getSocketTimeout()).build();
		} else {
			return requestConfig;
		}
	}
}
