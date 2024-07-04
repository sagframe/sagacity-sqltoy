/**
 * 
 */
package org.sagacity.sqltoy.translate.model;

import java.io.Serializable;
import java.util.ArrayList;
import java.util.List;

import org.sagacity.sqltoy.utils.NumberUtil;
import org.sagacity.sqltoy.utils.StringUtil;

/**
 * @project sagacity-sqltoy
 * @description 自动检测任务配置模型
 * @author zhongxuchen
 * @version v1.0,Date:2018年3月8日
 */
public class CheckerConfigModel implements Serializable {

	/**
	 * 
	 */
	private static final long serialVersionUID = -8328516221738636079L;

	private String id;

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
	private boolean increment = true;

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

	public String getId() {
		if (id != null) {
			return id;
		}
		if (cache != null) {
			return cache;
		}
		if ("sql".equals(type)) {
			return sql;
		}
		if ("service".equals(type)) {
			return service + "." + method;
		}
		if ("rest".equals(type)) {
			return url;
		}
		return null;
	}

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
	public CheckerConfigModel setType(String type) {
		if (type != null) {
			this.type = type.toLowerCase();
		}
		return this;
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
	public CheckerConfigModel setSql(String sql) {
		this.sql = sql;
		this.type = "sql";
		return this;
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
	public CheckerConfigModel setDataSource(String dataSource) {
		this.dataSource = dataSource;
		return this;
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
	public CheckerConfigModel setCheckFrequency(String frequency) {
		// frequency的格式 frequency="0..12?15,12..18:30?10,18:30..24?60"
		if (StringUtil.isNotBlank(frequency)) {
			List<TimeSection> timeParts = new ArrayList<TimeSection>();
			// 统一格式,去除全角字符,去除空白
			frequency = StringUtil.toDBC(frequency).replace(";", ",").trim();
			this.checkFrequency = frequency;
			// 0~24点 统一的检测频率
			// 可以是单个频率值,表示0到24小时采用统一的频率
			if (NumberUtil.isInteger(frequency)) {
				TimeSection section = new TimeSection();
				section.setStart(0);
				section.setEnd(2400);
				section.setIntervalSeconds(Integer.parseInt(frequency));
				timeParts.add(section);
			} else {
				// 归整分割符号统一为逗号,将时间格式由HH:mm 转为HHmm格式
				String[] sectionsStr = frequency.split("\\,");
				for (int j = 0; j < sectionsStr.length; j++) {
					TimeSection section = new TimeSection();
					// 问号切割获取时间区间和时间间隔
					String[] sectionPhase = sectionsStr[j].split("\\?");
					// 获取开始和结束时间点
					String[] startEnd = sectionPhase[0].split("\\.{2}");
					section.setIntervalSeconds(Integer.parseInt(sectionPhase[1].trim()));
					section.setStart(getHourMinute(startEnd[0].trim()));
					section.setEnd(getHourMinute(startEnd[1].trim()));
					timeParts.add(section);
				}
			}
			this.timeSections = timeParts;
		}
		return this;
	}

	private int getHourMinute(String hourMinuteStr) {
		// 320(3点20分)
		if (NumberUtil.isInteger(hourMinuteStr) && hourMinuteStr.length() > 2) {
			return Integer.parseInt(hourMinuteStr);
		}
		String tmp = hourMinuteStr.replace(".", ":");
		String[] hourMin = tmp.split("\\:");
		return Integer.parseInt(hourMin[0]) * 100 + ((hourMin.length > 1) ? Integer.parseInt(hourMin[1]) : 0);
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
	public CheckerConfigModel setService(String service) {
		this.service = service;
		this.type = "service";
		return this;
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
	public CheckerConfigModel setMethod(String method) {
		this.method = method;
		return this;
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
	public CheckerConfigModel setUrl(String url) {
		this.url = url;
		this.type = "rest";
		return this;
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
	public CheckerConfigModel setUsername(String username) {
		this.username = username;
		return this;
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
	public CheckerConfigModel setPassword(String password) {
		this.password = password;
		return this;
	}

	/**
	 * @return the timeSections
	 */
	public List<TimeSection> getTimeSections() {
		return timeSections;
	}

	public String[] getProperties() {
		return properties;
	}

	public CheckerConfigModel setProperties(String[] properties) {
		this.properties = properties;
		return this;
	}

	public String getCache() {
		return cache;
	}

	public CheckerConfigModel setCache(String cache) {
		this.cache = cache;
		return this;
	}

	public boolean isIncrement() {
		return increment;
	}

	public CheckerConfigModel setIncrement(boolean increment) {
		this.increment = increment;
		return this;
	}

	public boolean isHasInsideGroup() {
		return hasInsideGroup;
	}

	public CheckerConfigModel setHasInsideGroup(boolean hasInsideGroup) {
		this.hasInsideGroup = hasInsideGroup;
		return this;
	}

	public CheckerConfigModel setId(String id) {
		this.id = id;
		return this;
	}

}
