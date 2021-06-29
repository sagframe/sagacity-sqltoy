package org.sagacity.sqltoy.model;

import java.io.Serializable;

import org.sagacity.sqltoy.model.inner.DataSetResult;

/**
 * @project sagacity-sqltoy4.0
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
}
