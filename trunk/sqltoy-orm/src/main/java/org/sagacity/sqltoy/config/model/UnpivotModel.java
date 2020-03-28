/**
 * 
 */
package org.sagacity.sqltoy.config.model;

import java.io.Serializable;

/**
 * @project sagacity-sqltoy
 * @description 列传行配置模型
 * @author chenrenfei <a href="mailto:zhongxuchen@gmail.com">联系作者</a>
 * @version id:UnpivotModel.java,Revision:v1.0,Date:2015年12月20日
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

	public void setColumnsToRows(String[] columnsToRows) {
		this.columnsToRows = columnsToRows;
	}

	public String[] getNewColumnsLabels() {
		return newColumnsLabels;
	}

	public void setNewColumnsLabels(String[] newColumnsLabels) {
		this.newColumnsLabels = newColumnsLabels;
	}

	

}
