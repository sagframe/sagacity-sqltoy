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
package org.sagacity.sqltoy.lambda.engine;

import java.util.function.Predicate;

/**
 * @description 片段类型匹配,基于MyBatis-Plus同名实现改写
 * @author zhongxuchen
 */
public enum MatchSegment {
	GROUP_BY(i -> i == SqlKeyword.GROUP_BY), ORDER_BY(i -> i == SqlKeyword.ORDER_BY), NOT(i -> i == SqlKeyword.NOT),
	AND(i -> i == SqlKeyword.AND), OR(i -> i == SqlKeyword.OR),
	AND_OR(i -> i == SqlKeyword.AND || i == SqlKeyword.OR), HAVING(i -> i == SqlKeyword.HAVING);

	private final Predicate<SqlSegment> predicate;

	MatchSegment(Predicate<SqlSegment> predicate) {
		this.predicate = predicate;
	}

	public boolean match(SqlSegment segment) {
		return predicate.test(segment);
	}

	public Predicate<SqlSegment> getPredicate() {
		return predicate;
	}
}
