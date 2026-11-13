package org.sagacity.sqltoy.config.model;

import java.io.InputStream;
import java.io.Serializable;
import java.net.MalformedURLException;
import java.net.URL;
import java.security.KeyStore;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

import javax.net.ssl.SSLContext;

import org.apache.http.HttpHost;
import org.apache.http.auth.AuthScope;
import org.apache.http.auth.UsernamePasswordCredentials;
import org.apache.http.client.CredentialsProvider;
import org.apache.http.client.config.RequestConfig;
import org.apache.http.conn.ssl.TrustSelfSignedStrategy;
import org.apache.http.impl.client.BasicCredentialsProvider;
import org.apache.http.impl.nio.client.HttpAsyncClientBuilder;
import org.apache.http.ssl.SSLContextBuilder;
import org.apache.http.ssl.SSLContexts;
import org.elasticsearch.client.RestClient;
import org.elasticsearch.client.RestClientBuilder;
import org.sagacity.sqltoy.utils.FileUtil;
import org.sagacity.sqltoy.utils.StringUtil;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * @project sagacity-sqltoy
 * @description es配置
 *              作为超时与字符集的默认值:语句(eql)上未显式配置时采用本配置;本配置同时用于构建
 *              rest客户端(单地址与多地址统一使用rest客户端)
 * @author zhongxuchen
 * @version v1.0,Date:2018-02-05
 */
public class ElasticEndpoint implements Serializable {
	private final static Logger logger = LoggerFactory.getLogger(ElasticEndpoint.class);

	private static final long serialVersionUID = 7850474153384016421L;

	/**
	 * 搜索端点尾缀
	 */
	private final static String SEARCH_SUFFIX = "/_search";

	private volatile RestClient restClient;

	/**
	 * 语句级超时对应的rest客户端:客户端超时只能在构建时确定,按超时组合缓存
	 */
	private Map<String, RestClient> restClients = new ConcurrentHashMap<String, RestClient>();

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
	 * 编码格式(作为请求/响应的默认字符集,语句级配置优先)
	 */
	private String charset = NoSqlConfigModel.DEFAULT_CHARSET;

	/**
	 * 从连接池获取连接的等待上限,默认30秒(不是请求超时)
	 */
	private int requestTimeout = NoSqlConfigModel.DEFAULT_REQUEST_TIMEOUT;

	/**
	 * 建立连接超时,默认10秒
	 */
	private int connectTimeout = NoSqlConfigModel.DEFAULT_CONNECT_TIMEOUT;

	/**
	 * 响应超时(等待服务端返回数据),默认3分钟
	 */
	private int socketTimeout = NoSqlConfigModel.DEFAULT_SOCKET_TIMEOUT;

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
	 * 解析配置的节点地址列表:兼容全角分隔符,去除首尾空白与空白项。
	 * 多地址用于构建rest客户端,单地址同样使用rest客户端(默认节点)
	 *
	 * @return 节点地址数组,未配置时返回空数组
	 */
	public String[] getUrlList() {
		if (StringUtil.isBlank(url)) {
			return new String[0];
		}
		String[] urlAry = url.replaceAll("\\；", ";").replaceAll("\\，", ",").replaceAll("\\;", ",").split("\\,");
		List<String> result = new ArrayList<String>(urlAry.length);
		for (String urlStr : urlAry) {
			if (StringUtil.isNotBlank(urlStr)) {
				result.add(urlStr.trim());
			}
		}
		return result.toArray(new String[0]);
	}

	/**
	 * 取URL中的base path作为rest客户端的pathPrefix:如多节点部署在网关子路径下(http://host:9200/es)。
	 * 若配置的url本身已指向sql端点或搜索端点(如http://host:9200/_sql、http://host:9200/idx/_search),
	 * 则去掉该尾段,交由请求路径拼接,避免重复
	 *
	 * @return base path(以"/"开头,无尾斜杠);无base path返回空串
	 */
	public String getPathPrefix() {
		String[] urls = getUrlList();
		if (urls.length == 0) {
			return "";
		}
		String path;
		try {
			path = new java.net.URL(urls[0]).getPath();
		} catch (MalformedURLException e) {
			logger.error("elastic url is malformed:[" + urls[0] + "]", e);
			return "";
		}
		if (StringUtil.isBlank(path) || "/".equals(path)) {
			return "";
		}
		path = trimTrailingSlash(path);
		String sqlPath = StringUtil.isBlank(this.sqlPath) ? "_sql" : this.sqlPath;
		String lowerPath = path.toLowerCase(Locale.ROOT);
		String sqlSuffix = "/" + sqlPath.toLowerCase(Locale.ROOT);
		if (lowerPath.equals(sqlSuffix)) {
			return "";
		}
		if (lowerPath.endsWith(sqlSuffix)) {
			path = path.substring(0, path.length() - sqlSuffix.length());
		} else if (lowerPath.endsWith(SEARCH_SUFFIX)) {
			path = path.substring(0, path.length() - SEARCH_SUFFIX.length());
		}
		path = trimTrailingSlash(path);
		return "/".equals(path) ? "" : path;
	}

	private static String trimTrailingSlash(String path) {
		String result = path;
		while (result.length() > 1 && result.endsWith("/")) {
			result = result.substring(0, result.length() - 1);
		}
		return result;
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
	public RestClient getRestClient() {
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

	/**
	 * 关闭rest客户端(默认客户端与语句级超时客户端),资源释放入口
	 */
	public void close() {
		closeRestClient();
		for (RestClient client : restClients.values()) {
			try {
				client.close();
			} catch (Exception e) {
				logger.error("close rest client failed", e);
			}
		}
		restClients.clear();
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

	/**
	 * 初始化rest客户端(单地址同样使用rest客户端,base path通过pathPrefix支持);
	 * 方法加锁:自定义url端点由查询线程惰性初始化,避免并发重复构建
	 */
	public synchronized void initRestClient() {
		if (StringUtil.isBlank(this.getUrl())) {
			return;
		}
		if (restClient != null) {
			return;
		}
		restClient = buildRestClient(this.requestTimeout, this.connectTimeout, this.socketTimeout);
	}

	/**
	 * 获取rest客户端:与endpoint默认超时一致时复用默认客户端;语句级超时则按
	 * (requestTimeout,connectTimeout,socketTimeout)组合单独构建——
	 * 客户端的超时只能在构建时确定,无法逐请求生效
	 *
	 * @param requestTimeout 从连接池获取连接的等待上限(毫秒)
	 * @param connectTimeout 建连超时(毫秒)
	 * @param socketTimeout  响应超时(毫秒)
	 * @return rest客户端;地址非法或构建失败返回null
	 */
	public RestClient getRestClient(int requestTimeout, int connectTimeout, int socketTimeout) {
		if (restClient == null) {
			// 自定义url端点未在启动时初始化,首次查询时惰性构建
			initRestClient();
		}
		if (restClient == null) {
			return null;
		}
		if (requestTimeout == this.requestTimeout && connectTimeout == this.connectTimeout
				&& socketTimeout == this.socketTimeout) {
			return restClient;
		}
		final String key = requestTimeout + "_" + connectTimeout + "_" + socketTimeout;
		return restClients.computeIfAbsent(key,
				k -> buildRestClient(requestTimeout, connectTimeout, socketTimeout));
	}

	/**
	 * 按指定超时构建rest客户端,构建失败返回null
	 *
	 * @param requestTimeout 从连接池获取连接的等待上限(毫秒)
	 * @param connectTimeout 建连超时(毫秒)
	 * @param socketTimeout  响应超时(毫秒)
	 * @return
	 */
	private RestClient buildRestClient(int requestTimeout, int connectTimeout, int socketTimeout) {
		String[] urls = getUrlList();
		List<HttpHost> hosts = new ArrayList<HttpHost>(urls.length);
		for (String urlStr : urls) {
			try {
				URL url = new java.net.URL(urlStr);
				// httpclient4的HttpHost构造参数顺序为(hostname, port, protocol)
				hosts.add(new HttpHost(url.getHost(), url.getPort(), url.getProtocol()));
			} catch (MalformedURLException e) {
				logger.error("initRestClient method execution failed", e);
			}
		}
		if (hosts.isEmpty()) {
			return null;
		}
		HttpHost[] hostAry = new HttpHost[hosts.size()];
		hosts.toArray(hostAry);
		RestClientBuilder builder = RestClient.builder(hostAry);
		// base path(网关子路径)由pathPrefix承载,请求只传相对路径
		String pathPrefix = getPathPrefix();
		if (StringUtil.isNotBlank(pathPrefix)) {
			builder.setPathPrefix(pathPrefix);
		}
		final CredentialsProvider credsProvider = new BasicCredentialsProvider();
		final boolean hasCrede = (StringUtil.isNotBlank(this.getUsername())
				&& StringUtil.isNotBlank(getPassword())) ? true : false;
		// 是否ssl证书模式
		final boolean hasSsl = StringUtil.isNotBlank(this.keyStore);
		// 凭据提供器
		if (hasCrede) {
			credsProvider.setCredentials(AuthScope.ANY,
					// 认证用户名和密码
					new UsernamePasswordCredentials(getUsername(), getPassword()));
		}

		SSLContextBuilder sslBuilder = null;
		try {
			if (hasSsl) {
				KeyStore truststore = KeyStore.getInstance(
						StringUtil.isBlank(keyStoreType) ? KeyStore.getDefaultType() : keyStoreType);
				// KeyStore.load(null)不抛错而是初始化为空keystore,静默导致SSL握手失败难以定位
				try (InputStream keyStoreStream = FileUtil.getFileInputStream(keyStore)) {
					if (keyStoreStream == null) {
						throw new IllegalArgumentException("elastic keystore file not found:[" + keyStore + "]");
					}
					truststore.load(keyStoreStream, (keyStorePass == null) ? null : keyStorePass.toCharArray());
				}
				sslBuilder = SSLContexts.custom().loadTrustMaterial(truststore,
						keyStoreSelfSign ? new TrustSelfSignedStrategy() : null);
			}
			final SSLContext sslContext = (sslBuilder == null) ? null : sslBuilder.build();
			final boolean disableAuthCaching = !authCaching;
			// hc4的RequestConfig统一承载从池取连接/建连/响应三类超时
			final RequestConfig requestConfig = RequestConfig.custom()
					.setConnectionRequestTimeout(requestTimeout).setConnectTimeout(connectTimeout)
					.setSocketTimeout(socketTimeout).build();
			builder.setHttpClientConfigCallback(new RestClientBuilder.HttpClientConfigCallback() {
				@Override
				public HttpAsyncClientBuilder customizeHttpClient(HttpAsyncClientBuilder httpClientBuilder) {
					httpClientBuilder.setDefaultRequestConfig(requestConfig);
					// 禁用抢占式身份验证
					if (disableAuthCaching) {
						httpClientBuilder.disableAuthCaching();
					}
					// 用户名密码
					if (hasCrede) {
						httpClientBuilder.setDefaultCredentialsProvider(credsProvider);
					}
					// 证书
					if (hasSsl) {
						httpClientBuilder.setSSLContext(sslContext);
					}
					return httpClientBuilder;
				}
			});
			return builder.build();
		} catch (Exception e) {
			logger.error("customizeHttpClient method execution failed", e);
			return null;
		}
	}

}
