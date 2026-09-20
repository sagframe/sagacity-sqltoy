package org.sagacity.sqltoy.config.model;

import java.io.Serializable;

/**
 * @project sagacity-sqltoy
 * @description 表分区元数据模型(对应@Partition注解)
 * @author zhongxuchen
 * @version v1.0,Date:2026-09-18
 * @modify Date:2026-09-18,修改说明
 */
public class PartitionMeta implements Serializable {

	private static final long serialVersionUID = -4829153276154326731L;

	/**
	 * 分区策略:RANGE|LIST|HASH|RANGE_COLUMNS|LIST_COLUMNS
	 */
	private String strategy;

	/**
	 * 分区键列
	 */
	private String[] columns;

	/**
	 * 分区表达式原文
	 */
	private String expression;

	/**
	 * 分区名称
	 */
	private String[] partitionNames;

	/**
	 * 分区边界值或描述(与partitionNames对应)
	 */
	private String[] partitionValues;

	public PartitionMeta(String strategy, String[] columns, String expression) {
		this.strategy = strategy;
		this.columns = columns;
		this.expression = expression;
	}

	public String getStrategy() {
		return strategy;
	}

	public void setStrategy(String strategy) {
		this.strategy = strategy;
	}

	public String[] getColumns() {
		return columns;
	}

	public void setColumns(String[] columns) {
		this.columns = columns;
	}

	public String getExpression() {
		return expression;
	}

	public void setExpression(String expression) {
		this.expression = expression;
	}

	public String[] getPartitionNames() {
		return partitionNames;
	}

	public void setPartitionNames(String[] partitionNames) {
		this.partitionNames = partitionNames;
	}

	public String[] getPartitionValues() {
		return partitionValues;
	}

	public void setPartitionValues(String[] partitionValues) {
		this.partitionValues = partitionValues;
	}
}
