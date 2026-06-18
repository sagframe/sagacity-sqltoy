package org.sagacity.sqltoy.utils;

import java.lang.reflect.Array;
import java.sql.PreparedStatement;
import java.sql.SQLException;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.TreeMap;
import java.util.TreeSet;

import org.sagacity.sqltoy.dialect.utils.PostgreSqlDialectUtils;
import org.sagacity.sqltoy.utils.DataSourceUtils.DBType;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.alibaba.fastjson2.JSON;
import com.alibaba.fastjson2.JSONArray;
import com.alibaba.fastjson2.JSONObject;

/**
 * 提供一个通用的json类型处理工具
 * 
 * @date 2026-6-10
 */
public class JSONTypeUtil {
	/**
	 * 定义日志
	 */
	protected final static Logger logger = LoggerFactory.getLogger(JSONTypeUtil.class);
	// JSON 相关类型名称缓存
	private static final String JSON_OBJECT = "jsonobject";
	private static final String JSON_ARRAY = "jsonarray";
	private static final String STRING_TYPE = "java.lang.string";
	private static final String LIST_TYPE = "java.util.list";
	private static final String MAP_TYPE = "java.util.map";
	private static final String SET_TYPE = "java.util.set";
	private static final String COLLECTION_TYPE = "java.util.collection";

	/**
	 * 根据数据库方言类型针对null值做pst.setNull
	 * 
	 * @param dbType
	 * @param pst
	 * @param paramIndex
	 * @param jdbcType
	 * @throws SQLException
	 */
	public static void setNull(Integer dbType, PreparedStatement pst, int paramIndex, int jdbcType)
			throws SQLException {
		if (dbType == DBType.MYSQL || dbType == DBType.MYSQL57 || dbType == DBType.DORIS || dbType == DBType.STARROCKS
				|| dbType == DBType.OCEANBASE || dbType == DBType.TIDB) {
			pst.setNull(paramIndex, java.sql.Types.LONGVARCHAR);
		} else if (dbType == DBType.DM) {
			pst.setNull(paramIndex, java.sql.Types.CLOB);
		} else if (dbType == DBType.H2 || dbType == DBType.CLICKHOUSE) {
			pst.setNull(paramIndex, java.sql.Types.VARCHAR);
		} else if (dbType == DBType.SQLSERVER) {
			pst.setNull(paramIndex, java.sql.Types.NVARCHAR);
		} else if (dbType == DBType.POSTGRESQL || dbType == DBType.POSTGRESQL15 || dbType == DBType.GAUSSDB
				|| dbType == DBType.OPENGAUSS || dbType == DBType.MOGDB || dbType == DBType.VASTBASE
				|| dbType == DBType.STARDB || dbType == DBType.OSCAR) {
			pst.setNull(paramIndex, java.sql.Types.OTHER);
		} else if (dbType == DBType.ORACLE || dbType == DBType.ORACLE11) {
			pst.setNull(paramIndex, java.sql.Types.OTHER);
		} else if (dbType == DBType.KINGBASE) {
			pst.setNull(paramIndex, java.sql.Types.OTHER);
		} else {
			pst.setNull(paramIndex, java.sql.Types.OTHER);
		}
	}

	/**
	 * <li>返回true表示类型匹配上，并完成了setValue赋值</li>
	 * <li>返回false 表示常规类型,交回框架自行处理</li>
	 */
	public static void setJSONValue(Integer dbType, PreparedStatement pst, int paramIndex, int jdbcType, Object value)
			throws SQLException {
		if (dbType == DBType.POSTGRESQL || dbType == DBType.POSTGRESQL15) {
			PostgreSqlDialectUtils.setJSONValue(pst, paramIndex, jdbcType, JSON.toJSONString(value));
		} else {
			pst.setString(paramIndex, JSON.toJSONString(value));
		}
	}

	/*
	 * <li>1、返回null表示属于常规类型，交回框架完成处理</li>
	 * <li>2、返回非null,表示特殊类型，完成了类型转换可直接映射到VO属性</li>
	 */
	public static Object jsonToJavaType(int sqlType, String javaTypeName, Class genericType, Object jdbcValue)
			throws Exception {
		// 1.null直接返回
		if (jdbcValue == null) {
			return null;
		}
		String jsonStr = extractJsonString(jdbcValue);
		if (jsonStr == null) {
			return null;
		}
		String javaTypeNameLow = javaTypeName.toLowerCase();
		// 2.字符串
		if (javaTypeNameLow.equals(STRING_TYPE)) {
			return jsonStr;
		}
		// 3. 处理 JSONObject 类型
		if (javaTypeNameLow.endsWith(".jsonobject") || javaTypeNameLow.equals(JSON_OBJECT)) {
			return JSON.parseObject(jsonStr);
		}
		// 4. 处理 JSONArray 类型
		if (javaTypeNameLow.endsWith(".jsonarray") || javaTypeNameLow.equals(JSON_ARRAY)) {
			return JSON.parseArray(jsonStr);
		}
		// 5. 处理 Map 类型
		if (javaTypeNameLow.equals(MAP_TYPE) || javaTypeNameLow.startsWith("java.util.hashmap")
				|| javaTypeNameLow.startsWith("java.util.linkedhashmap")
				|| javaTypeNameLow.startsWith("java.util.treemap")) {
			JSONObject jsonObj = JSON.parseObject(jsonStr);
			if (javaTypeNameLow.startsWith("java.util.linkedhashmap")) {
				return new LinkedHashMap<>(jsonObj);
			}
			if (javaTypeNameLow.startsWith("java.util.treemap")) {
				return new TreeMap<>(jsonObj);
			}
			// 返回标准 HashMap
			return new HashMap<>(jsonObj);
		}
		// 6. 处理 List 类型
		if (javaTypeNameLow.equals(LIST_TYPE) || javaTypeNameLow.startsWith("java.util.arraylist")
				|| javaTypeNameLow.startsWith("java.util.linkedlist")) {
			if (genericType != null) {
				return JSONArray.parseArray(jsonStr, genericType);
			}
			return JSONArray.parseArray(jsonStr);
		}
		// 7. 处理 Set 类型
		if (javaTypeNameLow.equals(SET_TYPE) || javaTypeNameLow.startsWith("java.util.hashset")
				|| javaTypeNameLow.startsWith("java.util.linkedhashset")
				|| javaTypeNameLow.startsWith("java.util.treeset")) {
			JSONArray jsonArray = JSONArray.parseArray(jsonStr);
			if (jsonArray != null) {
				if (genericType != null) {
					if (javaTypeNameLow.startsWith("java.util.linkedhashset")) {
						return new LinkedHashSet<>(jsonArray.toJavaList(genericType));
					}
					if (javaTypeNameLow.startsWith("java.util.treeset")) {
						return new TreeSet<>(jsonArray.toJavaList(genericType));
					}
					return new HashSet<>(jsonArray.toJavaList(genericType));
				}
				if (javaTypeNameLow.startsWith("java.util.linkedhashset")) {
					return new LinkedHashSet<>(jsonArray);
				}
				if (javaTypeNameLow.startsWith("java.util.treeset")) {
					return new TreeSet<>(jsonArray);
				}
				return new HashSet<>(jsonArray);
			}
			return null;
		}
		// 8. 处理 Collection 类型
		if (javaTypeNameLow.equals(COLLECTION_TYPE)) {
			if (genericType != null) {
				return JSONArray.parseArray(jsonStr, genericType);
			}
			return JSONArray.parseArray(jsonStr);
		}
		// 9. 加载目标类
		Class<?> classType;
		try {
			classType = Class.forName(javaTypeName);
		} catch (ClassNotFoundException e) {
			logger.warn("无法加载类型: {}, 交由框架处理", javaTypeName);
			return null;
		}
		// 10. 处理数组类型
		if (classType.isArray()) {
			return parseJsonArray(jsonStr, classType);
		}
		// 11. 处理自定义对象类型
		if (!BeanUtil.isBaseDataType(classType)) {
			return JSON.parseObject(jsonStr, classType);
		}
		// 其他场景表示非json返回null交框架自行处理
		return null;
	}

	/**
	 * 解析 JSON 数组为目标数组类型
	 */
	private static Object parseJsonArray(String jsonStr, Class<?> arrayType) {
		Class<?> componentType = arrayType.getComponentType();
		JSONArray jsonArray = JSONArray.parseArray(jsonStr);
		if (jsonArray == null || jsonArray.isEmpty()) {
			return Array.newInstance(componentType, 0);
		}
		// 基础类型数组
		if (componentType == int.class) {
			return jsonArray.stream().mapToInt(o -> ((Number) o).intValue()).toArray();
		}
		if (componentType == long.class) {
			return jsonArray.stream().mapToLong(o -> ((Number) o).longValue()).toArray();
		}
		if (componentType == double.class) {
			return jsonArray.stream().mapToDouble(o -> ((Number) o).doubleValue()).toArray();
		}
		if (componentType == boolean.class) {
			boolean[] arr = new boolean[jsonArray.size()];
			for (int i = 0; i < arr.length; i++) {
				arr[i] = jsonArray.getBooleanValue(i);
			}
			return arr;
		}
		// 对象数组
		Object array = Array.newInstance(componentType, jsonArray.size());
		for (int i = 0; i < jsonArray.size(); i++) {
			Array.set(array, i, jsonArray.getObject(i, componentType));
		}
		return array;
	}

	/**
	 * 从 JDBC 值中提取 JSON 字符串
	 */
	private static String extractJsonString(Object jdbcValue) throws SQLException {
		if (jdbcValue == null) {
			return null;
		}
		// 已经是字符串
		if (jdbcValue instanceof String) {
			return (String) jdbcValue;
		}
		String className = jdbcValue.getClass().getName();
		// PostgreSQL PGobject
		if (className.equals("org.postgresql.util.PGobject")) {
			return jdbcValue.toString();
		}
		// Oracle JSON (21c+)
		if (className.startsWith("oracle.sql.json.OracleJson")) {
			return jdbcValue.toString();
		}
		// Clob 类型 (包括 NClob，因为 NClob 继承自 Clob)
		if (jdbcValue instanceof java.sql.Clob) {
			return SqlUtil.clobToString((java.sql.Clob) jdbcValue);
		}
		// byte[] 类型 - 尝试转为UTF-8字符串
		if (jdbcValue instanceof byte[]) {
			String str = new String((byte[]) jdbcValue, java.nio.charset.StandardCharsets.UTF_8);
			if (str.startsWith("\"") && str.endsWith("\"")) {
				return JSON.parseObject(str, String.class);
			}
			return str;
		}
		// Blob 类型
		if (jdbcValue instanceof java.sql.Blob) {
			java.sql.Blob blob = (java.sql.Blob) jdbcValue;
			byte[] bytes = blob.getBytes(1, (int) blob.length());
			String str = new String(bytes, java.nio.charset.StandardCharsets.UTF_8);
			if (str.startsWith("\"") && str.endsWith("\"")) {
				return JSON.parseObject(str, String.class);
			}
			return str;
		}
		// 其他类型尝试 toString
		return jdbcValue.toString();
	}

	/**
	 * 将 Java 对象转换为 JSON 字符串，特殊处理字符串类型直接返回原值
	 * @param value
	 * @return
	 */
	public static String toJSONString(Object value) {
		if (value == null) {
			return null;
		}
		if (value instanceof String) {
			return (String) value;
		}
		return JSON.toJSONString(value);
	}
}
