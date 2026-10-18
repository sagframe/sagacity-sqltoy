package org.sagacity.sqltoy.lambda.engine;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.stream.Collectors;

/**
 * @description having片段列表:多次having合并为一个子句,条件之间以AND连接;源自MyBatis-Plus的HavingSegmentList(Apache-2.0)经sqltoy-plus移植
 * @author zhongxuchen
 */
public class HavingSegmentList extends SegmentList {

	@Override
	public void merge() {
		List<Integer> havingIndexes = new ArrayList<>();
		List<SqlSegment> mergeSegments = new ArrayList<>();
		List<Integer> deleteIndexes = new ArrayList<>();
		int count = 0;
		for (int i = 0; i < segments.size(); i++) {
			if (MatchSegment.HAVING.match(segments.get(i))) {
				// 第一个having关键字不删除
				if (count != 0) {
					deleteIndexes.add(i);
				}
				count++;
				deleteIndexes.add(i + 1);
				havingIndexes.add(i);
				mergeSegments.add(segments.get(i + 1));
			}
		}
		if (count > 1) {
			// 删除原有片段,在首个having关键字后插入合并后的子句
			deleteByIndexes(deleteIndexes);
			segments.add(havingIndexes.get(0) + 1, new SqlSegment() {
				@Override
				public String getSqlSegment() {
					return mergeSegments.stream().filter(Objects::nonNull).map(SqlSegment::getSqlSegment)
							.collect(Collectors.joining(" AND "));
				}

				@Override
				public Map<String, Object> getParams() {
					return mergeSegments.stream().map(SqlSegment::getParams).filter(Objects::nonNull)
							.flatMap(params -> params.entrySet().stream())
							.collect(Collectors.toMap(Map.Entry::getKey, Map.Entry::getValue, (p1, p2) -> p1));
				}
			});
		}
	}
}
