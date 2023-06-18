/**
 * 
 */
package org.sagacity.sqltoy.config.model;

import java.io.Serializable;

import org.sagacity.sqltoy.utils.StringUtil;

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
	private String compareValues;

	public String getIdColumn() {
		return idColumn;
	}

	public TreeSortModel setIdColumn(String idColumn) {
		this.idColumn = idColumn;
		return this;
	}

	public String getPidColumn() {
		return pidColumn;
	}

	public TreeSortModel setPidColumn(String pidColumn) {
		this.pidColumn = pidColumn;
		return this;
	}

	public String getSumColumns() {
		return sumColumns;
	}

	public TreeSortModel setSumColumns(String... sumColumns) {
		if (sumColumns != null && sumColumns.length > 0) {
			this.sumColumns = StringUtil.linkAry(",", true, sumColumns);
		}
		return this;
	}

	public String getFilterColumn() {
		return filterColumn;
	}

	public TreeSortModel setFilterColumn(String filterColumn) {
		this.filterColumn = filterColumn;
		return this;
	}

	public String getCompareType() {
		return compareType;
	}

	public TreeSortModel setCompareType(String compareType) {
		// 统一比较字符
		if ("eq".equals(compareType)) {
			this.compareType = "==";
		} else if ("neq".equals(compareType)) {
			this.compareType = "!=";
		} else if ("gt".equals(compareType)) {
			this.compareType = ">";
		} else if ("gte".equals(compareType)) {
			this.compareType = ">=";
		} else if ("lt".equals(compareType)) {
			this.compareType = "<";
		} else if ("lte".equals(compareType)) {
			this.compareType = "<=";
		} else {
			this.compareType = compareType;
		}
		return this;
	}

	public String getCompareValues() {
		return compareValues;
	}

	public TreeSortModel setCompareValues(String... compareValues) {
		if (compareValues != null && compareValues.length > 0) {
			this.compareValues = StringUtil.linkAry(",", true, compareValues);
		}
		return this;
	}

}
