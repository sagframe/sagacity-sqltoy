package org.sagacity.sqltoy.plugins;

import java.lang.reflect.Method;
import java.sql.Connection;
import java.sql.ResultSet;
import java.util.concurrent.Callable;

import org.sagacity.sqltoy.config.model.DataType;
import org.sagacity.sqltoy.config.model.EntityMeta;
import org.sagacity.sqltoy.config.model.FieldMeta;
import org.sagacity.sqltoy.utils.BeanUtil;
import org.sagacity.sqltoy.utils.SqlUtilsExt;

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
	private int dbType;
	private Connection conn;
	private final ResultSet rs;
	private EntityMeta entityMeta;

	public EntityResultSetProxy(Integer dbType, Connection conn, ResultSet rs, EntityMeta entityMeta) {
		this.dbType = dbType;
		this.conn = conn;
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
			Object obj = rs.getObject(col);
			if (obj == null) {
				return null;
			}
			Class<?> returnType = method.getReturnType();
			// 字符串、日期等通用转换
			return BeanUtil.convertType(obj, fieldMeta.getType(), DataType.getType(returnType.getTypeName()),
					returnType.getTypeName());
		}

		// setter: setAmt(100) → 截取Amt → amt属性
		if (methodName.startsWith("set") && args != null && args.length == 1) {
			String propName = methodName.substring(3);
			FieldMeta fieldMeta = entityMeta.getFieldMeta(propName);
			String col = fieldMeta.getColumnName();
			if (col == null) {
				return superCall.call();
			}
			SqlUtilsExt.resultUpdate(conn, rs, fieldMeta, args[0], dbType, false, true);
			return null;
		}
		// toString/equals等原生方法直接执行
		return superCall.call();
	}

	/**
	 * 创建实体代理对象
	 */
	@SuppressWarnings("unchecked")
	public static <T> T createProxy(Integer dbType, Connection conn, ResultSet rs, Class<T> clazz,
			EntityMeta entityMeta) throws Exception {
		EntityResultSetProxy<T> interceptor = new EntityResultSetProxy<>(dbType, conn, rs, entityMeta);
		// 动态生成子类，拦截全部方法委托到interceptor.intercept
		Class<?> proxyCls = new ByteBuddy().subclass(clazz).method(ElementMatchers.any())
				.intercept(MethodDelegation.to(interceptor)).make().load(clazz.getClassLoader()).getLoaded();
		return (T) proxyCls.getDeclaredConstructor().newInstance();
	}
}
