/**
 * 
 */
package org.sagacity.sqltoy.translate.model;

import java.io.Serializable;
import java.util.List;

/**
 * @project sagacity-sqltoy4.2
 * @description 自动检测任务配置模型
 * @author chenrenfei <a href="mailto:zhongxuchen@gmail.com">联系作者</a>
 * @version id:ChecherConfigModel.java,Revision:v1.0,Date:2018年3月8日
 */
public class CheckerConfigModel implements Serializable {

	/**
	 * 
	 */
	private static final long serialVersionUID = -8328516221738636079L;

	/**
	 * (sql\service\rest)
	 */
	private String type = "sql";

	/**
	 * 具体执行的sql语句
	 */
	private String sql;

	/**
	 * 针对哪个缓存进行更新
	 */
	private String cache;

	/**
	 * 是否存在缓存内部分类
	 */
	private boolean hasInsideGroup;

	/**
	 * 增量更新
	 */
	private boolean increment = false;

	/**
	 * 数据源
	 */
	private String dataSource;

	/**
	 * 检测频率
	 */
	private String checkFrequency;

	/**
	 * 服务名称
	 */
	private String service;

	/**
	 * 方法
	 */
	private String method;

	/**
	 * 属性
	 */
	private String[] properties;

	/**
	 * rest模式的url地址
	 */
	private String url;

	/**
	 * http 请求用户名
	 */
	private String username;

	/**
	 * http 请求用户密码
	 */
	private String password;

	/**
	 * 检测时间区间
	 */
	private List<TimeSection> timeSections;

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
	 * @return the sql
	 */
	public String getSql() {
		return sql;
	}

	/**
	 * @param sql the sql to set
	 */
	public void setSql(String sql) {
		this.sql = sql;
	}

	/**
	 * @return the dataSource
	 */
	public String getDataSource() {
		return dataSource;
	}

	/**
	 * @param dataSource the dataSource to set
	 */
	public void setDataSource(String dataSource) {
		this.dataSource = dataSource;
	}

	/**
	 * @return the checkFrequency
	 */
	public String getCheckFrequency() {
		return checkFrequency;
	}

	/**
	 * @param checkFrequency the checkFrequency to set
	 */
	public void setCheckFrequency(String checkFrequency) {
		this.checkFrequency = checkFrequency;
	}

	/**
	 * @return the service
	 */
	public String getService() {
		return service;
	}

	/**
	 * @param service the service to set
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
	 * @param method the method to set
	 */
	public void setMethod(String method) {
		this.method = method;
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
	 * @return the timeSections
	 */
	public List<TimeSection> getTimeSections() {
		return timeSections;
	}

	/**
	 * @param timeSections the timeSections to set
	 */
	public void setTimeSections(List<TimeSection> timeSections) {
		this.timeSections = timeSections;
	}

	public String[] getProperties() {
		return properties;
	}

	public void setProperties(String[] properties) {
		this.properties = properties;
	}

	public String getCache() {
		return cache;
	}

	public void setCache(String cache) {
		this.cache = cache;
	}

	public boolean isIncrement() {
		return increment;
	}

	public void setIncrement(boolean increment) {
		this.increment = increment;
	}

	public boolean isHasInsideGroup() {
		return hasInsideGroup;
	}

	public void setHasInsideGroup(boolean hasInsideGroup) {
		this.hasInsideGroup = hasInsideGroup;
	}

}
