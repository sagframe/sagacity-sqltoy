/**
 * 
 */
package org.sagacity.sqltoy.utils;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import org.apache.http.HttpEntity;
import org.apache.http.HttpResponse;
import org.apache.http.NameValuePair;
import org.apache.http.auth.AuthScope;
import org.apache.http.auth.UsernamePasswordCredentials;
import org.apache.http.client.CredentialsProvider;
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
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.alibaba.fastjson.JSON;
import com.alibaba.fastjson.JSONObject;

/**
 * @project sagacity-sqltoy4.1
 * @description 提供基于http请求的工具类
 * @author chenrenfei <a href="mailto:zhongxuchen@gmail.com">联系作者</a>
 * @version id:HttpClientUtils.java,Revision:v1.0,Date:2018年1月7日
 */
public class HttpClientUtils {
	/**
	 * 请求配置
	 */
	private final static RequestConfig requestConfig = RequestConfig.custom().setConnectionRequestTimeout(30000)
			.setConnectTimeout(10000).setSocketTimeout(180000).build();

	/**
	 * 定义全局日志
	 */
	protected final static Logger logger = LoggerFactory.getLogger(HttpClientUtils.class);

	private final static String CHARSET = "UTF-8";

	private final static String SEARCH = "_search";

	private final static String CONTENT_TYPE = "application/json";

	private final static String POST = "POST";

	public static String doPost(SqlToyContext sqltoyContext, final String url, String username, String password,
			String paramName, String paramValue) throws Exception {
		HttpPost httpPost = new HttpPost(url);
		// 设置connection是否自动关闭
		httpPost.setHeader("Connection", "close");
		httpPost.setConfig(requestConfig);
		CloseableHttpClient client = null;
		try {
			if (StringUtil.isNotBlank(username) && StringUtil.isNotBlank(password)) {
				// 凭据提供器
				CredentialsProvider credsProvider = new BasicCredentialsProvider();
				credsProvider.setCredentials(AuthScope.ANY,
						// 认证用户名和密码
						new UsernamePasswordCredentials(username, password));
				client = HttpClients.custom().setDefaultCredentialsProvider(credsProvider).build();
			} else {
				client = HttpClients.createDefault();
			}
			if (paramValue != null) {
				List<NameValuePair> nvps = new ArrayList<NameValuePair>();
				nvps.add(new BasicNameValuePair(paramName, paramValue));
				HttpEntity httpEntity = new UrlEncodedFormEntity(nvps, CHARSET);
				((UrlEncodedFormEntity) httpEntity).setContentType(CONTENT_TYPE);
				httpPost.setEntity(httpEntity);
			}
			HttpResponse response = client.execute(httpPost);
			// 返回结果
			HttpEntity reponseEntity = response.getEntity();
			if (reponseEntity != null) {
				return EntityUtils.toString(reponseEntity, CHARSET);
			}
		} catch (Exception e) {
			throw e;
		}
		return null;
	}

	/**
	 * @todo 执行post请求
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
			throw new IllegalArgumentException("请正确配置sqltoyContext elasticConfigs 指定es的服务地址!");
		}
		String charset = (nosqlConfig.getCharset() == null) ? CHARSET : nosqlConfig.getCharset();
		HttpEntity httpEntity = null;
		// sql 模式
		if (nosqlConfig.isSqlMode()) {
			// 6.3.x 版本支持xpack sql查询
			if (esConfig.isNativeSql()) {
				Map<String, String> map = new HashMap<String, String>();
				map.put("query", postValue.toString());
				httpEntity = new StringEntity(JSON.toJSONString(map), charset);
			} else {
				httpEntity = new StringEntity(postValue.toString(), charset);
			}
		} else {
			httpEntity = new StringEntity(JSON.toJSONString(postValue), charset);
		}
		((StringEntity) httpEntity).setContentEncoding(charset);
		((StringEntity) httpEntity).setContentType(CONTENT_TYPE);
		String realUrl;
		// 返回结果
		HttpEntity reponseEntity = null;
		// 使用elastic rest client(默认)
		if (esConfig.getRestClient() != null) {
			realUrl = wrapUrl(esConfig, nosqlConfig);
			if (sqltoyContext.isDebug()) {
				logger.debug("esRestClient执行:URL=[{}],Path={},执行的JSON=[{}]", esConfig.getUrl(), realUrl,
						JSON.toJSONString(postValue));
			}
			// 默认采用post请求
			RestClient restClient = null;
			try {
				restClient = esConfig.getRestClient();
				Request request = new Request(POST, realUrl);
				request.setEntity(httpEntity);
				Response response = restClient.performRequest(request);
				reponseEntity = response.getEntity();
			} catch (Exception e) {
				throw e;
			} finally {
				if (restClient != null) {
					restClient.close();
				}
			}
		} // 组织httpclient模式调用(此种模式不推荐使用)
		else {
			realUrl = wrapUrl(esConfig, nosqlConfig);
			HttpPost httpPost = new HttpPost(realUrl);
			if (sqltoyContext.isDebug()) {
				logger.debug("httpClient执行URL=[{}],执行的JSON=[{}]", realUrl, JSON.toJSONString(postValue));
			}
			httpPost.setEntity(httpEntity);

			// 设置connection是否自动关闭
			httpPost.setHeader("Connection", "close");
			// 自定义超时
			if (nosqlConfig.getRequestTimeout() != 30000 || nosqlConfig.getConnectTimeout() != 10000
					|| nosqlConfig.getSocketTimeout() != 180000) {
				httpPost.setConfig(RequestConfig.custom().setConnectionRequestTimeout(nosqlConfig.getRequestTimeout())
						.setConnectTimeout(nosqlConfig.getConnectTimeout())
						.setSocketTimeout(nosqlConfig.getSocketTimeout()).build());
			} else {
				httpPost.setConfig(requestConfig);
			}
			CloseableHttpClient client = null;
			try {
				if (StringUtil.isNotBlank(esConfig.getUsername()) && StringUtil.isNotBlank(esConfig.getPassword())) {
					// 凭据提供器
					CredentialsProvider credsProvider = new BasicCredentialsProvider();
					credsProvider.setCredentials(AuthScope.ANY,
							// 认证用户名和密码
							new UsernamePasswordCredentials(esConfig.getUsername(), esConfig.getPassword()));
					client = HttpClients.custom().setDefaultCredentialsProvider(credsProvider).build();
				} else {
					client = HttpClients.createDefault();
				}
				HttpResponse response = client.execute(httpPost);
				reponseEntity = response.getEntity();
			} catch (Exception e) {
				throw e;
			}
		}
		String result = null;
		if (reponseEntity != null) {
			result = EntityUtils.toString(reponseEntity, nosqlConfig.getCharset());
			if (sqltoyContext.isDebug()) {
				logger.debug("result={}", result);
			}
		}
		if (StringUtil.isBlank(result))
			return null;
		// 将结果转换为JSON对象
		JSONObject json = JSON.parseObject(result);
		// 存在错误
		if (json.containsKey("error")) {
			String errorMessage = JSON.toJSONString(json.getJSONObject("error").getJSONArray("root_cause").get(0));
			logger.error("elastic查询失败,endpoint:[{}],错误信息:[{}]", nosqlConfig.getEndpoint(), errorMessage);
			throw new DataAccessException("ElasticSearch查询失败,错误信息:" + errorMessage);
		}
		return json;
	}

	/**
	 * @todo 重新组织url
	 * @param esConfig
	 * @param nosqlConfig
	 * @return
	 */
	private static String wrapUrl(ElasticEndpoint esConfig, NoSqlConfigModel nosqlConfig) {
		String url = esConfig.getUrl();
		String nativePath = "_xpack/sql";
		String sqlPluginPath = "_sql";
		if (esConfig.getMajorVersion() >= 7) {
			nativePath = "_sql";
			if (esConfig.getMajorVersion() > 7 || esConfig.getMinorVersion() >= 5) {
				sqlPluginPath = "_nlpcn/sql";
			}
		}
		if (nosqlConfig.isSqlMode()) {
			// elasticsearch6.3.x 通过xpack支持sql查询
			// 6.3 /_xpack/sql
			// 7.x /_sql
			// elasticsearch-sql7.4 /_sql
			// elasticsearch-sql7.5+ /_nlpcn/sql
			if (esConfig.isNativeSql()) {
				// 判断url中是否已经包含相应路径
				if (!url.toLowerCase().contains(nativePath)) {
					url = url.concat(url.endsWith("/") ? nativePath : "/".concat(nativePath));
				}
			} else {
				if (!url.toLowerCase().contains(sqlPluginPath)) {
					url = url.concat(url.endsWith("/") ? sqlPluginPath : "/".concat(sqlPluginPath));
				}
			}
		} else {
			if (StringUtil.isNotBlank(nosqlConfig.getIndex())) {
				url = url.concat(url.endsWith("/") ? "" : "/").concat(nosqlConfig.getIndex());
			}
			if (StringUtil.isNotBlank(nosqlConfig.getType())) {
				url = url.concat(url.endsWith("/") ? "" : "/").concat(nosqlConfig.getType());
			}
			if (!url.toLowerCase().endsWith(SEARCH)) {
				url = url.concat(url.endsWith("/") ? "" : "/").concat(SEARCH);
			}
		}
		return url;
	}
}
