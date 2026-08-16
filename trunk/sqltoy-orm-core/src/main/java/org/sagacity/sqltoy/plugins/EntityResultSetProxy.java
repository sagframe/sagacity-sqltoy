package org.sagacity.sqltoy.plugins;

import java.lang.reflect.Method;
import java.lang.reflect.Modifier;
import java.lang.reflect.ParameterizedType;
import java.lang.reflect.Type;
import java.sql.Connection;
import java.sql.ResultSet;
import java.util.concurrent.Callable;

import org.sagacity.sqltoy.config.model.DataType;
import org.sagacity.sqltoy.config.model.EntityMeta;
import org.sagacity.sqltoy.config.model.FieldMeta;
import org.sagacity.sqltoy.utils.BeanUtil;
import org.sagacity.sqltoy.utils.DataSourceUtils;
import org.sagacity.sqltoy.utils.SqlUtilsExt;

import net.bytebuddy.ByteBuddy;
import net.bytebuddy.TypeCache;
import net.bytebuddy.implementation.FieldAccessor;
import net.bytebuddy.implementation.MethodDelegation;
import net.bytebuddy.implementation.bind.annotation.AllArguments;
import net.bytebuddy.implementation.bind.annotation.FieldValue;
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
	private TypeHandler typeHandler;
	private int dbType;
	private Connection conn;
	private final ResultSet rs;
	private EntityMeta entityMeta;

	public EntityResultSetProxy(TypeHandler typeHandler, Integer dbType, Connection conn, ResultSet rs,
			EntityMeta entityMeta) {
		this.typeHandler = typeHandler;
		// dbType为Integer,直接赋值给int字段在null时拆箱NPE
		this.dbType = (dbType == null) ? DataSourceUtils.DBType.UNDEFINE : dbType.intValue();
		this.conn = conn;
		this.rs = rs;
		this.entityMeta = entityMeta;
	}

	/**
	 * 静态委托分发器:独立嵌套类且只暴露唯一候选方法,避免外部类的其他静态方法被纳入委托候选;
	 * 必须为public,因为代理类由子类加载器加载,包私有类跨加载器不可访问
	 */
	public static class Delegate {
		@RuntimeType
		public static Object intercept(@FieldValue(INTERCEPTOR_FIELD) EntityResultSetProxy<?> delegate,
				@This Object proxy, @Origin Method method, @AllArguments Object[] args,
				@SuperCall Callable<?> superCall) throws Exception {
			return delegate.doIntercept(proxy, method, args, superCall);
		}
	}

	private Object doIntercept(Object proxy, Method method, Object[] args, Callable<?> superCall) throws Exception {
		String methodName = method.getName();
		// getter: getAmt → 截取Amt → amt属性
		if ((methodName.startsWith("get") || methodName.startsWith("is")) && args.length == 0) {
			int subSize = methodName.startsWith("get") ? 3 : 2;
			String propName = methodName.substring(subSize);
			FieldMeta fieldMeta = entityMeta.getFieldMeta(propName);
			if (fieldMeta == null) {
				return superCall.call();
			}
			Object columnValue = rs.getObject(fieldMeta.getColumnName());
			if (columnValue == null) {
				return null;
			}
			Class<?> returnType = method.getReturnType();
			// 取泛型
			Class GenericType = null;
			Type type = method.getGenericReturnType();
			if (type instanceof ParameterizedType) {
				GenericType = (Class) ((ParameterizedType) type).getActualTypeArguments()[0];
			}
			// 字符串、日期等通用转换
			return BeanUtil.convertType(typeHandler, columnValue, fieldMeta.getType(),
					DataType.getType(returnType.getTypeName()), returnType.getTypeName(), GenericType);
		}

		// setter: setAmt(100) → 截取Amt → amt属性
		if (methodName.startsWith("set") && args != null && args.length == 1) {
			String propName = methodName.substring(3);
			FieldMeta fieldMeta = entityMeta.getFieldMeta(propName);
			if (fieldMeta == null) {
				return superCall.call();
			}
			SqlUtilsExt.resultUpdate(typeHandler, conn, rs, fieldMeta, args[0], dbType, false, true);
			return null;
		}
		// toString/equals等原生方法直接执行
		return superCall.call();
	}

	/**
	 * 代理实例承载行拦截器的接口,生成的代理类实现该接口用于注入与当前结果集行绑定的拦截器
	 */
	public interface InterceptorHolder {
		void $sqltoy$setInterceptor(EntityResultSetProxy<?> interceptor);
	}

	/**
	 * 代理类缓存:类加载器为弱引用key(不影响热部署时类加载器回收),同一实体类全局只生成并加载一次代理类;
	 * 拦截器按行绑定到代理实例字段,避免原先每行生成加载一个代理类导致Metaspace持续增长直至OOM
	 */
	private final static TypeCache<String> PROXY_CLASS_CACHE = new TypeCache.WithInlineExpunction<String>(
			TypeCache.Sort.WEAK);

	private final static String INTERCEPTOR_FIELD = "sqltoy$interceptor";

	/**
	 * 创建实体代理对象
	 */
	@SuppressWarnings("unchecked")
	public static <T> T createProxy(TypeHandler typeHandler, Integer dbType, Connection conn, ResultSet rs,
			Class<T> clazz, EntityMeta entityMeta) throws Exception {
		EntityResultSetProxy<T> interceptor = new EntityResultSetProxy<>(typeHandler, dbType, conn, rs, entityMeta);
		// 动态生成子类:实体方法委托到静态intercept,经实例字段找到行拦截器;代理类按实体类缓存
		Class<?> proxyCls = PROXY_CLASS_CACHE.findOrInsert(clazz.getClassLoader(), clazz.getName(), () -> {
			try {
				return new ByteBuddy().subclass(clazz)
						.defineField(INTERCEPTOR_FIELD, EntityResultSetProxy.class, Modifier.PRIVATE)
						.implement(InterceptorHolder.class).intercept(FieldAccessor.ofField(INTERCEPTOR_FIELD))
						.method(ElementMatchers.not(ElementMatchers.isDeclaredBy(InterceptorHolder.class)))
						.intercept(MethodDelegation.to(Delegate.class)).make().load(clazz.getClassLoader()).getLoaded();
			} catch (Exception e) {
				throw new IllegalStateException("创建实体结果集代理类失败: " + clazz.getName(), e);
			}
		});
		Object proxy = proxyCls.getDeclaredConstructor().newInstance();
		((InterceptorHolder) proxy).$sqltoy$setInterceptor(interceptor);
		return (T) proxy;
	}
}
