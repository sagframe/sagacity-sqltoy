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

	/**
	 * update 2026-10-13 分区上界(与partitionNames对应;DB2表分区为STARTING..ENDING双边界区间,
	 * partitionValues为下界);单边界库(mysql系/pg系/oracle等)为null
	 */
	private String[] partitionEndValues;

	/**
	 * update 2026-10-13 上界是否包含(与partitionEndValues对应;DB2的ENDING默认INCLUSIVE,
	 * EXCLUSIVE为false);partitionEndValues为null时无意义
	 */
	private boolean[] partitionEndInclusives;

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

	public String[] getPartitionEndValues() {
		return partitionEndValues;
	}

	public void setPartitionEndValues(String[] partitionEndValues) {
		this.partitionEndValues = partitionEndValues;
	}

	public boolean[] getPartitionEndInclusives() {
		return partitionEndInclusives;
	}

	public void setPartitionEndInclusives(boolean[] partitionEndInclusives) {
		this.partitionEndInclusives = partitionEndInclusives;
	}
}
