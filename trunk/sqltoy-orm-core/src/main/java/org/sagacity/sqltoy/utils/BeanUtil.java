package org.sagacity.sqltoy.utils;

import static java.lang.System.err;

import java.io.BufferedReader;
import java.io.Serializable;
import java.io.StringWriter;
import java.lang.reflect.Constructor;
import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.lang.reflect.Modifier;
import java.lang.reflect.ParameterizedType;
import java.lang.reflect.RecordComponent;
import java.lang.reflect.Type;
import java.math.BigDecimal;
import java.math.BigInteger;
import java.sql.Array;
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
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.regex.Pattern;
import org.sagacity.sqltoy.SqlToyConstants;
import org.sagacity.sqltoy.callback.ReflectPropsHandler;
import org.sagacity.sqltoy.config.annotation.Entity;
import org.sagacity.sqltoy.config.annotation.OneToMany;
import org.sagacity.sqltoy.config.annotation.OneToOne;
import org.sagacity.sqltoy.config.annotation.SqlToyEntity;
import org.sagacity.sqltoy.config.model.DataType;
import org.sagacity.sqltoy.config.model.EntityMeta;
import org.sagacity.sqltoy.config.model.KeyAndIndex;
import org.sagacity.sqltoy.config.model.PropertyType;
import org.sagacity.sqltoy.config.model.TableCascadeModel;
import org.sagacity.sqltoy.exception.DataAccessException;
import org.sagacity.sqltoy.model.IgnoreCaseSet;
import org.sagacity.sqltoy.model.IgnoreKeyCaseMap;
import org.sagacity.sqltoy.plugins.IUnifyFieldsHandler;
import org.sagacity.sqltoy.plugins.TypeHandler;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * @project sagacity-sqltoy
 * @description 类处理通用工具,提供反射处理
 * @author zhongxuchen
 * @version v1.0,Date:2008-11-10
 * @modify data:2019-09-05 优化匹配方式，修复setIsXXX的错误
 * @modify data:2020-06-23 优化convertType(Object, String) 方法
 * @modify data:2020-07-08 修复convertType(Object, String) 转Long类型时精度丢失问题
 * @modify data:2021-03-12 支持property中含下划线跟对象方法进行匹配
 * @modify data:2022-10-19
 *         convertType类型匹配改成int类型的匹配,通过DataType将TypeName转化为int，批量时效率大幅提升
 * @modify data:2023-08-06 增加对枚举类型的处理
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
	private static ConcurrentHashMap<String, Method> setMethods = new ConcurrentHashMap<String, Method>();

	/**
	 * 保存get方法
	 */
	private static ConcurrentHashMap<String, Method> getMethods = new ConcurrentHashMap<String, Method>();

	// 保存pojo的级联关系
	private static ConcurrentHashMap<String, List> cascadeModels = new ConcurrentHashMap<String, List>();

	private static ConcurrentHashMap<Class, Method> enumGetKeyMethods = new ConcurrentHashMap<Class, Method>();
	private static ConcurrentHashMap<Class, Integer> enumGetKeyExists = new ConcurrentHashMap<Class, Integer>();
	private static ConcurrentHashMap<String, Class> enumClassMap = new ConcurrentHashMap<String, Class>();

	// 枚举类型取key值的常用方法名称,枚举类中用getValue、getKey、getId等作为取值的都可自动完成映射
	private static String[] enumKeys = { "value", "key", "code", "id", "status", "level", "type" };

	private static ConcurrentHashMap<Class, PropertyType[]> recordProperties = new ConcurrentHashMap<Class, PropertyType[]>();

	// 静态方法避免实例化和继承
	private BeanUtil() {

	}

	/**
	 * @TODO 通过value\key\code\id 常规逐个排查方式得到获取key的方法
	 * @param enumValue
	 * @return
	 */
	public static Object getEnumValue(Object enumValue) {
		if (enumValue == null) {
			return null;
		}
		Class enumClass = enumValue.getClass();
		// 无自定义属性
		if (EnumUtil.isEnumWithoutCustomField(enumClass)) {
			return ((Enum) enumValue).name();
		}
		Object result = null;
		Method getKeyMethod;
		// Map缓存，不会每次都循环
		if (enumGetKeyExists.containsKey(enumClass)) {
			getKeyMethod = enumGetKeyMethods.get(enumClass);
			if (getKeyMethod != null) {
				try {
					result = getKeyMethod.invoke(enumValue);
				} catch (Exception e) {

				}
			}
		} else {
			getKeyMethod = matchEnumKeyMethod(enumClass, enumKeys);
			if (getKeyMethod != null) {
				try {
					result = getKeyMethod.invoke(enumValue);
				} catch (Exception e) {

				}
				enumGetKeyMethods.put(enumClass, getKeyMethod);
			}
			enumGetKeyExists.put(enumClass, 1);
		}
		if (result == null) {
			return enumValue.toString();
		}
		return result;
	}

	/**
	 * @TODO 实例化枚举类型
	 * @param key
	 * @param enumClass
	 * @return
	 */
	public static Object newEnumInstance(Object key, Class enumClass) {
		if (key == null) {
			return null;
		}
		String keyStr = key.toString();
		Method getKeyMethod = null;
		if (enumGetKeyExists.containsKey(enumClass)) {
			getKeyMethod = enumGetKeyMethods.get(enumClass);
		} else {
			getKeyMethod = matchEnumKeyMethod(enumClass, enumKeys);
			if (getKeyMethod != null) {
				enumGetKeyMethods.put(enumClass, getKeyMethod);
			}
			enumGetKeyExists.put(enumClass, 1);
		}
		Object[] enums = enumClass.getEnumConstants();
		if (getKeyMethod == null) {
			for (Object enumConstant : enums) {
				if(enumConstant instanceof Enum<?> enumVal && keyStr.equalsIgnoreCase(enumVal.name())){
					return enumVal;
				}
			}
		} else {
			try {
				for (Object enumVal : enums) {
					if (keyStr.equalsIgnoreCase(getKeyMethod.invoke(enumVal).toString())) {
						return enumVal;
					}
				}
			} catch (Exception e) {

			}
		}
		return null;
	}

	/**
	 * @TODO 找到枚举类型中获取key的方法
	 * @param enumClass
	 * @param props
	 * @return
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
			prop = props[i].toLowerCase();
			for (Method getKeyMethod : realMeth) {
				name = getKeyMethod.getName().toLowerCase();
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
	 * @todo 获取指定名称的方法集
	 * @param voClass
	 * @param props
	 * @return
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
				prop = "set".concat(props[i].toLowerCase());
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
					name = method.getName().toLowerCase();
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
	 * @todo 获取指定名称的方法集,不区分大小写
	 * @param voClass
	 * @param props
	 * @return
	 */
	public static Method[] matchGetMethods(Class voClass, String... props) {
		int indexSize = props.length;
		Method[] result = new Method[indexSize];
		// update 2025/07/30 支持Record类型
		if (voClass.isRecord()) {
			RecordComponent[] components = voClass.getRecordComponents();
			String propName;
			for (int i = 0; i < indexSize; i++) {
				propName = props[i];
				for (RecordComponent component : components) {
					if (propName.equalsIgnoreCase(component.getName())) {
						result[i] = component.getAccessor();
						break;
					}
				}
			}
			return result;
		}
		Method[] methods = voClass.getMethods();
		List<Method> realMeth = new ArrayList<Method>();
		String name;
		// 过滤get 和is 开头的方法
		for (Method mt : methods) {
			if (!void.class.equals(mt.getReturnType()) && mt.getParameterTypes().length == 0) {
				name = mt.getName().toLowerCase();
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
				prop = props[i].toLowerCase();
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
					name = method.getName().toLowerCase();
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
	 * @todo 获取指定名称的方法集,不区分大小写
	 * @param voClass
	 * @param properties
	 * @return
	 */
	public static Integer[] matchMethodsType(Class voClass, String... properties) {
		if (properties == null || properties.length == 0) {
			return null;
		}
		if (voClass.isRecord()) {
			return matchRecordType(voClass, properties);
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
			property = properties[i].toLowerCase();
			for (int j = 0; j < methodCnt; j++) {
				method = methods[j];
				methodName = method.getName().toLowerCase();
				// update 2012-10-25 from equals to ignoreCase
				if (!void.class.equals(method.getReturnType()) && method.getParameterTypes().length == 0
						&& (methodName.equals("get".concat(property)) || methodName.equals("is".concat(property))
								|| (methodName.startsWith("is") && methodName.equals(property)))) {
					fieldsType[i] = getSqlType(method.getReturnType().getSimpleName().toLowerCase());
					break;
				}
			}
		}
		return fieldsType;
	}

	private static Integer[] matchRecordType(Class voClass, String... properties) {
		if (properties == null || properties.length == 0) {
			return null;
		}
		int indexSize = properties.length;
		Integer[] fieldsType = new Integer[indexSize];
		RecordComponent[] components = voClass.getRecordComponents();
		String propName;
		for (int i = 0; i < indexSize; i++) {
			propName = properties[i];
			for (RecordComponent component : components) {
				if (propName.equalsIgnoreCase(component.getName())) {
					fieldsType[i] = getSqlType(component.getType().getSimpleName().toLowerCase());
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
	 * @todo 类的方法调用
	 * @param bean
	 * @param methodName
	 * @param args
	 * @return
	 * @throws Exception
	 */
	public static Object invokeMethod(Object bean, String methodName, Object... args) throws Exception {
		try {
			Method method = getMethod(bean.getClass(), methodName, args == null ? 0 : args.length);
			if (method == null) {
				return null;
			}
			return method.invoke(bean, args);
		} catch (Exception e) {
			e.printStackTrace();
			throw e;
		}
	}

	/**
	 * @todo <b>对象比较</b>
	 * @param target
	 * @param compared
	 * @return
	 */
	public static boolean equals(Object target, Object compared) {
		if (null == target) {
			return target == compared;
		}
		return target.equals(compared);
	}

	/**
	 * @todo 用于不同类型数据之间进行比较，判断是否相等,当类型不一致时统一用String类型比较
	 * @param target
	 * @param compared
	 * @param ignoreCase
	 * @return
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
	 * @TODO 比较两个对象的大小
	 * @param target
	 * @param compared
	 * @return
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
	 * @TODO 提供对象get/set 类型转换
	 * @param value
	 * @param typeValue DataType.getType(typeName) 注意typeName不用转小写
	 * @param typeName  getParameterTypes()[0].getTypeName() 没有转大小写
	 * @return
	 * @throws Exception
	 */
	public static Object convertType(Object value, int typeValue, String typeName) throws Exception {
		return convertType(null, value, typeValue, typeName, null);
	}

	/**
	 * @todo 类型转换 2022-10-18 已经完成了优化，减少了不必要的判断
	 * @param typeHandler
	 * @param value
	 * @param typeValue
	 * @param typeName    getTypeName()没有转大小写
	 * @param genericType 泛型类型
	 * @return
	 * @throws Exception
	 */
	public static Object convertType(TypeHandler typeHandler, Object value, int typeValue, String typeName,
			Class genericType) throws Exception {
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
		// 4 非数组类型,但传递的参数值是数组类型且长度为1提取出数组中的单一值
		if (paramValue.getClass().isArray() && typeValue < DataType.aryCharType) {
			if (typeValue == DataType.stringType && (paramValue instanceof byte[])) {
				paramValue = new String((byte[]) paramValue);
			} else if (typeValue == DataType.stringType && (paramValue instanceof char[])) {
				paramValue = new String((char[]) paramValue);
			} else {
				Object[] paramAry = CollectionUtil.convertArray(paramValue);
				if (paramAry.length > 1) {
					throw new DataAccessException("不能将长度大于1,类型为:" + paramValue.getClass().getTypeName() + " 的数组转化为:"
							+ typeName + " 类型的值,请检查!");
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
				java.sql.Clob clob = (java.sql.Clob) paramValue;
				return clob.getSubString((long) 1, (int) clob.length());
			} else if (paramValue instanceof LocalDate) {
				return DateUtil.formatDate(paramValue, "yyyy-MM-dd");
			} else if (paramValue instanceof LocalTime) {
				return DateUtil.formatDate(paramValue, "HH:mm:ss");
			} else if (paramValue instanceof java.util.Date) {
				return DateUtil.formatDate(paramValue, "yyyy-MM-dd HH:mm:ss");
			} else if (paramValue instanceof Enum) {
				return getEnumValue(paramValue).toString();
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
			if ("true".equals(valueStr.toLowerCase()) || "1".equals(valueStr)) {
				return Boolean.TRUE;
			}
			return Boolean.FALSE;
		}
		// 14 第10 字符串转 boolean 型
		if (DataType.primitiveBooleanType == typeValue) {
			String valueStr = paramValue.toString();
			if ("true".equals(valueStr.toLowerCase()) || "1".equals(valueStr)) {
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
			java.sql.Clob clob = (java.sql.Clob) paramValue;
			BufferedReader in = new BufferedReader(clob.getCharacterStream());
			StringWriter out = new StringWriter();
			int c;
			while ((c = in.read()) != -1) {
				out.write(c);
			}
			return out.toString();
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
				java.sql.Clob clob = (java.sql.Clob) paramValue;
				BufferedReader in = new BufferedReader(clob.getCharacterStream());
				StringWriter out = new StringWriter();
				int c;
				while ((c = in.read()) != -1) {
					out.write(c);
				}
				return out.toString().toCharArray();
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
			if ((paramValue instanceof Array)) {
				return convertArray(((Array) paramValue).getArray(), typeName);
			} else if ((paramValue instanceof Collection)) {
				return convertArray(((Collection) paramValue).toArray(), typeName);
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
			}
		}
		// 36 枚举类型
		if (DataType.enumType == typeValue) {
			Class enumClass = enumClassMap.get(typeName);
			if (enumClass == null) {
				enumClass = Class.forName(typeName);
				enumClassMap.put(typeName, enumClass);
			}
			return newEnumInstance(paramValue, enumClass);
		}
		return paramValue;
	}

	/**
	 * 只处理非null值
	 * 
	 * @param paramValue
	 * @return
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
		if ("true".equals(boolVar)) {
			return "1";
		}
		if ("false".equals(boolVar)) {
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
	 * @todo 利用java.lang.reflect并结合页面的property， 从对象中取出对应方法的值，组成一个List
	 * @param datas
	 * @param props
	 * @return
	 * @throws RuntimeException
	 */
	public static List reflectBeansToList(List datas, String... props) throws RuntimeException {
		return reflectBeansToList(datas, props, null);
	}

	/**
	 * @TODO 切取单列值并以数组返回,服务于loadAll方法
	 * @param datas
	 * @param propertyName
	 * @return
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
	 * @todo 利用java.lang.reflect并结合页面的property， 从对象中取出对应方法的值，组成一个List
	 * @param datas
	 * @param properties
	 * @param reflectPropsHandler
	 * @return
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
					propertyIndexMap.put(properties[i].toLowerCase(), i);
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
			// 增加map类型支持
			boolean isMap = false;
			if (datas.get(0) != null && Map.class.isAssignableFrom(datas.get(0).getClass())) {
				isMap = true;
			}
			Iterator iter;
			String fieldLow;
			Map.Entry<String, Object> entry;
			Map rowMap;
			for (int i = 0, n = datas.size(); i < n; i++) {
				rowObject = datas.get(i);
				if (null != rowObject) {
					List dataList = new ArrayList();
					// 2021-10-09 支持map类型
					if (isMap) {
						if (rowObject instanceof IgnoreKeyCaseMap) {
							rowMap = (IgnoreKeyCaseMap) rowObject;
							for (int j = 0; j < methodLength; j++) {
								dataList.add(rowMap.get(properties[j]));
							}
						} else {
							rowMap = (Map) rowObject;
							// 考虑key大小写兼容
							for (int j = 0; j < methodLength; j++) {
								fieldLow = properties[j].toLowerCase();
								iter = rowMap.entrySet().iterator();
								while (iter.hasNext()) {
									entry = (Map.Entry<String, Object>) iter.next();
									if (entry.getKey().toLowerCase().equals(fieldLow)) {
										dataList.add(entry.getValue());
										break;
									}
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
					if (logger.isDebugEnabled()) {
						logger.debug("BeanUtil.reflectBeansToList 方法,第:{}行数据为null,如果是sql查询请检查写法是否正确!", i);
					} else {
						err.println("BeanUtil.reflectBeansToList 方法,第:{" + i + "}行数据为null,如果是sql查询请检查写法是否正确!");
					}
					resultList.add(null);
				}
			}
		} catch (Exception e) {
			logger.error("反射Java Bean获取数据组装List集合异常!{}", e.getMessage());
			e.printStackTrace();
			throw new RuntimeException("反射Java Bean获取数据组装List集合异常!" + e.getMessage());
		}
		return resultList;
	}

	public static Object[] reflectBeanToAry(Object serializable, String... properties) {
		return reflectBeanToAry(serializable, properties, null, null);
	}

	/**
	 * @todo 反射出单个对象中的属性并以对象数组返回
	 * @param serializable
	 * @param properties
	 * @param defaultValues
	 * @param reflectPropsHandler
	 * @return
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
				propertyIndexMap.put(realProps[i].toLowerCase(), i);
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
						iter = ((Map) fieldValue).entrySet().iterator();
						fieldLow = realProps[i].toLowerCase();
						while (iter.hasNext()) {
							entry = (Map.Entry<String, Object>) iter.next();
							if (entry.getKey().toLowerCase().equals(fieldLow)) {
								fieldValue = entry.getValue();
								hasKey = true;
								break;
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
									iter = ((Map) fieldValue).entrySet().iterator();
									isMapped = false;
									fieldLow = field.toLowerCase();
									keyAndIndex = getKeyAndIndex(fieldLow);
									realFieldLow = (keyAndIndex == null) ? fieldLow : keyAndIndex.getKey();
									while (iter.hasNext()) {
										entry = (Map.Entry<String, Object>) iter.next();
										keyLowString = entry.getKey().toLowerCase();
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
	 * @todo 利用java.lang.reflect并结合页面的property， 从对象中取出对应方法的值，组成一个List
	 * @param dataSet
	 * @param properties
	 * @param defaultValues
	 * @param reflectPropsHandler
	 * @return
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
			boolean hasHandler = (reflectPropsHandler != null) ? true : false;
			// 存在反调，则将对象的属性和属性所在的顺序放入hashMap中，便于后面反调中通过属性调用
			if (hasHandler) {
				HashMap<String, Integer> propertyIndexMap = new HashMap<String, Integer>();
				for (int i = 0; i < methodLength; i++) {
					propertyIndexMap.put(properties[i].toLowerCase(), i);
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
					if (logger.isDebugEnabled()) {
						logger.debug("BeanUtil.reflectBeansToInnerAry 方法,第:{}行数据为null,如果是sql查询请检查写法是否正确!", i);
					} else {
						err.println("BeanUtil.reflectBeansToInnerAry 方法,第:{" + i + "}行数据为null,如果是sql查询请检查写法是否正确!");
					}
					resultList.add(null);
				}
			}
		} catch (Exception e) {
			logger.error("反射Java Bean获取数据组装List集合异常!{}", e.getMessage());
			e.printStackTrace();
			throw new RuntimeException(e);
		}
		return resultList;
	}

	public static List reflectListToBean(TypeHandler typeHandler, Collection datas, String[] properties,
			Class voClass) {
		int[] indexs = null;
		if (properties != null && properties.length > 0) {
			indexs = new int[properties.length];
			for (int i = 0; i < indexs.length; i++) {
				indexs[i] = i;
			}
		}
		return reflectListToBean(typeHandler, datas, indexs, properties, voClass, true);
	}

	/**
	 * @todo 将二维数组映射到对象集合中
	 * @param typeHandler
	 * @param datas
	 * @param indexs
	 * @param properties
	 * @param voClass
	 * @return
	 * @throws RuntimeException
	 */
	public static List reflectListToBean(TypeHandler typeHandler, Collection datas, int[] indexs, String[] properties,
			Class voClass) throws RuntimeException {
		return reflectListToBean(typeHandler, datas, indexs, properties, voClass, true);
	}

	/**
	 * @todo 利用java.lang.reflect并结合页面的property， 从对象中取出对应方法的值，组成一个List
	 * @param typeHandler
	 * @param datas
	 * @param indexs
	 * @param properties
	 * @param voClass
	 * @param autoConvertType
	 * @return
	 */
	public static List reflectListToBean(TypeHandler typeHandler, Collection datas, int[] indexs, String[] properties,
			Class voClass, boolean autoConvertType) {
		if (null == datas || datas.isEmpty()) {
			return null;
		}
		if (null == properties || properties.length < 1 || null == voClass || null == indexs || indexs.length == 0
				|| properties.length != indexs.length) {
			throw new IllegalArgumentException("集合或属性名称数组为空,请检查参数信息!");
		}
		if (Modifier.isAbstract(voClass.getModifiers()) || Modifier.isInterface(voClass.getModifiers())) {
			throw new IllegalArgumentException("toClassType:" + voClass.getName() + " 是抽象类或接口,非法参数!");
		}
		// record类型
		if (voClass.isRecord()) {
			return reflectListToRecord(typeHandler, datas, indexs, properties, voClass, autoConvertType);
		}
		List resultList = new ArrayList();
		Object cellData = null;
		String propertyName = null;
		try {
			Object rowObject = null;
			Object bean;
			boolean isArray = false;
			int notNullRowIndex = 0;
			Object[] rowArray = null;
			List rowList = null;
			int indexSize = indexs.length;
			Method[] realMethods = matchSetMethods(voClass, properties);
			String[] methodTypes = new String[indexSize];
			int[] methodTypeValues = new int[indexSize];
			Class[] genericTypes = new Class[indexSize];
			Type[] types;
			Class methodType;
			// 自动适配属性的数据类型
			if (autoConvertType) {
				for (int i = 0; i < indexSize; i++) {
					if (null != realMethods[i]) {
						methodType = realMethods[i].getParameterTypes()[0];
						methodTypes[i] = methodType.getTypeName();
						methodTypeValues[i] = DataType.getType(methodType);
						types = realMethods[i].getGenericParameterTypes();
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
													? convertType(typeHandler, cellData, methodTypeValues[i],
															methodTypes[i], genericTypes[i])
													: cellData);
								}
							}
						}
					}
					resultList.add(bean);
					notNullRowIndex++;
				} else {
					if (logger.isDebugEnabled()) {
						logger.debug("BeanUtil.reflectListToBean 方法,第:{}行数据为null,如果是sql查询请检查写法是否正确!", index);
					} else {
						err.println("BeanUtil.reflectListToBean 方法,第:{" + index + "}行数据为null,如果是sql查询请检查写法是否正确!");
					}
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

	private static PropertyType[] getRecordFields(Class recordType) {
		if (recordProperties.containsKey(recordType)) {
			return recordProperties.get(recordType);
		}
		RecordComponent[] components = recordType.getRecordComponents();
		PropertyType[] propTypes = new PropertyType[components.length];
		int index = 0;
		for (RecordComponent component : components) {
			PropertyType propertyType = new PropertyType();
			propertyType.setProperty(component.getName());
			propertyType.setIndex(index);
			propertyType.setType(component.getType());
			propertyType.setTypeName(component.getType().getTypeName());
			propertyType.setTypeValue(DataType.getType(component.getType()));
			propertyType.setGenericType((Class) component.getGenericType());
			propTypes[index] = propertyType;
			index++;
		}
		recordProperties.put(recordType, propTypes);
		return propTypes;
	}

	private static List reflectListToRecord(TypeHandler typeHandler, Collection datas, int[] indexs,
			String[] properties, Class voClass, boolean autoConvertType) {
		PropertyType[] recordProps = getRecordFields(voClass);
		int fieldsCnt = recordProps.length;
		Integer[] propIndexes = new Integer[fieldsCnt];
		String propName;
		Class[] parameterTypes = new Class[fieldsCnt];
		for (int i = 0; i < fieldsCnt; i++) {
			propName = recordProps[i].getProperty();
			parameterTypes[i] = recordProps[i].getType();
			for (int j = 0; j < properties.length; j++) {
				if (propName.equalsIgnoreCase(properties[j])) {
					propIndexes[i] = indexs[j];
					break;
				}
			}
		}
		List resultList = new ArrayList();
		Object cellData = null;
		try {
			Constructor constructor = voClass.getDeclaredConstructor(parameterTypes);
			if (!constructor.canAccess(null)) {
				constructor.setAccessible(true);
			}
			Object rowObject = null;
			Iterator iter = datas.iterator();
			int index = 0;
			int rowSize;
			boolean isArray = false;
			Object recordBean;
			int notNullRowIndex = 0;
			Object[] rowArray = null;
			List rowList = null;
			Object[] recordArgs;
			PropertyType recordPropTypes;
			while (iter.hasNext()) {
				rowObject = iter.next();
				if (rowObject != null) {
					if (notNullRowIndex == 0) {
						if (rowObject instanceof Object[]) {
							isArray = true;
						}
					}
					recordArgs = new Object[fieldsCnt];
					if (isArray) {
						rowArray = (Object[]) rowObject;
						rowSize = rowArray.length;
					} else {
						rowList = (List) rowObject;
						rowSize = rowList.size();
					}
					for (int i = 0; i < fieldsCnt; i++) {
						recordPropTypes = recordProps[i];
						if (propIndexes[i] != null && propIndexes[i] < rowSize) {
							cellData = isArray ? rowArray[propIndexes[i]] : rowList.get(propIndexes[i]);
							if (cellData != null) {
								// 类型相同
								if (cellData.getClass().equals(recordPropTypes.getType())) {
									recordArgs[i] = cellData;
								} else {
									recordArgs[i] = convertType(typeHandler, cellData, recordPropTypes.getTypeValue(),
											recordPropTypes.getTypeName(), recordPropTypes.getGenericType());
								}
							}
						}
					}
					recordBean = constructor.newInstance(recordArgs);
					resultList.add(recordBean);
					notNullRowIndex++;
				} else {
					if (logger.isDebugEnabled()) {
						logger.debug("BeanUtil.reflectListToBean 方法,第:{}行数据为null,如果是sql查询请检查写法是否正确!", index);
					} else {
						err.println("BeanUtil.reflectListToBean 方法,第:{" + index + "}行数据为null,如果是sql查询请检查写法是否正确!");
					}
					resultList.add(null);
				}
				index++;
			}
		} catch (Exception e) {
			String errorMsg = "将集合数据:[" + cellData + "] 映射到Record对象:" + voClass.getName() + " 的过程异常!" + e.getMessage();
			logger.error(errorMsg);
			throw new RuntimeException(errorMsg, e);
		}
		return resultList;
	}

	public static <T extends Serializable> T reflectRowToBean(TypeHandler typeHandler, Method[] realMethods,
			int[] methodTypeValues, String[] methodTypes, Class[] genericTypes, List rowList, int[] indexs,
			String[] properties, Class<T> voClass) {
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
							realMethods[i].invoke(bean, convertType(typeHandler, cellData, methodTypeValues[i],
									methodTypes[i], genericTypes[i]));
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
	 * @todo 批量对集合的属性设置相同的值
	 * @param voList
	 * @param properties
	 * @param values
	 * @param autoConvertType
	 * @param forceUpdate
	 */
	public static void batchSetProperties(Collection voList, String[] properties, Object[] values,
			boolean autoConvertType, boolean forceUpdate) {
		if (null == voList || voList.isEmpty()) {
			return;
		}
		if (null == properties || properties.length < 1 || null == values || values.length < 1
				|| properties.length != values.length) {
			throw new IllegalArgumentException("集合或属性名称数组为空,请检查参数信息!");
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
											? convertType(null, values[i], methodTypeValues[i], methodTypes[i],
													genericTypes[i])
											: values[i]);
						}
					}
				}
			}
		} catch (Exception e) {
			logger.error("将集合数据反射到Java Bean过程异常!{}", e.getMessage());
			e.printStackTrace();
			throw new RuntimeException("将集合数据反射到Java Bean过程异常!{}" + e.getMessage(), e);
		}
	}

	/**
	 * @todo 对集合属性进行赋值
	 * @param voList
	 * @param properties
	 * @param values
	 * @param index
	 * @param autoConvertType
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
			throw new IllegalArgumentException("集合或属性名称数组为空,请检查参数信息!");
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
											? convertType(null, rowData[index[i]], methodTypeValues[i], methodTypes[i],
													genericTypes[i])
											: rowData[index[i]]);
						}
					}
				}
				rowIndex++;
			}
		} catch (Exception e) {
			logger.error("将集合数据反射到Java Bean过程异常!{}", e.getMessage());
			e.printStackTrace();
			throw new RuntimeException("将集合数据反射到Java Bean过程异常!" + e.getMessage());
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
						&& !"getclass".equals(methodName.toLowerCase())) {
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
	 * @todo 根据方法名称以及参数数量获取类的具体方法
	 * @param beanClass
	 * @param methodName
	 * @param argLength
	 * @return
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
	 * @todo 判断对象是否是基本数据类型对象
	 * @param clazz
	 * @return
	 */
	public static boolean isBaseDataType(Class clazz) {
		return (clazz.isPrimitive() || clazz.equals(String.class) || clazz.equals(Integer.class)
				|| clazz.equals(Byte.class) || clazz.equals(Long.class) || clazz.equals(Double.class)
				|| clazz.equals(Float.class) || clazz.equals(Character.class) || clazz.equals(Short.class)
				|| clazz.equals(BigDecimal.class) || clazz.equals(BigInteger.class) || clazz.equals(Boolean.class)
				|| clazz.equals(Date.class) || clazz.equals(LocalDate.class) || clazz.equals(LocalDateTime.class)
				|| clazz.equals(LocalTime.class) || clazz.equals(Timestamp.class));
	}

	/**
	 * @TODO 代替PropertyUtil 和BeanUtils的setProperty方法
	 * @param bean
	 * @param property
	 * @param value
	 * @throws RuntimeException
	 */
	public static void setProperty(Object bean, String property, Object value) throws RuntimeException {
		String key = bean.getClass().getName().concat(":set").concat(property);
		// 利用缓存提升方法匹配效率
		Method method = setMethods.get(key);
		if (method == null) {
			method = matchSetMethods(bean.getClass(), new String[] { property })[0];
			if (method == null) {
				throw new RuntimeException(bean.getClass().getName() + " 没有对应的:" + property);
			}
			setMethods.put(key, method);
		}
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
			method.invoke(bean,
					convertType(null, value, DataType.getType(method.getParameterTypes()[0]), typeName, genericType));
		} catch (Exception e) {
			e.printStackTrace();
			throw new RuntimeException(e.getMessage());
		}
	}

	/**
	 * @TODO 代替BeanUtils.getProperty 方法
	 * @param bean
	 * @param property
	 * @return
	 * @throws RuntimeException
	 */
	public static Object getProperty(Object bean, String property) throws RuntimeException {
		if (bean instanceof Map) {
			return ((Map) bean).get(property);
		}
		String key = bean.getClass().getName().concat(":get").concat(property);
		// 利用缓存提升方法匹配效率
		Method method = getMethods.get(key);
		if (method == null) {
			method = matchGetMethods(bean.getClass(), new String[] { property })[0];
			if (method == null) {
				return null;
			}
			getMethods.put(key, method);
		}
		Object result = null;
		try {
			result = method.invoke(bean);
		} catch (Exception e) {
			e.printStackTrace();
			throw new RuntimeException(e.getMessage());
		}
		return result;
	}

	/**
	 * @TODO 代替BeanUtils.getProperty 方法,增加item[1] 数组模式调用
	 * @param bean
	 * @param property
	 * @return
	 * @throws RuntimeException
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
			method = matchGetMethods(bean.getClass(), new String[] { realProperty })[0];
			if (method == null) {
				return null;
			}
			getMethods.put(key, method);
		}
		try {
			result = method.invoke(bean);
			if (result != null && keyAndIndex != null) {
				result = getArrayIndexValue(result, keyAndIndex.getIndex());
			}
		} catch (Exception e) {
			e.printStackTrace();
			throw new RuntimeException(e.getMessage());
		}
		return result;
	}

	/**
	 * @TODO 为loadByIds提供Entity集合封装,便于将调用方式统一
	 * @param <T>
	 * @param typeHandler
	 * @param entityMeta
	 * @param voClass
	 * @param ids         数组
	 * @return
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
					method.invoke(bean, convertType(typeHandler, id, typeValue, typeName, genericType));
					entities.add(bean);
					repeat.add(id);
				}
			}
		} catch (Exception e) {
			logger.error("将集合数据反射到Java Bean过程异常!{}", e.getMessage());
			throw new RuntimeException(e);
		}
		return entities;
	}

	/**
	 * @TODO 获取VO对应的实际的entityClass,主要是规避{{}}实例导致无法正确获取类型
	 * @param entityClass
	 * @return
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
	 * @TODO 对常规类型进行转换，超出部分由自定义类型处理器完成(或配置类型完全一致)
	 * @param values
	 * @param typeName
	 * @return
	 */
	public static Object convertArray(Object values, String typeName) {
		// 类型完全一致
		if (typeName.equals(values.getClass().getTypeName())) {
			return values;
		}
		Object[] array = (Object[]) values;
		int index = 0;
		if ("java.lang.String[]".equals(typeName) && !(values instanceof String[])) {
			String[] result = new String[array.length];
			for (Object obj : array) {
				if (obj != null) {
					result[index] = obj.toString();
				}
				index++;
			}
			return result;
		}
		if ("java.lang.Integer[]".equals(typeName) && !(values instanceof Integer[])) {
			Integer[] result = new Integer[array.length];
			for (Object obj : array) {
				if (obj != null) {
					result[index] = Integer.valueOf(obj.toString());
				}
				index++;
			}
			return result;
		}
		if ("java.lang.Long[]".equals(typeName) && !(values instanceof Long[])) {
			Long[] result = new Long[array.length];
			for (Object obj : array) {
				if (obj != null) {
					result[index] = Long.valueOf(obj.toString());
				}
				index++;
			}
			return result;
		}
		if ("java.math.BigDecimal[]".equals(typeName) && !(values instanceof BigDecimal[])) {
			BigDecimal[] result = new BigDecimal[array.length];
			for (Object obj : array) {
				if (obj != null) {
					result[index] = new BigDecimal(obj.toString());
				}
				index++;
			}
			return result;
		}
		if ("int[]".equals(typeName) && !(values instanceof int[])) {
			int[] result = new int[array.length];
			for (Object obj : array) {
				if (obj != null) {
					result[index] = Integer.parseInt(obj.toString());
				}
				index++;
			}
			return result;
		}
		if ("long[]".equals(typeName) && !(values instanceof long[])) {
			long[] result = new long[array.length];
			for (Object obj : array) {
				if (obj != null) {
					result[index] = Long.parseLong(obj.toString());
				}
				index++;
			}
			return result;
		}
		if ("java.lang.Double[]".equals(typeName) && !(values instanceof Double[])) {
			Double[] result = new Double[array.length];
			for (Object obj : array) {
				if (obj != null) {
					result[index] = Double.valueOf(obj.toString());
				}
				index++;
			}
			return result;
		}
		if ("double[]".equals(typeName) && !(values instanceof double[])) {
			double[] result = new double[array.length];
			for (Object obj : array) {
				if (obj != null) {
					result[index] = Double.parseDouble(obj.toString());
				}
				index++;
			}
			return result;
		}
		if ("java.lang.Float[]".equals(typeName) && !(values instanceof Float[])) {
			Float[] result = new Float[array.length];
			for (Object obj : array) {
				if (obj != null) {
					result[index] = Float.valueOf(obj.toString());
				}
				index++;
			}
			return result;
		}
		if ("float[]".equals(typeName) && !(values instanceof float[])) {
			float[] result = new float[array.length];
			for (Object obj : array) {
				if (obj != null) {
					result[index] = Float.parseFloat(obj.toString());
				}
				index++;
			}
			return result;
		}
		return values;
	}

	/**
	 * @TODO 针对loadAll级联加载场景,子表通过主表id集合批量一次性完成加载的，所以子表集合包含了主表集合的全部关联信息
	 * @param mainEntities
	 * @param itemEntities
	 * @param cascadeModel
	 * @throws Exception
	 */
	public static void loadAllMapping(List mainEntities, List itemEntities, TableCascadeModel cascadeModel)
			throws Exception {
		if (mainEntities == null || mainEntities.isEmpty() || itemEntities == null || itemEntities.isEmpty()) {
			return;
		}
		boolean isOneToMany = (cascadeModel.getCascadeType() == 1);
		Object mainEntity;
		String[] mainProps = cascadeModel.getFields();
		Object[] mainValues;
		Object[] mappedFieldValues;
		String property = cascadeModel.getProperty();
		Object itemEntity;
		String[] mappedFields = cascadeModel.getMappedFields();
		List itemList = null;
		int fieldLength = mainProps.length;
		boolean isEqual = true;
		int itemSize = 0;
		for (int i = 0; i < mainEntities.size(); i++) {
			mainEntity = mainEntities.get(i);
			mainValues = reflectBeanToAry(mainEntity, mainProps, null, null);
			if (isOneToMany) {
				itemList = new ArrayList();
			}
			itemSize = 0;
			for (int j = 0; j < itemEntities.size(); j++) {
				itemEntity = itemEntities.get(j);
				mappedFieldValues = reflectBeanToAry(itemEntity, mappedFields, null, null);
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
							throw new DataAccessException(
									"请检查对象:" + mainEntity.getClass().getName() + "中的@OneToOne级联配置,级联查出的数据为>1条,不符合预期!");
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
	 * @TODO 解析类中的@OneToOne 和@OneToMany注解，服务sql查询结果按对象层次结构进行封装
	 * @param entityClass
	 * @return
	 */
	private static Field[] parseCascadeFields(Class entityClass) {
		Set<String> fieldSet = new HashSet<String>();
		List<Field> cascadeFields = new ArrayList<Field>();
		Class classType = entityClass;
		String fieldName;
		while (classType != null && !classType.equals(Object.class)) {
			for (Field field : classType.getDeclaredFields()) {
				fieldName = field.getName().toLowerCase();
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
	 * @TODO 获取类的级联关系
	 * @param entityClass
	 * @return
	 */
	public static List<TableCascadeModel> getCascadeModels(Class entityClass) {
		String className = entityClass.getName();
		List<TableCascadeModel> result = cascadeModels.get(className);
		if (result == null) {
			result = new ArrayList<TableCascadeModel>();
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
			cascadeModels.put(className, result);
		}
		return result;
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
	 * @TODO 将对象转数组获取index列对应的值
	 * @param result
	 * @param index
	 * @return
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
	 * @param unifyFieldsHandler
	 * @param fieldsAry
	 * @param type               1:save;2:update;3:saveOrUpdate
	 * @return
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
	 * @TODO 回写POJO的：创建人、创建时间、修改人、修改时间等公共字段
	 * @param entity
	 * @param fieldIndexMap
	 * @param values
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
	 * @TODO 批量回写POJO的：创建人、创建时间、修改人、修改时间等公共字段
	 * @param entitis
	 * @param fieldIndexMap
	 * @param values
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
}