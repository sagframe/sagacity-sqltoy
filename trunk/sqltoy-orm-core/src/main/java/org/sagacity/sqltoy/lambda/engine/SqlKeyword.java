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

/**
 * @description sql保留关键字枚举,基于MyBatis-Plus同名实现改写
 * @author zhongxuchen
 */
public enum SqlKeyword implements SqlSegment {
	AND("AND"), OR("OR"), NOT("NOT"), IN("IN"), NOT_IN("NOT IN"), LIKE("LIKE"), NOT_LIKE("NOT LIKE"), EQ("="),
	NE("<>"), GT(">"), GE(">="), LT("<"), LE("<="), IS_NULL("IS NULL"), IS_NOT_NULL("IS NOT NULL"), GROUP_BY("GROUP BY"),
	HAVING("HAVING"), ORDER_BY("ORDER BY"), EXISTS("EXISTS"), NOT_EXISTS("NOT EXISTS"), BETWEEN("BETWEEN"),
	NOT_BETWEEN("NOT BETWEEN"), ASC("ASC"), DESC("DESC"), FROM("FROM"), LEFT_JOIN("LEFT JOIN"),
	RIGHT_JOIN("RIGHT JOIN"), INNER_JOIN("INNER JOIN"), WHERE("WHERE"), ON("ON"), SELECT("SELECT"), DELETE("DELETE"),
	UPDATE("UPDATE"), SET("SET");

	private final String keyword;

	SqlKeyword(String keyword) {
		this.keyword = keyword;
	}

	@Override
	public String getSqlSegment() {
		return keyword;
	}
}
