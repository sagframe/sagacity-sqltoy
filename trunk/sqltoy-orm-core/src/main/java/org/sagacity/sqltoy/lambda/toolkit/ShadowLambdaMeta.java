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

import org.sagacity.sqltoy.utils.BeanUtil;

/**
 * @description 基于序列化镜像{@link SerializedLambda}读取的lambda元信息,
 *              基于MyBatis-Plus同名实现改写
 * @author zhongxuchen
 */
public class ShadowLambdaMeta implements LambdaMeta {

	private final SerializedLambda lambda;

	public ShadowLambdaMeta(SerializedLambda lambda) {
		this.lambda = lambda;
	}

	@Override
	public String getImplMethodName() {
		return lambda.getImplMethodName();
	}

	@Override
	public Class<?> getInstantiatedClass() {
		String instantiatedMethodType = lambda.getInstantiatedMethodType();
		String instantiatedType = instantiatedMethodType.substring(2, instantiatedMethodType.indexOf(';')).replace('/',
				'.');
		return BeanUtil.loadClass(instantiatedType, lambda.getCapturingClass().getClassLoader());
	}
}
