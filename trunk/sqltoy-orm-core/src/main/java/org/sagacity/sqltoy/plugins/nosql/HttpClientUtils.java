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
import org.apache.hc.core5.http.ContentType;
import org.apache.hc.core5.http.HttpEntity;
import org.apache.hc.core5.http.HttpException;
import org.apache.hc.core5.http.NameValuePair;
import org.apache.hc.core5.http.io.HttpClientResponseHandler;
import org.apache.hc.core5.http.io.entity.EntityUtils;
import org.apache.hc.core5.http.io.entity.StringEntity;
import org.apache.hc.core5.http.message.BasicNameValuePair;
import org.apache.hc.core5.util.Timeout;
import org.sagacity.sqltoy.SqlToyContext;
import org.sagacity.sqltoy.config.model.ElasticEndpoint;
import org.sagacity.sqltoy.config.model.NoSqlConfigModel;
import org.sagacity.sqltoy.exception.DataAccessException;
import org.sagacity.sqltoy.utils.IOUtil;
import org.sagacity.sqltoy.utils.StringUtil;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.alibaba.fastjson2.JSON;
import com.alibaba.fastjson2.JSONArray;
import com.alibaba.fastjson2.JSONObject;

import co.elastic.clients.transport.rest5_client.low_level.Request;
import co.elastic.clients.transport.rest5_client.low_level.Response;
import co.elastic.clients.transport.rest5_client.low_level.Rest5Client;

/**
 * @project sagacity-sqltoy
 * @description 提供基于http请求的工具类
 * @author zhongxuchen
 * @version v1.0,Date:2018-01-07
 * @modified 2025-3-17 适配性优化，为切换httpclient5做准备
 * @modified 2026-9-12 升级httpclient至5.x,elastic rest
 *           client切换为官方基于httpclient5的rest5-client
 */
public class HttpClientUtils {
	/**
	 * 请求配置(RequestConfig.setConnectTimeout已废弃,连接超时由connectionConfig配置)
	 */
	private final static RequestConfig requestConfig = RequestConfig.custom()
			.setConnectionRequestTimeout(Timeout.ofMilliseconds(30000))
			.setResponseTimeout(Timeout.ofMilliseconds(180000)).build();

	/**
	 * 连接配置(连接超时)
	 */
	private final static ConnectionConfig connectionConfig = ConnectionConfig.custom()
			.setConnectTimeout(Timeout.ofMilliseconds(10000)).build();

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
		HttpPost httpPost = new HttpPost(url);
		// 设置connection是否自动关闭
		httpPost.setHeader("Connection", "close");
		CloseableHttpClient client = null;
		try {
			if (StringUtil.isNotBlank(username) && StringUtil.isNotBlank(password)) {
				// 凭据提供器(AuthScope在httpclient5.6中移除了ANY常量,null/-1即为任意host/port)
				BasicCredentialsProvider credsProvider = new BasicCredentialsProvider();
				credsProvider.setCredentials(new AuthScope(null, -1),
						// 认证用户名和密码
						new UsernamePasswordCredentials(username, password.toCharArray()));
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
			return client.execute(httpPost, new HttpClientResponseHandler<String>() {
				@Override
				public String handleResponse(ClassicHttpResponse response) throws HttpException, IOException {
					// 返回结果
					HttpEntity reponseEntity = response.getEntity();
					if (reponseEntity != null) {
						return EntityUtils.toString(reponseEntity, CHARSET);
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
	public static JSONObject doPost(SqlToyContext sqltoyContext, NoSqlConfigModel nosqlConfig, ElasticEndpoint esConfig,
			Object postValue) throws Exception {
		if (esConfig.getUrl() == null) {
			throw new IllegalArgumentException(
					"elastic server address is not configured, please check sqltoyContext elasticConfigs!");
		}
		String charset = (nosqlConfig.getCharset() == null) ? CHARSET : nosqlConfig.getCharset();
		// httpclient5的实体内容类型在构造时指定
		ContentType contentType = ContentType.create(CONTENT_TYPE, Charset.forName(charset));
		HttpEntity httpEntity = null;
		// sql 模式
		if (nosqlConfig.isSqlMode()) {
			// 6.3.x 版本支持xpack sql查询
			if (esConfig.isNativeSql()) {
				Map<String, String> map = new HashMap<String, String>();
				map.put("query", postValue.toString());
				httpEntity = new StringEntity(JSON.toJSONString(map), contentType);
			} else {
				httpEntity = new StringEntity(postValue.toString(), contentType);
			}
		} else {
			httpEntity = new StringEntity(JSON.toJSONString(postValue), contentType);
		}
		return doPostEntity(sqltoyContext, nosqlConfig, esConfig, httpEntity);
	}

	/**
	 * 发送自定义请求体的post请求(请求体以json序列化),用于es原生sql的游标分页等场景
	 *
	 * @param sqltoyContext
	 * @param nosqlConfig
	 * @param esConfig
	 * @param bodyObject    请求体对象(将整体序列化为json发送,不做sql模式包装)
	 * @return
	 * @throws Exception
	 */
	public static JSONObject doPostBody(SqlToyContext sqltoyContext, NoSqlConfigModel nosqlConfig,
			ElasticEndpoint esConfig, Object bodyObject) throws Exception {
		if (esConfig.getUrl() == null) {
			throw new IllegalArgumentException(
					"elastic server address is not configured, please check sqltoyContext elasticConfigs!");
		}
		String charset = (nosqlConfig.getCharset() == null) ? CHARSET : nosqlConfig.getCharset();
		ContentType contentType = ContentType.create(CONTENT_TYPE, Charset.forName(charset));
		HttpEntity httpEntity = new StringEntity(JSON.toJSONString(bodyObject), contentType);
		return doPostEntity(sqltoyContext, nosqlConfig, esConfig, httpEntity);
	}

	/**
	 * 发送请求体并解析响应(统一rest5-client与直连httpclient两条路径)
	 *
	 * @param sqltoyContext
	 * @param nosqlConfig
	 * @param esConfig
	 * @param httpEntity
	 * @return
	 * @throws Exception
	 */
	private static JSONObject doPostEntity(SqlToyContext sqltoyContext, NoSqlConfigModel nosqlConfig,
			ElasticEndpoint esConfig, HttpEntity httpEntity) throws Exception {
		String realUrl;
		// 返回结果
		String result = null;
		// 使用elastic rest client(默认)
		if (esConfig.getRestClient() != null) {
			realUrl = wrapPath(esConfig, nosqlConfig);
			if (sqltoyContext.isDebug()) {
				logger.debug("esRestClient execution:URL=[{}],Path={}", esConfig.getUrl(), realUrl);
			}
			// 默认采用post请求
			// 多节点客户端自带base地址,请求只需相对路径(修复多地址url直接拼接产生非法uri的问题)
			Rest5Client restClient = esConfig.getRestClient();
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
					// 凭据提供器(AuthScope在httpclient5.6中移除了ANY常量,null/-1即为任意host/port)
					BasicCredentialsProvider credsProvider = new BasicCredentialsProvider();
					credsProvider.setCredentials(new AuthScope(null, -1),
							// 认证用户名和密码
							new UsernamePasswordCredentials(esConfig.getUsername(),
									esConfig.getPassword().toCharArray()));
					client = createHttpClient(getRequestConfig(nosqlConfig), getConnectionConfig(nosqlConfig),
							credsProvider);
				} else {
					client = createHttpClient(getRequestConfig(nosqlConfig), getConnectionConfig(nosqlConfig), null);
				}
				result = client.execute(httpPost, new HttpClientResponseHandler<String>() {
					@Override
					public String handleResponse(ClassicHttpResponse response) throws HttpException, IOException {
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
	public static JSONObject parseElasticResult(String result) {
		// 将结果转换为JSON对象
		JSONObject json = JSON.parseObject(result);
		// 存在错误:error结构随版本/异常类型变化,root_cause可能缺失,error本身可能是字符串,
		// 防御式提取避免NPE掩盖真实错误信息
		if (json.containsKey("error")) {
			Object errorObj = json.get("error");
			String errorMessage;
			if (errorObj instanceof JSONObject) {
				JSONObject errorJson = (JSONObject) errorObj;
				JSONArray rootCause = errorJson.getJSONArray("root_cause");
				if (rootCause != null && !rootCause.isEmpty()) {
					errorMessage = JSON.toJSONString(rootCause.get(0));
				} else if (errorJson.getString("reason") != null) {
					errorMessage = errorJson.getString("reason");
				} else {
					errorMessage = JSON.toJSONString(errorJson);
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
	 * 重新组织url(直连模式:节点地址+查询路径)
	 *
	 * @param esConfig
	 * @param nosqlConfig
	 * @return
	 */
	private static String wrapUrl(ElasticEndpoint esConfig, NoSqlConfigModel nosqlConfig) {
		// 多地址(逗号分隔)时取首个节点,与initRestClient一致先归一化全角分隔符
		String url = esConfig.getUrl().replaceAll("\\；", ";").replaceAll("\\，", ",").replaceAll("\\;", ",")
				.split("\\,")[0].trim();
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
	 * 组织rest5-client请求的相对路径(客户端构建时已携带全部节点base地址,请求只需路径)
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
		if (nosqlConfig != null
				&& (nosqlConfig.getRequestTimeout() != 30000 || nosqlConfig.getSocketTimeout() != 180000)) {
			return RequestConfig.custom()
					.setConnectionRequestTimeout(Timeout.ofMilliseconds(nosqlConfig.getRequestTimeout()))
					.setResponseTimeout(Timeout.ofMilliseconds(nosqlConfig.getSocketTimeout())).build();
		} else {
			return requestConfig;
		}
	}

	// RequestConfig.setConnectTimeout已废弃,连接超时改由ConnectionConfig配置
	private static ConnectionConfig getConnectionConfig(NoSqlConfigModel nosqlConfig) {
		if (nosqlConfig != null && nosqlConfig.getConnectTimeout() != 10000) {
			return ConnectionConfig.custom().setConnectTimeout(Timeout.ofMilliseconds(nosqlConfig.getConnectTimeout()))
					.build();
		}
		return connectionConfig;
	}

	// 连接管理器未设为shared,随client关闭时一并释放
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
