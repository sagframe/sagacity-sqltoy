/**
 * 
 */
package org.sagacity.sqltoy.configure;

import java.io.Serializable;

/**
 * @author zhongxuchen
 * @version v1.0,Date:2020年2月20日
 */
public class ElasticConfig implements Serializable {

	/**
	 * 
	 */
	private static final long serialVersionUID = -5753295867761297803L;

	/**
	 * 连接赋予的id
	 */
	private String id;

	/**
	 * 连接url地址
	 */
	private String url;

	private String username;

	private String password;

	/**
	 * es版本
	 */
	private String version;

	private String path;

	private String keyStore;

	private Integer requestTimeout;

	private Integer connectTimeout;

	private Integer socketTimeout;

	/**
	 * 字符集,默认UTF-8
	 */
	private String charset;

	public String getUrl() {
		return url;
	}

	public void setUrl(String url) {
		this.url = url;
	}

	public String getId() {
		return id;
	}

	public void setId(String id) {
		this.id = id;
	}

	public String getUsername() {
		return username;
	}

	public void setUsername(String username) {
		this.username = username;
	}

	public String getPassword() {
		return password;
	}

	public void setPassword(String password) {
		this.password = password;
	}

	public String getVersion() {
		return version;
	}

	public void setVersion(String version) {
		this.version = version;
	}

	public String getPath() {
		return path;
	}

	public void setPath(String path) {
		this.path = path;
	}

	public String getKeyStore() {
		return keyStore;
	}

	public void setKeyStore(String keyStore) {
		this.keyStore = keyStore;
	}

	public Integer getRequestTimeout() {
		return requestTimeout;
	}

	public void setRequestTimeout(Integer requestTimeout) {
		this.requestTimeout = requestTimeout;
	}

	public Integer getConnectTimeout() {
		return connectTimeout;
	}

	public void setConnectTimeout(Integer connectTimeout) {
		this.connectTimeout = connectTimeout;
	}

	public Integer getSocketTimeout() {
		return socketTimeout;
	}

	public void setSocketTimeout(Integer socketTimeout) {
		this.socketTimeout = socketTimeout;
	}

	public String getCharset() {
		return charset;
	}

	public void setCharset(String charset) {
		this.charset = charset;
	}

}
