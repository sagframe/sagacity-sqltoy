/**
 * 
 */
package org.sagacity.sqltoy.config.model;

import java.io.Serializable;

/**
 * @project sqltoy-orm
 * @description 定义sqltoy查询结果的处理模式,目前仅提供合计和求平均
 * @author renfei.chen <a href="mailto:zhongxuchen@hotmail.com">联系作者</a>
 * @version Revision:v1.0,Date:2013-5-17
 * @Modification Date:2013-5-17 {填写修改说明}
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
	 * 全局汇总是否逆向
	 */
	private boolean globalReverse=false;

	/**
	 * 定义所有需要计算的列
	 */
	private String summaryCols;

	/**
	 * 汇总和平均的联合输出模板，将汇总和平均的数值联合显示
	 */
	private String combineTemplate = "sum/aver";

	/**
	 * 汇总和平均两个值拼接输出时拼接的字符
	 */
	private String linkSign;

	/**
	 * 小数位长度
	 */
	private int radixSize = 2;

	// 多重分组汇总定义
	// {group-columns,sumTitle,averageTitle,sumSite}
	private GroupMeta[] groupMeta;

	/**
	 * 全局汇总合计标题存放的列
	 */
	private String globalLabelColumn;
	
	/**
	 * 全局统计的分组列
	 */
	private String groupColumn;

	/**
	 * 全局总计的标题
	 */
	private String globalSumTitle;

	/**
	 * 全局平均的标题
	 */
	private String globalAverageTitle;

	/**
	 * 平均值所在位置:top/buttom/left/right 四种模式
	 */
	private String sumSite = "bottom";

	/**
	 * @return the reverse
	 */
	public boolean isReverse() {
		return reverse;
	}

	/**
	 * @param reverse
	 *            the reverse to set
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
	 * @param summaryCols
	 *            the summaryCols to set
	 */
	public void setSummaryCols(String summaryCols) {
		this.summaryCols = summaryCols;
	}

	/**
	 * @return the combineTemplate
	 */
	public String getCombineTemplate() {
		return combineTemplate;
	}

	/**
	 * @param combineTemplate
	 *            the combineTemplate to set
	 */
	public void setCombineTemplate(String combineTemplate) {
		this.combineTemplate = combineTemplate;
	}

	public int getRadixSize() {
		return radixSize;
	}

	public void setRadixSize(int radixSize) {
		this.radixSize = radixSize;
	}

	/**
	 * @return the groupMeta
	 */
	public GroupMeta[] getGroupMeta() {
		return groupMeta;
	}

	/**
	 * @param groupMeta
	 *            the groupMeta to set
	 */
	public void setGroupMeta(GroupMeta[] groupMeta) {
		this.groupMeta = groupMeta;
	}

	public String getGlobalLabelColumn() {
		return globalLabelColumn;
	}

	public void setGlobalLabelColumn(String globalLabelColumn) {
		this.globalLabelColumn = globalLabelColumn;
	}

	/**
	 * @return the globalSumTitle
	 */
	public String getGlobalSumTitle() {
		return globalSumTitle;
	}

	/**
	 * @param globalSumTitle
	 *            the globalSumTitle to set
	 */
	public void setGlobalSumTitle(String globalSumTitle) {
		this.globalSumTitle = globalSumTitle;
	}

	/**
	 * @return the globalAverageTitle
	 */
	public String getGlobalAverageTitle() {
		return globalAverageTitle;
	}

	/**
	 * @param globalAverageTitle
	 *            the globalAverageTitle to set
	 */
	public void setGlobalAverageTitle(String globalAverageTitle) {
		this.globalAverageTitle = globalAverageTitle;
	}

	/**
	 * @return the sumSite
	 */
	public String getSumSite() {
		return sumSite;
	}

	/**
	 * @param sumSite
	 *            the sumSite to set
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
	 * @param linkSign
	 *            the linkSign to set
	 */
	public void setLinkSign(String linkSign) {
		this.linkSign = linkSign;
	}

	/**
	 * @return the globalReverse
	 */
	public boolean isGlobalReverse() {
		return globalReverse;
	}

	/**
	 * @param globalReverse the globalReverse to set
	 */
	public void setGlobalReverse(boolean globalReverse) {
		this.globalReverse = globalReverse;
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
	
	
}
