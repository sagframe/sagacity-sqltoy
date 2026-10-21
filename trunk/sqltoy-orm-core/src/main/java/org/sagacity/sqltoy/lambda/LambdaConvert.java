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
		String groupBy = query.getGroupByColumns();
		String having = query.getHavingCondition();
		String orderBy = query.getOrderByColumns();
		// 单表EntityQuery没有聚合select通道,having-only会被错误塞进where生成
		// "WHERE HAVING ..."非法SQL;此处快速失败,避免运行时才暴露
		if (StringUtil.isBlank(groupBy) && StringUtil.isNotBlank(having)) {
			throw new DataAccessException(
					"Lambda having without groupBy is not supported by the single-table channel; add groupBy or use the join/SQL channel with an aggregate select");
		}
		if (StringUtil.isNotBlank(query.getWhereSql())) {
			entityQuery.where(query.getWhereSql());
		}
		if (StringUtil.isNotBlank(groupBy)) {
			entityQuery.groupBy(groupBy);
		}
		if (StringUtil.isNotBlank(having)) {
			entityQuery.having(having);
		}
		if (StringUtil.isNotBlank(orderBy)) {
			entityQuery.orderBy(orderBy);
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
		if (query.getDataSource() != null) {
			entityQuery.dataSource(query.getDataSource());
		}
		if (query.getPageOptimize() != null) {
			entityQuery.pageOptimize(query.getPageOptimize());
		}
		return entityQuery;
	}

	/**
	 * @description lambda条件转换为按条件删除的EntityQuery,并做无条件防护
	 */
	public static EntityQuery toDeleteQuery(AbstractWhere<?, ?> where) {
		where.requireCondition("delete");
		if (where.hasLastSql()) {
			throw new DataAccessException(
					"last() raw sql append is not supported in the single-table delete channel (EntityQuery where cannot carry trailing raw sql); use explicit conditions or the join channel");
		}
		EntityQuery entityQuery = EntityQuery.create();
		String whereSql = where.getSqlSegment();
		Map<String, Object> params = where.getParams();
		if (StringUtil.isBlank(whereSql)) {
			// 兜底防护:只有显式matchAll()才允许无条件删除
			if (!where.isMatchAll()) {
				throw new DataAccessException(
						"Lambda conditional delete requires where conditions; call matchAll() explicitly to match all records");
			}
			whereSql = "1=:DEFAULT_VALUE ";
			params.put("DEFAULT_VALUE", 1);
		} else if (params.isEmpty()) {
			// where无命名参数时补齐参数个数(deleteByQuery对where/values空值直接拒绝),与更新通道同口径
			whereSql = "1=:DEFAULT_VALUE AND " + whereSql;
			params.put("DEFAULT_VALUE", 1);
		}
		entityQuery.where(whereSql);
		entityQuery.values(params);
		if (where.getDataSource() != null) {
			entityQuery.dataSource(where.getDataSource());
		}
		return entityQuery;
	}

	/**
	 * @description lambda更新条件转换为EntityUpdate,set/merge字段即更新字段,并做无条件防护;
	 *              where中无命名参数时补齐参数个数,规避updateByQuery的参数计数校验异常
	 */
	public static <T> EntityUpdate toEntityUpdate(LambdaUpdate<T> update) {
		if (update.getSetFields().isEmpty()) {
			throw new DataAccessException(
					"Lambda update requires at least one field set via set(), setNull() or setExpr()");
		}
		update.requireCondition("update");
		if (update.hasLastSql()) {
			throw new DataAccessException(
					"last() raw sql append is not supported in the single-table update channel (EntityUpdate where cannot carry trailing raw sql); use explicit conditions or the join channel");
		}
		EntityUpdate entityUpdate = EntityUpdate.create();
		update.getSetFields().forEach(entityUpdate::set);
		String whereSql = update.getSqlSegment();
		Map<String, Object> params = update.getParams();
		if (StringUtil.isBlank(whereSql)) {
			// 兜底防护:只有显式matchAll()才允许无条件更新
			if (!update.isMatchAll()) {
				throw new DataAccessException(
						"Lambda conditional update requires where conditions; call matchAll() explicitly to match all records");
			}
			whereSql = "1=:DEFAULT_VALUE ";
			params.put("DEFAULT_VALUE", 1);
		} else if (params.isEmpty()) {
			// where中无命名参数时补齐参数个数,规避updateByQuery对where与values数量的校验异常
			whereSql = "1=:DEFAULT_VALUE AND " + whereSql;
			params.put("DEFAULT_VALUE", 1);
		}
		if (update.getDataSource() != null) {
			entityUpdate.dataSource(update.getDataSource());
		}
		return entityUpdate.where(whereSql).values(params);
	}
}
