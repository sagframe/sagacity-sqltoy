/**
 * 
 */
package org.sagacity.quickvo.model;

import java.io.Serializable;

/**
 * @project sagacity-quickvo
 * @description 数据库配置模型
 * @author zhongxuchen <a href="mailto:zhongxuchen@hotmail.com">联系作者</a>
 * @version id:DataSourceModel.java,Revision:v1.0,Date:2017年12月30日
 */
public class DataSourceModel implements Serializable {
	/**
	 * 
	 */
	private static final long serialVersionUID = 4114777290559693850L;

	/**
	 * 数据库地址
	 */
	private String url;

	/**
	 * 数据库驱动
	 */
	private String driver;

	/**
	 * 
	 */
	private String catalog;
	private String schema;
	private String username;
	private String password;

	/**
	 * @return the url
	 */
	public String getUrl() {
		return url;
	}

	/**
	 * @param url
	 *            the url to set
	 */
	public void setUrl(String url) {
		this.url = url;
	}

	/**
	 * @return the driver
	 */
	public String getDriver() {
		return driver;
	}

	/**
	 * @param driver
	 *            the driver to set
	 */
	public void setDriver(String driver) {
		this.driver = driver;
	}

	/**
	 * @return the catalog
	 */
	public String getCatalog() {
		return catalog;
	}

	/**
	 * @param catalog
	 *            the catalog to set
	 */
	public void setCatalog(String catalog) {
		this.catalog = catalog;
	}

	/**
	 * @return the schema
	 */
	public String getSchema() {
		return schema;
	}

	/**
	 * @param schema
	 *            the schema to set
	 */
	public void setSchema(String schema) {
		this.schema = schema;
	}

	/**
	 * @return the username
	 */
	public String getUsername() {
		return username;
	}

	/**
	 * @param username
	 *            the username to set
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
	 * @param password
	 *            the password to set
	 */
	public void setPassword(String password) {
		this.password = password;
	}

}
