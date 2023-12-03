/**
 * 
 */
package org.sagacity.sqltoy.model;

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

	public PropsMapperConfig(String... properties) {
		if (properties != null && properties.length > 0) {
			this.properties = properties;
		}
	}

	public PropsMapperConfig isIgnore(boolean ignore) {
		this.ignore = ignore;
		return this;
	}

	public String[] getProperties() {
		return properties;
	}

	public boolean isIgnore() {
		return ignore;
	}

}
