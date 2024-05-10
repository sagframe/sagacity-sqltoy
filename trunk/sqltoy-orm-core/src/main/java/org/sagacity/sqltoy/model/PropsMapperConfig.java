/**
 * 
 */
package org.sagacity.sqltoy.model;

import java.util.HashMap;
import java.util.Map;

/**
 * @project sagacity-sqltoy
 * @description 复制对象属性的配置
 * @author zhongxuchen
 * @version v1.0, Date:2023年11月30日
 * @modify 2023年11月30日,修改说明
 */
public class PropsMapperConfig {
	// 复制或忽略的属性，当为null时，表示复制全部属性
	private String[] properties;

	// 针对properties，设定是否为忽略的属性，默认为false
	private boolean ignore = false;

	/**
	 * 是否跳过null
	 */
	private boolean skipNull = false;

	// add 2024-5-9 增加手工指定两个对象属性映射关系，比如:birthday-->staffBorthday
	private Map<String, String> fieldsMap = new HashMap<>();

	public PropsMapperConfig(String... properties) {
		if (properties != null && properties.length > 0) {
			this.properties = properties;
		}
	}

	public PropsMapperConfig isIgnore(boolean ignore) {
		this.ignore = ignore;
		return this;
	}

	public PropsMapperConfig fieldsMap(Map<String, String> fieldsMap) {
		this.fieldsMap = fieldsMap;
		return this;
	}

	public PropsMapperConfig skipNull(boolean skipNull) {
		this.skipNull = skipNull;
		return this;
	}

	public String[] getProperties() {
		return properties;
	}

	public boolean isIgnore() {
		return ignore;
	}

	/**
	 * 
	 */
	public boolean getSkipNull() {
		return skipNull;
	}

	public Map<String, String> getFieldsMap() {
		return fieldsMap;
	}

}
