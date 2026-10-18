package org.sagacity.sqltoy.plugins.json;

import java.util.List;
import java.util.Map;

import com.fasterxml.jackson.databind.ObjectMapper;

/**
 * @project sagacity-sqltoy
 * @description Jackson 2 实现(com.fasterxml.jackson,Boot 3 及非 Boot 项目的常见依赖)
 * @author zhongxuchen
 * @version v1.0, Date:2026-10-15
 */
public class Jackson2JsonProvider implements JsonProvider {

	private static final ObjectMapper MAPPER = new ObjectMapper()
			// match fastjson's lenient POJO binding: unknown JSON properties are ignored
			.configure(com.fasterxml.jackson.databind.DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES, false);

	@Override
	public String name() {
		return "jackson2";
	}

	@Override
	public String toJSONString(Object value) {
		try {
			return MAPPER.writeValueAsString(value);
		} catch (Exception e) {
			throw new IllegalArgumentException("jackson toJSONString failed: " + e.getMessage(), e);
		}
	}

	@Override
	@SuppressWarnings("unchecked")
	public Map<String, Object> parseObjectMap(String json) {
		try {
			return MAPPER.readValue(json, Map.class);
		} catch (Exception e) {
			throw new IllegalArgumentException("jackson parseObject failed: " + e.getMessage(), e);
		}
	}

	@Override
	public Object parseObjectNative(String json) {
		return parseObjectMap(json);
	}

	@Override
	public Object parseArrayNative(String json) {
		return parseArray(json);
	}

	@Override
	@SuppressWarnings("unchecked")
	public List<Object> parseArray(String json) {
		try {
			return MAPPER.readValue(json, List.class);
		} catch (Exception e) {
			throw new IllegalArgumentException("jackson parseArray failed: " + e.getMessage(), e);
		}
	}

	@Override
	public <T> List<T> parseArray(String json, Class<T> elementType) {
		try {
			return MAPPER.readValue(json, MAPPER.getTypeFactory().constructCollectionType(List.class, elementType));
		} catch (Exception e) {
			throw new IllegalArgumentException("jackson parseArray(elementType) failed: " + e.getMessage(), e);
		}
	}

	@Override
	public <T> T parseObject(String json, Class<T> type) {
		try {
			return MAPPER.readValue(json, type);
		} catch (Exception e) {
			throw new IllegalArgumentException("jackson parseObject(type) failed: " + e.getMessage(), e);
		}
	}

	@Override
	public <T> T convert(Object node, Class<T> type) {
		return MAPPER.convertValue(node, type);
	}
}
