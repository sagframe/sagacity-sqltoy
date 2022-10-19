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

	// 数组类型
	public static final int aryCharType = 91;
	public static final int aryByteType = 92;
	public static final int aryOtherType = 93;

	/**
	 * @TODO 获取类型
	 * @param typeName
	 * @return
	 */
	public static int getType(String typeName) {
		// 原生类型
		if (typeName.equals("int")) {
			return primitiveIntType;
		}
		if (typeName.equals("long")) {
			return primitiveLongType;
		}
		if (typeName.equals("double")) {
			return primitiveDoubleType;
		}
		if (typeName.equals("float")) {
			return primitiveFloatType;
		}
		if (typeName.equals("boolean")) {
			return primitiveBooleanType;
		}
		if (typeName.equals("char")) {
			return primitiveCharType;
		}
		if (typeName.equals("byte")) {
			return primitiveByteType;
		}
		if (typeName.equals("short")) {
			return primitiveShortType;
		}
		// string
		if (typeName.equals("java.lang.String")) {
			return stringType;
		}
		// clob
		if (typeName.equals("java.sql.Clob")) {
			return clobType;
		}
		// 包装类型
		if (typeName.equals("java.lang.Integer")) {
			return wrapIntegerType;
		}
		if (typeName.equals("java.lang.Long")) {
			return wrapLongType;
		}
		if (typeName.equals("java.lang.Short")) {
			return wrapShortType;
		}
		if (typeName.equals("java.lang.Double")) {
			return wrapDoubleType;
		}
		if (typeName.equals("java.lang.Boolean")) {
			return wrapBooleanType;
		}
		if (typeName.equals("java.lang.Float")) {
			return wrapFloatType;
		}
		if (typeName.equals("java.lang.Byte")) {
			return wrapByteType;
		}
		if (typeName.equals("java.math.BigDecimal")) {
			return wrapBigDecimalType;
		}
		if (typeName.equals("java.math.BigInteger")) {
			return wrapBigIntegerType;
		}
		// 时间类型
		if (typeName.equals("java.sql.Timestamp")) {
			return timestampType;
		}
		if (typeName.equals("java.sql.Time")) {
			return sqlTimeType;
		}
		if (typeName.equals("java.sql.Date")) {
			return sqlDateType;
		}
		if (typeName.equals("java.util.Date")) {
			return dateType;
		}
		if (typeName.equals("java.time.LocalDate")) {
			return localDateType;
		}
		if (typeName.equals("java.time.LocalDateTime")) {
			return localDateTimeType;
		}
		if (typeName.equals("java.time.LocalTime")) {
			return localTimeType;
		}
		// 数组类型
		if (typeName.equals("char[]")) {
			return aryCharType;
		}
		if (typeName.equals("byte[]")) {
			return aryByteType;
		}
		if (typeName.endsWith("[]")) {
			return aryOtherType;
		}
		// 其他
		return objectType;
	}

}
