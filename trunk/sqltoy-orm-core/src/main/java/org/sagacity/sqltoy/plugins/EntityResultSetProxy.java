package org.sagacity.sqltoy.plugins;

import java.lang.reflect.Method;
import java.math.BigDecimal;
import java.sql.ResultSet;
import java.sql.Time;
import java.sql.Timestamp;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.util.concurrent.Callable;

import org.sagacity.sqltoy.config.model.DataType;
import org.sagacity.sqltoy.config.model.EntityMeta;
import org.sagacity.sqltoy.config.model.FieldMeta;
import org.sagacity.sqltoy.model.JdbcTypes;
import org.sagacity.sqltoy.utils.BeanUtil;
import org.sagacity.sqltoy.utils.DateUtil;
import org.sagacity.sqltoy.utils.JSONTypeUtil;

import net.bytebuddy.ByteBuddy;
import net.bytebuddy.implementation.MethodDelegation;
import net.bytebuddy.implementation.bind.annotation.AllArguments;
import net.bytebuddy.implementation.bind.annotation.Origin;
import net.bytebuddy.implementation.bind.annotation.RuntimeType;
import net.bytebuddy.implementation.bind.annotation.SuperCall;
import net.bytebuddy.implementation.bind.annotation.This;
import net.bytebuddy.matcher.ElementMatchers;

/**
 * 针对updateSaveFetch 提供实体代理对象，get方法自动从ResultSet读取列值，set方法自动更新ResultSet行
 * 
 * @project sagacity-sqltoy
 * @param <T>
 * @date 2026-6-18
 */
public class EntityResultSetProxy<T> {
	private final ResultSet rs;
	private EntityMeta entityMeta;

	public EntityResultSetProxy(ResultSet rs, EntityMeta entityMeta) {
		this.rs = rs;
		this.entityMeta = entityMeta;
	}

	@RuntimeType
	public Object intercept(@This Object proxy, @Origin Method method, @AllArguments Object[] args,
			@SuperCall Callable<?> superCall) throws Exception {
		String methodName = method.getName();
		// getter: getAmt → 截取Amt → amt属性
		if ((methodName.startsWith("get") || methodName.startsWith("is")) && args.length == 0) {
			int subSize = methodName.startsWith("get") ? 3 : 2;
			String propName = methodName.substring(subSize);
			FieldMeta fieldMeta = entityMeta.getFieldMeta(propName);
			String col = fieldMeta.getColumnName();
			if (col == null) {
				return superCall.call();
			}
			Class<?> returnType = method.getReturnType();
			Object obj = rs.getObject(col);
			if (obj == null) {
				return null;
			}
			// 字符串、日期等通用转换
			return BeanUtil.convertType(obj, JdbcTypes.OTHER, DataType.getType(returnType.getTypeName()),
					returnType.getTypeName());
		}

		// setter: setAmt(100) → 截取Amt → amt属性
		if (methodName.startsWith("set") && args != null && args.length >= 1) {
			String propName = methodName.substring(3);
			FieldMeta fieldMeta = entityMeta.getFieldMeta(propName);
			String col = fieldMeta.getColumnName();
			if (col == null) {
				return superCall.call();
			}
			Object val = args[0];
			if (val == null) {
				rs.updateNull(col);
				return null;
			}
			Class<?> paramType = method.getParameterTypes()[0];
			// 自动匹配rs.updateXXX
			if (Double.class == paramType || double.class == paramType) {
				rs.updateDouble(col, (Double) val);
			} else if (Integer.class == paramType || int.class == paramType) {
				if (fieldMeta.getType() == JdbcTypes.BOOLEAN || fieldMeta.getType() == JdbcTypes.BIT) {
					// 1表示true，0表示false
					rs.updateBoolean(col, (Integer) val == 1);
				} else if (fieldMeta.getType() == JdbcTypes.VARCHAR) {
					rs.updateString(col, String.valueOf(val));
				} else {
					rs.updateInt(col, (Integer) val);
				}
			} else if (BigDecimal.class == paramType) {
				rs.updateBigDecimal(col, (BigDecimal) val);
			} else if (String.class == paramType) {
				if (fieldMeta.getType() == JdbcTypes.BOOLEAN) {
					// 1表示true，0表示false
					rs.updateBoolean(col, ("1".equals(val) || "true".equalsIgnoreCase((String) val)));
				} else {
					rs.updateString(col, (String) val);
				}
			} else if (Boolean.class == paramType) {
				if (fieldMeta.getType() == JdbcTypes.INTEGER || fieldMeta.getType() == JdbcTypes.SMALLINT
						|| fieldMeta.getType() == JdbcTypes.TINYINT) {
					rs.updateInt(col, (Boolean) val ? 1 : 0);
				} else if (fieldMeta.getType() == JdbcTypes.VARCHAR || fieldMeta.getType() == JdbcTypes.CHAR) {
					rs.updateString(col, (Boolean) val ? "1" : "0");
				} else {
					rs.updateBoolean(col, (Boolean) val);
				}
			} else if (Timestamp.class == paramType) {
				rs.updateTimestamp(col, (Timestamp) val);
			} else if (java.util.Date.class.isAssignableFrom(paramType)) {
				if (fieldMeta.getType() == JdbcTypes.VARCHAR) {
					rs.updateString(col, DateUtil.formatDate((java.util.Date) val, "yyyy-MM-dd HH:mm:ss"));
				} else {
					rs.updateDate(col, new java.sql.Date(((java.util.Date) val).getTime()));
				}
			} else if (Long.class == paramType || long.class == paramType) {
				if (fieldMeta.getType() == JdbcTypes.VARCHAR) {
					rs.updateString(col, String.valueOf(val));
				} else {
					rs.updateLong(col, (Long) val);
				}
			} else if (Float.class == paramType || float.class == paramType) {
				if (fieldMeta.getType() == JdbcTypes.VARCHAR) {
					rs.updateString(col, String.valueOf(val));
				} else {
					rs.updateFloat(col, (Float) val);
				}
			} else if (fieldMeta.getType() == JdbcTypes.JSON || fieldMeta.getType() == JdbcTypes.JSONB) {
				rs.updateString(col, JSONTypeUtil.toJSONString(val));
			} else if (LocalDateTime.class == paramType && fieldMeta.getType() == JdbcTypes.VARCHAR) {
				rs.updateString(col, DateUtil.formatDate((LocalDateTime) val, "yyyy-MM-dd HH:mm:ss"));
			} else if (LocalDate.class == paramType && fieldMeta.getType() == JdbcTypes.VARCHAR) {
				rs.updateString(col, DateUtil.formatDate((LocalDateTime) val, "yyyy-MM-dd"));
			} else if ((LocalTime.class == paramType || Time.class == paramType)
					&& fieldMeta.getType() == JdbcTypes.VARCHAR) {
				rs.updateString(col, DateUtil.formatDate((LocalDateTime) val, "HH:mm:ss"));
			} else {
				rs.updateObject(col, val);
			}
			return null;
		}
		// toString/equals等原生方法直接执行
		return superCall.call();
	}

	/**
	 * 创建实体代理对象
	 */
	@SuppressWarnings("unchecked")
	public static <T> T createProxy(ResultSet rs, Class<T> clazz, EntityMeta entityMeta) throws Exception {
		EntityResultSetProxy<T> interceptor = new EntityResultSetProxy<>(rs, entityMeta);
		// 动态生成子类，拦截全部方法委托到interceptor.intercept
		Class<?> proxyCls = new ByteBuddy().subclass(clazz).method(ElementMatchers.any())
				.intercept(MethodDelegation.to(interceptor)).make().load(clazz.getClassLoader()).getLoaded();
		return (T) proxyCls.getDeclaredConstructor().newInstance();
	}
}
