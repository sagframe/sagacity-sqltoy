package org.sagacity.sqltoy.lambda.engine;

import java.util.ArrayList;
import java.util.List;

/**
 * @description order
 *              by片段列表:多次orderBy合并为一个子句,列以逗号连接;源自MyBatis-Plus的OrderBySegmentList(Apache-2.0)经sqltoy-plus移植。
 *              merge必须可重复执行(getSqlSegment可能被多次调用,且渲染后仍可能追加排序),故不设一次性标志,
 *              而是识别"ORDER BY+列+方向"与"ORDER BY+已合并文本"两种形态后重建
 * @author zhongxuchen
 */
public class OrderBySegmentList extends SegmentList {

	@Override
	public void merge() {
		List<String> clauses = new ArrayList<>();
		for (int i = 0; i < segments.size(); i++) {
			if (!MatchSegment.ORDER_BY.match(segments.get(i))) {
				continue;
			}
			// 标准形态:ORDER BY 列 方向(ASC/DESC)
			if (i + 2 < segments.size() && isDirection(segments.get(i + 2))) {
				clauses.add(sql(segments.get(i + 1)) + " " + sql(segments.get(i + 2)));
				i += 2;
			} else if (i + 1 < segments.size()) {
				// 已合并形态:ORDER BY 后只有一个片段,内部已是"col1 ASC,col2 DESC"
				clauses.add(sql(segments.get(i + 1)));
				i += 1;
			}
		}
		if (clauses.size() > 1) {
			// 重建为 ORDER BY + 合并片段,保证下一次merge仍可识别并继续合并
			List<SqlSegment> mergedSegments = new ArrayList<>(2);
			mergedSegments.add(SqlKeyword.ORDER_BY);
			mergedSegments.add(() -> String.join(",", clauses));
			segments.clear();
			segments.addAll(mergedSegments);
		}
	}

	private static boolean isDirection(SqlSegment segment) {
		return segment == SqlKeyword.ASC || segment == SqlKeyword.DESC;
	}

	private static String sql(SqlSegment segment) {
		return segment == null ? "" : segment.getSqlSegment();
	}
}
