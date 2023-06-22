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
