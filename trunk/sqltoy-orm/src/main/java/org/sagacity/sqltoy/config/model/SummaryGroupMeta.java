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
public class SummaryGroupMeta implements Serializable {
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
	private String sumSite = "left";

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
	 * 同时存在汇总和求平均
	 */
	private boolean bothSumAverage = false;

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

	public boolean isBothSumAverage() {
		return bothSumAverage;
	}

	public void setBothSumAverage(boolean bothSumAverage) {
		this.bothSumAverage = bothSumAverage;
	}

	public boolean isGlobalReverse() {
		return globalReverse;
	}

	public void setGlobalReverse(boolean globalReverse) {
		this.globalReverse = globalReverse;
	}

}
