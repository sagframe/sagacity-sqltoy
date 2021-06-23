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

}
