package org.sagacity.sqltoy.lambda.engine;

import java.sql.Types;
import java.time.temporal.Temporal;
import java.util.Date;
import java.util.function.Function;

import org.sagacity.sqltoy.config.model.EntityMeta;
import org.sagacity.sqltoy.config.model.FieldMeta;
import org.sagacity.sqltoy.exception.DataAccessException;
import org.sagacity.sqltoy.lambda.LambdaFn;
import org.sagacity.sqltoy.lambda.LambdaUtils;
import org.sagacity.sqltoy.lambda.OuterRef;
import org.sagacity.sqltoy.lambda.toolkit.FieldNature;

/**
 * @description lambda条件值的装配期类型校验:数据库绑定参数带类型,不像sql字面量可隐式转换,
 *              在生成sql前拦截"Integer字段绑定String值"这类错误
 * @author zhongxuchen
 */
public final class LambdaTypeChecker {

	private LambdaTypeChecker() {
	}

	public static <T, R> void check(LambdaFn<T, R> column, Object value, Function<Class<?>, EntityMeta> metaLoader,
			String operation) {
		if (value == null || value instanceof OuterRef || metaLoader == null) {
			return;
		}
		// in条件传入集合/数组:逐元素校验;set数组/集合字段不展开
		if (value instanceof java.util.Collection) {
			if ("set".equals(operation)) {
				return;
			}
			if (!"in".equals(operation)) {
				throw new DataAccessException("Lambda " + operation
						+ " does not accept a collection value; use in()/notIn() for collection conditions");
			}
			for (Object item : (java.util.Collection<?>) value) {
				check(column, item, metaLoader, operation);
			}
			return;
		}
		if (value.getClass().isArray()) {
			if ("set".equals(operation)) {
				return;
			}
			if (!"in".equals(operation)) {
				throw new DataAccessException("Lambda " + operation
						+ " does not accept an array value; use in()/notIn() for collection conditions");
			}
			int length = java.lang.reflect.Array.getLength(value);
			for (int i = 0; i < length; i++) {
				check(column, java.lang.reflect.Array.get(value, i), metaLoader, operation);
			}
			return;
		}
		FieldNature nature;
		try {
			nature = LambdaUtils.extractNature(column);
		} catch (RuntimeException e) {
			// lambda元信息无法解析时交由后续列解析给出原有错误
			return;
		}
		if (nature == null || nature.entityClass() == null || nature.fieldName() == null) {
			return;
		}
		EntityMeta entityMeta = metaLoader.apply(nature.entityClass());
		if (entityMeta == null) {
			return;
		}
		checkProperty(nature.entityClass(), nature.fieldName(), value, entityMeta, operation);
	}

	/**
	 * 按属性名校验,供sqltoy-plus等使用属性名而非LambdaFn的场景复用
	 */
	public static void checkProperty(Class<?> entityClass, String fieldName, Object value, EntityMeta entityMeta,
			String operation) {
		if (value == null || entityMeta == null) {
			return;
		}
		// in条件在此处传入的是集合/数组,其元素类型由数据库按IN展开,不按单值校验
		if (value instanceof java.util.Collection || value.getClass().isArray()) {
			return;
		}
		FieldMeta fieldMeta = entityMeta.getFieldMeta(fieldName);
		if (fieldMeta == null) {
			return;
		}
		if (!compatible(fieldMeta, value)) {
			String expected = fieldMeta.getFieldType();
			if (expected == null || expected.isEmpty()) {
				expected = "jdbcType=" + fieldMeta.getType();
			}
			throw new DataAccessException("Lambda " + operation + " type mismatch: " + entityClass.getSimpleName() + "."
					+ fieldName + " expects " + expected + ", but value is " + value.getClass().getName()
					+ "; use typeCheck(false) only when the conversion is intentional");
		}
	}

	private static boolean compatible(FieldMeta fieldMeta, Object value) {
		Class<?> actual = value.getClass();
		switch (fieldMeta.getType()) {
		case Types.TINYINT:
		case Types.SMALLINT:
		case Types.INTEGER:
		case Types.BIGINT:
		case Types.FLOAT:
		case Types.REAL:
		case Types.DOUBLE:
		case Types.NUMERIC:
		case Types.DECIMAL:
			return Number.class.isAssignableFrom(actual);
		case Types.BIT:
		case Types.BOOLEAN:
			return Boolean.class.isAssignableFrom(actual);
		case Types.CHAR:
		case Types.VARCHAR:
		case Types.LONGVARCHAR:
		case Types.NCHAR:
		case Types.NVARCHAR:
		case Types.LONGNVARCHAR:
		case Types.CLOB:
		case Types.NCLOB:
			if (CharSequence.class.isAssignableFrom(actual) || Character.class.isAssignableFrom(actual)) {
				return true;
			}
			// 枚举常按VARCHAR存储,按字段类型名匹配,避免把合法的枚举绑定误判
			return actual.isEnum() && compatibleByFieldType(fieldMeta.getFieldType(), actual);
		case Types.DATE:
		case Types.TIME:
		case Types.TIME_WITH_TIMEZONE:
		case Types.TIMESTAMP:
		case Types.TIMESTAMP_WITH_TIMEZONE:
			return Date.class.isAssignableFrom(actual) || Temporal.class.isAssignableFrom(actual);
		case Types.BINARY:
		case Types.VARBINARY:
		case Types.LONGVARBINARY:
		case Types.BLOB:
			return actual == byte[].class;
		default:
			return compatibleByFieldType(fieldMeta.getFieldType(), actual);
		}
	}

	/**
	 * JDBC类型为OTHER/未知时的Java类型兜底;未知类型不拦截,避免误报
	 */
	private static boolean compatibleByFieldType(String fieldType, Class<?> actual) {
		if (fieldType == null || fieldType.isEmpty()) {
			return true;
		}
		String type = fieldType.toLowerCase();
		int dot = type.lastIndexOf('.');
		if (dot >= 0) {
			type = type.substring(dot + 1);
		}
		switch (type) {
		case "int":
		case "integer":
		case "long":
		case "short":
		case "byte":
		case "float":
		case "double":
		case "bigdecimal":
		case "biginteger":
		case "number":
			return Number.class.isAssignableFrom(actual);
		case "string":
		case "char":
		case "character":
		case "text":
			return CharSequence.class.isAssignableFrom(actual) || Character.class.isAssignableFrom(actual);
		case "boolean":
		case "bool":
			return Boolean.class.isAssignableFrom(actual);
		case "date":
			return Date.class.isAssignableFrom(actual) || Temporal.class.isAssignableFrom(actual);
		case "localdate":
		case "localdatetime":
		case "localtime":
		case "offsetdatetime":
		case "offsettime":
		case "zoneddatetime":
		case "instant":
			return Temporal.class.isAssignableFrom(actual);
		case "byte[]":
			return actual == byte[].class;
		default:
			// 未知/自定义类型(含JDBC OTHER、特殊TypeHandler)无法可靠判断,放行以避免误报
			return true;
		}
	}
}
