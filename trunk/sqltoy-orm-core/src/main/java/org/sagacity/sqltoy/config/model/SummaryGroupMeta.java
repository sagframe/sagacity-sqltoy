/**
 * 
 */
package org.sagacity.sqltoy.config.model;

import java.io.Serializable;

/**
 * @project sqltoy-orm
 * @description SummaryModel 的分组子模型
 * @author zhongxuchen
 * @version v1.0,Date:2015年3月3日
 */
public class SummaryGroupMeta implements Serializable, java.lang.Cloneable {
	/**
	 * 
	 */
	private static final long serialVersionUID = -1682139154905127196L;

	/**
	 * 分组列(xml配置解析后的列字符)
	 */
	private String groupColumn;

	/**
	 * 分组列(groupColumn拆解后的分组列)
	 */
	private Integer[] groupCols;

	/**
	 * 汇总标题
	 */
	private String sumTitle;

	/**
	 * 平均标题
	 */
	private String averageTitle;

	/**
	 * 标题存放位置
	 */
	private String labelColumn;

	/**
	 * 具体标题存放的列
	 */
	private int labelIndex;

	/**
	 * 汇总值排列位置
	 */
	private String sumSite = "top";

	/**
	 * 分组下面的每个计算列的设置配置
	 */
	private SummaryColMeta[] summaryCols;

	/**
	 * 全局汇总是否反转放于开始行
	 */
	private boolean globalReverse = false;

	// 存在求和、求平均，且排列方式为上下模式rowSize=2
	private int rowSize = 1;

	/**
	 * 默认sum计算
	 */
	private int summaryType = 1;

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

	public String getGroupColumn() {
		return groupColumn;
	}

	public void setGroupColumn(String groupColumn) {
		this.groupColumn = groupColumn;
	}

	public String getSumTitle() {
		return sumTitle;
	}

	public void setSumTitle(String sumTitle) {
		this.sumTitle = sumTitle;
	}

	public String getAverageTitle() {
		return averageTitle;
	}

	public void setAverageTitle(String averageTitle) {
		this.averageTitle = averageTitle;
	}

	public String getLabelColumn() {
		return labelColumn;
	}

	public void setLabelColumn(String labelColumn) {
		this.labelColumn = labelColumn;
	}

	public Integer[] getGroupCols() {
		return groupCols;
	}

	public void setGroupCols(Integer[] groupCols) {
		this.groupCols = groupCols;
	}

	public int getLabelIndex() {
		return labelIndex;
	}

	public void setLabelIndex(int labelIndex) {
		this.labelIndex = labelIndex;
	}

	public String getSumSite() {
		return sumSite;
	}

	public void setSumSite(String sumSite) {
		this.sumSite = sumSite;
	}

	public SummaryColMeta[] getSummaryCols() {
		return summaryCols;
	}

	public void setSummaryCols(SummaryColMeta[] summaryCols) {
		this.summaryCols = summaryCols;
	}

	public int getRowSize() {
		return rowSize;
	}

	public void setRowSize(int rowSize) {
		this.rowSize = rowSize;
	}

	public boolean isGlobalReverse() {
		return globalReverse;
	}

	public void setGlobalReverse(boolean globalReverse) {
		this.globalReverse = globalReverse;
	}

	public int getSummaryType() {
		return summaryType;
	}

	public void setSummaryType(int summaryType) {
		this.summaryType = summaryType;
	}

	public String getOrderColumn() {
		return orderColumn;
	}

	public void setOrderColumn(String orderColumn) {
		this.orderColumn = orderColumn;
	}

	public String getOrderWay() {
		return orderWay;
	}

	public void setOrderWay(String orderWay) {
		this.orderWay = orderWay;
	}

	public Boolean getOrderWithSum() {
		return orderWithSum;
	}

	public void setOrderWithSum(Boolean orderWithSum) {
		this.orderWithSum = orderWithSum;
	}

	@Override
	public SummaryGroupMeta clone() {
		try {
			return (SummaryGroupMeta) super.clone();
		} catch (CloneNotSupportedException e) {
			e.printStackTrace();
		}
		return null;
	}
}
