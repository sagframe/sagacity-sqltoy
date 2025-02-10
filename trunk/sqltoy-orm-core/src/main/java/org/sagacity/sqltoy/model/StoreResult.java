package org.sagacity.sqltoy.model;

import java.io.Serializable;
import java.util.List;

import org.sagacity.sqltoy.model.inner.DataSetResult;

/**
 * @project sagacity-sqltoy
 * @description 存储过程返回结果集
 * @author zhongxuchen
 * @version v1.0,Date:2009-12-25
 */
public class StoreResult<T> extends DataSetResult<T> implements Serializable {

	/**
	 * 
	 */
	private static final long serialVersionUID = 1936180601122427649L;

	/**
	 * 输出参数的值
	 */
	private Object[] outResult;

	/**
	 * 被修改的记录数量
	 */
	private Long updateCount;

	/**
	 * 针对一个存储过程返回多个结果集合
	 * getRow()只返回第一个集合，moreResults是包含getRow()的第一个集合全部集合
	 */
	private List[] moreResults;

	private List<String[]> labelsList;

	private List<String[]> labelTypesList;

	/**
	 * @return the updateCount
	 */
	public Long getUpdateCount() {
		return updateCount;
	}

	/**
	 * @param updateCount the updateCount to set
	 */
	public void setUpdateCount(Long updateCount) {
		this.updateCount = updateCount;
	}

	/**
	 * @return the outResult
	 */
	public Object[] getOutResult() {
		return outResult;
	}

	/**
	 * @param outResult the outResult to set
	 */
	public void setOutResult(Object[] outResult) {
		this.outResult = outResult;
	}

	public List[] getMoreResults() {
		return moreResults;
	}

	public void setMoreResults(List[] moreResults) {
		this.moreResults = moreResults;
	}

	public List<String[]> getLabelsList() {
		return labelsList;
	}

	public void setLabelsList(List<String[]> labelsList) {
		this.labelsList = labelsList;
	}

	public List<String[]> getLabelTypesList() {
		return labelTypesList;
	}

	public void setLabelTypesList(List<String[]> labelTypesList) {
		this.labelTypesList = labelTypesList;
	}

}
