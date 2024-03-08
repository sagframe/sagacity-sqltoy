package org.sagacity.sqltoy.config.model;

import java.io.Serializable;
import java.math.BigDecimal;
import java.math.RoundingMode;

/**
 * @project sagacity-sqltoy
 * @description 分组汇总计算列配置模型(2022-2-20 重构)
 * @author zhongxuchen
 * @version v1.0, Date:2022-2-20
 */
public class SummaryColMeta implements Serializable {

	/**
	 * 
	 */
	private static final long serialVersionUID = -1908959992729333525L;

	/**
	 * 计算列
	 */
	private int colIndex;

	/**
	 * 计算类型:1、求和;2、求平均;3、求和+求平均
	 */
	private int summaryType = 0;

	/**
	 * 保留小数位
	 */
	private int radixSize = 2;

	/**
	 * 求平均进位方式
	 */
	private RoundingMode roundingMode = RoundingMode.HALF_UP;

	/**
	 * 汇总值
	 */
	private BigDecimal sumValue = BigDecimal.ZERO;

	/**
	 * 求平均值是否跳过null值
	 */
	private boolean aveSkipNull = false;

	/**
	 * 数据数量
	 */
	private int rowCount = 0;

	/**
	 * null值数量
	 */
	private int nullCount = 0;

	public int getColIndex() {
		return colIndex;
	}

	public void setColIndex(int colIndex) {
		this.colIndex = colIndex;
	}

	public int getSummaryType() {
		return summaryType;
	}

	public void setSummaryType(int summaryType) {
		this.summaryType = summaryType;
	}

	public int getRadixSize() {
		return radixSize;
	}

	public void setRadixSize(int radixSize) {
		this.radixSize = radixSize;
	}

	public RoundingMode getRoundingMode() {
		return roundingMode;
	}

	public void setRoundingMode(RoundingMode roundingMode) {
		this.roundingMode = roundingMode;
	}

	public BigDecimal getSumValue() {
		return sumValue;
	}

	public void setSumValue(BigDecimal sumValue) {
		this.sumValue = sumValue;
	}

	public boolean isAveSkipNull() {
		return aveSkipNull;
	}

	public void setAveSkipNull(boolean aveSkipNull) {
		this.aveSkipNull = aveSkipNull;
	}

	public int getRowCount() {
		return rowCount;
	}

	public void setRowCount(int rowCount) {
		this.rowCount = rowCount;
	}

	public int getNullCount() {
		return nullCount;
	}

	public void setNullCount(int nullCount) {
		this.nullCount = nullCount;
	}

}
