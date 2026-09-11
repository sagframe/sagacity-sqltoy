package org.sagacity.sqltoy.utils;

import java.lang.reflect.Array;
import java.lang.reflect.Field;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.Locale;
import java.util.TreeMap;
import java.util.TreeSet;

import org.sagacity.sqltoy.model.DBProfile;
import org.sagacity.sqltoy.model.JdbcTypes;
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
	// PGobject 是否可用(静态一次性检测)
	private static final boolean HAS_PG_OBJECT;
	private static final boolean HAS_VB_OBJECT;
	static {
		boolean pgFound;
		try {
			Class.forName("org.postgresql.util.PGobject");
			pgFound = true;
		} catch (ClassNotFoundException e) {
			pgFound = false;
		}
		HAS_PG_OBJECT = pgFound;

		boolean vbFound;
		try {
			Class.forName("cn.com.vastbase.util.PGobject");
			vbFound = true;
		} catch (ClassNotFoundException e) {
			vbFound = false;
		}
		HAS_VB_OBJECT = vbFound;
	}

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
		} else if (dbType == DBType.POSTGRESQL || dbType == DBType.POSTGRESQL14 || dbType == DBType.GAUSSDB
				|| dbType == DBType.OPENGAUSS || dbType == DBType.MOGDB || dbType == DBType.VASTBASE
				|| dbType == DBType.STARDB || dbType == DBType.OSCAR) {
			pst.setNull(paramIndex, java.sql.Types.OTHER);
		} else if (dbType == DBType.ORACLE || dbType == DBType.ORACLE11) {
			// update 2026-9-6 oracle原生JSON列的null绑定:实测二参setNull(OTHER)报ORA-17004
			// (ojdbc拒绝OTHER形态),setNull(VARCHAR)/setNull(NULL)/setString(null)均可写入SQL NULL;
			// update 2026-9-7 21c+服务端以JSON类型setObject绑定(与setJSONValue同因:保证
			// nvl(?,col)包裹两端类型一致,保持null不覆盖语义)
			if (bindOracleJson(dbType, pst, paramIndex, null, true)) {
				return;
			}
			pst.setNull(paramIndex, java.sql.Types.VARCHAR);
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
		// toJSONString对String直接透传(实测sqlserver 2025原生json列拒收二次序列化的
		// 字符串标量形态""{...}"",报JSON text is not properly formatted),对象才做序列化
		String jsonStr = toJSONString(value);
		// update 2026-9-6 实测PGobject不能跨驱动setObject(报Can't infer the SQL type),按连接URL
		// scheme选择同源驱动PGobject(json列不接受varchar隐式转换的openGauss等内核依赖此包装);
		// 非PG系方言(mysql/oracle/db2/sqlserver等)保持字符串绑定;PG系无匹配驱动类型对象时
		// 回退setObject(str,OTHER)交服务器按目标列推断(实测openGauss可行)
		if (isPGFamily(dbType)) {
			Object pgObject = SqlUtil.getPGobjectByConn(pst, (jdbcType == JdbcTypes.JSONB) ? "jsonb" : "json", jsonStr);
			if (pgObject != null) {
				pst.setObject(paramIndex, pgObject);
			} else {
				pst.setObject(paramIndex, jsonStr, java.sql.Types.OTHER);
			}
		} else {
			// update 2026-9-7 oracle 21c+原生json以OracleTypes.JSON类型绑定替代setString:
			// 使update/merge的nvl(?,col)包裹两端类型一致(实测varchar绑定的nvl触发ORA-40478
			// "输出值太大(最大值:0)"),"null不覆盖原值"语义得以保持;驱动无JSON常量(<21c ojdbc)
			// 或服务端<21c回退setString(此时亦无原生json类型,varchar绑定本就正确)
			if (bindOracleJson(dbType, pst, paramIndex, jsonStr, false)) {
				return;
			}
			pst.setString(paramIndex, jsonStr);
		}
	}

	public static void updateJSONValue(Integer dbType, ResultSet rs, String columnName, int jdbcType, Object value)
			throws SQLException {
		// 同setJSONValue:String直接透传,对象才做序列化
		String jsonStr = toJSONString(value);
		if (isPGFamily(dbType)) {
			// update 2026-9-6 按连接URL scheme选择同源驱动PGobject(同setJSONValue,规避跨驱动错配)
			Object pgObject = (rs.getStatement() == null) ? null
					: SqlUtil.getPGobjectByConn(rs.getStatement().getConnection(),
							(jdbcType == JdbcTypes.JSONB) ? "jsonb" : "json", jsonStr);
			if (pgObject != null) {
				rs.updateObject(columnName, pgObject);
			} else {
				rs.updateObject(columnName, jsonStr);
			}
		} else {
			// 赋值语境(同insert),setString即可;仅nvl类型推断语境需JSON类型绑定(见setJSONValue)
			rs.updateString(columnName, jsonStr);
		}
	}

	/**
	 * update 2026-9-6 判定是否为PG系内核方言(PGobject类型包装的适用范围),与SqlUtil.isPGFamily一致
	 */
	private static boolean isPGFamily(Integer dbType) {
		return dbType == DBType.POSTGRESQL || dbType == DBType.POSTGRESQL14 || dbType == DBType.OPENGAUSS
				|| dbType == DBType.MOGDB || dbType == DBType.GAUSSDB || dbType == DBType.STARDB
				|| dbType == DBType.VASTBASE || dbType == DBType.KINGBASE;
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
		String javaTypeNameLow = javaTypeName.toLowerCase(Locale.ROOT);
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
			logger.warn("could not load type: {}, it will be handled by the framework", javaTypeName);
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
		// 已经是字符串:与byte[]形态一致地剥除JSON字符串标量外层引号
		// (h2 json列setString绑定会整体包一层引号成字符串标量,读回文本带引号,
		// ResultUtils.normalizeExtTypeValue已将byte[]归一为该文本形态,此处须对称剥引号)
		if (jdbcValue instanceof String) {
			return unwrapJsonStringScalar((String) jdbcValue);
		}
		String className = jdbcValue.getClass().getName();
		// PostgreSQL PGobject
		if (className.equals("org.postgresql.util.PGobject") || className.equals("cn.com.vastbase.util.PGobject")) {
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
			return unwrapJsonStringScalar(new String((byte[]) jdbcValue, java.nio.charset.StandardCharsets.UTF_8));
		}
		// Blob 类型
		if (jdbcValue instanceof java.sql.Blob) {
			java.sql.Blob blob = (java.sql.Blob) jdbcValue;
			byte[] bytes = blob.getBytes(1, (int) blob.length());
			return unwrapJsonStringScalar(new String(bytes, java.nio.charset.StandardCharsets.UTF_8));
		}
		// update 2026-9-11 clickhouse的JSON类型列getObject返回Map/List(动态JSON类型由驱动
		// 反序列化,getString则被驱动toString为"{name=x}"非JSON形态致fastjson解析失败,实测),
		// 重新序列化为标准JSON文本
		if (jdbcValue instanceof java.util.Map || jdbcValue instanceof java.util.Collection) {
			return JSON.toJSONString(jdbcValue);
		}
		// 其他类型尝试 toString
		return jdbcValue.toString();
	}

	/**
	 * 剥除JSON字符串标量的外层引号:文本以引号起始且结束(如h2 json列setString绑定
	 * 产生的"\"[{...}]\""形态)时解析出标量内部文本,否则原样返回 (update 2026-9-9
	 * 提升为public:ResultUtils的Map行路径h2 json列byte[]归一同款剥引号)
	 */
	public static String unwrapJsonStringScalar(String str) {
		if (str.startsWith("\"") && str.endsWith("\"")) {
			return JSON.parseObject(str, String.class);
		}
		return str;
	}

	/**
	 * 将 Java 对象转换为 JSON 字符串，特殊处理字符串类型直接返回原值
	 * 
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

	// oracle的JSON类型码(OracleTypes.JSON=2016,ojdbc 21c+提供;null表示驱动不可用)
	private static final Integer ORACLE_JSON_TYPE = initOracleJsonType();

	private static Integer initOracleJsonType() {
		try {
			Field f = Class.forName("oracle.jdbc.OracleTypes").getField("JSON");
			return f.getInt(null);
		} catch (Throwable e) {
			// 低版本ojdbc无JSON类型常量(对应服务端亦无原生json类型)
			return null;
		}
	}

	/**
	 * update 2026-9-7 oracle 21c+原生json列以OracleTypes.JSON类型绑定(实测setString绑定下
	 * update/merge的nvl(?,col)包裹触发ORA-40478"输出值太大(最大值:0)",JSON类型绑定后nvl
	 * 两端类型一致,"null不覆盖原值"语义得以保持);按服务端版本门控(21c起才有原生json类型),
	 * 驱动无JSON常量或服务端<21c回退false交由常规setString/setNull(此时varchar绑定本就正确)
	 * 
	 * @param dbType     数据库类型,参见DataSourceUtils.DBType
	 * @param pst        预编译语句(用于获取连接解析服务端版本,DBProfile按URL缓存)
	 * @param paramIndex 参数下标
	 * @param jsonStr    JSON文本(isNull时忽略)
	 * @param isNull     是否绑定null
	 * @return true表示已完成JSON类型绑定,false表示回退常规绑定
	 */
	private static boolean bindOracleJson(Integer dbType, PreparedStatement pst, int paramIndex, String jsonStr,
			boolean isNull) throws SQLException {
		if (dbType == null || dbType != DBType.ORACLE || ORACLE_JSON_TYPE == null) {
			return false;
		}
		try {
			DBProfile profile = DataSourceUtils.getDBProfile(pst.getConnection());
			if (profile.getMajorVersion() < 21) {
				return false;
			}
			pst.setObject(paramIndex, isNull ? null : jsonStr, ORACLE_JSON_TYPE);
			return true;
		} catch (Exception e) {
			// 连接/版本探测失败回退常规绑定
			return false;
		}
	}
}
