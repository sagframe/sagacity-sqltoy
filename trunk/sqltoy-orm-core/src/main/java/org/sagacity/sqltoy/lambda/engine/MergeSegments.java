package org.sagacity.sqltoy.lambda.engine;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * @description 条件片段总装容器:源自MyBatis-Plus的MergeSegments(Apache-2.0)经sqltoy-plus移植;将片段路由到where/groupBy/having/orderBy四个分区,
 *              分区各自规整后拼接为最终sql,仅存在分组或排序时以<code>1 = 1</code>补齐where
 * @author zhongxuchen
 */
public class MergeSegments implements SqlSegment {

	private final SegmentList normal = new NormalSegmentList();
	private final SegmentList groupBy = new GroupBySegmentList();
	private final SegmentList having = new HavingSegmentList();
	private final SegmentList orderBy = new OrderBySegmentList();
	private String lastSql;

	@Override
	public String getSqlSegment() {
		String sql = null;
		if (normal.isEmpty()) {
			if (!groupBy.isEmpty() || !orderBy.isEmpty() || !having.isEmpty()) {
				sql = "1 = 1 " + groupBy.getSqlSegment() + having.getSqlSegment() + orderBy.getSqlSegment();
			}
		} else {
			sql = normal.getSqlSegment() + groupBy.getSqlSegment() + having.getSqlSegment() + orderBy.getSqlSegment();
		}
		// 拼接最后的sql;无任何条件时以1=1承载last,避免last被静默丢弃
		if (lastSql != null && !lastSql.isEmpty()) {
			sql = (sql == null ? "1 = 1 " : sql + " ") + lastSql;
		}
		return sql;
	}

	@Override
	public Map<String, Object> getParams() {
		Map<String, Object> merged = new HashMap<>();
		for (Map<String, Object> part : Arrays.asList(normal.getParams(), groupBy.getParams(), having.getParams(),
				orderBy.getParams())) {
			if (part == null) {
				continue;
			}
			SegmentList.mergeParams(merged, part);
		}
		return merged;
	}

	/**
	 * @return 是否设置了last原始sql片段
	 */
	public boolean hasLastSql() {
		return lastSql != null && !lastSql.isEmpty();
	}

	/**
	 * @return last()原始sql片段(未设置返回null)
	 */
	public String getLastSql() {
		return lastSql;
	}

	/**
	 * @return 是否存在orderBy排序分区
	 */
	public boolean hasOrderBy() {
		return !orderBy.isEmpty();
	}

	/**
	 * @description 不含orderBy/last的where+groupBy+having片段;供union根排序拆分为
	 *              "分支SQL"与"union之后的orderBy/last"两部分
	 * @return sql片段,无where/groupBy/having时返回null
	 */
	public String getSqlSegmentWithoutOrderBy() {
		String sql = null;
		if (normal.isEmpty()) {
			if (!groupBy.isEmpty() || !having.isEmpty()) {
				sql = "1 = 1 " + groupBy.getSqlSegment() + having.getSqlSegment();
			}
		} else {
			sql = normal.getSqlSegment() + groupBy.getSqlSegment() + having.getSqlSegment();
		}
		return sql;
	}

	/**
	 * @description orderBy+last尾部片段(union根排序/限制须位于整个union之后)
	 * @return 尾部片段,均为空时返回空串
	 */
	public String getOrderByTailSql() {
		String orderSql = orderBy.getSqlSegment();
		if (!hasLastSql()) {
			return orderSql;
		}
		return orderSql.isEmpty() ? lastSql : orderSql + " " + lastSql;
	}

	/**
	 * @return 是否存在有效where条件片段(排除只有AND/OR/NOT连接符的情况)
	 */
	public boolean hasCondition() {
		return normal.hasEffectiveSegment();
	}

	/**
	 * @return 是否存在分组/聚合/排序分区(更新删除构建器不支持)
	 */
	public boolean hasAggregatePartitions() {
		return !groupBy.isEmpty() || !having.isEmpty() || !orderBy.isEmpty();
	}

	public void setLastSql(String lastSql) {
		this.lastSql = lastSql;
	}

	public void add(SqlSegment... sqlSegments) {
		List<SqlSegment> list = new ArrayList<>(Arrays.asList(sqlSegments));
		SqlSegment firstSqlSegment = list.get(0);
		if (MatchSegment.ORDER_BY.match(firstSqlSegment)) {
			orderBy.addAll(list);
		} else if (MatchSegment.GROUP_BY.match(firstSqlSegment)) {
			groupBy.addAll(list);
		} else if (MatchSegment.HAVING.match(firstSqlSegment)) {
			having.addAll(list);
		} else {
			normal.addAll(list);
		}
	}
}
