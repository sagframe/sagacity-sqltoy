package org.sagacity.sqltoy.lambda.engine;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import org.sagacity.sqltoy.utils.StringUtil;

/**
 * @description 条件片段总装容器:源自MyBatis-Plus的MergeSegments(Apache-2.0)经sqltoy-plus移植;将片段路由到where/groupBy/having/orderBy四个分区,
 *              分区各自规整后拼接为最终sql;不再用<code>1 = 1</code>兜底,无法确定合法落点的片段交给调用方/数据库报错
 * @author zhongxuchen
 */
public class MergeSegments implements SqlSegment {

	private final NormalSegmentList normal = new NormalSegmentList();
	private final SegmentList groupBy = new GroupBySegmentList();
	private final SegmentList having = new HavingSegmentList();
	private final SegmentList orderBy = new OrderBySegmentList();
	private String lastSql;

	@Override
	public String getSqlSegment() {
		String sql = null;
		// 以"是否存在有效条件"而非"是否添加过片段"判断:显式or()/and()留下的连接符在渲染时会被剔除,
		// 若按片段数量判断会把空白结果当成where
		if (!normal.hasEffectiveSegment()) {
			if (!groupBy.isEmpty() || !orderBy.isEmpty() || !having.isEmpty()) {
				// 不补 1 = 1:无法确定合法落点的场景交给调用方/数据库报错,避免把错误 SQL 伪装成可执行
				sql = groupBy.getSqlSegment() + having.getSqlSegment() + orderBy.getSqlSegment();
			}
		} else {
			sql = normal.getSqlSegment() + groupBy.getSqlSegment() + having.getSqlSegment() + orderBy.getSqlSegment();
		}
		// 拼接最后的sql;无任何条件时不再以1=1承载last
		if (hasLastSql()) {
			sql = (sql == null ? "" : sql + " ") + lastSql;
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
		return StringUtil.isNotBlank(lastSql);
	}

	/**
	 * @return last()原始sql片段(未设置返回null)
	 */
	public String getLastSql() {
		return hasLastSql() ? lastSql : null;
	}

	/**
	 * @return 是否存在orderBy排序分区
	 */
	public boolean hasOrderBy() {
		return !orderBy.isEmpty();
	}

	/**
	 * @description 仅 where 条件(不含 groupBy/having/orderBy/last);无真实条件时返回null
	 */
	public String getWhereSql() {
		if (!normal.hasEffectiveSegment()) {
			return null;
		}
		String sql = normal.getSqlSegment();
		return StringUtil.isBlank(sql) ? null : sql.trim();
	}

	/**
	 * @return GROUP BY 后的列清单(不含关键字);无分组返回null
	 */
	public String getGroupByColumns() {
		return stripClause(groupBy.getSqlSegment(), "GROUP BY");
	}

	/**
	 * @return HAVING 后的条件(不含关键字);无having返回null
	 */
	public String getHavingCondition() {
		return stripClause(having.getSqlSegment(), "HAVING");
	}

	/**
	 * @return ORDER BY 后的列清单(不含关键字);无排序返回null
	 */
	public String getOrderByColumns() {
		return stripClause(orderBy.getSqlSegment(), "ORDER BY");
	}

	private static String stripClause(String sql, String keyword) {
		if (StringUtil.isBlank(sql)) {
			return null;
		}
		String trimmed = sql.trim();
		if (trimmed.regionMatches(true, 0, keyword, 0, keyword.length())) {
			trimmed = trimmed.substring(keyword.length()).trim();
		}
		return trimmed.isEmpty() ? null : trimmed;
	}

	/**
	 * @description 不含orderBy/last的where+groupBy+having片段;供union根排序拆分为
	 *              "分支SQL"与"union之后的orderBy/last"两部分
	 * @return sql片段,无where/groupBy/having时返回null
	 */
	public String getSqlSegmentWithoutOrderBy() {
		String sql = null;
		if (!normal.hasEffectiveSegment()) {
			if (!groupBy.isEmpty() || !having.isEmpty()) {
				sql = groupBy.getSqlSegment() + having.getSqlSegment();
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
		// 空白last不构成任何片段,统一归一为null,避免hasLastSql误判与尾部空白
		this.lastSql = StringUtil.isBlank(lastSql) ? null : lastSql;
	}

	/**
	 * @description 空分组未产生SQL时,取消其前紧邻的显式OR连接符:prefix已改为仅非空才拼接,
	 *              空分组自身不写连接符,故这里清理的只可能是调用方此前or()挂起的OR。
	 *              启用但无谓词的OR分支被取消,避免落到后续条件上扩大结果范围;禁用分组不会走到这里
	 */
	public void removeTrailingOr() {
		normal.removeTrailingOr();
	}

	public void add(SqlSegment... sqlSegments) {
		if (sqlSegments == null || sqlSegments.length == 0) {
			return;
		}
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
