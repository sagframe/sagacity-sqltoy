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
package org.sagacity.sqltoy.lambda.condition;

import java.util.Collection;
import java.util.Map;

import org.sagacity.sqltoy.lambda.LambdaFn;
import org.sagacity.sqltoy.utils.StringUtil;

/**
 * @description 比较条件接口,方法族形状源自MyBatis-Plus的Compare接口(Apache-2.0),IfPresent语法糖为sqltoy-plus原创,
 *              按方法泛型独立推断字段所属实体为本次重构;每个方法均提供带condition参数的版本,
 *              condition为false时该条件不参与sql组装;字段引用按方法泛型独立推断所属实体:
 *              单表查询字段须属于查询实体,join查询字段须来自参与join的实体(装配期校验)
 * @param <Children> 具体的条件构建类型
 * @author zhongxuchen
 */
public interface Compare<Children> {

	/**
	 * ignore
	 */
	default <E, R> Children eq(LambdaFn<E, R> column, Object val) {
		return eq(true, column, val);
	}

	/**
	 * 等于 =
	 *
	 * @param condition 执行条件
	 * @param column    字段引用
	 * @param val       值
	 * @return children
	 */
	<E, R> Children eq(boolean condition, LambdaFn<E, R> column, Object val);

	/**
	 * @description 严格等值:val为null时渲染恒假条件1=0,复刻静态SQL <code>column = :p</code>(p=null)
	 *              恒不匹配的语义;非null时等价eq
	 * @param column 字段引用
	 * @param val    值
	 * @return children
	 */
	default <E, R> Children eqStrict(LambdaFn<E, R> column, Object val) {
		return eqStrict(true, column, val);
	}

	/**
	 * @description 严格等值(条件版)
	 * @param condition 执行条件
	 * @param column    字段引用
	 * @param val       值
	 * @return children
	 */
	<E, R> Children eqStrict(boolean condition, LambdaFn<E, R> column, Object val);

	/**
	 * ignore
	 */
	default <E, R> Children ne(LambdaFn<E, R> column, Object val) {
		return ne(true, column, val);
	}

	/**
	 * 不等于 &lt;&gt;
	 */
	<E, R> Children ne(boolean condition, LambdaFn<E, R> column, Object val);

	/**
	 * ignore
	 */
	default <E, R> Children gt(LambdaFn<E, R> column, Object val) {
		return gt(true, column, val);
	}

	/**
	 * 大于 &gt;
	 */
	<E, R> Children gt(boolean condition, LambdaFn<E, R> column, Object val);

	/**
	 * ignore
	 */
	default <E, R> Children ge(LambdaFn<E, R> column, Object val) {
		return ge(true, column, val);
	}

	/**
	 * 大于等于 &gt;=
	 */
	<E, R> Children ge(boolean condition, LambdaFn<E, R> column, Object val);

	/**
	 * ignore
	 */
	default <E, R> Children lt(LambdaFn<E, R> column, Object val) {
		return lt(true, column, val);
	}

	/**
	 * 小于 &lt;
	 */
	<E, R> Children lt(boolean condition, LambdaFn<E, R> column, Object val);

	/**
	 * ignore
	 */
	default <E, R> Children le(LambdaFn<E, R> column, Object val) {
		return le(true, column, val);
	}

	/**
	 * 小于等于 &lt;=
	 */
	<E, R> Children le(boolean condition, LambdaFn<E, R> column, Object val);

	/**
	 * ignore
	 */
	default <E, R> Children between(LambdaFn<E, R> column, Object val1, Object val2) {
		return between(true, column, val1, val2);
	}

	/**
	 * BETWEEN 值1 AND 值2
	 */
	<E, R> Children between(boolean condition, LambdaFn<E, R> column, Object val1, Object val2);

	/**
	 * ignore
	 */
	default <E, R> Children notBetween(LambdaFn<E, R> column, Object val1, Object val2) {
		return notBetween(true, column, val1, val2);
	}

	/**
	 * NOT BETWEEN 值1 AND 值2
	 */
	<E, R> Children notBetween(boolean condition, LambdaFn<E, R> column, Object val1, Object val2);

	/**
	 * ignore
	 */
	default <E, R> Children like(LambdaFn<E, R> column, Object val) {
		return like(true, column, val);
	}

	/**
	 * LIKE '%值%'
	 */
	<E, R> Children like(boolean condition, LambdaFn<E, R> column, Object val);

	/**
	 * ignore
	 */
	default <E, R> Children notLike(LambdaFn<E, R> column, Object val) {
		return notLike(true, column, val);
	}

	/**
	 * NOT LIKE '%值%'
	 */
	<E, R> Children notLike(boolean condition, LambdaFn<E, R> column, Object val);

	/**
	 * ignore
	 */
	default <E, R> Children likeLeft(LambdaFn<E, R> column, Object val) {
		return likeLeft(true, column, val);
	}

	/**
	 * LIKE '%值' ,以值结尾
	 */
	<E, R> Children likeLeft(boolean condition, LambdaFn<E, R> column, Object val);

	/**
	 * ignore
	 */
	default <E, R> Children likeRight(LambdaFn<E, R> column, Object val) {
		return likeRight(true, column, val);
	}

	/**
	 * 包含匹配,但值中的%按字面量处理
	 */
	default <E, R> Children likeExact(LambdaFn<E, R> column, Object val) {
		return likeExact(true, column, val);
	}

	<E, R> Children likeExact(boolean condition, LambdaFn<E, R> column, Object val);

	/**
	 * 后缀匹配(左侧补%),值中的%按字面量处理
	 */
	default <E, R> Children likeExactLeft(LambdaFn<E, R> column, Object val) {
		return likeExactLeft(true, column, val);
	}

	<E, R> Children likeExactLeft(boolean condition, LambdaFn<E, R> column, Object val);

	/**
	 * 前缀匹配(右侧补%),值中的%按字面量处理
	 */
	default <E, R> Children likeExactRight(LambdaFn<E, R> column, Object val) {
		return likeExactRight(true, column, val);
	}

	<E, R> Children likeExactRight(boolean condition, LambdaFn<E, R> column, Object val);

	/**
	 * LIKE '值%' ,以值开头
	 */
	<E, R> Children likeRight(boolean condition, LambdaFn<E, R> column, Object val);

	/**
	 * ignore
	 */
	default <E, R> Children isNull(LambdaFn<E, R> column) {
		return isNull(true, column);
	}

	/**
	 * 字段 IS NULL
	 */
	<E, R> Children isNull(boolean condition, LambdaFn<E, R> column);

	/**
	 * ignore
	 */
	default <E, R> Children isNotNull(LambdaFn<E, R> column) {
		return isNotNull(true, column);
	}

	/**
	 * 字段 IS NOT NULL
	 */
	<E, R> Children isNotNull(boolean condition, LambdaFn<E, R> column);

	/**
	 * ignore
	 */
	default <E, R> Children in(LambdaFn<E, R> column, Collection<?> coll) {
		return in(true, column, coll);
	}

	/**
	 * 字段 IN (值1, 值2, ...) ,集合为空则不参与sql组装
	 */
	<E, R> Children in(boolean condition, LambdaFn<E, R> column, Collection<?> coll);

	/**
	 * ignore
	 */
	default <E, R> Children in(LambdaFn<E, R> column, Object... values) {
		return in(true, column, values);
	}

	/**
	 * 字段 IN (值1, 值2, ...) ,数组为空则不参与sql组装
	 */
	<E, R> Children in(boolean condition, LambdaFn<E, R> column, Object... values);

	/**
	 * ignore
	 */
	default <E, R> Children inSql(LambdaFn<E, R> column, String subSql) {
		return inSql(true, column, subSql, null);
	}

	/**
	 * ignore
	 */
	default <E, R> Children inSql(LambdaFn<E, R> column, String subSql, Map<String, Object> paramMap) {
		return inSql(true, column, subSql, paramMap);
	}

	/**
	 * 字段 IN (子查询sql),子查询中的sqltoy命名参数通过paramMap提供
	 *
	 * @param condition 执行条件
	 * @param column    字段引用
	 * @param subSql    子查询sql
	 * @param paramMap  子查询条件值
	 * @return children
	 */
	<E, R> Children inSql(boolean condition, LambdaFn<E, R> column, String subSql, Map<String, Object> paramMap);

	/**
	 * ignore
	 */
	default <E> Children inb(Collection<LambdaFn<E, ?>> columns, Collection<Object[]> values) {
		return inb(true, columns, values);
	}

	/**
	 * 多字段元组in:(字段1,字段2...) IN ((值1,值2...), (值3,值4...)...)
	 *
	 * @param condition 执行条件
	 * @param columns   字段引用集合(单表须属于查询实体,join模式可跨已join实体)
	 * @param values    每行一个Object[]的元组值集合
	 * @return children
	 */
	<E> Children inb(boolean condition, Collection<LambdaFn<E, ?>> columns, Collection<Object[]> values);

	/**
	 * ignore
	 */
	default <E, R1, R2> Children inb(LambdaFn<E, R1> columnA, LambdaFn<E, R2> columnB, Collection<Object[]> values) {
		return inb(true, columnA, columnB, values);
	}

	/**
	 * 两字段元组in:(字段A,字段B) IN ((值A1,值B1), (值A2,值B2)...)
	 *
	 * @param condition 执行条件
	 * @param columnA   字段A
	 * @param columnB   字段B(与字段A须属于同一实体)
	 * @param values    每行一个Object[]{a值,b值}的元组值集合
	 * @return children
	 */
	<E, R1, R2> Children inb(boolean condition, LambdaFn<E, R1> columnA, LambdaFn<E, R2> columnB,
			Collection<Object[]> values);

	/**
	 * ignore
	 */
	default <E, R> Children notIn(LambdaFn<E, R> column, Collection<?> coll) {
		return notIn(true, column, coll);
	}

	/**
	 * 字段 NOT IN (值1, 值2, ...) ,集合为空则不参与sql组装
	 */
	<E, R> Children notIn(boolean condition, LambdaFn<E, R> column, Collection<?> coll);

	/**
	 * ignore
	 */
	default <E, R> Children notIn(LambdaFn<E, R> column, Object... values) {
		return notIn(true, column, values);
	}

	/**
	 * 字段 NOT IN (值1, 值2, ...) ,数组为空则不参与sql组装
	 */
	<E, R> Children notIn(boolean condition, LambdaFn<E, R> column, Object... values);

	/******************************* 语法糖:值为空时条件自动跳过 *******************************/

	default <E, R> Children eqIfPresent(LambdaFn<E, R> column, Object val) {
		return eq(val != null, column, val);
	}

	default <E, R> Children neIfPresent(LambdaFn<E, R> column, Object val) {
		return ne(val != null, column, val);
	}

	default <E, R> Children gtIfPresent(LambdaFn<E, R> column, Object val) {
		return gt(val != null, column, val);
	}

	default <E, R> Children geIfPresent(LambdaFn<E, R> column, Object val) {
		return ge(val != null, column, val);
	}

	default <E, R> Children ltIfPresent(LambdaFn<E, R> column, Object val) {
		return lt(val != null, column, val);
	}

	default <E, R> Children leIfPresent(LambdaFn<E, R> column, Object val) {
		return le(val != null, column, val);
	}

	default <E, R> Children likeIfPresent(LambdaFn<E, R> column, String val) {
		return like(StringUtil.isNotBlank(val), column, val);
	}

	default <E, R> Children inIfPresent(LambdaFn<E, R> column, Collection<?> values) {
		return in(values != null && !values.isEmpty(), column, values);
	}

	default <E, R> Children inIfPresent(LambdaFn<E, R> column, Object... values) {
		return in(values != null && values.length > 0, column, values);
	}

	default <E, R> Children notInIfPresent(LambdaFn<E, R> column, Collection<?> values) {
		return notIn(values != null && !values.isEmpty(), column, values);
	}

	/**
	 * 值均不为null时BETWEEN;仅有下限值时等效ge;仅有上限值时等效le
	 */
	default <E, R> Children betweenIfPresent(LambdaFn<E, R> column, Object val1, Object val2) {
		if (val1 != null && val2 != null) {
			return between(true, column, val1, val2);
		}
		if (val1 != null) {
			return ge(true, column, val1);
		}
		return le(val2 != null, column, val2);
	}

	/**
	 * @see #betweenIfPresent(LambdaFn, Object, Object)
	 */
	default <E, R> Children betweenIfPresent(LambdaFn<E, R> column, Object[] values) {
		if (values == null || values.length == 0) {
			return between(false, column, null, null);
		}
		return betweenIfPresent(column, values[0], values.length > 1 ? values[1] : null);
	}
}
