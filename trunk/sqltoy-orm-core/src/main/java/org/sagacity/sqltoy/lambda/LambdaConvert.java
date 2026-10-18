package org.sagacity.sqltoy.lambda;

import java.util.Map;

import org.sagacity.sqltoy.exception.DataAccessException;
import org.sagacity.sqltoy.lambda.engine.AbstractWhere;
import org.sagacity.sqltoy.model.EntityQuery;
import org.sagacity.sqltoy.model.EntityUpdate;
import org.sagacity.sqltoy.utils.StringUtil;

/**
 * @description lambda条件对象到sqltoy执行模型的转换器:单表条件转为{@link EntityQuery}/{@link EntityUpdate},
 *              复用sqltoy既有的实体查询、删除与更新执行链路,由LightDao的default方法在assemble之后调用
 * @author zhongxuchen
 */
public final class LambdaConvert {

	private LambdaConvert() {
	}

	/**
	 * @description lambda查询条件转换为EntityQuery(仅单表通道)
	 * @param query 查询条件,须已完成assemble
	 */
	public static <T> EntityQuery toEntityQuery(LambdaQuery<T> query) {
		if (query.hasLastSql()) {
			throw new DataAccessException(
					"last() raw sql append is not supported in the single-table channel (EntityQuery where cannot carry trailing raw sql); use findTop/findPage or the join channel");
		}
		EntityQuery entityQuery = EntityQuery.create();
		if (StringUtil.isNotBlank(query.getSqlSegment())) {
			entityQuery.where(query.getSqlSegment());
		}
		if (!query.getParams().isEmpty()) {
			entityQuery.values(query.getParams());
		}
		if (!query.getSelectFields().isEmpty()) {
			entityQuery.select(query.getSelectFields().toArray(new String[0]));
		}
		if (!query.getExcludeFields().isEmpty()) {
			entityQuery.unselect(query.getExcludeFields().toArray(new String[0]));
		}
		if (query.isDistinct()) {
			entityQuery.distinct();
		}
		return entityQuery;
	}

	/**
	 * @description lambda条件转换为按条件删除的EntityQuery,并做无条件防护
	 */
	public static EntityQuery toDeleteQuery(AbstractWhere<?, ?> where) {
		where.requireCondition("delete");
		EntityQuery entityQuery = EntityQuery.create();
		String whereSql = where.getSqlSegment();
		Map<String, Object> params = where.getParams();
		if (params.isEmpty()) {
			// where无命名参数时补齐参数个数(deleteByQuery对where/values空值直接拒绝),与更新通道同口径
			whereSql = StringUtil.isBlank(whereSql) ? "1=:DEFAULT_VALUE " : "1=:DEFAULT_VALUE AND " + whereSql;
			params.put("DEFAULT_VALUE", 1);
		}
		entityQuery.where(whereSql);
		entityQuery.values(params);
		return entityQuery;
	}

	/**
	 * @description lambda更新条件转换为EntityUpdate,set/merge字段即更新字段,并做无条件防护;
	 *               where中无命名参数时补齐参数个数,规避updateByQuery的参数计数校验异常
	 */
	public static <T> EntityUpdate toEntityUpdate(LambdaUpdate<T> update) {
		if (update.getSetFields().isEmpty()) {
			throw new DataAccessException(
					"Lambda update requires at least one field set via set() or setExpr()");
		}
		update.requireCondition("update");
		EntityUpdate entityUpdate = EntityUpdate.create();
		update.getSetFields().forEach(entityUpdate::set);
		String whereSql = update.getSqlSegment();
		Map<String, Object> params = update.getParams();
		if (params.isEmpty()) {
			// where中无命名参数时补齐参数个数,规避updateByQuery对where与values数量的校验异常
			whereSql = StringUtil.isBlank(whereSql) ? "1=:DEFAULT_VALUE " : "1=:DEFAULT_VALUE AND " + whereSql;
			params.put("DEFAULT_VALUE", 1);
		}
		return entityUpdate.where(whereSql).values(params);
	}
}
