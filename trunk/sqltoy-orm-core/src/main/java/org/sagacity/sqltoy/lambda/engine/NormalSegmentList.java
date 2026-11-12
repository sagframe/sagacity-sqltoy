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
		// 剔除首尾多余的and/or连接符;尾部悬挂NOT(空not()留下)剔除,首部NOT合法(NOT (...) AND x)保留;
		// 循环至稳定:剔除NOT后可能暴露新的尾部连接符
		boolean changed = true;
		while (changed) {
			changed = filterFirst(MatchSegment.AND_OR::match);
			changed |= filterLast(segment -> MatchSegment.AND_OR.match(segment) || MatchSegment.NOT.match(segment));
		}
	}

	private void autoAddAnd(List<SqlSegment> sqlSegments) {
		if (segments.isEmpty()) {
			return;
		}
		SqlSegment firstSegment = sqlSegments.get(0);
		SqlSegment lastSegment = segments.get(segments.size() - 1);
		// and()/or()开头不补;末尾是连接符或NOT时不补
		if (MatchSegment.AND_OR.match(firstSegment)) {
			return;
		}
		boolean lastIsConnector = MatchSegment.AND_OR.match(lastSegment) || MatchSegment.NOT.match(lastSegment);
		if (lastIsConnector) {
			return;
		}
		// NOT开头批次:前面已有普通条件时补AND(a NOT (b)非法,应为a AND NOT (b));列表首位置不补
		add(SqlKeyword.AND);
	}

	/**
	 * @description 空分组装配后取消紧随其前的显式OR连接符(prefix延后拼接的补偿):启用但无谓词的OR分支
	 *              若保留,会被后续条件当作OR左连接符而扩大结果范围
	 */
	public void removeTrailingOr() {
		if (!segments.isEmpty() && segments.get(segments.size() - 1) == SqlKeyword.OR) {
			segments.remove(segments.size() - 1);
		}
	}

	/**
	 * @description 剔除首个满足条件的片段
	 */
	private boolean filterFirst(Predicate<SqlSegment> predicate) {
		if (!segments.isEmpty() && predicate.test(segments.get(0))) {
			segments.remove(0);
			return true;
		}
		return false;
	}

	/**
	 * @description 剔除末尾满足条件的片段
	 */
	private boolean filterLast(Predicate<SqlSegment> predicate) {
		if (!segments.isEmpty() && predicate.test(segments.get(segments.size() - 1))) {
			segments.remove(segments.size() - 1);
			return true;
		}
		return false;
	}
}
