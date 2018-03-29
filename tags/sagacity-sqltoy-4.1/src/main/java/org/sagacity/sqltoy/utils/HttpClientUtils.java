/**
 * 
 */
package org.sagacity.sqltoy.utils;

import java.util.Collections;

import org.apache.http.HttpEntity;
import org.apache.http.HttpResponse;
import org.apache.http.auth.AuthScope;
import org.apache.http.auth.UsernamePasswordCredentials;
import org.apache.http.client.CredentialsProvider;
import org.apache.http.client.config.RequestConfig;
import org.apache.http.client.methods.HttpPost;
import org.apache.http.entity.StringEntity;
import org.apache.http.impl.client.BasicCredentialsProvider;
import org.apache.http.impl.client.CloseableHttpClient;
import org.apache.http.impl.client.HttpClients;
import org.apache.http.util.EntityUtils;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.elasticsearch.client.Response;
import org.sagacity.sqltoy.SqlToyContext;
import org.sagacity.sqltoy.config.model.ElasticEndpoint;
import org.sagacity.sqltoy.config.model.NoSqlConfigModel;

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
	protected final static Logger logger = LogManager.getLogger(HttpClientUtils.class);

	private final static String CHARSET = "UTF-8";

	private final static String SEARCH = "_search";

	private final static String CONTENT_TYPE = "application/json";

	/**
	 * @todo 执行post请求
	 * @param sqltoyContext
	 * @param nosqlConfig
	 * @param url
	 * @param jsonObject
	 * @return
	 * @throws Exception
	 */
	public static JSONObject doPost(SqlToyContext sqltoyContext, NoSqlConfigModel nosqlConfig, Object postValue)
			throws Exception {
		ElasticEndpoint esConfig = sqltoyContext.getElasticConfig(nosqlConfig.getUrl());
		if (esConfig.getUrl() == null)
			throw new Exception("请正确配置sqltoyContext elasticConfigs 指定es的服务地址!");

		String charset = (nosqlConfig.getCharset() == null) ? CHARSET : nosqlConfig.getCharset();
		HttpEntity httpEntity = new StringEntity(
				nosqlConfig.isSqlMode() ? postValue.toString() : JSON.toJSONString(postValue), charset);
		((StringEntity) httpEntity).setContentEncoding(charset);
		((StringEntity) httpEntity).setContentType(CONTENT_TYPE);
		String realUrl;
		// 返回结果
		HttpEntity reponseEntity = null;
		if (esConfig.getRestClient() != null) {
			realUrl = wrapUrl(esConfig.getPath(), nosqlConfig);
			if (sqltoyContext.isDebug())
				logger.debug("esRestClient执行:URL=[{}],Path={},执行的JSON=[{}]", esConfig.getUrl(), realUrl,
						JSON.toJSONString(postValue));
			// 默认采用post请求
			Response response = esConfig.getRestClient().performRequest("POST", realUrl,
					Collections.<String, String>emptyMap(), httpEntity);
			reponseEntity = response.getEntity();
		} else {
			realUrl = wrapUrl(esConfig.getUrl(), nosqlConfig);
			HttpPost httpPost = new HttpPost(realUrl);
			if (sqltoyContext.isDebug())
				logger.debug("httpClient执行URL=[{}],执行的JSON=[{}]", realUrl, JSON.toJSONString(postValue));
			httpPost.setEntity(httpEntity);

			// 设置connection是否自动关闭
			httpPost.setHeader("Connection", "close");
			// 自定义超时
			if (nosqlConfig.getRequestTimeout() != 30000 || nosqlConfig.getConnectTimeout() != 10000
					|| nosqlConfig.getSocketTimeout() != 180000) {
				httpPost.setConfig(RequestConfig.custom().setConnectionRequestTimeout(nosqlConfig.getRequestTimeout())
						.setConnectTimeout(nosqlConfig.getConnectTimeout())
						.setSocketTimeout(nosqlConfig.getSocketTimeout()).build());
			} else
				httpPost.setConfig(requestConfig);
			CloseableHttpClient client = null;
			if (StringUtil.isNotBlank(esConfig.getUsername()) && StringUtil.isNotBlank(esConfig.getPassword())) {
				// 凭据提供器
				CredentialsProvider credsProvider = new BasicCredentialsProvider();
				credsProvider.setCredentials(AuthScope.ANY,
						// 认证用户名和密码
						new UsernamePasswordCredentials(esConfig.getUsername(), esConfig.getPassword()));
				client = HttpClients.custom().setDefaultCredentialsProvider(credsProvider).build();
			} else
				client = HttpClients.createDefault();
			HttpResponse response = client.execute(httpPost);
			reponseEntity = response.getEntity();
		}
		String result = null;
		if (reponseEntity != null) {
			result = EntityUtils.toString(reponseEntity, nosqlConfig.getCharset());
			if (sqltoyContext.isDebug())
				logger.debug("result={}", result);
		}
		if (StringUtil.isBlank(result))
			return null;
		// 将结果转换为JSON对象
		JSONObject json = JSON.parseObject(result);
		// 存在错误
		if (json.containsKey("error")) {
			String errorMessage = JSON.toJSONString(json.getJSONObject("error").getJSONArray("root_cause").get(0));
			logger.error("elastic查询失败,URL:[{}],错误信息:[{}]", nosqlConfig.getUrl(), errorMessage);
			throw new Exception("ElasticSearch查询失败,错误信息:" + errorMessage);
		}
		return json;
	}

	/**
	 * @todo 重新组织url
	 * @param nosqlConfig
	 * @return
	 */
	private static String wrapUrl(String url, NoSqlConfigModel nosqlConfig) {
		if (nosqlConfig.isSqlMode()) {
			url = url.concat(url.endsWith("/") ? "_sql" : "/_sql");
		} else {
			if (StringUtil.isNotBlank(nosqlConfig.getIndex()))
				url = url.concat(url.endsWith("/") ? "" : "/").concat(nosqlConfig.getIndex());
			if (StringUtil.isNotBlank(nosqlConfig.getType()))
				url = url.concat(url.endsWith("/") ? "" : "/").concat(nosqlConfig.getType());
			if (!url.toLowerCase().endsWith(SEARCH)) {
				url = url.concat(url.endsWith("/") ? "" : "/").concat(SEARCH);
			}
		}
		return url;
	}
}
