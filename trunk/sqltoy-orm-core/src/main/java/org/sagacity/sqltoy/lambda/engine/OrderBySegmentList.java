package org.sagacity.sqltoy.lambda.engine;

import java.util.ArrayList;
import java.util.List;
import java.util.stream.Collectors;

/**
 * @description order
 *              by片段列表:多次orderBy合并为一个子句,列以逗号连接;源自MyBatis-Plus的OrderBySegmentList(Apache-2.0)经sqltoy-plus移植
 * @author zhongxuchen
 */
public class OrderBySegmentList extends SegmentList {

	/**
	 * 合并为破坏性改写,仅可执行一次(getSqlSegment可能被多次调用)
	 */
	private boolean merged;

	@Override
	public void merge() {
		if (merged) {
			return;
		}
		merged = true;
		int count = 0;
		List<SqlSegment[]> mergeSegments = new ArrayList<>();
		List<Integer> deleteIndexes = new ArrayList<>();
		// 合并前 -> ORDER BY col1 ASC ORDER BY col2 ASC ORDER BY col3 ASC
		for (int i = 0; i < segments.size(); i++) {
			if (MatchSegment.ORDER_BY.match(segments.get(i))) {
				SqlSegment[] columnAndSort = new SqlSegment[] { segments.get(i + 1), segments.get(i + 2) };
				// 第一个order by关键字不删除
				if (count != 0) {
					deleteIndexes.add(i);
				}
				deleteIndexes.add(i + 1);
				deleteIndexes.add(i + 2);
				mergeSegments.add(columnAndSort);
				count++;
			}
		}
		if (count > 1) {
			// 删除原有片段,合并为一个order by子句
			// 合并后 -> ORDER BY col1 ASC,col2 ASC,col3 ASC
			deleteByIndexes(deleteIndexes);
			add(() -> mergeSegments.stream().map(s -> s[0].getSqlSegment() + " " + s[1].getSqlSegment())
					.collect(Collectors.joining(",")));
		}
	}
}
