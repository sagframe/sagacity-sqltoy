/**
 * 
 */
package org.sagacity.sqltoy.config.model;

import java.io.Serializable;

/**
 * @project sagacity-sqltoy
 * @description 集合列与列环比计算(一般跟pivot行转列结合使用)
 * @author chenrenfei <a href="mailto:zhongxuchen@gmail.com">联系作者</a>
 * @version id:ColsChainRelativeModel.java,Revision:v1.0,Date:2020年3月24日
 */
public class ColsChainRelativeModel implements Serializable {

	/**
	 * 
	 */
	private static final long serialVersionUID = -1439243395603428110L;

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
	private int groupSize;

	/**
	 * 环比值是否将insert为新列
	 */
	private boolean insert = true;

	/**
	 * 分组内的哪几列进行环比
	 */
	private int[] relativeIndexs;

	/**
	 * 从第几列开始
	 */
	private int startColumn;

	/**
	 * 截止列
	 */
	private int endColumn;

	/**
	 * 分组后跳过多少列
	 */
	private int skipSize = 0;

	/**
	 * 保留多少小数位
	 */
	private int radixSize = 3;

	/**
	 * 环比显示格式(#.00%,#.00‰)
	 */
	private String format;

	public boolean isReduceOne() {
		return reduceOne;
	}

	public void setReduceOne(boolean reduceOne) {
		this.reduceOne = reduceOne;
	}

	public int getMultiply() {
		return multiply;
	}

	public void setMultiply(int multiply) {
		this.multiply = multiply;
	}

	public int getGroupSize() {
		return groupSize;
	}

	public void setGroupSize(int groupSize) {
		this.groupSize = groupSize;
	}

	public boolean isInsert() {
		return insert;
	}

	public void setInsert(boolean insert) {
		this.insert = insert;
	}

	public int[] getRelativeIndexs() {
		return relativeIndexs;
	}

	public void setRelativeIndexs(int[] relativeIndexs) {
		this.relativeIndexs = relativeIndexs;
	}

	public int getStartColumn() {
		return startColumn;
	}

	public void setStartColumn(int startColumn) {
		this.startColumn = startColumn;
	}

	public int getSkipSize() {
		return skipSize;
	}

	public void setSkipSize(int skipSize) {
		this.skipSize = skipSize;
	}

	public int getRadixSize() {
		return radixSize;
	}

	public void setRadixSize(int radixSize) {
		this.radixSize = radixSize;
	}

	public String getFormat() {
		return format;
	}

	public void setFormat(String format) {
		this.format = format;
	}

	public int getEndColumn() {
		return endColumn;
	}

	public void setEndColumn(int endColumn) {
		this.endColumn = endColumn;
	}

}
