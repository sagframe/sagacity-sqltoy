/**
 * 
 */
package org.sagacity.sqltoy.model;

import java.io.Serializable;
import java.util.HashMap;
import java.util.Map;

/**
 * @project sagacity-sqltoy
 * @description 便于快速构建Map用于传参
 * @author zhongxuchen
 * @version v1.0, Date:2021年11月4日
 */
public class MapKit implements Serializable {
	/**
	 * 
	 */
	private static final long serialVersionUID = -7326755576648579935L;

	private Map<String, Object> map = new HashMap<String, Object>();

	private String[] keys;

	public static MapKit keys(String... keys) {
		MapKit mapkit = new MapKit();
		mapkit.keys = keys;
		return mapkit;
	}

	public Map<String, Object> values(Object... values) {
		if (keys != null && values != null) {
			if (keys.length != values.length) {
				throw new IllegalArgumentException("构造Map对应的keys长度:" + keys.length + "不等于values长度:" + values.length);
			}
			for (int i = 0; i < keys.length; i++) {
				if (values[i] != null) {
					map.put(keys[i], values[i]);
				}
			}
		}
		return map;
	}

	/**
	 * 设置Map的key value
	 * 
	 * @param key
	 * @param value
	 * @return
	 */
	public static MapKit startOf(String key, Object value) {
		MapKit mapkit = new MapKit();
		mapkit.map.put(key, value);
		return mapkit;
	}

	/**
	 * 设置Map的key value
	 * 
	 * @param key
	 * @param value
	 * @return
	 */
	public MapKit of(String key, Object value) {
		map.put(key, value);
		return this;
	}

	public Map<String, Object> endOf(String key, Object value) {
		map.put(key, value);
		return map;
	}

	public Map<String, Object> get() {
		return map;
	}
}
