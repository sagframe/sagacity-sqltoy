package org.sagacity.sqltoy.model;

import java.io.Serializable;

/**
 * 
 * @author zhong
 *
 */
public class TreeSort implements Serializable {

	/**
	 * 
	 */
	private static final long serialVersionUID = -6451807102929254951L;

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
	private String[] sumColumns;

	/**
	 * filter过滤对比列(如:字段列 status==1 的行数据参与汇总)
	 */
	private String filterColumn;

	/**
	 * 对比类型(==,!=,>,<,>=,<=,in,between)
	 */
	private String compareType;

	/**
	 * 对比数据(如:1)
	 */
	private String[] compareValues;

	public String getIdColumn() {
		return idColumn;
	}

	public TreeSort idColumn(String idColumn) {
		this.idColumn = idColumn;
		return this;
	}

	public String getPidColumn() {
		return pidColumn;
	}

	public TreeSort pidColumn(String pidColumn) {
		this.pidColumn = pidColumn;
		return this;
	}

	public String[] getSumColumns() {
		return sumColumns;
	}

	public TreeSort sumColumns(String... sumColumns) {
		this.sumColumns = sumColumns;
		return this;
	}

	public String getFilterColumn() {
		return filterColumn;
	}

	public TreeSort filterColumn(String filterColumn) {
		this.filterColumn = filterColumn;
		return this;
	}

	public String getCompareType() {
		return compareType;
	}

	public TreeSort compareType(String compareType) {
		this.compareType = compareType;
		return this;
	}

	public String[] getCompareValues() {
		return compareValues;
	}

	public TreeSort compareValues(String... compareValues) {
		this.compareValues = compareValues;
		return this;
	}

}
