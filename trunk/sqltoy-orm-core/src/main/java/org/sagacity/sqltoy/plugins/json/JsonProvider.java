package org.sagacity.sqltoy.plugins.json;

import java.util.List;
import java.util.Map;

/**
 * @project sagacity-sqltoy
 * @description JSON 处理的抽象层:sqltoy 的 JSON/JSONB 列支持不绑定任何具体 JSON 库。 默认按 classpath
 *              探测(fastjson2 优先以兼容存量项目,其次 jackson), 也可通过
 *              spring.sqltoy.jsonProvider 显式指定实现类。
 * @author zhongxuchen
 * @version v1.0, Date:2026-10-15
 */
public interface JsonProvider {

	/**
	 * provider 名称(用于日志与排查)
	 */
	String name();

	/**
	 * 对象 -> JSON 文本
	 */
	String toJSONString(Object value);

	/**
	 * JSON 文本 -> 中立 Map 视图(实现可以是 provider 原生对象,只要它实现 Map)
	 */
	Map<String, Object> parseObjectMap(String json);

	/**
	 * JSON 文本 -> provider 原生对象(JSONObject 或 Map),用于属性类型即为原生类型的场景
	 */
	Object parseObjectNative(String json);

	/**
	 * JSON 文本 -> provider 原生数组(JSONArray 或 List)
	 */
	Object parseArrayNative(String json);

	/**
	 * JSON 文本 -> 中立 List 视图
	 */
	List<Object> parseArray(String json);

	/**
	 * JSON 文本 -> 指定元素类型的 List
	 */
	<T> List<T> parseArray(String json, Class<T> elementType);

	/**
	 * JSON 文本 -> 指定类型对象
	 */
	<T> T parseObject(String json, Class<T> type);

	/**
	 * 已解析节点 -> 指定类型(用于数组元素转换)
	 */
	<T> T convert(Object node, Class<T> type);

	/**
	 * JSON 文本 -> 中立对象视图
	 */
	default JsonObject object(String json) {
		return JsonObject.of(parseObjectNative(json));
	}

	/**
	 * JSON 文本 -> 中立数组视图
	 */
	default JsonArray array(String json) {
		return JsonArray.of(parseArrayNative(json));
	}

	/**
	 * 已解析节点集合 -> 指定元素类型的 List
	 */
	default <T> List<T> convertList(List<?> nodes, Class<T> elementType) {
		List<T> result = new java.util.ArrayList<>(nodes.size());
		for (Object node : nodes) {
			result.add(convert(node, elementType));
		}
		return result;
	}
}
