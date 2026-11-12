package org.sagacity.sqltoy.lambda.condition;

import java.util.Collection;
import java.util.Map;

import org.sagacity.sqltoy.lambda.LambdaFn;

/**
 * @description 分组、聚合过滤与排序接口(仅查询场景),形状源自MyBatis-Plus的Func接口(Apache-2.0)经sqltoy-plus移植,本次重构改写;
 *              字段引用按方法泛型独立推断所属实体
 * @param <Children> 具体的条件构建类型
 * @author zhongxuchen
 */
public interface Aggregate<Children> {

	/**
	 * 分组:GROUP BY 字段
	 */
	default <E, R> Children groupBy(LambdaFn<E, R> column) {
		return groupBy(true, column);
	}

	<E, R> Children groupBy(boolean condition, LambdaFn<E, R> column);

	/**
	 * 分组:GROUP BY 字段1, 字段2...
	 */
	default <E> Children groupBy(Collection<LambdaFn<E, ?>> columns) {
		return groupBy(true, columns);
	}

	<E> Children groupBy(boolean condition, Collection<LambdaFn<E, ?>> columns);

	/**
	 * 排序:ORDER BY 字段 ASC
	 */
	default <E, R> Children orderByAsc(LambdaFn<E, R> column) {
		return orderByAsc(true, column);
	}

	default <E, R> Children orderByAsc(boolean condition, LambdaFn<E, R> column) {
		return orderBy(condition, true, column);
	}

	/**
	 * 排序:ORDER BY 字段1 ASC, 字段2 ASC...
	 */
	default <E> Children orderByAsc(Collection<LambdaFn<E, ?>> columns) {
		return orderByAsc(true, columns);
	}

	default <E> Children orderByAsc(boolean condition, Collection<LambdaFn<E, ?>> columns) {
		return orderBy(condition, true, columns);
	}

	/**
	 * 排序:ORDER BY 字段 DESC
	 */
	default <E, R> Children orderByDesc(LambdaFn<E, R> column) {
		return orderByDesc(true, column);
	}

	default <E, R> Children orderByDesc(boolean condition, LambdaFn<E, R> column) {
		return orderBy(condition, false, column);
	}

	/**
	 * 排序:ORDER BY 字段1 DESC, 字段2 DESC...
	 */
	default <E> Children orderByDesc(Collection<LambdaFn<E, ?>> columns) {
		return orderByDesc(true, columns);
	}

	default <E> Children orderByDesc(boolean condition, Collection<LambdaFn<E, ?>> columns) {
		return orderBy(condition, false, columns);
	}

	/**
	 * 排序:ORDER BY 字段, isAsc决定升序或降序
	 */
	<E, R> Children orderBy(boolean condition, boolean isAsc, LambdaFn<E, R> column);

	/**
	 * 排序:多个字段按相同方向排序
	 */
	<E> Children orderBy(boolean condition, boolean isAsc, Collection<LambdaFn<E, ?>> columns);

	/**
	 * ignore
	 */
	default Children having(String sqlHaving) {
		return having(true, sqlHaving, null);
	}

	default Children having(String sqlHaving, Map<String, Object> paramMap) {
		return having(true, sqlHaving, paramMap);
	}

	/**
	 * HAVING 聚合过滤条件,sql中的sqltoy命名参数通过paramMap提供
	 * <p>
	 * 例: having("count(*) > :count", map)
	 * </p>
	 */
	Children having(boolean condition, String sqlHaving, Map<String, Object> paramMap);
}
