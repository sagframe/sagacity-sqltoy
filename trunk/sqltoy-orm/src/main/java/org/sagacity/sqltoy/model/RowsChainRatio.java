package org.sagacity.sqltoy.model;

import java.io.Serializable;

/**
 * 
 * @author zhong
 *
 */
public class RowsChainRatio implements Serializable {

	/**
	 * 
	 */
	private static final long serialVersionUID = 7946896698723423990L;

	/**
	 * 是否减一，只计算增量
	 */
	private boolean reduceOne = false;

	/**
	 * 乘数,根据计算的要求,一般有1,100(百分比),1000(千分比)
	 */
	private int multiply = 1;

	/**
	 * 分组列
	 */
	private String groupColumn;

	/**
	 * 环比值是否将insert为新列
	 */
	private Boolean isInsert = true;

	/**
	 * 对哪几列进行比较计算
	 */
	private String[] relativeColumns;

	/**
	 * 分组内的哪几列进行环比
	 */
	private Integer[] relativeIndexs;

	/**
	 * 从第几列开始
	 */
	private Integer startRow = 0;

	/**
	 * 截止行,负数表示倒数第几行
	 */
	private Integer endRow;

	/**
	 * 是否逆序
	 */
	private boolean reverse = true;

	/**
	 * 保留多少小数位
	 */
	private int radixSize = 3;

	/**
	 * 默认值
	 */
	private String defaultValue;

	/**
	 * 环比显示格式(#.00%,#.00‰)
	 */
	private String format;

	public boolean isReduceOne() {
		return reduceOne;
	}

	public RowsChainRatio reduceOne(boolean reduceOne) {
		this.reduceOne = reduceOne;
		return this;
	}

	public int getMultiply() {
		return multiply;
	}

	public RowsChainRatio multiply(int multiply) {
		if (multiply == 1 || multiply == 100 || multiply == 1000) {
			this.multiply = multiply;
		}
		return this;
	}

	public String getGroupColumn() {
		return groupColumn;
	}

	public RowsChainRatio groupColumn(String groupColumn) {
		this.groupColumn = groupColumn;
		return this;
	}

	public Boolean getIsInsert() {
		return isInsert;
	}

	public RowsChainRatio isInsert(Boolean isInsert) {
		this.isInsert = isInsert;
		return this;
	}

	public String[] getRelativeColumns() {
		return relativeColumns;
	}

	public RowsChainRatio relativeColumns(String[] relativeColumns) {
		this.relativeColumns = relativeColumns;
		return this;
	}

	public Integer[] getRelativeIndexs() {
		return relativeIndexs;
	}

	public RowsChainRatio relativeIndexs(Integer[] relativeIndexs) {
		this.relativeIndexs = relativeIndexs;
		return this;
	}

	public Integer getStartRow() {
		return startRow;
	}

	public RowsChainRatio startRow(Integer startRow) {
		this.startRow = startRow;
		return this;
	}

	public Integer getEndRow() {
		return endRow;
	}

	public RowsChainRatio endRow(Integer endRow) {
		this.endRow = endRow;
		return this;
	}

	public boolean isReverse() {
		return reverse;
	}

	public RowsChainRatio reverse(boolean reverse) {
		this.reverse = reverse;
		return this;
	}

	public int getRadixSize() {
		return radixSize;
	}

	/**
	 * 保留小数位
	 * 
	 * @param radixSize
	 * @return
	 */
	public RowsChainRatio radixSize(int radixSize) {
		this.radixSize = radixSize;
		return this;
	}

	public String getDefaultValue() {
		return defaultValue;
	}

	public RowsChainRatio defaultValue(String defaultValue) {
		this.defaultValue = defaultValue;
		return this;
	}

	public String getFormat() {
		return format;
	}

	/**
	 * #.00% 或 #.00‰
	 * 
	 * @param format
	 * @return
	 */
	public RowsChainRatio format(String format) {
		this.format = format;
		return this;
	}
}
