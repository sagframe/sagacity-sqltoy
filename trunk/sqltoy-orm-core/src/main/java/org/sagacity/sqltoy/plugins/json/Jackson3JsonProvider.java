package org.sagacity.sqltoy.plugins.json;

import java.util.List;
import java.util.Map;

import tools.jackson.databind.ObjectMapper;

/**
 * @project sagacity-sqltoy
 * @description Jackson 3 实现(tools.jackson, Spring Boot 4 的默认 JSON 库)
 * @author zhongxuchen
 * @version v1.0, Date:2026-10-15
 */
public class Jackson3JsonProvider implements JsonProvider {

	// jackson3 mappers are configured via the builder; ignore unknown properties to
	// match fastjson's lenient POJO binding
	private static final ObjectMapper MAPPER = tools.jackson.databind.json.JsonMapper.builder()
			.disable(tools.jackson.databind.DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES).build();

	@Override
	public String name() {
		return "jackson3";
	}

	@Override
	public String toJSONString(Object value) {
		return MAPPER.writeValueAsString(value);
	}

	@Override
	@SuppressWarnings("unchecked")
	public Map<String, Object> parseObjectMap(String json) {
		return MAPPER.readValue(json, Map.class);
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
		return MAPPER.readValue(json, List.class);
	}

	@Override
	public <T> List<T> parseArray(String json, Class<T> elementType) {
		return MAPPER.readValue(json, MAPPER.getTypeFactory().constructCollectionType(List.class, elementType));
	}

	@Override
	public <T> T parseObject(String json, Class<T> type) {
		return MAPPER.readValue(json, type);
	}

	@Override
	public <T> T convert(Object node, Class<T> type) {
		return MAPPER.convertValue(node, type);
	}
}
