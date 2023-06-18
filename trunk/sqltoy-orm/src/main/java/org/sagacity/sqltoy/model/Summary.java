package org.sagacity.sqltoy.model;

import java.io.Serializable;
import java.math.RoundingMode;

/**
 * 
 * @author zhong
 *
 */
public class Summary implements Serializable {

	/**
	 * 
	 */
	private static final long serialVersionUID = 5109122454024427584L;

	/**
	 * 是否是逆向汇总
	 */
	private boolean reverse = false;

	/**
	 * 求平均是否忽视掉null，举例:{1,3,5,null,9} 结果(1+3+5+9)/4
	 */
	private boolean aveSkipNull = false;

	/**
	 * 求和列
	 */
	private String sumColumns;

	/**
	 * 计算平均值的列
	 */
	private String aveColumns;

	/**
	 * 汇总和平均两个值拼接输出时拼接的字符
	 */
	private String linkSign;

	/**
	 * 小数位长度
	 */
	private Integer[] radixSize = { 3 };

	// 多重分组汇总定义
	// {group-columns,sumTitle,averageTitle,sumSite}
	private SummaryGroup[] summaryGroups;

	/**
	 * 平均值所在位置:top/buttom/left/right 四种模式
	 */
	private String sumSite = "top";

	/**
	 * 跳过单行数据的分组计算
	 */
	private boolean skipSingleRow = false;

	private RoundingMode[] roundingModes;

	public boolean isReverse() {
		return reverse;
	}

	public void setReverse(boolean reverse) {
		this.reverse = reverse;
	}

	public boolean isAveSkipNull() {
		return aveSkipNull;
	}

	public void setAveSkipNull(boolean aveSkipNull) {
		this.aveSkipNull = aveSkipNull;
	}

	public String getSumColumns() {
		return sumColumns;
	}

	public void setSumColumns(String sumColumns) {
		this.sumColumns = sumColumns;
	}

	public String getAveColumns() {
		return aveColumns;
	}

	public void setAveColumns(String aveColumns) {
		this.aveColumns = aveColumns;
	}

	public String getLinkSign() {
		return linkSign;
	}

	public void setLinkSign(String linkSign) {
		this.linkSign = linkSign;
	}

	public Integer[] getRadixSize() {
		return radixSize;
	}

	public void setRadixSize(Integer[] radixSize) {
		this.radixSize = radixSize;
	}

	public SummaryGroup[] getSummaryGroups() {
		return summaryGroups;
	}

	public void setSummaryGroups(SummaryGroup[] summaryGroups) {
		this.summaryGroups = summaryGroups;
	}

	public String getSumSite() {
		return sumSite;
	}

	public void setSumSite(String sumSite) {
		this.sumSite = sumSite;
	}

	public boolean isSkipSingleRow() {
		return skipSingleRow;
	}

	public void setSkipSingleRow(boolean skipSingleRow) {
		this.skipSingleRow = skipSingleRow;
	}

	public RoundingMode[] getRoundingModes() {
		return roundingModes;
	}

	public void setRoundingModes(RoundingMode[] roundingModes) {
		this.roundingModes = roundingModes;
	}

}
