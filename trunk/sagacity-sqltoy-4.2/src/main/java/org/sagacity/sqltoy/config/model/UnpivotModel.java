/**
 * 
 */
package org.sagacity.sqltoy.config.model;

import java.io.Serializable;

/**
 * @project sagacity-sqltoy3.1
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
	private String[] columns;

	/**
	 * 列传行标题的笔名
	 */
	private String[] colsAlias;

	/**
	 * 多列转换成1列时,新列的名称
	 */
	private String asColumn;

	/**
	 * 多列的标题作为的列名称
	 */
	private String labelsColumn;

	/**
	 * @return the columns
	 */
	public String[] getColumns() {
		return columns;
	}

	/**
	 * @param columns
	 *            the columns to set
	 */
	public void setColumns(String[] columns) {
		this.columns = columns;
	}

	/**
	 * @return the colsAlias
	 */
	public String[] getColsAlias() {
		return colsAlias;
	}

	/**
	 * @param colsAlias
	 *            the colsAlias to set
	 */
	public void setColsAlias(String[] colsAlias) {
		this.colsAlias = colsAlias;
	}

	/**
	 * @return the asColumn
	 */
	public String getAsColumn() {
		return asColumn;
	}

	/**
	 * @param asColumn
	 *            the asColumn to set
	 */
	public void setAsColumn(String asColumn) {
		this.asColumn = asColumn;
	}

	/**
	 * @return the labelsColumn
	 */
	public String getLabelsColumn() {
		return labelsColumn;
	}

	/**
	 * @param labelsColumn
	 *            the labelsColumn to set
	 */
	public void setLabelsColumn(String labelsColumn) {
		this.labelsColumn = labelsColumn;
	}

}
