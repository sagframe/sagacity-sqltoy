package org.sagacity.sqltoy.plugins.json;

import java.util.List;
import java.util.Map;

import com.alibaba.fastjson2.JSON;
import com.alibaba.fastjson2.JSONArray;

/**
 * @project sagacity-sqltoy
 * @description fastjson2 实现(存量项目的默认行为)
 * @author zhongxuchen
 * @version v1.0, Date:2026-10-15
 */
public class FastjsonJsonProvider implements JsonProvider {

	@Override
	public String name() {
		return "fastjson2";
	}

	@Override
	public String toJSONString(Object value) {
		return JSON.toJSONString(value);
	}

	@Override
	@SuppressWarnings("unchecked")
	public Map<String, Object> parseObjectMap(String json) {
		return JSON.parseObject(json);
	}

	@Override
	public Object parseObjectNative(String json) {
		return JSON.parseObject(json);
	}

	@Override
	public Object parseArrayNative(String json) {
		return JSON.parseArray(json);
	}

	@Override
	public List<Object> parseArray(String json) {
		return JSON.parseArray(json);
	}

	@Override
	public <T> List<T> parseArray(String json, Class<T> elementType) {
		return JSON.parseArray(json, elementType);
	}

	@Override
	public <T> T parseObject(String json, Class<T> type) {
		return JSON.parseObject(json, type);
	}

	@Override
	public <T> T convert(Object node, Class<T> type) {
		return JSON.parseObject(JSON.toJSONString(node), type);
	}
}
