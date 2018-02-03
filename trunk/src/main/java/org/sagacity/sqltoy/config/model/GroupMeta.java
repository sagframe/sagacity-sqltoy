/**
 * 
 */
package org.sagacity.sqltoy.config.model;

import java.io.Serializable;

/**
 * @project sqltoy-orm
 * @description SummaryModel 的分组子模型
 * @author chenrenfei <a href="mailto:zhongxuchen@gmail.com">联系作者</a>
 * @version id:GroupMeta.java,Revision:v1.0,Date:2015年3月3日
 */
public class GroupMeta implements Serializable {
	/**
	 * 
	 */
	private static final long serialVersionUID = -1682139154905127196L;

	/**
	 * 分组列
	 */
	private String groupColumn;

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

	public String getGroupColumn() {
		return groupColumn;
	}

	public void setGroupColumn(String groupColumn) {
		this.groupColumn = groupColumn;
	}

	/**
	 * @return the sumTitle
	 */
	public String getSumTitle() {
		return sumTitle;
	}

	/**
	 * @param sumTitle
	 *            the sumTitle to set
	 */
	public void setSumTitle(String sumTitle) {
		this.sumTitle = sumTitle;
	}

	/**
	 * @return the averageTitle
	 */
	public String getAverageTitle() {
		return averageTitle;
	}

	/**
	 * @param averageTitle
	 *            the averageTitle to set
	 */
	public void setAverageTitle(String averageTitle) {
		this.averageTitle = averageTitle;
	}

	public String getLabelColumn() {
		return labelColumn;
	}

	public void setLabelColumn(String labelColumn) {
		this.labelColumn = labelColumn;
	}
}
