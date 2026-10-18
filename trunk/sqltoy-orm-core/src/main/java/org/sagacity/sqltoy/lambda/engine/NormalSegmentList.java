package org.sagacity.sqltoy.lambda.engine;

import java.util.ArrayList;
import java.util.List;
import java.util.function.Predicate;

/**
 * @description where条件片段列表:相邻条件自动补AND连接符,合并重复连接符,剔除首尾悬挂连接符;源自MyBatis-Plus的NormalSegmentList(Apache-2.0)经sqltoy-plus移植,本次重构改写
 * @author zhongxuchen
 */
public class NormalSegmentList extends SegmentList {

	@Override
	public void addAll(List<SqlSegment> sqlSegments) {
		if (sqlSegments == null || sqlSegments.isEmpty()) {
			return;
		}
		// 自动追加and
		autoAddAnd(sqlSegments);
		for (SqlSegment sqlSegment : sqlSegments) {
			add(sqlSegment);
		}
	}

	@Override
	public void merge() {
		// 合并相邻的and/or连接符,保留最后一个
		List<Integer> deleteIndexes = new ArrayList<>();
		for (int i = 0; i < segments.size() - 1; i++) {
			if (MatchSegment.AND_OR.match(segments.get(i)) && MatchSegment.AND_OR.match(segments.get(i + 1))) {
				deleteIndexes.add(i);
			}
		}
		deleteByIndexes(deleteIndexes);
		// 剔除首尾多余的and/or连接符
		filterFirst(MatchSegment.AND_OR::match);
		filterLast(MatchSegment.AND_OR::match);
	}

	private void autoAddAnd(List<SqlSegment> sqlSegments) {
		if (segments.isEmpty()) {
			return;
		}
		SqlSegment firstSegment = sqlSegments.get(0);
		SqlSegment lastSegment = segments.get(segments.size() - 1);
		// 只有and()/or()/not()以及嵌套的not()不加自动连接符
		if (!MatchSegment.NOT.match(firstSegment) && !MatchSegment.AND_OR.match(firstSegment)) {
			boolean lastIsConnector = MatchSegment.AND_OR.match(lastSegment) || MatchSegment.NOT.match(lastSegment);
			if (!lastIsConnector) {
				add(SqlKeyword.AND);
			}
		}
	}

	/**
	 * @description 剔除首个满足条件的片段
	 */
	private void filterFirst(Predicate<SqlSegment> predicate) {
		if (!segments.isEmpty() && predicate.test(segments.get(0))) {
			segments.remove(0);
		}
	}

	/**
	 * @description 剔除末尾满足条件的片段
	 */
	private void filterLast(Predicate<SqlSegment> predicate) {
		if (!segments.isEmpty() && predicate.test(segments.get(segments.size() - 1))) {
			segments.remove(segments.size() - 1);
		}
	}
}
