/**
 * 
 */
package org.sagacity.sqltoy.config.model;

import java.io.Serializable;

/**
 * @project sagacity-sqltoy
 * @description 索引模型定义
 * @author zhongxuchen
 * @version v1.0, Date:2023年7月12日
 * @modify 2023年7月12日,修改说明
 */
public class IndexModel implements Serializable {

	/**
	 * 
	 */
	private static final long serialVersionUID = -5407446766236099957L;

	/**
	 * 索引名称
	 */
	private String name;

	/**
	 * 是否唯一索引
	 */
	private boolean isUnique = false;

	/**
	 * 索引列
	 */
	private String[] columns;

	public IndexModel(String name, boolean isUnique, String[] columns) {
		this.name = name;
		this.isUnique = isUnique;
		this.columns = columns;
	}

	public String getName() {
		return name;
	}

	public void setName(String name) {
		this.name = name;
	}

	public boolean isUnique() {
		return isUnique;
	}

	public void setUnique(boolean isUnique) {
		this.isUnique = isUnique;
	}

	public String[] getColumns() {
		return columns;
	}

	public void setColumns(String[] columns) {
		this.columns = columns;
	}

}
