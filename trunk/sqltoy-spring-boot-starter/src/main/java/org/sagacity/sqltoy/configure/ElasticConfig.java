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
	 * 相对路径
	 */
	private String sqlPath;
	
	/**
	 * 证书类型
	 */
	private String truststoreType;

	/**
	 * 证书文件
	 */
	private String truststoreFile;

	/**
	 * 证书秘钥
	 */
	private String truststorePassword;

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

	public String getSqlPath() {
		return sqlPath;
	}

	public void setSqlPath(String sqlPath) {
		this.sqlPath = sqlPath;
	}

	/**
	 * @return the truststoreFile
	 */
	public String getTruststoreFile() {
		return truststoreFile;
	}

	/**
	 * @param truststoreFile the truststoreFile to set
	 */
	public void setTruststoreFile(String truststoreFile) {
		this.truststoreFile = truststoreFile;
	}

	/**
	 * @return the truststorePassword
	 */
	public String getTruststorePassword() {
		return truststorePassword;
	}

	/**
	 * @param truststorePassword the truststorePassword to set
	 */
	public void setTruststorePassword(String truststorePassword) {
		this.truststorePassword = truststorePassword;
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

	/**
	 * @return the truststoreType
	 */
	public String getTruststoreType() {
		return truststoreType;
	}

	/**
	 * @param truststoreType the truststoreType to set
	 */
	public void setTruststoreType(String truststoreType) {
		this.truststoreType = truststoreType;
	}

}
