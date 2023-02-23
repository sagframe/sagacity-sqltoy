/**
 * 
 */
package org.sagacity.sqltoy.config.model;

import java.io.Serializable;

/**
 * @project sagacity-sqltoy
 * @description 对树型表结构进行排序
 * @author zhongxuchen
 * @version v1.0, Date:2022年10月28日
 * @modify 2022年10月28日,修改说明
 */
public class TreeSortModel implements Serializable {

	/**
	 * 
	 */
	private static final long serialVersionUID = 804547027448571388L;

	/**
	 * id节点字段
	 */
	private String idColumn;

	/**
	 * pid节点字段
	 */
	private String pidColumn;

	/**
	 * 汇总列
	 */
	private String sumColumns;

	/**
	 * filter过滤对比列
	 */
	private String filterColumn;

	/**
	 * 对比类型
	 */
	private String compareType;

	/**
	 * 对比数据
	 */
	private String compareValues;

	public String getIdColumn() {
		return idColumn;
	}

	public void setIdColumn(String idColumn) {
		this.idColumn = idColumn;
	}

	public String getPidColumn() {
		return pidColumn;
	}

	public void setPidColumn(String pidColumn) {
		this.pidColumn = pidColumn;
	}

	public String getSumColumns() {
		return sumColumns;
	}

	public void setSumColumns(String sumColumns) {
		this.sumColumns = sumColumns;
	}

	public String getFilterColumn() {
		return filterColumn;
	}

	public void setFilterColumn(String filterColumn) {
		this.filterColumn = filterColumn;
	}

	public String getCompareType() {
		return compareType;
	}

	public void setCompareType(String compareType) {
		this.compareType = compareType;
	}

	public String getCompareValues() {
		return compareValues;
	}

	public void setCompareValues(String compareValues) {
		this.compareValues = compareValues;
	}

}
