package org.sagacity.sqltoy.model;

import java.io.Serializable;

/**
 * 
 * @author zhong
 *
 */
public class SummaryGroup implements Serializable {

	/**
	 * 
	 */
	private static final long serialVersionUID = 7367450544020667310L;

	private String[] groupColumns;

	/**
	 * 汇总列
	 */
	private String[] sumColumns;

	/**
	 * 求评均列
	 */
	private String[] aveColumns;

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

	private int labelIndex;

	private boolean reverse;

	//top\bottom\left\right
	private String sumSite = "top";

	public String[] getGroupColumns() {
		return groupColumns;
	}

	public void setGroupColumns(String[] groupColumns) {
		this.groupColumns = groupColumns;
	}

	public String[] getSumColumns() {
		return sumColumns;
	}

	public void setSumColumns(String[] sumColumns) {
		this.sumColumns = sumColumns;
	}

	public String[] getAveColumns() {
		return aveColumns;
	}

	public void setAveColumns(String[] aveColumns) {
		this.aveColumns = aveColumns;
	}

	public String getLabelColumn() {
		return labelColumn;
	}

	public void setLabelColumn(String labelColumn) {
		this.labelColumn = labelColumn;
	}

	public String getSumTitle() {
		return sumTitle;
	}

	public void setSumTitle(String sumTitle) {
		this.sumTitle = sumTitle;
	}

	public String getAveTitle() {
		return aveTitle;
	}

	public void setAveTitle(String aveTitle) {
		this.aveTitle = aveTitle;
	}

	public int getLabelIndex() {
		return labelIndex;
	}

	public void setLabelIndex(int labelIndex) {
		this.labelIndex = labelIndex;
	}

	public boolean isReverse() {
		return reverse;
	}

	public void setReverse(boolean reverse) {
		this.reverse = reverse;
	}

	public String getSumSite() {
		return sumSite;
	}

	public void setSumSite(String sumSite) {
		this.sumSite = sumSite;
	}
	
	
}
