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

import org.sagacity.sqltoy.exception.DataAccessException;

import java.io.Serializable;

/**
 * @description {@link java.lang.invoke.SerializedLambda}的镜像类,反射读取writeReplace失败时
 *              通过序列化方式解析lambda,基于MyBatis-Plus同名实现改写
 * @author zhongxuchen
 */
public class SerializedLambda implements Serializable {

	private static final long serialVersionUID = 8025925345765570181L;

	private Class<?> capturingClass;
	private String functionalInterfaceClass;
	private String functionalInterfaceMethodName;
	private String functionalInterfaceMethodSignature;
	private String implClass;
	private String implMethodName;
	private String implMethodSignature;
	private int implMethodKind;
	private String instantiatedMethodType;
	private Object[] capturedArgs;

	public static SerializedLambda extract(Serializable serializable) {
		try (java.io.ByteArrayOutputStream baos = new java.io.ByteArrayOutputStream();
				java.io.ObjectOutputStream oos = new java.io.ObjectOutputStream(baos)) {
			oos.writeObject(serializable);
			oos.flush();
			try (java.io.ObjectInputStream ois = new java.io.ObjectInputStream(
					new java.io.ByteArrayInputStream(baos.toByteArray())) {
				@Override
				protected Class<?> resolveClass(java.io.ObjectStreamClass desc)
						throws java.io.IOException, ClassNotFoundException {
					Class<?> clazz = super.resolveClass(desc);
					return clazz == java.lang.invoke.SerializedLambda.class ? SerializedLambda.class : clazz;
				}
			}) {
				return (SerializedLambda) ois.readObject();
			}
		} catch (java.io.IOException | ClassNotFoundException e) {
			throw new DataAccessException(e.getMessage());
		}
	}

	public String getInstantiatedMethodType() {
		return instantiatedMethodType;
	}

	public Class<?> getCapturingClass() {
		return capturingClass;
	}

	public String getImplMethodName() {
		return implMethodName;
	}
}
