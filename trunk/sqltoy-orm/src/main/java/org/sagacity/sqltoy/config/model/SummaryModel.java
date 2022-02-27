/**
 * 
 */
package org.sagacity.sqltoy.config.model;

import java.io.Serializable;
import java.math.RoundingMode;

/**
 * @project sqltoy-orm
 * @description 定义sqltoy查询结果的处理模式,目前仅提供合计和求平均
 * @author zhongxuchen
 * @version v1.0,Date:2013-5-17
 * @modify Date:2013-5-17 {填写修改说明}
 */
public class SummaryModel implements Serializable {
	/**
	 * 
	 */
	private static final long serialVersionUID = -2510246189482255234L;

	/**
	 * 是否是逆向汇总
	 */
	private boolean reverse = false;

	/**
	 * 求平均是否忽视掉null，举例:{1,3,5,null,9} 结果(1+3+5+9)/4
	 */
	private boolean averageSkipNull = false;

	/**
	 * 定义所有需要计算的列
	 */
	private String summaryCols;

	/**
	 * 计算平均值的列
	 */
	private String averageCols;

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
	private SummaryGroupMeta[] groupMeta;

	/**
	 * 全局统计的分组列
	 */
	private String groupColumn;

	/**
	 * 平均值所在位置:top/buttom/left/right 四种模式
	 */
	private String sumSite = "top";

	private RoundingMode[] roundingModes;

	/**
	 * @return the reverse
	 */
	public boolean isReverse() {
		return reverse;
	}

	/**
	 * @param reverse the reverse to set
	 */
	public void setReverse(boolean reverse) {
		this.reverse = reverse;
	}

	/**
	 * @return the summaryCols
	 */
	public String getSummaryCols() {
		return summaryCols;
	}

	/**
	 * @param summaryCols the summaryCols to set
	 */
	public void setSummaryCols(String summaryCols) {
		this.summaryCols = summaryCols;
	}

	public Integer[] getRadixSize() {
		return radixSize;
	}

	public void setRadixSize(Integer[] radixSize) {
		this.radixSize = radixSize;
	}

	/**
	 * @return the groupMeta
	 */
	public SummaryGroupMeta[] getGroupMeta() {
		return groupMeta;
	}

	/**
	 * @param groupMeta the groupMeta to set
	 */
	public void setGroupMeta(SummaryGroupMeta[] groupMeta) {
		this.groupMeta = groupMeta;
	}

	/**
	 * @return the sumSite
	 */
	public String getSumSite() {
		return sumSite;
	}

	/**
	 * @param sumSite the sumSite to set
	 */
	public void setSumSite(String sumSite) {
		this.sumSite = sumSite;
	}

	/**
	 * @return the linkSign
	 */
	public String getLinkSign() {
		return linkSign;
	}

	/**
	 * @param linkSign the linkSign to set
	 */
	public void setLinkSign(String linkSign) {
		this.linkSign = linkSign;
	}

	/**
	 * @return the groupColumn
	 */
	public String getGroupColumn() {
		return groupColumn;
	}

	/**
	 * @param groupColumn the groupColumn to set
	 */
	public void setGroupColumn(String groupColumn) {
		this.groupColumn = groupColumn;
	}

	public boolean isAverageSkipNull() {
		return averageSkipNull;
	}

	public void setAverageSkipNull(boolean averageSkipNull) {
		this.averageSkipNull = averageSkipNull;
	}

	public String getAverageCols() {
		return averageCols;
	}

	public void setAverageCols(String averageCols) {
		this.averageCols = averageCols;
	}

	public RoundingMode[] getRoundingModes() {
		return roundingModes;
	}

	public void setRoundingModes(RoundingMode[] roundingModes) {
		this.roundingModes = roundingModes;
	}
}
