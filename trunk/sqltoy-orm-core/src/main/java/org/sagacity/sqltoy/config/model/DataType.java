package org.sagacity.sqltoy.config.model;

/**
 * @project sagacity-sqltoy
 * @description 提供一个对象类型定义，用于代替类型字符串的对比，提升对比效率(BeanUtil.convertType)
 * @author zhongxuchen
 * @version v1.0,2022-10-20
 */
public class DataType {
	// 其他
	public static final int objectType = 0;
	// 8种原生类型
	public static final int primitiveIntType = 1;
	public static final int primitiveLongType = 2;
	public static final int primitiveShortType = 3;
	public static final int primitiveFloatType = 4;
	public static final int primitiveDoubleType = 5;
	public static final int primitiveBooleanType = 6;
	public static final int primitiveCharType = 7;
	public static final int primitiveByteType = 8;

	// string
	public static final int stringType = 11;
	// clob
	public static final int clobType = 12;

	// 包装类型
	public static final int wrapBigDecimalType = 20;
	public static final int wrapIntegerType = 21;
	public static final int wrapLongType = 22;
	public static final int wrapFloatType = 23;
	public static final int wrapShortType = 24;
	public static final int wrapDoubleType = 25;
	public static final int wrapBigIntegerType = 26;
	public static final int wrapBooleanType = 27;
	public static final int wrapByteType = 28;

	// 日期类型
	public static final int localDateType = 41;
	public static final int timestampType = 42;

	public static final int localDateTimeType = 43;
	public static final int localTimeType = 44;
	public static final int dateType = 45;
	public static final int sqlDateType = 46;
	public static final int sqlTimeType = 47;
	public static final int offsetDateTimeType = 48;
	public static final int zonedDateTimeType = 49;
	public static final int offsetTimeType = 50;
	// 数组类型
	public static final int aryCharType = 91;
	public static final int aryByteType = 92;
	public static final int aryOtherType = 93;

	// list集合类型
	public static final int listType = 101;

	public static final int setType = 102;

	// 枚举类型
	public static final int enumType = 110;

	public static int getType(String typeName) {
		return getType(null, typeName);
	}

	public static int getType(Class typeClass) {
		return getType(typeClass, typeClass.getTypeName());
	}

	/**
	 * @TODO 获取类型
	 * @param typeClass
	 * @param typeName
	 * @return
	 */
	private static int getType(Class typeClass, String typeName) {
		// 原生类型
		if ("int".equals(typeName)) {
			return primitiveIntType;
		}
		if ("long".equals(typeName)) {
			return primitiveLongType;
		}
		if ("double".equals(typeName)) {
			return primitiveDoubleType;
		}
		if ("float".equals(typeName)) {
			return primitiveFloatType;
		}
		if ("boolean".equals(typeName)) {
			return primitiveBooleanType;
		}
		if ("char".equals(typeName)) {
			return primitiveCharType;
		}
		if ("byte".equals(typeName)) {
			return primitiveByteType;
		}
		if ("short".equals(typeName)) {
			return primitiveShortType;
		}
		// string
		if ("java.lang.String".equals(typeName)) {
			return stringType;
		}
		// clob
		if ("java.sql.Clob".equals(typeName)) {
			return clobType;
		}
		// 包装类型
		if ("java.lang.Integer".equals(typeName)) {
			return wrapIntegerType;
		}
		if ("java.lang.Long".equals(typeName)) {
			return wrapLongType;
		}
		if ("java.lang.Short".equals(typeName)) {
			return wrapShortType;
		}
		if ("java.lang.Double".equals(typeName)) {
			return wrapDoubleType;
		}
		if ("java.lang.Boolean".equals(typeName)) {
			return wrapBooleanType;
		}
		if ("java.lang.Float".equals(typeName)) {
			return wrapFloatType;
		}
		if ("java.lang.Byte".equals(typeName)) {
			return wrapByteType;
		}
		if ("java.math.BigDecimal".equals(typeName)) {
			return wrapBigDecimalType;
		}
		if ("java.math.BigInteger".equals(typeName)) {
			return wrapBigIntegerType;
		}
		// 时间类型
		if ("java.sql.Timestamp".equals(typeName)) {
			return timestampType;
		}
		if ("java.sql.Time".equals(typeName)) {
			return sqlTimeType;
		}
		if ("java.sql.Date".equals(typeName)) {
			return sqlDateType;
		}
		if ("java.util.Date".equals(typeName)) {
			return dateType;
		}
		if ("java.time.LocalDate".equals(typeName)) {
			return localDateType;
		}
		if ("java.time.LocalDateTime".equals(typeName)) {
			return localDateTimeType;
		}
		if ("java.time.OffsetDateTime".equals(typeName)) {
			return offsetDateTimeType;
		}
		if ("java.time.ZonedDateTime".equals(typeName)) {
			return zonedDateTimeType;
		}
		if ("java.time.LocalTime".equals(typeName)) {
			return localTimeType;
		}
		if ("java.time.OffsetTime".equals(typeName)) {
			return offsetTimeType;
		}
		// 数组类型
		if ("char[]".equals(typeName)) {
			return aryCharType;
		}
		if ("byte[]".equals(typeName)) {
			return aryByteType;
		}
		if (typeName.endsWith("[]")) {
			return aryOtherType;
		}
		// list
		if ("java.util.List".equals(typeName) || "java.util.ArrayList".equals(typeName)) {
			return listType;
		}
		// set
		if ("java.util.Set".equals(typeName) || "java.util.HashSet".equals(typeName)) {
			return setType;
		}
		if (typeClass != null && typeClass.isEnum()) {
			return enumType;
		}
		// 其他
		return objectType;
	}

}
