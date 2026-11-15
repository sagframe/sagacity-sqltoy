package org.sagacity.sqltoy.config.model;

import java.io.Serializable;

/**
 * @project sagacity-sqltoy
 * @description 基于mongo或elasticSearch的配置
 * 
 *              说明:elasticSearch的超时与字符集采用"endpoint默认+语句覆盖":eql上显式配置的
 *              request-timeout/connection-timeout/socket-timeout/charset优先,未配置时取
 *              ElasticEndpoint(由starter的ElasticConfig注入);单地址/多地址/eql自定义url统一走
 *              rest5客户端,超时组合与endpoint默认值不同的语句使用独立客户端(异步客户端超时
 *              只能在构建时确定)。
 * @author zhongxuchen
 * @version v1.0,Date:2018-01-03
 */
public class NoSqlConfigModel implements Serializable, Cloneable {

	private static final long serialVersionUID = -2126986650751681962L;

	/**
	 * 默认的从连接池获取连接等待时长:30秒
	 */
	public final static int DEFAULT_REQUEST_TIMEOUT = 30000;

	/**
	 * 默认的连接超时时长:10秒
	 */
	public final static int DEFAULT_CONNECT_TIMEOUT = 10000;

	/**
	 * 默认的响应超时时长:3分钟
	 */
	public final static int DEFAULT_SOCKET_TIMEOUT = 180000;

	/**
	 * 默认字符集
	 */
	public final static String DEFAULT_CHARSET = "UTF-8";

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
	 * 从连接池获取连接的最大等待时长(毫秒),未配置时取ElasticEndpoint(默认30秒)。
	 * 注意:该值不是请求超时,真正等待服务端返回数据的超时是socketTimeout
	 */
	private Integer requestTimeout;

	/**
	 * 建立连接的超时时长(毫秒),未配置时取ElasticEndpoint(默认10秒)。
	 * 该值与endpoint不同的语句使用独立rest5客户端(建连超时只能在客户端构建时确定)
	 */
	private Integer connectTimeout;

	/**
	 * 等待服务端返回数据的响应超时时长(毫秒),未配置时取ElasticEndpoint(默认3分钟)
	 */
	private Integer socketTimeout;

	/**
	 * 请求与响应的字符集,未配置时取ElasticEndpoint(默认UTF-8)
	 */
	private String charset = DEFAULT_CHARSET;

	/**
	 * charset是否被显式设置:未设置时采用ElasticEndpoint上的字符集
	 */
	private boolean charsetSet;

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
	 * @return 从连接池获取连接的等待上限(毫秒),未配置时返回默认值
	 */
	public int getRequestTimeout() {
		return requestTimeout == null ? DEFAULT_REQUEST_TIMEOUT : requestTimeout;
	}

	/**
	 * @return 是否显式配置了requestTimeout
	 */
	public boolean hasRequestTimeout() {
		return requestTimeout != null;
	}

	/**
	 * @param requestTimeout the requestTimeout to set
	 */
	public void setRequestTimeout(int requestTimeout) {
		this.requestTimeout = requestTimeout;
	}

	/**
	 * @return 建立连接的超时时长(毫秒),未配置时返回默认值
	 */
	public int getConnectTimeout() {
		return connectTimeout == null ? DEFAULT_CONNECT_TIMEOUT : connectTimeout;
	}

	/**
	 * @return 是否显式配置了connectTimeout
	 */
	public boolean hasConnectTimeout() {
		return connectTimeout != null;
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
	 * @return 是否显式配置了charset
	 */
	public boolean isCharsetSet() {
		return charsetSet;
	}

	/**
	 * @param charset the charset to set
	 */
	public void setCharset(String charset) {
		this.charset = charset;
		this.charsetSet = true;
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
	 * @return 等待服务端返回数据的响应超时时长(毫秒),未配置时返回默认值
	 */
	public int getSocketTimeout() {
		return socketTimeout == null ? DEFAULT_SOCKET_TIMEOUT : socketTimeout;
	}

	/**
	 * @return 是否显式配置了socketTimeout
	 */
	public boolean hasSocketTimeout() {
		return socketTimeout != null;
	}

	/**
	 * @param socketTimeout the socketTimeout to set
	 */
	public void setSocketTimeout(int socketTimeout) {
		this.socketTimeout = socketTimeout;
	}

	/**
	 * 配置副本，数组属性一并复制避免引用共享
	 * 
	 * @return NoSqlConfigModel副本
	 */
	@Override
	public NoSqlConfigModel clone() {
		try {
			NoSqlConfigModel cloned = (NoSqlConfigModel) super.clone();
			if (this.valueRoot != null) {
				cloned.valueRoot = this.valueRoot.clone();
			}
			if (this.fields != null) {
				cloned.fields = this.fields.clone();
			}
			return cloned;
		} catch (CloneNotSupportedException e) {
			return null;
		}
	}

}
