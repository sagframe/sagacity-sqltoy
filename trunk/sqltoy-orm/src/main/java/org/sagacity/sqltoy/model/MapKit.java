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
			// key的长度是1，但values是数组
			if (keys.length == 1 && values.length > 1) {
				if (keys[0] != null) {
					map.put(keys[0], values);
				}
			} else {
				if (keys.length != values.length) {
					throw new IllegalArgumentException(
							"构造Map对应的keys长度:" + keys.length + "不等于values长度:" + values.length);
				}
				for (int i = 0; i < keys.length; i++) {
					if (keys[i] != null && values[i] != null) {
						map.put(keys[i], values[i]);
					}
				}
			}
		}
		return map;
	}

	/**
	 * @TODO 设置Map的key value
	 * @param key
	 * @param value
	 * @return
	 */
	public static MapKit startOf(String key, Object value) {
		MapKit mapkit = new MapKit();
		if (key != null && value != null) {
			mapkit.map.put(key, value);
		}
		return mapkit;
	}

	/**
	 * @TODO 设置Map的key value
	 * @param key
	 * @param value
	 * @return
	 */
	public MapKit of(String key, Object value) {
		if (key != null && value != null) {
			map.put(key, value);
		}
		return this;
	}

	public Map<String, Object> endOf(String key, Object value) {
		if (key != null && value != null) {
			map.put(key, value);
		}
		return map;
	}

	/**
	 * @see map(String key, Object value)
	 * @return
	 */
	@Deprecated
	public Map<String, Object> get() {
		return map;
	}

	/**
	 * @TODO 创建一个空Map
	 * @return
	 */
	public static Map<String, Object> map() {
		return new HashMap<String, Object>();
	}

	/**
	 * @TODO 单个key和value场景
	 * @param key
	 * @param value
	 * @return
	 */
	public static Map<String, Object> map(String key, Object value) {
		HashMap<String, Object> result = new HashMap<String, Object>();
		if (key != null && value != null) {
			result.put(key, value);
		}
		return result;
	}
}
