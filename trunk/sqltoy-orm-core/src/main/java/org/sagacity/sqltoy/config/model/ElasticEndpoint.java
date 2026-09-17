package org.sagacity.sqltoy.config.model;

import java.io.InputStream;
import java.io.Serializable;
import java.net.MalformedURLException;
import java.net.URL;
import java.security.KeyStore;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

import javax.net.ssl.SSLContext;

import org.apache.hc.client5.http.auth.AuthScope;
import org.apache.hc.client5.http.auth.UsernamePasswordCredentials;
import org.apache.hc.client5.http.impl.auth.BasicCredentialsProvider;
import org.apache.hc.core5.http.HttpHost;
import org.apache.hc.core5.ssl.SSLContextBuilder;
import org.apache.hc.core5.ssl.SSLContexts;
import org.apache.hc.core5.util.Timeout;
import org.sagacity.sqltoy.utils.FileUtil;
import org.sagacity.sqltoy.utils.StringUtil;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import co.elastic.clients.transport.rest5_client.low_level.Rest5Client;
import co.elastic.clients.transport.rest5_client.low_level.Rest5ClientBuilder;

/**
 * @project sagacity-sqltoy
 * @description es配置
 * @author zhongxuchen
 * @version v1.0,Date:2018-02-05
 */
public class ElasticEndpoint implements Serializable {
	private final static Logger logger = LoggerFactory.getLogger(ElasticEndpoint.class);

	private static final long serialVersionUID = 7850474153384016421L;

	private Rest5Client restClient;

	public ElasticEndpoint(String url) {
		this.url = url;
	}

	public ElasticEndpoint(String url, String sqlPath) {
		this.url = url;
		if (StringUtil.isNotBlank(sqlPath)) {
			if (sqlPath.startsWith("/")) {
				this.sqlPath = sqlPath.substring(1);
			} else {
				this.sqlPath = sqlPath;
			}

			String sqlLowPath = this.sqlPath.toLowerCase(Locale.ROOT);
			// elasticsearch原生sql路径为_sql
			if (sqlLowPath.startsWith("_sql") || sqlLowPath.startsWith("_xpack/sql")) {
				this.nativeSql = true;
			} else {
				this.nativeSql = false;
			}
		}
	}

	// 6.3+原生: _xpack/sql
	// 7.x 原生:_sql
	// elasticsearch-sql7.4 /_sql
	// elasticsearch-sql7.5+ /_nlpcn/sql
	// elasticsearch-sql7.9.3 之后不再维护,启用_opendistro/_sql
	private String sqlPath = "_sql";

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
	 * 是否禁止抢占式身份认证
	 */
	private boolean authCaching = true;

	/**
	 * 证书类型
	 */
	private String keyStoreType;

	/**
	 * 证书文件
	 */
	private String keyStore;

	/**
	 * 证书秘钥
	 */
	private String keyStorePass;

	/**
	 * 证书是否自签名
	 */
	private boolean keyStoreSelfSign = true;

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
	 * 是否原生sql
	 */
	private boolean nativeSql = true;

	public String getSqlPath() {
		return sqlPath;
	}

	public void setSqlPath(String sqlPath) {
		if (StringUtil.isNotBlank(sqlPath)) {
			if (sqlPath.startsWith("/")) {
				this.sqlPath = sqlPath.substring(1);
			} else {
				this.sqlPath = sqlPath;
			}
			String sqlLowPath = this.sqlPath.toLowerCase(Locale.ROOT);
			// elasticsearch原生sql路径为_sql
			if (sqlLowPath.startsWith("_sql") || sqlLowPath.startsWith("_xpack/sql")) {
				this.nativeSql = true;
			} else {
				this.nativeSql = false;
			}
		}
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
	public Rest5Client getRestClient() {
		return restClient;
	}

	public void closeRestClient() {
		if (restClient != null) {
			try {
				restClient.close();
			} catch (Exception e) {
				logger.error("closeRestClient method execution failed", e);
			}
			restClient = null;
		}
	}

	public boolean isNativeSql() {
		return nativeSql;
	}

	/**
	 * @return the keyStoreType
	 */
	public String getKeyStoreType() {
		return keyStoreType;
	}

	/**
	 * @param keyStoreType the keyStoreType to set
	 */
	public void setKeyStoreType(String keyStoreType) {
		this.keyStoreType = keyStoreType;
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
	 * @return the keyStorePass
	 */
	public String getKeyStorePass() {
		return keyStorePass;
	}

	/**
	 * @param keyStorePass the keyStorePass to set
	 */
	public void setKeyStorePass(String keyStorePass) {
		this.keyStorePass = keyStorePass;
	}

	/**
	 * @return the keyStoreSelfSign
	 */
	public boolean isKeyStoreSelfSign() {
		return keyStoreSelfSign;
	}

	/**
	 * @param keyStoreSelfSign the keyStoreSelfSign to set
	 */
	public void setKeyStoreSelfSign(boolean keyStoreSelfSign) {
		this.keyStoreSelfSign = keyStoreSelfSign;
	}

	/**
	 * @return the authCaching
	 */
	public boolean isAuthCaching() {
		return authCaching;
	}

	/**
	 * @param authCaching the authCaching to set
	 */
	public void setAuthCaching(boolean authCaching) {
		this.authCaching = authCaching;
	}

	public void initRestClient() {
		if (StringUtil.isBlank(this.getUrl())) {
			return;
		}
		if (restClient == null) {
			// 替换全角字符
			String[] urls = this.getUrl().replaceAll("\\；", ";").replaceAll("\\，", ",").replaceAll("\\;", ",")
					.split("\\,");
			// 当为单一地址时使用httpclient直接调用
			if (urls.length < 2) {
				return;
			}
			List<HttpHost> hosts = new ArrayList<HttpHost>();
			for (String urlStr : urls) {
				try {
					if (StringUtil.isNotBlank(urlStr)) {
						URL url = new java.net.URL(urlStr.trim());
						// httpclient5的HttpHost构造参数顺序为(scheme, hostname, port)
						hosts.add(new HttpHost(url.getProtocol(), url.getHost(), url.getPort()));
					}
				} catch (MalformedURLException e) {
					logger.error("initRestClient method execution failed", e);
				}
			}
			if (!hosts.isEmpty()) {
				HttpHost[] hostAry = new HttpHost[hosts.size()];
				hosts.toArray(hostAry);
				Rest5ClientBuilder builder = Rest5Client.builder(hostAry);
				// 凭据提供器(AuthScope在httpclient5.6中移除了ANY常量,null/-1即为任意host/port)
				final BasicCredentialsProvider credsProvider = new BasicCredentialsProvider();
				final boolean hasCrede = (StringUtil.isNotBlank(this.getUsername())
						&& StringUtil.isNotBlank(getPassword())) ? true : false;
				// 是否ssl证书模式
				final boolean hasSsl = StringUtil.isNotBlank(this.keyStore);
				// 凭据提供器(AuthScope在httpclient5.6中移除了ANY常量,null/-1即为任意host/port)
				if (hasCrede) {
					credsProvider.setCredentials(new AuthScope(null, -1),
							// 认证用户名和密码
							new UsernamePasswordCredentials(getUsername(), getPassword().toCharArray()));
				}

				SSLContextBuilder sslBuilder = null;
				try {
					if (hasSsl) {
						KeyStore truststore = KeyStore.getInstance(
								StringUtil.isBlank(keyStoreType) ? KeyStore.getDefaultType() : keyStoreType);
						// KeyStore.load(null)不抛错而是初始化为空keystore,静默导致SSL握手失败难以定位
						try (InputStream keyStoreStream = FileUtil.getFileInputStream(keyStore)) {
							if (keyStoreStream == null) {
								throw new IllegalArgumentException(
										"elastic keystore file not found:[" + keyStore + "]");
							}
							truststore.load(keyStoreStream, (keyStorePass == null) ? null : keyStorePass.toCharArray());
						}
						// keyStoreSelfSign=true时信任自签名证书:TrustSelfSignedStrategy已被废弃,
						// 等价实现为信任单张证书链
						sslBuilder = SSLContexts.custom().loadTrustMaterial(truststore,
								keyStoreSelfSign ? (certs, authType) -> certs.length == 1 : null);
					}
					final SSLContext sslContext = (sslBuilder == null) ? null : sslBuilder.build();
					final boolean disableAuthCaching = !authCaching;
					// 请求超时配置(socketTimeout对应httpclient5的响应超时)
					// RequestConfig.setConnectTimeout已废弃,连接超时由ConnectionConfig配置
					builder.setRequestConfigCallback(requestConfig -> requestConfig
							.setConnectionRequestTimeout(Timeout.ofMilliseconds(this.requestTimeout))
							.setResponseTimeout(Timeout.ofMilliseconds(this.socketTimeout)));
					builder.setConnectionConfigCallback(connectionConfig -> connectionConfig
							.setConnectTimeout(Timeout.ofMilliseconds(this.connectTimeout)));
					builder.setHttpClientConfigCallback(httpClientBuilder -> {
						// 禁用抢占式身份验证
						if (disableAuthCaching) {
							httpClientBuilder.disableAuthCaching();
						}
						// 用户名密码
						if (hasCrede) {
							httpClientBuilder.setDefaultCredentialsProvider(credsProvider);
						}
					});
					// 证书
					if (hasSsl) {
						builder.setSSLContext(sslContext);
					}
					restClient = builder.build();
				} catch (Exception e) {
					logger.error("customizeHttpClient method execution failed", e);
				}
			}
		}
	}

}
