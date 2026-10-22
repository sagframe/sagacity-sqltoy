/*
 * Copyright (c) 2011-2022, baomidou (jobob@qq.com).
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package org.sagacity.sqltoy.lambda;

import java.lang.reflect.Method;
import java.lang.reflect.Proxy;

import org.sagacity.sqltoy.exception.DataAccessException;
import org.sagacity.sqltoy.lambda.toolkit.FieldNature;
import org.sagacity.sqltoy.lambda.toolkit.IdeaProxyLambdaMeta;
import org.sagacity.sqltoy.lambda.toolkit.LambdaMeta;
import org.sagacity.sqltoy.lambda.toolkit.ReflectLambdaMeta;
import org.sagacity.sqltoy.lambda.toolkit.SerializedLambda;
import org.sagacity.sqltoy.lambda.toolkit.ShadowLambdaMeta;
import org.sagacity.sqltoy.model.FIFOMap;
import org.sagacity.sqltoy.utils.BeanUtil;

/**
 * @description lambda解析工具类,提取方法引用对应的属性名和实体类,内部对解析结果做了缓存, 基于MyBatis-Plus同名实现改写
 * @author zhongxuchen
 */
public final class LambdaUtils {

	/**
	 * 解析结果缓存:非捕获型方法引用在jvm内是单例,重复构建同一查询结构时直接命中缓存。
	 * 使用有界accessOrder缓存,避免捕获型lambda导致无界增长;get/put由FIFOMap同步保护,
	 * 解析(可能含对象序列化)在锁外执行,允许并发重复解析但不会破坏缓存结构
	 */
	private static final FIFOMap<LambdaFn<?, ?>, LambdaMeta> CACHE = new FIFOMap<>(2048, 8192, true);

	private LambdaUtils() {
	}

	/**
	 * @param func 需要解析的lambda引用
	 * @return 解析后的元信息
	 */
	public static <T, R> LambdaMeta extract(LambdaFn<T, R> func) {
		LambdaMeta meta = CACHE.get(func);
		if (meta != null) {
			return meta;
		}
		// 解析(可能含对象序列化)在锁外执行,FIFOMap只在get/put时短暂持锁
		meta = doExtract(func);
		CACHE.put(func, meta);
		return meta;
	}

	/**
	 * @description 元信息有效性校验:代理/序列化路径可能产出空元信息,统一转为明确异常
	 */
	private static LambdaMeta validate(LambdaMeta meta) {
		String implMethodName;
		try {
			implMethodName = (meta == null) ? null : meta.getImplMethodName();
		} catch (Throwable e) {
			implMethodName = null;
		}
		if (implMethodName == null) {
			throw new DataAccessException(
					"Unable to resolve proxied lambda (IDEA evaluate/Mockito mock); use a method reference instead");
		}
		return meta;
	}

	private static LambdaMeta doExtract(LambdaFn<?, ?> func) {
		// 1.IDEA调试窗口或mock框架中lambda表达式是一个代理,代理元信息不可用时回退序列化路径
		if (func instanceof Proxy) {
			try {
				return validate(new IdeaProxyLambdaMeta((Proxy) func));
			} catch (Throwable ideaError) {
				// 代理元信息不可用时回退序列化路径,两者都失败给出明确指引
				try {
					return validate(new ShadowLambdaMeta(SerializedLambda.extract(func)));
				} catch (Throwable shadowError) {
					throw new DataAccessException(
							"Unable to resolve proxied lambda (IDEA evaluate/Mockito mock); use a method reference instead",
							shadowError);
				}
			}
		}
		// 2.反射读取writeReplace
		try {
			Method method = func.getClass().getDeclaredMethod("writeReplace");
			method.setAccessible(true);
			return new ReflectLambdaMeta((java.lang.invoke.SerializedLambda) method.invoke(func));
		} catch (Throwable e) {
			// 3.反射失败通过序列化方式读取
			return new ShadowLambdaMeta(SerializedLambda.extract(func));
		}
	}

	/**
	 * @param func 需要解析的lambda引用
	 * @return 方法引用对应的实体属性名
	 */
	public static <T, R> String extractProperty(LambdaFn<T, R> func) {
		return BeanUtil.methodToProperty(extract(func).getImplMethodName());
	}

	/**
	 * @param func 需要解析的lambda引用
	 * @return 属性名及所属实体类的二元组
	 */
	public static <T, R> FieldNature extractNature(LambdaFn<T, R> func) {
		LambdaMeta meta = extract(func);
		return new FieldNature(meta.getInstantiatedClass(), BeanUtil.methodToProperty(meta.getImplMethodName()));
	}
}
