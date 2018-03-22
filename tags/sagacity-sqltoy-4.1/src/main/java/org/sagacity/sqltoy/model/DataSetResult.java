/**
 * 
 */
package org.sagacity.sqltoy.model;

import java.io.Serializable;
import java.util.ArrayList;
import java.util.List;

import org.sagacity.sqltoy.utils.CollectionUtil;

/**
 * @project sagacity-sqltoy4.0
 * @description 提供一个基础的数据集对象模型
 * @author chenrenfei <a href="mailto:zhongxuchen@gmail.com">联系作者</a>
 * @version id:DataSetResult.java,Revision:v1.0,Date:2016年3月8日
 */
@SuppressWarnings("rawtypes")
public class DataSetResult implements Serializable {
	private static final long serialVersionUID = -2125295102578360914L;

	/**
	 * 标题的名称
	 */
	private String[] labelNames;

	/**
	 * 标题类别
	 */
	private String[] labelTypes;

	/**
	 * 结果记录信息,默认为空集合
	 */
	private List rows;

	/**
	 * 总记录数量
	 */
	private Long totalCount = 0L;

	/**
	 * @return the labelNames
	 */
	public String[] getLabelNames() {
		return labelNames;
	}

	/**
	 * @param labelNames
	 *            the labelNames to set
	 */
	public void setLabelNames(String[] labelNames) {
		this.labelNames = labelNames;
	}

	/**
	 * @return the labelTypes
	 */
	public String[] getLabelTypes() {
		return labelTypes;
	}

	/**
	 * @param labelTypes
	 *            the labelTypes to set
	 */
	public void setLabelTypes(String[] labelTypes) {
		this.labelTypes = labelTypes;
	}

	/**
	 * @return the rows
	 */
	public List getRows() {
		if (this.rows == null)
			return new ArrayList();
		return this.rows;
	}

	/**
	 * @param rows
	 *            the rows to set
	 */
	public void setRows(List rows) {
		this.rows = rows;
	}

	/**
	 * @todo 返回含标题的结果集
	 * @return
	 */
	@SuppressWarnings("unchecked")
	public List getLabelRows() {
		List result = new ArrayList();
		result.add(CollectionUtil.arrayToList(labelNames));
		if (this.getRows() != null) {
			result.addAll(this.getRows());
		}
		return result;
	}

	/**
	 * @return the totalCount
	 */
	public Long getTotalCount() {
		if (totalCount == null)
			return new Long(getRows().size());
		return totalCount;
	}

	/**
	 * @param totalCount
	 *            the totalCount to set
	 */
	public void setTotalCount(Long totalCount) {
		this.totalCount = totalCount;
	}

}
