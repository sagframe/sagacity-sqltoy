package org.sagacity.sqltoy.lambda.engine;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.stream.Collectors;
import java.util.stream.Stream;

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
			if (!groupBy.isEmpty() || !orderBy.isEmpty()) {
				sql = "1 = 1 " + groupBy.getSqlSegment() + having.getSqlSegment() + orderBy.getSqlSegment();
			}
		} else {
			sql = normal.getSqlSegment() + groupBy.getSqlSegment() + having.getSqlSegment() + orderBy.getSqlSegment();
		}
		// 拼接最后的sql
		if (sql != null && lastSql != null && !lastSql.isEmpty()) {
			sql = sql + " " + lastSql;
		}
		return sql;
	}

	@Override
	public Map<String, Object> getParams() {
		return Stream.of(normal.getParams(), groupBy.getParams(), having.getParams(), orderBy.getParams())
				.filter(Objects::nonNull).flatMap(params -> params.entrySet().stream())
				.collect(Collectors.toMap(Map.Entry::getKey, Map.Entry::getValue, (p1, p2) -> p1));
	}

	/**
	 * @return 是否存在where条件片段
	 */
	public boolean hasCondition() {
		return !normal.isEmpty();
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
