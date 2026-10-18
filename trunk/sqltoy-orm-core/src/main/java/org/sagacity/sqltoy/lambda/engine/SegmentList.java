package org.sagacity.sqltoy.lambda.engine;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.stream.Collectors;

/**
 * @description sql片段列表基础类,提供片段存储、合并和参数收集能力;形状源自MyBatis-Plus的AbstractSegmentList(Apache-2.0)经sqltoy-plus移植,本次重构改写
 * @author zhongxuchen
 */
public abstract class SegmentList implements SqlSegment {

	protected final List<SqlSegment> segments = new ArrayList<>();

	/**
	 * @description 对已添加的片段执行合并规整
	 */
	public abstract void merge();

	public void add(SqlSegment sqlSegment) {
		if (sqlSegment != null) {
			segments.add(sqlSegment);
		}
	}

	public void addAll(List<SqlSegment> sqlSegments) {
		if (sqlSegments == null) {
			return;
		}
		for (SqlSegment sqlSegment : sqlSegments) {
			add(sqlSegment);
		}
	}

	@Override
	public String getSqlSegment() {
		if (segments.isEmpty()) {
			return "";
		}
		// 合并sql片段
		merge();
		return segments.stream().filter(Objects::nonNull).map(SqlSegment::getSqlSegment)
				.collect(Collectors.joining(" ")) + " ";
	}

	@Override
	public Map<String, Object> getParams() {
		Map<String, Object> allParams = new HashMap<>();
		for (SqlSegment sqlSegment : segments) {
			if (sqlSegment.getParams() != null) {
				allParams.putAll(sqlSegment.getParams());
			}
		}
		return allParams;
	}

	/**
	 * @description 按位置批量剔除片段
	 * @param deleteIndexes 待剔除的位置集合
	 */
	public void deleteByIndexes(List<Integer> deleteIndexes) {
		if (deleteIndexes == null || deleteIndexes.isEmpty()) {
			return;
		}
		List<SqlSegment> remain = new ArrayList<>();
		for (int i = 0; i < segments.size(); i++) {
			if (!deleteIndexes.contains(i)) {
				remain.add(segments.get(i));
			}
		}
		segments.clear();
		segments.addAll(remain);
	}

	public boolean isEmpty() {
		return segments.isEmpty();
	}
}
