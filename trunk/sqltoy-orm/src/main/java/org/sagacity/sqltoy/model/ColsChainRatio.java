package org.sagacity.sqltoy.model;

import java.io.Serializable;

/**
 * @project sagacity-sqltoy
 * @description 列与列环比计算参数模型，供QueryExecutor传参使用
 * @author zhongxuchen
 * @version v1.0, Date:2023年6月22日
 * @modify 2023年6月22日,修改说明
 */
public class ColsChainRatio implements Serializable {

	/**
	 * 
	 */
	private static final long serialVersionUID = -7423832189571994928L;

	/**
	 * 是否减一，只计算增量
	 */
	private boolean reduceOne = false;

	/**
	 * 乘数,根据计算的要求,一般有1,100(百分比),1000(千分比)
	 */
	private int multiply = 1;

	/**
	 * 分组长度
	 */
	private int groupSize = 1;

	/**
	 * 环比值是否将insert为新列
	 */
	private Boolean isInsert = true;

	/**
	 * 分组内的哪几列进行环比
	 */
	private Integer[] relativeIndexs;

	/**
	 * 从第几列开始
	 */
	private String startColumn;

	/**
	 * 截止列(支持负数等同于${dataWidth}-x)
	 */
	private String endColumn;

	/**
	 * 分组后跳过多少列
	 */
	private int skipSize = 0;

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

	public ColsChainRatio reduceOne(boolean reduceOne) {
		this.reduceOne = reduceOne;
		return this;
	}

	public int getMultiply() {
		return multiply;
	}

	public ColsChainRatio multiply(int multiply) {
		if (multiply == 1 || multiply == 100 || multiply == 1000) {
			this.multiply = multiply;
		}
		return this;
	}

	public int getGroupSize() {
		return groupSize;
	}

	public ColsChainRatio groupSize(int groupSize) {
		this.groupSize = groupSize;
		return this;
	}

	public boolean getIsInsert() {
		return isInsert;
	}

	public ColsChainRatio isInsert(Boolean isInsert) {
		this.isInsert = isInsert;
		return this;
	}

	public Integer[] getRelativeIndexs() {
		return relativeIndexs;
	}

	public ColsChainRatio relativeIndexs(Integer... relativeIndexs) {
		this.relativeIndexs = relativeIndexs;
		return this;
	}

	public String getStartColumn() {
		return startColumn;
	}

	public ColsChainRatio startColumn(String startColumn) {
		this.startColumn = startColumn;
		return this;
	}

	public String getEndColumn() {
		return endColumn;
	}

	public ColsChainRatio endColumn(String endColumn) {
		this.endColumn = endColumn;
		return this;
	}

	public int getSkipSize() {
		return skipSize;
	}

	public ColsChainRatio skipSize(int skipSize) {
		this.skipSize = skipSize;
		return this;
	}

	public int getRadixSize() {
		return radixSize;
	}

	public ColsChainRatio radixSize(int radixSize) {
		this.radixSize = radixSize;
		return this;
	}

	public String getDefaultValue() {
		return defaultValue;
	}

	public ColsChainRatio defaultValue(String defaultValue) {
		this.defaultValue = defaultValue;
		return this;
	}

	public String getFormat() {
		return format;
	}

	public ColsChainRatio format(String format) {
		this.format = format;
		return this;
	}
}
