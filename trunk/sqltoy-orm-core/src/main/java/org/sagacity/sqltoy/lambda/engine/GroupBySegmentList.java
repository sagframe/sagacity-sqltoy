package org.sagacity.sqltoy.lambda.engine;

import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.stream.Collectors;

/**
 * @description group
 *              by片段列表:多次groupBy合并为一个子句,列以逗号连接;源自MyBatis-Plus的GroupBySegmentList(Apache-2.0)经sqltoy-plus移植
 * @author zhongxuchen
 */
public class GroupBySegmentList extends SegmentList {

	@Override
	public void merge() {
		List<SqlSegment> mergeSegments = new ArrayList<>();
		List<Integer> deleteIndexes = new ArrayList<>();
		int count = 0;
		for (int i = 0; i < segments.size(); i++) {
			if (MatchSegment.GROUP_BY.match(segments.get(i))) {
				// 第一个group by关键字不删除
				if (count != 0) {
					deleteIndexes.add(i);
				}
				count++;
				deleteIndexes.add(i + 1);
				mergeSegments.add(segments.get(i + 1));
			}
		}
		if (count > 1) {
			// 删除原有片段,合并为一个group by子句
			deleteByIndexes(deleteIndexes);
			add(() -> mergeSegments.stream().filter(Objects::nonNull).map(SqlSegment::getSqlSegment)
					.collect(Collectors.joining(",")));
		}
	}
}
