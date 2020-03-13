/**
 * 
 */
package org.sagacity.sqltoy.translate.model;

import java.io.Serializable;

/**
 * @project sqltoy-orm
 * @description 翻译配置模型
 * @author renfei.chen <a href="mailto:zhongxuchen@hotmail.com">联系作者</a>
 * @version Revision:v1.0,Date:2013-4-8
 * @modify Date:2020-3-8=10 修改heap\offheap\diskSize的默认策略
 */
public class TranslateConfigModel implements Serializable {
	/**
	 * 
	 */
	private static final long serialVersionUID = 4392516051742079330L;

	/**
	 * sql\service\rest
	 */
	private String type;

	/**
	 * 对应的cacheName
	 */
	private String cache;

	/**
	 * sql语句或sqltoy中的sqlId
	 */
	private String sql;

	/**
	 * 数据库源
	 */
	private String dataSource;

	/**
	 * 自定义的ServiceBean
	 */
	private String service;

	/**
	 * service method
	 */
	private String method;

	/**
	 * rest 情况下的url地址
	 */
	private String url;

	/**
	 * rest 请求安全认证信息
	 */
	private String username;

	private String password;

	/**
	 * 参数属性名称
	 */
	private String[] properties;

	/**
	 * 转换成hash 的key，只有针对sql语句起作用
	 */
	private int keyIndex = 0;

	/**
	 * 过期时长:默认60分钟
	 */
	private int keepAlive = 3600;

	/**
	 * 内存中存放的数量(条)
	 */
	private int heap = 10000;

	/**
	 * 堆外內存(MB)
	 */
	private int offHeap = 0;

	/**
	 * 存储磁盘的大小(MB)
	 */
	private int diskSize = 0;

	/**
	 * @return the keepAlive
	 */
	public int getKeepAlive() {
		return keepAlive;
	}

	/**
	 * @param keepAlive
	 *            the keepAlive to set
	 */
	public void setKeepAlive(int keepAlive) {
		this.keepAlive = keepAlive;
	}

	/**
	 * @return the heap
	 */
	public int getHeap() {
		return heap;
	}

	/**
	 * @param heap
	 *            the heap to set
	 */
	public void setHeap(int heap) {
		this.heap = heap;
	}

	/**
	 * @return the offHeap
	 */
	public int getOffHeap() {
		return offHeap;
	}

	/**
	 * @param offHeap
	 *            the offHeap to set
	 */
	public void setOffHeap(int offHeap) {
		this.offHeap = offHeap;
	}

	/**
	 * @return the cache
	 */
	public String getCache() {
		return cache;
	}

	/**
	 * @param cache
	 *            the cache to set
	 */
	public void setCache(String cache) {
		this.cache = cache;
	}

	/**
	 * @return the sql
	 */
	public String getSql() {
		return sql;
	}

	/**
	 * @param sql
	 *            the sql to set
	 */
	public void setSql(String sql) {
		this.sql = sql;
	}

	/**
	 * @return the service
	 */
	public String getService() {
		return service;
	}

	/**
	 * @param service
	 *            the service to set
	 */
	public void setService(String service) {
		this.service = service;
	}

	/**
	 * @return the method
	 */
	public String getMethod() {
		return method;
	}

	/**
	 * @param method
	 *            the method to set
	 */
	public void setMethod(String method) {
		this.method = method;
	}

	/**
	 * @return the keyIndex
	 */
	public int getKeyIndex() {
		return keyIndex;
	}

	/**
	 * @param keyIndex
	 *            the keyIndex to set
	 */
	public void setKeyIndex(int keyIndex) {
		this.keyIndex = keyIndex;
	}

	/**
	 * @return the dataSource
	 */
	public String getDataSource() {
		return dataSource;
	}

	/**
	 * @param dataSource
	 *            the dataSource to set
	 */
	public void setDataSource(String dataSource) {
		this.dataSource = dataSource;
	}

	/**
	 * @return the type
	 */
	public String getType() {
		return type;
	}

	/**
	 * @param type
	 *            the type to set
	 */
	public void setType(String type) {
		this.type = type;
	}

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

	/**
	 * @return the diskSize
	 */
	public int getDiskSize() {
		return diskSize;
	}

	/**
	 * @param diskSize
	 *            the diskSize to set
	 */
	public void setDiskSize(int diskSize) {
		this.diskSize = diskSize;
	}

	public String[] getProperties() {
		return properties;
	}

	public void setProperties(String[] properties) {
		this.properties = properties;
	}

}
