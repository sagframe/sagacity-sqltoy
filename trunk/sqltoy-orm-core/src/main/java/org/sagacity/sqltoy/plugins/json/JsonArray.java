package org.sagacity.sqltoy.plugins.json;

import java.util.ArrayList;
import java.util.List;

/**
 * @project sagacity-sqltoy
 * @description provider 中立的 JSON 数组视图。底层是 List:fastjson 的 JSONArray 本身 实现
 *              List,jackson 解析结果也是 List,因此同一套访问器对两种 provider 成立。
 * @author zhongxuchen
 * @version v1.0, Date:2026-10-15
 */
public class JsonArray extends java.util.AbstractList<Object> {

	private final List<Object> list;

	public JsonArray() {
		this.list = new ArrayList<>();
	}

	public JsonArray(List<Object> list) {
		this.list = (list == null) ? new ArrayList<>() : list;
	}

	@SuppressWarnings("unchecked")
	public static JsonArray of(Object nativeArray) {
		if (nativeArray == null) {
			return null;
		}
		if (nativeArray instanceof JsonArray) {
			return (JsonArray) nativeArray;
		}
		return new JsonArray((List<Object>) nativeArray);
	}

	public List<Object> unwrap() {
		return list;
	}

	public int size() {
		return list.size();
	}

	public boolean isEmpty() {
		return list.isEmpty();
	}

	public Object get(int index) {
		return list.get(index);
	}

	public JsonObject getJSONObject(int index) {
		return JsonObject.of(list.get(index));
	}

	public JsonArray getJSONArray(int index) {
		return of(list.get(index));
	}

	public String getString(int index) {
		Object v = list.get(index);
		return (v == null) ? null : String.valueOf(v);
	}

	@Override
	public boolean add(Object value) {
		return list.add((value instanceof JsonObject) ? ((JsonObject) value).unwrap()
				: (value instanceof JsonArray) ? ((JsonArray) value).unwrap() : value);
	}

	/**
	 * fluent add
	 */
	public JsonArray append(Object value) {
		add(value);
		return this;
	}

	public Object[] toArray() {
		return list.toArray();
	}

	public <T> List<T> toJavaList(Class<T> elementType) {
		return JsonProviders.get().convertList(list, elementType);
	}

	public String toJSONString() {
		return JsonProviders.get().toJSONString(list);
	}

	@Override
	public String toString() {
		return toJSONString();
	}
}
