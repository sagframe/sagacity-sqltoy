package org.sagacity.sqltoy.model;

import java.io.Serializable;
import java.time.LocalDateTime;

/**
 * @project sqltoy-orm
 * @description 用于类似从数据库或其他形式配置的xml格式的sql，用在如下场景
 * lightDao.findByQuery(new QueryExecutor(new XMLBinding(xml).id(id).lastUpdateTime(lastUpdateTime)))
 * @author zhongxuchen
 * @version v1.0,Date:2026-2-6
 */
public class XMLBinding implements Serializable {

	/**
	 * 
	 */
	private static final long serialVersionUID = 3068484596254185451L;

	/**
	 * sql xml形式的配置
	 * <sql id=""></sql>
	 */
	private String xml;

	/**
	 * sqlId 考虑<sql id="xxx">存在重复的风险，需要绑定到一个不唯一的id
	 */
	private String id;

	/**
	 * xml最后修改时间
	 */
	private LocalDateTime lastUpdateTime;

	public XMLBinding(String xml) {
		this.xml = xml;
	}

	public XMLBinding xml(String xml) {
		this.xml = xml;
		return this;
	}

	public XMLBinding lastUpdateTime(LocalDateTime lastUpdateTime) {
		this.lastUpdateTime = lastUpdateTime;
		return this;
	}

	public XMLBinding id(String id) {
		this.id = id;
		return this;
	}

	public String getId() {
		return id;
	}

	public String getXml() {
		return xml;
	}

	public LocalDateTime getLastUpdateTime() {
		return lastUpdateTime;
	}
}
