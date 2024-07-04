package org.sagacity.sqltoy.model;

import java.io.Serializable;

/**
 * @project sagacity-sqltoy
 * @description 提供基于QueryExecutor进行汇总计算的api对象模型
 * @author zhong
 * @version v1.0, Date:2023年6月21日
 * @modify 2023年6月21日,修改说明
 */
public class SummaryGroup implements Serializable {

	/**
	 * 
	 */
	private static final long serialVersionUID = 7367450544020667310L;

	/**
	 * 分组列，可以多列
	 */
	private String[] groupColumns;

	/**
	 * 标题所在列
	 */
	private String labelColumn;

	/**
	 * 汇总标题名称(总计、小计)
	 */
	private String sumTitle;

	/**
	 * 求平均标题
	 */
	private String aveTitle;

	/**
	 * 排序列
	 */
	private String orderColumn;

	/**
	 * 排序方式
	 */
	private String orderWay = "desc";

	/**
	 * 排序是否以求和的值为依据
	 */
	private Boolean orderWithSum;

	/**
	 * @return the orderColumn
	 */
	public String getOrderColumn() {
		return orderColumn;
	}

	public SummaryGroup orderColumn(String orderColumn) {
		this.orderColumn = orderColumn;
		return this;
	}

	/**
	 * @return the orderWay
	 */
	public String getOrderWay() {
		return orderWay;
	}

	public SummaryGroup orderWay(String orderWay) {
		this.orderWay = orderWay;
		return this;
	}

	/**
	 * @return the orderWithSum
	 */
	public Boolean getOrderWithSum() {
		return orderWithSum;
	}

	public SummaryGroup orderWithSum(Boolean orderWithSum) {
		this.orderWithSum = orderWithSum;
		return this;
	}

	public SummaryGroup(String... groupColumns) {
		if (groupColumns != null && groupColumns.length > 0) {
			this.groupColumns = groupColumns;
		}
	}

	public String[] getGroupColumns() {
		return groupColumns;
	}

	public SummaryGroup groupColumns(String... groupColumns) {
		this.groupColumns = groupColumns;
		return this;
	}

	public String getLabelColumn() {
		return labelColumn;
	}

	public SummaryGroup labelColumn(String labelColumn) {
		this.labelColumn = labelColumn;
		return this;
	}

	public String getSumTitle() {
		return sumTitle;
	}

	public SummaryGroup sumTitle(String sumTitle) {
		this.sumTitle = sumTitle;
		return this;
	}

	public String getAveTitle() {
		return aveTitle;
	}

	public SummaryGroup aveTitle(String aveTitle) {
		this.aveTitle = aveTitle;
		return this;
	}
}
