/**
 * 
 */
package org.sagacity.sqltoy.config.model;

/**
 * @project sagacity-sqltoy
 * @description 外键关联模型
 * @author zhongxuchen
 * @version v1.0, Date:2023年12月20日
 * @modify 2023年12月20日,修改说明
 */
public class ForeignModel {
	/**
	 * 约束名称
	 */
	private String constraintName;

	/**
	 * 当前表字段
	 */
	private String[] columns;

	/**
	 * 外键表
	 */
	private String foreignTable;

	/**
	 * 外键列
	 */
	private String[] foreignColumns;

	/**
	 * 删除约束
	 */
	private int deleteRestict = 0;

	/**
	 * 修改约束
	 */
	private int updateRestict = 0;

	public String getConstraintName() {
		return constraintName;
	}

	public void setConstraintName(String constraintName) {
		this.constraintName = constraintName;
	}

	public String[] getColumns() {
		return columns;
	}

	public void setColumns(String[] columns) {
		this.columns = columns;
	}

	public String getForeignTable() {
		return foreignTable;
	}

	public void setForeignTable(String foreignTable) {
		this.foreignTable = foreignTable;
	}

	public String[] getForeignColumns() {
		return foreignColumns;
	}

	public void setForeignColumns(String[] foreignColumns) {
		this.foreignColumns = foreignColumns;
	}

	public int getDeleteRestict() {
		return deleteRestict;
	}

	public void setDeleteRestict(int deleteRestict) {
		this.deleteRestict = deleteRestict;
	}

	public int getUpdateRestict() {
		return updateRestict;
	}

	public void setUpdateRestict(int updateRestict) {
		this.updateRestict = updateRestict;
	}
}
