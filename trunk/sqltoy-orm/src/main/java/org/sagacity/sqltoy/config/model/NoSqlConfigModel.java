/**
 * 
 */
package org.sagacity.sqltoy.config.model;

import java.io.Serializable;

/**
 * @project sagacity-sqltoy4.1
 * @description 基于mongo或elasticSearch的配置
 * @author chenrenfei <a href="mailto:zhongxuchen@gmail.com">联系作者</a>
 * @version id:NoSqlConfigModel.java,Revision:v1.0,Date:2018年1月3日
 */
public class NoSqlConfigModel implements Serializable {

	/**
	 * 
	 */
	private static final long serialVersionUID = -2126986650751681962L;
	/**
	 * mongo的集合
	 */
	private String collection;

	/**
	 * es的url地址
	 */
	private String endpoint;

	/**
	 * es的索引类型(相当于表)
	 */
	private String type;

	/**
	 * es的索引
	 */
	private String index;

	/**
	 * 请求获取数据超时时间默认为30秒
	 */
	@Deprecated
	private int requestTimeout = 30000;

	// 连接超时时间为10秒
	@Deprecated
	private int connectTimeout = 10000;

	/**
	 * 整个请求超时时长,3分钟
	 */
	@Deprecated
	private int socketTimeout = 180000;

	/**
	 * url请求是字符集类型
	 */
	@Deprecated
	private String charset = "UTF-8";

	/**
	 * 是否有聚合查询
	 */
	private boolean hasAggs = false;

	private String[] valueRoot;

	/**
	 * 语法模式(sql和原生模式)
	 */
	private boolean sqlMode = false;

	/**
	 * 显示字段信息
	 */
	private String[] fields;

	/**
	 * @return the collection
	 */
	public String getCollection() {
		return collection;
	}

	/**
	 * @param collection the collection to set
	 */
	public void setCollection(String collection) {
		this.collection = collection;
	}

	/**
	 * @return the endpoint
	 */
	public String getEndpoint() {
		return endpoint;
	}

	/**
	 * @param endpoint the endpoint to set
	 */
	public void setEndpoint(String endpoint) {
		this.endpoint = endpoint;
	}

	/**
	 * @return the type
	 */
	public String getType() {
		return type;
	}

	/**
	 * @param type the type to set
	 */
	public void setType(String type) {
		this.type = type;
	}

	/**
	 * @return the index
	 */
	public String getIndex() {
		return index;
	}

	/**
	 * @param index the index to set
	 */
	public void setIndex(String index) {
		this.index = index;
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
	 * @return the hasAggs
	 */
	public boolean isHasAggs() {
		return hasAggs;
	}

	/**
	 * @param hasAggs the hasAggs to set
	 */
	public void setHasAggs(boolean hasAggs) {
		this.hasAggs = hasAggs;
	}

	/**
	 * @return the fields
	 */
	public String[] getFields() {
		return fields;
	}

	/**
	 * @param fields the fields to set
	 */
	public void setFields(String[] fields) {
		this.fields = fields;
	}

	/**
	 * @return the valueRoot
	 */
	public String[] getValueRoot() {
		return valueRoot;
	}

	/**
	 * @param valueRoot the valueRoot to set
	 */
	public void setValueRoot(String[] valueRoot) {
		this.valueRoot = valueRoot;
	}

	/**
	 * @return the sqlMode
	 */
	public boolean isSqlMode() {
		return sqlMode;
	}

	/**
	 * @param sqlMode the sqlMode to set
	 */
	public void setSqlMode(boolean sqlMode) {
		this.sqlMode = sqlMode;
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

}
