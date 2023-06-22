package org.sagacity.sqltoy.model;

import java.io.Serializable;
import java.math.RoundingMode;

import org.sagacity.sqltoy.utils.StringUtil;

/**
 * 针对QueryExecutor 开放的汇总计算模型
 * 
 * @author zhong
 * @version v1.0,Date:2023-6-19
 */
public class Summary implements Serializable {

	/**
	 * 
	 */
	private static final long serialVersionUID = 5109122454024427584L;

	/**
	 * 所有都倒序汇总
	 */
	private boolean reverse = false;

	/**
	 * 仅仅全局汇总倒序
	 */
	private boolean globalReverse = false;

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
	 * 汇总和平均两个值在一行显示时标题拼接字符,例如:总计 / 平均
	 */
	private String linkSign;

	/**
	 * 小数位长度(针对求平均值的小数位)
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

	/**
	 * 填写一个表示全局设置
	 */
	private RoundingMode[] roundingModes;

	public boolean isReverse() {
		return reverse;
	}

	public Summary reverse(boolean reverse) {
		this.reverse = reverse;
		return this;
	}

	public boolean isAveSkipNull() {
		return aveSkipNull;
	}

	public Summary aveSkipNull(boolean aveSkipNull) {
		this.aveSkipNull = aveSkipNull;
		return this;
	}

	public String getSumColumns() {
		return sumColumns;
	}

	public Summary sumColumns(String... sumColumns) {
		if (sumColumns != null && sumColumns.length > 0) {
			this.sumColumns = StringUtil.linkAry(",", true, sumColumns);
		}
		return this;
	}

	public String getAveColumns() {
		return aveColumns;
	}

	public Summary aveColumns(String... aveColumns) {
		if (aveColumns != null && aveColumns.length > 0) {
			this.aveColumns = StringUtil.linkAry(",", true, aveColumns);
		}
		return this;
	}

	public String getLinkSign() {
		return linkSign;
	}

	public Summary linkSign(String linkSign) {
		this.linkSign = linkSign;
		return this;
	}

	public Integer[] getRadixSize() {
		return radixSize;
	}

	public Summary radixSize(Integer[] radixSize) {
		this.radixSize = radixSize;
		return this;
	}

	public SummaryGroup[] getSummaryGroups() {
		return summaryGroups;
	}

	public Summary summaryGroups(SummaryGroup... summaryGroups) {
		this.summaryGroups = summaryGroups;
		return this;
	}

	public String getSumSite() {
		return sumSite;
	}

	public Summary sumSite(String sumSite) {
		this.sumSite = sumSite;
		return this;
	}

	public boolean isSkipSingleRow() {
		return skipSingleRow;
	}

	public Summary skipSingleRow(boolean skipSingleRow) {
		this.skipSingleRow = skipSingleRow;
		return this;
	}

	public RoundingMode[] getRoundingModes() {
		return roundingModes;
	}

	public Summary roundingModes(RoundingMode[] roundingModes) {
		this.roundingModes = roundingModes;
		return this;
	}

	public boolean isGlobalReverse() {
		return globalReverse;
	}

	public Summary globalReverse(boolean globalReverse) {
		this.globalReverse = globalReverse;
		return this;
	}

}
