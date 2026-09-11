package org.sagacity.sqltoy.utils;

import java.io.Serializable;
import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.lang.reflect.Modifier;
import java.lang.reflect.ParameterizedType;
import java.lang.reflect.Type;
import java.math.BigDecimal;
import java.math.BigInteger;
import java.nio.charset.StandardCharsets;
import java.sql.Array;
import java.sql.Connection;
import java.sql.ResultSet;
import java.sql.Timestamp;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.time.OffsetDateTime;
import java.time.OffsetTime;
import java.time.ZoneOffset;
import java.time.ZonedDateTime;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Date;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Iterator;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.regex.Pattern;

import org.sagacity.sqltoy.SqlToyConstants;
import org.sagacity.sqltoy.callback.EntityUpdateCallback;
import org.sagacity.sqltoy.callback.ReflectPropsHandler;
import org.sagacity.sqltoy.callback.UpdateRowCallback;
import org.sagacity.sqltoy.config.annotation.Column;
import org.sagacity.sqltoy.config.annotation.Entity;
import org.sagacity.sqltoy.config.annotation.OneToMany;
import org.sagacity.sqltoy.config.annotation.OneToOne;
import org.sagacity.sqltoy.config.annotation.SqlToyEntity;
import org.sagacity.sqltoy.config.model.DataType;
import org.sagacity.sqltoy.config.model.EntityMeta;
import org.sagacity.sqltoy.config.model.KeyAndIndex;
import org.sagacity.sqltoy.config.model.TableCascadeModel;
import org.sagacity.sqltoy.exception.DataAccessException;
import org.sagacity.sqltoy.model.IgnoreCaseSet;
import org.sagacity.sqltoy.model.IgnoreKeyCaseMap;
import org.sagacity.sqltoy.model.JdbcTypes;
import org.sagacity.sqltoy.plugins.EntityResultSetProxy;
import org.sagacity.sqltoy.plugins.IUnifyFieldsHandler;
import org.sagacity.sqltoy.plugins.TypeHandler;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * @project sagacity-sqltoy
 * @description 类处理通用工具,提供反射处理
 * @author zhongxuchen
 * @version v1.0,Date:2008-11-10
 * @modify Date:2019-09-05 优化匹配方式，修复setIsXXX的错误
 * @modify Date:2020-06-23 优化convertType(Object, String) 方法
 * @modify Date:2020-07-08 修复convertType(Object, String) 转Long类型时精度丢失问题
 * @modify Date:2021-03-12 支持property中含下划线跟对象方法进行匹配
 * @modify Date:2022-10-19
 *         convertType类型匹配改成int类型的匹配,通过DataType将TypeName转化为int，批量时效率大幅提升
 * @modify Date:2023-08-06 增加对枚举类型的处理
 */
@SuppressWarnings({ "rawtypes", "unchecked" })
public class BeanUtil {
	/**
	 * 定义日志
	 */
	protected final static Logger logger = LoggerFactory.getLogger(BeanUtil.class);

	public final static Pattern ARRAY_PATTERN = Pattern.compile("\\[\\d+\\]$");

	/**
	 * 保存set方法
	 */
	private static ConcurrentHashMap<String, Method> setMethods = new ConcurrentHashMap<>();

	/**
	 * 保存get方法
	 */
	private static ConcurrentHashMap<String, Method> getMethods = new ConcurrentHashMap<>();

	// 保存pojo的级联关系
	private static ConcurrentHashMap<String, List> cascadeModels = new ConcurrentHashMap<>();

	private static ConcurrentHashMap<Class, Method> enumGetKeyMethods = new ConcurrentHashMap<>();
	private static ConcurrentHashMap<Class, Integer> enumGetKeyExists = new ConcurrentHashMap<>();
	private static ConcurrentHashMap<String, Class> enumClassMap = new ConcurrentHashMap<>();

	// 枚举类型取key值的常用方法名称,枚举类中用getValue、getKey、getId等作为取值的都可自动完成映射
	private static String[] enumKeys = { "value", "key", "code", "id", "status", "level", "type" };

	private static final Set<Class<?>> BASE_TYPE = ConcurrentHashMap.newKeySet();

	static {
		BASE_TYPE.add(String.class);
		BASE_TYPE.add(Integer.class);
		BASE_TYPE.add(Byte.class);
		BASE_TYPE.add(Long.class);
		BASE_TYPE.add(Double.class);
		BASE_TYPE.add(Float.class);
		BASE_TYPE.add(Character.class);
		BASE_TYPE.add(Short.class);
		BASE_TYPE.add(Boolean.class);
		BASE_TYPE.add(BigDecimal.class);
		BASE_TYPE.add(BigInteger.class);
		BASE_TYPE.add(Date.class);
		BASE_TYPE.add(Timestamp.class);
		BASE_TYPE.add(LocalDate.class);
		BASE_TYPE.add(LocalDateTime.class);
		BASE_TYPE.add(LocalTime.class);
		BASE_TYPE.add(OffsetDateTime.class);
		BASE_TYPE.add(ZonedDateTime.class);
	}

	// 静态方法避免实例化和继承
	private BeanUtil() {

	}

	/**
	 * 通过value\key\code\id 常规逐个排查方式得到获取key的方法
	 *
	 * @param enumValue 枚举对象，null返回null
	 * @return 枚举key值(存在value/key/code等getKey方法时返回其值；无getKey方法时，
	 *         无自定义字段枚举返回枚举name，其余返回toString值)
	 */
	public static Object getEnumValue(Object enumValue) {
		if (enumValue == null) {
			return null;
		}
		Class enumClass = enumValue.getClass();
		// 使用computeIfAbsent保证原子性,matchEnumKeyMethod只执行一次
		enumGetKeyExists.computeIfAbsent(enumClass, cls -> {
			Method m = matchEnumKeyMethod(cls, enumKeys);
			if (m != null) {
				enumGetKeyMethods.put(cls, m);
			}
			return 1;
		});
		Method getKeyMethod = enumGetKeyMethods.get(enumClass);
		if (getKeyMethod != null) {
			try {
				Object result = getKeyMethod.invoke(enumValue);
				if (result != null) {
					return result;
				}
			} catch (Exception e) {
				// 反射异常交由后续逻辑兜底
			}
		}
		// 无getKey方法时:无自定义字段枚举返回name,其余返回toString
		if (EnumUtil.isEnumWithoutCustomField(enumClass)) {
			return ((Enum) enumValue).name();
		}
		return enumValue.toString();
	}

	/**
	 * 实例化枚举类型
	 *
	 * @param key       枚举key值或name(忽略大小写)
	 * @param enumClass 枚举类型Class
	 * @return 对应的枚举常量，优先按getKey值匹配，匹配不到回退name匹配；key为null或无匹配返回null
	 */
	public static Object newEnumInstance(Object key, Class enumClass) {
		if (key == null) {
			return null;
		}
		String keyStr = key.toString();
		// 使用computeIfAbsent保证原子性,matchEnumKeyMethod只执行一次
		enumGetKeyExists.computeIfAbsent(enumClass, cls -> {
			Method m = matchEnumKeyMethod(cls, enumKeys);
			if (m != null) {
				enumGetKeyMethods.put(cls, m);
			}
			return 1;
		});
		Method getKeyMethod = enumGetKeyMethods.get(enumClass);
		Object[] enums = enumClass.getEnumConstants();
		if (getKeyMethod == null) {
			for (Object enumConstant : enums) {
				if (enumConstant instanceof Enum && keyStr.equalsIgnoreCase(((Enum) enumConstant).name())) {
					return enumConstant;
				}
			}
		} else {
			try {
				for (Object enumVal : enums) {
					Object keyVal = getKeyMethod.invoke(enumVal);
					// getKey值优先,匹配不到回退name匹配(与javadoc"key值或name"一致)
					if (keyStr.equalsIgnoreCase(((Enum) enumVal).name())
							|| (keyVal != null && keyStr.equalsIgnoreCase(keyVal.toString()))) {
						return enumVal;
					}
				}
			} catch (Exception e) {

			}
		}
		return null;
	}

	/**
	 * 找到枚举类型中获取key的方法
	 * 
	 * @param enumClass 枚举类型Class
	 * @param props     候选属性名称数组(如value、key、code、id)，按顺序匹配getXXX或XXX方法
	 * @return 匹配到的无参有返回值方法，未匹配返回null
	 */
	private static Method matchEnumKeyMethod(Class enumClass, String... props) {
		Method[] methods = enumClass.getMethods();
		List<Method> realMeth = new ArrayList<Method>();
		// 有返回值且无参数方法
		for (Method mt : methods) {
			if (!void.class.equals(mt.getReturnType()) && mt.getParameterTypes().length == 0) {
				realMeth.add(mt);
			}
		}
		String prop;
		String name;
		for (int i = 0; i < props.length; i++) {
			prop = props[i].toLowerCase(Locale.ROOT);
			for (Method getKeyMethod : realMeth) {
				name = getKeyMethod.getName().toLowerCase(Locale.ROOT);
				if ("get".concat(prop).equals(name) || prop.equals(name)) {
					return getKeyMethod;
				}
			}
		}
		return null;
	}

	/**
	 * <p>
	 * <li>update 2019-09-05 优化匹配方式，修复setIsXXX的错误</li>
	 * <li>update 2020-04-09 支持setXXX()并返回对象本身,适配链式操作</li>
	 * <li>update 2021-03-12 支持property中含下划线跟对象属性进行匹配</li>
	 * </p>
	 * 
	 * 获取指定名称的方法集
	 * 
	 * @param voClass 目标对象类型
	 * @param props   属性名称数组，与返回数组位置一一对应
	 * @return 与属性对应的set方法数组，未匹配到方法的位置为null
	 */
	public static Method[] matchSetMethods(Class voClass, String... props) {
		int indexSize = props.length;
		Method[] result = new Method[indexSize];
		Method[] methods = voClass.getMethods();
		// 先过滤出全是set且只有一个参数的方法
		List<Method> realMeth = new ArrayList<Method>();
		for (Method mt : methods) {
			// 剔除void 判断条件，存在: this setxxxx(){this.xxx=xxx;return this;}场景
			// if (mt.getParameterTypes().length == 1 &&
			// void.class.equals(mt.getReturnType())) {
			if (mt.getParameterTypes().length == 1) {
				if (mt.getName().startsWith("set")) {
					realMeth.add(mt);
				}
			}
		}
		if (realMeth.isEmpty()) {
			return result;
		}
		Method method;
		String prop;
		boolean matched = false;
		String name;
		Class type;
		String minProp;
		Method underlinMethod;
		int meter = 0;
		boolean isBool;
		int index;
		for (int i = 0; i < indexSize; i++) {
			if (props[i] != null) {
				prop = "set".concat(props[i].toLowerCase(Locale.ROOT));
				matched = false;
				// 将属性名称剔除下划线
				minProp = null;
				if (prop.contains("_")) {
					minProp = prop.replace("_", "");
				}
				meter = 0;
				underlinMethod = null;
				index = 0;
				for (int j = 0; j < realMeth.size(); j++) {
					isBool = false;
					method = realMeth.get(j);
					name = method.getName().toLowerCase(Locale.ROOT);
					// setXXX完全匹配(优先匹配不做下划线替换的场景)
					if (prop.equals(name)) {
						matched = true;
					} else {
						// boolean 类型参数
						type = method.getParameterTypes()[0];
						isBool = (type.equals(Boolean.class) || type.equals(boolean.class)) && prop.startsWith("setis");
						if (isBool && prop.replaceFirst("setis", "set").equals(name)) {
							matched = true;
						}
					}
					// 匹配去除下划线的场景
					if (!matched && minProp != null) {
						if (minProp.equals(name) || (isBool && minProp.replaceFirst("setis", "set").equals(name))) {
							meter++;
							underlinMethod = method;
							index = j;
						}
					}
					if (matched) {
						result[i] = method;
						result[i].setAccessible(true);
						realMeth.remove(j);
						break;
					}
				}
				// 属性剔除下划线后存在唯一匹配
				if (!matched && meter == 1) {
					result[i] = underlinMethod;
					result[i].setAccessible(true);
					realMeth.remove(index);
				}
				if (realMeth.isEmpty()) {
					break;
				}
			}
		}
		return result;
	}

	/**
	 * 获取指定名称的方法集,不区分大小写
	 * 
	 * @param voClass 目标对象类型(支持Record类型)
	 * @param props   属性名称数组，与返回数组位置一一对应
	 * @return 与属性对应的get/is方法数组，未匹配到方法的位置为null
	 */
	public static Method[] matchGetMethods(Class voClass, String... props) {
		int indexSize = props.length;
		Method[] result = new Method[indexSize];
		Method[] methods = voClass.getMethods();
		List<Method> realMeth = new ArrayList<Method>();
		String name;
		// 过滤get 和is 开头的方法
		for (Method mt : methods) {
			if (!void.class.equals(mt.getReturnType()) && mt.getParameterTypes().length == 0) {
				name = mt.getName().toLowerCase(Locale.ROOT);
				if (name.startsWith("get") || name.startsWith("is")) {
					realMeth.add(mt);
				}
			}
		}
		if (realMeth.isEmpty()) {
			return result;
		}
		String prop;
		Method method;
		boolean matched = false;
		Class type;
		String minProp;
		Method underlinMethod;
		int meter = 0;
		boolean isBool;
		int index;
		for (int i = 0; i < indexSize; i++) {
			if (props[i] != null) {
				prop = props[i].toLowerCase(Locale.ROOT);
				matched = false;
				// 将属性名称剔除下划线
				minProp = null;
				if (prop.contains("_")) {
					minProp = prop.replace("_", "");
				}
				meter = 0;
				underlinMethod = null;
				index = 0;
				for (int j = 0; j < realMeth.size(); j++) {
					isBool = false;
					method = realMeth.get(j);
					name = method.getName().toLowerCase(Locale.ROOT);
					// get完全匹配
					if (name.equals("get".concat(prop))) {
						matched = true;
					} else {
						// boolean型 is开头的方法
						type = method.getReturnType();
						isBool = name.startsWith("is") && (type.equals(Boolean.class) || type.equals(boolean.class));
						if (isBool && (name.equals(prop) || name.equals("is".concat(prop)))) {
							matched = true;
						}
					}
					// 匹配属性含下划线场景
					if (!matched && minProp != null) {
						if (name.equals("get".concat(minProp))
								|| (isBool && (name.equals(minProp) || name.equals("is".concat(minProp))))) {
							meter++;
							underlinMethod = method;
							index = j;
						}
					}
					if (matched) {
						result[i] = method;
						result[i].setAccessible(true);
						realMeth.remove(j);
						break;
					}
				}
				// 属性剔除下划线后存在唯一匹配
				if (!matched && meter == 1) {
					result[i] = underlinMethod;
					result[i].setAccessible(true);
					realMeth.remove(index);
				}
				if (realMeth.isEmpty()) {
					break;
				}
			}
		}
		return result;
	}

	/**
	 * 获取指定名称的方法集,不区分大小写
	 * 
	 * @param voClass    目标对象类型(支持Record类型)
	 * @param properties 属性名称数组
	 * @return 各属性对应的java.sql.Types类型值数组，未匹配的位置为Types.NULL
	 */
	public static Integer[] matchMethodsType(Class voClass, String... properties) {
		if (properties == null || properties.length == 0) {
			return null;
		}
		int indexSize = properties.length;
		Method[] methods = voClass.getMethods();
		Integer[] fieldsType = new Integer[indexSize];
		String methodName;
		int methodCnt = methods.length;
		String property;
		Method method;
		for (int i = 0; i < indexSize; i++) {
			fieldsType[i] = java.sql.Types.NULL;
			property = properties[i].toLowerCase(Locale.ROOT);
			for (int j = 0; j < methodCnt; j++) {
				method = methods[j];
				methodName = method.getName().toLowerCase(Locale.ROOT);
				// update 2012-10-25 from equals to ignoreCase
				if (!void.class.equals(method.getReturnType()) && method.getParameterTypes().length == 0
						&& (methodName.equals("get".concat(property)) || methodName.equals("is".concat(property))
								|| (methodName.startsWith("is") && methodName.equals(property)))) {
					fieldsType[i] = getSqlType(method.getReturnType().getSimpleName().toLowerCase(Locale.ROOT));
					break;
				}
			}
		}
		return fieldsType;
	}

	private static int getSqlType(String typeName) {
		if ("string".equals(typeName)) {
			return java.sql.Types.VARCHAR;
		} else if ("integer".equals(typeName)) {
			return java.sql.Types.INTEGER;
		} else if ("bigdecimal".equals(typeName)) {
			return java.sql.Types.DECIMAL;
		} else if ("date".equals(typeName) || "localdate".equals(typeName) || "datetime".equals(typeName)) {
			return java.sql.Types.DATE;
		} else if ("timestamp".equals(typeName) || "localdatetime".equals(typeName)) {
			return java.sql.Types.TIMESTAMP;
		} else if ("offsetdatetime".equals(typeName) || "zoneddatetime".equals(typeName)) {
			return java.sql.Types.TIMESTAMP_WITH_TIMEZONE;
		} else if ("int".equals(typeName)) {
			return java.sql.Types.INTEGER;
		} else if ("long".equals(typeName)) {
			return java.sql.Types.NUMERIC;
		} else if ("double".equals(typeName)) {
			return java.sql.Types.DOUBLE;
		} else if ("clob".equals(typeName)) {
			return java.sql.Types.CLOB;
		} else if ("biginteger".equals(typeName)) {
			return java.sql.Types.BIGINT;
		} else if ("blob".equals(typeName)) {
			return java.sql.Types.BLOB;
		} else if ("byte[]".equals(typeName)) {
			return java.sql.Types.BINARY;
		} else if ("boolean".equals(typeName)) {
			return java.sql.Types.BOOLEAN;
		} else if ("char".equals(typeName)) {
			return java.sql.Types.CHAR;
		} else if ("number".equals(typeName)) {
			return java.sql.Types.NUMERIC;
		} else if ("short".equals(typeName)) {
			return java.sql.Types.NUMERIC;
		} else if ("float".equals(typeName)) {
			return java.sql.Types.FLOAT;
		} else if ("time".equals(typeName)) {
			return java.sql.Types.TIME;
		} else if ("offsettime".equals(typeName)) {
			return java.sql.Types.TIME_WITH_TIMEZONE;
		} else if ("byte".equals(typeName)) {
			return java.sql.Types.TINYINT;
		} else if (typeName.endsWith("[]")) {
			return java.sql.Types.ARRAY;
		} else {
			return java.sql.Types.NULL;
		}
	}

	/**
	 * 类的方法调用
	 * 
	 * @param bean       目标对象
	 * @param methodName 方法名称(忽略大小写)
	 * @param args       方法参数数组
	 * @return 方法执行结果，方法不存在返回null
	 * @throws Exception
	 */
	public static Object invokeMethod(Object bean, String methodName, Object[] args) throws Exception {
		try {
			Method method = getMethod(bean.getClass(), methodName, args == null ? 0 : args.length);
			if (method == null) {
				return null;
			}
			return method.invoke(bean, args);
		} catch (Exception e) {
			throw e;
		}
	}

	public static Object invokeMethod(Object bean, String methodName, Object[] args, Class[] argsTypes)
			throws Exception {
		try {
			Method method = getMethod(bean.getClass(), methodName, args == null ? 0 : args.length, argsTypes);
			if (method == null) {
				return null;
			}
			return method.invoke(bean, args);
		} catch (Exception e) {
			throw e;
		}
	}

	/**
	 * 对象比较
	 * 
	 * @param target   目标对象，null时按引用比较
	 * @param compared 比较对象
	 * @return 相等返回true
	 */
	public static boolean equals(Object target, Object compared) {
		if (null == target) {
			return target == compared;
		}
		return target.equals(compared);
	}

	/**
	 * 用于不同类型数据之间进行比较，判断是否相等,当类型不一致时统一用String类型比较
	 * 
	 * @param target     目标对象，null时按引用比较
	 * @param compared   比较对象
	 * @param ignoreCase true按字符串忽略大小写比较
	 * @return 相等返回true，任一为null时仅当两者同为null返回true
	 */
	public static boolean equalsIgnoreType(Object target, Object compared, boolean ignoreCase) {
		if (target == null || compared == null) {
			return target == compared;
		}
		if (target.getClass().equals(compared.getClass()) && !(target instanceof CharSequence)) {
			return target.equals(compared);
		}
		if (ignoreCase) {
			return target.toString().equalsIgnoreCase(compared.toString());
		}
		return target.toString().equals(compared.toString());
	}

	/**
	 * 比较两个对象的大小
	 * 
	 * @param target   目标对象，null视为最小
	 * @param compared 比较对象
	 * @return 负数表示target小于compared，0相等，正数表示大于；支持日期、数字和字符串比较
	 */
	public static int compare(Object target, Object compared) {
		if (null == target && null == compared) {
			return 0;
		}
		if (null == target) {
			return -1;
		}
		if (null == compared) {
			return 1;
		}
		// 直接相等
		if (target.equals(compared)) {
			return 0;
		}
		// 日期类型
		if ((target instanceof Date || target instanceof LocalDate || target instanceof LocalTime
				|| target instanceof LocalDateTime || target instanceof OffsetDateTime
				|| target instanceof ZonedDateTime)
				|| (compared instanceof Date || compared instanceof LocalDate || compared instanceof LocalTime
						|| compared instanceof LocalDateTime || compared instanceof OffsetDateTime
						|| compared instanceof ZonedDateTime)) {
			return DateUtil.convertDateObject(target).compareTo(DateUtil.convertDateObject(compared));
		} // 数字
		else if ((target instanceof Number) || (compared instanceof Number)) {
			return new BigDecimal(target.toString()).compareTo(new BigDecimal(compared.toString()));
		} else {
			return target.toString().compareTo(compared.toString());
		}
	}

	/**
	 * 提供对象get/set 类型转换
	 * 
	 * @param value     待转换的值，null时按原生类型返回默认值(数字0、false等)
	 * @param typeValue DataType.getType(typeName) 注意typeName不用转小写
	 * @param typeName  getParameterTypes()[0].getTypeName() 没有转大小写
	 * @return 转换后的目标类型值
	 * @throws Exception
	 */
	public static Object convertType(Object value, int jdbcType, int typeValue, String typeName) throws Exception {
		return convertType(null, value, jdbcType, typeValue, typeName, null);
	}

	/**
	 * 类型转换 2022-10-18 已经完成了优化，减少了不必要的判断
	 * 
	 * @param typeHandler 自定义类型处理器，非null时优先通过其完成非常规类型转换
	 * @param value       待转换的值，null时按原生类型返回默认值(数字0、false等)
	 * @param jdbcType    sqlTypes.xxx
	 * @param typeValue   set(Type) set参数的类型
	 * @param typeName    getTypeName()没有转大小写
	 * @param genericType 泛型类型
	 * @return 转换后的目标类型值，无法识别的类型原值返回
	 * @throws Exception
	 */
	public static Object convertType(TypeHandler typeHandler, Object value, int jdbcType, int typeValue,
			String typeName, Class genericType) throws Exception {
		Object paramValue = value;
		// 1
		if (paramValue == null) {
			// 1~5 原生数字类型
			if (typeValue >= DataType.primitiveIntType && typeValue <= DataType.primitiveDoubleType) {
				return 0;
			}
			if (DataType.primitiveBooleanType == typeValue) {
				return false;
			}
			if (DataType.primitiveCharType == typeValue) {
				return " ".charAt(0);
			}
			if (DataType.primitiveByteType == typeValue) {
				return Byte.valueOf("0").byteValue();
			}
			return null;
		}
		// 2 value值的类型跟目标类型一致，直接返回
		if (value.getClass().getTypeName().equals(typeName)) {
			return value;
		}
		// 3 针对非常规类型转换，将jdbc获取的字段结果转为java对象属性对应的类型
		if (typeHandler != null) {
			Object result = typeHandler.toJavaType(typeName, genericType, paramValue);
			if (result != null) {
				return result;
			}
		}
		// 统一处理json字符类型转java对象
		if (jdbcType == JdbcTypes.JSON || jdbcType == JdbcTypes.JSONB) {
			Object result = JSONTypeUtil.jsonToJavaType(jdbcType, typeName, genericType, value);
			if (result != null) {
				return result;
			}
		}
		// 统一处理vector向量类型转java对象(返回null表示交回框架按常规类型处理)
		if (jdbcType == JdbcTypes.VECTOR) {
			Object result = vectorToJavaType(typeName, genericType, paramValue);
			if (result != null) {
				return result;
			}
		}
		// 统一处理geometry空间类型转java对象(返回null表示交回框架按常规类型处理)
		if (jdbcType == JdbcTypes.GEOMETRY) {
			Object result = geometryToJavaType(typeName, paramValue);
			if (result != null) {
				return result;
			}
		}
		// 4 非数组类型,但传递的参数值是数组类型且长度为1提取出数组中的单一值
		if (paramValue.getClass().isArray() && typeValue < DataType.aryCharType) {
			if (typeValue == DataType.stringType && (paramValue instanceof byte[])) {
				// update 2026-9-9 显式UTF-8(原平台默认字符集,与工程内其余byte[]转文本处不一致)
				paramValue = new String((byte[]) paramValue, StandardCharsets.UTF_8);
			} else if (typeValue == DataType.stringType && (paramValue instanceof char[])) {
				paramValue = new String((char[]) paramValue);
			} else if (typeValue == DataType.stringType) {
				// update 2026-9-11 对象/原始数组整体转'[a,b]'文本(与byte[]/char[]文本化同语义):
				// 数组列(java.sql.Array经normalizeExtTypeValue归一为String[]/Float[]等)承接为
				// String属性是vector/数组列的读回契约,多元素不再走单值提取守卫抛异常
				return buildArrayText(paramValue);
			} else {
				Object[] paramAry = CollectionUtil.convertArray(paramValue);
				if (paramAry.length > 1) {
					throw new DataAccessException("can not convert an array with length greater than 1 and type ["
							+ paramValue.getClass().getTypeName() + "] to type [" + typeName + "], please check!");
				}
				paramValue = paramAry[0];
				if (paramValue == null) {
					return null;
				}
			}
		}
		// 5 字符串第一优先
		if (DataType.stringType == typeValue) {
			if (paramValue instanceof java.sql.Clob) {
				return SqlUtil.clobToString((java.sql.Clob) paramValue);
			} else if (paramValue instanceof LocalDate) {
				return DateUtil.formatDate(paramValue, "yyyy-MM-dd");
			} else if (paramValue instanceof LocalDateTime) {
				return DateUtil.formatDate(paramValue, "yyyy-MM-dd HH:mm:ss");
			} else if (paramValue instanceof LocalTime) {
				return DateUtil.formatDate(paramValue, "HH:mm:ss");
			} else if (paramValue instanceof java.util.Date) {
				return DateUtil.formatDate(paramValue, "yyyy-MM-dd HH:mm:ss");
			} else if (paramValue instanceof Enum) {
				return getEnumValue(paramValue).toString();
			} else if (paramValue instanceof OffsetDateTime) {
				return DateUtil.formatDate(paramValue, "yyyy-MM-dd HH:mm:ss");
			} else if (paramValue instanceof ZonedDateTime) {
				return DateUtil.formatDate(paramValue, "yyyy-MM-dd HH:mm:ss");
			} else if (paramValue.getClass().isArray()) {
				// update 2026-9-11 数组值承接为String属性时转'[a,b]'文本(java.sql.Array经
				// normalizeExtTypeValue已归一为原生数组,其toString为对象地址形态无意义),
				// 与33/34/35分支的'[...]'文本解析(parseArrayText)形态对称
				return buildArrayText(paramValue);
			}
			return paramValue.toString();
		}
		// 6 bigDecimal第二优先
		if (DataType.wrapBigDecimalType == typeValue) {
			String valueStr = enumToString(paramValue);
			if ("".equals(valueStr.trim())) {
				return null;
			}
			return new BigDecimal(convertBoolean(valueStr));
		}
		// 7 Integer第三
		if (DataType.wrapIntegerType == typeValue) {
			String valueStr = enumToString(paramValue);
			if ("".equals(valueStr.trim())) {
				return null;
			}
			return Integer.valueOf(convertBoolean(valueStr).split("\\.")[0]);
		}
		// 8 第四优先
		if (DataType.localDateTimeType == typeValue) {
			if (paramValue instanceof LocalDateTime) {
				return (LocalDateTime) paramValue;
			}
			// 修复oracle.sql.timestamp 转localdatetime的缺陷
			if ("oracle.sql.TIMESTAMP".equals(paramValue.getClass().getTypeName())) {
				return oracleTimeStampConvert(paramValue).toLocalDateTime();
			}
			if (paramValue instanceof OffsetDateTime) {
				return ((OffsetDateTime) paramValue).toLocalDateTime();
			}
			if (paramValue instanceof ZonedDateTime) {
				return ((ZonedDateTime) paramValue).toLocalDateTime();
			}
			return DateUtil.asLocalDateTime(DateUtil.convertDateObject(paramValue));
		}
		if (DataType.offsetDateTimeType == typeValue) {
			if (paramValue instanceof OffsetDateTime) {
				return (OffsetDateTime) paramValue;
			}
			if (paramValue instanceof LocalDateTime) {
				return ((LocalDateTime) paramValue).atZone(SqlToyConstants.getZoneId()).toOffsetDateTime();
			}
			if (paramValue instanceof ZonedDateTime) {
				return ((ZonedDateTime) paramValue).toOffsetDateTime();
			}
			if (paramValue instanceof String) {
				return DateUtil.parseZonedDateTime(paramValue.toString()).toOffsetDateTime();
			}
			// 修复oracle.sql.timestamp 转localdatetime的缺陷
			if ("oracle.sql.TIMESTAMP".equals(paramValue.getClass().getTypeName())) {
				return oracleTimeStampConvert(paramValue).toLocalDateTime().atZone(SqlToyConstants.getZoneId())
						.toOffsetDateTime();
			}
			return DateUtil.asLocalDateTime(DateUtil.convertDateObject(paramValue)).atZone(SqlToyConstants.getZoneId())
					.toOffsetDateTime();
		}
		if (DataType.zonedDateTimeType == typeValue) {
			if (paramValue instanceof ZonedDateTime) {
				return (ZonedDateTime) paramValue;
			}
			if (paramValue instanceof OffsetDateTime) {
				return ((OffsetDateTime) paramValue).toZonedDateTime();
			}
			if (paramValue instanceof LocalDateTime) {
				return ((LocalDateTime) paramValue).atZone(SqlToyConstants.getZoneId());
			}
			if (paramValue instanceof String) {
				return DateUtil.parseZonedDateTime(paramValue.toString());
			}
			// 修复oracle.sql.timestamp 转localdatetime的缺陷
			if ("oracle.sql.TIMESTAMP".equals(paramValue.getClass().getTypeName())) {
				return oracleTimeStampConvert(paramValue).toLocalDateTime().atZone(SqlToyConstants.getZoneId());
			}
			return DateUtil.asLocalDateTime(DateUtil.convertDateObject(paramValue)).atZone(SqlToyConstants.getZoneId());
		}
		// 9 第五
		if (DataType.localDateType == typeValue) {
			if (paramValue instanceof LocalDate) {
				return (LocalDate) paramValue;
			}
			if ("oracle.sql.TIMESTAMP".equals(paramValue.getClass().getTypeName())) {
				return DateUtil.asLocalDate(oracleDateConvert(paramValue));
			}
			return DateUtil.asLocalDate(DateUtil.convertDateObject(paramValue));
		}
		// 10 第六
		if (DataType.timestampType == typeValue) {
			if (paramValue instanceof java.sql.Timestamp) {
				return (java.sql.Timestamp) paramValue;
			}
			if (paramValue instanceof OffsetDateTime) {
				return Timestamp.valueOf((((OffsetDateTime) paramValue).toLocalDateTime()));
			}
			if (paramValue instanceof ZonedDateTime) {
				return Timestamp.valueOf((((ZonedDateTime) paramValue).toLocalDateTime()));
			}
			if (paramValue instanceof java.util.Date) {
				return new Timestamp(((java.util.Date) paramValue).getTime());
			}
			if ("oracle.sql.TIMESTAMP".equals(paramValue.getClass().getTypeName())) {
				return oracleTimeStampConvert(paramValue);
			}
			String valueStr = paramValue.toString();
			if ("".equals(valueStr.trim())) {
				return null;
			}
			return new Timestamp(DateUtil.parseString(valueStr).getTime());
		}
		// 11 第7
		if (DataType.wrapLongType == typeValue) {
			String valueStr = enumToString(paramValue);
			if ("".equals(valueStr.trim())) {
				return null;
			}
			// 考虑数据库中存在默认值为0.00 的问题，导致new Long() 报错，因为精度而不用Double.parse(原生long则可以)
			return Long.valueOf(convertBoolean(valueStr).split("\\.")[0]);
		}
		// 12 第8
		if (DataType.primitiveIntType == typeValue) {
			String valueStr = enumToString(paramValue);
			if ("".equals(valueStr.trim())) {
				return 0;
			}
			// 防止小数而不使用Integer.parseInt
			return Double.valueOf(convertBoolean(valueStr)).intValue();
		}
		// 13 第9 字符串转 boolean 型
		if (DataType.wrapBooleanType == typeValue) {
			String valueStr = paramValue.toString();
			if ("true".equals(valueStr.toLowerCase(Locale.ROOT)) || "1".equals(valueStr)) {
				return Boolean.TRUE;
			}
			return Boolean.FALSE;
		}
		// 14 第10 字符串转 boolean 型
		if (DataType.primitiveBooleanType == typeValue) {
			String valueStr = paramValue.toString();
			if ("true".equals(valueStr.toLowerCase(Locale.ROOT)) || "1".equals(valueStr)) {
				return true;
			}
			return false;
		}
		// 15 第11
		if (DataType.dateType == typeValue) {
			if (paramValue instanceof java.util.Date) {
				return (java.util.Date) paramValue;
			}
			if (paramValue instanceof Number) {
				return new java.util.Date(((Number) paramValue).longValue());
			}
			if (paramValue instanceof OffsetDateTime) {
				return java.util.Date.from(((OffsetDateTime) paramValue).toInstant());
			}
			if (paramValue instanceof ZonedDateTime) {
				return java.util.Date.from(((ZonedDateTime) paramValue).toInstant());
			}
			if ("oracle.sql.TIMESTAMP".equals(paramValue.getClass().getTypeName())) {
				return oracleDateConvert(paramValue);
			}
			return DateUtil.parseString(paramValue.toString());
		}
		// 16 第12
		if (DataType.wrapBigIntegerType == typeValue) {
			String valueStr = enumToString(paramValue);
			if ("".equals(valueStr.trim())) {
				return null;
			}
			return new BigInteger(convertBoolean(valueStr).split("\\.")[0]);
		}
		// 17 第13
		if (DataType.wrapDoubleType == typeValue) {
			String valueStr = enumToString(paramValue);
			if ("".equals(valueStr.trim())) {
				return null;
			}
			return Double.valueOf(convertBoolean(valueStr));
		}
		// 18 第13
		if (DataType.localTimeType == typeValue) {
			if (paramValue instanceof LocalTime) {
				return (LocalTime) paramValue;
			}
			if (paramValue instanceof OffsetTime) {
				return ((OffsetTime) paramValue).toLocalTime();
			}
			if ("oracle.sql.TIMESTAMP".equals(paramValue.getClass().getTypeName())) {
				return DateUtil.asLocalTime(oracleTimeStampConvert(paramValue));
			}
			return DateUtil.asLocalTime(DateUtil.convertDateObject(paramValue));
		}
		// update 2025-11-3 增加带时区的时间类型
		if (DataType.offsetTimeType == typeValue) {
			if (paramValue instanceof OffsetTime) {
				return (OffsetTime) paramValue;
			}
			LocalTime localTime;
			if (paramValue instanceof LocalTime) {
				localTime = ((LocalTime) paramValue);
			} else if (paramValue instanceof String) {
				return DateUtil.parseZonedDateTime(paramValue.toString()).toOffsetDateTime().toOffsetTime();
			} else if ("oracle.sql.TIMESTAMP".equals(paramValue.getClass().getTypeName())) {
				localTime = DateUtil.asLocalTime(oracleTimeStampConvert(paramValue));
			} else {
				localTime = DateUtil.asLocalTime(DateUtil.convertDateObject(paramValue));
			}
			// 2. 获取该时区在当前日期的偏移量（需结合日期，这里用当天）
			ZoneOffset offset = SqlToyConstants.getZoneId().getRules()
					.getOffset(LocalDateTime.of(LocalDate.now(), localTime));
			return localTime.atOffset(offset);
		}
		// 19
		if (DataType.primitiveLongType == typeValue) {
			String valueStr = enumToString(paramValue);
			if ("".equals(valueStr.trim())) {
				return 0;
			}
			// 防止小数而不使用Long.parseLong
			return Double.valueOf(convertBoolean(valueStr)).longValue();
		}
		// 20
		if (DataType.primitiveDoubleType == typeValue) {
			String valueStr = enumToString(paramValue);
			if ("".equals(valueStr.trim())) {
				return 0;
			}
			return Double.parseDouble(convertBoolean(valueStr));
		}
		// 21 byte数组
		if (DataType.aryByteType == typeValue) {
			if (paramValue instanceof byte[]) {
				return (byte[]) paramValue;
			}
			// blob类型处理
			if (paramValue instanceof java.sql.Blob) {
				java.sql.Blob blob = (java.sql.Blob) paramValue;
				int size = (int) blob.length();
				if (size > 0) {
					return blob.getBytes(1, size);
				} else {
					return new byte[0];
				}
			}
			return paramValue.toString().getBytes();
		}
		// 22
		if (DataType.wrapFloatType == typeValue) {
			String valueStr = enumToString(paramValue);
			if ("".equals(valueStr.trim())) {
				return null;
			}
			return Float.valueOf(convertBoolean(valueStr));
		}
		// 23
		if (DataType.primitiveFloatType == typeValue) {
			String valueStr = enumToString(paramValue);
			if ("".equals(valueStr.trim())) {
				return 0;
			}
			return Float.parseFloat(convertBoolean(valueStr));
		}
		// 24
		if (DataType.wrapShortType == typeValue) {
			String valueStr = enumToString(paramValue);
			if ("".equals(valueStr.trim())) {
				return null;
			}
			return Short.valueOf(Double.valueOf(convertBoolean(valueStr)).shortValue());
		}
		// 25
		if (DataType.primitiveShortType == typeValue) {
			String valueStr = enumToString(paramValue);
			if ("".equals(valueStr.trim())) {
				return 0;
			}
			return Double.valueOf(convertBoolean(valueStr)).shortValue();
		}
		// 26
		if (DataType.sqlDateType == typeValue) {
			if (paramValue instanceof java.sql.Date) {
				return (java.sql.Date) paramValue;
			}
			if (paramValue instanceof java.util.Date) {
				return new java.sql.Date(((java.util.Date) paramValue).getTime());
			}
			if ("oracle.sql.TIMESTAMP".equals(paramValue.getClass().getTypeName())) {
				return new java.sql.Date(oracleDateConvert(paramValue).getTime());
			}
			if (paramValue instanceof OffsetDateTime) {
				return java.sql.Date.valueOf(((OffsetDateTime) paramValue).toLocalDate());
			}
			if (paramValue instanceof ZonedDateTime) {
				return java.sql.Date.valueOf(((ZonedDateTime) paramValue).toLocalDate());
			}
			String valueStr = paramValue.toString();
			if ("".equals(valueStr.trim())) {
				return null;
			}
			return new java.sql.Date(DateUtil.parseString(valueStr).getTime());
		}
		// 27 clob 类型比较特殊,对外转类型全部转为字符串
		if (DataType.clobType == typeValue) {
			// update 2020-6-23 增加兼容性判断
			if (paramValue instanceof String) {
				return paramValue.toString();
			}
			return SqlUtil.clobToString((java.sql.Clob) paramValue);
		}
		// 28
		if (DataType.sqlTimeType == typeValue) {
			if (paramValue instanceof java.sql.Time) {
				return (java.sql.Time) paramValue;
			}
			if (paramValue instanceof java.util.Date) {
				return new java.sql.Time(((java.util.Date) paramValue).getTime());
			}
			if ("oracle.sql.TIMESTAMP".equals(paramValue.getClass().getTypeName())) {
				return new java.sql.Time(oracleDateConvert(paramValue).getTime());
			}
			if (paramValue instanceof OffsetTime) {
				return java.sql.Time.valueOf(((OffsetTime) paramValue).toLocalTime());
			}
			return new java.sql.Time(DateUtil.parseString(paramValue.toString()).getTime());
		}
		// 29
		if (DataType.primitiveByteType == typeValue) {
			return Byte.valueOf(paramValue.toString()).byteValue();
		}
		// 30
		if (DataType.primitiveCharType == typeValue) {
			String valueStr = paramValue.toString();
			if ("".equals(valueStr.trim())) {
				return " ".charAt(0);
			}
			return valueStr.charAt(0);
		}
		// 31 字符数组
		if (DataType.aryCharType == typeValue) {
			if (paramValue instanceof char[]) {
				return (char[]) paramValue;
			}
			if (paramValue instanceof java.sql.Clob) {
				String str = SqlUtil.clobToString((java.sql.Clob) paramValue);
				return str != null ? str.toCharArray() : null;
			}
			return paramValue.toString().toCharArray();
		}
		// 32 update by 2020-4-13增加Byte类型的处理
		if (DataType.wrapByteType == typeValue) {
			return Byte.valueOf(paramValue.toString());
		}
		// update 2023-5-26 支持List 和Object[] 数组之间互转
		// 33 数组类型
		if (DataType.aryOtherType == typeValue) {
			if (paramValue instanceof Array) {
				return convertArray(((Array) paramValue).getArray(), typeName);
			} else if (paramValue instanceof Collection) {
				return convertArray(((Collection) paramValue).toArray(), typeName);
			} else if (paramValue.getClass().isArray()) {
				// update 2026-9-11 源值已是java数组(如float[]与Float[]、int[]与Integer[]互转)时
				// 交由convertArray反射统一装箱/拆箱,修复前原样返回导致setter调用argument type mismatch
				return convertArray(paramValue, typeName);
			} else if (paramValue instanceof String) {
				// update 2026-9-11 '[a,b]'文本形态还原:数组列值经读路径归一为文本
				// (normalizeExtTypeValue的java.sql.Array分支),find(VO)路径列级归一无法感知
				// 目标属性类型,集合/数组属性在此按括号+逗号解析还原
				Object[] items = parseArrayText((String) paramValue);
				if (items != null) {
					return convertArray(items, typeName);
				}
			}
		}
		// 34 List类型
		if (DataType.listType == typeValue) {
			if (paramValue instanceof Array) {
				Object[] tmp = (Object[]) ((Array) paramValue).getArray();
				// 存在泛型，转换数组类型
				if (genericType != null) {
					return CollectionUtil.arrayToList(convertArray(tmp, genericType.getName().concat("[]")));
				}
				return CollectionUtil.arrayToList(tmp);
			} else if (paramValue instanceof Object[]) {
				// 存在泛型，转换数组类型
				if (genericType != null) {
					return CollectionUtil.arrayToList(convertArray(paramValue, genericType.getName().concat("[]")));
				}
				return CollectionUtil.arrayToList((Object[]) paramValue);
			} else if (paramValue instanceof Set) {
				Object[] tmp = ((Set) paramValue).toArray();
				if (genericType != null) {
					return CollectionUtil.arrayToList(convertArray(tmp, genericType.getName().concat("[]")));
				}
				return CollectionUtil.arrayToList(tmp);
			} else if (paramValue instanceof String) {
				// update 2026-9-11 '[a,b]'文本形态还原(同33数组类型分支注释)
				Object[] items = parseArrayText((String) paramValue);
				if (items != null) {
					if (genericType != null) {
						return CollectionUtil.arrayToList(convertArray(items, genericType.getName().concat("[]")));
					}
					return CollectionUtil.arrayToList(items);
				}
			}
		}
		// 35 Set类型
		if (DataType.setType == typeValue) {
			if (paramValue instanceof Array) {
				Object[] tmp = (Object[]) ((Array) paramValue).getArray();
				// 存在泛型，转换数组类型
				if (genericType != null) {
					return arrayToSet((Object[]) convertArray(tmp, genericType.getName().concat("[]")));
				}
				return arrayToSet(tmp);
			} else if (paramValue instanceof Object[]) {
				if (genericType != null) {
					return arrayToSet((Object[]) convertArray(paramValue, genericType.getName().concat("[]")));
				}
				return arrayToSet((Object[]) paramValue);
			} else if (paramValue instanceof Collection) {
				Object[] tmp = ((Collection) paramValue).toArray();
				// 存在泛型，转换数组类型
				if (genericType != null) {
					return arrayToSet((Object[]) convertArray(tmp, genericType.getName().concat("[]")));
				}
				return arrayToSet(tmp);
			} else if (paramValue instanceof String) {
				// update 2026-9-11 '[a,b]'文本形态还原(同33数组类型分支注释)
				Object[] items = parseArrayText((String) paramValue);
				if (items != null) {
					if (genericType != null) {
						return arrayToSet((Object[]) convertArray(items, genericType.getName().concat("[]")));
					}
					return arrayToSet(items);
				}
			}
		}
		// 36 枚举类型
		if (DataType.enumType == typeValue) {
			// 原子性缓存,避免并发重复加载
			Class enumClass = enumClassMap.computeIfAbsent(typeName, tn -> {
				try {
					return Class.forName(tn);
				} catch (ClassNotFoundException e) {
					throw new DataAccessException("failed to load enum class [" + tn + "]!", e);
				}
			});
			return newEnumInstance(paramValue, enumClass);
		}
		return paramValue;
	}

	/**
	 * 将数据库vector向量类型的值转为java对象属性类型,支持String、float[]、Float[]、double[]、Double[]和List<Float>/List<Double>
	 * 
	 * @param typeName    属性类型名称
	 * @param genericType List属性的单值泛型类型
	 * @param jdbcValue   数据库返回的向量值(pgvector
	 *                    PGvector、oracle.sql.VECTOR、PGobject的toString以及字符串形式均为'[1,2,3]')
	 * @return 返回null表示目标类型无法识别(如org.pgvector.PGvector等驱动专属类型),交回框架按常规类型处理
	 */
	private static Object vectorToJavaType(String typeName, Class genericType, Object jdbcValue) {
		if (jdbcValue == null) {
			return null;
		}
		// update 2026-9-6 达梦等数据库的vector读回为Clob(NClob)形态,toString为对象地址而非文本,
		// 先归一为文本再解析
		if (jdbcValue instanceof java.sql.Clob) {
			jdbcValue = SqlUtil.clobToString((java.sql.Clob) jdbcValue);
		}
		// update 2026-9-11 java.sql.Array经normalizeExtTypeValue归一为原生数组(CH向量承载列
		// Array(Float32)→Float[]等),其toString为对象地址形态,先显式构建'[e1,e2]'文本再按目标解析
		if (jdbcValue.getClass().isArray()) {
			jdbcValue = buildArrayText(jdbcValue);
		}
		String typeNameLow = typeName.toLowerCase(Locale.ROOT);
		boolean isString = typeNameLow.equals("java.lang.string");
		// quickvo默认将vector列映射为Float[],因此同时支持包装类型数组和基本类型数组
		boolean isBoxedFloatAry = typeNameLow.equals("java.lang.float[]");
		boolean isBoxedDoubleAry = typeNameLow.equals("java.lang.double[]");
		boolean isFloatAry = typeNameLow.equals("float[]") || isBoxedFloatAry;
		boolean isDoubleAry = typeNameLow.equals("double[]") || isBoxedDoubleAry;
		boolean isList = typeNameLow.equals("java.util.list") || typeNameLow.startsWith("java.util.arraylist");
		if (!(isString || isFloatAry || isDoubleAry || isList)) {
			return null;
		}
		String vectorStr = jdbcValue instanceof String ? (String) jdbcValue : jdbcValue.toString();
		if (vectorStr == null || vectorStr.trim().isEmpty()) {
			return null;
		}
		if (isString) {
			return vectorStr;
		}
		vectorStr = vectorStr.trim();
		if (!vectorStr.startsWith("[") || !vectorStr.endsWith("]")) {
			return null;
		}
		String content = vectorStr.substring(1, vectorStr.length() - 1).trim();
		// 空向量: [1,2,3] 剔除中括号后无内容
		if (content.isEmpty()) {
			if (isFloatAry) {
				return isBoxedFloatAry ? new Float[0] : new float[0];
			}
			if (isDoubleAry) {
				return isBoxedDoubleAry ? new Double[0] : new double[0];
			}
			return new ArrayList(0);
		}
		String[] items = content.split(",");
		if (isFloatAry) {
			if (isBoxedFloatAry) {
				Float[] result = new Float[items.length];
				for (int i = 0; i < items.length; i++) {
					result[i] = Float.valueOf(items[i].trim());
				}
				return result;
			}
			float[] result = new float[items.length];
			for (int i = 0; i < items.length; i++) {
				result[i] = Float.parseFloat(items[i].trim());
			}
			return result;
		}
		if (isDoubleAry) {
			if (isBoxedDoubleAry) {
				Double[] result = new Double[items.length];
				for (int i = 0; i < items.length; i++) {
					result[i] = Double.valueOf(items[i].trim());
				}
				return result;
			}
			double[] result = new double[items.length];
			for (int i = 0; i < items.length; i++) {
				result[i] = Double.parseDouble(items[i].trim());
			}
			return result;
		}
		// List<Float>/List<Double>按泛型转换,默认Float(向量维度值一般为float32)
		// 注意不能写成三元表达式,Double和Float混用会触发数值提升统一转为Double
		boolean toDouble = (genericType == Double.class);
		List result = new ArrayList(items.length);
		for (String item : items) {
			if (toDouble) {
				result.add(Double.valueOf(Double.parseDouble(item.trim())));
			} else {
				result.add(Float.valueOf(Float.parseFloat(item.trim())));
			}
		}
		return result;
	}

	/**
	 * 将数据库geometry空间类型的值转为java对象属性类型,支持String(WKT/EWKT)和
	 * org.locationtech.jts.geom.Geometry及其子类型(需jts-core可选依赖)
	 *
	 * @param typeName  属性类型名称
	 * @param jdbcValue 数据库返回值(WKT/EWKT字符串、postgis EWKB hex、mysql WKB二进制、
	 *                  PGobject、oracle SDO_GEOMETRY的Struct读回值等)
	 * @return 返回null表示目标类型无法识别或解析失败,交回框架按常规类型处理
	 */
	private static Object geometryToJavaType(String typeName, Object jdbcValue) {
		if (jdbcValue == null) {
			return null;
		}
		// update 2026-9-6 达梦等数据库geometry/json以Clob形态存储读回时,先归一为文本
		// (WKT文本直接透传,不再依赖jts;二进制WKB形态仍走jts解析)
		if (jdbcValue instanceof java.sql.Clob) {
			jdbcValue = SqlUtil.clobToString((java.sql.Clob) jdbcValue);
		}
		String typeNameLow = typeName.toLowerCase(Locale.ROOT);
		// String目标:已是字符串直接返回(postgis下EWKB hex形式的透传,查询侧建议ST_AsText);
		// PGobject(EWKB hex)、byte[](mysql WKB)在JTS在场时统一转为WKT文本
		if (typeNameLow.equals("java.lang.string")) {
			if (jdbcValue instanceof String) {
				return jdbcValue;
			}
			if (GeometryTypeUtil.hasJts()) {
				return GeometryTypeUtil.toWKTString(jdbcValue);
			}
			return null;
		}
		// JTS Geometry及其子类型(Point/LineString/Polygon等)
		if (typeNameLow.startsWith("org.locationtech.jts.geom.") && GeometryTypeUtil.hasJts()) {
			return GeometryTypeUtil.parse(jdbcValue);
		}
		return null;
	}

	/**
	 * 只处理非null值
	 * 
	 * @param paramValue 待转字符串的值(枚举取key值后转字符串)
	 * @return 对应的字符串表示，调用方保证paramValue非null
	 */
	private static String enumToString(Object paramValue) {
		if (paramValue instanceof Enum) {
			// getEnumValue 逻辑也不会返回null
			return getEnumValue(paramValue).toString();
		}
		return paramValue.toString();
	}

	private static HashSet arrayToSet(Object... values) {
		HashSet result = new HashSet();
		for (Object val : values) {
			if (val != null) {
				result.add(val);
			}
		}
		return result;
	}

	public static String convertBoolean(String boolVar) {
		// 忽略大小写,与字符串转Boolean的判断规则保持一致
		if ("true".equalsIgnoreCase(boolVar)) {
			return "1";
		}
		if ("false".equalsIgnoreCase(boolVar)) {
			return "0";
		}
		return boolVar;
	}

	public static Timestamp oracleTimeStampConvert(Object obj) throws Exception {
		return ((oracle.sql.TIMESTAMP) obj).timestampValue();
	}

	public static Date oracleDateConvert(Object obj) throws Exception {
		return ((oracle.sql.TIMESTAMP) obj).dateValue();
	}

	/**
	 * 利用java.lang.reflect并结合页面的property， 从对象中取出对应方法的值，组成一个List
	 * 
	 * @param datas 对象集合(元素可为bean或Map)，null或空返回null
	 * @param props 待提取的属性名称数组，支持xxx.yyy级联形式
	 * @return 二维List，每行为一个对象提取出的属性值列表
	 * @throws RuntimeException
	 */
	public static List reflectBeansToList(List datas, String... props) throws RuntimeException {
		return reflectBeansToList(datas, props, null);
	}

	/**
	 * 切取单列值并以数组返回,服务于loadAll方法
	 * 
	 * @param datas        对象集合
	 * @param propertyName 属性名称
	 * @return 该列的值组成的数组(剔除null值)，无有效数据返回null
	 * @throws RuntimeException
	 */
	public static Object[] sliceToArray(List datas, String propertyName) throws RuntimeException {
		List sliceList = reflectBeansToList(datas, new String[] { propertyName }, null);
		if (sliceList == null || sliceList.isEmpty()) {
			return null;
		}
		List result = new ArrayList();
		List row;
		for (int i = 0; i < sliceList.size(); i++) {
			row = (List) sliceList.get(i);
			if (row != null && row.get(0) != null) {
				result.add(row.get(0));
			}
		}
		if (result.isEmpty()) {
			return null;
		}
		Object[] ary = new Object[result.size()];
		result.toArray(ary);
		return ary;
	}

	/**
	 * 利用java.lang.reflect并结合页面的property， 从对象中取出对应方法的值，组成一个List
	 * 
	 * @param datas               对象集合(元素可为bean或Map)，null或空返回null
	 * @param properties          待提取的属性名称数组，支持xxx.yyy级联形式
	 * @param reflectPropsHandler 属性值处理回调，非null时对每行提取结果做加工处理
	 * @return 二维List，每行为一个对象提取出的属性值列表
	 * @throws RuntimeException
	 */
	public static List reflectBeansToList(List datas, String[] properties, ReflectPropsHandler reflectPropsHandler)
			throws RuntimeException {
		if (null == datas || datas.isEmpty() || null == properties || properties.length < 1) {
			return null;
		}
		List resultList = new ArrayList();
		try {
			Object rowObject = null;
			int methodLength = properties.length;
			// 判断是否存在属性值处理反调
			boolean hasHandler = (reflectPropsHandler != null) ? true : false;
			// 存在反调，则将对象的属性和属性所在的顺序放入hashMap中，便于后面反调中通过属性调用
			if (hasHandler) {
				HashMap<String, Integer> propertyIndexMap = new HashMap<String, Integer>();
				for (int i = 0; i < methodLength; i++) {
					propertyIndexMap.put(properties[i].toLowerCase(Locale.ROOT), i);
				}
				reflectPropsHandler.setPropertyIndexMap(propertyIndexMap);
			}

			// 判断是否有级联
			boolean hasInnerClass = false;
			for (String prop : properties) {
				if (prop.contains(".")) {
					hasInnerClass = true;
					break;
				}
			}
			// 级联含子对象模式(属性名称:staff.name 模式)
			if (hasInnerClass) {
				Object[] rowAry;
				for (int i = 0, n = datas.size(); i < n; i++) {
					rowObject = datas.get(i);
					if (rowObject != null) {
						List rowList = new ArrayList();
						rowAry = reflectBeanToAry(rowObject, properties, null, reflectPropsHandler);
						for (Object cell : rowAry) {
							rowList.add(cell);
						}
						resultList.add(rowList);
					}
				}
				return resultList;
			}

			// 非级联模式
			Method[] realMethods = null;
			boolean inited = false;
			Object[] params = new Object[] {};
			Iterator iter;
			String fieldLow;
			Map.Entry<String, Object> entry;
			Map rowMap;
			for (int i = 0, n = datas.size(); i < n; i++) {
				rowObject = datas.get(i);
				if (null != rowObject) {
					List dataList = new ArrayList();
					// 2021-10-09 支持map类型(逐行判断,容忍首行为null或bean/map混排)
					if (rowObject instanceof Map) {
						if (rowObject instanceof IgnoreKeyCaseMap) {
							rowMap = (IgnoreKeyCaseMap) rowObject;
							for (int j = 0; j < methodLength; j++) {
								dataList.add(rowMap.get(properties[j]));
							}
						} else {
							rowMap = (Map) rowObject;
							for (int j = 0; j < methodLength; j++) {
								// 优先按参数名精确containsKey/get取值，尊重Map自身实现的get语义
								// (如自定义归一化键的Map)；同时避免每个参数都O(n)遍历entrySet
								if (rowMap.containsKey(properties[j])) {
									dataList.add(rowMap.get(properties[j]));
									continue;
								}
								// 考虑key大小写兼容
								fieldLow = properties[j].toLowerCase(Locale.ROOT);
								// 属性key缺失必须补null占位,否则行长度不足,后续按下标消费整体左移错位
								boolean matched = false;
								iter = rowMap.entrySet().iterator();
								while (iter.hasNext()) {
									entry = (Map.Entry<String, Object>) iter.next();
									if (entry.getKey().toLowerCase(Locale.ROOT).equals(fieldLow)) {
										dataList.add(entry.getValue());
										matched = true;
										break;
									}
								}
								if (!matched) {
									dataList.add(null);
								}
							}
						}
					} else {
						// 第一行数据
						if (!inited) {
							realMethods = matchGetMethods(rowObject.getClass(), properties);
							inited = true;
						}
						for (int j = 0; j < methodLength; j++) {
							if (realMethods[j] != null) {
								dataList.add(realMethods[j].invoke(rowObject, params));
							} else {
								dataList.add(null);
							}
						}
					}
					// 反调对数据值进行加工处理
					if (hasHandler) {
						reflectPropsHandler.setRowIndex(i);
						reflectPropsHandler.setRowList(dataList);
						reflectPropsHandler.process();
						resultList.add(reflectPropsHandler.getRowList());
					} else {
						resultList.add(dataList);
					}
				} else {
					logger.debug(
							"BeanUtil.reflectBeansToList method, row:{} data is null, please check the sql if it is a sql query!",
							i);
					resultList.add(null);
				}
			}
		} catch (Exception e) {
			logger.error("exception occurred while building List from java bean by reflection!{}", e.getMessage());
			logger.error("reflectBeansToList method execution failed", e);
			throw new RuntimeException(
					"reflectBeansToList error occurred while building List from java bean!" + e.getMessage());
		}
		return resultList;
	}

	public static Object[] reflectBeanToAry(Object serializable, String... properties) {
		return reflectBeanToAry(serializable, properties, null, null);
	}

	/**
	 * 反射出单个对象中的属性并以对象数组返回
	 * 
	 * @param serializable        目标对象(支持bean、Map，属性支持a.b.c[index]级联形式)
	 * @param properties          待提取的属性名称数组
	 * @param defaultValues       属性值为null时的默认值数组
	 * @param reflectPropsHandler 属性值处理回调，非null时对提取结果做加工处理
	 * @return 属性值组成的对象数组，与properties位置一一对应
	 */
	public static Object[] reflectBeanToAry(Object serializable, String[] properties, Object[] defaultValues,
			ReflectPropsHandler reflectPropsHandler) {
		if (null == serializable || null == properties || properties.length == 0) {
			return null;
		}
		int methodLength = properties.length;
		String[] realProps = new String[methodLength];
		for (int i = 0; i < methodLength; i++) {
			realProps[i] = properties[i].trim();
		}
		Object[] result = new Object[methodLength];
		// 判断是否存在属性值处理反调
		boolean hasHandler = (reflectPropsHandler != null) ? true : false;
		// 存在反调，则将对象的属性和属性所在的顺序放入hashMap中，便于后面反调中通过属性调用
		if (hasHandler && !reflectPropsHandler.initPropsIndexMap()) {
			HashMap<String, Integer> propertyIndexMap = new HashMap<String, Integer>();
			for (int i = 0; i < methodLength; i++) {
				propertyIndexMap.put(realProps[i].toLowerCase(Locale.ROOT), i);
			}
			reflectPropsHandler.setPropertyIndexMap(propertyIndexMap);
		}
		String[] fields;
		Iterator<?> iter;
		Map.Entry<String, Object> entry;
		boolean isMapped = false;
		String fieldLow;
		String realFieldLow;
		Object fieldValue;
		Object tmpValue;
		String keyLowString;
		boolean hasKey = false;
		try {
			KeyAndIndex keyAndIndex;
			// 通过反射提取属性getMethod返回的数据值
			for (int i = 0; i < methodLength; i++) {
				if (realProps[i] != null) {
					// 支持xxxx.xxx 子对象属性提取
					fields = realProps[i].split("\\.");
					fieldValue = serializable;
					hasKey = false;
					// map 类型且key本身就是xxxx.xxxx格式
					if (fieldValue instanceof Map) {
						// 优先按key精确取值，尊重Map自身实现的get语义(如自定义归一化键的Map)
						if (((Map) fieldValue).containsKey(realProps[i])) {
							fieldValue = ((Map) fieldValue).get(realProps[i]);
							hasKey = true;
						} else {
							iter = ((Map) fieldValue).entrySet().iterator();
							fieldLow = realProps[i].toLowerCase(Locale.ROOT);
							while (iter.hasNext()) {
								entry = (Map.Entry<String, Object>) iter.next();
								if (entry.getKey().toLowerCase(Locale.ROOT).equals(fieldLow)) {
									fieldValue = entry.getValue();
									hasKey = true;
									break;
								}
							}
						}
					}
					if (!hasKey) {
						int index = 0;
						int fieldLen = fields.length;
						// a.b.c[index] 切割后逐级向下取值
						for (String field : fields) {
							// 支持map类型 update 2021-01-31
							if (fieldValue instanceof Map) {
								if (fieldValue instanceof IgnoreKeyCaseMap) {
									keyAndIndex = getKeyAndIndex(field);
									realFieldLow = (keyAndIndex == null) ? field : keyAndIndex.getKey();
									tmpValue = ((IgnoreKeyCaseMap) fieldValue).get(realFieldLow);
									// 当前层级取到值，则继续向下
									if (tmpValue != null) {
										if (keyAndIndex != null) {
											fieldValue = getArrayIndexValue(tmpValue, keyAndIndex.getIndex());
										} else {
											fieldValue = tmpValue;
										}
									} else {
										// 没有取到值终止继续逐级取值，则以当前层级到结尾，a.b.c[index]则以b.c[index]
										// a.b.c[index] a.b.c直接是key模式进行尝试
										if (keyAndIndex == null) {
											fieldValue = getMaybeArrayValue((IgnoreKeyCaseMap) fieldValue,
													wrapMapKey(fields, index));
										} else {
											fieldValue = null;
										}
										break;
									}
								} else {
									keyAndIndex = getKeyAndIndex(field);
									realFieldLow = (keyAndIndex == null) ? field : keyAndIndex.getKey();
									// 优先按key精确取值，尊重Map自身实现的get语义(如自定义归一化键的Map)
									if (((Map) fieldValue).containsKey(realFieldLow)) {
										tmpValue = ((Map) fieldValue).get(realFieldLow);
										if (keyAndIndex != null) {
											fieldValue = getArrayIndexValue(tmpValue, keyAndIndex.getIndex());
										} else {
											fieldValue = tmpValue;
										}
									} else {
										iter = ((Map) fieldValue).entrySet().iterator();
										isMapped = false;
										fieldLow = field.toLowerCase(Locale.ROOT);
										keyAndIndex = getKeyAndIndex(fieldLow);
										realFieldLow = (keyAndIndex == null) ? fieldLow : keyAndIndex.getKey();
										while (iter.hasNext()) {
											entry = (Map.Entry<String, Object>) iter.next();
											keyLowString = entry.getKey().toLowerCase(Locale.ROOT);
											if (keyLowString.equals(realFieldLow)) {
												if (keyAndIndex != null) {
													fieldValue = getArrayIndexValue(entry.getValue(),
															keyAndIndex.getIndex());
												} else {
													fieldValue = entry.getValue();
												}
												isMapped = true;
												break;
											}
										}
										// 未匹配到，做a.b.c[index]，key直接是a.b.c尝试
										if (!isMapped) {
											if (keyAndIndex == null) {
												fieldValue = getMaybeArrayValue((Map) fieldValue,
														wrapMapKey(fields, index));
											} else {
												fieldValue = null;
											}
											break;
										}
									}
								}
							} // update 2022-5-25 支持将集合的属性直接映射成数组
								// update 2025-2-26 将instanceof List扩展成Iterable
							else if (fieldValue instanceof Iterable) {
								List tmp = null;
								if (fieldValue instanceof List) {
									tmp = (List) fieldValue;
								} else {
									tmp = new ArrayList();
									Iterator iters = ((Iterable) fieldValue).iterator();
									while (iters.hasNext()) {
										tmp.add(iters.next());
									}
								}
								// a.b.c 在最后一个属性c之前的属性取值
								if (index < fieldLen - 1) {
									fieldValue = sliceToArray(tmp, field);
								} else {
									Object[] fieldValueAry = new Object[tmp.size()];
									for (int j = 0; j < tmp.size(); j++) {
										fieldValueAry[j] = getComplexProperty(tmp.get(j), field);
									}
									fieldValue = fieldValueAry;
								}
							} else if (fieldValue instanceof Object[]) {
								Object[] tmp = (Object[]) fieldValue;
								Object[] fieldValueAry = new Object[tmp.length];
								for (int j = 0; j < tmp.length; j++) {
									fieldValueAry[j] = getComplexProperty(tmp[j], field);
								}
								fieldValue = fieldValueAry;
							} else {
								fieldValue = getComplexProperty(fieldValue, field);
							}
							if (fieldValue == null) {
								break;
							}
							index++;
						}
					}
					result[i] = fieldValue;
				}
			}
		} catch (Exception e) {
			throw new RuntimeException(e.getMessage());
		}
		// 默认值
		if (defaultValues != null) {
			int end = (defaultValues.length > methodLength) ? methodLength : defaultValues.length;
			for (int i = 0; i < end; i++) {
				if (result[i] == null) {
					result[i] = defaultValues[i];
				}
			}
		}
		// 反调对数据值进行加工处理
		if (hasHandler) {
			reflectPropsHandler.setRowIndex(0);
			reflectPropsHandler.setRowData(result);
			reflectPropsHandler.process();
			return reflectPropsHandler.getRowData();
		}
		return result;
	}

	private static String wrapMapKey(String[] names, int start) {
		StringBuilder resultNameBuilder = new StringBuilder();
		for (int i = start; i < names.length; i++) {
			if (i > start) {
				resultNameBuilder.append(".");
			}
			resultNameBuilder.append(names[i]);
		}
		return resultNameBuilder.toString();
	}

	/**
	 * 利用java.lang.reflect并结合页面的property， 从对象中取出对应方法的值，组成一个List
	 * 
	 * @param dataSet             对象集合，null或空返回null
	 * @param properties          待提取的属性名称数组
	 * @param defaultValues       属性值为null时的默认值数组
	 * @param reflectPropsHandler 属性值处理回调，非null时对每行提取结果做加工处理
	 * @return 每行为属性值数组的List
	 */
	public static List<Object[]> reflectBeansToInnerAry(List dataSet, String[] properties, Object[] defaultValues,
			ReflectPropsHandler reflectPropsHandler) {
		if (null == dataSet || dataSet.isEmpty() || null == properties || properties.length < 1) {
			return null;
		}
		List<Object[]> resultList = new ArrayList<Object[]>();
		try {
			int methodLength = properties.length;
			int defaultValueLength = (defaultValues == null) ? 0 : defaultValues.length;
			Method[] realMethods = null;
			boolean inited = false;
			Object rowObject = null;
			Object[] params = new Object[] {};
			// 判断是否存在属性值处理反调
			boolean hasHandler = reflectPropsHandler != null;
			// 存在反调，则将对象的属性和属性所在的顺序放入hashMap中，便于后面反调中通过属性调用
			if (hasHandler) {
				HashMap<String, Integer> propertyIndexMap = new HashMap<String, Integer>();
				for (int i = 0; i < methodLength; i++) {
					propertyIndexMap.put(properties[i].toLowerCase(Locale.ROOT), i);
				}
				reflectPropsHandler.setPropertyIndexMap(propertyIndexMap);
			}
			// 逐行提取属性数据
			for (int i = 0, n = dataSet.size(); i < n; i++) {
				rowObject = dataSet.get(i);
				if (null != rowObject) {
					// 初始化属性对应getMethod的位置,提升反射的效率
					if (!inited) {
						realMethods = matchGetMethods(rowObject.getClass(), properties);
						inited = true;
					}
					Object[] dataAry = new Object[methodLength];
					// 通过反射提取属性getMethod返回的数据值
					for (int j = 0; j < methodLength; j++) {
						if (null != realMethods[j]) {
							dataAry[j] = realMethods[j].invoke(rowObject, params);
							if (null == dataAry[j] && null != defaultValues) {
								dataAry[j] = (j >= defaultValueLength) ? null : defaultValues[j];
							}
						} else {
							if (null == defaultValues) {
								dataAry[j] = null;
							} else {
								dataAry[j] = (j >= defaultValueLength) ? null : defaultValues[j];
							}
						}
					}
					// 反调对数据值进行加工处理
					if (hasHandler) {
						reflectPropsHandler.setRowIndex(i);
						reflectPropsHandler.setRowData(dataAry);
						reflectPropsHandler.process();
						resultList.add(reflectPropsHandler.getRowData());
					} else {
						resultList.add(dataAry);
					}
				} else {
					logger.debug(
							"BeanUtil.reflectBeansToInnerAry method, row:{} data is null, please check the sql if it is a sql query!",
							i);
					resultList.add(null);
				}
			}
		} catch (Exception e) {
			logger.error("exception occurred while building List from java bean by reflection!{}", e.getMessage());
			logger.error("reflectBeansToInnerAry method execution failed", e);
			throw new RuntimeException(e);
		}
		return resultList;
	}

	public static List reflectListToBean(TypeHandler typeHandler, Collection datas, String[] properties,
			String[] columnTypes, Class voClass) {
		int[] indexs = null;
		if (properties != null && properties.length > 0) {
			indexs = new int[properties.length];
			for (int i = 0; i < indexs.length; i++) {
				indexs[i] = i;
			}
		}
		return reflectListToBean(typeHandler, datas, indexs, properties, columnTypes, voClass, true);
	}

	/**
	 * 将二维数组映射到对象集合中
	 * 
	 * @param typeHandler 自定义类型处理器，非null时优先通过其完成类型转换
	 * @param datas       二维数据集合(每行为数组或List)，null或空返回null
	 * @param indexs      数据列与属性的下标对应关系
	 * @param properties  属性名称数组(与indexs一一对应)
	 * @param voClass     目标VO对象类型
	 * @return 映射后的VO对象集合，异常抛出RuntimeException
	 * @throws RuntimeException
	 */
	public static List reflectListToBean(TypeHandler typeHandler, Collection datas, int[] indexs, String[] properties,
			String[] columnTypes, Class voClass) throws RuntimeException {
		return reflectListToBean(typeHandler, datas, indexs, properties, columnTypes, voClass, true);
	}

	/**
	 * 利用java.lang.reflect并结合页面的property， 从对象中取出对应方法的值，组成一个List
	 * 
	 * @param typeHandler     自定义类型处理器，非null时优先通过其完成类型转换
	 * @param datas           二维数据集合(每行为数组或List)，null或空返回null
	 * @param indexs          数据列与属性的下标对应关系
	 * @param properties      属性名称数组(与indexs一一对应)
	 * @param voClass         目标VO对象类型(支持Record类型)
	 * @param autoConvertType true自动适配属性的数据类型并做类型转换
	 * @return 映射后的VO对象集合，异常抛出RuntimeException
	 */
	public static List reflectListToBean(TypeHandler typeHandler, Collection datas, int[] indexs, String[] properties,
			String[] columnTypes, Class voClass, boolean autoConvertType) {
		if (null == datas || datas.isEmpty()) {
			return null;
		}
		if (null == properties || properties.length < 1 || null == voClass || null == indexs || indexs.length == 0
				|| properties.length != indexs.length) {
			throw new IllegalArgumentException(
					"collection or property name array is empty, please check the arguments!");
		}
		if (Modifier.isAbstract(voClass.getModifiers()) || Modifier.isInterface(voClass.getModifiers())) {
			throw new IllegalArgumentException(
					"toClassType [" + voClass.getName() + "] is an abstract class or interface, illegal argument!");
		}
		List resultList = new ArrayList();
		Object cellData = null;
		String propertyName = null;
		try {
			Object rowObject = null;
			Object bean;
			boolean isArray = false;
			int notNullRowIndex = 0;
			int columnTypeLength = (columnTypes == null) ? 0 : columnTypes.length;
			Object[] rowArray = null;
			List rowList = null;
			int indexSize = indexs.length;
			Method[] realMethods = matchSetMethods(voClass, properties);
			String[] methodTypes = new String[indexSize];
			int[] propertySqlTypes = new int[indexSize];
			int[] methodTypeValues = new int[indexSize];
			Class[] genericTypes = new Class[indexSize];
			Map<String, Integer> fieldTypeMap = getClassFieldMap(voClass, properties);
			Type[] types;
			Class methodType;
			// 自动适配属性的数据类型
			if (autoConvertType) {
				String tmpStr;
				for (int i = 0; i < indexSize; i++) {
					propertySqlTypes[i] = java.sql.Types.OTHER;
					if (null != realMethods[i]) {
						methodType = realMethods[i].getParameterTypes()[0];
						methodTypes[i] = methodType.getTypeName();
						methodTypeValues[i] = DataType.getType(methodType);
						types = realMethods[i].getGenericParameterTypes();
						if (properties[i] != null) {
							tmpStr = properties[i].toLowerCase(Locale.ROOT);
							// 先取字段注解上的sqlType
							if (fieldTypeMap.containsKey(tmpStr)) {
								propertySqlTypes[i] = fieldTypeMap.get(tmpStr);
							} // 再取sql查询getColumnType对应的类型,目前主要针对JSON/JSONB、VECTOR，预留GEOMETRY
							else if (columnTypeLength > i && columnTypes[i] != null) {
								tmpStr = columnTypes[i].toUpperCase(Locale.ROOT);
								if (tmpStr.equals("JSON")) {
									propertySqlTypes[i] = JdbcTypes.JSON;
								} else if (tmpStr.equals("JSONB")) {
									propertySqlTypes[i] = JdbcTypes.JSONB;
								} else if (tmpStr.equals("GEOMETRY")) {
									propertySqlTypes[i] = JdbcTypes.GEOMETRY;
								} else if (tmpStr.equals("UUID")) {
									propertySqlTypes[i] = JdbcTypes.UUID;
								} else if (tmpStr.equals("VECTOR") || tmpStr.equals("FLOATVECTOR")) {
									// floatvector为gaussdb企业版的向量类型名
									propertySqlTypes[i] = JdbcTypes.VECTOR;
								} else if (GeometryTypeUtil.isGeometryTypeName(tmpStr)) {
									// geometry空间类型(含mysql的POINT等子类型名)
									propertySqlTypes[i] = JdbcTypes.GEOMETRY;
								}
							}
						}
						if (types.length > 0) {
							if (types[0] instanceof ParameterizedType) {
								genericTypes[i] = (Class) ((ParameterizedType) types[0]).getActualTypeArguments()[0];
							}
						}
					}
				}
			}

			Iterator iter = datas.iterator();
			int index = 0;
			int size;
			while (iter.hasNext()) {
				rowObject = iter.next();
				if (rowObject != null) {
					bean = voClass.getDeclaredConstructor().newInstance();
					if (notNullRowIndex == 0) {
						if (rowObject instanceof Object[]) {
							isArray = true;
						}
					}
					if (isArray) {
						rowArray = (Object[]) rowObject;
						size = rowArray.length;
					} else {
						rowList = (List) rowObject;
						size = rowList.size();
					}
					for (int i = 0; i < indexSize; i++) {
						if (indexs[i] < size) {
							cellData = isArray ? rowArray[indexs[i]] : rowList.get(indexs[i]);
							if (realMethods[i] != null && cellData != null) {
								propertyName = realMethods[i].getName();
								// 类型相同
								if (cellData.getClass().getTypeName().equals(methodTypes[i])) {
									realMethods[i].invoke(bean, cellData);
								} else {
									realMethods[i].invoke(bean,
											autoConvertType
													? convertType(typeHandler, cellData, propertySqlTypes[i],
															methodTypeValues[i], methodTypes[i], genericTypes[i])
													: cellData);
								}
							}
						}
					}
					resultList.add(bean);
					notNullRowIndex++;
				} else {
					logger.debug(
							"BeanUtil.reflectListToBean method, row:{} data is null, please check the sql if it is a sql query!",
							index);
					resultList.add(null);
				}
				index++;
			}
		} catch (Exception e) {
			String errorMsg = "";
			if (propertyName == null) {
				errorMsg = "将集合数据映射到类:" + voClass.getName() + " 异常,请检查类是否正确!" + e.getMessage();
				logger.error(errorMsg);
			} else {
				errorMsg = "将集合数据:[" + cellData + "] 映射到类:" + voClass.getName() + " 的属性:" + propertyName + ":过程异常!"
						+ e.getMessage();
				logger.error(errorMsg);
			}
			throw new RuntimeException(errorMsg, e);
		}
		return resultList;
	}

	// update 2026-9-8 增加带列jdbcTypes的重载:sql查询映射VO时JSON/JSONB/VECTOR/GEOMETRY列
	// 需将jdbc值转换为POJO(如json字符串反序列化为对象字段),原硬编码JdbcTypes.OTHER导致
	// JSON类型标记丢失、反序列化不触发;旧签名委托保持第三方兼容
	public static <T extends Serializable> T reflectRowToBean(TypeHandler typeHandler, Method[] realMethods,
			int[] methodTypeValues, String[] methodTypes, Class[] genericTypes, List rowList, int[] indexs,
			String[] properties, Class<T> voClass, int[] columnJdbcTypes) {
		return reflectRowToBeanInternal(typeHandler, realMethods, methodTypeValues, methodTypes, genericTypes, rowList,
				indexs, properties, voClass, columnJdbcTypes);
	}

	// update 2026-9-8 保留旧9参签名兼容(第三方调用),内部按无列类型信息回退OTHER处理
	public static <T extends Serializable> T reflectRowToBean(TypeHandler typeHandler, Method[] realMethods,
			int[] methodTypeValues, String[] methodTypes, Class[] genericTypes, List rowList, int[] indexs,
			String[] properties, Class<T> voClass) {
		return reflectRowToBeanInternal(typeHandler, realMethods, methodTypeValues, methodTypes, genericTypes, rowList,
				indexs, properties, voClass, null);
	}

	private static <T extends Serializable> T reflectRowToBeanInternal(TypeHandler typeHandler, Method[] realMethods,
			int[] methodTypeValues, String[] methodTypes, Class[] genericTypes, List rowList, int[] indexs,
			String[] properties, Class<T> voClass, int[] columnJdbcTypes) {
		Object cellData = null;
		String propertyName = null;
		Object bean = null;
		try {
			bean = voClass.getDeclaredConstructor().newInstance();
			int indexSize = indexs.length;
			int size = rowList.size();
			for (int i = 0; i < indexSize; i++) {
				if (indexs[i] < size) {
					cellData = rowList.get(indexs[i]);
					if (realMethods[i] != null && cellData != null) {
						propertyName = realMethods[i].getName();
						if (cellData.getClass().getTypeName().equals(methodTypes[i])) {
							realMethods[i].invoke(bean, cellData);
						} else {
							int columnJdbcType = (columnJdbcTypes != null && i < columnJdbcTypes.length
									&& columnJdbcTypes[i] != 0) ? columnJdbcTypes[i] : JdbcTypes.OTHER;
							realMethods[i].invoke(bean, convertType(typeHandler, cellData, columnJdbcType,
									methodTypeValues[i], methodTypes[i], genericTypes[i]));
						}
					}
				}
			}
		} catch (Exception e) {
			String errorMsg = "";
			if (propertyName == null) {
				errorMsg = "将集合数据映射到类:" + voClass.getName() + " 异常,请检查类是否正确!" + e.getMessage();
				logger.error(errorMsg);
			} else {
				errorMsg = "将集合数据:[" + cellData + "] 映射到类:" + voClass.getName() + " 的属性:" + propertyName + ":过程异常!"
						+ e.getMessage();
				logger.error(errorMsg);
			}
			throw new RuntimeException(errorMsg, e);
		}
		return (T) bean;
	}

	public static void batchSetProperties(Collection voList, String[] properties, Object[] values,
			boolean autoConvertType) {
		batchSetProperties(voList, properties, values, autoConvertType, true);
	}

	/**
	 * 批量对集合的属性设置相同的值
	 * 
	 * @param voList          对象集合，null或空直接返回
	 * @param properties      待设置的属性名称数组
	 * @param values          与属性一一对应的值数组
	 * @param autoConvertType true按属性类型自动转换值类型
	 * @param forceUpdate     true强制覆盖(包括null值)，false属性值为null时跳过
	 */
	public static void batchSetProperties(Collection voList, String[] properties, Object[] values,
			boolean autoConvertType, boolean forceUpdate) {
		if (null == voList || voList.isEmpty()) {
			return;
		}
		if (null == properties || properties.length < 1 || null == values || values.length < 1
				|| properties.length != values.length) {
			throw new IllegalArgumentException(
					"collection or property name array is empty, please check the arguments!");
		}
		try {
			int indexSize = properties.length;
			Method[] realMethods = null;
			String[] methodTypes = new String[indexSize];
			int[] methodTypeValues = new int[indexSize];
			Class[] genericTypes = new Class[indexSize];
			Type[] types;
			Iterator iter = voList.iterator();
			Object bean;
			boolean inited = false;
			Class methodType;
			while (iter.hasNext()) {
				bean = iter.next();
				if (null != bean) {
					if (!inited) {
						realMethods = matchSetMethods(bean.getClass(), properties);
						if (autoConvertType) {
							for (int i = 0; i < indexSize; i++) {
								if (realMethods[i] != null) {
									methodType = realMethods[i].getParameterTypes()[0];
									methodTypes[i] = methodType.getTypeName();
									methodTypeValues[i] = DataType.getType(methodType);
									types = realMethods[i].getGenericParameterTypes();
									if (types.length > 0) {
										if (types[0] instanceof ParameterizedType) {
											genericTypes[i] = (Class) ((ParameterizedType) types[0])
													.getActualTypeArguments()[0];
										}
									}
								}
							}
						}
						inited = true;
					}
					for (int i = 0; i < indexSize; i++) {
						if (realMethods[i] != null && (forceUpdate || values[i] != null)) {
							realMethods[i].invoke(bean,
									autoConvertType
											? convertType(null, values[i], JdbcTypes.OTHER, methodTypeValues[i],
													methodTypes[i], genericTypes[i])
											: values[i]);
						}
					}
				}
			}
		} catch (Exception e) {
			logger.error("exception occurred while mapping collection data to java bean!{}", e.getMessage());
			logger.error("batchSetProperties method execution failed", e);
			throw new RuntimeException(
					"batchSetProperties error occurred while mapping collection data to java bean!{}" + e.getMessage(),
					e);
		}
	}

	/**
	 * 对集合属性进行赋值
	 * 
	 * @param voList          对象集合，null或空直接返回
	 * @param properties      待设置的属性名称数组
	 * @param values          每个对象对应的行值数组集合
	 * @param index           每个属性在行值数组中的下标
	 * @param autoConvertType true按属性类型自动转换值类型
	 * @throws RuntimeException
	 */
	public static void mappingSetProperties(Collection voList, String[] properties, List<Object[]> values, int[] index,
			boolean autoConvertType) throws RuntimeException {
		mappingSetProperties(voList, properties, values, index, autoConvertType, true);
	}

	public static void mappingSetProperties(Collection voList, String[] properties, List<Object[]> values, int[] index,
			boolean autoConvertType, boolean forceUpdate) throws RuntimeException {
		if (null == voList || voList.isEmpty()) {
			return;
		}
		if (null == properties || properties.length < 1 || null == values || values.isEmpty()
				|| values.get(0).length < 1 || properties.length != index.length) {
			throw new IllegalArgumentException(
					"collection or property name array is empty, please check the arguments!");
		}
		try {
			int indexSize = properties.length;
			Method[] realMethods = null;
			String[] methodTypes = new String[indexSize];
			int[] methodTypeValues = new int[indexSize];
			Class[] genericTypes = new Class[indexSize];
			Type[] types;
			Iterator iter = voList.iterator();
			Object bean;
			boolean inited = false;
			int rowIndex = 0;
			Object[] rowData;
			Class methodType;
			while (iter.hasNext()) {
				if (rowIndex > values.size() - 1) {
					break;
				}
				rowData = values.get(rowIndex);
				bean = iter.next();
				if (null != bean) {
					if (!inited) {
						realMethods = matchSetMethods(bean.getClass(), properties);
						if (autoConvertType) {
							for (int i = 0; i < indexSize; i++) {
								if (realMethods[i] != null) {
									methodType = realMethods[i].getParameterTypes()[0];
									methodTypes[i] = methodType.getTypeName();
									methodTypeValues[i] = DataType.getType(methodType);
									types = realMethods[i].getGenericParameterTypes();
									if (types.length > 0) {
										if (types[0] instanceof ParameterizedType) {
											genericTypes[i] = (Class) ((ParameterizedType) types[0])
													.getActualTypeArguments()[0];
										}
									}
								}
							}
						}
						inited = true;
					}
					for (int i = 0; i < indexSize; i++) {
						if (realMethods[i] != null && (forceUpdate || rowData[index[i]] != null)) {
							realMethods[i].invoke(bean,
									autoConvertType
											? convertType(null, rowData[index[i]], JdbcTypes.OTHER, methodTypeValues[i],
													methodTypes[i], genericTypes[i])
											: rowData[index[i]]);
						}
					}
				}
				rowIndex++;
			}
		} catch (Exception e) {
			logger.error("exception occurred while mapping collection data to java bean!{}", e.getMessage());
			logger.error("mappingSetProperties method execution failed", e);
			throw new RuntimeException(
					"mappingSetProperties error occurred while mapping collection data to java bean!" + e.getMessage());
		}
	}

	public static String[] matchSetMethodNames(Class voClass) {
		return matchMethodNames(voClass, false);
	}

	private static String[] matchMethodNames(Class voClass, boolean isGet) {
		Method[] methods = voClass.getMethods();
		int methodCnt = methods.length;
		List<String> methodAry = new ArrayList();
		String methodName;
		Method method;
		for (int i = 0; i < methodCnt; i++) {
			method = methods[i];
			methodName = method.getName();
			if (isGet) {
				if ((methodName.startsWith("get") || methodName.startsWith("is"))
						&& !void.class.equals(method.getReturnType()) && method.getParameterTypes().length == 0
						&& !"getclass".equals(methodName.toLowerCase(Locale.ROOT))) {
					methodAry.add(StringUtil.firstToLowerCase(methodName.replaceFirst("get|is", "")));
				}
			} else {
				if (methodName.startsWith("set") && void.class.equals(method.getReturnType())
						&& method.getParameterTypes().length == 1) {
					methodAry.add(StringUtil.firstToLowerCase(methodName.replaceFirst("set", "")));
				}
			}
		}
		return methodAry.toArray(new String[0]);
	}

	/**
	 * 根据方法名称以及参数数量获取类的具体方法
	 * 
	 * @param beanClass  目标类型
	 * @param methodName 方法名称(忽略大小写)
	 * @param argLength  参数个数
	 * @return 匹配到的方法，未找到返回null
	 */
	public static Method getMethod(Class beanClass, String methodName, int argLength) {
		Method[] methods = beanClass.getMethods();
		int methodArgsLength;
		for (Method method : methods) {
			methodArgsLength = 0;
			if (method.getParameterTypes() != null) {
				methodArgsLength = method.getParameterTypes().length;
			}
			if (method.getName().equalsIgnoreCase(methodName) && methodArgsLength == argLength) {
				return method;
			}
		}
		return null;
	}

	/**
	 * 根据方法名称、参数数量以及参数类型获取类的具体方法,支持重载方法精确匹配
	 * 
	 * @param beanClass  目标类型
	 * @param methodName 方法名称(忽略大小写)
	 * @param argLength  参数个数
	 * @param argTypes   参数类型数组,为null时退化为按名称和参数数量匹配
	 * @return 参数类型兼容的匹配方法，无精确匹配时返回首个同名方法，均无返回null
	 */
	public static Method getMethod(Class beanClass, String methodName, int argLength, Class[] argTypes) {
		if (argTypes == null || argTypes.length == 0) {
			return getMethod(beanClass, methodName, argLength);
		}
		Method[] methods = beanClass.getMethods();
		Method fallback = null;
		for (Method method : methods) {
			int methodArgsLength = method.getParameterTypes() != null ? method.getParameterTypes().length : 0;
			if (!method.getName().equalsIgnoreCase(methodName) || methodArgsLength != argLength) {
				continue;
			}
			if (fallback == null) {
				fallback = method;
			}
			Class<?>[] paramTypes = method.getParameterTypes();
			boolean matched = true;
			for (int i = 0; i < argLength; i++) {
				if (argTypes[i] != null && !paramTypes[i].isAssignableFrom(argTypes[i])) {
					matched = false;
					break;
				}
			}
			if (matched) {
				return method;
			}
		}
		return fallback;
	}

	/**
	 * 判断对象是否是基本数据类型对象
	 * 
	 * @param clazz 待判断的类型，null返回false
	 * @return true表示为原生类型或String、数字、日期等基础类型
	 */
	public static boolean isBaseDataType(Class clazz) {
		if (clazz == null) {
			return false;
		}
		return clazz.isPrimitive() || BASE_TYPE.contains(clazz);
	}

	/**
	 * 代替PropertyUtil 和BeanUtils的setProperty方法
	 * 
	 * @param bean     目标对象
	 * @param property 属性名称
	 * @param value    属性值(自动按属性类型转换)
	 * @throws RuntimeException 属性不存在或赋值失败时抛出
	 */
	public static void setProperty(Object bean, String property, Object value) throws RuntimeException {
		setProperty(bean, property, value, JdbcTypes.OTHER);
	}

	/**
	 * 代替PropertyUtil 和BeanUtils的setProperty方法(带列jdbcType语义)
	 *
	 * update 2026-9-10 增加jdbcType入参重载:updateSaveFetch等场景回写的行值已经过
	 * processResultRow归一(json列=String文本、vector/geometry=文本),原固定OTHER类型转换
	 * 不触发json→POJO/List反序列化与vector/geometry→属性类型转换,String直设对象属性报 argument type
	 * mismatch(vastbase G100真库json对象列实爆);调用方传FieldMeta.getType()
	 * (即@Column(type=JdbcTypes.X)注解值,常规列为java.sql.Types码,不命中扩展分支行为不变)
	 *
	 * @param bean     目标对象
	 * @param property 属性名称
	 * @param value    属性值(自动按属性类型转换)
	 * @param jdbcType 列的jdbc类型语义(JdbcTypes.JSON/JSONB/VECTOR/GEOMETRY触发扩展类型转换)
	 * @throws RuntimeException 属性不存在或赋值失败时抛出
	 */
	public static void setProperty(Object bean, String property, Object value, int jdbcType) throws RuntimeException {
		String key = bean.getClass().getName().concat(":set").concat(property);
		// 利用缓存提升方法匹配效率
		Method method = setMethods.computeIfAbsent(key, k -> {
			Method m = matchSetMethods(bean.getClass(), new String[] { property })[0];
			if (m == null) {
				throw new RuntimeException(
						bean.getClass().getName() + " does not have the property [" + property + "]!");
			}
			return m;
		});
		// 将数据类型进行转换再赋值
		String typeName = method.getParameterTypes()[0].getTypeName();
		Type[] types = method.getGenericParameterTypes();
		Class genericType = null;
		if (types.length > 0) {
			if (types[0] instanceof ParameterizedType) {
				genericType = (Class) ((ParameterizedType) types[0]).getActualTypeArguments()[0];
			}
		}
		try {
			method.invoke(bean, convertType(null, value, jdbcType, DataType.getType(method.getParameterTypes()[0]),
					typeName, genericType));
		} catch (Exception e) {
			logger.error("setProperty method execution failed", e);
			throw new RuntimeException(e.getMessage());
		}
	}

	/**
	 * 代替BeanUtils.getProperty 方法
	 * 
	 * @param bean     目标对象(Map类型直接按key取值)
	 * @param property 属性名称
	 * @return 属性值，属性不存在返回null
	 * @throws RuntimeException 方法调用失败时抛出
	 */
	public static Object getProperty(Object bean, String property) throws RuntimeException {
		if (bean instanceof Map) {
			return ((Map) bean).get(property);
		}
		String key = bean.getClass().getName().concat(":get").concat(property);
		// 利用缓存提升方法匹配效率
		Method method = getMethods.get(key);
		if (method == null) {
			Method matched = matchGetMethods(bean.getClass(), new String[] { property })[0];
			if (matched == null) {
				return null;
			}
			method = getMethods.putIfAbsent(key, matched);
			if (method == null) {
				method = matched;
			}
		}
		Object result = null;
		try {
			result = method.invoke(bean);
		} catch (Exception e) {
			logger.error("getProperty method execution failed", e);
			throw new RuntimeException(e.getMessage());
		}
		return result;
	}

	/**
	 * 代替BeanUtils.getProperty 方法,增加item[1] 数组模式调用
	 * 
	 * @param bean     目标对象(Map类型直接按key取值)
	 * @param property 属性名称，支持xxx[1]数组下标形式
	 * @return 属性值(数组形式取下标对应的元素)，属性不存在返回null
	 * @throws RuntimeException 方法调用失败时抛出
	 */
	public static Object getComplexProperty(Object bean, String property) throws RuntimeException {
		KeyAndIndex keyAndIndex = getKeyAndIndex(property);
		String realProperty = (keyAndIndex == null) ? property : keyAndIndex.getKey();
		Object result = null;
		if (bean instanceof Map) {
			if (keyAndIndex != null) {
				result = getArrayIndexValue(((Map) bean).get(realProperty), keyAndIndex.getIndex());
			} else {
				result = ((Map) bean).get(realProperty);
			}
			return result;
		}
		String key = bean.getClass().getName().concat(":get").concat(realProperty);
		// 利用缓存提升方法匹配效率
		Method method = getMethods.get(key);
		if (method == null) {
			Method matched = matchGetMethods(bean.getClass(), new String[] { realProperty })[0];
			if (matched == null) {
				return null;
			}
			method = getMethods.putIfAbsent(key, matched);
			if (method == null) {
				method = matched;
			}
		}
		try {
			result = method.invoke(bean);
			if (result != null && keyAndIndex != null) {
				result = getArrayIndexValue(result, keyAndIndex.getIndex());
			}
		} catch (Exception e) {
			logger.error("getComplexProperty method execution failed", e);
			throw new RuntimeException(e.getMessage());
		}
		return result;
	}

	/**
	 * 为loadByIds提供Entity集合封装,便于将调用方式统一
	 * 
	 * @param <T>         实体类型
	 * @param typeHandler 自定义类型处理器，非null时优先通过其完成主键值类型转换
	 * @param entityMeta  实体元数据(用于提取主键属性)
	 * @param voClass     实体类型
	 * @param ids         数组
	 * @return 主键赋值后的实体对象集合(自动去重)
	 */
	public static <T extends Serializable> List<T> wrapEntities(TypeHandler typeHandler, EntityMeta entityMeta,
			Class<T> voClass, Object... ids) {
		List<T> entities = new ArrayList<T>();
		Set<Object> repeat = new HashSet<Object>();
		try {
			// 获取主键的set方法
			Method method = BeanUtil.matchSetMethods(voClass, entityMeta.getIdArray())[0];
			String typeName = method.getParameterTypes()[0].getTypeName();
			int typeValue = DataType.getType(method.getParameterTypes()[0]);
			Type[] types = method.getGenericParameterTypes();
			Class genericType = null;
			if (types.length > 0) {
				if (types[0] instanceof ParameterizedType) {
					genericType = (Class) ((ParameterizedType) types[0]).getActualTypeArguments()[0];
				}
			}
			T bean;
			for (Object id : ids) {
				// 去除重复
				if (id != null && !repeat.contains(id)) {
					bean = voClass.getDeclaredConstructor().newInstance();
					method.invoke(bean,
							convertType(typeHandler, id, JdbcTypes.OTHER, typeValue, typeName, genericType));
					entities.add(bean);
					repeat.add(id);
				}
			}
		} catch (Exception e) {
			logger.error("exception occurred while mapping collection data to java bean!{}", e.getMessage());
			throw new RuntimeException(e);
		}
		return entities;
	}

	/**
	 * 获取VO对应的实际的entityClass,主要是规避{{}}实例导致无法正确获取类型
	 * 
	 * @param entityClass 传入的Class，{{}}双括号实例化场景逐层向上查找@Entity注解类
	 * @return 实际的实体Class，无法解析时原样返回
	 */
	public static Class getEntityClass(Class entityClass) {
		// update 2020-9-16
		// 主要规避VO对象{{}}模式初始化，导致Class获取变成了内部类(双括号实例化modifiers会等于0)
		// {{}}实例化得到的class是不正确的，所以这里将==0的进入后续判断
		if (entityClass == null || entityClass.equals(Object.class) || entityClass.getModifiers() != 0) {
			return entityClass;
		}
		Class realEntityClass = entityClass;
		// 通过逐层递归来判断是否SqlToy annotation注解所规定的关联数据库的实体类
		// 即@Entity 注解的抽象类
		while (!realEntityClass.equals(Object.class)) {
			// 实体bean
			if (realEntityClass.isAnnotationPresent(SqlToyEntity.class)
					|| (realEntityClass.isAnnotationPresent(Entity.class)
							&& !Modifier.isAbstract(realEntityClass.getModifiers()))) {
				return realEntityClass;
			}
			realEntityClass = realEntityClass.getSuperclass();
		}
		if (entityClass.getModifiers() == 0 && !entityClass.getSuperclass().equals(Object.class)) {
			return entityClass.getSuperclass();
		}
		return entityClass;
	}

	/**
	 * update 2026-9-11 java数组转'[e1,e2]'文本(元素String.valueOf,与parseArrayText解析
	 * 形态对称):数组列值承接为String属性时驱动/原生数组的toString为对象地址形态,须显式构建
	 *
	 * @param arrayValue java数组(对象数组或原始类型数组)
	 * @return '[e1,e2]'文本
	 */
	private static String buildArrayText(Object arrayValue) {
		StringBuilder sb = new StringBuilder("[");
		int len = java.lang.reflect.Array.getLength(arrayValue);
		for (int i = 0; i < len; i++) {
			if (i > 0) {
				sb.append(",");
			}
			sb.append(java.lang.reflect.Array.get(arrayValue, i));
		}
		return sb.append("]").toString();
	}

	/**
	 * update 2026-9-11 解析'[a,b,c]'文本为元素数组(非该形态返回null):数组列值经读路径
	 * 归一为文本(ResultUtils.normalizeExtTypeValue的java.sql.Array分支——CH向量Array列/
	 * PG数组列的驱动对象toString为对象地址,必须归一),find(VO)路径的列级归一无法感知目标
	 * 属性类型,集合/数组属性在convertType的33/34/35分支按本方法还原;元素本身含逗号/中括号
	 * 时无法无损还原(文本化归一的边界,load系直反射路径保留原始Array对象无此限制)
	 *
	 * @param text 待解析文本
	 * @return 元素字符串数组,'[..]'形态不符时返回null(交回常规转换)
	 */
	private static Object[] parseArrayText(String text) {
		if (text == null) {
			return null;
		}
		String content = text.trim();
		if (!content.startsWith("[") || !content.endsWith("]")) {
			return null;
		}
		content = content.substring(1, content.length() - 1).trim();
		if (content.isEmpty()) {
			return new Object[0];
		}
		String[] items = content.split(",");
		Object[] result = new Object[items.length];
		for (int i = 0; i < items.length; i++) {
			result[i] = items[i].trim();
		}
		return result;
	}

	/**
	 * 对常规类型进行转换，超出部分由自定义类型处理器完成(或配置类型完全一致)
	 *
	 * @param values   源数组(支持原始类型数组和对象数组)
	 * @param typeName 目标数组类型全名，如java.lang.String[]、int[]
	 * @return 转换后的目标类型数组，类型一致、不在支持范围或非数组时原样返回
	 */
	public static Object convertArray(Object values, String typeName) {
		if (values == null || typeName == null || !values.getClass().isArray()) {
			return values;
		}
		// 类型完全一致
		if (typeName.equals(values.getClass().getTypeName())) {
			return values;
		}
		Class componentType = getArrayComponentType(typeName);
		// 不在支持类型范围内原样返回
		if (componentType == null) {
			return values;
		}
		// 通过反射Array统一处理原始类型数组和对象数组(自动装箱拆箱),
		// 避免原先(Object[])强转导致原始类型数组在任何分支执行前就抛ClassCastException
		int length = java.lang.reflect.Array.getLength(values);
		Object result = java.lang.reflect.Array.newInstance(componentType, length);
		for (int i = 0; i < length; i++) {
			Object item = java.lang.reflect.Array.get(values, i);
			if (item != null) {
				java.lang.reflect.Array.set(result, i, parseArrayItem(componentType, item.toString()));
			}
		}
		return result;
	}

	/**
	 * 目标数组类型名称对应的组件类型,不在支持范围返回null
	 * 
	 * @param typeName 数组类型全名，如java.lang.String[]、int[]
	 * @return 数组元素组件类型，不在支持范围返回null
	 */
	private static Class getArrayComponentType(String typeName) {
		switch (typeName) {
		case "java.lang.String[]":
			return String.class;
		case "java.lang.Integer[]":
			return Integer.class;
		case "java.lang.Long[]":
			return Long.class;
		case "java.math.BigDecimal[]":
			return BigDecimal.class;
		case "int[]":
			return int.class;
		case "long[]":
			return long.class;
		case "java.lang.Double[]":
			return Double.class;
		case "double[]":
			return double.class;
		case "java.lang.Float[]":
			return Float.class;
		case "float[]":
			return float.class;
		default:
			return null;
		}
	}

	/**
	 * 按目标组件类型解析数组元素字符串值
	 * 
	 * @param componentType 数组元素组件类型
	 * @param item          元素字符串值
	 * @return 解析后的组件类型值，String原样返回，默认按Float解析
	 */
	private static Object parseArrayItem(Class componentType, String item) {
		if (componentType == String.class) {
			return item;
		}
		if (componentType == Integer.class || componentType == int.class) {
			return Integer.valueOf(item);
		}
		if (componentType == Long.class || componentType == long.class) {
			return Long.valueOf(item);
		}
		if (componentType == BigDecimal.class) {
			return new BigDecimal(item);
		}
		if (componentType == Double.class || componentType == double.class) {
			return Double.valueOf(item);
		}
		return Float.valueOf(item);
	}

	/**
	 * 针对loadAll级联加载场景,子表通过主表id集合批量一次性完成加载的，所以子表集合包含了主表集合的全部关联信息
	 * 
	 * @param mainEntities 主对象集合，直接在其上回写级联属性
	 * @param itemEntities 子对象集合
	 * @param cascadeModel 级联配置模型(oneToMany为集合赋值，oneToOne为单对象赋值)
	 * @throws Exception
	 */
	public static void loadAllMapping(List mainEntities, List itemEntities, TableCascadeModel cascadeModel)
			throws Exception {
		if (mainEntities == null || mainEntities.isEmpty() || itemEntities == null || itemEntities.isEmpty()) {
			return;
		}
		boolean isOneToMany = (cascadeModel.getCascadeType() == 1);
		String[] mainProps = cascadeModel.getFields();
		String property = cascadeModel.getProperty();
		String[] mappedFields = cascadeModel.getMappedFields();
		List<Object[]> itemEntityMappedFieldList = new ArrayList<>();
		for (Object itemEntity : itemEntities) {
			itemEntityMappedFieldList.add(reflectBeanToAry(itemEntity, mappedFields, null, null));
		}
		List itemList = null;
		int fieldLength = mainProps.length;
		boolean isEqual = true;
		int itemSize = 0;
		int itemEntitiesSize = itemEntities.size();
		Object itemEntity;
		Object[] mainValues;
		Object[] mappedFieldValues;
		for (Object mainEntity : mainEntities) {
			mainValues = reflectBeanToAry(mainEntity, mainProps, null, null);
			if (isOneToMany) {
				itemList = new ArrayList();
			}
			itemSize = 0;
			for (int j = 0; j < itemEntitiesSize; j++) {
				itemEntity = itemEntities.get(j);
				mappedFieldValues = itemEntityMappedFieldList.get(j);
				isEqual = true;
				for (int k = 0; k < fieldLength; k++) {
					if (null == mainValues[k] || !mainValues[k].equals(mappedFieldValues[k])) {
						isEqual = false;
						break;
					}
				}
				if (isEqual) {
					if (isOneToMany) {
						itemList.add(itemEntity);
					} // oneToOne 直接赋值
					else {
						// update 2022-5-18 增加oneToOne 级联数据校验
						if (itemSize > 0) {
							throw new DataAccessException("please check the @OneToOne cascade configuration of object ["
									+ mainEntity.getClass().getName()
									+ "], the cascade query returned more than 1 row, which is unexpected!");
						}
						setProperty(mainEntity, property, itemEntity);
					}
					// 屏蔽掉，兼容ManyToOne、ManyToMany 场景
					// itemEntities.remove(j);
					// j--;
					itemSize++;
				}
			}
			if (isOneToMany && (itemList != null && !itemList.isEmpty())) {
				setProperty(mainEntity, property, itemList);
			}
		}
	}

	/**
	 * 解析类中的@OneToOne 和@OneToMany注解，服务sql查询结果按对象层次结构进行封装
	 * 
	 * @param entityClass 实体类型(支持多级继承)
	 * @return 含级联注解的字段数组
	 */
	private static Field[] parseCascadeFields(Class entityClass) {
		Set<String> fieldSet = new HashSet<String>();
		List<Field> cascadeFields = new ArrayList<Field>();
		Class classType = entityClass;
		String fieldName;
		while (classType != null && !classType.equals(Object.class)) {
			for (Field field : classType.getDeclaredFields()) {
				fieldName = field.getName().toLowerCase(Locale.ROOT);
				if (!fieldSet.contains(fieldName) && (field.getAnnotation(OneToMany.class) != null
						|| field.getAnnotation(OneToOne.class) != null)) {
					cascadeFields.add(field);
					fieldSet.add(fieldName);
				}
			}
			// 支持多级继承关系
			classType = classType.getSuperclass();
		}
		return cascadeFields.toArray(new Field[0]);
	}

	/**
	 * 获取类的级联关系
	 * 
	 * @param entityClass 实体类型
	 * @return 级联关系配置模型列表(含oneToMany、oneToOne)，无级联返回空列表
	 */
	public static List<TableCascadeModel> getCascadeModels(Class entityClass) {
		String className = entityClass.getName();
		// 原子性缓存,避免并发重复解析级联关系
		return cascadeModels.computeIfAbsent(className, cls -> {
			List<TableCascadeModel> result = new ArrayList<TableCascadeModel>();
			Field[] cascadeFields = parseCascadeFields(entityClass);
			for (Field field : cascadeFields) {
				TableCascadeModel cascadeModel = new TableCascadeModel();
				cascadeModel.setProperty(field.getName());
				OneToMany oneToMany = field.getAnnotation(OneToMany.class);
				OneToOne oneToOne = field.getAnnotation(OneToOne.class);
				if (oneToMany != null) {
					cascadeModel.setCascadeType(1);
					cascadeModel.setFields(oneToMany.fields());
					cascadeModel.setMappedFields(oneToMany.mappedFields());
					cascadeModel.setMappedType(
							(Class) ((ParameterizedType) field.getGenericType()).getActualTypeArguments()[0]);
					if (StringUtil.isNotBlank(oneToMany.notNullField())) {
						cascadeModel.setNotNullField(oneToMany.notNullField());
					}
				} else {
					cascadeModel.setCascadeType(2);
					cascadeModel.setFields(oneToOne.fields());
					cascadeModel.setMappedFields(oneToOne.mappedFields());
					cascadeModel.setMappedType(field.getType());
					if (StringUtil.isNotBlank(oneToOne.notNullField())) {
						cascadeModel.setNotNullField(oneToOne.notNullField());
					}
				}
				result.add(cascadeModel);
			}
			return result;
		});
	}

	public static Object getMaybeArrayValue(Map value, String property) {
		KeyAndIndex keyAndIndex = getKeyAndIndex(property);
		if (keyAndIndex == null) {
			return value.get(property);
		} else {
			return getArrayIndexValue(value.get(keyAndIndex.getKey()), keyAndIndex.getIndex());
		}
	}

	public static KeyAndIndex getKeyAndIndex(String property) {
		if (property == null) {
			return null;
		}
		if (!StringUtil.matches(property, ARRAY_PATTERN)) {
			return null;
		}
		KeyAndIndex result = new KeyAndIndex();
		int lastIndex = property.lastIndexOf("[");
		result.setKey(property.substring(0, lastIndex));
		result.setIndex(Integer.parseInt(property.substring(lastIndex + 1, property.length() - 1)));
		return result;
	}

	/**
	 * 将对象转数组获取index列对应的值
	 * 
	 * @param result 目标对象，支持Object[]、Collection和Iterable类型
	 * @param index  数组下标
	 * @return 对应下标的元素，result为null、非集合类型或下标越界返回null
	 */
	public static Object getArrayIndexValue(Object result, int index) {
		if (result == null) {
			return null;
		}
		Object[] ary = null;
		if (result instanceof Object[]) {
			ary = (Object[]) result;
		} else if (result instanceof Collection) {
			ary = ((Collection) result).toArray();
		} else if (result instanceof Iterable) {
			ary = CollectionUtil.iterableToArray((Iterable) result);
		}
		if (ary != null && ary.length > index) {
			return ary[index];
		}
		return null;
	}

	/**
	 * 根据save/update/saveOrUpdate操作类型提取公共字段属性
	 * 
	 * @param unifyFieldsHandler 统一字段处理器，null返回空Map
	 * @param fieldsAry          实体全部字段名称数组
	 * @param type               1:save;2:update;3:saveOrUpdate
	 * @return 公共字段名称与其在fieldsAry中下标的对应Map
	 */
	public static Map<String, Integer> getUnifyFieldIndex(IUnifyFieldsHandler unifyFieldsHandler, String[] fieldsAry,
			int type) {
		Map<String, Integer> fieldIndexMap = new HashMap<>();
		if (unifyFieldsHandler == null) {
			return fieldIndexMap;
		}
		IgnoreCaseSet fieldSet = new IgnoreCaseSet();
		// 新增时，公共字段
		if (type == 1) {
			if (unifyFieldsHandler.createUnifyFields() != null) {
				fieldSet.addAll(unifyFieldsHandler.createUnifyFields().keySet());
			}
		} // 修改
		else if (type == 2) {
			if (unifyFieldsHandler.updateUnifyFields() != null) {
				fieldSet.addAll(unifyFieldsHandler.updateUnifyFields().keySet());
			}
		} // saveOrUpdate
		else if (type == 3) {
			if (unifyFieldsHandler.createUnifyFields() != null) {
				fieldSet.addAll(unifyFieldsHandler.createUnifyFields().keySet());
			}
			if (unifyFieldsHandler.updateUnifyFields() != null) {
				fieldSet.addAll(unifyFieldsHandler.updateUnifyFields().keySet());
			}
		}
		for (int i = 0; i < fieldsAry.length; i++) {
			// 不区分大小写包含
			if (fieldSet.contains(fieldsAry[i])) {
				fieldIndexMap.put(fieldsAry[i], i);
			}
		}
		return fieldIndexMap;
	}

	/**
	 * 回写POJO的：创建人、创建时间、修改人、修改时间等公共字段
	 * 
	 * @param entity        目标实体对象
	 * @param fieldIndexMap 公共字段名称与其下标的对应Map
	 * @param values        与字段下标对应的公共字段值数组
	 */
	public static void backWriteUnifyFields(Object entity, Map<String, Integer> fieldIndexMap, Object[] values) {
		if (null == fieldIndexMap || fieldIndexMap.isEmpty() || null == values || values.length == 0) {
			return;
		}
		List entities = new ArrayList();
		List valueList = new ArrayList();
		entities.add(entity);
		valueList.add(values);
		batchBackWriteUnifyFields(entities, fieldIndexMap, valueList);
	}

	/**
	 * 批量回写POJO的：创建人、创建时间、修改人、修改时间等公共字段
	 * 
	 * @param entitis       实体对象集合，直接在其上回写公共字段
	 * @param fieldIndexMap 公共字段名称与其下标的对应Map
	 * @param values        每个实体对应的公共字段值数组集合
	 */
	public static void batchBackWriteUnifyFields(List entitis, Map<String, Integer> fieldIndexMap,
			List<Object[]> values) {
		if (null == fieldIndexMap || fieldIndexMap.isEmpty() || null == values || values.isEmpty()) {
			return;
		}
		String[] fields = new String[fieldIndexMap.size()];
		int[] indexs = new int[fieldIndexMap.size()];
		int i = 0;
		for (Map.Entry<String, Integer> entry : fieldIndexMap.entrySet()) {
			fields[i] = entry.getKey();
			indexs[i] = entry.getValue();
			i++;
		}
		mappingSetProperties(entitis, fields, values, indexs, true, false);
	}

	/**
	 * 根据字段名称提取类字段上@Column注解中的jdbcType，便于识别json和jsonb等特殊类型
	 * 
	 * @param voClass    目标类型
	 * @param properties 待查询的字段名称数组
	 * @return 小写字段名与@Column.type值的对应Map，无注解或无匹配字段的属性不包含在内
	 */
	public static Map<String, Integer> getClassFieldMap(Class voClass, String[] properties) {
		Map<String, Integer> fieldMap = new HashMap<>();
		Field[] allFields = voClass.getDeclaredFields();
		Set<String> fieldNameSet = new HashSet<>();
		for (String str : properties) {
			fieldNameSet.add(str.toLowerCase(Locale.ROOT));
		}
		String strLow;
		for (Field field : allFields) {
			strLow = field.getName().toLowerCase(Locale.ROOT);
			if (fieldNameSet.contains(strLow)) {
				field.setAccessible(true);
				Column column = field.getAnnotation(Column.class);
				if (column != null) {
					fieldMap.put(strLow, column.type());
				}
			}
		}
		return fieldMap;
	}

	/**
	 * 适配转换，传入实体元数据、实体Class + 业务对象回调，输出原生UpdateRowHandler
	 * 
	 * @param entityMeta  实体元数据（上层预先获取缓存实例）
	 * @param entityClass 实体DTO类
	 * @param callback    面向对象行更新回调
	 * @return SqlToy原生UpdateRowHandler
	 */
	public static <T extends Serializable> UpdateRowCallback toSqlToyHandler(EntityMeta entityMeta,
			Class<? extends T> entityClass, EntityUpdateCallback<T> callback) {
		return (TypeHandler typeHandler, Integer dbType, Connection conn, ResultSet rs, int index) -> {
			T proxyEntity = EntityResultSetProxy.createProxy(typeHandler, dbType, conn, rs, entityClass, entityMeta);
			callback.update(proxyEntity, index);
		};
	}
}