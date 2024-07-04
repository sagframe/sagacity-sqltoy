/**
 * 
 */
package org.sagacity.sqltoy.config.model;

import java.io.Serializable;

/**
 * @project sagacity-sqltoy
 * @description 列传行配置模型
 * @author zhongxuchen
 * @version v1.0,Date:2015年12月20日
 */
public class UnpivotModel implements Serializable {

	/**
	 * 
	 */
	private static final long serialVersionUID = 5423610793788395709L;

	/**
	 * 要变成行的列
	 */
	private String[] columnsToRows;

	/**
	 * 列传行标题的笔名
	 */
	private String[] newColumnsLabels;

	/**
	 * 多组,指有多组列{1,3,5},{2,4,6} 6列作为2组旋转成新的2列
	 */
	private int groupSize = 1;

	public String[] getColumnsToRows() {
		return columnsToRows;
	}

	public UnpivotModel setColumnsToRows(String... columnsToRows) {
		if (columnsToRows != null && columnsToRows.length > 0) {
			if (columnsToRows.length > 1) {
				this.columnsToRows = columnsToRows;
			} else {
				this.columnsToRows = columnsToRows[0].split("\\,");
			}
		}
		return this;
	}

	public String[] getNewColumnsLabels() {
		return newColumnsLabels;
	}

	public UnpivotModel setNewColumnsLabels(String... newColumnsLabels) {
		if (newColumnsLabels != null && newColumnsLabels.length > 0) {
			if (newColumnsLabels.length > 1) {
				this.newColumnsLabels = newColumnsLabels;
			} else {
				this.newColumnsLabels = newColumnsLabels[0].split("\\,");
			}
		}
		return this;
	}

	public int getGroupSize() {
		return groupSize;
	}

	public void setGroupSize(int groupSize) {
		this.groupSize = groupSize;
	}

}
