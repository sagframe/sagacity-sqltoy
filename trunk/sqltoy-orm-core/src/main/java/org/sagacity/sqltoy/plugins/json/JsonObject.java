package org.sagacity.sqltoy.plugins.json;

import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Set;

/**
 * @project sagacity-sqltoy
 * @description provider 中立的 JSON 对象视图。底层是 Map:fastjson 的 JSONObject 本身
 *              实现 Map,jackson 解析结果也是 Map,因此同一套访问器语义对两种 provider 成立。
 *              取值做宽松类型转换(与 fastjson 的历史行为对齐):数字可转字符串、字符串数字可转 Long 等。
 * @author zhongxuchen
 * @version v1.0, Date:2026-10-15
 */
public class JsonObject extends java.util.AbstractMap<String, Object> {

	private final Map<String, Object> map;

	public JsonObject() {
		this.map = new LinkedHashMap<>();
	}

	public JsonObject(Map<String, Object> map) {
		this.map = (map == null) ? new LinkedHashMap<>() : map;
	}

	/**
	 * 将任意 provider 的原生对象(Map / JSONObject)包装为中立视图
	 */
	@SuppressWarnings("unchecked")
	public static JsonObject of(Object nativeObject) {
		if (nativeObject == null) {
			return null;
		}
		if (nativeObject instanceof JsonObject) {
			return (JsonObject) nativeObject;
		}
		return new JsonObject((Map<String, Object>) nativeObject);
	}

	public Map<String, Object> unwrap() {
		return map;
	}

	public Object get(String key) {
		return map.get(key);
	}

	public String getString(String key) {
		Object v = map.get(key);
		return (v == null) ? null : String.valueOf(v);
	}

	public Long getLong(String key) {
		Object v = map.get(key);
		if (v == null) {
			return null;
		}
		if (v instanceof Number) {
			return ((Number) v).longValue();
		}
		return Long.valueOf(String.valueOf(v).trim());
	}

	public Integer getInteger(String key) {
		Long v = getLong(key);
		return (v == null) ? null : v.intValue();
	}

	public Boolean getBoolean(String key) {
		Object v = map.get(key);
		if (v == null) {
			return null;
		}
		if (v instanceof Boolean) {
			return (Boolean) v;
		}
		return Boolean.valueOf(String.valueOf(v).trim());
	}

	public int getIntValue(String key) {
		Integer v = getInteger(key);
		return (v == null) ? 0 : v.intValue();
	}

	public long getLongValue(String key) {
		Long v = getLong(key);
		return (v == null) ? 0L : v.longValue();
	}

	public boolean getBooleanValue(String key) {
		Boolean v = getBoolean(key);
		return v != null && v.booleanValue();
	}

	public JsonObject getJSONObject(String key) {
		return of(map.get(key));
	}

	public JsonArray getJSONArray(String key) {
		return JsonArray.of(map.get(key));
	}

	public boolean containsKey(String key) {
		return map.containsKey(key);
	}

	public Set<Map.Entry<String, Object>> entrySet() {
		return map.entrySet();
	}

	public JsonObject put(String key, Object value) {
		map.put(key, (value instanceof JsonObject) ? ((JsonObject) value).unwrap()
				: (value instanceof JsonArray) ? ((JsonArray) value).unwrap() : value);
		return this;
	}

	public JsonObject fluentPut(String key, Object value) {
		return put(key, value);
	}

	public Object remove(String key) {
		return map.remove(key);
	}

	public String toJSONString() {
		return JsonProviders.get().toJSONString(map);
	}

	@Override
	public String toString() {
		return toJSONString();
	}
}
