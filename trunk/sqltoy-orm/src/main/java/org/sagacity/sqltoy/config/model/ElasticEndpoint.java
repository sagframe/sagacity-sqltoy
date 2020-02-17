/**
 * 
 */
package org.sagacity.sqltoy.config.model;

import java.io.Serializable;
import java.net.MalformedURLException;
import java.net.URL;
import java.nio.charset.Charset;
import java.util.ArrayList;
import java.util.List;

import org.apache.http.HttpHost;
import org.apache.http.auth.AuthScope;
import org.apache.http.auth.UsernamePasswordCredentials;
import org.apache.http.client.CredentialsProvider;
import org.apache.http.client.config.RequestConfig;
import org.apache.http.config.ConnectionConfig;
import org.apache.http.impl.client.BasicCredentialsProvider;
import org.apache.http.impl.nio.client.HttpAsyncClientBuilder;
import org.elasticsearch.client.RestClient;
import org.elasticsearch.client.RestClientBuilder;
import org.sagacity.sqltoy.utils.StringUtil;

/**
 * @project sagacity-sqltoy4.0
 * @description es配置
 * @author chenrenfei <a href="mailto:zhongxuchen@gmail.com">联系作者</a>
 * @version id:ElasticConfig.java,Revision:v1.0,Date:2018年2月5日
 */
public class ElasticEndpoint implements Serializable {

	/**
	 * 
	 */
	private static final long serialVersionUID = 7850474153384016421L;

	private RestClient restClient;

	/**
	 * 默认版本为6.3
	 * 
	 * @param url
	 */
	public ElasticEndpoint(String url) {
		this.url = url;
		this.version = "6.3";
		this.majorVersion = 6;
		this.minorVersion = 3;
	}

	public ElasticEndpoint(String url, String version) {
		this.url = url;
		this.version = StringUtil.isBlank(version) ? "6.3" : version;
		String[] vers = this.version.trim().split("\\.");
		this.majorVersion = Integer.parseInt(vers[0]);
		if (vers.length > 1) {
			this.minorVersion = Integer.parseInt(vers[1]);
		}
	}

	/**
	 * 请求路径(默认为根路径)
	 */
	private String path = "/";

	/**
	 * 配置名称
	 */
	private String id;

	/**
	 * url地址
	 */
	private String url;

	/**
	 * 用户名
	 */
	private String username;

	/**
	 * 密码
	 */
	private String password;

	/**
	 * 证书文件
	 */
	private String keyStore;

	/**
	 * 编码格式
	 */
	private String charset = "UTF-8";

	/**
	 * 请求超时30秒
	 */
	private int requestTimeout = 30000;

	/**
	 * 连接超时10秒
	 */
	private int connectTimeout = 10000;

	/**
	 * 整个连接超时时长,默认3分钟
	 */
	private int socketTimeout = 180000;

	/**
	 * 版本
	 */
	private String version;

	/**
	 * 主版本
	 */
	private int majorVersion = 6;

	/**
	 * 次版本
	 */
	private int minorVersion = 0;

	/**
	 * _xpack sql(6.3.x 版本开始支持sql)
	 */
	private boolean nativeSql = false;

	/**
	 * @return the path
	 */
	public String getPath() {
		return path;
	}

	/**
	 * @param path the path to set
	 */
	public void setPath(String path) {
		this.path = path;
	}

	/**
	 * @return the url
	 */
	public String getUrl() {
		return url;
	}

	/**
	 * @param url the url to set
	 */
	public void setUrl(String url) {
		this.url = url;
	}

	/**
	 * @return the username
	 */
	public String getUsername() {
		return username;
	}

	/**
	 * @param username the username to set
	 */
	public void setUsername(String username) {
		this.username = username;
	}

	/**
	 * @return the password
	 */
	public String getPassword() {
		return password;
	}

	/**
	 * @param password the password to set
	 */
	public void setPassword(String password) {
		this.password = password;
	}

	/**
	 * @return the keyStore
	 */
	public String getKeyStore() {
		return keyStore;
	}

	/**
	 * @param keyStore the keyStore to set
	 */
	public void setKeyStore(String keyStore) {
		this.keyStore = keyStore;
	}

	/**
	 * @return the id
	 */
	public String getId() {
		return id;
	}

	/**
	 * @param id the id to set
	 */
	public void setId(String id) {
		this.id = id;
	}

	/**
	 * @return the charset
	 */
	public String getCharset() {
		return charset;
	}

	/**
	 * @param charset the charset to set
	 */
	public void setCharset(String charset) {
		this.charset = charset;
	}

	/**
	 * @return the requestTimeout
	 */
	public int getRequestTimeout() {
		return requestTimeout;
	}

	/**
	 * @param requestTimeout the requestTimeout to set
	 */
	public void setRequestTimeout(int requestTimeout) {
		this.requestTimeout = requestTimeout;
	}

	/**
	 * @return the connectTimeout
	 */
	public int getConnectTimeout() {
		return connectTimeout;
	}

	/**
	 * @param connectTimeout the connectTimeout to set
	 */
	public void setConnectTimeout(int connectTimeout) {
		this.connectTimeout = connectTimeout;
	}

	/**
	 * @return the socketTimeout
	 */
	public int getSocketTimeout() {
		return socketTimeout;
	}

	/**
	 * @param socketTimeout the socketTimeout to set
	 */
	public void setSocketTimeout(int socketTimeout) {
		this.socketTimeout = socketTimeout;
	}

	/**
	 * @return the restClient
	 */
	public RestClient getRestClient() {
		return restClient;
	}

	/**
	 * @param restClient the restClient to set
	 */
	public void initRestClient() {
		if (StringUtil.isBlank(this.getUrl()))
			return;
		if (restClient == null) {
			// 替换全角字符
			String[] urls = this.getUrl().replaceAll("\\；", ";").replaceAll("\\，", ",").replaceAll("\\;", ",")
					.split("\\,");
			// 当为单一地址时使用httpclient直接调用
			if (urls.length < 2)
				return;
			List<HttpHost> hosts = new ArrayList<HttpHost>();
			for (String urlStr : urls) {
				try {
					if (StringUtil.isNotBlank(urlStr)) {
						URL url = new java.net.URL(urlStr.trim());
						hosts.add(new HttpHost(url.getHost(), url.getPort(), url.getProtocol()));
					}
				} catch (MalformedURLException e) {
					e.printStackTrace();
				}
			}
			if (!hosts.isEmpty()) {
				HttpHost[] hostAry = new HttpHost[hosts.size()];
				hosts.toArray(hostAry);
				RestClientBuilder builder = RestClient.builder(hostAry);
				final ConnectionConfig connectionConfig = ConnectionConfig.custom()
						.setCharset(Charset.forName(this.charset == null ? "UTF-8" : this.charset)).build();
				RequestConfig requestConfig = RequestConfig.custom().setConnectionRequestTimeout(this.requestTimeout)
						.setConnectTimeout(this.connectTimeout).setSocketTimeout(this.socketTimeout).build();
				final CredentialsProvider credsProvider = new BasicCredentialsProvider();
				final boolean hasCrede = (StringUtil.isNotBlank(this.getUsername())
						&& StringUtil.isNotBlank(getPassword())) ? true : false;
				// 凭据提供器
				if (hasCrede) {
					credsProvider.setCredentials(AuthScope.ANY,
							// 认证用户名和密码
							new UsernamePasswordCredentials(getUsername(), getPassword()));
				}
				builder.setHttpClientConfigCallback(new RestClientBuilder.HttpClientConfigCallback() {
					@Override
					public HttpAsyncClientBuilder customizeHttpClient(HttpAsyncClientBuilder httpClientBuilder) {
						httpClientBuilder.setDefaultConnectionConfig(connectionConfig)
								.setDefaultRequestConfig(requestConfig);
						if (hasCrede) {
							httpClientBuilder.setDefaultCredentialsProvider(credsProvider);
						}
						return httpClientBuilder;
					}
				});
				restClient = builder.build();
			}
		}
	}

	/**
	 * @return the nativeSql
	 */
	public boolean isNativeSql() {
		return nativeSql;
	}

	/**
	 * @param nativeSql the nativeSql to set
	 */
	public void setNativeSql(boolean nativeSql) {
		this.nativeSql = nativeSql;
	}

	/**
	 * @return the version
	 */
	public String getVersion() {
		return version;
	}

	/**
	 * @return the majorVersion
	 */
	public int getMajorVersion() {
		return majorVersion;
	}

	/**
	 * @return the minorVersion
	 */
	public int getMinorVersion() {
		return minorVersion;
	}

	public static void main(String args[]) {
		String urlStr = "http://192.168.56.1:9200";
		try {
			URL url = new java.net.URL(urlStr);
			System.err.println("protocol=" + url.getProtocol());
			System.err.println("host=" + url.getHost());
			System.err.println("port=" + url.getPort());
			System.err.println("path=" + url.getPath());
		} catch (MalformedURLException e) {
			e.printStackTrace();
		}
	}
}
