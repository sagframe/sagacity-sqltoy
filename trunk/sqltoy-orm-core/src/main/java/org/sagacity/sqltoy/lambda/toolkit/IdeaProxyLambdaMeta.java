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
package org.sagacity.sqltoy.lambda.toolkit;

import java.lang.reflect.Field;
import java.lang.reflect.InvocationHandler;
import java.lang.reflect.Proxy;

import org.sagacity.sqltoy.exception.DataAccessException;

/**
 * @description 在IDEA的Evaluate调试窗口中执行的lambda表达式是代理对象,需通过JDK内部结构解析元信息,
 *              基于MyBatis-Plus同名实现改写
 * @author zhongxuchen
 */
public class IdeaProxyLambdaMeta implements LambdaMeta {

	private static final Field FIELD_MEMBER_NAME;
	private static final Field FIELD_MEMBER_NAME_CLAZZ;
	private static final Field FIELD_MEMBER_NAME_NAME;

	static {
		Field memberName = null;
		Field memberClazz = null;
		Field memberNameName = null;
		try {
			Class<?> classDirectMethodHandle = Class.forName("java.lang.invoke.DirectMethodHandle");
			memberName = classDirectMethodHandle.getDeclaredField("member");
			memberName.setAccessible(true);
			Class<?> classMemberName = Class.forName("java.lang.invoke.MemberName");
			memberClazz = classMemberName.getDeclaredField("clazz");
			memberClazz.setAccessible(true);
			memberNameName = classMemberName.getDeclaredField("name");
			memberNameName.setAccessible(true);
		} catch (Throwable e) {
			// JDK模块强封装(InaccessibleObjectException等)下降级:字段置null,构造时给出明确指引
		}
		FIELD_MEMBER_NAME = memberName;
		FIELD_MEMBER_NAME_CLAZZ = memberClazz;
		FIELD_MEMBER_NAME_NAME = memberNameName;
	}

	private final Class<?> clazz;
	private final String name;

	public IdeaProxyLambdaMeta(Proxy func) {
		if (FIELD_MEMBER_NAME == null) {
			throw new DataAccessException(
					"Unable to resolve proxied lambda meta on this JVM (module encapsulation); add --add-opens java.base/java.lang.invoke=ALL-UNNAMED or use a method reference instead of a mocked/evaluated lambda");
		}
		InvocationHandler handler = Proxy.getInvocationHandler(func);
		try {
			Field targetField = handler.getClass().getDeclaredField("val$target");
			targetField.setAccessible(true);
			Object directMethodHandle = targetField.get(handler);
			Object member = FIELD_MEMBER_NAME.get(directMethodHandle);
			clazz = (Class<?>) FIELD_MEMBER_NAME_CLAZZ.get(member);
			name = (String) FIELD_MEMBER_NAME_NAME.get(member);
		} catch (IllegalAccessException | NoSuchFieldException e) {
			throw new DataAccessException(e.getMessage());
		}
	}

	@Override
	public String getImplMethodName() {
		return name;
	}

	@Override
	public Class<?> getInstantiatedClass() {
		return clazz;
	}

	@Override
	public String toString() {
		return clazz.getSimpleName() + "::" + name;
	}
}
